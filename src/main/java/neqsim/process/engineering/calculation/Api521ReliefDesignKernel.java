package neqsim.process.engineering.calculation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import neqsim.process.mechanicaldesign.designstandards.StandardApplicability;
import neqsim.process.mechanicaldesign.designstandards.StandardEdition;
import neqsim.process.mechanicaldesign.designstandards.StandardSupportLevel;
import neqsim.process.mechanicaldesign.designstandards.StandardType;
import neqsim.process.safety.overpressure.OverpressureProtectionStudy;
import neqsim.process.safety.overpressure.OverpressureStudyResult;
import neqsim.process.safety.overpressure.ProtectedItem;
import neqsim.process.safety.overpressure.ReliefPhase;
import neqsim.process.safety.overpressure.ReliefScenario;

/** Pure engineering-workflow adapter for API 521 relief-scenario screening. */
public final class Api521ReliefDesignKernel
    implements EquipmentDesignKernel<Api521ReliefDesignKernel.Input, Api521ReliefAssessment> {
  private static final long serialVersionUID = 1000L;
  private static final String IMPLEMENTED_EDITION = "7th Ed";

  /** Immutable, defensively copied relief-study input. */
  public static final class Input implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final StandardEdition edition;
    private final String equipmentType;
    private final ProtectedItem protectedItem;
    private final List<ReliefScenario> scenarios;
    private final boolean multipleDevices;

    /**
     * Create an API 521 relief-study input.
     *
     * @param edition explicit API 521 edition
     * @param equipmentType protected equipment or relief-system type
     * @param protectedItem protected-item pressure basis
     * @param scenarios documented relief scenarios
     * @param multipleDevices whether multiple devices protect the item
     */
    public Input(StandardEdition edition, String equipmentType, ProtectedItem protectedItem,
        List<ReliefScenario> scenarios, boolean multipleDevices) {
      if (edition == null || edition.getStandardType() != StandardType.API_521) {
        throw new IllegalArgumentException("edition must identify API-521");
      }
      if (equipmentType == null || equipmentType.trim().isEmpty()) {
        throw new IllegalArgumentException("equipmentType cannot be null or blank");
      }
      if (protectedItem == null) {
        throw new IllegalArgumentException("protectedItem cannot be null");
      }
      this.edition = edition;
      this.equipmentType = equipmentType.trim();
      this.protectedItem = copy(protectedItem);
      this.scenarios = Collections.unmodifiableList(
          scenarios == null ? new ArrayList<ReliefScenario>() : new ArrayList<ReliefScenario>(scenarios));
      this.multipleDevices = multipleDevices;
    }

    /** @return explicit API 521 edition */
    public StandardEdition getEdition() {
      return edition;
    }

    /** @return simple protected-equipment or relief-system type */
    public String getEquipmentType() {
      return equipmentType;
    }

    /** @return independent copy of the protected-item pressure basis */
    public ProtectedItem getProtectedItem() {
      return copy(protectedItem);
    }

    /** @return immutable list of immutable relief scenarios */
    public List<ReliefScenario> getScenarios() {
      return scenarios;
    }

    /** @return whether multiple devices protect the item */
    public boolean hasMultipleDevices() {
      return multipleDevices;
    }

    private static ProtectedItem copy(ProtectedItem item) {
      return new ProtectedItem(item.getName(), item.getMaximumAllowableWorkingPressureBara())
          .setReliefSetPressureBara(item.getReliefSetPressureBara()).setDesignTemperatureC(item.getDesignTemperatureC())
          .setBackPressureBara(item.getBackPressureBara());
    }
  }

  /** {@inheritDoc} */
  @Override
  public StandardType standard() {
    return StandardType.API_521;
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
    return "api-521-relief-scenario-screening";
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
      return readiness.addBlocker("API521_INPUT_MISSING", "API 521 relief-study input is required",
          "Provide an edition, protected item, equipment type, and documented scenarios").build();
    }
    StandardApplicability decision = applicability(input);
    if (!decision.isApplicable()) {
      readiness.addBlocker("API521_NOT_APPLICABLE", decision.getReason(),
          "Use a catalogued relief-system or protected-item equipment type");
    }
    if (!supports(input.getEdition())) {
      readiness.addBlocker("API521_EDITION_NOT_IMPLEMENTED",
          "The API 521 kernel implements " + IMPLEMENTED_EDITION + ", not " + input.getEdition().getEdition(),
          "Select the implemented edition or add separately validated edition logic");
    }
    assessProtectedItem(input.protectedItem, readiness);
    assessScenarios(input.scenarios, readiness);
    readiness.addWarning("API521_SCREENING_ONLY",
        "The kernel evaluates supplied relief scenarios; it does not establish that the scenario set is complete",
        "Perform an independent hazard review and verify the purchased edition and project requirements");
    return readiness.build();
  }

  /** {@inheritDoc} */
  @Override
  public EngineeringCalculationResult<Api521ReliefAssessment> calculate(Input input,
      EngineeringCalculationContext context) {
    EngineeringCalculationContext effectiveContext = context == null ? EngineeringCalculationContext.builder().build()
        : context;
    CalculationReadiness readiness = assess(input, effectiveContext);
    EngineeringCalculationResult.Builder<Api521ReliefAssessment> result = EngineeringCalculationResult
        .<Api521ReliefAssessment>builder("api-521-relief-scenario-screening", getMethod(), getMethodVersion())
        .context(effectiveContext).readiness(readiness);
    if (input == null || !readiness.isReady()) {
      return result.status(EngineeringCalculationResult.Status.BLOCKED)
          .message("API 521 screening is blocked until the readiness findings are resolved").build();
    }

    OverpressureProtectionStudy study = new OverpressureProtectionStudy(input.getProtectedItem())
        .setMultipleDevices(input.hasMultipleDevices());
    for (ReliefScenario scenario : input.getScenarios()) {
      study.addScenario(scenario);
    }
    try {
      OverpressureStudyResult studyResult = study.evaluate();
      Api521ReliefAssessment assessment = Api521ReliefAssessment.from(input.getEdition().getDisplayName(), studyResult);
      result.status(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED).value(assessment)
          .input("standard", input.getEdition().getDisplayName()).input("equipmentType", input.getEquipmentType())
          .input("protectedItem", input.protectedItem.getName())
          .input("scenarioCount", Integer.valueOf(input.scenarios.size()))
          .input("multipleDevices", Boolean.valueOf(input.hasMultipleDevices()))
          .warning("API 521 screening does not certify scenario completeness, device selection, or installation");
      if (!assessment.isCapacityAdequate()) {
        result.warning("The selected single standard orifice is not adequate for the governing scenario");
      }
      if (!assessment.isAccumulatedPressureAccepted()) {
        result.warning("The accumulated-pressure screening check is not accepted");
      }
      return result.message("API 521 relief-scenario screening completed; independent safety review remains required")
          .build();
    } catch (RuntimeException exception) {
      return result.status(EngineeringCalculationResult.Status.FAILED)
          .message("API 521 screening failed: " + exception.getMessage()).build();
    }
  }

  private static void assessProtectedItem(ProtectedItem item, CalculationReadiness.Builder readiness) {
    if (item.getName() == null || item.getName().trim().isEmpty()) {
      readiness.addBlocker("API521_ITEM_NAME_MISSING", "Protected item name is required",
          "Supply a traceable equipment tag");
    }
    double mawp = item.getMaximumAllowableWorkingPressureBara();
    double setPressure = item.getReliefSetPressureBara();
    double backPressure = item.getBackPressureBara();
    if (!Double.isFinite(mawp) || mawp <= 0.0) {
      readiness.addBlocker("API521_MAWP_INVALID", "MAWP must be finite and positive",
          "Supply the verified maximum allowable working pressure in bara");
    }
    if (!Double.isFinite(setPressure) || setPressure <= 0.0 || setPressure > mawp) {
      readiness.addBlocker("API521_SET_PRESSURE_INVALID", "Set pressure must be positive and no greater than MAWP",
          "Correct the pressure-relief-device set pressure basis");
    }
    if (!Double.isFinite(backPressure) || backPressure < 0.0) {
      readiness.addBlocker("API521_BACK_PRESSURE_INVALID", "Back pressure must be finite and non-negative",
          "Supply the relief-system back pressure in bara");
    }
  }

  private static void assessScenarios(List<ReliefScenario> scenarios, CalculationReadiness.Builder readiness) {
    if (scenarios.isEmpty()) {
      readiness.addBlocker("API521_SCENARIOS_MISSING", "At least one documented relief scenario is required",
          "Add credible and excluded scenarios with causes, rates, phases, and assumptions");
      return;
    }
    int crediblePositive = 0;
    for (int index = 0; index < scenarios.size(); index++) {
      ReliefScenario scenario = scenarios.get(index);
      String prefix = "Scenario " + (index + 1);
      if (scenario == null) {
        readiness.addBlocker("API521_SCENARIO_NULL", prefix + " is null", "Remove or define the scenario");
        continue;
      }
      if (scenario.getName() == null || scenario.getName().trim().isEmpty() || scenario.getCause() == null
          || scenario.getPhase() == null) {
        readiness.addBlocker("API521_SCENARIO_IDENTITY_INVALID", prefix + " needs a name, cause, and phase",
            "Complete the scenario identification");
      }
      double rate = scenario.getReliefRateKgPerS();
      if (!Double.isFinite(rate) || rate < 0.0) {
        readiness.addBlocker("API521_SCENARIO_RATE_INVALID", prefix + " relief rate must be finite and non-negative",
            "Supply a traceable required relief rate in kg/s");
      } else if (scenario.isCredible() && rate > 0.0) {
        crediblePositive++;
        assessPhaseInputs(prefix, scenario, readiness);
      }
    }
    if (crediblePositive == 0) {
      readiness.addBlocker("API521_CREDIBLE_SCENARIO_MISSING",
          "At least one credible scenario with a positive relief rate is required",
          "Review credibility and relief-rate calculations");
    }
  }

  private static void assessPhaseInputs(String prefix, ReliefScenario scenario,
      CalculationReadiness.Builder readiness) {
    if (scenario.getPhase() == ReliefPhase.VAPOUR) {
      if (!positive(scenario.getMolarMassKgPerMol()) || !positive(scenario.getCompressibility())
          || !Double.isFinite(scenario.getSpecificHeatRatio()) || scenario.getSpecificHeatRatio() <= 1.0) {
        readiness.addBlocker("API521_VAPOUR_PROPERTIES_INVALID",
            prefix + " requires positive molar mass and compressibility and a heat-capacity ratio above one",
            "Supply verified relieving-fluid vapour properties");
      }
      if (Double.isNaN(scenario.getReliefTemperatureK())) {
        readiness.addWarning("API521_TEMPERATURE_DEFAULTED", prefix + " will use the legacy 288.15 K default",
            "Supply a relieving temperature for project work");
      } else if (!positive(scenario.getReliefTemperatureK())) {
        readiness.addBlocker("API521_TEMPERATURE_INVALID", prefix + " relieving temperature must be positive",
            "Supply the relieving temperature in kelvin");
      }
    } else if (scenario.getPhase() == ReliefPhase.LIQUID) {
      double density = positive(scenario.getDensityKgPerM3()) ? scenario.getDensityKgPerM3()
          : scenario.getLiquidDensityKgPerM3();
      if (!positive(density)) {
        readiness.addWarning("API521_LIQUID_DENSITY_DEFAULTED", prefix + " will use the legacy 700 kg/m3 default",
            "Supply relieving-liquid density for project work");
      }
      if (!positive(scenario.getViscosityPaS())) {
        readiness.addWarning("API521_LIQUID_VISCOSITY_DEFAULTED", prefix + " will use the legacy 0.5 mPa s default",
            "Supply relieving-liquid viscosity for project work");
      }
    } else if (!fraction(scenario.getGasMassFraction()) || !positive(scenario.getGasDensityKgPerM3())
        || !positive(scenario.getLiquidDensityKgPerM3()) || !positive(scenario.getLatentHeatJPerKg())
        || !positive(scenario.getLiquidHeatCapacityJPerKgK())) {
      readiness.addBlocker("API521_TWO_PHASE_PROPERTIES_INVALID",
          prefix + " requires gas fraction, gas/liquid densities, latent heat, and liquid heat capacity",
          "Supply the complete two-phase omega-method input set");
    }
  }

  private static boolean positive(double value) {
    return Double.isFinite(value) && value > 0.0;
  }

  private static boolean fraction(double value) {
    return Double.isFinite(value) && value >= 0.0 && value <= 1.0;
  }
}
