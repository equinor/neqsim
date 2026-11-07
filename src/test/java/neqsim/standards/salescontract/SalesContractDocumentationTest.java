package neqsim.standards.salescontract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import neqsim.NeqSimTest;
import neqsim.thermo.system.SystemGERGwaterEos;
import neqsim.thermo.system.SystemInterface;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Executes and protects the database-backed example in the gas sales-contract guide. */
class SalesContractDocumentationTest extends NeqSimTest {
  private static final String EXAMPLE_MARKER =
      "<!-- sales-contract-example: brazil-screen -->";
  private static final Pattern JAVA_FENCE =
      Pattern.compile(
          "(?s)<!-- sales-contract-example: brazil-screen -->\\s*"
              + "```java\\s*\\r?\\n([\\s\\S]*?)\\r?\\n```");
  private static final Pattern PUBLIC_CLASS =
      Pattern.compile("public\\s+(?:final\\s+)?class\\s+([A-Za-z][A-Za-z0-9_]*)");

  @TempDir Path temporaryDirectory;

  /** Validate the current bundled fixture and its 12-column result-table contract. */
  @Test
  void testBrazilContractExampleAndResultTableContract() {
    SystemInterface gas = new SystemGERGwaterEos(268.15, 20.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.04);
    gas.addComponent("propane", 0.02);
    gas.addComponent("n-heptane", 0.00012);
    gas.addComponent("H2S", 0.000012);
    gas.addComponent("water", 0.000071);
    gas.addComponent("oxygen", 0.0012);
    gas.addComponent("CO2", 0.022);
    gas.addComponent("nitrogen", 0.022);
    gas.setMixingRule(8);
    gas.init(0);

    BaseContract contract = new BaseContract(gas, "central", "Brazil");
    contract.runCheck();

    String[][] rows = contract.getResultTable();
    int specificationCount = contract.getSpecificationsNumber();
    int rowsWithinLimits = 0;
    for (int rowIndex = 0; rowIndex < specificationCount; rowIndex++) {
      double value = parseDoubleOrFail(rows[rowIndex][1], rowIndex, 1);
      double minimum = parseDoubleOrFail(rows[rowIndex][4], rowIndex, 4);
      double maximum = parseDoubleOrFail(rows[rowIndex][5], rowIndex, 5);
      if (Double.isFinite(value) && value >= minimum && value <= maximum) {
        rowsWithinLimits++;
      }
    }

    assertEquals(8, specificationCount);
    assertEquals(8, rows.length);
    assertEquals(12, rows[0].length);
    assertEquals("CO2", rows[1][0]);
    assertEquals(2.18817727816606, parseDoubleOrFail(rows[1][1], 1, 1), 1.0e-6);
    assertEquals("Brazil", rows[1][2]);
    assertEquals("central", rows[1][3]);
    assertEquals(0.0, parseDoubleOrFail(rows[1][4], 1, 4), 0.0);
    assertEquals(3.0, parseDoubleOrFail(rows[1][5], 1, 5), 0.0);
    assertEquals("mol%", rows[1][6]);
    assertEquals(1.0, parseDoubleOrFail(rows[1][10], 1, 10), 0.0);
    assertEquals("", rows[1][11]);
    assertTrue(rowsWithinLimits > 0);
  }

  /** Extract, compile, and execute the literal complete Java program published in the guide. */
  @Test
  void literalSalesContractQuickStartCompilesAndRuns() throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    Path guidePath = repositoryRoot.resolve("docs/standards/sales_contracts.md");
    String guide = new String(Files.readAllBytes(guidePath), StandardCharsets.UTF_8);

    assertEquals(1, countOccurrences(guide, EXAMPLE_MARKER));
    Matcher fence = JAVA_FENCE.matcher(guide);
    assertTrue(fence.find(), "The marked gas sales-contract Java fence is missing");
    String exampleSource = fence.group(1);
    assertFalse(fence.find(), "The marked gas sales-contract Java fence must be unique");
    assertFalse(exampleSource.contains("System.out"));
    assertFalse(exampleSource.contains("System.err"));

    Matcher className = PUBLIC_CLASS.matcher(exampleSource);
    assertTrue(className.find(), "The guide example must declare a complete public class");
    assertEquals("GasSalesContractQuickStart", className.group(1));

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "Documentation examples require a JDK compiler");
    Path javaSource = temporaryDirectory.resolve("GasSalesContractQuickStart.java");
    Files.write(javaSource, exampleSource.getBytes(StandardCharsets.UTF_8));
    compile(compiler, javaSource);

    try (URLClassLoader loader =
        new URLClassLoader(
            new URL[] {temporaryDirectory.toUri().toURL()}, getClass().getClassLoader())) {
      loader.setDefaultAssertionStatus(true);
      Class<?> example = Class.forName("GasSalesContractQuickStart", true, loader);
      assertTrue(example.desiredAssertionStatus());
      try {
        example.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
      } catch (InvocationTargetException exception) {
        throw new AssertionError("GasSalesContractQuickStart failed", exception.getCause());
      }
    }
  }

  /**
   * Compile the extracted Markdown program with Java 8 syntax and the test classpath.
   *
   * @param compiler current JDK compiler
   * @param source extracted Java source
   * @throws Exception if source preparation or compilation fails
   */
  private void compile(JavaCompiler compiler, Path source) throws Exception {
    DiagnosticCollector<JavaFileObject> diagnostics =
        new DiagnosticCollector<JavaFileObject>();
    String classPath =
        System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    Iterable<String> options =
        Arrays.asList(
            "-source",
            "8",
            "-target",
            "8",
            "-proc:none",
            "-classpath",
            classPath,
            "-d",
            temporaryDirectory.toString());
    try (StandardJavaFileManager manager =
        compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
      Boolean successful =
          compiler
              .getTask(
                  null,
                  manager,
                  diagnostics,
                  options,
                  null,
                  manager.getJavaFileObjects(source.toFile()))
              .call();
      assertTrue(
          Boolean.TRUE.equals(successful),
          "docs/standards/sales_contracts.md: " + diagnostics.getDiagnostics());
    }
  }

  /**
   * Parse one numeric cell from the contract result table.
   *
   * @param value string value
   * @param rowIndex zero-based row index
   * @param columnIndex zero-based column index
   * @return parsed double
   */
  private static double parseDoubleOrFail(String value, int rowIndex, int columnIndex) {
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException | NullPointerException exception) {
      throw new AssertionError(
          "Expected a numeric contract result at row "
              + rowIndex
              + ", column "
              + columnIndex,
          exception);
    }
  }

  /**
   * Count exact non-overlapping occurrences of a marker.
   *
   * @param text text to search
   * @param token marker token
   * @return occurrence count
   */
  private static int countOccurrences(String text, String token) {
    int count = 0;
    int offset = 0;
    while ((offset = text.indexOf(token, offset)) >= 0) {
      count++;
      offset += token.length();
    }
    return count;
  }
}
