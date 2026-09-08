package neqsim.process.processmodel.diagram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.ContentProfile;
import neqsim.process.engineering.model.EngineeringNode;
import neqsim.process.engineering.pid.PidDesignModel;
import neqsim.process.engineering.pid.PidElement;
import neqsim.process.engineering.pid.PidElementType;
import org.junit.jupiter.api.Test;

/** Regression coverage for source fidelity and the distinction between material and control connections. */
class EngineeringDiagramPidRegistersTest {
  @Test
  void retainsCanonicalLinesAndProvenanceWithoutCountingMaterialLinksAsSignals() {
    EngineeringDiagramDocumentSet document = document();
    PidDesignModel model = new PidDesignModel("PLANT", "TEST");
    PidElement nozzleIn = element("NZ-IN", PidElementType.NOZZLE).connect("NZ-OUT");
    model.add(nozzleIn).add(element("NZ-OUT", PidElementType.NOZZLE));
    model.add(element("PT", PidElementType.MEASUREMENT).connect("PIC"));
    model.add(element("PIC", PidElementType.CONTROLLER).connect("PCV"));
    model.add(element("PCV", PidElementType.CONTROL_VALVE));
    model.add(element("PSV", PidElementType.SAFETY_RELIEF_VALVE).connect("FLARE"));
    model.add(element("FLARE", PidElementType.OFF_PAGE_CONNECTOR));
    EngineeringDiagramPidRegisters registers = EngineeringDiagramPidRegisters.fromDocumentSet(document, model);

    long canonicalLineCount = document.getSemanticObjects().stream()
        .filter(object -> object.getKind() == EngineeringNode.Kind.PIPE_SEGMENT).count();
    assertEquals(canonicalLineCount, registers.getLineCount());
    assertEquals(2, registers.getNozzleCount());
    assertEquals(2, registers.getValveCount());
    assertEquals(2, registers.getInstrumentCount());
    assertEquals(2, registers.getControlSignalCount());
    assertEquals(1, registers.getInterfaceCount());
    assertTrue(registers.toJson().contains("PROJECT_INPUT_REQUIRED"));
    assertTrue(registers.toJson().contains("NO_GOVERNED_REDUCER_DECLARATION"));
    assertTrue(registers.toJson().contains("TEST-RULE"));
    List<?> signals = (List<?>) registers.toMap().get("controlSignals");
    assertEquals("PT", ((Map<?, ?>) signals.get(0)).get("sourcePidElementId"));
    assertEquals("PIC", ((Map<?, ?>) signals.get(1)).get("sourcePidElementId"));
    assertFalse(signals.toString().contains("PSV"));
    assertFalse(signals.toString().contains("NZ-IN"));
  }

  @Test
  void snapshotsNestedProposalEvidenceAndRejectsForeignEquipment() {
    EngineeringDiagramDocumentSet document = document();
    List<String> evidence = new ArrayList<String>(Arrays.asList("original-evidence"));
    PidElement nozzle = element("NZ", PidElementType.NOZZLE).attribute("evidence", evidence);
    PidDesignModel model = new PidDesignModel("PLANT", "TEST").add(nozzle);
    EngineeringDiagramPidRegisters registers = EngineeringDiagramPidRegisters.fromDocumentSet(document, model);
    String snapshot = registers.toJson();

    evidence.add("later-change");
    nozzle.attribute("newAttribute", "later-change");
    assertEquals(snapshot, registers.toJson());
    assertThrows(UnsupportedOperationException.class, () -> registers.toMap().clear());
    Map<?, ?> row = (Map<?, ?>) ((List<?>) registers.toMap().get("nozzles")).get(0);
    assertThrows(UnsupportedOperationException.class, () -> ((Map<?, ?>) row.get("attributes")).clear());
    assertThrows(UnsupportedOperationException.class,
        () -> ((List<?>) ((Map<?, ?>) row.get("attributes")).get("evidence")).clear());
    assertThrows(IllegalArgumentException.class,
        () -> EngineeringDiagramPidRegisters.fromDocumentSet(document, new PidDesignModel("OTHER", "TEST")));
    assertThrows(IllegalArgumentException.class, () -> EngineeringDiagramPidRegisters.fromDocumentSet(document,
        new PidDesignModel("PLANT", "TEST").add(element("BAD", PidElementType.NOZZLE).equipment("24-VB-01"))));
  }

  @Test
  void doesNotInventProposalsForAnEmptyDesignModel() {
    EngineeringDiagramPidRegisters registers = EngineeringDiagramPidRegisters.fromDocumentSet(document(),
        new PidDesignModel("PLANT", "EMPTY"));
    assertEquals(0, registers.getNozzleCount());
    assertEquals(0, registers.getValveCount());
    assertEquals(0, registers.getInstrumentCount());
    assertEquals(0, registers.getControlSignalCount());
    assertEquals(0, registers.getInterfaceCount());
    assertTrue(registers.getGapCount() > 0);
  }

  private static PidElement element(String id, PidElementType type) {
    return new PidElement(id, id, type).equipment("10-VA-001").provenance("TEST-RULE", "Regression evidence");
  }

  private static EngineeringDiagramDocumentSet document() {
    return ProcessDiagramDocumentSetAdapter.fromProcessSystem(
        EngineeringDiagramReferenceFixtures.simpleTrain().getProcessSystem(), "PLANT", "A", "PID-001",
        "Register regression", ContentProfile.PID);
  }
}
