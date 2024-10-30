package org.jetbrains.sbt.project

import org.jetbrains.sbt.project.SbtMigrateConfigurationsAction.ConfigDetails
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested

import java.util.stream.Stream
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.{Arguments, MethodSource}

class SbtMigrateConfigurationsActionTest {

  // If separate modules for prod/test are enabled, then only main/test are passed to SbtMigrateConfigurationsAction#doFindModulesForConfig
  private val moduleNames_ProdTestSourcesEnabled = Seq(
    "root.main", "root.test",
    "root.foo.main", "root.foo.test",
    "root.foo.dummy.main", "root.foo.dummy.test",
    "root~1.main", "root~1.test",
    "root~1.foo.main", "root~1.foo.test"
  )

  private val moduleNames_ProdTestSourcesDisabled = Seq("root", "root.foo", "root.foo.dummy", "root~1", "root~1.foo")

  /**
   * Test the combination of two heuristic results:
   *  1. Finding suitable modules based on their names.
   *  1. Finding suitable modules based on the existence of main classes.
   */
  @ParameterizedTest
  @MethodSource(Array("org.jetbrains.sbt.project.SbtMigrateConfigurationsActionTest#moduleNameAndMainClassHeuristicsConsistency"))
  def testModuleNameAndMainClassHeuristicsConsistency(configDetails: ConfigDetails, expectedResult: Seq[String]): Unit = {
    val moduleNameToClassesInside = Map (
      "root~1.foo.test" -> Seq("Main1"),
      "root.foo.dummy.test" -> Seq("Main3"),
      "root~1.main" -> Seq("Main4"),
      "root~1.test"-> Seq("Main4"),
    )
    execute(
      configDetails,
      moduleNames_ProdTestSourcesEnabled,
      prodTestSourcesSeparated = true,
      isDowngradingFromSeparateMainTestModules = Some(false),
      expectedResult,
      moduleNameToClassesInside
    )
  }

  /**
   * Tests inside this class are focused on testing [[org.jetbrains.sbt.project.SbtMigrateConfigurationsAction.isConfigurationCompatibleWithModuleName]],
   * which is responsible for finding suitable modules based on their names.
   */
  @Nested
  class FindingSuitableModulesBasedOnTheirNamesTest {

    @ParameterizedTest
    @MethodSource(Array("org.jetbrains.sbt.project.SbtMigrateConfigurationsActionTest#prodTestEnabled_isDowngradingNoneOrFalse"))
    def testSeparateModulesForProdTestEnabled_isDowngradingNoneOrFalse(configDetails: ConfigDetails, expectedResult: Seq[String]): Unit =
      Seq(None, Some(false)).foreach { isDowngrading =>
        execute(
          configDetails,
          moduleNames_ProdTestSourcesEnabled,
          prodTestSourcesSeparated = true,
          isDowngradingFromSeparateMainTestModules = isDowngrading,
          expectedResult
        )
      }

    @ParameterizedTest
    @MethodSource(Array("org.jetbrains.sbt.project.SbtMigrateConfigurationsActionTest#prodTestDisabled_isDowngradingTrue"))
    def testSeparateModulesForProdTestDisabled_isDowngradingTrue(configDetails: ConfigDetails, expectedResult: Seq[String]): Unit =
      execute(
        configDetails,
        moduleNames_ProdTestSourcesDisabled,
        prodTestSourcesSeparated = false,
        isDowngradingFromSeparateMainTestModules = Some(true),
        expectedResult
      )

    @ParameterizedTest
    @MethodSource(Array("org.jetbrains.sbt.project.SbtMigrateConfigurationsActionTest#prodTestDisabled_isDowngradingFalse"))
    def testSeparateModulesForProdTestDisabled_isDowngradingFalse(configDetails: ConfigDetails, expectedResult: Seq[String]): Unit =
      execute(
        configDetails,
        moduleNames_ProdTestSourcesDisabled,
        prodTestSourcesSeparated = false,
        isDowngradingFromSeparateMainTestModules = Some(false),
        expectedResult
      )

    @ParameterizedTest
    @MethodSource(Array("org.jetbrains.sbt.project.SbtMigrateConfigurationsActionTest#prodTestDisabled_isDowngradingNone"))
    def testSeparateModulesForProdTestDisabled_isDowngradingNone(configDetails: ConfigDetails, expectedResult: Seq[String]): Unit =
      execute(
        configDetails,
        moduleNames_ProdTestSourcesDisabled,
        prodTestSourcesSeparated = false,
        isDowngradingFromSeparateMainTestModules = None,
        expectedResult
      )
  }

  private def execute(
    configDetails: ConfigDetails,
    moduleNames: Seq[String],
    prodTestSourcesSeparated: Boolean,
    isDowngradingFromSeparateMainTestModules: Option[Boolean],
    expectedResult: Seq[String],
    mapModuleToMainClasses: Map[String, Seq[String]] = Map.empty,
  ): Unit = {
    // These methods below simulate methods inside org.jetbrains.sbt.project.SbtMigrateConfigurationsAction.findModulesForConfig
    def _findModuleWithMainClass(mainClassName: String, moduleNames: Seq[String]): Option[String] =
      moduleNames.find { m =>
        val mainClasses = mapModuleToMainClasses.getOrElse(m, Seq.empty)
        mainClasses.contains(mainClassName)
      }

    def _findAllModulesWithMainClass(mainClassName: String): Seq[String] =
      mapModuleToMainClasses.filter { case (_, classNames) =>
        classNames.contains(mainClassName)
      }.keys.toSeq

    val result = SbtMigrateConfigurationsAction.doFindModulesForConfig(
      configDetails,
      moduleNames,
      prodTestSourcesSeparated,
      isDowngradingFromSeparateMainTestModules,
      _findModuleWithMainClass,
      _findAllModulesWithMainClass,
    )
    assertEquals(expectedResult.sorted, result.sorted)
  }
}

object SbtMigrateConfigurationsActionTest {

  def moduleNameAndMainClassHeuristicsConsistency(): Stream[Arguments] = Stream.of(
    // modules found based on the name -> root.foo.main, root.foo.test, root~1.foo.main, root~1.foo.test
    // modules with the main class -> root~1.foo.test
    Arguments.of(ConfigDetails(isTest = false, "foo", Some("Main1")), Seq("root~1.foo.test")),
    // modules found based on the name -> empty
    // modules with the main class -> root.foo.dummy.test
    Arguments.of(ConfigDetails(isTest = false, "notKnownName", Some("Main3")), Seq("root.foo.dummy.test")),
    // modules found based on the name -> empty
    // modules with the main class -> root~1.main, root~1.test
    Arguments.of(ConfigDetails(isTest = false, "root.foo.main", Some("Main4")), Seq("root~1.main", "root~1.test")),
    // modules found based on the name -> empty
    // modules with the main class -> empty
    Arguments.of(ConfigDetails(isTest = false, "notKnownName", Some("Alone")), Seq()),
    // modules found based on the name -> root.foo.main, root.foo.test
    // modules with the main class -> root~1.main, root~1.test
    Arguments.of(ConfigDetails(isTest = false, "root.foo", Some("Main4")), Seq("root~1.main", "root~1.test", "root.foo.main", "root.foo.test"))
  )

  def prodTestEnabled_isDowngradingNoneOrFalse(): Stream[Arguments] = Stream.of(
    Arguments.of(ConfigDetails(isTest = false, "foo", Some("Main1")), Seq("root.foo.main", "root.foo.test", "root~1.foo.main", "root~1.foo.test")),
    Arguments.of(ConfigDetails(isTest = true, "foo", Some("Main1")), Seq("root.foo.test", "root~1.foo.test")),
    Arguments.of(ConfigDetails(isTest = true, "foo", Some("Main1")), Seq("root.foo.test", "root~1.foo.test")),
    Arguments.of(ConfigDetails(isTest = false, "notKnownName", Some("Main1")), Seq()),
    Arguments.of(ConfigDetails(isTest = false, "root.foo", Some("Main4")), Seq("root.foo.main", "root.foo.test")),
    Arguments.of(ConfigDetails(isTest = false, "root.foo.main", None), Seq()),
  )

  // downgrading from separate main/test modules
  def prodTestDisabled_isDowngradingTrue(): Stream[Arguments] = Stream.of(
    Arguments.of(ConfigDetails(isTest = false, "root.foo.main", Some("Main")), Seq("root.foo")),
    Arguments.of(ConfigDetails(isTest = false, "root.foo.something", Some("Main")), Seq()),
    Arguments.of(ConfigDetails(isTest = false, "foo", Some("Main")), Seq()),
    Arguments.of(ConfigDetails(isTest = true, "foo.main", Some("Main")), Seq())
  )

  //upgrading from the old grouping scheme to the new one
  def prodTestDisabled_isDowngradingFalse(): Stream[Arguments] = Stream.of(
    Arguments.of(ConfigDetails(isTest = true, "root.foo.main", None), Seq()),
    Arguments.of(ConfigDetails(isTest = false, "foo", Some("Main")), Seq("root.foo", "root~1.foo")),
    Arguments.of(ConfigDetails(isTest = false, "foo.dummy", Some("Main")), Seq("root.foo.dummy"))
  )

  // It's unclear what occurred - the user called the action from all actions.
  // This might be upgrading from the old grouping to the new grouping,
  // or downgrading from separate modules for prod/test.
  def prodTestDisabled_isDowngradingNone(): Stream[Arguments] = Stream.of(
    Arguments.of(ConfigDetails(isTest = false, "root.foo.main", Some("Main")), Seq("root.foo")),
    Arguments.of(ConfigDetails(isTest = false, "foo", Some("Main")), Seq("root.foo", "root~1.foo")),
    Arguments.of(ConfigDetails(isTest = true, "foo.main", Some("Main")), Seq())
  )
}
