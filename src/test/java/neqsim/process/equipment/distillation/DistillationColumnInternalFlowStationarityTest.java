package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;

/**
 * Verifies that a column whose specifications admit no steady state is not reported as solved.
 *
 * <p>
 * The case is the TEG regeneration still from GitHub issue 4961: fixed reboiler and condenser temperatures at about
 * 1.1 bara. The reboiler temperature fixes the water left in the lean TEG and the condenser temperature fixes how much
 * water the overhead gas can carry. At a 95 C condenser more water enters than those two outlets can carry, so the
 * surplus circulates between reboiler and condenser and the internal reflux grows without bound. At 100 C the problem
 * is well-posed.
 * </p>
 *
 * @author NeqSim / AI-assisted
 * @version 1.0
 */
class DistillationColumnInternalFlowStationarityTest extends neqsim.NeqSimTest {
  /** Still pressure in bara. */
  private static final double PRESSURE_BARA = 1.10325;

  /**
   * Build and run the TEG still with fixed reboiler and condenser temperatures.
   *
   * @param condenserTemperatureC condenser outlet temperature in Celsius
   * @return the solved column
   */
  private static DistillationColumn runTegStill(double condenserTemperatureC) {
    double richTegKgPerHour = 5500.0;
    double waterWeightPercent = 5.0;
    SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + 120.0, PRESSURE_BARA);
    fluid.addComponent("nitrogen", 0.03, "kg/hr");
    fluid.addComponent("CO2", 2.0, "kg/hr");
    fluid.addComponent("methane", 5.0, "kg/hr");
    fluid.addComponent("ethane", 1.0, "kg/hr");
    fluid.addComponent("water", waterWeightPercent / 100.0 * richTegKgPerHour, "kg/hr");
    fluid.addComponent("TEG", (100.0 - waterWeightPercent) / 100.0 * richTegKgPerHour, "kg/hr");
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(false);

    Stream feed = new Stream("rich TEG", fluid);
    feed.setTemperature(120.0, "C");
    feed.setPressure(PRESSURE_BARA, "bara");

    SystemInterface strippingFluid = fluid.clone();
    strippingFluid.setMolarComposition(new double[] {0.0, 0.0, 1.0, 0.0, 0.0, 0.0});
    Stream strippingGas = new Stream("stripping gas", strippingFluid);
    strippingGas.setFlowRate(50.0, "kg/hr");
    strippingGas.setTemperature(204.0, "C");
    strippingGas.setPressure(PRESSURE_BARA, "bara");

    DistillationColumn column = new DistillationColumn("still", 1, true, true);
    column.addFeedStream(feed, 1);
    column.getTray(1).addStream(strippingGas);
    column.getReboiler().setOutTemperature(273.15 + 204.0);
    column.getCondenser().setOutTemperature(273.15 + condenserTemperatureC);
    column.setTopPressure(PRESSURE_BARA);
    column.setBottomPressure(PRESSURE_BARA);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(strippingGas);
    process.add(column);
    process.run();
    return column;
  }

  /** A condenser below the dew point of the net overhead has no steady state and must not report solved. */
  @Test
  void infeasibleFixedTemperatureStillIsNotReportedSolved() {
    DistillationColumn column = runTegStill(95.0);

    assertFalse(column.solved(), "A column accumulating water between reboiler and condenser must not be solved");
    assertEquals(DistillationColumn.SolveStatus.FAILED, column.getLastSolveStatus());
    assertTrue(column.getLastInternalFlowChange() > column.getInternalFlowStationarityTolerance(),
        "Internal flows should still be changing: " + column.getLastInternalFlowChange());
    assertTrue(column.getLastInternalFlowGrowth() > 0.0, "Internal traffic should be growing, not oscillating");
    assertTrue(column.isInternalInventoryAccumulating(), column.getConvergenceDiagnostics());
    assertTrue(column.getLastSolveStatusReason().contains("accumulating"),
        "The status reason should explain the accumulation: " + column.getLastSolveStatusReason());
    assertTrue(column.getConvergenceDiagnostics().contains("internal flow change per sweep"),
        "Diagnostics should report the internal flow change");
  }

  /** The same still with a condenser above the overhead dew point keeps solving to the established reflux. */
  @Test
  void wellPosedFixedTemperatureStillStillSolves() {
    DistillationColumn column = runTegStill(100.0);

    assertTrue(column.solved(), column.getConvergenceDiagnostics());
    assertEquals(DistillationColumn.SolveStatus.RIGOROUS_CONVERGED, column.getLastSolveStatus());
    assertTrue(column.getLastInternalFlowChange() <= column.getInternalFlowStationarityTolerance(),
        "Internal flows should be settled: " + column.getLastInternalFlowChange());
    assertFalse(column.isInternalInventoryAccumulating());
    assertEquals(9.2, column.getCondenser().getLiquidOutStream().getFlowRate("kg/hr"), 0.5,
        "Reflux should match the value reported by jneqsim 3.26 and 3.28");
  }

  /** The flow-step measure sums absolute tray outlet flow changes relative to the feed. */
  @Test
  void internalFlowStepIsRelativeToFeed() {
    double step = DistillationColumn.measureInternalFlowStep(new double[] {100.0, 10.0}, new double[] {20.0, 30.0},
        new double[] {104.0, 9.0}, new double[] {23.0, 30.0}, 100.0);
    assertEquals(0.08, step, 1.0e-12);
    assertTrue(Double.isInfinite(DistillationColumn.measureInternalFlowStep(new double[] {1.0}, new double[] {1.0},
        new double[] {2.0}, new double[] {1.0}, 0.0)));
  }

  /** The growth trend is averaged over a sliding window so one sweep against the trend does not flip its sign. */
  @Test
  void internalTrafficGrowthIsAveragedOverWindow() {
    double[] history = new double[4];
    int count = 0;
    double[] vapor = new double[] {0.0};
    assertTrue(Double.isNaN(DistillationColumn.averageInternalTrafficGrowth(history, count, 100.0)));
    double[] totals = new double[] {100.0, 110.0, 120.0, 130.0, 125.0};
    for (double total : totals) {
      count = DistillationColumn.recordInternalTraffic(history, count, new double[] {total}, vapor);
    }
    assertEquals(4, count);
    // Window holds 110, 120, 130, 125: net +15 over 3 sweeps despite the last sweep falling by 5.
    assertEquals(0.05, DistillationColumn.averageInternalTrafficGrowth(history, count, 100.0), 1.0e-12);
  }

  /** Shrinking growth is convergence; growth that does not shrink between windows is accumulation. */
  @Test
  void internalTrafficGrowthRatioSeparatesConvergenceFromAccumulation() {
    double[] converging = new double[] {0.0, 8.0, 12.0, 14.0, 15.0};
    assertEquals(0.25, DistillationColumn.internalTrafficGrowthRatio(converging, converging.length, 2), 1.0e-12);

    double[] accumulating = new double[] {0.0, 10.0, 20.0, 30.0, 40.0};
    assertEquals(1.0, DistillationColumn.internalTrafficGrowthRatio(accumulating, accumulating.length, 2), 1.0e-12);

    assertTrue(Double.isNaN(DistillationColumn.internalTrafficGrowthRatio(accumulating, 4, 2)),
        "two full windows are needed");
    double[] shrinking = new double[] {40.0, 30.0, 20.0, 10.0, 0.0};
    assertTrue(Double.isNaN(DistillationColumn.internalTrafficGrowthRatio(shrinking, shrinking.length, 2)),
        "falling traffic is not accumulation");
  }

  /** The stationarity tolerance must be positive; infinity switches the check off. */
  @Test
  void internalFlowStationarityToleranceIsValidated() {
    DistillationColumn column = new DistillationColumn("still", 1, true, true);
    assertEquals(1.0e-3, column.getInternalFlowStationarityTolerance(), 1.0e-15,
        "the default follows the default mass balance tolerance");
    column.setMassBalanceTolerance(0.16);
    assertEquals(1.0e-2, column.getInternalFlowStationarityTolerance(), 1.0e-15,
        "loosening the mass balance tolerance loosens the default internal flow tolerance");
    column.setMassBalanceTolerance(1.0e-8);
    assertEquals(1.0e-3, column.getInternalFlowStationarityTolerance(), 1.0e-15,
        "a very tight mass balance must not make the internal flow tolerance unattainable");
    assertThrows(IllegalArgumentException.class, () -> column.setInternalFlowStationarityTolerance(0.0));
    assertThrows(IllegalArgumentException.class, () -> column.setInternalFlowStationarityTolerance(Double.NaN));
    column.setInternalFlowStationarityTolerance(Double.POSITIVE_INFINITY);
    assertEquals(Double.POSITIVE_INFINITY, column.getInternalFlowStationarityTolerance(), 0.0);
  }
}
