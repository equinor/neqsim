package neqsim.process.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link AgenticProcessOptimizer} &mdash; the ML/agentic closed-loop optimizer that
 * drives a process simulation through {@link ProcessAutomation#evaluate}.
 *
 * @author NeqSim
 * @version 1.0
 */
class AgenticProcessOptimizerTest {

  private ProcessSystem process;
  private ProcessAutomation automation;

  /**
   * Builds a small gas-compression flowsheet and runs it once so the automation facade has live
   * variables to optimize over.
   */
  @BeforeEach
  void setUp() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 30.0, 70.0);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("ethane", 0.12);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-butane", 0.03);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(50000.0, "kg/hr");
    feed.setTemperature(30.0, "C");
    feed.setPressure(70.0, "bara");

    Separator separator = new Separator("HP Sep", feed);

    Compressor compressor = new Compressor("Compressor", separator.getGasOutStream());
    compressor.setOutletPressure(120.0);

    Cooler cooler = new Cooler("Cooler", compressor.getOutletStream());
    cooler.setOutletTemperature(30.0, "C");

    ThrottlingValve valve = new ThrottlingValve("Valve", cooler.getOutletStream());
    valve.setOutletPressure(50.0);

    process = new ProcessSystem();
    process.add(feed);
    process.add(separator);
    process.add(compressor);
    process.add(cooler);
    process.add(valve);
    process.run();

    automation = process.getAutomation();
  }

  /**
   * The optimizer should find an interior optimum of a custom quadratic cost function over a single
   * bounded decision variable.
   */
  @Test
  void testInteriorOptimumWithObjectiveFunction() {
    AgenticProcessOptimizer opt = automation.newOptimizer();
    opt.addVariable("Compressor.outletPressure", 80.0, 200.0, "bara");
    opt.setObjectiveFunction(new Function<Map<String, Double>, Double>() {
      @Override
      public Double apply(Map<String, Double> readMap) {
        double p = readMap.get("Compressor.outletPressure").doubleValue();
        return Double.valueOf((p - 130.0) * (p - 130.0));
      }
    });
    opt.setSeed(42L).setMaxEvaluations(80).setConvergenceTolerance(1.0e-4);

    AgenticProcessOptimizer.OptimizationResult result = opt.optimize();

    assertTrue(result.isSuccess(), "optimization should succeed");
    assertTrue(result.isFeasible(), "objective-function problem with no constraints is feasible");
    double bestP = result.getBestSetpoints().get("Compressor.outletPressure").doubleValue();
    assertEquals(130.0, bestP, 2.0, "should converge near the analytic minimum at 130 bara");
    assertFalse(result.getTrajectory().isEmpty(), "trajectory must be logged");
  }

  /**
   * A greater-or-equal constraint should steer the solution to the constraint boundary when the raw
   * objective would otherwise push the variable lower.
   */
  @Test
  void testConstraintSteersToBoundary() {
    AgenticProcessOptimizer opt = automation.newOptimizer();
    opt.addVariable("Compressor.outletPressure", 80.0, 200.0, "bara");
    opt.minimize("Compressor.power", "kW");
    // Compressor power rises with discharge pressure, so the unconstrained optimum is the lower
    // bound (80 bara). The constraint forces the pressure up to at least 120 bara.
    opt.addConstraintGreaterOrEqual("Compressor.outletPressure", 120.0, "bara", 1.0e4);
    opt.setSeed(7L).setMaxEvaluations(90);

    AgenticProcessOptimizer.OptimizationResult result = opt.optimize();

    assertTrue(result.isSuccess(), "optimization should succeed");
    assertTrue(result.isFeasible(), "a feasible point at/above the constraint must exist");
    double bestP = result.getBestSetpoints().get("Compressor.outletPressure").doubleValue();
    assertTrue(bestP >= 119.0, "constraint must be satisfied (>= 120 bara), got " + bestP);
    assertTrue(bestP <= 126.0, "solution should sit near the active constraint, got " + bestP);
  }

  /**
   * The optimizer must never throw even when the objective address is invalid; it should report
   * success=true (points were evaluated) but feasible=false.
   */
  @Test
  void testNeverThrowsWithBadObjectiveAddress() {
    AgenticProcessOptimizer opt = automation.newOptimizer();
    opt.addVariable("Compressor.outletPressure", 80.0, 200.0, "bara");
    opt.minimize("Compressor.thisDoesNotExist", "kW");
    opt.setSeed(1L).setMaxEvaluations(20);

    AgenticProcessOptimizer.OptimizationResult result = opt.optimize();

    assertNotNull(result, "optimize() must return a result, never throw");
    assertFalse(result.isFeasible(), "no readable objective means no feasible point");
    assertFalse(result.getTrajectory().isEmpty(), "trajectory is logged even for bad objectives");
  }

  /**
   * Two runs with the same seed and the same problem must produce an identical trajectory length
   * and identical best setpoints (deterministic seeding).
   */
  @Test
  void testDeterministicForFixedSeed() {
    AgenticProcessOptimizer.OptimizationResult r1 = buildQuadraticProblem(99L).optimize();
    AgenticProcessOptimizer.OptimizationResult r2 = buildQuadraticProblem(99L).optimize();

    assertEquals(r1.getEvaluations(), r2.getEvaluations(),
        "same seed must produce same number of evaluations");
    double p1 = r1.getBestSetpoints().get("Compressor.outletPressure").doubleValue();
    double p2 = r2.getBestSetpoints().get("Compressor.outletPressure").doubleValue();
    assertEquals(p1, p2, 1.0e-9, "same seed must produce the same best point");
  }

  /**
   * {@link AgenticProcessOptimizer#useAdjustableParameters()} should populate decision variables
   * from the process adjusters/bounds.
   */
  @Test
  void testUseAdjustableParameters() {
    AgenticProcessOptimizer opt = automation.newOptimizer();
    int count = opt.useAdjustableParameters();
    // Should not throw, and the returned count must match the number of variables added.
    assertEquals(count, opt.getVariables().size(),
        "returned count must equal the number of decision variables added");
  }

  /**
   * The readiness report must parse as JSON and advertise the never-throw and bounded-action-space
   * capabilities.
   */
  @Test
  void testReadinessJson() {
    AgenticProcessOptimizer opt = automation.newOptimizer();
    String json = opt.getReadinessJson();
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();

    assertEquals("1.0", root.get("schemaVersion").getAsString());
    assertEquals("AgenticProcessOptimizer", root.get("optimizer").getAsString());
    JsonArray caps = root.getAsJsonArray("capabilities");
    assertTrue(caps.size() > 0, "capabilities array must not be empty");

    boolean hasNeverThrows = false;
    boolean hasBoundedAction = false;
    for (int i = 0; i < caps.size(); i++) {
      JsonObject cap = caps.get(i).getAsJsonObject();
      String name = cap.get("capability").getAsString();
      if ("never_throws".equals(name)) {
        hasNeverThrows = true;
        assertEquals("full", cap.get("level").getAsString());
      }
      if ("bounded_action_space".equals(name)) {
        hasBoundedAction = true;
        assertEquals("full", cap.get("level").getAsString());
      }
    }
    assertTrue(hasNeverThrows, "readiness must report never_throws capability");
    assertTrue(hasBoundedAction, "readiness must report bounded_action_space capability");
  }

  /**
   * {@link AgenticProcessOptimizer#optimizeToJson()} must return a schema-versioned, parseable JSON
   * document containing the trajectory.
   */
  @Test
  void testOptimizeToJson() {
    AgenticProcessOptimizer opt = buildQuadraticProblem(5L);
    String json = opt.optimizeToJson();
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();

    assertEquals("1.0", root.get("schemaVersion").getAsString());
    assertTrue(root.get("success").getAsBoolean(), "quadratic problem should succeed");
    assertTrue(root.has("trajectory"), "result JSON must include the trajectory tape");
  }

  /**
   * Helper that builds a single-variable quadratic-cost optimization problem with a given seed.
   *
   * @param seed the random seed for the simplex initialization
   * @return a configured optimizer ready to run
   */
  private AgenticProcessOptimizer buildQuadraticProblem(long seed) {
    AgenticProcessOptimizer opt = automation.newOptimizer();
    opt.addVariable("Compressor.outletPressure", 80.0, 200.0, "bara");
    opt.setObjectiveFunction(new Function<Map<String, Double>, Double>() {
      @Override
      public Double apply(Map<String, Double> readMap) {
        double p = readMap.get("Compressor.outletPressure").doubleValue();
        return Double.valueOf((p - 130.0) * (p - 130.0));
      }
    });
    opt.setSeed(seed).setMaxEvaluations(80).setConvergenceTolerance(1.0e-4);
    return opt;
  }
}
