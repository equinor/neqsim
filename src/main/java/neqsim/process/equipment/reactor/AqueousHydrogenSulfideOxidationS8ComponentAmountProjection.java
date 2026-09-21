package neqsim.process.equipment.reactor;

import java.io.Serializable;

/**
 * Projects a verified S8 ledger-transition mass delta onto NeqSim's existing S8 component amount basis.
 *
 * <p>
 * This class performs one dimensional conversion after re-verifying the complete checkpoint-linked ledger transition.
 * It does not select a sulfur product, infer a rate, mutate a thermodynamic system, run a flash, or predict sulfur
 * deposition.
 * </p>
 *
 * @author esol
 * @version $Id: $
 */
public final class AqueousHydrogenSulfideOxidationS8ComponentAmountProjection {
  /** Molecular weight of NeqSim's existing S8 component [kg/mol]. */
  public static final double S8_MOLAR_MASS_KG_PER_MOL = 0.25648;

  /** Provenance identifier for the molecular-weight basis. */
  public static final String MOLECULAR_WEIGHT_BASIS_IDENTIFIER = "NeqSim-COMP.csv-S8-256.48-g-per-mol";

  private AqueousHydrogenSulfideOxidationS8ComponentAmountProjection() {
  }

  /**
   * Project the verified incremental S8 mass represented by one ledger transition to mol and kmol.
   *
   * @param prior persisted prior ledger state
   * @param candidate unchanged or strict-append candidate state
   * @param transition checkpoint-linked transition receipt for the two states
   * @return immutable dimensional projection
   * @throws IllegalArgumentException if the transition is missing, does not verify, or cannot be represented
   */
  public static Result project(AqueousHydrogenSulfideOxidationS8TransferLedger.Result prior,
      AqueousHydrogenSulfideOxidationS8TransferLedger.Result candidate,
      AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.Result transition) {
    if (transition == null) {
      throw new IllegalArgumentException("S8 transfer ledger transition receipt is required");
    }
    if (!AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.verify(prior, candidate, transition)) {
      throw new IllegalArgumentException("S8 transfer ledger transition receipt does not match the supplied states");
    }

    double massKg = transition.getTransferredS8MassDeltaKg();
    requireNonNegativeFinite(massKg, "Transferred S8 mass delta");
    double amountMol = massKg / S8_MOLAR_MASS_KG_PER_MOL;
    double amountKmol = amountMol / 1000.0;
    requireNonNegativeFinite(amountMol, "Transferred S8 amount");
    requireNonNegativeFinite(amountKmol, "Transferred S8 amount");
    if (massKg > 0.0 && (amountMol == 0.0 || amountKmol == 0.0)) {
      throw new IllegalArgumentException("Positive transferred S8 mass is not representable as a positive amount");
    }

    double reconstructedMassKg = amountMol * S8_MOLAR_MASS_KG_PER_MOL;
    double closureResidualKg = massKg - reconstructedMassKg;
    requireFinite(reconstructedMassKg, "Reconstructed S8 mass");
    requireFinite(closureResidualKg, "S8 amount-projection closure residual");
    double closureToleranceKg = 8.0 * Math.ulp(Math.max(Math.abs(massKg), Math.abs(reconstructedMassKg)));
    if (Math.abs(closureResidualKg) > closureToleranceKg) {
      throw new IllegalArgumentException("S8 amount projection does not close on its transferred mass");
    }

    return new Result(transition.getLedgerIdentifier(), transition.getProductIdentityBasisIdentifier(),
        transition.getPriorCheckpointHex(), transition.getCandidateCheckpointHex(), transition.getTransitionDigestHex(),
        transition.isUnchanged(), transition.isStrictAppend(), massKg, amountMol, amountKmol, reconstructedMassKg,
        closureResidualKg);
  }

  /**
   * Require a finite, non-negative numeric value.
   *
   * @param value value to validate
   * @param name diagnostic field name
   * @throws IllegalArgumentException if the value is negative or not finite
   */
  private static void requireNonNegativeFinite(double value, String name) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(name + " must be finite and non-negative");
    }
  }

  /**
   * Require a finite numeric value.
   *
   * @param value value to validate
   * @param name diagnostic field name
   * @throws IllegalArgumentException if the value is not finite
   */
  private static void requireFinite(double value, String name) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException(name + " must be finite");
    }
  }

  /** Immutable evidence for one verified mass-to-component-amount projection. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String ledgerIdentifier;
    private final String productIdentityBasisIdentifier;
    private final String priorCheckpointHex;
    private final String candidateCheckpointHex;
    private final String transitionDigestHex;
    private final boolean unchanged;
    private final boolean strictAppend;
    private final double transferredS8MassKg;
    private final double transferredS8AmountMol;
    private final double transferredS8AmountKmol;
    private final double reconstructedS8MassKg;
    private final double massClosureResidualKg;

    private Result(String ledgerIdentifier, String productIdentityBasisIdentifier, String priorCheckpointHex,
        String candidateCheckpointHex, String transitionDigestHex, boolean unchanged, boolean strictAppend,
        double transferredS8MassKg, double transferredS8AmountMol, double transferredS8AmountKmol,
        double reconstructedS8MassKg, double massClosureResidualKg) {
      this.ledgerIdentifier = ledgerIdentifier;
      this.productIdentityBasisIdentifier = productIdentityBasisIdentifier;
      this.priorCheckpointHex = priorCheckpointHex;
      this.candidateCheckpointHex = candidateCheckpointHex;
      this.transitionDigestHex = transitionDigestHex;
      this.unchanged = unchanged;
      this.strictAppend = strictAppend;
      this.transferredS8MassKg = transferredS8MassKg;
      this.transferredS8AmountMol = transferredS8AmountMol;
      this.transferredS8AmountKmol = transferredS8AmountKmol;
      this.reconstructedS8MassKg = reconstructedS8MassKg;
      this.massClosureResidualKg = massClosureResidualKg;
    }

    /** @return existing NeqSim component name represented by the projection. */
    public String getComponentName() {
      return AqueousHydrogenSulfideOxidationS8Transfer.S8_COMPONENT_NAME;
    }

    /** @return NeqSim component molecular weight used by this projection [kg/mol]. */
    public double getS8MolarMassKgPerMol() {
      return S8_MOLAR_MASS_KG_PER_MOL;
    }

    /** @return identifier for the existing NeqSim component-database molecular-weight basis. */
    public String getMolecularWeightBasisIdentifier() {
      return MOLECULAR_WEIGHT_BASIS_IDENTIFIER;
    }

    /** @return caller-supplied ledger identifier. */
    public String getLedgerIdentifier() {
      return ledgerIdentifier;
    }

    /** @return caller-supplied basis for selecting the S8 representation. */
    public String getProductIdentityBasisIdentifier() {
      return productIdentityBasisIdentifier;
    }

    /** @return canonical fingerprint of the prior ledger state. */
    public String getPriorCheckpointHex() {
      return priorCheckpointHex;
    }

    /** @return canonical fingerprint of the candidate ledger state. */
    public String getCandidateCheckpointHex() {
      return candidateCheckpointHex;
    }

    /** @return canonical fingerprint of the verified transition. */
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

    /** @return incremental mass represented as NeqSim's S8 component [kg]. */
    public double getTransferredS8MassKg() {
      return transferredS8MassKg;
    }

    /** @return incremental amount represented as NeqSim's S8 component [mol]. */
    public double getTransferredS8AmountMol() {
      return transferredS8AmountMol;
    }

    /** @return incremental amount represented as NeqSim's S8 component [kmol]. */
    public double getTransferredS8AmountKmol() {
      return transferredS8AmountKmol;
    }

    /** @return mass reconstructed from the projected S8 amount [kg]. */
    public double getReconstructedS8MassKg() {
      return reconstructedS8MassKg;
    }

    /** @return transferred minus reconstructed S8 mass [kg]. */
    public double getMassClosureResidualKg() {
      return massClosureResidualKg;
    }
  }
}
