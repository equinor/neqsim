package neqsim.process.engineering.calculation;

import java.io.Serializable;
import neqsim.process.corrosion.NorsokM506CorrosionRate;
import neqsim.process.mechanicaldesign.designstandards.StandardApplicability;
import neqsim.process.mechanicaldesign.designstandards.StandardEdition;
import neqsim.process.mechanicaldesign.designstandards.StandardSupportLevel;
import neqsim.process.mechanicaldesign.designstandards.StandardType;

/** Pure, edition-aware adapter around the existing NORSOK M-506 corrosion calculator. */
public final class NorsokM506CorrosionDesignKernel
    implements EquipmentDesignKernel<NorsokM506CorrosionDesignKernel.Input, NorsokM506CorrosionAssessment> {
  private static final long serialVersionUID = 1000L;
  private static final String IMPLEMENTED_EDITION = "2017";

  /** Immutable, unit-explicit corrosion screening input. */
  public static final class Input implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final StandardEdition edition;
    private final String equipmentType;
    private final double temperatureC;
    private final double totalPressureBara;
    private final double co2MoleFraction;
    private final double actualPH;
    private final double bicarbonateConcentrationMgPerL;
    private final double ionicStrengthMolPerL;
    private final double flowVelocityMPerS;
    private final double pipeInternalDiameterM;
    private final double liquidDensityKgPerM3;
    private final double liquidDynamicViscosityPaS;
    private final double inhibitorEfficiencyFraction;
    private final double glycolWeightFraction;
    private final double feCO3SaturationRatio;
    private final boolean phCorrectionEnabled;
    private final boolean scaleCorrectionEnabled;
    private final boolean flowCorrectionEnabled;
    private final double exposureYears;

    private Input(Builder builder) {
      edition = builder.edition;
      equipmentType = builder.equipmentType;
      temperatureC = builder.temperatureC;
      totalPressureBara = builder.totalPressureBara;
      co2MoleFraction = builder.co2MoleFraction;
      actualPH = builder.actualPH;
      bicarbonateConcentrationMgPerL = builder.bicarbonateConcentrationMgPerL;
      ionicStrengthMolPerL = builder.ionicStrengthMolPerL;
      flowVelocityMPerS = builder.flowVelocityMPerS;
      pipeInternalDiameterM = builder.pipeInternalDiameterM;
      liquidDensityKgPerM3 = builder.liquidDensityKgPerM3;
      liquidDynamicViscosityPaS = builder.liquidDynamicViscosityPaS;
      inhibitorEfficiencyFraction = builder.inhibitorEfficiencyFraction;
      glycolWeightFraction = builder.glycolWeightFraction;
      feCO3SaturationRatio = builder.feCO3SaturationRatio;
      phCorrectionEnabled = builder.phCorrectionEnabled;
      scaleCorrectionEnabled = builder.scaleCorrectionEnabled;
      flowCorrectionEnabled = builder.flowCorrectionEnabled;
      exposureYears = builder.exposureYears;
    }

    /**
     * Create a builder with an explicit standard and equipment basis.
     *
     * @param edition NORSOK M-506 edition
     * @param equipmentType simple equipment class name
     * @return input builder
     */
    public static Builder builder(StandardEdition edition, String equipmentType) {
      return new Builder(edition, equipmentType);
    }

    /** @return explicit edition */
    public StandardEdition getEdition() {
      return edition;
    }

    /** @return simple equipment class name */
    public String getEquipmentType() {
      return equipmentType;
    }

    /** @return temperature in degrees Celsius */
    public double getTemperatureC() {
      return temperatureC;
    }

    /** @return total pressure in bara */
    public double getTotalPressureBara() {
      return totalPressureBara;
    }

    /** @return gas-phase CO2 mole fraction */
    public double getCO2MoleFraction() {
      return co2MoleFraction;
    }

    /** @return measured or rigorous in-situ pH, or NaN to use the legacy estimate */
    public double getActualPH() {
      return actualPH;
    }

    /** @return bicarbonate concentration in mg/L */
    public double getBicarbonateConcentrationMgPerL() {
      return bicarbonateConcentrationMgPerL;
    }

    /** @return ionic strength in mol/L */
    public double getIonicStrengthMolPerL() {
      return ionicStrengthMolPerL;
    }

    /** @return liquid velocity in m/s */
    public double getFlowVelocityMPerS() {
      return flowVelocityMPerS;
    }

    /** @return pipe internal diameter in metres */
    public double getPipeInternalDiameterM() {
      return pipeInternalDiameterM;
    }

    /** @return liquid density in kg/m3 */
    public double getLiquidDensityKgPerM3() {
      return liquidDensityKgPerM3;
    }

    /** @return liquid dynamic viscosity in Pa.s */
    public double getLiquidDynamicViscosityPaS() {
      return liquidDynamicViscosityPaS;
    }

    /** @return assumed inhibitor efficiency as a fraction */
    public double getInhibitorEfficiencyFraction() {
      return inhibitorEfficiencyFraction;
    }

    /** @return glycol weight fraction in the aqueous phase */
    public double getGlycolWeightFraction() {
      return glycolWeightFraction;
    }

    /** @return FeCO3 saturation ratio, or -1 when the NeqSim extension is disabled */
    public double getFeCO3SaturationRatio() {
      return feCO3SaturationRatio;
    }

    /** @return whether the pH correction is enabled */
    public boolean isPHCorrectionEnabled() {
      return phCorrectionEnabled;
    }

    /** @return whether the scale correction is enabled */
    public boolean isScaleCorrectionEnabled() {
      return scaleCorrectionEnabled;
    }

    /** @return whether the flow correction is enabled */
    public boolean isFlowCorrectionEnabled() {
      return flowCorrectionEnabled;
    }

    /** @return exposure period in years */
    public double getExposureYears() {
      return exposureYears;
    }

    /** Builder that retains raw values so readiness checks can reject them without silent clamping. */
    public static final class Builder {
      private final StandardEdition edition;
      private final String equipmentType;
      private double temperatureC = Double.NaN;
      private double totalPressureBara = Double.NaN;
      private double co2MoleFraction = Double.NaN;
      private double actualPH = Double.NaN;
      private double bicarbonateConcentrationMgPerL = 0.0;
      private double ionicStrengthMolPerL = 0.0;
      private double flowVelocityMPerS = Double.NaN;
      private double pipeInternalDiameterM = Double.NaN;
      private double liquidDensityKgPerM3 = Double.NaN;
      private double liquidDynamicViscosityPaS = Double.NaN;
      private double inhibitorEfficiencyFraction = 0.0;
      private double glycolWeightFraction = 0.0;
      private double feCO3SaturationRatio = -1.0;
      private boolean phCorrectionEnabled = true;
      private boolean scaleCorrectionEnabled = true;
      private boolean flowCorrectionEnabled = true;
      private double exposureYears = Double.NaN;

      private Builder(StandardEdition edition, String equipmentType) {
        if (edition == null || edition.getStandardType() != StandardType.NORSOK_M_506) {
          throw new IllegalArgumentException("edition must identify NORSOK-M-506");
        }
        if (equipmentType == null || equipmentType.trim().isEmpty()) {
          throw new IllegalArgumentException("equipmentType cannot be null or blank");
        }
        this.edition = edition;
        this.equipmentType = equipmentType.trim();
      }

      /**
       * @param value temperature in degrees Celsius
       * @return this builder
       */
      public Builder temperatureC(double value) {
        temperatureC = value;
        return this;
      }

      /**
       * @param value total pressure in bara
       * @return this builder
       */
      public Builder totalPressureBara(double value) {
        totalPressureBara = value;
        return this;
      }

      /**
       * @param value gas-phase CO2 mole fraction
       * @return this builder
       */
      public Builder co2MoleFraction(double value) {
        co2MoleFraction = value;
        return this;
      }

      /**
       * @param value measured or rigorous in-situ pH
       * @return this builder
       */
      public Builder actualPH(double value) {
        actualPH = value;
        return this;
      }

      /**
       * @param value bicarbonate concentration in mg/L
       * @return this builder
       */
      public Builder bicarbonateConcentrationMgPerL(double value) {
        bicarbonateConcentrationMgPerL = value;
        return this;
      }

      /**
       * @param value ionic strength in mol/L
       * @return this builder
       */
      public Builder ionicStrengthMolPerL(double value) {
        ionicStrengthMolPerL = value;
        return this;
      }

      /**
       * @param value liquid velocity in m/s
       * @return this builder
       */
      public Builder flowVelocityMPerS(double value) {
        flowVelocityMPerS = value;
        return this;
      }

      /**
       * @param value pipe internal diameter in metres
       * @return this builder
       */
      public Builder pipeInternalDiameterM(double value) {
        pipeInternalDiameterM = value;
        return this;
      }

      /**
       * @param value liquid density in kg/m3
       * @return this builder
       */
      public Builder liquidDensityKgPerM3(double value) {
        liquidDensityKgPerM3 = value;
        return this;
      }

      /**
       * @param value liquid dynamic viscosity in Pa.s
       * @return this builder
       */
      public Builder liquidDynamicViscosityPaS(double value) {
        liquidDynamicViscosityPaS = value;
        return this;
      }

      /**
       * @param value assumed inhibitor efficiency fraction
       * @return this builder
       */
      public Builder inhibitorEfficiencyFraction(double value) {
        inhibitorEfficiencyFraction = value;
        return this;
      }

      /**
       * @param value glycol weight fraction in the aqueous phase
       * @return this builder
       */
      public Builder glycolWeightFraction(double value) {
        glycolWeightFraction = value;
        return this;
      }

      /**
       * @param value FeCO3 saturation ratio, or -1 to disable
       * @return this builder
       */
      public Builder feCO3SaturationRatio(double value) {
        feCO3SaturationRatio = value;
        return this;
      }

      /**
       * @param value whether to apply the pH correction
       * @return this builder
       */
      public Builder phCorrectionEnabled(boolean value) {
        phCorrectionEnabled = value;
        return this;
      }

      /**
       * @param value whether to apply the scale correction
       * @return this builder
       */
      public Builder scaleCorrectionEnabled(boolean value) {
        scaleCorrectionEnabled = value;
        return this;
      }

      /**
       * @param value whether to apply the flow correction
       * @return this builder
       */
      public Builder flowCorrectionEnabled(boolean value) {
        flowCorrectionEnabled = value;
        return this;
      }

      /**
       * @param value exposure period in years
       * @return this builder
       */
      public Builder exposureYears(double value) {
        exposureYears = value;
        return this;
      }

      /** @return immutable input retaining every supplied value */
      public Input build() {
        return new Input(this);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public StandardType standard() {
    return StandardType.NORSOK_M_506;
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
    return "norsok-m-506-co2-corrosion-screening";
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
      return readiness.addBlocker("M506_INPUT_MISSING", "NORSOK M-506 screening input is required",
          "Provide an explicit edition, equipment type, corrosion environment, hydraulic basis, and exposure period")
          .build();
    }

    StandardApplicability decision = applicability(input);
    if (!decision.isApplicable()) {
      readiness.addBlocker("M506_NOT_APPLICABLE", decision.getReason(),
          "Use a Pipeline, AdiabaticPipe, or Pipe equipment type");
    }
    if (!supports(input.getEdition())) {
      readiness.addBlocker("M506_EDITION_NOT_IMPLEMENTED",
          "The kernel implements the unamended 2017 edition, not " + input.getEdition().getDisplayName(),
          "Select the implemented edition or add separately validated edition criteria");
    }
    if (!inRange(input.getTemperatureC(), 5.0, 150.0)) {
      readiness.addBlocker("M506_TEMPERATURE_OUT_OF_RANGE", "Temperature must be finite and within 5 to 150 degC",
          "Supply a condition inside the implemented model range");
    }
    if (!positive(input.getTotalPressureBara()) || input.getTotalPressureBara() > 1000.0) {
      readiness.addBlocker("M506_PRESSURE_OUT_OF_RANGE", "Total pressure must be above 0 and at most 1000 bara",
          "Supply the gas-phase total pressure in bara");
    }
    if (!positive(input.getCO2MoleFraction()) || input.getCO2MoleFraction() > 1.0) {
      readiness.addBlocker("M506_CO2_FRACTION_INVALID", "CO2 mole fraction must be above 0 and at most 1",
          "Supply the gas-phase CO2 mole fraction");
    }
    if (positive(input.getTotalPressureBara()) && positive(input.getCO2MoleFraction())
        && input.getTotalPressureBara() * input.getCO2MoleFraction() > 10.0) {
      readiness.addBlocker("M506_CO2_PARTIAL_PRESSURE_OUT_OF_RANGE",
          "CO2 partial pressure exceeds the implemented 10 bar applicability limit",
          "Use a method qualified for the higher CO2 partial pressure or revise the condition basis");
    }
    if (!Double.isNaN(input.getActualPH()) && !inRange(input.getActualPH(), 3.5, 6.5)) {
      readiness.addBlocker("M506_PH_OUT_OF_RANGE", "Supplied pH must be finite and within 3.5 to 6.5",
          "Supply an in-situ aqueous pH inside the implemented range, or omit it to use the legacy estimate");
    }
    if (!nonNegative(input.getBicarbonateConcentrationMgPerL())) {
      readiness.addBlocker("M506_BICARBONATE_INVALID", "Bicarbonate concentration must be finite and non-negative",
          "Supply bicarbonate concentration in mg/L");
    }
    if (!inRange(input.getIonicStrengthMolPerL(), 0.0, 5.0)) {
      readiness.addBlocker("M506_IONIC_STRENGTH_INVALID", "Ionic strength must be finite and within 0 to 5 mol/L",
          "Supply the aqueous ionic strength in mol/L");
    }
    if (!inRange(input.getFlowVelocityMPerS(), 0.0, 30.0)) {
      readiness.addBlocker("M506_FLOW_VELOCITY_INVALID", "Flow velocity must be finite and within 0 to 30 m/s",
          "Supply the governing liquid velocity in m/s");
    }
    if (!positive(input.getPipeInternalDiameterM())) {
      readiness.addBlocker("M506_PIPE_DIAMETER_INVALID", "Pipe internal diameter must be finite and positive",
          "Supply the pipe internal diameter in metres");
    }
    if (!positive(input.getLiquidDensityKgPerM3())) {
      readiness.addBlocker("M506_LIQUID_DENSITY_INVALID", "Liquid density must be finite and positive",
          "Supply the liquid density in kg/m3");
    }
    if (!positive(input.getLiquidDynamicViscosityPaS())) {
      readiness.addBlocker("M506_LIQUID_VISCOSITY_INVALID", "Liquid viscosity must be finite and positive",
          "Supply dynamic viscosity in Pa.s");
    }
    if (!inRange(input.getInhibitorEfficiencyFraction(), 0.0, 1.0)) {
      readiness.addBlocker("M506_INHIBITOR_EFFICIENCY_INVALID",
          "Inhibitor efficiency must be a finite fraction from 0 to 1",
          "Supply a project-supported inhibitor availability and efficiency basis");
    }
    if (!inRange(input.getGlycolWeightFraction(), 0.0, 1.0)) {
      readiness.addBlocker("M506_GLYCOL_FRACTION_INVALID", "Glycol weight fraction must be from 0 to 1",
          "Supply the aqueous-phase glycol weight fraction");
    }
    if (!(input.getFeCO3SaturationRatio() == -1.0 || positive(input.getFeCO3SaturationRatio()))) {
      readiness.addBlocker("M506_FECO3_SATURATION_INVALID",
          "FeCO3 saturation ratio must be -1 to disable the extension or a finite positive value",
          "Supply IAP/Ksp from a compatible aqueous-chemistry basis, or -1");
    }
    if (!positive(input.getExposureYears())) {
      readiness.addBlocker("M506_EXPOSURE_INVALID", "Exposure period must be finite and positive",
          "Supply the assessed exposure period in years");
    }

    if (canEstimatePH(input) && Double.isNaN(input.getActualPH())) {
      double estimatedPH = configuredModel(input).calculateEquilibriumPH();
      if (!inRange(estimatedPH, 3.5, 6.5)) {
        readiness.addBlocker("M506_ESTIMATED_PH_OUT_OF_RANGE",
            "The legacy equilibrium estimate gives pH " + estimatedPH + ", outside 3.5 to 6.5",
            "Supply a rigorous in-situ pH or use a method qualified for the condition");
      } else {
        readiness.addWarning("M506_PH_ESTIMATED",
            "The legacy internal CO2-water pH estimate is being used instead of a rigorous aqueous-phase pH",
            "For buffered or saline brines, calculate pH with an electrolyte model and supply actualPH");
      }
    }
    if (input.getFeCO3SaturationRatio() > 0.0) {
      readiness.addWarning("M506_FECO3_EXTENSION",
          "The FeCO3 saturation-ratio film factor is a NeqSim screening extension to the legacy calculation",
          "Report the chemistry source and review the film credit independently");
    }
    if (!input.isPHCorrectionEnabled() || !input.isScaleCorrectionEnabled() || !input.isFlowCorrectionEnabled()) {
      readiness.addWarning("M506_CORRECTION_DISABLED", "One or more corrosion correction factors are disabled",
          "Document and independently approve the project calculation basis");
    }
    readiness.addWarning("M506_SCREENING_ONLY",
        "The adapter is a deterministic screening calculation, not a NORSOK conformity assessment",
        "Verify the purchased standard, wetting basis, chemistry, materials, localized corrosion, inhibitor "
            + "availability, and project acceptance criteria");
    return readiness.build();
  }

  /** {@inheritDoc} */
  @Override
  public EngineeringCalculationResult<NorsokM506CorrosionAssessment> calculate(Input input,
      EngineeringCalculationContext context) {
    EngineeringCalculationContext effectiveContext = context == null ? EngineeringCalculationContext.builder().build()
        : context;
    CalculationReadiness readiness = assess(input, effectiveContext);
    EngineeringCalculationResult.Builder<NorsokM506CorrosionAssessment> result = EngineeringCalculationResult
        .<NorsokM506CorrosionAssessment>builder("norsok-m-506-co2-corrosion-screening", getMethod(), getMethodVersion())
        .context(effectiveContext).readiness(readiness);
    if (input == null || !readiness.isReady()) {
      return result.status(EngineeringCalculationResult.Status.BLOCKED)
          .message("NORSOK M-506 screening is blocked until the readiness findings are resolved").build();
    }

    NorsokM506CorrosionRate model = configuredModel(input);
    model.calculate();
    NorsokM506CorrosionAssessment assessment = new NorsokM506CorrosionAssessment(input, model);
    return result.status(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED).value(assessment)
        .input("standard", input.getEdition().getDisplayName()).input("equipmentType", input.getEquipmentType())
        .input("temperatureC", Double.valueOf(input.getTemperatureC()))
        .input("totalPressureBara", Double.valueOf(input.getTotalPressureBara()))
        .input("co2MoleFraction", Double.valueOf(input.getCO2MoleFraction()))
        .input("actualPH", Double.isNaN(input.getActualPH()) ? null : Double.valueOf(input.getActualPH()))
        .input("bicarbonateConcentrationMgPerL", Double.valueOf(input.getBicarbonateConcentrationMgPerL()))
        .input("ionicStrengthMolPerL", Double.valueOf(input.getIonicStrengthMolPerL()))
        .input("flowVelocityMPerS", Double.valueOf(input.getFlowVelocityMPerS()))
        .input("pipeInternalDiameterM", Double.valueOf(input.getPipeInternalDiameterM()))
        .input("liquidDensityKgPerM3", Double.valueOf(input.getLiquidDensityKgPerM3()))
        .input("liquidDynamicViscosityPaS", Double.valueOf(input.getLiquidDynamicViscosityPaS()))
        .input("inhibitorEfficiencyFraction", Double.valueOf(input.getInhibitorEfficiencyFraction()))
        .input("glycolWeightFraction", Double.valueOf(input.getGlycolWeightFraction()))
        .input("feCO3SaturationRatio", Double.valueOf(input.getFeCO3SaturationRatio()))
        .input("phCorrectionEnabled", Boolean.valueOf(input.isPHCorrectionEnabled()))
        .input("scaleCorrectionEnabled", Boolean.valueOf(input.isScaleCorrectionEnabled()))
        .input("flowCorrectionEnabled", Boolean.valueOf(input.isFlowCorrectionEnabled()))
        .input("exposureYears", Double.valueOf(input.getExposureYears()))
        .warning("Projected uniform wall loss is rate multiplied by exposure time, not a code corrosion allowance")
        .warning("NORSOK M-506 screening does not certify materials selection, integrity, or fitness for service")
        .message("NORSOK M-506 corrosion screening completed; independent engineering review remains required").build();
  }

  private static NorsokM506CorrosionRate configuredModel(Input input) {
    NorsokM506CorrosionRate model = new NorsokM506CorrosionRate();
    model.setTemperatureCelsius(input.getTemperatureC());
    model.setTotalPressureBara(input.getTotalPressureBara());
    model.setCO2MoleFraction(input.getCO2MoleFraction());
    if (!Double.isNaN(input.getActualPH())) {
      model.setActualPH(input.getActualPH());
    }
    model.setBicarbonateConcentrationMgL(input.getBicarbonateConcentrationMgPerL());
    model.setIonicStrengthMolL(input.getIonicStrengthMolPerL());
    model.setFlowVelocityMs(input.getFlowVelocityMPerS());
    model.setPipeDiameterM(input.getPipeInternalDiameterM());
    model.setLiquidDensityKgM3(input.getLiquidDensityKgPerM3());
    model.setLiquidViscosityPas(input.getLiquidDynamicViscosityPaS());
    model.setInhibitorEfficiency(input.getInhibitorEfficiencyFraction());
    model.setGlycolWeightFraction(input.getGlycolWeightFraction());
    model.setFeCO3SaturationRatio(input.getFeCO3SaturationRatio());
    model.setUsePHCorrection(input.isPHCorrectionEnabled());
    model.setUseScaleCorrection(input.isScaleCorrectionEnabled());
    model.setUseFlowCorrection(input.isFlowCorrectionEnabled());
    return model;
  }

  private static boolean canEstimatePH(Input input) {
    return inRange(input.getTemperatureC(), 5.0, 150.0) && positive(input.getTotalPressureBara())
        && input.getTotalPressureBara() <= 1000.0 && positive(input.getCO2MoleFraction())
        && input.getCO2MoleFraction() <= 1.0 && nonNegative(input.getBicarbonateConcentrationMgPerL())
        && inRange(input.getIonicStrengthMolPerL(), 0.0, 5.0);
  }

  private static boolean positive(double value) {
    return Double.isFinite(value) && value > 0.0;
  }

  private static boolean nonNegative(double value) {
    return Double.isFinite(value) && value >= 0.0;
  }

  private static boolean inRange(double value, double lower, double upper) {
    return Double.isFinite(value) && value >= lower && value <= upper;
  }
}
