package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.ScalaVersion

class Scala3NamedTuplesTest extends TypeInferenceTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= ScalaVersion.Latest.Scala_3_5
  def testConformanceNormalOk(): Unit = checkTextHasNoErrors(
    """
      |val a: (x: Int) = (x = 1)
      |val b: (x: Int, y: Int) = (x = 1, y = 2)
      |""".stripMargin
  )

  def testConformanceLiteralTypeOk(): Unit = checkTextHasNoErrors(
    """
      |val x: (x: 1) = (x = 1)
      |""".stripMargin
  )

  def testConformanceWithDesugaredOk(): Unit = checkTextHasNoErrors(
    """
      |val a: scala.NamedTuple.NamedTuple[("x", "y"), (Int, String)] = (x = 1, y = "hello")
      |val b: (x: Int, y: String) = scala.NamedTuple[("x", "y"), (Int, String)]((1, "hello"))
      |""".stripMargin
  )

  def testExprType(): Unit = doTest(
    s"""
      |val a = $START(x = 1, y = 2)$END
      |//(x: Int, y: Int)
      |""".stripMargin
  )

  def testExprTypeLiteral(): Unit = doTest(
    s"""
       |val a: (x: 1, y: 2) = $START(x = 1, y = 2)$END
       |
       |//(x: 1, y: 2)
       |""".stripMargin
  )

  def testTypeElementType(): Unit = doTest(
    s"""
       |val a: (x: Int, y: String) = ???
       |println(${START}a$END)
       |
       |//(x: Int, y: String)
       |""".stripMargin
  )

  def testComponentPatternInference(): Unit = doTest(
    s"""
       |val (x = value) = (x = 1)
       |${START}value$END
       |
       |//Int
       |""".stripMargin
  )
}
