package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Compiles and executes the exact chart-to-efficiency program in the compressor guide.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class CompressorGuideDocumentationTest {
  private static final Path GUIDE = Paths.get("docs", "process", "equipment", "compressors.md");
  private static final String MARKER = "<!-- compressor-guide-example: chart-to-efficiency -->";
  private static final Pattern JAVA_FENCE = Pattern.compile("```java\\r?\\n(.*?)\\r?\\n```", Pattern.DOTALL);

  @TempDir
  Path temporaryDirectory;

  /**
   * Verifies the literal guide source performs the chart-to-efficiency transition.
   *
   * @throws Exception if the guide source cannot be compiled or executed
   */
  @Test
  void chartToEfficiencyExampleCompilesAndRunsFromGuide() throws Exception {
    String source = markedExampleSource();

    assertFalse(source.contains("System.out"));
    assertFalse(source.contains("System.err"));
    assertTrue(source.contains("setSolveSpeed(true)"));
    assertTrue(source.contains("setUseCompressorChart(false)"));
    assertTrue(source.contains("setSolveSpeed(false)"));
    assertTrue(source.contains("solveEfficiency(targetTemperatureK)"));
    assertTrue(source.contains("Chart-free rerun did not preserve the fit"));

    try (URLClassLoader loader = compile("CompressorChartEfficiencyExample", source)) {
      Class<?> example = loader.loadClass("CompressorChartEfficiencyExample");
      example.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
    }
  }

  /**
   * Extracts the uniquely marked Java fence.
   *
   * @return exact published Java source
   * @throws Exception if the guide cannot be read
   */
  private static String markedExampleSource() throws Exception {
    String markdown = new String(Files.readAllBytes(GUIDE), StandardCharsets.UTF_8);
    int markerPosition = markdown.indexOf(MARKER);
    assertTrue(markerPosition >= 0, "Missing marker: " + MARKER);
    assertEquals(markerPosition, markdown.lastIndexOf(MARKER), "Marker must be unique: " + MARKER);

    Matcher fence = JAVA_FENCE.matcher(markdown.substring(markerPosition));
    assertTrue(fence.find(), "Missing Java fence after marker: " + MARKER);
    assertTrue(fence.group(1).contains("public final class CompressorChartEfficiencyExample"));
    return fence.group(1);
  }

  /**
   * Compiles one Java 8 documentation source and returns its class loader.
   *
   * @param className public class name
   * @param source exact Java source
   * @return class loader for the compiled source
   * @throws Exception if the source or class output cannot be created
   */
  private URLClassLoader compile(String className, String source) throws Exception {
    Path classes = Files.createDirectories(temporaryDirectory.resolve(className));
    Path javaFile = classes.resolve(className + ".java");
    Files.write(javaFile, source.getBytes(StandardCharsets.UTF_8));

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "A JDK is required to validate documentation examples");
    String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    StringWriter diagnostics = new StringWriter();
    try (StandardJavaFileManager manager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
      Boolean successful = compiler
          .getTask(diagnostics, manager, null, Arrays.asList("-source", "8", "-target", "8", "-proc:none", "-classpath",
              classpath, "-d", classes.toString()), null, manager.getJavaFileObjects(javaFile.toFile()))
          .call();
      assertTrue(successful, diagnostics.toString());
    }
    return new URLClassLoader(new URL[] {classes.toUri().toURL()}, getClass().getClassLoader());
  }
}
