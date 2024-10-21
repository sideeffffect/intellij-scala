package org.jetbrains.plugins.scala.lang.psi.impl.base.patterns

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScNamedTuplePattern
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

class ScNamedTuplePatternImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScPatternImpl with ScNamedTuplePattern {

  override def isIrrefutableFor(t: Option[ScType]): Boolean = ???

  override def `type`(): TypeResult = ???

  override def toString: String = "NamedTuplePattern"
}
