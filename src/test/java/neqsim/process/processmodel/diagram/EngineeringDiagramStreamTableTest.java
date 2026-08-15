package neqsim.process.processmodel.diagram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import neqsim.process.engineering.model.EngineeringDiagramDesignationRegister;
import neqsim.process.engineering.model.EngineeringDiagramDesignationRegister.Designation;
import neqsim.process.engineering.model.EngineeringDiagramDesignationRegister.Kind;
import neqsim.process.engineering.model.EngineeringDiagramDesignationRegister.ReviewState;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.ContentProfile;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.SemanticObject;
import neqsim.process.engineering.model.EngineeringDiagramStreamTable;
import neqsim.process.engineering.model.EngineeringDiagramStreamTable.Quantity;
import neqsim.process.engineering.model.EngineeringDiagramStreamTable.Row;
import neqsim.process.engineering.model.EngineeringDiagramStreamTable.Value;
import neqsim.process.engineering.model.EngineeringNode;
import org.junit.jupiter.api.Test;

/** Regression tests for governed, deterministic stream-table projections. */
class EngineeringDiagramStreamTableTest {

  @Test
  void projectsUnitExplicitGovernedValuesWithoutChangingControlledDocument() {
    EngineeringDiagramReferenceFixtures.SystemCase reference = EngineeringDiagramReferenceFixtures.simpleTrain();
    reference.getProcessSystem().run();
    EngineeringDiagramDocumentSet documents = ProcessDiagramDocumentSetAdapter.fromProcessSystem(
        reference.getProcessSystem(), reference.getCaseId(), "A", "PFD-HMB-001", "Stream table", ContentProfile.PFD,
        "NORMAL-01");
    String documentJson = documents.toJson();

    EngineeringDiagramStreamTable table = EngineeringDiagramStreamTable.fromDocumentSet(documents, "NORMAL-01");

    assertTrue(table.isValid());
    assertFalse(table.getRows().isEmpty());
    Row row = firstCompleteRow(table);
    assertEquals(4, row.getValues().size());
    assertValue(row, Quantity.TEMPERATURE, "K", "THERMODYNAMIC_ABSOLUTE");
    assertValue(row, Quantity.PRESSURE, "bara", "ABSOLUTE");
    assertValue(row, Quantity.MASS_FLOW, "kg/s", "MASS");
    assertValue(row, Quantity.SPECIFIC_ENTHALPY, "J/kg", "MASS_SPECIFIC");
    assertEquals("NORMAL-01", table.getDesignCaseId());
    assertEquals(documents.getSourceGraphFingerprint(), table.getSourceGraphFingerprint());
    assertEquals(documentJson, documents.toJson());
    assertNotSame(table.getRows(), table.getRows());
    assertNotSame(table.getDiagnostics(), table.getDiagnostics());
    assertNotSame(row.getValues(), row.getValues());
    assertNotSame(row.getValues().get(Quantity.PRESSURE).getProvenance(),
        row.getValues().get(Quantity.PRESSURE).getProvenance());
    assertThrows(UnsupportedOperationException.class, () -> table.getRows().clear());
    assertThrows(UnsupportedOperationException.class, () -> row.getValues().clear());
  }

  @Test
  void isDeterministicForFreshMultiAreaProcessModels() {
    EngineeringDiagramReferenceFixtures.ModelCase firstReference = EngineeringDiagramReferenceFixtures
        .multiAreaFacility();
    firstReference.getProcessModel().run();
    EngineeringDiagramDocumentSet firstDocuments = ProcessDiagramDocumentSetAdapter.fromProcessModel(
        firstReference.getProcessModel(), firstReference.getCaseId(), "A", "PFD-HMB-002", "Multi-area stream table",
        ContentProfile.PFD, "NORMAL-01");

    EngineeringDiagramReferenceFixtures.ModelCase secondReference = EngineeringDiagramReferenceFixtures
        .multiAreaFacility();
    secondReference.getProcessModel().run();
    EngineeringDiagramDocumentSet secondDocuments = ProcessDiagramDocumentSetAdapter.fromProcessModel(
        secondReference.getProcessModel(), secondReference.getCaseId(), "A", "PFD-HMB-002", "Multi-area stream table",
        ContentProfile.PFD, "NORMAL-01");

    EngineeringDiagramStreamTable first = EngineeringDiagramStreamTable.fromDocumentSet(firstDocuments, "NORMAL-01");
    EngineeringDiagramStreamTable second = EngineeringDiagramStreamTable.fromDocumentSet(secondDocuments, "NORMAL-01");

    assertEquals(first.toJson(), second.toJson());
    assertTrue(first.isValid());
    assertFalse(first.getRows().isEmpty());
    assertTrue(hasArea(first));
  }

  @Test
  void prefersReviewedStreamNumberWithoutReplacingCanonicalIdentity() {
    EngineeringDiagramReferenceFixtures.SystemCase reference = EngineeringDiagramReferenceFixtures.simpleTrain();
    reference.getProcessSystem().run();
    EngineeringDiagramDocumentSet baseline = ProcessDiagramDocumentSetAdapter.fromProcessSystem(
        reference.getProcessSystem(), reference.getCaseId(), "B", "PFD-HMB-003", "Reviewed stream number",
        ContentProfile.PFD, "NORMAL-01");
    SemanticObject stream = firstLine(baseline);
    EngineeringDiagramDesignationRegister register = new EngineeringDiagramDesignationRegister().withDesignation(
        new Designation(stream.getId(), Kind.STREAM_NUMBER, "10-P-1001-A", "project-register:stream-numbers",
            ReviewState.REVIEWED, "Process discipline", "review:STREAM-42", "2026-08-15T08:00:00Z", "B"));
    EngineeringDiagramDocumentSet reviewed = ProcessDiagramDocumentSetAdapter.fromProcessSystem(
        reference.getProcessSystem(), reference.getCaseId(), "B", "PFD-HMB-003", "Reviewed stream number",
        ContentProfile.PFD, "NORMAL-01", register);

    EngineeringDiagramStreamTable table = EngineeringDiagramStreamTable.fromDocumentSet(reviewed, "NORMAL-01");
    Row row = findRow(table, stream.getId());

    assertEquals(stream.getId(), row.getSemanticObjectId());
    assertEquals(stream.getLabel(), row.getSourceLabel());
    assertEquals("10-P-1001-A", row.getDisplayIdentifier());
    assertEquals("project-register:stream-numbers", row.getDesignationSourceReference());
  }

  @Test
  void reportsMissingOperatingCaseAsStructuredLoss() {
    EngineeringDiagramReferenceFixtures.SystemCase reference = EngineeringDiagramReferenceFixtures.simpleTrain();
    EngineeringDiagramDocumentSet topologyOnly = ProcessDiagramDocumentSetAdapter.fromProcessSystem(
        reference.getProcessSystem(), reference.getCaseId(), "A", "PFD-HMB-004", "Topology only", ContentProfile.PFD);

    EngineeringDiagramStreamTable table = EngineeringDiagramStreamTable.fromDocumentSet(topologyOnly, "MISSING-CASE");

    assertFalse(table.isValid());
    assertTrue(hasDiagnostic(table, "STREAM_TABLE_CASE_NOT_FOUND"));
    assertTrue(hasDiagnostic(table, "STREAM_TABLE_VALUE_MISSING"));
    assertFalse(table.getRows().isEmpty());
  }

  private static void assertValue(Row row, Quantity quantity, String unit, String basis) {
    Value value = row.getValues().get(quantity);
    assertEquals(unit, value.getResultUnit());
    assertEquals(basis, value.getQuantityBasis());
    assertEquals("CALCULATED", value.getEngineeringState());
    assertEquals("REVIEW_REQUIRED", value.getApprovalStatus());
    assertFalse(value.getSourceCalculationId().isEmpty());
    assertEquals("SIMULATION_RESULT", value.getProvenance().get(0).getSourceType());
    assertEquals("NORMAL-01", value.getProvenance().get(0).getDesignCaseId());
  }

  private static boolean hasArea(EngineeringDiagramStreamTable table) {
    for (Row row : table.getRows()) {
      if (!row.getAreaName().isEmpty()) {
        return true;
      }
    }
    return false;
  }

  private static Row firstCompleteRow(EngineeringDiagramStreamTable table) {
    for (Row row : table.getRows()) {
      if (row.getValues().size() == Quantity.values().length) {
        return row;
      }
    }
    throw new AssertionError("No complete governed stream-table row found");
  }

  private static boolean hasDiagnostic(EngineeringDiagramStreamTable table, String code) {
    for (EngineeringDiagramStreamTable.Diagnostic diagnostic : table.getDiagnostics()) {
      if (code.equals(diagnostic.getCode())) {
        return true;
      }
    }
    return false;
  }

  private static SemanticObject firstLine(EngineeringDiagramDocumentSet documents) {
    for (SemanticObject object : documents.getSemanticObjects()) {
      if (object.getKind() == EngineeringNode.Kind.LINE) {
        return object;
      }
    }
    throw new AssertionError("No canonical line object found");
  }

  private static Row findRow(EngineeringDiagramStreamTable table, String semanticObjectId) {
    for (Row row : table.getRows()) {
      if (semanticObjectId.equals(row.getSemanticObjectId())) {
        return row;
      }
    }
    throw new AssertionError("No stream-table row for " + semanticObjectId);
  }
}
