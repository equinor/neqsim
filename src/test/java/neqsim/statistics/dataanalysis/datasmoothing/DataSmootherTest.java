package neqsim.statistics.dataanalysis.datasmoothing;

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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests the data smoother and its maintained documentation example. */
public class DataSmootherTest {
  private static final Pattern JAVA_FENCE = Pattern.compile("(?m)^```java\\r?\\n([\\s\\S]*?)^```[ \\t]*$");

  @TempDir
  Path temporaryDirectory;

  @Test
  void testgetSmoothedNumbers() {
    double[] numbers = {10, 11, 12, 13, 14, 15, 15.5, 15, 19, 14, 14, 13, 12, 12, 11, 10, 9, 8};
    DataSmoother test = new DataSmoother(numbers, 3, 3, 0, 4);
    Assertions.assertArrayEquals(numbers, test.getSmoothedNumbers());

    double[] expected = {10.000000, 11.000000, 12.000000, 12.567099567099579, 13.567099567099582, 14.556277056277072,
        15.305194805194823, 15.389610389610409, 16.66233766233768, 17.149350649350666, 13.350649350649375,
        13.627705627705646, 12.432900432900452, 11.89177489177491, 11.541125541125552, 10.000000, 9.000000, 8.000000};
    test.runSmoothing();
    Assertions.assertArrayEquals(expected, test.getSmoothedNumbers());
  }

  @Test
  void documentationExampleCompilesAndRuns() throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    Path guidePath = repositoryRoot.resolve("docs/statistics/data_analysis.md");
    String guide = new String(Files.readAllBytes(guidePath), StandardCharsets.UTF_8);
    String source = new String(
        Files.readAllBytes(
            repositoryRoot.resolve("src/main/java/neqsim/statistics/dataanalysis/datasmoothing/DataSmoother.java")),
        StandardCharsets.UTF_8);

    assertTrue(source.contains("public DataSmoother(double[] nonSmoothedNumbers, int nl, int nr, int ld, int m)"));
    for (String stale : Arrays.asList("System.out", "System.err", ".setInputNumbers(", "new Matrix(",
        "SampleCreator creator", "WettedWallColumnData data", "optimizer.solve()")) {
      assertFalse(guide.contains(stale), "Stale data-analysis guide token: " + stale);
    }

    Matcher fences = JAVA_FENCE.matcher(guide);
    assertTrue(fences.find(), "The data-analysis guide must contain one complete Java example");
    String exampleSource = fences.group(1);
    assertFalse(fences.find(), "The data-analysis guide should expose one maintained Java program");
    assertTrue(exampleSource.contains("public final class DataSmootherGuideExample"));
    assertTrue(exampleSource.contains("new DataSmoother("));
    assertTrue(exampleSource.contains("smoother.runSmoothing()"));
    assertTrue(exampleSource.contains("assert Math.abs(smoothed[8] - 16.66233766233768)"));

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "Documentation examples require a JDK compiler");
    Path javaSource = temporaryDirectory.resolve("DataSmootherGuideExample.java");
    Files.write(javaSource, exampleSource.getBytes(StandardCharsets.UTF_8));
    compile(compiler, javaSource);

    try (URLClassLoader loader = new URLClassLoader(new URL[] {temporaryDirectory.toUri().toURL()},
        getClass().getClassLoader())) {
      loader.setDefaultAssertionStatus(true);
      Class<?> example = Class.forName("DataSmootherGuideExample", true, loader);
      assertTrue(example.desiredAssertionStatus());
      try {
        example.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
      } catch (InvocationTargetException exception) {
        throw new AssertionError("DataSmootherGuideExample failed", exception.getCause());
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
      assertTrue(Boolean.TRUE.equals(successful), "docs/statistics/data_analysis.md: " + diagnostics.getDiagnostics());
    }
  }
}
