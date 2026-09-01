package neqsim.process.measurementdevice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Unit tests for the {@link FlowInducedVibrationAnalyser} class.
 */
public class FlowInducedVibrationAnalyserTest {
  private static final Logger logger = LogManager.getLogger(FlowInducedVibrationAnalyserTest.class);

  @Test
  @DisplayName("Test LOF calculation method with Stiff support arrangement")
  public void testLOFCalculationWithStiffSupport() {
    // Create a simple thermodynamic system with methane/ethane
    SystemInterface thermoSystem = new SystemSrkEos(298.15, 70.0);
    thermoSystem.addComponent("methane", 0.90);
    thermoSystem.addComponent("ethane", 0.10);
    thermoSystem.setMixingRule("classic");
    thermoSystem.setTotalFlowRate(100.0, "kg/hr");

    ThermodynamicOperations ops = new ThermodynamicOperations(thermoSystem);
    ops.TPflash();
    thermoSystem.initPhysicalProperties();

    // Create stream and pipe
    Stream stream = new Stream("test stream", thermoSystem);
    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("test pipe", stream);
    pipe.setDiameter(0.1); // 100 mm
    pipe.setThickness(0.01); // 10 mm
    pipe.setLength(50.0);
    pipe.setElevation(0.0);
    pipe.setPipeWallRoughness(1.0e-5);
    pipe.setNumberOfIncrements(10);

    // Create flow induced vibration analyzer with LOF method
    FlowInducedVibrationAnalyser analyzer = new FlowInducedVibrationAnalyser("LOF analyzer", pipe);
    analyzer.setMethod("LOF");
    analyzer.setSupportArrangement("Stiff"); // Default is Stiff

    // Create and run the process
    ProcessSystem process = new ProcessSystem();
    process.add(stream);
    process.add(pipe);
    process.add(analyzer);
    process.run();

    // Get measured LOF value
    double lofValue = analyzer.getMeasuredValue("any");

    // Assert LOF is within expected range for this type of flow
    assertTrue(lofValue > 0.0, "LOF value should be positive");
    assertTrue(lofValue < 1.0, "LOF value should be less than 1.0 for this flow regime");

    // The actual value will depend on the simulation results, but we can check if it's reasonable
    // logger.info("LOF value (Stiff support): " + lofValue);
  }

  @Test
  @DisplayName("Test LOF calculation with different support arrangements")
  public void testLOFCalculationWithDifferentSupports() {
    // Create a simple thermodynamic system with methane/ethane
    SystemInterface thermoSystem = new SystemSrkEos(298.15, 70.0);
    thermoSystem.addComponent("methane", 0.90);
    thermoSystem.addComponent("ethane", 0.10);
    thermoSystem.setMixingRule("classic");
    thermoSystem.setTotalFlowRate(100.0, "kg/hr");

    ThermodynamicOperations ops = new ThermodynamicOperations(thermoSystem);
    ops.TPflash();
    thermoSystem.initPhysicalProperties();

    // Create stream and pipe
    Stream stream = new Stream("test stream", thermoSystem);
    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("test pipe", stream);
    pipe.setDiameter(0.1); // 100 mm
    pipe.setThickness(0.01); // 10 mm
    pipe.setLength(50.0);
    pipe.setElevation(0.0);
    pipe.setPipeWallRoughness(1.0e-5);
    pipe.setNumberOfIncrements(10);

    // Create process system
    ProcessSystem process = new ProcessSystem();
    process.add(stream);
    process.add(pipe);

    // Test with different support arrangements
    FlowInducedVibrationAnalyser stiffAnalyzer = new FlowInducedVibrationAnalyser("Stiff analyzer", pipe);
    stiffAnalyzer.setMethod("LOF");
    stiffAnalyzer.setSupportArrangement("Stiff");
    process.add(stiffAnalyzer);

    FlowInducedVibrationAnalyser mediumStiffAnalyzer = new FlowInducedVibrationAnalyser("Medium stiff analyzer", pipe);
    mediumStiffAnalyzer.setMethod("LOF");
    mediumStiffAnalyzer.setSupportArrangement("Medium stiff");
    process.add(mediumStiffAnalyzer);

    FlowInducedVibrationAnalyser mediumAnalyzer = new FlowInducedVibrationAnalyser("Medium analyzer", pipe);
    mediumAnalyzer.setMethod("LOF");
    mediumAnalyzer.setSupportArrangement("Medium");
    process.add(mediumAnalyzer);

    // Run the process
    process.run();

    // Get measured values
    double stiffValue = stiffAnalyzer.getMeasuredValue();
    double mediumStiffValue = mediumStiffAnalyzer.getMeasuredValue();
    double mediumValue = mediumAnalyzer.getMeasuredValue();

    // logger.info("LOF value (Stiff support): " + stiffValue);
    // logger.info("LOF value (Medium stiff support): " + mediumStiffValue);
    // logger.info("LOF value (Medium support): " + mediumValue);

    // Different support arrangements should give different values
    // assertNotEquals(stiffValue, mediumStiffValue, DELTA);
    // assertNotEquals(mediumStiffValue, mediumValue, DELTA);
    // assertNotEquals(stiffValue, mediumValue, DELTA);

    // Stiff support should have the highest resistance to vibration (lowest LOF value)
    assertTrue(stiffValue < mediumStiffValue);
    assertTrue(mediumStiffValue < mediumValue);
  }

  @Test
  @DisplayName("Test FRMS calculation method")
  public void testFRMSCalculation() {
    // Create a thermodynamic system with gas-dominant composition
    SystemInterface thermoSystem = new SystemSrkEos(298.15, 70.0);
    thermoSystem.addComponent("methane", 0.90);
    thermoSystem.addComponent("ethane", 0.05);
    thermoSystem.addComponent("propane", 0.03);
    thermoSystem.addComponent("water", 0.02); // Small amount of water for two-phase behavior
    thermoSystem.setMixingRule("classic");
    thermoSystem.setTotalFlowRate(5000.0, "kg/hr"); // Higher flow rate for better phase separation

    ThermodynamicOperations ops = new ThermodynamicOperations(thermoSystem);
    ops.TPflash();
    thermoSystem.initPhysicalProperties();

    // Create stream and pipe
    Stream stream = new Stream("test stream", thermoSystem);
    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("test pipe", stream);
    pipe.setDiameter(0.1);
    pipe.setThickness(0.01);
    pipe.setLength(50.0);
    pipe.setElevation(0.0);
    pipe.setPipeWallRoughness(1.0e-5);
    pipe.setNumberOfIncrements(10);

    // Create flow induced vibration analyzer with FRMS method
    FlowInducedVibrationAnalyser frmsAnalyzer = new FlowInducedVibrationAnalyser("FRMS analyzer", pipe);
    frmsAnalyzer.setMethod("FRMS");
    frmsAnalyzer.setFRMSConstant(6.7); // Default constant

    // Create and run the process
    ProcessSystem process = new ProcessSystem();
    process.add(stream);
    process.add(pipe);
    process.add(frmsAnalyzer);
    process.run();

    // Get measured FRMS value
    double frmsValue = frmsAnalyzer.getMeasuredValue();

    logger.info("FRMS value: " + frmsValue);
    // The result depends on GVF. If GVF > 0.8, it will be calculated with the formula.
    // If GVF < 0.8, it will return the GVF value directly.
    // assertTrue(frmsValue > 0.0, "FRMS value should be positive");
  }

  @Test
  @DisplayName("Test specific segment selection")
  public void testSpecificSegmentSelection() {
    // Create a thermodynamic system
    SystemInterface thermoSystem = new SystemSrkEos(298.15, 70.0);
    thermoSystem.addComponent("methane", 0.90);
    thermoSystem.addComponent("ethane", 0.10);
    thermoSystem.setMixingRule("classic");
    thermoSystem.setTotalFlowRate(100.0, "kg/hr");

    ThermodynamicOperations ops = new ThermodynamicOperations(thermoSystem);
    ops.TPflash();
    thermoSystem.initPhysicalProperties();

    // Create stream and pipe with multiple segments
    Stream stream = new Stream("test stream", thermoSystem);
    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("test pipe", stream);
    pipe.setDiameter(0.1);
    pipe.setThickness(0.01);
    pipe.setLength(100.0);
    pipe.setElevation(10.0); // Add some elevation to create different segment properties
    pipe.setPipeWallRoughness(1.0e-5);
    pipe.setNumberOfIncrements(10);

    // Create analyzers for different segments
    FlowInducedVibrationAnalyser analyzerDefaultSegment = new FlowInducedVibrationAnalyser("Default segment analyzer",
        pipe);
    analyzerDefaultSegment.setMethod("LOF");

    FlowInducedVibrationAnalyser analyzerSegment5 = new FlowInducedVibrationAnalyser("Segment 5 analyzer", pipe);
    analyzerSegment5.setMethod("LOF");
    analyzerSegment5.setSegment(5); // Set to use segment 5

    // Create and run the process
    ProcessSystem process = new ProcessSystem();
    process.add(stream);
    process.add(pipe);
    process.add(analyzerDefaultSegment);
    process.add(analyzerSegment5);
    process.run();

    // Get measured values
    double defaultSegmentValue = analyzerDefaultSegment.getMeasuredValue();
    double segment5Value = analyzerSegment5.getMeasuredValue();

    logger.info("Default segment LOF value: " + defaultSegmentValue);
    logger.info("Segment 5 LOF value: " + segment5Value);

    // Due to pressure and density changes along the pipe, the values should be different
    // assertNotEquals(defaultSegmentValue, segment5Value, DELTA);
  }

  @Test
  @DisplayName("LOF with zero wall thickness throws a clear exception instead of returning NaN")
  public void testLOFRequiresPositiveWallThickness() {
    SystemInterface thermoSystem = new SystemSrkEos(298.15, 70.0);
    thermoSystem.addComponent("methane", 0.90);
    thermoSystem.addComponent("ethane", 0.10);
    thermoSystem.setMixingRule("classic");
    thermoSystem.setTotalFlowRate(100.0, "kg/hr");

    ThermodynamicOperations ops = new ThermodynamicOperations(thermoSystem);
    ops.TPflash();
    thermoSystem.initPhysicalProperties();

    Stream stream = new Stream("test stream", thermoSystem);
    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("test pipe", stream);
    pipe.setDiameter(0.1);
    // Intentionally leave the wall thickness unset (defaults to 0.0)
    pipe.setLength(50.0);
    pipe.setElevation(0.0);
    pipe.setPipeWallRoughness(1.0e-5);
    pipe.setNumberOfIncrements(10);

    FlowInducedVibrationAnalyser analyzer = new FlowInducedVibrationAnalyser("LOF analyzer", pipe);
    analyzer.setMethod("LOF");

    ProcessSystem process = new ProcessSystem();
    process.add(stream);
    process.add(pipe);
    process.add(analyzer);
    process.run();

    IllegalStateException ex = assertThrows(IllegalStateException.class, () -> analyzer.getMeasuredValue("any"));
    assertTrue(ex.getMessage().contains("wall thickness"),
        "Exception message should explain the missing wall thickness");
  }

  @Test
  @DisplayName("Support arrangement is validated and normalised; getters expose state")
  public void testSupportArrangementValidationAndGetters() {
    SystemInterface thermoSystem = new SystemSrkEos(298.15, 70.0);
    thermoSystem.addComponent("methane", 1.0);
    thermoSystem.setMixingRule("classic");
    Stream stream = new Stream("test stream", thermoSystem);
    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("test pipe", stream);

    FlowInducedVibrationAnalyser analyzer = new FlowInducedVibrationAnalyser("analyzer", pipe);

    // Default is Stiff
    assertEquals("Stiff", analyzer.getSupportArrangement());

    // Case-insensitive input is normalised to the canonical spelling
    analyzer.setSupportArrangement("medium STIFF");
    assertEquals("Medium stiff", analyzer.getSupportArrangement());

    analyzer.setSupportArrangement("Flexible");
    assertEquals("Flexible", analyzer.getSupportArrangement());

    // Invalid categories are rejected with a helpful message
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> analyzer.setSupportArrangement("Very Stiff"));
    assertTrue(ex.getMessage().contains("Valid values"));
    assertThrows(IllegalArgumentException.class, () -> analyzer.setSupportArrangement(null));

    // supportDistance is informational only but still round-trips
    analyzer.setSupportDistance(6.0);
    assertEquals(6.0, analyzer.getSupportDistance(), 1.0e-9);
  }

  /**
   * Builds a wet or dry inlet-gas case at a fixed standard gas rate and pressure and returns the LOF together with the
   * terms it is built from.
   *
   * @param dry true for an essentially dry gas (GVF above 0.99), false for a wet gas carrying condensate and water
   * @return array of {LOF, mixture density, mixture velocity, GVF, mixture viscosity in cP}
   */
  private double[] inletCase(boolean dry) {
    SystemInterface fluid = new SystemSrkEos(273.15 + 5.0, 48.0);
    fluid.addComponent("nitrogen", 0.60);
    fluid.addComponent("CO2", 1.60);
    fluid.addComponent("methane", 84.0);
    fluid.addComponent("ethane", 6.30);
    fluid.addComponent("propane", 2.70);
    double heavy = dry ? 0.0 : 3.6;
    fluid.addComponent("n-pentane", 0.35 * heavy + 0.05);
    fluid.addComponent("n-heptane", 0.35 * heavy + 0.01);
    fluid.addComponent("nC10", 0.30 * heavy + 0.005);
    fluid.addComponent("water", dry ? 0.02 : 2.0);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream stream = new Stream("inlet", fluid);
    stream.setTemperature(5.0, "C");
    stream.setPressure(48.0, "bara");
    stream.setFlowRate(10.5, "MSm3/day");

    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("inlet pipe", stream);
    pipe.setDiameter(0.3652);
    pipe.setThickness(0.0206);
    pipe.setLength(12.0);
    pipe.setElevation(0.0);
    pipe.setNumberOfIncrements(4);

    FlowInducedVibrationAnalyser analyzer = new FlowInducedVibrationAnalyser("LOF", pipe);
    analyzer.setMethod("LOF");
    analyzer.setSupportArrangement("Medium stiff");

    ProcessSystem process = new ProcessSystem();
    process.add(stream);
    process.add(pipe);
    process.run();

    int segment = pipe.getNumberOfIncrements();
    double rho = pipe.getSegmentMixtureDensity(segment);
    double velocity = pipe.getSegmentMixtureSuperficialVelocity(segment);
    double gvf = pipe.getSegmentGasSuperficialVelocity(segment) / velocity;
    return new double[] { analyzer.getMeasuredValue(""), rho, velocity, gvf,
        pipe.getSegmentMixtureViscosity(segment).doubleValue() };
  }

  @Test
  @DisplayName("Dry gas gives a lower LOF than wet gas at the same standard rate and pressure")
  public void testDryGasLofBelowWetGasLof() {
    double[] wet = inletCase(false);
    double[] dry = inletCase(true);

    assertTrue(wet[3] < 0.99, "the wet case should sit on the two-phase F_VF branch, GVF was " + wet[3]);
    assertTrue(dry[3] > 0.99, "the dry case should sit on the viscosity F_VF branch, GVF was " + dry[3]);

    // Removing the liquid lowers the mixture density far more than it raises the velocity, and it drops F_VF from
    // ~0.35 to ~0.11. A formulation that reports the dry case as the more onerous one is wrong.
    assertTrue(dry[0] < wet[0], "dry-gas LOF (" + dry[0] + ") must be below wet-gas LOF (" + wet[0] + ")");
    double ratio = wet[0] / dry[0];
    assertTrue(ratio > 2.0 && ratio < 6.0, "wet/dry LOF ratio should be around 3-4, was " + ratio);
  }

  @Test
  @DisplayName("F_VF above GVF 0.99 is the square root of the viscosity ratio to 1 cP")
  public void testDryGasFluidViscosityFactorReferencedToOneCentipoise() {
    double[] dry = inletCase(true);
    double lof = dry[0];
    double rhoV2 = dry[1] * dry[2] * dry[2];
    double expectedFvf = Math.sqrt(dry[4] / FlowInducedVibrationAnalyser.REFERENCE_VISCOSITY_CP);

    // Pipe factor for the "Medium stiff" arrangement, rebuilt from the documented EI coefficients.
    double externalDiameterMm = (0.3652 + 2 * 0.0206) * 1000.0;
    double alpha = 283921 + 370 * externalDiameterMm;
    double beta = 0.1106 * Math.log(externalDiameterMm) - 1.501;
    double fv = alpha * Math.pow(externalDiameterMm / (1000.0 * 0.0206), beta);

    assertEquals(rhoV2 * expectedFvf / fv, lof, 1.0e-6 * Math.max(1.0, lof));
    assertTrue(expectedFvf > 0.05 && expectedFvf < 0.2,
        "a hydrocarbon gas should give F_VF of roughly 0.11, was " + expectedFvf);
    assertTrue(expectedFvf < 0.268,
        "F_VF must continue below the 0.268 reached by the two-phase branch at GVF = 0.99, was " + expectedFvf);
  }
}
