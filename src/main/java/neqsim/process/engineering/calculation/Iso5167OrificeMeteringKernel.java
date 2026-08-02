package neqsim.process.engineering.calculation;

import java.io.Serializable;
import neqsim.process.equipment.diffpressure.Orifice;
import neqsim.process.mechanicaldesign.designstandards.StandardApplicability;
import neqsim.process.mechanicaldesign.designstandards.StandardEdition;
import neqsim.process.mechanicaldesign.designstandards.StandardSupportLevel;
import neqsim.process.mechanicaldesign.designstandards.StandardType;

/** Pure, edition-aware adapter around NeqSim's existing ISO 5167 orifice equations. */
public final class Iso5167OrificeMeteringKernel
    implements EquipmentDesignKernel<Iso5167OrificeMeteringKernel.Input, Iso5167OrificeMeteringAssessment> {
  private static final long serialVersionUID = 1000L;
  private static final String IMPLEMENTED_EDITION = "2022";
  private static final int MAX_ITERATIONS = 100;
  private static final double RELATIVE_TOLERANCE = 1.0e-10;

  /** Fluid service controls whether the expansibility correction is applied. */
  public enum ServiceType {
    /** Incompressible single-phase liquid; expansibility factor is one. */
    LIQUID,
    /** Single-phase gas or vapour; the existing ISO expansibility correlation is applied. */
    GAS_OR_VAPOUR
  }

  /** ISO 5167-2 pressure-tapping arrangements supported by the existing correlation. */
  public enum TapType {
    /** Corner pressure tappings. */
    CORNER("corner"),
    /** Flange pressure tappings. */
    FLANGE("flange"),
    /** Upstream D and downstream D/2 pressure tappings. */
    D_AND_D_OVER_2("D");

    private final String legacyValue;

    TapType(String legacyValue) {
      this.legacyValue = legacyValue;
    }

    String legacyValue() {
      return legacyValue;
    }
  }

  /** Immutable, unit-explicit orifice-metering input. */
  public static final class Input implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final StandardEdition edition;
    private final String equipmentType;
    private final ServiceType serviceType;
    private final TapType tapType;
    private final double pipeInternalDiameterM;
    private final double orificeBoreDiameterM;
    private final double upstreamPressurePaAbsolute;
    private final double downstreamPressurePaAbsolute;
    private final double upstreamDensityKgPerM3;
    private final double upstreamDynamicViscosityPaS;
    private final double isentropicExponent;
    private final boolean singlePhase;
    private final boolean conduitRunningFull;
    private final boolean subsonicThroughoutMeter;
    private final boolean pulsatingFlow;
    private final boolean geometryAndInstallationVerified;

    private Input(Builder builder) {
      edition = builder.edition;
      equipmentType = builder.equipmentType;
      serviceType = builder.serviceType;
      tapType = builder.tapType;
      pipeInternalDiameterM = builder.pipeInternalDiameterM;
      orificeBoreDiameterM = builder.orificeBoreDiameterM;
      upstreamPressurePaAbsolute = builder.upstreamPressurePaAbsolute;
      downstreamPressurePaAbsolute = builder.downstreamPressurePaAbsolute;
      upstreamDensityKgPerM3 = builder.upstreamDensityKgPerM3;
      upstreamDynamicViscosityPaS = builder.upstreamDynamicViscosityPaS;
      isentropicExponent = builder.isentropicExponent;
      singlePhase = builder.singlePhase;
      conduitRunningFull = builder.conduitRunningFull;
      subsonicThroughoutMeter = builder.subsonicThroughoutMeter;
      pulsatingFlow = builder.pulsatingFlow;
      geometryAndInstallationVerified = builder.geometryAndInstallationVerified;
    }

    /**
     * Create a builder with an explicit standard and equipment basis.
     *
     * @param edition ISO 5167-2 edition
     * @param equipmentType simple equipment class name
     * @return input builder
     */
    public static Builder builder(StandardEdition edition, String equipmentType) {
      return new Builder(edition, equipmentType);
    }

    /** @return explicit ISO 5167-2 edition */
    public StandardEdition getEdition() {
      return edition;
    }

    /** @return simple equipment class name */
    public String getEquipmentType() {
      return equipmentType;
    }

    /** @return fluid service */
    public ServiceType getServiceType() {
      return serviceType;
    }

    /** @return pressure-tapping arrangement */
    public TapType getTapType() {
      return tapType;
    }

    /** @return upstream pipe internal diameter in metres */
    public double getPipeInternalDiameterM() {
      return pipeInternalDiameterM;
    }

    /** @return orifice bore diameter in metres */
    public double getOrificeBoreDiameterM() {
      return orificeBoreDiameterM;
    }

    /** @return upstream static pressure in pascals absolute */
    public double getUpstreamPressurePaAbsolute() {
      return upstreamPressurePaAbsolute;
    }

    /** @return downstream static pressure in pascals absolute */
    public double getDownstreamPressurePaAbsolute() {
      return downstreamPressurePaAbsolute;
    }

    /** @return upstream density in kg/m3 */
    public double getUpstreamDensityKgPerM3() {
      return upstreamDensityKgPerM3;
    }

    /** @return upstream dynamic viscosity in Pa.s */
    public double getUpstreamDynamicViscosityPaS() {
      return upstreamDynamicViscosityPaS;
    }

    /** @return isentropic exponent; required for gas or vapour service */
    public double getIsentropicExponent() {
      return isentropicExponent;
    }

    /** @return whether the supplied condition is single phase */
    public boolean isSinglePhase() {
      return singlePhase;
    }

    /** @return whether the circular conduit is running full */
    public boolean isConduitRunningFull() {
      return conduitRunningFull;
    }

    /** @return whether flow remains subsonic throughout the measuring section */
    public boolean isSubsonicThroughoutMeter() {
      return subsonicThroughoutMeter;
    }

    /** @return whether the supplied flow is pulsating */
    public boolean isPulsatingFlow() {
      return pulsatingFlow;
    }

    /** @return whether geometry and installation were verified outside this calculator */
    public boolean isGeometryAndInstallationVerified() {
      return geometryAndInstallationVerified;
    }

    /** Builder that retains raw values so readiness checks can reject them without silent correction. */
    public static final class Builder {
      private final StandardEdition edition;
      private final String equipmentType;
      private ServiceType serviceType;
      private TapType tapType;
      private double pipeInternalDiameterM = Double.NaN;
      private double orificeBoreDiameterM = Double.NaN;
      private double upstreamPressurePaAbsolute = Double.NaN;
      private double downstreamPressurePaAbsolute = Double.NaN;
      private double upstreamDensityKgPerM3 = Double.NaN;
      private double upstreamDynamicViscosityPaS = Double.NaN;
      private double isentropicExponent = Double.NaN;
      private boolean singlePhase;
      private boolean conduitRunningFull;
      private boolean subsonicThroughoutMeter;
      private boolean pulsatingFlow;
      private boolean geometryAndInstallationVerified;

      private Builder(StandardEdition edition, String equipmentType) {
        if (edition == null || edition.getStandardType() != StandardType.ISO_5167_2) {
          throw new IllegalArgumentException("edition must identify ISO-5167-2");
        }
        if (equipmentType == null || equipmentType.trim().isEmpty()) {
          throw new IllegalArgumentException("equipmentType cannot be null or blank");
        }
        this.edition = edition;
        this.equipmentType = equipmentType.trim();
      }

      /**
       * @param value fluid service
       * @return this builder
       */
      public Builder serviceType(ServiceType value) {
        serviceType = value;
        return this;
      }

      /**
       * @param value pressure-tapping arrangement
       * @return this builder
       */
      public Builder tapType(TapType value) {
        tapType = value;
        return this;
      }

      /**
       * @param value upstream pipe internal diameter in metres
       * @return this builder
       */
      public Builder pipeInternalDiameterM(double value) {
        pipeInternalDiameterM = value;
        return this;
      }

      /**
       * @param value orifice bore diameter in metres
       * @return this builder
       */
      public Builder orificeBoreDiameterM(double value) {
        orificeBoreDiameterM = value;
        return this;
      }

      /**
       * @param value upstream static pressure in pascals absolute
       * @return this builder
       */
      public Builder upstreamPressurePaAbsolute(double value) {
        upstreamPressurePaAbsolute = value;
        return this;
      }

      /**
       * @param value downstream static pressure in pascals absolute
       * @return this builder
       */
      public Builder downstreamPressurePaAbsolute(double value) {
        downstreamPressurePaAbsolute = value;
        return this;
      }

      /**
       * @param value upstream density in kg/m3
       * @return this builder
       */
      public Builder upstreamDensityKgPerM3(double value) {
        upstreamDensityKgPerM3 = value;
        return this;
      }

      /**
       * @param value upstream dynamic viscosity in Pa.s
       * @return this builder
       */
      public Builder upstreamDynamicViscosityPaS(double value) {
        upstreamDynamicViscosityPaS = value;
        return this;
      }

      /**
       * @param value isentropic exponent for gas or vapour service
       * @return this builder
       */
      public Builder isentropicExponent(double value) {
        isentropicExponent = value;
        return this;
      }

      /**
       * @param value whether the condition is single phase
       * @return this builder
       */
      public Builder singlePhase(boolean value) {
        singlePhase = value;
        return this;
      }

      /**
       * @param value whether the conduit is running full
       * @return this builder
       */
      public Builder conduitRunningFull(boolean value) {
        conduitRunningFull = value;
        return this;
      }

      /**
       * @param value whether flow remains subsonic through the meter
       * @return this builder
       */
      public Builder subsonicThroughoutMeter(boolean value) {
        subsonicThroughoutMeter = value;
        return this;
      }

      /**
       * @param value whether the flow is pulsating
       * @return this builder
       */
      public Builder pulsatingFlow(boolean value) {
        pulsatingFlow = value;
        return this;
      }

      /**
       * @param value whether geometry and installation have external verification
       * @return this builder
       */
      public Builder geometryAndInstallationVerified(boolean value) {
        geometryAndInstallationVerified = value;
        return this;
      }

      /** @return immutable input retaining every supplied value */
      public Input build() {
        return new Input(this);
      }
    }
  }

  /** Package-visible numeric snapshot shared with the immutable assessment. */
  static final class Computation {
    final double betaRatio;
    final double differentialPressurePa;
    final double pressureRatio;
    final double dischargeCoefficient;
    final double expansibilityFactor;
    final double velocityOfApproachFactor;
    final double massFlowRateKgPerS;
    final double actualVolumeFlowRateM3PerS;
    final double pipeReynoldsNumber;
    final double permanentPressureLossPa;
    final int iterations;
    final boolean converged;

    Computation(double betaRatio, double differentialPressurePa, double pressureRatio, double dischargeCoefficient,
        double expansibilityFactor, double velocityOfApproachFactor, double massFlowRateKgPerS,
        double actualVolumeFlowRateM3PerS, double pipeReynoldsNumber, double permanentPressureLossPa, int iterations,
        boolean converged) {
      this.betaRatio = betaRatio;
      this.differentialPressurePa = differentialPressurePa;
      this.pressureRatio = pressureRatio;
      this.dischargeCoefficient = dischargeCoefficient;
      this.expansibilityFactor = expansibilityFactor;
      this.velocityOfApproachFactor = velocityOfApproachFactor;
      this.massFlowRateKgPerS = massFlowRateKgPerS;
      this.actualVolumeFlowRateM3PerS = actualVolumeFlowRateM3PerS;
      this.pipeReynoldsNumber = pipeReynoldsNumber;
      this.permanentPressureLossPa = permanentPressureLossPa;
      this.iterations = iterations;
      this.converged = converged;
    }
  }

  /** {@inheritDoc} */
  @Override
  public StandardType standard() {
    return StandardType.ISO_5167_2;
  }

  /** {@inheritDoc} */
  @Override
  public StandardSupportLevel maturity() {
    return StandardSupportLevel.SCREENING;
  }

  /** {@inheritDoc} */
  @Override
  public boolean supports(StandardEdition edition) {
    return edition != null && edition.getStandardType() == standard()
        && IMPLEMENTED_EDITION.equalsIgnoreCase(edition.getEdition()) && edition.getAmendments().isEmpty();
  }

  /** {@inheritDoc} */
  @Override
  public StandardApplicability applicability(Input input) {
    return StandardApplicability.assess(standard(), input == null ? null : input.getEquipmentType());
  }

  /** {@inheritDoc} */
  @Override
  public String getMethod() {
    return "iso-5167-2-orifice-metering-screening";
  }

  /** {@inheritDoc} */
  @Override
  public String getMethodVersion() {
    return "1.0.0";
  }

  /** {@inheritDoc} */
  @Override
  public CalculationReadiness assess(Input input, EngineeringCalculationContext context) {
    CalculationReadiness.Builder readiness = CalculationReadiness.builder();
    if (input == null) {
      return readiness.addBlocker("ISO5167_INPUT_MISSING", "ISO 5167-2 orifice-metering input is required",
          "Provide an explicit edition, geometry, pressure, property, tapping, and applicability basis").build();
    }

    StandardApplicability decision = applicability(input);
    if (!decision.isApplicable()) {
      readiness.addBlocker("ISO5167_NOT_APPLICABLE", decision.getReason(), "Use an Orifice equipment type");
    }
    if (!supports(input.getEdition())) {
      readiness.addBlocker("ISO5167_EDITION_NOT_IMPLEMENTED",
          "The kernel implements the unamended 2022 edition of Part 2, not " + input.getEdition().getDisplayName(),
          "Select ISO-5167-2:2022 without amendments or add separately validated edition criteria");
    }
    if (input.getServiceType() == null) {
      readiness.addBlocker("ISO5167_SERVICE_MISSING", "Fluid service is required",
          "Select LIQUID or GAS_OR_VAPOUR explicitly");
    }
    if (input.getTapType() == null) {
      readiness.addBlocker("ISO5167_TAP_TYPE_MISSING", "A supported pressure-tapping arrangement is required",
          "Select corner, flange, or D and D/2 tappings");
    }
    if (!inRange(input.getPipeInternalDiameterM(), 0.05, 1.0)) {
      readiness.addBlocker("ISO5167_PIPE_DIAMETER_OUT_OF_RANGE",
          "Pipe internal diameter must be finite and within 0.05 to 1.0 m",
          "Supply a circular conduit inside the published Part 2 pipe-size scope");
    }
    if (!positive(input.getOrificeBoreDiameterM()) || positive(input.getPipeInternalDiameterM())
        && input.getOrificeBoreDiameterM() >= input.getPipeInternalDiameterM()) {
      readiness.addBlocker("ISO5167_BORE_DIAMETER_INVALID",
          "Orifice bore diameter must be finite, positive, and smaller than the pipe internal diameter",
          "Supply the bore diameter at flowing conditions in metres");
    }
    if (positive(input.getPipeInternalDiameterM()) && positive(input.getOrificeBoreDiameterM())) {
      double beta = input.getOrificeBoreDiameterM() / input.getPipeInternalDiameterM();
      if (!inRange(beta, 0.1, 0.75)) {
        readiness.addBlocker("ISO5167_BETA_RATIO_OUT_OF_RANGE",
            "The implemented orifice-plate screening envelope requires beta from 0.10 to 0.75",
            "Revise the meter geometry or use a separately qualified method");
      }
    }
    if (!positive(input.getUpstreamPressurePaAbsolute())) {
      readiness.addBlocker("ISO5167_UPSTREAM_PRESSURE_INVALID",
          "Upstream static pressure must be finite, positive, and absolute", "Supply upstream pressure in Pa absolute");
    }
    if (!positive(input.getDownstreamPressurePaAbsolute()) || positive(input.getUpstreamPressurePaAbsolute())
        && input.getDownstreamPressurePaAbsolute() >= input.getUpstreamPressurePaAbsolute()) {
      readiness.addBlocker("ISO5167_DOWNSTREAM_PRESSURE_INVALID",
          "Downstream static pressure must be finite, positive, absolute, and below upstream pressure",
          "Supply downstream pressure in Pa absolute");
    }
    if (!positive(input.getUpstreamDensityKgPerM3())) {
      readiness.addBlocker("ISO5167_DENSITY_INVALID", "Upstream density must be finite and positive",
          "Supply single-phase density at the upstream tapping in kg/m3");
    }
    if (!positive(input.getUpstreamDynamicViscosityPaS())) {
      readiness.addBlocker("ISO5167_VISCOSITY_INVALID", "Upstream dynamic viscosity must be finite and positive",
          "Supply single-phase dynamic viscosity at the upstream tapping in Pa.s");
    }
    if (input.getServiceType() == ServiceType.GAS_OR_VAPOUR
        && (!Double.isFinite(input.getIsentropicExponent()) || input.getIsentropicExponent() <= 1.0)) {
      readiness.addBlocker("ISO5167_ISENTROPIC_EXPONENT_INVALID",
          "Gas or vapour service requires a finite isentropic exponent above 1",
          "Supply the flowing-condition isentropic exponent");
    }
    if (!input.isSinglePhase()) {
      readiness.addBlocker("ISO5167_SINGLE_PHASE_REQUIRED", "ISO 5167 screening is limited to single-phase flow",
          "Use a qualified multiphase metering method or establish a single-phase condition");
    }
    if (!input.isConduitRunningFull()) {
      readiness.addBlocker("ISO5167_FULL_PIPE_REQUIRED", "The circular conduit must be running full",
          "Establish full-pipe operation at the measuring section");
    }
    if (!input.isSubsonicThroughoutMeter()) {
      readiness.addBlocker("ISO5167_SUBSONIC_REQUIRED", "Flow must remain subsonic throughout the measuring section",
          "Verify the flow regime or use a method qualified for choked flow");
    }
    if (input.isPulsatingFlow()) {
      readiness.addBlocker("ISO5167_PULSATING_FLOW_NOT_COVERED", "Pulsating flow is outside the implemented scope",
          "Resolve the pulsation or use a separately qualified dynamic metering method");
    }
    if (!input.isGeometryAndInstallationVerified()) {
      readiness.addBlocker("ISO5167_INSTALLATION_NOT_VERIFIED",
          "Orifice geometry, pressure tappings, straight lengths, and installation require external verification",
          "Verify the meter against the purchased ISO 5167-1 and ISO 5167-2 documents and record the evidence");
    }

    CalculationReadiness preliminary = readiness.build();
    if (preliminary.isReady()) {
      Computation computation = compute(input);
      if (!computation.converged || !finitePositive(computation.massFlowRateKgPerS)
          || !finitePositive(computation.dischargeCoefficient)) {
        readiness.addBlocker("ISO5167_NUMERICAL_FAILURE", "The discharge-coefficient iteration did not converge",
            "Review the condition and use a separately verified solver if the failure persists");
      } else if (computation.pipeReynoldsNumber < 5000.0) {
        readiness.addBlocker("ISO5167_REYNOLDS_NUMBER_OUT_OF_RANGE",
            "Calculated pipe Reynolds number is below the published Part 2 lower limit of 5000",
            "Increase Reynolds number or use a method qualified for the low-Reynolds-number condition");
      }
      if (!Double.isFinite(computation.expansibilityFactor) || computation.expansibilityFactor <= 0.0
          || computation.expansibilityFactor > 1.0) {
        readiness.addBlocker("ISO5167_EXPANSIBILITY_INVALID",
            "The calculated expansibility factor is outside the physical interval above 0 and at most 1",
            "Review pressure ratio, service selection, and isentropic exponent");
      }
    }

    readiness.addWarning("ISO5167_EXTERNAL_ATTESTATION",
        "The geometry and installation flag is a caller attestation; NeqSim does not inspect the installed meter",
        "Retain plate inspection, tapping, straight-length, calibration, and installation evidence");
    readiness.addWarning("ISO5167_SCREENING_ONLY",
        "The adapter calculates single-phase orifice flow but is not a conformity or custody-transfer assessment",
        "Complete uncertainty, calibration, installation, data-quality, and accountable engineering review");
    return readiness.build();
  }

  /** {@inheritDoc} */
  @Override
  public EngineeringCalculationResult<Iso5167OrificeMeteringAssessment> calculate(Input input,
      EngineeringCalculationContext context) {
    EngineeringCalculationContext effectiveContext = context == null ? EngineeringCalculationContext.builder().build()
        : context;
    CalculationReadiness readiness = assess(input, effectiveContext);
    EngineeringCalculationResult.Builder<Iso5167OrificeMeteringAssessment> result = EngineeringCalculationResult
        .<Iso5167OrificeMeteringAssessment>builder("iso-5167-2-orifice-metering-screening", getMethod(),
            getMethodVersion())
        .context(effectiveContext).readiness(readiness);
    if (input == null || !readiness.isReady()) {
      return result.status(EngineeringCalculationResult.Status.BLOCKED)
          .message("ISO 5167-2 orifice metering is blocked until the readiness findings are resolved").build();
    }

    Computation computation = compute(input);
    Iso5167OrificeMeteringAssessment assessment = new Iso5167OrificeMeteringAssessment(input, computation);
    return result.status(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED).value(assessment)
        .input("standard", input.getEdition().getDisplayName()).input("companionStandard", "ISO-5167-1 2022")
        .input("equipmentType", input.getEquipmentType()).input("serviceType", input.getServiceType().name())
        .input("tapType", input.getTapType().name())
        .input("pipeInternalDiameterM", Double.valueOf(input.getPipeInternalDiameterM()))
        .input("orificeBoreDiameterM", Double.valueOf(input.getOrificeBoreDiameterM()))
        .input("upstreamPressurePaAbsolute", Double.valueOf(input.getUpstreamPressurePaAbsolute()))
        .input("downstreamPressurePaAbsolute", Double.valueOf(input.getDownstreamPressurePaAbsolute()))
        .input("upstreamDensityKgPerM3", Double.valueOf(input.getUpstreamDensityKgPerM3()))
        .input("upstreamDynamicViscosityPaS", Double.valueOf(input.getUpstreamDynamicViscosityPaS()))
        .input("isentropicExponent",
            Double.isNaN(input.getIsentropicExponent()) ? null : Double.valueOf(input.getIsentropicExponent()))
        .input("singlePhase", Boolean.valueOf(input.isSinglePhase()))
        .input("conduitRunningFull", Boolean.valueOf(input.isConduitRunningFull()))
        .input("subsonicThroughoutMeter", Boolean.valueOf(input.isSubsonicThroughoutMeter()))
        .input("pulsatingFlow", Boolean.valueOf(input.isPulsatingFlow()))
        .input("geometryAndInstallationVerified", Boolean.valueOf(input.isGeometryAndInstallationVerified()))
        .warning("Part 2 is applied with the general requirements of ISO 5167-1:2022")
        .warning("The result excludes measurement uncertainty, calibration, installation inspection, and acceptance")
        .message("ISO 5167-2 orifice-flow screening completed; independent metering review remains required").build();
  }

  private static Computation compute(Input input) {
    double pipeDiameter = input.getPipeInternalDiameterM();
    double boreDiameter = input.getOrificeBoreDiameterM();
    double density = input.getUpstreamDensityKgPerM3();
    double viscosity = input.getUpstreamDynamicViscosityPaS();
    double upstreamPressure = input.getUpstreamPressurePaAbsolute();
    double downstreamPressure = input.getDownstreamPressurePaAbsolute();
    double beta = Orifice.calculateBetaRatio(pipeDiameter, boreDiameter);
    double beta4 = Math.pow(beta, 4.0);
    double differentialPressure = upstreamPressure - downstreamPressure;
    double expansibility = input.getServiceType() == ServiceType.LIQUID ? 1.0
        : Orifice.calculateExpansibility(pipeDiameter, boreDiameter, upstreamPressure, downstreamPressure,
            input.getIsentropicExponent());
    double velocityOfApproach = 1.0 / Math.sqrt(1.0 - beta4);
    double area = 0.25 * Math.PI * boreDiameter * boreDiameter;
    double flowScale = area * expansibility * Math.sqrt(2.0 * density * differentialPressure / (1.0 - beta4));
    double massFlow = 0.61 * flowScale;
    double dischargeCoefficient = Double.NaN;
    boolean converged = false;
    int iterations = 0;
    for (int index = 0; index < MAX_ITERATIONS; index++) {
      iterations = index + 1;
      dischargeCoefficient = Orifice.calculateDischargeCoefficient(pipeDiameter, boreDiameter, density, viscosity,
          massFlow, input.getTapType().legacyValue());
      double nextMassFlow = dischargeCoefficient * flowScale;
      if (!finitePositive(nextMassFlow)) {
        massFlow = nextMassFlow;
        break;
      }
      double relativeChange = Math.abs(nextMassFlow - massFlow) / Math.max(Math.abs(nextMassFlow), 1.0e-30);
      massFlow = nextMassFlow;
      if (relativeChange <= RELATIVE_TOLERANCE) {
        converged = true;
        break;
      }
    }
    if (finitePositive(massFlow)) {
      dischargeCoefficient = Orifice.calculateDischargeCoefficient(pipeDiameter, boreDiameter, density, viscosity,
          massFlow, input.getTapType().legacyValue());
      massFlow = dischargeCoefficient * flowScale;
    }
    double reynoldsNumber = 4.0 * massFlow / (Math.PI * pipeDiameter * viscosity);
    double permanentPressureLoss = Orifice.calculatePressureDrop(pipeDiameter, boreDiameter, upstreamPressure,
        downstreamPressure, dischargeCoefficient);
    return new Computation(beta, differentialPressure, downstreamPressure / upstreamPressure, dischargeCoefficient,
        expansibility, velocityOfApproach, massFlow, massFlow / density, reynoldsNumber, permanentPressureLoss,
        iterations, converged);
  }

  private static boolean positive(double value) {
    return Double.isFinite(value) && value > 0.0;
  }

  private static boolean finitePositive(double value) {
    return Double.isFinite(value) && value > 0.0;
  }

  private static boolean inRange(double value, double lower, double upper) {
    return Double.isFinite(value) && value >= lower && value <= upper;
  }
}
