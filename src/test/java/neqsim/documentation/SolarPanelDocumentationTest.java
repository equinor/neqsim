package neqsim.documentation;

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
import neqsim.NeqSimTest;

/**
 * Compiles and executes the SolarPanel documentation example.
 *
 * @author OpenAI
 * @version 1.0
 */
public class SolarPanelDocumentationTest extends NeqSimTest {
  private static final String GUIDE = "docs/wiki/solar_panel.md";
  private static final Pattern EXECUTABLE_JAVA =
      Pattern.compile(
          "(?ms)^## Executable solar-panel screening example.*?^```java\\r?\\n([\\s\\S]*?)^```[ \\t]*$");
  private static final Pattern ALL_JAVA =
      Pattern.compile("(?ms)^```java\\r?\\n([\\s\\S]*?)^```[ \\t]*$");
  private static final Pattern PUBLIC_CLASS =
      Pattern.compile("public\\s+(?:final\\s+)?class\\s+([A-Za-z][A-Za-z0-9_]*)");

  @TempDir Path temporaryDirectory;

  /**
   * Verifies that the guide states its units, sign convention, validation, and model boundary.
   *
   * @throws Exception if the guide cannot be read
   */
  @Test
  void guideStatesUnitsSignConventionAndBoundary() throws Exception {
    String guide = readGuide();
    String prose = guide.replaceAll("\\s+", " ");

    assertTrue(guide.contains("irradiance in `W/m²`"));
    assertTrue(guide.contains("panel area in `m²`"));
    assertTrue(guide.contains("efficiency must be finite and between `0.0` and `1.0`"));
    assertTrue(guide.contains("negative duty in `W`"));
    assertTrue(prose.contains("does not calculate sun angle, shading, spectral response"));
    assertTrue(guide.contains("java -ea"));
    assertFalse(guide.contains("System.out"));

    Matcher fences = ALL_JAVA.matcher(guide);
    assertTrue(fences.find(), "Guide must publish one Java program");
    assertFalse(fences.find(), "Guide must not contain unverified Java fragments");
  }

  /**
   * Compiles the exact Java fence for Java 8 and executes it with assertions enabled.
   *
   * @throws Exception if extraction, compilation, or execution fails
   */
  @Test
  void publishedProgramCompilesAndRunsWithAssertions() throws Exception {
    Matcher fence = EXECUTABLE_JAVA.matcher(readGuide());

    assertTrue(fence.find(), "Executable SolarPanel example is missing");
    String source = fence.group(1);
    assertTrue(source.contains("class SolarPanelScreeningExample"));
    assertTrue(source.contains("panel.setIrradiance(irradianceWPerSquareMetre)"));
    assertTrue(source.contains("panel.setPanelArea(panelAreaSquareMetres)"));
    assertTrue(source.contains("panel.setEfficiency(efficiencyFraction)"));
    assertTrue(source.contains("assert Math.abs(generatedPowerW - 320.0) < 1.0e-9"));
    assertTrue(source.contains("assert Math.abs(electricalDutyW + generatedPowerW) < 1.0e-9"));
    assertTrue(source.contains("LogManager.getLogger"));
    assertFalse(source.contains("System.out"));
    compileAndRun(source);
    assertFalse(fence.find(), "Executable section must contain one Java program");
  }

  /**
   * Reads the guide from the current Maven repository.
   *
   * @return guide content
   * @throws Exception if the guide cannot be read
   */
  private String readGuide() throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    return new String(
        Files.readAllBytes(repositoryRoot.resolve(GUIDE)), StandardCharsets.UTF_8);
  }

  /**
   * Compiles one complete Java source fence and invokes its main method with assertions enabled.
   *
   * @param source exact Java source from the guide
   * @throws Exception if compilation or execution fails
   */
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
