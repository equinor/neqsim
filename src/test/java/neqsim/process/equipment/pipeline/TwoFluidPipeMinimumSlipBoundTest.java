package neqsim.process.equipment.pipeline;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * The minimum-slip hold-up bound must stay a slip statement at high liquid loading.
 *
 * <p>
 * The bound used to be written as {@code alphaL >= lambdaL * minimumSlipFactor}, which is equivalent to a fixed slip
 * ratio only as {@code vsL / vsG} goes to zero. Its exact slip ratio is {@code S * vsG / (vsG + vsL * (1 - S))}, which
 * diverges at {@code vsL = vsG / (S - 1)}, and as a hold-up it exceeds one for {@code lambdaL > 1 / S}. Past that point
 * the bound was no longer a bound on slip but the 0.9 clamp it was truncated to, so a liquid-rich line was held at a
 * constant hold-up and the momentum balance was not used at all. That is how the Tengesdal severe-slugging facility
 * came to sit at 0.9 in every section of both flowline and riser.
 * </p>
 *
 * <p>
 * The bound is now inverted from the slip ratio itself, {@code alphaL >= X / (1 + X)} with {@code X = S * vsL / vsG},
 * which is below one at every liquid loading.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
class TwoFluidPipeMinimumSlipBoundTest {
  /** Inside diameter, in m. */
  private static final double DIAMETER = 0.1;

  /** Pipe length, in m. */
  private static final double LENGTH = 200.0;

  /** Wall roughness, in m. */
  private static final double ROUGHNESS = 4.5e-5;

  /** Inlet pressure, in bara. */
  private static final double INLET_PRESSURE = 20.0;

  /** Inlet temperature, in degrees Celsius. */
  private static final double INLET_TEMPERATURE = 25.0;

  /** Mass flow, in kg/hr. */
  private static final double MASS_FLOW = 30000.0;

  /** Number of sections. */
  private static final int SECTIONS = 10;

  /** The hold-up value the closure clamps to, which the bound must not degenerate into. */
  private static final double HOLDUP_CLAMP = 0.9;

  /**
   * Builds a liquid-rich two-phase fluid, so the no-slip fraction is well above {@code 1 / minimumSlipFactor}.
   *
   * <p>
   * Nitrogen over n-heptane at 20 bara gives a no-slip liquid fraction near 0.71, so the old bound would have demanded
   * a hold-up of 1.42 and been truncated to the 0.9 clamp. The momentum balance solves 0.85.
   * </p>
   *
   * @return a flashed fluid with its physical properties initialised
   */
  private SystemInterface buildFluid() {
    SystemInterface fluid = new SystemSrkEos(273.15 + INLET_TEMPERATURE, INLET_PRESSURE);
    fluid.addComponent("nitrogen", 0.07);
    fluid.addComponent("n-heptane", 0.93);
    fluid.setMixingRule("classic");
    fluid.setPressure(INLET_PRESSURE, "bara");
    fluid.setTemperature(INLET_TEMPERATURE, "C");
    fluid.setTotalFlowRate(MASS_FLOW, "kg/hr");
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    return fluid;
  }

  /**
   * Runs a level, liquid-rich line, where the bound applies because the section is not downhill.
   *
   * @return the pipe after it has been run
   */
  private TwoFluidPipe runLiquidRichLine() {
    Stream stream = new Stream("feed", buildFluid());
    stream.setFlowRate(MASS_FLOW, "kg/hr");
    stream.setPressure(INLET_PRESSURE, "bara");
    stream.setTemperature(INLET_TEMPERATURE, "C");
    stream.run();

    TwoFluidPipe pipe = new TwoFluidPipe("liquidrichline", stream);
    pipe.setLength(LENGTH);
    pipe.setDiameter(DIAMETER);
    pipe.setRoughness(ROUGHNESS);
    pipe.setNumberOfSections(SECTIONS);
    pipe.setElevationProfile(new double[SECTIONS + 1]);
    pipe.run();
    return pipe;
  }

  /**
   * A liquid-rich level line must not be pinned at the hold-up clamp, and the bound must be respected where it binds.
   */
  @Test
  @DisplayName("The minimum-slip bound must not degenerate into the hold-up clamp on a liquid-rich line")
  void testBoundStaysASlipStatementAtHighLiquidLoading() {
    TwoFluidPipe pipe = runLiquidRichLine();
    Assertions.assertTrue(pipe.isSteadyStateConverged(), "the liquid-rich line must converge");

    double[] holdup = pipe.getLiquidHoldupProfile();
    double[] gasVelocity = pipe.getGasVelocityProfile();
    double[] liquidVelocity = pipe.getLiquidVelocityProfile();
    double slipFactor = pipe.getMinimumSlipFactor();

    boolean exercisesTheDefect = false;
    for (int i = 0; i < holdup.length; i++) {
      double superficialGas = gasVelocity[i] * (1.0 - holdup[i]);
      double superficialLiquid = liquidVelocity[i] * holdup[i];
      if (superficialGas <= 0.0 || superficialLiquid <= 0.0) {
        continue;
      }

      double noSlipFraction = superficialLiquid / (superficialGas + superficialLiquid);
      if (noSlipFraction * slipFactor <= 1.0) {
        continue;
      }
      exercisesTheDefect = true;

      Assertions.assertTrue(holdup[i] < HOLDUP_CLAMP, "section " + i + " sits at the hold-up clamp, so the bound "
          + "degenerated into a constant instead of a slip statement; holdup=" + holdup[i]);

      double slipRatioLimit = slipFactor * superficialLiquid / superficialGas;
      double boundHoldup = slipRatioLimit / (1.0 + slipRatioLimit);
      Assertions.assertTrue(holdup[i] >= boundHoldup - 1.0e-6,
          "section " + i + " falls below the minimum-slip bound; " + "holdup=" + holdup[i] + " bound=" + boundHoldup);
    }
    Assertions.assertTrue(exercisesTheDefect,
        "no section reached a no-slip fraction above 1 / minimumSlipFactor, so this fixture does not exercise the "
            + "loading at which the old no-slip multiple degenerated into the clamp");
  }
}
