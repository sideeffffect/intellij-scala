package org.jetbrains.plugins.scala.debugger

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.SourcePositionHighlighter
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.util.DocumentUtil
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScFunctionExpr

class ScalaSourcePositionHighlighter extends SourcePositionHighlighter {
  override def getHighlightRange(sourcePosition: SourcePosition): TextRange = {
    if (isScalaLanguage(sourcePosition)) {
      val element = sourcePosition.getElementAt
      if (element eq null) return null

      val document = sourcePosition.getFile.getViewProvider.getDocument
      if (document eq null) return null

      val lineRange = DocumentUtil.getLineTextRange(document, sourcePosition.getLine)

      if (isWholeLine(lineRange, element)) return null

      if (ScalaPositionManager.isLambda(element)) {
        element match {
          case f: ScFunctionExpr => f.result.getOrElse(f).getTextRange
          case e => e.getTextRange
        }
      } else null
    }
    else null
  }

  private def isScalaLanguage(sourcePosition: SourcePosition): Boolean =
    sourcePosition.getFile.getLanguage.isKindOf(ScalaLanguage.INSTANCE)

  private def isWholeLine(lineRange: TextRange, element: PsiElement): Boolean =
    lineRange == element.getTextRange
}
