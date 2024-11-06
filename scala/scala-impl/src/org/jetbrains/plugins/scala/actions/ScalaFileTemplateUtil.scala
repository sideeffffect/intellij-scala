package org.jetbrains.plugins.scala.actions

import com.intellij.application.options.CodeStyle
import com.intellij.ide.fileTemplates.FileTemplate
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.{PsiClass, PsiFile, PsiMethod}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.base.ScOptionalBracesOwner

import java.util.Properties

object ScalaFileTemplateUtil {
  val SCALA_IMPLEMENTED_METHOD_TEMPLATE = "Implemented Scala Method Body.scala"
  val SCALA_OVERRIDDEN_METHOD_TEMPLATE = "Overridden Scala Method Body.scala"

  val SCALA_OBJECT = "Scala Object"
  val SCALA_TRAIT = "Scala Trait"
  val SCALA_CLASS = "Scala Class"
  val SCALA_CASE_CLASS = "Scala CaseClass"
  val SCALA_CASE_OBJECT = "Scala CaseObject"
  val SCALA_FILE = "Scala File"
  val SCALA_ENUM = "Scala Enum"

  def setClassAndMethodNameProperties(properties: Properties, aClass: PsiClass, method: PsiMethod): Unit = {
    var className: String = aClass.qualifiedName
    if (className == null) className = ""
    properties.setProperty(FileTemplate.ATTRIBUTE_CLASS_NAME, className)
    var classSimpleName: String = aClass.name
    if (classSimpleName == null) classSimpleName = ""
    properties.setProperty(FileTemplate.ATTRIBUTE_SIMPLE_CLASS_NAME, classSimpleName)
    val methodName: String = method.name
    properties.setProperty(FileTemplate.ATTRIBUTE_METHOD_NAME, methodName)
  }

  def removeBracesIfIndentationBasedSyntaxIsEnabled(file: PsiFile): Unit = {
    if (useIndentationBasedSyntax(file)) {
      WriteCommandAction
        .writeCommandAction(file.getProject)
        .shouldRecordActionForActiveDocument(false)
        .run { () =>
          val optionalBracesOwners = file.elements.filterByType[ScOptionalBracesOwner]
          //TODO (minor) we should handle all kind of code with braces from potential user-defined file templates
          optionalBracesOwners.foreach(removeEmptyBraces)
        }
    }
  }

  private def useIndentationBasedSyntax(psiFile: PsiFile): Boolean = {
    val codeStyleSettings = CodeStyle.getSettings(psiFile)
    val scalaCodeStyleSettings = codeStyleSettings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    scalaCodeStyleSettings.USE_SCALA3_INDENTATION_BASED_SYNTAX
  }

  private def removeEmptyBraces(bracesOwner: ScOptionalBracesOwner): Unit = {
    for {
      lBrace <- bracesOwner.getLBrace
      rBrace <- bracesOwner.getRBrace
    } {
      if (lBrace.getNextSiblingNotWhitespace eq rBrace) {
        val spaceBefore = lBrace.prevLeaf.map(_.getNode)

        val bracesParent = lBrace.getNode.getTreeParent
        bracesParent.removeRange(lBrace.getNode, rBrace.getNode.getTreeNext)

        //remove space between the left brace and the previous code
        spaceBefore.foreach(space => space.getTreeParent.removeChild(space))
      }
    }
  }
}
