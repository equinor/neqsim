package neqsim.process.equipment.distillation;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import neqsim.process.equipment.distillation.DistillationColumn.ColumnPumparound;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.characterization.SarirAtmosphericReference;
import neqsim.thermo.characterization.SarirAtmosphericReference.PumparoundReference;

/**
 * Source-bounded pump-around screening for a {@link SarirAtmosphericFractionationCase}.
 *
 * <p>
 * The Sarir source publishes pump-around labels, raw tray numbers, circulation rates, and draw and
 * return temperatures, but it does not state the direction used to number trays. This class
 * therefore requires explicit bottom-up NeqSim tray indices and liquid draw fractions from the
 * caller. Published rates and temperatures are retained as comparison evidence and are not used as
 * hidden calibration targets.
 * </p>
 */
public final class SarirAtmosphericPumparoundScreen {
  private static final double FLOW_CLOSURE_TOLERANCE = 1.0e-8;
  private static final double TEMPERATURE_DROP_TOLERANCE_K = 1.0e-6;

  private final SarirAtmosphericFractionationCase fractionationCase;
  private final Mapping[] mappings;

  private SarirAtmosphericPumparoundScreen(SarirAtmosphericFractionationCase fractionationCase,
      Mapping[] mappings) {
    this.fractionationCase = fractionationCase;
    this.mappings = mappings.clone();
  }

  /**
   * Configure one or both published Sarir pump-around circuits on an unsolved case.
   *
   * @param fractionationCase unsolved qualified Sarir atmospheric-fractionation case
   * @param maxIterations positive pump-around outer-iteration budget
   * @param relativeTolerance positive finite pump-around return-flow tolerance
   * @param mappings explicit source-row-to-NeqSim mappings
   * @return configured screen
   * @throws NullPointerException if the case or mappings array is null
   * @throws IllegalArgumentException if a mapping set is empty, duplicated, or invalid
   * @throws IllegalStateException if the column was already solved or already has a pump-around
   */
  public static SarirAtmosphericPumparoundScreen configure(
      SarirAtmosphericFractionationCase fractionationCase, int maxIterations,
      double relativeTolerance, Mapping... mappings) {
    Objects.requireNonNull(fractionationCase, "fractionationCase");
    Objects.requireNonNull(mappings, "mappings");
    if (mappings.length == 0 || mappings.length > SarirAtmosphericReference.getPumparounds().length) {
      throw new IllegalArgumentException("Configure one or both published Sarir pump-arounds");
    }
    if (maxIterations <= 0) {
      throw new IllegalArgumentException("Pump-around maxIterations must be positive");
    }
    if (!Double.isFinite(relativeTolerance) || relativeTolerance <= 0.0) {
      throw new IllegalArgumentException("Pump-around tolerance must be finite and positive");
    }

    DistillationColumn column = fractionationCase.getColumn();
    if (column.getLastSolveStatus() != DistillationColumn.SolveStatus.NOT_RUN) {
      throw new IllegalStateException("Pump-arounds must be configured before the first column solve");
    }
    if (!column.getPumparounds().isEmpty()) {
      throw new IllegalStateException("Sarir column already has a configured pump-around");
    }

    Mapping[] copiedMappings = mappings.clone();
    Set<String> sourceRows = new HashSet<String>();
    Set<Integer> drawTrays = new HashSet<Integer>();
    for (Mapping mapping : copiedMappings) {
      if (mapping == null) {
        throw new IllegalArgumentException("Pump-around mappings cannot contain null");
      }
      if (!sourceRows.add(mapping.getReferenceName())) {
        throw new IllegalArgumentException("Each published Sarir pump-around may be mapped once");
      }
      if (!drawTrays.add(mapping.getDrawTrayNumber())) {
        throw new IllegalArgumentException("Each NeqSim draw tray may feed only one pump-around");
      }
    }

    for (Mapping mapping : copiedMappings) {
      PumparoundReference reference = mapping.getReference();
      column.addLiquidPumparound(reference.getName(), mapping.getDrawTrayNumber(),
          mapping.getReturnTrayNumber(), mapping.getDrawFraction(),
          reference.getTemperatureDropKelvin());
    }
    column.setMaxPumparoundIterations(maxIterations);
    column.setPumparoundTolerance(relativeTolerance);
    return new SarirAtmosphericPumparoundScreen(fractionationCase, copiedMappings);
  }

  /**
   * Run the connected Sarir feed and column, then return fail-closed pump-around evidence.
   *
   * @param id calculation identifier
   * @return immutable engineering result
   */
  public Result run(UUID id) {
    fractionationCase.run(Objects.requireNonNull(id, "id"));
    return evaluate();
  }

  /**
   * Evaluate a previously solved screen.
   *
   * @return immutable engineering result
   * @throws IllegalStateException if product qualification, outer-tear convergence, or internal
   * pump-around state fails
   */
  public Result evaluate() {
    SarirAtmosphericFractionationResult productResult =
        SarirAtmosphericFractionationResult.evaluate(fractionationCase);
    DistillationColumn column = fractionationCase.getColumn();
    if (!column.isLastColumnTearConverged()) {
      throw new IllegalStateException("Sarir pump-around outer tear must converge before evaluation");
    }

    List<ColumnPumparound> configured = column.getPumparounds();
    if (configured.size() != mappings.length) {
      throw new IllegalStateException("Configured pump-around count changed after screen creation");
    }
    PumparoundResult[] rows = new PumparoundResult[mappings.length];
    for (int i = 0; i < mappings.length; i++) {
      Mapping mapping = mappings[i];
      ColumnPumparound pumparound = findByName(configured, mapping.getReferenceName());
      rows[i] = evaluateCircuit(mapping, pumparound);
    }
    return new Result(productResult, rows, column.getLastPumparoundRelativeChange(),
        column.getLastColumnTearResidual(), column.getLastColumnTearIterationCount());
  }

  /** @return underlying qualified Sarir atmospheric-fractionation case */
  public SarirAtmosphericFractionationCase getFractionationCase() {
    return fractionationCase;
  }

  /** @return defensive copy of explicit source-row-to-NeqSim mappings */
  public Mapping[] getMappings() {
    return mappings.clone();
  }

  private static ColumnPumparound findByName(List<ColumnPumparound> configured, String name) {
    for (ColumnPumparound pumparound : configured) {
      if (name.equals(pumparound.getName())) {
        return pumparound;
      }
    }
    throw new IllegalStateException("Configured Sarir pump-around is missing: " + name);
  }

  private static PumparoundResult evaluateCircuit(Mapping mapping,
      ColumnPumparound pumparound) {
    StreamInterface draw = pumparound.getDrawStream();
    StreamInterface returned = pumparound.getReturnStream();
    if (draw == null || returned == null) {
      throw new IllegalStateException("Pump-around draw and return streams must be available");
    }
    double drawFlow = Math.abs(draw.getFlowRate("kg/hr"));
    double returnFlow = Math.abs(returned.getFlowRate("kg/hr"));
    requireFinitePositive(drawFlow, "Pump-around draw flow");
    requireFinitePositive(returnFlow, "Pump-around return flow");
    double flowClosure = Math.abs(drawFlow - returnFlow) / Math.max(drawFlow, returnFlow);
    if (!Double.isFinite(flowClosure) || flowClosure > FLOW_CLOSURE_TOLERANCE) {
      throw new IllegalStateException("Pump-around internal mass flow does not close");
    }

    double drawTemperatureCelsius = draw.getTemperature("C");
    double returnTemperatureCelsius = returned.getTemperature("C");
    requireFinite(drawTemperatureCelsius, "Pump-around draw temperature");
    requireFinite(returnTemperatureCelsius, "Pump-around return temperature");
    PumparoundReference reference = mapping.getReference();
    double modeledDrop = drawTemperatureCelsius - returnTemperatureCelsius;
    if (!Double.isFinite(modeledDrop)
        || Math.abs(modeledDrop - reference.getTemperatureDropKelvin())
            > TEMPERATURE_DROP_TOLERANCE_K) {
      throw new IllegalStateException("Modeled pump-around temperature drop changed from its source boundary");
    }
    double dutyW = pumparound.getDuty();
    if (!Double.isFinite(dutyW) || !(dutyW < 0.0)) {
      throw new IllegalStateException("A cooled pump-around must report a finite negative duty");
    }

    return new PumparoundResult(mapping, reference.getSourceDrawTrayNumber(),
        reference.getSourceReturnTrayNumber(), reference.getMassFlowRateKgPerHour(), drawFlow,
        returnFlow, flowClosure, reference.getDrawTemperatureCelsius(), drawTemperatureCelsius,
        reference.getReturnTemperatureCelsius(), returnTemperatureCelsius, dutyW);
  }

  private static void requireFinitePositive(double value, String label) {
    if (!Double.isFinite(value) || !(value > 0.0)) {
      throw new IllegalStateException(label + " must be finite and positive");
    }
  }

  private static void requireFinite(double value, String label) {
    if (!Double.isFinite(value)) {
      throw new IllegalStateException(label + " must be finite");
    }
  }

  /** Explicit mapping from one published row to NeqSim's bottom-up tray convention. */
  public static final class Mapping {
    private final String referenceName;
    private final int drawTrayNumber;
    private final int returnTrayNumber;
    private final double drawFraction;

    /**
     * Create an explicit mapping without inferring the source tray-numbering basis.
     *
     * @param referenceName exact source-table pump-around label
     * @param drawTrayNumber bottom-up NeqSim draw-tray index
     * @param returnTrayNumber bottom-up NeqSim return-tray index
     * @param drawFraction fraction of draw-tray liquid traffic circulated, in (0, 1)
     */
    public Mapping(String referenceName, int drawTrayNumber, int returnTrayNumber,
        double drawFraction) {
      PumparoundReference reference = SarirAtmosphericReference.getPumparound(referenceName);
      if (drawTrayNumber < 0 || drawTrayNumber >= SarirAtmosphericFractionationCase.SIMPLE_TRAY_COUNT
          || returnTrayNumber < 0
          || returnTrayNumber >= SarirAtmosphericFractionationCase.SIMPLE_TRAY_COUNT) {
        throw new IllegalArgumentException("Mapped tray indices must be valid bottom-up NeqSim indices");
      }
      if (drawTrayNumber == returnTrayNumber) {
        throw new IllegalArgumentException("Pump-around draw and return trays must differ");
      }
      if (!Double.isFinite(drawFraction) || !(drawFraction > 0.0) || !(drawFraction < 1.0)) {
        throw new IllegalArgumentException("Pump-around draw fraction must be finite and in (0, 1)");
      }
      if (!Double.isFinite(reference.getTemperatureDropKelvin())
          || !(reference.getTemperatureDropKelvin() > 0.0)) {
        throw new IllegalStateException("Published Sarir pump-around must define positive cooling");
      }
      this.referenceName = reference.getName();
      this.drawTrayNumber = drawTrayNumber;
      this.returnTrayNumber = returnTrayNumber;
      this.drawFraction = drawFraction;
    }

    /** @return exact source-table pump-around label */
    public String getReferenceName() {
      return referenceName;
    }

    /** @return bottom-up NeqSim draw-tray index supplied by the caller */
    public int getDrawTrayNumber() {
      return drawTrayNumber;
    }

    /** @return bottom-up NeqSim return-tray index supplied by the caller */
    public int getReturnTrayNumber() {
      return returnTrayNumber;
    }

    /** @return caller-supplied fraction of draw-tray liquid traffic */
    public double getDrawFraction() {
      return drawFraction;
    }

    private PumparoundReference getReference() {
      return SarirAtmosphericReference.getPumparound(referenceName);
    }
  }

  /** Immutable evidence for one solved pump-around circuit. */
  public static final class PumparoundResult {
    private final Mapping mapping;
    private final int sourceDrawTrayNumber;
    private final int sourceReturnTrayNumber;
    private final double sourceMassFlowKgPerHour;
    private final double modeledDrawMassFlowKgPerHour;
    private final double modeledReturnMassFlowKgPerHour;
    private final double internalFlowClosureRelativeError;
    private final double sourceDrawTemperatureCelsius;
    private final double modeledDrawTemperatureCelsius;
    private final double sourceReturnTemperatureCelsius;
    private final double modeledReturnTemperatureCelsius;
    private final double dutyW;

    private PumparoundResult(Mapping mapping, int sourceDrawTrayNumber,
        int sourceReturnTrayNumber, double sourceMassFlowKgPerHour,
        double modeledDrawMassFlowKgPerHour, double modeledReturnMassFlowKgPerHour,
        double internalFlowClosureRelativeError, double sourceDrawTemperatureCelsius,
        double modeledDrawTemperatureCelsius, double sourceReturnTemperatureCelsius,
        double modeledReturnTemperatureCelsius, double dutyW) {
      this.mapping = mapping;
      this.sourceDrawTrayNumber = sourceDrawTrayNumber;
      this.sourceReturnTrayNumber = sourceReturnTrayNumber;
      this.sourceMassFlowKgPerHour = sourceMassFlowKgPerHour;
      this.modeledDrawMassFlowKgPerHour = modeledDrawMassFlowKgPerHour;
      this.modeledReturnMassFlowKgPerHour = modeledReturnMassFlowKgPerHour;
      this.internalFlowClosureRelativeError = internalFlowClosureRelativeError;
      this.sourceDrawTemperatureCelsius = sourceDrawTemperatureCelsius;
      this.modeledDrawTemperatureCelsius = modeledDrawTemperatureCelsius;
      this.sourceReturnTemperatureCelsius = sourceReturnTemperatureCelsius;
      this.modeledReturnTemperatureCelsius = modeledReturnTemperatureCelsius;
      this.dutyW = dutyW;
    }

    /** @return explicit caller mapping */
    public Mapping getMapping() {
      return mapping;
    }

    /** @return raw draw-tray number printed by the source */
    public int getSourceDrawTrayNumber() {
      return sourceDrawTrayNumber;
    }

    /** @return raw return-tray number printed by the source */
    public int getSourceReturnTrayNumber() {
      return sourceReturnTrayNumber;
    }

    /** @return source circulation rate in kg/h, retained as comparison evidence */
    public double getSourceMassFlowKgPerHour() {
      return sourceMassFlowKgPerHour;
    }

    /** @return modeled liquid draw flow in kg/h */
    public double getModeledDrawMassFlowKgPerHour() {
      return modeledDrawMassFlowKgPerHour;
    }

    /** @return modeled liquid return flow in kg/h */
    public double getModeledReturnMassFlowKgPerHour() {
      return modeledReturnMassFlowKgPerHour;
    }

    /** @return modeled absolute draw/return flow difference divided by the larger flow */
    public double getInternalFlowClosureRelativeError() {
      return internalFlowClosureRelativeError;
    }

    /** @return source draw temperature in degrees Celsius */
    public double getSourceDrawTemperatureCelsius() {
      return sourceDrawTemperatureCelsius;
    }

    /** @return modeled draw temperature in degrees Celsius */
    public double getModeledDrawTemperatureCelsius() {
      return modeledDrawTemperatureCelsius;
    }

    /** @return source return temperature in degrees Celsius */
    public double getSourceReturnTemperatureCelsius() {
      return sourceReturnTemperatureCelsius;
    }

    /** @return modeled return temperature in degrees Celsius */
    public double getModeledReturnTemperatureCelsius() {
      return modeledReturnTemperatureCelsius;
    }

    /** @return modeled return-minus-draw enthalpy duty in W; cooling is negative */
    public double getDutyW() {
      return dutyW;
    }

    /** @return absolute modeled/source circulation-rate error in percent */
    public double getAbsoluteRelativeFlowErrorPercentAgainstSource() {
      return 100.0 * Math.abs(modeledReturnMassFlowKgPerHour - sourceMassFlowKgPerHour)
          / sourceMassFlowKgPerHour;
    }

    /** @return absolute modeled/source draw-temperature difference in kelvin */
    public double getAbsoluteDrawTemperatureErrorKelvin() {
      return Math.abs(modeledDrawTemperatureCelsius - sourceDrawTemperatureCelsius);
    }

    /** @return absolute modeled/source return-temperature difference in kelvin */
    public double getAbsoluteReturnTemperatureErrorKelvin() {
      return Math.abs(modeledReturnTemperatureCelsius - sourceReturnTemperatureCelsius);
    }
  }

  /** Immutable qualified products plus pump-around convergence and comparison evidence. */
  public static final class Result {
    private final SarirAtmosphericFractionationResult productResult;
    private final PumparoundResult[] pumparounds;
    private final double lastPumparoundRelativeChange;
    private final double lastColumnTearResidual;
    private final int lastColumnTearIterationCount;

    private Result(SarirAtmosphericFractionationResult productResult,
        PumparoundResult[] pumparounds, double lastPumparoundRelativeChange,
        double lastColumnTearResidual, int lastColumnTearIterationCount) {
      this.productResult = productResult;
      this.pumparounds = pumparounds.clone();
      this.lastPumparoundRelativeChange = lastPumparoundRelativeChange;
      this.lastColumnTearResidual = lastColumnTearResidual;
      this.lastColumnTearIterationCount = lastColumnTearIterationCount;
    }

    /** @return previously qualified atmospheric product result */
    public SarirAtmosphericFractionationResult getProductResult() {
      return productResult;
    }

    /** @return defensive copy of pump-around result rows */
    public PumparoundResult[] getPumparounds() {
      return pumparounds.clone();
    }

    /** @return maximum latest pump-around return-flow change */
    public double getLastPumparoundRelativeChange() {
      return lastPumparoundRelativeChange;
    }

    /** @return maximum combined column outer-tear residual */
    public double getLastColumnTearResidual() {
      return lastColumnTearResidual;
    }

    /** @return number of combined column outer-tear iterations */
    public int getLastColumnTearIterationCount() {
      return lastColumnTearIterationCount;
    }
  }
}
