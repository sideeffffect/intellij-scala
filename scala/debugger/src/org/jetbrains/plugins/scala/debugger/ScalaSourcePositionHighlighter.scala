package org.jetbrains.plugins.scala.debugger

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.SourcePositionHighlighter
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScFunctionExpr

class ScalaSourcePositionHighlighter extends SourcePositionHighlighter {
  override def getHighlightRange(sourcePosition: SourcePosition): TextRange = {
    if (!isScalaLanguage(sourcePosition)) return null
    //noinspection InstanceOf
    if (sourcePosition.isInstanceOf[ScalaSourcePositionWithWholeLineHighlighted]) return null

    val element = sourcePosition.getElementAt
    if (element eq null) return null

    element match {
      case f: ScFunctionExpr => f.result.getOrElse(f).getTextRange
      case e => e.getTextRange
    }
  }

  private def isScalaLanguage(sourcePosition: SourcePosition): Boolean =
    sourcePosition.getFile.getLanguage.isKindOf(ScalaLanguage.INSTANCE)
}
