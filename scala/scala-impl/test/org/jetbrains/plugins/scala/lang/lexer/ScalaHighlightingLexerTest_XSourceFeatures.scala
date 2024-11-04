package org.jetbrains.plugins.scala.lang.lexer

import com.intellij.lexer.Lexer
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightVirtualFile
import junit.framework.{Test, TestCase}
import org.jetbrains.plugins.scala.{ScalaFileType, ScalaVersion}
import org.jetbrains.plugins.scala.highlighter.ScalaSyntaxHighlighterFactory
import org.jetbrains.plugins.scala.project.{ModuleExt, ScalaFeaturePusher, ScalaLanguageLevel, UserDataKeys}

class ScalaHighlightingLexerTest_XSourceFeatures extends TestCase

object ScalaHighlightingLexerTest_XSourceFeatures {
  def suite: Test = new ScalaLexerTestBase("/lexer/highlighting_XSourceFeatures") {
    override protected def createLexer(project: Project): Lexer = {
      val virtualFile = new LightVirtualFile("dummy.scala", ScalaFileType.INSTANCE, "")

      val module = ModuleManager.getInstance(project).getModules()(0)
      addCompilerOptions(module, Seq("-Xsource:3", "-Xsource-features:unicode-escapes-raw"))
      module.putUserData(UserDataKeys.LightTestScalaVersion, ScalaVersion.Latest.Scala_2_13)

      ScalaFeaturePusher.setFeatures(virtualFile, module.features)

      val scalaSyntaxHighlighter = ScalaSyntaxHighlighterFactory.createScalaSyntaxHighlighter(project, virtualFile, getLanguage)
      scalaSyntaxHighlighter.getHighlightingLexer
    }

    protected def addCompilerOptions(module: Module, additionalCompilerOptions: Seq[String]): Unit = {
      val profile = module.scalaCompilerSettingsProfile
      val newSettings = profile.getSettings.copy(additionalCompilerOptions = additionalCompilerOptions)
      profile.setSettings(newSettings)
    }
  }
}