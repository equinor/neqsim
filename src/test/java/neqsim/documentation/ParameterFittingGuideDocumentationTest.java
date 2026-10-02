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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import neqsim.NeqSimTest;

/**
 * Verifies the parameter-fitting guide and executes its complete diagnostics program.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class ParameterFittingGuideDocumentationTest extends NeqSimTest {
  private static final String GUIDE = "docs/statistics/parameter_fitting.md";
  private static final Pattern JAVA_FENCE = Pattern.compile("```java\\s*(.*?)```", Pattern.DOTALL);
  private static final String PROGRAM_CLASS = "public final class ParameterFittingDiagnosticsExample";

  @TempDir
  Path temporaryDirectory;

  /** Verifies current diagnostic accessors, safeguards, and compatibility guidance. */
  @Test
  void guidePublishesCurrentArrayDiagnosticsProgram() throws Exception {
    String source = readGuide();
    String program = diagnosticsProgram(source);

    assertTrue(program.contains("getCovarianceMatrixArray()"));
    assertTrue(program.contains("getCorrelationMatrixArray()"));
    assertTrue(program.contains("result.isConverged()"));
    assertTrue(program.contains("Double.isFinite"));
    assertTrue(program.contains("assert result.isConverged()"));
    assertTrue(program.contains("LogManager.getLogger"));
    assertTrue(source.contains("defensive copies"));
    assertTrue(source.contains("getCovarianceMatrix()"));
    assertTrue(source.contains("getCorrelationMatrix()"));
    assertFalse(source.contains("result.getCovariance();"));
    assertFalse(source.contains("result.getCorrelation();"));
    assertFalse(program.contains("System.out"));
    assertFalse(program.contains("System.err"));
  }

  /** Compiles the exact diagnostics fence for Java 8 and runs it with assertions enabled. */
  @Test
  @Timeout(value = 180, unit = TimeUnit.SECONDS)
  void exactDiagnosticsProgramCompilesForJava8AndRuns() throws Exception {
    String program = diagnosticsProgram(readGuide());
    Path sourceDirectory = temporaryDirectory.resolve("source").resolve("examples");
    Path outputDirectory = temporaryDirectory.resolve("compiled");
    Files.createDirectories(sourceDirectory);
    Files.createDirectories(outputDirectory);
    Path sourceFile = sourceDirectory.resolve("ParameterFittingDiagnosticsExample.java");
    Files.write(sourceFile, program.getBytes(StandardCharsets.UTF_8));

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "Documentation examples require a JDK compiler");
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
    String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    Iterable<String> options = Arrays.asList("-source", "8", "-target", "8", "-classpath", classPath, "-d",
        outputDirectory.toString());

    try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
      Boolean successful = compiler
          .getTask(null, manager, diagnostics, options, null, manager.getJavaFileObjects(sourceFile.toFile())).call();
      assertTrue(Boolean.TRUE.equals(successful), diagnostics.getDiagnostics().toString());
    }

    try (URLClassLoader loader = new URLClassLoader(new URL[] {outputDirectory.toUri().toURL()},
        getClass().getClassLoader())) {
      loader.setDefaultAssertionStatus(true);
      Class<?> example = Class.forName("examples.ParameterFittingDiagnosticsExample", true, loader);
      assertTrue(example.desiredAssertionStatus());
      try {
        example.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
      } catch (InvocationTargetException exception) {
        throw new AssertionError("Parameter-fitting diagnostics example failed", exception.getCause());
      }
    }
  }

  /**
   * Reads the maintained parameter-fitting guide.
   *
   * @return guide text
   * @throws Exception if the guide cannot be read
   */
  private String readGuide() throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    return new String(Files.readAllBytes(repositoryRoot.resolve(GUIDE)), StandardCharsets.UTF_8);
  }

  /**
   * Extracts the complete diagnostics program from the guide.
   *
   * @param markdown guide source
   * @return exact Java program
   */
  private String diagnosticsProgram(String markdown) {
    Matcher matcher = JAVA_FENCE.matcher(markdown);
    List<String> matches = new ArrayList<String>();
    while (matcher.find()) {
      String candidate = matcher.group(1).trim() + "\n";
      if (candidate.contains(PROGRAM_CLASS)) {
        matches.add(candidate);
      }
    }
    assertEquals(1, matches.size(), "Expected exactly one complete diagnostics program");
    return matches.get(0);
  }
}
