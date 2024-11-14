package org.jetbrains.plugins.scala.lang.completion

import com.intellij.codeInsight.completion.{CompletionContributor, CompletionParameters, CompletionType, InsertHandler, InsertionContext}
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementBuilder}
import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiTreeUtil.getContextOfType
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, ToNullSafe}
import org.jetbrains.plugins.scala.lang.completion.ScalaNamedTupleCompletionContributor.ScalaNamedTupleCompletionProvider
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScNamedTuple, ScNamedTupleExprComponent, ScParenthesisedExpr, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.types.api.NamedTupleType

import scala.annotation.tailrec

class ScalaNamedTupleCompletionContributor extends CompletionContributor {
  extend(
    CompletionType.BASIC,
    identifierWithParentsPattern(classOf[ScReferenceExpression], classOf[ScNamedTupleExprComponent], classOf[ScNamedTuple]),
    ScalaNamedTupleCompletionProvider,
  )

  extend(
    CompletionType.BASIC,
    identifierWithParentsPattern(classOf[ScReferenceExpression], classOf[ScParenthesisedExpr]),
    ScalaNamedTupleCompletionProvider,
  )
}

object ScalaNamedTupleCompletionContributor {
  object ScalaNamedTupleCompletionProvider extends ScalaCompletionProvider {
    override protected def completionsFor(position: PsiElement)
                                         (implicit parameters: CompletionParameters, context: ProcessingContext): Iterable[LookupElement] = {
      val refParent = getContextOfType(position, classOf[ScReferenceExpression])
        .nullSafe
        .map(_.getParent)
        .orNull
      refParent match {
        case expr: ScParenthesisedExpr => createCompletionFor(expr, Map.empty)
        case comp: ScNamedTupleExprComponent =>
          val namedTuple = comp.namedTuple
          val existingComponents =
            namedTuple
              .components
              .flatMap { comp =>
                comp.nameElement.zip(comp.expr)
                  .map { case (name, expr) => name.getText -> expr.getText }
              }.toMap

          createCompletionFor(namedTuple, existingComponents)
        case _ =>
          Seq.empty
      }
    }

    private def createCompletionFor(expr: ScExpression, existingComponents: Map[String, String]): Seq[LookupElement] = {
      expr.expectedType() match {
        case Some(NamedTupleType(components)) =>
          val elements =
            components.map {
              case (NamedTupleType.NameType(name), _) => name //s"$name = " + existingComponents.getOrElse(name, s"???")
              case _ => "???"
            }

          val presentation = elements
            .filterNot(existingComponents.contains)
            .mkString("", ", ", " =")
          val text = elements
            .map(name => s"$name = ${existingComponents.getOrElse(name, "???")}")
            .mkString(", ")
          Seq(
            LookupElementBuilder
              .create(expr, text)
              .withPresentableText(presentation)
              .withInsertHandler(ScalaNamedTupleInsertHandler)
          )
        case _ =>
          Seq.empty
      }
    }
  }

  private object ScalaNamedTupleInsertHandler extends InsertHandler[LookupElement] {
    override final def handleInsert(context: InsertionContext, element: LookupElement): Unit = {
      context.getCompletionChar match {
        case ')' =>
        case _ =>
          val editor = context.getEditor
          val document = context.getDocument
          val startOffset = context.getStartOffset
          val endOffset = startOffset + element.getLookupString.length
          val rangeText = document.getText(new TextRange(startOffset, endOffset))

          val namedTuple = Option(context.getFile.findElementAt(startOffset))
            .flatMap(_.parentOfType(classOf[ScNamedTuple]))
            .orNull
          if (namedTuple == null)
            return

          val builder = new TemplateBuilderImpl(namedTuple)
          val offsetInNamedTuple = startOffset - namedTuple.startOffset

          @tailrec
          def foreachOccurrence(subj: String, idx: Int = 0)(f: TextRange => Unit): Unit =
            rangeText.indexOf(subj, idx) match {
              case -1 => ()
              case i =>
                f(TextRange.from(offsetInNamedTuple + i, subj.length))
                foreachOccurrence(subj, i + subj.length)(f)
            }

          // put template selections over all "???" in the tuple
          foreachOccurrence("???") {
            builder.replaceRange(_, "???")
          }

          if (PsiTreeUtil.nextLeaf(namedTuple) != null) {
            // make sure the caret is after the tuple
            builder.setEndVariableAfter(namedTuple)
          }

          builder.run(editor, true)

          // Remove existing elements. We already reinserted when the lookup is selected
          if (endOffset != namedTuple.endOffset - 1 || namedTuple.startOffset + 1 != startOffset) {
            document.deleteString(endOffset, namedTuple.endOffset - 1)
            document.deleteString(namedTuple.startOffset + 1, startOffset)
            context.commitDocument()
          }
      }
    }
  }
}