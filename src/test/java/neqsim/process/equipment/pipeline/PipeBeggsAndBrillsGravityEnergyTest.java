package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemPrEos;

/**
 * Static-enthalpy and gravitational-potential balance in adiabatic risers and downcomers.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class PipeBeggsAndBrillsGravityEnergyTest {
  /** Gravity changes sign on descent and vanishes horizontally, independently of segmentation and electrical heat. */
  @Test
  void adiabaticWaterRetainsGravityAndElectricalEnergyBalance() {
    for (double elevation : new double[] {-2000.0, 0.0, 2000.0}) {
      for (int increments : new int[] {1, 20}) {
        for (double heating : new double[] {0.0, 100.0}) {
          SystemPrEos fluid = new SystemPrEos(353.15, 300.0);
          fluid.addComponent("water", 1.0);
          fluid.setMixingRule("classic");
          fluid.setTotalFlowRate(10.0, "kg/sec");
          Stream feed = new Stream("water", fluid);
          feed.run();
          PipeBeggsAndBrills pipe = pipe(feed, elevation, increments);
          pipe.setDirectElectricalHeatingPowerPerMeter(heating);
          pipe.run();
          double change = pipe.getOutletStream().getFluid().getEnthalpy("J/kg") - feed.getFluid().getEnthalpy("J/kg");
          assertEquals(heating * 2000.0 / 10.0 - 9.80665 * elevation, change, 0.02);
        }
      }
    }
  }

  /** A valid coarse-cell two-phase PH endpoint must not be replaced by the constant-Cp fallback. */
  @Test
  void flashingWellClosesEnergyOnCoarseAndRefinedMeshes() {
    SystemPrEos fluid = new SystemPrEos(353.15, 200.0);
    String[] names = {"nitrogen", "CO2", "methane", "ethane", "propane", "i-butane", "n-butane", "i-pentane",
        "n-pentane", "n-hexane", "n-heptane", "n-octane", "n-nonane", "nC10"};
    double[] amounts = {0.5, 1, 45, 5, 5, 2, 3, 2, 2, 5, 8, 10, 6, 5.5};
    for (int index = 0; index < names.length; index++) {
      fluid.addComponent(names[index], amounts[index]);
    }
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    fluid.setTotalFlowRate(20000.0, "kg/hr");
    Stream feed = new Stream("well feed", fluid);
    feed.run();
    for (int increments : new int[] {1, 20, 40}) {
      PipeBeggsAndBrills pipe = pipe(feed, 2000.0, increments);
      pipe.run();
      double change = pipe.getOutletStream().getFluid().getEnthalpy("J/kg") - feed.getFluid().getEnthalpy("J/kg");
      assertEquals(-9.80665 * 2000.0, change, 0.02);
    }
  }

  /**
   * Create an adiabatic line with prescribed elevation and mesh.
   *
   * @param feed inlet stream
   * @param elevation net elevation change in m
   * @param increments uniform segment count
   * @return unexecuted pipe
   */
  private static PipeBeggsAndBrills pipe(Stream feed, double elevation, int increments) {
    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("gravity", feed);
    pipe.setLength(2000.0);
    pipe.setElevation(elevation);
    pipe.setDiameter(0.1);
    pipe.setNumberOfIncrements(increments);
    pipe.setHeatTransferMode(PipeBeggsAndBrills.HeatTransferMode.ADIABATIC);
    return pipe;
  }
}
