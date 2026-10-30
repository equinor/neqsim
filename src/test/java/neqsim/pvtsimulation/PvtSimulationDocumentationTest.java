package neqsim.pvtsimulation;

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
import neqsim.pvtsimulation.simulation.MultiStageSeparatorTest;
import neqsim.pvtsimulation.simulation.MultiStageSeparatorTest.SeparatorStageResult;

/**
 * Compiles and executes the exact complete Java program in the PVT package guide.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class PvtSimulationDocumentationTest {
  private static final Path GUIDE = Paths.get("docs", "pvtsimulation", "README.md");
  private static final String MARKER =
      "<!-- pvt-separator-quickstart-example: multi-stage -->";
  private static final Pattern JAVA_FENCE =
      Pattern.compile("```java\\r?\\n(.*?)\\r?\\n```", Pattern.DOTALL);

  @TempDir
  Path temporaryDirectory;

  /**
   * Verifies that the marked separator quick start is literal executable source.
   *
   * @throws Exception if the guide source cannot be compiled or executed
   */
  @Test
  void multiStageSeparatorQuickStartCompilesAndRunsFromGuide() throws Exception {
    String source = markedExampleSource();

    assertFalse(source.contains("System.out"));
    assertFalse(source.contains("System.err"));
    assertTrue(source.contains("public final class PvtSeparatorQuickStart"));
    assertTrue(source.contains("public static MultiStageSeparatorTest runExample()"));
    assertTrue(source.contains("new MultiStageSeparatorTest(reservoirFluid)"));
    assertTrue(source.contains("new SystemSrkEos(373.15, 300.0)"));
    assertTrue(source.contains("setReservoirConditions(300.0, 100.0)"));
    assertTrue(source.contains("addSeparatorStage(50.0, 40.0, \"HP separator\")"));
    assertTrue(source.contains("addSeparatorStage(10.0, 30.0, \"LP separator\")"));
    assertTrue(source.contains("separatorTest.addStockTankStage();"));
    assertTrue(source.contains("separatorTest.run();"));

    try (URLClassLoader loader = compile("PvtSeparatorQuickStart", source)) {
      Class<?> example = loader.loadClass("PvtSeparatorQuickStart");
      example.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
      Object result = example.getMethod("runExample").invoke(null);
      assertTrue(result instanceof MultiStageSeparatorTest);

      MultiStageSeparatorTest separatorTest = (MultiStageSeparatorTest) result;
      List<SeparatorStageResult> stages = separatorTest.getStageResults();
      assertEquals(3, stages.size());
      assertEquals("HP separator", stages.get(0).getStageName());
      assertEquals(50.0, stages.get(0).getPressure(), 1.0e-12);
      assertEquals(40.0, stages.get(0).getTemperature(), 1.0e-12);
      assertEquals("LP separator", stages.get(1).getStageName());
      assertEquals(10.0, stages.get(1).getPressure(), 1.0e-12);
      assertEquals(30.0, stages.get(1).getTemperature(), 1.0e-12);
      assertEquals(separatorTest.getTotalGOR(), stages.get(2).getCumulativeGOR(), 0.1);
      assertTrue(Double.isFinite(separatorTest.getTotalGOR()));
      assertTrue(separatorTest.getTotalGOR() > 0.0);
      assertTrue(Double.isFinite(separatorTest.getBo()));
      assertTrue(separatorTest.getBo() > 1.0);
      assertTrue(Double.isFinite(separatorTest.getStockTankOilDensity()));
      assertTrue(separatorTest.getStockTankOilDensity() > 500.0);
      assertTrue(separatorTest.getStockTankOilDensity() < 1200.0);
    }
  }

  /**
   * Extracts the uniquely marked Java fence.
   *
   * @return exact published Java source
   * @throws Exception if the guide cannot be read
   */
  private static String markedExampleSource() throws Exception {
    String markdown = new String(Files.readAllBytes(GUIDE), StandardCharsets.UTF_8);
    int markerPosition = markdown.indexOf(MARKER);
    assertTrue(markerPosition >= 0, "Missing marker: " + MARKER);
    assertEquals(markerPosition, markdown.lastIndexOf(MARKER), "Marker must be unique: " + MARKER);

    Matcher fenceMatcher = JAVA_FENCE.matcher(markdown.substring(markerPosition));
    assertTrue(fenceMatcher.find(), "Missing Java fence after marker: " + MARKER);
    assertTrue(fenceMatcher.group(1).contains("public final class PvtSeparatorQuickStart"));
    return fenceMatcher.group(1);
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
    return new URLClassLoader(new URL[] {classes.toUri().toURL()}, getClass().getClassLoader());
  }
}
