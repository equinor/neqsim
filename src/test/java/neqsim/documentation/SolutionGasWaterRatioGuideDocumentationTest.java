package neqsim.documentation;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Compiles and executes the solution gas-water ratio guide example. */
public class SolutionGasWaterRatioGuideDocumentationTest extends neqsim.NeqSimTest {
  private static final Pattern JAVA_FENCE = Pattern.compile("(?m)^```java\\r?\\n([\\s\\S]*?)^```[ \\t]*$");
  private static final Pattern PUBLIC_CLASS = Pattern
      .compile("public\\s+(?:final\\s+)?class\\s+([A-Za-z][A-Za-z0-9_]*)");

  @TempDir
  Path temporaryDirectory;

  @Test
  void guideMatchesCurrentSourceBoundary() throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    String guide = read(repositoryRoot.resolve("docs/pvtsimulation/SolutionGasWaterRatio.md"));
    String normalizedGuide = guide.replaceAll("\\s+", " ");
    String source = read(
        repositoryRoot.resolve("src/main/java/neqsim/pvtsimulation/simulation/SolutionGasWaterRatio.java"));

    assertTrue(source.contains("public enum CalculationMethod"));
    assertTrue(source.contains("public void setSalinity(double salinity, String unit)"));
    assertTrue(source.contains("public void setTemperaturesAndPressures(double[] temperatures, double[] pressures)"));
    assertTrue(source.contains("public double calculateRsw(double temperatureK, double pressureBara)"));
    assertTrue(source.contains("new SystemSoreideWhitson(temperatureK, pressureBara)"));
    assertTrue(source.contains("new SystemElectrolyteCPAstatoil(temperatureK, pressureBara)"));
    assertTrue(source.contains("return 0.0; // No aqueous phase found"));
    assertTrue(source.contains("salinity / (molarMassNaCl * 1000.0)"));

    assertTrue(
        normalizedGuide.contains("The source gas composition is ignored. Use only as a methane/brine correlation"));
    assertTrue(normalizedGuide.contains("A returned zero is therefore not distinguishable from a physical zero"));
    assertTrue(normalizedGuide.contains("assume water density near 1 kg/L"));
    assertTrue(normalizedGuide.contains("defaults to `ELECTROLYTE_CPA`"));

    for (String rejected : Arrays.asList("System.out", "### Typical Values", "## Comparison with Literature",
        "Most accurate for saline systems", "Gas type | Any composition", "CO₂ is 20-50× more soluble")) {
      assertFalse(guide.contains(rejected), "Rejected Rsw guidance: " + rejected);
    }
  }

  @Test
  void referenceExampleCompilesAndRunsWithAssertions() throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    String guide = read(repositoryRoot.resolve("docs/pvtsimulation/SolutionGasWaterRatio.md"));

    Matcher fences = JAVA_FENCE.matcher(guide);
    assertTrue(fences.find(), "The guide must contain one complete Java example");
    String exampleSource = fences.group(1);
    assertFalse(fences.find(), "The guide should expose one maintained Java program");

    Matcher className = PUBLIC_CLASS.matcher(exampleSource);
    assertTrue(className.find(), "The example must be a complete public class");
    assertEquals("SolutionGasWaterRatioReferenceExample", className.group(1));
    assertTrue(exampleSource.contains("LogManager.getLogger"));
    assertTrue(exampleSource.contains("CalculationMethod.MCCAIN"));
    assertTrue(exampleSource.contains("assert pressureSeries[0] < pressureSeries[1]"));
    assertTrue(exampleSource.contains("assert salineWaterRsw < pureWaterRsw"));

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "Documentation examples require a JDK compiler");
    Path javaSource = temporaryDirectory.resolve("SolutionGasWaterRatioReferenceExample.java");
    Files.write(javaSource, exampleSource.getBytes(StandardCharsets.UTF_8));
    compile(compiler, javaSource);

    try (URLClassLoader loader = new URLClassLoader(new URL[] { temporaryDirectory.toUri().toURL() },
        getClass().getClassLoader())) {
      loader.setDefaultAssertionStatus(true);
      Class<?> example = Class.forName("SolutionGasWaterRatioReferenceExample", true, loader);
      assertTrue(example.desiredAssertionStatus());
      try {
        example.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
      } catch (InvocationTargetException exception) {
        throw new AssertionError("SolutionGasWaterRatioReferenceExample failed", exception.getCause());
      }
    }
  }

  private String read(Path path) throws Exception {
    return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
  }

  /**
   * Compile the extracted Markdown program with Java 8 syntax and the test classpath.
   *
   * @param compiler current JDK compiler
   * @param source extracted Java source
   * @throws Exception if source preparation or compilation fails
   */
  private void compile(JavaCompiler compiler, Path source) throws Exception {
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
    String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    Iterable<String> options = Arrays.asList("-source", "8", "-target", "8", "-classpath", classPath, "-d",
        temporaryDirectory.toString());
    try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
      Boolean successful = compiler
          .getTask(null, manager, diagnostics, options, null, manager.getJavaFileObjects(source.toFile())).call();
      assertTrue(Boolean.TRUE.equals(successful),
          "docs/pvtsimulation/SolutionGasWaterRatio.md: " + diagnostics.getDiagnostics());
    }
  }
}
