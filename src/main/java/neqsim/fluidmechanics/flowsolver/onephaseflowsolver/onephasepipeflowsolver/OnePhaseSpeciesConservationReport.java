package neqsim.fluidmechanics.flowsolver.onephaseflowsolver.onephasepipeflowsolver;

import java.io.Serializable;
import java.util.Arrays;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;

/** Immutable conservative-species diagnostics for one transient pipe-flow step. */
public final class OnePhaseSpeciesConservationReport implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Reason why conservative species transport stopped. */
  public enum ConservationReason {
    /** No conservative species solve has run. */
    NOT_RUN(false),
    /** Every component inventory and composition criterion was satisfied. */
    CONVERGED(true),
    /** The supplied state was non-finite, negative, or dimensionally inconsistent. */
    INVALID_STATE(false),
    /** A zero or reversed face flow is outside the currently validated boundary model. */
    UNSUPPORTED_FLOW(false),
    /** A component inventory did not close to the integrated boundary flux. */
    COMPONENT_BALANCE_FAILED(false),
    /** Calculated mass fractions were negative, exceeded one, or did not close to unity. */
    COMPOSITION_BOUNDS_FAILED(false),
    /** Hydraulic/species fixed-point residuals did not converge within the iteration limit. */
    COUPLING_NOT_CONVERGED(false),
    /** The synchronized thermodynamic composition was inconsistent with conservative state. */
    THERMODYNAMIC_SYNC_FAILED(false);

    private final boolean converged;

    ConservationReason(boolean converged) {
      this.converged = converged;
    }

    /**
     * Check whether the species solve converged.
     *
     * @return true only for {@link #CONVERGED}
     */
    public boolean isConverged() {
      return converged;
    }
  }

  private final ConservationReason reason;
  private final String[] componentNames;
  private final double[][] massFractionProfile;
  private final double[] finalCellInventoryKg;
  private final double[][] finalComponentCellInventoryKg;
  private final double[] initialInventoryKg;
  private final double[] finalInventoryKg;
  private final double[] inletBoundaryMassKg;
  private final double[] outletBoundaryMassKg;
  private final double[] inventoryResidualKg;
  private final double[] relativeInventoryResidual;
  private final double maximumRelativeInventoryResidual;
  private final double minimumMassFraction;
  private final double maximumMassFraction;
  private final double maximumMassFractionSumError;
  private final double maximumThermodynamicMassFractionError;
  /** Advection resolution and numerical-spreading diagnostics for this accepted step. */
  private final SpeciesTransportDiagnostics transportDiagnostics;
  private final int couplingIterations;
  private final double[] maximumMassFractionChangeHistory;
  private final double[] densityResidualHistory;
  private final String message;

  OnePhaseSpeciesConservationReport(ConservationReason reason, String[] componentNames, double[][] massFractionProfile,
      double[] finalCellInventoryKg, double[] initialInventoryKg, double[] finalInventoryKg,
      double[] inletBoundaryMassKg, double[] outletBoundaryMassKg, double[] inventoryResidualKg,
      double[] relativeInventoryResidual, double maximumRelativeInventoryResidual, double minimumMassFraction,
      double maximumMassFraction, double maximumMassFractionSumError, double maximumThermodynamicMassFractionError,
      String message) {
    this(reason, componentNames, massFractionProfile, finalCellInventoryKg, initialInventoryKg, finalInventoryKg,
        inletBoundaryMassKg, outletBoundaryMassKg, inventoryResidualKg, relativeInventoryResidual,
        maximumRelativeInventoryResidual, minimumMassFraction, maximumMassFraction, maximumMassFractionSumError,
        maximumThermodynamicMassFractionError, SpeciesTransportDiagnostics.notRun(), message);
  }

  OnePhaseSpeciesConservationReport(ConservationReason reason, String[] componentNames, double[][] massFractionProfile,
      double[] finalCellInventoryKg, double[] initialInventoryKg, double[] finalInventoryKg,
      double[] inletBoundaryMassKg, double[] outletBoundaryMassKg, double[] inventoryResidualKg,
      double[] relativeInventoryResidual, double maximumRelativeInventoryResidual, double minimumMassFraction,
      double maximumMassFraction, double maximumMassFractionSumError, double maximumThermodynamicMassFractionError,
      SpeciesTransportDiagnostics transportDiagnostics, String message) {
    this(reason, componentNames, massFractionProfile, finalCellInventoryKg, initialInventoryKg, finalInventoryKg,
        inletBoundaryMassKg, outletBoundaryMassKg, inventoryResidualKg, relativeInventoryResidual,
        maximumRelativeInventoryResidual, minimumMassFraction, maximumMassFraction, maximumMassFractionSumError,
        maximumThermodynamicMassFractionError, 0, new double[0], new double[0], transportDiagnostics, message);
  }

  private OnePhaseSpeciesConservationReport(ConservationReason reason, String[] componentNames,
      double[][] massFractionProfile, double[] finalCellInventoryKg, double[] initialInventoryKg,
      double[] finalInventoryKg, double[] inletBoundaryMassKg, double[] outletBoundaryMassKg,
      double[] inventoryResidualKg, double[] relativeInventoryResidual, double maximumRelativeInventoryResidual,
      double minimumMassFraction, double maximumMassFraction, double maximumMassFractionSumError,
      double maximumThermodynamicMassFractionError, int couplingIterations, double[] maximumMassFractionChangeHistory,
      double[] densityResidualHistory, SpeciesTransportDiagnostics transportDiagnostics, String message) {
    this.reason = reason;
    this.componentNames = copy(componentNames);
    this.massFractionProfile = copy(massFractionProfile);
    this.finalCellInventoryKg = copy(finalCellInventoryKg);
    this.finalComponentCellInventoryKg = componentCellInventory(this.massFractionProfile, this.finalCellInventoryKg);
    this.initialInventoryKg = copy(initialInventoryKg);
    this.finalInventoryKg = copy(finalInventoryKg);
    this.inletBoundaryMassKg = copy(inletBoundaryMassKg);
    this.outletBoundaryMassKg = copy(outletBoundaryMassKg);
    this.inventoryResidualKg = copy(inventoryResidualKg);
    this.relativeInventoryResidual = copy(relativeInventoryResidual);
    this.maximumRelativeInventoryResidual = maximumRelativeInventoryResidual;
    this.minimumMassFraction = minimumMassFraction;
    this.maximumMassFraction = maximumMassFraction;
    this.maximumMassFractionSumError = maximumMassFractionSumError;
    this.maximumThermodynamicMassFractionError = maximumThermodynamicMassFractionError;
    this.transportDiagnostics = transportDiagnostics == null ? SpeciesTransportDiagnostics.notRun()
        : transportDiagnostics;
    this.couplingIterations = couplingIterations;
    this.maximumMassFractionChangeHistory = copy(maximumMassFractionChangeHistory);
    this.densityResidualHistory = copy(densityResidualHistory);
    this.message = message;
  }

  /**
   * Create a report for a solver that has not run.
   *
   * @return not-run report
   */
  public static OnePhaseSpeciesConservationReport notRun() {
    return new OnePhaseSpeciesConservationReport(ConservationReason.NOT_RUN, new String[0], new double[0][0],
        new double[0], new double[0], new double[0], new double[0], new double[0], new double[0], new double[0],
        Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, "Conservative species transport has not run.");
  }

  /** @return reason why transport stopped */
  public ConservationReason getReason() {
    return reason;
  }

  /** @return true when every conservative species criterion passed */
  public boolean isConverged() {
    return reason.isConverged();
  }

  /** @return defensive copy of component names in solver order */
  public String[] getComponentNames() {
    return copy(componentNames);
  }

  /** @return defensive copy of component-by-cell mass fractions */
  public double[][] getMassFractionProfile() {
    return copy(massFractionProfile);
  }

  /** @return defensive copy of final total inventory by physical finite-volume cell in kg */
  public double[] getFinalCellInventoryKg() {
    return copy(finalCellInventoryKg);
  }

  /** @return defensive copy of final component-by-cell inventories in kg */
  public double[][] getFinalComponentCellInventoryKg() {
    return copy(finalComponentCellInventoryKg);
  }

  /** @return defensive copy of previous-time component inventories in kg */
  public double[] getInitialInventoryKg() {
    return copy(initialInventoryKg);
  }

  /** @return defensive copy of final component inventories in kg */
  public double[] getFinalInventoryKg() {
    return copy(finalInventoryKg);
  }

  /** @return defensive copy of integrated inlet component masses in kg */
  public double[] getInletBoundaryMassKg() {
    return copy(inletBoundaryMassKg);
  }

  /** @return defensive copy of integrated outlet component masses in kg */
  public double[] getOutletBoundaryMassKg() {
    return copy(outletBoundaryMassKg);
  }

  /** @return defensive copy of component inventory residuals in kg */
  public double[] getInventoryResidualKg() {
    return copy(inventoryResidualKg);
  }

  /** @return defensive copy of relative component inventory residuals */
  public double[] getRelativeInventoryResidual() {
    return copy(relativeInventoryResidual);
  }

  /** @return maximum relative component inventory residual */
  public double getMaximumRelativeInventoryResidual() {
    return maximumRelativeInventoryResidual;
  }

  /** @return minimum mass fraction across components and physical cells */
  public double getMinimumMassFraction() {
    return minimumMassFraction;
  }

  /** @return maximum mass fraction across components and physical cells */
  public double getMaximumMassFraction() {
    return maximumMassFraction;
  }

  /** @return maximum absolute cell-wise mass-fraction sum error */
  public double getMaximumMassFractionSumError() {
    return maximumMassFractionSumError;
  }

  /** @return maximum conservative-versus-thermodynamic mass-fraction error */
  public double getMaximumThermodynamicMassFractionError() {
    return maximumThermodynamicMassFractionError;
  }

  /** @return immutable advection resolution and numerical-spreading diagnostic */
  public SpeciesTransportDiagnostics getTransportDiagnostics() {
    return transportDiagnostics;
  }

  /** @return number of hydraulic/species fixed-point iterations */
  public int getCouplingIterations() {
    return couplingIterations;
  }

  /** @return defensive copy of maximum mass-fraction changes by coupling iteration */
  public double[] getMaximumMassFractionChangeHistory() {
    return copy(maximumMassFractionChangeHistory);
  }

  /** @return defensive copy of EOS/FV density residuals by coupling iteration */
  public double[] getDensityResidualHistory() {
    return copy(densityResidualHistory);
  }

  /** @return diagnostic summary */
  public String getMessage() {
    return message;
  }

  /**
   * Serialize this report as stable, pretty-printed JSON.
   *
   * @return JSON representation
   */
  public String toJson() {
    JsonSerializer<Double> finiteDoubleSerializer = (value, type,
        context) -> value != null && Double.isFinite(value) ? new JsonPrimitive(value) : JsonNull.INSTANCE;
    return new GsonBuilder().registerTypeAdapter(Double.class, finiteDoubleSerializer)
        .registerTypeAdapter(Double.TYPE, finiteDoubleSerializer).serializeNulls().setPrettyPrinting().create()
        .toJson(this);
  }

  OnePhaseSpeciesConservationReport withThermodynamicSync(double maximumError, double tolerance) {
    ConservationReason updatedReason = reason;
    if (reason == ConservationReason.CONVERGED && (!Double.isFinite(maximumError) || maximumError > tolerance)) {
      updatedReason = ConservationReason.THERMODYNAMIC_SYNC_FAILED;
    }
    String updatedMessage = message + " Thermodynamic mass-fraction synchronization error=" + maximumError
        + " (tolerance " + tolerance + ").";
    return new OnePhaseSpeciesConservationReport(updatedReason, componentNames, massFractionProfile,
        finalCellInventoryKg, initialInventoryKg, finalInventoryKg, inletBoundaryMassKg, outletBoundaryMassKg,
        inventoryResidualKg, relativeInventoryResidual, maximumRelativeInventoryResidual, minimumMassFraction,
        maximumMassFraction, maximumMassFractionSumError, maximumError, couplingIterations,
        maximumMassFractionChangeHistory, densityResidualHistory, transportDiagnostics, updatedMessage);
  }

  OnePhaseSpeciesConservationReport withCouplingDiagnostics(int iterations, double[] massFractionChangeHistory,
      double[] eosDensityResidualHistory) {
    String updatedMessage = message + " Hydraulic/species fixed point iterations=" + iterations + ".";
    return new OnePhaseSpeciesConservationReport(reason, componentNames, massFractionProfile, finalCellInventoryKg,
        initialInventoryKg, finalInventoryKg, inletBoundaryMassKg, outletBoundaryMassKg, inventoryResidualKg,
        relativeInventoryResidual, maximumRelativeInventoryResidual, minimumMassFraction, maximumMassFraction,
        maximumMassFractionSumError, maximumThermodynamicMassFractionError, iterations, massFractionChangeHistory,
        eosDensityResidualHistory, transportDiagnostics, updatedMessage);
  }

  OnePhaseSpeciesConservationReport withReason(ConservationReason updatedReason, String updatedMessage) {
    return new OnePhaseSpeciesConservationReport(updatedReason, componentNames, massFractionProfile,
        finalCellInventoryKg, initialInventoryKg, finalInventoryKg, inletBoundaryMassKg, outletBoundaryMassKg,
        inventoryResidualKg, relativeInventoryResidual, maximumRelativeInventoryResidual, minimumMassFraction,
        maximumMassFraction, maximumMassFractionSumError, maximumThermodynamicMassFractionError, couplingIterations,
        maximumMassFractionChangeHistory, densityResidualHistory, transportDiagnostics, updatedMessage);
  }

  private static double[][] componentCellInventory(double[][] massFractionProfile, double[] finalCellInventoryKg) {
    if (massFractionProfile.length == 0 || finalCellInventoryKg.length == 0) {
      return new double[0][0];
    }
    double[][] inventory = new double[massFractionProfile.length][finalCellInventoryKg.length];
    for (int component = 0; component < massFractionProfile.length; component++) {
      if (massFractionProfile[component].length != finalCellInventoryKg.length) {
        throw new IllegalArgumentException(
            "Mass-fraction and cell-inventory profiles must have identical physical-cell dimensions.");
      }
      for (int cell = 0; cell < finalCellInventoryKg.length; cell++) {
        inventory[component][cell] = massFractionProfile[component][cell] * finalCellInventoryKg[cell];
      }
    }
    return inventory;
  }

  private static double[] copy(double[] values) {
    return values == null ? new double[0] : Arrays.copyOf(values, values.length);
  }

  private static String[] copy(String[] values) {
    return values == null ? new String[0] : Arrays.copyOf(values, values.length);
  }

  private static double[][] copy(double[][] values) {
    if (values == null) {
      return new double[0][0];
    }
    double[][] result = new double[values.length][];
    for (int i = 0; i < values.length; i++) {
      result[i] = copy(values[i]);
    }
    return result;
  }
}
