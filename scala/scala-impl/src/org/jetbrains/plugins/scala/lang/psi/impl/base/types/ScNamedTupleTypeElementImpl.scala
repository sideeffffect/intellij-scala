package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScNamedTupleTypeElement
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

class ScNamedTupleTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScNamedTupleTypeElement {
  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitNamedTupleTypeElement(this)
  }

  override protected def innerType: TypeResult = ???
}
