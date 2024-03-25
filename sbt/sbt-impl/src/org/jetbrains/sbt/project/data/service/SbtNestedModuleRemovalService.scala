package org.jetbrains.sbt.project.data.service

import com.intellij.openapi.externalSystem.model.project.{ModuleData, ProjectData}
import com.intellij.openapi.externalSystem.model.{DataNode, Key, ProjectKeys}
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractModuleDataService
import com.intellij.openapi.externalSystem.util.{ExternalSystemConstants, Order}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import org.jetbrains.sbt.project.data.computeOrphanDataForModuleType

import java.util

/**
 * The purpose of this service is to remove modules whose external module type is <code>nestedProject</code>.
 * Modules of this type have been introduced since version 2024.1.
 */
@Order(ExternalSystemConstants.BUILTIN_MODULE_DATA_SERVICE_ORDER + 1)
class SbtNestedModuleRemovalService extends AbstractModuleDataService[ModuleData]{

  override def getTargetDataKey: Key[ModuleData] = ProjectKeys.MODULE

  private val SbtNestedModuleType = "nestedProject"

  override def computeOrphanData(
    toImport: util.Collection[_ <: DataNode[ModuleData]],
    projectData: ProjectData,
    project: Project,
    modelsProvider: IdeModifiableModelsProvider
  ): Computable[util.Collection[Module]] =
    computeOrphanDataForModuleType(SbtNestedModuleType, projectData, modelsProvider)

}
