package neqsim.process.equipment.pipeline;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression test for the holdup closure of a lean, high-pressure gas line in {@link TwoFluidPipe}.
 *
 * <p>
 * The minimum-slip constraint used to apply the Beggs and Brill horizontal holdup correlation as a lower bound in every
 * regime, including the stratified and annular regimes which already solve a mechanistic closure. That correlation was
 * fitted to 1 to 1.5 inch air-water loops at near-atmospheric pressure with no-slip liquid fractions at or above about
 * 0.01. On a 14-inch high-pressure gas-condensate export line running near a no-slip fraction of 0.008 it was binding
 * in every section, so the reported holdup was the correlation rather than the solved momentum balance: roughly three
 * times the mechanistic value, which carried about twenty per cent onto the pressure drop through the mixture density.
 * </p>
 *
 * <p>
 * The correlation minimum is now skipped where a mechanistic closure exists. The slip bound itself still applies on
 * level and uphill sections, since a bound on the slip ratio is dimensionally sound at any scale; on a lean gas line it
 * reduces to the no-slip multiple {@code lambdaL * minimumSlipFactor}.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
class TwoFluidPipeLeanGasHoldupTest {
  /** Inside diameter, in m. Large enough that the small-bore correlation is an extrapolation. */
  private static final double DIAMETER = 0.355;

  /** Pipe length, in m. */
  private static final double LENGTH = 30000.0;

  /** Wall roughness, in m. */
  private static final double ROUGHNESS = 4.5e-5;

  /** Inlet pressure, in bara. */
  private static final double INLET_PRESSURE = 100.0;

  /** Inlet temperature, in degrees Celsius. */
  private static final double INLET_TEMPERATURE = 40.0;

  /** Mass flow, in kg/hr. */
  private static final double MASS_FLOW = 250000.0;

  /** Number of finite volumes. */
  private static final int SECTIONS = 100;

  /**
   * Builds a lean gas condensate that carries a small liquid fraction at pipeline conditions.
   *
   * @return a flashed fluid with its physical properties initialised
   */
  private SystemInterface buildFluid() {
    SystemInterface fluid = new SystemSrkEos(273.15 + INLET_TEMPERATURE, INLET_PRESSURE);
    fluid.addComponent("methane", 0.95);
    fluid.addComponent("ethane", 0.03);
    fluid.addComponent("n-heptane", 0.02);
    fluid.setMixingRule("classic");
    fluid.setPressure(INLET_PRESSURE, "bara");
    fluid.setTemperature(INLET_TEMPERATURE, "C");
    fluid.setTotalFlowRate(MASS_FLOW, "kg/hr");
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    return fluid;
  }

  /**
   * Runs the lean gas line.
   *
   * @return the pipe after it has been run
   */
  private TwoFluidPipe runLeanGasLine() {
    Stream stream = new Stream("feed", buildFluid());
    stream.setFlowRate(MASS_FLOW, "kg/hr");
    stream.setPressure(INLET_PRESSURE, "bara");
    stream.setTemperature(INLET_TEMPERATURE, "C");
    stream.run();

    TwoFluidPipe pipe = new TwoFluidPipe("leangasline", stream);
    pipe.setLength(LENGTH);
    pipe.setDiameter(DIAMETER);
    pipe.setRoughness(ROUGHNESS);
    pipe.setNumberOfSections(SECTIONS);
    pipe.setElevationProfile(new double[SECTIONS + 1]);
    pipe.setIncludeEnergyEquation(true);
    pipe.setHeatTransferCoefficient(3.0);
    pipe.setSurfaceTemperature(4.0, "C");
    pipe.run();
    return pipe;
  }

  /**
   * The slip ratio of a lean high-pressure gas line must come from the momentum balance.
   *
   * <p>
   * The holdup is compared against the no-slip fraction reconstructed from the reported phase velocities, which makes
   * the assertion independent of the absolute liquid loading of the fixture. With the correlation floor binding the
   * ratio was near seven; the mechanistic closure gives about two and a half.
   * </p>
   */
  @Test
  @DisplayName("Lean gas holdup must follow the momentum balance, not the Beggs and Brill floor")
  void testLeanGasHoldupIsNotSetByTheCorrelationFloor() {
    TwoFluidPipe pipe = runLeanGasLine();
    Assertions.assertTrue(pipe.isSteadyStateConverged(), "the lean gas line must converge");

    double[] holdup = pipe.getLiquidHoldupProfile();
    double[] gasVelocity = pipe.getGasVelocityProfile();
    double[] liquidVelocity = pipe.getLiquidVelocityProfile();

    double holdupSum = 0.0;
    double noSlipSum = 0.0;
    for (int i = 0; i < holdup.length; i++) {
      double superficialGas = gasVelocity[i] * (1.0 - holdup[i]);
      double superficialLiquid = liquidVelocity[i] * holdup[i];
      double total = superficialGas + superficialLiquid;
      if (total <= 0.0) {
        continue;
      }
      holdupSum += holdup[i];
      noSlipSum += superficialLiquid / total;
    }

    Assertions.assertTrue(noSlipSum > 0.0, "the fixture must carry some liquid");
    double meanHoldup = holdupSum / holdup.length;
    double meanNoSlip = noSlipSum / holdup.length;
    double slipDrivenRatio = meanHoldup / meanNoSlip;

    Assertions.assertTrue(slipDrivenRatio > 1.0,
        "holdup must exceed the no-slip fraction because the gas is faster than the liquid");
    Assertions.assertTrue(slipDrivenRatio < 4.0,
        "holdup is " + slipDrivenRatio + " times the no-slip fraction, which indicates the small-bore "
            + "correlation floor is overriding the mechanistic closure on a large-diameter line");
  }

  /**
   * Releasing the minimum-slip constraint must no longer transform the answer.
   *
   * <p>
   * When the correlation floor was binding in every section, switching the constraint off moved the mean holdup by a
   * factor of five and the pressure drop by roughly twenty per cent. A closure that is actually solving the momentum
   * balance must be far less sensitive to a bound that should only ever be a guard.
   * </p>
   */
  @Test
  @DisplayName("Releasing the minimum-slip guard must not transform a lean gas solution")
  void testMinimumSlipGuardIsNotLoadBearing() {
    TwoFluidPipe guarded = runLeanGasLine();

    Stream stream = new Stream("feed", buildFluid());
    stream.setFlowRate(MASS_FLOW, "kg/hr");
    stream.setPressure(INLET_PRESSURE, "bara");
    stream.setTemperature(INLET_TEMPERATURE, "C");
    stream.run();
    TwoFluidPipe released = new TwoFluidPipe("leangasline", stream);
    released.setLength(LENGTH);
    released.setDiameter(DIAMETER);
    released.setRoughness(ROUGHNESS);
    released.setNumberOfSections(SECTIONS);
    released.setElevationProfile(new double[SECTIONS + 1]);
    released.setIncludeEnergyEquation(true);
    released.setHeatTransferCoefficient(3.0);
    released.setSurfaceTemperature(4.0, "C");
    released.setEnforceMinimumSlip(false);
    released.run();

    Assertions.assertTrue(guarded.isSteadyStateConverged(), "guarded case must converge");
    Assertions.assertTrue(released.isSteadyStateConverged(), "released case must converge");

    double[] guardedProfile = guarded.getPressureProfile();
    double[] releasedProfile = released.getPressureProfile();
    double guardedDrop = guardedProfile[0] - guardedProfile[guardedProfile.length - 1];
    double releasedDrop = releasedProfile[0] - releasedProfile[releasedProfile.length - 1];

    double relativeDifference = Math.abs(guardedDrop - releasedDrop) / releasedDrop;
    Assertions.assertTrue(relativeDifference < 0.10, "the minimum-slip guard moved the pressure drop by "
        + (100.0 * relativeDifference) + "%, so it is setting the solution rather than guarding it");
  }
}
