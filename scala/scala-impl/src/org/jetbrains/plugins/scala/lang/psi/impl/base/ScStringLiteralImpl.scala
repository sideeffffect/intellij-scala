package org.jetbrains.plugins.scala.lang.psi.impl.base

import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.tree.IElementType
import org.apache.commons.text.StringEscapeUtils
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.impl.base.literals.QuotedLiteralImplBase
import org.jetbrains.plugins.scala.lang.psi.impl.base.literals.escapers.{ScLiteralEscaper, ScLiteralRawEscaper, ScalaStringParser}
import org.jetbrains.plugins.scala.lang.psi.types._

// todo: move to "literals" subpackage, but check usages

class ScStringLiteralImpl(node: ASTNode,
                          override val toString: String)
  extends QuotedLiteralImplBase(node, toString)
    with ScStringLiteral {

  import QuotedLiteralImplBase._
  import ScStringLiteralImpl._
  import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._

  override protected def startQuote: String =
    if (isMultiLineString) MultiLineQuote
    else if (hasValidClosingQuotes) SingleLineQuote
    else ""

  override protected final def toValue(text: String): String = {
    val noUnicodeEscapesInRawStrings = this.noUnicodeEscapesInRawStrings
    val isRaw = this match {
      case s: ScInterpolatedStringLiteral => s.kind == ScInterpolatedStringLiteral.Raw
      case _ => this.isMultiLineString
    }
    val parser = new ScalaStringParser(null, isRaw, noUnicodeEscapesInRawStrings = noUnicodeEscapesInRawStrings, exitOnEscapingWrongSymbol = false)
    val builder = new java.lang.StringBuilder()
    parser.parse(text, builder)
    builder.toString
  }

  override protected final def wrappedValue(value: String): ScLiteral.Value[String] =
    Value(value)

  override protected def fallbackType: ScType = cachedClass(CommonClassNames.JAVA_LANG_STRING)

  override def hasValidClosingQuotes: Boolean = firstChildElementType != `tWRONG_STRING`

  override def isMultiLineString: Boolean = firstChildElementType == `tMULTILINE_STRING`

  private def firstChildElementType: IElementType = firstNode.getElementType

  protected final def firstNode: ASTNode = getNode.getFirstChildNode

  override def isValidHost: Boolean = getValue != null

  override def updateText(text: String): ScStringLiteralImpl = {
    firstNode match {
      case leaf: LeafElement => leaf.replaceWithText(text)
    }
    this
  }

  override def createLiteralTextEscaper: LiteralTextEscaper[ScStringLiteral] =
    if (isMultiLineString) new ScLiteralRawEscaper(this)
    else new ScLiteralEscaper(this)

  override def getReferences: Array[PsiReference] = PsiReferenceService.getService.getContributedReferences(this)
}

object ScStringLiteralImpl {

  /**
   * @param value aт unescaped value of a string literal.<br>
   *              Example:<br>
   *              Original string: "test \u0023"
   *              Value: "test #"
   */
  final case class Value(override val value: String) extends ScLiteral.Value(value) {

    import QuotedLiteralImplBase.SingleLineQuote

    override def presentation: String = SingleLineQuote + StringEscapeUtils.escapeJava(super.presentation) + SingleLineQuote

    override def wideType(implicit project: Project): ScType = cachedClass(CommonClassNames.JAVA_LANG_STRING)
  }
}
