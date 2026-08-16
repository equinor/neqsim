package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleSupplier;
import java.util.function.ToDoubleFunction;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintSeverity;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType;
import neqsim.process.equipment.capacity.ValveCapacityStrategy;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link ProcessModelSimulationEvaluator}.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class ProcessModelSimulationEvaluatorTest {

  /**
   * Test fixture holding a small two-area process model.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  private static final class ModelFixture {
    /** Feed stream used as an optimization decision variable. */
    private final Stream feed;

    /** Separator used for installed capacity constraints. */
    private final Separator separator;

    /** Full process model. */
    private final ProcessModel model;

    /**
     * Creates a test fixture.
     *
     * @param feed feed stream
     * @param separator separator
     * @param model process model
     */
    private ModelFixture(Stream feed, Separator separator, ProcessModel model) {
      this.feed = feed;
      this.separator = separator;
      this.model = model;
    }
  }

  /**
   * Creates a simple gas fluid.
   *
   * @param flowRate feed flow rate
   * @return configured fluid
   */
  private SystemInterface createFluid(double flowRate) {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.10);
    fluid.setMixingRule("classic");
    fluid.setTotalFlowRate(flowRate, "kg/hr");
    return fluid;
  }

  /**
   * Creates a two-area process model with a feed and downstream separator.
   *
   * @return model fixture
   */
  private ModelFixture createModelFixture() {
    Stream feed = new Stream("feed", createFluid(10000.0));
    ThrottlingValve choke = new ThrottlingValve("choke", feed);
    choke.setOutletPressure(30.0, "bara");
    Separator separator = new Separator("separator", choke.getOutletStream());

    ProcessSystem wellArea = new ProcessSystem("wells");
    wellArea.add(feed);
    wellArea.add(choke);

    ProcessSystem separationArea = new ProcessSystem("separation");
    separationArea.add(separator);

    ProcessModel model = new ProcessModel();
    model.add("wells", wellArea);
    model.add("separation", separationArea);
    return new ModelFixture(feed, separator, model);
  }

  /**
   * Verifies area-qualified automation variables and model-level objectives.
   */
  @Test
  void evaluateUsesAreaQualifiedAutomationAddresses() {
    ModelFixture fixture = createModelFixture();
    ProcessModelSimulationEvaluator evaluator = new ProcessModelSimulationEvaluator(fixture.model);
    evaluator.addParameter("wells::feed.flowRate", 5000.0, 20000.0, "kg/hr");
    evaluator.addObjective("exportGas", new ToDoubleFunction<ProcessModel>() {
      /** {@inheritDoc} */
      @Override
      public double applyAsDouble(ProcessModel model) {
        return model.getVariableValue("separation::separator.gasOutStream.flowRate", "kg/hr");
      }
    }, ProcessModelSimulationEvaluator.ObjectiveDefinition.Direction.MAXIMIZE);
    evaluator.addConstraintUpperBound("feedLimit", new ToDoubleFunction<ProcessModel>() {
      /** {@inheritDoc} */
      @Override
      public double applyAsDouble(ProcessModel model) {
        return model.getVariableValue("wells::feed.flowRate", "kg/hr");
      }
    }, 15000.0);

    ProcessModelSimulationEvaluator.EvaluationResult result = evaluator.evaluate(new double[] { 12000.0 });

    assertTrue(result.isSimulationConverged(), "model should converge");
    assertTrue(result.isFeasible(), "feed should be below the upper bound");
    assertEquals(12000.0, fixture.model.getVariableValue("wells::feed.flowRate", "kg/hr"), 1.0e-6);
    assertEquals(-result.getObjectivesRaw()[0], result.getObjectives()[0], 1.0e-6,
        "maximization objective should be sign-adjusted for minimizers");
    assertEquals(3000.0, result.getConstraintMargins()[0], 1.0e-6);
    assertTrue(evaluator.getParameters().get(0).isClampToBounds(),
        "ordinary parameters must retain legacy clamping by default");

    ProcessModelSimulationEvaluator.EvaluationResult clamped = evaluator.evaluate(new double[] { 25000.0 });
    assertTrue(clamped.isSimulationConverged());
    assertEquals(20000.0, fixture.model.getVariableValue("wells::feed.flowRate", "kg/hr"), 1.0e-6,
        "ordinary parameters must retain their historical bound clamping");
  }

  /**
   * Verifies that one completed model point samples each user result callback once.
   */
  @Test
  void evaluateSamplesEachResultCallbackOnce() {
    ModelFixture fixture = createModelFixture();
    ProcessModelSimulationEvaluator evaluator = new ProcessModelSimulationEvaluator(fixture.model);
    AtomicInteger objectiveCalls = new AtomicInteger();
    AtomicInteger constraintCalls = new AtomicInteger();
    evaluator.addObjective("statefulObjective", model -> 42.0 + objectiveCalls.getAndIncrement(),
        ProcessModelSimulationEvaluator.ObjectiveDefinition.Direction.MAXIMIZE);
    evaluator.addConstraintUpperBound("statefulConstraint", model -> 11.0 + constraintCalls.getAndIncrement(), 10.0);

    ProcessModelSimulationEvaluator.EvaluationResult result = evaluator.evaluate(new double[0]);

    assertEquals(1, objectiveCalls.get(), "one simulated point must sample each objective once");
    assertEquals(1, constraintCalls.get(), "one simulated point must sample each constraint once");
    assertEquals(42.0, result.getObjectivesRaw()[0], 0.0);
    assertEquals(-42.0, result.getObjectives()[0], 0.0);
    assertEquals(11.0, result.getConstraintValues()[0], 0.0);
    assertEquals(-1.0, result.getConstraintMargins()[0], 0.0);
    assertEquals(1000.0, result.getPenaltySum(), 0.0);
  }

  /**
   * Verifies installed capacity discovery and active bottleneck reporting across model areas.
   */
  @Test
  void capacityConstraintsIdentifyActiveModelBottleneck() {
    final ModelFixture fixture = createModelFixture();
    CapacityConstraint installedCapacity = new CapacityConstraint("installedGasCapacity", "kg/hr", ConstraintType.HARD)
        .setDesignValue(12000.0).setMaxValue(13200.0).setSeverity(ConstraintSeverity.HARD)
        .setDataSource("mechanicalDesign").setConfidence(0.95).setValidityRange(8000.0, 12000.0)
        .setValueSupplier(new DoubleSupplier() {
          /** {@inheritDoc} */
          @Override
          public double getAsDouble() {
            return fixture.feed.getFlowRate("kg/hr");
          }
        });
    fixture.separator.clearCapacityConstraints();
    fixture.separator.addCapacityConstraint(installedCapacity);

    ProcessModelSimulationEvaluator evaluator = new ProcessModelSimulationEvaluator(fixture.model);
    evaluator.setIncludeStrategyCapacityConstraints(false);
    evaluator.addParameter("wells::feed.flowRate", 5000.0, 20000.0, "kg/hr")
        .addObjective("gas", new ToDoubleFunction<ProcessModel>() {
          /** {@inheritDoc} */
          @Override
          public double applyAsDouble(ProcessModel model) {
            return model.getVariableValue("separation::separator.gasOutStream.flowRate", "kg/hr");
          }
        }, ProcessModelSimulationEvaluator.ObjectiveDefinition.Direction.MAXIMIZE).addEquipmentCapacityConstraints();

    ProcessModelSimulationEvaluator.EvaluationResult result = evaluator.evaluate(new double[] { 13000.0 });

    assertFalse(result.isFeasible(), "installed capacity should be exceeded");
    assertTrue(evaluator.getConstraintCount() > 0, "capacity constraints should be registered");
    ProcessModelSimulationEvaluator.BottleneckStatus bottleneck = result.getActiveBottleneck();
    assertNotNull(bottleneck);
    assertEquals("separation", bottleneck.getAreaName());
    assertEquals("separator", bottleneck.getEquipmentName());
    assertEquals("installedGasCapacity", bottleneck.getConstraintName());
    assertEquals(13.0 / 12.0, bottleneck.getUtilization(), 1.0e-12, "metadata must not alter utilization");
    assertEquals("mechanicalDesign", bottleneck.getDataSource());
    assertTrue(bottleneck.hasConfidence());
    assertEquals(0.95, bottleneck.getConfidence(), 0.0);
    assertTrue(bottleneck.hasValidityRange());
    assertEquals(8000.0, bottleneck.getValidityMinimum(), 0.0);
    assertEquals(12000.0, bottleneck.getValidityMaximum(), 0.0);
    assertFalse(bottleneck.isCurrentValueWithinValidityRange());
    assertEquals("separation::separator", bottleneck.getQualifiedEquipmentName());
  }

  /** Verifies bottleneck utilization and evidence share one supplier snapshot. */
  @Test
  void bottleneckSnapshotInvokesValueSupplierOnce() {
    final ModelFixture fixture = createModelFixture();
    final AtomicInteger supplierCalls = new AtomicInteger();
    CapacityConstraint dynamicCapacity = new CapacityConstraint("dynamicGasCapacity", "kg/hr", ConstraintType.HARD)
        .setDesignValue(12000.0).setValidityRange(8000.0, 12000.0).setValueSupplier(new DoubleSupplier() {
          /** {@inheritDoc} */
          @Override
          public double getAsDouble() {
            return supplierCalls.incrementAndGet() == 1 ? 13000.0 : 9000.0;
          }
        });
    fixture.separator.clearCapacityConstraints();
    fixture.separator.addCapacityConstraint(dynamicCapacity);

    ProcessModelSimulationEvaluator evaluator = new ProcessModelSimulationEvaluator(fixture.model);
    evaluator.setIncludeStrategyCapacityConstraints(false);
    ProcessModelSimulationEvaluator.BottleneckStatus bottleneck = evaluator.findActiveBottleneck(fixture.model);

    assertEquals(1, supplierCalls.get());
    assertEquals(13000.0, bottleneck.getCurrentValue(), 0.0);
    assertEquals(13.0 / 12.0, bottleneck.getUtilization(), 1.0e-12);
    assertFalse(bottleneck.isCurrentValueWithinValidityRange());
  }

  /**
   * Verifies all capacity constraints are ranked strictly by utilization while evidence applicability remains a
   * diagnostic.
   */
  @Test
  void capacityConstraintRankingRetainsEvidenceWithoutChangingOrder() {
    final ModelFixture fixture = createModelFixture();
    final AtomicInteger highSupplierCalls = new AtomicInteger();
    final AtomicInteger mediumSupplierCalls = new AtomicInteger();
    final AtomicInteger lowSupplierCalls = new AtomicInteger();
    CapacityConstraint high = new CapacityConstraint("high", "kg/hr", ConstraintType.HARD).setDesignValue(12000.0)
        .setConfidence(0.20).setValidityRange(8000.0, 12000.0).setValueSupplier(() -> {
          highSupplierCalls.incrementAndGet();
          return 13000.0;
        });
    CapacityConstraint medium = new CapacityConstraint("medium", "kg/hr", ConstraintType.HARD).setDesignValue(14000.0)
        .setValidityRange(10000.0, 14000.0).setValueSupplier(() -> {
          mediumSupplierCalls.incrementAndGet();
          return 13000.0;
        });
    CapacityConstraint low = new CapacityConstraint("low", "kg/hr", ConstraintType.HARD).setDesignValue(15000.0)
        .setConfidence(0.99).setValueSupplier(() -> {
          lowSupplierCalls.incrementAndGet();
          return 13000.0;
        });
    fixture.separator.clearCapacityConstraints();
    fixture.separator.addCapacityConstraint(low);
    fixture.separator.addCapacityConstraint(high);
    fixture.separator.addCapacityConstraint(medium);

    ProcessModelSimulationEvaluator evaluator = new ProcessModelSimulationEvaluator(fixture.model);
    evaluator.setIncludeStrategyCapacityConstraints(false);
    List<ProcessModelSimulationEvaluator.BottleneckStatus> ranked = evaluator.rankCapacityConstraints(fixture.model);

    assertEquals(3, ranked.size());
    assertEquals("high", ranked.get(0).getConstraintName());
    assertEquals("medium", ranked.get(1).getConstraintName());
    assertEquals("low", ranked.get(2).getConstraintName());
    assertEquals(ProcessModelSimulationEvaluator.BottleneckStatus.EvidenceApplicability.OUTSIDE_VALIDITY_RANGE,
        ranked.get(0).getEvidenceApplicability());
    assertEquals(ProcessModelSimulationEvaluator.BottleneckStatus.EvidenceApplicability.WITHIN_VALIDITY_RANGE,
        ranked.get(1).getEvidenceApplicability());
    assertEquals(ProcessModelSimulationEvaluator.BottleneckStatus.EvidenceApplicability.NOT_ASSESSED,
        ranked.get(2).getEvidenceApplicability());
    assertEquals(1, highSupplierCalls.get());
    assertEquals(1, mediumSupplierCalls.get());
    assertEquals(1, lowSupplierCalls.get());
    assertEquals(ranked.get(0).getConstraintName(), evaluator.findActiveBottleneck(fixture.model).getConstraintName());
    assertThrows(UnsupportedOperationException.class,
        () -> ranked.add(ProcessModelSimulationEvaluator.BottleneckStatus.none()));
  }

  /** Verifies stable utilization ties retain capacity-constraint registration order. */
  @Test
  void capacityConstraintRankingPreservesRegistrationOrderForTies() {
    ModelFixture fixture = createModelFixture();
    CapacityConstraint first = new CapacityConstraint("first", "kg/hr", ConstraintType.HARD).setDesignValue(12000.0)
        .setCurrentValue(9000.0);
    CapacityConstraint second = new CapacityConstraint("second", "kg/hr", ConstraintType.HARD).setDesignValue(12000.0)
        .setCurrentValue(9000.0);
    fixture.separator.clearCapacityConstraints();
    fixture.separator.addCapacityConstraint(first);
    fixture.separator.addCapacityConstraint(second);

    ProcessModelSimulationEvaluator evaluator = new ProcessModelSimulationEvaluator(fixture.model);
    evaluator.setIncludeStrategyCapacityConstraints(false);
    List<ProcessModelSimulationEvaluator.BottleneckStatus> ranked = evaluator.rankCapacityConstraints(fixture.model);

    assertEquals(2, ranked.size());
    assertEquals("first", ranked.get(0).getConstraintName());
    assertEquals("second", ranked.get(1).getConstraintName());
  }

  /** Verifies built-in capacity strategies expose their declared constraint registration order. */
  @Test
  void strategyCapacityConstraintsPreserveRegistrationOrder() {
    ModelFixture fixture = createModelFixture();
    ThrottlingValve valve = new ThrottlingValve("strategyValve", fixture.feed);

    List<String> constraintNames = new ArrayList<String>(new ValveCapacityStrategy().getConstraints(valve).keySet());

    assertEquals(2, constraintNames.size());
    assertEquals("valveOpening", constraintNames.get(0));
    assertEquals("pressureDropRatio", constraintNames.get(1));

    fixture.model.get("wells").add(valve);
    ThrottlingValve directValve = new ThrottlingValve("directValve", fixture.feed);
    directValve.addCapacityConstraint(new CapacityConstraint("valveOpening", "custom-unit", ConstraintType.HARD)
        .setDesignValue(1.0).setCurrentValue(0.5));
    fixture.model.get("wells").add(directValve);
    ProcessModelSimulationEvaluator evaluator = new ProcessModelSimulationEvaluator(fixture.model);
    evaluator.addEquipmentCapacityConstraints();

    InstalledEquipmentCapacityEvidence.ConstraintOrigin strategyOrigin = null;
    InstalledEquipmentCapacityEvidence.ConstraintOrigin directOrigin = null;
    for (ProcessModelSimulationEvaluator.ConstraintDefinition definition : evaluator.getConstraints()) {
      if ("strategyValve".equals(definition.getEquipmentName())
          && "pressureDropRatio".equals(definition.getEquipmentConstraintName())) {
        strategyOrigin = definition.getCapacityConstraintOrigin();
      }
      if ("directValve".equals(definition.getEquipmentName())
          && "valveOpening".equals(definition.getEquipmentConstraintName())) {
        directOrigin = definition.getCapacityConstraintOrigin();
        assertEquals("custom-unit", definition.getCapacityPhysicalUnit());
      }
    }
    assertEquals(InstalledEquipmentCapacityEvidence.ConstraintOrigin.STRATEGY, strategyOrigin);
    assertEquals(InstalledEquipmentCapacityEvidence.ConstraintOrigin.DIRECT, directOrigin,
        "a direct row with the same name must override the strategy row");
  }

  /** Verifies enabled constraints with undefined utilization remain visible at the end. */
  @Test
  void capacityConstraintRankingKeepsUndefinedUtilizationLast() {
    ModelFixture fixture = createModelFixture();
    AtomicInteger undefinedSupplierCalls = new AtomicInteger();
    CapacityConstraint undefined = new CapacityConstraint("undefined", "kg/hr", ConstraintType.HARD)
        .setDesignValue(12000.0).setValueSupplier(() -> {
          undefinedSupplierCalls.incrementAndGet();
          return Double.NaN;
        });
    CapacityConstraint finite = new CapacityConstraint("finite", "kg/hr", ConstraintType.HARD).setDesignValue(12000.0)
        .setCurrentValue(9000.0);
    CapacityConstraint invalidLimit = new CapacityConstraint("invalidLimit", "kg/hr", ConstraintType.HARD)
        .setDesignValue(0.0).setCurrentValue(5.0);
    fixture.separator.clearCapacityConstraints();
    fixture.separator.addCapacityConstraint(undefined);
    fixture.separator.addCapacityConstraint(finite);
    fixture.separator.addCapacityConstraint(invalidLimit);

    ProcessModelSimulationEvaluator evaluator = new ProcessModelSimulationEvaluator(fixture.model);
    evaluator.setIncludeStrategyCapacityConstraints(false);
    List<ProcessModelSimulationEvaluator.BottleneckStatus> ranked = evaluator.rankCapacityConstraints(fixture.model);

    assertEquals(3, ranked.size());
    assertEquals("finite", ranked.get(0).getConstraintName());
    assertEquals("invalidLimit", ranked.get(1).getConstraintName());
    assertEquals("undefined", ranked.get(2).getConstraintName());
    assertTrue(Double.isNaN(ranked.get(2).getUtilization()));
    assertFalse(ranked.get(2).isFeasible());
    assertEquals(1, undefinedSupplierCalls.get());
    List<InstalledEquipmentCapacityEvidence> evidence = evaluator
        .snapshotInstalledEquipmentCapacityEvidence(fixture.model);
    assertEquals(InstalledEquipmentCapacityEvidence.EvidenceStatus.INVALID_APPLICABLE_LIMIT,
        evidence.get(1).getEvidenceStatus());
    assertEquals(InstalledEquipmentCapacityEvidence.EvidenceStatus.NON_FINITE_CURRENT_VALUE,
        evidence.get(2).getEvidenceStatus());
    assertEquals(2, undefinedSupplierCalls.get(),
        "a separate live snapshot call samples once again and does not reuse stale evidence");
  }

  /** Verifies snapshot selection preserves the legacy exclusion of invalid negative utilization. */
  @Test
  void evaluationPreservesLegacyNoBottleneckForInvalidNegativeUtilization() {
    ModelFixture fixture = createModelFixture();
    CapacityConstraint invalid = new CapacityConstraint("invalidNegative", "kg/hr", ConstraintType.HARD)
        .setDesignValue(10.0).setCurrentValue(-20.0);
    fixture.separator.clearCapacityConstraints();
    fixture.separator.addCapacityConstraint(invalid);

    ProcessModelSimulationEvaluator evaluator = new ProcessModelSimulationEvaluator(fixture.model);
    evaluator.setIncludeStrategyCapacityConstraints(false);
    evaluator.addParameter("wells::feed.flowRate", 5000.0, 20000.0, "kg/hr");

    ProcessModelSimulationEvaluator.EvaluationResult result = evaluator.evaluate(new double[] { 10000.0 });

    assertTrue(result.isSimulationConverged());
    assertEquals(1, result.getRankedCapacityConstraints().size());
    assertEquals(-2.0, result.getRankedCapacityConstraints().get(0).getUtilization(), 1.0e-12);
    assertFalse(result.getActiveBottleneck().isPresent());
    assertFalse(evaluator.findActiveBottleneck(fixture.model).isPresent());
  }

  /**
   * Verifies each model evaluation retains an immutable ranked capacity snapshot after later model runs.
   */
  @Test
  void evaluationRetainsRankedCapacitySnapshotAcrossOperatingPoints() {
    final ModelFixture fixture = createModelFixture();
    CapacityConstraint exportCapacity = new CapacityConstraint("exportCapacity", "kg/hr", ConstraintType.HARD)
        .setDesignValue(15000.0).setDataSource("exportNomination").setConfidence(0.90)
        .setValueSupplier(() -> fixture.feed.getFlowRate("kg/hr"));
    CapacityConstraint compressorHeadroom = new CapacityConstraint("compressorHeadroom", "kg/hr", ConstraintType.HARD)
        .setDesignValue(20000.0).setDataSource("compressorMap").setConfidence(0.95)
        .setValueSupplier(() -> 24000.0 - fixture.feed.getFlowRate("kg/hr"));
    fixture.separator.clearCapacityConstraints();
    fixture.separator.addCapacityConstraint(exportCapacity);
    fixture.separator.addCapacityConstraint(compressorHeadroom);

    ProcessModelSimulationEvaluator evaluator = new ProcessModelSimulationEvaluator(fixture.model);
    evaluator.setIncludeStrategyCapacityConstraints(false);
    evaluator.addParameter("wells::feed.flowRate", 5000.0, 20000.0, "kg/hr");

    ProcessModelSimulationEvaluator.EvaluationResult lowRate = evaluator.evaluate(new double[] { 10000.0 });
    ProcessModelSimulationEvaluator.EvaluationResult highRate = evaluator.evaluate(new double[] { 14000.0 });

    List<ProcessModelSimulationEvaluator.BottleneckStatus> lowRanked = lowRate.getRankedCapacityConstraints();
    List<ProcessModelSimulationEvaluator.BottleneckStatus> highRanked = highRate.getRankedCapacityConstraints();
    assertEquals(2, lowRanked.size());
    assertEquals(2, highRanked.size());
    assertEquals("compressorHeadroom", lowRanked.get(0).getConstraintName());
    assertEquals("exportCapacity", highRanked.get(0).getConstraintName());
    assertEquals(2.0 / 3.0, lowRanked.get(1).getUtilization(), 1.0e-12,
        "the first evaluation must retain its original export snapshot");
    assertEquals(14.0 / 15.0, highRanked.get(0).getUtilization(), 1.0e-12);
    assertEquals(lowRanked.get(0).getConstraintName(), lowRate.getActiveBottleneck().getConstraintName());
    assertEquals(highRanked.get(0).getConstraintName(), highRate.getActiveBottleneck().getConstraintName());
    assertThrows(UnsupportedOperationException.class,
        () -> lowRanked.add(ProcessModelSimulationEvaluator.BottleneckStatus.none()));
  }

  /**
   * Verifies that minimum-directed capacity limits retain their engineering-unit limit in model-level bottleneck
   * reporting.
   */
  @Test
  void minimumCapacityConstraintReportsItsFiniteLimit() {
    final ModelFixture fixture = createModelFixture();
    CapacityConstraint minimumHeadroom = new CapacityConstraint("availableHeadroom", "m", ConstraintType.HARD)
        .setMinValue(45.0).setSeverity(ConstraintSeverity.HARD).setValueSupplier(new DoubleSupplier() {
          /** {@inheritDoc} */
          @Override
          public double getAsDouble() {
            return 50.0;
          }
        });
    fixture.separator.clearCapacityConstraints();
    fixture.separator.addCapacityConstraint(minimumHeadroom);

    ProcessModelSimulationEvaluator evaluator = new ProcessModelSimulationEvaluator(fixture.model);
    evaluator.setIncludeStrategyCapacityConstraints(false);
    evaluator.addParameter("wells::feed.flowRate", 5000.0, 20000.0, "kg/hr").addEquipmentCapacityConstraints();

    ProcessModelSimulationEvaluator.EvaluationResult result = evaluator.evaluate(new double[] { 10000.0 });
    ProcessModelSimulationEvaluator.BottleneckStatus bottleneck = result.getActiveBottleneck();

    assertTrue(result.isFeasible());
    assertEquals(0.9, bottleneck.getUtilization(), 1.0e-12);
    assertEquals(50.0, bottleneck.getCurrentValue(), 1.0e-12);
    assertEquals(45.0, bottleneck.getDesignValue(), 1.0e-12, "minimum limit must not be reported as Double.MAX_VALUE");
    assertTrue(bottleneck.isMinimumConstraint());
    InstalledEquipmentCapacityEvidence evidence = result.getInstalledEquipmentCapacityEvidence().get(0);
    assertEquals(InstalledEquipmentCapacityEvidence.LimitDirection.MINIMUM, evidence.getLimitDirection());
    assertEquals(45.0, evidence.getApplicableLimit(), 0.0);
    assertEquals(5.0, evidence.getPhysicalMargin(), 0.0);
    assertEquals(0.0, evidence.getRequiredRelief(), 0.0);
    assertEquals("m", evidence.getPhysicalUnit());
  }

  /**
   * Verifies one supplier sample drives normalized feasibility, immutable physical evidence, serialization, and legacy
   * bottleneck reporting.
   */
  @Test
  void evaluationReusesUnitSafeInstalledCapacityEvidence() throws Exception {
    final ModelFixture fixture = createModelFixture();
    final AtomicInteger supplierCalls = new AtomicInteger();
    CapacityConstraint installedCapacity = new CapacityConstraint("installedGasCapacity", "kg/hr", ConstraintType.HARD)
        .setDesignValue(12000.0).setMaxValue(13200.0).setWarningThreshold(0.85).setSeverity(ConstraintSeverity.CRITICAL)
        .setDescription("Synthetic separator gas handling limit").setDataSource("mechanicalDesign:test")
        .setConfidence(0.95).setValidityRange(8000.0, 14000.0).setValueSupplier(() -> {
          supplierCalls.incrementAndGet();
          return 13000.0;
        });
    fixture.separator.clearCapacityConstraints();
    fixture.separator.addCapacityConstraint(installedCapacity);

    ProcessModelSimulationEvaluator evaluator = new ProcessModelSimulationEvaluator(fixture.model);
    evaluator.setIncludeStrategyCapacityConstraints(false);
    evaluator.addEquipmentCapacityConstraints();

    ProcessModelSimulationEvaluator.EvaluationResult result = evaluator.evaluate(new double[0]);

    assertEquals(1, supplierCalls.get(), "one completed point must sample each installed supplier once");
    assertEquals(13.0 / 12.0, result.getConstraintValues()[0], 1.0e-12);
    assertEquals(-1.0 / 12.0, result.getConstraintMargins()[0], 1.0e-12);
    assertEquals("1", evaluator.getConstraints().get(0).getUnit());
    assertEquals("kg/hr", evaluator.getConstraints().get(0).getCapacityPhysicalUnit());
    assertEquals(1, result.getInstalledEquipmentCapacityEvidence().size());

    InstalledEquipmentCapacityEvidence evidence = result.getInstalledEquipmentCapacityEvidence().get(0);
    assertEquals("separation::separator/installedGasCapacity", evidence.getQualifiedConstraintName());
    assertEquals(fixture.separator.getClass().getName(), evidence.getEquipmentClassName());
    assertEquals(InstalledEquipmentCapacityEvidence.ConstraintOrigin.DIRECT, evidence.getConstraintOrigin());
    assertEquals(ConstraintType.HARD, evidence.getConstraintType());
    assertEquals(ConstraintSeverity.CRITICAL, evidence.getSeverity());
    assertTrue(evidence.isEnabled());
    assertEquals(13.0 / 12.0, evidence.getNormalizedUtilization(), 1.0e-12);
    assertEquals(-1.0 / 12.0, evidence.getNormalizedMargin(), 1.0e-12);
    assertEquals("1", evidence.getNormalizedUnit());
    assertEquals(13000.0, evidence.getCurrentValue(), 0.0);
    assertEquals(12000.0, evidence.getDesignValue(), 0.0);
    assertEquals(0.0, evidence.getMinimumValue(), 0.0);
    assertEquals(13200.0, evidence.getMaximumValue(), 0.0);
    assertEquals(12000.0, evidence.getApplicableLimit(), 0.0);
    assertEquals(-1000.0, evidence.getPhysicalMargin(), 0.0);
    assertEquals(1000.0, evidence.getRequiredRelief(), 0.0);
    assertEquals(0.85, evidence.getWarningThreshold(), 0.0);
    assertEquals("kg/hr", evidence.getPhysicalUnit());
    assertEquals("mechanicalDesign:test", evidence.getDataSource());
    assertEquals(InstalledEquipmentCapacityEvidence.EvidenceStatus.AVAILABLE, evidence.getEvidenceStatus());
    assertEquals(InstalledEquipmentCapacityEvidence.EvidenceApplicability.WITHIN_VALIDITY_RANGE,
        evidence.getEvidenceApplicability());
    assertFalse(evidence.isFeasible());
    assertTrue(evidence.isNearLimit());
    assertEquals(evidence.getCurrentValue(), result.getActiveBottleneck().getCurrentValue(), 0.0);
    assertEquals(evidence.getNormalizedUtilization(), result.getActiveBottleneck().getUtilization(), 0.0);
    CapacityConstraintAdapter adapter = new CapacityConstraintAdapter("separation::separator/installedGasCapacity",
        installedCapacity);
    assertEquals("1", adapter.getUnit());
    assertEquals("kg/hr", adapter.getPhysicalUnit());

    installedCapacity.setDesignValue(20000.0).setUnit("t/day").setDataSource("mutated later");
    assertEquals(12000.0, evidence.getApplicableLimit(), 0.0);
    assertEquals("kg/hr", evidence.getPhysicalUnit());
    assertEquals("mechanicalDesign:test", evidence.getDataSource());

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ObjectOutputStream output = new ObjectOutputStream(bytes);
    output.writeObject(result);
    output.close();
    ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    ProcessModelSimulationEvaluator.EvaluationResult restored = (ProcessModelSimulationEvaluator.EvaluationResult) input
        .readObject();
    input.close();

    InstalledEquipmentCapacityEvidence restoredEvidence = restored.getInstalledEquipmentCapacityEvidence().get(0);
    assertEquals("separation::separator/installedGasCapacity", restoredEvidence.getQualifiedConstraintName());
    assertEquals(1000.0, restoredEvidence.getRequiredRelief(), 0.0);
    assertNotSame(restored.getInstalledEquipmentCapacityEvidence(), restored.getInstalledEquipmentCapacityEvidence());
    assertThrows(UnsupportedOperationException.class, () -> restored.getInstalledEquipmentCapacityEvidence().clear());
  }

  /** Verifies map-sourced compressor evidence is monotonic and deterministic at nearby operating points. */
  @Test
  void compressorMapCapacityEvidenceRetainsPhysicalResiduals() {
    SystemInterface fluid = createFluid(5000.0);
    Stream compressorFeed = new Stream("compressor feed", fluid);
    Compressor compressor = new Compressor("export compressor", compressorFeed);
    compressor.setOutletPressure(70.0, "bara");
    final double[] correctedSpeed = new double[] { 9500.0 };
    compressor.clearCapacityConstraints();
    compressor.addCapacityConstraint(new CapacityConstraint("mapCorrectedSpeed", "RPM", ConstraintType.HARD)
        .setDesignValue(10000.0).setMaxValue(10500.0).setSeverity(ConstraintSeverity.HARD)
        .setDataSource("synthetic compressor map envelope").setValidityRange(8000.0, 10500.0)
        .setValueSupplier(() -> correctedSpeed[0]));

    ProcessSystem compression = new ProcessSystem("compression");
    compression.add(compressorFeed);
    compression.add(compressor);
    ProcessModel model = new ProcessModel();
    model.add("compression", compression);
    ProcessModelSimulationEvaluator evaluator = new ProcessModelSimulationEvaluator(model);
    evaluator.setIncludeStrategyCapacityConstraints(false);
    evaluator.addEquipmentCapacityConstraints();

    ProcessModelSimulationEvaluator.EvaluationResult below = evaluator.evaluate(new double[0]);
    correctedSpeed[0] = 10500.0;
    ProcessModelSimulationEvaluator.EvaluationResult above = evaluator.evaluate(new double[0]);
    ProcessModelSimulationEvaluator.EvaluationResult repeated = evaluator.evaluate(new double[0]);

    InstalledEquipmentCapacityEvidence belowEvidence = below.getInstalledEquipmentCapacityEvidence().get(0);
    InstalledEquipmentCapacityEvidence aboveEvidence = above.getInstalledEquipmentCapacityEvidence().get(0);
    InstalledEquipmentCapacityEvidence repeatedEvidence = repeated.getInstalledEquipmentCapacityEvidence().get(0);
    assertEquals(0.95, belowEvidence.getNormalizedUtilization(), 1.0e-12);
    assertEquals(500.0, belowEvidence.getPhysicalMargin(), 0.0);
    assertEquals(0.0, belowEvidence.getRequiredRelief(), 0.0);
    assertEquals(1.05, aboveEvidence.getNormalizedUtilization(), 1.0e-12);
    assertEquals(-500.0, aboveEvidence.getPhysicalMargin(), 0.0);
    assertEquals(500.0, aboveEvidence.getRequiredRelief(), 0.0);
    assertEquals(aboveEvidence.getNormalizedUtilization(), repeatedEvidence.getNormalizedUtilization(), 0.0);
    assertEquals(aboveEvidence.getRequiredRelief(), repeatedEvidence.getRequiredRelief(), 0.0);
    assertEquals("RPM", aboveEvidence.getPhysicalUnit());
    assertEquals(compressor.getClass().getName(), aboveEvidence.getEquipmentClassName());
    assertEquals("synthetic compressor map envelope", aboveEvidence.getDataSource());
    assertEquals(9500.0, belowEvidence.getCurrentValue(), 0.0,
        "the earlier operating point must remain immutable after later evaluations");
  }

  /**
   * Verifies malformed manually constructed bottleneck evidence cannot leak non-finite output.
   */
  @Test
  void bottleneckStatusNormalizesMalformedEvidenceToUnset() {
    ProcessModelSimulationEvaluator.BottleneckStatus bottleneck = new ProcessModelSimulationEvaluator.BottleneckStatus(
        "separation", "separator", "installedGasCapacity", 1.0, 12000.0, 12000.0, false, "manual", true,
        Double.POSITIVE_INFINITY, true, 12000.0, 8000.0, "kg/hr", true);

    assertFalse(bottleneck.hasConfidence());
    assertTrue(Double.isNaN(bottleneck.getConfidence()));
    assertFalse(bottleneck.hasValidityRange());
    assertTrue(Double.isNaN(bottleneck.getValidityMinimum()));
    assertTrue(Double.isNaN(bottleneck.getValidityMaximum()));
    assertFalse(bottleneck.isCurrentValueWithinValidityRange());
  }

  /** Verifies applicability is derived rather than accepted as contradictory external input. */
  @Test
  void bottleneckStatusDerivesValidityApplicabilityFromSnapshot() {
    ProcessModelSimulationEvaluator.BottleneckStatus bottleneck = new ProcessModelSimulationEvaluator.BottleneckStatus(
        "separation", "separator", "installedGasCapacity", 13.0 / 12.0, 13000.0, 12000.0, false, "manual", true, 0.95,
        true, 8000.0, 12000.0, "kg/hr", false);

    assertTrue(bottleneck.hasValidityRange());
    assertFalse(bottleneck.isCurrentValueWithinValidityRange());
  }

  /**
   * Verifies finite differences divide by the perturbation that remains after applying parameter bounds.
   */
  @Test
  void finiteDifferencesUseActualAvailableStepAtUpperBound() {
    ModelFixture fixture = createModelFixture();
    ProcessModelSimulationEvaluator evaluator = new ProcessModelSimulationEvaluator(fixture.model);
    evaluator.addParameter("wells::feed.flowRate", 9999.995, 10000.0, "kg/hr");
    evaluator.addObjective("feed flow", model -> fixture.feed.getFlowRate("kg/hr"));
    evaluator.addConstraintUpperBound("feed limit", model -> fixture.feed.getFlowRate("kg/hr"), 11000.0);
    evaluator.setUseRelativeStep(false);
    evaluator.setFiniteDifferenceStep(10.0);

    double[] gradient = evaluator.estimateGradient(new double[] { 10000.0 });
    double[][] jacobian = evaluator.estimateConstraintJacobian(new double[] { 10000.0 });

    assertEquals(ProcessModelSimulationEvaluator.FiniteDifferenceMethod.FORWARD, evaluator.getFiniteDifferenceMethod());
    assertEquals(1.0, gradient[0], 1.0e-8);
    assertEquals(-1.0, jacobian[0][0], 1.0e-8);
  }

  /** Verifies the optional central stencil is second-order accurate at an interior point. */
  @Test
  void centralFiniteDifferenceUsesSymmetricInBoundsPoints() {
    ModelFixture fixture = createModelFixture();
    ProcessModelSimulationEvaluator evaluator = new ProcessModelSimulationEvaluator(fixture.model);
    evaluator.addParameter("wells::feed.flowRate", 9000.0, 11000.0, "kg/hr");
    evaluator.addObjective("quadratic feed objective", model -> {
      double offset = fixture.feed.getFlowRate("kg/hr") - 9000.0;
      return offset * offset;
    });
    evaluator.setUseRelativeStep(false);
    evaluator.setFiniteDifferenceStep(10.0);
    evaluator.setFiniteDifferenceMethod(ProcessModelSimulationEvaluator.FiniteDifferenceMethod.CENTRAL);

    double[] gradient = evaluator.estimateGradient(new double[] { 10000.0 });
    double[] nearbyGradient = evaluator.estimateGradient(new double[] { 10010.0 });

    assertEquals(2000.0, gradient[0], 1.0e-8);
    assertEquals(2020.0, nearbyGradient[0], 1.0e-8);
    assertEquals(6, evaluator.getEvaluationCount(), "each central gradient should use base, upper, and lower cases");
  }

  /** Verifies step-halving evidence and the improved fine-step derivative on a smooth analytical case. */
  @Test
  void sensitivityQualityReportsCentralStepConsistency() throws Exception {
    ModelFixture fixture = createModelFixture();
    ProcessModelSimulationEvaluator evaluator = new ProcessModelSimulationEvaluator(fixture.model);
    evaluator.addParameter("wells::feed.flowRate", 500.0, 1500.0, "kg/hr");
    evaluator.addObjective("cubic feed objective", model -> {
      double flow = fixture.feed.getFlowRate("kg/hr");
      return flow * flow * flow;
    });
    evaluator.setUseRelativeStep(false);
    evaluator.setFiniteDifferenceStep(100.0);
    evaluator.setFiniteDifferenceMethod(ProcessModelSimulationEvaluator.FiniteDifferenceMethod.CENTRAL);

    ProcessModelSimulationEvaluator.SensitivityQualityResult result = evaluator
        .estimateSensitivitiesWithQuality(new double[] { 1000.0 });
    ProcessModelSimulationEvaluator.ParameterSensitivityQuality quality = result.getParameterQuality().get(0);

    double analyticalDerivative = 3.0 * 1000.0 * 1000.0;
    assertEquals(3002500.0, result.getObjectiveGradient()[0], 1.0e-8);
    assertTrue(
        Math.abs(result.getObjectiveGradient()[0] - analyticalDerivative) < Math.abs(3010000.0 - analyticalDerivative),
        "halving must improve this smooth analytical case");
    assertEquals(ProcessModelSimulationEvaluator.AppliedFiniteDifferenceStencil.CENTRAL, quality.getStencil());
    assertEquals(100.0, quality.getRequestedStep(), 0.0);
    assertEquals(100.0, quality.getCoarseStep(), 0.0);
    assertEquals(50.0, quality.getFineStep(), 0.0);
    assertEquals(0.002491694352160703, quality.getObjectiveRelativeDisagreement(), 1.0e-12);
    assertEquals(quality.getObjectiveRelativeDisagreement(), quality.getMaximumRelativeDisagreement(), 0.0);
    assertEquals(4, quality.getPerturbations().size());
    assertTrue(quality.isAllEvaluationsConverged());
    assertTrue(quality.isAllEvaluationsFeasible());
    assertTrue(quality.isNumericallyStable(0.003));
    assertFalse(quality.isNumericallyStable(0.002));
    assertThrows(IllegalArgumentException.class, () -> quality.isNumericallyStable(Double.NaN));
    assertEquals(5, evaluator.getEvaluationCount(), "base plus coarse/fine evaluations on both sides");

    ProcessModelSimulationEvaluator.SensitivityQualityResult nearby = evaluator
        .estimateSensitivitiesWithQuality(new double[] { 1010.0 });
    ProcessModelSimulationEvaluator.SensitivityQualityResult nearbyRepeat = evaluator
        .estimateSensitivitiesWithQuality(new double[] { 1010.0 });
    assertEquals(3062800.0, nearby.getObjectiveGradient()[0], 1.0e-8);
    assertEquals(nearby.getObjectiveGradient()[0], nearbyRepeat.getObjectiveGradient()[0], 0.0);
    assertEquals(nearby.getParameterQuality().get(0).getMaximumRelativeDisagreement(),
        nearbyRepeat.getParameterQuality().get(0).getMaximumRelativeDisagreement(), 1.0e-12);
    assertEquals(15, evaluator.getEvaluationCount());

    double[] defensiveGradient = result.getObjectiveGradient();
    defensiveGradient[0] = 0.0;
    assertEquals(3002500.0, result.getObjectiveGradient()[0], 1.0e-8);
    assertThrows(UnsupportedOperationException.class, () -> result.getParameterQuality().clear());
    assertThrows(UnsupportedOperationException.class, () -> quality.getPerturbations().clear());

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ObjectOutputStream output = new ObjectOutputStream(bytes);
    output.writeObject(result);
    output.close();
    ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    ProcessModelSimulationEvaluator.SensitivityQualityResult restored = (ProcessModelSimulationEvaluator.SensitivityQualityResult) input
        .readObject();
    input.close();
    assertEquals(result.getObjectiveGradient()[0], restored.getObjectiveGradient()[0], 0.0);
    assertEquals(result.getParameterQuality().get(0).getPerturbations().size(),
        restored.getParameterQuality().get(0).getPerturbations().size());
  }

  /** Verifies sensitivity rows and columns retain immutable engineering identity and base values. */
  @Test
  void sensitivityQualitySnapshotsRemainSelfDescribing() throws Exception {
    ModelFixture fixture = createModelFixture();
    ProcessModelSimulationEvaluator evaluator = new ProcessModelSimulationEvaluator(fixture.model);
    evaluator.addParameter("field feed", "wells::feed.flowRate", 500.0, 1500.0, "kg/hr");
    evaluator.addObjective("export production", model -> fixture.feed.getFlowRate("kg/hr"),
        ProcessModelSimulationEvaluator.ObjectiveDefinition.Direction.MAXIMIZE);
    ProcessModelSimulationEvaluator.ObjectiveDefinition objective = evaluator.getObjectives().get(0);
    objective.setUnit("kg/hr");
    objective.setWeight(2.5);
    evaluator.addConstraintRange("operating envelope", model -> fixture.feed.getFlowRate("kg/hr"), 1000.0, 2000.0);
    ProcessModelSimulationEvaluator.ConstraintDefinition rangeConstraint = evaluator.getConstraints().get(0);
    rangeConstraint.setUnit("kg/hr");
    rangeConstraint.setHard(false);
    rangeConstraint.setPenaltyWeight(25.0);
    evaluator.addConstraintUpperBound("installed feed limit", model -> fixture.feed.getFlowRate("kg/hr"), 2000.0);
    ProcessModelSimulationEvaluator.ConstraintDefinition capacityConstraint = evaluator.getConstraints().get(1);
    capacityConstraint.setUnit("kg/hr");
    capacityConstraint.setCapacityMetadata("wells", "feed", "installedFeedCapacity",
        new CapacityConstraint("installedFeedCapacity", "kg/hr", ConstraintType.HARD));
    evaluator.setUseRelativeStep(false);
    evaluator.setFiniteDifferenceStep(100.0);

    ProcessModelSimulationEvaluator.SensitivityQualityResult result = evaluator
        .estimateSensitivitiesWithQuality(new double[] { 1600.0 });
    ProcessModelSimulationEvaluator.SensitivityParameterSnapshot parameter = result.getParameterSnapshots().get(0);
    ProcessModelSimulationEvaluator.SensitivityObjectiveSnapshot objectiveSnapshot = result.getObjectiveSnapshot();
    ProcessModelSimulationEvaluator.SensitivityConstraintSnapshot rangeSnapshot = result.getConstraintSnapshots()
        .get(0);
    ProcessModelSimulationEvaluator.SensitivityConstraintSnapshot capacitySnapshot = result.getConstraintSnapshots()
        .get(1);

    assertEquals(0, parameter.getIndex());
    assertEquals("field feed", parameter.getName());
    assertEquals("wells::feed.flowRate", parameter.getAddress());
    assertEquals("kg/hr", parameter.getUnit());
    assertEquals(500.0, parameter.getLowerBound(), 0.0);
    assertEquals(1500.0, parameter.getUpperBound(), 0.0);
    assertEquals(1500.0, parameter.getBaseValue(), 0.0, "base value must retain bound clamping");

    assertEquals(0, objectiveSnapshot.getIndex());
    assertEquals("export production", objectiveSnapshot.getName());
    assertEquals(ProcessModelSimulationEvaluator.ObjectiveDefinition.Direction.MAXIMIZE,
        objectiveSnapshot.getDirection());
    assertEquals("kg/hr", objectiveSnapshot.getUnit());
    assertEquals(2.5, objectiveSnapshot.getWeight(), 0.0);
    assertEquals(1500.0, objectiveSnapshot.getBaseRawValue(), 1.0e-8);
    assertEquals(-1500.0, objectiveSnapshot.getBaseMinimizerValue(), 1.0e-8);
    assertEquals(result.getObjectiveGradient()[0], objectiveSnapshot.getGradient()[0], 0.0);
    assertEquals(-1.0, objectiveSnapshot.getGradient()[0], 1.0e-8);

    assertEquals(0, rangeSnapshot.getIndex());
    assertEquals("operating envelope", rangeSnapshot.getName());
    assertEquals(ProcessModelSimulationEvaluator.ConstraintDefinition.Type.RANGE, rangeSnapshot.getType());
    assertEquals("kg/hr", rangeSnapshot.getUnit());
    assertFalse(rangeSnapshot.isHard());
    assertEquals(25.0, rangeSnapshot.getPenaltyWeight(), 0.0);
    assertEquals(1000.0, rangeSnapshot.getLowerBound(), 0.0);
    assertEquals(2000.0, rangeSnapshot.getUpperBound(), 0.0);
    assertEquals(1500.0, rangeSnapshot.getBaseValue(), 1.0e-8);
    assertEquals(500.0, rangeSnapshot.getBaseMargin(), 1.0e-8);
    assertEquals(result.getConstraintJacobian()[0][0], rangeSnapshot.getMarginGradient()[0], 0.0);

    assertEquals(1, capacitySnapshot.getIndex());
    assertTrue(capacitySnapshot.isCapacityConstraint());
    assertEquals("wells", capacitySnapshot.getAreaName());
    assertEquals("feed", capacitySnapshot.getEquipmentName());
    assertEquals("installedFeedCapacity", capacitySnapshot.getEquipmentConstraintName());
    assertEquals(1500.0, capacitySnapshot.getBaseValue(), 1.0e-8);
    assertEquals(500.0, capacitySnapshot.getBaseMargin(), 1.0e-8);
    assertEquals(-1.0, capacitySnapshot.getMarginGradient()[0], 1.0e-8);

    evaluator.getParameters().get(0).setName("mutated parameter");
    evaluator.getObjectives().get(0).setName("mutated objective");
    evaluator.getConstraints().get(0).setName("mutated constraint");
    evaluator.evaluate(new double[] { 1000.0 });
    assertEquals("field feed", parameter.getName());
    assertEquals("export production", objectiveSnapshot.getName());
    assertEquals("operating envelope", rangeSnapshot.getName());
    assertEquals(1500.0, parameter.getBaseValue(), 0.0);
    assertThrows(UnsupportedOperationException.class, () -> result.getParameterSnapshots().clear());
    assertThrows(UnsupportedOperationException.class, () -> result.getConstraintSnapshots().clear());
    double[] defensiveMarginGradient = capacitySnapshot.getMarginGradient();
    defensiveMarginGradient[0] = 0.0;
    assertEquals(-1.0, capacitySnapshot.getMarginGradient()[0], 1.0e-8);

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ObjectOutputStream output = new ObjectOutputStream(bytes);
    output.writeObject(result);
    output.close();
    ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    ProcessModelSimulationEvaluator.SensitivityQualityResult restored = (ProcessModelSimulationEvaluator.SensitivityQualityResult) input
        .readObject();
    input.close();
    assertEquals("field feed", restored.getParameterSnapshots().get(0).getName());
    assertEquals("export production", restored.getObjectiveSnapshot().getName());
    assertEquals("installedFeedCapacity", restored.getConstraintSnapshots().get(1).getEquipmentConstraintName());
    assertEquals(-1.0, restored.getConstraintSnapshots().get(1).getMarginGradient()[0], 1.0e-8);
  }

  /** Verifies bound-active quality evidence uses the actual backward steps for all derivatives. */
  @Test
  void sensitivityQualityReportsBoundedBackwardStencil() {
    ModelFixture fixture = createModelFixture();
    ProcessModelSimulationEvaluator evaluator = new ProcessModelSimulationEvaluator(fixture.model);
    evaluator.addParameter("wells::feed.flowRate", 9999.995, 10000.0, "kg/hr");
    evaluator.addObjective("feed flow", model -> fixture.feed.getFlowRate("kg/hr"));
    evaluator.addConstraintUpperBound("feed limit", model -> fixture.feed.getFlowRate("kg/hr"), 11000.0);
    evaluator.setUseRelativeStep(false);
    evaluator.setFiniteDifferenceStep(10.0);

    ProcessModelSimulationEvaluator.SensitivityQualityResult result = evaluator
        .estimateSensitivitiesWithQuality(new double[] { 10000.0 });
    ProcessModelSimulationEvaluator.ParameterSensitivityQuality quality = result.getParameterQuality().get(0);

    assertEquals(1.0, result.getObjectiveGradient()[0], 1.0e-8);
    assertEquals(-1.0, result.getConstraintJacobian()[0][0], 1.0e-8);
    assertEquals(ProcessModelSimulationEvaluator.AppliedFiniteDifferenceStencil.BACKWARD, quality.getStencil());
    assertEquals(0.005, quality.getCoarseStep(), 1.0e-9);
    assertEquals(0.0025, quality.getFineStep(), 1.0e-9);
    assertEquals(2, quality.getPerturbations().size());
    assertTrue(quality.getPerturbations().get(0).getSignedStep() < 0.0);
    assertTrue(quality.getPerturbations().get(1).getSignedStep() < 0.0);
    assertTrue(quality.isNumericallyStable(1.0e-8));
    assertTrue(result.isBaseSimulationConverged());
    assertTrue(result.isBaseFeasible());
  }

  /** Verifies a fixed parameter has zero derivatives without unnecessary perturbation runs. */
  @Test
  void sensitivityQualityReportsFixedParameter() {
    ModelFixture fixture = createModelFixture();
    ProcessModelSimulationEvaluator evaluator = new ProcessModelSimulationEvaluator(fixture.model);
    evaluator.addParameter("wells::feed.flowRate", 10000.0, 10000.0, "kg/hr");
    evaluator.addObjective("feed flow", model -> fixture.feed.getFlowRate("kg/hr"));
    evaluator.addConstraintUpperBound("feed limit", model -> fixture.feed.getFlowRate("kg/hr"), 11000.0);

    ProcessModelSimulationEvaluator.SensitivityQualityResult result = evaluator
        .estimateSensitivitiesWithQuality(new double[] { 10000.0 });
    ProcessModelSimulationEvaluator.ParameterSensitivityQuality quality = result.getParameterQuality().get(0);

    assertEquals(0.0, result.getObjectiveGradient()[0], 0.0);
    assertEquals(0.0, result.getConstraintJacobian()[0][0], 0.0);
    assertEquals(ProcessModelSimulationEvaluator.AppliedFiniteDifferenceStencil.FIXED, quality.getStencil());
    assertEquals(0.0, quality.getCoarseStep(), 0.0);
    assertEquals(0.0, quality.getFineStep(), 0.0);
    assertTrue(quality.getPerturbations().isEmpty());
    assertTrue(quality.isNumericallyStable(0.0));
    assertEquals(1, evaluator.getEvaluationCount());
  }

  /** Verifies failed perturbations produce explicit incomplete evidence instead of a trusted derivative. */
  @Test
  void sensitivityQualityRetainsPerturbationFailure() {
    ModelFixture fixture = createModelFixture();
    ProcessModelSimulationEvaluator evaluator = new ProcessModelSimulationEvaluator(fixture.model);
    evaluator.addParameter("wells::feed.flowRate", 500.0, 1500.0, "kg/hr");
    evaluator.addObjective("limited objective", model -> {
      double flow = fixture.feed.getFlowRate("kg/hr");
      if (flow >= 1050.0) {
        throw new IllegalStateException("synthetic objective validity limit");
      }
      return flow;
    });
    evaluator.setUseRelativeStep(false);
    evaluator.setFiniteDifferenceStep(100.0);
    evaluator.setFiniteDifferenceMethod(ProcessModelSimulationEvaluator.FiniteDifferenceMethod.CENTRAL);

    ProcessModelSimulationEvaluator.SensitivityQualityResult result = evaluator
        .estimateSensitivitiesWithQuality(new double[] { 1000.0 });
    ProcessModelSimulationEvaluator.ParameterSensitivityQuality quality = result.getParameterQuality().get(0);

    assertTrue(Double.isNaN(result.getObjectiveGradient()[0]));
    assertFalse(quality.isAllEvaluationsConverged());
    assertFalse(quality.isAllEvaluationsFeasible());
    assertTrue(Double.isNaN(quality.getMaximumRelativeDisagreement()));
    assertFalse(quality.isNumericallyStable(1.0));
    assertEquals("synthetic objective validity limit", quality.getPerturbations().get(0).getErrorMessage());
    assertFalse(quality.getPerturbations().get(0).isSimulationConverged());
  }

  /** Verifies local constraint sensitivities retain evidence while policy controls acceptance. */
  @Test
  void constraintSensitivityQualificationSeparatesEvidenceFromPolicy() throws Exception {
    ModelFixture fixture = createModelFixture();
    ProcessModelSimulationEvaluator evaluator = new ProcessModelSimulationEvaluator(fixture.model);
    evaluator.addParameter("field feed", "wells::feed.flowRate", 500.0, 1500.0, "kg/hr");
    evaluator.addObjective("export production", model -> fixture.feed.getFlowRate("kg/hr"),
        ProcessModelSimulationEvaluator.ObjectiveDefinition.Direction.MAXIMIZE);
    evaluator.getObjectives().get(0).setUnit("kg/hr");
    evaluator.addConstraintUpperBound("installed feed limit", model -> fixture.feed.getFlowRate("kg/hr"), 1050.0);
    evaluator.getConstraints().get(0).setUnit("kg/hr");
    evaluator.setUseRelativeStep(false);
    evaluator.setFiniteDifferenceStep(100.0);

    ProcessModelSimulationEvaluator.SensitivityQualityResult result = evaluator
        .estimateSensitivitiesWithQuality(new double[] { 1000.0 });
    int evaluationsAfterSampling = evaluator.getEvaluationCount();
    ProcessModelSimulationEvaluator.SensitivityQualificationPolicy strict = ProcessModelSimulationEvaluator.SensitivityQualificationPolicy
        .strict(1.0e-8);
    List<ProcessModelSimulationEvaluator.ConstraintSensitivityAssessment> strictAssessments = result
        .assessConstraintSensitivities(strict);
    ProcessModelSimulationEvaluator.ConstraintSensitivityAssessment strictAssessment = strictAssessments.get(0);

    assertFalse(strictAssessment.isAccepted());
    assertTrue(strictAssessment.getEvidenceFlags()
        .contains(ProcessModelSimulationEvaluator.SensitivityEvidenceFlag.PERTURBATION_INFEASIBLE));
    assertTrue(strictAssessment.getEvidenceFlags()
        .contains(ProcessModelSimulationEvaluator.SensitivityEvidenceFlag.ONE_SIDED_STENCIL));
    assertEquals(1, strictAssessment.getRejectionReasons().size());
    assertEquals(ProcessModelSimulationEvaluator.SensitivityEvidenceFlag.PERTURBATION_INFEASIBLE,
        strictAssessment.getRejectionReasons().get(0));
    assertTrue(result.getAcceptedConstraintSensitivities(strict).isEmpty());

    ProcessModelSimulationEvaluator.SensitivityQualificationPolicy numericalOnly = ProcessModelSimulationEvaluator.SensitivityQualificationPolicy
        .numericalOnly(1.0e-8);
    ProcessModelSimulationEvaluator.ConstraintSensitivityAssessment accepted = result
        .getAcceptedConstraintSensitivities(numericalOnly).get(0);
    assertTrue(accepted.isAccepted());
    assertTrue(accepted.getEvidenceFlags()
        .contains(ProcessModelSimulationEvaluator.SensitivityEvidenceFlag.PERTURBATION_INFEASIBLE));
    assertTrue(accepted.getRejectionReasons().isEmpty());
    assertEquals("installed feed limit", accepted.getConstraint().getName());
    assertEquals("field feed", accepted.getParameter().getName());
    assertEquals("export production", accepted.getObjective().getName());
    assertEquals(-1.0, accepted.getMinimizerObjectiveDerivative(), 1.0e-8);
    assertEquals(1.0, accepted.getRawObjectiveDerivative(), 1.0e-8);
    assertEquals(-1.0, accepted.getMarginDerivative(), 1.0e-8);
    assertEquals("kg/hr per kg/hr", accepted.getRawObjectiveDerivativeUnit());
    assertEquals("kg/hr per kg/hr", accepted.getMarginDerivativeUnit());
    assertTrue(accepted.isRawObjectiveImprovedByIncreasingParameter());
    assertTrue(accepted.isMarginReducedByIncreasingParameter());
    assertEquals(evaluationsAfterSampling, evaluator.getEvaluationCount(),
        "qualification must not rerun the process model");
    assertThrows(UnsupportedOperationException.class, () -> strictAssessments.clear());
    assertThrows(UnsupportedOperationException.class, () -> accepted.getEvidenceFlags().clear());
    assertThrows(IllegalArgumentException.class, () -> result.assessConstraintSensitivities(null));
    assertThrows(IllegalArgumentException.class,
        () -> new ProcessModelSimulationEvaluator.SensitivityQualificationPolicy(Double.NaN, true, true, true));

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ObjectOutputStream output = new ObjectOutputStream(bytes);
    output.writeObject(accepted);
    output.close();
    ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    ProcessModelSimulationEvaluator.ConstraintSensitivityAssessment restored = (ProcessModelSimulationEvaluator.ConstraintSensitivityAssessment) input
        .readObject();
    input.close();
    assertTrue(restored.isAccepted());
    assertEquals("installed feed limit", restored.getConstraint().getName());
    assertEquals(-1.0, restored.getMarginDerivative(), 1.0e-8);
  }

  /** Verifies fixed and policy-disallowed stencils are refused with explicit reasons. */
  @Test
  void constraintSensitivityQualificationRejectsUnavailableOperatingActions() {
    ModelFixture fixture = createModelFixture();
    ProcessModelSimulationEvaluator evaluator = new ProcessModelSimulationEvaluator(fixture.model);
    evaluator.addParameter("fixed feed", "wells::feed.flowRate", 1000.0, 1000.0, "kg/hr");
    evaluator.addObjective("feed flow", model -> fixture.feed.getFlowRate("kg/hr"));
    evaluator.addConstraintUpperBound("feed limit", model -> fixture.feed.getFlowRate("kg/hr"), 1100.0);

    ProcessModelSimulationEvaluator.SensitivityQualityResult fixedResult = evaluator
        .estimateSensitivitiesWithQuality(new double[] { 1000.0 });
    ProcessModelSimulationEvaluator.ConstraintSensitivityAssessment fixed = fixedResult.assessConstraintSensitivities(
        ProcessModelSimulationEvaluator.SensitivityQualificationPolicy.numericalOnly(0.0)).get(0);
    assertFalse(fixed.isAccepted());
    assertEquals(ProcessModelSimulationEvaluator.SensitivityEvidenceFlag.FIXED_PARAMETER,
        fixed.getRejectionReasons().get(0));

    ProcessModelSimulationEvaluator boundedEvaluator = new ProcessModelSimulationEvaluator(fixture.model);
    boundedEvaluator.addParameter("bounded feed", "wells::feed.flowRate", 500.0, 1000.0, "kg/hr");
    boundedEvaluator.addObjective("feed flow", model -> fixture.feed.getFlowRate("kg/hr"));
    boundedEvaluator.addConstraintUpperBound("feed limit", model -> fixture.feed.getFlowRate("kg/hr"), 1100.0);
    boundedEvaluator.setUseRelativeStep(false);
    boundedEvaluator.setFiniteDifferenceStep(100.0);
    ProcessModelSimulationEvaluator.SensitivityQualityResult boundedResult = boundedEvaluator
        .estimateSensitivitiesWithQuality(new double[] { 1000.0 });
    ProcessModelSimulationEvaluator.SensitivityQualificationPolicy centralRequired = new ProcessModelSimulationEvaluator.SensitivityQualificationPolicy(
        1.0e-8, true, true, false);
    ProcessModelSimulationEvaluator.ConstraintSensitivityAssessment bounded = boundedResult
        .assessConstraintSensitivities(centralRequired).get(0);

    assertEquals(ProcessModelSimulationEvaluator.AppliedFiniteDifferenceStencil.BACKWARD, bounded.getStencil());
    assertFalse(bounded.isAccepted());
    assertEquals(ProcessModelSimulationEvaluator.SensitivityEvidenceFlag.ONE_SIDED_STENCIL,
        bounded.getRejectionReasons().get(0));
  }

  /** Verifies invalid finite-difference configuration fails before a process evaluation. */
  @Test
  void finiteDifferenceConfigurationRejectsInvalidValues() {
    ProcessModelSimulationEvaluator evaluator = new ProcessModelSimulationEvaluator();

    assertThrows(IllegalArgumentException.class, () -> evaluator.setFiniteDifferenceStep(0.0));
    assertThrows(IllegalArgumentException.class, () -> evaluator.setFiniteDifferenceStep(Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> evaluator.setFiniteDifferenceMethod(null));
  }

  /**
   * Verifies exported problem metadata for external optimizer bridges.
   */
  @Test
  void problemDefinitionIncludesAreasAndBounds() {
    ModelFixture fixture = createModelFixture();
    ProcessModelSimulationEvaluator evaluator = new ProcessModelSimulationEvaluator(fixture.model);
    evaluator.addParameter("feed multiplier", "wells::feed.flowRate", 5000.0, 20000.0, "kg/hr");

    assertEquals(1, evaluator.getParameterCount());
    assertEquals(5000.0, evaluator.getLowerBounds()[0], 1.0e-12);
    assertEquals(20000.0, evaluator.getUpperBounds()[0], 1.0e-12);
    assertEquals(12500.0, evaluator.getInitialValues()[0], 1.0e-12);
    assertTrue(evaluator.toJson().contains("ProcessModelSimulationEvaluator"));
    assertTrue(evaluator.toJson().contains("wells"));
    assertTrue(evaluator.toJson().contains("separation"));
  }
}
