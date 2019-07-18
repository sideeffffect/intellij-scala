package org.jetbrains.plugins.scala.console.actions

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, CommonDataKeys}
import com.intellij.openapi.project.DumbAware
import org.jetbrains.plugins.scala.console.ScalaConsoleInfo

class ScalaConsoleCleanAction extends AnAction with DumbAware {

  override def actionPerformed(e: AnActionEvent): Unit = {
    val editor = e.getData(CommonDataKeys.EDITOR)
    if(editor == null || !ScalaConsoleInfo.isConsole(editor)) return

    val console = ScalaConsoleInfo.getConsole(editor)
    if(console == null) return

    console.clear()
  }
}
