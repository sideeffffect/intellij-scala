package org.jetbrains.plugins.scala.actions

import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.fileTemplates.impl.FileTemplateManagerImpl
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase

abstract class ScalaFileTemplateTestBase extends ScalaLightCodeInsightFixtureTestCase {

  /**
   * Creates a template called "File Header.java" which is references in all scala file templates
   *
   * @todo why the java header is used in Scala? It's also used in Kotlin.
   *       It seems some legacy thing.
   *       Ideally there should be separate headers for Scala and Kotlin.
   *       But due to it existed for > 15 years now it seems like no one cares and it's probably not a widely used feature
   */
  protected def initFileHeaderTemplate(isEmpty: Boolean = true): Unit = {
    val templateManager = FileTemplateManager.getInstance(getProject).asInstanceOf[FileTemplateManagerImpl]
    val templateText =
      if (isEmpty) ""
      else
        """
          |/**
          | * Created by ${USER} on ${DATE}.
          | */
          |""".stripMargin

    templateManager.setDefaultFileIncludeTemplateTextTemporarilyForTest(
      FileTemplateManager.FILE_HEADER_TEMPLATE_NAME,
      templateText,
      getTestRootDisposable
    )
  }
}
