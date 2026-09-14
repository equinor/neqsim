package neqsim.process.equipment.distillation;

import java.util.Objects;
import java.util.UUID;
import neqsim.process.equipment.distillation.SarirAtmosphericFractionationCase.OperatingInputs;
import neqsim.process.equipment.distillation.SarirAtmosphericMainSteamScreen.ReportedPressureBasis;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.characterization.SarirAtmosphericReference;
import neqsim.thermo.characterization.SarirAtmosphericReference.SteamInjectionReference;
import neqsim.thermo.characterization.SarirAtmosphericReference.SteamInjectionService;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;

/**
 * Source-bounded equilibrium-contact screen for a Sarir atmospheric side-stripper service.
 *
 * <p>
 * The public Sarir reference reports steam rate, temperature, and pressure for the kerosene and
 * diesel side strippers, but it does not report side-stripper topology, tray count, pressure,
 * injection location, efficiency, steam quality, or enthalpy. This class therefore contacts one
 * already calculated atmospheric-column liquid side draw with one independently prepared steam
 * stream on a single equilibrium {@link SimpleTray}. The caller must supply the contact pressure
 * and auditable steam-state provenance.
 * </p>
 *
 * <p>
 * The result is a screening calculation only. It is not a multistage side-stripper model and must
 * not be treated as a reproduction of plant product quality or yield.
 * </p>
 */
public final class SarirAtmosphericSideStripperContactScreen {
  private static final String KEROSENE_SOURCE_ROW = "Kerosene side stripper";
  private static final String DIESEL_SOURCE_ROW = "Diesel side stripper";
  private static final double WATER_MOLE_FRACTION_MINIMUM = 1.0 - 1.0e-10;
  private static final double FLOW_RELATIVE_TOLERANCE = 1.0e-9;
  private static final double TEMPERATURE_TOLERANCE_C = 1.0e-7;
  private static final double PRESSURE_TOLERANCE_KPA = 1.0e-7;
  private static final double MASS_CLOSURE_TOLERANCE = 1.0e-6;
  private static final double STANDARD_ATMOSPHERIC_PRESSURE_KPA = 101.325;

  private final SarirAtmosphericFractionationCase fractionationCase;
  private final SteamInjectionService service;
  private final StreamInterface sideDrawStream;
  private final StreamInterface steamStream;
  private final ReportedPressureBasis reportedPressureBasis;
  private final String thermodynamicStateBasis;
  private final double contactPressureBara;
  private final SteamInjectionReference sourceReference;
  private final SimpleTray contactStage;

  private SarirAtmosphericSideStripperContactScreen(
      SarirAtmosphericFractionationCase fractionationCase, SteamInjectionService service,
      StreamInterface sideDrawStream, StreamInterface steamStream,
      ReportedPressureBasis reportedPressureBasis, String thermodynamicStateBasis,
      double contactPressureBara, SteamInjectionReference sourceReference, SimpleTray contactStage) {
    this.fractionationCase = fractionationCase;
    this.service = service;
    this.sideDrawStream = sideDrawStream;
    this.steamStream = steamStream;
    this.reportedPressureBasis = reportedPressureBasis;
    this.thermodynamicStateBasis = thermodynamicStateBasis;
    this.contactPressureBara = contactPressureBara;
    this.sourceReference = sourceReference;
    this.contactStage = contactStage;
  }

  /**
   * Configure one equilibrium contact using a solved qualified Sarir side draw.
   *
   * @param fractionationCase already solved qualified Sarir atmospheric case
   * @param service kerosene or diesel side-stripper service
   * @param preparedSteam independently prepared, essentially pure-water gas stream matching the
   *        selected published steam row
   * @param reportedPressureBasis explicit absolute or gauge interpretation of source pressure
   * @param thermodynamicStateBasis non-blank description of independent steam-state evidence
   * @param contactPressureBara explicit single-contact pressure in bar absolute
   * @return configured source-bounded equilibrium-contact screen
   */
  public static SarirAtmosphericSideStripperContactScreen configure(
      SarirAtmosphericFractionationCase fractionationCase, SteamInjectionService service,
      StreamInterface preparedSteam, ReportedPressureBasis reportedPressureBasis,
      String thermodynamicStateBasis, double contactPressureBara) {
    Objects.requireNonNull(fractionationCase, "fractionationCase");
    Objects.requireNonNull(service, "service");
    Objects.requireNonNull(preparedSteam, "preparedSteam");
    Objects.requireNonNull(reportedPressureBasis, "reportedPressureBasis");
    String stateBasis = requireStateBasis(thermodynamicStateBasis);
    requireFinitePositive(contactPressureBara, "Contact pressure");
    SteamInjectionReference source = requireSourceBoundary(service);
    requireQualifiedColumn(fractionationCase);

    validatePreparedSteam(preparedSteam, source, reportedPressureBasis);
    validateMixerCompatibility(fractionationCase.getFeedStream().getFluid(),
        preparedSteam.getFluid());

    StreamInterface sideDraw = getSideDraw(fractionationCase, service);
    requireFinitePositive(sideDraw.getFlowRate("kg/hr"), "Atmospheric-column side draw");
    if (sideDraw == preparedSteam || preparedSteam == fractionationCase.getFeedStream()) {
      throw new IllegalArgumentException("Prepared steam must be independent of the crude and side draw");
    }

    SimpleTray stage = new SimpleTray("Sarir " + service.name().toLowerCase()
        + " single equilibrium contact");
    stage.addStream(sideDraw);
    stage.addStream(preparedSteam);
    stage.setPressure(contactPressureBara);

    return new SarirAtmosphericSideStripperContactScreen(fractionationCase, service,
        sideDraw, preparedSteam, reportedPressureBasis, stateBasis, contactPressureBara, source,
        stage);
  }

  /**
   * Run the single equilibrium contact and return immutable engineering evidence.
   *
   * @param id calculation identifier
   * @return immutable inlet, outlet, state, provenance, and closure evidence
   */
  public Result run(UUID id) {
    Objects.requireNonNull(id, "id");
    requireQualifiedColumn(fractionationCase);
    SteamInjectionReference currentSource = requireSourceBoundary(service);
    requireSameSourceBoundary(sourceReference, currentSource);
    validatePreparedSteam(steamStream, currentSource, reportedPressureBasis);
    requireFinitePositive(sideDrawStream.getFlowRate("kg/hr"),
        "Atmospheric-column side draw");

    contactStage.run(id);
    StreamInterface vapor = contactStage.getGasOutStream();
    StreamInterface liquid = contactStage.getLiquidOutStream();
    double sideDrawFlow = sideDrawStream.getFlowRate("kg/hr");
    double steamFlow = steamStream.getFlowRate("kg/hr");
    double vaporFlow = vapor.getFlowRate("kg/hr");
    double liquidFlow = liquid.getFlowRate("kg/hr");
    requireFinitePositive(vaporFlow, "Equilibrium-contact vapor product");
    requireFinitePositive(liquidFlow, "Equilibrium-contact liquid product");

    double inletFlow = sideDrawFlow + steamFlow;
    double outletFlow = vaporFlow + liquidFlow;
    double closure = Math.abs(inletFlow - outletFlow) / inletFlow;
    if (!Double.isFinite(closure) || closure > MASS_CLOSURE_TOLERANCE) {
      throw new IllegalStateException("Sarir side-stripper contact does not close its mass balance");
    }

    return new Result(service, sourceReference.getName(), reportedPressureBasis,
        thermodynamicStateBasis, contactPressureBara, sourceReference.getMassFlowRateKgPerHour(),
        steamFlow, sideDrawFlow, vaporFlow, liquidFlow, contactStage.getTemperature(), closure);
  }

  /** @return selected source side-stripper service */
  public SteamInjectionService getService() {
    return service;
  }

  /** @return live calculated atmospheric-column liquid side draw */
  public StreamInterface getSideDrawStream() {
    return sideDrawStream;
  }

  /** @return caller-supplied prepared steam stream */
  public StreamInterface getSteamStream() {
    return steamStream;
  }

  /** @return immutable published steam row */
  public SteamInjectionReference getSourceReference() {
    return sourceReference;
  }

  /** @return explicit single-contact pressure in bar absolute */
  public double getContactPressureBara() {
    return contactPressureBara;
  }

  /** @return retained independent steam-state provenance */
  public String getThermodynamicStateBasis() {
    return thermodynamicStateBasis;
  }

  private static void requireQualifiedColumn(SarirAtmosphericFractionationCase fractionationCase) {
    DistillationColumn column = fractionationCase.getColumn();
    if (!column.solved()
        || column.getLastSolveStatus() != DistillationColumn.SolveStatus.RIGOROUS_CONVERGED) {
      throw new IllegalStateException("Sarir atmospheric column must be rigorously solved first");
    }
  }

  private static StreamInterface getSideDraw(SarirAtmosphericFractionationCase fractionationCase,
      SteamInjectionService service) {
    OperatingInputs inputs = fractionationCase.getOperatingInputs();
    int trayIndex;
    if (service == SteamInjectionService.KEROSENE_SIDE_STRIPPER) {
      trayIndex = inputs.getKeroseneSideDrawTray();
    } else if (service == SteamInjectionService.DIESEL_SIDE_STRIPPER) {
      trayIndex = inputs.getDieselSideDrawTray();
    } else {
      throw new IllegalArgumentException("Only Sarir side-stripper services are supported");
    }
    return fractionationCase.getColumn().getSideDrawStream(trayIndex,
        DistillationColumn.SideDrawPhase.LIQUID);
  }

  private static SteamInjectionReference requireSourceBoundary(SteamInjectionService service) {
    String rowName;
    if (service == SteamInjectionService.KEROSENE_SIDE_STRIPPER) {
      rowName = KEROSENE_SOURCE_ROW;
    } else if (service == SteamInjectionService.DIESEL_SIDE_STRIPPER) {
      rowName = DIESEL_SOURCE_ROW;
    } else {
      throw new IllegalArgumentException("Only Sarir side-stripper services are supported");
    }
    if (SarirAtmosphericReference.hasExplicitSteamInjectionLocations()
        || SarirAtmosphericReference.hasExplicitSteamQuality()
        || SarirAtmosphericReference.hasExplicitSteamThermodynamicState()) {
      throw new IllegalStateException("Sarir steam source semantics changed and require requalification");
    }
    SteamInjectionReference source = SarirAtmosphericReference.getSteamInjection(rowName);
    if (source.getService() != service) {
      throw new IllegalStateException("Sarir side-stripper steam row changed service");
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
      throw new IllegalStateException("Published Sarir side-stripper steam boundary changed");
    }
  }

  private static String requireStateBasis(String thermodynamicStateBasis) {
    if (thermodynamicStateBasis == null || thermodynamicStateBasis.trim().isEmpty()) {
      throw new IllegalArgumentException("An explicit independent thermodynamic-state basis is required");
    }
    return thermodynamicStateBasis.trim();
  }

  private static void validatePreparedSteam(StreamInterface preparedSteam,
      SteamInjectionReference source, ReportedPressureBasis reportedPressureBasis) {
    double expectedPressureKPa = source.getPressureKPa()
        + (reportedPressureBasis == ReportedPressureBasis.GAUGE
            ? STANDARD_ATMOSPHERIC_PRESSURE_KPA : 0.0);
    requireRelativeClose(preparedSteam.getFlowRate("kg/hr"),
        source.getMassFlowRateKgPerHour(), FLOW_RELATIVE_TOLERANCE,
        "Prepared steam mass flow");
    requireAbsoluteClose(preparedSteam.getTemperature("C"), source.getTemperatureCelsius(),
        TEMPERATURE_TOLERANCE_C, "Prepared steam temperature");
    requireAbsoluteClose(preparedSteam.getPressure("bara") * 100.0, expectedPressureKPa,
        PRESSURE_TOLERANCE_KPA, "Prepared steam absolute pressure");

    SystemInterface fluid = preparedSteam.getFluid();
    if (fluid == null || fluid.getNumberOfPhases() != 1
        || fluid.getPhase(0).getType() != PhaseType.GAS) {
      throw new IllegalArgumentException("Prepared steam must expose exactly one gas phase");
    }
    if (!fluid.getPhase(0).hasComponent("water")) {
      throw new IllegalArgumentException("Prepared steam must contain water");
    }
    double waterMoleFraction = fluid.getPhase(0).getComponent("water").getz();
    if (!Double.isFinite(waterMoleFraction)
        || waterMoleFraction < WATER_MOLE_FRACTION_MINIMUM) {
      throw new IllegalArgumentException("Prepared steam must be essentially pure water");
    }
  }

  private static void validateMixerCompatibility(SystemInterface crudeFluid,
      SystemInterface steamFluid) {
    if (crudeFluid == null || steamFluid == null || crudeFluid.getNumberOfPhases() == 0
        || steamFluid.getNumberOfPhases() == 0) {
      throw new IllegalArgumentException("Crude and prepared-steam systems must expose a phase");
    }
    for (int crudeIndex = 0; crudeIndex < crudeFluid.getPhase(0).getNumberOfComponents();
        crudeIndex++) {
      String crudeName = crudeFluid.getPhase(0).getComponent(crudeIndex).getName();
      int crudeNumber = crudeFluid.getPhase(0).getComponent(crudeIndex).getComponentNumber();
      boolean compatible = false;
      for (int steamIndex = 0; steamIndex < steamFluid.getPhase(0).getNumberOfComponents();
          steamIndex++) {
        if (crudeName.equals(steamFluid.getPhase(0).getComponent(steamIndex).getName())
            && crudeNumber
                == steamFluid.getPhase(0).getComponent(steamIndex).getComponentNumber()) {
          compatible = true;
          break;
        }
      }
      if (!compatible) {
        throw new IllegalArgumentException(
            "Prepared steam must retain the crude component slate and numbering");
      }
    }
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
      throw new IllegalArgumentException(label + " must be finite and positive");
    }
  }

  private static void requireFinite(double value, String label) {
    if (!Double.isFinite(value)) {
      throw new IllegalStateException(label + " must be finite");
    }
  }

  /** Immutable source, boundary, product, state, provenance, and closure evidence. */
  public static final class Result {
    private final SteamInjectionService service;
    private final String sourceRowName;
    private final ReportedPressureBasis reportedPressureBasis;
    private final String thermodynamicStateBasis;
    private final double contactPressureBara;
    private final double sourceSteamMassFlowKgPerHour;
    private final double modeledSteamMassFlowKgPerHour;
    private final double sideDrawMassFlowKgPerHour;
    private final double vaporProductMassFlowKgPerHour;
    private final double liquidProductMassFlowKgPerHour;
    private final double contactTemperatureKelvin;
    private final double massClosureRelativeError;

    private Result(SteamInjectionService service, String sourceRowName,
        ReportedPressureBasis reportedPressureBasis, String thermodynamicStateBasis,
        double contactPressureBara, double sourceSteamMassFlowKgPerHour,
        double modeledSteamMassFlowKgPerHour, double sideDrawMassFlowKgPerHour,
        double vaporProductMassFlowKgPerHour, double liquidProductMassFlowKgPerHour,
        double contactTemperatureKelvin, double massClosureRelativeError) {
      this.service = service;
      this.sourceRowName = sourceRowName;
      this.reportedPressureBasis = reportedPressureBasis;
      this.thermodynamicStateBasis = thermodynamicStateBasis;
      this.contactPressureBara = contactPressureBara;
      this.sourceSteamMassFlowKgPerHour = sourceSteamMassFlowKgPerHour;
      this.modeledSteamMassFlowKgPerHour = modeledSteamMassFlowKgPerHour;
      this.sideDrawMassFlowKgPerHour = sideDrawMassFlowKgPerHour;
      this.vaporProductMassFlowKgPerHour = vaporProductMassFlowKgPerHour;
      this.liquidProductMassFlowKgPerHour = liquidProductMassFlowKgPerHour;
      this.contactTemperatureKelvin = contactTemperatureKelvin;
      this.massClosureRelativeError = massClosureRelativeError;
    }

    /** @return selected side-stripper service */
    public SteamInjectionService getService() {
      return service;
    }

    /** @return exact published source-row label */
    public String getSourceRowName() {
      return sourceRowName;
    }

    /** @return caller interpretation of the source pressure column */
    public ReportedPressureBasis getReportedPressureBasis() {
      return reportedPressureBasis;
    }

    /** @return retained description of independently established steam state */
    public String getThermodynamicStateBasis() {
      return thermodynamicStateBasis;
    }

    /** @return explicit equilibrium-contact pressure in bar absolute */
    public double getContactPressureBara() {
      return contactPressureBara;
    }

    /** @return published steam mass-flow rate in kg/h */
    public double getSourceSteamMassFlowKgPerHour() {
      return sourceSteamMassFlowKgPerHour;
    }

    /** @return prepared steam mass-flow rate in kg/h */
    public double getModeledSteamMassFlowKgPerHour() {
      return modeledSteamMassFlowKgPerHour;
    }

    /** @return atmospheric-column liquid side-draw rate in kg/h */
    public double getSideDrawMassFlowKgPerHour() {
      return sideDrawMassFlowKgPerHour;
    }

    /** @return equilibrium-contact vapor product rate in kg/h */
    public double getVaporProductMassFlowKgPerHour() {
      return vaporProductMassFlowKgPerHour;
    }

    /** @return equilibrium-contact liquid product rate in kg/h */
    public double getLiquidProductMassFlowKgPerHour() {
      return liquidProductMassFlowKgPerHour;
    }

    /** @return calculated equilibrium-contact temperature in kelvin */
    public double getContactTemperatureKelvin() {
      return contactTemperatureKelvin;
    }

    /** @return absolute relative mass-closure error */
    public double getMassClosureRelativeError() {
      return massClosureRelativeError;
    }
  }
}
