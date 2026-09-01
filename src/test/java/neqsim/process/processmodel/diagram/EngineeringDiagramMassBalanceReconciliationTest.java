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
import neqsim.process.engineering.model.EngineeringDiagramBalanceTable;
import neqsim.process.engineering.model.EngineeringDiagramBalanceTable.Boundary;
import neqsim.process.engineering.model.EngineeringDiagramBalanceTable.Direction;
import neqsim.process.engineering.model.EngineeringDiagramBalanceTable.EvidenceState;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.ContentProfile;
import neqsim.process.engineering.model.EngineeringDiagramMassBalanceReconciliation;
import neqsim.process.engineering.model.EngineeringDiagramMassBalanceReconciliation.Adjustment;
import neqsim.process.engineering.model.EngineeringDiagramMassBalanceReconciliation.ResultStatus;
import neqsim.process.engineering.model.EngineeringDiagramMassBalanceReconciliation.Uncertainty;
import neqsim.process.engineering.model.EngineeringDiagramStreamTable;
import neqsim.process.engineering.model.EngineeringDiagramStreamTable.Quantity;
import neqsim.process.engineering.model.EngineeringDiagramStreamTable.Row;
import neqsim.process.equipment.stream.StreamInterface;
import org.junit.jupiter.api.Test;

/** Regression tests for immutable, tolerance-gated diagram mass-balance reconciliation. */
class EngineeringDiagramMassBalanceReconciliationTest {

  @Test
  void reconcilesOutsideToleranceByUncertaintyWithoutMutatingSources() {
    Fixture fixture = outsideToleranceFixture();
    String streamJson = fixture.streamTable.toJson();
    String balanceJson = fixture.balanceTable.toJson();
    String assessmentJson = fixture.assessment.toJson();

    EngineeringDiagramMassBalanceReconciliation reconciliation = EngineeringDiagramMassBalanceReconciliation
        .fromSources(fixture.streamTable, fixture.balanceTable, fixture.assessment, uncertainties(fixture, 2.0, 1.0));

    assertTrue(reconciliation.isValid());
    assertEquals(ResultStatus.RECONCILED, reconciliation.getResults().get(0).getStatus());
    assertEquals(2, reconciliation.getAdjustments().size());
    assertEquals(0.0, reconciliation.getResults().get(0).getReconciledResidual(), 1.0e-12);
    Adjustment feedAdjustment = adjustment(reconciliation, fixture.feed.getSemanticObjectId());
    Adjustment productAdjustment = adjustment(reconciliation, fixture.product.getSemanticObjectId());
    assertEquals(4.0, Math.abs(feedAdjustment.getAdjustment() / productAdjustment.getAdjustment()), 1.0e-10);
    assertEquals(streamJson, fixture.streamTable.toJson());
    assertEquals(balanceJson, fixture.balanceTable.toJson());
    assertEquals(assessmentJson, fixture.assessment.toJson());
    assertFalse(reconciliation.getSourceStreamTableFingerprint().isEmpty());
    assertFalse(reconciliation.getSourceBalanceTableFingerprint().isEmpty());
    assertFalse(reconciliation.getSourceAssessmentFingerprint().isEmpty());
    assertNotSame(reconciliation.getAdjustments(), reconciliation.getAdjustments());
    assertThrows(UnsupportedOperationException.class, () -> reconciliation.getAdjustments().clear());
    assertTrue(reconciliation.toJson()
        .contains("\"schemaVersion\": \"" + EngineeringDiagramMassBalanceReconciliation.SCHEMA_VERSION + "\""));
    assertTrue(reconciliation.toJson().contains("\"massFlowUnit\": \"kg/s\""));
  }

  @Test
  void leavesWithinToleranceEvidenceUntouchedWithoutUncertainties() {
    Fixture fixture = withinToleranceFixture();

    EngineeringDiagramMassBalanceReconciliation reconciliation = EngineeringDiagramMassBalanceReconciliation
        .fromSources(fixture.streamTable, fixture.balanceTable, fixture.assessment,
            Collections.<Uncertainty>emptyList());

    assertTrue(reconciliation.isValid());
    assertEquals(ResultStatus.UNCHANGED_WITHIN_TOLERANCE, reconciliation.getResults().get(0).getStatus());
    assertTrue(reconciliation.getAdjustments().isEmpty());
    assertTrue(reconciliation.getUncertainties().isEmpty());
  }

  @Test
  void reportsMissingDuplicateUnknownAndInvalidUncertaintyEvidence() {
    Fixture fixture = outsideToleranceFixture();
    Uncertainty feed = uncertainty(fixture, fixture.feed, 1.0);
    Uncertainty duplicateFeed = uncertainty(fixture, fixture.feed, 2.0);
    Uncertainty unknownBoundary = new Uncertainty(BALANCE_ID, "line:unknown", 1.0, "kg/s", "MASS",
        "instrument-register:test", "test uncertainty", EvidenceState.PROPOSED);
    Uncertainty invalidProduct = new Uncertainty(BALANCE_ID, fixture.product.getSemanticObjectId(), 0.0, "kg/s", "MASS",
        "instrument-register:test", "test uncertainty", EvidenceState.PROPOSED);

    EngineeringDiagramMassBalanceReconciliation missing = EngineeringDiagramMassBalanceReconciliation
        .fromSources(fixture.streamTable, fixture.balanceTable, fixture.assessment, Collections.singletonList(feed));
    EngineeringDiagramMassBalanceReconciliation malformed = EngineeringDiagramMassBalanceReconciliation.fromSources(
        fixture.streamTable, fixture.balanceTable, fixture.assessment,
        Arrays.asList(feed, duplicateFeed, unknownBoundary, invalidProduct));

    assertFalse(missing.isValid());
    assertTrue(hasDiagnostic(missing, "MASS_RECONCILIATION_UNCERTAINTY_MISSING"));
    assertFalse(malformed.isValid());
    assertTrue(hasDiagnostic(malformed, "MASS_RECONCILIATION_UNCERTAINTY_DUPLICATE"));
    assertTrue(hasDiagnostic(malformed, "MASS_RECONCILIATION_UNCERTAINTY_UNKNOWN_BOUNDARY"));
    assertTrue(hasDiagnostic(malformed, "MASS_RECONCILIATION_UNCERTAINTY_INVALID"));
    assertEquals(ResultStatus.NOT_RECONCILED, malformed.getResults().get(0).getStatus());
  }

  @Test
  void rejectsUnderspecifiedAndMismatchedSources() {
    EngineeringDiagramReferenceFixtures.SystemCase reference = completeBoundaryCase();
    EngineeringDiagramStreamTable streamTable = streamTable(reference);
    Row feed = completeRow(streamTable, reference.getFeed().getName());
    EngineeringDiagramBalanceTable inletOnly = EngineeringDiagramBalanceTable.fromStreamTable(streamTable,
        Collections.singletonList(boundary(feed, Direction.INLET)));
    EngineeringDiagramBalanceAssessment inletAssessment = assessment(inletOnly, 0.0, 0.0);
    EngineeringDiagramMassBalanceReconciliation underspecified = EngineeringDiagramMassBalanceReconciliation
        .fromSources(streamTable, inletOnly, inletAssessment,
            Collections.singletonList(uncertainty(BALANCE_ID, feed, 1.0)));

    Fixture fixture = outsideToleranceFixture();
    EngineeringDiagramStreamTable otherStreamTable = streamTable(completeBranchedBoundaryCase());
    EngineeringDiagramMassBalanceReconciliation mismatched = EngineeringDiagramMassBalanceReconciliation
        .fromSources(otherStreamTable, fixture.balanceTable, fixture.assessment, Collections.<Uncertainty>emptyList());

    assertFalse(underspecified.isValid());
    assertTrue(hasDiagnostic(underspecified, "MASS_RECONCILIATION_BOUNDARY_UNDERSPECIFIED"));
    assertFalse(mismatched.isValid());
    assertTrue(hasDiagnostic(mismatched, "MASS_RECONCILIATION_STREAM_SOURCE_MISMATCH"));
    assertTrue(mismatched.getAdjustments().isEmpty());
    assertEquals(ResultStatus.NOT_RECONCILED, mismatched.getResults().get(0).getStatus());
  }

  @Test
  void isDeterministicForFreshSourcesAndUncertaintyOrder() {
    Fixture firstFixture = outsideToleranceFixture();
    Fixture secondFixture = outsideToleranceFixture();
    List<Uncertainty> firstUncertainties = uncertainties(firstFixture, 2.0, 1.0);
    List<Uncertainty> secondUncertainties = uncertainties(secondFixture, 2.0, 1.0);
    Collections.reverse(secondUncertainties);

    EngineeringDiagramMassBalanceReconciliation first = EngineeringDiagramMassBalanceReconciliation
        .fromSources(firstFixture.streamTable, firstFixture.balanceTable, firstFixture.assessment, firstUncertainties);
    EngineeringDiagramMassBalanceReconciliation second = EngineeringDiagramMassBalanceReconciliation.fromSources(
        secondFixture.streamTable, secondFixture.balanceTable, secondFixture.assessment, secondUncertainties);

    assertEquals(first.toJson(), second.toJson());
  }

  private static final String BALANCE_ID = "BAL-SIMPLE-01";

  private static Fixture outsideToleranceFixture() {
    EngineeringDiagramReferenceFixtures.SystemCase reference = completeBoundaryCase();
    EngineeringDiagramStreamTable streamTable = streamTable(reference);
    Row feed = completeRow(streamTable, reference.getFeed().getName());
    Row product = completeRow(streamTable, reference.getProducts().get(0).getName());
    List<Boundary> boundaries = Arrays.asList(boundary(feed, Direction.INLET), boundary(product, Direction.OUTLET));
    EngineeringDiagramBalanceTable balanceTable = EngineeringDiagramBalanceTable.fromStreamTable(streamTable,
        boundaries);
    return new Fixture(streamTable, balanceTable, assessment(balanceTable, 1.0e-10, 1.0e-10), feed, product);
  }

  private static Fixture withinToleranceFixture() {
    EngineeringDiagramReferenceFixtures.SystemCase reference = completeBoundaryCase();
    EngineeringDiagramStreamTable streamTable = streamTable(reference);
    Row feed = completeRow(streamTable, reference.getFeed().getName());
    List<Boundary> boundaries = new ArrayList<Boundary>();
    boundaries.add(boundary(feed, Direction.INLET));
    Row firstProduct = null;
    for (StreamInterface product : reference.getProducts()) {
      Row row = completeRow(streamTable, product.getName());
      if (firstProduct == null) {
        firstProduct = row;
      }
      boundaries.add(boundary(row, Direction.OUTLET));
    }
    EngineeringDiagramBalanceTable balanceTable = EngineeringDiagramBalanceTable.fromStreamTable(streamTable,
        boundaries);
    return new Fixture(streamTable, balanceTable, assessment(balanceTable, 1.0e6, 1.0), feed, firstProduct);
  }

  private static EngineeringDiagramBalanceAssessment assessment(EngineeringDiagramBalanceTable balanceTable,
      double massAbsoluteTolerance, double massRelativeTolerance) {
    Criteria criterion = new Criteria(BALANCE_ID, massAbsoluteTolerance, massRelativeTolerance, Double.MAX_VALUE, 1.0,
        "project-balance-criteria:test", EvidenceState.PROPOSED);
    return EngineeringDiagramBalanceAssessment.fromBalanceTable(balanceTable, Collections.singletonList(criterion));
  }

  private static List<Uncertainty> uncertainties(Fixture fixture, double feedUncertainty, double productUncertainty) {
    return new ArrayList<Uncertainty>(Arrays.asList(uncertainty(fixture, fixture.feed, feedUncertainty),
        uncertainty(fixture, fixture.product, productUncertainty)));
  }

  private static Uncertainty uncertainty(Fixture fixture, Row row, double standardUncertainty) {
    return uncertainty(fixture.balanceTable.getBalances().get(0).getBalanceId(), row, standardUncertainty);
  }

  private static Uncertainty uncertainty(String balanceId, Row row, double standardUncertainty) {
    return new Uncertainty(balanceId, row.getSemanticObjectId(), standardUncertainty, "kg/s", "MASS",
        "instrument-register:test", "test standard uncertainty", EvidenceState.PROPOSED);
  }

  private static Adjustment adjustment(EngineeringDiagramMassBalanceReconciliation reconciliation, String streamId) {
    for (Adjustment adjustment : reconciliation.getAdjustments()) {
      if (streamId.equals(adjustment.getStreamSemanticObjectId())) {
        return adjustment;
      }
    }
    throw new AssertionError("No adjustment for " + streamId);
  }

  private static Boundary boundary(Row row, Direction direction) {
    return new Boundary(BALANCE_ID, row.getSemanticObjectId(), direction, "project-balance-register:test",
        EvidenceState.PROPOSED);
  }

  private static EngineeringDiagramReferenceFixtures.SystemCase completeBoundaryCase() {
    EngineeringDiagramReferenceFixtures.SystemCase reference = EngineeringDiagramReferenceFixtures.simpleTrain();
    return executeWithProductBoundaries(reference);
  }

  private static EngineeringDiagramReferenceFixtures.SystemCase completeBranchedBoundaryCase() {
    EngineeringDiagramReferenceFixtures.SystemCase reference = EngineeringDiagramReferenceFixtures
        .branchedSeparatorCompressionTrain();
    return executeWithProductBoundaries(reference);
  }

  private static EngineeringDiagramReferenceFixtures.SystemCase executeWithProductBoundaries(
      EngineeringDiagramReferenceFixtures.SystemCase reference) {
    int productIndex = 1;
    for (StreamInterface product : reference.getProducts()) {
      product.setName(reference.getCaseId() + "-PRODUCT-" + productIndex);
      reference.getProcessSystem().add(product);
      productIndex++;
    }
    reference.getProcessSystem().run();
    return reference;
  }

  private static EngineeringDiagramStreamTable streamTable(EngineeringDiagramReferenceFixtures.SystemCase reference) {
    EngineeringDiagramDocumentSet documents = ProcessDiagramDocumentSetAdapter.fromProcessSystem(
        reference.getProcessSystem(), reference.getCaseId(), "A", "PFD-HMB-007", "Boundary reconciliation evidence",
        ContentProfile.PFD, "NORMAL-01");
    return EngineeringDiagramStreamTable.fromDocumentSet(documents, "NORMAL-01");
  }

  private static Row completeRow(EngineeringDiagramStreamTable table, String sourceLabel) {
    for (Row row : table.getRows()) {
      if (sourceLabel.equals(row.getSourceLabel()) && row.getValues().size() == Quantity.values().length) {
        return row;
      }
    }
    throw new AssertionError("No complete stream-table row for " + sourceLabel);
  }

  private static boolean hasDiagnostic(EngineeringDiagramMassBalanceReconciliation reconciliation, String code) {
    for (EngineeringDiagramMassBalanceReconciliation.Diagnostic diagnostic : reconciliation.getDiagnostics()) {
      if (code.equals(diagnostic.getCode())) {
        return true;
      }
    }
    return false;
  }

  private static final class Fixture {
    private final EngineeringDiagramStreamTable streamTable;
    private final EngineeringDiagramBalanceTable balanceTable;
    private final EngineeringDiagramBalanceAssessment assessment;
    private final Row feed;
    private final Row product;

    private Fixture(EngineeringDiagramStreamTable streamTable, EngineeringDiagramBalanceTable balanceTable,
        EngineeringDiagramBalanceAssessment assessment, Row feed, Row product) {
      this.streamTable = streamTable;
      this.balanceTable = balanceTable;
      this.assessment = assessment;
      this.feed = feed;
      this.product = product;
    }
  }
}
