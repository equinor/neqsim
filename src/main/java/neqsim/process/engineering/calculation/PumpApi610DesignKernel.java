package neqsim.process.engineering.calculation;

import java.io.Serializable;
import neqsim.process.mechanicaldesign.designstandards.StandardApplicability;
import neqsim.process.mechanicaldesign.designstandards.StandardEdition;
import neqsim.process.mechanicaldesign.designstandards.StandardSupportLevel;
import neqsim.process.mechanicaldesign.designstandards.StandardType;
import neqsim.process.mechanicaldesign.pump.PumpApi610DesignCalculator;

/** Pure engineering-workflow adapter for the legacy API 610 screening calculator. */
public final class PumpApi610DesignKernel
    implements EquipmentDesignKernel<PumpApi610DesignKernel.Input, PumpApi610DesignAssessment> {
  private static final long serialVersionUID = 1000L;
  private static final String IMPLEMENTED_EDITION = "13th Ed";

  /** Immutable, defensively copied kernel input. */
  public static final class Input implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final StandardEdition edition;
    private final String equipmentType;
    private final PumpApi610DesignCalculator configuration;

    /**
     * Create a kernel input from a legacy calculator configuration.
     *
     * @param edition explicit API 610 edition
     * @param equipmentType simple equipment class name
     * @param configuration configured, unexecuted legacy calculator
     */
    public Input(StandardEdition edition, String equipmentType, PumpApi610DesignCalculator configuration) {
      if (edition == null || edition.getStandardType() != StandardType.API_610) {
        throw new IllegalArgumentException("edition must identify API-610");
      }
      if (equipmentType == null || equipmentType.trim().isEmpty()) {
        throw new IllegalArgumentException("equipmentType cannot be null or blank");
      }
      if (configuration == null) {
        throw new IllegalArgumentException("configuration cannot be null");
      }
      this.edition = edition;
      this.equipmentType = equipmentType.trim();
      this.configuration = configuration.copyConfiguration();
    }

    /** @return explicit API 610 edition */
    public StandardEdition getEdition() {
      return edition;
    }

    /** @return simple equipment class name */
    public String getEquipmentType() {
      return equipmentType;
    }

    /** @return independent copy of the legacy calculator configuration */
    public PumpApi610DesignCalculator getConfiguration() {
      return configuration.copyConfiguration();
    }
  }

  /** {@inheritDoc} */
  @Override
  public StandardType standard() {
    return StandardType.API_610;
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
        && IMPLEMENTED_EDITION.equalsIgnoreCase(edition.getEdition());
  }

  /** {@inheritDoc} */
  @Override
  public StandardApplicability applicability(Input input) {
    return StandardApplicability.assess(standard(), input == null ? null : input.getEquipmentType());
  }

  /** {@inheritDoc} */
  @Override
  public String getMethod() {
    return "api-610-pump-screening";
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
      return readiness.addBlocker("API610_INPUT_MISSING", "API 610 kernel input is required",
          "Provide an explicit edition, equipment type, and calculator configuration").build();
    }

    StandardApplicability decision = applicability(input);
    if (!decision.isApplicable()) {
      readiness.addBlocker("API610_NOT_APPLICABLE", decision.getReason(),
          "Use API 610 only for a Pump equipment type");
    }
    if (!supports(input.getEdition())) {
      readiness.addBlocker("API610_EDITION_NOT_IMPLEMENTED",
          "The API 610 kernel implements " + IMPLEMENTED_EDITION + ", not " + input.getEdition().getEdition(),
          "Select the implemented edition or add separately validated edition logic");
    }
    readiness.addWarning("API610_SCREENING_ONLY",
        "The API 610 kernel provides preliminary screening, not conformity assessment",
        "Verify the purchased edition, project requirements, and vendor documentation");
    return readiness.build();
  }

  /** {@inheritDoc} */
  @Override
  public EngineeringCalculationResult<PumpApi610DesignAssessment> calculate(Input input,
      EngineeringCalculationContext context) {
    EngineeringCalculationContext effectiveContext =
        context == null ? EngineeringCalculationContext.builder().build() : context;
    CalculationReadiness readiness = assess(input, effectiveContext);
    EngineeringCalculationResult.Builder<PumpApi610DesignAssessment> result =
        EngineeringCalculationResult.<PumpApi610DesignAssessment>builder("api-610-pump-screening", getMethod(),
            getMethodVersion())
            .context(effectiveContext).readiness(readiness);

    if (input == null || !readiness.isReady()) {
      return result.status(EngineeringCalculationResult.Status.BLOCKED)
          .message("API 610 screening is blocked until the readiness findings are resolved").build();
    }

    PumpApi610DesignCalculator calculator = input.getConfiguration();
    calculator.setStandardEdition(input.getEdition().getEdition());
    calculator.calculate();
    PumpApi610DesignAssessment assessment = PumpApi610DesignAssessment.from(calculator);
    return result.status(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED).value(assessment)
        .input("standard", input.getEdition().getDisplayName()).input("equipmentType", input.getEquipmentType())
        .warning("API 610 screening does not certify code or vendor compliance")
        .message("API 610 screening completed; engineering and vendor review remain required").build();
  }
}
