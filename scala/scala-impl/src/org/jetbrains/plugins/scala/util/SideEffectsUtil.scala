package org.jetbrains.plugins.scala.util

import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.extensions.{&, ObjectExt, PsiClassExt, PsiMemberExt, PsiNamedElementExt, Resolved, ResolvesTo}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.types.api.FunctionType
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil.nameFitToPatterns

import scala.annotation.unused
import scala.collection.immutable.ArraySeq

object SideEffectsUtil {

  private val immutableClasses = listImmutableClasses

  private val knownUnsafeGetters: ArraySeq[String] = ArraySeq(
    "scala.Option",
    "scala.None",
    "scala.util.Try",
    "scala.util.Either.RightProjection",
    "scala.util.Either.LeftProjection"
  ).map(_ + ".get")

  private val knownUnsafeMethodNames: ArraySeq[String] =
    ArraySeq("head", "tail", "last", "reduce", "reduceLeft", "reduceRight")

  private val knownMethodsWithSideEffects: ArraySeq[String] = {
    val objectMethods = ArraySeq("wait", "finalize", "notifyAll", "notify").map("java.lang.Object." + _)
    val stringMethods = "java.lang.String.getChars" //copies to destination array
    objectMethods :+ stringMethods
  }

  private val knownMethodsWithoutSideEffects: Set[String] = Set(
    "getClass",
    "toString",
    "hashCode",
    "clone",
    "equals"
  )

  @unused
  private val methodPrefixesIndicatingNoSideEffect: ArraySeq[String] = ArraySeq(
    "to",
    "is",
    "can",
    // "get",
  )

  def hasNoSideEffects(expr: ScExpression): Boolean = hasNoSideEffectsInner(expr)(allowThrows = false)

  def hasNoSideEffectsItself(expr: ScExpression): Boolean =
    hasNoSideEffectsInner(expr, asArg = false, checkSubExpression = false)(allowThrows = false)

  def mayOnlyThrow(expr: ScExpression): Boolean = hasNoSideEffectsInner(expr)(allowThrows = true)

  private def hasNoSideEffectsInner(expr: ScExpression)(implicit allowThrows: Boolean): Boolean =
    hasNoSideEffectsInner(expr, asArg = false)

  private def hasNoSideEffectsInner(expr: ScExpression,
                                    asArg: Boolean,
                                    checkSubExpression: Boolean = true)(implicit allowThrows: Boolean): Boolean = {

    expr match {
      case lit: ScInterpolatedStringLiteral =>
        import ScInterpolatedStringLiteral._
        lit.kind match {
          case Standard |
               Format |
               Raw => true
          case _ => false
        }
      case _: ScLiteral => true
      case _: ScThisReference => true
      case und: ScUnderscoreSection if und.bindingExpr.isEmpty => true
      case ScParenthesisedExpr(inner) => !checkSubExpression || hasNoSideEffectsInner(inner)
      case typed: ScTypedExpression => (!checkSubExpression && !typed.hasAnnotation) || hasNoSideEffectsInner(typed.expr)
      case ref: ScReferenceExpression =>
        if (hasImplicitConversion(ref)) false
        else {
          ref.qualifier.forall(hasNoSideEffectsInner) && (ref.resolve() match {
            case (_: ScBindingPattern) & ScalaPsiUtil.inNameContext(pd: ScPatternDefinition)
              if pd.hasModifierProperty("lazy") => false
            case bp: ScBindingPattern =>
              val tp = bp.`type`()
              !(asArg && FunctionType.isFunctionType(tp.getOrAny))
            case _: ScObject => true // not correct, but very likely that a lone object-ref has no sideeffect
            case p: ScParameter if p.isCallByNameParameter => false
            case p: ScParameter if !(asArg && FunctionType.isFunctionType(p.getRealParameterType.getOrAny)) => true
            case _: ScSyntheticFunction => true
            case m: PsiMethod => methodHasNoSideEffects(m, ref.qualifier.flatMap(_.`type`().toOption))
            case _ => false
          })
        }
      case t: ScTuple => !checkSubExpression || t.exprs.forall(hasNoSideEffectsInner)
      case nt: ScNamedTuple => !checkSubExpression || nt.components.forall(_.expr.forall(hasNoSideEffectsInner))
      case inf: ScInfixExpr if inf.isAssignmentOperator => false
      case call@ScSugarCallExpr(baseExpr, operation, args) =>
        val checkOperation = operation match {
          case ref if hasImplicitConversion(ref) => false
          case ref if ref.refName.endsWith("_=") => false
          case ResolvesTo(fun: ScSyntheticFunction) => syntheticMethodHasNoSideEffects(fun)
          case ResolvesTo(m: PsiMethod) => methodHasNoSideEffects(m, baseExpr.`type`().toOption)
          case _ => false
        }
        checkOperation &&
          (!checkSubExpression || hasNoSideEffectsInner(baseExpr)) &&
          argsHaveNoSideEffectInner(call, args, checkSubExpression)
      case call@ScMethodCall(baseExpr, args) =>
        val (checkQual, typeOfQual) = baseExpr match {
          case ScReferenceExpression.withQualifier(qual) => (hasNoSideEffectsInner(qual), qual.`type`().toOption)
          case _ => (true, None)
        }
        val checkBaseExpr = baseExpr match {
          case _ if hasImplicitConversion(baseExpr) => false
          case _: ScUnderscoreSection => false
          case Resolved(rr) if rr.isAssignment => false
          case ResolvesTo(m: PsiMethod) => methodHasNoSideEffects(m, typeOfQual)
          case ResolvesTo(fun: ScSyntheticFunction) => syntheticMethodHasNoSideEffects(fun)
          case ResolvesTo(_: ScTypedDefinition) =>
            val withApplyText = baseExpr.getText + ".apply" + args.map(_.getText).mkString("(", ", ", ")")
            val withApply = ScalaPsiElementFactory.createExpressionWithContextFromText(withApplyText, expr.getContext, expr)
            withApply match {
              case ScMethodCall(ResolvesTo(m: PsiMethod), _) =>
                methodHasNoSideEffects(m, typeOfQual)
              case _ => false
            }
          case _ => hasNoSideEffectsInner(baseExpr)
        }
        checkQual && checkBaseExpr && argsHaveNoSideEffectInner(call, args, checkSubExpression)
      case _: ScNewTemplateDefinition => false
      case _ => false
    }
  }

  private def argsHaveNoSideEffectInner(invoc: ScExpression, args: Seq[ScExpression], checkSubExpression: Boolean)(implicit allowThrows: Boolean): Boolean = {
    if (args.isEmpty) true
    else if (args.exists(_.is[ScBlock])) false
    else if (checkSubExpression) {
      args.forall(hasNoSideEffectsInner(_, asArg = true))
    } else {
      invoc.matchedParameters.forall {
        case (arg, param) if param.isByName || FunctionType.isFunctionType(param.paramType) =>
          hasNoSideEffectsInner(arg, asArg = true)
        case _ => true
      }
    }
  }

  private def listImmutableClasses = {
    val excludeNonString = Seq("StringBuffer._", "StringBuilder._").map("exclude:java.lang." + _)

    val javaWrappers = Seq("Integer", "Byte", "Character", "Short", "Boolean", "Long", "Double", "Float")
      .map(name => s"java.lang.$name._")

    val otherJavaClasses = Seq("java.lang.String._", "java.lang.Math._", "java.math.BigInteger._", "java.math.BigDecimal._")

    val scalaValueClasses = Seq("Boolean", "Byte", "Char", "Double", "Float", "Int", "Long", "Unit")
      .map(name => s"scala.$name._")

    val otherFromScalaPackage = Seq("Option._", "Some._", "Tuple._", "Symbol._").map("scala." + _)

    val fromScalaUtil = Seq("Either", "Failure", "Left", "Right", "Success", "Try")
      .map(name => s"scala.util.$name._")

    val fromScalaMath = Seq("scala.math.BigInt._", "scala.math.BigDecimal._")

    val immutableCollections = Seq("scala.collection.immutable._", "scala.collection.IterableFactory._")

    (excludeNonString ++: javaWrappers ++: otherJavaClasses ++:
      scalaValueClasses ++: otherFromScalaPackage ++: fromScalaUtil ++: fromScalaMath ++: immutableCollections).to(ArraySeq)
  }

  private def hasImplicitConversion(refExpr: ScExpression) = {
    refExpr match {
      case ref: ScReferenceExpression =>
        ref.bind().exists(rr => rr.implicitFunction.isDefined)
      case _ => false
    }
  }

  private def syntheticMethodHasNoSideEffects(m: ScSyntheticFunction): Boolean = {
    m.name != "synchronized"
  }

  private def methodHasNoSideEffects(m: PsiMethod, typeOfQual: Option[ScType] = None)
                                    (implicit allowThrows: Boolean): Boolean = {
    val methodName = m.name

    if (knownUnsafeMethodNames.contains(methodName) && !allowThrows)
      return false

    if (methodNameIndicatesNoSideEffects(methodName))
      return true

    val methodClazzName = Option(m.containingClass).map(_.qualifiedName)

    methodClazzName match {
      case Some(fqn) =>
        val methodQName = fqn + "." + methodName
        if (nameFitToPatterns(methodQName, knownMethodsWithSideEffects, strict = false))
          return false

        if (nameFitToPatterns(methodQName, knownUnsafeGetters, strict = false) && !allowThrows)
          return false
      case _ =>
    }

    val clazzName = typeOfQual.map(_.widen.tryExtractDesignatorSingleton) match {
      case Some(tp) => tp.extractClass.map(_.qualifiedName)
      case None => methodClazzName
    }

    clazzName.map(_ + "." + methodName) match {
      case Some(methodQName) => nameFitToPatterns(methodQName, immutableClasses, strict = false)
      case None => false
    }
  }

  private def methodNameIndicatesNoSideEffects(name: String): Boolean =
    knownMethodsWithoutSideEffects.contains(name)
      // || methodPrefixesIndicatingNoSideEffect.exists(prefix => name.startsWith(prefix) && name.lift(prefix.length).forall(_.isUpper))
}

