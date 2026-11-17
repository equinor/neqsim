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
 * Compiles and executes the exact complete Java example published by the physics-based risk guide.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class PhysicsBasedRiskIntegrationDocumentationTest {
  private static final Path GUIDE =
      Paths.get("docs", "risk", "PHYSICS_BASED_RISK_INTEGRATION.md");
  private static final String MARKER = "<!-- physics-risk-quick-start: complete -->";
  private static final String CLASS_NAME = "PhysicsBasedRiskIntegrationExample";
  private static final Pattern JAVA_FENCE =
      Pattern.compile("```java\\r?\\n(.*?)\\r?\\n```", Pattern.DOTALL);

  @TempDir
  Path temporaryDirectory;

  /**
   * Verifies that the literal physics-based risk example compiles and runs.
   *
   * @throws Exception if the example cannot be extracted, compiled, or executed
   */
  @Test
  void completeExampleCompilesAndRuns() throws Exception {
    String source = markedExampleSource();
    assertFalse(source.contains("System.out"));
    assertFalse(source.contains("System.err"));
    assertFalse(source.contains("import java.util.*"));
    assertTrue(
        source.contains(
            "PhysicsBasedRiskMonitor.PhysicsBasedRiskAssessment assessment"));
    assertTrue(source.contains("Double.isFinite(overallRiskScore)"));
    assertTrue(source.contains("overallRiskScore > 10.0"));
    assertTrue(source.contains("Double.isFinite(capacityMargin)"));
    assertTrue(source.contains("getEquipmentHealthIndices().isEmpty()"));
    assertTrue(source.contains("getEquipmentRiskScores().isEmpty()"));

    try (URLClassLoader loader = compile(source)) {
      Class<?> example = loader.loadClass(CLASS_NAME);
      example.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
    }
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
    String classpath =
        System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    StringWriter diagnostics = new StringWriter();
    try (StandardJavaFileManager manager =
        compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
      Boolean successful =
          compiler
              .getTask(
                  diagnostics,
                  manager,
                  null,
                  Arrays.asList(
                      "-source",
                      "8",
                      "-target",
                      "8",
                      "-proc:none",
                      "-classpath",
                      classpath,
                      "-d",
                      classes.toString()),
                  null,
                  manager.getJavaFileObjects(javaFile.toFile()))
              .call();
      assertTrue(successful, diagnostics.toString());
    }
    return new URLClassLoader(
        new URL[] {classes.toUri().toURL()}, getClass().getClassLoader());
  }
}
