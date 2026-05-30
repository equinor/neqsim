package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;

/**
 * Regression tests for the compressor's "real phase" multi-phase-check guard.
 *
 * <p>
 * Historically the guard counted {@code getNumberOfPhases()} directly, which can include a phantom
 * phase carried over from a previous flash (β &lt; 1.0e-10). The phantom phase has non-physical Z
 * and seeds NaN inside {@link neqsim.thermo.phase.PhasePrEos#molarVolumeAnalytical} when the
 * compressor's outlet PHflash is allowed to run a multi-phase check. The fix counts only phases
 * with β &gt; 1.0e-10 ("real" phases) and disables the multi-phase check when there is only one
 * real phase.
 */
public class CompressorRealPhaseGuardTest {

  private static SystemInterface buildGas(double temperatureK, double pressureBara) {
    SystemInterface gas = new SystemPrEos(temperatureK, pressureBara);
    gas.addComponent("nitrogen", 0.012);
    gas.addComponent("CO2", 0.013);
    gas.addComponent("methane", 0.880);
    gas.addComponent("ethane", 0.053);
    gas.addComponent("propane", 0.033);
    gas.addComponent("n-butane", 0.005);
    gas.addComponent("n-hexane", 0.002);
    gas.addComponent("n-heptane", 0.002);
    gas.setMixingRule("classic");
    return gas;
  }

  @Test
  public void testSinglePhaseGasCompressionStaysFinite() {
    SystemInterface gas = buildGas(273.15 + 30.0, 50.0);
    StreamInterface feed = new Stream("feed", gas);
    feed.setFlowRate(100000.0, "kg/hr");
    feed.run();

    Compressor comp = new Compressor("comp", feed);
    comp.setOutletPressure(120.0);
    comp.setIsentropicEfficiency(0.80);
    comp.run();

    double outT = comp.getOutletStream().getTemperature("C");
    double power = comp.getPower();
    assertTrue(Double.isFinite(outT),
        "compressor outlet temperature must be finite (was " + outT + ")");
    assertTrue(Double.isFinite(power), "compressor shaft power must be finite (was " + power + ")");
    assertTrue(power > 0.0, "compressor power must be positive (was " + power + ")");
    assertEquals(1, comp.getOutletStream().getFluid().getNumberOfPhases(),
        "single-phase gas inlet should produce a single-phase outlet");
  }

  @Test
  public void testHighPressureRatioStaysFinite() {
    // Conditions close to the cricondenbar where the PR cubic is most
    // sensitive to trial-K pollution from supplementary stability trials.
    SystemInterface gas = buildGas(273.15 + 20.0, 80.0);
    StreamInterface feed = new Stream("feed", gas);
    feed.setFlowRate(250000.0, "kg/hr");
    feed.run();

    Compressor comp = new Compressor("comp", feed);
    comp.setOutletPressure(200.0);
    comp.setIsentropicEfficiency(0.78);
    comp.run();

    double outT = comp.getOutletStream().getTemperature("C");
    SystemInterface outFluid = comp.getOutletStream().getFluid();
    outFluid.initProperties();
    double outRho = outFluid.getPhase(0).getDensity();
    assertTrue(Double.isFinite(outT) && outT > 20.0 && outT < 300.0,
        "compressor outlet temperature must be physically reasonable (was " + outT + " C)");
    assertTrue(Double.isFinite(outRho) && outRho > 0.0,
        "compressor outlet density must be finite and positive (was " + outRho + ")");
  }
}
