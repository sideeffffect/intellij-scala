package org.jetbrains.plugins.scala.lang.parameterInfo

import com.intellij.codeInsight.hint.ShowParameterInfoHandler
import com.intellij.codeInsight.{CodeInsightBundle, TargetElementUtil}
import com.intellij.lang.parameterInfo._
import com.intellij.psi._
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parameterInfo.ScalaFunctionParameterInfoHandler.{AnnotationParameters, UniversalApplyCall, UniversalApplyCallContext}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParameterizedTypeElement, ScTypeArgs, ScTypeElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructorInvocation, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScConstructorOwner, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScTypeParametersOwner, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import org.jetbrains.plugins.scala.lang.psi.light.ScFunctionWrapper
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.TypeAnnotationRenderer.ParameterTypeDecorator
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.{ModifiersRenderer, ParameterRenderer, TypeAnnotationRenderer, TypeParamsRenderer, TypeRenderer}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.processor.CompletionProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ResolveUtils, ScalaResolveResult, StdKinds}
import org.jetbrains.plugins.scala.project.ProjectContext

import java.awt.Color
import java.util
import scala.annotation.tailrec
import scala.collection.immutable.ArraySeq

class ScalaFunctionParameterInfoHandler extends ScalaParameterInfoHandler[PsiElement, Any, ScExpression] {

  override def getArgListStopSearchClasses: util.Set[_ <: Class[_]] =
    util.Collections.singleton(classOf[PsiMethod])

  override def getActualParameterDelimiterType: IElementType = ScalaTokenTypes.tCOMMA

  override def getActualParameters(elem: PsiElement): Array[ScExpression] = {
    elem match {
      case argExprList: ScArgumentExprList =>
        argExprList.exprs.toArray
      case _: ScUnitExpr => Array.empty
      case p: ScParenthesisedExpr => p.innerElement.toArray
      case t: ScTuple => t.exprs.toArray
      case e: ScExpression => Array(e)
      case _ => Array.empty
    }
  }

  override def getArgumentListClass: Class[PsiElement] = classOf[PsiElement]

  override def getActualParametersRBraceType: IElementType = ScalaTokenTypes.tRBRACE

  override def getArgumentListAllowedParentClasses: util.Set[Class[_]] = {
    val set = new util.HashSet[Class[_]]()
    set.add(classOf[ScMethodCall])
    set.add(classOf[ScConstructorInvocation])
    set.add(classOf[ScSelfInvocation])
    set.add(classOf[ScInfixExpr])
    set.add(classOf[ScReferenceExpression])
    set
  }

  override def updateUI(p: Any, context: ParameterInfoUIContext): Unit = {
    if (context == null || context.getParameterOwner == null || !context.getParameterOwner.isValid) return
    context.getParameterOwner match {
      case args: PsiElement =>
        implicit val tpc: TypePresentationContext = TypePresentationContext(args)
        val color: Color = context.getDefaultParameterColor
        val index = context.getCurrentParameterIndex
        val buffer: StringBuilder = new StringBuilder("")
        var isGrey = false
        var isDeprecated = false

        def paramText(param: ScParameter, subst: ScSubstitutor) = {
          val typeRenderer: TypeRenderer = subst(_).presentableText
          val renderer = new ParameterRenderer(
            typeRenderer,
            ModifiersRenderer.SimpleText(),
            new TypeAnnotationRenderer(typeRenderer, ParameterTypeDecorator.DecorateAllMinimized),
            withAnnotations = true
          )
          renderer.render(param)
        }
        def typeParamText(param: ScTypeParam, subst: ScSubstitutor) = {
          new TypeParamsRenderer(subst(_).presentableText).render(param)
        }
        p match {
          case x: String if x == "" =>
            noParams(buffer)
          case (a: AnnotationParameters, _: Int) =>
            val seq = a.seq
            if (seq.isEmpty) noParams(buffer)
            else {
              val paramsSeq: Seq[(Parameter, String)] = seq.zipWithIndex.map {
                case ((name, tp, value), paramIndex) =>
                  val valueText = Option(value).map(_.getText)
                    .map(" = " + _)
                    .getOrElse("")

                  (new Parameter(name, None, tp, tp, value != null, false, false, paramIndex),
                    s"$name: ${tp.presentableText}$valueText")
              }
              isGrey = applyToParameters(paramsSeq, ScSubstitutor.empty, canBeNaming = true)(args, buffer, index)
            }
          case (sign: PhysicalMethodSignature, i: Int) => //i  can be -1 (it's update method)
            val subst = sign.substitutor

            def processMethod(psiMethod: PsiMethod): Unit = {
              def processApplyMethod(tpe: ScType): Unit = {
                val maybeApplyMethod = tpe.extractClass.flatMap(_.getAllMethods.collectFirst {
                  case fn: ScFunction if fn.isApplyMethod =>
                    fn
                  case wrapper: ScFunctionWrapper if wrapper.delegate.isApplyMethod =>
                    wrapper.delegate
                })

                maybeApplyMethod.fold(noParams(buffer))(processMethod)
              }

              if (psiMethod != null)
                isDeprecated = psiMethod.isDeprecated

              psiMethod match {
                case method: ScFunction =>
                  val isEffective = method.allClauses.length <= i
                  val clauses = if (isEffective) method.effectiveParameterClauses else method.allClauses

                  if (clauses.length <= i || (i == -1 && clauses.isEmpty)) {
                    if (clauses.isEmpty && i == 0) { // SCL-20512
                      method.returnType.fold(_ => noParams(buffer), processApplyMethod)
                    }
                    else noParams(buffer)
                  }
                  else {
                    val clause: ScParameterClause = if (i >= 0) clauses(i) else clauses.head
                    val length = clause.effectiveParameters.length

                    val precedingClauses = if (i == -1) Seq.empty else clauses.take(i)
                    val remainingClauses = if (i == -1) Seq.empty else clauses.drop(i + 1)

                    val typeParameters = method.typeParameters
                    val multipleLists = typeParameters.nonEmpty || precedingClauses.nonEmpty || remainingClauses.nonEmpty

                    def parametersOf(clause: ScParameterClause): Seq[(Parameter, String)] = {
                      val parameters0 = if (isEffective) clause.effectiveParameters else clause.parameters
                      val parameters: Seq[ScParameter] = if (i != -1) parameters0 else parameters0.take(length - 1)
                      parameters.map(param => (Parameter(param), paramText(param, subst)))
                    }

                    if (typeParameters.nonEmpty) {
                      buffer.append("[")
                      buffer.append(typeParameters.map(typeParamText(_, subst)).mkString(", "))
                      buffer.append("]")
                    }

                    precedingClauses.foreach { clause =>
                      buffer.append("(")
                      val parameters = parametersOf(clause)
                      if (parameters.nonEmpty) {
                        applyToParameters(parameters, subst, clause, canBeNaming = true)(args, buffer, -1)
                      }
                      buffer.append(")")
                    }

                    if (multipleLists) {
                      buffer.append("(")
                    }
                    isGrey = applyToParameters(parametersOf(clause), subst, clause, canBeNaming = true)(args, buffer, index)
                    if (multipleLists) {
                      buffer.append(")")
                    }

                    remainingClauses.foreach { clause =>
                      buffer.append("(")
                      val parameters = parametersOf(clause)
                      if (parameters.nonEmpty) {
                        applyToParameters(parameters, subst, clause, canBeNaming = true)(args, buffer, -1)
                      }
                      buffer.append(")")
                    }
                  }
                case method: FakePsiMethod =>
                  val params = method.params
                  if (params.length == 0) processApplyMethod(method.retType)
                  else {
                    buffer.append(params.
                      map((param: Parameter) => {
                        val buffer: StringBuilder = new StringBuilder("")
                        val paramType = param.paramType
                        val name = param.name
                        if (name != "") {
                          buffer.append(name)
                          buffer.append(": ")
                        }
                        buffer.append(paramType.presentableText)
                        if (param.isRepeated) buffer.append("*")

                        if (param.isDefault) buffer.append(" = _")

                        val isBold = if (params.indexOf(param) == index || (param.isRepeated && params.indexOf(param) <= index)) true
                        else {
                          //todo: check type
                          false
                        }
                        val paramText = buffer.toString()
                        if (isBold) "<b>" + paramText + "</b>" else paramText
                      }).mkString(", "))
                  }
                case method: PsiMethod =>
                  val p = method.getParameterList
                  if (p.getParameters.isEmpty) noParams(buffer)
                  else {
                    buffer.append(p.getParameters.
                      map((param: PsiParameter) => {
                        val buffer: StringBuilder = new StringBuilder("")
                        val list = param.getModifierList
                        if (list == null) return
                        val lastSize = buffer.length
                        for (a <- list.getAnnotations) {
                          if (lastSize != buffer.length) buffer.append(" ")
                          val element = a.getNameReferenceElement
                          if (element != null) buffer.append("@").append(element.getText)
                        }
                        if (lastSize != buffer.length) buffer.append(" ")

                        val name = param.name
                        if (name != null) {
                          buffer.append(name)
                        }
                        buffer.append(": ")
                        buffer.append(subst(param.paramType()).presentableText)
                        if (param.isVarArgs) buffer.append("*")

                        val isBold = if (p.getParameters.indexOf(param) == index || (param.isVarArgs && p.getParameters.indexOf(param) <= index)) true
                        else {
                          //todo: check type
                          false
                        }
                        val paramText = buffer.toString()
                        if (isBold) "<b>" + paramText + "</b>" else paramText
                      }).mkString(", "))
                  }
              }
            }

            processMethod(sign.method)
          case (constructor: ScPrimaryConstructor, subst: ScSubstitutor, i: Int) if constructor.isValid =>
            isDeprecated = constructor.isDeprecated
            val isEffective = constructor.allClauses.length <= i
            val clauses = if (isEffective) constructor.effectiveParameterClauses else constructor.allClauses

            if (clauses.length <= i) noParams(buffer)
            else {
              val clause: ScParameterClause = clauses(i)
              val preceedingClauses = clauses.take(i)
              val remainingClauses = clauses.drop(i + 1)
              val typeParameters = constructor.getParent match {
                case owner: ScTypeParametersOwner => owner.typeParameters
                case _ => Seq.empty
              }
              val multipleLists = typeParameters.nonEmpty || preceedingClauses.nonEmpty || remainingClauses.nonEmpty

              def parametersOf(clause: ScParameterClause) = {
                val parameters = if (isEffective) clause.effectiveParameters else clause.parameters
                parameters.map(param => (Parameter(param), paramText(param, subst)))
              }

              if (typeParameters.nonEmpty) {
                buffer.append("[")
                buffer.append(typeParameters.map(typeParamText(_, subst)).mkString(", "))
                buffer.append("]")
              }

              preceedingClauses.foreach { clause =>
                buffer.append("(")
                val parameters = parametersOf(clause)
                if (parameters.nonEmpty) {
                  applyToParameters(parameters, subst, clause, canBeNaming = true)(args, buffer, -1)
                }
                buffer.append(")")
              }

              if (multipleLists) {
                buffer.append("(")
              }
              isGrey = applyToParameters(parametersOf(clause), subst, clause, canBeNaming = true)(args, buffer, index)
              if (multipleLists) {
                buffer.append(")")
              }

              remainingClauses.foreach { clause =>
                buffer.append("(")
                val parameters = parametersOf(clause)
                if (parameters.nonEmpty) {
                  applyToParameters(parameters, subst, clause, canBeNaming = true)(args, buffer, -1)
                }
                buffer.append(")")
              }
            }
          case _ =>
        }
        val startOffset = buffer.indexOf("<b>")
        if (startOffset != -1) buffer.replace(startOffset, startOffset + 3, "")

        val endOffset = buffer.indexOf("</b>")
        if (endOffset != -1) buffer.replace(endOffset, endOffset + 4, "")

        if (buffer.toString != "")
          context.setupUIComponentPresentation(
            buffer.toString(),
            startOffset,
            endOffset,
            isGrey,
            isDeprecated && !context.isSingleParameterInfo && !context.isSingleOverload,
            false,
            color
          )
        else
          context.setUIComponentEnabled(false)
      case _ =>
    }
  }

  private def noParams(buffer: StringBuilder): Unit = buffer.append(CodeInsightBundle.message("parameter.info.no.parameters"))

  private def applyToParameters(parameters: Seq[(Parameter, String)],
                                subst: ScSubstitutor,
                                clause: ScParameterClause,
                                canBeNaming: Boolean)(args: PsiElement, buffer: StringBuilder, index: Int): Boolean = {
    val clauseModifiersText = if (clause.isImplicit) "implicit " else if (clause.isUsing) "using " else ""
    applyToParameters(parameters, subst, canBeNaming, clauseModifiersText)(args, buffer, index)
  }

  private def applyToParameters(parameters: Seq[(Parameter, String)],
                                subst: ScSubstitutor,
                                canBeNaming: Boolean,
                                clauseModifiersText: String = "")(args: PsiElement, buffer: StringBuilder, index: Int): Boolean = {
    var isGrey = false
    //todo: var isGreen = true
    var namedMode = false

    if (parameters.nonEmpty) {
      var k = 0
      val exprs: Seq[ScExpression] = getActualParameters(args).toSeq
      if (clauseModifiersText.nonEmpty) buffer.append(clauseModifiersText)
      val used = new Array[Boolean](parameters.length)
      while (k < parameters.length) {
        val namedPrefix = "["
        val namedPostfix = "]"

        def appendFirst(useGrey: Boolean = false): Unit = {
          val getIt = used.indexOf(false)
          used(getIt) = true
          if (namedMode) buffer.append(namedPrefix)
          val param: (Parameter, String) = parameters(getIt)

          buffer.append(param._2)
          if (namedMode) buffer.append(namedPostfix)
        }
        def doNoNamed(expr: ScExpression): Unit = {
          if (namedMode) {
            isGrey = true
            appendFirst()
          } else {
            val exprType = expr.`type`().getOrNothing
            val getIt = used.indexOf(false)
            used(getIt) = true
            val param: (Parameter, String) = parameters(getIt)
            val paramType = subst(param._1.paramType)
            if (!exprType.conforms(paramType)) isGrey = true
            buffer.append(param._2)
          }
        }
        if (k == index || (k == parameters.length - 1 && index >= parameters.length &&
          parameters.last._1.isRepeated)) {
          buffer.append("<b>")
        }
        if (k < index && !isGrey) {
          //slow checking
          if (k >= exprs.length) { //shouldn't be
            appendFirst(useGrey = true)
            isGrey = true
          } else {
            exprs(k) match {
              case assign@ScAssignment.Named(name) =>
                val ind = parameters.indexWhere(param => ScalaNamesUtil.equivalent(param._1.name, name))
                if (ind == -1 || used(ind)) {
                  doNoNamed(assign)
                } else {
                  if (k != ind) namedMode = true
                  used(ind) = true
                  val param: (Parameter, String) = parameters(ind)
                  if (namedMode) buffer.append(namedPrefix)
                  buffer.append(param._2)
                  if (namedMode) buffer.append(namedPostfix)
                  assign.rightExpression match {
                    case Some(expr: ScExpression) =>
                      for (exprType <- expr.`type`()) {
                        val paramType = subst(param._1.paramType)
                        if (!exprType.conforms(paramType)) isGrey = true
                      }
                    case _ => isGrey = true
                  }
                }
              case expr: ScExpression =>
                doNoNamed(expr)
            }
          }
        } else {
          //fast checking
          if (k >= exprs.length) {
            appendFirst()
          } else {
            exprs(k) match {
              case ScAssignment.Named(name) =>
                val ind = parameters.indexWhere(param => ScalaNamesUtil.equivalent(param._1.name, name))
                if (ind == -1 || used(ind)) {
                  appendFirst()
                } else {
                  if (k != ind) namedMode = true
                  used(ind) = true
                  if (namedMode) buffer.append(namedPrefix)
                  buffer.append(parameters(ind)._2)
                  if (namedMode) buffer.append(namedPostfix)
                }
              case _ => appendFirst()
            }
          }
        }
        if (k == index || (k == parameters.length - 1 && index >= parameters.length &&
          parameters.last._1.isRepeated)) {
          buffer.append("</b>")
        }
        k = k + 1
        if (k != parameters.length) buffer.append(", ")
      }
      if (!isGrey && exprs.length > parameters.length && index >= parameters.length) {
        if (!namedMode && parameters.last._1.isRepeated) {
          val paramType = subst(parameters.last._1.paramType)
          while (!isGrey && k < exprs.length.min(index)) {
            if (k < index) {
              for (exprType <- exprs(k).`type`()) {
                if (!exprType.conforms(paramType)) isGrey = true
              }
            }
            k = k + 1
          }
        } else isGrey = true
      }
    } else noParams(buffer)

    isGrey
  }

  trait Invocation {
    def element: PsiElement
    def parent: PsiElement = element.getParent
    def invocationCount: Int
    def callGeneric: Option[ScGenericCall] = None
    def callReference: Option[ScReferenceExpression]
    def arguments: Seq[ScExpression]
  }

  object Invocation {
    private class CallInvocation(args: ScArgumentExprList) extends Invocation {
      override def element: PsiElement = args

      override def callGeneric: Option[ScGenericCall] = args.callGeneric

      override def invocationCount: Int = args.invocationCount

      override def callReference: Option[ScReferenceExpression] = args.callReference

      override def arguments: Seq[ScExpression] = args.exprs
    }
    private trait InfixInvocation extends Invocation {
      override def invocationCount: Int = 1

      override def callReference: Option[ScReferenceExpression] = {
        element.getParent match {
          case i: ScInfixExpr => Some(i.operation)
        }
      }
    }
    private class InfixExpressionInvocation(expr: ScExpression) extends InfixInvocation {
      override def element: PsiElement = expr

      override def arguments: Seq[ScExpression] = Seq(expr)
    }
    private class ReferenceExpressionInvocation(expr: ScReferenceExpression) extends Invocation {
      override def element: PsiElement = expr

      override def parent: PsiElement = element

      override def invocationCount: Int = 0

      override def callReference: Option[ScReferenceExpression] = Some(expr)

      override def arguments: Seq[ScExpression] = Seq.empty
    }
    private class InfixTupleInvocation(tuple: ScTuple) extends InfixInvocation {
      override def element: PsiElement = tuple

      override def arguments: Seq[ScExpression] = tuple.exprs
    }
    private class InfixUnitInvocation(u: ScUnitExpr) extends InfixInvocation {
      override def element: PsiElement = u

      override def arguments: Seq[ScExpression] = Seq(u)
    }

    def getInvocation(elem: PsiElement): Option[Invocation] = {
      def create[T <: PsiElement](elem: T)
                                 (f: T => Invocation): Option[Invocation] =
        elem.getParent match {
          case ScInfixExpr.withAssoc(_, _, `elem`) => Some(f(elem))
          case _ => None
        }

      elem match {
        case args: ScArgumentExprList => Some(new CallInvocation(args))
        case t: ScTuple => create(t)(new InfixTupleInvocation(_))
        case u: ScUnitExpr => create(u)(new InfixUnitInvocation(_))
        case e: ScExpression => create(e)(new InfixExpressionInvocation(_))
        case _ => None
      }
    }

    def implicitInvocation(ref: ScReferenceExpression): Option[Invocation] =
      hasOnlyImplicitParameters(ref)
        .option(new ReferenceExpressionInvocation(ref))

    private def hasOnlyImplicitParameters(e: ScReferenceExpression) = {
      Option(e.resolve())
        .flatMap(_.asOptionOf[ScFunctionDefinition])
        .exists(f => f.paramClauses.clauses.nonEmpty && f.paramClauses.clauses.forall(_.isImplicitOrUsing))
    }
  }

  private def elementsForParameterInfo(args: Invocation): Seq[Object] = {
    implicit val project: ProjectContext = args.element.projectContext

    def elementsForConstructorInvocationParameterInfo(clazz: PsiClass,
                                                      subst: ScSubstitutor,
                                                      argumentLists: Seq[ScalaPsiElement],
                                                      maybeTypeArgs: Option[ScTypeArgs]): Seq[Object] = {
      val resultBuilder = ArraySeq.newBuilder[Object]
      val i = argumentLists.indexOf(args.element)

      clazz match {
        case constructorOwner: ScConstructorOwner =>
          constructorOwner.constructor match {
            case Some(constructor: ScPrimaryConstructor) if i < constructor.effectiveParameterClauses.length =>
              maybeTypeArgs match {
                case Some(typeArgs) =>
                  val substitutor = ScSubstitutor.bind(constructorOwner.typeParameters, typeArgs.typeArgs)(_.calcType)
                  resultBuilder += ((constructor, substitutor.followed(subst), i))
                case _ => resultBuilder += ((constructor, subst, i))
              }
            case Some(_) if i == 0 => resultBuilder += ""
            case None => resultBuilder += ""
            case _ =>
          }

          constructorOwner.functions.foreach { function =>
            if (function.isConstructor && function.clauses.fold(1)(_.clauses.length) > i) {
              resultBuilder += ((new PhysicalMethodSignature(function, subst), i))
            }
          }
        case annotation: PsiClass if annotation.isAnnotationType =>
          val seq = annotation.getMethods.toSeq.collect {
            case method: PsiAnnotationMethod =>
              (method.name, method.getReturnType.toScType(), method.getDefaultValue)
          }
          resultBuilder += ((AnnotationParameters(seq), i))
        case psiClass: PsiClass if !psiClass.is[ScTypeDefinition] =>
          psiClass.getConstructors.foreach { constructor =>
            maybeTypeArgs match {
              case Some(typeArgs) =>
                val substitutor = ScSubstitutor.bind(psiClass.getTypeParameters, typeArgs.typeArgs)(_.calcType)
                val signature = new PhysicalMethodSignature(constructor, substitutor.followed(subst))
                resultBuilder += ((signature, i))
              case _ =>
                val signature = new PhysicalMethodSignature(constructor, subst)
                resultBuilder += ((signature, i))
            }
          }
        case _ =>
      }
      resultBuilder.result()
    }

    args.parent match {
      case UniversalApplyCall(UniversalApplyCallContext(constructor, substitutor, argumentLists, maybeTypeArgs)) =>
        val psiClass = constructor.containingClass
        elementsForConstructorInvocationParameterInfo(psiClass, substitutor, argumentLists, maybeTypeArgs)
      case constrInvocation: ScConstructorInvocation =>
        val typeElement = constrInvocation.typeElement
        typeElement.calcType.extractClassType match {
          case Some((psiClass: PsiClass, substitutor: ScSubstitutor)) =>
            val maybeTypeArgs = typeElement match {
              case gen: ScParameterizedTypeElement =>
                Some(gen.typeArgList)
              case _ => None
            }
            elementsForConstructorInvocationParameterInfo(psiClass, substitutor, constrInvocation.arguments, maybeTypeArgs)
          case _ => Seq.empty
        }
      case call@(_: MethodInvocation | _: ScReferenceExpression) =>
        val resultBuilder = ArraySeq.newBuilder[Object]
        def collectResult(): Unit = {
          val canBeUpdate = call.getParent match {
            case assignStmt: ScAssignment if call == assignStmt.leftExpression => true
            case notExpr if !notExpr.is[ScExpression] || notExpr.is[ScBlockExpr] => true
            case _ => false
          }
          val count = args.invocationCount
          val gen = args.callGeneric.getOrElse(null: ScGenericCall)
          def collectSubstitutor(element: PsiElement): ScSubstitutor = {
            if (gen == null) return ScSubstitutor.empty
            val typeParams = element match {
              case tpo: ScTypeParametersOwner => tpo.typeParameters.toArray
              case ptpo: PsiTypeParameterListOwner => ptpo.getTypeParameters
              case _ => return ScSubstitutor.empty
            }
            ScSubstitutor.bind(typeParams, gen.arguments)(_.calcType)
          }
          def collectForType(typez: ScType): Unit = {
            def process(functionName: String): Unit = {
              val i = if (functionName == "update") -1 else 0
              val processor: CompletionProcessor = new CompletionProcessor(StdKinds.refExprQualRef, call, withImplicitConversions = true) {

                override protected val forName: Option[String] = Some(functionName)
              }
              processor.processType(typez, call)
              val variants: Array[ScalaResolveResult] = processor.candidates
              for {
                variant <- variants
                if !variant.getElement.isInstanceOf[PsiMember] ||
                  ResolveUtils.isAccessible(variant.getElement.asInstanceOf[PsiMember], call)
              } {
                variant match {
                  case ScalaResolveResult(method: ScFunction, subst: ScSubstitutor) =>
                    val signature: PhysicalMethodSignature = new PhysicalMethodSignature(method, subst.followed(collectSubstitutor(method)))
                    resultBuilder += ((signature, i))
                    resultBuilder ++= ScalaParameterInfoEnhancer.enhance(signature, args.arguments).map((_, i))
                  case _ =>
                }
              }
            }

            process("apply")
            if (canBeUpdate) process("update")
          }
          args.callReference match {
            case Some(ref: ScReferenceExpression) =>
              if (count > 1) {
                //todo: missed case with last implicit call
                ref.bind() match {
                  case Some(ScalaResolveResult(function: ScFunction, subst: ScSubstitutor)) if function.
                    effectiveParameterClauses.length >= count =>
                    resultBuilder += ((new PhysicalMethodSignature(function, subst.followed(collectSubstitutor(function))), count - 1))
                  case Some(ScalaResolveResult(function: ScFunction, _)) if function.effectiveParameterClauses.isEmpty =>
                    function.`type`().foreach(collectForType)
                  case _ =>
                    call match {
                      case invocation: MethodInvocation =>
                        for (typez <- invocation.getEffectiveInvokedExpr.`type`()) //todo: implicit conversions
                        {collectForType(typez)}
                      case _ =>
                    }
                }
              } else {
                val variants = {
                  val sameName = ref.getSameNameVariants
                  if (sameName.isEmpty) ref.multiResolveScala(false)
                  else sameName
                }
                for {
                  variant <- variants
                  if !variant.getElement.isInstanceOf[PsiMember] ||
                    ResolveUtils.isAccessible(variant.getElement.asInstanceOf[PsiMember], ref)
                } {
                  variant match {
                    //todo: Synthetic function
                    case ScalaResolveResult(method: PsiMethod, subst: ScSubstitutor) =>
                      val signature: PhysicalMethodSignature = new PhysicalMethodSignature(method, subst.followed(collectSubstitutor(method)))
                      resultBuilder += ((signature, 0))
                      resultBuilder ++= ScalaParameterInfoEnhancer.enhance(signature, args.arguments).map((_, 0))
                    case ScalaResolveResult(typed: ScTypedDefinition, subst: ScSubstitutor) =>
                      val typez = subst(typed.`type`().getOrNothing) //todo: implicit conversions
                      collectForType(typez)
                    case _ =>
                  }
                }
              }
            case None =>
              call match {
                case call: ScMethodCall =>
                  for (typez <- call.getEffectiveInvokedExpr.`type`()) { //todo: implicit conversions
                    collectForType(typez)
                  }
              }
          }
        }
        collectResult()
        resultBuilder.result()
      case self: ScSelfInvocation =>
        val resultBuilder = ArraySeq.newBuilder[Object]
        val i = self.arguments.indexOf(args.element)

        self.parentOfType(classOf[ScClass]).foreach { clazz =>
          clazz.constructor match {
            case Some(constr: ScPrimaryConstructor) if i < constr.effectiveParameterClauses.length =>
              resultBuilder += ((constr, ScSubstitutor.empty, i))
            case Some(_) if i == 0 => resultBuilder += ""
            case None => resultBuilder += ""
            case _ =>
          }

          for {
            constr <- clazz.functions
            if constr.isConstructor &&
               constr.clauses.map(_.clauses.length).getOrElse(1) > i
          } {
            if (!PsiTreeUtil.isAncestor(constr, self, true) &&
              constr.getTextRange.getStartOffset < self.getTextRange.getStartOffset) {
              resultBuilder += ((new PhysicalMethodSignature(constr, ScSubstitutor.empty), i))
            }
          }
        }
        resultBuilder.result()
    }
  }

  /**
   * Returns context's argument psi and fill context items
   * by appropriate PsiElements (in which we can resolve)
    *
   * @param context current context
   * @return context's argument expression
   */
  override protected def findCall(context: ParameterInfoContext): PsiElement = {
    val file = context.getFile
    val offset = context.getOffset
    val element = file.findElementAt(offset)
    if (element.is[PsiWhiteSpace])
    if (element == null) return null
    @tailrec
    def findArgs(elem: PsiElement): Option[Invocation] = {
      if (elem == null) return None
      val res = Invocation.getInvocation(elem)
      if (res.isDefined) return res
      findArgs(elem.getParent)
    }

    def refWithImplicitArgs = {
      TargetElementUtil.findReference(context.getEditor, offset) match {
        case ref: ScReferenceExpression => Invocation.implicitInvocation(ref)
        case _                          => None
      }
    }

    val argsOption = findArgs(element).orElse(refWithImplicitArgs)

    if (argsOption.isEmpty) return null
    val args = argsOption.get
    implicit val project: ProjectContext = file.projectContext
    context match {
      case context: CreateParameterInfoContext =>
        context.setItemsToShow(elementsForParameterInfo(args).toArray)
      case context: UpdateParameterInfoContext if args.arguments.nonEmpty =>
        var el = element
        while (el.getParent != args.element) el = el.getParent
        var index = 1
        for (expr <- getActualParameters(args.element) if expr != el) index += 1
        context.setCurrentParameter(index)
        context.setHighlightedParameter(el)

        if (!equivalent(ArraySeq.unsafeWrapArray(context.getObjectsToView), elementsForParameterInfo(args))) {
          //e.g. it may happen on moving caret to a different argument clause of the same call
          //let's try to show more specific hint for it

          context.removeHint()

          invokeLater {
            ShowParameterInfoHandler.invoke(project, context.getEditor, context.getFile, context.getOffset, null, false)
          }
        }
      case _ =>
    }
    args.element
  }

  private def equivalent(seq1: Seq[AnyRef], seq2: Seq[AnyRef]): Boolean = {
    seq1.size == seq2.size && seq1.zip(seq2).forall(equivObjectsToView)
  }

  private def equivObjectsToView(tuple: (AnyRef, AnyRef)): Boolean = tuple match {
    case (s1: String, s2: String) =>
      s1 == s2
    case ((a1: AnnotationParameters, i1: Int), (a2: AnnotationParameters, i2: Int)) =>
      i1 == i2 && a1 == a2
    case ((sign1: PhysicalMethodSignature, i1: Int), (sign2: PhysicalMethodSignature, i2: Int)) =>
      i1 == i2 && sign1.method == sign2.method
    case ((pc1: ScPrimaryConstructor, _: ScSubstitutor, i1: Int), (pc2: ScPrimaryConstructor, _: ScSubstitutor, i2: Int)) =>
      i1 == i2 && pc1 == pc2
    case _ => false
  }
}

object ScalaFunctionParameterInfoHandler {
  final case class AnnotationParameters(seq: Seq[(String, ScType, PsiAnnotationMemberValue)])

  private final case class UniversalApplyCallContext(
    constructor: PsiMethod,
    substitutor: ScSubstitutor,
    argumentLists: Seq[ScArgumentExprList] = Seq.empty,
    typeArgs: Option[ScTypeArgs] = None,
  )

  private object UniversalApplyCall {
    def unapply(call: ScMethodCall): Option[UniversalApplyCallContext] =
      if (!call.isInScala3File) None
      else call.deepestInvokedExpr match {
        case ref: ScReferenceExpression =>
          findConstructorWithSubstitutor(ref)
            .map(_.copy(argumentLists = getArgumentLists(call)))

        case genCall @ ScGenericCall(ref, _) =>
          findConstructorWithSubstitutor(ref)
            .map(_.copy(
              argumentLists = getArgumentLists(call),
              typeArgs = Some(genCall.typeArgs)
            ))
        case _ => None
      }

    private[this] def findConstructorWithSubstitutor(ref: ScReferenceExpression): Option[UniversalApplyCallContext] =
      ref.multiResolveScala(incomplete = false)
        .headOption
        .collect {
          case ScalaResolveResult(method: PsiMethod, subst) if method.isConstructor =>
            UniversalApplyCallContext(method, subst)
        }

    private[this] def getArgumentLists(call: ScMethodCall): Seq[ScArgumentExprList] = {
      @tailrec def doGetArgs(call: ScMethodCall, acc: Vector[ScArgumentExprList]): Seq[ScArgumentExprList] =
        call.getEffectiveInvokedExpr match {
          case inner: ScMethodCall => doGetArgs(inner, inner.args +: acc)
          case _ => acc
        }

      doGetArgs(call, Vector(call.args))
    }
  }
}
