package neqsim.process.engineering.calculation;

import java.io.Serializable;
import neqsim.process.equipment.separator.entrainment.DropletSettlingCalculator;
import neqsim.process.mechanicaldesign.designstandards.StandardApplicability;
import neqsim.process.mechanicaldesign.designstandards.StandardEdition;
import neqsim.process.mechanicaldesign.designstandards.StandardSupportLevel;
import neqsim.process.mechanicaldesign.designstandards.StandardType;

/** Pure API 12J gas-section and liquid-residence-time screening adapter. */
public final class Api12JSeparatorDesignKernel
    implements EquipmentDesignKernel<Api12JSeparatorDesignKernel.Input, Api12JSeparatorAssessment> {
  private static final long serialVersionUID = 1000L;
  private static final String IMPLEMENTED_EDITION = "8th Ed";

  /** Unit used to supply the gravity cut diameter. */
  public enum DiameterUnit {
    /** Metres. */
    METRE,
    /** Micrometres. */
    MICROMETRE
  }

  /** Separator orientation used by the implemented K-factor screen. */
  public enum Orientation {
    /** Horizontal vessel. */
    HORIZONTAL,
    /** Vertical vessel. */
    VERTICAL
  }

  /** Immutable, unit-aware screening input. */
  public static final class Input implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final StandardEdition edition;
    private final String equipmentType;
    private final double gravityCutDiameterM;
    private final double kFactorMPerS;
    private final boolean mistEliminatorPresent;
    private final double liquidResidenceTimeS;
    private final Orientation orientation;
    private final boolean threePhase;

    /**
     * Create an API 12J screening input.
     *
     * @param edition explicit API 12J edition
     * @param equipmentType simple separator equipment class name
     * @param gravityCutDiameter gravity-section cut diameter
     * @param diameterUnit unit of {@code gravityCutDiameter}
     * @param kFactorMPerS operating Souders-Brown K-factor in m/s
     * @param mistEliminatorPresent whether a mist eliminator is installed
     * @param liquidResidenceTimeS liquid residence time in seconds
     * @param orientation separator orientation
     * @param threePhase whether the separator handles gas, oil, and water
     */
    public Input(StandardEdition edition, String equipmentType, double gravityCutDiameter, DiameterUnit diameterUnit,
        double kFactorMPerS, boolean mistEliminatorPresent, double liquidResidenceTimeS, Orientation orientation,
        boolean threePhase) {
      if (edition == null || edition.getStandardType() != StandardType.API_12J) {
        throw new IllegalArgumentException("edition must identify API-12J");
      }
      if (equipmentType == null || equipmentType.trim().isEmpty()) {
        throw new IllegalArgumentException("equipmentType cannot be null or blank");
      }
      if (diameterUnit == null || orientation == null) {
        throw new IllegalArgumentException("diameterUnit and orientation cannot be null");
      }
      this.edition = edition;
      this.equipmentType = equipmentType.trim();
      this.gravityCutDiameterM = diameterUnit == DiameterUnit.METRE ? gravityCutDiameter
          : gravityCutDiameter * 1.0e-6;
      this.kFactorMPerS = kFactorMPerS;
      this.mistEliminatorPresent = mistEliminatorPresent;
      this.liquidResidenceTimeS = liquidResidenceTimeS;
      this.orientation = orientation;
      this.threePhase = threePhase;
    }

    /** @return explicit edition */
    public StandardEdition getEdition() {
      return edition;
    }

    /** @return simple separator equipment class name */
    public String getEquipmentType() {
      return equipmentType;
    }

    /** @return gravity cut diameter in metres */
    public double getGravityCutDiameterM() {
      return gravityCutDiameterM;
    }

    /** @return operating K-factor in m/s */
    public double getKFactorMPerS() {
      return kFactorMPerS;
    }

    /** @return whether a mist eliminator is present */
    public boolean hasMistEliminator() {
      return mistEliminatorPresent;
    }

    /** @return liquid residence time in seconds */
    public double getLiquidResidenceTimeS() {
      return liquidResidenceTimeS;
    }

    /** @return separator orientation */
    public Orientation getOrientation() {
      return orientation;
    }

    /** @return whether the service is three-phase */
    public boolean isThreePhase() {
      return threePhase;
    }
  }

  /** {@inheritDoc} */
  @Override
  public StandardType standard() {
    return StandardType.API_12J;
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
    return "api-12j-separator-performance-screening";
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
      return readiness.addBlocker("API12J_INPUT_MISSING", "API 12J screening input is required",
          "Provide an edition, equipment type, cut diameter, K-factor, and residence time").build();
    }
    StandardApplicability decision = applicability(input);
    if (!decision.isApplicable()) {
      readiness.addBlocker("API12J_NOT_APPLICABLE", decision.getReason(),
          "Use a Separator, ThreePhaseSeparator, or GasScrubber equipment type");
    }
    if (!supports(input.getEdition())) {
      readiness.addBlocker("API12J_EDITION_NOT_IMPLEMENTED",
          "The API 12J kernel implements " + IMPLEMENTED_EDITION + ", not " + input.getEdition().getEdition(),
          "Select the implemented edition or add separately validated edition criteria");
    }
    if (!positive(input.getGravityCutDiameterM())) {
      readiness.addBlocker("API12J_CUT_DIAMETER_INVALID", "Gravity cut diameter must be finite and positive",
          "Supply a calculated cut diameter with an explicit unit");
    }
    if (!Double.isFinite(input.getKFactorMPerS()) || input.getKFactorMPerS() < 0.0) {
      readiness.addBlocker("API12J_K_FACTOR_INVALID", "K-factor must be finite and non-negative",
          "Supply the operating Souders-Brown K-factor in m/s");
    }
    if (!positive(input.getLiquidResidenceTimeS())) {
      readiness.addBlocker("API12J_RESIDENCE_TIME_INVALID", "Liquid residence time must be finite and positive",
          "Supply the governing-case liquid residence time in seconds");
    }
    readiness.addWarning("API12J_SCREENING_ONLY",
        "The implemented checks cover cut diameter, K-factor, and liquid residence time only",
        "Verify the purchased edition, service applicability, vessel construction, internals, and project criteria");
    return readiness.build();
  }

  /** {@inheritDoc} */
  @Override
  public EngineeringCalculationResult<Api12JSeparatorAssessment> calculate(Input input,
      EngineeringCalculationContext context) {
    EngineeringCalculationContext effectiveContext = context == null ? EngineeringCalculationContext.builder().build()
        : context;
    CalculationReadiness readiness = assess(input, effectiveContext);
    EngineeringCalculationResult.Builder<Api12JSeparatorAssessment> result = EngineeringCalculationResult
        .<Api12JSeparatorAssessment>builder("api-12j-separator-performance-screening", getMethod(),
            getMethodVersion())
        .context(effectiveContext).readiness(readiness);
    if (input == null || !readiness.isReady()) {
      return result.status(EngineeringCalculationResult.Status.BLOCKED)
          .message("API 12J screening is blocked until the readiness findings are resolved").build();
    }

    DropletSettlingCalculator.ApiComplianceResult legacy = DropletSettlingCalculator.checkApi12JCompliance(
        input.getGravityCutDiameterM(), input.getKFactorMPerS(), input.hasMistEliminator(),
        input.getLiquidResidenceTimeS(), input.getOrientation().name().toLowerCase(), input.isThreePhase());
    Api12JSeparatorAssessment assessment = new Api12JSeparatorAssessment(input.getEdition().getDisplayName(), legacy);
    result.status(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED).value(assessment)
        .input("standard", input.getEdition().getDisplayName()).input("equipmentType", input.getEquipmentType())
        .input("gravityCutDiameterM", Double.valueOf(input.getGravityCutDiameterM()))
        .input("kFactorMPerS", Double.valueOf(input.getKFactorMPerS()))
        .input("liquidResidenceTimeS", Double.valueOf(input.getLiquidResidenceTimeS()))
        .warning("API 12J screening does not certify separator design or performance");
    if (!assessment.areAllScreeningCriteriaPassing()) {
      result.warning("One or more implemented API 12J screening criteria do not pass")
          .message("API 12J screening completed with findings requiring design revision and engineering review");
    } else {
      result.message("API 12J screening criteria pass; independent design review remains required");
    }
    return result.build();
  }

  private static boolean positive(double value) {
    return Double.isFinite(value) && value > 0.0;
  }
}
