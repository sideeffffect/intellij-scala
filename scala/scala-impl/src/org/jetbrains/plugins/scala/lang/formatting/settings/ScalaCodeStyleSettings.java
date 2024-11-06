package org.jetbrains.plugins.scala.lang.formatting.settings;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.OptionTag;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.ScalaBundle;
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil;
import scala.collection.immutable.ArraySeq;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents a dedicated tab panel in Scala code style settings
 */
public class ScalaCodeStyleSettings extends CustomCodeStyleSettings {
  public boolean WRAP_BEFORE_WITH_KEYWORD = false;
  public int METHOD_BRACE_FORCE = 0;
  public int FINALLY_BRACE_FORCE = 0;
  public int TRY_BRACE_FORCE = 0;
  public int CLOSURE_BRACE_FORCE = 0;
  public int CASE_CLAUSE_BRACE_FORCE = 0;
  public boolean PLACE_CLOSURE_PARAMETERS_ON_NEW_LINE = false;
  public boolean SPACE_INSIDE_CLOSURE_BRACES = true;
  public boolean PLACE_SELF_TYPE_ON_NEW_LINE = true;
  public boolean SPACE_INSIDE_SELF_TYPE_BRACES = true;
  public boolean ALIGN_IF_ELSE = false;

  public static final int DO_NOT_ALIGN = 0;
  public static final int ON_FIRST_TOKEN = 1;
  public static final int ON_FIRST_ANCESTOR = 2;
  public static final int ALIGN_TO_EXTENDS = 3;
  public static final int[] EXTENDS_ALIGN_VALUES = new int[]{
      DO_NOT_ALIGN,
      ON_FIRST_TOKEN,
      ALIGN_TO_EXTENDS
  };
  public static final String[] EXTENDS_ALIGN_STRING = new String[]{
      ScalaBundle.message("wrapping.and.braces.panel.extends.do.not.align"),
      ScalaBundle.message("wrapping.and.braces.panel.extends.on.first.token"),
      ScalaBundle.message("wrapping.and.braces.panel.extends.align.to.extends")
  };
  public int ALIGN_EXTENDS_WITH = DO_NOT_ALIGN;

  //indents
  public boolean NOT_CONTINUATION_INDENT_FOR_PARAMS = false;
  public boolean ALIGN_IN_COLUMNS_CASE_BRANCH = false;
  public boolean ALIGN_COMPOSITE_PATTERN = true;
  public boolean DO_NOT_ALIGN_BLOCK_EXPR_PARAMS = true;
  public boolean DO_NOT_INDENT_TUPLES_CLOSE_BRACE = true;
  public boolean ALIGN_TUPLE_ELEMENTS = false;
  public boolean INDENT_FIRST_PARAMETER = true;
  public boolean INDENT_FIRST_PARAMETER_CLAUSE = false;

  public boolean INDENT_YIELD_AFTER_ONE_LINE_ENUMERATORS = true;

  public String SCALAFMT_CONFIG_PATH = "";
  public boolean SCALAFMT_SHOW_INVALID_CODE_WARNINGS = true;
  public boolean SCALAFMT_USE_INTELLIJ_FORMATTER_FOR_RANGE_FORMAT = true;
  public boolean SCALAFMT_REFORMAT_ON_FILES_SAVE = false;
  public boolean SCALAFMT_FALLBACK_TO_DEFAULT_SETTINGS = false;

  public static final int INTELLIJ_FORMATTER = 0;
  public static final int SCALAFMT_FORMATTER = 1;
  public int FORMATTER = INTELLIJ_FORMATTER;

  public boolean USE_SCALAFMT_FORMATTER() {
    return FORMATTER == SCALAFMT_FORMATTER;
  }

  public boolean USE_INTELLIJ_FORMATTER() {
    return FORMATTER == INTELLIJ_FORMATTER;
  }

  public boolean USE_ALTERNATE_CONTINUATION_INDENT_FOR_PARAMS = false;
  public int ALTERNATE_CONTINUATION_INDENT_FOR_PARAMS = 4;

  public boolean SPACE_AFTER_MODIFIERS_CONSTRUCTOR = false;

  // simple method call:
  // obj foo (x, y)
  // obj ** (bar, baz)
  // operator-like method call:
  // a max b
  // a max (b)
  // actual operator callcall:
  public boolean SPACE_BEFORE_INFIX_METHOD_CALL_PARENTHESES = false;
  public boolean SPACE_BEFORE_INFIX_OPERATOR_LIKE_METHOD_CALL_PARENTHESES = true;

  public boolean SPACE_BEFORE_TYPE_COLON = false;
  public boolean SPACE_AFTER_TYPE_COLON = true;
  public boolean INDENT_BRACED_FUNCTION_ARGS = true;
  public boolean DO_NOT_INDENT_CASE_CLAUSE_BODY = false;

  //ATTENTION:
  //This setting is currently not available for the user and is needed for internal use only (for "Java to Scala" converter)
  //If we decide to make it public:
  // 1. consider setting it to true by default
  // 2. maybe create some setting for any `=>`, not only for case clauses but for functional expressions?
  //Also probably think about better name
  public boolean NEW_LINE_AFTER_CASE_CLAUSE_ARROW_WHEN_MULTILINE_BODY = false;

  //blank lines
  public int BLANK_LINES_AROUND_METHOD_IN_INNER_SCOPES = 1;
  public int BLANK_LINES_AROUND_FIELD_IN_INNER_SCOPES = 0;
  public int BLANK_LINES_AROUND_CLASS_IN_INNER_SCOPES = 0;

  //spacing settings:
  public boolean SPACE_BEFORE_BRACE_METHOD_CALL = true;
  public boolean KEEP_ONE_LINE_LAMBDAS_IN_ARG_LIST = false;

  public boolean USE_SCALADOC2_FORMATTING = false;

  public boolean PRESERVE_SPACE_AFTER_METHOD_DECLARATION_NAME = false;
  public boolean SPACE_BEFORE_INFIX_LIKE_METHOD_PARENTHESES = false;

  public boolean SPACES_IN_ONE_LINE_BLOCKS = false;
  public boolean SPACES_IN_IMPORTS = false;
  public boolean SPACES_AROUND_AT_IN_PATTERNS = false;
  public boolean NEWLINE_AFTER_ANNOTATIONS = false;

  public static final int ALIGN_ON_COLON = 1;
  public static final int ALIGN_ON_TYPE = 2;
  public static final int[] TYPE_ANNOTATION_ALIGN_VALUES = new int[]{DO_NOT_ALIGN, ALIGN_ON_COLON, ALIGN_ON_TYPE};
  public static final String[] TYPE_ANNOTATION_ALIGN_STRING = new String[]{"Do not align", "On colon", "On type",};

  /**
   * @deprecated This field is left for migration only. Use {@link #ALIGN_PARAMETER_TYPES_IN_MULTILINE_DECLARATIONS}
   * @see org.jetbrains.plugins.scala.lang.formatting.settings.migration.CodeStyleSettingsMigrationServiceBase
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  public boolean ALIGN_TYPES_IN_MULTILINE_DECLARATIONS = false;
  public int ALIGN_PARAMETER_TYPES_IN_MULTILINE_DECLARATIONS = DO_NOT_ALIGN;

  public boolean KEEP_COMMENTS_ON_SAME_LINE = true;
  public boolean SPACE_BEFORE_TYPE_PARAMETER_IN_DEF_LIST = false;

  // multiple versions created to preserve legacy formatting
  public boolean SPACE_BEFORE_TYPE_PARAMETER_LEADING_CONTEXT_BOUND_COLON = false; // class A[M: T]
  public boolean SPACE_BEFORE_TYPE_PARAMETER_LEADING_CONTEXT_BOUND_COLON_HK = true; // class A[M[_] : T]
  public boolean SPACE_BEFORE_TYPE_PARAMETER_REST_CONTEXT_BOUND_COLONS = true; // class A[M: T1 : T2]

  public boolean INDENT_TYPE_ARGUMENTS = true;
  public boolean INDENT_TYPE_PARAMETERS = true;

  public static final int NO_NEW_LINE = 0;
  public static final int NEW_LINE_ALWAYS = 1;
  public static final int NEW_LINE_FOR_MULTIPLE_ARGUMENTS = 2;
  public int CALL_PARAMETERS_NEW_LINE_AFTER_LPAREN = NO_NEW_LINE;

  //When set to true formats like this:
  //  foo0.foo1:
  //      42
  //    .foo0
  //    .foo0
  //When set to false formats like this:
  //  foo0.foo1:
  //      42
  //  .foo0
  //  .foo0
  //NOTE: the name is not the best and can be changed
  //NOTE: syntax with extra indent is only valid since Scala 3.3.1-RC1
  public boolean INDENT_FEWER_BRACES_IN_METHOD_CALL_CHAINS = false;

  //xml formatting
  public boolean KEEP_XML_FORMATTING = false;

  //multiline strings support
  public boolean MULTILINE_STRING_INSERT_MARGIN_ON_ENTER = true;
  public boolean MULTILINE_STRING_ALIGN_DANGLING_CLOSING_QUOTES = false;
  public boolean MULTILINE_STRING_CLOSING_QUOTES_ON_NEW_LINE = false;

  public boolean USE_SCALA3_INDENTATION_BASED_SYNTAX = true;
  
  /**
   * @deprecated This field is left for migration only. Use {@link #MULTILINE_STRING_CLOSING_QUOTES_ON_NEW_LINE} and {@link #MULTILINE_STRING_INSERT_MARGIN_ON_ENTER}
   * @see org.jetbrains.plugins.scala.lang.formatting.settings.migration.CodeStyleSettingsMigrationServiceBase
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  public int MULTILINE_STRING_SUPORT = MULTILINE_STRING_ALL; // !! do not fix this typo in supPort, let legacy settings t be migrated properly

  @OptionTag("MARGIN_CHAR")
  public String MULTILINE_STRING_MARGIN_CHAR = "|";
  @OptionTag("MULTI_LINE_QUOTES_ON_NEW_LINE")
  public boolean MULTILINE_STRING_OPENING_QUOTES_ON_NEW_LINE = true;
  @OptionTag("MULTI_LINE_STRING_MARGIN_INDENT")
  public int MULTILINE_STRING_MARGIN_INDENT = 2;
  @OptionTag("PROCESS_MARGIN_ON_COPY_PASTE")
  public boolean MULTILINE_STRING_PROCESS_MARGIN_ON_COPY_PASTE = true;

  public boolean supportMultilineString() {
    return MULTILINE_STRING_CLOSING_QUOTES_ON_NEW_LINE | MULTILINE_STRING_INSERT_MARGIN_ON_ENTER | MULTILINE_STRING_PROCESS_MARGIN_ON_COPY_PASTE;
  }

  @Deprecated
  public static final int MULTILINE_STRING_NONE = 0;
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  public static final int MULTILINE_STRING_QUOTES_AND_INDENT = 1;
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  public static final int MULTILINE_STRING_INSERT_MARGIN_CHAR = 2;
  @Deprecated
  private static final int MULTILINE_STRING_ALL = 2;

  @Override
  protected void afterLoaded() {
    super.afterLoaded();
  }

  //type annotations
  public boolean TYPE_ANNOTATION_PUBLIC_MEMBER = true;
  public boolean TYPE_ANNOTATION_PROTECTED_MEMBER = true;
  public boolean TYPE_ANNOTATION_PRIVATE_MEMBER = false;
  public boolean TYPE_ANNOTATION_LOCAL_DEFINITION = false;
  public boolean TYPE_ANNOTATION_FUNCTION_PARAMETER = false;
  public boolean TYPE_ANNOTATION_UNDERSCORE_PARAMETER = false;

  public boolean TYPE_ANNOTATION_IMPLICIT_MODIFIER = true;
  public boolean TYPE_ANNOTATION_UNIT_TYPE = true;
  public boolean TYPE_ANNOTATION_STRUCTURAL_TYPE = true;

  public boolean TYPE_ANNOTATION_EXCLUDE_MEMBER_OF_ANONYMOUS_CLASS = false;
  public boolean TYPE_ANNOTATION_EXCLUDE_MEMBER_OF_PRIVATE_CLASS = false;
  public boolean TYPE_ANNOTATION_EXCLUDE_CONSTANT = true;
  public boolean TYPE_ANNOTATION_EXCLUDE_WHEN_TYPE_IS_STABLE = true;
  public boolean TYPE_ANNOTATION_EXCLUDE_IN_DIALECT_SOURCES = true;
  public boolean TYPE_ANNOTATION_EXCLUDE_IN_TEST_SOURCES = false;

  public Set<String> TYPE_ANNOTATION_EXCLUDE_MEMBER_OF = asSet("scala.App", "junit.framework.TestCase");
  public Set<String> TYPE_ANNOTATION_EXCLUDE_ANNOTATED_WITH = asSet("junit.framework.Test", "org.junit.Test");
  @NonNls public Set<String> TYPE_ANNOTATION_EXCLUDE_WHEN_TYPE_MATCHES = asSet("sbt.*", "slick.*");

  //scaladoc formatting
  // TODO: not all ScalaDoc settings are ignored if this setting is disabled
  public boolean ENABLE_SCALADOC_FORMATTING = true;

  public boolean SD_ALIGN_OTHER_TAGS_COMMENTS = true;
  public boolean SD_ALIGN_PARAMETERS_COMMENTS = true;
  public boolean SD_ALIGN_EXCEPTION_COMMENTS = true;
  public boolean SD_ALIGN_RETURN_COMMENTS = true;

  public boolean SD_ALIGN_LIST_ITEM_CONTENT = true;

  public boolean SD_BLANK_LINE_BEFORE_TAGS = true;
  public boolean SD_BLANK_LINE_AFTER_PARAMETERS_COMMENTS = false;
  public boolean SD_BLANK_LINE_AFTER_RETURN_COMMENTS = false;
  public boolean SD_BLANK_LINE_BETWEEN_PARAMETERS = false;
  public boolean SD_BLANK_LINE_BEFORE_PARAMETERS = false;
  public boolean SD_KEEP_BLANK_LINES_BETWEEN_TAGS = false;

  public boolean SD_PRESERVE_SPACES_IN_TAGS = false;


  //other
  public boolean ENFORCE_FUNCTIONAL_SYNTAX_FOR_UNIT = true;
  public boolean REPLACE_CASE_ARROW_WITH_UNICODE_CHAR = false;
  public boolean REPLACE_MAP_ARROW_WITH_UNICODE_CHAR = false;
  public boolean REPLACE_FOR_GENERATOR_ARROW_WITH_UNICODE_CHAR = false;
  public boolean REPLACE_LAMBDA_WITH_GREEK_LETTER = false;


  public enum TrailingCommaMode {TRAILING_COMMA_KEEP, TRAILING_COMMA_REMOVE_WHEN_MULTILINE, TRAILING_COMMA_ADD_WHEN_MULTILINE}

  public TrailingCommaMode TRAILING_COMMA_MODE = TrailingCommaMode.TRAILING_COMMA_KEEP;
  /** used via reflection in {@link org.jetbrains.plugins.scala.lang.formatting.settings.TrailingCommaPanel scopeFields} */
  public boolean TRAILING_COMMA_ARG_LIST_ENABLED = true;
  public boolean TRAILING_COMMA_PARAMS_ENABLED = true;
  public boolean TRAILING_COMMA_TUPLE_ENABLED = false;
  public boolean TRAILING_COMMA_PATTERN_ARG_LIST_ENABLED = false;
  public boolean TRAILING_COMMA_TYPE_PARAMS_ENABLED = false;
  public boolean TRAILING_COMMA_IMPORT_SELECTOR_ENABLED = false;

  public String IMPLICIT_VALUE_CLASS_PREFIX = "";
  public String IMPLICIT_VALUE_CLASS_SUFFIX = DEFAULT_IMPLICIT_VALUE_CLASS_SUFFIX;
  @NonNls
  public static final String DEFAULT_IMPLICIT_VALUE_CLASS_SUFFIX = "Ops";

  //global
  public boolean REFORMAT_ON_COMPILE = false;

  /**
   * If you reimplement read/write logic, do not forget to replace OptionTag annotations with something appropriate
   * @see com.intellij.util.xmlb.BeanBinding createBinding(MutableAccessor, Serializer, Property.Style) method
   */
  @Override
  public void readExternal(Element parentElement) throws InvalidDataException {
    Element scalaCodeStyleSettings = parentElement.getChild(getTagName());
    if (scalaCodeStyleSettings != null) {
      XmlSerializer.deserializeInto(this, scalaCodeStyleSettings);
    }
  }

  @SuppressWarnings("deprecation")
  @Override
  public void writeExternal(Element parentElement, @NotNull CustomCodeStyleSettings parentSettings) throws WriteExternalException {
    Element scalaCodeStyleSettings = new Element(getTagName());
    parentElement.addContent(scalaCodeStyleSettings);
    XmlSerializer.serializeInto(this, scalaCodeStyleSettings, new SkipDefaultValuesSerializationFilters());
    if (scalaCodeStyleSettings.getChildren().isEmpty()) {
      parentElement.removeChild(getTagName());
    }
  }

  @NotNull
  private static HashSet<String> asSet(String... strings) {
    return new HashSet<>(Arrays.asList(strings));
  }

  //import
  private boolean IMPORT_SHORTEST_PATH_FOR_AMBIGUOUS_REFERENCES = true;
  private int CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = 5;
  private boolean ADD_IMPORT_MOST_CLOSE_TO_REFERENCE = false;
  private boolean ADD_FULL_QUALIFIED_IMPORTS = true;
  private boolean ADD_IMPORTS_RELATIVE_TO_BASE_PACKAGE = true;
  private boolean DO_NOT_CHANGE_LOCAL_IMPORTS_ON_OPTIMIZE = true;
  private boolean SORT_IMPORTS = true;
  private boolean SORT_AS_SCALASTYLE = false;
  private boolean COLLECT_IMPORTS_TOGETHER = true;
  private boolean FORCE_SCALA2_IMPORT_SYNTAX_IN_SOURCE3 = false;

  private String[] ALWAYS_USED_IMPORTS = new String[0];

  @NonNls
  private String[] IMPORTS_WITH_PREFIX = new String[]{
      "exclude:scala.collection.mutable.ArrayBuffer",
      "exclude:scala.collection.mutable.ListBuffer",
      "exclude:scala.collection.mutable.StringBuilder",
      "java.lang.StringBuilder",
      "java.util.AbstractCollection",
      "java.util.AbstractList",
      "java.util.AbstractMap",
      "java.util.AbstractQueue",
      "java.util.AbstractSequentialList",
      "java.util.AbstractSet",
      "java.util.ArrayDeque",
      "java.util.ArrayList",
      "java.util.Arrays",
      "java.util.BitSet",
      "java.util.Collection",
      "java.util.Deque",
      "java.util.EnumMap",
      "java.util.EnumSet",
      "java.util.Enumeration",
      "java.util.HashMap",
      "java.util.HashSet",
      "java.util.Hashtable",
      "java.util.IdentityHashMap",
      "java.util.Iterator",
      "java.util.LinkedHashMap",
      "java.util.LinkedHashSet",
      "java.util.LinkedList",
      "java.util.List",
      "java.util.ListIterator",
      "java.util.Map",
      "java.util.NavigableMap",
      "java.util.NavigableSet",
      "java.util.Queue",
      "java.util.Set",
      "java.util.SortedMap",
      "java.util.SortedSet",
      "java.util.Stack",
      "java.util.SubList",
      "java.util.TreeMap",
      "java.util.TreeSet",
      "java.util.Vector",
      "java.util.WeakHashMap",
      "org.scalatest.fixture._",
      "org.scalatest.path._",
      "scala.collection.mutable._",
      "scala.reflect.macros.blackbox.Context",
      "scala.reflect.macros.whitebox.Context"
  };

  private String[] IMPORT_LAYOUT = DEFAULT_IMPORT_LAYOUT;

  // TODO For some reason, SkipDefaultValuesSerializationFilters uses reflection to instantiate the class and needs an empty constructor.
  @SuppressWarnings("deprecation")
  public ScalaCodeStyleSettings() {
    this(new CodeStyleSettings());
  }

  public ScalaCodeStyleSettings(CodeStyleSettings container) {
    super("ScalaCodeStyleSettings", container);
  }

  public int getClassCountToUseImportOnDemand() {
    return CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND;
  }

  public void setClassCountToUseImportOnDemand(int value) {
    CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = value;
  }

  public boolean isAddImportMostCloseToReference() {
    return ADD_IMPORT_MOST_CLOSE_TO_REFERENCE;
  }

  public void setAddImportMostCloseToReference(boolean value) {
    ADD_IMPORT_MOST_CLOSE_TO_REFERENCE = value;
  }

  public boolean isAddFullQualifiedImports() {
    return ADD_FULL_QUALIFIED_IMPORTS;
  }

  public void setAddFullQualifiedImports(boolean value) {
    ADD_FULL_QUALIFIED_IMPORTS = value;
  }

  public boolean isAddImportsRelativeToBasePackage() {
    return ADD_IMPORTS_RELATIVE_TO_BASE_PACKAGE;
  }

  public void setAddImportsRelativeToBasePackage(boolean value) {
    ADD_IMPORTS_RELATIVE_TO_BASE_PACKAGE = value;
  }

  public boolean isSortImports() {
    return SORT_IMPORTS;
  }

  public void setSortImports(boolean value) {
    SORT_IMPORTS = value;
  }

  public boolean isSortAsScalastyle() {
    return SORT_AS_SCALASTYLE;
  }

  public void setSortAsScalastyle(boolean value) {
    this.SORT_AS_SCALASTYLE = value;
  }

  public boolean isCollectImports() {
    return COLLECT_IMPORTS_TOGETHER;
  }

  public void setCollectImports(boolean value) {
    COLLECT_IMPORTS_TOGETHER = value;
  }

  public boolean isForceScala2ImportSyntaxInSource3() {
    return FORCE_SCALA2_IMPORT_SYNTAX_IN_SOURCE3;
  }

  public void setForceScala2ImportSyntaxInSource3(boolean value) {
    FORCE_SCALA2_IMPORT_SYNTAX_IN_SOURCE3 = value;
  }

  public boolean isImportShortestPathForAmbiguousReferences() {
    return IMPORT_SHORTEST_PATH_FOR_AMBIGUOUS_REFERENCES;
  }

  public void setImportShortestPathForAmbiguousReferences(boolean importShortestPathForAmbiguousReferences) {
    this.IMPORT_SHORTEST_PATH_FOR_AMBIGUOUS_REFERENCES = importShortestPathForAmbiguousReferences;
  }

  public String[] getImportsWithPrefix() {
    return IMPORTS_WITH_PREFIX;
  }

  public void setImportsWithPrefix(String[] importsWithPrefix) {
    this.IMPORTS_WITH_PREFIX = importsWithPrefix;
  }

  public boolean hasImportWithPrefix(@Nullable String qualName) {
    if (qualName != null && qualName.contains(".")) {
      String[] importsWithPrefix = getImportsWithPrefix();
      return nameFitToPatterns(qualName, importsWithPrefix);
    } else return false;
  }

  public String[] getAlwaysUsedImports() {
    return ALWAYS_USED_IMPORTS;
  }

  public void setAlwaysUsedImports(String[] alwaysUsedImports) {
    this.ALWAYS_USED_IMPORTS = alwaysUsedImports;
    //this setting may be modified from a quickfix, we need to update counter to mark it as changed
    getContainer().getModificationTracker().incModificationCount();
  }

  public boolean isAlwaysUsedImport(String qualName) {
    if (qualName != null && qualName.contains(".")) {
      String[] alwaysUsedImports = getAlwaysUsedImports();
      return nameFitToPatterns(qualName, alwaysUsedImports);
    } else return false;
  }

  public String[] getImportLayout() {
    return IMPORT_LAYOUT;
  }

  public void setImportLayout(String[] importLayout) {
    this.IMPORT_LAYOUT = importLayout;
  }

  //TODO: if decide to i18 be careful to separate view value from persisted value
  public static final String EXCLUDE_PREFIX = "exclude:";

  public static final String BLANK_LINE = "_______ blank line _______";
  public static final String BASE_PACKAGE_IMPORTS = "base package imports";
  public static final String ALL_OTHER_IMPORTS = "all other imports";

  public static String[] DEFAULT_IMPORT_LAYOUT = new String[]{
          BASE_PACKAGE_IMPORTS,
          BLANK_LINE,
          ALL_OTHER_IMPORTS,
          BLANK_LINE,
          "java",
          "javax",
          "scala"
  };

  public static String[] LEGACY_IMPORT_LAYOUT = new String[]{
          "java",
          BLANK_LINE,
          BASE_PACKAGE_IMPORTS,
          BLANK_LINE,
          ALL_OTHER_IMPORTS,
          BLANK_LINE,
          "scala"
  };

  public static ScalaCodeStyleSettings getInstance(Project project) {
    return CodeStyle.getSettings(project).getCustomSettings(ScalaCodeStyleSettings.class);
  }

  /**
   * Checks whether qualified class name fit to the list of patterns.
   * Expamples of patterns:
   * "java.util.ArrayList"                              java.util.ArrayList added
   * "scala.collection.mutable._"                       all classes from package scala.collection.mutable added
   * "scala.collection.mutable._._"                     all classes from subpackages and inner classes of scala.collection.mutable
   * "exclude:scala.Option"                             scala.Option excluded
   * "exclude:scala.collection.immutable._"             all classes from package scala.collection.immutable excluded
   **/
  private static boolean nameFitToPatterns(String qualName, String[] patterns) {
    return ScalaNamesUtil.nameFitToPatterns(qualName, ArraySeq.unsafeWrapArray(patterns), true);
  }

  public boolean isDoNotChangeLocalImportsOnOptimize() {
    return DO_NOT_CHANGE_LOCAL_IMPORTS_ON_OPTIMIZE;
  }

  public void setDoNotChangeLocalImportsOnOptimize(boolean value) {
    this.DO_NOT_CHANGE_LOCAL_IMPORTS_ON_OPTIMIZE = value;
  }

  public char getMarginChar() {
    return MULTILINE_STRING_MARGIN_CHAR.charAt(0);
  }
}
