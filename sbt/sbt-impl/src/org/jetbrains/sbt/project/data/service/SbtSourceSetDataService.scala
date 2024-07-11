package org.jetbrains.sbt.project.data.service

import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.model.{DataNode, Key}
import com.intellij.openapi.module.Module

import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.sbt.project.data.service.SbtSourceSetDataService.sbtSourceSetModuleType
import org.jetbrains.sbt.project.module.SbtSourceSetData

class SbtSourceSetDataService extends AbstractSbtModuleDataService[SbtSourceSetData] {

  override def getTargetDataKey: Key[SbtSourceSetData] = SbtSourceSetData.Key

  override protected def moduleType: String = sbtSourceSetModuleType


  override protected def generateNewName(
    parentModule: Module,
    data: SbtSourceSetData,
    parentModuleActualName: String
  ): Option[String] =
    Some(s"$parentModuleActualName.${data.getModuleName}")
}

object SbtSourceSetDataService {
  // if the value of this field is modified, then org.jetbrains.jps.incremental.scala.model.impl.JpsSbtModuleExtensionImpl.SbtSourceSetModuleTypeKey should be updated
  @VisibleForTesting
  val sbtSourceSetModuleType = "sbtSourceSet"
}
