package neqsim.process.safety.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.safety.release.ReleaseSolidRiskAssessment.Status;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Mixture-specific, fail-closed applicability checks for solid-free release models. */
class ReleaseSolidRiskAssessmentTest extends neqsim.NeqSimTest {

  @Test
  void coldCo2MixtureDetectsEquilibriumSolidWithoutChangingInput() {
    SystemInterface fluid = new SystemSrkEos(210.0, 20.0);
    fluid.addComponent("methane", 0.1);
    fluid.addComponent("CO2", 0.9);
    fluid.setMixingRule("classic");

    ReleaseSolidRiskAssessment result = ReleaseSolidRiskAssessment.assess(fluid);

    assertEquals(Status.SOLID_RISK, result.getStatus());
    assertEquals("CO2", result.getComponent());
    assertTrue(result.getSolidMassFraction() > 0.0);
    assertEquals(210.0, result.getStationTemperatureK());
    assertEquals(20.0, fluid.getPressure());
    assertEquals(210.0, fluid.getTemperature());
    assertFalse(fluid.doSolidPhaseCheck());
  }

  @Test
  void warmCo2MixtureIsNotRejectedByPureComponentTriplePointAlone() {
    SystemInterface fluid = new SystemSrkEos(230.0, 20.0);
    fluid.addComponent("methane", 0.1);
    fluid.addComponent("CO2", 0.9);
    fluid.setMixingRule("classic");

    ReleaseSolidRiskAssessment result = ReleaseSolidRiskAssessment.assess(fluid);

    assertEquals(Status.CLEAR, result.getStatus(), result.getMessage());
  }

  @Test
  void hydrateFormerMixtureUsesCalculatedEquilibriumBoundary() {
    SystemInterface fluid = new SystemSrkEos(276.0, 80.0);
    fluid.addComponent("methane", 0.99);
    fluid.addComponent("water", 0.01);
    fluid.setMixingRule(2);

    ReleaseSolidRiskAssessment result = ReleaseSolidRiskAssessment.assess(fluid);

    assertEquals(Status.HYDRATE_RISK, result.getStatus(), result.getMessage());
    assertEquals("water", result.getComponent());
    assertTrue(result.getBoundaryTemperatureK() >= result.getStationTemperatureK());
    assertFalse(fluid.getHydrateCheck());
  }

  @Test
  void homogeneousModelEmitsMachineReadableApplicabilityEvidence() {
    SystemInterface methane = new SystemSrkEos(300.0, 5.0);
    methane.addComponent("methane", 1.0);
    methane.setMixingRule("classic");

    ReleaseFlowResult result = new HomogeneousEquilibriumReleaseModel()
        .calculate(new ReleaseFlowRequest(methane, 0.01, 0.62, 1e5));

    assertTrue(result.isUsable(), result.getDiagnostics().get(0).getMessage());
    assertEquals(3, result.getDiagnostics().stream()
        .filter(diagnostic -> "SOLID_RISK_ASSESSED".equals(diagnostic.getCode())).count());
  }
}
