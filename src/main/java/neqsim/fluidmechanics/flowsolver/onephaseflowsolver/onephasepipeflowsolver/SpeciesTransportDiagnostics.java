package neqsim.fluidmechanics.flowsolver.onephaseflowsolver.onephasepipeflowsolver;

import java.io.Serializable;
import java.util.Arrays;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import neqsim.fluidmechanics.flowsolver.SpeciesAdvectionScheme;

/** Immutable resolution and numerical-spreading diagnostics for one conservative species step. */
public final class SpeciesTransportDiagnostics implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Selected conservative species advection scheme. */
  private final SpeciesAdvectionScheme scheme;
  /** Full-step local mass Courant number for each physical cell. */
  private final double[] cellCourantNumbers;
  /** Maximum full-step local mass Courant number. */
  private final double maximumCellCourantNumber;
  /** Arithmetic mean of the local full-step mass Courant numbers. */
  private final double effectiveCellCourantNumber;
  /** Number of conservative transport substeps. */
  private final int substeps;
  /** First-order implicit modified-equation dispersion reference by cell in m2/s. */
  private final double[] firstOrderImplicitNumericalDispersionM2PerSecond;
  /** Maximum finite first-order implicit dispersion reference in m2/s. */
  private final double maximumFirstOrderImplicitNumericalDispersionM2PerSecond;
  /** Physical cell Peclet numbers, unavailable until a dispersion model is enabled. */
  private final double[] cellPecletNumbers;
  /** Whether physical axial dispersion contributed to the accepted step. */
  private final boolean physicalDispersionIncluded;
  /** Human-readable interpretation and limitations. */
  private final String message;

  SpeciesTransportDiagnostics(SpeciesAdvectionScheme scheme, double[] cellCourantNumbers,
      double effectiveCellCourantNumber, int substeps, double[] numericalDispersionM2PerSecond,
      double[] cellPecletNumbers, boolean physicalDispersionIncluded, String message) {
    this.scheme = scheme;
    this.cellCourantNumbers = copy(cellCourantNumbers);
    this.maximumCellCourantNumber = maximumFinite(this.cellCourantNumbers);
    this.effectiveCellCourantNumber = effectiveCellCourantNumber;
    this.substeps = substeps;
    this.firstOrderImplicitNumericalDispersionM2PerSecond = copy(numericalDispersionM2PerSecond);
    this.maximumFirstOrderImplicitNumericalDispersionM2PerSecond = maximumFinite(
        this.firstOrderImplicitNumericalDispersionM2PerSecond);
    this.cellPecletNumbers = copy(cellPecletNumbers);
    this.physicalDispersionIncluded = physicalDispersionIncluded;
    this.message = message;
  }

  /**
   * Create an empty diagnostic before conservative transport has run.
   *
   * @return immutable not-run diagnostic
   */
  public static SpeciesTransportDiagnostics notRun() {
    return new SpeciesTransportDiagnostics(SpeciesAdvectionScheme.FIRST_ORDER_IMPLICIT, new double[0], Double.NaN, 0,
        new double[0], new double[0], false, "Conservative species transport has not run.");
  }

  /** @return selected conservative species scheme */
  public SpeciesAdvectionScheme getScheme() {
    return scheme;
  }

  /** @return defensive copy of full-step local mass Courant numbers */
  public double[] getCellCourantNumbers() {
    return copy(cellCourantNumbers);
  }

  /** @return maximum full-step local mass Courant number */
  public double getMaximumCellCourantNumber() {
    return maximumCellCourantNumber;
  }

  /** @return arithmetic mean of full-step local mass Courant numbers */
  public double getEffectiveCellCourantNumber() {
    return effectiveCellCourantNumber;
  }

  /** @return number of conservative transport substeps used for this accepted hydraulic step */
  public int getSubsteps() {
    return substeps;
  }

  /**
   * Get the modified-equation numerical-dispersion reference for first-order implicit upwind.
   *
   * <p>
   * Each value uses {@code 0.5 * u * dx * (1 + CFL)}. It is a reference for the compatibility scheme, not a claim that
   * the high-resolution limiter has an equivalent constant diffusion coefficient.
   * </p>
   *
   * @return defensive copy in m2/s; entries are non-finite when cell length was unavailable
   */
  public double[] getFirstOrderImplicitNumericalDispersionM2PerSecond() {
    return copy(firstOrderImplicitNumericalDispersionM2PerSecond);
  }

  /** @return maximum finite first-order implicit numerical-dispersion reference in m2/s */
  public double getMaximumFirstOrderImplicitNumericalDispersionM2PerSecond() {
    return maximumFirstOrderImplicitNumericalDispersionM2PerSecond;
  }

  /**
   * Get cell Peclet numbers when a physical dispersion model is active.
   *
   * @return defensive copy; entries are non-finite while physical dispersion is disabled
   */
  public double[] getCellPecletNumbers() {
    return copy(cellPecletNumbers);
  }

  /** @return true when physical axial dispersion contributed to this step */
  public boolean isPhysicalDispersionIncluded() {
    return physicalDispersionIncluded;
  }

  /** @return diagnostic interpretation and limitations */
  public String getMessage() {
    return message;
  }

  /**
   * Serialize the diagnostic with non-finite values represented as JSON null.
   *
   * @return stable JSON representation
   */
  public String toJson() {
    JsonSerializer<Double> finiteDoubleSerializer = (value, type,
        context) -> value != null && Double.isFinite(value) ? new JsonPrimitive(value) : JsonNull.INSTANCE;
    return new GsonBuilder().registerTypeAdapter(Double.class, finiteDoubleSerializer)
        .registerTypeAdapter(Double.TYPE, finiteDoubleSerializer).serializeNulls().setPrettyPrinting().create()
        .toJson(this);
  }

  private static double[] copy(double[] values) {
    return values == null ? new double[0] : Arrays.copyOf(values, values.length);
  }

  private static double maximumFinite(double[] values) {
    double maximum = Double.NaN;
    for (double value : values) {
      if (Double.isFinite(value) && (!Double.isFinite(maximum) || value > maximum)) {
        maximum = value;
      }
    }
    return maximum;
  }
}
