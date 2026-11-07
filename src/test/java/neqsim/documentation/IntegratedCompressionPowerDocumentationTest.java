package neqsim.documentation;

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

/**
 * Compiles and executes the exact three-topology compression-power guide.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class IntegratedCompressionPowerDocumentationTest {
  @TempDir
  Path temporaryDirectory;

  /**
   * Executes the guide with Java 8 language syntax and assertions enabled.
   *
   * @throws Exception if reading, compilation or execution fails
   */
  @Test
  void exactPublishedExampleCompilesAndRuns() throws Exception {
    Path root = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    String guide = new String(
        Files.readAllBytes(root.resolve("docs/process/equipment/integrated_compression_power.md")),
        StandardCharsets.UTF_8);
    Matcher fence = Pattern.compile("(?ms)^```java\\r?\\n(.*?)^```[ \\t]*$").matcher(guide);
    assertTrue(fence.find());
    String source = fence.group(1);
    assertFalse(fence.find(), "Every additional Java fence needs execution coverage");
    assertFalse(source.contains("System.out"));
    Path javaFile = temporaryDirectory.resolve("IntegratedCompressionPowerExample.java");
    Files.write(javaFile, source.getBytes(StandardCharsets.UTF_8));
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "A JDK is required for documentation examples");
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
    String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
      Boolean success = compiler.getTask(null, manager, diagnostics,
          Arrays.asList("-source", "8", "-target", "8", "-classpath", classPath, "-d", temporaryDirectory.toString()),
          null, manager.getJavaFileObjects(javaFile.toFile())).call();
      assertTrue(Boolean.TRUE.equals(success), diagnostics.getDiagnostics().toString());
    }
    try (URLClassLoader loader = new URLClassLoader(new URL[] {temporaryDirectory.toUri().toURL()},
        getClass().getClassLoader())) {
      loader.setDefaultAssertionStatus(true);
      Class<?> example = Class.forName("IntegratedCompressionPowerExample", true, loader);
      assertTrue(example.desiredAssertionStatus());
      example.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
    }
  }
}
