package neqsim.documentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

/**
 * Executable contract for the published mathematical-model selection example.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class MathematicalModelsDocumentationTest {
  private static final String GUIDE = "docs/thermo/mathematical_models.md";
  private static final String MARKER =
      "<!-- mathematical-models-example: cubic-eos-selection -->";

  /**
   * Read the maintained guide.
   *
   * @return guide text
   * @throws Exception if the guide cannot be read
   */
  private String guide() throws Exception {
    return new String(Files.readAllBytes(Paths.get(GUIDE)), StandardCharsets.UTF_8);
  }

  /**
   * Extract the first Java fence after the unique example marker.
   *
   * @param guide guide text
   * @return literal Java source
   */
  private String source(String guide) {
    assertEquals(guide.indexOf(MARKER), guide.lastIndexOf(MARKER), "Marker must be unique");
    assertTrue(guide.indexOf(MARKER) >= 0, "Missing mathematical-model example marker");
    Matcher fence =
        Pattern.compile("```java\\r?\\n(.*?)\\r?\\n```", Pattern.DOTALL)
            .matcher(guide.substring(guide.indexOf(MARKER) + MARKER.length()));
    assertTrue(fence.find(), "Missing Java fence after mathematical-model example marker");
    return fence.group(1);
  }

  /**
   * Compile the literal fence with Java 8 settings and run it with assertions enabled.
   *
   * @throws Exception if compilation, loading, or execution fails
   */
  @Test
  void modelSelectionProgramCompilesAndRuns() throws Exception {
    String guide = guide();
    assertTrue(
        guide.contains(
            "[`MathematicalModelsDocumentationTest`]"
                + "(../../src/test/java/neqsim/documentation/"
                + "MathematicalModelsDocumentationTest.java)"));
    assertTrue(
        guide.contains("./mvnw -Dtest=MathematicalModelsDocumentationTest test"));

    java.nio.file.Path directory = Files.createTempDirectory("mathematical-models-");
    java.nio.file.Path file = directory.resolve("MathematicalModelsQuickStart.java");
    Files.write(file, source(guide).getBytes(StandardCharsets.UTF_8));

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertTrue(compiler != null, "A JDK is required for the literal documentation example");
    StringWriter diagnostics = new StringWriter();
    try (StandardJavaFileManager manager =
        compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
      assertTrue(
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
                      "-classpath",
                      System.getProperty("java.class.path"),
                      "-d",
                      directory.toString()),
                  null,
                  manager.getJavaFileObjects(file.toFile()))
              .call(),
          diagnostics.toString());
    }

    try (URLClassLoader loader =
        new URLClassLoader(
            new URL[] {directory.toUri().toURL()}, getClass().getClassLoader())) {
      loader.setDefaultAssertionStatus(true);
      loader
          .loadClass("MathematicalModelsQuickStart")
          .getMethod("main", String[].class)
          .invoke(null, (Object) new String[0]);
    }
  }
}
