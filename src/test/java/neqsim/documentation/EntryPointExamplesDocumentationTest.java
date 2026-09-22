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

/** Compiles the root Java quickstart and guards the primary Python entry-point guidance. */
public class EntryPointExamplesDocumentationTest extends NeqSimTest {
  private static final String README = "README.md";
  private static final String PYTHON_GUIDE = "docs/quickstart/python-quickstart.md";
  private static final Pattern README_JAVA = Pattern
      .compile("(?ms)^### Java - add to your project.*?^```java\\r?\\n([\\s\\S]*?)^```[ \\t]*$");
  private static final Pattern PUBLIC_CLASS = Pattern
      .compile("public\\s+(?:final\\s+)?class\\s+([A-Za-z][A-Za-z0-9_]*)");
  private static final Pattern UNITLESS_PRESSURE = Pattern.compile("setOutletPressure\\(\\s*80\\.0\\s*\\)");

  @TempDir
  Path temporaryDirectory;

  @Test
  void primaryEntryPointsUseExplicitUnitsAndTruthfulCorrectionGuidance() throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    String readme = read(repositoryRoot.resolve(README));
    String pythonGuide = read(repositoryRoot.resolve(PYTHON_GUIDE));

    assertFalse(readme.contains("System.out"));
    assertTrue(readme.contains("java -ea ReadmeQuickstart"));
    assertTrue(pythonGuide.contains("compressor.setOutletPressure(80.0, \"bara\")"));
    assertFalse(UNITLESS_PRESSURE.matcher(pythonGuide).find());
    assertTrue(pythonGuide.contains("Selecting a density unit does not enable Peneloux volume correction"));
    assertTrue(pythonGuide.contains("correction is a separate model choice"));
    assertFalse(pythonGuide.contains("with unit for Peneloux correction"));
    assertFalse(pythonGuide.contains("includes Peneloux correction"));
  }

  @Test
  void publishedReadmeProgramCompilesAndRunsWithAssertions() throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    String readme = read(repositoryRoot.resolve(README));
    Matcher fences = README_JAVA.matcher(readme);

    assertTrue(fences.find(), "README Java quickstart must be a complete program");
    String source = fences.group(1);
    assertTrue(source.contains("LogManager.getLogger"));
    assertTrue(source.contains("assert fluid.getNumberOfPhases() >= 1"));
    assertTrue(source.contains("assert Double.isFinite(densityKgPerCubicMetre)"));
    assertTrue(source.contains("assert densityKgPerCubicMetre > 0.0"));
    assertFalse(source.contains("System.out"));
    compileAndRun(source);
    assertFalse(fences.find(), "README Java quickstart section must contain one Java program");
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
    try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
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
