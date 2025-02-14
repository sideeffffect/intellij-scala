package org.jetbrains.bsp

import ch.epfl.scala.bsp4j
import ch.epfl.scala.bsp4j._
import com.intellij.mock.MockApplication
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationEvent, ExternalSystemTaskNotificationListener, ExternalSystemTaskType}
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.ex.temp.TempFileSystem
import com.intellij.openapi.vfs.impl.VirtualFileManagerImpl
import org.jetbrains.bsp
import org.jetbrains.bsp.project.importing.BspProjectResolver
import org.jetbrains.bsp.protocol.session.BspSession.{BspServer, BspSessionTask}
import org.jetbrains.bsp.protocol.{BspCommunication, BspCommunicationService}
import org.jetbrains.bsp.settings.BspProjectSettings.{AutoConfig, AutoPreImport}
import org.jetbrains.bsp.settings.{BspExecutionSettings, BspSystemSettings}
import org.jetbrains.plugins.scala.build.{BuildReporter, ConsoleReporter}

import java.io.File
import java.util
import java.util.concurrent.CompletableFuture
import java.util.{Collections, UUID}
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.io.StdIn
import scala.jdk.CollectionConverters._
import scala.language.postfixOps

/**
 * To run the program enter the following command in sbt interactive shell
 * run-main org.jetbrains.bsp.BSPCli --bloop $(which bloop) --project /some/project --log /my/bsp.log
 *
 * compile and test are called against previously resolved build target URIs.
 */
object BSPCli extends App {

  private implicit val reporter: ConsoleReporter = new ConsoleReporter("BSPCli")

  private class DummyListener extends ExternalSystemTaskNotificationListener {
    override def onStart(externalSystemTaskId: ExternalSystemTaskId, workingDir: String): Unit = {}

    override def onStart(externalSystemTaskId: ExternalSystemTaskId): Unit = {}

    override def onStatusChange(externalSystemTaskNotificationEvent: ExternalSystemTaskNotificationEvent): Unit = {}

    override def onTaskOutput(externalSystemTaskId: ExternalSystemTaskId, s: String, b: Boolean): Unit = {}

    override def onEnd(externalSystemTaskId: ExternalSystemTaskId): Unit = {}

    override def onSuccess(externalSystemTaskId: ExternalSystemTaskId): Unit = {}

    override def onFailure(externalSystemTaskId: ExternalSystemTaskId, e: Exception): Unit = {}

    override def beforeCancel(externalSystemTaskId: ExternalSystemTaskId): Unit = {}

    override def onCancel(externalSystemTaskId: ExternalSystemTaskId): Unit = {}
  }

  class Opts(val projectPath: String, val tracePath: Option[String])

  private val opts = try {
    System.setProperty("java.awt.headless", "true")
    val opts = parseOpts(args)
    opts.tracePath.fold({})(p => sys.props += ("BSP_TRACE_PATH" -> p))
    val application: MockApplication = new MockApplication(() => {}) {
      val bspSettingsState: BspSystemSettings.State = {
        val st = new bsp.settings.BspSystemSettings.State()
        st.traceBsp = opts.tracePath.isDefined
        st
      }
      override def isUnitTestMode: Boolean = false

      override def getComponent[T](interfaceClass: Class[T]): T = {
        if (interfaceClass == classOf[VirtualFileManager])
          new VirtualFileManagerImpl(Collections.singletonList(TempFileSystem.getInstance())).asInstanceOf[T]
        else if (interfaceClass == classOf[BspCommunicationService])
          (new BspCommunicationService).asInstanceOf[T]
        else if (interfaceClass == classOf[BspSystemSettings]) {
          val set = new BspSystemSettings
          set.loadState(bspSettingsState)
          set.asInstanceOf[T]
        }
        else super.getService(interfaceClass)
      }
    }
    ApplicationManager.setApplication(application, () => {})
    opts
  } catch {
    case e: IllegalArgumentException =>
      System.err.println(Console.RED + "Error: " + e.getMessage)
      println("Usage: --bloop <path> --project <path> [--log <path>]")
      System.exit(1)
      throw e
    case b: Throwable => throw b
  }

  var running = true
  val bspExecSettings = new BspExecutionSettings(
    new File(opts.projectPath), true, true, AutoPreImport, AutoConfig)
  val bspComm = BspCommunication.forWorkspace(new File(opts.projectPath), AutoConfig)
  val resolver = new BspProjectResolver()
  val targets = {
    println("Resolving build targets...")
    val task = ExternalSystemTaskId.create(BSP.ProjectSystemId, ExternalSystemTaskType.RESOLVE_PROJECT, opts.projectPath)
    resolver.resolveProjectInfo(task, opts.projectPath, isPreviewMode = false, bspExecSettings, new DummyListener)
    val targets = bspReq1(buildTargets)
    println(s"Received ${targets.getTargets.size()} targets:")
    targets.getTargets.forEach(x => println(x.getId.getUri))
    targets
  }
  private val targetIdToTarget =
    targets.getTargets.asScala.map { t => (t.getId, t)}.toMap
  private val targetIds =
    targetIdToTarget.keys.toList
  repl

  type BuildIds = util.List[BuildTargetIdentifier]

  def parseOpts(argsArr: Array[String]): Opts = {
    type OptMap = Map[String, String]

    @scala.annotation.tailrec
    def nextOpt(map: OptMap, list: List[String]): OptMap = {
      list match {
        case Nil => map
        case opt :: value :: tail if opt.startsWith("--") => nextOpt(map + (opt.substring(2) -> value), tail)
        case _ => throw new IllegalArgumentException("Wrong arg format")
      }
    }

    def assertFile(map: OptMap, key: String): String = map.get(key)
      .map(f => if (new File(f).exists()) f else throw new IllegalArgumentException(s"File `$f` does not exist"))
      .orElse(throw new IllegalArgumentException(s"Argument $key was not provided"))
      .get

    val args = nextOpt(Map(), argsArr.toList)
    new Opts(
      assertFile(args, "project"),
      args.get("log")
    )
  }

  private def logProcess(str: String): Unit =
    if (str.endsWith(System.lineSeparator()))
      print(Console.BLUE + str + Console.RESET)
    else println(Console.BLUE + str + Console.RESET)

  private def logDto(not: Any): Unit = println(Console.YELLOW + not + Console.RESET)

  private def bspReq1[T](bspSessionTask: BspServer => CompletableFuture[T])(implicit reporter: BuildReporter): T =
    bspReq { case (server, _) => bspSessionTask(server) }

  private def bspReq[T](bspSessionTask: BspSessionTask[T])(implicit reporter: BuildReporter): T = {
    val result = Await.result(bspComm.run(bspSessionTask, logDto, logProcess).future, Int.MaxValue seconds)
    logDto(result)
    result
  }

  def buildTargets(server: BspServer): CompletableFuture[WorkspaceBuildTargetsResult] =
    server.workspaceBuildTargets()

  def compileRequest(targets: BuildIds)(server: BspServer): CompletableFuture[CompileResult] = {
    val params = new bsp4j.CompileParams(targets)
    params.setOriginId(UUID.randomUUID().toString)
    server.buildTargetCompile(params)
  }

  private def testAllRequest(targets: BuildIds)(server: BspServer): CompletableFuture[TestResult] = {
    val params = new bsp4j.TestParams(targets)
    params.setOriginId(UUID.randomUUID().toString)
    server.buildTargetTest(params)
  }

  private def testSingleRequest(targets: BuildIds, clasId: String, buildTargetUri: String)(server: BspServer): CompletableFuture[TestResult] = {
    val params = new bsp4j.TestParams(targets)
    params.setOriginId(UUID.randomUUID().toString)
    params.setDataKind("scala-test")
    params.setData({
      val p = new ScalaTestParams
      p.setTestClasses(List(
        new ScalaTestClassesItem(
          new BuildTargetIdentifier(buildTargetUri),
          List(clasId).asJava)
      ).asJava)
      p
    })
    server.buildTargetTest(params)
  }


  def testClasses(targets: BuildIds)(server: BspServer): CompletableFuture[ScalaTestClassesResult] = {
    val params = new bsp4j.ScalaTestClassesParams(targets)
    params.setOriginId(UUID.randomUUID().toString)
    server.buildTargetScalaTestClasses(params)
  }

  private def repl(implicit reporter: BuildReporter): Unit = {
    val exit = "exit[ \t]*".r
    val help = "help[ \t]*".r
    val compile = "compile[ \t]*".r
    val getScalaTestClasses = "getScalaTestClasses[ \t]*".r
    val runAllTests = "runAllTests[ \t]*".r
    val runTestClass = "runTestClass[ \t]+([^\\s\\\\]+)[ \t]+([^\\s\\\\]+)[ \t]*".r
    val helpText =
      """Available commands:
        |- compile
        |- getScalaTestClasses
        |- runAllTests
        |- runTestClass <className> <targetUri>
        |- exit
        |- help""".stripMargin
    while (running) {
      print(Console.GREEN + ">>> " + Console.RESET)
      StdIn.readLine() match {
        case exit() =>
          bspComm.closeSession()
          running = false
        case help() =>
          println(helpText)
        case compile() =>
          bspReq1(compileRequest(targetIds.asJava))
        case getScalaTestClasses() =>
          bspReq1(testClasses(targetIds.asJava))
        case runAllTests() =>
          bspReq1(testAllRequest(targetIds.asJava))
        case runTestClass(className, targetUri) =>
          bspReq1(testSingleRequest(targetIds.asJava, className, targetUri))
        case _ =>
          println("Illegal command, type help for more")
      }
    }
    // There are non daemon threads, need to explicitly stop them
    System.exit(0)
  }
}

