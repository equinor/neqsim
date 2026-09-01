package neqsim.process.equipment.pipeline;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression tests for the steady-state convergence criterion of {@link TwoFluidPipe}.
 *
 * <p>
 * The refinement loop used to test only the per-section pressure change. That quantity is proportional to the section
 * length, so on any reasonably fine mesh it falls below the tolerance after a single sweep and convergence was declared
 * while the profile still carried the densities the sections were initialised with. On a gas line the density falls by
 * tens of per cent between inlet and outlet, and it is exactly that change which makes the pressure gradient steepen
 * towards the outlet, so the reported pressure drop was wrong by roughly ten per cent and the error did not shrink
 * under mesh refinement.
 * </p>
 *
 * <p>
 * The tests below use a single-phase gas line, where the momentum balance reduces to Darcy-Weisbach and an independent
 * reference can be integrated in the test itself.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
class TwoFluidPipeGasDensityCouplingTest {
  /** Inside diameter, in m. */
  private static final double DIAMETER = 0.30;

  /** Pipe length, in m. Long enough that the gas expands appreciably. */
  private static final double LENGTH = 30000.0;

  /** Wall roughness, in m. */
  private static final double ROUGHNESS = 4.5e-5;

  /** Inlet pressure, in bara. */
  private static final double INLET_PRESSURE = 150.0;

  /** Inlet temperature, in degrees Celsius. */
  private static final double INLET_TEMPERATURE = 30.0;

  /** Mass flow, in kg/hr. Chosen to give roughly a third of the inlet pressure as drop. */
  private static final double MASS_FLOW = 200000.0;

  /**
   * Builds a lean single-phase gas at the requested state.
   *
   * @param pressureBara pressure in bara, must be positive
   * @param temperatureC temperature in degrees Celsius
   * @return a flashed fluid with its physical properties initialised
   */
  private SystemInterface buildFluid(double pressureBara, double temperatureC) {
    SystemInterface fluid = new SystemSrkEos(273.15 + temperatureC, pressureBara);
    fluid.addComponent("methane", 0.92);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("propane", 0.03);
    fluid.setMixingRule("classic");
    fluid.setPressure(pressureBara, "bara");
    fluid.setTemperature(temperatureC, "C");
    fluid.setTotalFlowRate(MASS_FLOW, "kg/hr");
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    return fluid;
  }

  /**
   * Runs a horizontal isothermal gas line with the requested number of sections.
   *
   * @param sections number of finite volumes, must be positive
   * @return the pipe after it has been run
   */
  private TwoFluidPipe runPipe(int sections) {
    Stream stream = new Stream("feed", buildFluid(INLET_PRESSURE, INLET_TEMPERATURE));
    stream.setFlowRate(MASS_FLOW, "kg/hr");
    stream.setPressure(INLET_PRESSURE, "bara");
    stream.setTemperature(INLET_TEMPERATURE, "C");
    stream.run();

    TwoFluidPipe pipe = new TwoFluidPipe("gasline", stream);
    pipe.setLength(LENGTH);
    pipe.setDiameter(DIAMETER);
    pipe.setRoughness(ROUGHNESS);
    pipe.setNumberOfSections(sections);
    double[] elevation = new double[sections + 1];
    pipe.setElevationProfile(elevation);
    pipe.setIncludeEnergyEquation(false);
    pipe.setEnableTerrainTracking(false);
    pipe.run();
    return pipe;
  }

  /**
   * Pressure drop of the pipe, in bar.
   *
   * @param pipe a pipe that has been run, must not be null
   * @return inlet minus outlet pressure in bar
   */
  private double pressureDrop(TwoFluidPipe pipe) {
    double[] profile = pipe.getPressureProfile();
    return (profile[0] - profile[profile.length - 1]) / 1.0e5;
  }

  /**
   * Darcy-Weisbach reference, integrated isothermally with a flash in every step.
   *
   * @param steps number of integration steps, must be positive
   * @return pressure drop in bar
   */
  private double darcyReference(int steps) {
    double pressure = INLET_PRESSURE;
    double area = Math.PI / 4.0 * DIAMETER * DIAMETER;
    double massFlowPerSecond = MASS_FLOW / 3600.0;
    for (int i = 0; i < steps; i++) {
      SystemInterface fluid = buildFluid(pressure, INLET_TEMPERATURE);
      double density = fluid.getDensity("kg/m3");
      double viscosity = fluid.getViscosity("kg/msec");
      double velocity = massFlowPerSecond / (density * area);
      double reynolds = density * velocity * DIAMETER / viscosity;
      double friction = Math.pow(1.0 / (-1.8 * Math.log10(Math.pow(ROUGHNESS / DIAMETER / 3.7, 1.11) + 6.9 / reynolds)),
          2.0);
      pressure -= friction * (LENGTH / steps) / DIAMETER * density * velocity * velocity / 2.0 / 1.0e5;
    }
    return INLET_PRESSURE - pressure;
  }

  @Test
  @DisplayName("Steady-state solve does not stop before the densities have fed back")
  void testSolverDoesNotConvergeOnTheFirstSweep() {
    TwoFluidPipe pipe = runPipe(80);
    Assertions.assertTrue(pipe.isSteadyStateConverged(), "the case must converge within the default budget");
    // The old criterion reported convergence after a single sweep, on the densities
    // the sections were initialised with.
    Assertions.assertTrue(pipe.getSteadyStateIterationsUsed() > 5,
        "convergence was declared after " + pipe.getSteadyStateIterationsUsed()
            + " iterations, before the flash could feed back into the momentum balance");
  }

  @Test
  @DisplayName("Single-phase gas pressure drop matches a Darcy-Weisbach integration")
  void testSinglePhaseGasMatchesDarcy() {
    TwoFluidPipe pipe = runPipe(80);
    Assertions.assertTrue(pipe.isSteadyStateConverged());
    double reference = darcyReference(200);
    double reported = pressureDrop(pipe);
    // The gas expands enough over this line that using the inlet density throughout
    // understates the reference by about ten per cent.
    Assertions.assertEquals(reference, reported, 0.06 * reference,
        "reported " + reported + " bar against a Darcy reference of " + reference + " bar");
  }

  @Test
  @DisplayName("Refining the mesh moves the answer towards the reference, not away from it")
  void testMeshRefinementImprovesAgreement() {
    double reference = darcyReference(200);
    List<Double> errors = new ArrayList<Double>();
    for (int sections : new int[] { 40, 80 }) {
      TwoFluidPipe pipe = runPipe(sections);
      Assertions.assertTrue(pipe.isSteadyStateConverged(), "case with " + sections + " sections must converge");
      errors.add(Math.abs(pressureDrop(pipe) - reference));
    }
    Assertions.assertTrue(errors.get(1) <= errors.get(0) * 1.05,
        "error grew from " + errors.get(0) + " bar at 40 sections to " + errors.get(1) + " bar at 80");
  }
}
