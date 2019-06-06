package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import org.apache.commons.lang.StringEscapeUtils.escapeJava
import org.jetbrains.plugins.scala.lang.psi.api.base.literals._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScalaType, api}

/**
 * @author Alexander Podkhalyuzin
 *         Date: 22.02.2008
 */
trait ScLiteral extends ScExpression with PsiLiteral with PsiLanguageInjectionHost {

  def isString: Boolean

  def isMultiLineString: Boolean

  def getAnnotationOwner(annotationOwnerLookUp: ScLiteral => Option[PsiAnnotationOwner with PsiElement]): Option[PsiAnnotationOwner]

  def contentRange: TextRange
}

object ScLiteral {

  val CharQuote = "\'"

  def unapply(literal: ScLiteral): Option[Value[_]] =
    unapplyImpl(literal, literal.getFirstChild.getNode)

  abstract class Value[V <: Any](val value: V) {

    def presentation: String = String.valueOf(value)

    def wideType(implicit project: Project): ScType

    protected final def cachedClass(fqn: String)
                                   (implicit project: Project): ScType =
      ElementScope(project).getCachedClass(fqn)
        .fold(api.Nothing: ScType)(ScalaType.designator)
  }

  sealed trait NumericValue {

    def negate: Value[_] with NumericValue
  }

  object Value {

    def unapply(obj: Any): Option[Value[_]] = Option {
      obj match {
        case integer: Int => IntegerValue(integer)
        case long: Long => LongValue(long)
        case float: Float => FloatValue(float)
        case double: Double => DoubleValue(double)
        case boolean: Boolean => ScBooleanLiteral.Value(boolean)
        case character: Char => ScCharLiteral.Value(character)
        case string: String => StringValue(string)
        case symbol: Symbol => ScSymbolLiteral.Value(symbol)
        case _ => null
      }
    }
  }

  final case class IntegerValue(override val value: Int) extends Value(value) with NumericValue {

    override def negate = IntegerValue(-value)

    override def wideType(implicit project: Project): ScType = api.Int
  }

  final case class LongValue(override val value: Long) extends Value(value) with NumericValue {

    override def negate = LongValue(-value)

    override def presentation: String = super.presentation + 'L'

    override def wideType(implicit project: Project): ScType = api.Long
  }

  final case class FloatValue(override val value: Float) extends Value(value) with NumericValue {

    override def negate = FloatValue(-value)

    override def presentation: String = super.presentation + 'f'

    override def wideType(implicit project: Project): ScType = api.Float
  }

  final case class DoubleValue(override val value: Double) extends Value(value) with NumericValue {

    override def negate = DoubleValue(-value)

    override def wideType(implicit project: Project): ScType = api.Double
  }

  final case class StringValue(override val value: String) extends Value(value) {

    override def presentation: String = "\"" + escapeJava(super.presentation) + "\""

    override def wideType(implicit project: Project): ScType = cachedClass(CommonClassNames.JAVA_LANG_STRING)
  }

  @annotation.tailrec
  private[this] def unapplyImpl(literal: ScLiteral, node: ASTNode): Option[Value[_]] = {
    import lang.lexer.{ScalaTokenTypes => T}

    def as[T: reflect.ClassTag](function: T => Value[T]) = literal.getValue match {
      case value: T => Some(function(value))
      case _ => None
    }

    (node.getElementType, node.getText) match {
      case (T.tIDENTIFIER, "-") => unapplyImpl(literal, node.getTreeNext)
      case (T.tINTEGER, text) =>
        if (text.matches(".*[lL]$")) as(LongValue)
        else as(IntegerValue) // but a conversion exists to narrower types in case range fits
      case (T.tFLOAT, text) =>
        if (text.matches(".*[fF]$")) as(FloatValue)
        else as(DoubleValue)
      case (T.tSTRING |
            T.tWRONG_STRING |
            T.tMULTILINE_STRING, _) => as(StringValue)
      case _ => None
    }
  }
}