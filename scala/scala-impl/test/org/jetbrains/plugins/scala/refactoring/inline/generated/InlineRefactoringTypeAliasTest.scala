package org.jetbrains.plugins.scala.refactoring.inline.generated

import org.jetbrains.plugins.scala.refactoring.inline.InlineRefactoringTestBase

class InlineRefactoringTypeAliasTest extends InlineRefactoringTestBase {
  override def folderPath: String = super.folderPath + "typeAlias/"

  def testInlineSimple(): Unit = doTest()

  def testMultiple(): Unit = doTest()

  def testMultipleFromUsage(): Unit = doTest()

  def testStablePath(): Unit = doTest()

  def testInlineAndKeep(): Unit = doTest()

  def testInlineAndKeepMultiple(): Unit = doTest()

  def testInlineAndKeepMultipleFromUsage(): Unit = doTest()

  def testInlineOnlyCurrentUsage(): Unit = doTest()
}
