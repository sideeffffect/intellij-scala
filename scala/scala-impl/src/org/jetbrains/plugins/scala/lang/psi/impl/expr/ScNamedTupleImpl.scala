package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNamedTuple
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

final class ScNamedTupleImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScNamedTuple {
  protected override def innerType: TypeResult = ???

  override def deleteChildInternal(child: ASTNode): Unit =
    ScalaPsiUtil.deleteElementInCommaSeparatedList(this, child)

  override def toString: String = "NamedTuple"
}
