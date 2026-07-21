package neqsim.process.engineering.calculation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import neqsim.process.mechanicaldesign.designstandards.StandardApplicability;
import neqsim.process.mechanicaldesign.designstandards.StandardEdition;
import neqsim.process.mechanicaldesign.designstandards.StandardSupportLevel;
import neqsim.process.mechanicaldesign.designstandards.StandardType;
import neqsim.process.safety.overpressure.ProtectedItem;
import neqsim.process.safety.overpressure.ReliefCause;
import neqsim.process.safety.overpressure.ReliefPhase;
import neqsim.process.safety.overpressure.ReliefScenario;

/** Tests the fail-closed common engineering adapter for API 521 screening. */
class Api521ReliefDesignKernelTest {
  @Test
  void registryExposesScreeningKernel() {
    EquipmentDesignKernelRegistry.Lookup lookup = EquipmentDesignKernelRegistry.lookup(StandardType.API_521);

    assertEquals(EquipmentDesignKernelRegistry.Status.IMPLEMENTED, lookup.getStatus());
    assertEquals("Api521ReliefDesignKernel", lookup.getImplementationClassName());
    assertEquals(StandardSupportLevel.SCREENING, lookup.getMaturity());
    assertTrue(lookup.supports(StandardEdition.defaultEdition(StandardType.API_521)));
  }

  @Test
  void selectsGoverningScenarioWithoutMutatingInput() throws Exception {
    ProtectedItem item = new ProtectedItem("V-100", 100.0).setReliefSetPressureBara(100.0)
        .setBackPressureBara(1.0);
    ReliefScenario blockedOutlet = vapourScenario("blocked outlet", ReliefCause.BLOCKED_OUTLET, 1.0);
    ReliefScenario fire = vapourScenario("pool fire", ReliefCause.FIRE, 2.0);
    Api521ReliefDesignKernel.Input input = new Api521ReliefDesignKernel.Input(
        StandardEdition.defaultEdition(StandardType.API_521), "ProtectedItem", item,
        Arrays.asList(blockedOutlet, fire), false);
    item.setMaximumAllowableWorkingPressureBara(1.0).setReliefSetPressureBara(1.0);

    EngineeringCalculationResult<Api521ReliefAssessment> result = new Api521ReliefDesignKernel().calculate(input,
        EngineeringCalculationContext.builder().designCaseId("fire").build());

    assertEquals(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED, result.getStatus());
    assertEquals("pool fire", result.getValue().getGoverningScenarioName());
    assertEquals(ReliefCause.FIRE.name(), result.getValue().getGoverningCause());
    assertEquals(2.0, result.getValue().getGoverningReliefRateKgPerS(), 1.0e-12);
    assertTrue(result.getValue().getRequiredAreaM2() > 0.0);
    assertTrue(result.getValue().isCapacityAdequate());
    assertTrue(result.getValue().isAccumulatedPressureAccepted());
    assertEquals(2, result.getValue().getScenarios().size());
    assertThrows(UnsupportedOperationException.class, () -> result.getValue().getScenarios().clear());
    assertThrows(UnsupportedOperationException.class,
        () -> result.getValue().getScenarios().get(0).getAssumptions().clear());
    assertEquals("V-100", roundTrip(result.getValue()).getProtectedItemName());
    input.getProtectedItem().setMaximumAllowableWorkingPressureBara(5.0);
    assertEquals(100.0, input.getProtectedItem().getMaximumAllowableWorkingPressureBara(), 1.0e-12);
  }

  @Test
  void blocksUnsupportedEditionEquipmentAndIncompleteProperties() {
    Api521ReliefDesignKernel kernel = new Api521ReliefDesignKernel();
    ProtectedItem item = new ProtectedItem("V-100", 100.0);
    ReliefScenario incomplete = new ReliefScenario.Builder("incomplete", ReliefCause.BLOCKED_OUTLET)
        .phase(ReliefPhase.VAPOUR).reliefRateKgPerS(1.0).build();

    Api521ReliefDesignKernel.Input oldEdition = new Api521ReliefDesignKernel.Input(
        StandardEdition.of(StandardType.API_521, "6th Ed"), "ProtectedItem", item,
        Collections.singletonList(vapourScenario("case", ReliefCause.OTHER, 1.0)), false);
    Api521ReliefDesignKernel.Input wrongEquipment = new Api521ReliefDesignKernel.Input(
        StandardEdition.defaultEdition(StandardType.API_521), "Pump", item,
        Collections.singletonList(vapourScenario("case", ReliefCause.OTHER, 1.0)), false);
    Api521ReliefDesignKernel.Input incompleteInput = new Api521ReliefDesignKernel.Input(
        StandardEdition.defaultEdition(StandardType.API_521), "SafetyValve", item,
        Collections.singletonList(incomplete), false);

    assertEquals(EngineeringCalculationResult.Status.BLOCKED,
        kernel.calculate(oldEdition, EngineeringCalculationContext.builder().build()).getStatus());
    assertEquals(StandardApplicability.Status.NOT_APPLICABLE, kernel.applicability(wrongEquipment).getStatus());
    assertEquals(EngineeringCalculationResult.Status.BLOCKED,
        kernel.calculate(wrongEquipment, EngineeringCalculationContext.builder().build()).getStatus());
    EngineeringCalculationResult<Api521ReliefAssessment> incompleteResult = kernel.calculate(incompleteInput,
        EngineeringCalculationContext.builder().build());
    assertEquals(EngineeringCalculationResult.Status.BLOCKED, incompleteResult.getStatus());
    assertFalse(incompleteResult.getReadiness().isReady());
    assertThrows(IllegalArgumentException.class,
        () -> new Api521ReliefDesignKernel.Input(StandardEdition.defaultEdition(StandardType.API_526), "SafetyValve",
            item, Collections.singletonList(incomplete), false));
  }

  private static ReliefScenario vapourScenario(String name, ReliefCause cause, double rateKgPerS) {
    return new ReliefScenario.Builder(name, cause).phase(ReliefPhase.VAPOUR).reliefRateKgPerS(rateKgPerS)
        .reliefTemperatureK(320.0).molarMassKgPerMol(0.020).compressibility(0.95).specificHeatRatio(1.25)
        .addAssumption("screening property basis").build();
  }

  private static Api521ReliefAssessment roundTrip(Api521ReliefAssessment assessment) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ObjectOutputStream output = new ObjectOutputStream(bytes);
    output.writeObject(assessment);
    output.close();
    ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    Api521ReliefAssessment copy = (Api521ReliefAssessment) input.readObject();
    input.close();
    return copy;
  }
}
