package neqsim.process.engineering.calculation;

import java.io.Serializable;
import neqsim.process.mechanicaldesign.compressor.CompressorCasingDesignCalculator;
import neqsim.process.mechanicaldesign.compressor.CompressorMechanicalDesign;
import neqsim.process.mechanicaldesign.designstandards.StandardApplicability;
import neqsim.process.mechanicaldesign.designstandards.StandardEdition;
import neqsim.process.mechanicaldesign.designstandards.StandardSupportLevel;
import neqsim.process.mechanicaldesign.designstandards.StandardType;

/** Pure engineering-workflow adapter for the legacy API 617 compressor-casing screening calculator. */
public final class Api617CompressorDesignKernel
    implements EquipmentDesignKernel<Api617CompressorDesignKernel.Input, Api617CompressorAssessment> {
  private static final long serialVersionUID = 1000L;
  private static final String IMPLEMENTED_EDITION = "8th Ed";

  /** Immutable, defensively copied kernel input. */
  public static final class Input implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final StandardEdition edition;
    private final String equipmentType;
    private final double designPressureMPa;
    private final double designTemperatureC;
    private final double maxOperatingPressureMPa;
    private final double maxOperatingTemperatureC;
    private final double minOperatingTemperatureC;
    private final double casingInnerDiameterMm;
    private final double casingLengthMm;
    private final String materialGrade;
    private final double corrosionAllowanceMm;
    private final double jointEfficiency;
    private final CompressorMechanicalDesign.CasingType casingType;
    private final boolean sourService;
    private final double h2sPartialPressureKPa;
    private final double suctionNozzleSizeMm;
    private final double dischargeNozzleSizeMm;

    /**
     * Create an API 617 kernel input from a legacy calculator configuration.
     *
     * @param edition explicit API 617 edition
     * @param equipmentType simple compressor equipment class name
     * @param configuration configured, unexecuted legacy calculator
     */
    public Input(StandardEdition edition, String equipmentType, CompressorCasingDesignCalculator configuration) {
      if (edition == null || edition.getStandardType() != StandardType.API_617) {
        throw new IllegalArgumentException("edition must identify API-617");
      }
      if (equipmentType == null || equipmentType.trim().isEmpty()) {
        throw new IllegalArgumentException("equipmentType cannot be null or blank");
      }
      if (configuration == null) {
        throw new IllegalArgumentException("configuration cannot be null");
      }
      this.edition = edition;
      this.equipmentType = equipmentType.trim();
      designPressureMPa = configuration.getDesignPressureMPa();
      designTemperatureC = configuration.getDesignTemperatureC();
      maxOperatingPressureMPa = configuration.getMaxOperatingPressureMPa();
      maxOperatingTemperatureC = configuration.getMaxOperatingTemperatureC();
      minOperatingTemperatureC = configuration.getMinOperatingTemperatureC();
      casingInnerDiameterMm = configuration.getCasingInnerDiameterMm();
      casingLengthMm = configuration.getCasingLengthMm();
      materialGrade = configuration.getMaterialGrade();
      corrosionAllowanceMm = configuration.getCorrosionAllowanceMm();
      jointEfficiency = configuration.getJointEfficiency();
      casingType = configuration.getCasingType();
      sourService = configuration.isSourService();
      h2sPartialPressureKPa = configuration.getH2sPartialPressureKPa();
      suctionNozzleSizeMm = configuration.getSuctionNozzleSizeMm();
      dischargeNozzleSizeMm = configuration.getDischargeNozzleSizeMm();
    }

    /** @return explicit API 617 edition */
    public StandardEdition getEdition() {
      return edition;
    }

    /** @return simple compressor equipment class name */
    public String getEquipmentType() {
      return equipmentType;
    }

    /** @return independent copy of the legacy calculator configuration */
    public CompressorCasingDesignCalculator getConfiguration() {
      CompressorCasingDesignCalculator configuration = new CompressorCasingDesignCalculator();
      configuration.setDesignPressureMPa(designPressureMPa);
      configuration.setDesignTemperatureC(designTemperatureC);
      configuration.setMaxOperatingPressureMPa(maxOperatingPressureMPa);
      configuration.setMaxOperatingTemperatureC(maxOperatingTemperatureC);
      configuration.setMinOperatingTemperatureC(minOperatingTemperatureC);
      configuration.setCasingInnerDiameterMm(casingInnerDiameterMm);
      configuration.setCasingLengthMm(casingLengthMm);
      configuration.setMaterialGrade(materialGrade);
      configuration.setCorrosionAllowanceMm(corrosionAllowanceMm);
      configuration.setJointEfficiency(jointEfficiency);
      configuration.setCasingType(casingType);
      configuration.setSourService(sourService);
      configuration.setH2sPartialPressureKPa(h2sPartialPressureKPa);
      configuration.setSuctionNozzleSizeMm(suctionNozzleSizeMm);
      configuration.setDischargeNozzleSizeMm(dischargeNozzleSizeMm);
      return configuration;
    }
  }

  /** {@inheritDoc} */
  @Override
  public StandardType standard() {
    return StandardType.API_617;
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
    return "api-617-compressor-casing-screening";
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
      return readiness.addBlocker("API617_INPUT_MISSING", "API 617 compressor-casing input is required",
          "Provide an edition, compressor equipment type, and calculator configuration").build();
    }
    StandardApplicability decision = applicability(input);
    if (!decision.isApplicable()) {
      readiness.addBlocker("API617_NOT_APPLICABLE", decision.getReason(),
          "Use API 617 only for Compressor equipment");
    }
    if (!supports(input.getEdition())) {
      readiness.addBlocker("API617_EDITION_NOT_IMPLEMENTED",
          "The API 617 kernel implements " + IMPLEMENTED_EDITION + ", not " + input.getEdition().getEdition(),
          "Select the implemented edition or add separately validated edition logic");
    }
    assessConfiguration(input.getConfiguration(), readiness);
    readiness.addWarning("API617_SCREENING_ONLY",
        "The compressor-casing calculator provides preliminary mechanical screening, not package conformity",
        "Verify the purchased edition, rotor dynamics, vendor design, materials, auxiliaries, and project requirements");
    return readiness.build();
  }

  /** {@inheritDoc} */
  @Override
  public EngineeringCalculationResult<Api617CompressorAssessment> calculate(Input input,
      EngineeringCalculationContext context) {
    EngineeringCalculationContext effectiveContext = context == null ? EngineeringCalculationContext.builder().build()
        : context;
    CalculationReadiness readiness = assess(input, effectiveContext);
    EngineeringCalculationResult.Builder<Api617CompressorAssessment> result = EngineeringCalculationResult
        .<Api617CompressorAssessment>builder("api-617-compressor-casing-screening", getMethod(), getMethodVersion())
        .context(effectiveContext).readiness(readiness);
    if (input == null || !readiness.isReady()) {
      return result.status(EngineeringCalculationResult.Status.BLOCKED)
          .message("API 617 screening is blocked until the readiness findings are resolved").build();
    }

    CompressorCasingDesignCalculator calculator = input.getConfiguration();
    try {
      calculator.calculate();
      Api617CompressorAssessment assessment = Api617CompressorAssessment.from(input.getEdition().getDisplayName(),
          calculator);
      result.status(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED).value(assessment)
          .input("standard", input.getEdition().getDisplayName()).input("equipmentType", input.getEquipmentType())
          .input("designPressureMPa", Double.valueOf(calculator.getDesignPressureMPa()))
          .input("designTemperatureC", Double.valueOf(calculator.getDesignTemperatureC()))
          .warning("API 617 screening does not certify compressor package or casing compliance");
      if (!assessment.areAllScreeningChecksPassing()) {
        result.warning("One or more implemented compressor-casing screening checks do not pass")
            .message("API 617 screening completed with findings requiring design revision and vendor review");
      } else {
        result.message("API 617 compressor-casing screening completed; vendor review remains required");
      }
      return result.build();
    } catch (RuntimeException exception) {
      return result.status(EngineeringCalculationResult.Status.FAILED)
          .message("API 617 screening failed: " + exception.getMessage()).build();
    }
  }

  private static void assessConfiguration(CompressorCasingDesignCalculator calculator,
      CalculationReadiness.Builder readiness) {
    if (!positive(calculator.getDesignPressureMPa())) {
      readiness.addBlocker("API617_DESIGN_PRESSURE_INVALID", "Design pressure must be finite and positive",
          "Supply the compressor-casing design pressure in MPa");
    }
    if (!positive(calculator.getMaxOperatingPressureMPa())
        || calculator.getMaxOperatingPressureMPa() > calculator.getDesignPressureMPa()) {
      readiness.addBlocker("API617_OPERATING_PRESSURE_INVALID",
          "Maximum operating pressure must be positive and no greater than design pressure",
          "Correct the governing pressure basis");
    }
    if (!Double.isFinite(calculator.getDesignTemperatureC())
        || !Double.isFinite(calculator.getMaxOperatingTemperatureC())
        || !Double.isFinite(calculator.getMinOperatingTemperatureC())
        || calculator.getMinOperatingTemperatureC() > calculator.getMaxOperatingTemperatureC()
        || calculator.getMaxOperatingTemperatureC() > calculator.getDesignTemperatureC()) {
      readiness.addBlocker("API617_TEMPERATURE_BASIS_INVALID",
          "Operating temperature range must be finite, ordered, and within design temperature",
          "Correct the compressor-casing temperature basis");
    }
    if (!positive(calculator.getCasingInnerDiameterMm()) || !positive(calculator.getCasingLengthMm())) {
      readiness.addBlocker("API617_CASING_GEOMETRY_INVALID", "Casing diameter and length must be positive",
          "Supply preliminary casing geometry in millimetres");
    }
    if (calculator.getMaterialGrade() == null || calculator.getMaterialGrade().trim().isEmpty()) {
      readiness.addBlocker("API617_MATERIAL_MISSING", "A traceable material grade is required",
          "Select a supported casing material grade");
    }
    if (!Double.isFinite(calculator.getCorrosionAllowanceMm()) || calculator.getCorrosionAllowanceMm() < 0.0) {
      readiness.addBlocker("API617_CORROSION_ALLOWANCE_INVALID",
          "Corrosion allowance must be finite and non-negative", "Supply the project corrosion allowance");
    }
    if (!Double.isFinite(calculator.getJointEfficiency()) || calculator.getJointEfficiency() <= 0.0
        || calculator.getJointEfficiency() > 1.0) {
      readiness.addBlocker("API617_JOINT_EFFICIENCY_INVALID", "Joint efficiency must be above zero and at most one",
          "Supply the verified fabrication joint efficiency");
    }
    if (!positive(calculator.getSuctionNozzleSizeMm()) || !positive(calculator.getDischargeNozzleSizeMm())) {
      readiness.addBlocker("API617_NOZZLE_SIZE_INVALID", "Suction and discharge nozzle sizes must be positive",
          "Supply preliminary nozzle sizes in millimetres");
    }
    if (calculator.isSourService()
        && (!Double.isFinite(calculator.getH2sPartialPressureKPa()) || calculator.getH2sPartialPressureKPa() < 0.0)) {
      readiness.addBlocker("API617_H2S_BASIS_INVALID", "Sour-service H2S partial pressure must be non-negative",
          "Supply the governing H2S partial pressure in kPa");
    }
  }

  private static boolean positive(double value) {
    return Double.isFinite(value) && value > 0.0;
  }
}
