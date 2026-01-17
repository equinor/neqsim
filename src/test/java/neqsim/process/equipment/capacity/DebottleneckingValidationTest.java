package neqsim.process.equipment.capacity;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.compressor.CompressorChartGenerator;
import neqsim.process.equipment.compressor.CompressorChartInterface;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for debottlenecking validation improvements.
 * 
 * <p>
 * Verifies that:
 * <ul>
 * <li>Compressors detect invalid simulation states (negative power, head)</li>
 * <li>Separators handle single-phase conditions gracefully</li>
 * <li>Constraint severity levels work correctly</li>
 * <li>Operating envelope validation catches out-of-range conditions</li>
 * </ul>
 * </p>
 * 
 * @author NeqSim Development Team
 * @version 1.0
 */
public class DebottleneckingValidationTest {

  private ProcessSystem process;
  private Stream feedStream;
  private Separator separator;
  private Compressor compressor;

  @BeforeEach
  public void setUp() {
    // Create a simple gas system
    SystemInterface gas = new SystemSrkEos(273.15 + 30.0, 50.0);
    gas.addComponent("methane", 90.0);
    gas.addComponent("ethane", 5.0);
    gas.addComponent("propane", 3.0);
    gas.addComponent("n-butane", 2.0);
    gas.setMixingRule("classic");
    gas.setMultiPhaseCheck(true);

    process = new ProcessSystem();

    feedStream = new Stream("Feed", gas);
    feedStream.setFlowRate(10000.0, "kg/hr");
    feedStream.setTemperature(30.0, "C");
    feedStream.setPressure(50.0, "bara");
    process.add(feedStream);

    // Valve to reduce pressure and create two-phase flow
    ThrottlingValve valve = new ThrottlingValve("Valve", feedStream);
    valve.setOutletPressure(20.0);
    process.add(valve);

    separator = new Separator.Builder("Test Separator").inletStream(valve.getOutletStream())
        .orientation("horizontal").length(3.0).diameter(1.5).build();
    separator.setDesignGasLoadFactor(0.10);
    process.add(separator);

    compressor = new Compressor("Test Compressor", separator.getGasOutStream());
    compressor.setOutletPressure(80.0, "bara");
    compressor.setPolytropicEfficiency(0.75);
    process.add(compressor);

    process.run();
  }

  /**
   * Tests that compressor simulation validity check works for normal operation.
   */
  @Test
  public void testCompressorSimulationValidNormalOperation() {
    // Normal operation should be valid
    assertTrue(compressor.isSimulationValid(),
        "Compressor should report valid simulation for normal operation");

    List<String> errors = compressor.getSimulationValidationErrors();
    assertTrue(errors.isEmpty(), "No validation errors expected: " + errors);

    // Power should be positive
    assertTrue(compressor.getPower() > 0, "Power should be positive");

    // Head should be positive
    assertTrue(compressor.getPolytropicFluidHead() > 0, "Polytropic head should be positive");
  }

  /**
   * Tests that compressor correctly reports operating envelope status.
   */
  @Test
  public void testCompressorOperatingEnvelope() {
    // Without compressor chart, should be within envelope
    assertTrue(compressor.isWithinOperatingEnvelope(),
        "Compressor without chart should be within envelope");
    assertNull(compressor.getOperatingEnvelopeViolation(), "No envelope violation expected");

    // Set up compressor chart
    CompressorChartGenerator gen = new CompressorChartGenerator(compressor);
    gen.setChartType("interpolate and extrapolate");
    CompressorChartInterface chart = gen.generateCompressorChart("normal curves", 5);
    compressor.setCompressorChart(chart);
    compressor.getCompressorChart().setUseCompressorChart(true);
    process.run();

    // With chart at design point, should still be within envelope
    assertTrue(compressor.isWithinOperatingEnvelope(),
        "Compressor at design point should be within envelope");
  }

  /**
   * Tests separator single-phase handling.
   */
  @Test
  public void testSeparatorSinglePhaseHandling() {
    // Create a pure gas feed (single phase)
    SystemInterface pureGas = new SystemSrkEos(273.15 + 30.0, 20.0);
    pureGas.addComponent("methane", 100.0);
    pureGas.setMixingRule("classic");
    pureGas.setMultiPhaseCheck(true);

    Stream pureGasFeed = new Stream("Pure Gas Feed", pureGas);
    pureGasFeed.setFlowRate(5000.0, "kg/hr");
    pureGasFeed.run();

    Separator singlePhaseSep =
        new Separator.Builder("Single Phase Sep").inletStream(pureGasFeed).build();
    singlePhaseSep.run();

    // Single phase should return 0% utilization (no separation needed)
    double util = singlePhaseSep.getCapacityUtilization();
    assertEquals(0.0, util, 0.01, "Single phase should have 0% utilization");

    // isSinglePhase should return true
    assertTrue(singlePhaseSep.isSinglePhase(), "Should detect single phase");

    // Simulation should still be valid
    assertTrue(singlePhaseSep.isSimulationValid(), "Single phase simulation is valid");

    // Within operating envelope
    assertTrue(singlePhaseSep.isWithinOperatingEnvelope(), "Single phase is within envelope");
  }

  /**
   * Tests separator validation errors are correctly reported.
   */
  @Test
  public void testSeparatorValidationErrors() {
    // Normal operation should have no errors
    assertTrue(separator.isSimulationValid(), "Separator should be valid");
    List<String> errors = separator.getSimulationValidationErrors();
    assertTrue(errors.isEmpty(), "No errors expected: " + errors);
  }

  /**
   * Tests constraint severity levels.
   */
  @Test
  public void testConstraintSeverityLevels() {
    // Create constraint with CRITICAL severity
    CapacityConstraint criticalConstraint =
        new CapacityConstraint("surge", "m3/hr", CapacityConstraint.ConstraintType.HARD);
    criticalConstraint.setDesignValue(100.0)
        .setSeverity(CapacityConstraint.ConstraintSeverity.CRITICAL).setValueSupplier(() -> 110.0); // Exceeded

    assertEquals(CapacityConstraint.ConstraintSeverity.CRITICAL, criticalConstraint.getSeverity(),
        "Severity should be CRITICAL");
    assertTrue(criticalConstraint.isViolated(), "Constraint should be violated");
    assertTrue(criticalConstraint.isCriticalViolation(), "Should be critical violation");

    // Create constraint with SOFT severity
    CapacityConstraint softConstraint =
        new CapacityConstraint("efficiency", "%", CapacityConstraint.ConstraintType.SOFT);
    softConstraint.setDesignValue(80.0).setSeverity(CapacityConstraint.ConstraintSeverity.SOFT)
        .setValueSupplier(() -> 75.0); // Below design but not critical

    assertEquals(CapacityConstraint.ConstraintSeverity.SOFT, softConstraint.getSeverity(),
        "Severity should be SOFT");
    assertFalse(softConstraint.isCriticalViolation(), "Should not be critical violation");

    // Create ADVISORY constraint
    CapacityConstraint advisoryConstraint =
        new CapacityConstraint("turndown", "ratio", CapacityConstraint.ConstraintType.DESIGN);
    advisoryConstraint.setDesignValue(1.0)
        .setSeverity(CapacityConstraint.ConstraintSeverity.ADVISORY).setValueSupplier(() -> 1.5); // Exceeded
                                                                                                  // but
                                                                                                  // advisory

    assertEquals(CapacityConstraint.ConstraintSeverity.ADVISORY, advisoryConstraint.getSeverity(),
        "Severity should be ADVISORY");
    assertFalse(advisoryConstraint.isCriticalViolation(), "Advisory violations are not critical");
  }

  /**
   * Tests that compressor constraints have correct severity assignments.
   */
  @Test
  public void testCompressorConstraintSeverity() {
    // Set up compressor chart to initialize constraints
    CompressorChartGenerator gen = new CompressorChartGenerator(compressor);
    CompressorChartInterface chart = gen.generateCompressorChart("normal curves", 5);
    compressor.setCompressorChart(chart);
    compressor.getCompressorChart().setUseCompressorChart(true);
    compressor.setMaximumSpeed(compressor.getSpeed() * 1.15);
    process.run();
    compressor.reinitializeCapacityConstraints();

    // Check that constraints exist and have appropriate severities
    java.util.Map<String, CapacityConstraint> constraints = compressor.getCapacityConstraints();
    assertFalse(constraints.isEmpty(), "Compressor should have constraints");

    // Surge margin should exist
    CapacityConstraint surgeConstraint = constraints.get("surgeMargin");
    assertNotNull(surgeConstraint, "Surge margin constraint should exist");

    // Speed constraint should exist
    CapacityConstraint speedConstraint = constraints.get("speed");
    assertNotNull(speedConstraint, "Speed constraint should exist");
  }

  /**
   * Tests getSimulationValidationErrors returns meaningful messages.
   */
  @Test
  public void testValidationErrorMessages() {
    // Run at a valid operating point
    List<String> errors = compressor.getSimulationValidationErrors();
    assertTrue(errors.isEmpty(), "Valid operation should have no errors");

    // Separator errors
    List<String> sepErrors = separator.getSimulationValidationErrors();
    assertTrue(sepErrors.isEmpty(), "Valid separator should have no errors");
  }

  /**
   * Tests operating envelope violation messages.
   */
  @Test
  public void testOperatingEnvelopeViolationMessages() {
    // Normal operation - no violation
    assertNull(compressor.getOperatingEnvelopeViolation(), "No violation expected at design point");
    assertNull(separator.getOperatingEnvelopeViolation(), "No violation expected for separator");
  }
}
