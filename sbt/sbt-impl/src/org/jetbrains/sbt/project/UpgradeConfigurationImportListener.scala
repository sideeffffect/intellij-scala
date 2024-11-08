package org.jetbrains.sbt.project

import com.intellij.ide.impl.TrustedProjects
import com.intellij.notification.{Notification, NotificationAction, NotificationGroupManager, NotificationType}
import com.intellij.openapi.actionSystem.{ActionManager, AnActionEvent, CustomizedDataContext}
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.extensions.invokeWhenSmart
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.sbt.project.MigrateConfigurationsDialogWrapper.ModuleConfigurationExt
import org.jetbrains.sbt.project.SbtMigrateConfigurationsAction.{IsDowngradingFromSeparateMainTestModules, ModuleConfiguration, ModuleHeuristicResult, getConfigurationToHeuristicResult}
import org.jetbrains.sbt.{SbtBundle, SbtUtil}

/**
 * Project import listener created to detect whether a notification with upgrade configuration action should be displayed.
 * The notification is displayed only once for non-new sbt projects.
 */
class UpgradeConfigurationImportListener(project: Project) extends ProjectDataImportListener {

  private var separateProdTestSources: Boolean = _

  override def onImportStarted(projectPath: String): Unit =
    separateProdTestSources = getSeparateProdTestSourcesValue

  override def onImportFinished(projectPath: String): Unit = {
    val isTrustedProject = TrustedProjects.isTrusted(project)
    if (!(SbtUtil.isSbtProject(project) && isTrustedProject)) return

    // note: we need to wait until the project switches to smart mode before executing
    // this logic, because under the hood it calls JavaExecutionUtil#findMainClass
    // (from com.intellij.execution.configurations.JavaRunConfigurationModule.findNotNullClass),
    // which for a project in a dumb mode returns null so we can get incorrect results.
    invokeWhenSmart(project) {
      val newSeparateProdTestSourcesValue = getSeparateProdTestSourcesValue
      val separateProdTestSourcesChanged = newSeparateProdTestSourcesValue != separateProdTestSources
      val shouldUpdate = shouldUpdateRunConfigurations(project, separateProdTestSourcesChanged)
      if (shouldUpdate) {
        // If separate prod/test sources were enabled before the reload and are disabled now,
        // it means this feature has been switched off, indicating a downgrade.
        // For more details check org.jetbrains.sbt.project.SbtMigrateConfigurationsAction.IsDowngradingFromSeparateMainTestModules
        val isDowngrading = separateProdTestSources && !newSeparateProdTestSourcesValue
        val configToHeuristicResult = getConfigurationToHeuristicResult(project, Some(isDowngrading))
        /*
        The listener only updates the run configurations in advance and not whenever the user calls `SbtMigrateConfigurationsAction` because:
         1. The heuristic is less accurate when the user calls this action from all actions - since it's unclear what exactly happened;
            the user might want to adjust some configurations from the old grouping to the new one, or some configurations might need to be downgraded from main/test modules.
         2. If there is an issue with the heuristic, the user can call `SbtMigrateConfigurationsAction` explicitly and manually apply changes to the configuration modules.
         */
        val notModifiedConfigurations = applyHeuristicResultsIfPossible(configToHeuristicResult)
        val containsNonTemporaryConfigs = notModifiedConfigurations.exists { case (config, _) => !config.isTemporary }
        if (containsNonTemporaryConfigs) {
          showNotification(isDowngrading)
        }
      }
      setNotificationShown()
    }
  }

  // ScalaCompilerConfiguration.separateProdTestSources was initially created to record whether a project was imported
  // with separate modules for production and test, and to use this information when initializing ProjectSettingsImpl.
  // However, it is also useful here because in #onImportStarted, the old value of ScalaCompilerConfiguration.separateProdTestSources is read
  // (before it's updated in SbtProjectDataService.updateSeparateProdTestSources) and in #onImportFinished, the updated value is read.
  private def getSeparateProdTestSourcesValue: Boolean =
    ScalaCompilerConfiguration.instanceIn(project).separateProdTestSources

  private def isNewlyCreatedProject(project: Project): Boolean = {
    val isNew = project.getUserData(ExternalSystemDataKeys.NEWLY_CREATED_PROJECT)
    isNew != null && isNew
  }

  private def shouldUpdateRunConfigurations(project: Project, prodTestSourcesHasChanged: Boolean): Boolean = {
    val isNew = isNewlyCreatedProject(project)
    val isMigrateConfigurationsNotificationShown = ScalaProjectSettings.getInstance(project).isMigrateConfigurationsNotificationShown

    !isNew && (!isMigrateConfigurationsNotificationShown || prodTestSourcesHasChanged)
  }

  private def showNotification(isDowngradingFromSeparateMainTestModules: Boolean): Unit = {
    val notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("sbt.configuration.migration")
    val notification = notificationGroup
      .createNotification(SbtBundle.message("sbt.configuration.migration.notification.content"), NotificationType.WARNING)
      .addAction(new NotificationAction(SbtBundle.message("sbt.migrate.configurations.text")) {
        override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
          val action = ActionManager.getInstance.getAction(SbtMigrateConfigurationsAction.ID)
          val wrapper = CustomizedDataContext.withSnapshot(e.getDataContext, sink => {
            sink.set(IsDowngradingFromSeparateMainTestModules, isDowngradingFromSeparateMainTestModules)
          })
          action.actionPerformed(e.withDataContext(wrapper))
          // It will close the notification when the user completes the action (either migrates the configurations or closes the dialog).
          notification.expire()
        }
      })

    val ignoreAction = NotificationAction.createSimpleExpiring(SbtBundle.message("sbt.configuration.migration.notification.ignore.text"), () => notification.expire())
    notification.addAction(ignoreAction)

    notification.notify(project)
  }

  private def setNotificationShown(): Unit =
    ScalaProjectSettings.getInstance(project).setMigrateConfigurationsNotificationShown(true)

  /**
   * Modifies modules in configurations if the heuristic identifies a single suitable module.
   *
   * @return the rest of configurations that couldn't be updated
   */
  private def applyHeuristicResultsIfPossible(
    configToHeuristicResult: Seq[(ModuleConfiguration, ModuleHeuristicResult)]
  ): Seq[(ModuleConfiguration, ModuleHeuristicResult)] =
     configToHeuristicResult.filter { case (config, ModuleHeuristicResult(moduleOpt, _)) =>
      moduleOpt match {
        case Some(module) =>
          config.setModule(module)
          false
        case _ => true
      }
    }
}
