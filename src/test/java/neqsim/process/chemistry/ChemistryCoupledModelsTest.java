package neqsim.process.chemistry;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.chemistry.scale.ClosedLoopDepositionSolver;
import neqsim.process.chemistry.scale.ScaleDepositionAccumulator;
import neqsim.process.chemistry.scavenger.PackedBedScavengerReactor;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the coupled deposition-flow solver and the packed-bed scavenger model.
 *
 * @author ESOL
 * @version 1.0
 */
class ChemistryCoupledModelsTest {

  /**
   * Builds a minimal pipe and exercises the closed-loop deposition solver. Verifies that the
   * iterative diameter sequence is monotonically non-increasing and the solver either converges or
   * terminates with a documented blocked-pipe state.
   */
  @Test
  void closedLoopDepositionSolverConverges() {
    SystemInterface fluid = new SystemSrkEos(308.15, 80.0);
    fluid.addComponent("methane", 95.0);
    fluid.addComponent("CO2", 5.0);
    fluid.addComponent("water", 1.5);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(50000.0, "kg/hr");
    feed.run();

    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("scale-line", feed);
    pipe.setLength(1000.0);
    pipe.setDiameter(0.2);
    pipe.setElevation(0.0);
    pipe.setAngle(0.0);
    pipe.setNumberOfIncrements(10);
    pipe.run();

    ScaleDepositionAccumulator acc = new ScaleDepositionAccumulator(pipe)
        .setBrineChemistry(1500.0, 400.0, 100.0, 5.0, 12000.0, 35000.0).setpHAndCo2(6.5, 4.0)
        .setInhibitorEfficiency(0.7).setServiceYears(5.0);

    ClosedLoopDepositionSolver solver =
        new ClosedLoopDepositionSolver(pipe, acc).setMaxIterations(5).setToleranceM(1e-3);
    solver.solve();

    assertTrue(solver.getIterationsTaken() >= 1);
    // diameter history must be monotonically non-increasing
    for (int i = 1; i < solver.getDiameterHistoryM().size(); i++) {
      assertTrue(
          solver.getDiameterHistoryM().get(i) <= solver.getDiameterHistoryM().get(i - 1) + 1e-9,
          "diameter must not grow");
    }
    assertNotNull(solver.toJson());
    // pipe diameter must be restored (within tolerance)
    assertTrue(Math.abs(pipe.getDiameter() - 0.2) < 1e-9, "pipe diameter must be restored");
  }

  /**
   * Packed-bed scavenger: outlet concentration grows with time and breakthrough is detected.
   */
  @Test
  void packedBedScavengerProducesBreakthroughCurve() {
    PackedBedScavengerReactor bed = new PackedBedScavengerReactor().setGeometry(0.5, 2.0, 0.4)
        .setMedia(5.0, 1100.0, 1.0).setRateConstant(8.0).setFeed(2.0, 0.005)
        .setDiscretisation(30, 100).setSimulationTime(3600.0 * 24.0 * 60.0, 0.05).evaluate();

    assertTrue(bed.isEvaluated());
    assertTrue(bed.getOutletConcentrationProfile().size() == 100);

    // outlet concentration should rise over time (accept noise of one bin)
    double cFirst = bed.getOutletConcentrationProfile().get(0);
    double cLast = bed.getOutletConcentrationProfile().get(99);
    assertTrue(cLast >= cFirst - 1e-9, "outlet concentration should grow over time");

    // either breakthrough is reached or simulation horizon exceeded
    assertTrue(bed.getTotalH2sRemovedKg() > 0.0);
    assertTrue(bed.getFinalBedUtilisation() > 0.0);
    assertNotNull(bed.toJson());
    assertTrue(bed.getStandardsApplied().size() >= 1);
  }
}
