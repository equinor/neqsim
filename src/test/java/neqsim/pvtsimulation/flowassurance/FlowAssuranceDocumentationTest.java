package neqsim.pvtsimulation.flowassurance;

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
import neqsim.pvtsimulation.flowassurance.DeBoerAsphalteneScreening.DeBoerRisk;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Verifies executable examples in the flow-assurance landing page and overview.
 *
 * @author ESOL
 * @version 1.0
 */
class FlowAssuranceDocumentationTest {
  private static final Path GUIDE = Paths.get("docs", "pvtsimulation", "flow_assurance_overview.md");
  private static final String MARKER = "<!-- flow-assurance-overview-example: integrated-screen -->";
  private static final Pattern JAVA_FENCE = Pattern.compile("```java\\r?\\n(.*?)\\r?\\n```", Pattern.DOTALL);

  @TempDir
  Path temporaryDirectory;

  /**
   * Compiles and executes the literal marked flow-assurance overview source.
   *
   * @throws Exception if the guide source cannot be compiled or executed
   */
  @Test
  void testIntegratedOverviewExecutesExactGuideSource() throws Exception {
    String source = markedExampleSource();

    assertFalse(source.contains("System.out"));
    assertFalse(source.contains("System.err"));
    assertTrue(source.contains("public class FlowAssuranceScreen"));
    assertTrue(source.contains("hydrateOps.hydrateFormationTemperature()"));
    assertTrue(source.contains("asphalteneScreen.evaluateRisk()"));
    assertTrue(source.contains("scaleScreen.calculate()"));

    try (URLClassLoader loader = compile("FlowAssuranceScreen", source)) {
      Class<?> example = loader.loadClass("FlowAssuranceScreen");
      example.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
    }
  }

  @Test
  void testDeBoerQuickStart() {
    DeBoerAsphalteneScreening screening = new DeBoerAsphalteneScreening(350.0, 150.0, 750.0);

    DeBoerRisk risk = screening.evaluateRisk();
    double riskIndex = screening.calculateRiskIndex();

    assertEquals(DeBoerRisk.MODERATE_PROBLEM, risk);
    assertEquals(1.6, riskIndex, 1.0e-12);
    assertFalse(risk.getDescription().trim().isEmpty());
  }

  /**
   * Verifies the hydrate and scale API calls in the integrated flow-assurance overview.
   *
   * @throws Exception if the hydrate equilibrium calculation fails
   */
  @Test
  void testIntegratedOverviewHydrateAndScaleScreen() throws Exception {
    SystemInterface hydrateFluid = new SystemElectrolyteCPAstatoil(273.15 + 10.0, 50.0);
    hydrateFluid.addComponent("water", 0.494505);
    hydrateFluid.addComponent("MEG", 0.164835);
    hydrateFluid.addComponent("methane", 0.247253);
    hydrateFluid.addComponent("ethane", 0.0164835);
    hydrateFluid.addComponent("propane", 0.010989);
    hydrateFluid.addComponent("i-butane", 0.00549451);
    hydrateFluid.addComponent("n-butane", 0.00549451);
    hydrateFluid.addComponent("Na+", 0.0274725);
    hydrateFluid.addComponent("Cl-", 0.0274725);
    hydrateFluid.setMixingRule(10);
    hydrateFluid.setMultiPhaseCheck(true);
    hydrateFluid.setHydrateCheck(true);

    ThermodynamicOperations hydrateOps = new ThermodynamicOperations(hydrateFluid);
    hydrateOps.hydrateFormationTemperature();
    double hydrateTemperatureC = hydrateFluid.getTemperature("C");

    assertTrue(Double.isFinite(hydrateTemperatureC));
    assertTrue(hydrateTemperatureC > -30.0 && hydrateTemperatureC < 15.0);

    ScalePredictionCalculator scaleScreen = new ScalePredictionCalculator();
    scaleScreen.setTemperatureCelsius(80.0);
    scaleScreen.setPressureBara(100.0);
    scaleScreen.setCalciumConcentration(400.0);
    scaleScreen.setBariumConcentration(10.0);
    scaleScreen.setStrontiumConcentration(5.0);
    scaleScreen.setIronConcentration(2.0);
    scaleScreen.setMagnesiumConcentration(1300.0);
    scaleScreen.setSodiumConcentration(11000.0);
    scaleScreen.setBicarbonateConcentration(150.0);
    scaleScreen.setSulphateConcentration(10.0);
    scaleScreen.setTotalDissolvedSolids(35000.0);
    scaleScreen.setCO2PartialPressure(2.0);
    scaleScreen.enableAutoPH();
    scaleScreen.calculate();

    assertTrue(Double.isFinite(scaleScreen.getCaCO3SaturationIndex()));
    assertTrue(Double.isFinite(scaleScreen.getBaSO4SaturationIndex()));
    assertEquals(!scaleScreen.getScaleRisks().isEmpty(), scaleScreen.hasScalingRisk());
    assertNotNull(scaleScreen.getScaleRisks());
    assertNotNull(scaleScreen.toJson());
  }

  @Test
  void testScreeningToolsGuide() {
    PipelineCooldownCalculator cooldown = new PipelineCooldownCalculator();
    cooldown.setInternalDiameter(0.254);
    cooldown.setWallThickness(0.0127);
    cooldown.setInsulationThickness(0.050);
    cooldown.setInitialFluidTemperature(273.15 + 80.0);
    cooldown.setAmbientTemperature(273.15 + 4.0);
    cooldown.setFluidDensity(750.0);
    cooldown.setFluidSpecificHeat(2200.0);
    cooldown.setOverallUValue(3.0);
    cooldown.setTimeStepMinutes(5.0);
    cooldown.setTotalTimeHours(48.0);
    cooldown.calculate();

    assertTrue(Double.isFinite(cooldown.getTimeConstantHours()));
    assertTrue(cooldown.getTimeConstantHours() > 0.0);
    assertTrue(cooldown.getTemperatureAtTime(12.0) < 273.15 + 80.0);
    assertTrue(cooldown.getTemperatureAtTime(12.0) > 273.15 + 4.0);
    assertTrue(cooldown.getTimeToReachTemperature(273.15 + 20.0) >= -1.0);
    assertFalse(cooldown.toJson().trim().isEmpty());

    DeWaardMilliamsCorrosion corrosion = new DeWaardMilliamsCorrosion();
    corrosion.setTemperatureCelsius(60.0);
    corrosion.setCO2PartialPressure(2.0);
    corrosion.setPH(4.5);
    corrosion.setFlowVelocity(2.0);
    corrosion.setInhibitorEfficiency(0.80);
    double corrosionRate = corrosion.calculateCorrosionRate();

    assertTrue(Double.isFinite(corrosionRate));
    assertTrue(corrosionRate >= 0.0);
    assertEquals(corrosionRate * 25.0, corrosion.estimateCorrosionAllowance(25.0), 1.0e-12);

    ScalePredictionCalculator scale = new ScalePredictionCalculator();
    scale.setTemperatureCelsius(80.0);
    scale.setPressureBara(100.0);
    scale.setCalciumConcentration(1000.0);
    scale.setBicarbonateConcentration(500.0);
    scale.setBariumConcentration(50.0);
    scale.setSulphateConcentration(200.0);
    scale.setTotalDissolvedSolids(50000.0);
    scale.setCO2PartialPressure(2.0);
    scale.enableAutoPH();
    scale.calculate();

    assertTrue(Double.isFinite(scale.getCaCO3SaturationIndex()));
    assertTrue(Double.isFinite(scale.getBaSO4SaturationIndex()));
    assertEquals(!scale.getScaleRisks().isEmpty(), scale.hasScalingRisk());
    assertFalse(scale.toJson().trim().isEmpty());

    SystemInterface waxFluid = new SystemSrkEos(273.15 + 60.0, 50.0);
    waxFluid.addComponent("methane", 1.0);
    WaxCurveCalculator waxCurve = new WaxCurveCalculator(waxFluid);
    waxCurve.setPressure(50.0);
    waxCurve.setTemperatureRange(-10.0, 60.0, 1.0);
    assertEquals(50.0, waxCurve.getPressureBara(), 1.0e-12);

    double[] fractions = {0.0, 0.02, 0.01, 0.03};
    assertEquals(1, WaxCurveCalculator.countMonotonicityViolations(fractions, true));
    assertEquals(1, WaxCurveCalculator.enforceNonDecreasing(fractions));
    assertEquals(0.02, fractions[2], 1.0e-12);
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

    Matcher fence = JAVA_FENCE.matcher(markdown.substring(markerPosition));
    assertTrue(fence.find(), "Missing Java fence after marker: " + MARKER);
    assertTrue(fence.group(1).contains("public class FlowAssuranceScreen"));
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
