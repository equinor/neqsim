package neqsim.process.safety.risk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;
import neqsim.process.safety.risk.sis.LOPAResult;
import neqsim.process.safety.risk.sis.SISIntegratedRiskModel;
import neqsim.process.safety.risk.sis.SafetyInstrumentedFunction;

/** Verifies the executable LOPA example in {@code docs/risk/index.md}. */
class RiskDocumentationTest {

  @Test
  void testRiskFrameworkQuickStart() {
    String eventName = "HP vessel overpressure";

    SISIntegratedRiskModel model = new SISIntegratedRiskModel("HP vessel LOPA");
    model.addInitiatingEvent(eventName, 0.1, RiskEvent.ConsequenceCategory.MAJOR);

    SISIntegratedRiskModel.IndependentProtectionLayer bpcs =
        new SISIntegratedRiskModel.IndependentProtectionLayer(
            "BPCS pressure control", 0.1,
            SISIntegratedRiskModel.IndependentProtectionLayer.IPLType.BPCS);
    bpcs.addApplicableEvent(eventName);
    model.addIPL(bpcs);

    SafetyInstrumentedFunction esd =
        SafetyInstrumentedFunction.builder().id("SIF-001").name("High-pressure ESD")
            .description("Isolate the HP vessel on confirmed high pressure").sil(2).pfd(0.005)
            .initiatingEvent(eventName).addProtectedEquipment("HP vessel").safeState("Isolated").build();
    model.addSIF(esd);

    LOPAResult result = model.performLOPA(eventName);

    assertEquals(5.0e-5, result.getMitigatedFrequency(), 1.0e-12);
    assertEquals(2000.0, result.getTotalRRF(), 1.0e-9);
    assertEquals(2, result.getLayers().size());
    assertEquals("SIF-001", esd.getId());
    assertEquals(2, esd.getSil());
    assertEquals(0.005, esd.getPfdAvg(), 1.0e-12);
    assertEquals("Isolated", esd.getSafeState());
    assertFalse(esd.getProtectedEquipment().isEmpty());
  }
}
