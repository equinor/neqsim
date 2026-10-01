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
import java.util.concurrent.TimeUnit;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import neqsim.NeqSimTest;

/** Compiles and executes the maintained TwoFluid Java examples. */
public class TwoFluidJavaExamplesDocumentationTest extends NeqSimTest {
  private static final String COMPARISON = "docs/examples/TwoFluidVsDriftFluxComparisonExample.java";
  private static final String ACCUMULATION = "docs/examples/TwoFluidPipelineLiquidAccumulationExample.java";

  @TempDir
  Path temporaryDirectory;

  @Test
  void examplesStateUnitsChecksAndQualificationBoundaries() throws Exception {
    for (String source : Arrays.asList(readSource(COMPARISON), readSource(ACCUMULATION))) {
      assertTrue(source.contains("LogManager.getLogger"));
      assertTrue(source.contains("--smoke"));
      assertTrue(source.contains("kg/sec"));
      assertTrue(source.contains("bara"));
      assertTrue(source.contains("IllegalStateException"));
      assertTrue(source.contains("Qualification boundary"));
      assertTrue(source.contains("assert "));
      assertFalse(source.contains("System.out"));
      assertFalse(source.contains("System.err"));
    }

    String comparison = readSource(COMPARISON);
    assertTrue(comparison.contains("no partial result is valid"));
    assertTrue(comparison.contains("must remain in [0, 1]"));
    assertFalse(comparison.contains("physically correct"));
    assertFalse(comparison.contains("unrealistically high"));
    assertFalse(comparison.contains("Use Two-Fluid Model for"));

    String accumulation = readSource(ACCUMULATION);
    assertTrue(accumulation.contains("Math.max(1, positionsMetres.length / 8)"));
    assertTrue(accumulation.contains("not establish"));
    assertFalse(accumulation.contains("More accurate"));
    assertFalse(accumulation.contains("Better prediction"));
  }

  @Test
  @Timeout(value = 180, unit = TimeUnit.SECONDS)
  void exactSourcesCompileForJava8AndRunBoundedSmokeCases() throws Exception {
    Path outputDirectory = temporaryDirectory.resolve("compiled");
    Path sourceDirectory = temporaryDirectory.resolve("source").resolve("examples");
    Files.createDirectories(outputDirectory);
    Files.createDirectories(sourceDirectory);

    Path comparisonSource = sourceDirectory.resolve("TwoFluidVsDriftFluxComparisonExample.java");
    Path accumulationSource = sourceDirectory.resolve("TwoFluidPipelineLiquidAccumulationExample.java");
    Files.write(comparisonSource, readSource(COMPARISON).getBytes(StandardCharsets.UTF_8));
    Files.write(accumulationSource, readSource(ACCUMULATION).getBytes(StandardCharsets.UTF_8));

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "Documentation examples require a JDK compiler");
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
    String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    Iterable<String> options = Arrays.asList("-source", "8", "-target", "8", "-classpath", classPath, "-d",
        outputDirectory.toString());

    try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
      Boolean successful = compiler.getTask(null, manager, diagnostics, options, null,
          manager.getJavaFileObjects(comparisonSource.toFile(), accumulationSource.toFile())).call();
      assertTrue(Boolean.TRUE.equals(successful), diagnostics.getDiagnostics().toString());
    }

    try (URLClassLoader loader = new URLClassLoader(new URL[] {outputDirectory.toUri().toURL()},
        getClass().getClassLoader())) {
      loader.setDefaultAssertionStatus(true);
      runSmoke(loader, "examples.TwoFluidVsDriftFluxComparisonExample");
      runSmoke(loader, "examples.TwoFluidPipelineLiquidAccumulationExample");
    }
  }

  private String readSource(String relativePath) throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    return new String(Files.readAllBytes(repositoryRoot.resolve(relativePath)), StandardCharsets.UTF_8);
  }

  private void runSmoke(ClassLoader loader, String className) throws Exception {
    Class<?> example = Class.forName(className, true, loader);
    assertTrue(example.desiredAssertionStatus());
    try {
      example.getMethod("main", String[].class).invoke(null, (Object) new String[] {"--smoke"});
    } catch (InvocationTargetException exception) {
      throw new AssertionError(className + " smoke case failed", exception.getCause());
    }
  }
}
