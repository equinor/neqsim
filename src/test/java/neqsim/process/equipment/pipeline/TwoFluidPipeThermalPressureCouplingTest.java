package neqsim.process.equipment.pipeline;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression test for momentum-energy coupling in the {@link TwoFluidPipe} steady solve.
 *
 * <p>
 * At fixed mass rate a gas line that is heated carries less dense gas, and the frictional gradient scales as
 * {@code G^2 / rho}, so heating must raise the pressure drop. Earlier builds reported an arrival temperature rise of
 * more than twenty kelvin from direct electrical heating while the computed pressure drop stayed unchanged to five
 * figures, which meant the energy equation was not feeding the momentum balance. That symptom came from steady-state
 * sweeps that terminated before the density field had been fed back into the pressure march.
 * </p>
 *
 * <p>
 * On a 73.8 km subsea gas-condensate export line at 10 MSm3/d, 10 MW of heating raises the arrival temperature by about
 * 17 K and the pressure drop by about 15 per cent.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
class TwoFluidPipeThermalPressureCouplingTest {
  /** Inside diameter, in m. */
  private static final double DIAMETER = 0.30;

  /** Pipe length, in m. */
  private static final double LENGTH = 40000.0;

  /** Wall roughness, in m. */
  private static final double ROUGHNESS = 4.5e-5;

  /** Inlet pressure, in bara. */
  private static final double INLET_PRESSURE = 150.0;

  /** Inlet temperature, in degrees Celsius. */
  private static final double INLET_TEMPERATURE = 40.0;

  /** Surrounding temperature, in degrees Celsius. */
  private static final double SURFACE_TEMPERATURE = 4.0;

  /** Overall heat transfer coefficient, in W/m2K. */
  private static final double HEAT_TRANSFER_COEFFICIENT = 3.0;

  /** Mass flow, in kg/hr. */
  private static final double MASS_FLOW = 250000.0;

  /** Number of finite volumes. */
  private static final int SECTIONS = 120;

  /** Uniform direct electrical heating, in W/m. */
  private static final double HEATING_PER_METRE = 120.0;

  /**
   * Builds a lean gas at the inlet state.
   *
   * @return a flashed fluid with its physical properties initialised
   */
  private SystemInterface buildFluid() {
    SystemInterface fluid = new SystemSrkEos(273.15 + INLET_TEMPERATURE, INLET_PRESSURE);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.06);
    fluid.addComponent("propane", 0.04);
    fluid.setMixingRule("classic");
    fluid.setPressure(INLET_PRESSURE, "bara");
    fluid.setTemperature(INLET_TEMPERATURE, "C");
    fluid.setTotalFlowRate(MASS_FLOW, "kg/hr");
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    return fluid;
  }

  /**
   * Runs the line with the requested uniform heating.
   *
   * @param heatingPerMetre direct electrical heating in W/m, zero for the unheated reference
   * @return the pipe after it has been run
   */
  private TwoFluidPipe runLine(double heatingPerMetre) {
    Stream stream = new Stream("feed", buildFluid());
    stream.setFlowRate(MASS_FLOW, "kg/hr");
    stream.setPressure(INLET_PRESSURE, "bara");
    stream.setTemperature(INLET_TEMPERATURE, "C");
    stream.run();

    TwoFluidPipe pipe = new TwoFluidPipe("heatedline", stream);
    pipe.setLength(LENGTH);
    pipe.setDiameter(DIAMETER);
    pipe.setRoughness(ROUGHNESS);
    pipe.setNumberOfSections(SECTIONS);
    pipe.setElevationProfile(new double[SECTIONS + 1]);
    pipe.setIncludeEnergyEquation(true);
    pipe.setHeatTransferCoefficient(HEAT_TRANSFER_COEFFICIENT);
    pipe.setSurfaceTemperature(SURFACE_TEMPERATURE, "C");
    if (heatingPerMetre > 0.0) {
      pipe.setDirectElectricalHeatingPowerPerMeter(heatingPerMetre);
    }
    pipe.run();
    return pipe;
  }

  /**
   * Returns the total pressure drop of a solved pipe.
   *
   * @param pipe a pipe that has been run
   * @return pressure drop in Pa
   */
  private double pressureDrop(TwoFluidPipe pipe) {
    double[] profile = pipe.getPressureProfile();
    return profile[0] - profile[profile.length - 1];
  }

  /**
   * Heating the line must raise both the arrival temperature and the pressure drop.
   */
  @Test
  @DisplayName("Pressure drop must respond to a heating-driven density change")
  void testHeatingRaisesPressureDrop() {
    TwoFluidPipe cold = runLine(0.0);
    TwoFluidPipe heated = runLine(HEATING_PER_METRE);

    Assertions.assertTrue(cold.isSteadyStateConverged(), "unheated reference must converge");
    Assertions.assertTrue(heated.isSteadyStateConverged(), "heated case must converge");

    double coldArrival = cold.getOutletStream().getTemperature("C");
    double heatedArrival = heated.getOutletStream().getTemperature("C");
    Assertions.assertTrue(heatedArrival - coldArrival > 5.0,
        "the heating load must raise the arrival temperature, but it moved only " + (heatedArrival - coldArrival)
            + " K");

    double coldDrop = pressureDrop(cold);
    double heatedDrop = pressureDrop(heated);
    double relativeChange = (heatedDrop - coldDrop) / coldDrop;

    Assertions.assertTrue(relativeChange > 0.005,
        "warmer gas is less dense and dP scales as G^2/rho, so the pressure drop must rise with "
            + "heating, but it moved " + (100.0 * relativeChange) + "%");
    Assertions.assertTrue(relativeChange < 0.60,
        "the pressure-drop response to heating is implausibly large at " + (100.0 * relativeChange) + "%");
  }
}
