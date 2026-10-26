package neqsim.pvtsimulation.flowassurance;

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
import java.util.Locale;
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

/** Verifies the executable De Boer guide and its engineering-use boundaries. */
public class DeBoerScreeningDocumentationTest extends NeqSimTest {
  private static final Pattern EXACT_EXAMPLE = Pattern
      .compile("(?s)<!-- deboer-screening-example:start -->\\s*```java\\r?\\n(.*?)\\r?\\n```\\s*"
          + "<!-- deboer-screening-example:end -->");
  private static final Pattern JAVA_FENCE = Pattern.compile("(?m)^```java[ \\t]*$");
  private static final Pattern PUBLIC_CLASS = Pattern
      .compile("public\\s+(?:final\\s+)?class\\s+([A-Za-z][A-Za-z0-9_]*)");
  private static final Pattern DUPLICATE_H1 = Pattern.compile("(?m)^#\\s+");

  @TempDir
  Path temporaryDirectory;

  /**
   * Extracts, compiles, and runs the exact published Java program with assertions enabled.
   *
   * @throws Exception if the documentation cannot be read, compiled, loaded, or executed
   */
  @Test
  void publishedDeBoerProgramCompilesAndRunsWithAssertions() throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    String guide = read(repositoryRoot
        .resolve("docs/pvtsimulation/flowassurance/asphaltene_deboer_screening.md"));
    Matcher exactExample = EXACT_EXAMPLE.matcher(guide);

    assertTrue(exactExample.find(), "Guide must contain the marked exact Java program");
    String source = exactExample.group(1);
    assertFalse(exactExample.find(), "Guide must contain exactly one marked exact Java program");
    Matcher javaFences = JAVA_FENCE.matcher(guide);
    assertTrue(javaFences.find(), "Guide must contain one Java fence");
    assertFalse(javaFences.find(), "Guide must contain exactly one Java fence");
    assertTrue(source.contains("LogManager.getLogger"));
    assertTrue(source.contains("assert risk == DeBoerRisk.MODERATE_PROBLEM;"));
    assertTrue(source.contains("assert Math.abs(riskIndex - 1.6) < 1.0e-12;"));
    assertFalse(source.contains("System.out"));
    assertFalse(source.contains("System.err"));

    compileAndRun(source);
  }

  /**
   * Protects the documented input contract and limitations from unsupported claims.
   *
   * @throws Exception if the documentation cannot be read
   */
  @Test
  void guideRetainsInputAndEngineeringBoundaries() throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    String guide = read(repositoryRoot
        .resolve("docs/pvtsimulation/flowassurance/asphaltene_deboer_screening.md"));
    String normalized = guide.replaceAll("\\s+", " ");
    String lowerGuide = normalized.toLowerCase(Locale.ROOT);

    assertTrue(guide.startsWith("---\ntitle:"));
    assertFalse(DUPLICATE_H1.matcher(guide).find());
    assertTrue(guide.contains("| Reservoir pressure | bara |"));
    assertTrue(guide.contains("| Saturation pressure | bara |"));
    assertTrue(guide.contains("| In-situ oil density | kg/m³ |"));
    assertTrue(normalized.contains("It is not an asphaltene-onset pressure"));
    assertTrue(normalized.contains(
        "It does not predict deposition rate, plugging, or inhibitor dose."));
    assertTrue(normalized.contains(
        "validate against measured SARA data and onset/deposition observations"));
    assertFalse(lowerGuide.contains("production-ready"));
  }

  /**
   * Reads a UTF-8 documentation file.
   *
   * @param path path to read
   * @return complete file contents
   * @throws Exception if the file cannot be read
   */
  private String read(Path path) throws Exception {
    return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
  }

  /**
   * Compiles and runs a complete Java source string with runtime assertions enabled.
   *
   * @param source complete Java source
   * @throws Exception if compilation, class loading, or execution fails
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
    Iterable<String> options = Arrays.asList("-source", "8", "-target", "8", "-classpath",
        classPath, "-d", outputDirectory.toString());
    try (StandardJavaFileManager manager =
        compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
      Boolean successful = compiler
          .getTask(null, manager, diagnostics, options, null,
              manager.getJavaFileObjects(javaSource.toFile()))
          .call();
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
