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

/** Compiles and executes the maintained thermal-utility screening guides. */
public class ThermalUtilityDocumentationTest extends neqsim.NeqSimTest {
  private static final Pattern JAVA_FENCE =
      Pattern.compile("(?m)^```java\\r?\\n([\\s\\S]*?)^```[ \\t]*$");
  private static final Pattern PUBLIC_CLASS =
      Pattern.compile("public\\s+(?:final\\s+)?class\\s+([A-Za-z][A-Za-z0-9_]*)");
  private static final Pattern DUPLICATE_H1 = Pattern.compile("(?m)^# ");

  @TempDir
  Path temporaryDirectory;

  @Test
  void guidesMatchCurrentApisAndDocumentationBoundaries() throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    String hydraulics =
        read(repositoryRoot.resolve("docs/process/thermal_utility_hydraulics.md"));
    String quality = read(repositoryRoot.resolve("docs/process/thermal_utility_quality.md"));

    assertNotNull(neqsim.process.equipment.energy.ThermalUtilityHydraulicModel.class.getMethod(
        "setGeometry", double.class, double.class, double.class));
    assertEquals(double.class,
        neqsim.process.equipment.energy.UtilityEnergyBus.class
            .getMethod("getMassFlowForDuty", double.class).getReturnType());
    assertEquals(boolean.class,
        neqsim.process.equipment.energy.ThermalUtilityQualityAnalysis.class
            .getMethod("canServeProcessTemperature",
                neqsim.process.equipment.energy.UtilityEnergyBus.class, double.class,
                double.class)
            .getReturnType());
    assertNotNull(neqsim.process.equipment.energy.ThermalUtilityConsumer.class.getMethod(
        "setProcessTemperatureRequirement", double.class, double.class));

    for (String guide : Arrays.asList(hydraulics, quality)) {
      assertTrue(guide.startsWith("---\n"), "Guide must retain Jekyll front matter");
      assertFalse(DUPLICATE_H1.matcher(guide).find(),
          "Front-matter title must not be repeated as a Markdown H1");
      assertFalse(guide.contains("\\["), "Use supported display-math delimiters");
      assertFalse(guide.contains("\\]"), "Use supported display-math delimiters");
      assertFalse(guide.contains("\\("), "Use supported inline-math delimiters");
      assertFalse(guide.contains("\\)"), "Use supported inline-math delimiters");
      assertFalse(guide.contains("System.out"), "Examples must use Log4j2");
    }

    assertTrue(hydraulics.contains("getMassFlowForDuty(thermalDutyW)"));
    assertTrue(hydraulics.contains("getMaximumMassFlow()"));
    assertTrue(hydraulics.contains("compressible Fanno flow"));
    assertFalse(hydraulics.contains("coolingWaterBus.getServedMassFlow()"));

    assertTrue(quality.contains("reboiler.validateSetup().isValid()"));
    assertTrue(quality.contains("getExergyRateForDuty"));
    assertTrue(quality.contains("do not perform the network allocation"));
  }

  @Test
  void publishedProgramsCompileAndRunWithAssertions() throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    int programCount = 0;
    for (String relativePath : Arrays.asList("docs/process/thermal_utility_hydraulics.md",
        "docs/process/thermal_utility_quality.md")) {
      String guide = read(repositoryRoot.resolve(relativePath));
      Matcher fences = JAVA_FENCE.matcher(guide);
      int guideProgramCount = 0;
      while (fences.find()) {
        String source = fences.group(1);
        assertTrue(source.contains("LogManager.getLogger"));
        assertTrue(source.contains("assert "));
        assertFalse(source.contains("System.out"));
        compileAndRun(relativePath, source);
        guideProgramCount++;
        programCount++;
      }
      assertEquals(1, guideProgramCount, relativePath + " must expose one complete program");
    }
    assertEquals(2, programCount);
  }

  private String read(Path path) throws Exception {
    return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
  }

  private void compileAndRun(String guidePath, String source) throws Exception {
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
    Iterable<String> options = Arrays.asList("-source", "8", "-target", "8",
        "-classpath", classPath, "-d", outputDirectory.toString());
    try (StandardJavaFileManager manager =
        compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
      Boolean successful = compiler
          .getTask(null, manager, diagnostics, options, null,
              manager.getJavaFileObjects(javaSource.toFile()))
          .call();
      assertTrue(Boolean.TRUE.equals(successful),
          guidePath + ": " + diagnostics.getDiagnostics());
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
