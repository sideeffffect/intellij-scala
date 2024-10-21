package org.jetbrains.plugins.scala.lang.psi.api.base

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable

/**
 * Either a ScNamedTupleExprComponent or a ScNamedTupleTypeComponent or a ScNamedTuplePatternComponent
 */
trait ScNamedTupleComponent extends ScNamedElement with Typeable {
  def nameElement: Option[PsiElement]
}
