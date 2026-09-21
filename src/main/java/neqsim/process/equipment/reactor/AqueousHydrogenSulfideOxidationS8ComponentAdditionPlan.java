package neqsim.process.equipment.reactor;

import java.io.Serializable;

/**
 * Prepares a non-mutating, target-scoped plan for adding a verified S8 ledger-transition amount.
 *
 * <p>
 * The complete checkpoint-linked transition is re-verified through
 * {@link AqueousHydrogenSulfideOxidationS8ComponentAmountProjection}. This class does not inspect
 * or mutate a thermodynamic system, reserve an idempotency key, run a flash, or predict sulfur
 * deposition.
 * </p>
 *
 * @author esol
 * @version $Id: $
 */
public final class AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan {
  private static final int MAXIMUM_IDENTIFIER_LENGTH = 256;

  private AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan() {
  }

  /**
   * Create a target-scoped plan from one verified ledger transition.
   *
   * @param prior persisted prior ledger state
   * @param candidate unchanged or strict-append candidate ledger state
   * @param transition checkpoint-linked transition receipt for the two states
   * @param targetStateIdentifier caller's stable identifier for the expected target state
   * @param applicationIdempotencyKey key a later executor must atomically consume at most once
   * @param priorS8AmountMol expected S8 amount in the target before application [mol]
   * @return immutable component-addition plan
   * @throws IllegalArgumentException if transition evidence, identifiers, or numeric values are
   *         invalid or if the addition is not representable
   */
  public static Result create(AqueousHydrogenSulfideOxidationS8TransferLedger.Result prior,
      AqueousHydrogenSulfideOxidationS8TransferLedger.Result candidate,
      AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.Result transition,
      String targetStateIdentifier, String applicationIdempotencyKey, double priorS8AmountMol) {
    String targetIdentifier = requireIdentifier(targetStateIdentifier, "Target-state identifier");
    String idempotencyKey =
        requireIdentifier(applicationIdempotencyKey, "Application idempotency key");
    requireNonNegativeFinite(priorS8AmountMol, "Prior target S8 amount");

    AqueousHydrogenSulfideOxidationS8ComponentAmountProjection.Result projection =
        AqueousHydrogenSulfideOxidationS8ComponentAmountProjection.project(prior, candidate,
            transition);
    double incrementMol = projection.getTransferredS8AmountMol();
    requireNonNegativeFinite(incrementMol, "Transferred S8 amount");

    double candidateAmountMol = priorS8AmountMol + incrementMol;
    requireNonNegativeFinite(candidateAmountMol, "Candidate target S8 amount");
    if (incrementMol > 0.0 && candidateAmountMol == priorS8AmountMol) {
      throw new IllegalArgumentException(
          "Positive transferred S8 amount is not representable at the target amount scale");
    }

    double reconstructedIncrementMol = candidateAmountMol - priorS8AmountMol;
    double amountClosureResidualMol = incrementMol - reconstructedIncrementMol;
    requireNonNegativeFinite(reconstructedIncrementMol, "Reconstructed S8 amount increment");
    requireFinite(amountClosureResidualMol, "S8 addition-plan amount-closure residual");
    double closureToleranceMol =
        8.0 * Math.max(Math.ulp(Math.abs(candidateAmountMol)),
            Math.max(Math.ulp(Math.abs(priorS8AmountMol)), Math.ulp(Math.abs(incrementMol))));
    if (Math.abs(amountClosureResidualMol) > closureToleranceMol) {
      throw new IllegalArgumentException(
          "S8 component-addition plan does not close on its transferred amount");
    }

    return new Result(projection.getLedgerIdentifier(),
        projection.getProductIdentityBasisIdentifier(), targetIdentifier, idempotencyKey,
        projection.getPriorCheckpointHex(), projection.getCandidateCheckpointHex(),
        projection.getTransitionDigestHex(), projection.isUnchanged(),
        projection.isStrictAppend(), priorS8AmountMol, incrementMol,
        projection.getTransferredS8AmountKmol(), candidateAmountMol, reconstructedIncrementMol,
        amountClosureResidualMol);
  }

  private static String requireIdentifier(String identifier, String name) {
    if (identifier == null || identifier.isEmpty() || !identifier.equals(identifier.trim())
        || identifier.length() > MAXIMUM_IDENTIFIER_LENGTH) {
      throw new IllegalArgumentException(
          name + " must be non-blank, trimmed, and no longer than 256 characters");
    }
    return identifier;
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

  /** Immutable evidence for one target-scoped S8 component-addition plan. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String ledgerIdentifier;
    private final String productIdentityBasisIdentifier;
    private final String targetStateIdentifier;
    private final String applicationIdempotencyKey;
    private final String priorCheckpointHex;
    private final String candidateCheckpointHex;
    private final String transitionDigestHex;
    private final boolean unchanged;
    private final boolean strictAppend;
    private final double priorS8AmountMol;
    private final double transferredS8AmountMol;
    private final double transferredS8AmountKmol;
    private final double candidateS8AmountMol;
    private final double reconstructedIncrementMol;
    private final double amountClosureResidualMol;

    private Result(String ledgerIdentifier, String productIdentityBasisIdentifier,
        String targetStateIdentifier, String applicationIdempotencyKey, String priorCheckpointHex,
        String candidateCheckpointHex, String transitionDigestHex, boolean unchanged,
        boolean strictAppend, double priorS8AmountMol, double transferredS8AmountMol,
        double transferredS8AmountKmol, double candidateS8AmountMol,
        double reconstructedIncrementMol, double amountClosureResidualMol) {
      this.ledgerIdentifier = ledgerIdentifier;
      this.productIdentityBasisIdentifier = productIdentityBasisIdentifier;
      this.targetStateIdentifier = targetStateIdentifier;
      this.applicationIdempotencyKey = applicationIdempotencyKey;
      this.priorCheckpointHex = priorCheckpointHex;
      this.candidateCheckpointHex = candidateCheckpointHex;
      this.transitionDigestHex = transitionDigestHex;
      this.unchanged = unchanged;
      this.strictAppend = strictAppend;
      this.priorS8AmountMol = priorS8AmountMol;
      this.transferredS8AmountMol = transferredS8AmountMol;
      this.transferredS8AmountKmol = transferredS8AmountKmol;
      this.candidateS8AmountMol = candidateS8AmountMol;
      this.reconstructedIncrementMol = reconstructedIncrementMol;
      this.amountClosureResidualMol = amountClosureResidualMol;
    }

    /** @return existing NeqSim component name represented by the plan. */
    public String getComponentName() {
      return AqueousHydrogenSulfideOxidationS8Transfer.S8_COMPONENT_NAME;
    }

    /** @return molecular-weight basis used by the upstream amount projection. */
    public String getMolecularWeightBasisIdentifier() {
      return AqueousHydrogenSulfideOxidationS8ComponentAmountProjection
          .MOLECULAR_WEIGHT_BASIS_IDENTIFIER;
    }

    /** @return caller-supplied ledger identifier. */
    public String getLedgerIdentifier() {
      return ledgerIdentifier;
    }

    /** @return caller-supplied basis for selecting the S8 representation. */
    public String getProductIdentityBasisIdentifier() {
      return productIdentityBasisIdentifier;
    }

    /** @return caller-supplied stable identifier for the expected target state. */
    public String getTargetStateIdentifier() {
      return targetStateIdentifier;
    }

    /** @return key a later executor must atomically consume at most once. */
    public String getApplicationIdempotencyKey() {
      return applicationIdempotencyKey;
    }

    /** @return canonical fingerprint of the prior ledger state. */
    public String getPriorCheckpointHex() {
      return priorCheckpointHex;
    }

    /** @return canonical fingerprint of the candidate ledger state. */
    public String getCandidateCheckpointHex() {
      return candidateCheckpointHex;
    }

    /** @return canonical fingerprint of the verified ledger transition. */
    public String getTransitionDigestHex() {
      return transitionDigestHex;
    }

    /** @return true when prior and candidate are the same exact ledger state. */
    public boolean isUnchanged() {
      return unchanged;
    }

    /** @return true when candidate is a strict append of the prior ledger. */
    public boolean isStrictAppend() {
      return strictAppend;
    }

    /** @return true when the planned addition is strictly positive. */
    public boolean requiresMutation() {
      return transferredS8AmountMol > 0.0;
    }

    /** @return expected S8 amount in the target before application [mol]. */
    public double getPriorS8AmountMol() {
      return priorS8AmountMol;
    }

    /** @return verified incremental S8 amount to add [mol]. */
    public double getTransferredS8AmountMol() {
      return transferredS8AmountMol;
    }

    /** @return verified incremental S8 amount to add [kmol]. */
    public double getTransferredS8AmountKmol() {
      return transferredS8AmountKmol;
    }

    /** @return expected S8 amount after one application [mol]. */
    public double getCandidateS8AmountMol() {
      return candidateS8AmountMol;
    }

    /** @return candidate minus prior S8 amount [mol]. */
    public double getReconstructedIncrementMol() {
      return reconstructedIncrementMol;
    }

    /** @return transferred minus reconstructed S8 amount [mol]. */
    public double getAmountClosureResidualMol() {
      return amountClosureResidualMol;
    }
  }
}
