package neqsim.documentation;

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
 * Compiles and executes the exact Java quick start published by the asphaltene characterization guide.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class AsphalteneCharacterizationDocumentationTest {
  private static final Path GUIDE = Paths.get("docs", "thermo", "characterization", "asphaltene_characterization.md");
  private static final String MARKER = "<!-- asphaltene-characterization-quick-start: complete -->";
  private static final String CLASS_NAME = "AsphalteneCharacterizationExample";
  private static final Pattern JAVA_FENCE = Pattern.compile("```java\\r?\\n(.*?)\\r?\\n```", Pattern.DOTALL);

  @TempDir
  Path temporaryDirectory;

  /**
   * Verifies that the literal SARA screening quick start compiles and runs.
   *
   * @throws Exception if the example cannot be extracted, compiled, or executed
   */
  @Test
  void completeExampleCompilesAndRuns() throws Exception {
    String source = markedExampleSource();
    assertFalse(source.contains("System.out"));
    assertFalse(source.contains("System.err"));
    assertTrue(source.contains("getColloidalInstabilityIndex()"));
    assertTrue(source.contains("getResinToAsphalteneRatio()"));
    assertTrue(source.contains("estimateAsphalteneMolecularWeight()"));
    assertTrue(source.contains("estimateResinMolecularWeight()"));
    assertTrue(source.contains("Double.isFinite(cii)"));

    try (URLClassLoader loader = compile(source)) {
      Class<?> example = loader.loadClass(CLASS_NAME);
      example.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
    }
  }

  /**
   * Verifies that the guide contains current APIs and preserves the screening validity boundary.
   *
   * @throws Exception if the guide cannot be read
   */
  @Test
  void guideUsesCurrentApisAndSafeBoundaries() throws Exception {
    String markdown = new String(Files.readAllBytes(GUIDE), StandardCharsets.UTF_8);
    assertFalse(markdown.contains("calcColloidalInstabilityIndex"));
    assertFalse(markdown.contains("calcResinAsphalteneRatio"));
    assertFalse(markdown.contains("setMwC7plus"));
    assertFalse(markdown.contains("setDensityC7plus"));
    assertFalse(markdown.contains("estimateAsphalteneMW"));
    assertFalse(markdown.contains("estimateResinMW"));
    assertFalse(markdown.contains("setAsphalteneMW"));
    assertFalse(markdown.contains("setResinMW"));
    assertFalse(markdown.contains("setResinAsphalteneAssociationEnergy"));
    assertFalse(markdown.contains("getResinMW"));
    assertFalse(markdown.contains("getStabilityClassification"));
    assertFalse(markdown.contains("hasPhaseType(\"solid\")"));
    assertFalse(markdown.contains("System.out"));
    assertFalse(markdown.contains("System.err"));
    assertTrue(markdown.contains("does not by itself establish an asphaltene phase"));
    assertTrue(markdown.contains("asphaltene_deboer_screening.md"));
  }

  private static String markedExampleSource() throws Exception {
    String markdown = new String(Files.readAllBytes(GUIDE), StandardCharsets.UTF_8);
    int markerPosition = markdown.indexOf(MARKER);
    assertTrue(markerPosition >= 0, "Missing marker: " + MARKER);
    assertEquals(markerPosition, markdown.lastIndexOf(MARKER), "Marker must be unique");

    Matcher fence = JAVA_FENCE.matcher(markdown.substring(markerPosition));
    assertTrue(fence.find(), "Missing Java fence after marker");
    return fence.group(1);
  }

  private URLClassLoader compile(String source) throws Exception {
    Path classes = Files.createDirectories(temporaryDirectory.resolve(CLASS_NAME));
    Path javaFile = classes.resolve(CLASS_NAME + ".java");
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
