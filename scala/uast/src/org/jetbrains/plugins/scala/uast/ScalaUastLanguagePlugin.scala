package org.jetbrains.plugins.scala
package uast

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher
import com.intellij.psi.{PsiElement, PsiMethod}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.EditorArea.isVisible
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.uast.converter.Scala2UastConverter._
import org.jetbrains.plugins.scala.lang.psi.uast.utils.NotNothing
import org.jetbrains.uast._
import org.jetbrains.uast.util.{ClassSet, ClassSetsWrapper}

import scala.language.postfixOps

/**
 * [[UastLanguagePlugin]] implementation for the Scala plugin.
 */
final class ScalaUastLanguagePlugin extends UastLanguagePlugin {

  import UastLanguagePlugin._

  private val fileNameMatcher = new ExtensionFileNameMatcher(ScalaFileType.INSTANCE.getDefaultExtension)

  override def getLanguage: Language =
    if (isEnabled) ScalaLanguage.INSTANCE else DummyDialect

  override def getPriority: Int = 10

  override def isFileSupported(fileName: String): Boolean =
    isEnabled && fileNameMatcher.acceptsCharSequence(fileName)

  @Nullable
  override def convertElement(element: PsiElement,
                              @Nullable parent: UElement,
                              @Nullable requiredType: Class[_ <: UElement]): UElement = {

    if (!isVisible(element)) return null

    convertTo(element, parent)(
      toClassTag(requiredType),
      implicitly[NotNothing[UElement]]
    ).orNull
  }

  @Nullable
  override def convertElementWithParent(element: PsiElement,
                                        @Nullable requiredType: Class[_ <: UElement]): UElement = {

    if (!isVisible(element)) return null

    convertWithParentTo(element)(
      toClassTag(requiredType),
      implicitly[NotNothing[UElement]]
    ).orNull
  }

  // TODO:
  //  - add lazy vals where possible

  @Nullable
  override def getConstructorCallExpression(element: PsiElement,
                                            fqName: String): ResolvedConstructor = {

    if (!isVisible(element)) return null

    element match {
      case constructorCall: ScConstructorInvocation
        if constructorCall.typeElement
          .`type`()
          .exists(_.canonicalText == fqName) =>
        val resolvedConstructor =
          for {
            callExpression <- convertWithParentTo[UCallExpression](element)
            constructorMethod <- constructorCall.reference.map(
              _.resolveTo[PsiMethod]
            )
            containingClass <- Option(constructorMethod.getContainingClass)
            if containingClass.getQualifiedName == fqName
          } yield
            new ResolvedConstructor(
              callExpression,
              constructorMethod,
              containingClass
            )

        resolvedConstructor.orNull

      case _ => null
    }
  }

  @Nullable
  override def getMethodCallExpression(element: PsiElement,
                                       containingClassFqName: String,
                                       methodName: String): ResolvedMethod = {

    if (!isVisible(element)) return null

    element match {
      case methodCall: ScMethodCall
        if Option(methodCall.getInvokedExpr).exists {
          case ref: ScReference => ref.refName == methodName
          case _ => false
        } =>
        val callExpression = convertElementWithParent(methodCall, null) match {
          case callExpr: UCallExpression => callExpr
          case qualifiedRef: UQualifiedReferenceExpression
            if qualifiedRef.getSelector.is[UCallExpression] =>
            qualifiedRef.getSelector.asInstanceOf[UCallExpression]
          case otherwise => sys.error(s"Invalid element type: $otherwise")
        }

        Option(callExpression.resolve())
          .filter { method =>
            if (containingClassFqName != null) {
              val containingClass = method.getContainingClass
              containingClass != null && containingClass.getQualifiedName == containingClassFqName
            } else true
          }
          .map(new ResolvedMethod(callExpression, _))
          .orNull
      case _ => null
    }
  }

  override def isExpressionValueUsed(uExpression: UExpression): Boolean =
    throw new NotImplementedError // TODO: not implemented

  override def getPossiblePsiSourceTypes(uastTypes: Class[_ <: UElement]*): ClassSet[PsiElement] = {
    import ScalaUastSourceTypeMapping.possibleSourceTypes
    uastTypes match {
      case Seq() => possibleSourceTypes(classOf[UElement])
      case Seq(one) => possibleSourceTypes(one)
      case multiple => new ClassSetsWrapper[PsiElement](multiple.map(possibleSourceTypes).toArray)
    }
  }
}
