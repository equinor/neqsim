package neqsim.process.util.optimizer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintSeverity;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProcessSimulationEvaluator.ConstraintDefinition;
import neqsim.process.util.optimizer.ProductionOptimizer.ConstraintDirection;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationConstraint;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the unified constraint framework: {@link ProcessConstraint},
 * {@link ConstraintSeverityLevel}, {@link CapacityConstraintAdapter},
 * {@link ConstraintPenaltyCalculator}, and the conversions between constraint types.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class UnifiedConstraintTest {

  private ProcessSystem process;
  private Stream feed;
  private Compressor compressor;

  /**
   * Sets up a simple process for constraint testing.
   */
  @BeforeEach
  void setUp() {
    SystemSrkEos system = new SystemSrkEos(298.15, 20.0);
    system.addComponent("methane", 200.0);

    feed = new Stream("feed", system);
    feed.setFlowRate(100.0, "kg/hr");

    compressor = new Compressor("compressor", feed);
    compressor.setOutletPressure(60.0);

    process = new ProcessSystem();
    process.add(feed);
    process.add(compressor);
    process.run();
  }

  // ============================================================================
  // ProcessConstraint interface tests
  // ============================================================================

  /**
   * Tests that OptimizationConstraint implements ProcessConstraint correctly.
   */
  @Test
  void testOptimizationConstraintImplementsProcessConstraint() {
    OptimizationConstraint oc = OptimizationConstraint.lessThan("maxPower",
        proc -> Math.abs(((Compressor) proc.getUnit("compressor")).getPower()), 1e12,
        ProductionOptimizer.ConstraintSeverity.HARD, 100.0, "Max power limit");

    // Verify it's a ProcessConstraint
    ProcessConstraint pc = oc;
    Assertions.assertEquals("maxPower", pc.getName());
    Assertions.assertTrue(pc.isSatisfied(process), "Should be satisfied with very large limit");
    Assertions.assertTrue(pc.margin(process) > 0, "Margin should be positive");
    Assertions.assertEquals(ConstraintSeverityLevel.HARD, pc.getSeverityLevel());
    Assertions.assertEquals(100.0, pc.getPenaltyWeight());
    Assertions.assertTrue(pc.isHard());
    Assertions.assertEquals("Max power limit", pc.getDescription());
  }

  /**
   * Tests that ConstraintDefinition implements ProcessConstraint correctly.
   */
  @Test
  void testConstraintDefinitionImplementsProcessConstraint() {
    ConstraintDefinition cd = new ConstraintDefinition("pressure",
        proc -> ((StreamInterface) proc.getUnit("feed")).getPressure(), 10.0);
    cd.setHard(false);
    cd.setPenaltyWeight(50.0);

    // Verify it's a ProcessConstraint
    ProcessConstraint pc = cd;
    Assertions.assertEquals("pressure", pc.getName());
    Assertions.assertTrue(pc.isSatisfied(process), "Feed pressure should be >= 10 bar");
    Assertions.assertEquals(ConstraintSeverityLevel.SOFT, pc.getSeverityLevel());
    Assertions.assertEquals(50.0, pc.getPenaltyWeight());
    Assertions.assertFalse(pc.isHard());
  }

  /**
   * Tests ProcessConstraint.penalty() default implementation.
   */
  @Test
  void testProcessConstraintPenaltyDefault() {
    // Satisfied constraint -> zero penalty
    OptimizationConstraint satisfied = OptimizationConstraint.lessThan("ok", proc -> 5.0, 100.0,
        ProductionOptimizer.ConstraintSeverity.HARD, 200.0, "OK");
    Assertions.assertEquals(0.0, satisfied.penalty(process), 1e-10);

    // Violated constraint -> positive penalty
    OptimizationConstraint violated = OptimizationConstraint.lessThan("violated", proc -> 150.0,
        100.0, ProductionOptimizer.ConstraintSeverity.HARD, 200.0, "Bad");
    double penalty = violated.penalty(process);
    Assertions.assertTrue(penalty > 0, "Violated constraint should have positive penalty");
  }

  // ============================================================================
  // CapacityConstraintAdapter tests
  // ============================================================================

  /**
   * Tests adapter wrapping for a satisfied capacity constraint.
   */
  @Test
  void testCapacityConstraintAdapterSatisfied() {
    CapacityConstraint cc =
        new CapacityConstraint("speed", "RPM", CapacityConstraint.ConstraintType.HARD);
    cc.setDesignValue(10000.0);
    cc.setCurrentValue(8000.0);
    cc.setSeverity(ConstraintSeverity.HARD);

    CapacityConstraintAdapter adapter = new CapacityConstraintAdapter("Comp1/speed", cc);

    Assertions.assertEquals("Comp1/speed", adapter.getName());
    Assertions.assertTrue(adapter.isSatisfied(process), "80% utilization is satisfied");
    Assertions.assertEquals(0.2, adapter.margin(process), 0.001, "Margin should be 1 - 0.8 = 0.2");
    Assertions.assertEquals(ConstraintSeverityLevel.HARD, adapter.getSeverityLevel());
    Assertions.assertTrue(adapter.isHard());
    Assertions.assertEquals(0.8, adapter.getUtilization(), 0.001);
  }

  /**
   * Tests adapter wrapping for a violated capacity constraint.
   */
  @Test
  void testCapacityConstraintAdapterViolated() {
    CapacityConstraint cc =
        new CapacityConstraint("gasLoad", "m/s", CapacityConstraint.ConstraintType.SOFT);
    cc.setDesignValue(100.0);
    cc.setCurrentValue(120.0);
    cc.setSeverity(ConstraintSeverity.SOFT);

    CapacityConstraintAdapter adapter = new CapacityConstraintAdapter("Sep1/gasLoad", cc);

    Assertions.assertFalse(adapter.isSatisfied(process), "120% utilization is violated");
    Assertions.assertTrue(adapter.margin(process) < 0, "Margin should be negative");
    Assertions.assertEquals(ConstraintSeverityLevel.SOFT, adapter.getSeverityLevel());
    Assertions.assertFalse(adapter.isHard());
    double penalty = adapter.penalty(process);
    Assertions.assertTrue(penalty > 0, "Should have penalty for violation");
  }

  /**
   * Tests adapter with CRITICAL severity maps correctly.
   */
  @Test
  void testCapacityConstraintAdapterCritical() {
    CapacityConstraint cc =
        new CapacityConstraint("surge", "", CapacityConstraint.ConstraintType.HARD);
    cc.setSeverity(ConstraintSeverity.CRITICAL);
    cc.setDesignValue(100.0);
    cc.setCurrentValue(50.0);

    CapacityConstraintAdapter adapter = new CapacityConstraintAdapter("Comp/surge", cc);
    Assertions.assertEquals(ConstraintSeverityLevel.CRITICAL, adapter.getSeverityLevel());
    Assertions.assertTrue(adapter.isHard());
  }

  // ============================================================================
  // ConstraintSeverityLevel mapping tests
  // ============================================================================

  /**
   * Tests round-trip conversions between severity systems.
   */
  @Test
  void testSeverityRoundTrip() {
    // Capacity -> Unified -> Capacity
    for (CapacityConstraint.ConstraintSeverity cs : CapacityConstraint.ConstraintSeverity
        .values()) {
      ConstraintSeverityLevel unified = ConstraintSeverityLevel.fromCapacitySeverity(cs);
      CapacityConstraint.ConstraintSeverity back = unified.toCapacitySeverity();
      Assertions.assertEquals(cs, back, "Round-trip should preserve severity: " + cs);
    }

    // Optimizer -> Unified -> Optimizer
    for (ProductionOptimizer.ConstraintSeverity os : ProductionOptimizer.ConstraintSeverity
        .values()) {
      ConstraintSeverityLevel unified = ConstraintSeverityLevel.fromOptimizerSeverity(os);
      ProductionOptimizer.ConstraintSeverity back = unified.toOptimizerSeverity();
      Assertions.assertEquals(os, back, "Round-trip should preserve severity: " + os);
    }
  }

  /**
   * Tests isHard() mapping for all levels.
   */
  @Test
  void testSeverityIsHardMapping() {
    Assertions.assertTrue(ConstraintSeverityLevel.CRITICAL.toIsHard());
    Assertions.assertTrue(ConstraintSeverityLevel.HARD.toIsHard());
    Assertions.assertFalse(ConstraintSeverityLevel.SOFT.toIsHard());
    Assertions.assertFalse(ConstraintSeverityLevel.ADVISORY.toIsHard());

    Assertions.assertEquals(ConstraintSeverityLevel.HARD, ConstraintSeverityLevel.fromIsHard(true));
    Assertions.assertEquals(ConstraintSeverityLevel.SOFT,
        ConstraintSeverityLevel.fromIsHard(false));
  }

  // ============================================================================
  // Constraint conversion tests
  // ============================================================================

  /**
   * Tests OptimizationConstraint -> ConstraintDefinition conversion.
   */
  @Test
  void testOptimizationConstraintToDefinition() {
    OptimizationConstraint oc = OptimizationConstraint.lessThan("maxTemp", proc -> 350.0, 400.0,
        ProductionOptimizer.ConstraintSeverity.HARD, 50.0, "Temp limit");

    ConstraintDefinition cd = oc.toConstraintDefinition();
    Assertions.assertEquals("maxTemp", cd.getName());
    Assertions.assertTrue(cd.isHard());
    Assertions.assertEquals(50.0, cd.getPenaltyWeight());
    Assertions.assertEquals(ConstraintDefinition.Type.UPPER_BOUND, cd.getType());
    Assertions.assertEquals(400.0, cd.getUpperBound());
    // Verify margin is consistent
    Assertions.assertEquals(oc.margin(process), cd.margin(process), 0.001);
  }

  /**
   * Tests ConstraintDefinition -> OptimizationConstraint conversion.
   */
  @Test
  void testConstraintDefinitionToOptimizationConstraint() {
    ConstraintDefinition cd = new ConstraintDefinition("minFlow",
        proc -> ((StreamInterface) proc.getUnit("feed")).getFlowRate("kg/hr"), 50.0);
    cd.setHard(true);
    cd.setPenaltyWeight(75.0);

    OptimizationConstraint oc = cd.toOptimizationConstraint();
    Assertions.assertEquals("minFlow", oc.getName());
    Assertions.assertEquals(ProductionOptimizer.ConstraintSeverity.HARD, oc.getSeverity());
    // Both should agree on satisfaction
    Assertions.assertEquals(cd.isSatisfied(process), oc.isSatisfied(process));
  }

  /**
   * Tests that ConstraintDefinition deserialization detects null evaluator.
   */
  @Test
  void testConstraintDefinitionNullEvaluatorConversion() {
    ConstraintDefinition cd = new ConstraintDefinition();
    cd.setName("orphan");
    // No evaluator set - simulates deserialization
    Assertions.assertThrows(IllegalStateException.class, () -> cd.toOptimizationConstraint(),
        "Should throw when evaluator is null");
  }

  // ============================================================================
  // ConstraintPenaltyCalculator tests
  // ============================================================================

  /**
   * Tests penalty calculator with functional constraints.
   */
  @Test
  void testPenaltyCalculatorFunctionalConstraints() {
    ConstraintPenaltyCalculator calc = new ConstraintPenaltyCalculator();

    // Add a satisfied constraint
    OptimizationConstraint satisfied = OptimizationConstraint.lessThan("ok", proc -> 5.0, 100.0,
        ProductionOptimizer.ConstraintSeverity.HARD, 100.0, "OK");
    calc.addConstraint(satisfied);

    Assertions.assertTrue(calc.isFeasible(process));
    Assertions.assertEquals(0.0, calc.totalPenalty(process), 1e-10);
    Assertions.assertEquals(1, calc.getConstraintCount());

    double raw = 42.0;
    Assertions.assertEquals(42.0, calc.penalize(raw, process), 1e-10, "No penalty when feasible");
  }

  /**
   * Tests penalty calculator with a violated constraint.
   */
  @Test
  void testPenaltyCalculatorViolation() {
    ConstraintPenaltyCalculator calc = new ConstraintPenaltyCalculator();

    OptimizationConstraint violated = OptimizationConstraint.lessThan("bad", proc -> 200.0, 100.0,
        ProductionOptimizer.ConstraintSeverity.HARD, 100.0, "Bad");
    calc.addConstraint(violated);

    Assertions.assertFalse(calc.isFeasible(process));
    Assertions.assertTrue(calc.totalPenalty(process) > 0);

    double raw = 42.0;
    double penalized = calc.penalize(raw, process);
    Assertions.assertTrue(penalized < raw, "Penalized should be worse than raw");
    Assertions.assertTrue(penalized < 0, "Penalized should be negative (infeasible)");
  }

  /**
   * Tests penalty calculator auto-discovery of equipment capacity constraints.
   */
  @Test
  void testPenaltyCalculatorEquipmentDiscovery() {
    ConstraintPenaltyCalculator calc = new ConstraintPenaltyCalculator();
    calc.addEquipmentCapacityConstraints(process);

    // Should have discovered at least compressor constraints
    Assertions.assertTrue(calc.getConstraintCount() > 0,
        "Should discover equipment capacity constraints");

    // All constraints should be ProcessConstraint instances
    for (ProcessConstraint pc : calc.getConstraints()) {
      Assertions.assertNotNull(pc.getName());
      Assertions.assertNotNull(pc.getSeverityLevel());
      // margin should be a finite number
      double m = pc.margin(process);
      Assertions.assertTrue(Double.isFinite(m), "Margin should be finite for: " + pc.getName());
    }
  }

  /**
   * Tests margin vector output for NLP solvers.
   */
  @Test
  void testMarginVector() {
    ConstraintPenaltyCalculator calc = new ConstraintPenaltyCalculator();
    calc.addConstraint(OptimizationConstraint.lessThan("a", proc -> 50.0, 100.0,
        ProductionOptimizer.ConstraintSeverity.HARD, 100.0, ""));
    calc.addConstraint(OptimizationConstraint.lessThan("b", proc -> 150.0, 100.0,
        ProductionOptimizer.ConstraintSeverity.SOFT, 100.0, ""));

    double[] margins = calc.evaluateMargins(process);
    Assertions.assertEquals(2, margins.length);
    Assertions.assertEquals(50.0, margins[0], 0.001, "First constraint margin = 100 - 50");
    Assertions.assertEquals(-50.0, margins[1], 0.001, "Second constraint margin = 100 - 150");
  }

  /**
   * Tests the evaluate() detailed report.
   */
  @Test
  void testPenaltyCalculatorEvaluateReport() {
    ConstraintPenaltyCalculator calc = new ConstraintPenaltyCalculator();
    calc.addConstraint(OptimizationConstraint.lessThan("temp", proc -> 300.0, 400.0,
        ProductionOptimizer.ConstraintSeverity.HARD, 100.0, "Temp OK"));

    List<ConstraintPenaltyCalculator.ConstraintEvaluation> report = calc.evaluate(process);
    Assertions.assertEquals(1, report.size());
    ConstraintPenaltyCalculator.ConstraintEvaluation eval = report.get(0);
    Assertions.assertEquals("temp", eval.getName());
    Assertions.assertTrue(eval.isSatisfied());
    Assertions.assertEquals(100.0, eval.getMargin(), 0.001);
    Assertions.assertEquals(0.0, eval.getPenalty(), 1e-10);
  }

  // ============================================================================
  // ProcessSimulationEvaluator integration tests
  // ============================================================================

  /**
   * Tests addEquipmentCapacityConstraints on ProcessSimulationEvaluator.
   */
  @Test
  void testEvaluatorEquipmentConstraintDiscovery() {
    ProcessSimulationEvaluator evaluator = new ProcessSimulationEvaluator(process);
    int before = evaluator.getConstraintCount();

    evaluator.addEquipmentCapacityConstraints();

    Assertions.assertTrue(evaluator.getConstraintCount() > before,
        "Should add equipment constraints");

    // Verify they're accessible as ProcessConstraint
    List<ProcessConstraint> unified = evaluator.getAllProcessConstraints();
    Assertions.assertEquals(evaluator.getConstraintCount(), unified.size());
    for (ProcessConstraint pc : unified) {
      Assertions.assertNotNull(pc.getName());
      Assertions.assertNotNull(pc.getSeverityLevel());
    }
  }

  /**
   * Tests constraint margin vector from ProcessSimulationEvaluator.
   */
  @Test
  void testEvaluatorMarginVector() {
    ProcessSimulationEvaluator evaluator = new ProcessSimulationEvaluator(process);
    evaluator.addConstraintUpperBound("testLimit", proc -> 50.0, 100.0);

    double[] margins = evaluator.getConstraintMarginVector(process);
    Assertions.assertEquals(1, margins.length);
    Assertions.assertEquals(50.0, margins[0], 0.001);
  }

  /**
   * Tests that constraints from different sources can be mixed in ConstraintPenaltyCalculator.
   */
  @Test
  void testMixedConstraintSources() {
    ConstraintPenaltyCalculator calc = new ConstraintPenaltyCalculator();

    // Source 1: OptimizationConstraint (internal optimizer)
    calc.addConstraint(OptimizationConstraint.lessThan("funcLimit", proc -> 50.0, 200.0,
        ProductionOptimizer.ConstraintSeverity.HARD, 100.0, "Func"));

    // Source 2: ConstraintDefinition (external optimizer)
    ConstraintDefinition cd = new ConstraintDefinition("nlpLimit", proc -> 80.0, 10.0);
    cd.setHard(false);
    calc.addConstraint(cd);

    // Source 3: CapacityConstraintAdapter (equipment)
    CapacityConstraint cc =
        new CapacityConstraint("speed", "RPM", CapacityConstraint.ConstraintType.HARD);
    cc.setDesignValue(10000.0).setCurrentValue(7000.0);
    calc.addConstraint(new CapacityConstraintAdapter("Comp/speed", cc));

    Assertions.assertEquals(3, calc.getConstraintCount());
    Assertions.assertTrue(calc.isFeasible(process), "All constraints should be satisfied");

    double[] margins = calc.evaluateMargins(process);
    Assertions.assertEquals(3, margins.length);
    for (double m : margins) {
      Assertions.assertTrue(m > 0, "All margins should be positive");
    }
  }

  /**
   * Tests the adaptive penalty scales with objective magnitude.
   */
  @Test
  void testAdaptivePenaltyScaling() {
    ConstraintPenaltyCalculator calc = new ConstraintPenaltyCalculator();
    OptimizationConstraint violated = OptimizationConstraint.lessThan("v", proc -> 200.0, 100.0,
        ProductionOptimizer.ConstraintSeverity.HARD, 1.0, "");
    calc.addConstraint(violated);

    // Small objective
    double penalizedSmall = calc.penalize(1.0, process);
    // Large objective
    double penalizedLarge = calc.penalize(1000.0, process);

    // Both should be negative (infeasible)
    Assertions.assertTrue(penalizedSmall < 0);
    Assertions.assertTrue(penalizedLarge < 0);

    // The large-objective penalty should be absolutely larger
    Assertions.assertTrue(Math.abs(penalizedLarge) > Math.abs(penalizedSmall),
        "Penalty should scale with objective magnitude");
  }
}
