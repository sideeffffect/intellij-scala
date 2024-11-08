package org.jetbrains.sbt.project

import com.intellij.application.options.ModulesComboBox
import com.intellij.execution.RunManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.uiDesigner.core.{GridConstraints, GridLayoutManager}

import java.awt.{Component, Dimension}
import javax.swing._
import javax.swing.table.{DefaultTableCellRenderer, DefaultTableModel, TableCellEditor}
import com.intellij.execution.configurations.ModuleBasedConfiguration
import com.intellij.icons.AllIcons
import com.intellij.openapi.module.Module
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.{LayeredIcon, SimpleTextAttributes, SortedComboBoxModel}
import com.intellij.util.ui.EmptyIcon
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.project.MigrateConfigurationsDialogWrapper.{TableCellRendererWithLeftMargin, isTemporaryConfig}
import org.jetbrains.sbt.project.SbtMigrateConfigurationsAction.{ModuleConfiguration, ModuleHeuristicResult}

import java.awt.event.MouseEvent
import java.util.Comparator
import scala.jdk.CollectionConverters.IterableHasAsJava

class MigrateConfigurationsDialogWrapper(modules: Seq[Module], configurationToModule: Map[ModuleConfiguration, ModuleHeuristicResult]) extends DialogWrapper(true) {

  private val jModules = modules.asJavaCollection
  private val defaultModulesComboBoxText = SbtBundle.message("sbt.migrate.configurations.dialog.wrapper.default")

  // It provides different cell editors for each cell with ModulesComboBox,
  // allowing custom sorting in each cell to keep suggested modules on top.
  private val configToComboBoxCellEditor = configurationToModule.view.mapValues { heuristicResult =>
    createComboBoxCellEditor(heuristicResult.guesses)
  }.toMap

  private case class ConfigDisplayOptions(isTemporary: Boolean, isShared: Boolean)

  private object ConfigDisplayOptions {
    def apply(config: ModuleConfiguration): ConfigDisplayOptions  =
      ConfigDisplayOptions(
        isTemporary = isTemporaryConfig(config),
        isShared = isShared(config)
      )

    private def isShared(config: ModuleConfiguration): Boolean = {
      val project = config.getProject
      val settings = RunManager.getInstance(project).findSettings(config)
      Option(settings).exists(_.isShared)
    }
  }

  private val configToDisplayOptions = configurationToModule.keys.map { config => config -> ConfigDisplayOptions(config) }.toMap

  private def createComboBoxCellEditor(namesToKeepOnTop: Seq[String]): DefaultCellEditor = {
    // Creating a SortedComboBoxModel in this way and injecting it via javax.swing.JComboBox.setModel is a workaround,
    // since ModulesComboBox does not directly provide a possibility to modify the default model using ModulesAlphaComparator.
    val customModel = new SortedComboBoxModel(jModules, new ModulesPrioritizedComparator(namesToKeepOnTop))
    val comboBox = new ModulesComboBox()
    comboBox.setModel(customModel)
    comboBox.setModules(jModules)
    comboBox.allowEmptySelection(defaultModulesComboBoxText)
    new DefaultCellEditor(comboBox)
  }

  private val myTable = new JBTable() {
    override def getCellEditor(row: Int, column: Int): TableCellEditor = {
      lazy val defaultCellEditor = super.getCellEditor(row, column)
      if (column == 2) getCellEditorForModulesComboBoxColumn(row, defaultCellEditor)
      else defaultCellEditor
    }

    private def getCellEditorForModulesComboBoxColumn(row: Int, defaultValue: => TableCellEditor): TableCellEditor = {
      val configurationOpt = findConfigInRow(row)
      configurationOpt match {
        case Some(config) => configToComboBoxCellEditor.getOrElse(config, defaultValue)
        case None => defaultValue
      }
    }

    override def getToolTipText(event: MouseEvent): String = {
      // TODO The tooltip doesn't work when the user just clicks on it.
      //  In order for a tooltip to appear, the user has to move the mouse a little after clicking on it.
      val row = rowAtPoint(event.getPoint)
      val configurationOpt = findConfigInRow(row)
      val guesses = configurationOpt.flatMap(configurationToModule.get).map(_.guesses).getOrElse(Nil)
      if (guesses.nonEmpty) {
        guesses.mkString("Suggested modules: ", ", ", "")
      } else {
        super.getToolTipText(event)
      }
    }
  }

  private val myTableModel = new DefaultTableModel(Array[AnyRef]("Configuration", "Module name in previous scheme", "New module"), 0) {
    override def isCellEditable(row: Int, column: Int): Boolean = column != 0 && column != 1
  }

  private var isClosed: Boolean = false

  locally {
    setTitle(SbtBundle.message("sbt.migrate.configurations.dialog.wrapper.title"))
    myTable.setModel(myTableModel)
    myTable.setShowGrid(false)
    myTable.setRowHeight(JBUIScale.scale(22))
    setModal(true)
    init()
  }

  override def createCenterPanel(): JComponent = {
    setUpTableHeaderRenderer()
    setUpConfigurationColumn()
    setUpPreviousModuleNameColumn()
    setUpSelectingModulesColumn()

    // Sort the configurations first alphabetically by type,
    // and then by whether they are temporary, placing temporary configurations last.
    val sorted = configurationToModule.toSeq.sortBy { case (config, _) =>
      val configTypeName = config.getType.getDisplayName
      (configTypeName, isTemporary(config))
    }
    sorted.foreach { case (config, ModuleHeuristicResult(module, _)) =>
      val moduleName = config.getConfigurationModule.getModuleName
      val row: Array[AnyRef] = module match {
        case Some(module) => Array(config, moduleName, module)
        case _ => Array(config, moduleName)
      }
      myTableModel.addRow(row)
    }

    getOKAction.setEnabled(true)

    val scrollPane = new JBScrollPane
    scrollPane.setViewportView(myTable)
    val panel = new JPanel
    panel.setLayout(new GridLayoutManager(1, 1))
    panel.add(scrollPane, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(600, 300), null, 0, false))
    panel
  }

  override def doCancelAction(): Unit = {
    super.doCancelAction()
    isClosed = true
    dispose()
  }

  override def doOKAction(): Unit = {
    super.doOKAction()
    dispose()
  }

  private def getValueAt[T](row: Integer, column: Integer): Option[T] =
    Option(myTableModel.getValueAt(row, column)).map(_.asInstanceOf[T])

  private def setUpTableHeaderRenderer(): Unit =
    myTable.getTableHeader.setDefaultRenderer(new TableCellRendererWithLeftMargin)

  private def setUpSelectingModulesColumn(): Unit = {
    val columnModel = myTable.getColumnModel.getColumn(2)
    columnModel.setCellRenderer(new ModuleComboBoxColumnCellRenderer(defaultModulesComboBoxText))
  }

  private def isTemporary(config: ModuleConfiguration): Boolean =
    configToDisplayOptions.get(config).exists(_.isTemporary)

  private def isShared(config: ModuleConfiguration): Boolean =
    configToDisplayOptions.get(config).exists(_.isShared)

  private def setUpConfigurationColumn(): Unit = {
    val columnModel = myTable.getColumnModel.getColumn(0)
    columnModel.setCellRenderer(new TableCellRendererWithLeftMargin {
      override def setValue(value: Any): Unit = {
        value match {
          case config: ModuleConfiguration =>
            // The logic of where to put the shared icon,
            // and the gray foreground was taken from com.intellij.execution.impl.RunConfigurableTreeRenderer
            val secondLayerIcon = if (isShared(config)) AllIcons.Nodes.Shared else EmptyIcon.ICON_16
            setIcon(LayeredIcon.layeredIcon(Array(config.getType.getIcon, secondLayerIcon)))

            setText(config.getName)

            val fg = if (isTemporary(config)) SimpleTextAttributes.GRAY_ATTRIBUTES.getFgColor else null
            setForeground(fg)
          case _ => setForeground(null)
        }
      }
    })
  }

  private def setUpPreviousModuleNameColumn(): Unit = {
    // note: it is necessary because column name "Module name in previous scheme" is too long
    val columnModel = myTable.getColumnModel.getColumn(1)
    columnModel.setPreferredWidth(150)
    columnModel.setCellRenderer(new TableCellRendererWithLeftMargin)
  }

  def open(): Map[ModuleBasedConfiguration[_, _], Module] = {
    pack()
    show()
    if (!isClosed) getCurrentTableState
    else Map.empty
  }

  private def getCurrentTableState: Map[ModuleBasedConfiguration[_, _], Module] = {
    val rowsCount = myTableModel.getRowCount
    (0 until rowsCount)
      .map(createConfigModuleTuple)
      .collect { case (Some(config), Some(module)) => config -> module }
      .toMap
  }

  private def createConfigModuleTuple(row: Int): (Option[ModuleConfiguration], Option[Module]) = {
    val config = getValueAt[ModuleConfiguration](row, 0)
    val module = getValueAt[Module](row, 2)
    (config, module)
  }

  private def findConfigInRow(row: Integer): Option[ModuleConfiguration] = {
    val isRowWithinRange = row >= 0 && row < configurationToModule.size
    if (isRowWithinRange) getValueAt[ModuleConfiguration](row, 0)
    else None
  }
}

object MigrateConfigurationsDialogWrapper {

  def isTemporaryConfig(config: ModuleConfiguration): Boolean = {
    val project = config.getProject
    val settings = RunManager.getInstance(project).findSettings(config)
    Option(settings).exists(_.isTemporary)
  }

  /**
   * Adding a left margin is necessary for the last column with the <code>ModulesComboBox</code>,
   * so to maintain the table consistency it is added to all columns. <p>
   * This is because when the cell containing the <code>ModulesComboBox</code> is active (the user clicks on it), the ModulesComboBox is responsible for displaying the cell's values,
   * and it has a margin. Without adding a border, the value's position in the cell would shift depending on whether it is active or not.
   */
  class TableCellRendererWithLeftMargin extends DefaultTableCellRenderer {
    override def getTableCellRendererComponent(table: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component = {
      val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
      setBorder(com.intellij.util.ui.JBUI.Borders.emptyLeft(12))
      component
    }
  }
}
/**
 * A comparator for modules that prioritizes specified modules before
 * performing a case-insensitive alphabetical comparison based on their names.
 *
 * @param prioritizedElements a sequence of module names to prioritize.
 */
final class ModulesPrioritizedComparator(prioritizedElements: Seq[String]) extends Comparator[Module] {

  override def compare(module1: Module, module2: Module): Int = {
    // this part is taken from com.intellij.openapi.roots.ui.configuration.ModulesAlphaComparator
    if (module1 == null && module2 == null) return 0
    if (module1 == null) return -1
    if (module2 == null) return 1

    val module1Name = module1.getName
    val module2Name = module2.getName

    (prioritizedElements.contains(module1Name), prioritizedElements.contains(module2Name)) match {
      case (true, false) => -1
      case (false, true) => 1
      case _  => module1Name.compareToIgnoreCase(module2Name)
    }
  }
}

