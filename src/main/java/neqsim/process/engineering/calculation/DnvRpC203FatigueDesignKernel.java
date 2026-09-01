package neqsim.process.engineering.calculation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.mechanicaldesign.designstandards.StandardApplicability;
import neqsim.process.mechanicaldesign.designstandards.StandardEdition;
import neqsim.process.mechanicaldesign.designstandards.StandardSupportLevel;
import neqsim.process.mechanicaldesign.designstandards.StandardType;

/** Edition-aware S-N and Palmgren-Miner fatigue adapter for DNV-RP-C203 screening. */
public final class DnvRpC203FatigueDesignKernel
    implements EquipmentDesignKernel<DnvRpC203FatigueDesignKernel.Input, DnvRpC203FatigueAssessment> {
  private static final long serialVersionUID = 1000L;
  private static final String IMPLEMENTED_EDITION = "2024-10+AMD:2025-10";
  private static final double CURVE_CONTINUITY_TOLERANCE = 0.01;

  /** Immutable user-supplied S-N curve definition from a controlled project source. */
  public static final class SnCurve implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String identifier;
    private final double highStressLog10A;
    private final double highStressSlope;
    private final double transitionCycles;
    private final double lowStressLog10A;
    private final double lowStressSlope;

    private SnCurve(String identifier, double highStressLog10A, double highStressSlope, double transitionCycles,
        double lowStressLog10A, double lowStressSlope) {
      if (identifier == null || identifier.trim().isEmpty()) {
        throw new IllegalArgumentException("curve identifier cannot be null or blank");
      }
      this.identifier = identifier.trim();
      this.highStressLog10A = highStressLog10A;
      this.highStressSlope = highStressSlope;
      this.transitionCycles = transitionCycles;
      this.lowStressLog10A = lowStressLog10A;
      this.lowStressSlope = lowStressSlope;
    }

    /**
     * Create a single-slope S-N curve, {@code log10(N) = log10(A) - m log10(S)}.
     *
     * @param identifier controlled curve reference
     * @param log10A base-10 intercept for stress range in MPa
     * @param slope positive S-N slope
     * @return immutable curve definition retaining the supplied values
     */
    public static SnCurve singleSlope(String identifier, double log10A, double slope) {
      return new SnCurve(identifier, log10A, slope, Double.NaN, Double.NaN, Double.NaN);
    }

    /**
     * Create a continuous bi-linear curve definition.
     *
     * @param identifier controlled curve reference
     * @param highStressLog10A high-stress base-10 intercept for stress in MPa
     * @param highStressSlope high-stress positive slope
     * @param transitionCycles cycles at the branch transition
     * @param lowStressLog10A low-stress base-10 intercept for stress in MPa
     * @param lowStressSlope low-stress positive slope
     * @return immutable curve definition retaining the supplied values
     */
    public static SnCurve biLinear(String identifier, double highStressLog10A, double highStressSlope,
        double transitionCycles, double lowStressLog10A, double lowStressSlope) {
      return new SnCurve(identifier, highStressLog10A, highStressSlope, transitionCycles, lowStressLog10A,
          lowStressSlope);
    }

    /** @return controlled curve identifier */
    public String getIdentifier() {
      return identifier;
    }

    /** @return high-stress base-10 intercept */
    public double getHighStressLog10A() {
      return highStressLog10A;
    }

    /** @return high-stress S-N slope */
    public double getHighStressSlope() {
      return highStressSlope;
    }

    /** @return transition cycles, or NaN for a single-slope curve */
    public double getTransitionCycles() {
      return transitionCycles;
    }

    /** @return low-stress base-10 intercept, or NaN for a single-slope curve */
    public double getLowStressLog10A() {
      return lowStressLog10A;
    }

    /** @return low-stress S-N slope, or NaN for a single-slope curve */
    public double getLowStressSlope() {
      return lowStressSlope;
    }

    /** @return whether two S-N branches were supplied */
    public boolean isBiLinear() {
      return !Double.isNaN(transitionCycles) || !Double.isNaN(lowStressLog10A) || !Double.isNaN(lowStressSlope);
    }

    double transitionStressRangeMPa() {
      return Math.pow(10.0, (highStressLog10A - Math.log10(transitionCycles)) / highStressSlope);
    }

    double cyclesToFailure(double effectiveStressRangeMPa) {
      double log10A = highStressLog10A;
      double slope = highStressSlope;
      if (isBiLinear() && effectiveStressRangeMPa < transitionStressRangeMPa()) {
        log10A = lowStressLog10A;
        slope = lowStressSlope;
      }
      return Math.pow(10.0, log10A) / Math.pow(effectiveStressRangeMPa, slope);
    }

    /** @return serializable curve representation */
    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("identifier", identifier);
      result.put("highStressLog10A", Double.valueOf(highStressLog10A));
      result.put("highStressSlope", Double.valueOf(highStressSlope));
      result.put("transitionCycles", isBiLinear() ? Double.valueOf(transitionCycles) : null);
      result.put("lowStressLog10A", isBiLinear() ? Double.valueOf(lowStressLog10A) : null);
      result.put("lowStressSlope", isBiLinear() ? Double.valueOf(lowStressSlope) : null);
      return result;
    }
  }

  /** One immutable stress-range spectrum bin. */
  public static final class StressBin implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String label;
    private final double nominalStressRangeMPa;
    private final double numberOfCycles;

    private StressBin(String label, double nominalStressRangeMPa, double numberOfCycles) {
      if (label == null || label.trim().isEmpty()) {
        throw new IllegalArgumentException("stress-bin label cannot be null or blank");
      }
      this.label = label.trim();
      this.nominalStressRangeMPa = nominalStressRangeMPa;
      this.numberOfCycles = numberOfCycles;
    }

    /** @return spectrum-bin label */
    public String getLabel() {
      return label;
    }

    /** @return nominal stress range in MPa before supplied factors */
    public double getNominalStressRangeMPa() {
      return nominalStressRangeMPa;
    }

    /** @return cycles during the assessed exposure */
    public double getNumberOfCycles() {
      return numberOfCycles;
    }

    /** @return serializable spectrum-bin representation */
    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("label", label);
      result.put("nominalStressRangeMPa", Double.valueOf(nominalStressRangeMPa));
      result.put("numberOfCycles", Double.valueOf(numberOfCycles));
      return result;
    }
  }

  /** Immutable, unit-explicit fatigue-screening input. */
  public static final class Input implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final StandardEdition edition;
    private final String equipmentType;
    private final SnCurve snCurve;
    private final List<StressBin> stressBins;
    private final double stressConcentrationFactor;
    private final double thicknessCorrectionFactor;
    private final double otherStressRangeFactor;
    private final double designFatigueFactor;
    private final double minerDamageLimit;
    private final double assessedExposureYears;
    private final boolean curveDefinitionVerified;
    private final boolean stressSpectrumVerified;

    private Input(Builder builder) {
      edition = builder.edition;
      equipmentType = builder.equipmentType;
      snCurve = builder.snCurve;
      stressBins = Collections.unmodifiableList(new ArrayList<StressBin>(builder.stressBins));
      stressConcentrationFactor = builder.stressConcentrationFactor;
      thicknessCorrectionFactor = builder.thicknessCorrectionFactor;
      otherStressRangeFactor = builder.otherStressRangeFactor;
      designFatigueFactor = builder.designFatigueFactor;
      minerDamageLimit = builder.minerDamageLimit;
      assessedExposureYears = builder.assessedExposureYears;
      curveDefinitionVerified = builder.curveDefinitionVerified;
      stressSpectrumVerified = builder.stressSpectrumVerified;
    }

    /**
     * Create a builder with an explicit standard and structural-component basis.
     *
     * @param edition DNV-RP-C203 edition
     * @param equipmentType supported equipment or component type
     * @return input builder
     */
    public static Builder builder(StandardEdition edition, String equipmentType) {
      return new Builder(edition, equipmentType);
    }

    /** @return explicit edition */
    public StandardEdition getEdition() {
      return edition;
    }

    /** @return equipment or component type */
    public String getEquipmentType() {
      return equipmentType;
    }

    /** @return controlled S-N curve */
    public SnCurve getSnCurve() {
      return snCurve;
    }

    /** @return immutable stress spectrum */
    public List<StressBin> getStressBins() {
      return stressBins;
    }

    /** @return stress concentration factor applied to every nominal stress range */
    public double getStressConcentrationFactor() {
      return stressConcentrationFactor;
    }

    /** @return thickness correction factor applied to every nominal stress range */
    public double getThicknessCorrectionFactor() {
      return thicknessCorrectionFactor;
    }

    /** @return other controlled stress-range factor */
    public double getOtherStressRangeFactor() {
      return otherStressRangeFactor;
    }

    /** @return project design fatigue factor */
    public double getDesignFatigueFactor() {
      return designFatigueFactor;
    }

    /** @return Palmgren-Miner damage limit */
    public double getMinerDamageLimit() {
      return minerDamageLimit;
    }

    /** @return years represented by the supplied cycle counts */
    public double getAssessedExposureYears() {
      return assessedExposureYears;
    }

    /** @return whether the curve was verified against the controlled project source */
    public boolean isCurveDefinitionVerified() {
      return curveDefinitionVerified;
    }

    /** @return whether spectrum derivation and cycle counts were externally verified */
    public boolean isStressSpectrumVerified() {
      return stressSpectrumVerified;
    }

    /** Builder retaining raw numeric inputs for fail-closed readiness checks. */
    public static final class Builder {
      private final StandardEdition edition;
      private final String equipmentType;
      private SnCurve snCurve;
      private final List<StressBin> stressBins = new ArrayList<StressBin>();
      private double stressConcentrationFactor = Double.NaN;
      private double thicknessCorrectionFactor = Double.NaN;
      private double otherStressRangeFactor = Double.NaN;
      private double designFatigueFactor = Double.NaN;
      private double minerDamageLimit = Double.NaN;
      private double assessedExposureYears = Double.NaN;
      private boolean curveDefinitionVerified;
      private boolean stressSpectrumVerified;

      private Builder(StandardEdition edition, String equipmentType) {
        if (edition == null || edition.getStandardType() != StandardType.DNV_RP_C203) {
          throw new IllegalArgumentException("edition must identify DNV-RP-C203");
        }
        if (equipmentType == null || equipmentType.trim().isEmpty()) {
          throw new IllegalArgumentException("equipmentType cannot be null or blank");
        }
        this.edition = edition;
        this.equipmentType = equipmentType.trim();
      }

      /**
       * Set the externally controlled S-N curve.
       *
       * @param value controlled S-N curve
       * @return this builder
       */
      public Builder snCurve(SnCurve value) {
        snCurve = value;
        return this;
      }

      /**
       * Add one spectrum bin.
       *
       * @param label bin label
       * @param nominalStressRangeMPa nominal stress range in MPa
       * @param numberOfCycles cycles during the assessed exposure
       * @return this builder
       */
      public Builder addStressBin(String label, double nominalStressRangeMPa, double numberOfCycles) {
        stressBins.add(new StressBin(label, nominalStressRangeMPa, numberOfCycles));
        return this;
      }

      /**
       * Set the stress concentration factor.
       *
       * @param value stress concentration factor
       * @return this builder
       */
      public Builder stressConcentrationFactor(double value) {
        stressConcentrationFactor = value;
        return this;
      }

      /**
       * Set the thickness correction factor.
       *
       * @param value thickness correction factor
       * @return this builder
       */
      public Builder thicknessCorrectionFactor(double value) {
        thicknessCorrectionFactor = value;
        return this;
      }

      /**
       * Set any other controlled stress-range factor.
       *
       * @param value other controlled stress-range factor
       * @return this builder
       */
      public Builder otherStressRangeFactor(double value) {
        otherStressRangeFactor = value;
        return this;
      }

      /**
       * Set the project design fatigue factor.
       *
       * @param value project design fatigue factor
       * @return this builder
       */
      public Builder designFatigueFactor(double value) {
        designFatigueFactor = value;
        return this;
      }

      /**
       * Set the Palmgren-Miner damage limit.
       *
       * @param value Palmgren-Miner damage limit
       * @return this builder
       */
      public Builder minerDamageLimit(double value) {
        minerDamageLimit = value;
        return this;
      }

      /**
       * Set the years represented by the supplied spectrum.
       *
       * @param value years represented by the spectrum
       * @return this builder
       */
      public Builder assessedExposureYears(double value) {
        assessedExposureYears = value;
        return this;
      }

      /**
       * Record whether the curve values were externally verified.
       *
       * @param value whether curve values were externally verified
       * @return this builder
       */
      public Builder curveDefinitionVerified(boolean value) {
        curveDefinitionVerified = value;
        return this;
      }

      /**
       * Record whether the spectrum derivation was externally verified.
       *
       * @param value whether spectrum derivation was externally verified
       * @return this builder
       */
      public Builder stressSpectrumVerified(boolean value) {
        stressSpectrumVerified = value;
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
    return StandardType.DNV_RP_C203;
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
    return "dnv-rp-c203-sn-miner-fatigue-screening";
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
      return readiness
          .addBlocker("C203_INPUT_MISSING", "DNV-RP-C203 fatigue input is required",
              "Provide an edition, component type, controlled S-N curve, factored spectrum, exposure, and damage basis")
          .build();
    }
    StandardApplicability applicability = applicability(input);
    if (!applicability.isApplicable()) {
      readiness.addBlocker("C203_NOT_APPLICABLE", applicability.getReason(),
          "Use Pipeline, AdiabaticPipe, Pipe, Riser, or OffshoreStructure");
    }
    if (!supports(input.getEdition())) {
      readiness.addBlocker("C203_EDITION_NOT_IMPLEMENTED",
          "The kernel implements " + IMPLEMENTED_EDITION + ", not " + input.getEdition().getDisplayName(),
          "Select the catalogued edition without project amendments or implement a separately controlled method");
    }
    validateCurve(input, readiness);
    if (!input.isCurveDefinitionVerified()) {
      readiness.addBlocker("C203_CURVE_NOT_VERIFIED",
          "The supplied S-N parameters require verification against the licensed project curve basis",
          "Check curve identifier, environment, detail category, thickness basis, and edition, then record approval");
    }
    if (input.getStressBins().isEmpty()) {
      readiness.addBlocker("C203_SPECTRUM_MISSING", "At least one stress-range spectrum bin is required",
          "Supply rainflow-counted or otherwise approved stress ranges and cycles");
    } else {
      boolean positiveCyclesPresent = false;
      for (int index = 0; index < input.getStressBins().size(); index++) {
        StressBin bin = input.getStressBins().get(index);
        if (!positive(bin.getNominalStressRangeMPa())) {
          readiness.addBlocker("C203_STRESS_RANGE_INVALID",
              "Stress range in bin " + bin.getLabel() + " must be finite and positive",
              "Supply the nominal structural stress range in MPa");
        }
        if (!nonNegative(bin.getNumberOfCycles())) {
          readiness.addBlocker("C203_CYCLE_COUNT_INVALID",
              "Cycle count in bin " + bin.getLabel() + " must be finite and non-negative",
              "Supply cycles during the assessed exposure");
        } else if (bin.getNumberOfCycles() > 0.0) {
          positiveCyclesPresent = true;
        }
      }
      if (!positiveCyclesPresent) {
        readiness.addBlocker("C203_POSITIVE_CYCLES_REQUIRED", "The spectrum contains no positive cycle count",
            "Supply at least one fatigue cycle during the assessed exposure");
      }
    }
    if (!input.isStressSpectrumVerified()) {
      readiness.addBlocker("C203_SPECTRUM_NOT_VERIFIED",
          "Stress derivation, load combination, and cycle counts require external verification",
          "Verify structural analysis, SCFs, transients, simultaneous loads, and counting method");
    }
    if (!positive(input.getStressConcentrationFactor())) {
      readiness.addBlocker("C203_SCF_INVALID", "Stress concentration factor must be finite and positive",
          "Supply the approved SCF explicitly, using 1.0 only when justified");
    }
    if (!positive(input.getThicknessCorrectionFactor())) {
      readiness.addBlocker("C203_THICKNESS_FACTOR_INVALID", "Thickness correction factor must be finite and positive",
          "Calculate and supply the approved thickness factor explicitly");
    }
    if (!positive(input.getOtherStressRangeFactor())) {
      readiness.addBlocker("C203_OTHER_FACTOR_INVALID", "Other stress-range factor must be finite and positive",
          "Supply 1.0 when no other approved stress-range factor applies");
    }
    if (!Double.isFinite(input.getDesignFatigueFactor()) || input.getDesignFatigueFactor() < 1.0) {
      readiness.addBlocker("C203_DFF_INVALID", "Design fatigue factor must be finite and at least 1",
          "Supply the project-selected design fatigue factor");
    }
    if (!positive(input.getMinerDamageLimit()) || input.getMinerDamageLimit() > 1.0) {
      readiness.addBlocker("C203_DAMAGE_LIMIT_INVALID", "Miner damage limit must be above 0 and at most 1",
          "Supply the approved cumulative-damage limit");
    }
    if (!positive(input.getAssessedExposureYears())) {
      readiness.addBlocker("C203_EXPOSURE_INVALID", "Assessed exposure must be finite and positive",
          "Supply the years represented by the cycle counts");
    }
    CalculationReadiness preliminary = readiness.build();
    if (preliminary.isReady()) {
      double stressRangeFactor = input.getStressConcentrationFactor() * input.getThicknessCorrectionFactor()
          * input.getOtherStressRangeFactor();
      double cumulativeDamage = 0.0;
      for (StressBin bin : input.getStressBins()) {
        double cyclesToFailure = input.getSnCurve().cyclesToFailure(bin.getNominalStressRangeMPa() * stressRangeFactor);
        double damage = bin.getNumberOfCycles() / cyclesToFailure;
        if (!positive(cyclesToFailure) || !nonNegative(damage)) {
          readiness.addBlocker("C203_NUMERICAL_RANGE_INVALID",
              "The supplied curve and spectrum produce a non-finite result in bin " + bin.getLabel(),
              "Review logarithm basis, units, curve parameters, stress factors, and spectrum range");
          break;
        }
        cumulativeDamage += damage;
      }
      if (!positive(cumulativeDamage)) {
        readiness.addBlocker("C203_DAMAGE_NOT_POSITIVE",
            "The supplied spectrum and curve do not produce finite positive cumulative damage",
            "Review curve magnitude, stress units, cycle counts, and numerical range");
      }
    }
    readiness.addWarning("C203_EXTERNAL_CURVE",
        "NeqSim does not select or embed DNV S-N tables; the caller supplies and verifies controlled curve parameters",
        "Retain the licensed curve, detail category, environment, thickness, weld, and fabrication evidence");
    readiness.addWarning("C203_SCREENING_ONLY",
        "The kernel applies supplied factors and Palmgren-Miner summation but is not a fatigue conformity assessment",
        "Review structural stress derivation, spectra, simultaneous loads, SCFs, inspection, and acceptance externally");
    return readiness.build();
  }

  /** {@inheritDoc} */
  @Override
  public EngineeringCalculationResult<DnvRpC203FatigueAssessment> calculate(Input input,
      EngineeringCalculationContext context) {
    EngineeringCalculationContext effectiveContext = context == null ? EngineeringCalculationContext.builder().build()
        : context;
    CalculationReadiness readiness = assess(input, effectiveContext);
    EngineeringCalculationResult.Builder<DnvRpC203FatigueAssessment> result = EngineeringCalculationResult
        .<DnvRpC203FatigueAssessment>builder("dnv-rp-c203-sn-miner-fatigue-screening", getMethod(), getMethodVersion())
        .context(effectiveContext).readiness(readiness);
    if (input == null || !readiness.isReady()) {
      return result.status(EngineeringCalculationResult.Status.BLOCKED)
          .message("DNV-RP-C203 fatigue screening is blocked until readiness findings are resolved").build();
    }
    DnvRpC203FatigueAssessment assessment = new DnvRpC203FatigueAssessment(input);
    return result.status(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED).value(assessment)
        .input("standard", input.getEdition().getDisplayName()).input("equipmentType", input.getEquipmentType())
        .input("snCurve", input.getSnCurve().toMap()).input("stressBins", binMaps(input.getStressBins()))
        .input("stressConcentrationFactor", Double.valueOf(input.getStressConcentrationFactor()))
        .input("thicknessCorrectionFactor", Double.valueOf(input.getThicknessCorrectionFactor()))
        .input("otherStressRangeFactor", Double.valueOf(input.getOtherStressRangeFactor()))
        .input("designFatigueFactor", Double.valueOf(input.getDesignFatigueFactor()))
        .input("minerDamageLimit", Double.valueOf(input.getMinerDamageLimit()))
        .input("assessedExposureYears", Double.valueOf(input.getAssessedExposureYears()))
        .input("curveDefinitionVerified", Boolean.valueOf(input.isCurveDefinitionVerified()))
        .input("stressSpectrumVerified", Boolean.valueOf(input.isStressSpectrumVerified()))
        .warning("S-N parameters and stress spectrum are caller-controlled evidence, not selected by NeqSim")
        .warning("Estimated life is linear extrapolation of the supplied exposure and Palmgren-Miner damage")
        .message("DNV-RP-C203 S-N/Miner screening completed; independent fatigue review remains required").build();
  }

  private static void validateCurve(Input input, CalculationReadiness.Builder readiness) {
    SnCurve curve = input.getSnCurve();
    if (curve == null) {
      readiness.addBlocker("C203_CURVE_MISSING", "A controlled S-N curve definition is required",
          "Supply single-slope or continuous bi-linear curve parameters from the licensed project basis");
      return;
    }
    if (!finite(curve.getHighStressLog10A()) || !positive(curve.getHighStressSlope())) {
      readiness.addBlocker("C203_HIGH_STRESS_CURVE_INVALID",
          "High-stress S-N intercept must be finite and slope must be positive",
          "Verify log10(A), slope, units, and logarithm basis");
    }
    if (curve.isBiLinear()) {
      if (!positive(curve.getTransitionCycles()) || !finite(curve.getLowStressLog10A())
          || !positive(curve.getLowStressSlope())) {
        readiness.addBlocker("C203_LOW_STRESS_CURVE_INVALID",
            "Bi-linear curve requires positive transition cycles, finite low-stress intercept, and positive slope",
            "Supply a complete controlled bi-linear curve");
      } else if (finite(curve.getHighStressLog10A()) && positive(curve.getHighStressSlope())) {
        double transitionStress = curve.transitionStressRangeMPa();
        double lowBranchCycles = Math.pow(10.0, curve.getLowStressLog10A())
            / Math.pow(transitionStress, curve.getLowStressSlope());
        double mismatch = Math.abs(lowBranchCycles - curve.getTransitionCycles()) / curve.getTransitionCycles();
        if (!finite(mismatch) || mismatch > CURVE_CONTINUITY_TOLERANCE) {
          readiness.addBlocker("C203_CURVE_DISCONTINUOUS",
              "S-N branches differ by more than 1% at the supplied transition",
              "Correct the intercepts, slopes, or transition cycles using the controlled curve definition");
        }
      }
    }
  }

  private static List<Map<String, Object>> binMaps(List<StressBin> bins) {
    List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
    for (StressBin bin : bins) {
      result.add(bin.toMap());
    }
    return result;
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
