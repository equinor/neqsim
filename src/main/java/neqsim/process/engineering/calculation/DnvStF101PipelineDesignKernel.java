package neqsim.process.engineering.calculation;

import neqsim.process.mechanicaldesign.designstandards.StandardApplicability;
import neqsim.process.mechanicaldesign.designstandards.StandardEdition;
import neqsim.process.mechanicaldesign.designstandards.StandardSupportLevel;
import neqsim.process.mechanicaldesign.designstandards.StandardType;
import neqsim.process.mechanicaldesign.pipeline.DnvStF101PipelineAssessment;
import neqsim.process.mechanicaldesign.pipeline.DnvStF101PipelineDesignCalculator;
import neqsim.process.mechanicaldesign.pipeline.DnvStF101PipelineDesignInput;

/** Fail-closed engineering-workflow kernel for DNV-ST-F101 pipeline screening. */
public final class DnvStF101PipelineDesignKernel
    implements EquipmentDesignKernel<DnvStF101PipelineDesignInput, DnvStF101PipelineAssessment> {
  private static final long serialVersionUID = 1000L;
  private static final String IMPLEMENTED_EDITION = "2021";

  @Override
  public StandardType standard() {
    return StandardType.DNV_ST_F101;
  }

  @Override
  public StandardSupportLevel maturity() {
    return StandardSupportLevel.SCREENING;
  }

  @Override
  public boolean supports(StandardEdition edition) {
    return edition != null && edition.getStandardType() == standard()
        && IMPLEMENTED_EDITION.equalsIgnoreCase(edition.getEdition()) && edition.getAmendments().isEmpty();
  }

  @Override
  public StandardApplicability applicability(DnvStF101PipelineDesignInput input) {
    return StandardApplicability.assess(standard(), input == null ? null : input.getEquipmentType());
  }

  @Override
  public String getMethod() {
    return "dnv-st-f101-pipeline-limit-state-screening";
  }

  @Override
  public String getMethodVersion() {
    return "1.0.0";
  }

  @Override
  public CalculationReadiness assess(DnvStF101PipelineDesignInput input, EngineeringCalculationContext context) {
    CalculationReadiness.Builder readiness = CalculationReadiness.builder();
    if (input == null) {
      return readiness
          .addBlocker("DNV_F101_INPUT_MISSING", "DNV-ST-F101 pipeline input is required",
              "Provide geometry, material, pressure cases, loads, fatigue, fabrication, and installation inputs")
          .build();
    }
    StandardApplicability decision = applicability(input);
    if (!decision.isApplicable()) {
      readiness.addBlocker("DNV_F101_NOT_APPLICABLE", decision.getReason(),
          "Use a pipeline equipment type catalogued for DNV-ST-F101");
    }
    if (!supports(input.getEdition())) {
      readiness.addBlocker("DNV_F101_EDITION_NOT_IMPLEMENTED",
          "The kernel implements the unamended 2021 screening basis only",
          "Select DNV-ST-F101 2021 or add a separately reviewed edition adapter");
    }
    assessGeometryAndMaterial(input, readiness);
    assessLoadCases(input, readiness);
    assessFatigueAndInstallation(input, readiness);
    readiness.addWarning("DNV_F101_SCREENING_ONLY",
        "The kernel is a transparent screening implementation, not a conformity assessment",
        "Verify the purchased standard, project amendments, load cases, fabrication records, "
            + "installation analysis, and independent design review");
    readiness.addWarning("DNV_F101_LOAD_INTERACTION_BOUNDARY",
        "Local-buckling load interaction uses a conservative screening envelope",
        "Replace it with the project-approved clause method or nonlinear analysis for design approval");
    return readiness.build();
  }

  @Override
  public EngineeringCalculationResult<DnvStF101PipelineAssessment> calculate(DnvStF101PipelineDesignInput input,
      EngineeringCalculationContext context) {
    EngineeringCalculationContext effectiveContext = context == null ? EngineeringCalculationContext.builder().build()
        : context;
    CalculationReadiness readiness = assess(input, effectiveContext);
    EngineeringCalculationResult.Builder<DnvStF101PipelineAssessment> result = EngineeringCalculationResult
        .<DnvStF101PipelineAssessment>builder("dnv-st-f101-pipeline-limit-state-screening", getMethod(),
            getMethodVersion())
        .context(effectiveContext).readiness(readiness);
    if (input == null || !readiness.isReady()) {
      return result.status(EngineeringCalculationResult.Status.BLOCKED)
          .message("DNV-ST-F101 screening is blocked until all readiness findings are resolved").build();
    }
    try {
      DnvStF101PipelineAssessment assessment = DnvStF101PipelineDesignCalculator.calculate(input);
      result.status(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED).value(assessment)
          .input("standard", input.getEdition().getDisplayName()).input("equipmentType", input.getEquipmentType())
          .input("designInput", input.toMap())
          .warning("A passing screening result is not DNV-ST-F101 certification or engineering approval");
      if (assessment.areAllScreeningChecksPassing()) {
        result.message("DNV-ST-F101 screening completed; independent engineering review remains required");
      } else {
        result.warning("One or more implemented screening checks do not pass")
            .message("DNV-ST-F101 screening completed with findings requiring design revision and review");
      }
      return result.build();
    } catch (RuntimeException exception) {
      return result.status(EngineeringCalculationResult.Status.FAILED)
          .message("DNV-ST-F101 screening failed: " + exception.getMessage()).build();
    }
  }

  private static void assessGeometryAndMaterial(DnvStF101PipelineDesignInput input,
      CalculationReadiness.Builder readiness) {
    positive(input.getOutsideDiameterM(), "DNV_F101_DIAMETER_INVALID", "Outside diameter must be finite and positive",
        readiness);
    positive(input.getNominalWallThicknessM(), "DNV_F101_THICKNESS_INVALID",
        "Nominal wall thickness must be finite and positive", readiness);
    nonNegative(input.getCorrosionAllowanceM(), "DNV_F101_CORROSION_ALLOWANCE_INVALID",
        "Corrosion allowance must be finite and non-negative", readiness);
    fraction(input.getFabricationToleranceFraction(), "DNV_F101_TOLERANCE_INVALID",
        "Fabrication tolerance must be in the interval [0, 1)", readiness);
    fraction(input.getOvalityFraction(), "DNV_F101_OVALITY_INVALID", "Ovality must be in the interval [0, 1)",
        readiness);
    positive(input.getMaximumAllowableOvalityFraction(), "DNV_F101_OVALITY_LIMIT_INVALID",
        "Maximum allowable ovality must be finite and positive", readiness);
    positive(input.getSmysMPa(), "DNV_F101_SMYS_INVALID", "SMYS must be finite and positive", readiness);
    positive(input.getSmtsMPa(), "DNV_F101_SMTS_INVALID", "SMTS must be finite and positive", readiness);
    positive(input.getYoungsModulusMPa(), "DNV_F101_YOUNGS_MODULUS_INVALID",
        "Young's modulus must be finite and positive", readiness);
    if (!Double.isFinite(input.getPoissonRatio()) || input.getPoissonRatio() <= 0.0 || input.getPoissonRatio() >= 0.5) {
      readiness.addBlocker("DNV_F101_POISSON_RATIO_INVALID", "Poisson ratio must be above zero and below 0.5",
          "Supply a traceable material value");
    }
    positiveAtMostOne(input.getStrengthDeratingFactor(), "DNV_F101_DERATING_INVALID",
        "Strength derating factor must be above zero and at most one", readiness);
    positiveAtMostOne(input.getSmysStrengthFactor(), "DNV_F101_SMYS_FACTOR_INVALID",
        "SMYS strength factor must be above zero and at most one", readiness);
    positiveAtMostOne(input.getSmtsStrengthFactor(), "DNV_F101_SMTS_FACTOR_INVALID",
        "SMTS strength factor must be above zero and at most one", readiness);
    atLeastOne(input.getMaterialResistanceFactor(), "DNV_F101_MATERIAL_FACTOR_INVALID",
        "Material resistance factor must be finite and at least one", readiness);
    positiveAtMostOne(input.getFabricationFactor(), "DNV_F101_FABRICATION_FACTOR_INVALID",
        "Fabrication factor must be above zero and at most one", readiness);
    if (input.getSafetyClass() == null) {
      readiness.addBlocker("DNV_F101_SAFETY_CLASS_MISSING", "Safety class is required",
          "Select LOW, MEDIUM, or HIGH from the project consequence assessment");
    }
    if (input.getFabricationRoute() == null) {
      readiness.addBlocker("DNV_F101_FABRICATION_ROUTE_MISSING", "Fabrication route is required",
          "Select a traceable line-pipe fabrication route");
    }
    if (finiteGeometry(input)) {
      double characteristicThickness = input.getNominalWallThicknessM()
          * (1.0 - input.getFabricationToleranceFraction()) - input.getCorrosionAllowanceM();
      if (!Double.isFinite(characteristicThickness) || characteristicThickness <= 0.0
          || 2.0 * characteristicThickness >= input.getOutsideDiameterM()) {
        readiness.addBlocker("DNV_F101_CHARACTERISTIC_THICKNESS_INVALID",
            "Tolerance and corrosion allowance leave an invalid characteristic wall thickness",
            "Revise nominal thickness, tolerance, corrosion allowance, or diameter");
      }
    }
  }

  private static void assessLoadCases(DnvStF101PipelineDesignInput input, CalculationReadiness.Builder readiness) {
    nonNegative(input.getLocalOperatingPressureMPa(), "DNV_F101_OPERATING_PRESSURE_INVALID",
        "Local operating pressure must be finite and non-negative", readiness);
    nonNegative(input.getLocalIncidentalPressureMPa(), "DNV_F101_INCID_PRESSURE_INVALID",
        "Local incidental pressure must be finite and non-negative", readiness);
    nonNegative(input.getExternalPressureMPa(), "DNV_F101_EXTERNAL_PRESSURE_INVALID",
        "External pressure must be finite and non-negative", readiness);
    nonNegative(input.getMinimumInternalPressureMPa(), "DNV_F101_MIN_INTERNAL_PRESSURE_INVALID",
        "Minimum internal pressure must be finite and non-negative", readiness);
    positive(input.getSystemTestPressureMPa(), "DNV_F101_TEST_PRESSURE_INVALID",
        "System test pressure must be finite and positive", readiness);
    nonNegative(input.getTestExternalPressureMPa(), "DNV_F101_TEST_EXTERNAL_PRESSURE_INVALID",
        "Test external pressure must be finite and non-negative", readiness);
    if (Double.isFinite(input.getLocalIncidentalPressureMPa()) && Double.isFinite(input.getLocalOperatingPressureMPa())
        && input.getLocalIncidentalPressureMPa() < input.getLocalOperatingPressureMPa()) {
      readiness.addBlocker("DNV_F101_PRESSURE_ORDER_INVALID",
          "Local incidental pressure is below local operating pressure",
          "Correct the operating and incidental pressure basis");
    }
    finite(input.getDesignAxialForceKN(), "DNV_F101_AXIAL_LOAD_MISSING",
        "Design axial force must be supplied, including an explicit zero", readiness);
    finite(input.getDesignBendingMomentKNm(), "DNV_F101_BENDING_LOAD_MISSING",
        "Design bending moment must be supplied, including an explicit zero", readiness);
    finite(input.getDesignTorsionMomentKNm(), "DNV_F101_TORSION_LOAD_MISSING",
        "Design torsion moment must be supplied, including an explicit zero", readiness);
  }

  private static void assessFatigueAndInstallation(DnvStF101PipelineDesignInput input,
      CalculationReadiness.Builder readiness) {
    finite(input.getInstallationAxialStrainFraction(), "DNV_F101_INSTALL_AXIAL_STRAIN_MISSING",
        "Installation axial strain must be supplied, including an explicit zero", readiness);
    finite(input.getInstallationBendingStrainFraction(), "DNV_F101_INSTALL_BENDING_STRAIN_MISSING",
        "Installation bending strain must be supplied, including an explicit zero", readiness);
    nonNegative(input.getAccumulatedPlasticStrainFraction(), "DNV_F101_PLASTIC_STRAIN_INVALID",
        "Accumulated plastic strain must be finite and non-negative", readiness);
    positive(input.getAllowableInstallationStrainFraction(), "DNV_F101_INSTALL_STRAIN_LIMIT_INVALID",
        "Allowable installation strain must be finite and positive", readiness);
    finite(input.getFatigueSnLogA(), "DNV_F101_SN_LOGA_MISSING", "A project-approved S-N intercept must be supplied",
        readiness);
    positive(input.getFatigueSnSlope(), "DNV_F101_SN_SLOPE_INVALID", "S-N slope must be finite and positive",
        readiness);
    positive(input.getFatigueStressConcentrationFactor(), "DNV_F101_FATIGUE_SCF_INVALID",
        "Fatigue stress concentration factor must be finite and positive", readiness);
    atLeastOne(input.getFatigueDesignFactor(), "DNV_F101_FATIGUE_DFF_INVALID",
        "Fatigue design factor must be finite and at least one", readiness);
    if (input.getFatigueSpectrum().isEmpty()) {
      readiness.addBlocker("DNV_F101_FATIGUE_SPECTRUM_MISSING", "A fatigue stress-range spectrum is required",
          "Supply at least one stress-range and cycle-count bin");
    }
    for (DnvStF101PipelineDesignInput.FatigueBin bin : input.getFatigueSpectrum()) {
      if (!Double.isFinite(bin.getStressRangeMPa()) || bin.getStressRangeMPa() <= 0.0
          || !Double.isFinite(bin.getCycles()) || bin.getCycles() < 0.0) {
        readiness.addBlocker("DNV_F101_FATIGUE_BIN_INVALID",
            "Fatigue bins require a positive stress range and non-negative cycle count",
            "Correct the fatigue spectrum");
        break;
      }
    }
  }

  private static boolean finiteGeometry(DnvStF101PipelineDesignInput input) {
    return Double.isFinite(input.getOutsideDiameterM()) && Double.isFinite(input.getNominalWallThicknessM())
        && Double.isFinite(input.getFabricationToleranceFraction()) && Double.isFinite(input.getCorrosionAllowanceM());
  }

  private static void positive(double value, String code, String message, CalculationReadiness.Builder readiness) {
    if (!Double.isFinite(value) || value <= 0.0) {
      readiness.addBlocker(code, message, "Supply a traceable project design value");
    }
  }

  private static void nonNegative(double value, String code, String message, CalculationReadiness.Builder readiness) {
    if (!Double.isFinite(value) || value < 0.0) {
      readiness.addBlocker(code, message, "Supply a traceable project design value");
    }
  }

  private static void finite(double value, String code, String message, CalculationReadiness.Builder readiness) {
    if (!Double.isFinite(value)) {
      readiness.addBlocker(code, message, "Supply a traceable project design value");
    }
  }

  private static void fraction(double value, String code, String message, CalculationReadiness.Builder readiness) {
    if (!Double.isFinite(value) || value < 0.0 || value >= 1.0) {
      readiness.addBlocker(code, message, "Supply the value as a fraction, not percent");
    }
  }

  private static void positiveAtMostOne(double value, String code, String message,
      CalculationReadiness.Builder readiness) {
    if (!Double.isFinite(value) || value <= 0.0 || value > 1.0) {
      readiness.addBlocker(code, message, "Supply a traceable project design factor");
    }
  }

  private static void atLeastOne(double value, String code, String message, CalculationReadiness.Builder readiness) {
    if (!Double.isFinite(value) || value < 1.0) {
      readiness.addBlocker(code, message, "Supply a traceable project design factor");
    }
  }
}
