package neqsim.process.equipment.pipeline;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;

/** Immutable component, phase-transfer, boundedness, and synchronization diagnostics for one transient call. */
public final class TwoFluidComponentConservationReport implements Serializable {
  private static final long serialVersionUID = 1L;
  private static final int PHASE_COUNT = 3;

  /** Hydrodynamic phase identity used by component arrays. */
  public enum Phase {
    /** Gas phase. */
    GAS(0),
    /** Hydrocarbon-liquid phase. */
    OIL(1),
    /** Aqueous phase. */
    WATER(2);

    private final int index;

    Phase(int index) {
      this.index = index;
    }
  }

  private final double elapsedTimeSeconds;
  private final int acceptedSubsteps;
  private final String[] componentNames;
  private final double[] initialInventoryKg;
  private final double[] finalInventoryKg;
  private final double[] inletBoundaryMassKg;
  private final double[] outletBoundaryMassKg;
  private final double[] inventoryResidualKg;
  private final double[] relativeInventoryResidual;
  private final double maximumRelativeInventoryResidual;
  private final double[][] finalPhaseInventoryKg;
  private final double[][] interphaseTransferKg;
  private final double maximumInterphaseTransferResidualKg;
  private final double[][][] phaseMassFractionProfile;
  private final double minimumMassFraction;
  private final double maximumMassFraction;
  private final double maximumMassFractionSumError;
  private final double maximumPhaseMassSynchronizationErrorKg;
  private final double interphaseLatentHeatEnergyJ;
  private final boolean converged;
  private final String message;

  /**
   * Create an immutable component-conservation snapshot.
   *
   * <p>
   * This constructor is public so the component transport implementation in the sibling two-phase-pipe package can
   * publish diagnostics. Callers normally obtain reports from
   * {@link TwoFluidPipe#getLastComponentConservationReport()}.
   * </p>
   *
   * @throws IllegalArgumentException if names or array dimensions are inconsistent, required numeric values are
   * non-finite, or elapsed time/substep count is negative
   */
  public TwoFluidComponentConservationReport(double elapsedTimeSeconds, int acceptedSubsteps, String[] componentNames,
      double[] initialInventoryKg, double[] finalInventoryKg, double[] inletBoundaryMassKg,
      double[] outletBoundaryMassKg, double[] inventoryResidualKg, double[] relativeInventoryResidual,
      double maximumRelativeInventoryResidual, double[][] finalPhaseInventoryKg, double[][] interphaseTransferKg,
      double maximumInterphaseTransferResidualKg, double[][][] phaseMassFractionProfile, double minimumMassFraction,
      double maximumMassFraction, double maximumMassFractionSumError, double maximumPhaseMassSynchronizationErrorKg,
      double interphaseLatentHeatEnergyJ, boolean converged, String message) {
    this.elapsedTimeSeconds = requireFiniteNonNegative(elapsedTimeSeconds, "elapsedTimeSeconds");
    if (acceptedSubsteps < 0) {
      throw new IllegalArgumentException("acceptedSubsteps must be non-negative");
    }
    this.acceptedSubsteps = acceptedSubsteps;
    this.componentNames = requireComponentNames(componentNames);
    int componentCount = this.componentNames.length;
    this.initialInventoryKg = requireComponentValues(initialInventoryKg, "initialInventoryKg", componentCount);
    this.finalInventoryKg = requireComponentValues(finalInventoryKg, "finalInventoryKg", componentCount);
    this.inletBoundaryMassKg = requireComponentValues(inletBoundaryMassKg, "inletBoundaryMassKg", componentCount);
    this.outletBoundaryMassKg = requireComponentValues(outletBoundaryMassKg, "outletBoundaryMassKg", componentCount);
    this.inventoryResidualKg = requireComponentValues(inventoryResidualKg, "inventoryResidualKg", componentCount);
    this.relativeInventoryResidual = requireComponentValues(relativeInventoryResidual, "relativeInventoryResidual",
        componentCount);
    this.maximumRelativeInventoryResidual = requireFinite(maximumRelativeInventoryResidual,
        "maximumRelativeInventoryResidual");
    this.finalPhaseInventoryKg = requirePhaseComponentValues(finalPhaseInventoryKg, "finalPhaseInventoryKg",
        componentCount);
    this.interphaseTransferKg = requirePhaseComponentValues(interphaseTransferKg, "interphaseTransferKg",
        componentCount);
    this.maximumInterphaseTransferResidualKg = requireFinite(maximumInterphaseTransferResidualKg,
        "maximumInterphaseTransferResidualKg");
    this.phaseMassFractionProfile = requireProfiles(phaseMassFractionProfile, componentCount);
    requireMassFractionBounds(minimumMassFraction, maximumMassFraction);
    this.minimumMassFraction = minimumMassFraction;
    this.maximumMassFraction = maximumMassFraction;
    this.maximumMassFractionSumError = requireFinite(maximumMassFractionSumError, "maximumMassFractionSumError");
    this.maximumPhaseMassSynchronizationErrorKg = requireFinite(maximumPhaseMassSynchronizationErrorKg,
        "maximumPhaseMassSynchronizationErrorKg");
    this.interphaseLatentHeatEnergyJ = requireFinite(interphaseLatentHeatEnergyJ, "interphaseLatentHeatEnergyJ");
    this.converged = converged;
    if (message == null) {
      throw new IllegalArgumentException("message cannot be null");
    }
    this.message = message;
  }

  /** @return accepted elapsed time in seconds */
  public double getElapsedTimeSeconds() {
    return elapsedTimeSeconds;
  }

  /** @return number of accepted hydrodynamic substeps represented by the report */
  public int getAcceptedSubsteps() {
    return acceptedSubsteps;
  }

  /** @return deterministic component order */
  public String[] getComponentNames() {
    return copy(componentNames);
  }

  /** @return total initial component inventories in kg */
  public double[] getInitialInventoryKg() {
    return copy(initialInventoryKg);
  }

  /** @return total final component inventories in kg */
  public double[] getFinalInventoryKg() {
    return copy(finalInventoryKg);
  }

  /** @return integrated inlet component masses in kg */
  public double[] getInletBoundaryMassKg() {
    return copy(inletBoundaryMassKg);
  }

  /** @return integrated outlet component masses in kg */
  public double[] getOutletBoundaryMassKg() {
    return copy(outletBoundaryMassKg);
  }

  /** @return total component residuals {@code final - initial - inlet + outlet} in kg */
  public double[] getInventoryResidualKg() {
    return copy(inventoryResidualKg);
  }

  /** @return defensive relative component-residual array */
  public double[] getRelativeInventoryResidual() {
    return copy(relativeInventoryResidual);
  }

  /** @return maximum absolute relative total-component residual */
  public double getMaximumRelativeInventoryResidual() {
    return maximumRelativeInventoryResidual;
  }

  /**
   * Get the final inventory of a named component in one phase.
   *
   * @param phase phase identity
   * @param componentName NeqSim component name
   * @return mass in kg
   */
  public double getFinalPhaseInventoryKg(Phase phase, String componentName) {
    return finalPhaseInventoryKg[phase.index][componentIndex(componentName)];
  }

  /**
   * Get signed interphase transfer accumulated in one phase for a named component.
   *
   * <p>
   * Positive values enter the phase. The gas, oil, and water values for each component sum to zero apart from
   * floating-point round-off.
   * </p>
   *
   * @param phase phase identity
   * @param componentName NeqSim component name
   * @return signed transferred mass in kg
   */
  public double getInterphaseTransferKg(Phase phase, String componentName) {
    return interphaseTransferKg[phase.index][componentIndex(componentName)];
  }

  /** @return maximum cell/component equal-and-opposite transfer residual in kg */
  public double getMaximumInterphaseTransferResidualKg() {
    return maximumInterphaseTransferResidualKg;
  }

  /**
   * Get component-by-cell mass fractions for one phase.
   *
   * @param phase phase identity
   * @return defensive component-by-cell profile
   */
  public double[][] getPhaseMassFractionProfile(Phase phase) {
    return copy(phaseMassFractionProfile[phase.index]);
  }

  /**
   * Get one component's cell profile in one phase.
   *
   * @param phase phase identity
   * @param componentName NeqSim component name
   * @return defensive cell mass-fraction profile
   */
  public double[] getPhaseMassFractionProfile(Phase phase, String componentName) {
    return copy(phaseMassFractionProfile[phase.index][componentIndex(componentName)]);
  }

  /** @return minimum non-empty-phase component mass fraction */
  public double getMinimumMassFraction() {
    return minimumMassFraction;
  }

  /** @return maximum non-empty-phase component mass fraction */
  public double getMaximumMassFraction() {
    return maximumMassFraction;
  }

  /** @return maximum non-empty phase/cell mass-fraction sum error */
  public double getMaximumMassFractionSumError() {
    return maximumMassFractionSumError;
  }

  /** @return maximum absolute component-sum versus hydrodynamic phase-mass synchronization error in kg */
  public double getMaximumPhaseMassSynchronizationErrorKg() {
    return maximumPhaseMassSynchronizationErrorKg;
  }

  /**
   * Get the latent/compositional heat added to the fluid sensible-energy equation.
   *
   * <p>
   * Positive values denote heat released by phase formation; negative values denote heat consumed. Phase-specific
   * partial component enthalpies are evaluated from the cell's conservative named-component slate.
   * </p>
   *
   * @return interval-integrated latent heat in joules
   */
  public double getInterphaseLatentHeatEnergyJ() {
    return interphaseLatentHeatEnergyJ;
  }

  /** @return true when component balance, transfer, boundedness, and phase synchronization passed */
  public boolean isConverged() {
    return converged;
  }

  /** @return diagnostic summary */
  public String getMessage() {
    return message;
  }

  /**
   * Serialize this immutable report for Python/JPype capture.
   *
   * @return stable, pretty-printed JSON
   */
  public String toJson() {
    JsonSerializer<Double> finiteDoubleSerializer = (value, type,
        context) -> value != null && Double.isFinite(value) ? new JsonPrimitive(value) : JsonNull.INSTANCE;
    return new GsonBuilder().registerTypeAdapter(Double.class, finiteDoubleSerializer)
        .registerTypeAdapter(Double.TYPE, finiteDoubleSerializer).serializeNulls().setPrettyPrinting().create()
        .toJson(this);
  }

  private int componentIndex(String componentName) {
    for (int index = 0; index < componentNames.length; index++) {
      if (componentNames[index].equals(componentName)) {
        return index;
      }
    }
    throw new IllegalArgumentException("Component '" + componentName + "' is not present in this report");
  }

  private static String[] copy(String[] values) {
    return values == null ? new String[0] : Arrays.copyOf(values, values.length);
  }

  private static double[] copy(double[] values) {
    return values == null ? new double[0] : Arrays.copyOf(values, values.length);
  }

  private static double[][] copy(double[][] values) {
    if (values == null) {
      return new double[0][0];
    }
    double[][] result = new double[values.length][];
    for (int index = 0; index < values.length; index++) {
      result[index] = copy(values[index]);
    }
    return result;
  }

  private static String[] requireComponentNames(String[] values) {
    if (values == null || values.length == 0) {
      throw new IllegalArgumentException("componentNames must contain at least one name");
    }
    String[] result = copy(values);
    Set<String> uniqueNames = new HashSet<String>();
    for (String value : result) {
      if (value == null || value.trim().isEmpty()) {
        throw new IllegalArgumentException("componentNames cannot contain null or blank names");
      }
      if (!uniqueNames.add(value)) {
        throw new IllegalArgumentException("componentNames cannot contain duplicate name '" + value + "'");
      }
    }
    return result;
  }

  private static double[] requireComponentValues(double[] values, String name, int componentCount) {
    if (values == null || values.length != componentCount) {
      throw new IllegalArgumentException(name + " must contain one value per component");
    }
    double[] result = copy(values);
    for (double value : result) {
      requireFinite(value, name);
    }
    return result;
  }

  private static double[][] requirePhaseComponentValues(double[][] values, String name, int componentCount) {
    if (values == null || values.length != PHASE_COUNT) {
      throw new IllegalArgumentException(name + " must contain gas, oil, and water rows");
    }
    double[][] result = new double[PHASE_COUNT][];
    for (int phase = 0; phase < PHASE_COUNT; phase++) {
      result[phase] = requireComponentValues(values[phase], name + "[" + phase + "]", componentCount);
    }
    return result;
  }

  private static double[][][] requireProfiles(double[][][] values, int componentCount) {
    if (values == null || values.length != PHASE_COUNT) {
      throw new IllegalArgumentException("phaseMassFractionProfile must contain gas, oil, and water rows");
    }
    double[][][] result = new double[PHASE_COUNT][componentCount][];
    int cellCount = -1;
    for (int phase = 0; phase < PHASE_COUNT; phase++) {
      if (values[phase] == null || values[phase].length != componentCount) {
        throw new IllegalArgumentException(
            "phaseMassFractionProfile[" + phase + "] must contain one row per component");
      }
      for (int component = 0; component < componentCount; component++) {
        double[] profile = values[phase][component];
        if (profile == null) {
          throw new IllegalArgumentException("phaseMassFractionProfile cannot contain null profiles");
        }
        if (cellCount < 0) {
          cellCount = profile.length;
        } else if (profile.length != cellCount) {
          throw new IllegalArgumentException("phaseMassFractionProfile must use one common cell count");
        }
        result[phase][component] = copy(profile);
        for (double value : result[phase][component]) {
          requireFinite(value, "phaseMassFractionProfile");
        }
      }
    }
    return result;
  }

  private static void requireMassFractionBounds(double minimum, double maximum) {
    boolean bothAbsent = Double.isNaN(minimum) && Double.isNaN(maximum);
    if (bothAbsent) {
      return;
    }
    if (!Double.isFinite(minimum) || !Double.isFinite(maximum)) {
      throw new IllegalArgumentException("minimumMassFraction and maximumMassFraction must both be finite or NaN");
    }
    if (minimum > maximum) {
      throw new IllegalArgumentException("minimumMassFraction cannot exceed maximumMassFraction");
    }
  }

  private static double requireFiniteNonNegative(double value, String name) {
    requireFinite(value, name);
    if (value < 0.0) {
      throw new IllegalArgumentException(name + " must be non-negative");
    }
    return value;
  }

  private static double requireFinite(double value, String name) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException(name + " must be finite");
    }
    return value;
  }
}
