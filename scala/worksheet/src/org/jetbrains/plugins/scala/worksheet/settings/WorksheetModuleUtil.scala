package org.jetbrains.plugins.scala.worksheet.settings

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.sbt.SbtUtil

private object WorksheetModuleUtil {
  def allProductionModulesWithScalaSdk(project: Project): Seq[Module] = {
    val separate = SbtUtil.isBuiltWithSeparateModulesForProdTest(project)
    val allScalaModules = project.modulesWithScala
    // If the project has separate production and test modules enabled, only offer the production modules as a choice.
    if (separate) allScalaModules.filter(_.getName.endsWith(".main")) else allScalaModules
  }
}
