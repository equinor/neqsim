package neqsim.process.equipment.pipeline;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Feed phase split of a steady solve with a prescribed outlet pressure.
 *
 * <p>
 * With a prescribed outlet pressure the inlet pressure is a result. The feed split used to be flashed at the nominal
 * pressure of the feed stream, so that pressure decided how much liquid the line carried. On a 30 km, 355 mm lean
 * gas-condensate line with 80 bara at the outlet the mean hold-up was 0.021, 0.020, 0.017 and 0.009 for feed stream
 * pressures of 90, 100, 114 and 140 bara, although the solved inlet pressure was 114 bara in every case.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
class TwoFluidPipeFeedSplitPressureTest {
  private static final int SECTIONS = 20;

  /**
   * Solves the lean gas-condensate line from a feed stream at the given nominal pressure.
   *
   * @param feedStreamPressure nominal pressure of the feed stream in bara
   * @return the solved pipe
   */
  private static TwoFluidPipe solve(double feedStreamPressure) {
    return solve(feedStreamPressure, false, SECTIONS);
  }

  /**
   * Solve the line using the selected source-free admission condition.
   *
   * @param feedStreamPressure stream pressure in bara
   * @param streamPhaseSplit whether the stream supplies the phase mass fractions
   * @param sections number of finite-volume cells
   * @return solved pipe
   */
  static TwoFluidPipe solve(double feedStreamPressure, boolean streamPhaseSplit, int sections) {
    SystemInterface fluid = new SystemSrkEos(273.15 + 40.0, feedStreamPressure);
    fluid.addComponent("methane", 95.0);
    fluid.addComponent("ethane", 3.0);
    fluid.addComponent("n-heptane", 2.0);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(69.44, "kg/sec");
    feed.setTemperature(40.0, "C");
    feed.setPressure(feedStreamPressure, "bara");
    feed.run();

    TwoFluidPipe pipe = new TwoFluidPipe("lean gas line", feed);
    pipe.setLength(30000.0);
    pipe.setDiameter(0.355);
    pipe.setRoughness(4.5e-5);
    pipe.setNumberOfSections(sections);
    pipe.setElevationProfile(new double[sections + 1]);
    pipe.setIncludeEnergyEquation(true);
    pipe.setHeatTransferCoefficient(2.0);
    pipe.setSurfaceTemperature(5.0, "C");
    pipe.setOutletPressure(80.0, "bara");
    pipe.setUseStreamPhaseSplit(streamPhaseSplit);
    pipe.setAnnularFlowingDensityFriction(true);
    pipe.run();
    Assertions.assertTrue(pipe.isSteadyStateConverged(), "the line must converge");
    return pipe;
  }

  /**
   * Mean of a profile.
   *
   * @param profile values per section
   * @return arithmetic mean
   */
  private static double mean(double[] profile) {
    double sum = 0.0;
    for (double value : profile) {
      sum += value;
    }
    return sum / profile.length;
  }

  /** The solution does not depend on the nominal pressure of the feed stream. */
  @Test
  void solutionIsIndependentOfTheFeedStreamPressure() {
    TwoFluidPipe low = solve(90.0);
    TwoFluidPipe high = solve(140.0);
    double inletPressure = low.getPressureProfile()[0] / 1.0e5;
    Assertions.assertTrue(inletPressure > 100.0 && inletPressure < 130.0, "inlet pressure " + inletPressure);
    Assertions.assertEquals(inletPressure, high.getPressureProfile()[0] / 1.0e5, 0.01);
    Assertions.assertEquals(low.getGasMassFlowProfile()[0], high.getGasMassFlowProfile()[0], 1.0e-3);
    Assertions.assertEquals(mean(low.getLiquidHoldupProfile()), mean(high.getLiquidHoldupProfile()), 1.0e-5);
  }

  /** A transient continued from the steady solution admits the feed split that solution was solved with. */
  @Test
  void transientAdmitsTheSteadyFeedSplit() {
    TwoFluidPipe pipe = solve(140.0);
    double steadyGasFlow = pipe.getGasMassFlowProfile()[0];
    // The feed stream flashed at its own 140 bara carries 67.27 kg/s of gas, the solved inlet 65.4 kg/s.
    Assertions.assertTrue(steadyGasFlow < 66.5, "the steady split belongs to the solved inlet pressure");
    for (int step = 0; step < 5; step++) {
      pipe.runTransient(0.1, null);
    }
    Assertions.assertEquals(steadyGasFlow, pipe.getGasMassFlowProfile()[0], 1.0e-3 * steadyGasFlow);
  }

  /** A metered inlet admits the upstream phase rates at steady state and after a change in total flow. */
  @Test
  void streamPhaseSplitPreservesSuppliedPhaseRates() {
    TwoFluidPipe pipe = solve(140.0, true, SECTIONS);
    Stream feed = (Stream) pipe.getInletStream();
    double streamGas = feed.getFluid().getPhase("gas").getFlowRate("kg/sec");
    Assertions.assertEquals(streamGas, pipe.getGasMassFlowProfile()[0], 1.0e-6);
    feed.setFlowRate(50.0, "kg/sec");
    feed.run();
    pipe.runTransient(0.01, null);
    Assertions.assertEquals(feed.getFluid().getPhase("gas").getFlowRate("kg/sec"), pipe.getGasMassFlowProfile()[0],
        1.0e-6);
    Assertions.assertEquals(feed.getFluid().getPhase("oil").getFlowRate("kg/sec"), pipe.getOilMassFlowProfile()[0],
        1.0e-6);
  }

  /** Round-off in an upstream reflash must not switch the admitted gas back to its nominal-pressure value. */
  @Test
  void upstreamReflashPreservesSplit() {
    TwoFluidPipe pipe = solve(140.0);
    double steadyGas = pipe.getGasMassFlowProfile()[0];
    pipe.runTransient(0.01, null);
    Stream feed = (Stream) pipe.getInletStream();
    feed.setTemperature(feed.getTemperature("K") + 1.0e-9, "K");
    feed.setPressure(Math.nextUp(feed.getPressure("bara")), "bara");
    feed.run();
    pipe.runTransient(0.01, null);
    Assertions.assertEquals(steadyGas, pipe.getGasMassFlowProfile()[0], 0.01);
  }

  /** A rate-only step moves the cell pressure. The admitted gas follows an independent flash at that pressure. */
  @Test
  void movingInletPressureUpdatesAdmittedGas() {
    TwoFluidPipe pipe = solve(140.0);
    Stream feed = (Stream) pipe.getInletStream();
    double frozenFraction = pipe.getGasMassFlowProfile()[0] / feed.getFlowRate("kg/sec");
    feed.setFlowRate(50.0, "kg/sec");
    feed.run();
    pipe.runTransient(0.1, null);
    double cellPressure = pipe.getPressureProfile()[0];
    SystemInterface expected = feed.getFluid().clone();
    expected.setPressure(cellPressure, "Pa");
    new ThermodynamicOperations(expected).TPflash();
    double gas = expected.getPhase("gas").getFlowRate("kg/sec");
    Assertions.assertTrue(Math.abs(gas - frozenFraction * 50.0) > 0.02, "pressure must change the split measurably");
    pipe.runTransient(1.0e-5, null);
    Assertions.assertEquals(gas, pipe.getGasMassFlowProfile()[0], 0.01);
    Assertions.assertEquals(140.0, feed.getPressure("bara"), 1.0e-10);
  }
}
