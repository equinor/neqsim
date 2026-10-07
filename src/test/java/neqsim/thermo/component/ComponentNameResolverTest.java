package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.ResultSet;
import org.h2.tools.Csv;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link ComponentNameResolver}.
 *
 * @author NeqSim
 * @version $Id: $Id
 */
public class ComponentNameResolverTest {
  /** Names as spelled in data/COMP.csv. */
  private static List<String> databaseNames;
  private static final String COMPONENT_GUIDE = "docs/thermo/component/README.md";
  private static final Pattern COMPONENT_GUIDE_JAVA = Pattern.compile("(?ms)^```java\\r?\\n([\\s\\S]*?)^```[ \\t]*$");
  private static final Pattern PUBLIC_CLASS = Pattern
      .compile("public\\s+(?:final\\s+)?class\\s+([A-Za-z][A-Za-z0-9_]*)");

  @TempDir
  Path temporaryDirectory;

  /**
   * Read the component names straight from the CSV resource, so the test fails if the resolver tables drift away from
   * the database rather than from a copy of it.
   *
   * @throws Exception if the resource cannot be read
   */
  @BeforeAll
  public static void loadDatabaseNames() throws Exception {
    databaseNames = new ArrayList<String>();
    InputStream in = ComponentNameResolverTest.class.getClassLoader().getResourceAsStream("data/COMP.csv");
    assertNotNull(in, "data/COMP.csv must be on the test classpath");
    BufferedReader reader = new BufferedReader(new InputStreamReader(in, Charset.forName("UTF-8")));
    try (ResultSet rows = new Csv().read(reader, null)) {
      while (rows.next()) {
        databaseNames.add(rows.getString("NAME"));
      }
    } finally {
      reader.close();
    }
    assertTrue(databaseNames.size() > 200, "expected the full component list");
  }

  /** Every database name must resolve to itself, otherwise a lookup would break. */
  @Test
  public void databaseNamesResolveToThemselves() {
    for (int i = 0; i < databaseNames.size(); i++) {
      String name = databaseNames.get(i);
      assertEquals(name, ComponentNameResolver.resolve(name), "database name changed by the resolver: " + name);
    }
  }

  /** Database names must also resolve when the caller uses a different letter case. */
  @Test
  public void databaseNamesResolveCaseInsensitively() {
    for (int i = 0; i < databaseNames.size(); i++) {
      String name = databaseNames.get(i);
      assertEquals(name, ComponentNameResolver.resolve(name.toUpperCase()), "upper-case form not resolved: " + name);
      assertEquals(name, ComponentNameResolver.resolve(name.toLowerCase()), "lower-case form not resolved: " + name);
    }
  }

  /** Every synonym must point at a component that actually exists. */
  @Test
  public void everySynonymTargetExistsInTheDatabase() {
    Set<String> known = new HashSet<String>(databaseNames);
    Map<String, String> synonyms = ComponentNameResolver.getSynonyms();
    for (Map.Entry<String, String> entry : synonyms.entrySet()) {
      assertTrue(known.contains(entry.getValue()),
          "synonym '" + entry.getKey() + "' points at unknown component '" + entry.getValue() + "'");
    }
    assertTrue(synonyms.size() > 100, "expected a substantial synonym table");
  }

  /** The reservoir shorthand handled before this class existed must keep working. */
  @Test
  public void legacyReservoirShorthandStillResolves() {
    assertEquals("water", ComponentNameResolver.resolve("H2O"));
    assertEquals("nitrogen", ComponentNameResolver.resolve("N2"));
    assertEquals("methane", ComponentNameResolver.resolve("C1"));
    assertEquals("ethane", ComponentNameResolver.resolve("C2"));
    assertEquals("propane", ComponentNameResolver.resolve("C3"));
    assertEquals("i-butane", ComponentNameResolver.resolve("iC4"));
    assertEquals("n-butane", ComponentNameResolver.resolve("nC4"));
    assertEquals("i-pentane", ComponentNameResolver.resolve("iC5"));
    assertEquals("n-pentane", ComponentNameResolver.resolve("nC5"));
    assertEquals("n-hexane", ComponentNameResolver.resolve("C6"));
    assertEquals("n-heptane", ComponentNameResolver.resolve("nC7"));
    assertEquals("n-octane", ComponentNameResolver.resolve("nC8"));
    assertEquals("n-nonane", ComponentNameResolver.resolve("nC9"));
    assertEquals("oxygen", ComponentNameResolver.resolve("O2"));
    assertEquals("helium", ComponentNameResolver.resolve("He"));
    assertEquals("hydrogen", ComponentNameResolver.resolve("H2"));
    assertEquals("argon", ComponentNameResolver.resolve("Ar"));
    assertEquals("H2S", ComponentNameResolver.resolve("H2S"));
  }

  /** The same shorthand must also work through the original entry point. */
  @Test
  public void componentInterfaceAliasDelegatesToResolver() {
    assertEquals("methane", ComponentInterface.getComponentNameFromAlias("C1"));
    assertEquals("n-heptane", ComponentInterface.getComponentNameFromAlias("nC7"));
    assertEquals("2-m-C5", ComponentInterface.getComponentNameFromAlias("2-methylpentane"));
  }

  /** Systematic names must map onto the in-house shorthand used by the database. */
  @Test
  public void systematicNamesResolveToDatabaseShorthand() {
    assertEquals("224-TM-C5", ComponentNameResolver.resolve("2,2,4-trimethylpentane"));
    assertEquals("2-m-C5", ComponentNameResolver.resolve("2-methylpentane"));
    assertEquals("3-M-C7", ComponentNameResolver.resolve("3-methylheptane"));
    assertEquals("M-cy-C5", ComponentNameResolver.resolve("methylcyclopentane"));
    assertEquals("23-dim-C4", ComponentNameResolver.resolve("2,3-dimethylbutane"));
    assertEquals("nC10-Benzene", ComponentNameResolver.resolve("decylbenzene"));
  }

  /** Trivial names in common laboratory use must resolve. */
  @Test
  public void trivialNamesResolve() {
    assertEquals("i-pentane", ComponentNameResolver.resolve("isopentane"));
    assertEquals("i-pentane", ComponentNameResolver.resolve("2-methylbutane"));
    assertEquals("i-butane", ComponentNameResolver.resolve("isobutane"));
    assertEquals("22-dim-C3", ComponentNameResolver.resolve("neopentane"));
    assertEquals("224-TM-C5", ComponentNameResolver.resolve("isooctane"));
    assertEquals("c-hexane", ComponentNameResolver.resolve("cyclohexane"));
    assertEquals("toluene", ComponentNameResolver.resolve("methylbenzene"));
    assertEquals("o-Xylene", ComponentNameResolver.resolve("1,2-dimethylbenzene"));
    assertEquals("CO2", ComponentNameResolver.resolve("carbon dioxide"));
    assertEquals("MEG", ComponentNameResolver.resolve("ethylene glycol"));
  }

  /** The shorthand uses '.' where a systematic name uses ',' between locants. */
  @Test
  public void locantSeparatorIsInterchangeable() {
    assertEquals("1.2.3-TM-Benzene", ComponentNameResolver.resolve("1,2,3-TM-Benzene"));
    assertEquals("1.2.3-TM-Benzene", ComponentNameResolver.resolve("1.2.3-TM-Benzene"));
    assertEquals("1.2.3-TM-Benzene", ComponentNameResolver.resolve("1,2,3-trimethylbenzene"));
  }

  /** Inverted CAS index names, as printed by chromatography software, must resolve. */
  @Test
  public void invertedCasIndexNamesResolve() {
    assertEquals("1.2.4-TMcyC6", ComponentNameResolver.resolve("Cyclohexane, 1,2,4-trimethyl-"));
    assertEquals("2-m-C5", ComponentNameResolver.resolve("Pentane, 2-methyl-"));
  }

  /**
   * A name that does not identify one component must be passed through untouched.
   *
   * <p>
   * The database holds both cis and trans partners for these skeletons, so resolving the stereochemically unspecified
   * parent would silently pick one of the two.
   * </p>
   */
  @Test
  public void stereochemicallyAmbiguousNamesAreNotResolved() {
    assertEquals("1,3-dimethylcyclopentane", ComponentNameResolver.resolve("1,3-dimethylcyclopentane"));
    assertEquals("1,2-dimethylcyclohexane", ComponentNameResolver.resolve("1,2-dimethylcyclohexane"));
    assertEquals("2-butene", ComponentNameResolver.resolve("2-butene"));
    assertFalse(ComponentNameResolver.isKnownName("1,3-dimethylcyclopentane"));
  }

  /** Stereochemistry that is given must be honoured. */
  @Test
  public void explicitStereochemistryResolves() {
    assertEquals("cis-butene", ComponentNameResolver.resolve("cis-2-butene"));
    assertEquals("trans-butene", ComponentNameResolver.resolve("trans-2-butene"));
    assertEquals("cis-13-DM-cy-C6", ComponentNameResolver.resolve("cis-1,3-dimethylcyclohexane"));
  }

  /** Unknown names and null must be returned untouched. */
  @Test
  public void unknownNamesArePassedThrough() {
    assertEquals("not-a-component", ComponentNameResolver.resolve("not-a-component"));
    assertEquals("", ComponentNameResolver.resolve(""));
    assertNull(ComponentNameResolver.resolve(null));
    assertFalse(ComponentNameResolver.isKnownName("not-a-component"));
    assertFalse(ComponentNameResolver.isKnownName(null));
  }

  /** Whitespace and underscores must not defeat the lookup. */
  @Test
  public void separatorsAndWhitespaceAreNormalised() {
    assertEquals("n-heptane", ComponentNameResolver.resolve("  n-heptane  "));
    assertEquals("CO2", ComponentNameResolver.resolve("carbon   dioxide"));
    assertEquals("methane", ComponentNameResolver.resolve("METHANE"));
  }

  /** The examples given in docs/thermo/component_list.md must behave as documented. */
  @Test
  public void documentedExamplesBehaveAsDescribed() {
    assertEquals("224-TM-C5", ComponentNameResolver.resolve("224-TM-C5"));
    assertEquals("224-TM-C5", ComponentNameResolver.resolve("2,2,4-trimethylpentane"));
    assertEquals("224-TM-C5", ComponentNameResolver.resolve("isooctane"));
    assertEquals("224-TM-C5", ComponentNameResolver.resolve("ISOOCTANE"));
    assertEquals("n-heptane", ComponentNameResolver.resolve("N-HEPTANE"));
    assertEquals("nC10", ComponentNameResolver.resolve("n-decane"));
    assertEquals("n-water", ComponentNameResolver.resolve("n-water"));
    assertEquals("cis-13-DM-cy-C6", ComponentNameResolver.resolve("cis-1,3-dimethylcyclohexane"));
    assertTrue(ComponentNameResolver.isKnownName("isooctane"));
    assertFalse(ComponentNameResolver.getSynonyms().isEmpty());
    assertFalse(ComponentNameResolver.getCanonicalNames().isEmpty());
  }

  /**
   * The straight-chain 'n-' prefix must be optional.
   *
   * <p>
   * The database stores C4 to C9 as {@code n-butane} to {@code n-nonane} but C10 upwards as {@code nC10}, so without
   * this the naming cliff at C10 made {@code n-decane} fail while {@code n-nonane} worked.
   * </p>
   */
  @Test
  public void straightChainPrefixIsOptional() {
    assertEquals("nC10", ComponentNameResolver.resolve("n-decane"));
    assertEquals("nC10", ComponentNameResolver.resolve("decane"));
    assertEquals("nC11", ComponentNameResolver.resolve("n-undecane"));
    assertEquals("nC12", ComponentNameResolver.resolve("n-dodecane"));
    assertEquals("nC15", ComponentNameResolver.resolve("n-pentadecane"));
    assertEquals("nC20", ComponentNameResolver.resolve("n-icosane"));
    // names that already carry the prefix in the database are untouched
    assertEquals("n-butane", ComponentNameResolver.resolve("n-butane"));
    assertEquals("n-nonane", ComponentNameResolver.resolve("n-nonane"));
    assertEquals("nC5-Benzene", ComponentNameResolver.resolve("n-pentylbenzene"));
    assertTrue(ComponentNameResolver.isKnownName("n-decane"));
    assertEquals("n-water", ComponentNameResolver.resolve("n-water"));
    assertEquals("n-acetone", ComponentNameResolver.resolve("n-acetone"));
    assertEquals("n-isobutane", ComponentNameResolver.resolve("n-isobutane"));
    assertEquals("n-n-decane", ComponentNameResolver.resolve("n-n-decane"));
    assertFalse(ComponentNameResolver.isKnownName("n-water"));
    assertFalse(ComponentNameResolver.isKnownName("n-acetone"));
    assertFalse(ComponentNameResolver.isKnownName("n-isobutane"));
    assertFalse(ComponentNameResolver.isKnownName("n-n-decane"));
  }

  /** A fluid must accept a systematic name and build the corresponding component. */
  @Test
  public void fluidAcceptsSystematicName() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 10.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("2,2,4-trimethylpentane", 0.1);
    fluid.addComponent("isopentane", 0.1);
    fluid.setMixingRule("classic");
    assertEquals(3, fluid.getNumberOfComponents());
    assertEquals("224-TM-C5", fluid.getPhase(0).getComponent(1).getComponentName());
    assertEquals("i-pentane", fluid.getPhase(0).getComponent(2).getComponentName());
  }

  /** The component package guide must publish one complete, unit-explicit Java program. */
  @Test
  public void componentPackageGuideHasOneExecutableProgram() throws Exception {
    String guide = readComponentGuide();

    assertTrue(guide.startsWith("---\n"));
    assertTrue(guide.contains("absolute bara"));
    assertTrue(guide.replaceAll("\\s+", " ").contains("molar mass is read in kg/mol"));
    assertTrue(guide.contains("../component_list#component-name-resolution"));
    assertTrue(guide.contains("## Database and pseudo-components"));
    assertFalse(guide.contains("System.out"));
    assertFalse(guide.contains("```python"));

    Matcher fences = COMPONENT_GUIDE_JAVA.matcher(guide);
    assertTrue(fences.find(), "Component guide must publish one Java program");
    assertFalse(fences.find(), "Component guide must not publish unverified Java fragments");
  }

  /** Compiles the exact component guide fence for Java 8 and executes it with assertions enabled. */
  @Test
  public void componentPackageGuideCompilesAndRuns() throws Exception {
    Matcher fence = COMPONENT_GUIDE_JAVA.matcher(readComponentGuide());
    assertTrue(fence.find(), "Executable component example is missing");
    String source = fence.group(1);

    assertTrue(source.contains("new SystemSrkEos(298.15, 50.0)"));
    assertTrue(source.contains("fluid.addComponent(\"2,2,4-trimethylpentane\", 1.0)"));
    assertTrue(source.contains("fluid.getComponent(\"isooctane\")"));
    assertTrue(source.contains("phase.getComponent(\"ISOOCTANE\")"));
    assertTrue(source.contains("operations.TPflash()"));
    assertTrue(source.contains("fluid.initProperties()"));
    assertTrue(source.contains("assert Math.abs(overallFractionSum - 1.0) < 1.0e-10"));
    assertTrue(source.contains("getFugacityCoefficient() > 0.0"));
    assertFalse(source.contains("System.out"));

    compileAndRunComponentGuide(source);
    assertFalse(fence.find(), "Executable section must contain one Java program");
  }

  /** Reads the component package guide from the repository root. */
  private String readComponentGuide() throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    return new String(Files.readAllBytes(repositoryRoot.resolve(COMPONENT_GUIDE)), StandardCharsets.UTF_8)
        .replace("\r\n", "\n");
  }

  /** Compiles one extracted Java source and invokes its main method with assertions enabled. */
  private void compileAndRunComponentGuide(String source) throws Exception {
    Matcher className = PUBLIC_CLASS.matcher(source);
    assertTrue(className.find(), "Java fence must contain a complete public class");
    String name = className.group(1);
    assertFalse(className.find(), "Java fence must contain one public class");

    Path outputDirectory = temporaryDirectory.resolve(name);
    Files.createDirectories(outputDirectory);
    Path javaSource = outputDirectory.resolve(name + ".java");
    Files.write(javaSource, source.getBytes(StandardCharsets.UTF_8));

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "Documentation examples require a JDK compiler");
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
    String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    Iterable<String> options = java.util.Arrays.asList("-source", "8", "-target", "8", "-classpath", classPath, "-d",
        outputDirectory.toString());
    try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
      Boolean successful = compiler
          .getTask(null, manager, diagnostics, options, null, manager.getJavaFileObjects(javaSource.toFile())).call();
      assertTrue(Boolean.TRUE.equals(successful), diagnostics.getDiagnostics().toString());
    }

    try (URLClassLoader loader = new URLClassLoader(new URL[] {outputDirectory.toUri().toURL()},
        getClass().getClassLoader())) {
      loader.setDefaultAssertionStatus(true);
      Class<?> example = Class.forName(name, true, loader);
      assertTrue(example.desiredAssertionStatus());
      try {
        example.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
      } catch (InvocationTargetException exception) {
        throw new AssertionError(name + " failed", exception.getCause());
      }
    }
  }

}
