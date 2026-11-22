package neqsim.physicalproperties;

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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Compiles and executes the exact Java programs published in the physical-properties overview.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class PhysicalPropertiesExactSourceDocumentationTest {
  private static final Path GUIDE = Paths.get("docs", "physical_properties", "README.md");
  private static final Pattern JAVA_FENCE = Pattern.compile("```java\\r?\\n(.*?)\\r?\\n```", Pattern.DOTALL);
  private static final List<String> MARKERS = Arrays.asList("<!-- physical-properties-example: overview -->",
      "<!-- physical-properties-example: phase-specific -->",
      "<!-- physical-properties-example: temperature-sweep -->");
  private static final List<String> CLASS_NAMES = Arrays.asList("PhysicalPropertiesOverview",
      "PhaseSpecificPhysicalProperties", "PhysicalPropertyTemperatureSweep");

  @TempDir
  Path temporaryDirectory;

  /** Verifies that every marked physical-properties program is the exact executable source. */
  @Test
  void physicalPropertiesExamplesCompileAndRun() throws Exception {
    assertEquals(MARKERS.size(), CLASS_NAMES.size());
    for (int exampleIndex = 0; exampleIndex < MARKERS.size(); exampleIndex++) {
      String className = CLASS_NAMES.get(exampleIndex);
      String source = markedExampleSource(MARKERS.get(exampleIndex), className);

      assertFalse(source.contains("System.out"));
      assertFalse(source.contains("System.err"));

      try (URLClassLoader loader = compile(className, source)) {
        Class<?> example = loader.loadClass(className);
        example.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
      }
    }
  }

  /**
   * Extracts one uniquely marked Java fence from the physical-properties overview.
   *
   * @param marker unique marker immediately before the fence
   * @param className expected public class name
   * @return exact published Java source
   * @throws Exception if the guide cannot be read
   */
  private static String markedExampleSource(String marker, String className) throws Exception {
    String markdown = new String(Files.readAllBytes(GUIDE), StandardCharsets.UTF_8);
    int markerPosition = markdown.indexOf(marker);
    assertTrue(markerPosition >= 0, "Missing marker: " + marker);
    assertEquals(markerPosition, markdown.lastIndexOf(marker), "Marker must be unique: " + marker);

    Matcher fence = JAVA_FENCE.matcher(markdown.substring(markerPosition));
    assertTrue(fence.find(), "Missing Java fence after marker: " + marker);
    assertTrue(fence.group(1).contains("public class " + className));
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
