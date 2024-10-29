package org.jetbrains.sbt.project.extensionPoints

import com.intellij.execution.configurations.ModuleBasedConfiguration
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.plugins.scala.ExtensionPointDeclaration

@Internal
trait ModuleBasedConfigurationDetailsExtractor {
  def getConfigurationMainClass(config: ModuleBasedConfiguration[_, _]): Option[String]
}

object ModuleBasedConfigurationDetailsExtractor
  extends ExtensionPointDeclaration[ModuleBasedConfigurationDetailsExtractor]("com.intellij.sbt.configurationDetailsExtractor") {

  def getMainClass(config: ModuleBasedConfiguration[_, _]): Option[String] =
    implementations
      .map(_.getConfigurationMainClass(config))
      .collectFirst { case Some(result) => result }

}
