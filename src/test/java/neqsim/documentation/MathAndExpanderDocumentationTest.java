package neqsim.documentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

/**
 * Compiles the reader-facing snippets, then checks their numerical and physical results.
 *
 * <p>
 * Source extraction prevents a separately maintained test copy from hiding broken documentation.
 * </p>
 */
class MathAndExpanderDocumentationTest {
  @TempDir
  Path temporaryDirectory;

  private static final Pattern EXAMPLE = Pattern
      .compile("<!-- doc-test: ([a-z0-9-]+) -->\\s*```java\\r?\\n(.*?)\\r?\\n```", Pattern.DOTALL);
  private static final Pattern IMPORT = Pattern.compile("(?m)^import [^;]+;\\s*$");

  /**
   * Compile and execute all explicitly covered examples, rejecting missing or duplicate examples.
   *
   * @return independently reported documentation examples
   * @throws Exception if a source document cannot be read
   */
  @TestFactory
  List<DynamicTest> documentedExamples() throws Exception {
    Map<String, String> checks = new LinkedHashMap<String, String>();
    checks.put("math-newton", "assertEquals(Math.sqrt(2.0), root, 1e-9); assertTrue(residual < 1e-9);");
    checks.put("math-brent", "assertEquals(1.5213797068045676, root, 1e-9);");
    checks.put("math-bisection", "assertEquals(Math.PI / 6.0, root, 1e-9);");
    checks.put("math-derivative", "assertEquals(Math.E, derivative, 1e-9);");
    checks.put("math-tdma",
        "for (int i = 0; i < d.length; i++) {" + "double sum = b[i] * solution[i];"
            + "if (i > 0) { sum += a[i] * solution[i - 1]; }"
            + "if (i + 1 < d.length) { sum += c[i] * solution[i + 1]; }" + "assertEquals(d[i], sum, 1e-12); }");
    checks.put("math-spline",
        "assertEquals(4.0, valueAtKnot, 1e-12);" + "assertEquals(6.263157894736842, interpolated, 1e-12);");
    checks.put("math-functions", "assertEquals(Math.log(2.0), logValue, 1e-12);"
        + "assertEquals(17.0, polynomialValue, 1e-12); assertEquals(4.0, linearValue, 1e-12);");
    checks.put("math-matrix", "assertEquals(0.0, residual, 1e-12);" + "assertEquals(0.5, solution.get(1, 0), 1e-12);");
    checks.put("math-minimum", "assertEquals(2.0, minimumLocation, 1e-8);" + "assertEquals(1.0, minimumValue, 1e-12);");
    checks.put("expander-basic",
        "assertTrue(Double.isFinite(recoveredPowerKW) && recoveredPowerKW > 0.0);"
            + "assertTrue(outletTemperatureC < feed.getTemperature(\"C\"));"
            + "assertEquals(20.0, expander.getOutletStream().getPressure(\"bara\"), 1e-9);"
            + "assertEquals(50000.0, outletMassFlow, 1e-6);"
            + "assertEquals(recoveredPowerKW / 5000.0, powerUtilization, 1e-9);");
    checks.put("expander-ngl", "assertTrue(Double.isFinite(nglKgPerHour) && nglKgPerHour > 0.0);"
        + "assertEquals(feed.getFlowRate(\"kg/hr\"), totalOutletKgPerHour, 1e-6);");
    checks.put("expander-shaft",
        "assertTrue(Double.isFinite(extraCoolingK) && extraCoolingK > 0.0);"
            + "assertEquals(-expander.getPower(\"kW\") - compressor.getPower(\"kW\"), shaftSurplusKW, 1e-9);"
            + "assertEquals(10000.0, expander.getOutletStream().getFlowRate(\"kg/hr\"), 1e-6);");
    checks.put("thermo-electrolyte",
        "assertTrue(Double.isFinite(aqueousDensity));"
            + "assertTrue(aqueousDensity > 900.0 && aqueousDensity < 1200.0);"
            + "assertEquals(1.102, brine.getTotalNumberOfMoles(), 1e-10);");
    checks.put("thermo-umr", "assertTrue(Double.isFinite(density) && density > 300.0 && density < 800.0);"
        + "assertEquals(1.0, lng.getTotalNumberOfMoles(), 1e-10);");
    checks.put("reliability-record",
        "assertEquals(5464.0 / 5488.0, availability, 1e-12);" + "assertEquals(1.0 / 5464.0, failuresPerHour, 1e-15);");

    List<DynamicTest> tests = new ArrayList<DynamicTest>();
    Set<String> seen = new LinkedHashSet<String>();
    for (String document : Arrays.asList("docs/mathlib/README.md", "docs/process/equipment/expanders.md",
        "docs/thermo/system/README.md", "docs/risk/RELIABILITY_DATA_GUIDE.md")) {
      String markdown = new String(Files.readAllBytes(Paths.get(document)), StandardCharsets.UTF_8);
      Matcher matcher = EXAMPLE.matcher(markdown);
      while (matcher.find()) {
        String id = matcher.group(1);
        String snippet = matcher.group(2);
        assertTrue(seen.add(id), "Duplicate example " + id);
        assertTrue(checks.containsKey(id), "Add result checks for " + id);
        tests.add(DynamicTest.dynamicTest(document + ": " + id, () -> compileAndRun(id, snippet, checks.get(id))));
      }
    }
    assertEquals(checks.keySet(), seen, "Every expected example must remain present");
    return tests;
  }

  private void compileAndRun(String id, String snippet, String checks) throws Exception {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "Documentation examples require a JDK");
    String className = "Documented_" + id.replace('-', '_');
    Matcher imports = IMPORT.matcher(snippet);
    StringBuilder source = new StringBuilder("import static org.junit.jupiter.api.Assertions.*;\n");
    while (imports.find()) {
      source.append(imports.group()).append('\n');
    }
    source.append("public class ").append(className).append(" { public static void verify() throws Exception {\n")
        .append(IMPORT.matcher(snippet).replaceAll("")).append('\n').append(checks).append("\n} }\n");
    Path directory = Files.createDirectory(temporaryDirectory.resolve(id));
    Path javaFile = directory.resolve(className + ".java");
    Files.write(javaFile, source.toString().getBytes(StandardCharsets.UTF_8));
    String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    StringWriter diagnostics = new StringWriter();
    try (StandardJavaFileManager manager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
      boolean compiled = compiler.getTask(diagnostics, manager, null,
          Arrays.asList("-classpath", classpath, "-d", directory.toString(), "-source", "8", "-target", "8"), null,
          manager.getJavaFileObjects(javaFile.toFile())).call();
      assertTrue(compiled, id + " failed to compile:\n" + diagnostics);
    }
    try (URLClassLoader loader = new URLClassLoader(new URL[] { directory.toUri().toURL() },
        getClass().getClassLoader())) {
      try {
        loader.loadClass(className).getMethod("verify").invoke(null);
      } catch (InvocationTargetException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof AssertionError) {
          throw (AssertionError) cause;
        }
        throw new AssertionError(id + " failed during execution", cause);
      }
    }
  }
}
