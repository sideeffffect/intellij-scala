package org.jetbrains.plugins.scala.lang.psi.api.statements

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.psi.{PsiClass, PsiNamedElement, PsiTypeParameter}
import com.intellij.util.containers.{ConcurrentLongObjectMap, ContainerUtil}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiClassExt, PsiElementExt, PsiNamedElementExt, StubBasedExt}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.types.api.{TypeParameter, TypeParameterType}

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import scala.language.implicitConversions

package object params {
  private val typeParameterCounter = new AtomicLong(0)
  private val reusableIdMap = new ConcurrentHashMap[String, Long]()

  private val paramToIdMap = ContainerUtil.createConcurrentWeakMap[PsiNamedElement, Long]()

  //for better debugging, cleared by ScalaPsiManager on every change
  val idToName: ConcurrentLongObjectMap[String] =
    ConcurrentCollectionFactory.createConcurrentLongObjectMap()

  //never cleared
  private val reusableIdToName: ConcurrentLongObjectMap[String] =
    ConcurrentCollectionFactory.createConcurrentLongObjectMap()

  def typeParamName(id: Long): String = {
    idToName.get(id).toOption
      .getOrElse(reusableIdToName.get(id)).toOption
      .getOrElse(id.toString)
  }

  private val nameBasedIdBaseline = Long.MaxValue / 2

  private def elementQual(element: ScalaPsiElement): String =
    element match {
      case t: ScTypeParam => elementQual(t.owner) + "#" + t.name
      case c: PsiClass    => c.qualifiedName
      case f: ScFunction  =>
        val indexInParent = f.withGreenStub(
          stub => stub.getParentStub.getChildrenStubs.indexOf(stub),
          () => 0
        )
        elementQual(f.containingClass) + ".." + indexInParent
      case _              => ""
    }

  def freshTypeParamId(element: PsiNamedElement): Long = {
    val id = typeParameterCounter.getAndIncrement()
    idToName.put(id, element.name)
    id
  }

  def reusableId(typeParameter: ScTypeParam): Long = typeParameter.containingFile match {
    case Some(file: ScalaFile) if file.isCompiled =>
      val qualifier = elementQual(typeParameter)
      val id = reusableIdMap.computeIfAbsent(qualifier, _ => freshTypeParamId(typeParameter))
      reusableIdToName.put(id, typeParameter.name)
      id
    case _ => freshTypeParamId(typeParameter)
  }

  private def cachedId(element: PsiNamedElement): Long = paramToIdMap.computeIfAbsent(element, freshTypeParamId(_))

  implicit class TypeParamIdOwner[T](private val t: T) extends AnyVal {
    def typeParamId(implicit ev: TypeParamId[T]): Long = ev.typeParamId(t)
  }

  trait TypeParamId[-T] {
    def typeParamId(t: T): Long
  }

  object TypeParamId {
    implicit val psi: TypeParamId[PsiTypeParameter] = {
      case sc: ScTypeParam => sc.typeParamId
      case null            => -1
      case p               => cachedId(p)
    }

    implicit val typeParam: TypeParamId[TypeParameter] = t => psi.typeParamId(t.psiTypeParameter)

    implicit val typeParamType: TypeParamId[TypeParameterType] = t => psi.typeParamId(t.psiTypeParameter)

    implicit val long: TypeParamId[Long] = identity(_)

    //I'd rather avoid implicit usages of this one
    val nameBased: TypeParamId[String] = name => nameBasedIdBaseline + name.hashCode
  }

}
