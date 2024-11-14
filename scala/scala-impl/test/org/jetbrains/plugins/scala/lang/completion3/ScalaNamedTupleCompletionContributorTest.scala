package org.jetbrains.plugins.scala.lang.completion3

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase
import org.jetbrains.plugins.scala.util.runners.{RunWithScalaVersions, TestScalaVersion}

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_3_5
))
class ScalaNamedTupleCompletionContributorTest extends ScalaCompletionTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= ScalaVersion.Latest.Scala_3_5

  def testInUnitExpr(): Unit = doCompletionTest(
    fileText =
      s"""val x: (a: Int, b: String) = ($CARET)
         |""".stripMargin,
    resultText =
      s"""val x: (a: Int, b: String) = (a = ???, b = ???)
         |""".stripMargin,
    item = "a = ???, b = ???",
  )

  def testCompleteExistingNamedTuple(): Unit = doCompletionTest(
    fileText =
      s"""val x: (a: Int, b: String) = (a = 1, $CARET)
         |""".stripMargin,
    resultText =
      s"""val x: (a: Int, b: String) = (a = 1, b = ???)
         |""".stripMargin,
    item = "a = 1, b = ???",
  )

  def testCompleteExistingNamedTupleWithReordering(): Unit = doCompletionTest(
    fileText =
      s"""val x: (a: Int, b: String) = (b = "test", $CARET)
         |""".stripMargin,
    resultText =
      s"""val x: (a: Int, b: String) = (a = ???, b = "test")
         |""".stripMargin,
    item = """a = ???, b = "test"""",
  )
}
