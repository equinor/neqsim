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

/** Compiles and executes the field-development planning guide. */
public class FieldDevelopmentPlanningDocumentationTest extends NeqSimTest {
  private static final String GUIDE = "docs/wiki/field_development_planning.md";
  private static final Pattern EXECUTABLE_JAVA =
      Pattern.compile(
          "(?ms)^## Executable field-development planning example.*?^\`\`\`java\\r?\\n([\\s\\S]*?)^\`\`\`[ \\t]*$");
  private static final Pattern ALL_JAVA =
      Pattern.compile("(?ms)^\`\`\`java\\r?\\n([\\s\\S]*?)^\`\`\`[ \\t]*$");
  private static final Pattern PUBLIC_CLASS =
      Pattern.compile("public\\s+(?:final\\s+)?class\\s+([A-Za-z][A-Za-z0-9_]*)");

  @TempDir Path temporaryDirectory;

  @Test
  void guideStatesCurrentApisUnitsAndEngineeringBoundaries() throws Exception {
    String guide = readGuide();
    String prose = guide.replaceAll("\\s+", " ");

    assertTrue(guide.contains("There is no \`FacilityCapacity\` class"));
    assertTrue(guide.contains("The constructor takes a \`ProcessSystem\`"));
    assertTrue(guide.contains("multiply the returned rate-years value by 365.25 day/year"));
    assertTrue(guide.contains("means a fractional 12% potential increase"));
    assertTrue(guide.contains("Schedule availability is a fraction from 0 to 1"));
    assertTrue(guide.contains("TieInCapacityPlanner"));
    assertTrue(guide.contains("ProductionOptimizer"));
    assertTrue(guide.contains("java -ea"));
    assertTrue(
        prose.contains(
            "do not replace reservoir history matching, detailed well modelling, facility design, operations planning, cost estimation, or an independent safety review"));
    assertFalse(guide.contains("new ProductionProfile(\""));
    assertFalse(guide.contains("new WellScheduler(\""));
    assertFalse(guide.contains("new FacilityCapacity"));
    assertFalse(guide.contains("System.out"));

    Matcher fences = ALL_JAVA.matcher(guide);
    assertTrue(fences.find(), "Guide must publish one Java program");
    assertFalse(fences.find(), "Guide must not contain unverified Java fragments");
  }

  @Test
  void publishedProgramCompilesAndRunsWithAssertions() throws Exception {
    Matcher fence = EXECUTABLE_JAVA.matcher(readGuide());

    assertTrue(fence.find(), "Executable field-development example is missing");
    String source = fence.group(1);
    assertTrue(source.contains("class FieldDevelopmentPlanningExample"));
    assertTrue(source.contains("new DeclineParameters("));
    assertTrue(source.contains("DeclineType.EXPONENTIAL"));
    assertTrue(source.contains("ProductionProfile.calculateRate(decline, 2.0)"));
    assertTrue(source.contains("ProductionProfile.calculateCumulativeProduction(decline, 5.0)"));
    assertTrue(source.contains("cumulativeRateYears * 365.25"));
    assertTrue(source.contains("new WellScheduler()"));
    assertTrue(source.contains("scheduler.addWell(\"Well-A1\", initialRateSm3PerDay, \"Sm3/day\")"));
    assertTrue(source.contains("InterventionType.COILED_TUBING"));
    assertTrue(source.contains(".expectedGain(0.12)"));
    assertTrue(source.contains(".cost(250000.0, \"USD\")"));
    assertTrue(source.contains("scheduler.optimizeSchedule("));
    assertTrue(source.contains("assert schedule.getOptimizedSchedule().size() == 1"));
    assertTrue(source.contains("LogManager.getLogger"));
    assertFalse(source.contains("System.out"));
    assertFalse(source.contains("FacilityCapacity"));
    compileAndRun(source);
    assertFalse(fence.find(), "Executable section must contain one Java program");
  }

  private String readGuide() throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    return new String(
        Files.readAllBytes(repositoryRoot.resolve(GUIDE)), StandardCharsets.UTF_8);
  }

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
