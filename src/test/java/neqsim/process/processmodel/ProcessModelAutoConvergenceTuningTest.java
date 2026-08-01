package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the automatic convergence tuning that lets {@code runUntilConverged(maxIterations)} configure its own flow
 * noise filters instead of requiring hand-picked, plant-specific numbers.
 *
 * @author NeqSim
 * @version 1.0
 */
class ProcessModelAutoConvergenceTuningTest {

  /**
   * Creates a small two-component gas fluid.
   *
   * @return configured gas fluid
   */
  private static SystemInterface createGasFluid() {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /**
   * Builds a two-area model: a main train carrying {@code mainFlow} kg/hr and a stagnant dead leg carrying
   * {@code deadLegFlow} kg/hr.
   *
   * @param mainFlow main-train mass flow in kg/hr
   * @param deadLegFlow dead-leg mass flow in kg/hr
   * @return a runnable two-area ProcessModel
   */
  private static ProcessModel buildModelWithDeadLeg(double mainFlow, double deadLegFlow) {
    Stream feed = new Stream("feed", createGasFluid());
    feed.setFlowRate(mainFlow, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(50.0, "bara");
    Separator separator = new Separator("separator", feed);

    ProcessSystem mainTrain = new ProcessSystem("main train");
    mainTrain.add(feed);
    mainTrain.add(separator);

    Stream seed = new Stream("dead leg seed", createGasFluid());
    seed.setFlowRate(deadLegFlow, "kg/hr");
    seed.setTemperature(25.0, "C");
    seed.setPressure(50.0, "bara");
    Heater deadLegHeater = new Heater("dead leg heater", seed);
    deadLegHeater.setOutTemperature(40.0, "C");

    ProcessSystem deadLeg = new ProcessSystem("dead leg");
    deadLeg.add(seed);
    deadLeg.add(deadLegHeater);

    ProcessModel model = new ProcessModel();
    model.add("main train", mainTrain);
    model.add("dead leg", deadLeg);
    return model;
  }

  /**
   * The single-argument runUntilConverged should converge and self-configure its flow filters from the plant scale.
   */
  @Test
  void testSingleArgumentRunUntilConvergedAutoTunes() {
    ProcessModel model = buildModelWithDeadLeg(1.0e6, 0.05);

    assertTrue(model.isAutoConvergenceTuning(), "Auto convergence tuning should be on by default");
    boolean converged = model.runUntilConverged(25);

    assertTrue(converged, "Feed-forward model should converge");
    assertFalse(model.isRunStep(), "runUntilConverged must force iterating mode");
    assertEquals(25, model.getMaxIterations(), "maxIterations should be applied");
    assertEquals(1.0e6, model.getDetectedPlantFlowScale(), 1.0,
        "Plant flow scale should be the total feed entering the plant");
    assertEquals(1.0, model.getBoundaryFlowFloor(), 1e-3,
        "Boundary flow floor should be 1e-6 of the detected plant scale");
    assertEquals(1.0, model.getAbsoluteFlowTolerance(), 1e-3,
        "Absolute flow tolerance should be 1e-6 of the detected plant scale");
    assertFalse(model.getAutoTuningSummary().isEmpty(), "Auto-tuning summary should be populated");
  }

  /**
   * The flow scale must be the feed boundary, not the largest number found anywhere in the flowsheet: an internal or
   * not-yet-solved stream can carry an arbitrary flow and would otherwise set a meaningless scale.
   */
  @Test
  void testFlowScaleIsTheFeedBoundaryNotTheLargestStream() {
    ProcessModel model = buildModelWithDeadLeg(1.0e6, 0.05);
    model.runUntilConverged(25);

    assertEquals(1.0e6, model.getTotalFeedFlowRate(), 1.0, "Total feed should be the sum of the plant feed streams");
    assertEquals(model.getTotalFeedFlowRate(), model.getDetectedPlantFlowScale(), 1e-6,
        "The auto-tuner must use the feed boundary as its scale");

    // A recycle-fed or internally produced stream is not a feed even though a unit consumes it.
    assertEquals(2, model.get("main train").getFeedStreams().size() + model.get("dead leg").getFeedStreams().size(),
        "Only the two source streams should be reported as feeds");
  }

  /**
   * A dead leg below the detected noise floor should be auto-bypassed without any per-section configuration.
   */
  @Test
  void testStagnantSectionIsAutoBypassed() {
    ProcessModel model = buildModelWithDeadLeg(1.0e6, 0.05);
    model.runUntilConverged(25);

    assertTrue(model.getBypassedUnits().size() > 0, "The stagnant dead leg should be auto-bypassed");
    assertTrue(model.getBypassedUnits().toString().contains("dead leg heater"),
        "The dead-leg heater should be among the bypassed units, was " + model.getBypassedUnits());
    assertEquals(0, model.get("main train").getBypassedUnits().size(), "No unit on the main train should be bypassed");
  }

  /**
   * The same model at a different throughput must self-configure without any parameter change.
   */
  @Test
  void testThresholdsScaleWithPlantThroughput() {
    ProcessModel small = buildModelWithDeadLeg(1.0e4, 0.05);
    small.runUntilConverged(25);

    ProcessModel large = buildModelWithDeadLeg(1.0e7, 0.05);
    large.runUntilConverged(25);

    assertEquals(1.0e-2, small.getBoundaryFlowFloor(), 1e-5, "Small plant should get a small noise floor");
    assertEquals(10.0, large.getBoundaryFlowFloor(), 1e-2, "Large plant should get a proportionally larger floor");
  }

  /**
   * Disabling auto tuning must restore the historical un-filtered behaviour.
   */
  @Test
  void testAutoTuningCanBeDisabled() {
    ProcessModel model = buildModelWithDeadLeg(1.0e6, 0.05);
    model.setAutoConvergenceTuning(false);
    model.runUntilConverged(25);

    assertEquals(ProcessModel.DEFAULT_BOUNDARY_FLOW_FLOOR, model.getBoundaryFlowFloor(), 1e-15,
        "Boundary flow floor should stay at the default when auto tuning is off");
    assertEquals(0.0, model.getAbsoluteFlowTolerance(), 1e-15,
        "Absolute flow tolerance should stay at zero when auto tuning is off");
    assertEquals(0, model.getBypassedUnits().size(), "No unit should be auto-bypassed when auto tuning is off");
  }

  /**
   * Explicitly configured filters must always win over the automatic values.
   */
  @Test
  void testExplicitSettingsAreNotOverridden() {
    ProcessModel model = buildModelWithDeadLeg(1.0e6, 0.05);
    model.setBoundaryFlowFloor(123.0);
    model.setAbsoluteFlowTolerance(456.0);
    model.runUntilConverged(25);

    assertEquals(123.0, model.getBoundaryFlowFloor(), 1e-12, "An explicit boundary floor must not be overridden");
    assertEquals(456.0, model.getAbsoluteFlowTolerance(), 1e-12,
        "An explicit absolute flow tolerance must not be overridden");
  }

  /**
   * A unit-level threshold set by the caller must survive the auto-tuner.
   */
  @Test
  void testExplicitUnitThresholdIsNotOverridden() {
    ProcessModel model = buildModelWithDeadLeg(1.0e6, 0.05);
    model.get("main train").getUnit("separator").setMinimumFlow(7.0);
    model.runUntilConverged(25);

    assertEquals(7.0, model.get("main train").getUnit("separator").getMinimumFlow(), 1e-12,
        "A caller-supplied unit threshold must not be overwritten by the auto-tuner");
  }

  /**
   * The tuning fraction should be configurable and validated.
   */
  @Test
  void testTuningFractionIsConfigurableAndValidated() {
    ProcessModel model = buildModelWithDeadLeg(1.0e6, 0.05);
    model.setAutoTuningFlowFraction(1.0e-4);
    model.runUntilConverged(25);

    assertEquals(100.0, model.getBoundaryFlowFloor(), 1e-2, "The configured fraction should drive the noise floor");
    assertThrows(IllegalArgumentException.class, () -> model.setAutoTuningFlowFraction(1.0));
    assertThrows(IllegalArgumentException.class, () -> model.setAutoTuningFlowFraction(-1.0e-6));
  }

  /**
   * resetAutoTuning should undo every threshold the tuner applied.
   */
  @Test
  void testResetAutoTuningRestoresDefaults() {
    ProcessModel model = buildModelWithDeadLeg(1.0e6, 0.05);
    model.runUntilConverged(25);
    int cleared = model.resetAutoTuning();

    assertTrue(cleared > 0, "Reset should report the units it cleared");
    assertEquals(ProcessModel.DEFAULT_BOUNDARY_FLOW_FLOOR, model.getBoundaryFlowFloor(), 1e-15);
    assertEquals(0.0, model.getAbsoluteFlowTolerance(), 1e-15);
    assertEquals(ProcessEquipmentBaseClass.DEFAULT_MINIMUM_FLOW,
        model.get("dead leg").getUnit("dead leg heater").getMinimumFlow(), 1e-30);
  }

  /**
   * The convergence report JSON should expose the auto-tuning outcome for agentic workflows.
   */
  @Test
  void testConvergenceReportContainsAutoTuning() {
    ProcessModel model = buildModelWithDeadLeg(1.0e6, 0.05);
    model.runUntilConverged(25);

    JsonObject report = JsonParser.parseString(model.getConvergenceReportJson()).getAsJsonObject();
    JsonObject autoTuning = report.getAsJsonObject("autoTuning");

    assertTrue(autoTuning.get("enabled").getAsBoolean(), "Auto tuning should be reported as enabled");
    assertEquals(1.0e6, autoTuning.get("detectedPlantFlowScaleKgPerHr").getAsDouble(), 1.0);
    assertEquals(1.0, autoTuning.get("boundaryFlowFloorKgPerHr").getAsDouble(), 1e-3);
    assertTrue(model.getConvergenceSummary().contains("Auto-tuning"),
        "The text summary should mention the auto-tuning outcome");
  }

  /**
   * A single ProcessSystem should offer the same self-configuring entry point.
   */
  @Test
  void testProcessSystemRunUntilConverged() {
    Stream feed = new Stream("feed", createGasFluid());
    feed.setFlowRate(1.0e6, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(50.0, "bara");
    Separator separator = new Separator("separator", feed);

    Stream seed = new Stream("dead leg seed", createGasFluid());
    seed.setFlowRate(0.05, "kg/hr");
    seed.setTemperature(25.0, "C");
    seed.setPressure(50.0, "bara");
    Heater deadLegHeater = new Heater("dead leg heater", seed);
    deadLegHeater.setOutTemperature(40.0, "C");

    ProcessSystem process = new ProcessSystem("plant");
    process.add(feed);
    process.add(separator);
    process.add(seed);
    process.add(deadLegHeater);

    assertTrue(process.runUntilConverged(10), "A feed-forward process should solve");
    assertEquals(1.0e6, process.getMaxStreamFlowRate(), 1.0, "Flow scale should be the largest stream");
    assertTrue(process.getBypassedUnits().contains("dead leg heater"),
        "The stagnant heater should be auto-bypassed, bypassed units were " + process.getBypassedUnits());

    process.resetAutoLowFlowThreshold();
    assertEquals(ProcessEquipmentBaseClass.DEFAULT_MINIMUM_FLOW, deadLegHeater.getMinimumFlow(), 1e-30);
  }

  /**
   * Invalid iteration budgets must be rejected.
   */
  @Test
  void testInvalidIterationBudgetIsRejected() {
    ProcessModel model = buildModelWithDeadLeg(1.0e6, 0.05);
    assertThrows(IllegalArgumentException.class, () -> model.runUntilConverged(0));
    assertThrows(IllegalArgumentException.class, () -> new ProcessSystem("p").runUntilConverged(0));
  }
}
