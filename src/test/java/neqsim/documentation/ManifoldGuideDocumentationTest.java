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

/** Compiles and executes the complete production-manifold example published in the guide. */
public class ManifoldGuideDocumentationTest extends neqsim.NeqSimTest {
  private static final Pattern JAVA_FENCE = Pattern.compile("(?m)^```java\\r?\\n([\\s\\S]*?)^```[ \\t]*$");
  private static final Pattern PUBLIC_CLASS = Pattern
      .compile("public\\s+(?:final\\s+)?class\\s+([A-Za-z][A-Za-z0-9_]*)");

  @TempDir
  Path temporaryDirectory;

  @Test
  void productionManifoldExampleCompilesAndRunsWithAssertions() throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    Path guidePath = repositoryRoot.resolve("docs/process/equipment/manifolds.md");
    String guide = new String(Files.readAllBytes(guidePath), StandardCharsets.UTF_8);
    Path sourcePath = repositoryRoot.resolve("src/main/java/neqsim/process/equipment/manifold/Manifold.java");
    String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);

    assertTrue(guide.contains("not a vibration qualification"));
    assertTrue(source.contains("public void addStream(StreamInterface newStream)"));
    assertTrue(source.contains("public void setSplitFactors(double[] splitFact)"));
    assertTrue(source.contains("public StreamInterface getMixedStream()"));
    assertTrue(source.contains("public double getMassBalance(String unit)"));

    for (String stale : Arrays.asList("System.out", "System.err", "setSplitNumber", "getMixer()", "getSplitter()",
        "setInnerHeaderDiameter", "setInnerBranchDiameter", "setMaxDesignVelocity", "getAverageBranchVelocity",
        "createWellStream")) {
      assertFalse(guide.contains(stale), "Stale manifold guide token: " + stale);
    }

    Matcher fences = JAVA_FENCE.matcher(guide);
    assertTrue(fences.find(), "The manifold guide must contain one complete Java example");
    String exampleSource = fences.group(1);
    assertFalse(fences.find(), "The manifold guide should expose one maintained Java program");
    Matcher className = PUBLIC_CLASS.matcher(exampleSource);
    assertTrue(className.find(), "The manifold example must be a complete public class");
    assertEquals("ProductionManifoldExample", className.group(1));
    assertTrue(exampleSource.contains("LogManager.getLogger"));
    assertTrue(exampleSource.contains("manifold.getMassBalance"));
    assertTrue(exampleSource.contains("assert manifold.isAutoSized()"));

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "Documentation examples require a JDK compiler");
    Path javaSource = temporaryDirectory.resolve("ProductionManifoldExample.java");
    Files.write(javaSource, exampleSource.getBytes(StandardCharsets.UTF_8));
    compile(compiler, javaSource);

    try (URLClassLoader loader = new URLClassLoader(new URL[] {temporaryDirectory.toUri().toURL()},
        getClass().getClassLoader())) {
      loader.setDefaultAssertionStatus(true);
      Class<?> example = Class.forName("ProductionManifoldExample", true, loader);
      assertTrue(example.desiredAssertionStatus());
      try {
        example.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
      } catch (InvocationTargetException exception) {
        throw new AssertionError("ProductionManifoldExample failed", exception.getCause());
      }
    }
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
          "docs/process/equipment/manifolds.md: " + diagnostics.getDiagnostics());
    }
  }
}
