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
import neqsim.process.engineering.model.EngineeringDiagramBalanceTable;
import neqsim.process.engineering.model.EngineeringDiagramBalanceTable.Balance;
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

/** Regression tests for deterministic, explicit-boundary engineering balances. */
class EngineeringDiagramBalanceTableTest {

  @Test
  void aggregatesExplicitMassAndStreamEnthalpyBoundaries() {
    EngineeringDiagramReferenceFixtures.SystemCase reference = completeBoundaryCase();
    EngineeringDiagramStreamTable streamTable = streamTable(reference);
    List<Boundary> boundaries = boundaries(reference, streamTable);

    EngineeringDiagramBalanceTable table = EngineeringDiagramBalanceTable.fromStreamTable(streamTable, boundaries);

    assertTrue(table.isValid());
    assertEquals(1, table.getBalances().size());
    Balance balance = table.getBalances().get(0);
    assertEquals("BAL-SIMPLE-01", balance.getBalanceId());
    assertEquals(3, balance.getBoundaryCount());
    assertTrue(balance.isMassFlowComplete());
    assertTrue(balance.isStreamEnthalpyFlowComplete());
    assertEquals(0.0, balance.getMassResidual(), 1.0e-8);
    assertEquals(0.0, balance.getRelativeMassResidual(), 1.0e-8);
    assertTrue(Double.isFinite(balance.getStreamEnthalpyResidual()));
    assertTrue(Double.isFinite(balance.getRelativeStreamEnthalpyResidual()));
    assertFalse(table.getSourceStreamTableFingerprint().isEmpty());
    assertNotSame(table.getBoundaries(), table.getBoundaries());
    assertNotSame(table.getBalances(), table.getBalances());
    assertNotSame(table.getDiagnostics(), table.getDiagnostics());
    assertThrows(UnsupportedOperationException.class, () -> table.getBoundaries().clear());
    assertTrue(table.toJson().contains("\"massFlowUnit\": \"kg/s\""));
    assertTrue(table.toJson().contains("\"streamEnthalpyFlowUnit\": \"W\""));
  }

  @Test
  void isDeterministicForFreshProcessSystemsAndBoundaryOrder() {
    EngineeringDiagramReferenceFixtures.SystemCase firstReference = completeBoundaryCase();
    EngineeringDiagramStreamTable firstStreamTable = streamTable(firstReference);
    List<Boundary> firstBoundaries = boundaries(firstReference, firstStreamTable);

    EngineeringDiagramReferenceFixtures.SystemCase secondReference = completeBoundaryCase();
    EngineeringDiagramStreamTable secondStreamTable = streamTable(secondReference);
    List<Boundary> secondBoundaries = boundaries(secondReference, secondStreamTable);
    Collections.reverse(secondBoundaries);

    EngineeringDiagramBalanceTable first = EngineeringDiagramBalanceTable.fromStreamTable(firstStreamTable,
        firstBoundaries);
    EngineeringDiagramBalanceTable second = EngineeringDiagramBalanceTable.fromStreamTable(secondStreamTable,
        secondBoundaries);

    assertEquals(first.toJson(), second.toJson());
  }

  @Test
  void rejectsUnknownAndDuplicateBoundaryAssignments() {
    EngineeringDiagramReferenceFixtures.SystemCase reference = completeBoundaryCase();
    EngineeringDiagramStreamTable streamTable = streamTable(reference);
    Row feed = completeRow(streamTable, reference.getFeed().getName());
    Boundary declared = new Boundary("BAL-SIMPLE-01", feed.getSemanticObjectId(), Direction.INLET,
        "project-balance-register:test", EvidenceState.PROPOSED);

    EngineeringDiagramBalanceTable duplicate = EngineeringDiagramBalanceTable.fromStreamTable(streamTable,
        Arrays.asList(declared, declared));
    EngineeringDiagramBalanceTable unknown = EngineeringDiagramBalanceTable.fromStreamTable(streamTable,
        Arrays.asList(new Boundary("BAL-SIMPLE-01", "line:missing", Direction.OUTLET, "project-balance-register:test",
            EvidenceState.PROPOSED)));

    assertFalse(duplicate.isValid());
    assertTrue(hasDiagnostic(duplicate, "BALANCE_BOUNDARY_DUPLICATE_STREAM"));
    assertFalse(unknown.isValid());
    assertTrue(hasDiagnostic(unknown, "BALANCE_BOUNDARY_UNKNOWN_STREAM"));
  }

  @Test
  void requiresExplicitBoundaryAssignments() {
    EngineeringDiagramReferenceFixtures.SystemCase reference = completeBoundaryCase();

    EngineeringDiagramBalanceTable table = EngineeringDiagramBalanceTable.fromStreamTable(streamTable(reference),
        Collections.<Boundary>emptyList());

    assertFalse(table.isValid());
    assertTrue(hasDiagnostic(table, "BALANCE_BOUNDARY_NOT_DECLARED"));
    assertTrue(table.getBalances().isEmpty());
  }

  private static EngineeringDiagramReferenceFixtures.SystemCase completeBoundaryCase() {
    EngineeringDiagramReferenceFixtures.SystemCase reference = EngineeringDiagramReferenceFixtures.simpleTrain();
    for (StreamInterface product : reference.getProducts()) {
      reference.getProcessSystem().add(product);
    }
    reference.getProcessSystem().run();
    return reference;
  }

  private static EngineeringDiagramStreamTable streamTable(EngineeringDiagramReferenceFixtures.SystemCase reference) {
    EngineeringDiagramDocumentSet documents = ProcessDiagramDocumentSetAdapter.fromProcessSystem(
        reference.getProcessSystem(), reference.getCaseId(), "A", "PFD-HMB-005", "Boundary balance", ContentProfile.PFD,
        "NORMAL-01");
    return EngineeringDiagramStreamTable.fromDocumentSet(documents, "NORMAL-01");
  }

  private static List<Boundary> boundaries(EngineeringDiagramReferenceFixtures.SystemCase reference,
      EngineeringDiagramStreamTable streamTable) {
    List<Boundary> result = new ArrayList<Boundary>();
    Row feed = completeRow(streamTable, reference.getFeed().getName());
    result.add(new Boundary("BAL-SIMPLE-01", feed.getSemanticObjectId(), Direction.INLET,
        "project-balance-register:test", EvidenceState.PROPOSED));
    for (StreamInterface product : reference.getProducts()) {
      Row row = completeRow(streamTable, product.getName());
      result.add(new Boundary("BAL-SIMPLE-01", row.getSemanticObjectId(), Direction.OUTLET,
          "project-balance-register:test", EvidenceState.PROPOSED));
    }
    return result;
  }

  private static Row completeRow(EngineeringDiagramStreamTable table, String sourceLabel) {
    for (Row row : table.getRows()) {
      if (sourceLabel.equals(row.getSourceLabel()) && row.getValues().size() == Quantity.values().length) {
        return row;
      }
    }
    throw new AssertionError("No complete stream-table row for " + sourceLabel);
  }

  private static boolean hasDiagnostic(EngineeringDiagramBalanceTable table, String code) {
    for (EngineeringDiagramBalanceTable.Diagnostic diagnostic : table.getDiagnostics()) {
      if (code.equals(diagnostic.getCode())) {
        return true;
      }
    }
    return false;
  }
}
