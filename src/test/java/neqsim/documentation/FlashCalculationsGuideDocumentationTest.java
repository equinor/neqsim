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
 * Compiles and executes the exact PH-flash example published in the flash calculations guide.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class FlashCalculationsGuideDocumentationTest {
  private static final Path GUIDE = Paths.get("docs", "thermo", "flash_calculations_guide.md");
  private static final String MARKER = "<!-- flash-guide-example: ph-joule-thomson -->";

  @TempDir
  Path temporaryDirectory;

  /** Verifies the published example's source policy and isenthalpic engineering result. */
  @Test
  void phFlashJouleThomsonExampleCompilesAndRuns() throws Exception {
    String source = exampleSource();

    assertFalse(source.contains("System.out"));
    assertFalse(source.contains("System.err"));
    assertTrue(source.contains("LogManager.getLogger(FlashCalculationsGuidePHExample.class)"));
    assertTrue(source.contains("operations.PHflash(inletEnthalpy);"));

    try (URLClassLoader loader = compile("FlashCalculationsGuidePHExample", source)) {
      double[] result = (double[]) loader.loadClass("FlashCalculationsGuidePHExample").getMethod("run").invoke(null);

      assertEquals(5, result.length);
      assertTrue(Double.isFinite(result[0]) && result[0] > 0.0);
      assertTrue(Double.isFinite(result[1]) && result[1] > 0.0);
      assertTrue(result[1] < result[0], "The stated methane case must cool during expansion");
      assertTrue(Double.isFinite(result[2]));
      assertTrue(Double.isFinite(result[3]));
      assertEquals(result[2], result[3], Math.max(1.0e-3, Math.abs(result[2]) * 1.0e-6));
      assertEquals(10.0, result[4], 1.0e-8);
    }
  }

  /**
   * Extracts the one marked Java fence without modifying the published source.
   *
   * @return exact published Java source
   * @throws Exception if the guide cannot be read
   */
  private static String exampleSource() throws Exception {
    String markdown = new String(Files.readAllBytes(GUIDE), StandardCharsets.UTF_8);
    int marker = markdown.indexOf(MARKER);
    assertTrue(marker >= 0, "Missing PH-flash example marker");
    assertEquals(marker, markdown.lastIndexOf(MARKER), "PH-flash example marker must be unique");

    Matcher fence = Pattern.compile("```java\\r?\\n(.*?)\\r?\\n```", Pattern.DOTALL)
        .matcher(markdown.substring(marker));
    assertTrue(fence.find(), "Missing Java fence after PH-flash example marker");
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
