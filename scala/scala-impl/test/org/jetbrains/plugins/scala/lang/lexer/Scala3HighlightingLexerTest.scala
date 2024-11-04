package org.jetbrains.plugins.scala.lang.lexer

import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightVirtualFile
import junit.framework.{Test, TestCase}
import org.jetbrains.plugins.scala.highlighter.ScalaSyntaxHighlighterFactory
import org.jetbrains.plugins.scala.project.{ScalaFeaturePusher, ScalaFeatures}
import org.jetbrains.plugins.scala.{Scala3Language, ScalaFileType, ScalaVersion}

class Scala3HighlightingLexerTest extends TestCase

object Scala3HighlightingLexerTest {
  def suite: Test = new ScalaLexerTestBase("/lexer/highlighting3") {
    override protected def createLexer(project: Project): Lexer = {
      val virtualFile = new LightVirtualFile("dummy.scala", ScalaFileType.INSTANCE, "")

      ScalaFeaturePusher.setFeatures(virtualFile, ScalaFeatures.onlyByVersion(ScalaVersion.Latest.Scala_3))

      val scalaSyntaxHighlighter = ScalaSyntaxHighlighterFactory.createScalaSyntaxHighlighter(project, virtualFile, getLanguage)
      scalaSyntaxHighlighter.getHighlightingLexer
    }

    override protected def getLanguage: Scala3Language = return Scala3Language.INSTANCE
  }
}
