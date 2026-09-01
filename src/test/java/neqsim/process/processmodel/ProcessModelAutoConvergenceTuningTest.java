package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.processmodel.processmodules.WellFluidModule;
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

  /** Non-recycle unit with a deliberate diagnostic mass imbalance. */
  private static final class MassImbalanceHeater extends Heater {
    private static final long serialVersionUID = 1000L;

    MassImbalanceHeater(String name, Stream inlet) {
      super(name, inlet);
    }

    /** {@inheritDoc} */
    @Override
    public double getMassBalance(String unit) {
      return 100.0;
    }
  }

  /** Module probe whose internal operations were deliberately bypassed for the current pass. */
  private static final class InactiveRecycleModule extends WellFluidModule {
    private static final long serialVersionUID = 1000L;

    InactiveRecycleModule(String name) {
      super(name);
    }

    /** {@inheritDoc} */
    @Override
    public void initializeModule() {
      // This probe already owns its intentionally stale internal operations.
    }

    /** {@inheritDoc} */
    @Override
    public void initializeStreams() {
      // No public streams are needed for an inactive-module traversal test.
    }

    /** {@inheritDoc} */
    @Override
    public boolean isActive() {
      return false;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isLockedInactive() {
      return true;
    }
  }

  /** Active module probe that executes its deliberately imbalanced internal recycle. */
  private static final class ActiveRecycleModule extends WellFluidModule {
    private static final long serialVersionUID = 1000L;

    ActiveRecycleModule(String name) {
      super(name);
    }

    /** {@inheritDoc} */
    @Override
    public void initializeModule() {
      // This probe already owns its intentionally minimal internal operations.
    }

    /** {@inheritDoc} */
    @Override
    public void initializeStreams() {
      // The enclosing area owns the active feed boundary used by the closure scale.
    }

    /** {@inheritDoc} */
    @Override
    public void run(UUID id) {
      getOperations().run(id);
      setCalculationIdentifier(id);
    }
  }

  /** Recycle probe that remains solved while exposing a standing tear imbalance. */
  private static final class RecycleMassImbalanceProbe extends Recycle {
    private static final long serialVersionUID = 1000L;

    RecycleMassImbalanceProbe(String name) {
      super(name);
    }

    /** {@inheritDoc} */
    @Override
    public void run(UUID id) {
      setCalculationIdentifier(id);
    }

    /** {@inheritDoc} */
    @Override
    public boolean solved() {
      return true;
    }

    /** {@inheritDoc} */
    @Override
    public double getMassBalance(String unit) {
      return 100.0;
    }
  }

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
    assertTrue(model.getLastIterationCount() >= 2,
        "Changing thresholds after the first sweep must force a complete validation sweep");
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
   * Feed-boundary topology may be cached, but the flow values must remain live so scenario changes are observed without
   * rebuilding the execution plan.
   */
  @Test
  void testCachedFeedBoundaryTracksLiveFlowValues() {
    ProcessModel model = buildModelWithDeadLeg(1.0e6, 0.05);
    assertEquals(1_000_000.05, model.getTotalFeedFlowRate(), 1e-6);

    Stream feed = (Stream) model.get("main train").getUnit("feed");
    feed.setFlowRate(2.0e6, "kg/hr");

    assertEquals(2_000_000.05, model.getTotalFeedFlowRate(), 1e-6,
        "Cached feed identities must read the current stream flow on every call");
  }

  /**
   * A ProcessSystem structure-version change must rebuild the cached feed boundary before the next diagnostic read.
   */
  @Test
  void testFeedBoundaryRebuildsAfterAreaTopologyChange() {
    ProcessModel model = buildModelWithDeadLeg(1.0e6, 0.05);
    assertEquals(1_000_000.05, model.getTotalFeedFlowRate(), 1e-6);

    Stream addedFeed = new Stream("added feed", createGasFluid());
    addedFeed.setFlowRate(25_000.0, "kg/hr");
    Heater addedConsumer = new Heater("added consumer", addedFeed);
    model.get("main train").add(addedFeed);
    model.get("main train").add(addedConsumer);

    assertEquals(1_025_000.05, model.getTotalFeedFlowRate(), 1e-6,
        "The execution plan must refresh feed identities after an area topology change");
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

  /** A lower-throughput scenario on the same model must not retain the previous high-flow noise floor. */
  @Test
  void testThresholdsRetuneDownwardBetweenScenarios() {
    ProcessModel model = buildModelWithDeadLeg(1.0e7, 0.05);
    model.runUntilConverged(25);
    assertEquals(10.0, model.getBoundaryFlowFloor(), 1e-2);

    Stream feed = (Stream) model.get("main train").getUnit("feed");
    feed.setFlowRate(1.0e4, "kg/hr");
    model.runUntilConverged(25);

    assertEquals(1.0e4, model.getDetectedPlantFlowScale(), 1.0);
    assertEquals(1.0e-2, model.getBoundaryFlowFloor(), 1e-5,
        "A new scenario must derive its thresholds from the current feed boundary");
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

  /** A changed automatic threshold must force the affected unit through its low-flow branch. */
  @Test
  void testAutoThresholdForcesARealReevaluation() {
    ProcessModel model = buildModelWithDeadLeg(1.0e6, 0.05);
    ProcessSystem deadLeg = model.get("dead leg");
    Heater heater = (Heater) deadLeg.getUnit("dead leg heater");

    deadLeg.run();
    assertTrue(heater.isActive());
    deadLeg.applyAutoLowFlowThreshold(1.0);
    assertTrue(heater.isMinimumFlowRecalculationPending());

    deadLeg.run();
    assertFalse(heater.isMinimumFlowRecalculationPending());
    assertFalse(heater.isActive(), "The heater must execute and apply its low-flow bypass");
  }

  /** A caller override made after tuning must survive both retuning and reset, even at the same numeric value. */
  @Test
  void testCallerOverrideAfterAutoTuningIsProtected() {
    ProcessModel model = buildModelWithDeadLeg(1.0e6, 0.05);
    ProcessSystem deadLeg = model.get("dead leg");
    Heater heater = (Heater) deadLeg.getUnit("dead leg heater");

    deadLeg.applyAutoLowFlowThreshold(1.0);
    heater.setMinimumFlow(1.0);
    assertTrue(heater.isMinimumFlowExplicitlyConfigured());

    deadLeg.applyAutoLowFlowThreshold(2.0);
    assertEquals(1.0, heater.getMinimumFlow(), 1e-12);
    deadLeg.resetAutoLowFlowThreshold();
    assertEquals(1.0, heater.getMinimumFlow(), 1e-12,
        "Reset must not clear a threshold that the caller took ownership of");
  }

  /** Auto-tuning ownership must survive the serialization-based ProcessSystem.copy() lifecycle. */
  @Test
  void testAutoThresholdCanBeRetunedAndResetAfterCopy() {
    ProcessModel model = buildModelWithDeadLeg(1.0e6, 0.05);
    ProcessSystem deadLeg = model.get("dead leg");
    deadLeg.applyAutoLowFlowThreshold(1.0);

    ProcessSystem copied = deadLeg.copy();
    Heater copiedHeater = (Heater) copied.getUnit("dead leg heater");
    assertTrue(copiedHeater.isMinimumFlowAutoManaged());

    copied.applyAutoLowFlowThreshold(2.0);
    assertEquals(2.0, copiedHeater.getMinimumFlow(), 1e-12);
    copied.resetAutoLowFlowThreshold();
    assertEquals(ProcessEquipmentBaseClass.DEFAULT_MINIMUM_FLOW, copiedHeater.getMinimumFlow(), 1e-30);
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

  /**
   * The model-level closure gate owns recycle tears only. A unit-level diagnostic imbalance must remain visible through
   * ProcessSystem mass-balance reporting without being reclassified as an open recycle tear.
   */
  @Test
  void testMassClosureGateIgnoresNonRecycleUnitDiagnostics() {
    Stream feed = new Stream("probe feed", createGasFluid());
    feed.setFlowRate(1000.0, "kg/hr");
    MassImbalanceHeater heater = new MassImbalanceHeater("imbalanced diagnostic heater", feed);

    ProcessSystem area = new ProcessSystem("probe area");
    area.add(feed);
    area.add(heater);

    ProcessModel model = new ProcessModel();
    model.add("probe area", area);

    assertTrue(model.runUntilConverged(5),
        "A non-recycle unit diagnostic must not be treated as a standing recycle tear");
    assertTrue(area.getFailedMassBalance("kg/hr", 0.0).containsKey("imbalanced diagnostic heater"),
        "The unit-level ProcessSystem diagnostic must remain visible");

    JsonObject massClosure = JsonParser.parseString(model.getConvergenceReportJson()).getAsJsonObject()
        .getAsJsonObject("massClosure");
    assertTrue(massClosure.get("enabled").getAsBoolean());
    assertEquals(0.0, massClosure.get("relativeError").getAsDouble(), 0.0);
    assertFalse(massClosure.get("worstUnits").getAsString().contains("imbalanced diagnostic heater"));
  }

  /** A standing recycle tear must still block model convergence and identify the offending recycle. */
  @Test
  void testMassClosureGateRejectsOpenRecycleTear() {
    Stream feed = new Stream("recycle probe feed", createGasFluid());
    feed.setFlowRate(1000.0, "kg/hr");

    RecycleMassImbalanceProbe recycle = new RecycleMassImbalanceProbe("open recycle tear");
    recycle.addStream(feed);

    ProcessSystem area = new ProcessSystem("recycle area");
    area.add(feed);
    area.add(recycle);

    ProcessModel model = new ProcessModel();
    model.add("recycle area", area);

    assertFalse(model.runUntilConverged(5), "A 100 kg/hr standing recycle tear must block convergence");

    JsonObject massClosure = JsonParser.parseString(model.getConvergenceReportJson()).getAsJsonObject()
        .getAsJsonObject("massClosure");
    assertEquals(0.1, massClosure.get("relativeError").getAsDouble(), 1.0e-12);
    assertTrue(massClosure.get("worstUnits").getAsString().contains("open recycle tear"));
  }

  /** A bypassed module must not contribute stale internal recycle state to the current closure gate. */
  @Test
  void testMassClosureGateSkipsRecycleInsideInactiveModule() {
    Stream feed = new Stream("inactive module feed", createGasFluid());
    feed.setFlowRate(1000.0, "kg/hr");

    RecycleMassImbalanceProbe recycle = new RecycleMassImbalanceProbe("stale inactive recycle");
    recycle.addStream(feed);
    InactiveRecycleModule module = new InactiveRecycleModule("bypassed module");
    module.getOperations().add(recycle);

    Separator feedBoundary = new Separator("active feed boundary", feed);
    ProcessSystem area = new ProcessSystem("inactive module area");
    area.add(feed);
    area.add(feedBoundary);
    area.add(module);

    ProcessModel model = new ProcessModel();
    model.add("inactive module area", area);

    assertTrue(model.runUntilConverged(5),
        "A recycle inside a module that did not execute this pass must not block convergence");

    JsonObject massClosure = JsonParser.parseString(model.getConvergenceReportJson()).getAsJsonObject()
        .getAsJsonObject("massClosure");
    assertEquals(0.0, massClosure.get("relativeError").getAsDouble(), 0.0,
        "The active feed boundary must make closure evaluable while the inactive recycle remains excluded");
    assertFalse(massClosure.get("worstUnits").getAsString().contains("stale inactive recycle"));
  }

  /** Adding an active module after plan creation must refresh the fast-path guard and inspect its recycle. */
  @Test
  void testMassClosureGateFindsRecycleInModuleAddedAfterPlanCreation() {
    Stream feed = new Stream("active module feed", createGasFluid());
    feed.setFlowRate(1000.0, "kg/hr");
    Separator feedBoundary = new Separator("active module feed boundary", feed);

    ProcessSystem area = new ProcessSystem("active module area");
    area.add(feed);
    area.add(feedBoundary);

    ProcessModel model = new ProcessModel();
    model.add("active module area", area);
    assertEquals(1000.0, model.getTotalFeedFlowRate(), 1.0e-12,
        "Initial diagnostic read should build a no-module execution plan");

    RecycleMassImbalanceProbe recycle = new RecycleMassImbalanceProbe("late nested recycle");
    recycle.addStream(feed);
    ActiveRecycleModule module = new ActiveRecycleModule("late active module");
    module.getOperations().add(recycle);
    area.add(module);

    assertFalse(model.runUntilConverged(5),
        "A module added after plan creation must invalidate the no-recycle fast path");
    assertEquals(0.1, model.getLastMassClosureError(), 1.0e-12);
    assertTrue(model.getConvergenceReportJson().contains("late nested recycle"));
  }

  /** Disabled closure evaluation must be reported as disabled and serialize the unevaluated error as JSON null. */
  @Test
  void testDisabledMassClosureReportIsStandardJson() {
    ProcessModel model = buildModelWithDeadLeg(1.0e6, 0.05);
    model.setAutoConvergenceTuning(false);
    model.runUntilConverged(25);

    JsonObject massClosure = JsonParser.parseString(model.getConvergenceReportJson()).getAsJsonObject()
        .getAsJsonObject("massClosure");
    assertFalse(massClosure.get("enabled").getAsBoolean());
    assertTrue(massClosure.get("relativeError").isJsonNull(),
        "An unevaluated error must be JSON null, never the non-standard NaN literal");
    assertTrue(massClosure.get("unitRelativeError").isJsonNull(),
        "A disabled gate must not evaluate the unit-level closure either");
  }

  /**
   * A non-recycle unit that does not conserve mass must be reported, so an internal mass source cannot hide behind a
   * recycle-tear gate that only claims every tear closes.
   */
  @Test
  void testUnitMassClosureIsReportedWithoutGating() {
    Stream feed = new Stream("unit closure feed", createGasFluid());
    feed.setFlowRate(1000.0, "kg/hr");
    MassImbalanceHeater heater = new MassImbalanceHeater("imbalanced diagnostic heater", feed);

    ProcessSystem area = new ProcessSystem("unit closure area");
    area.add(feed);
    area.add(heater);

    ProcessModel model = new ProcessModel();
    model.add("unit closure area", area);

    assertFalse(model.isUnitMassClosureGate(), "Unit-level closure must be report-only by default");
    assertTrue(model.runUntilConverged(5), "Reporting a unit imbalance must not block convergence by default");
    assertEquals(0.1, model.getLastUnitMassClosureError(), 1.0e-12);
    assertTrue(model.getUnitMassClosureOffenders().contains("imbalanced diagnostic heater"));
    assertTrue(model.getMassClosureSummary().contains("Unit-level closure"),
        "The summary must state the unit-level closure, not only the recycle tears");

    JsonObject massClosure = JsonParser.parseString(model.getConvergenceReportJson()).getAsJsonObject()
        .getAsJsonObject("massClosure");
    assertEquals(0.0, massClosure.get("relativeError").getAsDouble(), 0.0);
    assertEquals(0.1, massClosure.get("unitRelativeError").getAsDouble(), 1.0e-12);
    assertFalse(massClosure.get("unitGateEnabled").getAsBoolean());
    assertTrue(massClosure.get("unitWorstUnits").getAsString().contains("imbalanced diagnostic heater"));
    assertFalse(massClosure.get("worstUnits").getAsString().contains("imbalanced diagnostic heater"),
        "The recycle-tear list must stay free of unit-level diagnostics");
  }

  /** Opting in must make the same unit-level imbalance block the converged verdict. */
  @Test
  void testUnitMassClosureGateCanBeEnabled() {
    Stream feed = new Stream("gated closure feed", createGasFluid());
    feed.setFlowRate(1000.0, "kg/hr");
    MassImbalanceHeater heater = new MassImbalanceHeater("imbalanced diagnostic heater", feed);

    ProcessSystem area = new ProcessSystem("gated closure area");
    area.add(feed);
    area.add(heater);

    ProcessModel model = new ProcessModel();
    model.add("gated closure area", area);
    model.setUnitMassClosureGate(true);

    assertFalse(model.runUntilConverged(5), "An opted-in unit-level imbalance must block convergence");
    assertTrue(model.getMassClosureSummary().contains("imbalanced diagnostic heater"));

    JsonObject massClosure = JsonParser.parseString(model.getConvergenceReportJson()).getAsJsonObject()
        .getAsJsonObject("massClosure");
    assertTrue(massClosure.get("unitGateEnabled").getAsBoolean());
    assertEquals(0.1, massClosure.get("unitRelativeError").getAsDouble(), 1.0e-12);
  }

  /**
   * Topology mutations must invalidate both cached auto-tuning subsets so units added after an initial scan are tuned.
   */
  @Test
  void testAutoTuningCandidateCachesFollowTopologyChanges() {
    ProcessSystem process = new ProcessSystem("changing process");
    Stream initialFeed = new Stream("initial feed", createGasFluid());
    process.add(initialFeed);

    assertTrue(process.applyAutoLowFlowThreshold(1.0) > 0);
    assertEquals(0, process.applyAutoRecycleFlowTolerance(1.0));
    assertEquals(0, process.applyAutoRecycleAdaptiveAcceleration());

    Stream addedFeed = new Stream("added feed", createGasFluid());
    Heater addedHeater = new Heater("added heater", addedFeed);
    Recycle addedRecycle = new Recycle("added recycle");
    process.add(addedFeed);
    process.add(addedHeater);
    process.add(addedRecycle);

    assertTrue(process.applyAutoLowFlowThreshold(2.0) >= 3);
    assertEquals(2.0, addedHeater.getMinimumFlow(), 1.0e-12,
        "a unit added after cache creation must receive the automatic threshold");
    assertEquals(1, process.applyAutoRecycleFlowTolerance(2.0),
        "a recycle added after cache creation must receive the automatic tolerance");
    assertEquals(2.0, addedRecycle.getAbsoluteFlowTolerance(), 1.0e-12);
    assertEquals(1, process.applyAutoRecycleAdaptiveAcceleration(),
        "a recycle added after cache creation must receive automatic acceleration");
  }

}
