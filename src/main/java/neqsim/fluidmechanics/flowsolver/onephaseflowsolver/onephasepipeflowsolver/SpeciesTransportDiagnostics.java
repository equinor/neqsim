package neqsim.fluidmechanics.flowsolver.onephaseflowsolver.onephasepipeflowsolver;

import java.io.Serializable;
import java.util.Arrays;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import neqsim.fluidmechanics.flowsolver.AxialDispersionBoundaryCondition;
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
  /** Selected physical axial-dispersion model name. */
  private final String physicalDispersionModelName;
  /** Physical axial-dispersion coefficient by cell in m2/s. */
  private final double[] physicalAxialDispersionM2PerSecond;
  /** Minimum finite physical axial-dispersion coefficient in m2/s. */
  private final double minimumPhysicalAxialDispersionM2PerSecond;
  /** Maximum finite physical axial-dispersion coefficient in m2/s. */
  private final double maximumPhysicalAxialDispersionM2PerSecond;
  /** Full-step explicit physical-dispersion number by cell. */
  private final double[] cellPhysicalDispersionNumbers;
  /** Maximum full-step explicit physical-dispersion number. */
  private final double maximumCellPhysicalDispersionNumber;
  /** Whether physical axial dispersion contributed to the accepted step. */
  private final boolean physicalDispersionIncluded;
  /** Physical inlet boundary condition. */
  private final AxialDispersionBoundaryCondition inletDispersionBoundaryCondition;
  /** Physical outlet boundary condition. */
  private final AxialDispersionBoundaryCondition outletDispersionBoundaryCondition;
  /** Human-readable interpretation and limitations. */
  private final String message;

  SpeciesTransportDiagnostics(SpeciesAdvectionScheme scheme, double[] cellCourantNumbers,
      double effectiveCellCourantNumber, int substeps, double[] numericalDispersionM2PerSecond,
      double[] cellPecletNumbers, String physicalDispersionModelName, double[] physicalAxialDispersionM2PerSecond,
      double[] cellPhysicalDispersionNumbers, boolean physicalDispersionIncluded, String message) {
    this.scheme = scheme;
    this.cellCourantNumbers = copy(cellCourantNumbers);
    this.maximumCellCourantNumber = maximumFinite(this.cellCourantNumbers);
    this.effectiveCellCourantNumber = effectiveCellCourantNumber;
    this.substeps = substeps;
    this.firstOrderImplicitNumericalDispersionM2PerSecond = copy(numericalDispersionM2PerSecond);
    this.maximumFirstOrderImplicitNumericalDispersionM2PerSecond = maximumFinite(
        this.firstOrderImplicitNumericalDispersionM2PerSecond);
    this.cellPecletNumbers = copy(cellPecletNumbers);
    this.physicalDispersionModelName = physicalDispersionModelName;
    this.physicalAxialDispersionM2PerSecond = copy(physicalAxialDispersionM2PerSecond);
    this.minimumPhysicalAxialDispersionM2PerSecond = minimumFinite(this.physicalAxialDispersionM2PerSecond);
    this.maximumPhysicalAxialDispersionM2PerSecond = maximumFinite(this.physicalAxialDispersionM2PerSecond);
    this.cellPhysicalDispersionNumbers = copy(cellPhysicalDispersionNumbers);
    this.maximumCellPhysicalDispersionNumber = maximumFinite(this.cellPhysicalDispersionNumbers);
    this.physicalDispersionIncluded = physicalDispersionIncluded;
    this.inletDispersionBoundaryCondition = AxialDispersionBoundaryCondition.DIRICHLET_INLET;
    this.outletDispersionBoundaryCondition = AxialDispersionBoundaryCondition.ZERO_GRADIENT_OUTLET;
    this.message = message;
  }

  /**
   * Create an empty diagnostic before conservative transport has run.
   *
   * @return immutable not-run diagnostic
   */
  public static SpeciesTransportDiagnostics notRun() {
    return new SpeciesTransportDiagnostics(SpeciesAdvectionScheme.FIRST_ORDER_IMPLICIT, new double[0], Double.NaN, 0,
        new double[0], new double[0], "none", new double[0], new double[0], false,
        "Conservative species transport has not run.");
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

  /** @return stable selected physical axial-dispersion model name */
  public String getPhysicalDispersionModelName() {
    return physicalDispersionModelName;
  }

  /** @return defensive copy of physical axial-dispersion coefficients by cell in m2/s */
  public double[] getPhysicalAxialDispersionM2PerSecond() {
    return copy(physicalAxialDispersionM2PerSecond);
  }

  /** @return minimum finite physical axial-dispersion coefficient in m2/s */
  public double getMinimumPhysicalAxialDispersionM2PerSecond() {
    return minimumPhysicalAxialDispersionM2PerSecond;
  }

  /** @return maximum finite physical axial-dispersion coefficient in m2/s */
  public double getMaximumPhysicalAxialDispersionM2PerSecond() {
    return maximumPhysicalAxialDispersionM2PerSecond;
  }

  /**
   * Get full-step explicit physical-dispersion numbers.
   *
   * <p>
   * Each cell value is {@code dt * (G_w + G_e) / M}, where {@code G} is the conservative physical-dispersion face
   * conductance. It controls explicit TVD substepping; first-order implicit transport remains unconditionally stable.
   * </p>
   *
   * @return defensive copy of cell physical-dispersion numbers
   */
  public double[] getCellPhysicalDispersionNumbers() {
    return copy(cellPhysicalDispersionNumbers);
  }

  /** @return maximum full-step explicit physical-dispersion number */
  public double getMaximumCellPhysicalDispersionNumber() {
    return maximumCellPhysicalDispersionNumber;
  }

  /** @return true when physical axial dispersion contributed to this step */
  public boolean isPhysicalDispersionIncluded() {
    return physicalDispersionIncluded;
  }

  /** @return fixed physical inlet boundary condition used for this step */
  public AxialDispersionBoundaryCondition getInletDispersionBoundaryCondition() {
    return inletDispersionBoundaryCondition;
  }

  /** @return fixed physical outlet boundary condition used for this step */
  public AxialDispersionBoundaryCondition getOutletDispersionBoundaryCondition() {
    return outletDispersionBoundaryCondition;
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

  private static double minimumFinite(double[] values) {
    double minimum = Double.NaN;
    for (double value : values) {
      if (Double.isFinite(value) && (!Double.isFinite(minimum) || value < minimum)) {
        minimum = value;
      }
    }
    return minimum;
  }
}
