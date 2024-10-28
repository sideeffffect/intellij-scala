package org.jetbrains.plugins.scala.annotator.element

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.ThrowableRunnable
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.{ScalaFileSetTestCase, ScalaLightCodeInsightFixtureTestCase}
import org.jetbrains.plugins.scala.extensions.StringExt

import java.io.File
import java.nio.charset.StandardCharsets

trait ScalaLightCodeInsightFixtureTest_ForFileTestTests extends ScalaLightCodeInsightFixtureTestCase {

  def fileBasedTest: ScalaFileSetTestCase
  def myTestFile: File

  override def getName: String = myTestFile.getName.takeWhile(_ != '.') //file name without extension

  override protected def getTestName(lowercaseFirstLetter: Boolean) = ""

  override protected def supportedIn(version: ScalaVersion): Boolean =
    fileBasedTest.supportedInScalaVersion(version)

  override protected def setUp(): Unit = {
    super.setUp()

    fileBasedTest.setUp(getProject)
  }

  override def tearDown(): Unit = {
    fileBasedTest.tearDown(getProject)

    super.tearDown()
  }

  override def runTestRunnable(testRunnable: ThrowableRunnable[Throwable]): Unit = {
    val testFileText = FileUtil.loadFile(myTestFile, StandardCharsets.UTF_8).withNormalizedSeparator
    try {
      runTestForTestFileText(testFileText)
    } catch {
      case error: Throwable =>
        // to be able to navigate to the original test file location on test failure
        // (you can use Ctrl/Cmd + Click in the console)
        // (note, might not work with Android plugin disabled, see IDEA-257969)
        System.err.println(s"### Test file: ${myTestFile.getAbsolutePath}")
        throw error
    }
  }

  protected def runTestForTestFileText(testFileText: String): Unit
}
