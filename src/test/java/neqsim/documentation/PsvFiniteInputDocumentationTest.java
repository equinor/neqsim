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

/** Executable contract for the published PSV finite-input example. */
class PsvFiniteInputDocumentationTest {
  private static final String GUIDE = "docs/safety/vessel_thermomechanical_safety.md";
  private static final String MARKER = "<!-- psv-example: finite-input-contract -->";

  private String guide() throws Exception {
    return new String(Files.readAllBytes(Paths.get(GUIDE)), StandardCharsets.UTF_8);
  }

  private String source(String guide) {
    assertEquals(guide.indexOf(MARKER), guide.lastIndexOf(MARKER), "Marker must be unique");
    assertTrue(guide.indexOf(MARKER) >= 0, "Missing PSV example marker");
    Matcher fence = Pattern.compile("```java\\r?\\n(.*?)\\r?\\n```", Pattern.DOTALL)
        .matcher(guide.substring(guide.indexOf(MARKER) + MARKER.length()));
    assertTrue(fence.find(), "Missing Java fence after PSV example marker");
    return fence.group(1);
  }

  @Test
  void finiteInputProgramCompilesAndRuns() throws Exception {
    String guide = guide();
    assertTrue(guide.contains(
        "[`PsvFiniteInputDocumentationTest`](../../src/test/java/neqsim/documentation/PsvFiniteInputDocumentationTest.java)"));
    assertTrue(guide.contains("./mvnw -Dtest=PsvFiniteInputDocumentationTest test"));

    java.nio.file.Path directory = Files.createTempDirectory("psv-finite-input-");
    java.nio.file.Path file = directory.resolve("PsvFiniteInputExample.java");
    Files.write(file, source(guide).getBytes(StandardCharsets.UTF_8));

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertTrue(compiler != null, "A JDK is required for the literal documentation example");
    StringWriter diagnostics = new StringWriter();
    try (StandardJavaFileManager manager =
        compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
      assertTrue(
          compiler.getTask(
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
        new URLClassLoader(new URL[] {directory.toUri().toURL()}, getClass().getClassLoader())) {
      loader.setDefaultAssertionStatus(true);
      loader
          .loadClass("PsvFiniteInputExample")
          .getMethod("main", String[].class)
          .invoke(null, (Object) new String[0]);
    }
  }
}
