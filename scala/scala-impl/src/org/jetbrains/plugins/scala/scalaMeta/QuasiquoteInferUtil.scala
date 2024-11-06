package org.jetbrains.plugins.scala.scalaMeta

import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.base.patterns.ScInterpolationPatternImpl
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

trait QuasiquoteInferUtil {
  def isMetaQQ(ref: ScReference): Boolean
  def isMetaQQ(fun: ScFunction): Boolean
  def getMetaQQExpectedTypes(srr: ScalaResolveResult, stringContextApplicationRef: ScReferenceExpression): Seq[Parameter]
  def getMetaQQExprType(pat: ScInterpolatedStringLiteral): TypeResult
  def getMetaQQPatternTypes(pat: ScInterpolationPatternImpl): Seq[String]
}

object QuasiquoteInferUtil extends QuasiquoteInferUtil {
  private lazy val impl = ApplicationManager.getApplication.getService(classOf[QuasiquoteInferUtil])

  override def isMetaQQ(ref: ScReference): Boolean = impl.isMetaQQ(ref)
  override def isMetaQQ(fun: ScFunction): Boolean = impl.isMetaQQ(fun)
  override def getMetaQQExpectedTypes(srr: ScalaResolveResult, stringContextApplicationRef: ScReferenceExpression): Seq[Parameter] =
    impl.getMetaQQExpectedTypes(srr, stringContextApplicationRef)
  override def getMetaQQExprType(pat: ScInterpolatedStringLiteral): TypeResult =
    impl.getMetaQQExprType(pat)
  override def getMetaQQPatternTypes(pat: ScInterpolationPatternImpl): Seq[String] =
    impl.getMetaQQPatternTypes(pat)
}