package neqsim.process.engineering.calculation;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.mechanicaldesign.designstandards.StandardApplicability;
import neqsim.process.mechanicaldesign.designstandards.StandardEdition;
import neqsim.process.mechanicaldesign.designstandards.StandardSupportLevel;
import neqsim.process.mechanicaldesign.designstandards.StandardType;

/** Edition-aware isolated metal-loss pressure-resistance screening for DNV-RP-F101. */
public final class DnvRpF101CorrodedPipelineScreeningKernel implements
    EquipmentDesignKernel<DnvRpF101CorrodedPipelineScreeningKernel.Input, DnvRpF101CorrodedPipelineAssessment> {
  private static final long serialVersionUID = 1000L;
  private static final String IMPLEMENTED_EDITION = "2019-09+AMD:2025-09";
  private static final double LENGTH_CORRECTION_COEFFICIENT = 0.31;

  /** Immutable, unit-explicit isolated-defect input. */
  public static final class Input implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final StandardEdition edition;
    private final String equipmentType;
    private final double steelOuterDiameterM;
    private final double assessmentWallThicknessM;
    private final double measuredDefectDepthM;
    private final double defectDepthAllowanceM;
    private final double defectAxialLengthM;
    private final double characteristicUltimateTensileStrengthPa;
    private final double internalPressurePaAbsolute;
    private final double externalPressurePaAbsolute;
    private final double callerControlledPressureFactor;
    private final boolean geometryVerified;
    private final boolean inspectionSizingVerified;
    private final boolean materialStrengthVerified;
    private final boolean pressureBasisVerified;
    private final boolean projectFactorVerified;
    private final boolean isolatedLongitudinalMetalLossApplicabilityVerified;

    private Input(Builder builder) {
      edition = builder.edition;
      equipmentType = builder.equipmentType;
      steelOuterDiameterM = builder.steelOuterDiameterM;
      assessmentWallThicknessM = builder.assessmentWallThicknessM;
      measuredDefectDepthM = builder.measuredDefectDepthM;
      defectDepthAllowanceM = builder.defectDepthAllowanceM;
      defectAxialLengthM = builder.defectAxialLengthM;
      characteristicUltimateTensileStrengthPa = builder.characteristicUltimateTensileStrengthPa;
      internalPressurePaAbsolute = builder.internalPressurePaAbsolute;
      externalPressurePaAbsolute = builder.externalPressurePaAbsolute;
      callerControlledPressureFactor = builder.callerControlledPressureFactor;
      geometryVerified = builder.geometryVerified;
      inspectionSizingVerified = builder.inspectionSizingVerified;
      materialStrengthVerified = builder.materialStrengthVerified;
      pressureBasisVerified = builder.pressureBasisVerified;
      projectFactorVerified = builder.projectFactorVerified;
      isolatedLongitudinalMetalLossApplicabilityVerified = builder.isolatedLongitudinalMetalLossApplicabilityVerified;
    }

    /**
     * Create an isolated-defect input builder.
     *
     * @param edition DNV-RP-F101 edition
     * @param equipmentType supported pipeline or riser type
     * @return input builder
     */
    public static Builder builder(StandardEdition edition, String equipmentType) {
      return new Builder(edition, equipmentType);
    }

    /** @return selected standard edition */
    public StandardEdition getEdition() {
      return edition;
    }

    /** @return equipment type */
    public String getEquipmentType() {
      return equipmentType;
    }

    /** @return steel outside diameter in m */
    public double getSteelOuterDiameterM() {
      return steelOuterDiameterM;
    }

    /** @return externally established assessment wall thickness in m */
    public double getAssessmentWallThicknessM() {
      return assessmentWallThicknessM;
    }

    /** @return measured maximum defect depth in m */
    public double getMeasuredDefectDepthM() {
      return measuredDefectDepthM;
    }

    /** @return caller-controlled depth allowance in m */
    public double getDefectDepthAllowanceM() {
      return defectDepthAllowanceM;
    }

    /** @return axial defect length in m */
    public double getDefectAxialLengthM() {
      return defectAxialLengthM;
    }

    /** @return externally established characteristic ultimate tensile strength in Pa */
    public double getCharacteristicUltimateTensileStrengthPa() {
      return characteristicUltimateTensileStrengthPa;
    }

    /** @return internal absolute pressure in Pa */
    public double getInternalPressurePaAbsolute() {
      return internalPressurePaAbsolute;
    }

    /** @return external absolute pressure in Pa */
    public double getExternalPressurePaAbsolute() {
      return externalPressurePaAbsolute;
    }

    /** @return caller-controlled multiplier applied to calculated failure pressure */
    public double getCallerControlledPressureFactor() {
      return callerControlledPressureFactor;
    }

    /** @return whether pipe geometry was externally verified */
    public boolean isGeometryVerified() {
      return geometryVerified;
    }

    /** @return whether inspection sizing and depth allowance were externally verified */
    public boolean isInspectionSizingVerified() {
      return inspectionSizingVerified;
    }

    /** @return whether the material strength basis was externally verified */
    public boolean isMaterialStrengthVerified() {
      return materialStrengthVerified;
    }

    /** @return whether the internal/external pressure basis was externally verified */
    public boolean isPressureBasisVerified() {
      return pressureBasisVerified;
    }

    /** @return whether the project pressure factor was externally verified */
    public boolean isProjectFactorVerified() {
      return projectFactorVerified;
    }

    /** @return whether isolated longitudinal metal-loss/internal-pressure applicability was verified */
    public boolean isIsolatedLongitudinalMetalLossApplicabilityVerified() {
      return isolatedLongitudinalMetalLossApplicabilityVerified;
    }

    /** Builder retaining raw values for fail-closed readiness assessment. */
    public static final class Builder {
      private final StandardEdition edition;
      private final String equipmentType;
      private double steelOuterDiameterM = Double.NaN;
      private double assessmentWallThicknessM = Double.NaN;
      private double measuredDefectDepthM = Double.NaN;
      private double defectDepthAllowanceM = Double.NaN;
      private double defectAxialLengthM = Double.NaN;
      private double characteristicUltimateTensileStrengthPa = Double.NaN;
      private double internalPressurePaAbsolute = Double.NaN;
      private double externalPressurePaAbsolute = Double.NaN;
      private double callerControlledPressureFactor = Double.NaN;
      private boolean geometryVerified;
      private boolean inspectionSizingVerified;
      private boolean materialStrengthVerified;
      private boolean pressureBasisVerified;
      private boolean projectFactorVerified;
      private boolean isolatedLongitudinalMetalLossApplicabilityVerified;

      private Builder(StandardEdition edition, String equipmentType) {
        if (edition == null || edition.getStandardType() != StandardType.DNV_RP_F101) {
          throw new IllegalArgumentException("edition must identify DNV-RP-F101");
        }
        if (equipmentType == null || equipmentType.trim().isEmpty()) {
          throw new IllegalArgumentException("equipmentType cannot be null or blank");
        }
        this.edition = edition;
        this.equipmentType = equipmentType.trim();
      }

      /**
       * Set the steel outside diameter.
       *
       * @param value outside diameter in m
       * @return this builder
       */
      public Builder steelOuterDiameterM(double value) {
        steelOuterDiameterM = value;
        return this;
      }

      /**
       * Set the externally established assessment wall thickness.
       *
       * @param value wall thickness in m
       * @return this builder
       */
      public Builder assessmentWallThicknessM(double value) {
        assessmentWallThicknessM = value;
        return this;
      }

      /**
       * Set the measured maximum depth of the isolated defect.
       *
       * @param value measured depth in m
       * @return this builder
       */
      public Builder measuredDefectDepthM(double value) {
        measuredDefectDepthM = value;
        return this;
      }

      /**
       * Set the caller-controlled allowance added to measured defect depth.
       *
       * @param value depth allowance in m
       * @return this builder
       */
      public Builder defectDepthAllowanceM(double value) {
        defectDepthAllowanceM = value;
        return this;
      }

      /**
       * Set the axial defect length.
       *
       * @param value axial length in m
       * @return this builder
       */
      public Builder defectAxialLengthM(double value) {
        defectAxialLengthM = value;
        return this;
      }

      /**
       * Set characteristic ultimate tensile strength.
       *
       * @param value strength in Pa
       * @return this builder
       */
      public Builder characteristicUltimateTensileStrengthPa(double value) {
        characteristicUltimateTensileStrengthPa = value;
        return this;
      }

      /**
       * Set internal absolute pressure.
       *
       * @param value pressure in Pa absolute
       * @return this builder
       */
      public Builder internalPressurePaAbsolute(double value) {
        internalPressurePaAbsolute = value;
        return this;
      }

      /**
       * Set external absolute pressure.
       *
       * @param value pressure in Pa absolute
       * @return this builder
       */
      public Builder externalPressurePaAbsolute(double value) {
        externalPressurePaAbsolute = value;
        return this;
      }

      /**
       * Set the project-controlled failure-pressure multiplier.
       *
       * @param value multiplier above zero and no greater than one
       * @return this builder
       */
      public Builder callerControlledPressureFactor(double value) {
        callerControlledPressureFactor = value;
        return this;
      }

      /**
       * Record external verification of pipe geometry.
       *
       * @param value whether verified
       * @return this builder
       */
      public Builder geometryVerified(boolean value) {
        geometryVerified = value;
        return this;
      }

      /**
       * Record external verification of inspection sizing and allowance.
       *
       * @param value whether verified
       * @return this builder
       */
      public Builder inspectionSizingVerified(boolean value) {
        inspectionSizingVerified = value;
        return this;
      }

      /**
       * Record external verification of material strength.
       *
       * @param value whether verified
       * @return this builder
       */
      public Builder materialStrengthVerified(boolean value) {
        materialStrengthVerified = value;
        return this;
      }

      /**
       * Record external verification of the pressure basis.
       *
       * @param value whether verified
       * @return this builder
       */
      public Builder pressureBasisVerified(boolean value) {
        pressureBasisVerified = value;
        return this;
      }

      /**
       * Record external verification of the project pressure factor.
       *
       * @param value whether verified
       * @return this builder
       */
      public Builder projectFactorVerified(boolean value) {
        projectFactorVerified = value;
        return this;
      }

      /**
       * Record external verification of the isolated metal-loss/internal-pressure envelope.
       *
       * @param value whether verified
       * @return this builder
       */
      public Builder isolatedLongitudinalMetalLossApplicabilityVerified(boolean value) {
        isolatedLongitudinalMetalLossApplicabilityVerified = value;
        return this;
      }

      /** @return immutable input */
      public Input build() {
        return new Input(this);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public StandardType standard() {
    return StandardType.DNV_RP_F101;
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
    return "dnv-rp-f101-isolated-defect-pressure-screening";
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
      return readiness.addBlocker("RP_F101_INPUT_MISSING", "DNV-RP-F101 defect input is required",
          "Provide pipe, defect, material, pressure, project-factor, and verification inputs").build();
    }
    StandardApplicability applicability = applicability(input);
    if (!applicability.isApplicable()) {
      readiness.addBlocker("RP_F101_NOT_APPLICABLE", applicability.getReason(),
          "Use a catalogued pipeline or riser equipment type");
    }
    if (!supports(input.getEdition())) {
      readiness.addBlocker("RP_F101_EDITION_NOT_IMPLEMENTED",
          "The kernel implements " + IMPLEMENTED_EDITION + ", not " + input.getEdition().getDisplayName(),
          "Select the catalogued unamended edition or implement a controlled method version");
    }
    if (!positive(input.getSteelOuterDiameterM())) {
      readiness.addBlocker("RP_F101_DIAMETER_INVALID", "Steel outside diameter must be finite and positive",
          "Supply outside diameter in m");
    }
    if (!positive(input.getAssessmentWallThicknessM()) || (positive(input.getSteelOuterDiameterM())
        && input.getAssessmentWallThicknessM() >= input.getSteelOuterDiameterM() / 2.0)) {
      readiness.addBlocker("RP_F101_WALL_INVALID",
          "Assessment wall thickness must be finite, positive, and less than half the outside diameter",
          "Supply the verified wall-thickness basis in m");
    }
    if (!positive(input.getMeasuredDefectDepthM()) || !nonNegative(input.getDefectDepthAllowanceM())) {
      readiness.addBlocker("RP_F101_DEFECT_DEPTH_INVALID",
          "Measured depth must be finite and positive and its allowance finite and non-negative",
          "Supply measured maximum depth and controlled inspection/growth allowance in m");
    } else if (positive(input.getAssessmentWallThicknessM())
        && input.getMeasuredDefectDepthM() + input.getDefectDepthAllowanceM() >= input.getAssessmentWallThicknessM()) {
      readiness.addBlocker("RP_F101_REMAINING_WALL_INVALID",
          "Assessment defect depth leaves no positive remaining wall",
          "Escalate through-wall or near-through-wall damage outside this isolated-defect screen");
    }
    if (!positive(input.getDefectAxialLengthM())) {
      readiness.addBlocker("RP_F101_DEFECT_LENGTH_INVALID", "Axial defect length must be finite and positive",
          "Supply the verified axial metal-loss length in m");
    }
    if (!positive(input.getCharacteristicUltimateTensileStrengthPa())) {
      readiness.addBlocker("RP_F101_UTS_INVALID",
          "Characteristic ultimate tensile strength must be finite and positive",
          "Supply the controlled material strength in Pa");
    }
    if (!positive(input.getInternalPressurePaAbsolute()) || !positive(input.getExternalPressurePaAbsolute())
        || input.getInternalPressurePaAbsolute() <= input.getExternalPressurePaAbsolute()) {
      readiness.addBlocker("RP_F101_PRESSURE_INVALID",
          "Internal and external absolute pressures must be finite and positive with internal above external",
          "Supply the assessed internal-pressure differential in Pa absolute");
    }
    if (!positive(input.getCallerControlledPressureFactor()) || input.getCallerControlledPressureFactor() > 1.0) {
      readiness.addBlocker("RP_F101_PRESSURE_FACTOR_INVALID",
          "Caller-controlled pressure factor must be finite, above zero, and no greater than one",
          "Supply a verified factor from the licensed project assessment basis");
    }
    if (!input.isGeometryVerified()) {
      readiness.addBlocker("RP_F101_GEOMETRY_NOT_VERIFIED", "Pipe geometry has not been verified",
          "Verify diameter, wall-thickness definition, tolerances, and location geometry");
    }
    if (!input.isInspectionSizingVerified()) {
      readiness.addBlocker("RP_F101_INSPECTION_NOT_VERIFIED",
          "Defect sizing and depth allowance have not been verified",
          "Verify inspection method, sizing uncertainty, growth allowance, and defect dimensions");
    }
    if (!input.isMaterialStrengthVerified()) {
      readiness.addBlocker("RP_F101_MATERIAL_NOT_VERIFIED", "Material strength has not been verified",
          "Verify grade, actual/characteristic strength basis, toughness, welds, and supplementary requirements");
    }
    if (!input.isPressureBasisVerified()) {
      readiness.addBlocker("RP_F101_PRESSURE_BASIS_NOT_VERIFIED", "Pressure basis has not been verified",
          "Verify operating, incidental, test, external, transient, temperature, and elevation cases");
    }
    if (!input.isProjectFactorVerified()) {
      readiness.addBlocker("RP_F101_FACTOR_NOT_VERIFIED", "Caller-controlled pressure factor is not verified",
          "Verify safety class, assessment approach, inspection accuracy, and project factor source");
    }
    if (!input.isIsolatedLongitudinalMetalLossApplicabilityVerified()) {
      readiness.addBlocker("RP_F101_DEFECT_APPLICABILITY_NOT_VERIFIED",
          "Isolated longitudinal metal-loss/internal-pressure-only applicability is not verified",
          "Exclude interactions, complex profiles, cracking, gouges, dents, blisters, weld flaws, and combined loads");
    }
    readiness.addWarning("RP_F101_CALLER_CONTROLLED_FACTOR",
        "The pressure factor and defect-depth allowance are caller-controlled evidence, not values selected by NeqSim",
        "Retain their licensed-source, inspection, growth, safety-class, and approval basis");
    readiness.addWarning("RP_F101_NARROW_SINGLE_DEFECT_SCOPE",
        "The kernel calculates only the deterministic isolated longitudinal defect equation under internal pressure",
        "Perform required interacting/complex defect, combined-load, probabilistic, growth, and fitness-for-service work");
    return readiness.build();
  }

  /** {@inheritDoc} */
  @Override
  public EngineeringCalculationResult<DnvRpF101CorrodedPipelineAssessment> calculate(Input input,
      EngineeringCalculationContext context) {
    EngineeringCalculationContext effectiveContext = context == null ? EngineeringCalculationContext.builder().build()
        : context;
    CalculationReadiness readiness = assess(input, effectiveContext);
    EngineeringCalculationResult.Builder<DnvRpF101CorrodedPipelineAssessment> result = EngineeringCalculationResult
        .<DnvRpF101CorrodedPipelineAssessment>builder("dnv-rp-f101-isolated-defect-pressure-screening", getMethod(),
            getMethodVersion())
        .context(effectiveContext).readiness(readiness);
    if (input == null || !readiness.isReady()) {
      return result.status(EngineeringCalculationResult.Status.BLOCKED)
          .message("DNV-RP-F101 isolated-defect screening is blocked until readiness findings are resolved").build();
    }
    DnvRpF101CorrodedPipelineAssessment assessment = new DnvRpF101CorrodedPipelineAssessment(input);
    if (!numericallyValid(assessment)) {
      CalculationReadiness numericalReadiness = CalculationReadiness.builder().merge(readiness)
          .addBlocker("RP_F101_NUMERICAL_RESULT_INVALID",
              "The isolated-defect equation produced a non-finite or non-positive resistance result",
              "Review the geometry, strength, pressure, and factor magnitudes and units")
          .build();
      return result.readiness(numericalReadiness).status(EngineeringCalculationResult.Status.BLOCKED)
          .message("DNV-RP-F101 isolated-defect screening is blocked by an invalid numerical result").build();
    }
    return result.status(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED).value(assessment)
        .input("standard", input.getEdition().getDisplayName()).input("equipmentType", input.getEquipmentType())
        .input("pipeAndDefect", pipeAndDefectMap(input)).input("pressureAndFactor", pressureAndFactorMap(input))
        .warning(
            "Within-factor status is caller-controlled and is not a DNV compliance or fitness-for-service decision")
        .warning(
            "DNV-ST-F101 design, interacting/complex defects, combined loads, and crack-like damage are not calculated")
        .message("DNV-RP-F101 isolated longitudinal metal-loss pressure screen completed; review remains required")
        .build();
  }

  static double lengthCorrectionFactor(Input input) {
    double normalizedLength = input.getDefectAxialLengthM()
        / Math.sqrt(input.getSteelOuterDiameterM() * input.getAssessmentWallThicknessM());
    return Math.sqrt(1.0 + LENGTH_CORRECTION_COEFFICIENT * normalizedLength * normalizedLength);
  }

  private static Map<String, Object> pipeAndDefectMap(Input input) {
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    values.put("steelOuterDiameterM", Double.valueOf(input.getSteelOuterDiameterM()));
    values.put("assessmentWallThicknessM", Double.valueOf(input.getAssessmentWallThicknessM()));
    values.put("measuredDefectDepthM", Double.valueOf(input.getMeasuredDefectDepthM()));
    values.put("defectDepthAllowanceM", Double.valueOf(input.getDefectDepthAllowanceM()));
    values.put("defectAxialLengthM", Double.valueOf(input.getDefectAxialLengthM()));
    values.put("characteristicUltimateTensileStrengthPa",
        Double.valueOf(input.getCharacteristicUltimateTensileStrengthPa()));
    values.put("geometryVerified", Boolean.valueOf(input.isGeometryVerified()));
    values.put("inspectionSizingVerified", Boolean.valueOf(input.isInspectionSizingVerified()));
    values.put("materialStrengthVerified", Boolean.valueOf(input.isMaterialStrengthVerified()));
    values.put("isolatedLongitudinalMetalLossApplicabilityVerified",
        Boolean.valueOf(input.isIsolatedLongitudinalMetalLossApplicabilityVerified()));
    return values;
  }

  private static Map<String, Object> pressureAndFactorMap(Input input) {
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    values.put("internalPressurePaAbsolute", Double.valueOf(input.getInternalPressurePaAbsolute()));
    values.put("externalPressurePaAbsolute", Double.valueOf(input.getExternalPressurePaAbsolute()));
    values.put("callerControlledPressureFactor", Double.valueOf(input.getCallerControlledPressureFactor()));
    values.put("pressureBasisVerified", Boolean.valueOf(input.isPressureBasisVerified()));
    values.put("projectFactorVerified", Boolean.valueOf(input.isProjectFactorVerified()));
    return values;
  }

  private static boolean positive(double value) {
    return Double.isFinite(value) && value > 0.0;
  }

  private static boolean nonNegative(double value) {
    return Double.isFinite(value) && value >= 0.0;
  }

  private static boolean numericallyValid(DnvRpF101CorrodedPipelineAssessment assessment) {
    return positive(assessment.getLengthCorrectionFactor()) && positive(assessment.getUncorrodedFailurePressurePa())
        && positive(assessment.getDefectPressureReductionFactor())
        && positive(assessment.getCalculatedFailurePressurePa())
        && positive(assessment.getCallerControlledPressureLimitPa()) && positive(assessment.getPressureUtilization())
        && Double.isFinite(assessment.getPressureMarginPa());
  }
}
