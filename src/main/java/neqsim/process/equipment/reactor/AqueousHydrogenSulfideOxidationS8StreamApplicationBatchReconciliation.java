package neqsim.process.equipment.reactor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reconciles an ordered S8 stream-application preview with verified external application evidence.
 *
 * <p>
 * Reconciliation compares immutable evidence only. It preserves source order, requires exact identity and transition
 * provenance, and closes preview-versus-applied candidate amounts without mutating, running, or flashing a stream.
 * </p>
 *
 * @author esol
 * @version $Id: $
 */
public final class AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliation {
  private static final double COMPARISON_ULPS = 8.0;

  private AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliation() {
  }

  /**
   * Reconcile ordered preview evidence against ordered external-application evidence.
   *
   * @param preview qualified detached batch preview
   * @param application qualified external-application batch receipt
   * @return immutable ordered reconciliation evidence
   * @throws IllegalArgumentException if identity, order, transition provenance, or numerical evidence differs
   */
  public static Result reconcile(AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview.Result preview,
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt.Result application) {
    if (preview == null || application == null) {
      throw new IllegalArgumentException("Preview and application evidence cannot be null");
    }
    List<AqueousHydrogenSulfideOxidationS8StreamApplicationPreview.Result> previews = preview.getPreviews();
    List<AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt.Result> applications = application.getReceipts();
    if (previews.isEmpty() || applications.isEmpty() || previews.size() != applications.size()) {
      throw new IllegalArgumentException("Preview and application batches must have the same non-zero size");
    }
    if (preview.getStrictAppendCount() != application.getStrictAppendCount()
        || preview.getUnchangedCount() != application.getUnchangedCount()) {
      throw new IllegalArgumentException("Preview and application batch state counts differ");
    }

    List<Entry> entries = new ArrayList<Entry>(previews.size());
    double previewObservedS8IncrementMol = 0.0;
    double applicationObservedS8IncrementMol = 0.0;
    double previewObservedTotalIncrementMol = 0.0;
    double applicationObservedTotalIncrementMol = 0.0;
    double s8EntryToleranceMol = 0.0;
    double totalEntryToleranceMol = 0.0;
    double maximumEntryS8ResidualMol = 0.0;
    double maximumEntryTotalAmountResidualMol = 0.0;

    for (int index = 0; index < previews.size(); index++) {
      AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt.Result expected = previews.get(index)
          .getApplicationReceipt();
      AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt.Result observed = applications.get(index);
      requireSameProvenance(expected, observed);

      requireClose(expected.getPriorS8AmountMol(), observed.getPriorS8AmountMol(),
          "Prior S8 amount differs from preview");
      requireClose(expected.getPlannedS8IncrementMol(), observed.getPlannedS8IncrementMol(),
          "Planned S8 increment differs from preview");
      requireClose(expected.getObservedS8IncrementMol(), observed.getObservedS8IncrementMol(),
          "Observed S8 increment differs from preview");
      requireClose(expected.getPriorTotalAmountMol(), observed.getPriorTotalAmountMol(),
          "Prior total amount differs from preview");
      requireClose(expected.getObservedTotalIncrementMol(), observed.getObservedTotalIncrementMol(),
          "Observed total increment differs from preview");
      if (expected.getPreservedNonS8ComponentCount() != observed.getPreservedNonS8ComponentCount()) {
        throw new IllegalArgumentException("Preserved non-S8 component count differs from preview");
      }

      double candidateS8ResidualMol = observed.getCandidateS8AmountMol() - expected.getCandidateS8AmountMol();
      double candidateTotalAmountResidualMol = observed.getCandidateTotalAmountMol()
          - expected.getCandidateTotalAmountMol();
      requireFinite(candidateS8ResidualMol, "Candidate S8 reconciliation residual");
      requireFinite(candidateTotalAmountResidualMol, "Candidate total-amount reconciliation residual");
      double candidateS8ToleranceMol = comparisonTolerance(expected.getCandidateS8AmountMol(),
          observed.getCandidateS8AmountMol());
      double candidateTotalAmountToleranceMol = comparisonTolerance(expected.getCandidateTotalAmountMol(),
          observed.getCandidateTotalAmountMol());
      if (Math.abs(candidateS8ResidualMol) > candidateS8ToleranceMol) {
        throw new IllegalArgumentException("Applied candidate S8 amount differs from preview");
      }
      if (Math.abs(candidateTotalAmountResidualMol) > candidateTotalAmountToleranceMol) {
        throw new IllegalArgumentException("Applied candidate total amount differs from preview");
      }

      previewObservedS8IncrementMol += expected.getObservedS8IncrementMol();
      applicationObservedS8IncrementMol += observed.getObservedS8IncrementMol();
      previewObservedTotalIncrementMol += expected.getObservedTotalIncrementMol();
      applicationObservedTotalIncrementMol += observed.getObservedTotalIncrementMol();
      s8EntryToleranceMol += candidateS8ToleranceMol;
      totalEntryToleranceMol += candidateTotalAmountToleranceMol;
      maximumEntryS8ResidualMol = Math.max(maximumEntryS8ResidualMol, Math.abs(candidateS8ResidualMol));
      maximumEntryTotalAmountResidualMol = Math.max(maximumEntryTotalAmountResidualMol,
          Math.abs(candidateTotalAmountResidualMol));
      entries.add(new Entry(expected.getTargetStateIdentifier(), expected.getApplicationIdempotencyKey(),
          expected.getTransitionDigestHex(), expected.getCandidateS8AmountMol(), observed.getCandidateS8AmountMol(),
          candidateS8ResidualMol, candidateS8ToleranceMol, expected.getCandidateTotalAmountMol(),
          observed.getCandidateTotalAmountMol(), candidateTotalAmountResidualMol,
          candidateTotalAmountToleranceMol));
    }

    requireClose(preview.getPlannedS8IncrementMol(), application.getPlannedS8IncrementMol(),
        "Aggregate planned S8 increment differs from preview");
    requireClose(preview.getObservedS8IncrementMol(), application.getObservedS8IncrementMol(),
        "Aggregate observed S8 increment differs from preview");

    double s8ResidualMol = applicationObservedS8IncrementMol - previewObservedS8IncrementMol;
    double totalAmountResidualMol = applicationObservedTotalIncrementMol - previewObservedTotalIncrementMol;
    requireFinite(s8ResidualMol, "Aggregate S8 reconciliation residual");
    requireFinite(totalAmountResidualMol, "Aggregate total-amount reconciliation residual");
    double s8ToleranceMol = s8EntryToleranceMol
        + summationTolerance(entries.size(), previewObservedS8IncrementMol, applicationObservedS8IncrementMol);
    double totalAmountToleranceMol = totalEntryToleranceMol + summationTolerance(entries.size(),
        previewObservedTotalIncrementMol, applicationObservedTotalIncrementMol);
    requireNonNegativeFinite(s8ToleranceMol, "Aggregate S8 reconciliation tolerance");
    requireNonNegativeFinite(totalAmountToleranceMol, "Aggregate total-amount reconciliation tolerance");
    if (Math.abs(s8ResidualMol) > s8ToleranceMol) {
      throw new IllegalArgumentException("Application batch does not reconcile with previewed S8 increment");
    }
    if (Math.abs(totalAmountResidualMol) > totalAmountToleranceMol) {
      throw new IllegalArgumentException("Application batch does not reconcile with previewed total increment");
    }

    return new Result(entries, preview.getStrictAppendCount(), preview.getUnchangedCount(),
        preview.getPlannedS8IncrementMol(), previewObservedS8IncrementMol, applicationObservedS8IncrementMol,
        s8ResidualMol, s8ToleranceMol, previewObservedTotalIncrementMol, applicationObservedTotalIncrementMol,
        totalAmountResidualMol, totalAmountToleranceMol, maximumEntryS8ResidualMol,
        maximumEntryTotalAmountResidualMol);
  }

  private static void requireSameProvenance(
      AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt.Result expected,
      AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt.Result observed) {
    requireExact(expected.getLedgerIdentifier(), observed.getLedgerIdentifier(), "Ledger identifier");
    requireExact(expected.getProductIdentityBasisIdentifier(), observed.getProductIdentityBasisIdentifier(),
        "Product identity basis");
    requireExact(expected.getMolecularWeightBasisIdentifier(), observed.getMolecularWeightBasisIdentifier(),
        "Molecular-weight basis");
    requireExact(expected.getTargetStateIdentifier(), observed.getTargetStateIdentifier(), "Target-state identifier");
    requireExact(expected.getApplicationIdempotencyKey(), observed.getApplicationIdempotencyKey(),
        "Application idempotency key");
    requireExact(expected.getPriorCheckpointHex(), observed.getPriorCheckpointHex(), "Prior checkpoint");
    requireExact(expected.getCandidateCheckpointHex(), observed.getCandidateCheckpointHex(), "Candidate checkpoint");
    requireExact(expected.getTransitionDigestHex(), observed.getTransitionDigestHex(), "Transition digest");
    requireExact(expected.getComponentName(), observed.getComponentName(), "Component name");
    if (expected.isStrictAppend() != observed.isStrictAppend() || expected.isUnchanged() != observed.isUnchanged()) {
      throw new IllegalArgumentException("Application transition state differs from preview");
    }
  }

  private static void requireExact(String expected, String observed, String name) {
    if (expected == null || !expected.equals(observed)) {
      throw new IllegalArgumentException(name + " differs from preview");
    }
  }

  private static void requireClose(double expected, double observed, String message) {
    double residual = observed - expected;
    requireFinite(residual, "Reconciliation residual");
    if (Math.abs(residual) > comparisonTolerance(expected, observed)) {
      throw new IllegalArgumentException(message);
    }
  }

  private static double comparisonTolerance(double first, double second) {
    requireFinite(first, "First comparison amount");
    requireFinite(second, "Second comparison amount");
    return COMPARISON_ULPS * Math.max(Math.ulp(Math.abs(first)), Math.ulp(Math.abs(second)));
  }

  private static double summationTolerance(int count, double first, double second) {
    return COMPARISON_ULPS * count * Math.max(Math.ulp(Math.abs(first)), Math.ulp(Math.abs(second)));
  }

  private static void requireNonNegativeFinite(double value, String name) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(name + " must be finite and non-negative");
    }
  }

  private static void requireFinite(double value, String name) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException(name + " must be finite");
    }
  }

  /** Immutable per-entry preview-to-application reconciliation evidence. */
  public static final class Entry implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String targetStateIdentifier;
    private final String applicationIdempotencyKey;
    private final String transitionDigestHex;
    private final double previewCandidateS8AmountMol;
    private final double applicationCandidateS8AmountMol;
    private final double candidateS8ResidualMol;
    private final double candidateS8ToleranceMol;
    private final double previewCandidateTotalAmountMol;
    private final double applicationCandidateTotalAmountMol;
    private final double candidateTotalAmountResidualMol;
    private final double candidateTotalAmountToleranceMol;

    private Entry(String targetStateIdentifier, String applicationIdempotencyKey, String transitionDigestHex,
        double previewCandidateS8AmountMol, double applicationCandidateS8AmountMol, double candidateS8ResidualMol,
        double candidateS8ToleranceMol, double previewCandidateTotalAmountMol,
        double applicationCandidateTotalAmountMol, double candidateTotalAmountResidualMol,
        double candidateTotalAmountToleranceMol) {
      this.targetStateIdentifier = targetStateIdentifier;
      this.applicationIdempotencyKey = applicationIdempotencyKey;
      this.transitionDigestHex = transitionDigestHex;
      this.previewCandidateS8AmountMol = previewCandidateS8AmountMol;
      this.applicationCandidateS8AmountMol = applicationCandidateS8AmountMol;
      this.candidateS8ResidualMol = candidateS8ResidualMol;
      this.candidateS8ToleranceMol = candidateS8ToleranceMol;
      this.previewCandidateTotalAmountMol = previewCandidateTotalAmountMol;
      this.applicationCandidateTotalAmountMol = applicationCandidateTotalAmountMol;
      this.candidateTotalAmountResidualMol = candidateTotalAmountResidualMol;
      this.candidateTotalAmountToleranceMol = candidateTotalAmountToleranceMol;
    }

    /** @return reconciled target-state identifier. */
    public String getTargetStateIdentifier() {
      return targetStateIdentifier;
    }

    /** @return reconciled application idempotency key. */
    public String getApplicationIdempotencyKey() {
      return applicationIdempotencyKey;
    }

    /** @return reconciled transition digest. */
    public String getTransitionDigestHex() {
      return transitionDigestHex;
    }

    /** @return previewed candidate S8 amount [mol]. */
    public double getPreviewCandidateS8AmountMol() {
      return previewCandidateS8AmountMol;
    }

    /** @return externally applied candidate S8 amount [mol]. */
    public double getApplicationCandidateS8AmountMol() {
      return applicationCandidateS8AmountMol;
    }

    /** @return application minus preview candidate S8 amount [mol]. */
    public double getCandidateS8ResidualMol() {
      return candidateS8ResidualMol;
    }

    /** @return ULP-scaled candidate S8 reconciliation tolerance [mol]. */
    public double getCandidateS8ToleranceMol() {
      return candidateS8ToleranceMol;
    }

    /** @return previewed candidate total amount [mol]. */
    public double getPreviewCandidateTotalAmountMol() {
      return previewCandidateTotalAmountMol;
    }

    /** @return externally applied candidate total amount [mol]. */
    public double getApplicationCandidateTotalAmountMol() {
      return applicationCandidateTotalAmountMol;
    }

    /** @return application minus preview candidate total amount [mol]. */
    public double getCandidateTotalAmountResidualMol() {
      return candidateTotalAmountResidualMol;
    }

    /** @return ULP-scaled candidate total-amount reconciliation tolerance [mol]. */
    public double getCandidateTotalAmountToleranceMol() {
      return candidateTotalAmountToleranceMol;
    }
  }

  /** Immutable aggregate preview-to-application reconciliation evidence. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final List<Entry> entries;
    private final int strictAppendCount;
    private final int unchangedCount;
    private final double plannedS8IncrementMol;
    private final double previewObservedS8IncrementMol;
    private final double applicationObservedS8IncrementMol;
    private final double s8ResidualMol;
    private final double s8ToleranceMol;
    private final double previewObservedTotalIncrementMol;
    private final double applicationObservedTotalIncrementMol;
    private final double totalAmountResidualMol;
    private final double totalAmountToleranceMol;
    private final double maximumEntryS8ResidualMol;
    private final double maximumEntryTotalAmountResidualMol;

    private Result(List<Entry> entries, int strictAppendCount, int unchangedCount, double plannedS8IncrementMol,
        double previewObservedS8IncrementMol, double applicationObservedS8IncrementMol, double s8ResidualMol,
        double s8ToleranceMol, double previewObservedTotalIncrementMol, double applicationObservedTotalIncrementMol,
        double totalAmountResidualMol, double totalAmountToleranceMol, double maximumEntryS8ResidualMol,
        double maximumEntryTotalAmountResidualMol) {
      this.entries = Collections.unmodifiableList(new ArrayList<Entry>(entries));
      this.strictAppendCount = strictAppendCount;
      this.unchangedCount = unchangedCount;
      this.plannedS8IncrementMol = plannedS8IncrementMol;
      this.previewObservedS8IncrementMol = previewObservedS8IncrementMol;
      this.applicationObservedS8IncrementMol = applicationObservedS8IncrementMol;
      this.s8ResidualMol = s8ResidualMol;
      this.s8ToleranceMol = s8ToleranceMol;
      this.previewObservedTotalIncrementMol = previewObservedTotalIncrementMol;
      this.applicationObservedTotalIncrementMol = applicationObservedTotalIncrementMol;
      this.totalAmountResidualMol = totalAmountResidualMol;
      this.totalAmountToleranceMol = totalAmountToleranceMol;
      this.maximumEntryS8ResidualMol = maximumEntryS8ResidualMol;
      this.maximumEntryTotalAmountResidualMol = maximumEntryTotalAmountResidualMol;
    }

    /** @return fresh unmodifiable copy of ordered reconciliation entries. */
    public List<Entry> getEntries() {
      return Collections.unmodifiableList(new ArrayList<Entry>(entries));
    }

    /** @return number of reconciled strict-append entries. */
    public int getStrictAppendCount() {
      return strictAppendCount;
    }

    /** @return number of reconciled unchanged entries. */
    public int getUnchangedCount() {
      return unchangedCount;
    }

    /** @return aggregate planned S8 increment [mol]. */
    public double getPlannedS8IncrementMol() {
      return plannedS8IncrementMol;
    }

    /** @return aggregate S8 increment observed by the preview [mol]. */
    public double getPreviewObservedS8IncrementMol() {
      return previewObservedS8IncrementMol;
    }

    /** @return aggregate S8 increment observed after external application [mol]. */
    public double getApplicationObservedS8IncrementMol() {
      return applicationObservedS8IncrementMol;
    }

    /** @return application minus preview aggregate S8 increment [mol]. */
    public double getS8ResidualMol() {
      return s8ResidualMol;
    }

    /** @return ULP-scaled aggregate S8 reconciliation tolerance [mol]. */
    public double getS8ToleranceMol() {
      return s8ToleranceMol;
    }

    /** @return aggregate total increment observed by the preview [mol]. */
    public double getPreviewObservedTotalIncrementMol() {
      return previewObservedTotalIncrementMol;
    }

    /** @return aggregate total increment observed after external application [mol]. */
    public double getApplicationObservedTotalIncrementMol() {
      return applicationObservedTotalIncrementMol;
    }

    /** @return application minus preview aggregate total increment [mol]. */
    public double getTotalAmountResidualMol() {
      return totalAmountResidualMol;
    }

    /** @return ULP-scaled aggregate total-amount reconciliation tolerance [mol]. */
    public double getTotalAmountToleranceMol() {
      return totalAmountToleranceMol;
    }

    /** @return largest absolute per-entry candidate S8 residual [mol]. */
    public double getMaximumEntryS8ResidualMol() {
      return maximumEntryS8ResidualMol;
    }

    /** @return largest absolute per-entry candidate total-amount residual [mol]. */
    public double getMaximumEntryTotalAmountResidualMol() {
      return maximumEntryTotalAmountResidualMol;
    }
  }
}
