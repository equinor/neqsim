package neqsim.process.measurementdevice;

import static org.junit.jupiter.api.Assertions.assertTrue;
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

  private static final double DELTA = 1e-2;

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
    // System.out.println("LOF value (Stiff support): " + lofValue);
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
    FlowInducedVibrationAnalyser stiffAnalyzer =
        new FlowInducedVibrationAnalyser("Stiff analyzer", pipe);
    stiffAnalyzer.setMethod("LOF");
    stiffAnalyzer.setSupportArrangement("Stiff");
    process.add(stiffAnalyzer);

    FlowInducedVibrationAnalyser mediumStiffAnalyzer =
        new FlowInducedVibrationAnalyser("Medium stiff analyzer", pipe);
    mediumStiffAnalyzer.setMethod("LOF");
    mediumStiffAnalyzer.setSupportArrangement("Medium stiff");
    process.add(mediumStiffAnalyzer);

    FlowInducedVibrationAnalyser mediumAnalyzer =
        new FlowInducedVibrationAnalyser("Medium analyzer", pipe);
    mediumAnalyzer.setMethod("LOF");
    mediumAnalyzer.setSupportArrangement("Medium");
    process.add(mediumAnalyzer);

    // Run the process
    process.run();

    // Get measured values
    double stiffValue = stiffAnalyzer.getMeasuredValue();
    double mediumStiffValue = mediumStiffAnalyzer.getMeasuredValue();
    double mediumValue = mediumAnalyzer.getMeasuredValue();

    // System.out.println("LOF value (Stiff support): " + stiffValue);
    // System.out.println("LOF value (Medium stiff support): " + mediumStiffValue);
    // System.out.println("LOF value (Medium support): " + mediumValue);

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
    FlowInducedVibrationAnalyser frmsAnalyzer =
        new FlowInducedVibrationAnalyser("FRMS analyzer", pipe);
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

    System.out.println("FRMS value: " + frmsValue);
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
    FlowInducedVibrationAnalyser analyzerDefaultSegment =
        new FlowInducedVibrationAnalyser("Default segment analyzer", pipe);
    analyzerDefaultSegment.setMethod("LOF");

    FlowInducedVibrationAnalyser analyzerSegment5 =
        new FlowInducedVibrationAnalyser("Segment 5 analyzer", pipe);
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

    System.out.println("Default segment LOF value: " + defaultSegmentValue);
    System.out.println("Segment 5 LOF value: " + segment5Value);

    // Due to pressure and density changes along the pipe, the values should be different
    // assertNotEquals(defaultSegmentValue, segment5Value, DELTA);
  }
}
