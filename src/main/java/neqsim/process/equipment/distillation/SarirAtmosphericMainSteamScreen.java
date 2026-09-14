package neqsim.process.equipment.distillation;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.characterization.SarirAtmosphericReference;
import neqsim.thermo.characterization.SarirAtmosphericReference.SteamInjectionReference;
import neqsim.thermo.characterization.SarirAtmosphericReference.SteamInjectionService;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;

/**
 * Source-bounded main-column steam-injection screening for a
 * {@link SarirAtmosphericFractionationCase}.
 *
 * <p>
 * The Sarir source publishes a main-column steam rate, temperature, and pressure, but it does not
 * publish an injection tray, steam quality, enthalpy, or a complete thermodynamic state. This class
 * therefore requires a bottom-up NeqSim tray index, an explicit interpretation of the reported
 * pressure basis, and an independently prepared, single-gas-phase water stream from the caller. A
 * non-blank state-basis description is retained as auditable engineering provenance.
 * </p>
 *
 * <p>
 * The two published side-stripper rows are outside this class because the qualified Sarir case does
 * not yet model either side stripper. They are never redirected into the main column.
 * </p>
 */
public final class SarirAtmosphericMainSteamScreen {
  private static final String SOURCE_ROW_NAME = "Main atmospheric column";
  private static final double WATER_MOLE_FRACTION_MINIMUM = 1.0 - 1.0e-10;
  private static final double FLOW_RELATIVE_TOLERANCE = 1.0e-9;
  private static final double TEMPERATURE_TOLERANCE_C = 1.0e-7;
  private static final double PRESSURE_TOLERANCE_KPA = 1.0e-7;
  private static final double TOTAL_CLOSURE_TOLERANCE = 5.0e-2;
  private static final double STANDARD_ATMOSPHERIC_PRESSURE_KPA = 101.325;

  /** Explicit engineering interpretation of the source pressure column. */
  public enum ReportedPressureBasis {
    /** Source pressure is interpreted as absolute. */
    ABSOLUTE,
    /** Source pressure is interpreted as gauge relative to 101.325 kPa. */
    GAUGE
  }

  private final SarirAtmosphericFractionationCase fractionationCase;
  private final int injectionTrayIndex;
  private final StreamInterface steamStream;
  private final ReportedPressureBasis reportedPressureBasis;
  private final String thermodynamicStateBasis;
  private final SteamInjectionReference sourceReference;

  private SarirAtmosphericMainSteamScreen(SarirAtmosphericFractionationCase fractionationCase,
      int injectionTrayIndex, StreamInterface steamStream,
      ReportedPressureBasis reportedPressureBasis, String thermodynamicStateBasis,
      SteamInjectionReference sourceReference) {
    this.fractionationCase = fractionationCase;
    this.injectionTrayIndex = injectionTrayIndex;
    this.steamStream = steamStream;
    this.reportedPressureBasis = reportedPressureBasis;
    this.thermodynamicStateBasis = thermodynamicStateBasis;
    this.sourceReference = sourceReference;
  }

  /**
   * Attach an explicit prepared steam stream to an unsolved qualified Sarir atmospheric case.
   *
   * @param fractionationCase unsolved qualified Sarir atmospheric-fractionation case
   * @param injectionTrayIndex explicit bottom-up NeqSim tray index
   * @param preparedSteam independently prepared material stream at the published rate, temperature,
   *        and pressure, with exactly one gas phase and essentially pure water composition
   * @param reportedPressureBasis explicit engineering interpretation of the source pressure column
   * @param thermodynamicStateBasis non-blank description of the independent quality, enthalpy, or
   *        state evidence used to prepare the stream
   * @return configured source-bounded screen
   * @throws NullPointerException if the case or stream is null
   * @throws IllegalArgumentException if the tray, basis, or stream boundary is invalid
   * @throws IllegalStateException if the source boundary changed, the case was solved, or the column
   *         already has an additional feed
   */
  public static SarirAtmosphericMainSteamScreen configure(
      SarirAtmosphericFractionationCase fractionationCase, int injectionTrayIndex,
      StreamInterface preparedSteam, ReportedPressureBasis reportedPressureBasis,
      String thermodynamicStateBasis) {
    Objects.requireNonNull(fractionationCase, "fractionationCase");
    Objects.requireNonNull(preparedSteam, "preparedSteam");
    Objects.requireNonNull(reportedPressureBasis, "reportedPressureBasis");
    String stateBasis = requireStateBasis(thermodynamicStateBasis);

    if (injectionTrayIndex < 0
        || injectionTrayIndex >= SarirAtmosphericFractionationCase.SIMPLE_TRAY_COUNT) {
      throw new IllegalArgumentException(
          "Steam injection tray must be a valid bottom-up Sarir NeqSim index");
    }

    SteamInjectionReference source = requireSourceBoundary();
    DistillationColumn column = fractionationCase.getColumn();
    if (column.getLastSolveStatus() != DistillationColumn.SolveStatus.NOT_RUN) {
      throw new IllegalStateException("Steam must be configured before the first column solve");
    }
    requireUnaugmentedColumn(fractionationCase);
    if (preparedSteam == fractionationCase.getFeedStream()) {
      throw new IllegalArgumentException("Steam must be independent of the crude feed stream");
    }
    validatePreparedSteam(preparedSteam, source, reportedPressureBasis);

    column.addFeedStream(preparedSteam, injectionTrayIndex);
    return new SarirAtmosphericMainSteamScreen(fractionationCase, injectionTrayIndex, preparedSteam,
        reportedPressureBasis, stateBasis, source);
  }

  /**
   * Run the connected crude feed and column, then return fail-closed steam and balance evidence.
   *
   * @param id calculation identifier
   * @return immutable engineering evidence
   */
  public Result run(UUID id) {
    fractionationCase.run(Objects.requireNonNull(id, "id"));
    return evaluate();
  }

  /**
   * Evaluate an already solved screen.
   *
   * @return immutable engineering evidence
   * @throws IllegalStateException if the source boundary, connection, prepared state, column
   *         qualification, or total material closure fails
   */
  public Result evaluate() {
    SteamInjectionReference currentSource = requireSourceBoundary();
    requireSameSourceBoundary(sourceReference, currentSource);
    validatePreparedSteam(steamStream, currentSource, reportedPressureBasis);
    requireAttachedSteam();

    SarirAtmosphericFractionationResult productResult =
        SarirAtmosphericFractionationResult.evaluate(fractionationCase);
    DistillationColumn column = fractionationCase.getColumn();

    double crudeFlow = fractionationCase.getFeedStream().getFlowRate("kg/hr");
    double steamFlow = steamStream.getFlowRate("kg/hr");
    double totalInletFlow = crudeFlow + steamFlow;
    double totalProductFlow = productResult.getProductMassFlowKgPerHour();
    double totalClosure = Math.abs(totalInletFlow - totalProductFlow) / totalInletFlow;
    if (!Double.isFinite(totalClosure) || totalClosure > TOTAL_CLOSURE_TOLERANCE) {
      throw new IllegalStateException(
          "Sarir crude-plus-steam inlet does not close against calculated products");
    }

    double vaporMoleFraction = getVaporMoleFraction(steamStream.getFluid());
    return new Result(productResult, injectionTrayIndex, reportedPressureBasis,
        thermodynamicStateBasis, currentSource.getMassFlowRateKgPerHour(), steamFlow,
        currentSource.getTemperatureCelsius(), steamStream.getTemperature("C"),
        currentSource.getPressureKPa(), steamStream.getPressure("bara") * 100.0,
        vaporMoleFraction, totalInletFlow, totalProductFlow, totalClosure,
        column.getMassBalanceError(), column.getEnergyBalanceError());
  }

  /** @return underlying qualified Sarir atmospheric-fractionation case */
  public SarirAtmosphericFractionationCase getFractionationCase() {
    return fractionationCase;
  }

  /** @return explicit bottom-up NeqSim steam-injection tray */
  public int getInjectionTrayIndex() {
    return injectionTrayIndex;
  }

  /** @return caller-supplied prepared steam stream */
  public StreamInterface getSteamStream() {
    return steamStream;
  }

  /** @return explicit caller interpretation of the source pressure column */
  public ReportedPressureBasis getReportedPressureBasis() {
    return reportedPressureBasis;
  }

  /** @return retained caller description of the independently established steam state */
  public String getThermodynamicStateBasis() {
    return thermodynamicStateBasis;
  }

  /** @return immutable published main-column steam row */
  public SteamInjectionReference getSourceReference() {
    return sourceReference;
  }

  private static String requireStateBasis(String thermodynamicStateBasis) {
    if (thermodynamicStateBasis == null || thermodynamicStateBasis.trim().isEmpty()) {
      throw new IllegalArgumentException(
          "An explicit independent thermodynamic-state basis is required");
    }
    return thermodynamicStateBasis.trim();
  }

  private static SteamInjectionReference requireSourceBoundary() {
    if (SarirAtmosphericReference.hasExplicitSteamInjectionLocations()
        || SarirAtmosphericReference.hasExplicitSteamQuality()
        || SarirAtmosphericReference.hasExplicitSteamThermodynamicState()) {
      throw new IllegalStateException(
          "Sarir steam source semantics changed and require independent requalification");
    }
    SteamInjectionReference source = SarirAtmosphericReference.getSteamInjection(SOURCE_ROW_NAME);
    if (source.getService() != SteamInjectionService.MAIN_ATMOSPHERIC_COLUMN) {
      throw new IllegalStateException("Sarir main-column steam row changed service");
    }
    requireFinitePositive(source.getMassFlowRateKgPerHour(), "Source steam mass flow");
    requireFinite(source.getTemperatureCelsius(), "Source steam temperature");
    requireFinitePositive(source.getPressureKPa(), "Source steam pressure");
    return source;
  }

  private static void requireSameSourceBoundary(SteamInjectionReference expected,
      SteamInjectionReference actual) {
    if (!expected.getName().equals(actual.getName()) || expected.getService() != actual.getService()
        || Double.compare(expected.getMassFlowRateKgPerHour(),
            actual.getMassFlowRateKgPerHour()) != 0
        || Double.compare(expected.getTemperatureCelsius(), actual.getTemperatureCelsius()) != 0
        || Double.compare(expected.getPressureKPa(), actual.getPressureKPa()) != 0) {
      throw new IllegalStateException("Published Sarir main-column steam boundary changed");
    }
  }

  private static void requireUnaugmentedColumn(
      SarirAtmosphericFractionationCase fractionationCase) {
    DistillationColumn column = fractionationCase.getColumn();
    int feedCount = 0;
    for (int tray = 0; tray < SarirAtmosphericFractionationCase.SIMPLE_TRAY_COUNT; tray++) {
      feedCount += column.getFeedStreams(tray).size();
    }
    List<StreamInterface> crudeTrayFeeds =
        column.getFeedStreams(SarirAtmosphericFractionationCase.FEED_INTERNAL_INDEX);
    if (feedCount != 1 || crudeTrayFeeds.size() != 1
        || crudeTrayFeeds.get(0) != fractionationCase.getFeedStream()) {
      throw new IllegalStateException(
          "Sarir column must contain only its qualified crude feed before steam configuration");
    }
  }

  private void requireAttachedSteam() {
    List<StreamInterface> trayFeeds =
        fractionationCase.getColumn().getFeedStreams(injectionTrayIndex);
    int identityMatches = 0;
    for (StreamInterface trayFeed : trayFeeds) {
      if (trayFeed == steamStream) {
        identityMatches++;
      }
    }
    if (identityMatches != 1) {
      throw new IllegalStateException(
          "Prepared Sarir main-column steam stream is not attached exactly once");
    }
  }

  private static void validatePreparedSteam(StreamInterface preparedSteam,
      SteamInjectionReference source, ReportedPressureBasis reportedPressureBasis) {
    double flowKgPerHour = preparedSteam.getFlowRate("kg/hr");
    double temperatureCelsius = preparedSteam.getTemperature("C");
    double pressureKPa = preparedSteam.getPressure("bara") * 100.0;
    double expectedAbsolutePressureKPa = source.getPressureKPa()
        + (reportedPressureBasis == ReportedPressureBasis.GAUGE
            ? STANDARD_ATMOSPHERIC_PRESSURE_KPA : 0.0);
    requireRelativeClose(flowKgPerHour, source.getMassFlowRateKgPerHour(),
        FLOW_RELATIVE_TOLERANCE, "Prepared steam mass flow");
    requireAbsoluteClose(temperatureCelsius, source.getTemperatureCelsius(),
        TEMPERATURE_TOLERANCE_C, "Prepared steam temperature");
    requireAbsoluteClose(pressureKPa, expectedAbsolutePressureKPa, PRESSURE_TOLERANCE_KPA,
        "Prepared steam absolute pressure");

    SystemInterface fluid = preparedSteam.getFluid();
    if (fluid == null || fluid.getNumberOfPhases() != 1
        || fluid.getPhase(0).getType() != PhaseType.GAS) {
      throw new IllegalArgumentException(
          "Prepared steam must expose exactly one independently established gas phase");
    }
    if (!fluid.getPhase(0).hasComponent("water")) {
      throw new IllegalArgumentException("Prepared steam must contain water");
    }
    double waterMoleFraction = fluid.getPhase(0).getComponent("water").getz();
    if (!Double.isFinite(waterMoleFraction)
        || waterMoleFraction < WATER_MOLE_FRACTION_MINIMUM) {
      throw new IllegalArgumentException("Prepared steam must be essentially pure water");
    }
    double vaporMoleFraction = getVaporMoleFraction(fluid);
    if (!Double.isFinite(vaporMoleFraction)
        || vaporMoleFraction < WATER_MOLE_FRACTION_MINIMUM) {
      throw new IllegalArgumentException(
          "Prepared steam must have an independently established vapor fraction of one");
    }
  }

  private static double getVaporMoleFraction(SystemInterface fluid) {
    if (fluid.getNumberOfPhases() == 1 && fluid.getPhase(0).getType() == PhaseType.GAS) {
      return 1.0;
    }
    if (!fluid.hasPhaseType("gas")) {
      return 0.0;
    }
    return fluid.getPhase("gas").getBeta();
  }

  private static void requireRelativeClose(double value, double expected,
      double relativeTolerance, String label) {
    if (!Double.isFinite(value)
        || Math.abs(value - expected) > relativeTolerance * Math.max(1.0, Math.abs(expected))) {
      throw new IllegalArgumentException(label + " does not match the published Sarir boundary");
    }
  }

  private static void requireAbsoluteClose(double value, double expected,
      double absoluteTolerance, String label) {
    if (!Double.isFinite(value) || Math.abs(value - expected) > absoluteTolerance) {
      throw new IllegalArgumentException(label + " does not match the published Sarir boundary");
    }
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

  /** Immutable source, mapping, state, and total material-closure evidence. */
  public static final class Result {
    private final SarirAtmosphericFractionationResult productResult;
    private final int injectionTrayIndex;
    private final ReportedPressureBasis reportedPressureBasis;
    private final String thermodynamicStateBasis;
    private final double sourceMassFlowKgPerHour;
    private final double modeledMassFlowKgPerHour;
    private final double sourceTemperatureCelsius;
    private final double modeledTemperatureCelsius;
    private final double sourcePressureKPa;
    private final double modeledPressureKPa;
    private final double vaporMoleFraction;
    private final double totalInletMassFlowKgPerHour;
    private final double totalProductMassFlowKgPerHour;
    private final double totalMassClosureRelativeError;
    private final double columnMassBalanceError;
    private final double columnEnergyBalanceError;

    private Result(SarirAtmosphericFractionationResult productResult, int injectionTrayIndex,
        ReportedPressureBasis reportedPressureBasis, String thermodynamicStateBasis,
        double sourceMassFlowKgPerHour,
        double modeledMassFlowKgPerHour, double sourceTemperatureCelsius,
        double modeledTemperatureCelsius, double sourcePressureKPa, double modeledPressureKPa,
        double vaporMoleFraction, double totalInletMassFlowKgPerHour,
        double totalProductMassFlowKgPerHour, double totalMassClosureRelativeError,
        double columnMassBalanceError, double columnEnergyBalanceError) {
      this.productResult = productResult;
      this.injectionTrayIndex = injectionTrayIndex;
      this.reportedPressureBasis = reportedPressureBasis;
      this.thermodynamicStateBasis = thermodynamicStateBasis;
      this.sourceMassFlowKgPerHour = sourceMassFlowKgPerHour;
      this.modeledMassFlowKgPerHour = modeledMassFlowKgPerHour;
      this.sourceTemperatureCelsius = sourceTemperatureCelsius;
      this.modeledTemperatureCelsius = modeledTemperatureCelsius;
      this.sourcePressureKPa = sourcePressureKPa;
      this.modeledPressureKPa = modeledPressureKPa;
      this.vaporMoleFraction = vaporMoleFraction;
      this.totalInletMassFlowKgPerHour = totalInletMassFlowKgPerHour;
      this.totalProductMassFlowKgPerHour = totalProductMassFlowKgPerHour;
      this.totalMassClosureRelativeError = totalMassClosureRelativeError;
      this.columnMassBalanceError = columnMassBalanceError;
      this.columnEnergyBalanceError = columnEnergyBalanceError;
    }

    /** @return previously qualified atmospheric product result */
    public SarirAtmosphericFractionationResult getProductResult() {
      return productResult;
    }

    /** @return explicit bottom-up NeqSim injection-tray index */
    public int getInjectionTrayIndex() {
      return injectionTrayIndex;
    }

    /** @return explicit caller interpretation of the source pressure column */
    public ReportedPressureBasis getReportedPressureBasis() {
      return reportedPressureBasis;
    }

    /** @return retained independent steam-state basis */
    public String getThermodynamicStateBasis() {
      return thermodynamicStateBasis;
    }

    /** @return published source steam flow in kg/h */
    public double getSourceMassFlowKgPerHour() {
      return sourceMassFlowKgPerHour;
    }

    /** @return modeled prepared-steam flow in kg/h */
    public double getModeledMassFlowKgPerHour() {
      return modeledMassFlowKgPerHour;
    }

    /** @return published source temperature in degrees Celsius */
    public double getSourceTemperatureCelsius() {
      return sourceTemperatureCelsius;
    }

    /** @return modeled prepared-steam temperature in degrees Celsius */
    public double getModeledTemperatureCelsius() {
      return modeledTemperatureCelsius;
    }

    /** @return published source pressure in kPa as reported */
    public double getSourcePressureKPa() {
      return sourcePressureKPa;
    }

    /** @return modeled prepared-steam pressure in kPa absolute */
    public double getModeledPressureKPa() {
      return modeledPressureKPa;
    }

    /** @return independently established prepared-stream vapor mole fraction */
    public double getVaporMoleFraction() {
      return vaporMoleFraction;
    }

    /** @return crude plus prepared-steam inlet mass flow in kg/h */
    public double getTotalInletMassFlowKgPerHour() {
      return totalInletMassFlowKgPerHour;
    }

    /** @return sum of qualified column product mass flows in kg/h */
    public double getTotalProductMassFlowKgPerHour() {
      return totalProductMassFlowKgPerHour;
    }

    /** @return absolute total inlet/product mass difference divided by total inlet */
    public double getTotalMassClosureRelativeError() {
      return totalMassClosureRelativeError;
    }

    /** @return column-reported relative mass-balance error */
    public double getColumnMassBalanceError() {
      return columnMassBalanceError;
    }

    /** @return column-reported relative energy-balance error */
    public double getColumnEnergyBalanceError() {
      return columnEnergyBalanceError;
    }
  }
}
