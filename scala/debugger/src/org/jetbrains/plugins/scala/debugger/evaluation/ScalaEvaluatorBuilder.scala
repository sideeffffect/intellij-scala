package org.jetbrains.plugins.scala.debugger.evaluation

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.evaluation._
import com.intellij.debugger.engine.evaluation.expression._
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.psi._
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.debugger.DebuggerBundle
import org.jetbrains.plugins.scala.debugger.evaluation.evaluator._
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml.ScXmlExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionWithContextFromText
import org.jetbrains.plugins.scala.lang.psi.impl.source.ScalaCodeFragment
import org.jetbrains.plugins.scala.project.{ProjectContext, ProjectContextOwner}
import org.jetbrains.plugins.scala.statistics.ScalaDebuggerUsagesCollector
import org.jetbrains.plugins.scala.util.AnonymousFunction
import org.jetbrains.plugins.scala.{NlsString, Scala3Language}

object ScalaEvaluatorBuilder extends EvaluatorBuilder {
  override def build(codeFragment: PsiElement, position: SourcePosition): ExpressionEvaluator = {
    if (codeFragment.getLanguage.is(JavaLanguage.INSTANCE))
      return EvaluatorBuilderImpl.getInstance().build(codeFragment, position) //java builder (e.g. SCL-6117)

    ScalaDebuggerUsagesCollector.logEvaluator(codeFragment.getProject)

    val scalaFragment = codeFragment match {
      case sf: ScalaCodeFragment => sf
      case _ => throw new IllegalArgumentException("Non-scala code fragment in scala evaluator builder")
    }

    val project = codeFragment.getProject

    val cache = ScalaEvaluatorCache.getInstance(project)
    val cached: Option[Evaluator] = {
      try cache.get(position, codeFragment)
      catch {
        case c: ControlFlowException => throw c
        case _: Exception =>
          cache.clear()
          None
      }
    }

    def buildSimpleEvaluator: Evaluator = {
      cached.getOrElse {
        val simple = new ScalaEvaluatorBuilder(scalaFragment, position).getEvaluator
        val evaluator =
          if (codeFragment.getLanguage.is(Scala3Language.INSTANCE)) {
            val shim: Evaluator = new ExpressionCompilerEvaluator(codeFragment, position).evaluate(_)
            ScalaDuplexEvaluator(simple, shim)
          } else simple

        cache.add(position, scalaFragment, evaluator)
      }
    }

    def buildCompilingEvaluator: ScalaCompilingEvaluator = {
      val compilingEvaluator = new ScalaCompilingEvaluator(DebuggerUtil.elementAtSourcePosition(position), scalaFragment)
      cache.add(position, scalaFragment, compilingEvaluator).asInstanceOf[ScalaCompilingEvaluator]
    }

    try {
      val ev = buildSimpleEvaluator
      new ExpressionEvaluatorImpl(ev)
    } catch {
      case _: NeedCompilationException =>
        ScalaDebuggerUsagesCollector.logCompilingEvaluator(project)
        if (codeFragment.getLanguage.is(Scala3Language.INSTANCE))
          new ExpressionCompilerEvaluator(codeFragment, position)
        else
          new ScalaCompilingExpressionEvaluator(buildCompilingEvaluator)
      case e: EvaluateException =>
        throw e
    }
  }
}

private[evaluation] class NeedCompilationException(@Nls message: String) extends EvaluateException(message)

private[evaluation] class ScalaEvaluatorBuilder(val codeFragment: ScalaCodeFragment,
                                                val position: SourcePosition)
        extends ScalaEvaluatorBuilderUtil with SyntheticVariablesHelper with ProjectContextOwner {

  import org.jetbrains.plugins.scala.debugger.evaluation.ScalaEvaluatorBuilderUtil._

  override implicit def projectContext: ProjectContext = codeFragment.projectContext

  val contextClass: PsiElement =
    Option(DebuggerUtil.elementAtSourcePosition(position)).map(getContextClass(_, strict = false)).orNull

  private def getEvaluator: Evaluator = new UnwrapRefEvaluator(fragmentEvaluator(codeFragment))

  protected def evaluatorFor(element: PsiElement): Evaluator = {
    element match {
      case implicitlyConvertedTo(expr)     => evaluatorFor(expr)
      case needsCompilation(message)       => throw new NeedCompilationException(message.nls)
      case byNameParameterFunction(p, ref) => byNameParamEvaluator(ref, p, computeValue = false)
      case thisFromFrame(eval)             => eval
      case expr: ScExpression              =>
        val innerEval = expr match {
          case lit: ScLiteral                 => literalEvaluator(lit)
          case mc: ScMethodCall               => scMethodCallEvaluator(mc)
          case ref: ScReferenceExpression     => refExpressionEvaluator(ref)
          case t: ScThisReference             => thisOrSuperEvaluator(t.reference, isSuper = false)
          case t: ScSuperReference            => thisOrSuperEvaluator(t.reference, isSuper = true)
          case tuple: ScTuple                 => tupleEvaluator(tuple)
          case newTd: ScNewTemplateDefinition => newTemplateDefinitionEvaluator(newTd)
          case inf: ScInfixExpr               => infixExpressionEvaluator(inf)
          case ScParenthesisedExpr(inner)     => evaluatorFor(inner)
          case p: ScPrefixExpr                => prefixExprEvaluator(p)
          case p: ScPostfixExpr               => postfixExprEvaluator(p)
          case stmt: ScIf                     => ifStmtEvaluator(stmt)
          case ws: ScWhile                    => whileStmtEvaluator(ws)
          case doSt: ScDo                     => doStmtEvaluator(doSt)
          case block: ScBlock                 => blockExprEvaluator(block)
          case call: ScGenericCall            => methodCallEvaluator(call, Nil, Map.empty)
          case stmt: ScAssignment             => assignmentEvaluator(stmt)
          case stmt: ScTypedExpression        => evaluatorFor(stmt.expr)
          case e if e.textMatches("()")       => UnitEvaluator
          case e                              => throw EvaluationException(DebuggerBundle.message("evaluation.of.expression.is.not.supported", e.getText))
        }
        postProcessExpressionEvaluator(expr, innerEval)
      case pd: ScPatternDefinition         => patternDefinitionEvaluator(pd)
      case vd: ScVariableDefinition        => variableDefinitionEvaluator(vd)
      case e                               => throw EvaluationException(DebuggerBundle.message("evaluation.of.element.is.not.supported", e.getText))
    }
  }

  private def fragmentEvaluator(fragment: ScalaCodeFragment): Evaluator = {
    val childrenEvaluators = fragment.children.filter(!_.is[ScImportStmt]).collect {
      case e @ (_: ScBlockStatement | _: ScMember) => evaluatorFor(e)
    }
    new BlockStatementEvaluator(childrenEvaluators.toArray)
  }
}

private[evaluation] trait SyntheticVariablesHelper {
  private var currentHolder = new SyntheticVariablesHolderEvaluator(null)

  protected def withNewSyntheticVariablesHolder(evaluatorComputation: => Evaluator): Evaluator = {
    val old = currentHolder
    val newEvaluator = new SyntheticVariablesHolderEvaluator(currentHolder)
    currentHolder = newEvaluator
    var result: Evaluator = null
    try {
      result = evaluatorComputation
    }
    finally {
      currentHolder = old
    }
    result
  }

  protected def createSyntheticVariable(name: String): Unit = currentHolder.setInitialValue(name, null)
  protected def syntheticVariableEvaluator(name: String) = new SyntheticVariableEvaluator(currentHolder, name, null)
}

private object needsCompilation {
  def unapply(elem: PsiElement): Option[NlsString] = elem match {
    case m: ScMember => m match {
      case td: ScTemplateDefinition =>
        td match {
          case _: ScObject => Some(NlsString(DebuggerBundle.message("evaluation.of.object.needs.compilation")))
          case _: ScClass => Some(NlsString(DebuggerBundle.message("evaluation.of.class.needs.compilation")))
          case _: ScTrait => Some(NlsString(DebuggerBundle.message("evaluation.of.trait.needs.compilation")))
          case newTd: ScNewTemplateDefinition if AnonymousFunction.generatesAnonClass(newTd) =>
            Some(NlsString(DebuggerBundle.message("evaluation.of.anonymous.class.needs.compilation")))
          case _ => None
        }
      case _: ScTypeAlias => Some(NlsString(DebuggerBundle.message("evaluation.of.type.alias.needs.compilation")))
      case _: ScFunction => Some(NlsString(DebuggerBundle.message("evaluation.of.function.definition.needs.compilation")))
      case _: ScVariableDeclaration | _: ScValueDeclaration => Some(NlsString(DebuggerBundle.message("evaluation.of.variable.declaration.needs.compilation")))
      case LazyVal(_) => Some(NlsString(DebuggerBundle.message("evaluation.of.lazy.val.definition.needs.compilation")))
      case _ => None
    }
    case expr if AnonymousFunction.isGenerateAnonfun211(expr) => Some(NlsString(DebuggerBundle.message("evaluation.of.anonymous.function.needs.compilation")))
    case _: ScFor => Some(NlsString(DebuggerBundle.message("evaluation.of.for.expression.needs.compilation")))
    case _: ScTry => Some(NlsString(DebuggerBundle.message("evaluation.of.try.statement.needs.compilation")))
    case _: ScReturn => Some(NlsString(DebuggerBundle.message("evaluation.of.return.statement.needs.compilation")))
    case _: ScMatch => Some(NlsString(DebuggerBundle.message("evaluation.of.match.statement.needs.compilation")))
    case _: ScThrow => Some(NlsString(DebuggerBundle.message("evaluation.of.throw.statement.needs.compilation")))
    case _: ScXmlExpr => Some(NlsString(DebuggerBundle.message("evaluation.of.xml.expression.needs.compilation")))
    case _: ScInterpolatedStringLiteral => Some(NlsString(DebuggerBundle.message("evaluation.of.interpolated.string.needs.compilation")))
    case _ => None
  }
}

private object byNameParameterFunction {
  private val byNameFunctionSuffix = "_byNameFun"

  def unapply(ref: ScReferenceExpression): Option[(ScParameter, ScReferenceExpression)] = {
    if (ref.qualifier.isDefined) None
    else {
      val refText = ref.refName
      if (refText.endsWith(byNameFunctionSuffix)) {
        val paramName = refText.stripSuffix(byNameFunctionSuffix)
        createExpressionWithContextFromText(paramName, ref.getContext, ref) match {
          case (ref: ScReferenceExpression) & ResolvesTo(p: ScParameter) if p.isCallByNameParameter => Some((p, ref))
          case _ => throw EvaluationException(DebuggerBundle.message("cannot.find.by.name.parameter.with.such.name", paramName))
        }
      }
      else None
    }
  }
}

private object thisFromFrame {
  private val thisKey = "$this0"

  def unapply(ref: ScReferenceExpression): Option[Evaluator] = {
    if (ref.qualifier.isDefined) None
    else if (ref.refName == thisKey) Some(new ThisEvaluator())
    else None
  }
}
