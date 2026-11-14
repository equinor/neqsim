package neqsim.documentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
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
 * Executes the literal Java program published by the ProcessModule guide.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class ProcessModuleDocumentationTest {
  private static final Path GUIDE = Paths.get("docs", "process", "processmodel", "process_module.md");
  private static final String MARKER = "<!-- process-module-example: nested-process-systems -->";
  private static final Pattern JAVA_FENCE = Pattern.compile("```java\\r?\\n(.*?)\\r?\\n```", Pattern.DOTALL);

  @TempDir
  Path temporaryDirectory;

  /** Verifies the page contract, compiles the literal Java 8 source, and executes it. */
  @Test
  void nestedProcessSystemExampleCompilesAndRuns() throws Exception {
    String source = exampleSource();

    assertFalse(source.contains("System.out"));
    assertFalse(source.contains("System.err"));
    assertFalse(source.contains("printStackTrace"));
    assertFalse(source.contains("catch ("));
    assertTrue(source.contains("LogManager.getLogger(ProcessModuleExample.class)"));
    assertTrue(source.contains("ProcessSystem heatingSystem"));
    assertTrue(source.contains("heatingSystem.add(feed)"));
    assertTrue(source.contains("heatingSystem.add(heater)"));
    assertTrue(source.contains("heatingArea.add(heatingSystem)"));
    assertTrue(source.contains("facility.add(heatingArea)"));
    assertTrue(source.contains("facility.run()"));
    assertTrue(source.contains("facility.solved()"));
    assertTrue(source.contains("facility.getAllProcessSystems().size() == 1"));
    assertTrue(source.contains("facility.getUnit(\"heater\") == heater"));
    assertTrue(source.contains("Double.isFinite(outletTemperatureK)"));
    assertTrue(source.contains("outletTemperatureK > 329.0"));
    assertTrue(source.contains("outletTemperatureK < 331.0"));

    try (URLClassLoader loader = compile("ProcessModuleExample", source)) {
      Class<?> example = loader.loadClass("ProcessModuleExample");
      example.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
    }
  }

  /**
   * Extracts the uniquely marked Java program and verifies the surrounding page contract.
   *
   * @return exact Java source published by the guide
   * @throws IOException if the guide cannot be read
   */
  private String exampleSource() throws IOException {
    String markdown = new String(Files.readAllBytes(GUIDE), StandardCharsets.UTF_8);
    assertEquals(markdown.indexOf(MARKER), markdown.lastIndexOf(MARKER), "ProcessModule example marker must be unique");
    assertFalse(markdown.contains("addModule"));
    assertFalse(markdown.contains("ModuleInterface"));
    assertFalse(markdown.contains("CompressorTrainModule"));
    assertFalse(markdown.contains("SeparationTrainModule"));
    assertFalse(markdown.contains("DehydrationModule"));
    assertFalse(markdown.contains("WaterTreatmentModule"));
    assertTrue(markdown.contains("does not accept equipment directly"));
    assertTrue(markdown.contains("[ProcessSystem](process_system.md)"));
    assertTrue(markdown.contains("[Graph Simulation](graph_simulation.md)"));
    assertTrue(markdown.contains("[Capacity Constraint Framework](../CAPACITY_CONSTRAINT_FRAMEWORK.md)"));

    String markedTail = markdown.substring(markdown.indexOf(MARKER) + MARKER.length());
    Matcher fenceMatcher = JAVA_FENCE.matcher(markedTail);
    assertTrue(fenceMatcher.find(), "Marked ProcessModule Java fence is missing");
    String source = fenceMatcher.group(1).trim();
    assertTrue(source.contains("public final class ProcessModuleExample"));
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
