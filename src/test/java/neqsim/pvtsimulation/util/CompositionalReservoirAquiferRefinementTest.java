package neqsim.pvtsimulation.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.pvtsimulation.reservoirproperties.materialbalance.FetkovichAquifer;
import neqsim.pvtsimulation.util.CompositionalReservoirHistory.Result;
import neqsim.pvtsimulation.util.CompositionalReservoirHistory.Step;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Independent dilute-gas/aquifer ODE comparison and interval-refinement evidence for the coupled replay.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class CompositionalReservoirAquiferRefinementTest {
  private static final double TEMPERATURE_K = 600.0;
  private static final double INITIAL_PRESSURE_BARA = 0.2;
  private static final double VOLUME_M3 = 1000.0;
  private static final double DURATION_SECONDS = 10000.0;
  private static final double PRODUCED_FRACTION = 0.5;

  /**
   * Defines a dilute pure-methane EOS without tuning reservoir-history observations.
   *
   * @return initialized methane fluid
   */
  private SystemInterface sample() {
    SystemInterface fluid = new SystemPrEos(TEMPERATURE_K, INITIAL_PRESSURE_BARA);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(1);
    return fluid;
  }

  /**
   * Builds a constant-molar-rate depletion schedule split into equal intervals.
   *
   * @param intervals number of measured intervals
   * @param initialMoles independently evaluated EOS inventory in the initial tank
   * @return coupled replay result with physical finite-aquifer support
   */
  private Result replay(int intervals, double initialMoles) {
    SystemInterface sample = sample();
    CalibratedPvtModel model = new CalibratedPvtModel(sample, sample, "public synthetic dilute-methane aquifer fixture",
        "PR/classic original", "PR/classic unchanged", "analytical ideal-gas limiting case; no field calibration",
        new String[] {"synthetic-CCE"});
    Step[] steps = new Step[intervals];
    for (int i = 0; i < intervals; i++) {
      steps[i] = new Step(DURATION_SECONDS / intervals, new double[] {PRODUCED_FRACTION * initialMoles / intervals},
          new double[1], Double.NaN, Double.NaN, false);
    }
    CompositionalReservoirHistory history = new CompositionalReservoirHistory(model,
        "constant methane withdrawal in mol per interval; independently specified duration and initial volume",
        new String[] {"methane"}, TEMPERATURE_K, INITIAL_PRESSURE_BARA, new double[] {1.0, 0.0, 0.0}, steps);
    double capacity = VOLUME_M3 / INITIAL_PRESSURE_BARA;
    FetkovichAquifer aquifer = new FetkovichAquifer(1.0e8, capacity / 1.0e8, capacity / DURATION_SECONDS,
        INITIAL_PRESSURE_BARA);
    return history.replay(VOLUME_M3, aquifer);
  }

  /**
   * Returns the independent ideal-gas dimensionless water-influx ODE derivative.
   *
   * <p>
   * For u=t/T and w=We/V, the independently specified ratios are C*pi/V=1 and J*T*pi/V=1. With measured constant molar
   * production n/n0=1-f*u, the equations reduce to pa/pi=1-w, p/pi=(1-f*u)/(1-w), and dw/du=pa/pi-p/pi. This reference
   * does not call the production aquifer integrator, TV flash or coupled pressure solver.
   * </p>
   *
   * @param dimensionlessTime elapsed fraction of the production interval
   * @param waterFraction cumulative aquifer influx divided by pore volume
   * @return dimensionless influx derivative
   */
  private double derivative(double dimensionlessTime, double waterFraction) {
    return 1.0 - waterFraction - (1.0 - PRODUCED_FRACTION * dimensionlessTime) / (1.0 - waterFraction);
  }

  /**
   * Integrates the independent ideal-gas/aquifer initial-value problem with fourth-order Runge-Kutta.
   *
   * @param subdivisions independent reference integration resolution
   * @return final dimensionless cumulative water influx
   */
  private double referenceWaterFraction(int subdivisions) {
    double water = 0.0;
    double h = 1.0 / subdivisions;
    for (int i = 0; i < subdivisions; i++) {
      double time = i * h;
      double k1 = derivative(time, water);
      double k2 = derivative(time + 0.5 * h, water + 0.5 * h * k1);
      double k3 = derivative(time + 0.5 * h, water + 0.5 * h * k2);
      double k4 = derivative(time + h, water + h * k3);
      water += h * (k1 + 2.0 * k2 + 2.0 * k3 + k4) / 6.0;
    }
    return water;
  }

  /**
   * Checks retained component inventory, fixed total volume and independent aquifer storage conservation.
   *
   * @param result accepted coupled trajectory
   * @param initialMoles independently evaluated initial methane inventory
   */
  private void assertConservation(Result result, double initialMoles) {
    double[][] moles = result.getComponentMoles();
    double[] influx = result.getAquiferInfluxM3();
    double[] aquiferPressure = result.getAquiferPressuresBara();
    double[] times = result.getTimesSeconds();
    int intervals = moles.length - 1;
    for (int i = 0; i <= intervals; i++) {
      double fraction = (double) i / intervals;
      Assertions.assertEquals(initialMoles * (1.0 - PRODUCED_FRACTION * fraction), moles[i][0], initialMoles * 1.0e-8);
      Assertions.assertEquals(INITIAL_PRESSURE_BARA * (1.0 - influx[i] / VOLUME_M3), aquiferPressure[i], 1.0e-10);
      Assertions.assertEquals(DURATION_SECONDS * fraction, times[i], DURATION_SECONDS * 1.0e-12);
      Assertions.assertTrue(influx[i] >= 0.0 && influx[i] < VOLUME_M3);
    }
    SystemInterface finalFluid = result.getFinalFluid();
    finalFluid.init(1);
    Assertions.assertEquals(VOLUME_M3, finalFluid.getVolume("m3") + influx[intervals], VOLUME_M3 * 1.0e-7);
    Assertions.assertEquals(1, finalFluid.getNumberOfComponents());
    Assertions.assertEquals("methane", finalFluid.getComponentNames()[0]);
  }

  /** Checks pressure-path refinement and the independent ideal-gas ODE limit while conserving both inventories. */
  @Test
  void coupledReplayRefinesTowardIndependentDiluteGasAquiferOde() {
    SystemInterface sample = sample();
    double initialMoles = sample.getTotalNumberOfMoles() * VOLUME_M3 / sample.getVolume("m3");
    double waterReference = referenceWaterFraction(10000);
    Assertions.assertEquals(waterReference, referenceWaterFraction(20000), 1.0e-12);
    double referencePressure = INITIAL_PRESSURE_BARA * (1.0 - PRODUCED_FRACTION) / (1.0 - waterReference);
    double[] pressure = new double[5];
    Result finest = null;
    for (int i = 0; i < pressure.length; i++) {
      int intervals = 1 << i;
      Result result = replay(intervals, initialMoles);
      assertConservation(result, initialMoles);
      pressure[i] = result.getPressuresBara()[intervals];
      finest = result;
    }
    double coarseChange = Math.abs(pressure[1] - pressure[0]);
    double mediumChange = Math.abs(pressure[2] - pressure[1]);
    double fineChange = Math.abs(pressure[3] - pressure[2]);
    double finestChange = Math.abs(pressure[4] - pressure[3]);
    Assertions.assertTrue(mediumChange < 0.4 * coarseChange);
    Assertions.assertTrue(fineChange < 0.4 * mediumChange);
    Assertions.assertTrue(finestChange < 0.5 * fineChange);
    // PR has a finite dilute-gas deviation from Z=1; the independent ideal reference is a limiting check.
    Assertions.assertEquals(referencePressure, pressure[4], referencePressure * 5.0e-5);
    Assertions.assertEquals(VOLUME_M3 * waterReference, finest.getAquiferInfluxM3()[16], 0.01);
    Assertions.assertTrue(Math.abs(pressure[4] - referencePressure) < Math.abs(pressure[0] - referencePressure));
  }
}
