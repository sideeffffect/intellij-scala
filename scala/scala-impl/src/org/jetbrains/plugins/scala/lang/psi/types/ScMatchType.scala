package org.jetbrains.plugins.scala.lang.psi.types

import org.jetbrains.plugins.scala.lang.psi.types.api.ValueType
import org.jetbrains.plugins.scala.project.ProjectContext

final class ScMatchType private (val scrutinee: ScType, val cases: Seq[(ScType, ScType)], val upperBound: Option[ScType]) extends ScalaType with ValueType {
  override def visitType(visitor: ScalaTypeVisitor): Unit = visitor.visitMatchType(this)

  override implicit def projectContext: ProjectContext = scrutinee.projectContext
}

object ScMatchType {
  def apply(scrutinee: ScType, cases: Seq[(ScType, ScType)], upperBound: Option[ScType] = None) = new ScMatchType(scrutinee, cases, upperBound)

  def unapply(mt: ScMatchType): Some[(ScType, Seq[(ScType, ScType)])] = Some((mt.scrutinee, mt.cases))
}
