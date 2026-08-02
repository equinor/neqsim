package neqsim.process.engineering.calculation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import neqsim.process.mechanicaldesign.designstandards.StandardApplicability;
import neqsim.process.mechanicaldesign.designstandards.StandardEdition;
import neqsim.process.mechanicaldesign.designstandards.StandardSupportLevel;
import neqsim.process.mechanicaldesign.designstandards.StandardType;

/** Edition-aware caller-controlled CO2 pipeline transport-envelope screening for DNV-RP-F104. */
public final class DnvRpF104Co2PipelineEnvelopeScreeningKernel implements
    EquipmentDesignKernel<DnvRpF104Co2PipelineEnvelopeScreeningKernel.Input, DnvRpF104Co2PipelineEnvelopeAssessment> {
  private static final long serialVersionUID = 1000L;
  private static final String IMPLEMENTED_EDITION = "2021-02+AMD:2021-09";

  /** Immutable ordered pressure-temperature profile point with an external phase boundary. */
  public static final class OperatingPoint implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String label;
    private final double distanceM;
    private final double pressurePaAbsolute;
    private final double temperatureK;
    private final double callerControlledMinimumSinglePhasePressurePaAbsolute;

    /**
     * Create one transport-profile point.
     *
     * @param label caller-controlled point label
     * @param distanceM distance from the profile origin in m
     * @param pressurePaAbsolute operating pressure in Pa absolute
     * @param temperatureK operating temperature in K
     * @param callerControlledMinimumSinglePhasePressurePaAbsolute externally derived pressure boundary in Pa absolute
     */
    public OperatingPoint(String label, double distanceM, double pressurePaAbsolute, double temperatureK,
        double callerControlledMinimumSinglePhasePressurePaAbsolute) {
      this.label = label;
      this.distanceM = distanceM;
      this.pressurePaAbsolute = pressurePaAbsolute;
      this.temperatureK = temperatureK;
      this.callerControlledMinimumSinglePhasePressurePaAbsolute = callerControlledMinimumSinglePhasePressurePaAbsolute;
    }

    /** @return caller-controlled point label */
    public String getLabel() {
      return label;
    }

    /** @return distance from the profile origin in m */
    public double getDistanceM() {
      return distanceM;
    }

    /** @return operating pressure in Pa absolute */
    public double getPressurePaAbsolute() {
      return pressurePaAbsolute;
    }

    /** @return operating temperature in K */
    public double getTemperatureK() {
      return temperatureK;
    }

    /** @return caller-controlled minimum single-phase pressure boundary in Pa absolute */
    public double getCallerControlledMinimumSinglePhasePressurePaAbsolute() {
      return callerControlledMinimumSinglePhasePressurePaAbsolute;
    }
  }

  /** Immutable, unit-explicit input for one CO2 pipeline transport-envelope screen. */
  public static final class Input implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final StandardEdition edition;
    private final String equipmentType;
    private final double co2MoleFraction;
    private final double minimumCo2MoleFraction;
    private final double waterMoleFraction;
    private final double maximumWaterMoleFraction;
    private final boolean otherImpuritiesWithinProjectSpecification;
    private final double designMinimumTemperatureK;
    private final double designMaximumTemperatureK;
    private final double maximumAllowableOperatingPressurePaAbsolute;
    private final List<OperatingPoint> operatingPoints;
    private final boolean co2PipelineApplicabilityVerified;
    private final boolean compositionAndSpecificationVerified;
    private final boolean thermodynamicModelVerified;
    private final boolean singlePhaseBoundaryInterpretationVerified;
    private final boolean operatingProfileVerified;
    private final boolean pressureTemperatureLimitsVerified;
    private final boolean materialsCorrosionAndFractureBasisVerified;
    private final boolean safetyConstructionOperationsAndRequalificationReviewed;

    private Input(Builder builder) {
      edition = builder.edition;
      equipmentType = builder.equipmentType;
      co2MoleFraction = builder.co2MoleFraction;
      minimumCo2MoleFraction = builder.minimumCo2MoleFraction;
      waterMoleFraction = builder.waterMoleFraction;
      maximumWaterMoleFraction = builder.maximumWaterMoleFraction;
      otherImpuritiesWithinProjectSpecification = builder.otherImpuritiesWithinProjectSpecification;
      designMinimumTemperatureK = builder.designMinimumTemperatureK;
      designMaximumTemperatureK = builder.designMaximumTemperatureK;
      maximumAllowableOperatingPressurePaAbsolute = builder.maximumAllowableOperatingPressurePaAbsolute;
      operatingPoints = Collections.unmodifiableList(new ArrayList<OperatingPoint>(builder.operatingPoints));
      co2PipelineApplicabilityVerified = builder.co2PipelineApplicabilityVerified;
      compositionAndSpecificationVerified = builder.compositionAndSpecificationVerified;
      thermodynamicModelVerified = builder.thermodynamicModelVerified;
      singlePhaseBoundaryInterpretationVerified = builder.singlePhaseBoundaryInterpretationVerified;
      operatingProfileVerified = builder.operatingProfileVerified;
      pressureTemperatureLimitsVerified = builder.pressureTemperatureLimitsVerified;
      materialsCorrosionAndFractureBasisVerified = builder.materialsCorrosionAndFractureBasisVerified;
      safetyConstructionOperationsAndRequalificationReviewed = builder.safetyConstructionOperationsAndRequalificationReviewed;
    }

    /**
     * Create a DNV-RP-F104 input builder.
     *
     * @param edition DNV-RP-F104 edition
     * @param equipmentType supported pipeline equipment type
     * @return input builder
     */
    public static Builder builder(StandardEdition edition, String equipmentType) {
      return new Builder(edition, equipmentType);
    }

    public StandardEdition getEdition() {
      return edition;
    }

    public String getEquipmentType() {
      return equipmentType;
    }

    public double getCo2MoleFraction() {
      return co2MoleFraction;
    }

    public double getMinimumCo2MoleFraction() {
      return minimumCo2MoleFraction;
    }

    public double getWaterMoleFraction() {
      return waterMoleFraction;
    }

    public double getMaximumWaterMoleFraction() {
      return maximumWaterMoleFraction;
    }

    public boolean isOtherImpuritiesWithinProjectSpecification() {
      return otherImpuritiesWithinProjectSpecification;
    }

    public double getDesignMinimumTemperatureK() {
      return designMinimumTemperatureK;
    }

    public double getDesignMaximumTemperatureK() {
      return designMaximumTemperatureK;
    }

    public double getMaximumAllowableOperatingPressurePaAbsolute() {
      return maximumAllowableOperatingPressurePaAbsolute;
    }

    public List<OperatingPoint> getOperatingPoints() {
      return Collections.unmodifiableList(new ArrayList<OperatingPoint>(operatingPoints));
    }

    public boolean isCo2PipelineApplicabilityVerified() {
      return co2PipelineApplicabilityVerified;
    }

    public boolean isCompositionAndSpecificationVerified() {
      return compositionAndSpecificationVerified;
    }

    public boolean isThermodynamicModelVerified() {
      return thermodynamicModelVerified;
    }

    public boolean isSinglePhaseBoundaryInterpretationVerified() {
      return singlePhaseBoundaryInterpretationVerified;
    }

    public boolean isOperatingProfileVerified() {
      return operatingProfileVerified;
    }

    public boolean isPressureTemperatureLimitsVerified() {
      return pressureTemperatureLimitsVerified;
    }

    public boolean isMaterialsCorrosionAndFractureBasisVerified() {
      return materialsCorrosionAndFractureBasisVerified;
    }

    public boolean isSafetyConstructionOperationsAndRequalificationReviewed() {
      return safetyConstructionOperationsAndRequalificationReviewed;
    }

    /** Builder retaining raw values for fail-closed readiness assessment. */
    public static final class Builder {
      private final StandardEdition edition;
      private final String equipmentType;
      private double co2MoleFraction = Double.NaN;
      private double minimumCo2MoleFraction = Double.NaN;
      private double waterMoleFraction = Double.NaN;
      private double maximumWaterMoleFraction = Double.NaN;
      private boolean otherImpuritiesWithinProjectSpecification;
      private double designMinimumTemperatureK = Double.NaN;
      private double designMaximumTemperatureK = Double.NaN;
      private double maximumAllowableOperatingPressurePaAbsolute = Double.NaN;
      private final List<OperatingPoint> operatingPoints = new ArrayList<OperatingPoint>();
      private boolean co2PipelineApplicabilityVerified;
      private boolean compositionAndSpecificationVerified;
      private boolean thermodynamicModelVerified;
      private boolean singlePhaseBoundaryInterpretationVerified;
      private boolean operatingProfileVerified;
      private boolean pressureTemperatureLimitsVerified;
      private boolean materialsCorrosionAndFractureBasisVerified;
      private boolean safetyConstructionOperationsAndRequalificationReviewed;

      private Builder(StandardEdition edition, String equipmentType) {
        if (edition == null || edition.getStandardType() != StandardType.DNV_RP_F104) {
          throw new IllegalArgumentException("edition must identify DNV-RP-F104");
        }
        if (equipmentType == null || equipmentType.trim().isEmpty()) {
          throw new IllegalArgumentException("equipmentType cannot be null or blank");
        }
        this.edition = edition;
        this.equipmentType = equipmentType.trim();
      }

      public Builder co2MoleFraction(double value) {
        co2MoleFraction = value;
        return this;
      }

      public Builder minimumCo2MoleFraction(double value) {
        minimumCo2MoleFraction = value;
        return this;
      }

      public Builder waterMoleFraction(double value) {
        waterMoleFraction = value;
        return this;
      }

      public Builder maximumWaterMoleFraction(double value) {
        maximumWaterMoleFraction = value;
        return this;
      }

      public Builder otherImpuritiesWithinProjectSpecification(boolean value) {
        otherImpuritiesWithinProjectSpecification = value;
        return this;
      }

      public Builder designMinimumTemperatureK(double value) {
        designMinimumTemperatureK = value;
        return this;
      }

      public Builder designMaximumTemperatureK(double value) {
        designMaximumTemperatureK = value;
        return this;
      }

      public Builder maximumAllowableOperatingPressurePaAbsolute(double value) {
        maximumAllowableOperatingPressurePaAbsolute = value;
        return this;
      }

      public Builder addOperatingPoint(OperatingPoint value) {
        operatingPoints.add(value);
        return this;
      }

      public Builder operatingPoints(List<OperatingPoint> values) {
        operatingPoints.clear();
        if (values != null) {
          operatingPoints.addAll(values);
        }
        return this;
      }

      public Builder co2PipelineApplicabilityVerified(boolean value) {
        co2PipelineApplicabilityVerified = value;
        return this;
      }

      public Builder compositionAndSpecificationVerified(boolean value) {
        compositionAndSpecificationVerified = value;
        return this;
      }

      public Builder thermodynamicModelVerified(boolean value) {
        thermodynamicModelVerified = value;
        return this;
      }

      public Builder singlePhaseBoundaryInterpretationVerified(boolean value) {
        singlePhaseBoundaryInterpretationVerified = value;
        return this;
      }

      public Builder operatingProfileVerified(boolean value) {
        operatingProfileVerified = value;
        return this;
      }

      public Builder pressureTemperatureLimitsVerified(boolean value) {
        pressureTemperatureLimitsVerified = value;
        return this;
      }

      public Builder materialsCorrosionAndFractureBasisVerified(boolean value) {
        materialsCorrosionAndFractureBasisVerified = value;
        return this;
      }

      public Builder safetyConstructionOperationsAndRequalificationReviewed(boolean value) {
        safetyConstructionOperationsAndRequalificationReviewed = value;
        return this;
      }

      /** @return immutable input */
      public Input build() {
        return new Input(this);
      }
    }
  }

  @Override
  public StandardType standard() {
    return StandardType.DNV_RP_F104;
  }

  @Override
  public StandardSupportLevel maturity() {
    return StandardSupportLevel.SCREENING;
  }

  @Override
  public boolean supports(StandardEdition edition) {
    return edition != null && edition.getStandardType() == standard()
        && IMPLEMENTED_EDITION.equalsIgnoreCase(edition.getEdition()) && edition.getAmendments().isEmpty();
  }

  @Override
  public StandardApplicability applicability(Input input) {
    return StandardApplicability.assess(standard(), input == null ? null : input.getEquipmentType());
  }

  @Override
  public String getMethod() {
    return "dnv-rp-f104-co2-transport-envelope-screening";
  }

  @Override
  public String getMethodVersion() {
    return "1.0.0";
  }

  @Override
  public CalculationReadiness assess(Input input, EngineeringCalculationContext context) {
    CalculationReadiness.Builder readiness = CalculationReadiness.builder();
    if (input == null) {
      return readiness
          .addBlocker("DNV_RP_F104_INPUT_MISSING", "DNV-RP-F104 CO2 pipeline input is required",
              "Provide composition, operating profile, project limits, external phase boundaries, and evidence flags")
          .build();
    }
    StandardApplicability applicability = applicability(input);
    if (!applicability.isApplicable()) {
      readiness.addBlocker("DNV_RP_F104_NOT_APPLICABLE", applicability.getReason(),
          "Use a catalogued CO2 pipeline, pipe, multiphase-pipe, or riser equipment type");
    }
    if (!supports(input.getEdition())) {
      readiness.addBlocker("DNV_RP_F104_EDITION_NOT_IMPLEMENTED",
          "The kernel implements " + IMPLEMENTED_EDITION + ", not " + input.getEdition().getDisplayName(),
          "Select the catalogued unamended edition or implement a controlled method version");
    }
    validateComposition(input, readiness);
    validateLimits(input, readiness);
    validateOperatingPoints(input, readiness);
    validateEvidence(input, readiness);
    readiness.addWarning("DNV_RP_F104_CALLER_CONTROLLED_BOUNDARIES",
        "Composition limits and every minimum single-phase pressure boundary are caller-controlled project evidence",
        "Retain approved specifications, EOS selection, phase-envelope calculations, uncertainties, and review records");
    readiness.addWarning("DNV_RP_F104_SCREENING_ONLY",
        "The result is an operating-envelope margin screen and is not a DNV-RP-F104 conformity assessment",
        "Complete independent design, construction, commissioning, operation, requalification, and safety reviews");
    return readiness.build();
  }

  private static void validateComposition(Input input, CalculationReadiness.Builder readiness) {
    if (!fraction(input.getCo2MoleFraction()) || !fraction(input.getMinimumCo2MoleFraction())) {
      readiness.addBlocker("DNV_RP_F104_CO2_FRACTION_INVALID",
          "Actual and minimum CO2 mole fractions must be finite values from zero through one",
          "Supply the controlled composition and project CO2 specification as fractions");
    }
    if (!fraction(input.getWaterMoleFraction()) || !fraction(input.getMaximumWaterMoleFraction())) {
      readiness.addBlocker("DNV_RP_F104_WATER_FRACTION_INVALID",
          "Actual and maximum water mole fractions must be finite values from zero through one",
          "Supply water content and the project maximum on the same mole-fraction basis");
    }
  }

  private static void validateLimits(Input input, CalculationReadiness.Builder readiness) {
    if (!positive(input.getDesignMinimumTemperatureK()) || !positive(input.getDesignMaximumTemperatureK())
        || input.getDesignMaximumTemperatureK() <= input.getDesignMinimumTemperatureK()) {
      readiness.addBlocker("DNV_RP_F104_TEMPERATURE_LIMIT_INVALID",
          "Design temperature limits must be finite, positive, and ordered",
          "Supply project minimum and maximum temperatures in K");
    }
    if (!positive(input.getMaximumAllowableOperatingPressurePaAbsolute())) {
      readiness.addBlocker("DNV_RP_F104_MAOP_INVALID",
          "Maximum allowable operating pressure must be finite, positive, and absolute",
          "Supply the externally established pipeline MAOP in Pa absolute");
    }
  }

  private static void validateOperatingPoints(Input input, CalculationReadiness.Builder readiness) {
    if (input.getOperatingPoints().isEmpty()) {
      readiness.addBlocker("DNV_RP_F104_PROFILE_EMPTY", "At least one operating point is required",
          "Supply an ordered pressure-temperature profile and project phase boundary at every point");
      return;
    }
    Set<String> labels = new HashSet<String>();
    double previousDistanceM = -1.0;
    for (int index = 0; index < input.getOperatingPoints().size(); index++) {
      OperatingPoint point = input.getOperatingPoints().get(index);
      if (point == null) {
        readiness.addBlocker("DNV_RP_F104_PROFILE_POINT_MISSING", "Operating points cannot be null",
            "Replace the missing point at index " + index);
        continue;
      }
      String label = point.getLabel() == null ? "" : point.getLabel().trim();
      if (label.isEmpty() || !labels.add(label)) {
        readiness.addBlocker("DNV_RP_F104_PROFILE_LABEL_INVALID", "Operating-point labels must be non-blank and unique",
            "Correct the label at index " + index);
      }
      if (!nonNegative(point.getDistanceM()) || point.getDistanceM() <= previousDistanceM) {
        readiness.addBlocker("DNV_RP_F104_PROFILE_DISTANCE_INVALID",
            "Operating-point distances must be finite, non-negative, and strictly increasing",
            "Correct the distance at point " + (label.isEmpty() ? Integer.toString(index) : label));
      }
      if (Double.isFinite(point.getDistanceM())) {
        previousDistanceM = point.getDistanceM();
      }
      if (!positive(point.getPressurePaAbsolute()) || !positive(point.getTemperatureK())
          || !positive(point.getCallerControlledMinimumSinglePhasePressurePaAbsolute())) {
        readiness.addBlocker("DNV_RP_F104_PROFILE_VALUE_INVALID",
            "Point pressure, temperature, and external single-phase pressure boundary must be finite and positive",
            "Correct the SI absolute values at point " + (label.isEmpty() ? Integer.toString(index) : label));
      }
    }
  }

  private static void validateEvidence(Input input, CalculationReadiness.Builder readiness) {
    evidence(input.isCo2PipelineApplicabilityVerified(), readiness, "APPLICABILITY",
        "CO2 pipeline applicability has not been verified", "Verify transported fluid, pipeline scope, and lifecycle");
    evidence(input.isCompositionAndSpecificationVerified(), readiness, "COMPOSITION",
        "Composition and project impurity specification have not been verified",
        "Verify sampling, analysis, units, composition envelope, and project limits");
    evidence(input.isThermodynamicModelVerified(), readiness, "THERMODYNAMICS",
        "The composition-specific thermodynamic model has not been verified",
        "Verify EOS, binary interactions, property data, phase envelope, and uncertainty");
    evidence(input.isSinglePhaseBoundaryInterpretationVerified(), readiness, "PHASE_BOUNDARY",
        "The minimum-pressure interpretation of each supplied single-phase boundary has not been verified",
        "Confirm that pressure above each boundary represents the intended project single-phase region");
    evidence(input.isOperatingProfileVerified(), readiness, "PROFILE",
        "The operating pressure-temperature profile has not been verified",
        "Verify hydraulic/thermal cases, transients, elevations, start-up, shutdown, and depressurization");
    evidence(input.isPressureTemperatureLimitsVerified(), readiness, "LIMITS",
        "Pipeline pressure and temperature limits have not been verified",
        "Verify MAOP, design temperatures, absolute-pressure basis, tolerances, and protective settings");
    evidence(input.isMaterialsCorrosionAndFractureBasisVerified(), readiness, "INTEGRITY",
        "Materials, corrosion, decompression, and fracture-control bases have not been verified",
        "Complete project materials, impurity reaction, corrosion, toughness, crack-arrest, and fracture reviews");
    evidence(input.isSafetyConstructionOperationsAndRequalificationReviewed(), readiness, "LIFECYCLE",
        "Safety, construction, operation, and requalification evidence has not been reviewed",
        "Complete consequence, construction, commissioning, operating, integrity-management, and change reviews");
  }

  private static void evidence(boolean verified, CalculationReadiness.Builder readiness, String suffix, String message,
      String action) {
    if (!verified) {
      readiness.addBlocker("DNV_RP_F104_" + suffix + "_NOT_VERIFIED", message, action);
    }
  }

  @Override
  public EngineeringCalculationResult<DnvRpF104Co2PipelineEnvelopeAssessment> calculate(Input input,
      EngineeringCalculationContext context) {
    EngineeringCalculationContext effectiveContext = context == null ? EngineeringCalculationContext.builder().build()
        : context;
    CalculationReadiness readiness = assess(input, effectiveContext);
    EngineeringCalculationResult.Builder<DnvRpF104Co2PipelineEnvelopeAssessment> result = EngineeringCalculationResult
        .<DnvRpF104Co2PipelineEnvelopeAssessment>builder("dnv-rp-f104-co2-transport-envelope-screening", getMethod(),
            getMethodVersion())
        .context(effectiveContext).readiness(readiness);
    if (input == null || !readiness.isReady()) {
      return result.status(EngineeringCalculationResult.Status.BLOCKED)
          .message("DNV-RP-F104 CO2 pipeline envelope screening is blocked until readiness findings are resolved")
          .build();
    }
    DnvRpF104Co2PipelineEnvelopeAssessment assessment = new DnvRpF104Co2PipelineEnvelopeAssessment(input);
    if (!numericallyValid(assessment)) {
      CalculationReadiness numericalReadiness = CalculationReadiness.builder().merge(readiness)
          .addBlocker("DNV_RP_F104_NUMERICAL_RESULT_INVALID",
              "Envelope-margin calculation produced a non-finite result",
              "Review profile magnitudes, project limits, external phase boundaries, and units")
          .build();
      return result.readiness(numericalReadiness).status(EngineeringCalculationResult.Status.BLOCKED)
          .message("DNV-RP-F104 CO2 pipeline envelope screening is blocked by an invalid numerical result").build();
    }
    return result.status(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED).value(assessment)
        .input("standard", input.getEdition().getDisplayName()).input("equipmentType", input.getEquipmentType())
        .input("compositionAndLimits", compositionAndLimitsMap(input))
        .input("operatingPoints", operatingPointMaps(input))
        .warning("Constraint status is caller-controlled and is not a DNV-RP-F104 compliance decision")
        .warning("DNV-ST-F101 pressure containment, collapse, propagation buckling, local buckling, load interaction, "
            + "fatigue, incidental/test pressure, de-rating, safety class, ovality, fabrication route, and "
            + "installation strain are not replaced")
        .warning("Fracture/decompression and crack arrest, materials, corrosion, impurity reactions, construction, "
            + "commissioning, operation, requalification, release consequences, and emergency response are not calculated")
        .message("DNV-RP-F104 caller-controlled CO2 transport-envelope screen completed; review remains required")
        .build();
  }

  private static Map<String, Object> compositionAndLimitsMap(Input input) {
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    values.put("co2MoleFraction", Double.valueOf(input.getCo2MoleFraction()));
    values.put("minimumCo2MoleFraction", Double.valueOf(input.getMinimumCo2MoleFraction()));
    values.put("waterMoleFraction", Double.valueOf(input.getWaterMoleFraction()));
    values.put("maximumWaterMoleFraction", Double.valueOf(input.getMaximumWaterMoleFraction()));
    values.put("otherImpuritiesWithinProjectSpecification",
        Boolean.valueOf(input.isOtherImpuritiesWithinProjectSpecification()));
    values.put("designMinimumTemperatureK", Double.valueOf(input.getDesignMinimumTemperatureK()));
    values.put("designMaximumTemperatureK", Double.valueOf(input.getDesignMaximumTemperatureK()));
    values.put("maximumAllowableOperatingPressurePaAbsolute",
        Double.valueOf(input.getMaximumAllowableOperatingPressurePaAbsolute()));
    return values;
  }

  private static List<Map<String, Object>> operatingPointMaps(Input input) {
    List<Map<String, Object>> values = new ArrayList<Map<String, Object>>();
    for (OperatingPoint point : input.getOperatingPoints()) {
      Map<String, Object> value = new LinkedHashMap<String, Object>();
      value.put("label", point.getLabel());
      value.put("distanceM", Double.valueOf(point.getDistanceM()));
      value.put("pressurePaAbsolute", Double.valueOf(point.getPressurePaAbsolute()));
      value.put("temperatureK", Double.valueOf(point.getTemperatureK()));
      value.put("callerControlledMinimumSinglePhasePressurePaAbsolute",
          Double.valueOf(point.getCallerControlledMinimumSinglePhasePressurePaAbsolute()));
      values.add(value);
    }
    return values;
  }

  private static boolean fraction(double value) {
    return Double.isFinite(value) && value >= 0.0 && value <= 1.0;
  }

  private static boolean positive(double value) {
    return Double.isFinite(value) && value > 0.0;
  }

  private static boolean nonNegative(double value) {
    return Double.isFinite(value) && value >= 0.0;
  }

  private static boolean numericallyValid(DnvRpF104Co2PipelineEnvelopeAssessment assessment) {
    return Double.isFinite(assessment.getCo2MoleFractionMargin())
        && Double.isFinite(assessment.getWaterMoleFractionMargin())
        && Double.isFinite(assessment.getMinimumSinglePhasePressureMarginPa())
        && Double.isFinite(assessment.getMinimumMaximumAllowableOperatingPressureMarginPa())
        && Double.isFinite(assessment.getMinimumLowTemperatureMarginK())
        && Double.isFinite(assessment.getMinimumHighTemperatureMarginK());
  }
}
