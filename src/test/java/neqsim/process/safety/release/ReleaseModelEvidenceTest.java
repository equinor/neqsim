package neqsim.process.safety.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Contract and immutability tests for explicit release-model evidence manifests. */
class ReleaseModelEvidenceTest extends neqsim.NeqSimTest {

  @Test
  void builtInModelsDeclareApplicabilityLimitationsAndRetainedEvidence() {
    List<ReleaseFlowModel> models = Arrays.<ReleaseFlowModel>asList(new IdealGasReleaseModel(),
        new HomogeneousEquilibriumReleaseModel(), new LegacyScreeningReleaseModel(),
        new IdealGasFannoPipeReleaseModel());
    for (ReleaseFlowModel model : models) {
      ReleaseModelEvidence evidence = model.getEvidence();
      assertTrue(evidence.getManifestId().startsWith(model.getModelId() + ":"));
      assertFalse(evidence.getApplicability().isEmpty());
      assertFalse(evidence.getLimitations().isEmpty());
      assertFalse(evidence.getRecords().isEmpty());
      assertFalse(evidence.hasIndependentEvidence());
      assertTrue(evidence.getLimitations().contains("NO_EXPERIMENTAL_QUALIFICATION")
          || evidence.getLimitations().contains("NO_ENGINEERING_QUALIFICATION"));
      assertThrows(UnsupportedOperationException.class, () -> evidence.getApplicability().add("OTHER"));
      assertThrows(UnsupportedOperationException.class, () -> evidence.getRecords().clear());
    }
  }

  @Test
  void constructorDefensivelyCopiesAndRejectsUnstableCodes() {
    List<String> applicability = new ArrayList<String>(Collections.singletonList("SINGLE_GAS_PHASE"));
    List<String> limitations = new ArrayList<String>(Collections.singletonList("NO_EXPERIMENTAL_QUALIFICATION"));
    List<ReleaseModelEvidence.Record> records = new ArrayList<ReleaseModelEvidence.Record>();
    records.add(new ReleaseModelEvidence.Record("case", ReleaseModelEvidence.Type.ANALYTICAL, "reference",
        "description", false));
    ReleaseModelEvidence evidence = new ReleaseModelEvidence("model:1.0.0", applicability, limitations, records);
    applicability.clear();
    limitations.clear();
    records.clear();
    assertEquals(Collections.singletonList("SINGLE_GAS_PHASE"), evidence.getApplicability());
    assertEquals(1, evidence.getRecords().size());
    assertThrows(IllegalArgumentException.class,
        () -> new ReleaseModelEvidence("invalid", Collections.singletonList("not-stable"),
            Collections.singletonList("LIMIT"), Collections.<ReleaseModelEvidence.Record>emptyList()));
  }

  @Test
  void sourceFrameExportsSchemaValidatedUnqualifiedManifest() {
    SystemInterface gas = new SystemSrkEos(300.0, 10.0);
    gas.addComponent("nitrogen", 1.0);
    gas.setMixingRule("classic");
    ReleaseFlowRequest request = new ReleaseFlowRequest(gas, 0.01, 0.62, 101325.0);
    ReleaseFlowResult result = new IdealGasReleaseModel().calculate(request);
    SourceTermFrame frame = SourceTermFrame.calculated("evidence", "opening", UUID.randomUUID(), 0, 0.0,
        Instant.parse("2026-09-23T00:00:00Z"), request, result, Collections.singletonMap("mode", "STEADY"));
    SourceTermFrame.verifyEnvelope(frame.toJson());
    JsonObject model = JsonParser.parseString(frame.toJson()).getAsJsonObject().getAsJsonObject("model");
    assertEquals("UNQUALIFIED", model.get("evidenceLevel").getAsString());
    JsonObject evidence = model.getAsJsonObject("evidence");
    assertEquals("ideal-gas-isentropic-orifice:1.0.0", evidence.get("manifestId").getAsString());
    assertFalse(evidence.get("independentEvidence").getAsBoolean());
    assertTrue(evidence.getAsJsonArray("applicability").toString().contains("SINGLE_GAS_PHASE"));
    assertEquals(2, evidence.getAsJsonArray("records").size());
  }

  @Test
  void customModelsWithoutDeclarationsFailClosedToUndeclaredEvidence() {
    ReleaseFlowModel custom = new ReleaseFlowModel() {
      private static final long serialVersionUID = 1L;

      @Override
      public String getModelId() {
        return "custom";
      }

      @Override
      public ReleaseFlowResult calculate(ReleaseFlowRequest request) {
        return ReleaseFlowResult.failure(this, true, "NOT_IMPLEMENTED", "test model");
      }
    };
    ReleaseFlowResult result = custom.calculate(null);
    assertEquals("custom:1.0.0:unqualified", result.getEvidence().getManifestId());
    assertEquals(Collections.singletonList("NO_DECLARED_VALIDATION_EVIDENCE"), result.getEvidence().getLimitations());
    assertFalse(result.getEvidence().hasIndependentEvidence());
  }
}
