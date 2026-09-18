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
import neqsim.process.safety.ProcessSafetyAnalyzer;
import neqsim.process.safety.ProcessSafetyScenario;
import neqsim.process.safety.release.LeakModel;
import neqsim.process.safety.release.ReleaseOrientation;
import neqsim.process.safety.release.SourceTermResult;
import neqsim.thermo.system.SystemInterface;

/** Compiles and executes the maintained safety-capability roadmap example. */
public class SafetyRoadmapDocumentationTest extends neqsim.NeqSimTest {
  private static final Pattern JAVA_FENCE = Pattern.compile("(?m)^```java\\r?\\n([\\s\\S]*?)^```[ \\t]*$");
  private static final Pattern PUBLIC_CLASS = Pattern
      .compile("public\\s+(?:final\\s+)?class\\s+([A-Za-z][A-Za-z0-9_]*)");
  private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[[^\\]]+\\]\\(([^)]+)\\)");
  private static final Pattern DUPLICATE_H1 = Pattern.compile("(?m)^# ");

  @TempDir
  Path temporaryDirectory;

  @Test
  void roadmapMatchesCurrentApisAndDocumentationBoundaries() throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    Path roadmapPath = repositoryRoot.resolve("docs/safety/SAFETY_SIMULATION_ROADMAP.md");
    String roadmap = read(roadmapPath);

    assertTrue(roadmap.startsWith("---\ntitle:"));
    String body = roadmap.split("---", 3)[2];
    String bodyWithoutFences = JAVA_FENCE.matcher(body).replaceAll("");
    assertFalse(DUPLICATE_H1.matcher(bodyWithoutFences).find());
    assertFalse(roadmap.contains("System.out"));
    assertFalse(roadmap.contains("90-95%"));
    assertFalse(roadmap.contains("Recently Implemented (2024)"));
    assertFalse(roadmap.contains("Not started"));
    assertFalse(roadmap.contains("docs/fire_blowdown_capabilities.md"));
    assertFalse(roadmap.contains("notebooks/VesselDepressurizationTutorial.ipynb"));

    assertNotNull(ProcessSafetyScenario.class.getMethod("builder", String.class));
    assertNotNull(ProcessSafetyAnalyzer.class.getMethod("analyze", ProcessSafetyScenario.class));
    assertNotNull(LeakModel.class.getMethod("builder"));
    assertNotNull(LeakModel.Builder.class.getMethod("fluid", SystemInterface.class));
    assertNotNull(LeakModel.Builder.class.getMethod("holeDiameter", double.class, String.class));
    assertNotNull(LeakModel.Builder.class.getMethod("backPressure", double.class, String.class));
    assertNotNull(LeakModel.Builder.class.getMethod("orientation", ReleaseOrientation.class));
    assertEquals(double.class, SourceTermResult.class.getMethod("getPeakMassFlowRate").getReturnType());
    assertNotNull(Class.forName("neqsim.process.safety.envelope.SafetyEnvelopeCalculator"));
    assertNotNull(Class.forName("neqsim.process.safety.risk.RiskModel"));

    Matcher links = MARKDOWN_LINK.matcher(roadmap);
    while (links.find()) {
      String target = links.group(1).split("#", 2)[0];
      if (target.isEmpty() || target.contains("://")) {
        continue;
      }
      assertFalse(target.endsWith(".md"), "Published links must be extensionless");
      Path resolved = roadmapPath.getParent().resolve(target + ".md").normalize();
      assertTrue(Files.isRegularFile(resolved), resolved.toString());
    }
  }

  @Test
  void publishedProgramCompilesAndRunsWithAssertions() throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    String roadmap = read(repositoryRoot.resolve("docs/safety/SAFETY_SIMULATION_ROADMAP.md"));
    Matcher fences = JAVA_FENCE.matcher(roadmap);

    assertTrue(fences.find(), "Roadmap must contain one complete Java program");
    String source = fences.group(1);
    assertTrue(source.contains("LogManager.getLogger"));
    assertTrue(source.contains("assert result.getPeakMassFlowRate()"));
    assertFalse(source.contains("System.out"));
    compileAndRun(source);
    assertFalse(fences.find(), "Roadmap must contain exactly one Java program");
  }

  private String read(Path path) throws Exception {
    return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
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
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
    String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    Iterable<String> options = Arrays.asList("-source", "8", "-target", "8", "-classpath", classPath, "-d",
        outputDirectory.toString());
    try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, null,
        StandardCharsets.UTF_8)) {
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
