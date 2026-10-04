package neqsim.process.equipment.adsorber;

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

/**
 * Compiles and executes the complete example in {@code docs/process/mercury_removal.md}.
 *
 * <p>The exact published fence covers steady removal, pressure drop, bounded transient loading,
 * mechanical design, and cost screening.</p>
 *
 * @author OpenAI
 * @version 1.0
 */
public class MercuryRemovalDocumentationTest {
  private static final String GUIDE = "docs/process/mercury_removal.md";
  private static final Pattern EXECUTABLE_JAVA = Pattern.compile(
      "(?ms)^## Executable steady and transient workflow.*?^\`\`\`java\\r?\\n([\\s\\S]*?)^\`\`\`[ \\t]*$");
  private static final Pattern ALL_JAVA =
      Pattern.compile("(?ms)^\`\`\`java\\r?\\n([\\s\\S]*?)^\`\`\`[ \\t]*$");
  private static final Pattern PUBLIC_CLASS =
      Pattern.compile("public\\s+(?:final\\s+)?class\\s+([A-Za-z][A-Za-z0-9_]*)");

  @TempDir
  Path temporaryDirectory;

  /** Verifies units, design limits, and the single-program contract. */
  @Test
  void guideStatesUnitsAndEngineeringBoundaries() throws Exception {
    String guide = readGuide();

    assertTrue(guide.contains("temperature in K"));
    assertTrue(guide.contains("absolute pressure in bara"));
    assertTrue(guide.contains("feed flow in kg/hr"));
    assertTrue(guide.contains("pressure drop in Pa and bar"));
    assertTrue(guide.contains("sorbent loading in mg/kg"));
    assertTrue(guide.contains("elapsed time in hours"));
    assertTrue(guide.contains("wall thickness in"));
    assertTrue(guide.contains("equipment mass in kg"));
    assertTrue(guide.contains("cost in nominal USD"));
    assertTrue(guide.contains("not a substitute for\ncode-compliant ASME design"));
    assertTrue(guide.contains("screening estimates, not vendor quotes"));
    assertTrue(guide.contains("sorbent-specific validation"));
    assertFalse(guide.contains("System.out"));
    assertFalse(guide.contains("\`\`\`python"));
    assertFalse(guide.contains("# Mercury Removal Guard Beds"));

    Matcher fences = ALL_JAVA.matcher(guide);
    assertTrue(fences.find(), "Guide must publish one Java program");
    assertFalse(fences.find(), "Guide must not publish unverified Java fragments");
  }

  /** Compiles the exact Markdown fence for Java 8 and executes it with assertions enabled. */
  @Test
  void mercuryRemovalProgramExecutes() throws Exception {
    Matcher fence = EXECUTABLE_JAVA.matcher(readGuide());
    assertTrue(fence.find(), "Executable mercury-removal workflow is missing");
    String source = fence.group(1);

    assertTrue(source.contains("new SystemSrkEos(temperatureK, pressureBara)"));
    assertTrue(source.contains("feed.setFlowRate(feedFlowKgPerHour, \"kg/hr\")"));
    assertTrue(source.contains("steadyBed.run(UUID.randomUUID())"));
    assertTrue(source.contains("steadyBed.getPressureDrop(\"bar\")"));
    assertTrue(source.contains("design.calcDesign()"));
    assertTrue(source.contains("cost.calculateCostEstimate()"));
    assertTrue(source.contains("transientBed.runTransient(60.0, transientId)"));
    assertTrue(source.contains("assert averageLoadingMgPerKg > 0.0"));
    assertTrue(source.contains("logger.info("));
    assertFalse(source.contains("System.out"));

    compileAndRun(source);
    assertFalse(fence.find(), "Executable section must contain one Java program");
  }

  /** Reads the workflow guide from the repository root. */
  private String readGuide() throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    return new String(Files.readAllBytes(repositoryRoot.resolve(GUIDE)), StandardCharsets.UTF_8);
  }

  /** Compiles one extracted Java source and invokes its main method with assertions enabled. */
  private void compileAndRun(String source) throws Exception {
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
    DiagnosticCollector<JavaFileObject> diagnostics =
        new DiagnosticCollector<JavaFileObject>();
    String classPath =
        System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    Iterable<String> options = Arrays.asList("-source", "8", "-target", "8", "-classpath",
        classPath, "-d", outputDirectory.toString());
    try (StandardJavaFileManager manager =
        compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
      Boolean successful = compiler.getTask(null, manager, diagnostics, options, null,
          manager.getJavaFileObjects(javaSource.toFile())).call();
      assertTrue(Boolean.TRUE.equals(successful), diagnostics.getDiagnostics().toString());
    }

    try (URLClassLoader loader = new URLClassLoader(
        new URL[] {outputDirectory.toUri().toURL()}, getClass().getClassLoader())) {
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
