package org.jetbrains.sbt.project.template.wizard

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.ui.components.JBCheckBox
import org.jetbrains.plugins.scala.extensions.applyTo
import org.jetbrains.plugins.scala.isUnitTestMode
import org.jetbrains.plugins.scala.project.Versions
import org.jetbrains.plugins.scala.project.template.ScalaVersionDownloadingDialog
import org.jetbrains.plugins.scala.util.AsynchronousVersionsDownloading
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.project.template.{SComboBox, ScalaModuleBuilderSelections}

import java.util.concurrent.atomic.AtomicBoolean
import scala.collection.immutable.ListSet

trait ScalaVersionStepLike extends AsynchronousVersionsDownloading {

  protected def selections: ScalaModuleBuilderSelections

  protected val defaultAvailableScalaVersions: Versions

  private val isScalaVersionManuallySelected: AtomicBoolean = new AtomicBoolean(false)

  private val isScalaLoading = new AtomicBoolean(false)
  protected lazy val scalaVersionComboBox: SComboBox[String] = createSComboBoxWithSearchingListRenderer(ListSet(defaultAvailableScalaVersions.versions: _*), None, isScalaLoading)

  private def downloadScalaVersions(disposable: Disposable): Unit = {
    val scalaDownloadVersions: ProgressIndicator => Versions = indicator => {
      Versions.Scala.loadVersionsWithProgress(indicator)
    }
    downloadVersionsAsynchronously(isScalaLoading, disposable, scalaDownloadVersions, Versions.Scala.toString) { v =>
      updateSelectionsAndElementsModelForScala(v)
    }
  }

  protected val scalaLabelText: String = SbtBundle.message("sbt.settings.scala")

  protected val downloadScalaSourcesCheckbox: JBCheckBox = applyTo(new JBCheckBox(SbtBundle.message("sbt.module.step.download.sources")))(
    _.setToolTipText(SbtBundle.message("sbt.download.scala.standard.library.sources"))
  )

  /**
   * Initializes selections and UI elements only once
   */
  protected def initSelectionsAndUi(contextDisposable: Disposable): Unit = {
    _initSelectionsAndUi
    if (!isUnitTestMode) {
      downloadScalaVersions(contextDisposable)
    }
  }

  private lazy val _initSelectionsAndUi: Unit = {
    selections.updateScalaVersion(defaultAvailableScalaVersions)

    initUiElementsModel()
    initUiElementsListeners()
  }


  private def updateSelectionsAndElementsModelForScala(scalaVersions: Versions): Unit = {
    if (!isScalaVersionManuallySelected.get()) {
      selections.scalaVersion = None
      selections.updateScalaVersion(scalaVersions)
    }
    scalaVersionComboBox.updateComboBoxModel(scalaVersions.versions.toArray, selections.scalaVersion)
    initSelectedScalaVersion(scalaVersions)
  }

  private def initUiElementsModel(): Unit = {
    initUiElementsModelFrom(selections)
    initSelectedScalaVersion(defaultAvailableScalaVersions)
  }

  private def initUiElementsModelFrom(selections: ScalaModuleBuilderSelections): Unit = {
    scalaVersionComboBox.setSelectedItemSafe(selections.scalaVersion.orNull)
    downloadScalaSourcesCheckbox.setSelected(selections.downloadScalaSdkSources)
  }

  /**
   * Init UI --> Selections binding
   */
  private def initUiElementsListeners(): Unit = {
    scalaVersionComboBox.addActionListener { _ =>
      isScalaVersionManuallySelected.set(true)
      selections.scalaVersion = scalaVersionComboBox.getSelectedItemTyped
    }

    downloadScalaSourcesCheckbox.addChangeListener(_ =>
      selections.downloadScalaSdkSources = downloadScalaSourcesCheckbox.isSelected
    )
  }

  private def initSelectedScalaVersion(scalaVersions: Versions): Unit = {
    selections.scalaVersion match {
      case Some(version) if scalaVersions.versions.contains(version) =>
        scalaVersionComboBox.setSelectedItemSafe(version)

        if (selections.scrollScalaVersionDropdownToTheTop) {
          ScalaVersionDownloadingDialog.UiUtils.scrollToTheTop(scalaVersionComboBox)
        }
      case _ if scalaVersionComboBox.getItemCount > 0 =>
        scalaVersionComboBox.setSelectedIndex(0)
      case _ =>
    }
  }
}
