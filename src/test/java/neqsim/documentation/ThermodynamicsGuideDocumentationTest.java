package neqsim.documentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
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
 * Executes the literal Java dew-point program published by the thermodynamics guide.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class ThermodynamicsGuideDocumentationTest {
  private static final Path GUIDE = Paths.get("docs", "wiki", "thermodynamics_guide.md");
  private static final String MARKER =
      "<!-- thermodynamics-guide-example: independent-dew-points -->";
  private static final Pattern JAVA_FENCE =
      Pattern.compile("```java\\r?\\n(.*?)\\r?\\n```", Pattern.DOTALL);

  @TempDir
  Path temporaryDirectory;

  /** Verifies the guide contract, compiles the literal Java 8 source, and executes it. */
  @Test
  void independentDewPointExampleCompilesAndRuns() throws Exception {
    String source = exampleSource();

    assertFalse(source.contains("System.out"));
    assertFalse(source.contains("System.err"));
    assertFalse(source.contains("printStackTrace"));
    assertFalse(source.contains("catch ("));
    assertTrue(source.contains("LogManager.getLogger(DewPointExample.class)"));
    assertTrue(source.contains("SystemSrkEos hydrocarbonGas"));
    assertTrue(source.contains("SystemSrkEos wetGas"));
    assertTrue(source.contains("hydrocarbonOperations.dewPointTemperatureFlash()"));
    assertTrue(source.contains("waterOperations.waterDewPointTemperatureFlash()"));
    assertTrue(source.contains("Double.isFinite(hydrocarbonDewPointC)"));
    assertTrue(source.contains("Double.isFinite(waterDewPointC)"));
    assertTrue(source.contains("hydrocarbonDewPointC > 1.0"));
    assertTrue(source.contains("hydrocarbonDewPointC < 2.5"));
    assertTrue(source.contains("waterDewPointC > -14.0"));
    assertTrue(source.contains("waterDewPointC < -11.0"));

    try (URLClassLoader loader = compile("DewPointExample", source)) {
      Class<?> example = loader.loadClass("DewPointExample");
      example.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
    }
  }

  /**
   * Extracts the uniquely marked Java program and verifies the surrounding page contract.
   *
   * @return exact Java source published by the guide
   * @throws IOException if the guide cannot be read
   */
  private String exampleSource() throws IOException {
    String markdown = new String(Files.readAllBytes(GUIDE), StandardCharsets.UTF_8);
    assertEquals(markdown.indexOf(MARKER), markdown.lastIndexOf(MARKER),
        "Dew-point example marker must be unique");
    assertFalse(markdown.contains("\n# NeqSim Thermodynamics Guide\n"),
        "Jekyll front matter already owns the page title");
    assertTrue(markdown.contains("[Viscosity Models](viscosity_models.md)"));
    assertTrue(markdown.contains("[Fluid Characterization](fluid_characterization.md)"));
    assertTrue(markdown.contains("different feeds and pressures"));
    assertTrue(markdown.contains("not design-acceptance limits"));

    String markedTail = markdown.substring(markdown.indexOf(MARKER) + MARKER.length());
    Matcher fenceMatcher = JAVA_FENCE.matcher(markedTail);
    assertTrue(fenceMatcher.find(), "Marked dew-point Java fence is missing");
    String source = fenceMatcher.group(1).trim();
    assertTrue(source.contains("public final class DewPointExample"));
    return source;
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
