package neqsim.process.util.fire;

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
 * Compiles and executes the complete example in
 * {@code docs/safety/blocked_in_liquid_thermal_expansion.md}.
 *
 * <p>
 * The exact published fence covers equation-of-state and local beta/kappa pressure-rise
 * screening without presenting the result as a relief-device design.
 * </p>
 *
 * @author OpenAI
 * @version 1.0
 */
public class BlockedInLiquidExpansionDocumentationTest {
  private static final String GUIDE =
      "docs/safety/blocked_in_liquid_thermal_expansion.md";
  private static final Pattern EXECUTABLE_JAVA =
      Pattern.compile(
          "(?ms)^## Executable Java Workflow.*?^```java\\r?\\n([\\s\\S]*?)^```[ \\t]*$");
  private static final Pattern ALL_JAVA =
      Pattern.compile("(?ms)^```java\\r?\\n([\\s\\S]*?)^```[ \\t]*$");
  private static final Pattern PUBLIC_CLASS =
      Pattern.compile("public\\s+(?:final\\s+)?class\\s+([A-Za-z][A-Za-z0-9_]*)");

  @TempDir
  Path temporaryDirectory;

  /** Verifies units, limitations, Jekyll title ownership, and the single-program contract. */
  @Test
  void guideStatesUnitsAndEngineeringBoundaries() throws Exception {
    String guide = readGuide();

    assertTrue(guide.contains("absolute K"));
    assertTrue(guide.contains("absolute pressures in Pa"));
    assertTrue(guide.contains("canonical pressure from bara to Pa"));
    assertTrue(guide.contains("coefficient $\\beta$ in 1/K"));
    assertTrue(guide.contains("$\\kappa$ in 1/Pa"));
    assertTrue(guide.contains("does not calculate thermal-relief flow"));
    assertTrue(guide.contains("does not prove that every trial or result is a single liquid phase"));
    assertTrue(guide.contains("not experimental"));
    assertTrue(guide.contains("accountable engineering review"));
    assertFalse(guide.contains("System.out"));
    assertFalse(guide.contains("```python"));
    assertFalse(guide.contains("# Blocked-In Liquid Thermal Expansion Screening"));

    Matcher fences = ALL_JAVA.matcher(guide);
    assertTrue(fences.find(), "Guide must publish one Java program");
    assertFalse(fences.find(), "Guide must not publish unverified Java fragments");
  }

  /** Compiles the exact Markdown fence for Java 8 and executes it with assertions enabled. */
  @Test
  void blockedInLiquidProgramExecutes() throws Exception {
    Matcher fence = EXECUTABLE_JAVA.matcher(readGuide());
    assertTrue(fence.find(), "Executable blocked-in liquid workflow is missing");
    String source = fence.group(1);

    assertTrue(source.contains("new SystemSrkEos(referenceTemperatureK, referencePressureBara)"));
    assertTrue(source.contains("computeIsochoricPressureProfile("));
    assertTrue(source.contains("estimateThermalExpansionCoefficient("));
    assertTrue(source.contains("estimateIsothermalCompressibility("));
    assertTrue(source.contains("simplifiedPressureRise("));
    assertTrue(source.contains("assert relativeDifference < 0.30"));
    assertTrue(source.contains("assert liquid.getTemperature() == referenceTemperatureK"));
    assertTrue(source.contains("logger.info("));
    assertFalse(source.contains("System.out"));

    compileAndRun(source);
    assertFalse(fence.find(), "Executable section must contain one Java program");
  }

  /** Reads the safety guide from the repository root. */
  private String readGuide() throws Exception {
    Path repositoryRoot =
        Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    return new String(
        Files.readAllBytes(repositoryRoot.resolve(GUIDE)), StandardCharsets.UTF_8);
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
        System.getProperty(
            "surefire.test.class.path", System.getProperty("java.class.path"));
    Iterable<String> options =
        Arrays.asList(
            "-source",
            "8",
            "-target",
            "8",
            "-classpath",
            classPath,
            "-d",
            outputDirectory.toString());
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
                  manager.getJavaFileObjects(javaSource.toFile()))
              .call();
      assertTrue(Boolean.TRUE.equals(successful), diagnostics.getDiagnostics().toString());
    }

    try (URLClassLoader loader =
        new URLClassLoader(
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
