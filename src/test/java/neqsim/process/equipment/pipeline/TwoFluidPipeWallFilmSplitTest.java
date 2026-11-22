package neqsim.process.equipment.pipeline;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Wall thermal inertia in the simple transient thermal model of {@link TwoFluidPipe}.
 *
 * <p>
 * The configured overall U is split around the wall node. With the physical split (flow-dependent fluid film, remaining
 * resistance outside the wall) the wall discharges to ambient through the small outer coefficient and the gas follows
 * the wall, so the outlet temperature after a rate reduction responds over the wall time constant (hours for a 20 mm
 * steel wall at 5 W/m2K). The legacy equal split discharged the wall four times faster. OLGA with the same wall gave
 * 15.5 degC at 3 h where NeqSim with the equal split gave 12.5 degC on a 20 km gas line; a 0.5 mm OLGA wall reproduced
 * the fast response.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class TwoFluidPipeWallFilmSplitTest {

  private static final int SECTIONS = 40;
  private static final double LENGTH = 5000.0;
  private static final double DIAMETER = 0.40;

  /**
   * Build and solve a dry-gas line with a 20 mm steel wall and an overall U of 5 W/m2K to 4 degC.
   *
   * @param physicalSplit true for the flow-dependent film split
   * @return pipe after the steady solve
   */
  private static TwoFluidPipe buildGasPipe(boolean physicalSplit) {
    SystemInterface fluid = new SystemSrkEos(273.15 + 40.0, 90.0);
    fluid.addComponent("nitrogen", 1.0);
    fluid.addComponent("CO2", 1.5);
    fluid.addComponent("methane", 91.0);
    fluid.addComponent("ethane", 5.0);
    fluid.addComponent("propane", 1.5);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(60.0, "kg/sec");
    feed.setTemperature(40.0, "C");
    feed.setPressure(90.0, "bara");
    feed.run();

    TwoFluidPipe pipe = new TwoFluidPipe("pipe", feed);
    pipe.setLength(LENGTH);
    pipe.setDiameter(DIAMETER);
    pipe.setNumberOfSections(SECTIONS);
    pipe.setElevationProfile(new double[SECTIONS]);
    pipe.setIncludeEnergyEquation(true);
    pipe.setHeatTransferCoefficient(5.0);
    pipe.setSurfaceTemperature(4.0, "C");
    pipe.setOutletPressure(80.0, "bara");
    pipe.setPhysicalWallFilmSplit(physicalSplit);
    pipe.run();
    return pipe;
  }

  private static double outletTemperatureC(TwoFluidPipe pipe) {
    double[] profile = pipe.getTemperatureProfile();
    return profile[profile.length - 1] - 273.15;
  }

  @Test
  void testSteadyTemperatureIsAFixedPointWithBothSplits() {
    for (boolean physical : new boolean[] {true, false}) {
      TwoFluidPipe pipe = buildGasPipe(physical);
      double before = outletTemperatureC(pipe);
      for (int i = 0; i < 30; i++) {
        pipe.runTransient(60.0, null);
      }
      double after = outletTemperatureC(pipe);
      Assertions.assertEquals(before, after, 0.5, "outlet temperature drifted at constant boundary conditions with "
          + (physical ? "the physical" : "the equal") + " film split");
    }
  }

  @Test
  void testPhysicalSplitSlowsTheOutletTemperatureResponseToARateReduction() {
    TwoFluidPipe physical = buildGasPipe(true);
    TwoFluidPipe equal = buildGasPipe(false);
    double startPhysical = outletTemperatureC(physical);
    double startEqual = outletTemperatureC(equal);
    Assertions.assertEquals(startPhysical, startEqual, 0.2, "the steady solve does not depend on the split");

    for (TwoFluidPipe pipe : new TwoFluidPipe[] {physical, equal}) {
      pipe.getInletStream().setFlowRate(30.0, "kg/sec");
      pipe.getInletStream().run();
      for (int i = 0; i < 60; i++) {
        pipe.runTransient(60.0, null);
      }
    }
    double dropPhysical = startPhysical - outletTemperatureC(physical);
    double dropEqual = startEqual - outletTemperatureC(equal);
    Assertions.assertTrue(dropEqual > 0.5, "halving the rate must cool the outlet; equal split dropped " + dropEqual);
    Assertions.assertTrue(dropPhysical < 0.6 * dropEqual,
        "after 1 h the physical film split must hold the outlet"
            + " temperature closer to its initial value than the equal split: " + dropPhysical + " against " + dropEqual
            + " K");
    Assertions.assertTrue(dropPhysical > 0.0, "the physical split must still cool: " + dropPhysical);
  }
}
