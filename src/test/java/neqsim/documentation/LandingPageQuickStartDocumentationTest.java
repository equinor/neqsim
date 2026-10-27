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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Compiles and executes the exact Java quick-start program published on the documentation landing page.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class LandingPageQuickStartDocumentationTest {
  private static final Path LANDING_PAGE = Paths.get("docs", "index.md");
  private static final String MARKER = "<!-- landing-page-example: java-quick-start -->";
  private static final Pattern JAVA_FENCE = Pattern.compile("```java\\r?\\n(.*?)\\r?\\n```", Pattern.DOTALL);
  private static final List<Path> REPLICAS = Arrays.asList(Paths.get("docs", "README.md"),
      Paths.get("docs", "wiki", "index.md"), Paths.get("docs", "wiki", "getting_started.md"));

  @TempDir
  Path temporaryDirectory;

  /** Verifies the canonical quick-start source, its replicas, and its Java 8 execution. */
  @Test
  void landingQuickStartExampleCompilesAndRuns() throws Exception {
    String source = markedExampleSource();

    assertFalse(source.contains("System.out"));
    assertFalse(source.contains("System.err"));
    assertTrue(source.contains("LogManager.getLogger(NeqSimQuickStart.class)"));
    assertTrue(source.contains("gas.initProperties();"));
    assertTrue(source.contains("gas.getDensity(\"kg/m3\")"));
    assertTrue(source.contains("gas.getZ()"));

    for (Path replica : REPLICAS) {
      assertEquals(source, quickStartSource(replica), replica.toString());
    }

    try (URLClassLoader loader = compile("NeqSimQuickStart", source)) {
      Class<?> example = loader.loadClass("NeqSimQuickStart");
      example.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
    }
  }

  /**
   * Extracts the uniquely marked Java fence from the canonical landing page.
   *
   * @return exact published Java source
   * @throws Exception if the landing page cannot be read
   */
  private static String markedExampleSource() throws Exception {
    String markdown = new String(Files.readAllBytes(LANDING_PAGE), StandardCharsets.UTF_8);
    int marker = markdown.indexOf(MARKER);
    assertTrue(marker >= 0, "Missing landing-page Java example marker");
    assertEquals(marker, markdown.lastIndexOf(MARKER), "Landing-page Java example marker must be unique");

    Matcher fence = JAVA_FENCE.matcher(markdown.substring(marker));
    assertTrue(fence.find(), "Missing Java fence after landing-page example marker");
    assertTrue(fence.group(1).contains("public final class NeqSimQuickStart"));
    return fence.group(1);
  }

  /**
   * Extracts the one quick-start Java fence from a replicated landing page.
   *
   * @param page replicated documentation page
   * @return exact quick-start Java source
   * @throws Exception if the page cannot be read
   */
  private static String quickStartSource(Path page) throws Exception {
    String markdown = new String(Files.readAllBytes(page), StandardCharsets.UTF_8);
    Matcher fences = JAVA_FENCE.matcher(markdown);
    String source = null;
    int matches = 0;
    while (fences.find()) {
      if (fences.group(1).contains("public final class NeqSimQuickStart")) {
        source = fences.group(1);
        matches++;
      }
    }
    assertEquals(1, matches, page + ": expected one NeqSimQuickStart Java fence");
    assertNotNull(source);
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
