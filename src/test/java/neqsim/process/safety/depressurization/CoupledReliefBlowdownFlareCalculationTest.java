package neqsim.process.safety.depressurization;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import neqsim.process.engineering.calculation.EngineeringCalculationContext;
import neqsim.process.engineering.calculation.EngineeringCalculationResult;
import neqsim.process.safety.depressurization.DynamicBlowdownFlareStudyDataSource.BlowdownSource;
import neqsim.process.safety.overpressure.BlockedOutletRelief;
import neqsim.process.safety.overpressure.OverpressureProtectionStudy;
import neqsim.process.safety.overpressure.ProtectedItem;
import neqsim.process.safety.overpressure.ReliefScenario;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import org.junit.jupiter.api.Test;

/** Tests the coupled steady relief and transient flare-load envelope. */
class CoupledReliefBlowdownFlareCalculationTest {

  @Test
  void calculatesGoverningSteadyAndDynamicDisposalLoad() {
    SystemInterface fluid = fluid();
    ReliefScenario scenario = new BlockedOutletRelief().setName("Blocked outlet").setInflowRateKgPerS(2.0)
        .setReliefPressureBara(75.0).setReliefTemperatureC(30.0).setFluid(fluid).calculate();
    OverpressureProtectionStudy relief = new OverpressureProtectionStudy(
        new ProtectedItem("V-100", 80.0).setReliefSetPressureBara(75.0).setBackPressureBara(1.5)).addScenario(scenario);
    BlowdownSource source = BlowdownSource.builder("V-100", fluid).equipmentTag("V-100").vesselVolumeM3(2.0)
        .orificeDiameterM(0.012).dischargeCoefficient(0.72).backPressureBara(1.5).stopPressureBara(1.5)
        .api521FireCase(8.0, true, true).psvBasis(75.0, 0.21, false, false).build();
    DynamicBlowdownFlareStudyDataSource dynamic = DynamicBlowdownFlareStudyDataSource.builder("dynamic")
        .addSource(source).flareHeader(0.30, 1.5, 288.15, 0.020, 1.30).flareGeometry(0.5, 35.0, 0.20)
        .flareDesignCapacity(5.0e8, 500.0, 30000.0).build();
    CoupledReliefBlowdownFlareInput input = CoupledReliefBlowdownFlareInput.builder("coupled-1")
        .addReliefStudy(relief, "FIRE-ZONE-1").dynamicStudy(dynamic).scenarioSelectionReviewed(true)
        .addEvidenceReference("HAZOP-001").build();

    CoupledReliefBlowdownFlareCalculation calculation = new CoupledReliefBlowdownFlareCalculation(
        DynamicBlowdownFlareStudyRunner.builder().timeStepSeconds(5.0).maxTimeSeconds(60.0).build());
    EngineeringCalculationResult<CoupledReliefBlowdownFlareResult> result = calculation.calculate(input,
        EngineeringCalculationContext.builder().designCaseId("FIRE").build());

    assertTrue(result.getStatus() == EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED
        || result.getStatus() == EngineeringCalculationResult.Status.CALCULATED);
    assertNotNull(result.getValue().getDynamicHandoff().getResult());
    assertTrue(result.getValue().getGoverningMassFlowKgPerS() > 0.0);
    assertTrue(result.getValue().getSteadyLoadByConcurrencyGroupKgPerS().containsKey("FIRE-ZONE-1"));
    assertTrue(result.getValue().toJson().contains("scenarioCredibilityRequiresHazardReview"));
  }

  private SystemInterface fluid() {
    SystemInterface fluid = new SystemSrkEos(313.15, 60.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");
    return fluid;
  }
}
