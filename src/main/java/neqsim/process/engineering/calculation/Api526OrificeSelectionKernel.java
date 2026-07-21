package neqsim.process.engineering.calculation;

import java.io.Serializable;
import neqsim.process.mechanicaldesign.designstandards.StandardApplicability;
import neqsim.process.mechanicaldesign.designstandards.StandardEdition;
import neqsim.process.mechanicaldesign.designstandards.StandardSupportLevel;
import neqsim.process.mechanicaldesign.designstandards.StandardType;

/** Pure API 526 standard-orifice screening selection. */
public final class Api526OrificeSelectionKernel
    implements EquipmentDesignKernel<Api526OrificeSelectionKernel.Input, Api526OrificeSelectionAssessment> {
  private static final long serialVersionUID = 1000L;
  private static final String IMPLEMENTED_EDITION = "7th Ed";
  private static final double SQUARE_METRES_PER_SQUARE_INCH = 6.4516e-4;
  private static final double[] ORIFICE_AREAS_IN2 = { 0.110, 0.196, 0.307, 0.503, 0.785, 1.287, 1.838, 2.853, 3.600,
      4.340, 6.380, 11.05, 16.0, 26.0 };
  private static final String[] ORIFICE_LETTERS = { "D", "E", "F", "G", "H", "J", "K", "L", "M", "N", "P", "Q", "R",
      "T" };

  /** Unit used to supply a required effective flow area. */
  public enum AreaUnit {
    /** Square metres. */
    SQUARE_METRE,
    /** Square inches. */
    SQUARE_INCH
  }

  /** Immutable kernel input with a canonical square-metre value. */
  public static final class Input implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final StandardEdition edition;
    private final String equipmentType;
    private final double requiredAreaM2;

    /**
     * Create an orifice-selection input.
     *
     * @param edition explicit API 526 edition
     * @param equipmentType simple equipment class name
     * @param requiredArea required effective flow area
     * @param unit unit of {@code requiredArea}
     */
    public Input(StandardEdition edition, String equipmentType, double requiredArea, AreaUnit unit) {
      if (edition == null || edition.getStandardType() != StandardType.API_526) {
        throw new IllegalArgumentException("edition must identify API-526");
      }
      if (equipmentType == null || equipmentType.trim().isEmpty()) {
        throw new IllegalArgumentException("equipmentType cannot be null or blank");
      }
      if (unit == null) {
        throw new IllegalArgumentException("area unit cannot be null");
      }
      this.edition = edition;
      this.equipmentType = equipmentType.trim();
      this.requiredAreaM2 = unit == AreaUnit.SQUARE_METRE ? requiredArea : requiredArea * SQUARE_METRES_PER_SQUARE_INCH;
    }

    /** @return explicit API 526 edition */
    public StandardEdition getEdition() {
      return edition;
    }

    /** @return simple equipment class name */
    public String getEquipmentType() {
      return equipmentType;
    }

    /** @return required effective area in square metres */
    public double getRequiredAreaM2() {
      return requiredAreaM2;
    }

    /** @return required effective area in square inches */
    public double getRequiredAreaIn2() {
      return requiredAreaM2 / SQUARE_METRES_PER_SQUARE_INCH;
    }
  }

  /** {@inheritDoc} */
  @Override
  public StandardType standard() {
    return StandardType.API_526;
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
    return "api-526-orifice-selection";
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
      return readiness.addBlocker("API526_INPUT_MISSING", "API 526 orifice-selection input is required",
          "Provide an explicit edition, relief-valve equipment type, area, and unit").build();
    }
    StandardApplicability decision = applicability(input);
    if (!decision.isApplicable()) {
      readiness.addBlocker("API526_NOT_APPLICABLE", decision.getReason(),
          "Use API 526 only for SafetyValve or SafetyReliefValve equipment");
    }
    if (!supports(input.getEdition())) {
      readiness.addBlocker("API526_EDITION_NOT_IMPLEMENTED",
          "The API 526 kernel implements " + IMPLEMENTED_EDITION + ", not " + input.getEdition().getEdition(),
          "Select the implemented edition or add separately validated edition data");
    }
    if (!Double.isFinite(input.getRequiredAreaM2()) || input.getRequiredAreaM2() <= 0.0) {
      readiness.addBlocker("API526_AREA_INVALID", "Required effective area must be finite and positive",
          "Supply a positive area with an explicit unit");
    }
    readiness.addWarning("API526_SCREENING_ONLY",
        "The kernel selects a standard orifice size only; it does not qualify a valve assembly",
        "Verify the purchased edition, inlet/outlet sizes, pressure class, materials, and vendor certification");
    return readiness.build();
  }

  /** {@inheritDoc} */
  @Override
  public EngineeringCalculationResult<Api526OrificeSelectionAssessment> calculate(Input input,
      EngineeringCalculationContext context) {
    EngineeringCalculationContext effectiveContext = context == null ? EngineeringCalculationContext.builder().build()
        : context;
    CalculationReadiness readiness = assess(input, effectiveContext);
    EngineeringCalculationResult.Builder<Api526OrificeSelectionAssessment> result = EngineeringCalculationResult
        .<Api526OrificeSelectionAssessment>builder("api-526-orifice-selection", getMethod(), getMethodVersion())
        .context(effectiveContext).readiness(readiness);
    if (input == null || !readiness.isReady()) {
      return result.status(EngineeringCalculationResult.Status.BLOCKED)
          .message("API 526 selection is blocked until the readiness findings are resolved").build();
    }

    double requiredIn2 = input.getRequiredAreaIn2();
    int selectedIndex = ORIFICE_AREAS_IN2.length - 1;
    for (int index = 0; index < ORIFICE_AREAS_IN2.length; index++) {
      if (ORIFICE_AREAS_IN2[index] >= requiredIn2) {
        selectedIndex = index;
        break;
      }
    }
    double selectedIn2 = ORIFICE_AREAS_IN2[selectedIndex];
    boolean adequate = selectedIn2 >= requiredIn2;
    Api526OrificeSelectionAssessment assessment = new Api526OrificeSelectionAssessment(
        input.getEdition().getDisplayName(), input.getRequiredAreaM2(), requiredIn2, ORIFICE_LETTERS[selectedIndex],
        selectedIn2 * SQUARE_METRES_PER_SQUARE_INCH, selectedIn2, adequate);
    result.status(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED).value(assessment)
        .input("standard", input.getEdition().getDisplayName()).input("equipmentType", input.getEquipmentType())
        .input("requiredAreaM2", Double.valueOf(input.getRequiredAreaM2()))
        .warning("API 526 screening does not certify valve or installation compliance");
    if (!adequate) {
      result.warning("Required area exceeds the largest catalogued standard orifice").message(
          "No adequate single standard orifice was found; use multiple devices or a separately engineered size");
    } else {
      result.message("API 526 standard-orifice screening completed; valve and installation review remain required");
    }
    return result.build();
  }
}
