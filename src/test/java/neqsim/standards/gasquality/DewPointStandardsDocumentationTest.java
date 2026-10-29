package neqsim.standards.gasquality;

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
import neqsim.thermo.system.SystemGERGwaterEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Executes the complete examples and limitations in the dew-point standards guide. */
class DewPointStandardsDocumentationTest extends neqsim.NeqSimTest {
  private static final Path GUIDE = Paths.get("docs", "standards", "dew_point_standards.md");
  private static final String SAMPLE_SYSTEM_MARKER = "<!-- analyser-sample-system-example: complete -->";
  private static final String SAMPLE_SYSTEM_CLASS = "AnalyserSampleSystemGuideExample";
  private static final Pattern JAVA_FENCE = Pattern.compile("```java\\r?\\n(.*?)\\r?\\n```", Pattern.DOTALL);

  @TempDir
  Path temporaryDirectory;

  @Test
  void documentedWaterDewPointWorkflow() {
    SystemInterface wetGas = new SystemGERGwaterEos(268.15, 20.0);
    wetGas.addComponent("methane", 0.9);
    wetGas.addComponent("water", 0.0000051);
    wetGas.createDatabase(true);
    wetGas.setMixingRule(8);
    wetGas.init(0);

    Standard_ISO18453 waterDewPoint = new Standard_ISO18453(wetGas);
    waterDewPoint.setPressure(70.0);
    waterDewPoint.calculate();

    double waterDewPointC = waterDewPoint.getValue("dewPointTemperature", "C");
    double calculationPressureBara = waterDewPoint.getValue("pressure");

    assertEquals(-28.49313488, waterDewPointC, 1.0e-6);
    assertEquals(70.0, calculationPressureBara, 1.0e-12);
    assertEquals("C", waterDewPoint.getUnit("dewPointTemperature"));
    assertEquals(waterDewPointC + 273.15, waterDewPoint.getValue("dewPointTemperature", "K"), 1.0e-12);

    double maximumWaterDewPointC = -8.0;
    boolean withinWaterDewPointLimit = Double.isFinite(waterDewPointC) && waterDewPointC <= maximumWaterDewPointC;
    assertTrue(withinWaterDewPointLimit);
  }

  @Test
  void documentedHydrocarbonDewPointWorkflow() {
    SystemInterface richGas = createRichGas();

    BestPracticeHydrocarbonDewPoint hydrocarbonDewPoint = new BestPracticeHydrocarbonDewPoint(richGas);
    hydrocarbonDewPoint.calculate();

    double hydrocarbonDewPointC = hydrocarbonDewPoint.getValue("hydrocarbondewpointTemperature", "C");
    double calculationPressureBara = hydrocarbonDewPoint.getValue("pressure");

    assertTrue(Double.isFinite(hydrocarbonDewPointC));
    assertTrue(hydrocarbonDewPointC > -100.0 && hydrocarbonDewPointC < 100.0);
    assertEquals(50.0, calculationPressureBara, 1.0e-12);

    double maximumHydrocarbonDewPointC = -2.0;
    boolean comparisonIsDefined = Double.isFinite(hydrocarbonDewPointC) && Double.isFinite(maximumHydrocarbonDewPointC);
    assertTrue(comparisonIsDefined);
  }

  @Test
  void hydrocarbonReferencePressureSetterDoesNotChangeFixedPressure() {
    BestPracticeHydrocarbonDewPoint hydrocarbonDewPoint = new BestPracticeHydrocarbonDewPoint(createRichGas());
    hydrocarbonDewPoint.setReferencePressure(80.0);
    hydrocarbonDewPoint.calculate();

    assertEquals(80.0, hydrocarbonDewPoint.getReferencePressure(), 1.0e-12);
    assertEquals(50.0, hydrocarbonDewPoint.getValue("pressure"), 1.0e-12);
  }

  /**
   * Compiles and executes the exact sample-system screening program published by the guide.
   *
   * @throws Exception if the example cannot be extracted, compiled, or executed
   */
  @Test
  void documentedAnalyserSampleSystemWorkflowCompilesAndRuns() throws Exception {
    String source = markedSampleSystemSource();
    assertFalse(source.contains("System.out"));
    assertFalse(source.contains("System.err"));
    assertTrue(source.contains("new AnalyserSampleSystem(sampleGas)"));
    assertTrue(source.contains("getWaterCondensationMarginK()"));
    assertTrue(source.contains("getTransportLagSeconds()"));
    assertTrue(source.contains("getWaterFilmDryOutTimeHours("));

    try (URLClassLoader loader = compileSampleSystemExample(source)) {
      Class<?> example = loader.loadClass(SAMPLE_SYSTEM_CLASS);
      example.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
    }
  }

  /**
   * Extracts the uniquely marked Java fence from the guide.
   *
   * @return exact published Java source
   * @throws Exception if the guide cannot be read
   */
  private static String markedSampleSystemSource() throws Exception {
    String markdown = new String(Files.readAllBytes(GUIDE), StandardCharsets.UTF_8);
    int markerPosition = markdown.indexOf(SAMPLE_SYSTEM_MARKER);
    assertTrue(markerPosition >= 0, "Missing marker: " + SAMPLE_SYSTEM_MARKER);
    assertEquals(markerPosition, markdown.lastIndexOf(SAMPLE_SYSTEM_MARKER), "Sample-system marker must be unique");

    Matcher fence = JAVA_FENCE.matcher(markdown.substring(markerPosition));
    assertTrue(fence.find(), "Missing Java fence after sample-system marker");
    return fence.group(1);
  }

  /**
   * Compiles the exact Java fence with the Java 8 language level.
   *
   * @param source exact Java source from the guide
   * @return class loader for the compiled source
   * @throws Exception if source or class output cannot be created
   */
  private URLClassLoader compileSampleSystemExample(String source) throws Exception {
    Path classes = Files.createDirectories(temporaryDirectory.resolve(SAMPLE_SYSTEM_CLASS));
    Path javaFile = classes.resolve(SAMPLE_SYSTEM_CLASS + ".java");
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

  private static SystemInterface createRichGas() {
    SystemInterface richGas = new SystemSrkEos(293.15, 70.0);
    richGas.addComponent("methane", 0.85);
    richGas.addComponent("ethane", 0.05);
    richGas.addComponent("propane", 0.03);
    richGas.addComponent("i-butane", 0.01);
    richGas.addComponent("n-butane", 0.015);
    richGas.addComponent("i-pentane", 0.005);
    richGas.addComponent("n-pentane", 0.005);
    richGas.addComponent("n-hexane", 0.003);
    richGas.addComponent("nitrogen", 0.02);
    richGas.addComponent("CO2", 0.012);
    richGas.setMixingRule("classic");
    return richGas;
  }
}
