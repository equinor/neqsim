package neqsim.process.equipment.reactor;

import java.io.Serializable;
import java.util.Map;
import java.util.TreeMap;
import neqsim.thermo.system.SystemInterface;

/**
 * Verifies an externally applied S8 component-addition plan without mutating either system.
 *
 * <p>
 * The caller supplies the target identity, application idempotency key, and thermodynamic systems immediately before
 * and after its separately owned application step. The verifier compares deterministic component inventories, including
 * total-mole closure, and returns immutable evidence. It does not reserve or consume the key, call
 * {@code addComponent}, run a flash, or authenticate either system as the real target.
 * </p>
 *
 * @author esol
 * @version $Id: $
 */
public final class AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt {
  private static final double COMPARISON_ULPS = 8.0;
  private static final double SUMMATION_ULPS_PER_COMPONENT = 16.0;

  private AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt() {
  }

  /**
   * Verify one externally applied S8 component-addition plan.
   *
   * @param plan verified target-scoped component-addition plan
   * @param observedTargetStateIdentifier identifier observed by the external executor
   * @param observedApplicationIdempotencyKey idempotency key observed by the external executor
   * @param priorTarget target system immediately before the external application step
   * @param candidateTarget target system immediately after the external application step
   * @return immutable application-verification receipt
   * @throws IllegalArgumentException if identities, inventories, or numerical closure do not match the plan
   */
  public static Result verify(AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result plan,
      String observedTargetStateIdentifier, String observedApplicationIdempotencyKey, SystemInterface priorTarget,
      SystemInterface candidateTarget) {
    if (plan == null) {
      throw new IllegalArgumentException("S8 component-addition plan cannot be null");
    }
    requireExactIdentity(plan.getTargetStateIdentifier(), observedTargetStateIdentifier, "Target-state identifier");
    requireExactIdentity(plan.getApplicationIdempotencyKey(), observedApplicationIdempotencyKey,
        "Application idempotency key");
    if (!AqueousHydrogenSulfideOxidationS8Transfer.S8_COMPONENT_NAME.equals(plan.getComponentName())) {
      throw new IllegalArgumentException("Component-addition plan does not represent NeqSim S8");
    }
    if (plan.requiresMutation() && priorTarget == candidateTarget) {
      throw new IllegalArgumentException("Positive S8 application requires distinct before and after system objects");
    }

    Inventory prior = inspect(priorTarget, "Prior target");
    Inventory candidate = inspect(candidateTarget, "Candidate target");
    String componentName = plan.getComponentName();
    double priorS8AmountMol = amountOrZero(prior.componentAmountsMol, componentName);
    double candidateS8AmountMol = amountOrZero(candidate.componentAmountsMol, componentName);

    requireClose(plan.getPriorS8AmountMol(), priorS8AmountMol, "Observed prior S8 amount does not match the plan");
    requireClose(plan.getCandidateS8AmountMol(), candidateS8AmountMol,
        "Observed candidate S8 amount does not match the plan");

    TreeMap<String, Double> priorNonS8 = withoutComponent(prior.componentAmountsMol, componentName);
    TreeMap<String, Double> candidateNonS8 = withoutComponent(candidate.componentAmountsMol, componentName);
    if (!priorNonS8.keySet().equals(candidateNonS8.keySet())) {
      throw new IllegalArgumentException("Non-S8 component identities changed during the observed application");
    }
    double maximumNonS8InventoryResidualMol = 0.0;
    for (Map.Entry<String, Double> entry : priorNonS8.entrySet()) {
      double observed = candidateNonS8.get(entry.getKey());
      double residual = observed - entry.getValue();
      requireFinite(residual, "Non-S8 component inventory residual");
      requireClose(entry.getValue(), observed, "Non-S8 component amount changed during the observed application");
      maximumNonS8InventoryResidualMol = Math.max(maximumNonS8InventoryResidualMol, Math.abs(residual));
    }

    double observedIncrementMol = candidateS8AmountMol - priorS8AmountMol;
    requireNonNegativeFinite(observedIncrementMol, "Observed S8 amount increment");
    double planApplicationResidualMol = plan.getTransferredS8AmountMol() - observedIncrementMol;
    requireFinite(planApplicationResidualMol, "Plan-application S8 amount residual");
    requireIncrementClose(plan.getTransferredS8AmountMol(), observedIncrementMol, priorS8AmountMol,
        candidateS8AmountMol, "Observed S8 amount increment does not match the plan");

    double observedTotalIncrementMol = candidate.totalAmountMol - prior.totalAmountMol;
    requireFinite(observedTotalIncrementMol, "Observed total-mole increment");
    double totalAmountClosureResidualMol = observedTotalIncrementMol - observedIncrementMol;
    requireFinite(totalAmountClosureResidualMol, "Observed total-mole closure residual");
    requireIncrementClose(observedIncrementMol, observedTotalIncrementMol, prior.totalAmountMol,
        candidate.totalAmountMol, "Total-mole change does not close on the observed S8 increment");

    return new Result(plan.getLedgerIdentifier(), plan.getProductIdentityBasisIdentifier(),
        plan.getMolecularWeightBasisIdentifier(), plan.getTargetStateIdentifier(), plan.getApplicationIdempotencyKey(),
        plan.getPriorCheckpointHex(), plan.getCandidateCheckpointHex(), plan.getTransitionDigestHex(),
        plan.isUnchanged(), plan.isStrictAppend(), componentName, priorS8AmountMol, plan.getTransferredS8AmountMol(),
        candidateS8AmountMol, observedIncrementMol, planApplicationResidualMol, prior.totalAmountMol,
        candidate.totalAmountMol, observedTotalIncrementMol, totalAmountClosureResidualMol, priorNonS8.size(),
        maximumNonS8InventoryResidualMol);
  }

  private static Inventory inspect(SystemInterface system, String name) {
    if (system == null) {
      throw new IllegalArgumentException(name + " system cannot be null");
    }
    int numberOfComponents = system.getNumberOfComponents();
    if (numberOfComponents < 0) {
      throw new IllegalArgumentException(name + " component count cannot be negative");
    }
    TreeMap<String, Double> amounts = new TreeMap<>();
    double sum = 0.0;
    double compensation = 0.0;
    for (int componentIndex = 0; componentIndex < numberOfComponents; componentIndex++) {
      if (system.getComponent(componentIndex) == null) {
        throw new IllegalArgumentException(name + " contains a null component");
      }
      String componentName = system.getComponent(componentIndex).getName();
      if (componentName == null || componentName.isEmpty()) {
        throw new IllegalArgumentException(name + " contains a component without a name");
      }
      double amountMol = system.getComponent(componentIndex).getNumberOfmoles();
      requireNonNegativeFinite(amountMol, name + " component amount");
      if (amounts.put(componentName, amountMol) != null) {
        throw new IllegalArgumentException(name + " contains a duplicate component identity");
      }
      double corrected = amountMol - compensation;
      double updated = sum + corrected;
      compensation = (updated - sum) - corrected;
      sum = updated;
    }
    requireNonNegativeFinite(sum, name + " summed component amount");
    double totalAmountMol = system.getTotalNumberOfMoles();
    requireNonNegativeFinite(totalAmountMol, name + " total amount");
    double inventoryResidualMol = totalAmountMol - sum;
    requireFinite(inventoryResidualMol, name + " inventory residual");
    double toleranceMol = SUMMATION_ULPS_PER_COMPONENT * Math.max(1, numberOfComponents)
        * Math.max(Math.ulp(Math.abs(totalAmountMol)), Math.ulp(Math.abs(sum)));
    requireFinite(toleranceMol, name + " inventory tolerance");
    if (Math.abs(inventoryResidualMol) > toleranceMol) {
      throw new IllegalArgumentException(name + " component amounts do not close on total amount");
    }
    return new Inventory(amounts, totalAmountMol);
  }

  private static TreeMap<String, Double> withoutComponent(Map<String, Double> amounts, String excludedComponent) {
    TreeMap<String, Double> copy = new TreeMap<>(amounts);
    copy.remove(excludedComponent);
    return copy;
  }

  private static double amountOrZero(Map<String, Double> amounts, String componentName) {
    Double amount = amounts.get(componentName);
    return amount == null ? 0.0 : amount;
  }

  private static void requireExactIdentity(String expected, String observed, String name) {
    if (observed == null || !expected.equals(observed)) {
      throw new IllegalArgumentException(name + " does not match the component-addition plan");
    }
  }

  private static void requireClose(double expected, double observed, String message) {
    requireFinite(expected, "Expected comparison amount");
    requireFinite(observed, "Observed comparison amount");
    double residual = observed - expected;
    requireFinite(residual, "Comparison residual");
    double tolerance = COMPARISON_ULPS * Math.max(Math.ulp(Math.abs(expected)), Math.ulp(Math.abs(observed)));
    requireFinite(tolerance, "Comparison tolerance");
    if (Math.abs(residual) > tolerance) {
      throw new IllegalArgumentException(message);
    }
  }

  private static void requireIncrementClose(double expected, double observed, double priorAmount,
      double candidateAmount, String message) {
    requireFinite(expected, "Expected increment");
    requireFinite(observed, "Observed increment");
    requireFinite(priorAmount, "Prior comparison amount");
    requireFinite(candidateAmount, "Candidate comparison amount");
    double residual = observed - expected;
    requireFinite(residual, "Increment comparison residual");
    double tolerance = COMPARISON_ULPS * Math.max(Math.ulp(Math.abs(expected)), Math.max(Math.ulp(Math.abs(observed)),
        Math.max(Math.ulp(Math.abs(priorAmount)), Math.ulp(Math.abs(candidateAmount)))));
    requireFinite(tolerance, "Increment comparison tolerance");
    if (Math.abs(residual) > tolerance) {
      throw new IllegalArgumentException(message);
    }
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

  private static final class Inventory {
    private final TreeMap<String, Double> componentAmountsMol;
    private final double totalAmountMol;

    private Inventory(TreeMap<String, Double> componentAmountsMol, double totalAmountMol) {
      this.componentAmountsMol = componentAmountsMol;
      this.totalAmountMol = totalAmountMol;
    }
  }

  /** Immutable evidence for one verified external S8 component application. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String ledgerIdentifier;
    private final String productIdentityBasisIdentifier;
    private final String molecularWeightBasisIdentifier;
    private final String targetStateIdentifier;
    private final String applicationIdempotencyKey;
    private final String priorCheckpointHex;
    private final String candidateCheckpointHex;
    private final String transitionDigestHex;
    private final boolean unchanged;
    private final boolean strictAppend;
    private final String componentName;
    private final double priorS8AmountMol;
    private final double plannedS8IncrementMol;
    private final double candidateS8AmountMol;
    private final double observedS8IncrementMol;
    private final double planApplicationResidualMol;
    private final double priorTotalAmountMol;
    private final double candidateTotalAmountMol;
    private final double observedTotalIncrementMol;
    private final double totalAmountClosureResidualMol;
    private final int preservedNonS8ComponentCount;
    private final double maximumNonS8InventoryResidualMol;

    private Result(String ledgerIdentifier, String productIdentityBasisIdentifier,
        String molecularWeightBasisIdentifier, String targetStateIdentifier, String applicationIdempotencyKey,
        String priorCheckpointHex, String candidateCheckpointHex, String transitionDigestHex, boolean unchanged,
        boolean strictAppend, String componentName, double priorS8AmountMol, double plannedS8IncrementMol,
        double candidateS8AmountMol, double observedS8IncrementMol, double planApplicationResidualMol,
        double priorTotalAmountMol, double candidateTotalAmountMol, double observedTotalIncrementMol,
        double totalAmountClosureResidualMol, int preservedNonS8ComponentCount,
        double maximumNonS8InventoryResidualMol) {
      this.ledgerIdentifier = ledgerIdentifier;
      this.productIdentityBasisIdentifier = productIdentityBasisIdentifier;
      this.molecularWeightBasisIdentifier = molecularWeightBasisIdentifier;
      this.targetStateIdentifier = targetStateIdentifier;
      this.applicationIdempotencyKey = applicationIdempotencyKey;
      this.priorCheckpointHex = priorCheckpointHex;
      this.candidateCheckpointHex = candidateCheckpointHex;
      this.transitionDigestHex = transitionDigestHex;
      this.unchanged = unchanged;
      this.strictAppend = strictAppend;
      this.componentName = componentName;
      this.priorS8AmountMol = priorS8AmountMol;
      this.plannedS8IncrementMol = plannedS8IncrementMol;
      this.candidateS8AmountMol = candidateS8AmountMol;
      this.observedS8IncrementMol = observedS8IncrementMol;
      this.planApplicationResidualMol = planApplicationResidualMol;
      this.priorTotalAmountMol = priorTotalAmountMol;
      this.candidateTotalAmountMol = candidateTotalAmountMol;
      this.observedTotalIncrementMol = observedTotalIncrementMol;
      this.totalAmountClosureResidualMol = totalAmountClosureResidualMol;
      this.preservedNonS8ComponentCount = preservedNonS8ComponentCount;
      this.maximumNonS8InventoryResidualMol = maximumNonS8InventoryResidualMol;
    }

    /** @return caller-supplied ledger identifier. */
    public String getLedgerIdentifier() {
      return ledgerIdentifier;
    }

    /** @return caller-supplied basis for selecting the S8 representation. */
    public String getProductIdentityBasisIdentifier() {
      return productIdentityBasisIdentifier;
    }

    /** @return molecular-weight basis inherited from the verified plan. */
    public String getMolecularWeightBasisIdentifier() {
      return molecularWeightBasisIdentifier;
    }

    /** @return target-state identifier matched during verification. */
    public String getTargetStateIdentifier() {
      return targetStateIdentifier;
    }

    /** @return application idempotency key matched during verification. */
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

    /** @return true when the plan represents an unchanged ledger state. */
    public boolean isUnchanged() {
      return unchanged;
    }

    /** @return true when the plan represents a strict ledger append. */
    public boolean isStrictAppend() {
      return strictAppend;
    }

    /** @return verified NeqSim component name. */
    public String getComponentName() {
      return componentName;
    }

    /** @return observed S8 amount before application [mol]. */
    public double getPriorS8AmountMol() {
      return priorS8AmountMol;
    }

    /** @return S8 increment specified by the plan [mol]. */
    public double getPlannedS8IncrementMol() {
      return plannedS8IncrementMol;
    }

    /** @return observed S8 amount after application [mol]. */
    public double getCandidateS8AmountMol() {
      return candidateS8AmountMol;
    }

    /** @return observed candidate-minus-prior S8 amount [mol]. */
    public double getObservedS8IncrementMol() {
      return observedS8IncrementMol;
    }

    /** @return planned minus observed S8 increment [mol]. */
    public double getPlanApplicationResidualMol() {
      return planApplicationResidualMol;
    }

    /** @return observed total amount before application [mol]. */
    public double getPriorTotalAmountMol() {
      return priorTotalAmountMol;
    }

    /** @return observed total amount after application [mol]. */
    public double getCandidateTotalAmountMol() {
      return candidateTotalAmountMol;
    }

    /** @return observed candidate-minus-prior total amount [mol]. */
    public double getObservedTotalIncrementMol() {
      return observedTotalIncrementMol;
    }

    /** @return total increment minus observed S8 increment [mol]. */
    public double getTotalAmountClosureResidualMol() {
      return totalAmountClosureResidualMol;
    }

    /** @return number of non-S8 component identities preserved by the application. */
    public int getPreservedNonS8ComponentCount() {
      return preservedNonS8ComponentCount;
    }

    /** @return largest absolute non-S8 component inventory residual [mol]. */
    public double getMaximumNonS8InventoryResidualMol() {
      return maximumNonS8InventoryResidualMol;
    }
  }
}
