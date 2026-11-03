package neqsim.process.processmodel.diagram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.LineDesignInput;
import neqsim.process.engineering.NorsokOffshoreEngineeringBuilder;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.ContentProfile;
import neqsim.process.engineering.model.EngineeringNode;
import neqsim.process.engineering.pid.PidDesignModel;
import neqsim.process.engineering.pid.PidElement;
import neqsim.process.engineering.pid.PidElementType;
import neqsim.process.engineering.pid.PidPhysicalProjection;
import neqsim.process.processmodel.ProcessSystem;
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
    Map<String, Object> firstView = registers.toMap();
    Map<String, Object> secondView = registers.toMap();
    assertNotSame(firstView, secondView);
    assertNotSame(firstView.get("nozzles"), secondView.get("nozzles"));
    assertThrows(UnsupportedOperationException.class, () -> firstView.clear());
    Map<?, ?> row = (Map<?, ?>) ((List<?>) firstView.get("nozzles")).get(0);
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

  /**
   * Verifies supplied synthetic line inputs and actual product boundaries remain proposals with real source evidence.
   */
  @Test
  void retainsProjectLineInputsAndConfiguredProductBoundaryWithoutApproval() {
    ProcessSystem process = EngineeringDiagramReferenceFixtures.simpleTrain().getProcessSystem();
    process.getUnit("10-HA-001").getOutletStreams().get(0).setName("10-GAS-PRODUCT");
    EngineeringDiagramDocumentSet document = ProcessDiagramDocumentSetAdapter.fromProcessSystem(process, "PLANT", "A",
        "PID-001", "Project line evidence", ContentProfile.PID);
    EngineeringDiagramDocumentSet.SemanticObject canonical = document.getSemanticObjects().stream()
        .filter(object -> object.getKind() == EngineeringNode.Kind.PIPE_SEGMENT).findFirst().get();
    String canonicalTag = canonical.getProperties().get("carriedObjectName").toString();
    EngineeringProject project = NorsokOffshoreEngineeringBuilder.from("Synthetic lines", process, "2.0.1")
        .projectId("PLANT").registerProposedInstruments(false).build().setRevision("A");
    LineDesignInput supplied = completeLine(canonicalTag, "10-XV-001");
    project.addLineDesignInput(supplied).addLineDesignInput(completeLine("10-GAS-PRODUCT", "10-HA-001"))
        .addLineDesignInput(completeLine("NOT-AN-ACTUAL-STREAM", "10-HA-001"));
    PidDesignModel model = new PidDesignModel("PLANT", "EMPTY");
    EngineeringDiagramPidRegisters registers = EngineeringDiagramPidRegisters.fromDocumentSet(document, model, project,
        PidPhysicalProjection.resolve(process, model, null));

    Map<?, ?> line = lineByTag(registers, canonicalTag);
    assertEquals(canonical.getId(), line.get("semanticConnectionId"));
    assertEquals("DN 80", line.get("nominalPipeSize"));
    assertEquals("SYNTHETIC-CLASS", line.get("pipingClass"));
    assertEquals("40", line.get("schedule"));
    assertEquals("SYNTHETIC-MATERIAL", line.get("materialGrade"));
    assertEquals(75.0, ((Number) line.get("designPressureBara")).doubleValue(), 0.0);
    assertEquals(90.0, ((Number) line.get("designTemperatureC")).doubleValue(), 0.0);
    assertEquals(0.0889, ((Number) line.get("outerDiameterM")).doubleValue(), 1.0e-10);
    assertEquals(Arrays.asList("SYNTHETIC-LINE-LIST-A"), line.get("evidenceReferences"));
    assertEquals("SUPPLIED_REVIEW_REQUIRED", line.get("lineDesignInputStatus"));
    assertEquals("REVIEW_REQUIRED", line.get("qualificationStatus"));
    Map<?, ?> product = lineByTag(registers, "10-GAS-PRODUCT");
    assertEquals("ACTUAL_STREAM_BOUNDARY", product.get("lineOrigin"));
    assertEquals("10-HA-001", product.get("sourceEquipmentTag"));
    assertEquals("10-GAS-PRODUCT", product.get("sourceStreamTag"));
    assertEquals("", product.get("targetEquipmentTag"));
    assertEquals("OUTLET", product.get("boundaryDirection"));
    assertFalse(product.containsKey("semanticConnectionId"));
    assertTrue(registers.toJson().contains("PID-LINE-INPUT-UNMATCHED"));
    assertFalse(((List<?>) registers.toMap().get("lines")).stream()
        .anyMatch(row -> "NOT-AN-ACTUAL-STREAM".equals(((Map<?, ?>) row).get("lineTag"))));
    assertEquals(Boolean.FALSE, registers.toMap().get("fitnessForConstruction"));
    EngineeringDiagramPidRegisters legacy = EngineeringDiagramPidRegisters.fromDocumentSet(document, model);
    assertEquals(legacy.getLineCount() + 1, registers.getLineCount());
    assertFalse(legacy.toJson().contains("SYNTHETIC-LINE-LIST-A"));
    assertTrue(legacy.toJson().contains("PROJECT_INPUT_REQUIRED"));
    supplied.addEvidenceReference("LATER-CHANGE");
    assertFalse(registers.toJson().contains("LATER-CHANGE"));
  }

  /** Verifies one line per auxiliary series and exact shared designations, source endpoints and missing-input HOLDs. */
  @Test
  void groupsAuxiliaryRoutesBySharedMaterialLineTagAndRetainsEndpoints() {
    ProcessSystem process = EngineeringDiagramReferenceFixtures.simpleTrain().getProcessSystem();
    EngineeringProject project = NorsokOffshoreEngineeringBuilder.from("Synthetic auxiliary lines", process, "2.0.1")
        .projectId("PLANT").registerProposedInstruments(false).build().setRevision("A");
    EngineeringDiagramDocumentSet document = ProcessDiagramDocumentSetAdapter.fromProcessSystem(process, "PLANT", "A",
        "PID-001", "Auxiliary line evidence", ContentProfile.PID);
    PidDesignModel model = new PidDesignModel("PLANT", "TEST");
    model.add(utilitySeriesValve("TCV-CW", PidElementType.CONTROL_VALVE, 20));
    model.add(utilitySeriesValve("SDV-CW", PidElementType.SHUTDOWN_VALVE, 10));
    model.add(new PidElement("drain", "DV-CW", PidElementType.DRAIN).equipment("10-HA-001")
        .attribute("physicalSourceEquipmentTag", "10-HA-001").attribute("physicalSourceNozzleTag", "CW-DRAIN")
        .attribute("physicalSourcePortRole", "UTILITY").attribute("destinationTag", "CW-DRAIN-HEADER")
        .attribute("designEvidenceReference", "SYNTHETIC-CW-BASIS"));
    PidPhysicalProjection projection = PidPhysicalProjection.resolve(process, model, null);
    assertEquals(PidPhysicalProjection.Kind.AUXILIARY_ROUTE, projection.getAttachments().get("TCV-CW").getKind());
    EngineeringDiagramPidRegisters registers = EngineeringDiagramPidRegisters.fromDocumentSet(document, model, project,
        projection);

    Map<?, ?> line = lineByTag(registers, projection.getAttachments().get("TCV-CW").getMaterialLineTag());
    assertEquals("10-CW-RETURN", line.get("lineTag"));
    assertEquals("DECLARED_AUXILIARY_ROUTE", line.get("lineOrigin"));
    assertEquals(Arrays.asList("SDV-CW", "TCV-CW"), line.get("sourceProposalTags"));
    assertEquals("10-HA-001", line.get("physicalSourceEquipmentTag"));
    assertEquals("CW-RETURN", line.get("physicalSourceNozzleTag"));
    assertEquals("UTILITY", line.get("physicalSourcePortRole"));
    assertEquals("CW-RETURN-HEADER", line.get("destinationTag"));
    assertEquals("", line.get("physicalTargetEquipmentTag"));
    assertEquals(2, ((List<?>) line.get("sourceAttachments")).size());
    assertFalse(line.containsKey("semanticConnectionId"));
    assertEquals("HOLD", line.get("sizingStatus"));
    assertEquals("HOLD", line.get("pipingClassStatus"));
    assertEquals("PROJECT_INPUT_REQUIRED", line.get("nominalPipeSize"));
    assertEquals("REVIEW_REQUIRED", line.get("qualificationStatus"));
    Map<?, ?> independent = lineByTag(registers, "DV-CW-ROUTE");
    assertEquals(Arrays.asList("DV-CW"), independent.get("sourceProposalTags"));
    assertEquals("CW-DRAIN", independent.get("physicalSourceNozzleTag"));
    assertEquals(EngineeringDiagramPidRegisters.fromDocumentSet(document, model).getLineCount() + 2,
        registers.getLineCount());
  }

  /** Verifies conflicting project line inputs cannot be silently selected by insertion order. */
  @Test
  void rejectsAmbiguousProjectLineDesignTags() {
    ProcessSystem process = EngineeringDiagramReferenceFixtures.simpleTrain().getProcessSystem();
    EngineeringProject project = NorsokOffshoreEngineeringBuilder.from("Ambiguous lines", process, "2.0.1")
        .projectId("PLANT").registerProposedInstruments(false).build();
    project.addLineDesignInput(completeLine("LINE-1", "10-HA-001"))
        .addLineDesignInput(completeLine("LINE-1", "10-HA-001"));
    PidDesignModel model = new PidDesignModel("PLANT", "EMPTY");
    assertThrows(IllegalArgumentException.class, () -> EngineeringDiagramPidRegisters.fromDocumentSet(document(), model,
        project, PidPhysicalProjection.resolve(process, model, null)));
  }

  /**
   * Creates complete synthetic input for register fidelity checks without implying project approval.
   *
   * @param tag actual line designation
   * @param equipment source association
   * @return supplied synthetic line input
   */
  private static LineDesignInput completeLine(String tag, String equipment) {
    return new LineDesignInput(tag, equipment).setNominalPipeSize("DN 80").setPipingClass("SYNTHETIC-CLASS")
        .setSchedule("40").setMaterialGrade("SYNTHETIC-MATERIAL").setOuterDiameter(88.9, "mm")
        .setNominalWallThickness(5.49, "mm").setCorrosionAllowance(1.0, "mm").setDesignPressureBara(75.0)
        .setDesignTemperatureC(90.0).addEvidenceReference("SYNTHETIC-LINE-LIST-A");
  }

  /**
   * Creates one member of an explicitly declared utility series.
   *
   * @param tag proposal identity
   * @param type proposed valve category
   * @param order declared series position
   * @return synthetic route proposal
   */
  private static PidElement utilitySeriesValve(String tag, PidElementType type, int order) {
    return new PidElement(tag, tag, type).equipment("10-HA-001").attribute("physicalSourceEquipmentTag", "10-HA-001")
        .attribute("physicalSourceNozzleTag", "CW-RETURN").attribute("physicalSourcePortRole", "UTILITY")
        .attribute("designEvidenceReference", "SYNTHETIC-CW-BASIS").attribute("destinationTag", "CW-RETURN-HEADER")
        .attribute("physicalRouteId", "10-CW-RETURN").attribute("physicalRouteOrder", Integer.valueOf(order));
  }

  /**
   * Finds an exact line designation in the immutable register.
   *
   * @param registers register snapshot
   * @param tag exact material line designation
   * @return matching register row
   */
  private static Map<?, ?> lineByTag(EngineeringDiagramPidRegisters registers, String tag) {
    for (Object row : (List<?>) registers.toMap().get("lines")) {
      if (tag.equals(((Map<?, ?>) row).get("lineTag"))) {
        return (Map<?, ?>) row;
      }
    }
    throw new AssertionError("Missing line " + tag);
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
