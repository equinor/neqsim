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

/** Compiles and executes the exact Java program published in the advanced risk tutorial. */
class AdvancedRiskFrameworkTutorialDocumentationTest extends NeqSimTest {
  private static final String GUIDE =
      "docs/examples/AdvancedRiskFramework_Tutorial.md";
  private static final Pattern JAVA_FENCE =
      Pattern.compile("(?m)^```java\\r?\\n([\\s\\S]*?)^```[ \\t]*$");
  private static final Pattern PUBLIC_CLASS = Pattern
      .compile("public\\s+(?:final\\s+)?class\\s+([A-Za-z][A-Za-z0-9_]*)");

  @TempDir
  Path temporaryDirectory;

  @Test
  void publishedProgramCompilesAndRunsWithAssertions() throws Exception {
    Path repositoryRoot =
        Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    String guide = read(repositoryRoot.resolve(GUIDE));
    Matcher fences = JAVA_FENCE.matcher(guide);

    assertTrue(fences.find(), "Tutorial must contain one complete Java program");
    String source = fences.group(1);
    assertTrue(source.contains("ConditionBasedReliability"));
    assertTrue(source.contains("SafetyInstrumentedFunction.builder()"));
    assertTrue(source.contains("BowTieModel"));
    assertTrue(source.contains("getProbabilityOfFailure(8760.0)"));
    assertTrue(source.contains("assert Math.abs(riskReductionFactor - 200.0)"));
    assertTrue(source.contains(
        "assert Math.abs(bowTie.getMitigatedFrequency() - 5.0e-5)"));
    assertTrue(source.contains(
        "LogManager.getLogger(AdvancedRiskFrameworkExample.class)"));
    assertFalse(source.contains("System.out"));
    assertFalse(source.contains("SISIntegratedRiskModel(\"HP Separator"));
    assertFalse(source.contains("BowTieAnalyzer"));
    assertFalse(source.contains("PortfolioRiskAnalyzer(\"North Sea Assets\")"));
    compileAndRun(source);
    assertFalse(fences.find(), "Tutorial must contain exactly one Java program");
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
    DiagnosticCollector<JavaFileObject> diagnostics =
        new DiagnosticCollector<JavaFileObject>();
    String classPath = System.getProperty(
        "surefire.test.class.path", System.getProperty("java.class.path"));
    Iterable<String> options = Arrays.asList(
        "-source", "8", "-target", "8", "-classpath", classPath,
        "-d", outputDirectory.toString());
    try (StandardJavaFileManager manager = compiler.getStandardFileManager(
        diagnostics, null, StandardCharsets.UTF_8)) {
      Boolean successful = compiler.getTask(
          null, manager, diagnostics, options, null,
          manager.getJavaFileObjects(javaSource.toFile())).call();
      assertTrue(
          Boolean.TRUE.equals(successful), diagnostics.getDiagnostics().toString());
    }

    try (URLClassLoader loader = new URLClassLoader(
        new URL[] {outputDirectory.toUri().toURL()},
        getClass().getClassLoader())) {
      loader.setDefaultAssertionStatus(true);
      Class<?> example = Class.forName(name, true, loader);
      assertTrue(example.desiredAssertionStatus());
      try {
        example.getMethod("main", String[].class)
            .invoke(null, (Object) new String[0]);
      } catch (InvocationTargetException exception) {
        throw new AssertionError(name + " failed", exception.getCause());
      }
    }
  }
}
