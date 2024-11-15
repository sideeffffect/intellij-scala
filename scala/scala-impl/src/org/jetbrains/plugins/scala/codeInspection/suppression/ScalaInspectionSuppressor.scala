package org.jetbrains.plugins.scala.codeInspection.suppression

import com.intellij.codeInspection.{InspectionSuppressor, SuppressQuickFix}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.EditorArea.isVisible

class ScalaInspectionSuppressor extends InspectionSuppressor {
  override def isSuppressedFor(element: PsiElement, toolId: String): Boolean = {
    if (!isVisible(element)) return false

    ScalaSuppressableInspectionTool.findElementToolSuppressedIn(element, toolId).isDefined
  }

  override def getSuppressActions(element: PsiElement, toolShortName: String): Array[SuppressQuickFix] = {
    if (!isVisible(element)) return Array.empty

    ScalaSuppressableInspectionTool.suppressActions(toolShortName)
  }
}
