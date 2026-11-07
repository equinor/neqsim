package neqsim.documentation;

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
 * Compiles and executes the exact reference cooling-water guide example.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class ReferenceCoolingWaterDocumentationTest {
  /** Directory for compiled documentation examples. */
  @TempDir
  Path temporaryDirectory;

  /**
   * Runs the literal published Java example using Java 8 source/target settings.
   *
   * @throws Exception if the guide cannot be read, compiled or executed
   */
  @Test
  void literalGuideExampleCompilesAndRuns() throws Exception {
    Path guide = Paths.get("docs", "process", "equipment", "reference_cooling_water.md");
    String markdown = new String(Files.readAllBytes(guide), StandardCharsets.UTF_8);
    Matcher fence = Pattern.compile("```java\\r?\\n(.*?)\\r?\\n```", Pattern.DOTALL).matcher(markdown);
    assertTrue(fence.find());
    String source = fence.group(1);
    Path javaFile = temporaryDirectory.resolve("CoolingWaterGuideExample.java");
    Files.write(javaFile, source.getBytes(StandardCharsets.UTF_8));
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "A JDK is required to validate the documentation example");
    String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    StringWriter diagnostics = new StringWriter();
    try (StandardJavaFileManager manager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
      Boolean success = compiler
          .getTask(diagnostics, manager, null, Arrays.asList("-source", "8", "-target", "8", "-proc:none", "-classpath",
              classpath, "-d", temporaryDirectory.toString()), null, manager.getJavaFileObjects(javaFile.toFile()))
          .call();
      assertTrue(success, diagnostics.toString());
    }
    try (URLClassLoader loader = new URLClassLoader(new URL[] {temporaryDirectory.toUri().toURL()},
        getClass().getClassLoader())) {
      loader.loadClass("CoolingWaterGuideExample").getMethod("main", String[].class).invoke(null,
          (Object) new String[0]);
    }
  }
}
