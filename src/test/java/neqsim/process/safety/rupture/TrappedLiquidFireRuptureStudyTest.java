package neqsim.process.safety.rupture;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.safety.barrier.SafetySystemDemand;
import neqsim.process.safety.inventory.TrappedInventoryCalculator;
import neqsim.process.safety.inventory.TrappedInventoryCalculator.InventoryResult;
import neqsim.process.safety.release.ReleaseOrientation;
import neqsim.process.safety.release.SourceTermResult;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for trapped-liquid fire rupture screening.
 *
 * @author ESOL
 * @version 1.0
 */
class TrappedLiquidFireRuptureStudyTest {

  /**
   * Verifies API 5L material strength loading and high-temperature degradation.
   */
  @Test
  void api5lMaterialStrengthReducesAtFireTemperature() {
    MaterialStrengthCurve material = MaterialStrengthCurve.forApi5LPipeGrade("X52");

    assertTrue(material.getAmbientYieldStrengthPa() > 300.0e6);
    assertTrue(material.tensileStrengthAt(873.15) < material.tensileStrengthAt(293.15));
    assertTrue(material.toJson().contains("API 5L X52"));
  }

  /**
   * Verifies API 521 fire exposure is converted to positive heat flux and PFP factor applies.
   */
  @Test
  void fireExposureCalculatesHeatFluxAndPfpReduction() {
    FireExposureScenario unprotected = FireExposureScenario.api521PoolFire(4.0, 1.0);
    FireExposureScenario protectedScenario = unprotected.withPassiveProtectionFactor(0.25);

    assertTrue(unprotected.incidentHeatFlux(300.0) > 0.0);
    assertTrue(protectedScenario.incidentHeatFlux(300.0) < unprotected.incidentHeatFlux(300.0));
  }

  /**
   * Verifies a severe fire predicts pipe rupture and produces auditable outputs.
   */
  @Test
  void severeFirePredictsPipeRuptureForBlockedLiquidSegment() {
    SystemInterface fluid = createLiquidHydrocarbon();
    InventoryResult inventory = createLiquidInventory(fluid);

    TrappedLiquidFireRuptureResult result = TrappedLiquidFireRuptureStudy.builder()
        .segmentId("TL-001").fluid(fluid).inventory(inventory)
        .pipeGeometry(0.10, "m", 3.0, "mm", 10.0, "m").api5lMaterial("X52")
        .fireScenario(FireExposureScenario.fixedHeatFlux(3.4, 180000.0)).flangeClass(900)
        .timeControls(600.0, 2.0).vaporPocketDetectionEnabled(false).build().run();

    assertTrue(result.isRupturePredicted());
    assertTrue(Double.isFinite(result.getMinimumFailureTimeSeconds()));
    assertTrue(result.getFinalPressureBara() > inventory.getPressureBara());
    assertTrue(result.toJson().contains("API 521"));
    assertTrue(result.getRecommendations().size() >= 3);
  }

  /**
   * Verifies reduced fire heat flux delays rupture time.
   */
  @Test
  void passiveProtectionDelaysPredictedFailure() {
    SystemInterface fluid = createLiquidHydrocarbon();
    InventoryResult inventory = createLiquidInventory(fluid);
    FireExposureScenario baseFire = FireExposureScenario.fixedHeatFlux(3.4, 180000.0);

    TrappedLiquidFireRuptureResult unprotected = TrappedLiquidFireRuptureStudy.builder()
        .segmentId("TL-002").fluid(fluid).inventory(inventory).pipeGeometry(0.10, 0.003, 10.0)
        .api5lMaterial("X52").fireScenario(baseFire).timeControls(600.0, 2.0)
        .vaporPocketDetectionEnabled(false).build().run();
    TrappedLiquidFireRuptureResult protectedResult = TrappedLiquidFireRuptureStudy.builder()
        .segmentId("TL-002").fluid(fluid).inventory(inventory).pipeGeometry(0.10, 0.003, 10.0)
        .api5lMaterial("X52").fireScenario(baseFire.withPassiveProtectionFactor(0.35))
        .timeControls(600.0, 2.0).vaporPocketDetectionEnabled(false).build().run();

    assertTrue(protectedResult.getMinimumFailureTimeSeconds()
        > unprotected.getMinimumFailureTimeSeconds());
  }

  /**
   * Verifies barrier and source-term handoff objects can be produced from a rupture result.
   */
  @Test
  void resultCreatesBarrierDemandAndSourceTerm() {
    SystemInterface fluid = createLiquidHydrocarbon();
    InventoryResult inventory = createLiquidInventory(fluid);
    TrappedLiquidFireRuptureResult result = TrappedLiquidFireRuptureStudy.builder()
        .segmentId("TL-003").fluid(fluid).inventory(inventory).pipeGeometry(0.10, 0.003, 10.0)
        .api5lMaterial("X52").fireScenario(FireExposureScenario.fixedHeatFlux(3.4, 180000.0))
        .timeControls(600.0, 2.0).vaporPocketDetectionEnabled(false).build().run();

    SafetySystemDemand demand = result.toPassiveFireProtectionDemand("PFP-TL-003", 1800.0);
    SourceTermResult sourceTerm = result.createRuptureSourceTerm(fluid, ReleaseOrientation.HORIZONTAL,
        20.0, 5.0);

    assertTrue(demand.getCapacityValue() > 0.0);
    assertNotNull(sourceTerm);
    assertTrue(sourceTerm.getPeakMassFlowRate() > 0.0);
  }

  /**
   * Creates a representative liquid hydrocarbon fluid.
   *
   * @return configured SRK fluid
   */
  private SystemInterface createLiquidHydrocarbon() {
    SystemInterface fluid = new SystemSrkEos(298.15, 10.0);
    fluid.addComponent("n-heptane", 100.0);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /**
   * Creates trapped liquid inventory for tests.
   *
   * @param fluid representative fluid
   * @return calculated inventory result
   */
  private InventoryResult createLiquidInventory(SystemInterface fluid) {
    return new TrappedInventoryCalculator().setFluid(fluid).setOperatingConditions(10.0, "bara",
        25.0, "C").addPipeSegment("TL-PIPE", 0.10, 10.0, 1.0, null).calculate();
  }
}
