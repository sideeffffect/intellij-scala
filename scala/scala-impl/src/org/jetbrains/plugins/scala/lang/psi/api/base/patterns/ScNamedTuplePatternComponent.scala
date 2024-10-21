package org.jetbrains.plugins.scala.lang.psi.api.base.patterns

import org.jetbrains.plugins.scala.lang.psi.api.base.ScNamedTupleComponent

trait ScNamedTuplePatternComponent extends ScNamedTupleComponent {
  final def namedTuplePattern: ScNamedTuplePattern = getParent.asInstanceOf[ScNamedTuplePattern]
  def subPattern: Option[ScPattern] = findChild[ScPattern]
}
