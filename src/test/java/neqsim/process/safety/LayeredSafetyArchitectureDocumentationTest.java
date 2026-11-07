package neqsim.process.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.NeqSimTest;

/**
 * Compiles and executes the literal quick start in the layered safety architecture guide.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
@Tag("slow")
class LayeredSafetyArchitectureDocumentationTest extends NeqSimTest {
  private static final Path GUIDE = Paths.get("docs", "safety", "layered_safety_architecture.md");
  private static final String QUICK_START_MARKER = "<!-- layered-safety-example: hipps-esd-quickstart -->";
  private static final Pattern JAVA_FENCE = Pattern.compile("```java\\r?\\n(.*?)\\r?\\n```", Pattern.DOTALL);

  @TempDir
  Path temporaryDirectory;

  /**
   * Verifies that the complete published Java 8 program compiles and runs.
   *
   * @throws Exception if guide reading, compilation, or execution fails
   */
  @Test
  void literalLayeredSafetyQuickStartCompilesAndRuns() throws Exception {
    String markdown = new String(Files.readAllBytes(GUIDE), StandardCharsets.UTF_8);
    String source = markedExampleSource(markdown);

    assertFalse(markdown.contains("All implemented safety systems comply"));
    assertFalse(markdown.contains("Meets SIL 2/3 requirements"));
    assertFalse(markdown.contains("All systems fail to safe state"));
    assertFalse(source.contains("System.out"));
    assertFalse(source.contains("System.err"));
    assertTrue(source.contains("public final class LayeredSafetyQuickStart"));
    assertTrue(source.contains("VotingLogic.TWO_OUT_OF_THREE"));
    assertTrue(source.contains("hipps.update(96.0, 96.0, 90.0)"));
    assertTrue(source.contains("hipps.linkToEscalationLogic(esd, 2.0)"));
    assertTrue(source.contains("esd.addAction(new TripValveAction(esdShutdown), 0.0)"));
    assertTrue(source.contains("esd.execute(0.0)"));
    assertTrue(source.contains("esdShutdown.isClosing()"));

    try (URLClassLoader loader = compile(source)) {
      loader.setDefaultAssertionStatus(true);
      Class<?> example = Class.forName("LayeredSafetyQuickStart", true, loader);
      assertTrue(example.desiredAssertionStatus());
      try {
        example.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
      } catch (InvocationTargetException exception) {
        throw new AssertionError("LayeredSafetyQuickStart failed", exception.getCause());
      }
    }
  }

  /**
   * Extracts the uniquely marked Java fence.
   *
   * @param markdown complete guide source
   * @return exact published Java source
   */
  private static String markedExampleSource(String markdown) {
    int markerPosition = markdown.indexOf(QUICK_START_MARKER);
    assertTrue(markerPosition >= 0, "Missing layered-safety quick-start marker");
    assertEquals(markerPosition, markdown.lastIndexOf(QUICK_START_MARKER),
        "Layered-safety quick-start marker must be unique");

    Matcher fence = JAVA_FENCE.matcher(markdown.substring(markerPosition));
    assertTrue(fence.find(), "Missing Java fence after layered-safety quick-start marker");
    return fence.group(1);
  }

  /**
   * Compiles the exact guide source with Java 8 source and target compatibility.
   *
   * @param source exact published Java source
   * @return class loader for the compiled program
   * @throws Exception if source or class output cannot be created
   */
  private URLClassLoader compile(String source) throws Exception {
    Path classes = Files.createDirectories(temporaryDirectory.resolve("layered-safety-example"));
    Path javaFile = classes.resolve("LayeredSafetyQuickStart.java");
    Files.write(javaFile, source.getBytes(StandardCharsets.UTF_8));

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "Documentation examples require a JDK compiler");
    String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    StringWriter diagnostics = new StringWriter();
    try (StandardJavaFileManager manager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
      Boolean successful = compiler
          .getTask(diagnostics, manager, null, Arrays.asList("-source", "8", "-target", "8", "-proc:none", "-classpath",
              classPath, "-d", classes.toString()), null, manager.getJavaFileObjects(javaFile.toFile()))
          .call();
      assertTrue(Boolean.TRUE.equals(successful), diagnostics.toString());
    }
    return new URLClassLoader(new URL[] {classes.toUri().toURL()}, getClass().getClassLoader());
  }
}
