package neqsim.process.equipment.distillation;

import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import Jama.Matrix;
import neqsim.physicalproperties.system.PhysicalProperties;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.distillation.internals.PackingHydraulicsCalculator;
import neqsim.process.equipment.distillation.internals.PackingSpecification;
import neqsim.process.equipment.distillation.internals.PackingSpecificationLibrary;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;
import neqsim.util.validation.ValidationResult;

/**
 * Counter-current rate-based packed column for non-reactive absorption and stripping.
 *
 * <p>
 * The column is divided into axial segments. In each segment, NeqSim phase-equilibrium calculations
 * provide the interfacial equilibrium driving force, {@link PhysicalProperties} provides effective
 * diffusivities, and {@link PackingHydraulicsCalculator} provides packing hydraulics, wetted area,
 * and film mass-transfer coefficients. Component transfer is bidirectional, so the same equipment
 * can model gas-to-liquid absorption and liquid-to-gas stripping.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class RateBasedPackedColumn extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Minimum finite gas diffusivity used when a physical-property model is unavailable. */
  private static final double MIN_GAS_DIFFUSIVITY = 1.0e-7;

  /** Minimum finite liquid diffusivity used when a physical-property model is unavailable. */
  private static final double MIN_LIQUID_DIFFUSIVITY = 1.0e-12;

  /** Default gas diffusivity fallback in square metres per second. */
  private static final double DEFAULT_GAS_DIFFUSIVITY = 1.5e-5;

  /** Default liquid diffusivity fallback in square metres per second. */
  private static final double DEFAULT_LIQUID_DIFFUSIVITY = 1.5e-9;

  /** Default liquid surface tension in newtons per metre. */
  private static final double DEFAULT_SURFACE_TENSION = 0.025;

  /** Default gas thermal conductivity in watts per metre kelvin. */
  private static final double DEFAULT_GAS_THERMAL_CONDUCTIVITY = 0.030;

  /** Default liquid thermal conductivity in watts per metre kelvin. */
  private static final double DEFAULT_LIQUID_THERMAL_CONDUCTIVITY = 0.60;

  /** Default gas heat capacity in joules per kilogram kelvin. */
  private static final double DEFAULT_GAS_HEAT_CAPACITY = 2200.0;

  /** Default liquid heat capacity in joules per kilogram kelvin. */
  private static final double DEFAULT_LIQUID_HEAT_CAPACITY = 4200.0;

  /** Gas inlet stream entering the bottom of the packed section. */
  private StreamInterface gasInStream;

  /** Liquid inlet stream entering the top of the packed section. */
  private StreamInterface liquidInStream;

  /** Gas outlet stream leaving the top of the packed section. */
  private StreamInterface gasOutStream;

  /** Liquid outlet stream leaving the bottom of the packed section. */
  private StreamInterface liquidOutStream;

  /** Column internal diameter in metres. */
  private double columnDiameter = 1.0;

  /** Packed height in metres. */
  private double packedHeight = 5.0;

  /** Number of axial calculation segments. */
  private int numberOfSegments = 10;

  /** Maximum profile iterations for counter-current convergence. */
  private int maxIterations = 30;

  /** Outlet convergence tolerance in mol/s. */
  private double convergenceTolerance = 1.0e-8;

  /** Maximum fraction of available component moles transferred in one segment. */
  private double maxTransferFractionPerSegment = 0.35;

  /** Global correction factor for film mass-transfer coefficients. */
  private double massTransferCorrectionFactor = 1.0;

  /** Packing specification used in all segments. */
  private PackingSpecification packingSpecification =
      PackingSpecificationLibrary.getOrDefault("Pall-Ring-50");

  /** Optional transfer component whitelist. Empty means all components are considered. */
  private final List<String> transferComponents = new ArrayList<String>();

  /** Last calculated segment results from bottom to top. */
  private final List<SegmentResult> segmentResults = new ArrayList<SegmentResult>();

  /** Last calculated component transfer totals, positive for gas-to-liquid transfer. */
  private final Map<String, Double> componentTransferTotals = new LinkedHashMap<String, Double>();

  /** Number of iterations used by the last run. */
  private int lastIterationCount = 0;

  /** Last convergence residual in mol/s. */
  private double lastConvergenceResidual = Double.NaN;

  /** Total absolute molar transfer in the last converged profile. */
  private double totalAbsoluteMolarTransfer = 0.0;

  /** Mass-transfer correlation used by the segment calculations. */
  private MassTransferCorrelation massTransferCorrelation = MassTransferCorrelation.ONDA_1968;

  /** Multicomponent film model used for component-transfer calculations. */
  private FilmModel filmModel = FilmModel.MAXWELL_STEFAN_MATRIX;

  /** Heat-transfer model used for interphase heat exchange. */
  private HeatTransferModel heatTransferModel = HeatTransferModel.CHILTON_COLBURN_ANALOGY;

  /** Segment solver used for heat, mass, and interface-equilibrium coupling. */
  private SegmentSolver segmentSolver = SegmentSolver.SEQUENTIAL_EXPLICIT;

  /** Column-level profile solver used for counter-current coupling. */
  private ColumnSolver columnSolver = ColumnSolver.FIXED_POINT_PROFILE;

  /** Global correction factor for interphase heat-transfer coefficients. */
  private double heatTransferCorrectionFactor = 1.0;

  /** Maximum fraction of the available thermal approach transferred in one segment. */
  private double maxHeatTransferFractionPerSegment = 0.50;

  /** Maximum Newton iterations for the simultaneous segment residual solve. */
  private int maxSegmentResidualIterations = 12;

  /** Normalized residual tolerance for the simultaneous segment solver. */
  private double segmentResidualTolerance = 1.0e-6;

  /** Maximum Newton iterations for the column-wide equation-oriented solver. */
  private int maxColumnResidualIterations = 8;

  /** Number of homotopy continuation steps for the equation-oriented solver. */
  private int columnHomotopySteps = 3;

  /** Normalized residual tolerance for the column-wide equation-oriented solver. */
  private double columnResidualTolerance = 1.0e-6;

  /** Last equation-oriented residual norm. */
  private double lastColumnResidualNorm = Double.NaN;

  /** Last equation-oriented Newton iteration count. */
  private int lastColumnResidualIterations = 0;

  /** Last maximum gas component-balance residual in mol/s. */
  private double lastGasComponentBalanceResidual = Double.NaN;

  /** Last maximum liquid component-balance residual in mol/s. */
  private double lastLiquidComponentBalanceResidual = Double.NaN;

  /** Last maximum column energy-balance residual in W-equivalent stream basis. */
  private double lastColumnEnergyBalanceResidual = Double.NaN;

  /**
   * Mass-transfer correlation options for the packed-column film coefficients.
   */
  public enum MassTransferCorrelation {
    /** Onda 1968-style wetted-area and film coefficient calculation. */
    ONDA_1968,
    /** Billet-Schultes-style correction of the packing-specific film coefficients. */
    BILLET_SCHULTES_1999
  }

  /**
   * Film-model options for component transfer across the gas-liquid interface.
   */
  public enum FilmModel {
    /** Classic two-resistance calculation using scalar gas and liquid film coefficients. */
    OVERALL_TWO_RESISTANCE,
    /** Maxwell-Stefan matrix correction using NeqSim binary diffusivities and phase composition. */
    MAXWELL_STEFAN_MATRIX
  }

  /**
   * Heat-transfer options for gas-liquid heat exchange in the packed section.
   */
  public enum HeatTransferModel {
    /**
     * Disable explicit interphase heat transfer and only re-equilibrate after material transfer.
     */
    NONE,
    /** Use Chilton-Colburn heat and mass transfer analogy from packed-bed film coefficients. */
    CHILTON_COLBURN_ANALOGY
  }

  /**
   * Segment-level coupling options.
   */
  public enum SegmentSolver {
    /** Apply mass transfer and heat transfer sequentially, then re-flash the segment outlets. */
    SEQUENTIAL_EXPLICIT,
    /** Solve mass-transfer flux residuals and interfacial heat balance simultaneously. */
    SIMULTANEOUS_RESIDUAL
  }

  /**
   * Column-level profile solver options.
   */
  public enum ColumnSolver {
    /** Fixed-point counter-current liquid profile iteration. */
    FIXED_POINT_PROFILE,
    /** Equation-oriented column-wide damped Newton solve with homotopy continuation. */
    EQUATION_ORIENTED
  }

  /**
   * Create a rate-based packed column.
   *
   * @param name equipment name
   */
  public RateBasedPackedColumn(String name) {
    super(name);
  }

  /**
   * Create a rate-based packed column with inlet streams.
   *
   * @param name equipment name
   * @param gasInStream gas inlet stream entering the bottom of the packing
   * @param liquidInStream liquid inlet stream entering the top of the packing
   */
  public RateBasedPackedColumn(String name, StreamInterface gasInStream,
      StreamInterface liquidInStream) {
    super(name);
    setGasInStream(gasInStream);
    setLiquidInStream(liquidInStream);
  }

  /**
   * Set the gas inlet stream.
   *
   * @param gasInStream gas inlet stream entering the bottom of the packing
   */
  public void setGasInStream(StreamInterface gasInStream) {
    this.gasInStream = gasInStream;
    if (gasInStream != null) {
      this.gasOutStream = gasInStream.clone();
      this.gasOutStream.setName(getName() + " gas out");
    }
  }

  /**
   * Add the gas inlet stream.
   *
   * @param gasInStream gas inlet stream entering the bottom of the packing
   */
  public void addGasInStream(StreamInterface gasInStream) {
    setGasInStream(gasInStream);
  }

  /**
   * Set the liquid inlet stream.
   *
   * @param liquidInStream liquid inlet stream entering the top of the packing
   */
  public void setLiquidInStream(StreamInterface liquidInStream) {
    this.liquidInStream = liquidInStream;
    if (liquidInStream != null) {
      this.liquidOutStream = liquidInStream.clone();
      this.liquidOutStream.setName(getName() + " liquid out");
    }
  }

  /**
   * Add the liquid or solvent inlet stream.
   *
   * @param liquidInStream liquid inlet stream entering the top of the packing
   */
  public void addLiquidInStream(StreamInterface liquidInStream) {
    setLiquidInStream(liquidInStream);
  }

  /**
   * Add the solvent inlet stream.
   *
   * @param solventInStream solvent inlet stream entering the top of the packing
   */
  public void addSolventInStream(StreamInterface solventInStream) {
    setLiquidInStream(solventInStream);
  }

  /**
   * Get the gas inlet stream.
   *
   * @return gas inlet stream
   */
  public StreamInterface getGasInStream() {
    return gasInStream;
  }

  /**
   * Get the liquid inlet stream.
   *
   * @return liquid inlet stream
   */
  public StreamInterface getLiquidInStream() {
    return liquidInStream;
  }

  /**
   * Get the gas outlet stream.
   *
   * @return gas outlet stream leaving the top of the packing
   */
  public StreamInterface getGasOutStream() {
    return gasOutStream;
  }

  /**
   * Get the liquid outlet stream.
   *
   * @return liquid outlet stream leaving the bottom of the packing
   */
  public StreamInterface getLiquidOutStream() {
    return liquidOutStream;
  }

  /** {@inheritDoc} */
  @Override
  public List<StreamInterface> getInletStreams() {
    List<StreamInterface> streams = new ArrayList<StreamInterface>();
    if (gasInStream != null) {
      streams.add(gasInStream);
    }
    if (liquidInStream != null) {
      streams.add(liquidInStream);
    }
    return Collections.unmodifiableList(streams);
  }

  /** {@inheritDoc} */
  @Override
  public List<StreamInterface> getOutletStreams() {
    List<StreamInterface> streams = new ArrayList<StreamInterface>();
    if (gasOutStream != null) {
      streams.add(gasOutStream);
    }
    if (liquidOutStream != null) {
      streams.add(liquidOutStream);
    }
    return Collections.unmodifiableList(streams);
  }

  /**
   * Set the column internal diameter.
   *
   * @param columnDiameter column internal diameter in metres, must be positive
   * @throws IllegalArgumentException if the diameter is not positive
   */
  public void setColumnDiameter(double columnDiameter) {
    validatePositive(columnDiameter, "columnDiameter");
    this.columnDiameter = columnDiameter;
  }

  /**
   * Get the column internal diameter.
   *
   * @return column diameter in metres
   */
  public double getColumnDiameter() {
    return columnDiameter;
  }

  /**
   * Set the packed height.
   *
   * @param packedHeight packed height in metres, must be non-negative
   * @throws IllegalArgumentException if the packed height is negative
   */
  public void setPackedHeight(double packedHeight) {
    validateNonNegative(packedHeight, "packedHeight");
    this.packedHeight = packedHeight;
  }

  /**
   * Get the packed height.
   *
   * @return packed height in metres
   */
  public double getPackedHeight() {
    return packedHeight;
  }

  /**
   * Set the number of axial calculation segments.
   *
   * @param numberOfSegments number of segments, must be at least one
   * @throws IllegalArgumentException if the segment count is below one
   */
  public void setNumberOfSegments(int numberOfSegments) {
    if (numberOfSegments < 1) {
      throw new IllegalArgumentException("numberOfSegments must be at least one");
    }
    this.numberOfSegments = numberOfSegments;
  }

  /**
   * Get the number of axial calculation segments.
   *
   * @return number of segments
   */
  public int getNumberOfSegments() {
    return numberOfSegments;
  }

  /**
   * Set the maximum number of profile iterations.
   *
   * @param maxIterations maximum iterations, must be at least one
   * @throws IllegalArgumentException if the iteration count is below one
   */
  public void setMaxIterations(int maxIterations) {
    if (maxIterations < 1) {
      throw new IllegalArgumentException("maxIterations must be at least one");
    }
    this.maxIterations = maxIterations;
  }

  /**
   * Get the maximum number of profile iterations.
   *
   * @return maximum iterations
   */
  public int getMaxIterations() {
    return maxIterations;
  }

  /**
   * Set the convergence tolerance.
   *
   * @param convergenceTolerance outlet residual tolerance in mol/s, must be positive
   * @throws IllegalArgumentException if the tolerance is not positive
   */
  public void setConvergenceTolerance(double convergenceTolerance) {
    validatePositive(convergenceTolerance, "convergenceTolerance");
    this.convergenceTolerance = convergenceTolerance;
  }

  /**
   * Get the convergence tolerance.
   *
   * @return convergence tolerance in mol/s
   */
  public double getConvergenceTolerance() {
    return convergenceTolerance;
  }

  /**
   * Set the maximum component transfer fraction per segment.
   *
   * @param fraction maximum fraction from zero to one
   * @throws IllegalArgumentException if the fraction is outside zero to one
   */
  public void setMaxTransferFractionPerSegment(double fraction) {
    if (!Double.isFinite(fraction) || fraction <= 0.0 || fraction > 1.0) {
      throw new IllegalArgumentException(
          "fraction must be greater than zero and less than or equal to one");
    }
    this.maxTransferFractionPerSegment = fraction;
  }

  /**
   * Get the maximum component transfer fraction per segment.
   *
   * @return maximum component transfer fraction
   */
  public double getMaxTransferFractionPerSegment() {
    return maxTransferFractionPerSegment;
  }

  /**
   * Set a global mass-transfer correction factor.
   *
   * @param correctionFactor correction factor, must be positive
   * @throws IllegalArgumentException if the correction factor is not positive
   */
  public void setMassTransferCorrectionFactor(double correctionFactor) {
    validatePositive(correctionFactor, "correctionFactor");
    this.massTransferCorrectionFactor = correctionFactor;
  }

  /**
   * Get the global mass-transfer correction factor.
   *
   * @return mass-transfer correction factor
   */
  public double getMassTransferCorrectionFactor() {
    return massTransferCorrectionFactor;
  }

  /**
   * Set the packing by name or alias.
   *
   * @param packingName packing name or alias from {@link PackingSpecificationLibrary}
   */
  public void setPackingType(String packingName) {
    this.packingSpecification = PackingSpecificationLibrary.getOrDefault(packingName);
  }

  /**
   * Set the packing specification directly.
   *
   * @param packingSpecification packing specification to use
   * @throws IllegalArgumentException if the specification is null
   */
  public void setPackingSpecification(PackingSpecification packingSpecification) {
    if (packingSpecification == null) {
      throw new IllegalArgumentException("packingSpecification can not be null");
    }
    this.packingSpecification = packingSpecification;
  }

  /**
   * Get the active packing specification.
   *
   * @return packing specification
   */
  public PackingSpecification getPackingSpecification() {
    return packingSpecification;
  }

  /**
   * Set the mass-transfer correlation.
   *
   * @param massTransferCorrelation correlation to use
   * @throws IllegalArgumentException if the correlation is null
   */
  public void setMassTransferCorrelation(MassTransferCorrelation massTransferCorrelation) {
    if (massTransferCorrelation == null) {
      throw new IllegalArgumentException("massTransferCorrelation can not be null");
    }
    this.massTransferCorrelation = massTransferCorrelation;
  }

  /**
   * Get the mass-transfer correlation.
   *
   * @return active mass-transfer correlation
   */
  public MassTransferCorrelation getMassTransferCorrelation() {
    return massTransferCorrelation;
  }

  /**
   * Set the gas-liquid film model used for component transfer.
   *
   * @param filmModel film model to use
   * @throws IllegalArgumentException if the film model is null
   */
  public void setFilmModel(FilmModel filmModel) {
    if (filmModel == null) {
      throw new IllegalArgumentException("filmModel can not be null");
    }
    this.filmModel = filmModel;
  }

  /**
   * Get the active gas-liquid film model.
   *
   * @return active film model
   */
  public FilmModel getFilmModel() {
    return filmModel;
  }

  /**
   * Set the interphase heat-transfer model.
   *
   * @param heatTransferModel heat-transfer model to use
   * @throws IllegalArgumentException if the heat-transfer model is null
   */
  public void setHeatTransferModel(HeatTransferModel heatTransferModel) {
    if (heatTransferModel == null) {
      throw new IllegalArgumentException("heatTransferModel can not be null");
    }
    this.heatTransferModel = heatTransferModel;
  }

  /**
   * Get the active interphase heat-transfer model.
   *
   * @return active heat-transfer model
   */
  public HeatTransferModel getHeatTransferModel() {
    return heatTransferModel;
  }

  /**
   * Set the segment solver used for heat and mass coupling.
   *
   * @param segmentSolver segment solver to use
   * @throws IllegalArgumentException if the segment solver is null
   */
  public void setSegmentSolver(SegmentSolver segmentSolver) {
    if (segmentSolver == null) {
      throw new IllegalArgumentException("segmentSolver can not be null");
    }
    this.segmentSolver = segmentSolver;
  }

  /**
   * Get the active segment solver.
   *
   * @return active segment solver
   */
  public SegmentSolver getSegmentSolver() {
    return segmentSolver;
  }

  /**
   * Set the column-level profile solver.
   *
   * @param columnSolver column solver to use
   * @throws IllegalArgumentException if the column solver is null
   */
  public void setColumnSolver(ColumnSolver columnSolver) {
    if (columnSolver == null) {
      throw new IllegalArgumentException("columnSolver can not be null");
    }
    this.columnSolver = columnSolver;
  }

  /**
   * Get the active column-level profile solver.
   *
   * @return active column solver
   */
  public ColumnSolver getColumnSolver() {
    return columnSolver;
  }

  /**
   * Set the global interphase heat-transfer correction factor.
   *
   * @param correctionFactor correction factor, must be positive
   * @throws IllegalArgumentException if the correction factor is not positive
   */
  public void setHeatTransferCorrectionFactor(double correctionFactor) {
    validatePositive(correctionFactor, "correctionFactor");
    this.heatTransferCorrectionFactor = correctionFactor;
  }

  /**
   * Get the global interphase heat-transfer correction factor.
   *
   * @return heat-transfer correction factor
   */
  public double getHeatTransferCorrectionFactor() {
    return heatTransferCorrectionFactor;
  }

  /**
   * Set the maximum thermal approach transferred in one segment.
   *
   * @param fraction maximum fraction from zero to one
   * @throws IllegalArgumentException if the fraction is outside zero to one
   */
  public void setMaxHeatTransferFractionPerSegment(double fraction) {
    if (!Double.isFinite(fraction) || fraction <= 0.0 || fraction > 1.0) {
      throw new IllegalArgumentException(
          "fraction must be greater than zero and less than or equal to one");
    }
    this.maxHeatTransferFractionPerSegment = fraction;
  }

  /**
   * Get the maximum thermal approach transferred in one segment.
   *
   * @return maximum heat-transfer fraction
   */
  public double getMaxHeatTransferFractionPerSegment() {
    return maxHeatTransferFractionPerSegment;
  }

  /**
   * Set the maximum iterations for the simultaneous segment residual solver.
   *
   * @param maxSegmentResidualIterations maximum residual iterations, must be at least one
   * @throws IllegalArgumentException if the iteration count is below one
   */
  public void setMaxSegmentResidualIterations(int maxSegmentResidualIterations) {
    if (maxSegmentResidualIterations < 1) {
      throw new IllegalArgumentException("maxSegmentResidualIterations must be at least one");
    }
    this.maxSegmentResidualIterations = maxSegmentResidualIterations;
  }

  /**
   * Get the maximum iterations for the simultaneous segment residual solver.
   *
   * @return maximum residual iterations
   */
  public int getMaxSegmentResidualIterations() {
    return maxSegmentResidualIterations;
  }

  /**
   * Set the normalized residual tolerance for the simultaneous segment solver.
   *
   * @param segmentResidualTolerance normalized tolerance, must be positive
   * @throws IllegalArgumentException if the tolerance is not positive
   */
  public void setSegmentResidualTolerance(double segmentResidualTolerance) {
    validatePositive(segmentResidualTolerance, "segmentResidualTolerance");
    this.segmentResidualTolerance = segmentResidualTolerance;
  }

  /**
   * Get the normalized residual tolerance for the simultaneous segment solver.
   *
   * @return normalized residual tolerance
   */
  public double getSegmentResidualTolerance() {
    return segmentResidualTolerance;
  }

  /**
   * Set the maximum Newton iterations for the column-wide equation-oriented solver.
   *
   * @param maxColumnResidualIterations maximum Newton iterations, must be at least one
   * @throws IllegalArgumentException if the iteration count is below one
   */
  public void setMaxColumnResidualIterations(int maxColumnResidualIterations) {
    if (maxColumnResidualIterations < 1) {
      throw new IllegalArgumentException("maxColumnResidualIterations must be at least one");
    }
    this.maxColumnResidualIterations = maxColumnResidualIterations;
  }

  /**
   * Get the maximum Newton iterations for the column-wide equation-oriented solver.
   *
   * @return maximum Newton iterations
   */
  public int getMaxColumnResidualIterations() {
    return maxColumnResidualIterations;
  }

  /**
   * Set the number of homotopy continuation steps for the equation-oriented solver.
   *
   * @param columnHomotopySteps number of continuation steps, must be at least one
   * @throws IllegalArgumentException if the step count is below one
   */
  public void setColumnHomotopySteps(int columnHomotopySteps) {
    if (columnHomotopySteps < 1) {
      throw new IllegalArgumentException("columnHomotopySteps must be at least one");
    }
    this.columnHomotopySteps = columnHomotopySteps;
  }

  /**
   * Get the number of homotopy continuation steps for the equation-oriented solver.
   *
   * @return homotopy continuation steps
   */
  public int getColumnHomotopySteps() {
    return columnHomotopySteps;
  }

  /**
   * Set the normalized residual tolerance for the column-wide equation-oriented solver.
   *
   * @param columnResidualTolerance normalized tolerance, must be positive
   * @throws IllegalArgumentException if the tolerance is not positive
   */
  public void setColumnResidualTolerance(double columnResidualTolerance) {
    validatePositive(columnResidualTolerance, "columnResidualTolerance");
    this.columnResidualTolerance = columnResidualTolerance;
  }

  /**
   * Get the normalized residual tolerance for the column-wide equation-oriented solver.
   *
   * @return normalized column residual tolerance
   */
  public double getColumnResidualTolerance() {
    return columnResidualTolerance;
  }

  /**
   * Get the last column-wide equation-oriented residual norm.
   *
   * @return last normalized column residual norm
   */
  public double getLastColumnResidualNorm() {
    return lastColumnResidualNorm;
  }

  /**
   * Get the last column-wide Newton iteration count.
   *
   * @return last column residual iterations
   */
  public int getLastColumnResidualIterations() {
    return lastColumnResidualIterations;
  }

  /**
   * Get the maximum gas component-balance residual from the last equation-oriented solve.
   *
   * @return gas component-balance residual in mol/s
   */
  public double getLastGasComponentBalanceResidual() {
    return lastGasComponentBalanceResidual;
  }

  /**
   * Get the maximum liquid component-balance residual from the last equation-oriented solve.
   *
   * @return liquid component-balance residual in mol/s
   */
  public double getLastLiquidComponentBalanceResidual() {
    return lastLiquidComponentBalanceResidual;
  }

  /**
   * Get the maximum column energy-balance residual from the last equation-oriented solve.
   *
   * @return energy-balance residual in W-equivalent stream basis
   */
  public double getLastColumnEnergyBalanceResidual() {
    return lastColumnEnergyBalanceResidual;
  }

  /**
   * Set transfer components explicitly.
   *
   * <p>
   * When no components are supplied, all components in the inlet streams are considered.
   * </p>
   *
   * @param componentNames component names to include in transfer calculations
   */
  public void setTransferComponents(String... componentNames) {
    transferComponents.clear();
    if (componentNames == null) {
      return;
    }
    for (int i = 0; i < componentNames.length; i++) {
      if (componentNames[i] != null && !componentNames[i].trim().isEmpty()) {
        transferComponents.add(componentNames[i].trim());
      }
    }
  }

  /**
   * Get explicitly configured transfer components.
   *
   * @return unmodifiable list of transfer components; empty means automatic component discovery
   */
  public List<String> getTransferComponents() {
    return Collections.unmodifiableList(transferComponents);
  }

  /**
   * Get the last calculated segment profile.
   *
   * @return unmodifiable segment results from bottom to top
   */
  public List<SegmentResult> getSegmentResults() {
    return Collections.unmodifiableList(segmentResults);
  }

  /**
   * Get the last calculated component transfer totals.
   *
   * @return component transfer totals in mol/s, positive for gas-to-liquid transfer
   */
  public Map<String, Double> getComponentTransferTotals() {
    return Collections.unmodifiableMap(componentTransferTotals);
  }

  /**
   * Get the last profile iteration count.
   *
   * @return number of iterations used by the last run
   */
  public int getLastIterationCount() {
    return lastIterationCount;
  }

  /**
   * Get the last convergence residual.
   *
   * @return convergence residual in mol/s
   */
  public double getLastConvergenceResidual() {
    return lastConvergenceResidual;
  }

  /**
   * Get total absolute molar transfer in the last profile.
   *
   * @return total absolute transfer in mol/s
   */
  public double getTotalAbsoluteMolarTransfer() {
    return totalAbsoluteMolarTransfer;
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface getThermoSystem() {
    return gasOutStream == null ? null : gasOutStream.getThermoSystem();
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    validateRuntimeSetup();
    SystemInterface gasIn = gasInStream.getThermoSystem().clone();
    SystemInterface liquidIn = liquidInStream.getThermoSystem().clone();
    flashAndInitialize(gasIn);
    flashAndInitialize(liquidIn);

    CounterCurrentSolution solution = solveCounterCurrentProfile(gasIn, liquidIn);
    gasOutStream.setThermoSystem(solution.gasOutlet);
    liquidOutStream.setThermoSystem(solution.liquidOutlet);
    gasOutStream.run(id);
    liquidOutStream.run(id);
    gasOutStream.setCalculationIdentifier(id);
    liquidOutStream.setCalculationIdentifier(id);
    setCalculationIdentifier(id);
    isSolved = true;
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    if (gasOutStream != null) {
      gasOutStream.displayResult();
    }
    if (liquidOutStream != null) {
      liquidOutStream.displayResult();
    }
  }

  /** {@inheritDoc} */
  @Override
  public ValidationResult validateSetup() {
    ValidationResult result = new ValidationResult(getName());
    if (getName() == null || getName().trim().isEmpty()) {
      result.addError("equipment", "Equipment has no name", "Set equipment name in constructor");
    }
    if (gasInStream == null) {
      result.addError("gasInStream", "No gas inlet stream connected",
          "Call setGasInStream(stream) before running the column");
    } else if (gasInStream.getThermoSystem() == null) {
      result.addError("gasInStream", "Gas inlet stream has no thermodynamic system",
          "Create the gas inlet stream with a valid fluid");
    }
    if (liquidInStream == null) {
      result.addError("liquidInStream", "No liquid inlet stream connected",
          "Call setLiquidInStream(stream) before running the column");
    } else if (liquidInStream.getThermoSystem() == null) {
      result.addError("liquidInStream", "Liquid inlet stream has no thermodynamic system",
          "Create the liquid inlet stream with a valid fluid");
    }
    if (columnDiameter <= 0.0) {
      result.addError("columnDiameter", "Column diameter must be positive",
          "Set column diameter in metres with setColumnDiameter");
    }
    if (packedHeight < 0.0) {
      result.addError("packedHeight", "Packed height can not be negative",
          "Set a non-negative packed height in metres");
    }
    if (numberOfSegments < 1) {
      result.addError("numberOfSegments", "At least one segment is required",
          "Set numberOfSegments to one or more");
    }
    if (packingSpecification == null) {
      result.addError("packingSpecification", "No packing specification configured",
          "Use setPackingType or setPackingSpecification");
    }
    return result;
  }

  /**
   * Convert the last run results to JSON.
   *
   * @return JSON report with configuration, hydraulics, transfer totals, and segment profiles
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(new ColumnReport(this));
  }

  /**
   * Validate runtime setup and throw an exception on blocking errors.
   *
   * @throws IllegalStateException if the equipment setup is invalid
   */
  private void validateRuntimeSetup() {
    ValidationResult result = validateSetup();
    if (!result.isValid()) {
      throw new IllegalStateException(
          "RateBasedPackedColumn setup is invalid: " + result.toString());
    }
  }

  /**
   * Solve the counter-current segment profile by fixed-point iteration.
   *
   * @param gasIn gas inlet system
   * @param liquidIn liquid inlet system
   * @return converged counter-current solution
   */
  private CounterCurrentSolution solveCounterCurrentProfile(SystemInterface gasIn,
      SystemInterface liquidIn) {
    if (columnSolver == ColumnSolver.EQUATION_ORIENTED && packedHeight > 0.0) {
      return solveEquationOrientedProfile(gasIn, liquidIn);
    }
    return solveFixedPointProfile(gasIn, liquidIn);
  }

  /**
   * Solve the counter-current profile by fixed-point liquid profile iteration.
   *
   * @param gasIn gas inlet system
   * @param liquidIn liquid inlet system
   * @return converged counter-current solution
   */
  private CounterCurrentSolution solveFixedPointProfile(SystemInterface gasIn,
      SystemInterface liquidIn) {
    resetColumnResidualDiagnostics();
    List<SystemInterface> liquidEntering = initializeLiquidProfile(liquidIn);
    SystemInterface previousGasOutlet = null;
    SystemInterface previousLiquidOutlet = null;
    CounterCurrentSolution solution = null;
    for (int iteration = 1; iteration <= maxIterations; iteration++) {
      solution = runOneProfileIteration(gasIn, liquidIn, liquidEntering);
      double residual = calculateOutletResidual(previousGasOutlet, solution.gasOutlet,
          previousLiquidOutlet, solution.liquidOutlet);
      lastIterationCount = iteration;
      lastConvergenceResidual = residual;
      if (residual <= convergenceTolerance || packedHeight == 0.0) {
        acceptSolution(solution);
        return solution;
      }
      previousGasOutlet = solution.gasOutlet.clone();
      previousLiquidOutlet = solution.liquidOutlet.clone();
      liquidEntering = updateLiquidProfile(liquidIn, solution.liquidLeavingSegments);
    }
    acceptSolution(solution);
    return solution;
  }

  /**
   * Reset column-wide residual diagnostics for non-equation-oriented profile solves.
   */
  private void resetColumnResidualDiagnostics() {
    lastColumnResidualNorm = Double.NaN;
    lastColumnResidualIterations = 0;
    lastGasComponentBalanceResidual = Double.NaN;
    lastLiquidComponentBalanceResidual = Double.NaN;
    lastColumnEnergyBalanceResidual = Double.NaN;
  }

  /**
   * Solve the full packed section with a column-wide equation-oriented residual system.
   *
   * @param gasIn gas inlet system
   * @param liquidIn liquid inlet system
   * @return equation-oriented counter-current solution
   */
  private CounterCurrentSolution solveEquationOrientedProfile(SystemInterface gasIn,
      SystemInterface liquidIn) {
    CounterCurrentSolution seed = solveFixedPointProfile(gasIn, liquidIn);
    List<String> components = getTransferComponentList(gasIn, liquidIn);
    if (components.isEmpty()) {
      return seed;
    }
    double[] unknowns = createColumnUnknowns(seed, components);
    ColumnResidualEvaluation evaluation = null;
    int totalIterations = 0;
    for (int step = 1; step <= columnHomotopySteps; step++) {
      double homotopyFactor = ((double) step) / columnHomotopySteps;
      evaluation = solveColumnResiduals(gasIn, liquidIn, components, unknowns, homotopyFactor);
      unknowns = evaluation.unknowns;
      totalIterations += evaluation.iterations;
    }
    evaluation = evaluateColumnResidual(gasIn, liquidIn, components, unknowns, 1.0,
        totalIterations);
    lastColumnResidualNorm = evaluation.norm;
    lastColumnResidualIterations = totalIterations;
    lastGasComponentBalanceResidual = evaluation.maxGasComponentBalanceResidual;
    lastLiquidComponentBalanceResidual = evaluation.maxLiquidComponentBalanceResidual;
    lastColumnEnergyBalanceResidual = evaluation.maxEnergyBalanceResidual;
    lastIterationCount = Math.max(1, totalIterations);
    lastConvergenceResidual = evaluation.norm;
    acceptSolution(evaluation.solution);
    return evaluation.solution;
  }

  /**
   * Create a column-wide unknown vector from an existing profile solution.
   *
   * @param seed seed profile solution
   * @param components active transfer components
   * @return unknown vector containing segment fluxes, interface temperatures, and outlet temperatures
   */
  private double[] createColumnUnknowns(CounterCurrentSolution seed, List<String> components) {
    int blockSize = columnUnknownBlockSize(components);
    double[] unknowns = new double[numberOfSegments * blockSize];
    for (int segment = 0; segment < numberOfSegments; segment++) {
      SegmentResult result = seed.segmentResults.get(Math.min(segment, seed.segmentResults.size() - 1));
      for (int componentIndex = 0; componentIndex < components.size(); componentIndex++) {
        Double transfer = result.componentMoleTransfer.get(components.get(componentIndex));
        unknowns[columnFluxIndex(segment, componentIndex, components)] =
            transfer == null ? 0.0 : transfer.doubleValue();
      }
      unknowns[columnInterfaceTemperatureIndex(segment, components)] =
          finitePositive(result.interfaceTemperatureK, 0.5 * (result.gasTemperatureK
              + result.liquidTemperatureK));
      unknowns[columnGasOutletTemperatureIndex(segment, components)] =
          finitePositive(result.gasTemperatureK, 300.0);
      unknowns[columnLiquidOutletTemperatureIndex(segment, components)] =
          finitePositive(result.liquidTemperatureK, 300.0);
    }
    return unknowns;
  }

  /**
   * Solve the column-wide residual equations at one homotopy continuation factor.
   *
   * @param gasIn gas inlet system
   * @param liquidIn liquid inlet system
   * @param components active transfer components
   * @param initialUnknowns initial unknown vector
   * @param homotopyFactor continuation factor from zero to one
   * @return best residual evaluation found
   */
  private ColumnResidualEvaluation solveColumnResiduals(SystemInterface gasIn,
      SystemInterface liquidIn, List<String> components, double[] initialUnknowns,
      double homotopyFactor) {
    double[] unknowns = clampColumnUnknowns(initialUnknowns, gasIn, liquidIn, components);
    ColumnResidualEvaluation best = evaluateColumnResidual(gasIn, liquidIn, components, unknowns,
        homotopyFactor, 0);
    for (int iteration = 0; iteration < maxColumnResidualIterations; iteration++) {
      if (best.norm <= columnResidualTolerance) {
        return best.withIterations(iteration);
      }
      Matrix step = calculateColumnResidualStep(gasIn, liquidIn, components, unknowns, best,
          homotopyFactor);
      if (step == null) {
        return best.withIterations(iteration);
      }
      boolean improved = false;
      double[] bestUnknowns = unknowns;
      ColumnResidualEvaluation bestCandidate = best;
      double damping = 1.0;
      for (int lineSearch = 0; lineSearch < 10; lineSearch++) {
        double[] candidateUnknowns = applyColumnResidualStep(unknowns, step, damping, gasIn,
            liquidIn, components);
        ColumnResidualEvaluation candidate = evaluateColumnResidual(gasIn, liquidIn, components,
            candidateUnknowns, homotopyFactor, iteration + 1);
        if (candidate.norm < bestCandidate.norm) {
          bestUnknowns = candidateUnknowns;
          bestCandidate = candidate;
          improved = true;
          break;
        }
        damping *= 0.5;
      }
      if (!improved) {
        return best.withIterations(iteration);
      }
      unknowns = bestUnknowns;
      best = bestCandidate;
    }
    return best.withIterations(maxColumnResidualIterations);
  }

  /**
   * Evaluate the full column residual vector for the equation-oriented solver.
   *
   * @param gasIn gas inlet system
   * @param liquidIn liquid inlet system
   * @param components active transfer components
   * @param unknowns column unknown vector
   * @param homotopyFactor continuation factor from zero to one
   * @param iterations iteration count represented by this evaluation
   * @return column residual evaluation
   */
  private ColumnResidualEvaluation evaluateColumnResidual(SystemInterface gasIn,
      SystemInterface liquidIn, List<String> components, double[] unknowns, double homotopyFactor,
      int iterations) {
    double[] boundedUnknowns = clampColumnUnknowns(unknowns, gasIn, liquidIn, components);
    ColumnState state = buildColumnState(gasIn, liquidIn, components, boundedUnknowns);
    List<Double> residuals = new ArrayList<Double>();
    List<SegmentResult> results = new ArrayList<SegmentResult>();
    double maxFluxResidual = 0.0;
    double maxHeatResidual = 0.0;
    double maxEnergyResidual = 0.0;
    double maxGasBalanceResidual = 0.0;
    double maxLiquidBalanceResidual = 0.0;
    double segmentHeight = packedHeight / numberOfSegments;
    for (int segment = 0; segment < numberOfSegments; segment++) {
      SystemInterface gas = state.gasEntering.get(segment).clone();
      SystemInterface liquid = state.liquidEntering.get(segment).clone();
      TransportSnapshot snapshot = calculateTransportSnapshot(gas, liquid, segmentHeight);
      double interfaceTemperature = boundedUnknowns[columnInterfaceTemperatureIndex(segment,
          components)];
      InterfaceEquilibrium equilibrium = calculateInterfaceEquilibrium(gas, liquid,
          interfaceTemperature);
      Map<String, Double> componentTransfers = new LinkedHashMap<String, Double>();
      double gasMassEnthalpy = 0.0;
      double liquidMassEnthalpy = 0.0;
      for (int componentIndex = 0; componentIndex < components.size(); componentIndex++) {
        String component = components.get(componentIndex);
        double transfer = boundedUnknowns[columnFluxIndex(segment, componentIndex, components)];
        double predictedTransfer = calculateUnboundedComponentTransfer(component, gas, liquid,
            snapshot, equilibrium) * homotopyFactor;
        predictedTransfer = limitTransfer(component, predictedTransfer, gas, liquid);
        double fluxResidual = transfer - predictedTransfer;
        residuals.add(Double.valueOf(fluxResidual / transferResidualScale(component,
            predictedTransfer, gas, liquid)));
        maxFluxResidual = Math.max(maxFluxResidual, Math.abs(fluxResidual));
        componentTransfers.put(component, transfer);
        gasMassEnthalpy += transfer * equilibrium.getGasMolarEnthalpy(component);
        liquidMassEnthalpy += transfer * equilibrium.getLiquidMolarEnthalpy(component);
      }
      double segmentVolume = Math.PI * columnDiameter * columnDiameter / 4.0 * packedHeight
          / numberOfSegments;
      double gasSensibleHeat = snapshot.gasHeatTransferCoefficient * segmentVolume
          * (gas.getTemperature() - interfaceTemperature);
      double liquidSensibleHeat = snapshot.liquidHeatTransferCoefficient * segmentVolume
          * (interfaceTemperature - liquid.getTemperature());
      double heatResidual = gasSensibleHeat + gasMassEnthalpy - liquidSensibleHeat
          - liquidMassEnthalpy;
      residuals.add(Double.valueOf(heatResidual / heatResidualScale(gasSensibleHeat,
          liquidSensibleHeat, gasMassEnthalpy, liquidMassEnthalpy)));
      maxHeatResidual = Math.max(maxHeatResidual, Math.abs(heatResidual));

      SystemInterface gasOutletTarget = state.gasLeaving.get(segment).clone();
      SystemInterface liquidOutletTarget = state.liquidLeaving.get(segment).clone();
      double gasTargetEnthalpy = gas.getEnthalpy() - gasSensibleHeat - gasMassEnthalpy;
      double liquidTargetEnthalpy = liquid.getEnthalpy() + liquidSensibleHeat + liquidMassEnthalpy;
      double gasTargetTemperature = estimateTemperatureForTargetEnthalpy(gasOutletTarget,
          gasTargetEnthalpy);
      double liquidTargetTemperature = estimateTemperatureForTargetEnthalpy(liquidOutletTarget,
          liquidTargetEnthalpy);
      double gasTemperatureResidual = state.gasLeaving.get(segment).getTemperature()
          - gasTargetTemperature;
      double liquidTemperatureResidual = state.liquidLeaving.get(segment).getTemperature()
          - liquidTargetTemperature;
        double gasEnthalpyResidual = state.gasLeaving.get(segment).getEnthalpy()
          - gasTargetEnthalpy;
        double liquidEnthalpyResidual = state.liquidLeaving.get(segment).getEnthalpy()
          - liquidTargetEnthalpy;
      residuals.add(Double.valueOf(gasTemperatureResidual / 10.0));
      residuals.add(Double.valueOf(liquidTemperatureResidual / 10.0));
      maxEnergyResidual = Math.max(maxEnergyResidual,
          Math.max(Math.abs(gasEnthalpyResidual), Math.abs(liquidEnthalpyResidual)));

      for (int componentIndex = 0; componentIndex < components.size(); componentIndex++) {
        String component = components.get(componentIndex);
        double transfer = componentTransfers.get(component).doubleValue();
        double gasBalance = componentMoles(state.gasLeaving.get(segment), component)
            - (componentMoles(gas, component) - transfer);
        double liquidBalance = componentMoles(state.liquidLeaving.get(segment), component)
            - (componentMoles(liquid, component) + transfer);
        residuals.add(Double.valueOf(gasBalance / transferResidualScale(component, transfer, gas,
            liquid)));
        residuals.add(Double.valueOf(liquidBalance / transferResidualScale(component, transfer,
            gas, liquid)));
        maxGasBalanceResidual = Math.max(maxGasBalanceResidual, Math.abs(gasBalance));
        maxLiquidBalanceResidual = Math.max(maxLiquidBalanceResidual, Math.abs(liquidBalance));
      }

      double totalTransfer = 0.0;
      for (Double value : componentTransfers.values()) {
        totalTransfer += value.doubleValue();
      }
      double segmentEnergyResidual = state.gasLeaving.get(segment).getEnthalpy()
          + state.liquidLeaving.get(segment).getEnthalpy() - gas.getEnthalpy()
          - liquid.getEnthalpy();
      SegmentResult result = new SegmentResult(segment + 1, (segment + 0.5) * segmentHeight,
          state.gasLeaving.get(segment).getTemperature(),
          state.liquidLeaving.get(segment).getTemperature(),
          state.gasLeaving.get(segment).getPressure(),
          state.liquidLeaving.get(segment).getPressure(),
          state.gasLeaving.get(segment).getTotalNumberOfMoles(),
          state.liquidLeaving.get(segment).getTotalNumberOfMoles(), snapshot.gasDensity,
          snapshot.liquidDensity, snapshot.gasViscosity, snapshot.liquidViscosity,
          snapshot.gasDiffusivity, snapshot.liquidDiffusivity, snapshot.wettedArea, snapshot.kGa,
          snapshot.kLa, snapshot.gasHeatTransferCoefficient, snapshot.liquidHeatTransferCoefficient,
          snapshot.overallHeatTransferCoefficient, interfaceTemperature, liquidSensibleHeat,
          snapshot.pressureDropPerMeter, snapshot.percentFlood, totalTransfer, componentTransfers,
          equilibrium.gasMoleFractions, equilibrium.liquidMoleFractions,
          equilibrium.equilibriumRatios, ColumnSolver.EQUATION_ORIENTED.name(), iterations,
          maxFluxResidual, heatResidual, segmentEnergyResidual);
      results.add(result);
    }
    double[] residualArray = toPrimitiveArray(residuals);
    CounterCurrentSolution solution = new CounterCurrentSolution(state.gasOutlet, state.liquidOutlet,
        state.liquidLeaving, results);
    return new ColumnResidualEvaluation(boundedUnknowns, residualArray, residualNorm(residualArray),
        solution, iterations, maxFluxResidual, maxHeatResidual, maxEnergyResidual,
        maxGasBalanceResidual, maxLiquidBalanceResidual);
  }

  /**
   * Calculate one sparse-pattern Newton step for the column residual equations.
   *
   * @param gasIn gas inlet system
   * @param liquidIn liquid inlet system
   * @param components active transfer components
   * @param unknowns current column unknown vector
   * @param evaluation current residual evaluation
   * @param homotopyFactor continuation factor from zero to one
   * @return Newton step, or null if the least-squares solve fails
   */
  private Matrix calculateColumnResidualStep(SystemInterface gasIn, SystemInterface liquidIn,
      List<String> components, double[] unknowns, ColumnResidualEvaluation evaluation,
      double homotopyFactor) {
    int residualCount = evaluation.normalizedResiduals.length;
    int variableCount = unknowns.length;
    SparseJacobian sparseJacobian = new SparseJacobian(residualCount, variableCount);
    for (int variable = 0; variable < variableCount; variable++) {
      double step = columnResidualVariableStep(unknowns, variable, components.size());
      double[] shifted = unknowns.clone();
      shifted[variable] += step;
      shifted = clampColumnUnknowns(shifted, gasIn, liquidIn, components);
      double actualStep = shifted[variable] - unknowns[variable];
      if (Math.abs(actualStep) < 1.0e-20) {
        shifted = unknowns.clone();
        shifted[variable] -= step;
        shifted = clampColumnUnknowns(shifted, gasIn, liquidIn, components);
        actualStep = shifted[variable] - unknowns[variable];
      }
      if (Math.abs(actualStep) < 1.0e-20) {
        sparseJacobian.set(variable % residualCount, variable, 1.0);
      } else {
        ColumnResidualEvaluation shiftedEvaluation = evaluateColumnResidual(gasIn, liquidIn,
            components, shifted, homotopyFactor, evaluation.iterations);
        for (int row = 0; row < residualCount; row++) {
          double derivative = (shiftedEvaluation.normalizedResiduals[row]
              - evaluation.normalizedResiduals[row]) / actualStep;
          if (Math.abs(derivative) > 1.0e-14 && Double.isFinite(derivative)) {
            sparseJacobian.set(row, variable, derivative);
          }
        }
      }
    }
    try {
      Matrix jacobian = sparseJacobian.toDenseMatrix();
      Matrix normalMatrix = jacobian.transpose().times(jacobian)
          .plus(Matrix.identity(variableCount, variableCount).times(1.0e-10));
      double[][] rhsValues = new double[residualCount][1];
      for (int row = 0; row < residualCount; row++) {
        rhsValues[row][0] = -evaluation.normalizedResiduals[row];
      }
      Matrix normalRhs = jacobian.transpose().times(new Matrix(rhsValues));
      return normalMatrix.solve(normalRhs);
    } catch (RuntimeException ex) {
      return null;
    }
  }

  /**
   * Apply a damped column residual step and clamp to physical bounds.
   *
   * @param unknowns current unknown vector
   * @param step Newton step
   * @param damping damping factor from zero to one
   * @param gasIn gas inlet system
   * @param liquidIn liquid inlet system
   * @param components active transfer components
   * @return bounded candidate unknowns
   */
  private double[] applyColumnResidualStep(double[] unknowns, Matrix step, double damping,
      SystemInterface gasIn, SystemInterface liquidIn, List<String> components) {
    double[] candidate = unknowns.clone();
    for (int i = 0; i < candidate.length; i++) {
      candidate[i] += damping * step.get(i, 0);
    }
    return clampColumnUnknowns(candidate, gasIn, liquidIn, components);
  }

  /**
   * Build gas and liquid segment states from column-wide flux and temperature unknowns.
   *
   * @param gasIn gas inlet system
   * @param liquidIn liquid inlet system
   * @param components active transfer components
   * @param unknowns bounded column unknown vector
   * @return reconstructed column state
   */
  private ColumnState buildColumnState(SystemInterface gasIn, SystemInterface liquidIn,
      List<String> components, double[] unknowns) {
    List<SystemInterface> gasEntering = new ArrayList<SystemInterface>();
    List<SystemInterface> gasLeaving = new ArrayList<SystemInterface>();
    SystemInterface gasCurrent = gasIn.clone();
    flashAndInitialize(gasCurrent);
    for (int segment = 0; segment < numberOfSegments; segment++) {
      gasEntering.add(gasCurrent.clone());
      SystemInterface gasOutlet = gasCurrent.clone();
      for (int componentIndex = 0; componentIndex < components.size(); componentIndex++) {
        double transfer = unknowns[columnFluxIndex(segment, componentIndex, components)];
        addComponentDelta(gasOutlet, components.get(componentIndex), -transfer);
      }
      gasOutlet.setTemperature(unknowns[columnGasOutletTemperatureIndex(segment, components)]);
      flashAndInitialize(gasOutlet);
      gasLeaving.add(gasOutlet);
      gasCurrent = gasOutlet.clone();
    }

    List<SystemInterface> liquidEntering = new ArrayList<SystemInterface>();
    List<SystemInterface> liquidLeaving = new ArrayList<SystemInterface>();
    for (int segment = 0; segment < numberOfSegments; segment++) {
      liquidEntering.add(null);
      liquidLeaving.add(null);
    }
    SystemInterface liquidCurrent = liquidIn.clone();
    flashAndInitialize(liquidCurrent);
    for (int segment = numberOfSegments - 1; segment >= 0; segment--) {
      liquidEntering.set(segment, liquidCurrent.clone());
      SystemInterface liquidOutlet = liquidCurrent.clone();
      for (int componentIndex = 0; componentIndex < components.size(); componentIndex++) {
        double transfer = unknowns[columnFluxIndex(segment, componentIndex, components)];
        addComponentDelta(liquidOutlet, components.get(componentIndex), transfer);
      }
      liquidOutlet
          .setTemperature(unknowns[columnLiquidOutletTemperatureIndex(segment, components)]);
      flashAndInitialize(liquidOutlet);
      liquidLeaving.set(segment, liquidOutlet);
      liquidCurrent = liquidOutlet.clone();
    }
    return new ColumnState(gasEntering, gasLeaving, liquidEntering, liquidLeaving,
        gasLeaving.get(numberOfSegments - 1).clone(), liquidLeaving.get(0).clone());
  }

  /**
   * Clamp column unknowns to inventory and temperature limits.
   *
   * @param unknowns column unknown vector
   * @param gasIn gas inlet system
   * @param liquidIn liquid inlet system
   * @param components active transfer components
   * @return clamped unknown vector
   */
  private double[] clampColumnUnknowns(double[] unknowns, SystemInterface gasIn,
      SystemInterface liquidIn, List<String> components) {
    double[] bounded = unknowns.clone();
    Map<String, Double> gasAvailable = componentInventoryMap(gasIn, components);
    for (int segment = 0; segment < numberOfSegments; segment++) {
      for (int componentIndex = 0; componentIndex < components.size(); componentIndex++) {
        int index = columnFluxIndex(segment, componentIndex, components);
        if (!Double.isFinite(bounded[index])) {
          bounded[index] = 0.0;
        }
        String component = components.get(componentIndex);
        if (bounded[index] > 0.0) {
          double available = Math.max(0.0, gasAvailable.get(component).doubleValue()
              * maxTransferFractionPerSegment);
          bounded[index] = Math.min(bounded[index], available);
          gasAvailable.put(component, Double.valueOf(Math.max(0.0,
              gasAvailable.get(component).doubleValue() - bounded[index])));
        }
      }
    }
    Map<String, Double> liquidAvailable = componentInventoryMap(liquidIn, components);
    for (int segment = numberOfSegments - 1; segment >= 0; segment--) {
      for (int componentIndex = 0; componentIndex < components.size(); componentIndex++) {
        int index = columnFluxIndex(segment, componentIndex, components);
        String component = components.get(componentIndex);
        if (bounded[index] < 0.0) {
          double available = Math.max(0.0, liquidAvailable.get(component).doubleValue()
              * maxTransferFractionPerSegment);
          bounded[index] = -Math.min(-bounded[index], available);
          liquidAvailable.put(component, Double.valueOf(Math.max(0.0,
              liquidAvailable.get(component).doubleValue() + bounded[index])));
        }
      }
    }
    for (int segment = 0; segment < numberOfSegments; segment++) {
      clampColumnTemperatureUnknown(bounded, columnInterfaceTemperatureIndex(segment, components),
          gasIn, liquidIn);
      clampColumnTemperatureUnknown(bounded, columnGasOutletTemperatureIndex(segment, components),
          gasIn, liquidIn);
      clampColumnTemperatureUnknown(bounded,
          columnLiquidOutletTemperatureIndex(segment, components), gasIn, liquidIn);
    }
    return bounded;
  }

  /**
   * Clamp one column temperature unknown.
   *
   * @param unknowns unknown vector to update
   * @param index temperature unknown index
   * @param gasIn gas inlet system
   * @param liquidIn liquid inlet system
   */
  private void clampColumnTemperatureUnknown(double[] unknowns, int index, SystemInterface gasIn,
      SystemInterface liquidIn) {
    if (!Double.isFinite(unknowns[index])) {
      unknowns[index] = 0.5 * (gasIn.getTemperature() + liquidIn.getTemperature());
    }
    double minTemperature = Math.max(1.0, Math.min(gasIn.getTemperature(), liquidIn.getTemperature())
        - 150.0);
    double maxTemperature = Math.max(gasIn.getTemperature(), liquidIn.getTemperature()) + 150.0;
    unknowns[index] = clamp(unknowns[index], minTemperature, maxTemperature);
  }

  /**
   * Build a component inventory map for active components.
   *
   * @param system thermodynamic system
   * @param components active transfer components
   * @return component moles by component name
   */
  private Map<String, Double> componentInventoryMap(SystemInterface system,
      List<String> components) {
    Map<String, Double> inventory = new LinkedHashMap<String, Double>();
    for (int i = 0; i < components.size(); i++) {
      String component = components.get(i);
      inventory.put(component, Double.valueOf(componentMoles(system, component)));
    }
    return inventory;
  }

  /**
   * Add a bounded component delta to a system.
   *
   * @param system system to update
   * @param component component name
   * @param deltaMoles component delta in mol/s stream basis
   */
  private void addComponentDelta(SystemInterface system, String component, double deltaMoles) {
    double boundedDelta = deltaMoles;
    if (deltaMoles < 0.0) {
      boundedDelta = -Math.min(-deltaMoles, componentMoles(system, component) * 0.999999);
    }
    if (Math.abs(boundedDelta) > 0.0) {
      system.addComponent(component, boundedDelta);
    }
  }

  /**
   * Get the column unknown block size per segment.
   *
   * @param components active transfer components
   * @return unknown count per segment
   */
  private int columnUnknownBlockSize(List<String> components) {
    return components.size() + 3;
  }

  /**
   * Get the unknown index for a segment component flux.
   *
   * @param segment segment index
   * @param componentIndex component index
   * @param components active transfer components
   * @return flux unknown index
   */
  private int columnFluxIndex(int segment, int componentIndex, List<String> components) {
    return segment * columnUnknownBlockSize(components) + componentIndex;
  }

  /**
   * Get the unknown index for a segment interface temperature.
   *
   * @param segment segment index
   * @param components active transfer components
   * @return interface-temperature unknown index
   */
  private int columnInterfaceTemperatureIndex(int segment, List<String> components) {
    return segment * columnUnknownBlockSize(components) + components.size();
  }

  /**
   * Get the unknown index for a segment gas outlet temperature.
   *
   * @param segment segment index
   * @param components active transfer components
   * @return gas outlet temperature unknown index
   */
  private int columnGasOutletTemperatureIndex(int segment, List<String> components) {
    return segment * columnUnknownBlockSize(components) + components.size() + 1;
  }

  /**
   * Get the unknown index for a segment liquid outlet temperature.
   *
   * @param segment segment index
   * @param components active transfer components
   * @return liquid outlet temperature unknown index
   */
  private int columnLiquidOutletTemperatureIndex(int segment, List<String> components) {
    return segment * columnUnknownBlockSize(components) + components.size() + 2;
  }

  /**
   * Calculate finite-difference step size for a column unknown.
   *
   * @param unknowns current unknown vector
   * @param variable variable index
   * @param componentCount number of component flux unknowns per segment
   * @return finite-difference step
   */
  private double columnResidualVariableStep(double[] unknowns, int variable, int componentCount) {
    int localIndex = variable % (componentCount + 3);
    if (localIndex >= componentCount) {
      return Math.max(1.0e-3, Math.abs(unknowns[variable]) * 1.0e-5);
    }
    return Math.max(1.0e-8, Math.abs(unknowns[variable]) * 1.0e-4);
  }

  /**
   * Convert a boxed residual list to a primitive array.
   *
   * @param values residual values
   * @return primitive residual array
   */
  private double[] toPrimitiveArray(List<Double> values) {
    double[] array = new double[values.size()];
    for (int i = 0; i < values.size(); i++) {
      array[i] = values.get(i).doubleValue();
    }
    return array;
  }

  /**
   * Initialize all liquid segment inlets from the fresh liquid inlet.
   *
   * @param liquidIn fresh liquid inlet system
   * @return list of liquid entering systems for each segment
   */
  private List<SystemInterface> initializeLiquidProfile(SystemInterface liquidIn) {
    List<SystemInterface> liquidProfile = new ArrayList<SystemInterface>();
    for (int i = 0; i < numberOfSegments; i++) {
      liquidProfile.add(liquidIn.clone());
    }
    return liquidProfile;
  }

  /**
   * Run one bottom-to-top gas pass against the current counter-current liquid profile.
   *
   * @param gasIn gas inlet system
   * @param liquidIn liquid inlet system
   * @param liquidEntering liquid systems entering each segment
   * @return one profile iteration solution
   */
  private CounterCurrentSolution runOneProfileIteration(SystemInterface gasIn,
      SystemInterface liquidIn, List<SystemInterface> liquidEntering) {
    SystemInterface gasCurrent = gasIn.clone();
    List<SystemInterface> liquidLeaving = new ArrayList<SystemInterface>();
    List<SegmentResult> iterationResults = new ArrayList<SegmentResult>();
    for (int segment = 0; segment < numberOfSegments; segment++) {
      SystemInterface liquidCurrent = liquidEntering.get(segment).clone();
      SegmentComputation computation = calculateSegment(segment, gasCurrent, liquidCurrent);
      gasCurrent = computation.gasOutlet;
      liquidLeaving.add(computation.liquidOutlet);
      iterationResults.add(computation.result);
    }
    SystemInterface liquidOutlet = liquidLeaving.get(0).clone();
    return new CounterCurrentSolution(gasCurrent, liquidOutlet, liquidLeaving, iterationResults);
  }

  /**
   * Shift liquid leaving one segment into the segment below for the next iteration.
   *
   * @param liquidIn fresh liquid inlet system
   * @param liquidLeaving liquid systems leaving each segment from the previous iteration
   * @return updated segment liquid inlet profile
   */
  private List<SystemInterface> updateLiquidProfile(SystemInterface liquidIn,
      List<SystemInterface> liquidLeaving) {
    List<SystemInterface> updated = new ArrayList<SystemInterface>();
    for (int segment = 0; segment < numberOfSegments; segment++) {
      if (segment == numberOfSegments - 1) {
        updated.add(liquidIn.clone());
      } else {
        updated.add(liquidLeaving.get(segment + 1).clone());
      }
    }
    return updated;
  }

  /**
   * Accept a profile solution as the last calculated result.
   *
   * @param solution solution to accept
   */
  private void acceptSolution(CounterCurrentSolution solution) {
    segmentResults.clear();
    componentTransferTotals.clear();
    totalAbsoluteMolarTransfer = 0.0;
    if (solution == null) {
      return;
    }
    segmentResults.addAll(solution.segmentResults);
    for (int i = 0; i < solution.segmentResults.size(); i++) {
      SegmentResult result = solution.segmentResults.get(i);
      for (Map.Entry<String, Double> entry : result.getComponentMoleTransfer().entrySet()) {
        Double oldValue = componentTransferTotals.get(entry.getKey());
        double newValue = (oldValue == null ? 0.0 : oldValue.doubleValue()) + entry.getValue();
        componentTransferTotals.put(entry.getKey(), newValue);
        totalAbsoluteMolarTransfer += Math.abs(entry.getValue());
      }
    }
  }

  /**
   * Calculate one rate-based segment.
   *
   * @param segment segment index from bottom, zero based
   * @param gasIn gas system entering the segment
   * @param liquidIn liquid system entering the segment
   * @return segment computation with outlet systems and result data
   */
  private SegmentComputation calculateSegment(int segment, SystemInterface gasIn,
      SystemInterface liquidIn) {
    SystemInterface gas = gasIn.clone();
    SystemInterface liquid = liquidIn.clone();
    flashAndInitialize(gas);
    flashAndInitialize(liquid);

    double segmentHeight = packedHeight / numberOfSegments;
    if (segmentHeight > 0.0 && segmentSolver == SegmentSolver.SIMULTANEOUS_RESIDUAL
        && heatTransferModel != HeatTransferModel.NONE) {
      return calculateSimultaneousResidualSegment(segment, gas, liquid, segmentHeight);
    }

    double inletTotalEnthalpy = gas.getEnthalpy() + liquid.getEnthalpy();
    Map<String, Double> componentTransfers = new LinkedHashMap<String, Double>();
    TransportSnapshot snapshot = calculateTransportSnapshot(gas, liquid, segmentHeight);
    InterfaceEquilibrium interfaceEquilibrium =
        calculateInterfaceEquilibrium(gas, liquid, snapshot);
    double heatTransferRate = 0.0;
    if (segmentHeight > 0.0) {
      List<String> components = getTransferComponentList(gas, liquid);
      for (int i = 0; i < components.size(); i++) {
        String component = components.get(i);
        double transfer =
            calculateComponentTransfer(component, gas, liquid, snapshot, interfaceEquilibrium);
        if (Math.abs(transfer) > 0.0) {
          applyComponentTransfer(component, transfer, gas, liquid);
          componentTransfers.put(component, transfer);
        }
      }
      heatTransferRate = applyInterphaseHeatTransfer(gas, liquid, snapshot);
      flashAndInitialize(gas);
      flashAndInitialize(liquid);
    }

    double totalTransfer = 0.0;
    for (Double value : componentTransfers.values()) {
      totalTransfer += value.doubleValue();
    }
    SegmentResult result = new SegmentResult(segment + 1, (segment + 0.5) * segmentHeight,
        gas.getTemperature(), liquid.getTemperature(), gas.getPressure(), liquid.getPressure(),
        gas.getTotalNumberOfMoles(), liquid.getTotalNumberOfMoles(), snapshot.gasDensity,
        snapshot.liquidDensity, snapshot.gasViscosity, snapshot.liquidViscosity,
        snapshot.gasDiffusivity, snapshot.liquidDiffusivity, snapshot.wettedArea, snapshot.kGa,
        snapshot.kLa, snapshot.gasHeatTransferCoefficient, snapshot.liquidHeatTransferCoefficient,
        snapshot.overallHeatTransferCoefficient, interfaceEquilibrium.interfaceTemperatureK,
        heatTransferRate, snapshot.pressureDropPerMeter, snapshot.percentFlood, totalTransfer,
        componentTransfers, interfaceEquilibrium.gasMoleFractions,
        interfaceEquilibrium.liquidMoleFractions, interfaceEquilibrium.equilibriumRatios,
        SegmentSolver.SEQUENTIAL_EXPLICIT.name(), 0, 0.0, 0.0,
        gas.getEnthalpy() + liquid.getEnthalpy() - inletTotalEnthalpy);
    return new SegmentComputation(gas, liquid, result);
  }

  /**
   * Calculate transport properties and packing coefficients for one segment.
   *
   * @param gas gas system in the segment
   * @param liquid liquid system in the segment
   * @param segmentHeight segment height in metres
   * @return transport snapshot for the segment
   */
  private TransportSnapshot calculateTransportSnapshot(SystemInterface gas, SystemInterface liquid,
      double segmentHeight) {
    PhaseInterface gasPhase = getGasPhase(gas);
    PhaseInterface liquidPhase = getLiquidPhase(liquid);
    double gasDensity = finitePositive(gasPhase.getDensity("kg/m3"), 1.0);
    double liquidDensity = finitePositive(liquidPhase.getDensity("kg/m3"), 800.0);
    double gasViscosity = finitePositive(gasPhase.getViscosity("kg/msec"), 1.0e-5);
    double liquidViscosity = finitePositive(liquidPhase.getViscosity("kg/msec"), 1.0e-3);
    double gasDiffusivity = averageDiffusivity(gasPhase, true);
    double liquidDiffusivity = averageDiffusivity(liquidPhase, false);
    double surfaceTension = estimateSurfaceTension(gas, liquid);
    double gasHeatCapacity = heatCapacityMass(gasPhase, DEFAULT_GAS_HEAT_CAPACITY);
    double liquidHeatCapacity = heatCapacityMass(liquidPhase, DEFAULT_LIQUID_HEAT_CAPACITY);
    double gasConductivity = thermalConductivity(gasPhase, DEFAULT_GAS_THERMAL_CONDUCTIVITY);
    double liquidConductivity =
        thermalConductivity(liquidPhase, DEFAULT_LIQUID_THERMAL_CONDUCTIVITY);

    PackingHydraulicsCalculator hydraulics = new PackingHydraulicsCalculator();
    hydraulics.setPackingSpecification(packingSpecification);
    hydraulics.setColumnDiameter(columnDiameter);
    hydraulics.setPackedHeight(Math.max(segmentHeight, 1.0e-9));
    hydraulics.setVaporMassFlow(gasPhase.getMass());
    hydraulics.setLiquidMassFlow(liquidPhase.getMass());
    hydraulics.setVaporDensity(gasDensity);
    hydraulics.setLiquidDensity(liquidDensity);
    hydraulics.setVaporViscosity(gasViscosity);
    hydraulics.setLiquidViscosity(liquidViscosity);
    hydraulics.setVaporDiffusivity(gasDiffusivity);
    hydraulics.setLiquidDiffusivity(liquidDiffusivity);
    hydraulics.setSurfaceTension(surfaceTension);
    hydraulics.calculate();

    double gasMultiplier = 1.0;
    double liquidMultiplier = 1.0;
    if (massTransferCorrelation == MassTransferCorrelation.BILLET_SCHULTES_1999) {
      gasMultiplier = Math.max(0.1, packingSpecification.getBilletGasConstant() / 0.4);
      liquidMultiplier = Math.max(0.1, packingSpecification.getBilletLiquidConstant());
    }
    double kGa =
        finiteNonNegative(hydraulics.getKGa(), 0.0) * gasMultiplier * massTransferCorrectionFactor;
    double kLa = finiteNonNegative(hydraulics.getKLa(), 0.0) * liquidMultiplier
        * massTransferCorrectionFactor;
    double gasHeatTransferCoefficient = calculateVolumetricHeatTransferCoefficient(kGa, gasDensity,
        gasHeatCapacity, gasViscosity, gasDiffusivity, gasConductivity);
    double liquidHeatTransferCoefficient = calculateVolumetricHeatTransferCoefficient(kLa,
        liquidDensity, liquidHeatCapacity, liquidViscosity, liquidDiffusivity, liquidConductivity);
    double overallHeatTransferCoefficient =
        combineHeatTransferCoefficients(gasHeatTransferCoefficient, liquidHeatTransferCoefficient);
    double interfaceTemperature = calculateInterfaceTemperature(gas.getTemperature(),
        liquid.getTemperature(), gasHeatTransferCoefficient, liquidHeatTransferCoefficient);
    return new TransportSnapshot(gasDensity, liquidDensity, gasViscosity, liquidViscosity,
        gasDiffusivity, liquidDiffusivity, finiteNonNegative(hydraulics.getWettedArea(), 0.0), kGa,
        kLa, gasHeatCapacity, liquidHeatCapacity, gasHeatTransferCoefficient,
        liquidHeatTransferCoefficient, overallHeatTransferCoefficient, interfaceTemperature,
        finiteNonNegative(hydraulics.getPressureDropPerMeter(), 0.0),
        finiteNonNegative(hydraulics.getPercentFlood(), 0.0));
  }

  /**
   * Calculate component transfer rate for a component in one segment.
   *
   * @param component component name
   * @param gas gas system
   * @param liquid liquid system
   * @param snapshot transport snapshot for the segment
   * @param interfaceEquilibrium interface equilibrium data
   * @return transfer rate in mol/s, positive from gas to liquid
   */
  private double calculateComponentTransfer(String component, SystemInterface gas,
      SystemInterface liquid, TransportSnapshot snapshot,
      InterfaceEquilibrium interfaceEquilibrium) {
    double transfer =
        calculateUnboundedComponentTransfer(component, gas, liquid, snapshot, interfaceEquilibrium);
    return limitTransfer(component, transfer, gas, liquid);
  }

  /**
   * Calculate the unbounded Maxwell-Stefan film transfer rate for one component.
   *
   * @param component component name
   * @param gas gas system
   * @param liquid liquid system
   * @param snapshot transport snapshot for the segment
   * @param interfaceEquilibrium interface equilibrium data
   * @return unbounded transfer rate in mol/s, positive from gas to liquid
   */
  private double calculateUnboundedComponentTransfer(String component, SystemInterface gas,
      SystemInterface liquid, TransportSnapshot snapshot,
      InterfaceEquilibrium interfaceEquilibrium) {
    PhaseInterface gasPhase = getGasPhase(gas);
    PhaseInterface liquidPhase = getLiquidPhase(liquid);
    double kValue = interfaceEquilibrium.getEquilibriumRatio(component);
    double gasFraction = moleFraction(gasPhase, component);
    double liquidFraction = moleFraction(liquidPhase, component);
    double gasInterfaceFraction = interfaceEquilibrium.getGasMoleFraction(component,
        clamp(kValue * liquidFraction, 0.0, 0.999999));
    double liquidInterfaceFraction = interfaceEquilibrium.getLiquidMoleFraction(component,
        kValue > 1.0e-12 ? clamp(gasInterfaceFraction / kValue, 0.0, 0.999999) : liquidFraction);
    double gasDrivingForce = gasFraction - gasInterfaceFraction;
    double liquidDrivingForce = liquidInterfaceFraction - liquidFraction;
    if (Math.abs(gasDrivingForce) < 1.0e-12 || snapshot.kGa <= 0.0 || snapshot.kLa <= 0.0) {
      return 0.0;
    }
    double gasFilmCoefficient =
        calculateFilmCoefficient(gasPhase, component, snapshot.kGa, snapshot.gasDiffusivity, true);
    double liquidFilmCoefficient = calculateFilmCoefficient(liquidPhase, component, snapshot.kLa,
        snapshot.liquidDiffusivity, false);
    double gasFluxDensity = gasFilmCoefficient * molarConcentration(gasPhase) * gasDrivingForce;
    double liquidFluxDensity =
        liquidFilmCoefficient * molarConcentration(liquidPhase) * liquidDrivingForce;
    double transferDensity = combineFilmFluxes(gasFluxDensity, liquidFluxDensity);
    if (Math.abs(transferDensity) <= 0.0) {
      double yStar = clamp(kValue * liquidFraction, 0.0, 0.999999);
      double drivingForce = gasFraction - yStar;
      double overallCoefficient =
          1.0 / (1.0 / gasFilmCoefficient + Math.max(kValue, 1.0e-12) / liquidFilmCoefficient);
      transferDensity = overallCoefficient * molarConcentration(gasPhase) * drivingForce;
    }
    double segmentVolume =
        Math.PI * columnDiameter * columnDiameter / 4.0 * packedHeight / numberOfSegments;
    return transferDensity * segmentVolume;
  }

  /**
   * Calculate one segment using simultaneous flux and interfacial energy residuals.
   *
   * @param segment segment index from bottom, zero based
   * @param gas gas system entering the segment
   * @param liquid liquid system entering the segment
   * @param segmentHeight segment height in metres
   * @return segment computation with outlet systems and residual diagnostics
   */
  private SegmentComputation calculateSimultaneousResidualSegment(int segment, SystemInterface gas,
      SystemInterface liquid, double segmentHeight) {
    double inletTotalEnthalpy = gas.getEnthalpy() + liquid.getEnthalpy();
    TransportSnapshot snapshot = calculateTransportSnapshot(gas, liquid, segmentHeight);
    List<String> components = getTransferComponentList(gas, liquid);
    SegmentResidualEvaluation evaluation = solveSegmentResiduals(gas, liquid, snapshot, components);

    Map<String, Double> componentTransfers = new LinkedHashMap<String, Double>();
    for (int i = 0; i < components.size(); i++) {
      String component = components.get(i);
      Double transferValue = evaluation.componentTransfers.get(component);
      double transfer = transferValue == null ? 0.0 : transferValue.doubleValue();
      if (Math.abs(transfer) > 0.0) {
        applyComponentTransfer(component, transfer, gas, liquid);
        componentTransfers.put(component, transfer);
      }
    }
    flashAndInitialize(gas);
    flashAndInitialize(liquid);
    applyEnthalpyTarget(gas, evaluation.gasTargetEnthalpy);
    applyEnthalpyTarget(liquid, inletTotalEnthalpy - gas.getEnthalpy());

    double totalTransfer = 0.0;
    for (Double value : componentTransfers.values()) {
      totalTransfer += value.doubleValue();
    }
    double enthalpyBalanceResidual = gas.getEnthalpy() + liquid.getEnthalpy() - inletTotalEnthalpy;
    SegmentResult result = new SegmentResult(segment + 1, (segment + 0.5) * segmentHeight,
        gas.getTemperature(), liquid.getTemperature(), gas.getPressure(), liquid.getPressure(),
        gas.getTotalNumberOfMoles(), liquid.getTotalNumberOfMoles(), snapshot.gasDensity,
        snapshot.liquidDensity, snapshot.gasViscosity, snapshot.liquidViscosity,
        snapshot.gasDiffusivity, snapshot.liquidDiffusivity, snapshot.wettedArea, snapshot.kGa,
        snapshot.kLa, snapshot.gasHeatTransferCoefficient, snapshot.liquidHeatTransferCoefficient,
        snapshot.overallHeatTransferCoefficient,
        evaluation.interfaceEquilibrium.interfaceTemperatureK, evaluation.heatTransferRateW,
        snapshot.pressureDropPerMeter, snapshot.percentFlood, totalTransfer, componentTransfers,
        evaluation.interfaceEquilibrium.gasMoleFractions,
        evaluation.interfaceEquilibrium.liquidMoleFractions,
        evaluation.interfaceEquilibrium.equilibriumRatios,
        SegmentSolver.SIMULTANEOUS_RESIDUAL.name(), evaluation.iterations,
        evaluation.maxFluxResidualMolPerSec, evaluation.heatBalanceResidualW,
        enthalpyBalanceResidual);
    return new SegmentComputation(gas, liquid, result);
  }

  /**
   * Solve the simultaneous residual equations for a segment.
   *
   * @param gas gas system entering the segment
   * @param liquid liquid system entering the segment
   * @param snapshot transport snapshot for the segment
   * @param components active transfer components
   * @return best residual evaluation found
   */
  private SegmentResidualEvaluation solveSegmentResiduals(SystemInterface gas,
      SystemInterface liquid, TransportSnapshot snapshot, List<String> components) {
    double[] unknowns = createInitialResidualUnknowns(gas, liquid, snapshot, components);
    SegmentResidualEvaluation best =
        evaluateSegmentResidual(gas, liquid, snapshot, components, unknowns, 0);
    for (int iteration = 0; iteration < maxSegmentResidualIterations; iteration++) {
      if (best.norm <= segmentResidualTolerance) {
        return best;
      }
      Matrix step = calculateResidualStep(gas, liquid, snapshot, components, unknowns, best);
      if (step == null) {
        return best;
      }
      boolean improved = false;
      double[] bestUnknowns = unknowns;
      SegmentResidualEvaluation bestCandidate = best;
      double damping = 1.0;
      for (int lineSearch = 0; lineSearch < 8; lineSearch++) {
        double[] candidateUnknowns =
            applyResidualStep(unknowns, step, damping, gas, liquid, components);
        SegmentResidualEvaluation candidate = evaluateSegmentResidual(gas, liquid, snapshot,
            components, candidateUnknowns, iteration + 1);
        if (candidate.norm < bestCandidate.norm) {
          bestUnknowns = candidateUnknowns;
          bestCandidate = candidate;
          improved = true;
          break;
        }
        damping *= 0.5;
      }
      if (!improved) {
        return best;
      }
      unknowns = bestUnknowns;
      best = bestCandidate;
    }
    return best;
  }

  /**
   * Create initial guesses for component transfers and interface temperature.
   *
   * @param gas gas system
   * @param liquid liquid system
   * @param snapshot transport snapshot
   * @param components active transfer components
   * @return residual unknown vector
   */
  private double[] createInitialResidualUnknowns(SystemInterface gas, SystemInterface liquid,
      TransportSnapshot snapshot, List<String> components) {
    double[] unknowns = new double[components.size() + 1];
    InterfaceEquilibrium equilibrium = calculateInterfaceEquilibrium(gas, liquid, snapshot);
    for (int i = 0; i < components.size(); i++) {
      unknowns[i] =
          calculateComponentTransfer(components.get(i), gas, liquid, snapshot, equilibrium);
    }
    unknowns[components.size()] = snapshot.interfaceTemperatureK;
    return clampResidualUnknowns(unknowns, gas, liquid, components);
  }

  /**
   * Evaluate normalized residuals for the simultaneous segment equations.
   *
   * @param gas gas system
   * @param liquid liquid system
   * @param snapshot transport snapshot
   * @param components active transfer components
   * @param unknowns residual unknown vector
   * @param iterations iteration count represented by the evaluation
   * @return residual evaluation
   */
  private SegmentResidualEvaluation evaluateSegmentResidual(SystemInterface gas,
      SystemInterface liquid, TransportSnapshot snapshot, List<String> components,
      double[] unknowns, int iterations) {
    double[] boundedUnknowns = clampResidualUnknowns(unknowns, gas, liquid, components);
    double interfaceTemperature = boundedUnknowns[components.size()];
    InterfaceEquilibrium equilibrium =
        calculateInterfaceEquilibrium(gas, liquid, interfaceTemperature);
    double[] residuals = new double[components.size() + 1];
    Map<String, Double> componentTransfers = new LinkedHashMap<String, Double>();
    double maxFluxResidual = 0.0;
    for (int i = 0; i < components.size(); i++) {
      String component = components.get(i);
      double proposedTransfer = boundedUnknowns[i];
      double predictedTransfer =
          calculateUnboundedComponentTransfer(component, gas, liquid, snapshot, equilibrium);
      predictedTransfer = limitTransfer(component, predictedTransfer, gas, liquid);
      double fluxResidual = proposedTransfer - predictedTransfer;
      residuals[i] =
          fluxResidual / transferResidualScale(component, predictedTransfer, gas, liquid);
      maxFluxResidual = Math.max(maxFluxResidual, Math.abs(fluxResidual));
      componentTransfers.put(component, proposedTransfer);
    }

    double segmentVolume =
        Math.PI * columnDiameter * columnDiameter / 4.0 * packedHeight / numberOfSegments;
    double gasSensibleHeat = snapshot.gasHeatTransferCoefficient * segmentVolume
        * (gas.getTemperature() - interfaceTemperature);
    double liquidSensibleHeat = snapshot.liquidHeatTransferCoefficient * segmentVolume
        * (interfaceTemperature - liquid.getTemperature());
    double gasMassEnthalpy = 0.0;
    double liquidMassEnthalpy = 0.0;
    for (int i = 0; i < components.size(); i++) {
      String component = components.get(i);
      double transfer = componentTransfers.get(component).doubleValue();
      gasMassEnthalpy += transfer * equilibrium.getGasMolarEnthalpy(component);
      liquidMassEnthalpy += transfer * equilibrium.getLiquidMolarEnthalpy(component);
    }
    double heatBalanceResidual =
        gasSensibleHeat + gasMassEnthalpy - liquidSensibleHeat - liquidMassEnthalpy;
    residuals[components.size()] = heatBalanceResidual / heatResidualScale(gasSensibleHeat,
        liquidSensibleHeat, gasMassEnthalpy, liquidMassEnthalpy);
    double gasTargetEnthalpy = gas.getEnthalpy() - gasSensibleHeat - gasMassEnthalpy;
    double liquidTargetEnthalpy = liquid.getEnthalpy() + liquidSensibleHeat + liquidMassEnthalpy;
    return new SegmentResidualEvaluation(equilibrium, componentTransfers, residuals,
        residualNorm(residuals), maxFluxResidual, heatBalanceResidual, liquidSensibleHeat,
        gasTargetEnthalpy, liquidTargetEnthalpy, iterations);
  }

  /**
   * Calculate a Newton step for normalized segment residuals.
   *
   * @param gas gas system
   * @param liquid liquid system
   * @param snapshot transport snapshot
   * @param components active transfer components
   * @param unknowns current unknown vector
   * @param evaluation current residual evaluation
   * @return Newton step, or null if the linear solve fails
   */
  private Matrix calculateResidualStep(SystemInterface gas, SystemInterface liquid,
      TransportSnapshot snapshot, List<String> components, double[] unknowns,
      SegmentResidualEvaluation evaluation) {
    int dimension = unknowns.length;
    double[][] jacobian = new double[dimension][dimension];
    for (int variable = 0; variable < dimension; variable++) {
      double step = residualVariableStep(unknowns, variable, components.size());
      double[] shifted = unknowns.clone();
      shifted[variable] += step;
      shifted = clampResidualUnknowns(shifted, gas, liquid, components);
      double actualStep = shifted[variable] - unknowns[variable];
      if (Math.abs(actualStep) < 1.0e-20) {
        shifted = unknowns.clone();
        shifted[variable] -= step;
        shifted = clampResidualUnknowns(shifted, gas, liquid, components);
        actualStep = shifted[variable] - unknowns[variable];
      }
      if (Math.abs(actualStep) < 1.0e-20) {
        jacobian[variable][variable] = 1.0;
      } else {
        SegmentResidualEvaluation shiftedEvaluation = evaluateSegmentResidual(gas, liquid, snapshot,
            components, shifted, evaluation.iterations);
        for (int row = 0; row < dimension; row++) {
          jacobian[row][variable] =
              (shiftedEvaluation.normalizedResiduals[row] - evaluation.normalizedResiduals[row])
                  / actualStep;
        }
      }
    }
    double[][] rhsValues = new double[dimension][1];
    for (int row = 0; row < dimension; row++) {
      rhsValues[row][0] = -evaluation.normalizedResiduals[row];
    }
    try {
      return new Matrix(jacobian).solve(new Matrix(rhsValues));
    } catch (RuntimeException ex) {
      return null;
    }
  }

  /**
   * Apply a damped residual step and clamp the unknowns to physical bounds.
   *
   * @param unknowns current unknowns
   * @param step Newton step
   * @param damping damping factor from zero to one
   * @param gas gas system
   * @param liquid liquid system
   * @param components active transfer components
   * @return bounded candidate unknowns
   */
  private double[] applyResidualStep(double[] unknowns, Matrix step, double damping,
      SystemInterface gas, SystemInterface liquid, List<String> components) {
    double[] candidate = unknowns.clone();
    for (int i = 0; i < candidate.length; i++) {
      candidate[i] += damping * step.get(i, 0);
    }
    return clampResidualUnknowns(candidate, gas, liquid, components);
  }

  /**
   * Clamp residual unknowns to available component inventory and temperature bounds.
   *
   * @param unknowns unknown vector to clamp
   * @param gas gas system
   * @param liquid liquid system
   * @param components active transfer components
   * @return clamped unknown vector
   */
  private double[] clampResidualUnknowns(double[] unknowns, SystemInterface gas,
      SystemInterface liquid, List<String> components) {
    double[] bounded = unknowns.clone();
    for (int i = 0; i < components.size(); i++) {
      if (!Double.isFinite(bounded[i])) {
        bounded[i] = 0.0;
      }
      bounded[i] = limitTransfer(components.get(i), bounded[i], gas, liquid);
    }
    double minimumTemperature =
        Math.max(1.0, Math.min(gas.getTemperature(), liquid.getTemperature()) - 100.0);
    double maximumTemperature = Math.max(gas.getTemperature(), liquid.getTemperature()) + 100.0;
    if (!Double.isFinite(bounded[components.size()])) {
      bounded[components.size()] = 0.5 * (gas.getTemperature() + liquid.getTemperature());
    }
    bounded[components.size()] =
        clamp(bounded[components.size()], minimumTemperature, maximumTemperature);
    return bounded;
  }

  /**
   * Calculate finite-difference step size for a residual unknown.
   *
   * @param unknowns current unknown vector
   * @param variable variable index
   * @param componentCount number of component transfer unknowns
   * @return finite-difference step
   */
  private double residualVariableStep(double[] unknowns, int variable, int componentCount) {
    if (variable == componentCount) {
      return Math.max(1.0e-3, Math.abs(unknowns[variable]) * 1.0e-5);
    }
    return Math.max(1.0e-8, Math.abs(unknowns[variable]) * 1.0e-4);
  }

  /**
   * Calculate the transfer residual scaling for one component.
   *
   * @param component component name
   * @param predictedTransfer predicted transfer rate in mol/s
   * @param gas gas system
   * @param liquid liquid system
   * @return positive residual scaling in mol/s
   */
  private double transferResidualScale(String component, double predictedTransfer,
      SystemInterface gas, SystemInterface liquid) {
    double inventory = Math.max(componentMoles(gas, component), componentMoles(liquid, component));
    return Math.max(1.0e-10,
        Math.max(Math.abs(predictedTransfer), inventory * maxTransferFractionPerSegment * 1.0e-4));
  }

  /**
   * Calculate heat residual scaling.
   *
   * @param gasSensibleHeat gas-side sensible heat rate in W
   * @param liquidSensibleHeat liquid-side sensible heat rate in W
   * @param gasMassEnthalpy gas-side transferred component enthalpy rate in W
   * @param liquidMassEnthalpy liquid-side transferred component enthalpy rate in W
   * @return positive residual scaling in W
   */
  private double heatResidualScale(double gasSensibleHeat, double liquidSensibleHeat,
      double gasMassEnthalpy, double liquidMassEnthalpy) {
    return Math.max(1.0, Math.abs(gasSensibleHeat) + Math.abs(liquidSensibleHeat)
        + Math.abs(gasMassEnthalpy) + Math.abs(liquidMassEnthalpy));
  }

  /**
   * Calculate infinity norm of normalized residuals.
   *
   * @param residuals normalized residual array
   * @return maximum absolute residual
   */
  private double residualNorm(double[] residuals) {
    double norm = 0.0;
    for (int i = 0; i < residuals.length; i++) {
      norm = Math.max(norm, Math.abs(residuals[i]));
    }
    return norm;
  }

  /**
   * Apply a total enthalpy target by pressure-enthalpy flash.
   *
   * @param system thermodynamic system to flash
   * @param targetEnthalpy target total enthalpy in J or W-equivalent stream basis
   */
  private void applyEnthalpyTarget(SystemInterface system, double targetEnthalpy) {
    try {
      ThermodynamicOperations operations = new ThermodynamicOperations(system);
      operations.PHflash(targetEnthalpy);
      system.initProperties();
    } catch (RuntimeException ex) {
      double estimatedTemperature = estimateTemperatureForTargetEnthalpy(system, targetEnthalpy);
      system.setTemperature(estimatedTemperature);
      try {
        flashAndInitialize(system);
      } catch (RuntimeException innerException) {
        system.setTemperature(clamp(system.getTemperature(), 250.0, 500.0));
        system.init(3);
        system.initProperties();
      }
    }
  }

  /**
   * Estimate a temperature for an enthalpy target if PH flash fails.
   *
   * @param system thermodynamic system
   * @param targetEnthalpy target total enthalpy in J or W-equivalent stream basis
   * @return estimated temperature in kelvin
   */
  private double estimateTemperatureForTargetEnthalpy(SystemInterface system,
      double targetEnthalpy) {
    double heatCapacity = Math.max(1.0, system.getCp("J/K"));
    double estimatedTemperature =
        system.getTemperature() + (targetEnthalpy - system.getEnthalpy()) / heatCapacity;
    if (!Double.isFinite(estimatedTemperature)) {
      return system.getTemperature();
    }
    return clamp(estimatedTemperature, 1.0, 1500.0);
  }

  /**
   * Apply a component transfer to gas and liquid systems.
   *
   * @param component component name
   * @param transfer transfer rate in mol/s, positive from gas to liquid
   * @param gas gas system to update
   * @param liquid liquid system to update
   */
  private void applyComponentTransfer(String component, double transfer, SystemInterface gas,
      SystemInterface liquid) {
    gas.addComponent(component, -transfer);
    liquid.addComponent(component, transfer);
  }

  /**
   * Limit transfer to available component inventory and configured stability fraction.
   *
   * @param component component name
   * @param proposedTransfer proposed transfer in mol/s, positive from gas to liquid
   * @param gas gas system
   * @param liquid liquid system
   * @return bounded transfer in mol/s
   */
  private double limitTransfer(String component, double proposedTransfer, SystemInterface gas,
      SystemInterface liquid) {
    if (proposedTransfer > 0.0) {
      double available = componentMoles(gas, component) * maxTransferFractionPerSegment;
      return Math.min(proposedTransfer, Math.max(0.0, available));
    } else if (proposedTransfer < 0.0) {
      double available = componentMoles(liquid, component) * maxTransferFractionPerSegment;
      return -Math.min(-proposedTransfer, Math.max(0.0, available));
    }
    return 0.0;
  }

  /**
   * Calculate interface equilibrium compositions for the mixed segment fluids.
   *
   * @param gas gas system
   * @param liquid liquid system
   * @param snapshot transport snapshot containing the interfacial temperature estimate
   * @return interface equilibrium data
   */
  private InterfaceEquilibrium calculateInterfaceEquilibrium(SystemInterface gas,
      SystemInterface liquid, TransportSnapshot snapshot) {
    Map<String, Double> gasFractions = new LinkedHashMap<String, Double>();
    Map<String, Double> liquidFractions = new LinkedHashMap<String, Double>();
    Map<String, Double> ratios = new LinkedHashMap<String, Double>();
    Map<String, Double> gasMolarEnthalpies = new LinkedHashMap<String, Double>();
    Map<String, Double> liquidMolarEnthalpies = new LinkedHashMap<String, Double>();
    SystemInterface mixed = gas.clone();
    addSystemComponents(mixed, liquid);
    mixed.setTemperature(snapshot.interfaceTemperatureK);
    mixed.setPressure(0.5 * (gas.getPressure() + liquid.getPressure()));
    PhaseInterface gasPhase;
    PhaseInterface liquidPhase;
    try {
      flashAndInitialize(mixed);
      gasPhase = getGasPhase(mixed);
      liquidPhase = getLiquidPhase(mixed);
    } catch (RuntimeException ex) {
      gasPhase = getGasPhase(gas);
      liquidPhase = getLiquidPhase(liquid);
    }
    List<String> allComponents = getTransferComponentList(gas, liquid);
    for (int i = 0; i < allComponents.size(); i++) {
      String component = allComponents.get(i);
      double x = moleFraction(liquidPhase, component);
      double y = moleFraction(gasPhase, component);
      gasFractions.put(component, y);
      liquidFractions.put(component, x);
      gasMolarEnthalpies.put(component, componentMolarEnthalpy(gasPhase, component,
          snapshot.interfaceTemperatureK, phaseMolarEnthalpy(gasPhase)));
      liquidMolarEnthalpies.put(component, componentMolarEnthalpy(liquidPhase, component,
          snapshot.interfaceTemperatureK, phaseMolarEnthalpy(liquidPhase)));
      if (x > 1.0e-12 && y >= 0.0) {
        ratios.put(component, Math.max(1.0e-12, y / x));
      } else {
        ratios.put(component, 1.0);
      }
    }
    return new InterfaceEquilibrium(snapshot.interfaceTemperatureK, gasFractions, liquidFractions,
        ratios, gasMolarEnthalpies, liquidMolarEnthalpies);
  }

  /**
   * Calculate interface equilibrium at a specified temperature.
   *
   * @param gas gas system
   * @param liquid liquid system
   * @param interfaceTemperatureK interface temperature in kelvin
   * @return interface equilibrium data
   */
  private InterfaceEquilibrium calculateInterfaceEquilibrium(SystemInterface gas,
      SystemInterface liquid, double interfaceTemperatureK) {
    TransportSnapshot snapshot = new TransportSnapshot(0.0, 0.0, 0.0, 0.0, DEFAULT_GAS_DIFFUSIVITY,
        DEFAULT_LIQUID_DIFFUSIVITY, 0.0, 0.0, 0.0, DEFAULT_GAS_HEAT_CAPACITY,
        DEFAULT_LIQUID_HEAT_CAPACITY, 0.0, 0.0, 0.0, interfaceTemperatureK, 0.0, 0.0);
    return calculateInterfaceEquilibrium(gas, liquid, snapshot);
  }

  /**
   * Calculate the active film coefficient for one component.
   *
   * @param phase phase to inspect
   * @param component component name
   * @param baseCoefficient scalar packed-bed film coefficient
   * @param referenceDiffusivity reference effective diffusivity in m2/s
   * @param gasPhase true when the phase is gas
   * @return component film coefficient in 1/s
   */
  private double calculateFilmCoefficient(PhaseInterface phase, String component,
      double baseCoefficient, double referenceDiffusivity, boolean gasPhase) {
    if (filmModel != FilmModel.MAXWELL_STEFAN_MATRIX || phase == null) {
      return baseCoefficient;
    }
    return maxwellStefanFilmCoefficient(phase, component, baseCoefficient, referenceDiffusivity,
        gasPhase);
  }

  /**
   * Calculate a Maxwell-Stefan matrix-corrected film coefficient for one component.
   *
   * @param phase phase to inspect
   * @param component component name
   * @param baseCoefficient scalar packed-bed film coefficient
   * @param referenceDiffusivity reference effective diffusivity in m2/s
   * @param gasPhase true when the phase is gas
   * @return Maxwell-Stefan corrected film coefficient in 1/s
   */
  private double maxwellStefanFilmCoefficient(PhaseInterface phase, String component,
      double baseCoefficient, double referenceDiffusivity, boolean gasPhase) {
    if (!isFinitePositive(baseCoefficient)) {
      return 0.0;
    }
    int componentIndex = componentIndex(phase, component);
    int componentCount = phase.getNumberOfComponents();
    if (componentIndex < 0 || componentCount <= 2) {
      double diffusivity =
          mixtureDiffusivityForComponent(phase, componentIndex, referenceDiffusivity, gasPhase);
      return scaleFilmCoefficient(baseCoefficient, diffusivity, referenceDiffusivity);
    }
    int reducedDimension = componentCount - 1;
    if (componentIndex >= reducedDimension) {
      double diffusivity =
          mixtureDiffusivityForComponent(phase, componentIndex, referenceDiffusivity, gasPhase);
      return scaleFilmCoefficient(baseCoefficient, diffusivity, referenceDiffusivity);
    }
    try {
      Matrix resistanceMatrix = new Matrix(reducedDimension, reducedDimension);
      for (int row = 0; row < reducedDimension; row++) {
        double rowSum = 0.0;
        double referenceCoefficient = binaryFilmCoefficient(phase, row, reducedDimension,
            baseCoefficient, referenceDiffusivity, gasPhase);
        for (int column = 0; column < componentCount; column++) {
          double binaryCoefficient = binaryFilmCoefficient(phase, row, column, baseCoefficient,
              referenceDiffusivity, gasPhase);
          if (row != column) {
            rowSum += moleFraction(phase, column) / binaryCoefficient;
          }
          if (column < reducedDimension) {
            double value =
                -moleFraction(phase, row) * (1.0 / binaryCoefficient - 1.0 / referenceCoefficient);
            resistanceMatrix.set(row, column, value);
          }
        }
        resistanceMatrix.set(row, row, resistanceMatrix.get(row, row) + rowSum
            + moleFraction(phase, row) / referenceCoefficient);
      }
      Matrix coefficientMatrix = resistanceMatrix.inverse();
      double coefficient = coefficientMatrix.get(componentIndex, componentIndex);
      if (isFinitePositive(coefficient)) {
        return clamp(coefficient, baseCoefficient * 0.02, baseCoefficient * 50.0);
      }
    } catch (RuntimeException ex) {
      return scaleFilmCoefficient(baseCoefficient,
          mixtureDiffusivityForComponent(phase, componentIndex, referenceDiffusivity, gasPhase),
          referenceDiffusivity);
    }
    return baseCoefficient;
  }

  /**
   * Combine gas- and liquid-side volumetric flux densities with a rate-limiting harmonic mean.
   *
   * @param gasFluxDensity gas-side flux density in mol/(m3 s)
   * @param liquidFluxDensity liquid-side flux density in mol/(m3 s)
   * @return combined transfer density in mol/(m3 s)
   */
  private double combineFilmFluxes(double gasFluxDensity, double liquidFluxDensity) {
    if (!Double.isFinite(gasFluxDensity) || !Double.isFinite(liquidFluxDensity)) {
      return 0.0;
    }
    if (Math.abs(gasFluxDensity) < 1.0e-30 || Math.abs(liquidFluxDensity) < 1.0e-30) {
      return 0.0;
    }
    if (Math.signum(gasFluxDensity) != Math.signum(liquidFluxDensity)) {
      return 0.0;
    }
    double magnitude = 1.0 / (1.0 / Math.abs(gasFluxDensity) + 1.0 / Math.abs(liquidFluxDensity));
    return Math.signum(gasFluxDensity) * magnitude;
  }

  /**
   * Apply interphase heat transfer to gas and liquid segment systems.
   *
   * @param gas gas system to update
   * @param liquid liquid system to update
   * @param snapshot transport snapshot for the segment
   * @return heat-transfer rate in W, positive from gas to liquid
   */
  private double applyInterphaseHeatTransfer(SystemInterface gas, SystemInterface liquid,
      TransportSnapshot snapshot) {
    if (heatTransferModel == HeatTransferModel.NONE
        || !isFinitePositive(snapshot.overallHeatTransferCoefficient)) {
      return 0.0;
    }
    double temperatureDifference = gas.getTemperature() - liquid.getTemperature();
    if (Math.abs(temperatureDifference) < 1.0e-12) {
      return 0.0;
    }
    double gasHeatCapacityRate = heatCapacityRate(getGasPhase(gas), snapshot.gasHeatCapacity);
    double liquidHeatCapacityRate =
        heatCapacityRate(getLiquidPhase(liquid), snapshot.liquidHeatCapacity);
    if (!isFinitePositive(gasHeatCapacityRate) || !isFinitePositive(liquidHeatCapacityRate)) {
      return 0.0;
    }
    double segmentVolume =
        Math.PI * columnDiameter * columnDiameter / 4.0 * packedHeight / numberOfSegments;
    double heatRate =
        snapshot.overallHeatTransferCoefficient * segmentVolume * temperatureDifference;
    double maximumHeatRate = Math.min(gasHeatCapacityRate, liquidHeatCapacityRate)
        * Math.abs(temperatureDifference) * maxHeatTransferFractionPerSegment;
    heatRate = Math.signum(heatRate) * Math.min(Math.abs(heatRate), maximumHeatRate);
    if (Math.abs(heatRate) <= 0.0) {
      return 0.0;
    }
    gas.setTemperature(Math.max(1.0, gas.getTemperature() - heatRate / gasHeatCapacityRate));
    liquid
        .setTemperature(Math.max(1.0, liquid.getTemperature() + heatRate / liquidHeatCapacityRate));
    return heatRate;
  }

  /**
   * Calculate a volumetric heat-transfer coefficient from the Chilton-Colburn analogy.
   *
   * @param massTransferCoefficient volumetric mass-transfer coefficient in 1/s
   * @param density phase density in kg/m3
   * @param heatCapacity phase heat capacity in J/(kg K)
   * @param viscosity phase viscosity in kg/(m s)
   * @param diffusivity phase diffusivity in m2/s
   * @param thermalConductivity thermal conductivity in W/(m K)
   * @return volumetric heat-transfer coefficient in W/(m3 K)
   */
  private double calculateVolumetricHeatTransferCoefficient(double massTransferCoefficient,
      double density, double heatCapacity, double viscosity, double diffusivity,
      double thermalConductivity) {
    if (heatTransferModel == HeatTransferModel.NONE || !isFinitePositive(massTransferCoefficient)
        || !isFinitePositive(density) || !isFinitePositive(heatCapacity)
        || !isFinitePositive(viscosity) || !isFinitePositive(diffusivity)
        || !isFinitePositive(thermalConductivity)) {
      return 0.0;
    }
    double prandtlNumber = heatCapacity * viscosity / thermalConductivity;
    double schmidtNumber = viscosity / (density * diffusivity);
    if (!isFinitePositive(prandtlNumber) || !isFinitePositive(schmidtNumber)) {
      return 0.0;
    }
    double analogyFactor = Math.pow(schmidtNumber / prandtlNumber, 2.0 / 3.0);
    return massTransferCoefficient * density * heatCapacity * analogyFactor
        * heatTransferCorrectionFactor;
  }

  /**
   * Combine gas and liquid heat-transfer coefficients as series resistances.
   *
   * @param gasCoefficient gas-side volumetric heat-transfer coefficient in W/(m3 K)
   * @param liquidCoefficient liquid-side volumetric heat-transfer coefficient in W/(m3 K)
   * @return overall volumetric heat-transfer coefficient in W/(m3 K)
   */
  private double combineHeatTransferCoefficients(double gasCoefficient, double liquidCoefficient) {
    if (!isFinitePositive(gasCoefficient) || !isFinitePositive(liquidCoefficient)) {
      return 0.0;
    }
    return 1.0 / (1.0 / gasCoefficient + 1.0 / liquidCoefficient);
  }

  /**
   * Calculate an interfacial temperature estimate from heat-transfer resistances.
   *
   * @param gasTemperature gas bulk temperature in K
   * @param liquidTemperature liquid bulk temperature in K
   * @param gasCoefficient gas-side heat-transfer coefficient in W/(m3 K)
   * @param liquidCoefficient liquid-side heat-transfer coefficient in W/(m3 K)
   * @return estimated interface temperature in K
   */
  private double calculateInterfaceTemperature(double gasTemperature, double liquidTemperature,
      double gasCoefficient, double liquidCoefficient) {
    if (!isFinitePositive(gasCoefficient) || !isFinitePositive(liquidCoefficient)) {
      return 0.5 * (gasTemperature + liquidTemperature);
    }
    return (gasCoefficient * gasTemperature + liquidCoefficient * liquidTemperature)
        / (gasCoefficient + liquidCoefficient);
  }

  /**
   * Get heat capacity on a mass basis.
   *
   * @param phase phase to inspect
   * @param fallback fallback heat capacity in J/(kg K)
   * @return heat capacity in J/(kg K)
   */
  private double heatCapacityMass(PhaseInterface phase, double fallback) {
    try {
      double value = phase.getCp("J/kgK");
      return finitePositive(value, fallback);
    } catch (RuntimeException ex) {
      double moles = finitePositive(phase.getNumberOfMolesInPhase(), 1.0);
      double molarMass = finitePositive(phase.getMolarMass(), 0.020);
      return finitePositive(phase.getCp() / (moles * molarMass), fallback);
    }
  }

  /**
   * Get phase molar enthalpy.
   *
   * @param phase phase to inspect
   * @return molar enthalpy in J/mol
   */
  private double phaseMolarEnthalpy(PhaseInterface phase) {
    double moles = finitePositive(phase.getNumberOfMolesInPhase(), 1.0);
    return phase.getEnthalpy() / moles;
  }

  /**
   * Get component molar enthalpy at an interface temperature.
   *
   * @param phase phase to inspect
   * @param component component name
   * @param temperature temperature in kelvin
   * @param fallback fallback molar enthalpy in J/mol
   * @return component molar enthalpy in J/mol
   */
  private double componentMolarEnthalpy(PhaseInterface phase, String component, double temperature,
      double fallback) {
    if (phase == null || component == null || !phase.hasComponent(component)) {
      return fallback;
    }
    ComponentInterface componentObject = phase.getComponent(component);
    double componentMoles = componentObject.getNumberOfMolesInPhase();
    if (Math.abs(componentMoles) < 1.0e-30) {
      return fallback;
    }
    return componentObject.getEnthalpy(temperature) / componentMoles;
  }

  /**
   * Get phase thermal conductivity.
   *
   * @param phase phase to inspect
   * @param fallback fallback thermal conductivity in W/(m K)
   * @return thermal conductivity in W/(m K)
   */
  private double thermalConductivity(PhaseInterface phase, double fallback) {
    try {
      return finitePositive(phase.getThermalConductivity("W/mK"), fallback);
    } catch (RuntimeException ex) {
      return finitePositive(phase.getThermalConductivity(), fallback);
    }
  }

  /**
   * Calculate phase heat-capacity rate.
   *
   * @param phase phase to inspect
   * @param heatCapacity heat capacity in J/(kg K)
   * @return heat-capacity rate in W/K
   */
  private double heatCapacityRate(PhaseInterface phase, double heatCapacity) {
    return finitePositive(phase.getMass(), 0.0) * heatCapacity;
  }

  /**
   * Find a component index in a phase.
   *
   * @param phase phase to inspect
   * @param component component name
   * @return component index, or minus one if absent
   */
  private int componentIndex(PhaseInterface phase, String component) {
    if (phase == null || component == null) {
      return -1;
    }
    for (int i = 0; i < phase.getNumberOfComponents(); i++) {
      if (component.equals(phase.getComponent(i).getComponentName())) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Get a component mole fraction by component index.
   *
   * @param phase phase to inspect
   * @param componentIndex component index
   * @return mole fraction, or zero if unavailable
   */
  private double moleFraction(PhaseInterface phase, int componentIndex) {
    if (phase == null || componentIndex < 0 || componentIndex >= phase.getNumberOfComponents()) {
      return 0.0;
    }
    return Math.max(0.0, phase.getComponent(componentIndex).getx());
  }

  /**
   * Calculate a binary film coefficient by diffusivity scaling.
   *
   * @param phase phase to inspect
   * @param firstComponent first component index
   * @param secondComponent second component index
   * @param baseCoefficient scalar packed-bed film coefficient
   * @param referenceDiffusivity reference diffusivity in m2/s
   * @param gasPhase true for gas fallback diffusivity
   * @return binary film coefficient in 1/s
   */
  private double binaryFilmCoefficient(PhaseInterface phase, int firstComponent,
      int secondComponent, double baseCoefficient, double referenceDiffusivity, boolean gasPhase) {
    if (firstComponent == secondComponent) {
      return baseCoefficient;
    }
    return scaleFilmCoefficient(baseCoefficient,
        binaryDiffusivity(phase, firstComponent, secondComponent, referenceDiffusivity, gasPhase),
        referenceDiffusivity);
  }

  /**
   * Scale a film coefficient by diffusivity relative to a reference value.
   *
   * @param baseCoefficient scalar packed-bed film coefficient
   * @param diffusivity component or binary diffusivity in m2/s
   * @param referenceDiffusivity reference diffusivity in m2/s
   * @return scaled film coefficient in 1/s
   */
  private double scaleFilmCoefficient(double baseCoefficient, double diffusivity,
      double referenceDiffusivity) {
    double reference = finitePositive(referenceDiffusivity, diffusivity);
    double scaled = baseCoefficient * finitePositive(diffusivity, reference) / reference;
    return clamp(scaled, baseCoefficient * 0.02, baseCoefficient * 50.0);
  }

  /**
   * Calculate a mixture diffusivity for one component.
   *
   * @param phase phase to inspect
   * @param componentIndex component index
   * @param referenceDiffusivity reference diffusivity in m2/s
   * @param gasPhase true for gas fallback diffusivity
   * @return mixture diffusivity in m2/s
   */
  private double mixtureDiffusivityForComponent(PhaseInterface phase, int componentIndex,
      double referenceDiffusivity, boolean gasPhase) {
    if (componentIndex < 0 || phase.getNumberOfComponents() <= 1) {
      return referenceDiffusivity;
    }
    double resistance = 0.0;
    for (int i = 0; i < phase.getNumberOfComponents(); i++) {
      if (i != componentIndex) {
        double diffusivity =
            binaryDiffusivity(phase, componentIndex, i, referenceDiffusivity, gasPhase);
        resistance += moleFraction(phase, i) / diffusivity;
      }
    }
    if (isFinitePositive(resistance)) {
      return 1.0 / resistance;
    }
    return referenceDiffusivity;
  }

  /**
   * Get a binary diffusion coefficient from NeqSim physical properties.
   *
   * @param phase phase to inspect
   * @param firstComponent first component index
   * @param secondComponent second component index
   * @param referenceDiffusivity reference diffusivity in m2/s
   * @param gasPhase true for gas fallback diffusivity
   * @return binary diffusivity in m2/s
   */
  private double binaryDiffusivity(PhaseInterface phase, int firstComponent, int secondComponent,
      double referenceDiffusivity, boolean gasPhase) {
    try {
      double value =
          phase.getPhysicalProperties().getDiffusionCoefficient(firstComponent, secondComponent);
      if (isFinitePositive(value)) {
        return value;
      }
    } catch (RuntimeException ex) {
      // Fallback below uses effective component diffusivity or robust defaults.
    }
    try {
      double value = phase.getPhysicalProperties().getEffectiveDiffusionCoefficient(firstComponent);
      if (isFinitePositive(value)) {
        return value;
      }
    } catch (RuntimeException ex) {
      // Fallback below keeps Maxwell-Stefan correction robust for sparse property models.
    }
    return finitePositive(referenceDiffusivity,
        gasPhase ? DEFAULT_GAS_DIFFUSIVITY : DEFAULT_LIQUID_DIFFUSIVITY);
  }

  /**
   * Calculate residual between previous and current outlets.
   *
   * @param previousGas previous gas outlet, or null on the first iteration
   * @param currentGas current gas outlet
   * @param previousLiquid previous liquid outlet, or null on the first iteration
   * @param currentLiquid current liquid outlet
   * @return maximum absolute component residual in mol/s
   */
  private double calculateOutletResidual(SystemInterface previousGas, SystemInterface currentGas,
      SystemInterface previousLiquid, SystemInterface currentLiquid) {
    if (previousGas == null || previousLiquid == null) {
      return Double.POSITIVE_INFINITY;
    }
    double residual = 0.0;
    Set<String> components = new LinkedHashSet<String>();
    components.addAll(getComponentNames(currentGas));
    components.addAll(getComponentNames(currentLiquid));
    for (String component : components) {
      residual = Math.max(residual,
          Math.abs(componentMoles(previousGas, component) - componentMoles(currentGas, component)));
      residual = Math.max(residual, Math.abs(
          componentMoles(previousLiquid, component) - componentMoles(currentLiquid, component)));
    }
    return residual;
  }

  /**
   * Flash and initialize a thermodynamic system including physical properties.
   *
   * @param system thermodynamic system to initialize
   */
  private void flashAndInitialize(SystemInterface system) {
    try {
      ThermodynamicOperations operations = new ThermodynamicOperations(system);
      operations.TPflash();
    } catch (RuntimeException ex) {
      system.init(3);
    }
    system.initProperties();
  }

  /**
   * Get the gas phase from a system.
   *
   * @param system thermodynamic system
   * @return gas phase if present, otherwise phase zero
   */
  private PhaseInterface getGasPhase(SystemInterface system) {
    if (system.hasPhaseType(PhaseType.GAS)) {
      return system.getPhase(PhaseType.GAS);
    }
    return system.getPhase(0);
  }

  /**
   * Get the liquid phase from a system.
   *
   * @param system thermodynamic system
   * @return liquid-like phase if present, otherwise phase zero
   */
  private PhaseInterface getLiquidPhase(SystemInterface system) {
    if (system.hasPhaseType(PhaseType.AQUEOUS)) {
      return system.getPhase(PhaseType.AQUEOUS);
    }
    if (system.hasPhaseType(PhaseType.OIL)) {
      return system.getPhase(PhaseType.OIL);
    }
    if (system.hasPhaseType(PhaseType.LIQUID)) {
      return system.getPhase(PhaseType.LIQUID);
    }
    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      if (system.getPhase(phase).getType() != PhaseType.GAS) {
        return system.getPhase(phase);
      }
    }
    return system.getPhase(0);
  }

  /**
   * Estimate average diffusivity for a phase.
   *
   * @param phase phase to inspect
   * @param gasPhase true for gas fallback values, false for liquid fallback values
   * @return finite diffusivity in m2/s
   */
  private double averageDiffusivity(PhaseInterface phase, boolean gasPhase) {
    double sum = 0.0;
    int count = 0;
    PhysicalProperties properties = phase.getPhysicalProperties();
    for (int component = 0; component < phase.getNumberOfComponents(); component++) {
      try {
        double value = properties.getEffectiveDiffusionCoefficient(component);
        if (isFinitePositive(value)) {
          sum += value;
          count++;
        }
      } catch (RuntimeException ex) {
        // Fallback below keeps the column robust when a diffusion model is unavailable.
      }
    }
    if (count > 0) {
      return sum / count;
    }
    return gasPhase ? DEFAULT_GAS_DIFFUSIVITY : DEFAULT_LIQUID_DIFFUSIVITY;
  }

  /**
   * Estimate surface tension between the segment gas and liquid phases.
   *
   * @param gas gas system
   * @param liquid liquid system
   * @return surface tension in N/m
   */
  private double estimateSurfaceTension(SystemInterface gas, SystemInterface liquid) {
    SystemInterface mixed = gas.clone();
    addSystemComponents(mixed, liquid);
    flashAndInitialize(mixed);
    try {
      if (mixed.hasPhaseType(PhaseType.GAS) && mixed.getNumberOfPhases() > 1) {
        int gasPhaseNumber = mixed.getPhaseNumberOfPhase(PhaseType.GAS);
        int liquidPhaseNumber = getLiquidPhaseNumber(mixed);
        if (liquidPhaseNumber >= 0 && liquidPhaseNumber != gasPhaseNumber) {
          double value =
              mixed.getInterphaseProperties().getSurfaceTension(gasPhaseNumber, liquidPhaseNumber);
          if (isFinitePositive(value)) {
            return value;
          }
        }
      }
    } catch (RuntimeException ex) {
      return DEFAULT_SURFACE_TENSION;
    }
    return DEFAULT_SURFACE_TENSION;
  }

  /**
   * Add all positive-mole components from a source system to a target system.
   *
   * @param target target system receiving component moles
   * @param source source system providing component moles
   */
  private void addSystemComponents(SystemInterface target, SystemInterface source) {
    boolean addedComponent = false;
    List<String> components = getComponentNames(source);
    for (int i = 0; i < components.size(); i++) {
      String component = components.get(i);
      double moles = componentMoles(source, component);
      if (moles > 0.0) {
        target.addComponent(component, moles);
        addedComponent = true;
      }
    }
    if (addedComponent) {
      refreshComponentDatabase(target);
    }
  }

  /**
   * Refresh database and mixing-rule matrices after adding components to a cloned system.
   *
   * @param system thermodynamic system to refresh
   */
  private void refreshComponentDatabase(SystemInterface system) {
    String mixingRuleName = system.getMixingRuleName();
    try {
      system.createDatabase(true);
      if (mixingRuleName != null && !mixingRuleName.trim().isEmpty()) {
        system.setMixingRule(mixingRuleName);
      }
    } catch (RuntimeException ex) {
      if (mixingRuleName == null || mixingRuleName.trim().isEmpty()) {
        system.setMixingRule("classic");
      }
    }
  }

  /**
   * Get the first liquid-like phase number in a system.
   *
   * @param system thermodynamic system
   * @return liquid phase number, or minus one if no liquid-like phase exists
   */
  private int getLiquidPhaseNumber(SystemInterface system) {
    if (system.hasPhaseType(PhaseType.AQUEOUS)) {
      return system.getPhaseNumberOfPhase(PhaseType.AQUEOUS);
    }
    if (system.hasPhaseType(PhaseType.OIL)) {
      return system.getPhaseNumberOfPhase(PhaseType.OIL);
    }
    if (system.hasPhaseType(PhaseType.LIQUID)) {
      return system.getPhaseNumberOfPhase(PhaseType.LIQUID);
    }
    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      if (system.getPhase(phase).getType() != PhaseType.GAS) {
        return phase;
      }
    }
    return -1;
  }

  /**
   * Calculate phase molar concentration.
   *
   * @param phase phase to inspect
   * @return molar concentration in mol/m3
   */
  private double molarConcentration(PhaseInterface phase) {
    double molarMass = finitePositive(phase.getMolarMass(), 0.020);
    double density = finitePositive(phase.getDensity("kg/m3"), 1.0);
    return density / molarMass;
  }

  /**
   * Get a component mole fraction in a phase.
   *
   * @param phase phase to inspect
   * @param component component name
   * @return mole fraction, or zero if the component is absent
   */
  private double moleFraction(PhaseInterface phase, String component) {
    if (phase == null || component == null || !phase.hasComponent(component)) {
      return 0.0;
    }
    return Math.max(0.0, phase.getComponent(component).getx());
  }

  /**
   * Get total component moles in a system.
   *
   * @param system thermodynamic system
   * @param component component name
   * @return component moles in the system
   */
  private double componentMoles(SystemInterface system, String component) {
    if (system == null || component == null || !system.getPhase(0).hasComponent(component)) {
      return 0.0;
    }
    return Math.max(0.0, system.getPhase(0).getComponent(component).getNumberOfmoles());
  }

  /**
   * Get component names from a thermodynamic system.
   *
   * @param system thermodynamic system
   * @return component names in insertion order
   */
  private List<String> getComponentNames(SystemInterface system) {
    List<String> names = new ArrayList<String>();
    if (system == null || system.getNumberOfPhases() == 0) {
      return names;
    }
    PhaseInterface phase = system.getPhase(0);
    for (int i = 0; i < phase.getNumberOfComponents(); i++) {
      ComponentInterface component = phase.getComponent(i);
      names.add(component.getComponentName());
    }
    return names;
  }

  /**
   * Get the active transfer component list.
   *
   * @param gas gas system
   * @param liquid liquid system
   * @return active component names
   */
  private List<String> getTransferComponentList(SystemInterface gas, SystemInterface liquid) {
    if (!transferComponents.isEmpty()) {
      return new ArrayList<String>(transferComponents);
    }
    Set<String> components = new LinkedHashSet<String>();
    components.addAll(getComponentNames(gas));
    components.addAll(getComponentNames(liquid));
    return new ArrayList<String>(components);
  }

  /**
   * Validate a positive finite value.
   *
   * @param value numeric value
   * @param name field name for error messages
   * @throws IllegalArgumentException if the value is not positive and finite
   */
  private void validatePositive(double value, String name) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(name + " must be positive");
    }
  }

  /**
   * Validate a non-negative finite value.
   *
   * @param value numeric value
   * @param name field name for error messages
   * @throws IllegalArgumentException if the value is negative or not finite
   */
  private void validateNonNegative(double value, String name) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(name + " must be non-negative");
    }
  }

  /**
   * Check whether a double is positive and finite.
   *
   * @param value value to check
   * @return true if the value is finite and positive
   */
  private boolean isFinitePositive(double value) {
    return Double.isFinite(value) && value > 0.0;
  }

  /**
   * Replace invalid values with a fallback.
   *
   * @param value value to check
   * @param fallback fallback value
   * @return value if positive and finite, otherwise fallback
   */
  private double finitePositive(double value, double fallback) {
    return isFinitePositive(value) ? value : fallback;
  }

  /**
   * Replace invalid or negative values with a fallback.
   *
   * @param value value to check
   * @param fallback fallback value
   * @return value if non-negative and finite, otherwise fallback
   */
  private double finiteNonNegative(double value, double fallback) {
    return Double.isFinite(value) && value >= 0.0 ? value : fallback;
  }

  /**
   * Clamp a value between lower and upper bounds.
   *
   * @param value value to clamp
   * @param min lower bound
   * @param max upper bound
   * @return clamped value
   */
  private double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }

  /**
   * Segment result data for a rate-based packed column.
   */
  public static class SegmentResult implements java.io.Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;

    /** Segment number from bottom to top. */
    private final int segmentNumber;

    /** Segment midpoint height from bottom in metres. */
    private final double heightFromBottom;

    /** Gas temperature in kelvin. */
    private final double gasTemperatureK;

    /** Liquid temperature in kelvin. */
    private final double liquidTemperatureK;

    /** Gas pressure in bara. */
    private final double gasPressureBar;

    /** Liquid pressure in bara. */
    private final double liquidPressureBar;

    /** Gas molar flow in mol/s. */
    private final double gasMolarFlow;

    /** Liquid molar flow in mol/s. */
    private final double liquidMolarFlow;

    /** Gas density in kg/m3. */
    private final double gasDensity;

    /** Liquid density in kg/m3. */
    private final double liquidDensity;

    /** Gas viscosity in kg/(m s). */
    private final double gasViscosity;

    /** Liquid viscosity in kg/(m s). */
    private final double liquidViscosity;

    /** Gas diffusivity in m2/s. */
    private final double gasDiffusivity;

    /** Liquid diffusivity in m2/s. */
    private final double liquidDiffusivity;

    /** Wetted area in m2/m3. */
    private final double wettedArea;

    /** Gas-phase volumetric mass-transfer coefficient in 1/s. */
    private final double kGa;

    /** Liquid-phase volumetric mass-transfer coefficient in 1/s. */
    private final double kLa;

    /** Gas-side volumetric heat-transfer coefficient in W/(m3 K). */
    private final double gasHeatTransferCoefficient;

    /** Liquid-side volumetric heat-transfer coefficient in W/(m3 K). */
    private final double liquidHeatTransferCoefficient;

    /** Overall volumetric interphase heat-transfer coefficient in W/(m3 K). */
    private final double overallHeatTransferCoefficient;

    /** Interface equilibrium temperature in kelvin. */
    private final double interfaceTemperatureK;

    /** Interphase heat-transfer rate in W, positive from gas to liquid. */
    private final double heatTransferRateW;

    /** Pressure drop per metre in Pa/m. */
    private final double pressureDropPerMeter;

    /** Percent of flooding. */
    private final double percentFlood;

    /** Net molar transfer in mol/s, positive for gas-to-liquid. */
    private final double netMolarTransfer;

    /** Component transfer map in mol/s, positive for gas-to-liquid. */
    private final Map<String, Double> componentMoleTransfer;

    /** Gas-side interface equilibrium mole fractions by component. */
    private final Map<String, Double> interfaceGasMoleFractions;

    /** Liquid-side interface equilibrium mole fractions by component. */
    private final Map<String, Double> interfaceLiquidMoleFractions;

    /** Interface gas-to-liquid equilibrium ratios by component. */
    private final Map<String, Double> interfaceEquilibriumRatios;

    /** Segment solver used to calculate this result. */
    private final String segmentSolver;

    /** Iterations used by the simultaneous residual solver. */
    private final int residualIterations;

    /** Maximum component flux residual in mol/s. */
    private final double maxFluxResidualMolPerSec;

    /** Interfacial heat-balance residual in W. */
    private final double heatBalanceResidualW;

    /** Total outlet enthalpy-balance residual in W-equivalent stream basis. */
    private final double enthalpyBalanceResidualW;

    /**
     * Create a segment result.
     *
     * @param segmentNumber segment number from bottom to top
     * @param heightFromBottom segment midpoint height in metres
     * @param gasTemperatureK gas temperature in kelvin
     * @param liquidTemperatureK liquid temperature in kelvin
     * @param gasPressureBar gas pressure in bara
     * @param liquidPressureBar liquid pressure in bara
     * @param gasMolarFlow gas molar flow in mol/s
     * @param liquidMolarFlow liquid molar flow in mol/s
     * @param gasDensity gas density in kg/m3
     * @param liquidDensity liquid density in kg/m3
     * @param gasViscosity gas viscosity in kg/(m s)
     * @param liquidViscosity liquid viscosity in kg/(m s)
     * @param gasDiffusivity gas diffusivity in m2/s
     * @param liquidDiffusivity liquid diffusivity in m2/s
     * @param wettedArea wetted area in m2/m3
     * @param kGa gas-phase volumetric mass-transfer coefficient in 1/s
     * @param kLa liquid-phase volumetric mass-transfer coefficient in 1/s
     * @param gasHeatTransferCoefficient gas-side heat-transfer coefficient in W/(m3 K)
     * @param liquidHeatTransferCoefficient liquid-side heat-transfer coefficient in W/(m3 K)
     * @param overallHeatTransferCoefficient overall heat-transfer coefficient in W/(m3 K)
     * @param interfaceTemperatureK interface equilibrium temperature in K
     * @param heatTransferRateW heat-transfer rate in W, positive from gas to liquid
     * @param pressureDropPerMeter pressure drop per metre in Pa/m
     * @param percentFlood percent flooding
     * @param netMolarTransfer net molar transfer in mol/s
     * @param componentMoleTransfer component transfer map in mol/s
     * @param interfaceGasMoleFractions gas-side interface mole fractions by component
     * @param interfaceLiquidMoleFractions liquid-side interface mole fractions by component
     * @param interfaceEquilibriumRatios interface equilibrium ratios by component
     * @param segmentSolver segment solver name
     * @param residualIterations residual solver iterations
     * @param maxFluxResidualMolPerSec maximum component flux residual in mol/s
     * @param heatBalanceResidualW interfacial heat-balance residual in W
     * @param enthalpyBalanceResidualW total outlet enthalpy-balance residual in W-equivalent stream
     *        basis
     */
    public SegmentResult(int segmentNumber, double heightFromBottom, double gasTemperatureK,
        double liquidTemperatureK, double gasPressureBar, double liquidPressureBar,
        double gasMolarFlow, double liquidMolarFlow, double gasDensity, double liquidDensity,
        double gasViscosity, double liquidViscosity, double gasDiffusivity,
        double liquidDiffusivity, double wettedArea, double kGa, double kLa,
        double gasHeatTransferCoefficient, double liquidHeatTransferCoefficient,
        double overallHeatTransferCoefficient, double interfaceTemperatureK,
        double heatTransferRateW, double pressureDropPerMeter, double percentFlood,
        double netMolarTransfer, Map<String, Double> componentMoleTransfer,
        Map<String, Double> interfaceGasMoleFractions,
        Map<String, Double> interfaceLiquidMoleFractions,
        Map<String, Double> interfaceEquilibriumRatios, String segmentSolver,
        int residualIterations, double maxFluxResidualMolPerSec, double heatBalanceResidualW,
        double enthalpyBalanceResidualW) {
      this.segmentNumber = segmentNumber;
      this.heightFromBottom = heightFromBottom;
      this.gasTemperatureK = gasTemperatureK;
      this.liquidTemperatureK = liquidTemperatureK;
      this.gasPressureBar = gasPressureBar;
      this.liquidPressureBar = liquidPressureBar;
      this.gasMolarFlow = gasMolarFlow;
      this.liquidMolarFlow = liquidMolarFlow;
      this.gasDensity = gasDensity;
      this.liquidDensity = liquidDensity;
      this.gasViscosity = gasViscosity;
      this.liquidViscosity = liquidViscosity;
      this.gasDiffusivity = gasDiffusivity;
      this.liquidDiffusivity = liquidDiffusivity;
      this.wettedArea = wettedArea;
      this.kGa = kGa;
      this.kLa = kLa;
      this.gasHeatTransferCoefficient = gasHeatTransferCoefficient;
      this.liquidHeatTransferCoefficient = liquidHeatTransferCoefficient;
      this.overallHeatTransferCoefficient = overallHeatTransferCoefficient;
      this.interfaceTemperatureK = interfaceTemperatureK;
      this.heatTransferRateW = heatTransferRateW;
      this.pressureDropPerMeter = pressureDropPerMeter;
      this.percentFlood = percentFlood;
      this.netMolarTransfer = netMolarTransfer;
      this.componentMoleTransfer = new LinkedHashMap<String, Double>(componentMoleTransfer);
      this.interfaceGasMoleFractions = new LinkedHashMap<String, Double>(interfaceGasMoleFractions);
      this.interfaceLiquidMoleFractions =
          new LinkedHashMap<String, Double>(interfaceLiquidMoleFractions);
      this.interfaceEquilibriumRatios =
          new LinkedHashMap<String, Double>(interfaceEquilibriumRatios);
      this.segmentSolver = segmentSolver;
      this.residualIterations = residualIterations;
      this.maxFluxResidualMolPerSec = maxFluxResidualMolPerSec;
      this.heatBalanceResidualW = heatBalanceResidualW;
      this.enthalpyBalanceResidualW = enthalpyBalanceResidualW;
    }

    /**
     * Get segment number.
     *
     * @return segment number from bottom to top
     */
    public int getSegmentNumber() {
      return segmentNumber;
    }

    /**
     * Get segment midpoint height.
     *
     * @return height from bottom in metres
     */
    public double getHeightFromBottom() {
      return heightFromBottom;
    }

    /**
     * Get gas temperature.
     *
     * @return gas temperature in kelvin
     */
    public double getGasTemperatureK() {
      return gasTemperatureK;
    }

    /**
     * Get liquid temperature.
     *
     * @return liquid temperature in kelvin
     */
    public double getLiquidTemperatureK() {
      return liquidTemperatureK;
    }

    /**
     * Get gas pressure.
     *
     * @return gas pressure in bara
     */
    public double getGasPressureBar() {
      return gasPressureBar;
    }

    /**
     * Get liquid pressure.
     *
     * @return liquid pressure in bara
     */
    public double getLiquidPressureBar() {
      return liquidPressureBar;
    }

    /**
     * Get gas molar flow.
     *
     * @return gas molar flow in mol/s
     */
    public double getGasMolarFlow() {
      return gasMolarFlow;
    }

    /**
     * Get liquid molar flow.
     *
     * @return liquid molar flow in mol/s
     */
    public double getLiquidMolarFlow() {
      return liquidMolarFlow;
    }

    /**
     * Get gas density.
     *
     * @return gas density in kg/m3
     */
    public double getGasDensity() {
      return gasDensity;
    }

    /**
     * Get liquid density.
     *
     * @return liquid density in kg/m3
     */
    public double getLiquidDensity() {
      return liquidDensity;
    }

    /**
     * Get gas viscosity.
     *
     * @return gas viscosity in kg/(m s)
     */
    public double getGasViscosity() {
      return gasViscosity;
    }

    /**
     * Get liquid viscosity.
     *
     * @return liquid viscosity in kg/(m s)
     */
    public double getLiquidViscosity() {
      return liquidViscosity;
    }

    /**
     * Get gas diffusivity.
     *
     * @return gas diffusivity in m2/s
     */
    public double getGasDiffusivity() {
      return gasDiffusivity;
    }

    /**
     * Get liquid diffusivity.
     *
     * @return liquid diffusivity in m2/s
     */
    public double getLiquidDiffusivity() {
      return liquidDiffusivity;
    }

    /**
     * Get wetted area.
     *
     * @return wetted area in m2/m3
     */
    public double getWettedArea() {
      return wettedArea;
    }

    /**
     * Get gas-side mass-transfer coefficient.
     *
     * @return kG a in 1/s
     */
    public double getKGa() {
      return kGa;
    }

    /**
     * Get liquid-side mass-transfer coefficient.
     *
     * @return kL a in 1/s
     */
    public double getKLa() {
      return kLa;
    }

    /**
     * Get gas-side heat-transfer coefficient.
     *
     * @return gas-side volumetric heat-transfer coefficient in W/(m3 K)
     */
    public double getGasHeatTransferCoefficient() {
      return gasHeatTransferCoefficient;
    }

    /**
     * Get liquid-side heat-transfer coefficient.
     *
     * @return liquid-side volumetric heat-transfer coefficient in W/(m3 K)
     */
    public double getLiquidHeatTransferCoefficient() {
      return liquidHeatTransferCoefficient;
    }

    /**
     * Get overall interphase heat-transfer coefficient.
     *
     * @return overall volumetric heat-transfer coefficient in W/(m3 K)
     */
    public double getOverallHeatTransferCoefficient() {
      return overallHeatTransferCoefficient;
    }

    /**
     * Get interface equilibrium temperature.
     *
     * @return interface temperature in kelvin
     */
    public double getInterfaceTemperatureK() {
      return interfaceTemperatureK;
    }

    /**
     * Get interphase heat-transfer rate.
     *
     * @return heat-transfer rate in W, positive from gas to liquid
     */
    public double getHeatTransferRateW() {
      return heatTransferRateW;
    }

    /**
     * Get pressure drop per metre.
     *
     * @return pressure drop in Pa/m
     */
    public double getPressureDropPerMeter() {
      return pressureDropPerMeter;
    }

    /**
     * Get percent flooding.
     *
     * @return percent flooding
     */
    public double getPercentFlood() {
      return percentFlood;
    }

    /**
     * Get net molar transfer.
     *
     * @return net molar transfer in mol/s, positive from gas to liquid
     */
    public double getNetMolarTransfer() {
      return netMolarTransfer;
    }

    /**
     * Get component transfer data.
     *
     * @return component transfer map in mol/s, positive from gas to liquid
     */
    public Map<String, Double> getComponentMoleTransfer() {
      return Collections.unmodifiableMap(componentMoleTransfer);
    }

    /**
     * Get gas-side interface mole fractions.
     *
     * @return gas-side interface mole fractions by component
     */
    public Map<String, Double> getInterfaceGasMoleFractions() {
      return Collections.unmodifiableMap(interfaceGasMoleFractions);
    }

    /**
     * Get liquid-side interface mole fractions.
     *
     * @return liquid-side interface mole fractions by component
     */
    public Map<String, Double> getInterfaceLiquidMoleFractions() {
      return Collections.unmodifiableMap(interfaceLiquidMoleFractions);
    }

    /**
     * Get interface equilibrium ratios.
     *
     * @return gas-to-liquid equilibrium ratios by component
     */
    public Map<String, Double> getInterfaceEquilibriumRatios() {
      return Collections.unmodifiableMap(interfaceEquilibriumRatios);
    }

    /**
     * Get the segment solver used for this result.
     *
     * @return segment solver name
     */
    public String getSegmentSolver() {
      return segmentSolver;
    }

    /**
     * Get residual solver iteration count.
     *
     * @return residual solver iterations
     */
    public int getResidualIterations() {
      return residualIterations;
    }

    /**
     * Get maximum Maxwell-Stefan flux residual.
     *
     * @return maximum component flux residual in mol/s
     */
    public double getMaxFluxResidualMolPerSec() {
      return maxFluxResidualMolPerSec;
    }

    /**
     * Get interfacial heat-balance residual.
     *
     * @return heat-balance residual in W
     */
    public double getHeatBalanceResidualW() {
      return heatBalanceResidualW;
    }

    /**
     * Get total enthalpy-balance residual across gas and liquid outlets.
     *
     * @return enthalpy-balance residual in W-equivalent stream basis
     */
    public double getEnthalpyBalanceResidualW() {
      return enthalpyBalanceResidualW;
    }
  }

  /** Internal segment computation container. */
  private static class SegmentComputation {
    /** Segment gas outlet system. */
    private final SystemInterface gasOutlet;
    /** Segment liquid outlet system. */
    private final SystemInterface liquidOutlet;
    /** Segment result. */
    private final SegmentResult result;

    /**
     * Create a segment computation container.
     *
     * @param gasOutlet segment gas outlet system
     * @param liquidOutlet segment liquid outlet system
     * @param result segment result
     */
    private SegmentComputation(SystemInterface gasOutlet, SystemInterface liquidOutlet,
        SegmentResult result) {
      this.gasOutlet = gasOutlet;
      this.liquidOutlet = liquidOutlet;
      this.result = result;
    }
  }

  /** Internal counter-current solution container. */
  private static class CounterCurrentSolution {
    /** Column gas outlet system. */
    private final SystemInterface gasOutlet;
    /** Column liquid outlet system. */
    private final SystemInterface liquidOutlet;
    /** Liquid systems leaving each segment. */
    private final List<SystemInterface> liquidLeavingSegments;
    /** Segment profile results. */
    private final List<SegmentResult> segmentResults;

    /**
     * Create a counter-current solution.
     *
     * @param gasOutlet column gas outlet system
     * @param liquidOutlet column liquid outlet system
     * @param liquidLeavingSegments liquid systems leaving each segment
     * @param segmentResults segment profile results
     */
    private CounterCurrentSolution(SystemInterface gasOutlet, SystemInterface liquidOutlet,
        List<SystemInterface> liquidLeavingSegments, List<SegmentResult> segmentResults) {
      this.gasOutlet = gasOutlet;
      this.liquidOutlet = liquidOutlet;
      this.liquidLeavingSegments = liquidLeavingSegments;
      this.segmentResults = segmentResults;
    }
  }

  /** Internal transport-property snapshot. */
  private static class TransportSnapshot {
    /** Gas density in kg/m3. */
    private final double gasDensity;
    /** Liquid density in kg/m3. */
    private final double liquidDensity;
    /** Gas viscosity in kg/(m s). */
    private final double gasViscosity;
    /** Liquid viscosity in kg/(m s). */
    private final double liquidViscosity;
    /** Gas diffusivity in m2/s. */
    private final double gasDiffusivity;
    /** Liquid diffusivity in m2/s. */
    private final double liquidDiffusivity;
    /** Wetted area in m2/m3. */
    private final double wettedArea;
    /** Gas-phase volumetric mass-transfer coefficient in 1/s. */
    private final double kGa;
    /** Liquid-phase volumetric mass-transfer coefficient in 1/s. */
    private final double kLa;
    /** Gas heat capacity in J/(kg K). */
    private final double gasHeatCapacity;
    /** Liquid heat capacity in J/(kg K). */
    private final double liquidHeatCapacity;
    /** Gas-side volumetric heat-transfer coefficient in W/(m3 K). */
    private final double gasHeatTransferCoefficient;
    /** Liquid-side volumetric heat-transfer coefficient in W/(m3 K). */
    private final double liquidHeatTransferCoefficient;
    /** Overall interphase heat-transfer coefficient in W/(m3 K). */
    private final double overallHeatTransferCoefficient;
    /** Estimated interface equilibrium temperature in K. */
    private final double interfaceTemperatureK;
    /** Pressure drop per metre in Pa/m. */
    private final double pressureDropPerMeter;
    /** Percent of flooding. */
    private final double percentFlood;

    /**
     * Create a transport snapshot.
     *
     * @param gasDensity gas density in kg/m3
     * @param liquidDensity liquid density in kg/m3
     * @param gasViscosity gas viscosity in kg/(m s)
     * @param liquidViscosity liquid viscosity in kg/(m s)
     * @param gasDiffusivity gas diffusivity in m2/s
     * @param liquidDiffusivity liquid diffusivity in m2/s
     * @param wettedArea wetted area in m2/m3
     * @param kGa gas-phase volumetric mass-transfer coefficient in 1/s
     * @param kLa liquid-phase volumetric mass-transfer coefficient in 1/s
     * @param gasHeatCapacity gas heat capacity in J/(kg K)
     * @param liquidHeatCapacity liquid heat capacity in J/(kg K)
     * @param gasHeatTransferCoefficient gas-side heat-transfer coefficient in W/(m3 K)
     * @param liquidHeatTransferCoefficient liquid-side heat-transfer coefficient in W/(m3 K)
     * @param overallHeatTransferCoefficient overall heat-transfer coefficient in W/(m3 K)
     * @param interfaceTemperatureK estimated interface equilibrium temperature in K
     * @param pressureDropPerMeter pressure drop per metre in Pa/m
     * @param percentFlood percent flooding
     */
    private TransportSnapshot(double gasDensity, double liquidDensity, double gasViscosity,
        double liquidViscosity, double gasDiffusivity, double liquidDiffusivity, double wettedArea,
        double kGa, double kLa, double gasHeatCapacity, double liquidHeatCapacity,
        double gasHeatTransferCoefficient, double liquidHeatTransferCoefficient,
        double overallHeatTransferCoefficient, double interfaceTemperatureK,
        double pressureDropPerMeter, double percentFlood) {
      this.gasDensity = gasDensity;
      this.liquidDensity = liquidDensity;
      this.gasViscosity = gasViscosity;
      this.liquidViscosity = liquidViscosity;
      this.gasDiffusivity = Math.max(gasDiffusivity, MIN_GAS_DIFFUSIVITY);
      this.liquidDiffusivity = Math.max(liquidDiffusivity, MIN_LIQUID_DIFFUSIVITY);
      this.wettedArea = wettedArea;
      this.kGa = kGa;
      this.kLa = kLa;
      this.gasHeatCapacity = gasHeatCapacity;
      this.liquidHeatCapacity = liquidHeatCapacity;
      this.gasHeatTransferCoefficient = gasHeatTransferCoefficient;
      this.liquidHeatTransferCoefficient = liquidHeatTransferCoefficient;
      this.overallHeatTransferCoefficient = overallHeatTransferCoefficient;
      this.interfaceTemperatureK = interfaceTemperatureK;
      this.pressureDropPerMeter = pressureDropPerMeter;
      this.percentFlood = percentFlood;
    }
  }

  /** Interface equilibrium data for one segment. */
  private static class InterfaceEquilibrium {
    /** Interface equilibrium temperature in kelvin. */
    private final double interfaceTemperatureK;
    /** Gas-side interface mole fractions. */
    private final Map<String, Double> gasMoleFractions;
    /** Liquid-side interface mole fractions. */
    private final Map<String, Double> liquidMoleFractions;
    /** Gas-to-liquid equilibrium ratios. */
    private final Map<String, Double> equilibriumRatios;
    /** Gas-side interface component molar enthalpies. */
    private final Map<String, Double> gasMolarEnthalpies;
    /** Liquid-side interface component molar enthalpies. */
    private final Map<String, Double> liquidMolarEnthalpies;

    /**
     * Create interface equilibrium data.
     *
     * @param interfaceTemperatureK interface equilibrium temperature in K
     * @param gasMoleFractions gas-side mole fractions by component
     * @param liquidMoleFractions liquid-side mole fractions by component
     * @param equilibriumRatios gas-to-liquid equilibrium ratios by component
     * @param gasMolarEnthalpies gas-side component molar enthalpies by component
     * @param liquidMolarEnthalpies liquid-side component molar enthalpies by component
     */
    private InterfaceEquilibrium(double interfaceTemperatureK, Map<String, Double> gasMoleFractions,
        Map<String, Double> liquidMoleFractions, Map<String, Double> equilibriumRatios,
        Map<String, Double> gasMolarEnthalpies, Map<String, Double> liquidMolarEnthalpies) {
      this.interfaceTemperatureK = interfaceTemperatureK;
      this.gasMoleFractions = new LinkedHashMap<String, Double>(gasMoleFractions);
      this.liquidMoleFractions = new LinkedHashMap<String, Double>(liquidMoleFractions);
      this.equilibriumRatios = new LinkedHashMap<String, Double>(equilibriumRatios);
      this.gasMolarEnthalpies = new LinkedHashMap<String, Double>(gasMolarEnthalpies);
      this.liquidMolarEnthalpies = new LinkedHashMap<String, Double>(liquidMolarEnthalpies);
    }

    /**
     * Get a gas-side interface mole fraction.
     *
     * @param component component name
     * @param fallback fallback value
     * @return gas-side interface mole fraction
     */
    private double getGasMoleFraction(String component, double fallback) {
      Double value = gasMoleFractions.get(component);
      return value == null ? fallback : value.doubleValue();
    }

    /**
     * Get a liquid-side interface mole fraction.
     *
     * @param component component name
     * @param fallback fallback value
     * @return liquid-side interface mole fraction
     */
    private double getLiquidMoleFraction(String component, double fallback) {
      Double value = liquidMoleFractions.get(component);
      return value == null ? fallback : value.doubleValue();
    }

    /**
     * Get a gas-to-liquid equilibrium ratio.
     *
     * @param component component name
     * @return gas-to-liquid equilibrium ratio
     */
    private double getEquilibriumRatio(String component) {
      Double value = equilibriumRatios.get(component);
      return value == null ? 1.0 : value.doubleValue();
    }

    /**
     * Get gas-side component molar enthalpy at the interface.
     *
     * @param component component name
     * @return gas-side molar enthalpy in J/mol
     */
    private double getGasMolarEnthalpy(String component) {
      Double value = gasMolarEnthalpies.get(component);
      return value == null ? 0.0 : value.doubleValue();
    }

    /**
     * Get liquid-side component molar enthalpy at the interface.
     *
     * @param component component name
     * @return liquid-side molar enthalpy in J/mol
     */
    private double getLiquidMolarEnthalpy(String component) {
      Double value = liquidMolarEnthalpies.get(component);
      return value == null ? 0.0 : value.doubleValue();
    }
  }

  /** Internal column state reconstructed from equation-oriented unknowns. */
  private static class ColumnState {
    /** Gas entering each segment from bottom to top. */
    private final List<SystemInterface> gasEntering;
    /** Gas leaving each segment from bottom to top. */
    private final List<SystemInterface> gasLeaving;
    /** Liquid entering each segment from bottom to top. */
    private final List<SystemInterface> liquidEntering;
    /** Liquid leaving each segment from bottom to top. */
    private final List<SystemInterface> liquidLeaving;
    /** Column gas outlet. */
    private final SystemInterface gasOutlet;
    /** Column liquid outlet. */
    private final SystemInterface liquidOutlet;

    /**
     * Create a reconstructed column state.
     *
     * @param gasEntering gas systems entering each segment
     * @param gasLeaving gas systems leaving each segment
     * @param liquidEntering liquid systems entering each segment
     * @param liquidLeaving liquid systems leaving each segment
     * @param gasOutlet column gas outlet system
     * @param liquidOutlet column liquid outlet system
     */
    private ColumnState(List<SystemInterface> gasEntering, List<SystemInterface> gasLeaving,
        List<SystemInterface> liquidEntering, List<SystemInterface> liquidLeaving,
        SystemInterface gasOutlet, SystemInterface liquidOutlet) {
      this.gasEntering = gasEntering;
      this.gasLeaving = gasLeaving;
      this.liquidEntering = liquidEntering;
      this.liquidLeaving = liquidLeaving;
      this.gasOutlet = gasOutlet;
      this.liquidOutlet = liquidOutlet;
    }
  }

  /** Internal sparse Jacobian assembled by the equation-oriented column solver. */
  private static class SparseJacobian {
    /** Row count. */
    private final int rows;
    /** Column count. */
    private final int columns;
    /** Sparse matrix values keyed by row then column. */
    private final Map<Integer, Map<Integer, Double>> values =
        new LinkedHashMap<Integer, Map<Integer, Double>>();

    /**
     * Create a sparse Jacobian.
     *
     * @param rows number of residual rows
     * @param columns number of unknown columns
     */
    private SparseJacobian(int rows, int columns) {
      this.rows = rows;
      this.columns = columns;
    }

    /**
     * Set a sparse matrix entry.
     *
     * @param row row index
     * @param column column index
     * @param value matrix value
     */
    private void set(int row, int column, double value) {
      Map<Integer, Double> rowValues = values.get(Integer.valueOf(row));
      if (rowValues == null) {
        rowValues = new LinkedHashMap<Integer, Double>();
        values.put(Integer.valueOf(row), rowValues);
      }
      rowValues.put(Integer.valueOf(column), Double.valueOf(value));
    }

    /**
     * Convert the sparse matrix to a dense matrix for the available linear solver.
     *
     * @return dense matrix representation
     */
    private Matrix toDenseMatrix() {
      double[][] dense = new double[rows][columns];
      for (Map.Entry<Integer, Map<Integer, Double>> rowEntry : values.entrySet()) {
        int row = rowEntry.getKey().intValue();
        for (Map.Entry<Integer, Double> columnEntry : rowEntry.getValue().entrySet()) {
          dense[row][columnEntry.getKey().intValue()] = columnEntry.getValue().doubleValue();
        }
      }
      return new Matrix(dense);
    }
  }

  /** Internal residual evaluation container for the equation-oriented column solver. */
  private static class ColumnResidualEvaluation {
    /** Bounded unknown vector used in the evaluation. */
    private final double[] unknowns;
    /** Normalized residual vector. */
    private final double[] normalizedResiduals;
    /** Infinity norm of normalized residuals. */
    private final double norm;
    /** Counter-current solution represented by the unknown vector. */
    private final CounterCurrentSolution solution;
    /** Newton iteration count. */
    private final int iterations;
    /** Maximum Maxwell-Stefan flux residual in mol/s. */
    private final double maxFluxResidual;
    /** Maximum interfacial heat residual in W. */
    private final double maxHeatResidual;
    /** Maximum segment energy residual in W-equivalent stream basis. */
    private final double maxEnergyBalanceResidual;
    /** Maximum gas component-balance residual in mol/s. */
    private final double maxGasComponentBalanceResidual;
    /** Maximum liquid component-balance residual in mol/s. */
    private final double maxLiquidComponentBalanceResidual;

    /**
     * Create a column residual evaluation.
     *
     * @param unknowns bounded unknown vector
     * @param normalizedResiduals normalized residual vector
     * @param norm infinity norm of normalized residuals
     * @param solution counter-current solution represented by the unknown vector
     * @param iterations Newton iteration count
     * @param maxFluxResidual maximum flux residual in mol/s
     * @param maxHeatResidual maximum heat residual in W
     * @param maxEnergyBalanceResidual maximum energy residual in W-equivalent stream basis
     * @param maxGasComponentBalanceResidual maximum gas component-balance residual in mol/s
     * @param maxLiquidComponentBalanceResidual maximum liquid component-balance residual in mol/s
     */
    private ColumnResidualEvaluation(double[] unknowns, double[] normalizedResiduals, double norm,
        CounterCurrentSolution solution, int iterations, double maxFluxResidual,
        double maxHeatResidual, double maxEnergyBalanceResidual,
        double maxGasComponentBalanceResidual, double maxLiquidComponentBalanceResidual) {
      this.unknowns = unknowns.clone();
      this.normalizedResiduals = normalizedResiduals.clone();
      this.norm = norm;
      this.solution = solution;
      this.iterations = iterations;
      this.maxFluxResidual = maxFluxResidual;
      this.maxHeatResidual = maxHeatResidual;
      this.maxEnergyBalanceResidual = maxEnergyBalanceResidual;
      this.maxGasComponentBalanceResidual = maxGasComponentBalanceResidual;
      this.maxLiquidComponentBalanceResidual = maxLiquidComponentBalanceResidual;
    }

    /**
     * Return a copy with an updated iteration count.
     *
     * @param iterations updated iteration count
     * @return residual evaluation with updated iteration count
     */
    private ColumnResidualEvaluation withIterations(int iterations) {
      return new ColumnResidualEvaluation(unknowns, normalizedResiduals, norm, solution, iterations,
          maxFluxResidual, maxHeatResidual, maxEnergyBalanceResidual,
          maxGasComponentBalanceResidual, maxLiquidComponentBalanceResidual);
    }
  }

  /** Internal residual evaluation container for the simultaneous segment solver. */
  private static class SegmentResidualEvaluation {
    /** Interface equilibrium used in the residual evaluation. */
    private final InterfaceEquilibrium interfaceEquilibrium;
    /** Proposed component transfers in mol/s. */
    private final Map<String, Double> componentTransfers;
    /** Normalized residual vector. */
    private final double[] normalizedResiduals;
    /** Infinity norm of the normalized residual vector. */
    private final double norm;
    /** Maximum component flux residual in mol/s. */
    private final double maxFluxResidualMolPerSec;
    /** Interfacial heat-balance residual in W. */
    private final double heatBalanceResidualW;
    /** Sensible heat transferred to the liquid side in W. */
    private final double heatTransferRateW;
    /** Gas outlet enthalpy target in J or W-equivalent stream basis. */
    private final double gasTargetEnthalpy;
    /** Liquid outlet enthalpy target in J or W-equivalent stream basis. */
    private final double liquidTargetEnthalpy;
    /** Residual iterations used for this evaluation. */
    private final int iterations;

    /**
     * Create a residual evaluation.
     *
     * @param interfaceEquilibrium interface equilibrium data
     * @param componentTransfers proposed component transfers in mol/s
     * @param normalizedResiduals normalized residual vector
     * @param norm infinity norm of normalized residuals
     * @param maxFluxResidualMolPerSec maximum component flux residual in mol/s
     * @param heatBalanceResidualW interfacial heat-balance residual in W
     * @param heatTransferRateW sensible heat transferred to liquid in W
     * @param gasTargetEnthalpy gas outlet enthalpy target in J or W-equivalent stream basis
     * @param liquidTargetEnthalpy liquid outlet enthalpy target in J or W-equivalent stream basis
     * @param iterations residual iterations used for this evaluation
     */
    private SegmentResidualEvaluation(InterfaceEquilibrium interfaceEquilibrium,
        Map<String, Double> componentTransfers, double[] normalizedResiduals, double norm,
        double maxFluxResidualMolPerSec, double heatBalanceResidualW, double heatTransferRateW,
        double gasTargetEnthalpy, double liquidTargetEnthalpy, int iterations) {
      this.interfaceEquilibrium = interfaceEquilibrium;
      this.componentTransfers = new LinkedHashMap<String, Double>(componentTransfers);
      this.normalizedResiduals = normalizedResiduals.clone();
      this.norm = norm;
      this.maxFluxResidualMolPerSec = maxFluxResidualMolPerSec;
      this.heatBalanceResidualW = heatBalanceResidualW;
      this.heatTransferRateW = heatTransferRateW;
      this.gasTargetEnthalpy = gasTargetEnthalpy;
      this.liquidTargetEnthalpy = liquidTargetEnthalpy;
      this.iterations = iterations;
    }
  }

  /** JSON report DTO. */
  private static class ColumnReport {
    /** Equipment name. */
    private final String name;
    /** Column diameter in metres. */
    private final double columnDiameterM;
    /** Packed height in metres. */
    private final double packedHeightM;
    /** Number of calculation segments. */
    private final int numberOfSegments;
    /** Packing name. */
    private final String packingName;
    /** Packing category. */
    private final String packingCategory;
    /** Mass-transfer correlation name. */
    private final String massTransferCorrelation;
    /** Film-model name. */
    private final String filmModel;
    /** Heat-transfer model name. */
    private final String heatTransferModel;
    /** Column solver name. */
    private final String columnSolver;
    /** Segment solver name. */
    private final String segmentSolver;
    /** Heat-transfer correction factor. */
    private final double heatTransferCorrectionFactor;
    /** Last iteration count. */
    private final int iterationCount;
    /** Last residual in mol/s. */
    private final double convergenceResidualMolPerSec;
    /** Last equation-oriented column residual norm. */
    private final double columnResidualNorm;
    /** Last equation-oriented column Newton iteration count. */
    private final int columnResidualIterations;
    /** Last gas component-balance residual in mol/s. */
    private final double gasComponentBalanceResidualMolPerSec;
    /** Last liquid component-balance residual in mol/s. */
    private final double liquidComponentBalanceResidualMolPerSec;
    /** Last column energy-balance residual in W-equivalent stream basis. */
    private final double columnEnergyBalanceResidualW;
    /** Total absolute transfer in mol/s. */
    private final double totalAbsoluteMolarTransferMolPerSec;
    /** Component transfer totals in mol/s. */
    private final Map<String, Double> componentTransferMolPerSec;
    /** Segment profiles. */
    private final List<SegmentResult> segments;

    /**
     * Create a report DTO.
     *
     * @param column column to serialize
     */
    private ColumnReport(RateBasedPackedColumn column) {
      this.name = column.getName();
      this.columnDiameterM = column.getColumnDiameter();
      this.packedHeightM = column.getPackedHeight();
      this.numberOfSegments = column.getNumberOfSegments();
      this.packingName = column.getPackingSpecification().getName();
      this.packingCategory = column.getPackingSpecification().getCategory();
      this.massTransferCorrelation = column.getMassTransferCorrelation().name();
      this.filmModel = column.getFilmModel().name();
      this.heatTransferModel = column.getHeatTransferModel().name();
        this.columnSolver = column.getColumnSolver().name();
        this.segmentSolver = column.getSegmentSolver().name();
      this.heatTransferCorrectionFactor = column.getHeatTransferCorrectionFactor();
      this.iterationCount = column.getLastIterationCount();
      this.convergenceResidualMolPerSec = column.getLastConvergenceResidual();
        this.columnResidualNorm = column.getLastColumnResidualNorm();
        this.columnResidualIterations = column.getLastColumnResidualIterations();
        this.gasComponentBalanceResidualMolPerSec =
          column.getLastGasComponentBalanceResidual();
        this.liquidComponentBalanceResidualMolPerSec =
          column.getLastLiquidComponentBalanceResidual();
        this.columnEnergyBalanceResidualW = column.getLastColumnEnergyBalanceResidual();
      this.totalAbsoluteMolarTransferMolPerSec = column.getTotalAbsoluteMolarTransfer();
      this.componentTransferMolPerSec =
          new LinkedHashMap<String, Double>(column.getComponentTransferTotals());
      this.segments = new ArrayList<SegmentResult>(column.getSegmentResults());
    }
  }
}
