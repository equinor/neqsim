package neqsim.process.engineering.calculation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.Map;
import neqsim.process.engineering.calculation.Iso5167OrificeMeteringKernel.Input;
import neqsim.process.engineering.calculation.Iso5167OrificeMeteringKernel.ServiceType;
import neqsim.process.engineering.calculation.Iso5167OrificeMeteringKernel.TapType;
import neqsim.process.mechanicaldesign.designstandards.StandardEdition;
import neqsim.process.mechanicaldesign.designstandards.StandardType;
import org.junit.jupiter.api.Test;

/** Tests edition, scope, readiness, immutability, and numeric identities of the ISO 5167 adapter. */
class Iso5167OrificeMeteringKernelTest {
  private final Iso5167OrificeMeteringKernel kernel = new Iso5167OrificeMeteringKernel();

  @Test
  void currentEditionCalculatesReviewGatedLiquidResult() {
    Input input = validLiquidInput();

    EngineeringCalculationResult<Iso5167OrificeMeteringAssessment> result = kernel.calculate(input, null);
    Iso5167OrificeMeteringAssessment value = result.getValue();

    assertEquals(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED, result.getStatus());
    assertNotNull(value);
    assertEquals("ISO-5167-2 2022", value.getStandardEdition());
    assertEquals("ISO-5167-1 2022", value.getCompanionStandardEdition());
    assertEquals(0.5, value.getBetaRatio(), 1.0e-12);
    assertEquals(20000.0, value.getDifferentialPressurePa(), 1.0e-12);
    assertEquals(1.0, value.getExpansibilityFactor(), 0.0);
    assertTrue(value.getMassFlowRateKgPerS() > 0.0);
    assertEquals(value.getMassFlowRateKgPerS() / input.getUpstreamDensityKgPerM3(),
        value.getActualVolumeFlowRateM3PerS(), 1.0e-12);
    assertTrue(value.getPipeReynoldsNumber() >= 5000.0);
    assertEquals(Boolean.TRUE, value.toMap().get("engineeringApprovalRequired"));
    assertEquals(Boolean.TRUE, result.toMap().get("engineeringApprovalRequired"));
  }

  @Test
  void gasExpansibilityReducesFlowWithoutMutatingLiquidInput() {
    Input liquidInput = validLiquidInput();
    Iso5167OrificeMeteringAssessment liquid = kernel.calculate(liquidInput, null).getValue();
    Iso5167OrificeMeteringAssessment gas = kernel.calculate(validGasInput(), null).getValue();

    assertEquals(ServiceType.LIQUID, liquidInput.getServiceType());
    assertEquals(1.0, liquid.getExpansibilityFactor(), 0.0);
    assertTrue(gas.getExpansibilityFactor() > 0.0);
    assertTrue(gas.getExpansibilityFactor() < 1.0);
    assertEquals("GAS_OR_VAPOUR", gas.getServiceType());
  }

  @Test
  void inputRetainsRawInvalidValuesAndCalculationFailsClosed() {
    Input input = baseBuilder().pipeInternalDiameterM(0.04).orificeBoreDiameterM(0.05)
        .upstreamPressurePaAbsolute(400000.0).downstreamPressurePaAbsolute(500000.0).upstreamDensityKgPerM3(-1.0)
        .build();

    EngineeringCalculationResult<Iso5167OrificeMeteringAssessment> result = kernel.calculate(input, null);

    assertEquals(0.04, input.getPipeInternalDiameterM(), 0.0);
    assertEquals(-1.0, input.getUpstreamDensityKgPerM3(), 0.0);
    assertEquals(EngineeringCalculationResult.Status.BLOCKED, result.getStatus());
    assertNull(result.getValue());
    assertFalse(result.getReadiness().isReady());
  }

  @Test
  void exactEditionAndApplicabilityAreEnforced() {
    StandardEdition historical = StandardEdition.of(StandardType.ISO_5167_2, "2003");
    StandardEdition amended = StandardEdition.of(StandardType.ISO_5167_2, "2022", Arrays.asList("Project amendment A"));

    assertTrue(kernel.supports(StandardEdition.defaultEdition(StandardType.ISO_5167_2)));
    assertFalse(kernel.supports(historical));
    assertFalse(kernel.supports(amended));
    assertFalse(kernel.assess(copyWithBasis(historical, "Orifice"), null).isReady());
    assertFalse(
        kernel.assess(copyWithBasis(StandardEdition.defaultEdition(StandardType.ISO_5167_2), "Valve"), null).isReady());
    assertThrows(IllegalArgumentException.class,
        () -> Input.builder(StandardEdition.defaultEdition(StandardType.ISO_5167_1), "Orifice"));
  }

  @Test
  void publishedScopeAttestationsAndLowReynoldsNumberFailClosed() {
    Input missingAttestations = Input.builder(StandardEdition.defaultEdition(StandardType.ISO_5167_2), "Orifice")
        .serviceType(ServiceType.LIQUID).tapType(TapType.FLANGE).pipeInternalDiameterM(0.1).orificeBoreDiameterM(0.05)
        .upstreamPressurePaAbsolute(500000.0).downstreamPressurePaAbsolute(480000.0).upstreamDensityKgPerM3(998.0)
        .upstreamDynamicViscosityPaS(0.001).build();
    Input lowReynolds = baseBuilder().upstreamDynamicViscosityPaS(100.0).build();

    assertFalse(kernel.assess(missingAttestations, null).isReady());
    assertFalse(kernel.assess(lowReynolds, null).isReady());
    assertEquals(EngineeringCalculationResult.Status.BLOCKED, kernel.calculate(lowReynolds, null).getStatus());
  }

  @Test
  void documentedExampleIsRunnable() {
    Input input = validGasInput();
    EngineeringCalculationResult<Iso5167OrificeMeteringAssessment> result = new Iso5167OrificeMeteringKernel()
        .calculate(input, null);
    Map<String, Object> report = result.getValue().toMap();

    assertEquals(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED, result.getStatus());
    assertTrue(((Double) report.get("massFlowRateKgPerS")).doubleValue() > 0.0);
    assertTrue(((Double) report.get("expansibilityFactor")).doubleValue() < 1.0);
  }

  private static Input validLiquidInput() {
    return baseBuilder().build();
  }

  private static Input validGasInput() {
    return baseBuilder().serviceType(ServiceType.GAS_OR_VAPOUR).upstreamDensityKgPerM3(5.5)
        .upstreamDynamicViscosityPaS(1.2e-5).isentropicExponent(1.30).build();
  }

  private static Input copyWithBasis(StandardEdition edition, String equipmentType) {
    return baseBuilder(edition, equipmentType).build();
  }

  private static Input.Builder baseBuilder() {
    return baseBuilder(StandardEdition.defaultEdition(StandardType.ISO_5167_2), "Orifice");
  }

  private static Input.Builder baseBuilder(StandardEdition edition, String equipmentType) {
    return Input.builder(edition, equipmentType).serviceType(ServiceType.LIQUID).tapType(TapType.FLANGE)
        .pipeInternalDiameterM(0.1).orificeBoreDiameterM(0.05).upstreamPressurePaAbsolute(500000.0)
        .downstreamPressurePaAbsolute(480000.0).upstreamDensityKgPerM3(998.0).upstreamDynamicViscosityPaS(0.001)
        .singlePhase(true).conduitRunningFull(true).subsonicThroughoutMeter(true).pulsatingFlow(false)
        .geometryAndInstallationVerified(true);
  }
}
