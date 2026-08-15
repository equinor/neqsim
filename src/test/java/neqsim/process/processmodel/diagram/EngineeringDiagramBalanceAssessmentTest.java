package neqsim.process.processmodel.diagram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import neqsim.process.engineering.model.EngineeringDiagramBalanceAssessment;
import neqsim.process.engineering.model.EngineeringDiagramBalanceAssessment.Criteria;
import neqsim.process.engineering.model.EngineeringDiagramBalanceAssessment.Result;
import neqsim.process.engineering.model.EngineeringDiagramBalanceAssessment.Status;
import neqsim.process.engineering.model.EngineeringDiagramBalanceTable;
import neqsim.process.engineering.model.EngineeringDiagramBalanceTable.Boundary;
import neqsim.process.engineering.model.EngineeringDiagramBalanceTable.Direction;
import neqsim.process.engineering.model.EngineeringDiagramBalanceTable.EvidenceState;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.ContentProfile;
import neqsim.process.engineering.model.EngineeringDiagramStreamTable;
import neqsim.process.engineering.model.EngineeringDiagramStreamTable.Quantity;
import neqsim.process.engineering.model.EngineeringDiagramStreamTable.Row;
import neqsim.process.equipment.stream.StreamInterface;
import org.junit.jupiter.api.Test;

/** Regression tests for governed balance-tolerance assessment. */
class EngineeringDiagramBalanceAssessmentTest {

  @Test
  void assessesExplicitCriteriaWithoutReconcilingSourceValues() {
    EngineeringDiagramBalanceTable balanceTable = balanceTable(completeBoundaryCase());
    String sourceJson = balanceTable.toJson();
    Criteria criterion = criteria(1.0e-8, 1.0e-8, Double.MAX_VALUE, 2.0);

    EngineeringDiagramBalanceAssessment assessment = EngineeringDiagramBalanceAssessment.fromBalanceTable(balanceTable,
        Collections.singletonList(criterion));

    assertTrue(assessment.isValid());
    assertEquals(1, assessment.getResults().size());
    Result result = assessment.getResults().get(0);
    assertEquals("BAL-SIMPLE-01", result.getBalanceId());
    assertEquals(Status.WITHIN_TOLERANCE, result.getMassStatus());
    assertEquals(Status.WITHIN_TOLERANCE, result.getStreamEnthalpyStatus());
    assertTrue(result.getMassAbsoluteResidual() >= 0.0);
    assertTrue(result.getStreamEnthalpyAbsoluteResidual() >= 0.0);
    assertEquals(sourceJson, balanceTable.toJson());
    assertFalse(assessment.getSourceBalanceTableFingerprint().isEmpty());
    assertNotSame(assessment.getCriteria(), assessment.getCriteria());
    assertNotSame(assessment.getResults(), assessment.getResults());
    assertNotSame(assessment.getDiagnostics(), assessment.getDiagnostics());
    assertThrows(UnsupportedOperationException.class, () -> assessment.getCriteria().clear());
    assertTrue(assessment.toJson().contains("\"massAbsoluteToleranceUnit\": \"kg/s\""));
    assertTrue(assessment.toJson().contains("\"streamEnthalpyAbsoluteToleranceUnit\": \"W\""));
  }

  @Test
  void flagsCompleteResidualsOutsideExplicitTolerance() {
    EngineeringDiagramBalanceTable balanceTable = inletOnlyBalanceTable(completeBoundaryCase());
    Criteria criterion = criteria(1.0e-8, 1.0e-8, 0.0, 0.0);

    EngineeringDiagramBalanceAssessment assessment = EngineeringDiagramBalanceAssessment.fromBalanceTable(balanceTable,
        Collections.singletonList(criterion));

    assertTrue(assessment.isValid());
    assertEquals(Status.OUTSIDE_TOLERANCE, assessment.getResults().get(0).getMassStatus());
    assertEquals(Status.OUTSIDE_TOLERANCE, assessment.getResults().get(0).getStreamEnthalpyStatus());
    assertTrue(hasDiagnostic(assessment, "BALANCE_ASSESSMENT_MASS_OUTSIDE_TOLERANCE"));
    assertTrue(hasDiagnostic(assessment, "BALANCE_ASSESSMENT_STREAM_ENTHALPY_OUTSIDE_TOLERANCE"));
  }

  @Test
  void rejectsMissingUnknownAndDuplicateCriteria() {
    EngineeringDiagramBalanceTable balanceTable = balanceTable(completeBoundaryCase());
    Criteria declared = criteria(1.0, 1.0, 1.0, 1.0);
    Criteria unknown = new Criteria("BAL-MISSING", 1.0, 1.0, 1.0, 1.0, "project-balance-criteria:test",
        EvidenceState.PROPOSED);

    EngineeringDiagramBalanceAssessment missing = EngineeringDiagramBalanceAssessment.fromBalanceTable(balanceTable,
        Collections.<Criteria>emptyList());
    EngineeringDiagramBalanceAssessment duplicate = EngineeringDiagramBalanceAssessment.fromBalanceTable(balanceTable,
        Arrays.asList(declared, declared));
    EngineeringDiagramBalanceAssessment unknownAssessment = EngineeringDiagramBalanceAssessment
        .fromBalanceTable(balanceTable, Collections.singletonList(unknown));

    assertFalse(missing.isValid());
    assertTrue(hasDiagnostic(missing, "BALANCE_ASSESSMENT_CRITERIA_NOT_DECLARED"));
    assertEquals(Status.NOT_ASSESSED, missing.getResults().get(0).getMassStatus());
    assertFalse(duplicate.isValid());
    assertTrue(hasDiagnostic(duplicate, "BALANCE_ASSESSMENT_DUPLICATE_CRITERIA"));
    assertFalse(unknownAssessment.isValid());
    assertTrue(hasDiagnostic(unknownAssessment, "BALANCE_ASSESSMENT_UNKNOWN_BALANCE"));
  }

  @Test
  void reportsIncompleteGovernedSourceValues() {
    EngineeringDiagramBalanceTable balanceTable = incompleteBalanceTable(completeBoundaryCase());

    EngineeringDiagramBalanceAssessment assessment = EngineeringDiagramBalanceAssessment.fromBalanceTable(balanceTable,
        Collections.singletonList(criteria(1.0, 1.0, 1.0, 1.0)));

    assertFalse(assessment.isValid());
    assertEquals(Status.INCOMPLETE, assessment.getResults().get(0).getMassStatus());
    assertEquals(Status.INCOMPLETE, assessment.getResults().get(0).getStreamEnthalpyStatus());
    assertTrue(hasDiagnostic(assessment, "BALANCE_ASSESSMENT_MASS_INCOMPLETE"));
    assertTrue(hasDiagnostic(assessment, "BALANCE_ASSESSMENT_STREAM_ENTHALPY_INCOMPLETE"));
    assertTrue(hasDiagnostic(assessment, "BALANCE_ASSESSMENT_SOURCE_BALANCE_BOUNDARY_UNKNOWN_STREAM"));
  }

  @Test
  void isDeterministicForFreshSystemsAndCriteriaOrder() {
    EngineeringDiagramBalanceAssessment first = EngineeringDiagramBalanceAssessment.fromBalanceTable(
        balanceTable(completeBoundaryCase()), Collections.singletonList(criteria(1.0, 1.0, Double.MAX_VALUE, 2.0)));
    List<Criteria> secondCriteria = new ArrayList<Criteria>();
    secondCriteria.add(criteria(1.0, 1.0, Double.MAX_VALUE, 2.0));
    Collections.reverse(secondCriteria);
    EngineeringDiagramBalanceAssessment second = EngineeringDiagramBalanceAssessment
        .fromBalanceTable(balanceTable(completeBoundaryCase()), secondCriteria);

    assertEquals(first.toJson(), second.toJson());
  }

  @Test
  void rejectsInvalidCriteriaConstruction() {
    assertThrows(IllegalArgumentException.class, () -> new Criteria("BAL-SIMPLE-01", -1.0, 1.0, 1.0, 1.0,
        "project-balance-criteria:test", EvidenceState.PROPOSED));
    assertThrows(IllegalArgumentException.class, () -> new Criteria("BAL-SIMPLE-01", 1.0, Double.NaN, 1.0, 1.0,
        "project-balance-criteria:test", EvidenceState.PROPOSED));
    assertThrows(IllegalArgumentException.class, () -> new Criteria("BAL-SIMPLE-01", 1.0, 1.0, Double.POSITIVE_INFINITY,
        1.0, "project-balance-criteria:test", EvidenceState.PROPOSED));
  }

  private static Criteria criteria(double massAbsolute, double massRelative, double enthalpyAbsolute,
      double enthalpyRelative) {
    return new Criteria("BAL-SIMPLE-01", massAbsolute, massRelative, enthalpyAbsolute, enthalpyRelative,
        "project-balance-criteria:test", EvidenceState.PROPOSED);
  }

  private static EngineeringDiagramReferenceFixtures.SystemCase completeBoundaryCase() {
    EngineeringDiagramReferenceFixtures.SystemCase reference = EngineeringDiagramReferenceFixtures.simpleTrain();
    for (StreamInterface product : reference.getProducts()) {
      reference.getProcessSystem().add(product);
    }
    reference.getProcessSystem().run();
    return reference;
  }

  private static EngineeringDiagramBalanceTable balanceTable(EngineeringDiagramReferenceFixtures.SystemCase reference) {
    EngineeringDiagramDocumentSet documents = ProcessDiagramDocumentSetAdapter.fromProcessSystem(
        reference.getProcessSystem(), reference.getCaseId(), "A", "PFD-HMB-006", "Boundary tolerance assessment",
        ContentProfile.PFD, "NORMAL-01");
    EngineeringDiagramStreamTable streamTable = EngineeringDiagramStreamTable.fromDocumentSet(documents, "NORMAL-01");
    List<Boundary> boundaries = new ArrayList<Boundary>();
    Row feed = completeRow(streamTable, reference.getFeed().getName());
    boundaries.add(new Boundary("BAL-SIMPLE-01", feed.getSemanticObjectId(), Direction.INLET,
        "project-balance-register:test", EvidenceState.PROPOSED));
    for (StreamInterface product : reference.getProducts()) {
      Row row = completeRow(streamTable, product.getName());
      boundaries.add(new Boundary("BAL-SIMPLE-01", row.getSemanticObjectId(), Direction.OUTLET,
          "project-balance-register:test", EvidenceState.PROPOSED));
    }
    return EngineeringDiagramBalanceTable.fromStreamTable(streamTable, boundaries);
  }

  private static EngineeringDiagramBalanceTable inletOnlyBalanceTable(
      EngineeringDiagramReferenceFixtures.SystemCase reference) {
    EngineeringDiagramDocumentSet documents = ProcessDiagramDocumentSetAdapter.fromProcessSystem(
        reference.getProcessSystem(), reference.getCaseId(), "A", "PFD-HMB-006", "Boundary tolerance assessment",
        ContentProfile.PFD, "NORMAL-01");
    EngineeringDiagramStreamTable streamTable = EngineeringDiagramStreamTable.fromDocumentSet(documents, "NORMAL-01");
    Row feed = completeRow(streamTable, reference.getFeed().getName());
    Boundary boundary = new Boundary("BAL-SIMPLE-01", feed.getSemanticObjectId(), Direction.INLET,
        "project-balance-register:test", EvidenceState.PROPOSED);
    return EngineeringDiagramBalanceTable.fromStreamTable(streamTable, Collections.singletonList(boundary));
  }

  private static EngineeringDiagramBalanceTable incompleteBalanceTable(
      EngineeringDiagramReferenceFixtures.SystemCase reference) {
    EngineeringDiagramDocumentSet documents = ProcessDiagramDocumentSetAdapter.fromProcessSystem(
        reference.getProcessSystem(), reference.getCaseId(), "A", "PFD-HMB-006", "Boundary tolerance assessment",
        ContentProfile.PFD, "NORMAL-01");
    EngineeringDiagramStreamTable streamTable = EngineeringDiagramStreamTable.fromDocumentSet(documents, "NORMAL-01");
    Boundary boundary = new Boundary("BAL-SIMPLE-01", "line:missing", Direction.INLET, "project-balance-register:test",
        EvidenceState.PROPOSED);
    return EngineeringDiagramBalanceTable.fromStreamTable(streamTable, Collections.singletonList(boundary));
  }

  private static Row completeRow(EngineeringDiagramStreamTable table, String sourceLabel) {
    for (Row row : table.getRows()) {
      if (sourceLabel.equals(row.getSourceLabel()) && row.getValues().size() == Quantity.values().length) {
        return row;
      }
    }
    throw new AssertionError("No complete stream-table row for " + sourceLabel);
  }

  private static boolean hasDiagnostic(EngineeringDiagramBalanceAssessment assessment, String code) {
    for (EngineeringDiagramBalanceAssessment.Diagnostic diagnostic : assessment.getDiagnostics()) {
      if (code.equals(diagnostic.getCode())) {
        return true;
      }
    }
    return false;
  }
}
