package neqsim.pvtsimulation.flowassurance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests that a dry fluid is reported as an input defect rather than as "no hydrate risk".
 *
 * <p>
 * Before this guard existed, a cooldown study run on a dry reservoir fluid returned {@code NO_HYDRATE_RISK} and an
 * infinite no-touch time for a line that was in fact wet - the same answer a genuinely non-hydrate-forming fluid gives.
 * That is a silently wrong answer on a safety-relevant number.
 * </p>
 */
public class SurfCooldownAnalyzerWaterGuardTest {
  private static SystemInterface dryGas() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 40.0, 70.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.03);
    fluid.setMixingRule("classic");
    return fluid;
  }

  private static SystemInterface wetGas() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 40.0, 70.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.03);
    fluid.addComponent("water", 0.02);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  private static SurfCooldownAnalyzer configure(SystemInterface fluid) {
    SurfCooldownAnalyzer analyzer = new SurfCooldownAnalyzer(fluid);
    analyzer.setInternalDiameter(0.2032);
    analyzer.setWallThickness(0.0159);
    analyzer.setInsulationThickness(0.05);
    analyzer.setInsulationConductivity(0.22);
    analyzer.setExternalHTC(500.0);
    analyzer.setSeabedTemperature(4.0);
    analyzer.setOperatingTemperature(35.0);
    analyzer.setRequiredNoTouchTimeHours(8.0);
    analyzer.setTotalTimeHours(48.0);
    return analyzer;
  }

  @Test
  void aDryFluidIsQueryableAsCarryingNoWater() {
    SurfCooldownAnalyzer analyzer = configure(dryGas());
    analyzer.calculate();
    // The historical contract is preserved: a dry gas genuinely cannot form hydrates.
    assertEquals(SurfCooldownAnalyzer.VERDICT_NO_HYDRATE_RISK, analyzer.getVerdict());
    // ...but the caller can now tell a dry gas from a wet line with the wrong fluid file.
    assertFalse(analyzer.isWaterPresent());
    assertEquals(0.0, analyzer.getWaterMoleFraction(), 1.0e-12);
  }

  @Test
  void theVerdictAloneCannotSeparateADryGasFromAMissingWaterComponent() {
    SurfCooldownAnalyzer dry = configure(dryGas());
    dry.calculate();
    assertTrue(Double.isInfinite(dry.getNoTouchTimeHours()),
        "a dry fluid reports an unbounded no-touch time, which is why isWaterPresent matters");
    assertFalse(dry.isWaterPresent());
  }

  @Test
  void strictModeTurnsADryFluidIntoAHardFailure() {
    SurfCooldownAnalyzer analyzer = configure(dryGas());
    analyzer.setRequireWater(true);
    assertTrue(analyzer.isRequireWater());
    assertThrows(RuntimeException.class, () -> analyzer.calculate());
  }

  @Test
  void aWetFluidIsAssessedNormally() {
    SurfCooldownAnalyzer analyzer = configure(wetGas());
    analyzer.calculate();
    assertTrue(analyzer.isWaterPresent());
    assertTrue(analyzer.getWaterMoleFraction() > 0.0);
    assertTrue(analyzer.getNoTouchTimeHours() >= 0.0);
  }

  @Test
  void strictModePassesOnAWetFluid() {
    SurfCooldownAnalyzer analyzer = configure(wetGas());
    analyzer.setRequireWater(true);
    analyzer.calculate();
    assertTrue(analyzer.isWaterPresent());
  }

  @Test
  void jsonReportsWhetherWaterWasPresent() {
    SurfCooldownAnalyzer analyzer = configure(dryGas());
    String json = analyzer.toJson();
    assertTrue(json.contains("waterPresentInFluid"));
  }
}
