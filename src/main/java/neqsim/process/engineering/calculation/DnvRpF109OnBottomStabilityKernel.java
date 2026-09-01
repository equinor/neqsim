package neqsim.process.engineering.calculation;

import java.util.HashSet;
import java.util.Set;
import neqsim.process.mechanicaldesign.designstandards.StandardApplicability;
import neqsim.process.mechanicaldesign.designstandards.StandardEdition;
import neqsim.process.mechanicaldesign.designstandards.StandardSupportLevel;
import neqsim.process.mechanicaldesign.designstandards.StandardType;
import neqsim.process.mechanicaldesign.subsea.DnvRpF109OnBottomStabilityAssessment;
import neqsim.process.mechanicaldesign.subsea.DnvRpF109OnBottomStabilityCalculator;
import neqsim.process.mechanicaldesign.subsea.DnvRpF109OnBottomStabilityInput;
import neqsim.process.mechanicaldesign.subsea.DnvRpF109OnBottomStabilityInput.LateralMethod;
import neqsim.process.mechanicaldesign.subsea.DnvRpF109OnBottomStabilityInput.LoadCase;

/** Fail-closed engineering-workflow kernel for DNV-RP-F109 on-bottom stability screening. */
public final class DnvRpF109OnBottomStabilityKernel
    implements EquipmentDesignKernel<DnvRpF109OnBottomStabilityInput, DnvRpF109OnBottomStabilityAssessment> {
  private static final long serialVersionUID = 1000L;

  /** {@inheritDoc} */
  @Override
  public StandardType standard() {
    return StandardType.DNV_RP_F109;
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
        && standard().getDefaultVersion().equalsIgnoreCase(edition.getEdition()) && edition.getAmendments().isEmpty();
  }

  /** {@inheritDoc} */
  @Override
  public StandardApplicability applicability(DnvRpF109OnBottomStabilityInput input) {
    return StandardApplicability.assess(standard(), input == null ? null : input.getEquipmentType());
  }

  /** {@inheritDoc} */
  @Override
  public String getMethod() {
    return "dnv-rp-f109-on-bottom-stability-screening";
  }

  /** {@inheritDoc} */
  @Override
  public String getMethodVersion() {
    return "1.0.0";
  }

  /** {@inheritDoc} */
  @Override
  public CalculationReadiness assess(DnvRpF109OnBottomStabilityInput input, EngineeringCalculationContext context) {
    CalculationReadiness.Builder readiness = CalculationReadiness.builder();
    if (input == null) {
      return readiness.addBlocker("DNV_F109_INPUT_MISSING", "DNV-RP-F109 on-bottom stability input is required",
          "Provide geometry, seawater, load cases, hydrodynamic, soil, safety-factor, and response inputs").build();
    }
    StandardApplicability decision = applicability(input);
    if (decision.getStatus() != StandardApplicability.Status.APPLICABLE) {
      readiness.addBlocker("DNV_F109_NOT_APPLICABLE", decision.getReason(),
          "Use Pipeline, AdiabaticPipe, PipeBeggsAndBrills, FlexiblePipe, Umbilical, or Cable equipment context");
    }
    if (!supports(input.getEdition())) {
      readiness.addBlocker("DNV_F109_EDITION_UNSUPPORTED",
          "Only DNV-RP-F109 2021-05 amended 2025-09 without project amendments is implemented",
          "Use the exact catalogued edition or route another edition to independent engineering review");
    }
    if (input.getAssetType() == null) {
      readiness.addBlocker("DNV_F109_ASSET_TYPE_MISSING", "Subsea asset type is required",
          "Select PIPELINE, CABLE, or UMBILICAL");
    } else if (!assetMatchesEquipment(input)) {
      readiness.addBlocker("DNV_F109_ASSET_EQUIPMENT_MISMATCH",
          "Asset type " + input.getAssetType() + " does not match equipment type " + input.getEquipmentType(),
          "Use pipeline equipment with PIPELINE, Cable with CABLE, or Umbilical with UMBILICAL");
    }
    positive(input.getOutsideDiameterM(), "DNV_F109_DIAMETER_INVALID",
        "Hydrodynamic outside diameter must be finite and positive", readiness);
    positive(input.getSeawaterDensityKgM3(), "DNV_F109_WATER_DENSITY_INVALID",
        "Seawater density must be finite and positive", readiness);
    positive(input.getGravitationalAccelerationMPerS2(), "DNV_F109_GRAVITY_INVALID",
        "Gravitational acceleration must be finite and positive", readiness);
    if (blank(input.getEngineeringBasis())) {
      readiness.addBlocker("DNV_F109_ENGINEERING_BASIS_MISSING", "A traceable engineering basis is required",
          "Reference environmental, soil, hydrodynamic, and design-condition sources");
    }
    assessLoadCases(input, readiness);
    readiness.addWarning("DNV_F109_SCREENING_ONLY",
        "This transparent kernel is not a clause-complete DNV-RP-F109 conformity assessment",
        "Verify the current licensed RP, project basis, hydrodynamics, pipe-soil interaction, "
            + "and independent approval");
    readiness.addWarning("DNV_F109_RESPONSE_BOUNDARY",
        "Generalized and dynamic response displacements are checked but not calculated or qualified",
        "Use a validated response model and preserve its applicability and verification evidence");
    return readiness.build();
  }

  /** {@inheritDoc} */
  @Override
  public EngineeringCalculationResult<DnvRpF109OnBottomStabilityAssessment> calculate(
      DnvRpF109OnBottomStabilityInput input, EngineeringCalculationContext context) {
    EngineeringCalculationContext effectiveContext = context == null ? EngineeringCalculationContext.builder().build()
        : context;
    CalculationReadiness readiness = assess(input, effectiveContext);
    EngineeringCalculationResult.Builder<DnvRpF109OnBottomStabilityAssessment> result = EngineeringCalculationResult
        .<DnvRpF109OnBottomStabilityAssessment>builder("dnv-rp-f109-on-bottom-stability", getMethod(),
            getMethodVersion())
        .context(effectiveContext).readiness(readiness);
    if (input == null || !readiness.isReady()) {
      return result.status(EngineeringCalculationResult.Status.BLOCKED)
          .message("DNV-RP-F109 screening is blocked until every readiness finding is resolved").build();
    }
    try {
      DnvRpF109OnBottomStabilityAssessment assessment = DnvRpF109OnBottomStabilityCalculator.calculate(input);
      result.status(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED).value(assessment)
          .input("standard", input.getEdition().getDisplayName()).input("equipmentType", input.getEquipmentType())
          .input("designInput", input.toMap())
          .warning("A passing screening result is not DNV-RP-F109 certification or engineering approval");
      if (assessment.areAllScreeningChecksPassing()) {
        return result.message("Implemented stability checks pass; independent engineering review remains required")
            .build();
      }
      return result.message("One or more implemented stability checks fail; redesign and engineering review required")
          .build();
    } catch (RuntimeException exception) {
      return result.status(EngineeringCalculationResult.Status.FAILED)
          .message("DNV-RP-F109 screening failed: " + exception.getMessage()).build();
    }
  }

  private static void assessLoadCases(DnvRpF109OnBottomStabilityInput input, CalculationReadiness.Builder readiness) {
    if (input.getLoadCases().isEmpty()) {
      readiness.addBlocker("DNV_F109_LOAD_CASES_MISSING", "At least one stability load case is required",
          "Provide empty, installation, hydrotest, operating, or other governing design conditions");
      return;
    }
    Set<String> caseIds = new HashSet<String>();
    for (LoadCase loadCase : input.getLoadCases()) {
      if (loadCase == null || blank(loadCase.getCaseId())) {
        readiness.addBlocker("DNV_F109_CASE_ID_MISSING", "Every stability load case needs a non-blank identifier",
            "Assign a unique traceable case identifier");
        continue;
      }
      String prefix = loadCase.getCaseId().trim();
      if (!caseIds.add(prefix)) {
        readiness.addBlocker("DNV_F109_CASE_ID_DUPLICATE", "Duplicate stability load-case identifier: " + prefix,
            "Use unique identifiers for every environmental and design condition");
      }
      positive(loadCase.getSubmergedWeightNPerM(), "DNV_F109_SUBMERGED_WEIGHT_INVALID",
          prefix + ": submerged weight must be finite and positive", readiness);
      nonNegative(loadCase.getCurrentVelocityMPerS(), "DNV_F109_CURRENT_INVALID",
          prefix + ": current velocity must be finite and non-negative", readiness);
      nonNegative(loadCase.getWaveVelocityMPerS(), "DNV_F109_WAVE_VELOCITY_INVALID",
          prefix + ": wave velocity must be finite and non-negative", readiness);
      nonNegative(loadCase.getWaveAccelerationMPerS2(), "DNV_F109_WAVE_ACCELERATION_INVALID",
          prefix + ": wave acceleration must be finite and non-negative", readiness);
      finite(loadCase.getCurrentDirectionRelativeToPipeDeg(), "DNV_F109_CURRENT_DIRECTION_INVALID",
          prefix + ": current direction must be finite", readiness);
      finite(loadCase.getWaveDirectionRelativeToPipeDeg(), "DNV_F109_WAVE_DIRECTION_INVALID",
          prefix + ": wave direction must be finite", readiness);
      positive(loadCase.getDragCoefficient(), "DNV_F109_DRAG_COEFFICIENT_INVALID",
          prefix + ": drag coefficient must be finite and positive", readiness);
      nonNegative(loadCase.getLiftCoefficient(), "DNV_F109_LIFT_COEFFICIENT_INVALID",
          prefix + ": lift coefficient must be finite and non-negative", readiness);
      nonNegative(loadCase.getInertiaCoefficient(), "DNV_F109_INERTIA_COEFFICIENT_INVALID",
          prefix + ": inertia coefficient must be finite and non-negative", readiness);
      factor(loadCase.getHorizontalLoadReductionFactor(), "DNV_F109_HORIZONTAL_REDUCTION_INVALID",
          prefix + ": horizontal reduction factor must be above zero and at most one", readiness);
      factor(loadCase.getVerticalLoadReductionFactor(), "DNV_F109_VERTICAL_REDUCTION_INVALID",
          prefix + ": vertical reduction factor must be above zero and at most one", readiness);
      positive(loadCase.getSoilFrictionCoefficient(), "DNV_F109_FRICTION_INVALID",
          prefix + ": soil friction coefficient must be finite and positive", readiness);
      nonNegative(loadCase.getPassiveSoilResistanceNPerM(), "DNV_F109_PASSIVE_RESISTANCE_INVALID",
          prefix + ": passive soil resistance must be finite and non-negative", readiness);
      atLeastOne(loadCase.getHorizontalSafetyFactor(), "DNV_F109_HORIZONTAL_FACTOR_INVALID",
          prefix + ": horizontal safety factor must be finite and at least one", readiness);
      atLeastOne(loadCase.getVerticalSafetyFactor(), "DNV_F109_VERTICAL_FACTOR_INVALID",
          prefix + ": vertical safety factor must be finite and at least one", readiness);
      positive(loadCase.getStormDurationHours(), "DNV_F109_STORM_DURATION_INVALID",
          prefix + ": storm duration must be finite and positive", readiness);
      positive(loadCase.getOscillationCount(), "DNV_F109_OSCILLATION_COUNT_INVALID",
          prefix + ": oscillation count must be finite and positive", readiness);
      assessLateralMethod(loadCase, prefix, readiness);
    }
  }

  private static void assessLateralMethod(LoadCase loadCase, String prefix, CalculationReadiness.Builder readiness) {
    if (loadCase.getLateralMethod() == null) {
      readiness.addBlocker("DNV_F109_LATERAL_METHOD_MISSING", prefix + ": lateral method is required",
          "Select absolute static or an external response displacement route");
      return;
    }
    if (loadCase.getLateralMethod() == LateralMethod.ABSOLUTE_STATIC) {
      return;
    }
    nonNegative(loadCase.getPredictedLateralDisplacementM(), "DNV_F109_DISPLACEMENT_INVALID",
        prefix + ": external predicted displacement must be finite and non-negative", readiness);
    if (!Boolean.TRUE.equals(loadCase.getResponseModelWithinValidatedRange())) {
      readiness.addBlocker("DNV_F109_RESPONSE_RANGE_UNCONFIRMED",
          prefix + ": external response model validity range is not confirmed",
          "Confirm method, soil, hydrodynamic, storm, geometry, and response applicability");
    }
    if (blank(loadCase.getResponseModelBasis())) {
      readiness.addBlocker("DNV_F109_RESPONSE_BASIS_MISSING", prefix + ": external response basis is required",
          "Reference the qualified generalized or dynamic model, version, inputs, and verification evidence");
    }
    if (loadCase.getLateralMethod() == LateralMethod.EXTERNAL_RESPONSE_USER_DEFINED) {
      positive(loadCase.getAllowableDisplacementDiameters(), "DNV_F109_DISPLACEMENT_LIMIT_INVALID",
          prefix + ": user-defined displacement limit must be finite and positive", readiness);
    }
  }

  private static void positive(double value, String code, String message, CalculationReadiness.Builder readiness) {
    if (!Double.isFinite(value) || value <= 0.0) {
      readiness.addBlocker(code, message, "Supply a traceable positive project value");
    }
  }

  private static void nonNegative(double value, String code, String message, CalculationReadiness.Builder readiness) {
    if (!Double.isFinite(value) || value < 0.0) {
      readiness.addBlocker(code, message, "Supply a traceable non-negative project value");
    }
  }

  private static void finite(double value, String code, String message, CalculationReadiness.Builder readiness) {
    if (!Double.isFinite(value)) {
      readiness.addBlocker(code, message, "Supply a traceable finite project value");
    }
  }

  private static void atLeastOne(double value, String code, String message, CalculationReadiness.Builder readiness) {
    if (!Double.isFinite(value) || value < 1.0) {
      readiness.addBlocker(code, message, "Supply a traceable project factor of at least one");
    }
  }

  private static void factor(double value, String code, String message, CalculationReadiness.Builder readiness) {
    if (!Double.isFinite(value) || value <= 0.0 || value > 1.0) {
      readiness.addBlocker(code, message, "Supply a traceable project reduction factor in (0, 1]");
    }
  }

  private static boolean blank(String value) {
    return value == null || value.trim().isEmpty();
  }

  private static boolean assetMatchesEquipment(DnvRpF109OnBottomStabilityInput input) {
    String equipmentType = input.getEquipmentType();
    if (blank(equipmentType)) {
      return false;
    }
    if (input.getAssetType() == DnvRpF109OnBottomStabilityInput.AssetType.CABLE) {
      return "Cable".equalsIgnoreCase(equipmentType.trim());
    }
    if (input.getAssetType() == DnvRpF109OnBottomStabilityInput.AssetType.UMBILICAL) {
      return "Umbilical".equalsIgnoreCase(equipmentType.trim());
    }
    return "Pipeline".equalsIgnoreCase(equipmentType.trim()) || "AdiabaticPipe".equalsIgnoreCase(equipmentType.trim())
        || "PipeBeggsAndBrills".equalsIgnoreCase(equipmentType.trim())
        || "FlexiblePipe".equalsIgnoreCase(equipmentType.trim());
  }
}
