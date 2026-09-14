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

/** Compiles and executes the manifold mechanical-design screening example. */
public class ManifoldMechanicalDesignGuideDocumentationTest extends neqsim.NeqSimTest {
  private static final Pattern JAVA_FENCE = Pattern.compile("(?m)^```java\\r?\\n([\\s\\S]*?)^```[ \\t]*$");
  private static final Pattern PUBLIC_CLASS = Pattern
      .compile("public\\s+(?:final\\s+)?class\\s+([A-Za-z][A-Za-z0-9_]*)");

  @TempDir
  Path temporaryDirectory;

  @Test
  void guideMatchesCurrentSourceBoundary() throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    String guide = read(repositoryRoot.resolve("docs/process/equipment/manifold_design.md"));
    String normalizedGuide = guide.replaceAll("\\s+", " ");
    String calculator = read(repositoryRoot
        .resolve("src/main/java/neqsim/process/mechanicaldesign/manifold/ManifoldMechanicalDesignCalculator.java"));
    String bridge = read(
        repositoryRoot.resolve("src/main/java/neqsim/process/mechanicaldesign/manifold/ManifoldMechanicalDesign.java"));
    String baseDesign = read(
        repositoryRoot.resolve("src/main/java/neqsim/process/mechanicaldesign/MechanicalDesign.java"));

    assertTrue(calculator.contains("Design pressure in MPa"));
    assertTrue(calculator.contains("public void setDesignPressure(double designPressure)"));
    assertTrue(calculator.contains("public boolean performDesignVerification()"));
    assertTrue(calculator.contains("public enum ManifoldLocation"));
    assertTrue(calculator.contains("public enum ManifoldType"));
    assertTrue(bridge.contains("calculator.setDesignPressure(getMaxOperationPressure() * 1.1)"));
    assertTrue(baseDesign.contains("maximum operating pressure in bara"));

    assertTrue(normalizedGuide.contains("Do not treat that bridge as a unit-safe design calculation"));
    assertTrue(normalizedGuide.contains("does not use that value in a collapse or combined-pressure check"));
    assertTrue(normalizedGuide.contains("not evidence that a complete standards review was performed"));
    assertTrue(normalizedGuide.contains("Neither result is a lifting, transport, installation, or structural design"));

    for (String rejected : Arrays.asList("System.out", "comprehensive mechanical design capabilities",
        "follows these industry standards", "Always specify water depth", "NACE compliant A106-B")) {
      assertFalse(guide.contains(rejected), "Rejected manifold-design claim: " + rejected);
    }
    assertFalse(Pattern.compile("(?m)^# Manifold Mechanical Design Guide$").matcher(guide).find());
  }

  @Test
  void screeningExampleCompilesAndRunsWithAssertions() throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    String guide = read(repositoryRoot.resolve("docs/process/equipment/manifold_design.md"));

    Matcher fences = JAVA_FENCE.matcher(guide);
    assertTrue(fences.find(), "The guide must contain one complete Java example");
    String exampleSource = fences.group(1);
    assertFalse(fences.find(), "The guide should expose one maintained Java program");
    Matcher className = PUBLIC_CLASS.matcher(exampleSource);
    assertTrue(className.find(), "The example must be a complete public class");
    assertEquals("ManifoldMechanicalDesignScreeningExample", className.group(1));
    assertTrue(exampleSource.contains("LogManager.getLogger"));
    assertTrue(exampleSource.contains("performDesignVerification"));
    assertTrue(exampleSource.contains("assert screeningPass"));

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "Documentation examples require a JDK compiler");
    Path javaSource = temporaryDirectory.resolve("ManifoldMechanicalDesignScreeningExample.java");
    Files.write(javaSource, exampleSource.getBytes(StandardCharsets.UTF_8));
    compile(compiler, javaSource);

    try (URLClassLoader loader = new URLClassLoader(new URL[] { temporaryDirectory.toUri().toURL() },
        getClass().getClassLoader())) {
      loader.setDefaultAssertionStatus(true);
      Class<?> example = Class.forName("ManifoldMechanicalDesignScreeningExample", true, loader);
      assertTrue(example.desiredAssertionStatus());
      try {
        example.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
      } catch (InvocationTargetException exception) {
        throw new AssertionError("ManifoldMechanicalDesignScreeningExample failed", exception.getCause());
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
          "docs/process/equipment/manifold_design.md: " + diagnostics.getDiagnostics());
    }
  }
}
