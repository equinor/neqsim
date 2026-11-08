package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Checks that the API RP 14E erosional ratio of {@link PipeBeggsAndBrills} uses the no-slip mixture density of the same
 * increment as the mixture velocity, so the ratio agrees with a hand calculation from one flash of the stream.
 *
 * @author ESOL
 * @version 1.0
 */
class PipeBeggsAndBrillsErosionalRatioTest {

  private PipeBeggsAndBrills runPipe() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 40.0, 40.0);
    fluid.addComponent("methane", 0.60);
    fluid.addComponent("n-heptane", 0.30);
    fluid.addComponent("nC10", 0.10);
    fluid.setMixingRule("classic");
    fluid.setTotalFlowRate(40000.0, "kg/hr");
    new ThermodynamicOperations(fluid).TPflash();
    Stream feed = new Stream("feed", fluid);
    feed.run();
    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("pipe", feed);
    pipe.setLength(2000.0);
    pipe.setDiameter(0.2);
    pipe.setElevation(0.0);
    pipe.setNumberOfIncrements(10);
    pipe.run();
    return pipe;
  }

  @Test
  void erosionalVelocityUsesTheNoSlipDensityOfTheLastIncrement() {
    PipeBeggsAndBrills pipe = runPipe();
    double rho = pipe.getNoSlipMixtureDensity();
    assertTrue(rho > 1.0, "no-slip density should be available after the run");
    assertEquals(100.0 / Math.sqrt(rho), pipe.getErosionalVelocity(), 1e-9);
    assertEquals(122.0 / Math.sqrt(rho), pipe.getErosionalVelocity(122.0), 1e-9);
  }

  @Test
  void noSlipDensityIsBetweenTheGasAndLiquidDensityOfTheOutlet() {
    PipeBeggsAndBrills pipe = runPipe();
    SystemInterface out = pipe.getOutletStream().getFluid();
    out.initProperties();
    assertTrue(out.hasPhaseType("gas") && out.hasPhaseType("oil"), "test fluid must be two-phase");
    double gas = out.getPhase("gas").getDensity("kg/m3");
    double liquid = out.getPhase("oil").getDensity("kg/m3");
    double rho = pipe.getNoSlipMixtureDensity();
    assertTrue(rho > gas && rho < liquid, "density " + rho + " not between " + gas + " and " + liquid);
  }

  @Test
  void maximumRatioMatchesTheVelocityOverErosionalVelocityOfTheProfile() {
    PipeBeggsAndBrills pipe = runPipe();
    double max = pipe.getMaxErosionalVelocityRatio(122.0);
    assertFalse(Double.isNaN(max));
    assertTrue(max > 0.0);
    double lastIncrement = pipe.getMixtureVelocity() / pipe.getErosionalVelocity(122.0);
    assertEquals(lastIncrement, max, 0.25 * max, "last increment ratio should be close to the maximum of a short pipe");
    assertTrue(max >= lastIncrement - 1e-9);
  }

  @Test
  void ratioScalesInverselyWithTheCFactor() {
    PipeBeggsAndBrills pipe = runPipe();
    assertEquals(pipe.getMaxErosionalVelocityRatio(100.0) * 100.0 / 150.0, pipe.getMaxErosionalVelocityRatio(150.0),
        1e-9);
  }
}
