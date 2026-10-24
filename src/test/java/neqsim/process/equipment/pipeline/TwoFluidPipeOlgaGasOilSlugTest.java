package neqsim.process.equipment.pipeline;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Steady slug-flow holdup and pressure drop of a horizontal gas-oil line against OLGA 2025.1.0.
 *
 * <p>
 * Reference: 5 km, 10 in, 4.5e-5 m roughness, 50 C feed, 30 bara outlet, U = 5 W/m2K on the outer wall, 4 C ambient,
 * SRK gas-oil of 60/5/3 mol methane/ethane/propane with 20/12 heptane/n-decane. OLGA holds 0.239 (25 kg/s) and 0.206
 * (50 kg/s) of liquid with 4.14 and 13.76 bar pressure drop. Before the high-Froude slip reduction the holdup was 0.259
 * and 0.271.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
class TwoFluidPipeOlgaGasOilSlugTest {
  private static final int SECTIONS = 50;
  private static final double LENGTH = 5000.0;
  private static final double DIAMETER = 0.25;
  private static final double OUTLET_PRESSURE = 30.0;

  private static TwoFluidPipe solve(double massFlow, double inletPressure, boolean slipReduction) {
    SystemInterface fluid = new SystemSrkEos(273.15 + 50.0, inletPressure);
    fluid.addComponent("methane", 60.0);
    fluid.addComponent("ethane", 5.0);
    fluid.addComponent("propane", 3.0);
    fluid.addComponent("n-heptane", 20.0);
    fluid.addComponent("nC10", 12.0);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(massFlow, "kg/sec");
    feed.setTemperature(50.0, "C");
    feed.setPressure(inletPressure, "bara");
    feed.run();

    TwoFluidPipe pipe = new TwoFluidPipe("pipe", feed);
    pipe.setLength(LENGTH);
    pipe.setDiameter(DIAMETER);
    pipe.setRoughness(4.5e-5);
    pipe.setNumberOfSections(SECTIONS);
    pipe.setElevationProfile(new double[SECTIONS]);
    pipe.setHeatTransferCoefficient(5.0 * (DIAMETER + 0.04) / DIAMETER);
    pipe.setSurfaceTemperature(4.0, "C");
    pipe.setOutletPressure(OUTLET_PRESSURE, "bara");
    pipe.setSlugHighFroudeSlipReductionEnabled(slipReduction);
    pipe.run();
    return pipe;
  }

  private static double meanHoldup(TwoFluidPipe pipe) {
    double[] holdup = pipe.getLiquidHoldupProfile();
    double sum = 0.0;
    for (double value : holdup) {
      sum += value;
    }
    return sum / holdup.length;
  }

  private static double pressureDropBar(TwoFluidPipe pipe) {
    return pipe.getPressureProfile()[0] / 1.0e5 - OUTLET_PRESSURE;
  }

  @Test
  void midRateHoldupAndPressureDropFollowOlga() {
    TwoFluidPipe pipe = solve(25.0, 34.1, true);
    Assertions.assertEquals(0.239, meanHoldup(pipe), 0.03);
    Assertions.assertEquals(4.14, pressureDropBar(pipe), 0.9);
  }

  @Test
  void highRateHoldupFollowsOlgaOnlyWithSlipReduction() {
    TwoFluidPipe reduced = solve(50.0, 44.0, true);
    TwoFluidPipe bendiksen = solve(50.0, 44.0, false);
    Assertions.assertEquals(0.206, meanHoldup(reduced), 0.03);
    Assertions.assertTrue(meanHoldup(bendiksen) > meanHoldup(reduced) + 0.02,
        "plain Bendiksen coefficients over-predict the high-rate holdup");
    Assertions.assertEquals(13.76, pressureDropBar(reduced), 1.5);
  }
}
