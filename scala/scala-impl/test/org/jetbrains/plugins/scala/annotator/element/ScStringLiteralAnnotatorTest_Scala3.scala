package org.jetbrains.plugins.scala.annotator.element

import junit.framework.Test
import org.jetbrains.plugins.scala.ScalaVersion

class ScStringLiteralAnnotatorTest_Scala3
  extends ScStringLiteralAnnotatorTestBase("/annotator/string_literals/scala3") {
  
  override def supportedInScalaVersion(version: ScalaVersion): Boolean = version.isScala3
}

object ScStringLiteralAnnotatorTest_Scala3 {
  def suite: Test = new ScStringLiteralAnnotatorTest_Scala3
}