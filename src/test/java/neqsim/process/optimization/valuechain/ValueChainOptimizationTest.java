package neqsim.process.optimization.valuechain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.capacity.CapacityConstraint;

/**
 * Unit tests for the value-chain optimization package.
 *
 * <p>
 * The tests exercise the pure economic and optimization logic with deterministic mock evaluators so
 * they run fast and never depend on a converging NeqSim simulation.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class ValueChainOptimizationTest {

  /** Numerical tolerance for floating-point assertions. */
  private static final double TOL = 1e-6;

  /**
   * Verifies the economic breakdown produced by {@link ValueChainObjective}.
   */
  @Test
  void testValueChainObjectiveBreakdown() {
    EconomicParameters econ = new EconomicParameters().setGasPrice(3.0).setPowerCost(0.6)
        .setCo2Tax(1200.0).setCo2IntensityTonnePerMWh(0.20).setDiscountRate(0.0);
    ValueChainObjective obj = new ValueChainObjective(econ);
    ValueChainObjective.ValueResult r = obj.evaluate(3.2e6, 0.0, 1000.0);

    assertEquals(9.6e6, r.getRevenueNokPerDay(), 1.0);
    assertEquals(14400.0, r.getEnergyCostNokPerDay(), TOL);
    assertEquals(4.8, r.getCo2TonnePerDay(), TOL);
    assertEquals(5760.0, r.getCarbonCostNokPerDay(), TOL);
    assertEquals(9.6e6 - 14400.0 - 5760.0, r.getNetValueNokPerDay(), 1.0);

    // Zero discount: present value of one year equals net*365.
    double pv = obj.presentValueOfAnnualCashFlow(r.getNetValueNokPerDay(), 0);
    assertEquals(r.getNetValueNokPerDay() * 365.0, pv, 1.0);
  }

  /**
   * Verifies that a higher carbon price reduces the net value (carbon-aware sweep).
   */
  @Test
  void testCarbonPriceReducesValue() {
    EconomicParameters econ = new EconomicParameters().setGasPrice(3.0).setPowerCost(0.6)
        .setCo2IntensityTonnePerMWh(0.20);
    ValueChainObjective obj = new ValueChainObjective(econ);
    double low = obj.evaluate(1.0e6, 0.0, 2000.0, 0.0).getNetValueNokPerDay();
    double high = obj.evaluate(1.0e6, 0.0, 2000.0, 5000.0).getNetValueNokPerDay();
    assertTrue(high < low, "Higher carbon price must reduce net value");
  }

  /**
   * Verifies ranking, shadow-price write-back, and attractiveness in
   * {@link DebottleneckingAdvisor}.
   */
  @Test
  void testDebottleneckingAdvisorRanking() {
    EconomicParameters econ = new EconomicParameters().setDiscountRate(0.08);
    DebottleneckingAdvisor advisor = new DebottleneckingAdvisor(econ);
    CapacityConstraint constraintA = new CapacityConstraint("Tubing-1 velocity");

    advisor.addCandidate(new DebottleneckingAdvisor.DebottleneckCandidate("Larger tubing Well-1",
        "Tubing-1", 80.0e6, 5, 11, 60.0e6, constraintA));
    advisor.addCandidate(new DebottleneckingAdvisor.DebottleneckCandidate("New compressor stage",
        "Compressor", 400.0e6, 8, 11, 30.0e6, null));

    List<DebottleneckingAdvisor.Recommendation> recs = advisor.evaluate();
    assertEquals(2, recs.size());
    // The cheaper, earlier, higher-value upgrade should rank first.
    assertEquals("Larger tubing Well-1", recs.get(0).getCandidate().getName());
    assertTrue(recs.get(0).getNpvNok() > recs.get(1).getNpvNok());
    assertTrue(recs.get(0).isAttractive());

    // Shadow price written back onto the live constraint.
    int updated = advisor.applyShadowPrices();
    assertEquals(1, updated);
    assertEquals(60.0e6, constraintA.getShadowPrice(), TOL);
  }

  /**
   * Verifies the life-of-field optimizer installs a profitable investment in its best year.
   */
  @Test
  void testLifeOfFieldInstallsProfitableInvestment() {
    EconomicParameters econ = new EconomicParameters().setDiscountRate(0.0);
    LifeOfFieldOptimizer opt = new LifeOfFieldOptimizer(5, econ);
    opt.addInvestment(new LifeOfFieldOptimizer.Investment("Compression", 90.0, 0));

    // Active -> 20/yr, inactive -> 0/yr. Installing in year 0 gives 5*20-90 = 10 (zero discount).
    LifeOfFieldOptimizer.LifeOfFieldEvaluator ev = new LifeOfFieldOptimizer.LifeOfFieldEvaluator() {
      @Override
      public double annualNetValueNok(int year, boolean[] active) {
        return active[0] ? 20.0 : 0.0;
      }
    };
    LifeOfFieldOptimizer.LifeOfFieldResult res = opt.optimize(ev);
    assertEquals(0, res.getInstallYears()[0]);
    assertEquals(10.0, res.getNpvNok(), TOL);
  }

  /**
   * Verifies the life-of-field optimizer skips an uneconomic investment.
   */
  @Test
  void testLifeOfFieldSkipsUneconomicInvestment() {
    EconomicParameters econ = new EconomicParameters().setDiscountRate(0.0);
    LifeOfFieldOptimizer opt = new LifeOfFieldOptimizer(5, econ);
    opt.addInvestment(new LifeOfFieldOptimizer.Investment("Expensive", 10000.0, 0));

    LifeOfFieldOptimizer.LifeOfFieldEvaluator ev = new LifeOfFieldOptimizer.LifeOfFieldEvaluator() {
      @Override
      public double annualNetValueNok(int year, boolean[] active) {
        return active[0] ? 20.0 : 0.0;
      }
    };
    LifeOfFieldOptimizer.LifeOfFieldResult res = opt.optimize(ev);
    assertEquals(-1, res.getInstallYears()[0]);
    assertEquals(0.0, res.getNpvNok(), TOL);
  }

  /**
   * Verifies the network allocation optimizer finds the interior optimum of a concave objective.
   */
  @Test
  void testNetworkAllocationFindsOptimum() {
    NetworkAllocationOptimizer opt = new NetworkAllocationOptimizer(10.0, 2);
    opt.setInitialStepFraction(0.4).setMaxIterations(2000).setTolerance(1e-5);

    // Concave objective maximised at a=3, b=7.
    NetworkAllocationOptimizer.AllocationEvaluator ev =
        new NetworkAllocationOptimizer.AllocationEvaluator() {
          @Override
          public NetworkAllocationOptimizer.AllocationResult evaluate(double[] allocation) {
            double a = allocation[0];
            double b = allocation[1];
            double f = -(a - 3.0) * (a - 3.0) - (b - 7.0) * (b - 7.0);
            return new NetworkAllocationOptimizer.AllocationResult(allocation, f, true);
          }
        };
    NetworkAllocationOptimizer.AllocationResult res = opt.optimize(ev);
    assertTrue(res.isFeasible());
    assertEquals(3.0, res.getAllocation()[0], 0.05);
    assertEquals(7.0, res.getAllocation()[1], 0.05);
    // Sum constraint preserved.
    assertEquals(10.0, res.getAllocation()[0] + res.getAllocation()[1], TOL);
  }

  /**
   * Verifies the robustness percentiles and feasibility fraction.
   */
  @Test
  void testRobustOptimizationPercentiles() {
    RobustOptimizationStudy study = new RobustOptimizationStudy();
    study.addScenario(new double[] {1.0});
    study.addScenario(new double[] {2.0});
    study.addScenario(new double[] {3.0});

    // objective = decision * scenario; always feasible.
    RobustOptimizationStudy.ScenarioEvaluator ev = new RobustOptimizationStudy.ScenarioEvaluator() {
      @Override
      public RobustOptimizationStudy.ScenarioOutcome evaluate(double[] decision,
          double[] scenario) {
        return new RobustOptimizationStudy.ScenarioOutcome(decision[0] * scenario[0], true);
      }
    };
    RobustOptimizationStudy.RobustResult r = study.evaluateDecision(new double[] {10.0}, ev);
    assertEquals(20.0, r.getP50(), TOL);
    assertEquals(20.0, r.getMean(), TOL);
    assertEquals(1.0, r.getFeasibleFraction(), TOL);
    assertTrue(r.getP10() < r.getP50());
    assertTrue(r.getP90() > r.getP50());
  }

  /**
   * Verifies chance-constrained selection picks a feasible-enough candidate over a fragile one.
   */
  @Test
  void testRobustSelectionHonoursConfidence() {
    RobustOptimizationStudy study = new RobustOptimizationStudy().setRequiredConfidence(0.6);
    for (int i = 1; i <= 10; i++) {
      study.addScenario(new double[] {i});
    }
    // Candidate 0 (aggressive): high value but feasible only when scenario <= 3 (30%).
    // Candidate 1 (robust): lower value but always feasible.
    RobustOptimizationStudy.ScenarioEvaluator ev = new RobustOptimizationStudy.ScenarioEvaluator() {
      @Override
      public RobustOptimizationStudy.ScenarioOutcome evaluate(double[] decision,
          double[] scenario) {
        boolean aggressive = decision[0] > 50.0;
        if (aggressive) {
          return new RobustOptimizationStudy.ScenarioOutcome(decision[0], scenario[0] <= 3.0);
        }
        return new RobustOptimizationStudy.ScenarioOutcome(decision[0], true);
      }
    };
    List<double[]> candidates = new ArrayList<double[]>();
    candidates.add(new double[] {100.0});
    candidates.add(new double[] {30.0});
    RobustOptimizationStudy.RobustResult chosen = study.selectRobust(candidates, ev);
    assertEquals(30.0, chosen.getDecision()[0], TOL);
    assertEquals(1.0, chosen.getFeasibleFraction(), TOL);
  }

  /**
   * Verifies the parallel sweep preserves input order and evaluates every input.
   */
  @Test
  void testParallelSweepPreservesOrder() {
    ParallelSweep sweep = new ParallelSweep().setParallelism(4);
    List<double[]> inputs = new ArrayList<double[]>();
    for (int i = 0; i < 20; i++) {
      inputs.add(new double[] {i});
    }
    List<Double> out = sweep.run(inputs, new ParallelSweep.SweepEvaluator<Double>() {
      @Override
      public Double evaluate(double[] input) {
        return input[0] * 2.0;
      }
    });
    assertEquals(20, out.size());
    for (int i = 0; i < 20; i++) {
      assertEquals(i * 2.0, out.get(i), TOL);
    }
  }

  /**
   * Verifies the real-time optimization loop runs the configured number of cycles and records them.
   */
  @Test
  void testRealTimeOptimizationLoopCycles() {
    final AtomicInteger optimizeCalls = new AtomicInteger(0);
    final AtomicInteger applyCalls = new AtomicInteger(0);

    RealTimeOptimizationLoop loop = new RealTimeOptimizationLoop();
    loop.setReader(new RealTimeOptimizationLoop.PlantReader() {
      @Override
      public double[] read() {
        return new double[] {1.0, 2.0};
      }
    });
    loop.setOptimizer(new RealTimeOptimizationLoop.SetpointOptimizer() {
      @Override
      public double[] optimize() {
        int n = optimizeCalls.incrementAndGet();
        return new double[] {n * 10.0};
      }
    });
    loop.setWriter(new RealTimeOptimizationLoop.SetpointWriter() {
      @Override
      public void apply(double[] setpoints) {
        applyCalls.incrementAndGet();
      }
    });
    loop.setObjectiveProbe(new RealTimeOptimizationLoop.ObjectiveProbe() {
      @Override
      public double currentObjective() {
        return 42.0;
      }
    });

    List<RealTimeOptimizationLoop.CycleRecord> history = loop.run(3);
    assertEquals(3, history.size());
    assertEquals(3, optimizeCalls.get());
    assertEquals(3, applyCalls.get());
    assertEquals(30.0, history.get(2).getSetpoints()[0], TOL);
    assertEquals(42.0, history.get(0).getObjective(), TOL);
    assertFalse(loop.toJson().isEmpty());
  }

  /**
   * Verifies the shadow-price accessor default and round-trip on {@link CapacityConstraint}.
   */
  @Test
  void testCapacityConstraintShadowPrice() {
    CapacityConstraint c = new CapacityConstraint("test");
    assertEquals(0.0, c.getShadowPrice(), TOL);
    c.setShadowPrice(1234.5);
    assertEquals(1234.5, c.getShadowPrice(), TOL);
  }
}
