package neqsim.process.engineering.calculation;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.mechanicaldesign.designstandards.StandardApplicability;
import neqsim.process.mechanicaldesign.designstandards.StandardEdition;
import neqsim.process.mechanicaldesign.designstandards.StandardSupportLevel;
import neqsim.process.mechanicaldesign.designstandards.StandardType;

/** Edition-aware first-mode and dimensionless free-span screening for DNV-RP-F105. */
public final class DnvRpF105FreeSpanScreeningKernel
    implements EquipmentDesignKernel<DnvRpF105FreeSpanScreeningKernel.Input, DnvRpF105FreeSpanAssessment> {
  private static final long serialVersionUID = 1000L;
  private static final String IMPLEMENTED_EDITION = "2025-12";
  private static final double MINIMUM_SPAN_TO_HYDRODYNAMIC_DIAMETER = 10.0;

  /** Immutable, unit-explicit free-span screening input. */
  public static final class Input implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final StandardEdition edition;
    private final String equipmentType;
    private final double spanLengthM;
    private final double steelOuterDiameterM;
    private final double steelWallThicknessM;
    private final double hydrodynamicDiameterM;
    private final double youngsModulusPa;
    private final double effectiveMassPerLengthKgPerM;
    private final double effectiveAxialForceN;
    private final double currentVelocityMPerS;
    private final double waveOrbitalVelocityAmplitudeMPerS;
    private final double wavePeriodS;
    private final double strouhalNumber;
    private final double lockInFrequencyRatioLower;
    private final double lockInFrequencyRatioUpper;
    private final double maxCurrentReducedVelocityForScreening;
    private final double maxWaveReducedVelocityForScreening;
    private final boolean spanGeometryVerified;
    private final boolean structuralModelVerified;
    private final boolean environmentalBasisVerified;
    private final boolean projectScreeningLimitsVerified;

    private Input(Builder builder) {
      edition = builder.edition;
      equipmentType = builder.equipmentType;
      spanLengthM = builder.spanLengthM;
      steelOuterDiameterM = builder.steelOuterDiameterM;
      steelWallThicknessM = builder.steelWallThicknessM;
      hydrodynamicDiameterM = builder.hydrodynamicDiameterM;
      youngsModulusPa = builder.youngsModulusPa;
      effectiveMassPerLengthKgPerM = builder.effectiveMassPerLengthKgPerM;
      effectiveAxialForceN = builder.effectiveAxialForceN;
      currentVelocityMPerS = builder.currentVelocityMPerS;
      waveOrbitalVelocityAmplitudeMPerS = builder.waveOrbitalVelocityAmplitudeMPerS;
      wavePeriodS = builder.wavePeriodS;
      strouhalNumber = builder.strouhalNumber;
      lockInFrequencyRatioLower = builder.lockInFrequencyRatioLower;
      lockInFrequencyRatioUpper = builder.lockInFrequencyRatioUpper;
      maxCurrentReducedVelocityForScreening = builder.maxCurrentReducedVelocityForScreening;
      maxWaveReducedVelocityForScreening = builder.maxWaveReducedVelocityForScreening;
      spanGeometryVerified = builder.spanGeometryVerified;
      structuralModelVerified = builder.structuralModelVerified;
      environmentalBasisVerified = builder.environmentalBasisVerified;
      projectScreeningLimitsVerified = builder.projectScreeningLimitsVerified;
    }

    /**
     * Create a builder for an explicit DNV-RP-F105 pipeline basis.
     *
     * @param edition DNV-RP-F105 edition
     * @param equipmentType supported pipeline equipment type
     * @return input builder
     */
    public static Builder builder(StandardEdition edition, String equipmentType) {
      return new Builder(edition, equipmentType);
    }

    /** @return selected edition */
    public StandardEdition getEdition() {
      return edition;
    }

    /** @return equipment type */
    public String getEquipmentType() {
      return equipmentType;
    }

    /** @return free-span length in m */
    public double getSpanLengthM() {
      return spanLengthM;
    }

    /** @return steel outside diameter in m */
    public double getSteelOuterDiameterM() {
      return steelOuterDiameterM;
    }

    /** @return steel wall thickness in m */
    public double getSteelWallThicknessM() {
      return steelWallThicknessM;
    }

    /** @return hydrodynamic diameter including relevant coatings and marine growth in m */
    public double getHydrodynamicDiameterM() {
      return hydrodynamicDiameterM;
    }

    /** @return Young's modulus in Pa */
    public double getYoungsModulusPa() {
      return youngsModulusPa;
    }

    /** @return externally derived effective mass per length in kg/m */
    public double getEffectiveMassPerLengthKgPerM() {
      return effectiveMassPerLengthKgPerM;
    }

    /** @return effective axial force in N, positive in tension and negative in compression */
    public double getEffectiveAxialForceN() {
      return effectiveAxialForceN;
    }

    /** @return design current velocity normal to the span in m/s */
    public double getCurrentVelocityMPerS() {
      return currentVelocityMPerS;
    }

    /** @return wave orbital velocity amplitude normal to the span in m/s */
    public double getWaveOrbitalVelocityAmplitudeMPerS() {
      return waveOrbitalVelocityAmplitudeMPerS;
    }

    /** @return wave period in s, or NaN when no wave velocity is supplied */
    public double getWavePeriodS() {
      return wavePeriodS;
    }

    /** @return caller-controlled Strouhal number */
    public double getStrouhalNumber() {
      return strouhalNumber;
    }

    /** @return lower caller-controlled current frequency-ratio trigger */
    public double getLockInFrequencyRatioLower() {
      return lockInFrequencyRatioLower;
    }

    /** @return upper caller-controlled current frequency-ratio trigger */
    public double getLockInFrequencyRatioUpper() {
      return lockInFrequencyRatioUpper;
    }

    /** @return caller-controlled maximum current reduced velocity for simple screening */
    public double getMaxCurrentReducedVelocityForScreening() {
      return maxCurrentReducedVelocityForScreening;
    }

    /** @return caller-controlled maximum wave reduced velocity for simple screening */
    public double getMaxWaveReducedVelocityForScreening() {
      return maxWaveReducedVelocityForScreening;
    }

    /** @return whether surveyed span geometry was verified externally */
    public boolean isSpanGeometryVerified() {
      return spanGeometryVerified;
    }

    /** @return whether the simply supported first-mode model was verified externally */
    public boolean isStructuralModelVerified() {
      return structuralModelVerified;
    }

    /** @return whether environmental velocities and period were verified externally */
    public boolean isEnvironmentalBasisVerified() {
      return environmentalBasisVerified;
    }

    /** @return whether project response triggers were verified externally */
    public boolean isProjectScreeningLimitsVerified() {
      return projectScreeningLimitsVerified;
    }

    /** Builder retaining raw values for fail-closed readiness assessment. */
    public static final class Builder {
      private final StandardEdition edition;
      private final String equipmentType;
      private double spanLengthM = Double.NaN;
      private double steelOuterDiameterM = Double.NaN;
      private double steelWallThicknessM = Double.NaN;
      private double hydrodynamicDiameterM = Double.NaN;
      private double youngsModulusPa = Double.NaN;
      private double effectiveMassPerLengthKgPerM = Double.NaN;
      private double effectiveAxialForceN = Double.NaN;
      private double currentVelocityMPerS = Double.NaN;
      private double waveOrbitalVelocityAmplitudeMPerS = Double.NaN;
      private double wavePeriodS = Double.NaN;
      private double strouhalNumber = Double.NaN;
      private double lockInFrequencyRatioLower = Double.NaN;
      private double lockInFrequencyRatioUpper = Double.NaN;
      private double maxCurrentReducedVelocityForScreening = Double.NaN;
      private double maxWaveReducedVelocityForScreening = Double.NaN;
      private boolean spanGeometryVerified;
      private boolean structuralModelVerified;
      private boolean environmentalBasisVerified;
      private boolean projectScreeningLimitsVerified;

      private Builder(StandardEdition edition, String equipmentType) {
        if (edition == null || edition.getStandardType() != StandardType.DNV_RP_F105) {
          throw new IllegalArgumentException("edition must identify DNV-RP-F105");
        }
        if (equipmentType == null || equipmentType.trim().isEmpty()) {
          throw new IllegalArgumentException("equipmentType cannot be null or blank");
        }
        this.edition = edition;
        this.equipmentType = equipmentType.trim();
      }

      /**
       * Set the free-span length.
       *
       * @param value span length in m
       * @return this builder
       */
      public Builder spanLengthM(double value) {
        spanLengthM = value;
        return this;
      }

      /**
       * Set the steel outside diameter.
       *
       * @param value steel outside diameter in m
       * @return this builder
       */
      public Builder steelOuterDiameterM(double value) {
        steelOuterDiameterM = value;
        return this;
      }

      /**
       * Set the steel structural wall thickness.
       *
       * @param value steel wall thickness in m
       * @return this builder
       */
      public Builder steelWallThicknessM(double value) {
        steelWallThicknessM = value;
        return this;
      }

      /**
       * Set the hydrodynamic diameter.
       *
       * @param value diameter including applicable coatings and marine growth in m
       * @return this builder
       */
      public Builder hydrodynamicDiameterM(double value) {
        hydrodynamicDiameterM = value;
        return this;
      }

      /**
       * Set Young's modulus.
       *
       * @param value Young's modulus in Pa
       * @return this builder
       */
      public Builder youngsModulusPa(double value) {
        youngsModulusPa = value;
        return this;
      }

      /**
       * Set the externally derived effective modal mass per length.
       *
       * @param value effective mass per length in kg/m
       * @return this builder
       */
      public Builder effectiveMassPerLengthKgPerM(double value) {
        effectiveMassPerLengthKgPerM = value;
        return this;
      }

      /**
       * Set the effective axial force.
       *
       * @param value force in N, positive in tension and negative in compression
       * @return this builder
       */
      public Builder effectiveAxialForceN(double value) {
        effectiveAxialForceN = value;
        return this;
      }

      /**
       * Set the current velocity normal to the span.
       *
       * @param value current velocity in m/s
       * @return this builder
       */
      public Builder currentVelocityMPerS(double value) {
        currentVelocityMPerS = value;
        return this;
      }

      /**
       * Set the wave orbital velocity amplitude normal to the span.
       *
       * @param value wave orbital velocity amplitude in m/s
       * @return this builder
       */
      public Builder waveOrbitalVelocityAmplitudeMPerS(double value) {
        waveOrbitalVelocityAmplitudeMPerS = value;
        return this;
      }

      /**
       * Set the governing wave period.
       *
       * @param value wave period in s
       * @return this builder
       */
      public Builder wavePeriodS(double value) {
        wavePeriodS = value;
        return this;
      }

      /**
       * Set the caller-controlled Strouhal number.
       *
       * @param value Strouhal number
       * @return this builder
       */
      public Builder strouhalNumber(double value) {
        strouhalNumber = value;
        return this;
      }

      /**
       * Set the lower caller-controlled current frequency-ratio trigger.
       *
       * @param value lower trigger
       * @return this builder
       */
      public Builder lockInFrequencyRatioLower(double value) {
        lockInFrequencyRatioLower = value;
        return this;
      }

      /**
       * Set the upper caller-controlled current frequency-ratio trigger.
       *
       * @param value upper trigger
       * @return this builder
       */
      public Builder lockInFrequencyRatioUpper(double value) {
        lockInFrequencyRatioUpper = value;
        return this;
      }

      /**
       * Set the caller-controlled current reduced-velocity trigger.
       *
       * @param value maximum current reduced velocity for simple screening
       * @return this builder
       */
      public Builder maxCurrentReducedVelocityForScreening(double value) {
        maxCurrentReducedVelocityForScreening = value;
        return this;
      }

      /**
       * Set the caller-controlled wave reduced-velocity trigger.
       *
       * @param value maximum wave reduced velocity for simple screening
       * @return this builder
       */
      public Builder maxWaveReducedVelocityForScreening(double value) {
        maxWaveReducedVelocityForScreening = value;
        return this;
      }

      /**
       * Record external verification of the surveyed span geometry.
       *
       * @param value whether the basis was verified
       * @return this builder
       */
      public Builder spanGeometryVerified(boolean value) {
        spanGeometryVerified = value;
        return this;
      }

      /**
       * Record external verification of the simply supported structural model.
       *
       * @param value whether the basis was verified
       * @return this builder
       */
      public Builder structuralModelVerified(boolean value) {
        structuralModelVerified = value;
        return this;
      }

      /**
       * Record external verification of the environmental basis.
       *
       * @param value whether the basis was verified
       * @return this builder
       */
      public Builder environmentalBasisVerified(boolean value) {
        environmentalBasisVerified = value;
        return this;
      }

      /**
       * Record external verification of the project-controlled response triggers.
       *
       * @param value whether the basis was verified
       * @return this builder
       */
      public Builder projectScreeningLimitsVerified(boolean value) {
        projectScreeningLimitsVerified = value;
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
    return StandardType.DNV_RP_F105;
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
    return "dnv-rp-f105-first-mode-free-span-screening";
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
      return readiness.addBlocker("F105_INPUT_MISSING", "DNV-RP-F105 free-span input is required",
          "Provide span, pipe, mass, force, environment, response-trigger, and verification inputs").build();
    }
    StandardApplicability applicability = applicability(input);
    if (!applicability.isApplicable()) {
      readiness.addBlocker("F105_NOT_APPLICABLE", applicability.getReason(), "Use Pipeline or AdiabaticPipe");
    }
    if (!supports(input.getEdition())) {
      readiness.addBlocker("F105_EDITION_NOT_IMPLEMENTED",
          "The kernel implements " + IMPLEMENTED_EDITION + ", not " + input.getEdition().getDisplayName(),
          "Select the catalogued edition without project amendments or implement a controlled method version");
    }
    if (!positive(input.getSpanLengthM())) {
      readiness.addBlocker("F105_SPAN_LENGTH_INVALID", "Span length must be finite and positive",
          "Supply surveyed unsupported span length in m");
    }
    if (!positive(input.getSteelOuterDiameterM())) {
      readiness.addBlocker("F105_STEEL_DIAMETER_INVALID", "Steel outside diameter must be finite and positive",
          "Supply steel outside diameter in m");
    }
    if (!positive(input.getSteelWallThicknessM()) || (finite(input.getSteelOuterDiameterM())
        && input.getSteelWallThicknessM() >= input.getSteelOuterDiameterM() / 2.0)) {
      readiness.addBlocker("F105_WALL_THICKNESS_INVALID",
          "Steel wall thickness must be finite, positive, and less than half the steel outside diameter",
          "Supply the structural wall thickness in m");
    }
    if (!positive(input.getHydrodynamicDiameterM()) || (positive(input.getSteelOuterDiameterM())
        && input.getHydrodynamicDiameterM() < input.getSteelOuterDiameterM())) {
      readiness.addBlocker("F105_HYDRODYNAMIC_DIAMETER_INVALID",
          "Hydrodynamic diameter must be finite and at least the steel outside diameter",
          "Include applicable coatings and marine growth in the hydrodynamic diameter");
    }
    if (positive(input.getSpanLengthM()) && positive(input.getHydrodynamicDiameterM())
        && input.getSpanLengthM() / input.getHydrodynamicDiameterM() < MINIMUM_SPAN_TO_HYDRODYNAMIC_DIAMETER) {
      readiness.addBlocker("F105_BEAM_SLENDERNESS_INVALID",
          "Span-to-hydrodynamic-diameter ratio is below the NeqSim beam-screening minimum of 10",
          "Use a structural model appropriate for a short span or revise verified geometry");
    }
    if (!positive(input.getYoungsModulusPa())) {
      readiness.addBlocker("F105_YOUNGS_MODULUS_INVALID", "Young's modulus must be finite and positive",
          "Supply the project structural modulus in Pa");
    }
    if (!positive(input.getEffectiveMassPerLengthKgPerM())) {
      readiness.addBlocker("F105_EFFECTIVE_MASS_INVALID", "Effective mass per length must be finite and positive",
          "Supply verified pipe, coating, content, entrained-water, and added-mass basis in kg/m");
    }
    if (!finite(input.getEffectiveAxialForceN())) {
      readiness.addBlocker("F105_EFFECTIVE_FORCE_INVALID", "Effective axial force must be finite",
          "Supply tension as positive or compression as negative in N");
    }
    if (!nonNegative(input.getCurrentVelocityMPerS()) || !nonNegative(input.getWaveOrbitalVelocityAmplitudeMPerS())) {
      readiness.addBlocker("F105_ENVIRONMENT_VELOCITY_INVALID",
          "Current and wave orbital velocity amplitudes must be finite and non-negative",
          "Supply velocities normal to the span in m/s");
    } else if (input.getCurrentVelocityMPerS() == 0.0 && input.getWaveOrbitalVelocityAmplitudeMPerS() == 0.0) {
      readiness.addBlocker("F105_ENVIRONMENT_MISSING", "At least one current or wave velocity must be positive",
          "Supply the governing environmental velocity basis");
    }
    if (positive(input.getWaveOrbitalVelocityAmplitudeMPerS()) && !positive(input.getWavePeriodS())) {
      readiness.addBlocker("F105_WAVE_PERIOD_INVALID", "Positive wave velocity requires a finite positive period",
          "Supply the governing wave period in s");
    }
    if (!positive(input.getStrouhalNumber()) || input.getStrouhalNumber() >= 1.0) {
      readiness.addBlocker("F105_STROUHAL_INVALID", "Strouhal number must be finite, above zero, and below one",
          "Supply a controlled hydrodynamic Strouhal number");
    }
    if (!positive(input.getLockInFrequencyRatioLower()) || !finite(input.getLockInFrequencyRatioUpper())
        || input.getLockInFrequencyRatioUpper() <= input.getLockInFrequencyRatioLower()) {
      readiness.addBlocker("F105_FREQUENCY_TRIGGER_INVALID",
          "Current frequency-ratio trigger requires finite positive ordered lower and upper limits",
          "Supply the project-controlled response-screening band");
    }
    if (!positive(input.getMaxCurrentReducedVelocityForScreening())
        || !positive(input.getMaxWaveReducedVelocityForScreening())) {
      readiness.addBlocker("F105_REDUCED_VELOCITY_TRIGGER_INVALID",
          "Current and wave reduced-velocity triggers must be finite and positive",
          "Supply project-controlled response-screening limits");
    }
    if (!input.isSpanGeometryVerified()) {
      readiness.addBlocker("F105_GEOMETRY_NOT_VERIFIED", "Surveyed free-span geometry has not been verified",
          "Verify span length, shoulders, gaps, pipe dimensions, coatings, and marine growth");
    }
    if (!input.isStructuralModelVerified()) {
      readiness.addBlocker("F105_STRUCTURAL_MODEL_NOT_VERIFIED",
          "The simply supported first-mode beam model has not been accepted for this span",
          "Verify supports, soil stiffness, effective force, stiffness, effective mass, and mode applicability");
    }
    if (!input.isEnvironmentalBasisVerified()) {
      readiness.addBlocker("F105_ENVIRONMENT_NOT_VERIFIED", "Environmental inputs have not been verified",
          "Verify current, wave, directionality, return periods, seabed proximity, and combinations");
    }
    if (!input.isProjectScreeningLimitsVerified()) {
      readiness.addBlocker("F105_LIMITS_NOT_VERIFIED", "Project response-screening triggers are not verified",
          "Verify controlled limits against the licensed standard and project design basis");
    }
    CalculationReadiness preliminary = readiness.build();
    if (preliminary.isReady()) {
      double inertia = secondMomentOfArea(input);
      double waveNumber = Math.PI / input.getSpanLengthM();
      double omegaSquared = (input.getYoungsModulusPa() * inertia * Math.pow(waveNumber, 4.0)
          + input.getEffectiveAxialForceN() * waveNumber * waveNumber) / input.getEffectiveMassPerLengthKgPerM();
      if (!positive(inertia) || !positive(omegaSquared)) {
        readiness.addBlocker("F105_MODAL_SOLUTION_INVALID",
            "Pipe stiffness and effective axial force do not produce a stable finite first mode",
            "Review compression against Euler instability and verify structural inputs");
      }
    }
    readiness.addWarning("F105_CALLER_CONTROLLED_TRIGGERS",
        "Response triggers are caller-controlled evidence; NeqSim does not reproduce DNV response or acceptance tables",
        "Retain licensed-source references, project values, applicability, and approval with the result");
    readiness.addWarning("F105_SIMPLE_FIRST_MODE_ONLY",
        "The kernel is a simply supported first-mode screen, not a ULS, FLS, VIV, or direct-wave response analysis",
        "Perform required multi-mode response, fatigue, direct loading, soil interaction, and intervention assessment");
    return readiness.build();
  }

  /** {@inheritDoc} */
  @Override
  public EngineeringCalculationResult<DnvRpF105FreeSpanAssessment> calculate(Input input,
      EngineeringCalculationContext context) {
    EngineeringCalculationContext effectiveContext = context == null ? EngineeringCalculationContext.builder().build()
        : context;
    CalculationReadiness readiness = assess(input, effectiveContext);
    EngineeringCalculationResult.Builder<DnvRpF105FreeSpanAssessment> result = EngineeringCalculationResult
        .<DnvRpF105FreeSpanAssessment>builder("dnv-rp-f105-first-mode-free-span-screening", getMethod(),
            getMethodVersion())
        .context(effectiveContext).readiness(readiness);
    if (input == null || !readiness.isReady()) {
      return result.status(EngineeringCalculationResult.Status.BLOCKED)
          .message("DNV-RP-F105 free-span screening is blocked until readiness findings are resolved").build();
    }
    DnvRpF105FreeSpanAssessment assessment = new DnvRpF105FreeSpanAssessment(input);
    return result.status(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED).value(assessment)
        .input("standard", input.getEdition().getDisplayName()).input("equipmentType", input.getEquipmentType())
        .input("spanGeometry", spanGeometryMap(input)).input("structuralBasis", structuralBasisMap(input))
        .input("environmentalBasis", environmentalBasisMap(input)).input("responseTriggers", triggerMap(input))
        .warning("Detailed-response triggers are project-controlled and are not DNV acceptance decisions")
        .warning(
            "No ULS, FLS, response amplitude, stress range, fatigue damage, or intervention decision is calculated")
        .message("DNV-RP-F105 first-mode free-span screening completed; detailed engineering review remains required")
        .build();
  }

  static double secondMomentOfArea(Input input) {
    double insideDiameter = input.getSteelOuterDiameterM() - 2.0 * input.getSteelWallThicknessM();
    return Math.PI * (Math.pow(input.getSteelOuterDiameterM(), 4.0) - Math.pow(insideDiameter, 4.0)) / 64.0;
  }

  private static Map<String, Object> spanGeometryMap(Input input) {
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    values.put("spanLengthM", Double.valueOf(input.getSpanLengthM()));
    values.put("steelOuterDiameterM", Double.valueOf(input.getSteelOuterDiameterM()));
    values.put("steelWallThicknessM", Double.valueOf(input.getSteelWallThicknessM()));
    values.put("hydrodynamicDiameterM", Double.valueOf(input.getHydrodynamicDiameterM()));
    values.put("spanGeometryVerified", Boolean.valueOf(input.isSpanGeometryVerified()));
    return values;
  }

  private static Map<String, Object> structuralBasisMap(Input input) {
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    values.put("youngsModulusPa", Double.valueOf(input.getYoungsModulusPa()));
    values.put("effectiveMassPerLengthKgPerM", Double.valueOf(input.getEffectiveMassPerLengthKgPerM()));
    values.put("effectiveAxialForceN", Double.valueOf(input.getEffectiveAxialForceN()));
    values.put("structuralModelVerified", Boolean.valueOf(input.isStructuralModelVerified()));
    return values;
  }

  private static Map<String, Object> environmentalBasisMap(Input input) {
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    values.put("currentVelocityMPerS", Double.valueOf(input.getCurrentVelocityMPerS()));
    values.put("waveOrbitalVelocityAmplitudeMPerS", Double.valueOf(input.getWaveOrbitalVelocityAmplitudeMPerS()));
    values.put("wavePeriodS", finite(input.getWavePeriodS()) ? Double.valueOf(input.getWavePeriodS()) : null);
    values.put("strouhalNumber", Double.valueOf(input.getStrouhalNumber()));
    values.put("environmentalBasisVerified", Boolean.valueOf(input.isEnvironmentalBasisVerified()));
    return values;
  }

  private static Map<String, Object> triggerMap(Input input) {
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    values.put("lockInFrequencyRatioLower", Double.valueOf(input.getLockInFrequencyRatioLower()));
    values.put("lockInFrequencyRatioUpper", Double.valueOf(input.getLockInFrequencyRatioUpper()));
    values.put("maxCurrentReducedVelocityForScreening",
        Double.valueOf(input.getMaxCurrentReducedVelocityForScreening()));
    values.put("maxWaveReducedVelocityForScreening", Double.valueOf(input.getMaxWaveReducedVelocityForScreening()));
    values.put("projectScreeningLimitsVerified", Boolean.valueOf(input.isProjectScreeningLimitsVerified()));
    return values;
  }

  private static boolean finite(double value) {
    return Double.isFinite(value);
  }

  private static boolean positive(double value) {
    return Double.isFinite(value) && value > 0.0;
  }

  private static boolean nonNegative(double value) {
    return Double.isFinite(value) && value >= 0.0;
  }
}
