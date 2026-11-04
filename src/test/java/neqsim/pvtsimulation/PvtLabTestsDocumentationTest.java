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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.NeqSimTest;
import neqsim.pvtsimulation.simulation.ConstantMassExpansion;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Verifies the executable constant-mass-expansion example and plus-fraction naming guidance.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class PvtLabTestsDocumentationTest extends NeqSimTest {
  private static final Path GUIDE = Paths.get("docs", "pvtsimulation", "pvt_lab_tests.md");
  private static final String QUICK_START_MARKER =
      "<!-- pvt-lab-example: constant-mass-expansion -->";
  private static final Pattern JAVA_FENCE =
      Pattern.compile("```java\\r?\\n(.*?)\\r?\\n```", Pattern.DOTALL);

  @TempDir
  Path temporaryDirectory;

  @Test
  void testPlusSignIsPreservedBeforeCharacterization() {
    SystemInterface fluid = new SystemSrkEos(298.15, 1.01325);
    fluid.addPlusFraction("C20+", 1.0, 0.453, 0.918);

    assertTrue(fluid.hasComponent("C20+_PC"));
  }

  /** Compiles and executes the exact complete Java example published in the guide. */
  @Test
  void literalConstantMassExpansionExampleCompilesAndRuns() throws Exception {
    String source = markedExampleSource(QUICK_START_MARKER, "ConstantMassExpansionExample");
    assertFalse(source.contains("System.out"));
    assertFalse(source.contains("System.err"));

    try (URLClassLoader loader = compile("ConstantMassExpansionExample", source)) {
      Class<?> exampleClass = loader.loadClass("ConstantMassExpansionExample");
      exampleClass.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
    }
  }

  @Test
  void testConstantMassExpansionQuickStart() {
    SystemInterface fluid = new SystemSrkEos(370.65, 350.0);
    fluid.addComponent("nitrogen", 0.39);
    fluid.addComponent("CO2", 0.30);
    fluid.addComponent("methane", 40.20);
    fluid.addComponent("ethane", 7.61);
    fluid.addComponent("propane", 7.95);
    fluid.addComponent("i-butane", 1.19);
    fluid.addComponent("n-butane", 4.08);
    fluid.addComponent("i-pentane", 1.39);
    fluid.addComponent("n-pentane", 2.15);
    fluid.addComponent("n-hexane", 2.79);
    fluid.addTBPfraction("C7", 4.28, 95.0 / 1000.0, 0.729);
    fluid.addTBPfraction("C8", 4.31, 106.0 / 1000.0, 0.749);
    fluid.addTBPfraction("C9", 3.08, 121.0 / 1000.0, 0.770);
    fluid.addTBPfraction("C10", 2.47, 135.0 / 1000.0, 0.786);
    fluid.addTBPfraction("C11", 1.91, 148.0 / 1000.0, 0.792);
    fluid.addTBPfraction("C12", 1.69, 161.0 / 1000.0, 0.804);
    fluid.addTBPfraction("C13", 1.59, 175.0 / 1000.0, 0.819);
    fluid.addTBPfraction("C14", 1.22, 196.0 / 1000.0, 0.833);
    fluid.addTBPfraction("C15", 1.25, 206.0 / 1000.0, 0.836);
    fluid.addTBPfraction("C16", 1.00, 225.0 / 1000.0, 0.843);
    fluid.addTBPfraction("C17", 0.99, 236.0 / 1000.0, 0.840);
    fluid.addTBPfraction("C18", 0.92, 245.0 / 1000.0, 0.846);
    fluid.addTBPfraction("C19", 0.60, 265.0 / 1000.0, 0.857);
    fluid.addPlusFraction("C20", 6.64, 453.0 / 1000.0, 0.918);
    assertTrue(fluid.hasComponent("C20_PC"));
    fluid.getCharacterization().getLumpingModel().setNumberOfPseudoComponents(12);
    fluid.getCharacterization().characterisePlusFraction();
    fluid.setMixingRule("classic");

    double[] pressuresBara = {351.4, 323.2, 301.5, 275.9, 250.1, 226.1, 205.9, 197.3, 189.3, 183.3, 165.0, 131.2, 108.3,
        85.3, 55.6};

    ConstantMassExpansion cce = new ConstantMassExpansion(fluid);
    cce.setTemperature(97.5, "C");
    cce.setPressures(pressuresBara);
    cce.runCalc();

    double[] relativeVolume = cce.getRelativeVolume();
    double[] liquidRelativeVolume = cce.getLiquidRelativeVolume();
    double[] gasZ = cce.getZgas();
    double[] yFunction = cce.getYfactor();

    assertEquals(pressuresBara.length, relativeVolume.length);
    assertEquals(pressuresBara.length, liquidRelativeVolume.length);
    assertEquals(pressuresBara.length, gasZ.length);
    assertEquals(pressuresBara.length, yFunction.length);
    assertEquals(0.95756922523, relativeVolume[0], 0.001);
    assertEquals(1.35726592522, relativeVolume[12], 0.001);
    assertEquals(2.19059327098, yFunction[12], 0.001);
    assertTrue(Double.isFinite(liquidRelativeVolume[12]));
    assertTrue(liquidRelativeVolume[12] >= 0.0);
    assertTrue(liquidRelativeVolume[12] <= 100.0);
    assertTrue(Double.isFinite(cce.getSaturationPressure()));
    assertTrue(cce.getSaturationPressure() > 0.0);
  }

  /**
   * Extracts one uniquely marked Java fence from the guide.
   *
   * @param marker unique marker immediately before the fence
   * @param className expected public class name
   * @return exact published Java source
   * @throws Exception if the guide cannot be read
   */
  private static String markedExampleSource(String marker, String className) throws Exception {
    String markdown = new String(Files.readAllBytes(GUIDE), StandardCharsets.UTF_8);
    int markerPosition = markdown.indexOf(marker);
    assertTrue(markerPosition >= 0, "Missing marker: " + marker);
    assertEquals(markerPosition, markdown.lastIndexOf(marker), "Marker must be unique: " + marker);

    Matcher fence = JAVA_FENCE.matcher(markdown.substring(markerPosition));
    assertTrue(fence.find(), "Missing Java fence after marker: " + marker);
    assertTrue(fence.group(1).contains("public final class " + className));
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
    return new URLClassLoader(
        new URL[] {classes.toUri().toURL()}, getClass().getClassLoader());
  }
}
