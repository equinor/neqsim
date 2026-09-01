package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression tests for direct electrical heating (DEH) in {@link TwoFluidPipe}.
 *
 * <p>
 * DEH is a uniform heat source added to the fluid energy equation. The steady-state solution must approach the balance
 * temperature where the wall loss equals the injected power, and the source must remain active when wall heat transfer
 * is switched off.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class TwoFluidPipeDehTest {

  private static final double PIPE_LENGTH = 20000.0;
  private static final double PIPE_DIAMETER = 0.3;
  private static final int SECTIONS = 40;

  /**
   * Build a lean wet-gas feed stream.
   *
   * @return a run stream at 100 bara and 40 degrees Celsius
   */
  private Stream makeStream() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 40.0, 100.0);
    fluid.addComponent("methane", 0.95);
    fluid.addComponent("ethane", 0.03);
    fluid.addComponent("n-heptane", 0.02);
    fluid.setMixingRule("classic");

    Stream stream = new Stream("feed", fluid);
    stream.setFlowRate(200000.0, "kg/hr");
    stream.setPressure(100.0, "bara");
    stream.setTemperature(40.0, "C");
    stream.run();
    return stream;
  }

  /**
   * Build a horizontal cooled pipe.
   *
   * @param heatTransferCoefficient overall heat transfer coefficient in W/(m2 K)
   * @return a configured pipe that has not yet been run
   */
  private TwoFluidPipe makePipe(double heatTransferCoefficient) {
    TwoFluidPipe pipe = new TwoFluidPipe("pipe", makeStream());
    pipe.setLength(PIPE_LENGTH);
    pipe.setDiameter(PIPE_DIAMETER);
    pipe.setRoughness(4.5e-5);
    pipe.setNumberOfSections(SECTIONS);
    pipe.setElevationProfile(new double[SECTIONS + 1]);
    pipe.setEnableTerrainTracking(false);
    pipe.setEnableJouleThomson(false);
    pipe.setSurfaceTemperature(4.0, "C");
    pipe.setHeatTransferCoefficient(heatTransferCoefficient);
    return pipe;
  }

  /** DEH is off by default and must not change the thermal solution. */
  @Test
  void testDefaultIsZero() {
    TwoFluidPipe pipe = makePipe(15.0);
    assertEquals(0.0, pipe.getDirectElectricalHeatingPowerPerMeter(), 0.0);
    assertEquals(0.0, pipe.getDirectElectricalHeatingPower(), 0.0);
  }

  /** Total power is distributed over the pipe length. */
  @Test
  void testTotalPowerIsDistributedOverLength() {
    TwoFluidPipe pipe = makePipe(15.0);
    pipe.setDirectElectricalHeatingPower(1.0e6);
    assertEquals(1.0e6 / PIPE_LENGTH, pipe.getDirectElectricalHeatingPowerPerMeter(), 1.0e-9);
    assertEquals(1.0e6, pipe.getDirectElectricalHeatingPower(), 1.0e-6);
  }

  /** Negative power and a non-positive length are rejected. */
  @Test
  void testInvalidInputRejected() {
    TwoFluidPipe pipe = makePipe(15.0);
    assertThrows(IllegalArgumentException.class, () -> pipe.setDirectElectricalHeatingPower(-1.0));
    assertThrows(IllegalArgumentException.class, () -> pipe.setDirectElectricalHeatingPowerPerMeter(-1.0));

    TwoFluidPipe zeroLength = makePipe(15.0);
    zeroLength.setLength(0.0);
    assertThrows(IllegalArgumentException.class, () -> zeroLength.setDirectElectricalHeatingPower(1.0e6));
  }

  /** DEH must raise the arrival temperature of a cooled line. */
  @Test
  void testDehRaisesArrivalTemperature() {
    TwoFluidPipe cold = makePipe(15.0);
    cold.run();
    double arrivalWithoutDeh = cold.getTemperatureProfile()[SECTIONS - 1];

    TwoFluidPipe heated = makePipe(15.0);
    heated.setDirectElectricalHeatingPowerPerMeter(150.0);
    heated.run();
    double arrivalWithDeh = heated.getTemperatureProfile()[SECTIONS - 1];

    assertTrue(arrivalWithDeh > arrivalWithoutDeh + 1.0,
        "DEH must raise the arrival temperature, got " + arrivalWithDeh + " K vs " + arrivalWithoutDeh + " K");
  }

  /**
   * DEH matched to the wall loss must hold the line isothermal.
   *
   * <p>
   * Setting q = U*pi*D*(T_inlet - T_surface) places the balance temperature exactly at the inlet temperature, so the
   * exact steady solution is flat. This check is independent of heat capacity, mass flow, and pipe length, so it tests
   * the source term itself rather than the decay rate.
   * </p>
   */
  @Test
  void testDehMatchedToWallLossHoldsLineIsothermal() {
    double u = 15.0;
    double inletTemperature = 273.15 + 40.0;
    double surfaceTemperature = 273.15 + 4.0;
    double balancePowerPerMeter = u * Math.PI * PIPE_DIAMETER * (inletTemperature - surfaceTemperature);

    TwoFluidPipe pipe = makePipe(u);
    pipe.setDirectElectricalHeatingPowerPerMeter(balancePowerPerMeter);
    pipe.run();

    for (double temperature : pipe.getTemperatureProfile()) {
      assertEquals(inletTemperature, temperature, 0.05,
          "DEH matched to the wall loss must hold the inlet temperature along the whole line");
    }
  }

  /** The steady-state solution must never overshoot the balance temperature from below. */
  @Test
  void testNoOvershootOfBalanceTemperature() {
    double u = 15.0;
    double powerPerMeter = 5000.0;
    TwoFluidPipe pipe = makePipe(u);
    pipe.setDirectElectricalHeatingPowerPerMeter(powerPerMeter);
    pipe.run();

    double balance = (273.15 + 4.0) + powerPerMeter / (u * Math.PI * PIPE_DIAMETER);
    for (double temperature : pipe.getTemperatureProfile()) {
      assertTrue(temperature <= balance + 1.0e-6,
          "Temperature " + temperature + " K overshot the balance temperature " + balance + " K");
    }
  }

  /** DEH must also heat an adiabatic line, where there is no wall term to counteract. */
  @Test
  void testDehWorksWithoutWallHeatTransfer() {
    TwoFluidPipe adiabatic = new TwoFluidPipe("adiabatic", makeStream());
    adiabatic.setLength(PIPE_LENGTH);
    adiabatic.setDiameter(PIPE_DIAMETER);
    adiabatic.setRoughness(4.5e-5);
    adiabatic.setNumberOfSections(SECTIONS);
    adiabatic.setElevationProfile(new double[SECTIONS + 1]);
    adiabatic.setEnableTerrainTracking(false);
    adiabatic.setEnableJouleThomson(false);
    adiabatic.setDirectElectricalHeatingPowerPerMeter(200.0);
    adiabatic.run();

    double inlet = adiabatic.getTemperatureProfile()[0];
    double arrival = adiabatic.getTemperatureProfile()[SECTIONS - 1];
    assertTrue(arrival > inlet + 1.0,
        "DEH must heat an adiabatic line, inlet " + inlet + " K, arrival " + arrival + " K");
  }
}
