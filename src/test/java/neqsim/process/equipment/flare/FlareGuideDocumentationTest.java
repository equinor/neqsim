package neqsim.process.equipment.flare;

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

/** Compiles and executes the literal quick start published in the flare equipment guide. */
@Tag("slow")
class FlareGuideDocumentationTest extends NeqSimTest {
  private static final Path GUIDE = Paths.get("docs", "process", "equipment", "flares.md");
  private static final String QUICK_START_MARKER =
      "<!-- flare-guide-example: java-quickstart -->";
  private static final Pattern JAVA_FENCE =
      Pattern.compile("```java\\r?\\n(.*?)\\r?\\n```", Pattern.DOTALL);

  @TempDir Path temporaryDirectory;

  /**
   * Compiles and runs the exact marked Java source with assertions enabled.
   *
   * @throws Exception if guide reading, compilation, or execution fails
   */
  @Test
  void literalFlareQuickStartCompilesAndRuns() throws Exception {
    String markdown = new String(Files.readAllBytes(GUIDE), StandardCharsets.UTF_8);
    String source = markedExampleSource(markdown);

    assertFalse(source.contains("System.out"));
    assertFalse(source.contains("System.err"));
    assertTrue(source.contains("public final class FlareQuickStart"));
    assertTrue(source.contains("new SystemSrkEos(298.15, 1.2)"));
    assertTrue(source.contains("flareGas.setFlowRate(1000.0, \"kg/hr\")"));
    assertTrue(source.contains("ProcessSystem process = new ProcessSystem()"));
    assertTrue(source.contains("process.run()"));
    assertTrue(source.contains("flare.getHeatDuty(\"MW\")"));
    assertTrue(source.contains("flare.getCO2Emission(\"kg/hr\")"));
    assertTrue(source.contains("Double.isFinite(heatReleaseMW)"));
    assertTrue(source.contains("Double.isFinite(carbonDioxideKgPerHr)"));

    compileAndRun(source);
  }

  private static String markedExampleSource(String markdown) {
    int markerPosition = markdown.indexOf(QUICK_START_MARKER);
    assertTrue(markerPosition >= 0, "Missing flare quick-start marker");
    assertTrue(
        markerPosition == markdown.lastIndexOf(QUICK_START_MARKER),
        "Flare quick-start marker must be unique");

    Matcher fence = JAVA_FENCE.matcher(markdown.substring(markerPosition));
    assertTrue(fence.find(), "Missing Java fence after flare quick-start marker");
    return fence.group(1);
  }

  private void compileAndRun(String source) throws Exception {
    Path outputDirectory = Files.createDirectories(temporaryDirectory.resolve("flare-example"));
    Path javaSource = outputDirectory.resolve("FlareQuickStart.java");
    Files.write(javaSource, source.getBytes(StandardCharsets.UTF_8));

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "Documentation examples require a JDK compiler");
    String classPath =
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
                      classPath,
                      "-d",
                      outputDirectory.toString()),
                  null,
                  manager.getJavaFileObjects(javaSource.toFile()))
              .call();
      assertTrue(Boolean.TRUE.equals(successful), diagnostics.toString());
    }

    try (URLClassLoader loader =
        new URLClassLoader(
            new URL[] {outputDirectory.toUri().toURL()}, getClass().getClassLoader())) {
      loader.setDefaultAssertionStatus(true);
      Class<?> example = Class.forName("FlareQuickStart", true, loader);
      assertTrue(example.desiredAssertionStatus());
      try {
        example.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
      } catch (InvocationTargetException exception) {
        throw new AssertionError("FlareQuickStart failed", exception.getCause());
      }
    }
  }
}
