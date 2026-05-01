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

  /** Global correction factor for interphase heat-transfer coefficients. */
  private double heatTransferCorrectionFactor = 1.0;

  /** Maximum fraction of the available thermal approach transferred in one segment. */
  private double maxHeatTransferFractionPerSegment = 0.50;

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
        interfaceEquilibrium.liquidMoleFractions, interfaceEquilibrium.equilibriumRatios);
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
    double transfer = transferDensity * segmentVolume;
    return limitTransfer(component, transfer, gas, liquid);
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
    SystemInterface mixed = gas.clone();
    addSystemComponents(mixed, liquid);
    mixed.setTemperature(snapshot.interfaceTemperatureK);
    mixed.setPressure(0.5 * (gas.getPressure() + liquid.getPressure()));
    flashAndInitialize(mixed);
    PhaseInterface gasPhase = getGasPhase(mixed);
    PhaseInterface liquidPhase = getLiquidPhase(mixed);
    List<String> allComponents = getTransferComponentList(gas, liquid);
    for (int i = 0; i < allComponents.size(); i++) {
      String component = allComponents.get(i);
      double x = moleFraction(liquidPhase, component);
      double y = moleFraction(gasPhase, component);
      gasFractions.put(component, y);
      liquidFractions.put(component, x);
      if (x > 1.0e-12 && y >= 0.0) {
        ratios.put(component, Math.max(1.0e-12, y / x));
      } else {
        ratios.put(component, 1.0);
      }
    }
    return new InterfaceEquilibrium(snapshot.interfaceTemperatureK, gasFractions, liquidFractions,
        ratios);
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
        Map<String, Double> interfaceEquilibriumRatios) {
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

    /**
     * Create interface equilibrium data.
     *
     * @param interfaceTemperatureK interface equilibrium temperature in K
     * @param gasMoleFractions gas-side mole fractions by component
     * @param liquidMoleFractions liquid-side mole fractions by component
     * @param equilibriumRatios gas-to-liquid equilibrium ratios by component
     */
    private InterfaceEquilibrium(double interfaceTemperatureK, Map<String, Double> gasMoleFractions,
        Map<String, Double> liquidMoleFractions, Map<String, Double> equilibriumRatios) {
      this.interfaceTemperatureK = interfaceTemperatureK;
      this.gasMoleFractions = new LinkedHashMap<String, Double>(gasMoleFractions);
      this.liquidMoleFractions = new LinkedHashMap<String, Double>(liquidMoleFractions);
      this.equilibriumRatios = new LinkedHashMap<String, Double>(equilibriumRatios);
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
    /** Heat-transfer correction factor. */
    private final double heatTransferCorrectionFactor;
    /** Last iteration count. */
    private final int iterationCount;
    /** Last residual in mol/s. */
    private final double convergenceResidualMolPerSec;
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
      this.heatTransferCorrectionFactor = column.getHeatTransferCorrectionFactor();
      this.iterationCount = column.getLastIterationCount();
      this.convergenceResidualMolPerSec = column.getLastConvergenceResidual();
      this.totalAbsoluteMolarTransferMolPerSec = column.getTotalAbsoluteMolarTransfer();
      this.componentTransferMolPerSec =
          new LinkedHashMap<String, Double>(column.getComponentTransferTotals());
      this.segments = new ArrayList<SegmentResult>(column.getSegmentResults());
    }
  }
}
