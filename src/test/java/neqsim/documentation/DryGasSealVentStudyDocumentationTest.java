package neqsim.documentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

/**
 * Compiles and executes the exact Java program published in the dry gas seal vent guide.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class DryGasSealVentStudyDocumentationTest extends NeqSimTest {
  private static final Path GUIDE = Paths.get("docs", "process", "equipment", "dry_gas_seal_vent_study.md");
  private static final String MARKER = "<!-- dry-gas-seal-vent-study: complete -->";
  private static final String CLASS_NAME = "DryGasSealVentStudyGuideExample";
  private static final Pattern JAVA_FENCE = Pattern.compile("(?m)^```java\\r?\\n([\\s\\S]*?)^```[ \\t]*$");

  @TempDir
  Path temporaryDirectory;

  /**
   * Verifies that the literal combined-failure example compiles and runs with its engineering checks enabled.
   *
   * @throws Exception if extraction, compilation, loading, or execution fails
   */
  @Test
  void publishedProgramCompilesAndRuns() throws Exception {
    String source = markedExampleSource();

    assertFalse(source.contains("System.out"));
    assertFalse(source.contains("System.err"));
    assertTrue(source.contains("Double.isFinite(combined.primaryBara)"));
    assertTrue(source.contains("Total cavity mass balance did not close"));
    assertTrue(source.contains("Process-origin mass balance did not close"));
    assertTrue(source.contains("combined.minimumPressureMarginBar(4.0, 1.5, 1.0, 1.0)"));

    try (URLClassLoader loader = compile(source)) {
      Class<?> example = loader.loadClass(CLASS_NAME);
      example.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
    }
  }

  /**
   * Extracts the unique marked Java example.
   *
   * @return literal Java source
   * @throws Exception if the guide cannot be read
   */
  private static String markedExampleSource() throws Exception {
    String markdown = new String(Files.readAllBytes(GUIDE), StandardCharsets.UTF_8);
    int markerPosition = markdown.indexOf(MARKER);
    assertTrue(markerPosition >= 0, "Missing marker: " + MARKER);
    assertEquals(markerPosition, markdown.lastIndexOf(MARKER), "Marker must be unique");

    Matcher fence = JAVA_FENCE.matcher(markdown.substring(markerPosition));
    assertTrue(fence.find(), "Missing Java fence after marker");
    String source = fence.group(1);
    assertFalse(fence.find(), "The marked section must contain one Java program");
    return source;
  }

  /**
   * Compiles source with the repository classpath and Java 8 language level.
   *
   * @param source Java source to compile
   * @return class loader for the compiled program
   * @throws Exception if compiler setup or compilation fails
   */
  private URLClassLoader compile(String source) throws Exception {
    Path classes = Files.createDirectories(temporaryDirectory.resolve(CLASS_NAME));
    Path javaFile = classes.resolve(CLASS_NAME + ".java");
    Files.write(javaFile, source.getBytes(StandardCharsets.UTF_8));

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "A JDK is required to validate documentation examples");
    String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
    try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
      Boolean successful = compiler
          .getTask(null, manager, diagnostics, Arrays.asList("-source", "8", "-target", "8", "-proc:none", "-classpath",
              classpath, "-d", classes.toString()), null, manager.getJavaFileObjects(javaFile.toFile()))
          .call();
      assertTrue(successful, diagnostics.getDiagnostics().toString());
    }
    return new URLClassLoader(new URL[] {classes.toUri().toURL()}, getClass().getClassLoader());
  }
}
