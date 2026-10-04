package neqsim.thermo;

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
 * Compiles and executes the complete example in docs/thermo/thermodynamic_workflows.md.
 *
 * <p>The extracted program covers {@code addTBPfraction("C10", 0.10, 0.134, 0.792)} and
 * {@code setMixingRule("classic")} directly.
 */
public class ThermodynamicWorkflowsDocumentationTest {
  private static final String GUIDE = "docs/thermo/thermodynamic_workflows.md";
  private static final Pattern EXECUTABLE_JAVA = Pattern
      .compile("(?ms)^## Build, flash, and branch a characterized fluid.*?^```java\\r?\\n([\\s\\S]*?)^```[ \\t]*$");
  private static final Pattern ALL_JAVA = Pattern.compile("(?ms)^```java\\r?\\n([\\s\\S]*?)^```[ \\t]*$");
  private static final Pattern PUBLIC_CLASS = Pattern
      .compile("public\\s+(?:final\\s+)?class\\s+([A-Za-z][A-Za-z0-9_]*)");

  @TempDir
  Path temporaryDirectory;

  /** Verifies units, model boundaries, and the single-program contract. */
  @Test
  void buildFlashAndReadExample() throws Exception {
    String guide = readGuide();

    assertTrue(guide.contains("Temperature is in K and pressure is absolute"));
    assertTrue(guide.contains("molar mass in kg/mol"));
    assertTrue(guide.contains("dimensionless specific gravity"));
    assertTrue(guide.contains("densityKgM3"));
    assertTrue(guide.contains("molarMassKgMol * 1000.0"));
    assertTrue(guide.contains("source code does not benchmark"));
    assertTrue(guide.contains("laboratory PVT data"));
    assertFalse(guide.contains("System.out"));

    Matcher fences = ALL_JAVA.matcher(guide);
    assertTrue(fences.find(), "Guide must publish one Java program");
    assertFalse(fences.find(), "Guide must not publish unverified Java fragments");
  }

  /** Compiles the exact Markdown fence for Java 8 and executes it with assertions enabled. */
  @Test
  void cloneSweepKeepsOriginalState() throws Exception {
    Matcher fence = EXECUTABLE_JAVA.matcher(readGuide());
    assertTrue(fence.find(), "Executable thermodynamic workflow is missing");
    String source = fence.group(1);

    assertTrue(source.contains("new SystemPrEos(temperatureK, pressureBara)"));
    assertTrue(source.contains("fluid.addTBPfraction(\"C10\", 0.10, 0.134, 0.792)"));
    assertTrue(source.contains("fluid.createDatabase(true)"));
    assertTrue(source.contains("fluid.setMixingRule(\"classic\")"));
    assertTrue(source.contains("operations.TPflash()"));
    assertTrue(source.contains("sweepOperations.TPflash()"));
    assertTrue(source.contains("fluid.initProperties()"));
    assertTrue(source.contains("fluid.getTotalNumberOfMoles() - 1.0"));
    assertTrue(source.contains("assert sweepCase != fluid"));
    assertTrue(source.contains("Double.isFinite(sweepCase.getDensity(\"kg/m3\"))"));
    assertFalse(source.contains("System.out"));

    compileAndRun(source);
    assertFalse(fence.find(), "Executable section must contain one Java program");
  }

  /** Reads the workflow guide from the repository root. */
  private String readGuide() throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    return new String(Files.readAllBytes(repositoryRoot.resolve(GUIDE)), StandardCharsets.UTF_8);
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
