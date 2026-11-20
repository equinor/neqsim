package neqsim.process.util.fire;

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
 * Compiles and executes the exact complete Java program in the relief-valve sizing guide.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class ReliefValveSizingGuideDocumentationTest {
  private static final Path GUIDE = Paths.get("docs", "safety", "relief_valve_sizing_api.md");
  private static final String MARKER = "<!-- relief-sizing-example: complete -->";
  private static final String EXAMPLE_CLASS = "ReliefValveSizingExample";
  private static final Pattern JAVA_FENCE = Pattern.compile("```java\\r?\\n(.*?)\\r?\\n```", Pattern.DOTALL);

  @TempDir
  Path temporaryDirectory;

  /**
   * Verifies that the complete relief-sizing example is executable literal guide source.
   *
   * @throws Exception if the guide source cannot be compiled or executed
   */
  @Test
  void completeReliefSizingExampleCompilesAndRunsFromGuide() throws Exception {
    String source = markedExampleSource();

    assertFalse(source.contains("System.out"));
    assertFalse(source.contains("System.err"));
    assertTrue(source.contains("public final class " + EXAMPLE_CLASS));
    assertTrue(source.contains("ReliefValveSizing.calculateRequiredArea("));
    assertTrue(source.contains("ReliefValveSizing.calculateLiquidReliefArea("));
    assertTrue(source.contains("ReliefValveSizing.calculateTwoPhaseReliefArea("));
    assertTrue(source.contains("ReliefValveSizing.calculateAPI521FireHeatInput("));
    assertTrue(source.contains("if (gasResult.getRequiredArea() <= 0.0"));

    try (URLClassLoader loader = compile(EXAMPLE_CLASS, source)) {
      Class<?> example = loader.loadClass(EXAMPLE_CLASS);
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
    assertTrue(fence.group(1).contains("public final class " + EXAMPLE_CLASS));
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
