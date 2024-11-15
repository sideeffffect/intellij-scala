package org.jetbrains.plugins.scala

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.editor.event.{EditorFactoryEvent, EditorFactoryListener, VisibleAreaEvent, VisibleAreaListener}
import com.intellij.openapi.editor.{Editor, EditorFactory, LogicalPosition}
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.{Key, TextRange}
import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiManager}
import org.jetbrains.plugins.scala.EditorArea._

import java.awt.Point

class EditorArea extends EditorFactoryListener {
  private val visibleAreaListener = new VisibleAreaListener {
    override def visibleAreaChanged(e: VisibleAreaEvent): Unit = {
      val editor = e.getEditor

      val visibleRange = visibleRangeIn(editor, incrementalHighlightingLookaround)
      editor.putUserData(VISIBLE_RANGE_KEY, visibleRange)

      val psiFile = PsiManager.getInstance(editor.getProject).findFile(editor.getVirtualFile)
      DaemonCodeAnalyzer.getInstance(editor.getProject).restart(psiFile)
    }
  }

  override def editorCreated(event: EditorFactoryEvent): Unit = {
    if (!isIncrementalHighlightingEnabled) return

    val editor = event.getEditor
    val file = editor.getVirtualFile
    if (file == null || file.getExtension != "scala" && file.getExtension != "sc" && file.getExtension != "sbt") return

    editor.getScrollingModel.addVisibleAreaListener(visibleAreaListener)
  }

  override def editorReleased(event: EditorFactoryEvent): Unit = {
    if (!isIncrementalHighlightingEnabled) return

    event.getEditor.getScrollingModel.removeVisibleAreaListener(visibleAreaListener)
  }
}

object EditorArea {
  private val VISIBLE_RANGE_KEY: Key[TextRange] = Key.create[TextRange]("editor_visible_range")

  private def isNativeHighlightingEnabled: Boolean = Registry.is("scala.native.highlighting")

  private def isIncrementalHighlightingEnabled: Boolean = Registry.is("scala.incremental.highlighting")

  private def incrementalHighlightingLookaround: Int = Registry.intValue("scala.incremental.highlighting.lookaround")

  def isVisible(e: PsiElement): Boolean = {
    if (!isNativeHighlightingEnabled) return false

    if (!isIncrementalHighlightingEnabled) return true

    val document = PsiDocumentManager.getInstance(e.getProject).getDocument(e.getContainingFile)
    if (document == null) return false

    val editors = EditorFactory.getInstance.getEditors(document)
    if (editors.isEmpty) return false

    val visibleRange = editors.head.getUserData(VISIBLE_RANGE_KEY)
    e.getTextRange.intersects(visibleRange)
  }

  private def visibleRangeIn(editor: Editor, lookaround: Int): TextRange = {
    val visibleRectangle = editor.getScrollingModel.getVisibleArea

    val startOffset = {
      val position = editor.xyToLogicalPosition(visibleRectangle.getLocation)
      val adjustedPosition = new LogicalPosition((position.line - lookaround).max(0), 0)
      editor.logicalPositionToOffset(adjustedPosition)
    }

    val endOffset = {
      val position = editor.xyToLogicalPosition(new Point(visibleRectangle.x + visibleRectangle.width, visibleRectangle.y + visibleRectangle.height))
      val adjustedPosition = new LogicalPosition(position.line + lookaround + 1, 0)
      editor.logicalPositionToOffset(adjustedPosition)
    }

    TextRange.create(startOffset, startOffset.max(endOffset))
  }
}
