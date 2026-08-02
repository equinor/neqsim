package neqsim.process.engineering.calculation;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.mechanicaldesign.designstandards.StandardApplicability;
import neqsim.process.mechanicaldesign.designstandards.StandardEdition;
import neqsim.process.mechanicaldesign.designstandards.StandardSupportLevel;
import neqsim.process.mechanicaldesign.designstandards.StandardType;

/** Edition-aware caller-controlled tank vent-demand and rated-capacity screening for API 2000. */
public final class Api2000TankVentingScreeningKernel
    implements EquipmentDesignKernel<Api2000TankVentingScreeningKernel.Input, Api2000TankVentingAssessment> {
  private static final long serialVersionUID = 1000L;
  private static final String IMPLEMENTED_EDITION = "7th Ed";

  /** Immutable unit-explicit input for one fixed-roof tank venting screen. */
  public static final class Input implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final StandardEdition edition;
    private final String equipmentType;
    private final double liquidFillingRateM3PerS;
    private final double fillingOutbreathingVolumeRatio;
    private final double liquidWithdrawalRateM3PerS;
    private final double withdrawalInbreathingVolumeRatio;
    private final double thermalOutbreathingRateM3PerS;
    private final double thermalInbreathingRateM3PerS;
    private final double otherNormalOutbreathingRateM3PerS;
    private final double otherNormalInbreathingRateM3PerS;
    private final double totalEmergencyOutbreathingRateM3PerS;
    private final double normalOutbreathingRatedCapacityM3PerS;
    private final double normalInbreathingRatedCapacityM3PerS;
    private final double emergencyOutbreathingRatedCapacityM3PerS;
    private final double tankMaximumPositiveGaugePressurePa;
    private final double tankMaximumVacuumPressurePa;
    private final double normalOutbreathingRatedGaugePressurePa;
    private final double normalInbreathingRatedVacuumPressurePa;
    private final double emergencyOutbreathingRatedGaugePressurePa;
    private final double flowReferenceTemperatureK;
    private final double flowReferencePressurePaAbsolute;
    private final boolean fixedRoofNonRefrigeratedApplicabilityVerified;
    private final boolean ventDemandBasisVerified;
    private final boolean ratedCapacityBasisVerified;
    private final boolean pressureVacuumBasisVerified;
    private final boolean normalCombinationBasisVerified;
    private final boolean emergencyCombinationBasisVerified;

    private Input(Builder builder) {
      edition = builder.edition;
      equipmentType = builder.equipmentType;
      liquidFillingRateM3PerS = builder.liquidFillingRateM3PerS;
      fillingOutbreathingVolumeRatio = builder.fillingOutbreathingVolumeRatio;
      liquidWithdrawalRateM3PerS = builder.liquidWithdrawalRateM3PerS;
      withdrawalInbreathingVolumeRatio = builder.withdrawalInbreathingVolumeRatio;
      thermalOutbreathingRateM3PerS = builder.thermalOutbreathingRateM3PerS;
      thermalInbreathingRateM3PerS = builder.thermalInbreathingRateM3PerS;
      otherNormalOutbreathingRateM3PerS = builder.otherNormalOutbreathingRateM3PerS;
      otherNormalInbreathingRateM3PerS = builder.otherNormalInbreathingRateM3PerS;
      totalEmergencyOutbreathingRateM3PerS = builder.totalEmergencyOutbreathingRateM3PerS;
      normalOutbreathingRatedCapacityM3PerS = builder.normalOutbreathingRatedCapacityM3PerS;
      normalInbreathingRatedCapacityM3PerS = builder.normalInbreathingRatedCapacityM3PerS;
      emergencyOutbreathingRatedCapacityM3PerS = builder.emergencyOutbreathingRatedCapacityM3PerS;
      tankMaximumPositiveGaugePressurePa = builder.tankMaximumPositiveGaugePressurePa;
      tankMaximumVacuumPressurePa = builder.tankMaximumVacuumPressurePa;
      normalOutbreathingRatedGaugePressurePa = builder.normalOutbreathingRatedGaugePressurePa;
      normalInbreathingRatedVacuumPressurePa = builder.normalInbreathingRatedVacuumPressurePa;
      emergencyOutbreathingRatedGaugePressurePa = builder.emergencyOutbreathingRatedGaugePressurePa;
      flowReferenceTemperatureK = builder.flowReferenceTemperatureK;
      flowReferencePressurePaAbsolute = builder.flowReferencePressurePaAbsolute;
      fixedRoofNonRefrigeratedApplicabilityVerified = builder.fixedRoofNonRefrigeratedApplicabilityVerified;
      ventDemandBasisVerified = builder.ventDemandBasisVerified;
      ratedCapacityBasisVerified = builder.ratedCapacityBasisVerified;
      pressureVacuumBasisVerified = builder.pressureVacuumBasisVerified;
      normalCombinationBasisVerified = builder.normalCombinationBasisVerified;
      emergencyCombinationBasisVerified = builder.emergencyCombinationBasisVerified;
    }

    /**
     * Create an API 2000 input builder.
     *
     * @param edition API 2000 edition
     * @param equipmentType Tank or SimpleTankFiller
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

    /** @return maximum liquid filling rate in m3/s */
    public double getLiquidFillingRateM3PerS() {
      return liquidFillingRateM3PerS;
    }

    /** @return caller-controlled reference gas volume per filled liquid volume */
    public double getFillingOutbreathingVolumeRatio() {
      return fillingOutbreathingVolumeRatio;
    }

    /** @return maximum liquid withdrawal rate in m3/s */
    public double getLiquidWithdrawalRateM3PerS() {
      return liquidWithdrawalRateM3PerS;
    }

    /** @return caller-controlled reference gas volume per withdrawn liquid volume */
    public double getWithdrawalInbreathingVolumeRatio() {
      return withdrawalInbreathingVolumeRatio;
    }

    /** @return externally established thermal outbreathing demand in reference m3/s */
    public double getThermalOutbreathingRateM3PerS() {
      return thermalOutbreathingRateM3PerS;
    }

    /** @return externally established thermal inbreathing demand in reference m3/s */
    public double getThermalInbreathingRateM3PerS() {
      return thermalInbreathingRateM3PerS;
    }

    /** @return other normal outbreathing demand in reference m3/s */
    public double getOtherNormalOutbreathingRateM3PerS() {
      return otherNormalOutbreathingRateM3PerS;
    }

    /** @return other normal inbreathing demand in reference m3/s */
    public double getOtherNormalInbreathingRateM3PerS() {
      return otherNormalInbreathingRateM3PerS;
    }

    /** @return externally established total emergency outbreathing demand in reference m3/s */
    public double getTotalEmergencyOutbreathingRateM3PerS() {
      return totalEmergencyOutbreathingRateM3PerS;
    }

    /** @return rated normal outbreathing capacity in reference m3/s */
    public double getNormalOutbreathingRatedCapacityM3PerS() {
      return normalOutbreathingRatedCapacityM3PerS;
    }

    /** @return rated normal inbreathing capacity in reference m3/s */
    public double getNormalInbreathingRatedCapacityM3PerS() {
      return normalInbreathingRatedCapacityM3PerS;
    }

    /** @return rated total emergency outbreathing capacity in reference m3/s */
    public double getEmergencyOutbreathingRatedCapacityM3PerS() {
      return emergencyOutbreathingRatedCapacityM3PerS;
    }

    /** @return tank maximum positive gauge pressure in Pa */
    public double getTankMaximumPositiveGaugePressurePa() {
      return tankMaximumPositiveGaugePressurePa;
    }

    /** @return tank maximum vacuum magnitude in Pa */
    public double getTankMaximumVacuumPressurePa() {
      return tankMaximumVacuumPressurePa;
    }

    /** @return pressure at rated normal outbreathing capacity in Pa gauge */
    public double getNormalOutbreathingRatedGaugePressurePa() {
      return normalOutbreathingRatedGaugePressurePa;
    }

    /** @return vacuum magnitude at rated normal inbreathing capacity in Pa */
    public double getNormalInbreathingRatedVacuumPressurePa() {
      return normalInbreathingRatedVacuumPressurePa;
    }

    /** @return pressure at rated emergency outbreathing capacity in Pa gauge */
    public double getEmergencyOutbreathingRatedGaugePressurePa() {
      return emergencyOutbreathingRatedGaugePressurePa;
    }

    /** @return common volumetric-flow reference temperature in K */
    public double getFlowReferenceTemperatureK() {
      return flowReferenceTemperatureK;
    }

    /** @return common volumetric-flow reference absolute pressure in Pa */
    public double getFlowReferencePressurePaAbsolute() {
      return flowReferencePressurePaAbsolute;
    }

    /** @return whether the non-refrigerated fixed-roof scope was verified */
    public boolean isFixedRoofNonRefrigeratedApplicabilityVerified() {
      return fixedRoofNonRefrigeratedApplicabilityVerified;
    }

    /** @return whether flow factors and demand cases were externally verified */
    public boolean isVentDemandBasisVerified() {
      return ventDemandBasisVerified;
    }

    /** @return whether device ratings use the same gas and reference basis */
    public boolean isRatedCapacityBasisVerified() {
      return ratedCapacityBasisVerified;
    }

    /** @return whether tank and rated pressure/vacuum bases were verified */
    public boolean isPressureVacuumBasisVerified() {
      return pressureVacuumBasisVerified;
    }

    /** @return whether normal simultaneous/additive demand combinations were verified */
    public boolean isNormalCombinationBasisVerified() {
      return normalCombinationBasisVerified;
    }

    /** @return whether emergency demand and available-device combination were verified */
    public boolean isEmergencyCombinationBasisVerified() {
      return emergencyCombinationBasisVerified;
    }

    /** Builder retaining raw values for fail-closed readiness assessment. */
    public static final class Builder {
      private final StandardEdition edition;
      private final String equipmentType;
      private double liquidFillingRateM3PerS = Double.NaN;
      private double fillingOutbreathingVolumeRatio = Double.NaN;
      private double liquidWithdrawalRateM3PerS = Double.NaN;
      private double withdrawalInbreathingVolumeRatio = Double.NaN;
      private double thermalOutbreathingRateM3PerS = Double.NaN;
      private double thermalInbreathingRateM3PerS = Double.NaN;
      private double otherNormalOutbreathingRateM3PerS = Double.NaN;
      private double otherNormalInbreathingRateM3PerS = Double.NaN;
      private double totalEmergencyOutbreathingRateM3PerS = Double.NaN;
      private double normalOutbreathingRatedCapacityM3PerS = Double.NaN;
      private double normalInbreathingRatedCapacityM3PerS = Double.NaN;
      private double emergencyOutbreathingRatedCapacityM3PerS = Double.NaN;
      private double tankMaximumPositiveGaugePressurePa = Double.NaN;
      private double tankMaximumVacuumPressurePa = Double.NaN;
      private double normalOutbreathingRatedGaugePressurePa = Double.NaN;
      private double normalInbreathingRatedVacuumPressurePa = Double.NaN;
      private double emergencyOutbreathingRatedGaugePressurePa = Double.NaN;
      private double flowReferenceTemperatureK = Double.NaN;
      private double flowReferencePressurePaAbsolute = Double.NaN;
      private boolean fixedRoofNonRefrigeratedApplicabilityVerified;
      private boolean ventDemandBasisVerified;
      private boolean ratedCapacityBasisVerified;
      private boolean pressureVacuumBasisVerified;
      private boolean normalCombinationBasisVerified;
      private boolean emergencyCombinationBasisVerified;

      private Builder(StandardEdition edition, String equipmentType) {
        if (edition == null || edition.getStandardType() != StandardType.API_2000) {
          throw new IllegalArgumentException("edition must identify API-2000");
        }
        if (equipmentType == null || equipmentType.trim().isEmpty()) {
          throw new IllegalArgumentException("equipmentType cannot be null or blank");
        }
        this.edition = edition;
        this.equipmentType = equipmentType.trim();
      }

      public Builder liquidFillingRateM3PerS(double value) {
        liquidFillingRateM3PerS = value;
        return this;
      }

      public Builder fillingOutbreathingVolumeRatio(double value) {
        fillingOutbreathingVolumeRatio = value;
        return this;
      }

      public Builder liquidWithdrawalRateM3PerS(double value) {
        liquidWithdrawalRateM3PerS = value;
        return this;
      }

      public Builder withdrawalInbreathingVolumeRatio(double value) {
        withdrawalInbreathingVolumeRatio = value;
        return this;
      }

      public Builder thermalOutbreathingRateM3PerS(double value) {
        thermalOutbreathingRateM3PerS = value;
        return this;
      }

      public Builder thermalInbreathingRateM3PerS(double value) {
        thermalInbreathingRateM3PerS = value;
        return this;
      }

      public Builder otherNormalOutbreathingRateM3PerS(double value) {
        otherNormalOutbreathingRateM3PerS = value;
        return this;
      }

      public Builder otherNormalInbreathingRateM3PerS(double value) {
        otherNormalInbreathingRateM3PerS = value;
        return this;
      }

      public Builder totalEmergencyOutbreathingRateM3PerS(double value) {
        totalEmergencyOutbreathingRateM3PerS = value;
        return this;
      }

      public Builder normalOutbreathingRatedCapacityM3PerS(double value) {
        normalOutbreathingRatedCapacityM3PerS = value;
        return this;
      }

      public Builder normalInbreathingRatedCapacityM3PerS(double value) {
        normalInbreathingRatedCapacityM3PerS = value;
        return this;
      }

      public Builder emergencyOutbreathingRatedCapacityM3PerS(double value) {
        emergencyOutbreathingRatedCapacityM3PerS = value;
        return this;
      }

      public Builder tankMaximumPositiveGaugePressurePa(double value) {
        tankMaximumPositiveGaugePressurePa = value;
        return this;
      }

      public Builder tankMaximumVacuumPressurePa(double value) {
        tankMaximumVacuumPressurePa = value;
        return this;
      }

      public Builder normalOutbreathingRatedGaugePressurePa(double value) {
        normalOutbreathingRatedGaugePressurePa = value;
        return this;
      }

      public Builder normalInbreathingRatedVacuumPressurePa(double value) {
        normalInbreathingRatedVacuumPressurePa = value;
        return this;
      }

      public Builder emergencyOutbreathingRatedGaugePressurePa(double value) {
        emergencyOutbreathingRatedGaugePressurePa = value;
        return this;
      }

      public Builder flowReferenceTemperatureK(double value) {
        flowReferenceTemperatureK = value;
        return this;
      }

      public Builder flowReferencePressurePaAbsolute(double value) {
        flowReferencePressurePaAbsolute = value;
        return this;
      }

      public Builder fixedRoofNonRefrigeratedApplicabilityVerified(boolean value) {
        fixedRoofNonRefrigeratedApplicabilityVerified = value;
        return this;
      }

      public Builder ventDemandBasisVerified(boolean value) {
        ventDemandBasisVerified = value;
        return this;
      }

      public Builder ratedCapacityBasisVerified(boolean value) {
        ratedCapacityBasisVerified = value;
        return this;
      }

      public Builder pressureVacuumBasisVerified(boolean value) {
        pressureVacuumBasisVerified = value;
        return this;
      }

      public Builder normalCombinationBasisVerified(boolean value) {
        normalCombinationBasisVerified = value;
        return this;
      }

      public Builder emergencyCombinationBasisVerified(boolean value) {
        emergencyCombinationBasisVerified = value;
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
    return StandardType.API_2000;
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
    return "api-2000-tank-vent-demand-capacity-screening";
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
      return readiness.addBlocker("API_2000_INPUT_MISSING", "API 2000 tank-venting input is required",
          "Provide movement, thermal, emergency, device-rating, tank-limit, and evidence inputs").build();
    }
    StandardApplicability applicability = applicability(input);
    if (!applicability.isApplicable()) {
      readiness.addBlocker("API_2000_NOT_APPLICABLE", applicability.getReason(),
          "Use a catalogued Tank or SimpleTankFiller equipment type");
    }
    if (!supports(input.getEdition())) {
      readiness.addBlocker("API_2000_EDITION_NOT_IMPLEMENTED",
          "The kernel implements " + IMPLEMENTED_EDITION + ", not " + input.getEdition().getDisplayName(),
          "Select the catalogued unamended edition or implement a controlled method version");
    }
    validateDemand(input, readiness);
    validateCapacityAndPressure(input, readiness);
    validateEvidence(input, readiness);
    readiness.addWarning("API_2000_CALLER_CONTROLLED_DEMAND",
        "Flow ratios, thermal demands, other demands, and total emergency demand are caller-controlled evidence",
        "Retain the licensed-source calculation, scenarios, product properties, climate, insulation, and approvals");
    readiness.addWarning("API_2000_RATED_CAPACITY_ONLY",
        "The kernel compares demand with externally rated capacities at stated pressures; it does not size a vent",
        "Verify device curves, gas basis, piping losses, backpressure, installation, flame arresters, and testing");
    return readiness.build();
  }

  private static void validateDemand(Input input, CalculationReadiness.Builder readiness) {
    if (!nonNegative(input.getLiquidFillingRateM3PerS()) || !nonNegative(input.getLiquidWithdrawalRateM3PerS())) {
      readiness.addBlocker("API_2000_LIQUID_RATE_INVALID", "Liquid movement rates must be finite and non-negative",
          "Supply maximum filling and withdrawal rates in m3/s");
    }
    if (!nonNegative(input.getFillingOutbreathingVolumeRatio())
        || !nonNegative(input.getWithdrawalInbreathingVolumeRatio())) {
      readiness.addBlocker("API_2000_VOLUME_RATIO_INVALID", "Movement flow ratios must be finite and non-negative",
          "Supply licensed-project reference gas volume per liquid volume factors");
    }
    if (!nonNegative(input.getThermalOutbreathingRateM3PerS()) || !nonNegative(input.getThermalInbreathingRateM3PerS())
        || !nonNegative(input.getOtherNormalOutbreathingRateM3PerS())
        || !nonNegative(input.getOtherNormalInbreathingRateM3PerS())) {
      readiness.addBlocker("API_2000_NORMAL_DEMAND_INVALID",
          "Thermal and other normal demands must be finite and non-negative",
          "Supply each demand on one explicit reference volumetric-flow basis");
    }
    if (!positive(input.getTotalEmergencyOutbreathingRateM3PerS())) {
      readiness.addBlocker("API_2000_EMERGENCY_DEMAND_INVALID",
          "Total emergency outbreathing demand must be finite and positive",
          "Supply the governing total emergency demand established by the controlled design basis");
    }
    if (rawNormalOutbreathingDemand(input) <= 0.0 || rawNormalInbreathingDemand(input) <= 0.0) {
      readiness.addBlocker("API_2000_NORMAL_TOTAL_INVALID",
          "Normal outbreathing and inbreathing totals must each be finite and positive",
          "Complete the movement, thermal, and other normal-demand cases in both directions");
    }
    if (!positive(input.getFlowReferenceTemperatureK()) || !positive(input.getFlowReferencePressurePaAbsolute())) {
      readiness.addBlocker("API_2000_FLOW_REFERENCE_INVALID",
          "Reference temperature and absolute pressure must be finite and positive",
          "State the common reference conditions used for every gas demand and rated capacity");
    }
  }

  private static void validateCapacityAndPressure(Input input, CalculationReadiness.Builder readiness) {
    if (!positive(input.getNormalOutbreathingRatedCapacityM3PerS())
        || !positive(input.getNormalInbreathingRatedCapacityM3PerS())
        || !positive(input.getEmergencyOutbreathingRatedCapacityM3PerS())) {
      readiness.addBlocker("API_2000_RATED_CAPACITY_INVALID",
          "Normal and emergency rated capacities must be finite and positive",
          "Supply device or system rated capacities on the common reference gas basis");
    }
    if (!positive(input.getTankMaximumPositiveGaugePressurePa()) || !positive(input.getTankMaximumVacuumPressurePa())) {
      readiness.addBlocker("API_2000_TANK_LIMIT_INVALID",
          "Tank positive-pressure and vacuum-magnitude limits must be finite and positive",
          "Supply the controlled tank pressure and vacuum design limits in Pa");
    }
    if (!nonNegative(input.getNormalOutbreathingRatedGaugePressurePa())
        || !nonNegative(input.getNormalInbreathingRatedVacuumPressurePa())
        || !nonNegative(input.getEmergencyOutbreathingRatedGaugePressurePa())) {
      readiness.addBlocker("API_2000_RATED_PRESSURE_INVALID",
          "Rated pressure and vacuum magnitudes must be finite and non-negative",
          "Supply the pressure/vacuum conditions corresponding to each rated capacity in Pa");
    }
  }

  private static void validateEvidence(Input input, CalculationReadiness.Builder readiness) {
    if (!input.isFixedRoofNonRefrigeratedApplicabilityVerified()) {
      readiness.addBlocker("API_2000_SCOPE_NOT_VERIFIED",
          "The non-refrigerated fixed-roof screening scope has not been verified",
          "Verify tank construction, stored liquid, pressure range, roof type, and exclusions");
    }
    if (!input.isVentDemandBasisVerified()) {
      readiness.addBlocker("API_2000_DEMAND_NOT_VERIFIED", "Vent-demand inputs have not been verified",
          "Verify liquid movement, thermal cases, other sources, gas basis, and governing scenarios");
    }
    if (!input.isRatedCapacityBasisVerified()) {
      readiness.addBlocker("API_2000_CAPACITY_NOT_VERIFIED", "Rated vent capacities have not been verified",
          "Verify manufacturer curves, gas properties, reference conditions, settings, and available devices");
    }
    if (!input.isPressureVacuumBasisVerified()) {
      readiness.addBlocker("API_2000_PRESSURE_BASIS_NOT_VERIFIED",
          "Tank limits and rated pressure/vacuum conditions have not been verified",
          "Verify gauge/absolute conventions, tank design limits, settings, tolerances, and pressure losses");
    }
    if (!input.isNormalCombinationBasisVerified()) {
      readiness.addBlocker("API_2000_NORMAL_COMBINATION_NOT_VERIFIED",
          "Normal simultaneous/additive demand combinations have not been verified",
          "Verify which movement, thermal, blanketing, and other normal cases must be combined");
    }
    if (!input.isEmergencyCombinationBasisVerified()) {
      readiness.addBlocker("API_2000_EMERGENCY_COMBINATION_NOT_VERIFIED",
          "Emergency demand and available-device combination have not been verified",
          "Verify the governing emergency scenario and which normal/emergency devices provide total capacity");
    }
  }

  /** {@inheritDoc} */
  @Override
  public EngineeringCalculationResult<Api2000TankVentingAssessment> calculate(Input input,
      EngineeringCalculationContext context) {
    EngineeringCalculationContext effectiveContext = context == null ? EngineeringCalculationContext.builder().build()
        : context;
    CalculationReadiness readiness = assess(input, effectiveContext);
    EngineeringCalculationResult.Builder<Api2000TankVentingAssessment> result = EngineeringCalculationResult
        .<Api2000TankVentingAssessment>builder("api-2000-tank-vent-demand-capacity-screening", getMethod(),
            getMethodVersion())
        .context(effectiveContext).readiness(readiness);
    if (input == null || !readiness.isReady()) {
      return result.status(EngineeringCalculationResult.Status.BLOCKED)
          .message("API 2000 tank-venting screening is blocked until readiness findings are resolved").build();
    }
    Api2000TankVentingAssessment assessment = new Api2000TankVentingAssessment(input);
    if (!numericallyValid(assessment)) {
      CalculationReadiness numericalReadiness = CalculationReadiness.builder().merge(readiness)
          .addBlocker("API_2000_NUMERICAL_RESULT_INVALID",
              "Demand aggregation or utilization produced a non-finite result",
              "Review rates, ratios, capacity magnitudes, reference conditions, and units")
          .build();
      return result.readiness(numericalReadiness).status(EngineeringCalculationResult.Status.BLOCKED)
          .message("API 2000 tank-venting screening is blocked by an invalid numerical result").build();
    }
    return result.status(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED).value(assessment)
        .input("standard", input.getEdition().getDisplayName()).input("equipmentType", input.getEquipmentType())
        .input("demandBasis", demandMap(input)).input("ratedCapacityAndPressureBasis", capacityMap(input))
        .warning("Constraint status is caller-controlled and is not an API 2000 compliance or device certification")
        .warning("Vent sizing equations/tables, external floating roofs, refrigerated storage, flame arresters, "
            + "blanketing control, piping losses, dispersion, emissions, installation, and testing are not calculated")
        .message("API 2000 normal and emergency tank-vent demand/capacity screen completed; review remains required")
        .build();
  }

  private static Map<String, Object> demandMap(Input input) {
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    values.put("liquidFillingRateM3PerS", Double.valueOf(input.getLiquidFillingRateM3PerS()));
    values.put("fillingOutbreathingVolumeRatio", Double.valueOf(input.getFillingOutbreathingVolumeRatio()));
    values.put("liquidWithdrawalRateM3PerS", Double.valueOf(input.getLiquidWithdrawalRateM3PerS()));
    values.put("withdrawalInbreathingVolumeRatio", Double.valueOf(input.getWithdrawalInbreathingVolumeRatio()));
    values.put("thermalOutbreathingRateM3PerS", Double.valueOf(input.getThermalOutbreathingRateM3PerS()));
    values.put("thermalInbreathingRateM3PerS", Double.valueOf(input.getThermalInbreathingRateM3PerS()));
    values.put("otherNormalOutbreathingRateM3PerS", Double.valueOf(input.getOtherNormalOutbreathingRateM3PerS()));
    values.put("otherNormalInbreathingRateM3PerS", Double.valueOf(input.getOtherNormalInbreathingRateM3PerS()));
    values.put("totalEmergencyOutbreathingRateM3PerS", Double.valueOf(input.getTotalEmergencyOutbreathingRateM3PerS()));
    values.put("flowReferenceTemperatureK", Double.valueOf(input.getFlowReferenceTemperatureK()));
    values.put("flowReferencePressurePaAbsolute", Double.valueOf(input.getFlowReferencePressurePaAbsolute()));
    values.put("ventDemandBasisVerified", Boolean.valueOf(input.isVentDemandBasisVerified()));
    values.put("normalCombinationBasisVerified", Boolean.valueOf(input.isNormalCombinationBasisVerified()));
    values.put("emergencyCombinationBasisVerified", Boolean.valueOf(input.isEmergencyCombinationBasisVerified()));
    return values;
  }

  private static Map<String, Object> capacityMap(Input input) {
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    values.put("normalOutbreathingRatedCapacityM3PerS",
        Double.valueOf(input.getNormalOutbreathingRatedCapacityM3PerS()));
    values.put("normalInbreathingRatedCapacityM3PerS", Double.valueOf(input.getNormalInbreathingRatedCapacityM3PerS()));
    values.put("emergencyOutbreathingRatedCapacityM3PerS",
        Double.valueOf(input.getEmergencyOutbreathingRatedCapacityM3PerS()));
    values.put("tankMaximumPositiveGaugePressurePa", Double.valueOf(input.getTankMaximumPositiveGaugePressurePa()));
    values.put("tankMaximumVacuumPressurePa", Double.valueOf(input.getTankMaximumVacuumPressurePa()));
    values.put("normalOutbreathingRatedGaugePressurePa",
        Double.valueOf(input.getNormalOutbreathingRatedGaugePressurePa()));
    values.put("normalInbreathingRatedVacuumPressurePa",
        Double.valueOf(input.getNormalInbreathingRatedVacuumPressurePa()));
    values.put("emergencyOutbreathingRatedGaugePressurePa",
        Double.valueOf(input.getEmergencyOutbreathingRatedGaugePressurePa()));
    values.put("fixedRoofNonRefrigeratedApplicabilityVerified",
        Boolean.valueOf(input.isFixedRoofNonRefrigeratedApplicabilityVerified()));
    values.put("ratedCapacityBasisVerified", Boolean.valueOf(input.isRatedCapacityBasisVerified()));
    values.put("pressureVacuumBasisVerified", Boolean.valueOf(input.isPressureVacuumBasisVerified()));
    return values;
  }

  private static double rawNormalOutbreathingDemand(Input input) {
    return input.getLiquidFillingRateM3PerS() * input.getFillingOutbreathingVolumeRatio()
        + input.getThermalOutbreathingRateM3PerS() + input.getOtherNormalOutbreathingRateM3PerS();
  }

  private static double rawNormalInbreathingDemand(Input input) {
    return input.getLiquidWithdrawalRateM3PerS() * input.getWithdrawalInbreathingVolumeRatio()
        + input.getThermalInbreathingRateM3PerS() + input.getOtherNormalInbreathingRateM3PerS();
  }

  private static boolean positive(double value) {
    return Double.isFinite(value) && value > 0.0;
  }

  private static boolean nonNegative(double value) {
    return Double.isFinite(value) && value >= 0.0;
  }

  private static boolean numericallyValid(Api2000TankVentingAssessment assessment) {
    return positive(assessment.getRequiredNormalOutbreathingRateM3PerS())
        && positive(assessment.getRequiredNormalInbreathingRateM3PerS())
        && positive(assessment.getRequiredEmergencyOutbreathingRateM3PerS())
        && nonNegative(assessment.getNormalOutbreathingUtilization())
        && nonNegative(assessment.getNormalInbreathingUtilization())
        && nonNegative(assessment.getEmergencyOutbreathingUtilization())
        && Double.isFinite(assessment.getNormalOutbreathingCapacityMarginM3PerS())
        && Double.isFinite(assessment.getNormalInbreathingCapacityMarginM3PerS())
        && Double.isFinite(assessment.getEmergencyOutbreathingCapacityMarginM3PerS())
        && Double.isFinite(assessment.getNormalPositivePressureMarginPa())
        && Double.isFinite(assessment.getVacuumPressureMarginPa())
        && Double.isFinite(assessment.getEmergencyPositivePressureMarginPa());
  }
}
