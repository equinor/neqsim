package neqsim.pvtsimulation.util;

import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.reservoir.SimpleReservoir;
import neqsim.pvtsimulation.reservoirproperties.materialbalance.FetkovichAquifer;
import neqsim.pvtsimulation.util.CompositionalReservoirHistory.Result;
import neqsim.pvtsimulation.util.CompositionalReservoirHistory.Step;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;

/**
 * Physical aquifer/tank coupling, conserved inventories and implicit pressure closure.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class CompositionalReservoirAquiferTest {
  /**
   * Creates a methane laboratory checkpoint fixture, not a measured fluid calibration.
   *
   * @return immutable gas EOS
   */
  private CalibratedPvtModel model() {
    SystemInterface fluid = new SystemPrEos(400.0, 200.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    fluid.init(0);
    return new CalibratedPvtModel(fluid, fluid, "synthetic methane", "PR original", "PR accepted",
        "synthetic independent EOS fixture", new String[] {"CCE-fixture"});
  }

  /**
   * Builds measured depletion intervals with optionally observed end pressures.
   *
   * @param pressure optional observed end pressures, NaN for prediction only
   * @return gas-only tank history
   */
  private CompositionalReservoirHistory history(double... pressure) {
    Step[] steps = new Step[pressure.length];
    for (int i = 0; i < steps.length; i++) {
      steps[i] = new Step(86400.0, new double[] {200000.0 + i * 50000.0}, new double[1], pressure[i],
          Double.isNaN(pressure[i]) ? Double.NaN : 0.5, false);
    }
    return new CompositionalReservoirHistory(model(), "synthetic measured withdrawals", new String[] {"methane"}, 400.0,
        200.0, new double[] {1.0, 0.0, 0.0}, steps);
  }

  /** Checks separate water storage, EOS volume closure, aquifer depletion and unchanged tracked components. */
  @Test
  void coupledReplayClosesVolumesAndBothInventories() {
    CompositionalReservoirHistory history = history(Double.NaN, Double.NaN, Double.NaN);
    FetkovichAquifer aquifer = new FetkovichAquifer(100000.0, 0.00005, 0.00001, 200.0);
    Result coupled = history.replay(1000.0, aquifer);
    Result unsupported = history.replay(1000.0);
    double[] influx = coupled.getAquiferInfluxM3();
    double[] aqPressure = coupled.getAquiferPressuresBara();
    for (int i = 1; i < influx.length; i++) {
      Assertions.assertTrue(influx[i] > influx[i - 1]);
      Assertions.assertEquals(200.0 - influx[i] / 5.0, aqPressure[i], 1.0e-9);
      Assertions.assertTrue(coupled.getPressuresBara()[i] > unsupported.getPressuresBara()[i]);
      Assertions.assertArrayEquals(unsupported.getComponentMoles()[i], coupled.getComponentMoles()[i], 1.0e-6);
    }
    Assertions.assertEquals(1000.0, coupled.getFinalFluid().getVolume("m3") + influx[3], 0.0001);
    Assertions.assertArrayEquals(coupled.getPressuresBara(), history.replay(1000.0, aquifer).getPressuresBara(),
        1.0e-10);
    influx[1] = -1.0;
    Assertions.assertTrue(coupled.getAquiferInfluxM3()[1] > 0.0);
  }

  /** Checks exactly unchanged no-support limits and informative volume recovery with a known physical aquifer. */
  @Test
  void noSupportLimitsAndBoundedVolumeFit() {
    CompositionalReservoirHistory prediction = history(Double.NaN, Double.NaN);
    Result baseline = prediction.replay(1017.3);
    Assertions.assertArrayEquals(baseline.getPressuresBara(),
        prediction.replay(1017.3, new FetkovichAquifer(10000.0, 0.001, 0.0, 200.0)).getPressuresBara(), 0.0);
    Assertions.assertArrayEquals(baseline.getPressuresBara(),
        prediction.replay(1017.3, new FetkovichAquifer(10000.0, 0.0, 0.01, 200.0)).getPressuresBara(), 0.0);
    FetkovichAquifer aquifer = new FetkovichAquifer(100000.0, 0.00005, 0.00001, 200.0);
    double[] pressures = prediction.replay(1017.3, aquifer).getPressuresBara();
    CompositionalReservoirHistory observed = history(pressures[1], pressures[2]);
    Assertions.assertEquals(1017.3, observed.fitVolume(900.0, 1200.0, aquifer).getResult().getInitialVolumeM3(), 0.003);
  }

  /** Invalid support never commits time, inventory, pressure or identity; fixed support persists on later intervals. */
  @Test
  void explicitTankSupportIsTransactionalAndRetained() {
    SimpleReservoir tank = new SimpleReservoir("segregated support");
    tank.setRecordProductionHistory(false);
    tank.setReservoirFluid(model().getTunedFluid(), 1000.0, 0.0, 0.0);
    UUID id = UUID.randomUUID();
    tank.advanceComponentAmounts(1.0, new double[1], new double[1], 10.0, id);
    double pressure = tank.getReservoirFluid().getPressure();
    Assertions.assertEquals(990.0, tank.getReservoirFluid().getVolume("m3"), 0.0001);
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> tank.advanceComponentAmounts(1.0, new double[1], new double[1], 1000.0, UUID.randomUUID()));
    Assertions.assertEquals(1.0, tank.getTime());
    Assertions.assertEquals(id, tank.getCalculationIdentifier());
    Assertions.assertEquals(pressure, tank.getReservoirFluid().getPressure());
    tank.advanceComponentAmounts(1.0, new double[1], new double[1], UUID.randomUUID());
    Assertions.assertEquals(990.0, tank.getReservoirFluid().getVolume("m3"), 0.0001);
  }

  /** Checks rejection instead of drawing water from nonexistent segregated support during initial repressurization. */
  @Test
  void rejectsUnsupportedInitialAquiferRecharge() {
    Step inject = new Step(86400.0, new double[1], new double[] {500000.0}, Double.NaN, Double.NaN, false);
    CompositionalReservoirHistory history = new CompositionalReservoirHistory(model(), "synthetic repressurization",
        new String[] {"methane"}, 400.0, 200.0, new double[] {1.0, 0.0, 0.0}, new Step[] {inject});
    Assertions.assertThrows(IllegalStateException.class,
        () -> history.replay(1000.0, new FetkovichAquifer(100000.0, 0.00005, 0.00001, 200.0)));
  }

  /** An exhausted low-pressure root probe must not reject an otherwise admissible coupled interval. */
  @Test
  void infeasibleBracketProbeDoesNotRejectFeasibleInterval() {
    Step small = new Step(86400.0, new double[] {1000.0}, new double[1], Double.NaN, Double.NaN, false);
    CompositionalReservoirHistory h = new CompositionalReservoirHistory(model(), "tight aquifer inventory fixture",
        new String[] {"methane"}, 400.0, 200.0, new double[] {1.0, 0.0, 0.0}, new Step[] {small});
    FetkovichAquifer aquifer = new FetkovichAquifer(10.0, 1.0, 0.00001, 200.0);
    Assertions.assertThrows(FetkovichAquifer.InventoryExhaustedException.class,
        () -> aquifer.advance(aquifer.initialState(), 86400.0, 200.0, 0.000001));
    Result result = h.replay(1000.0, aquifer);
    Assertions.assertTrue(result.getAquiferInfluxM3()[1] > 0.0 && result.getAquiferInfluxM3()[1] < 1.0);
    Assertions.assertTrue(result.getPressuresBara()[1] > 199.0);
  }

  /** A positive endpoint cannot conceal withdrawal from nonexistent segregated water early in an interval. */
  @Test
  void rejectsInteriorNegativeSegregatedWater() {
    Step deplete = new Step(86400.0, new double[] {1000000.0}, new double[1], Double.NaN, Double.NaN, false);
    CompositionalReservoirHistory h = new CompositionalReservoirHistory(model(), "interior recharge fixture",
        new String[] {"methane"}, 400.0, 200.0, new double[] {1.0, 0.0, 0.0}, new Step[] {deplete});
    Assertions.assertThrows(IllegalStateException.class,
        () -> h.replay(1000.0, new FetkovichAquifer(100000.0, 0.00005, 0.00001, 190.0)));
  }

  /** Endpoint volume closure cannot conceal an interior interval that fills all tracked pore space. */
  @Test
  void rejectsInteriorPoreSpaceExhaustion() {
    Step deplete = new Step(86400.0, new double[] {4000000.0}, new double[1], Double.NaN, Double.NaN, false);
    CompositionalReservoirHistory h = new CompositionalReservoirHistory(model(), "interior fill fixture",
        new String[] {"methane"}, 400.0, 200.0, new double[] {1.0, 0.0, 0.0}, new Step[] {deplete});
    Assertions.assertThrows(IllegalStateException.class,
        () -> h.replay(1000.0, new FetkovichAquifer(Double.POSITIVE_INFINITY, 0.00005, 0.01, 210.0)));
  }

}
