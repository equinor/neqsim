package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression tests for the steady-state pressure floor in {@link TwoFluidPipe}.
 *
 * <p>
 * The marching solver clamps every section at 1 bara to stay numerically alive. A profile resting on that clamp is a
 * fixed point of the clamp, not of the momentum balance, so the per-section change falls below tolerance and the solver
 * used to report success on a line that cannot deliver the specified rate. These tests pin the reporting behaviour.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class TwoFluidPipePressureFloorTest {

  /**
   * Build a wet-gas feed stream.
   *
   * @param massFlowKgPerHour mass flow in kg/hr
   * @return a run stream at 100 bara and 40 degrees Celsius
   */
  private Stream makeStream(double massFlowKgPerHour) {
    SystemInterface fluid = new SystemSrkEos(273.15 + 40.0, 100.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("n-heptane", 0.05);
    fluid.setMixingRule("classic");

    Stream stream = new Stream("feed", fluid);
    stream.setFlowRate(massFlowKgPerHour, "kg/hr");
    stream.setPressure(100.0, "bara");
    stream.setTemperature(40.0, "C");
    stream.run();
    return stream;
  }

  /**
   * Build a horizontal pipe carrying the requested rate.
   *
   * @param lengthMeter pipe length in metres
   * @param diameterMeter inner diameter in metres
   * @param massFlowKgPerHour mass flow in kg/hr
   * @return a configured pipe that has not yet been run
   */
  private TwoFluidPipe makePipe(double lengthMeter, double diameterMeter, double massFlowKgPerHour) {
    int sections = 40;
    TwoFluidPipe pipe = new TwoFluidPipe("pipe", makeStream(massFlowKgPerHour));
    pipe.setLength(lengthMeter);
    pipe.setDiameter(diameterMeter);
    pipe.setRoughness(4.5e-5);
    pipe.setNumberOfSections(sections);
    pipe.setElevationProfile(new double[sections + 1]);
    pipe.setEnableTerrainTracking(false);
    return pipe;
  }

  /** A line with ample deliverability must converge without touching the floor. */
  @Test
  void testDeliverableLineIsNotFloorLimited() {
    TwoFluidPipe pipe = makePipe(5000.0, 0.4, 100000.0);
    pipe.run();

    assertFalse(pipe.isSteadyStatePressureFloorLimited(), "A short, wide line must not rest on the pressure floor");
    assertTrue(pipe.getPressureProfile()[pipe.getPressureProfile().length - 1] > 1.5e5,
        "Arrival pressure must stay well above the floor");
  }

  /**
   * A line far beyond its deliverability must report the floor rather than convergence.
   *
   * <p>
   * A long, narrow line at a high rate cannot reach the outlet at any positive pressure. The solver clamps at 1 bara;
   * what it must not do is call that a converged solution.
   * </p>
   */
  @Test
  void testUndeliverableLineIsReportedNotConverged() {
    TwoFluidPipe pipe = makePipe(200000.0, 0.1, 200000.0);
    pipe.run();

    double arrival = pipe.getPressureProfile()[pipe.getPressureProfile().length - 1];
    assertTrue(arrival <= 1.0e5 * (1.0 + 1.0e-9),
        "The test case must actually reach the floor, got " + arrival / 1.0e5 + " bara");
    assertTrue(pipe.isSteadyStatePressureFloorLimited(), "A profile resting on the pressure floor must be flagged");
    assertFalse(pipe.isSteadyStateConverged(),
        "A profile resting on the pressure floor must not be reported as converged");
  }
}
