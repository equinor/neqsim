package neqsim.process.mechanicaldesign.subsea;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import neqsim.process.mechanicaldesign.designstandards.StandardType;
import neqsim.process.mechanicaldesign.subsea.DnvRpF109OnBottomStabilityInput.LateralMethod;
import neqsim.process.mechanicaldesign.subsea.DnvRpF109OnBottomStabilityInput.LoadCase;
import neqsim.process.mechanicaldesign.subsea.DnvRpF109StabilityCheck.LimitState;

/**
 * Pure calculator for transparent DNV-RP-F109 on-bottom stability screening.
 *
 * <p>
 * The absolute-static route uses normal flow components, conservative co-linear wave and current velocity addition,
 * Morison drag/inertia loads, lift, Coulomb friction, and caller-supplied passive resistance. The response routes only
 * check displacement produced by an externally validated generalized or dynamic model. This calculator does not
 * reproduce proprietary design tables or qualify an external response model.
 * </p>
 */
public final class DnvRpF109OnBottomStabilityCalculator {
  private DnvRpF109OnBottomStabilityCalculator() {
  }

  /**
   * Calculate every load case.
   *
   * @param input complete input already accepted by the typed kernel readiness check
   * @return immutable on-bottom stability assessment
   */
  public static DnvRpF109OnBottomStabilityAssessment calculate(DnvRpF109OnBottomStabilityInput input) {
    validateDirectUse(input);
    List<DnvRpF109OnBottomStabilityAssessment.CaseResult> caseResults = new ArrayList<DnvRpF109OnBottomStabilityAssessment.CaseResult>();
    List<DnvRpF109StabilityCheck> checks = new ArrayList<DnvRpF109StabilityCheck>();

    double diameter = input.getOutsideDiameterM();
    double waterDensity = input.getSeawaterDensityKgM3();
    double buoyancy = waterDensity * input.getGravitationalAccelerationMPerS2() * Math.PI * diameter * diameter / 4.0;

    for (LoadCase loadCase : input.getLoadCases()) {
      double normalCurrent = normalComponent(loadCase.getCurrentVelocityMPerS(),
          loadCase.getCurrentDirectionRelativeToPipeDeg());
      double normalWave = normalComponent(loadCase.getWaveVelocityMPerS(),
          loadCase.getWaveDirectionRelativeToPipeDeg());
      double normalAcceleration = normalComponent(loadCase.getWaveAccelerationMPerS2(),
          loadCase.getWaveDirectionRelativeToPipeDeg());
      double combinedVelocity = normalCurrent + normalWave;

      double drag = loadCase.getHorizontalLoadReductionFactor() * 0.5 * waterDensity * loadCase.getDragCoefficient()
          * diameter * combinedVelocity * combinedVelocity;
      double inertia = loadCase.getHorizontalLoadReductionFactor() * waterDensity * loadCase.getInertiaCoefficient()
          * Math.PI * diameter * diameter * normalAcceleration / 4.0;
      double lift = loadCase.getVerticalLoadReductionFactor() * 0.5 * waterDensity * loadCase.getLiftCoefficient()
          * diameter * combinedVelocity * combinedVelocity;
      double horizontalDemand = loadCase.getHorizontalSafetyFactor() * (drag + inertia);
      double verticalDemand = loadCase.getVerticalSafetyFactor() * lift;
      double effectiveNormalWeight = Math.max(loadCase.getSubmergedWeightNPerM() - verticalDemand, 0.0);
      double lateralResistance = loadCase.getSoilFrictionCoefficient() * effectiveNormalWeight
          + loadCase.getPassiveSoilResistanceNPerM();
      double requiredWeight = Double.NaN;

      checks.add(new DnvRpF109StabilityCheck(loadCase.getCaseId(), LimitState.VERTICAL_STABILITY, verticalDemand,
          loadCase.getSubmergedWeightNPerM(), "N/m", "factored lift versus submerged weight",
          "Vertical equilibrium screening; project seabed, embedment, and uplift mechanisms require review."));

      if (loadCase.getLateralMethod() == LateralMethod.ABSOLUTE_STATIC) {
        requiredWeight = verticalDemand + Math.max(horizontalDemand - loadCase.getPassiveSoilResistanceNPerM(), 0.0)
            / loadCase.getSoilFrictionCoefficient();
        checks.add(new DnvRpF109StabilityCheck(loadCase.getCaseId(), LimitState.ABSOLUTE_LATERAL_STABILITY,
            horizontalDemand, lateralResistance, "N/m", "Morison absolute-static screening",
            "Hydrodynamic coefficients, reduction factors, penetration, and soil resistance are project inputs."));
      } else {
        double allowableDisplacement = allowableDisplacementDiameters(loadCase) * diameter;
        checks.add(new DnvRpF109StabilityCheck(loadCase.getCaseId(), LimitState.LATERAL_DISPLACEMENT,
            loadCase.getPredictedLateralDisplacementM(), allowableDisplacement, "m",
            "external response displacement check",
            "The external generalized or dynamic response is checked, not calculated or qualified by NeqSim."));
      }

      double specificGravity = (loadCase.getSubmergedWeightNPerM() + buoyancy) / buoyancy;
      caseResults.add(new DnvRpF109OnBottomStabilityAssessment.CaseResult(loadCase.getCaseId(), normalCurrent,
          normalWave, normalAcceleration, drag, inertia, lift, horizontalDemand, lateralResistance, requiredWeight,
          specificGravity));
    }
    return new DnvRpF109OnBottomStabilityAssessment(caseResults, checks);
  }

  private static double normalComponent(double magnitude, double directionDegrees) {
    return Math.abs(magnitude * Math.sin(Math.toRadians(directionDegrees)));
  }

  private static double allowableDisplacementDiameters(LoadCase loadCase) {
    if (loadCase.getLateralMethod() == LateralMethod.EXTERNAL_RESPONSE_0_5D) {
      return 0.5;
    }
    if (loadCase.getLateralMethod() == LateralMethod.EXTERNAL_RESPONSE_10D) {
      return 10.0;
    }
    return loadCase.getAllowableDisplacementDiameters();
  }

  private static void validateDirectUse(DnvRpF109OnBottomStabilityInput input) {
    if (input == null || !supportedEdition(input) || input.getAssetType() == null || input.getEquipmentType() == null
        || input.getEquipmentType().trim().isEmpty() || !assetMatchesEquipment(input)
        || input.getEngineeringBasis() == null || input.getEngineeringBasis().trim().isEmpty()
        || !positive(input.getOutsideDiameterM()) || !positive(input.getSeawaterDensityKgM3())
        || !positive(input.getGravitationalAccelerationMPerS2()) || input.getLoadCases().isEmpty()) {
      throw new IllegalArgumentException("Complete DNV-RP-F109 input is required; use the typed kernel for findings");
    }
    Set<String> caseIds = new HashSet<String>();
    for (LoadCase loadCase : input.getLoadCases()) {
      if (loadCase == null || loadCase.getCaseId() == null || loadCase.getCaseId().trim().isEmpty()
          || !positive(loadCase.getSubmergedWeightNPerM()) || !nonNegative(loadCase.getCurrentVelocityMPerS())
          || !nonNegative(loadCase.getWaveVelocityMPerS()) || !nonNegative(loadCase.getWaveAccelerationMPerS2())
          || !Double.isFinite(loadCase.getCurrentDirectionRelativeToPipeDeg())
          || !Double.isFinite(loadCase.getWaveDirectionRelativeToPipeDeg()) || !positive(loadCase.getDragCoefficient())
          || !nonNegative(loadCase.getLiftCoefficient()) || !nonNegative(loadCase.getInertiaCoefficient())
          || !factor(loadCase.getHorizontalLoadReductionFactor()) || !factor(loadCase.getVerticalLoadReductionFactor())
          || !positive(loadCase.getSoilFrictionCoefficient()) || !nonNegative(loadCase.getPassiveSoilResistanceNPerM())
          || !atLeastOne(loadCase.getHorizontalSafetyFactor()) || !atLeastOne(loadCase.getVerticalSafetyFactor())
          || !positive(loadCase.getStormDurationHours()) || !positive(loadCase.getOscillationCount())
          || loadCase.getLateralMethod() == null) {
        throw new IllegalArgumentException("Invalid DNV-RP-F109 load case: " + loadCaseId(loadCase));
      }
      if (!caseIds.add(loadCase.getCaseId().trim())) {
        throw new IllegalArgumentException("Duplicate DNV-RP-F109 load case: " + loadCase.getCaseId());
      }
      if (loadCase.getLateralMethod() != LateralMethod.ABSOLUTE_STATIC
          && (!nonNegative(loadCase.getPredictedLateralDisplacementM()) || !responseBasisComplete(loadCase))) {
        throw new IllegalArgumentException("External response evidence is incomplete for " + loadCase.getCaseId());
      }
      if (loadCase.getLateralMethod() == LateralMethod.EXTERNAL_RESPONSE_USER_DEFINED
          && !positive(loadCase.getAllowableDisplacementDiameters())) {
        throw new IllegalArgumentException("User-defined displacement limit is invalid for " + loadCase.getCaseId());
      }
    }
  }

  private static boolean supportedEdition(DnvRpF109OnBottomStabilityInput input) {
    return input.getEdition() != null && input.getEdition().getStandardType() == StandardType.DNV_RP_F109
        && StandardType.DNV_RP_F109.getDefaultVersion().equalsIgnoreCase(input.getEdition().getEdition())
        && input.getEdition().getAmendments().isEmpty();
  }

  private static boolean responseBasisComplete(LoadCase loadCase) {
    return Boolean.TRUE.equals(loadCase.getResponseModelWithinValidatedRange())
        && loadCase.getResponseModelBasis() != null && !loadCase.getResponseModelBasis().trim().isEmpty();
  }

  private static String loadCaseId(LoadCase loadCase) {
    return loadCase == null || loadCase.getCaseId() == null ? "<missing>" : loadCase.getCaseId();
  }

  private static boolean positive(double value) {
    return Double.isFinite(value) && value > 0.0;
  }

  private static boolean nonNegative(double value) {
    return Double.isFinite(value) && value >= 0.0;
  }

  private static boolean atLeastOne(double value) {
    return Double.isFinite(value) && value >= 1.0;
  }

  private static boolean factor(double value) {
    return Double.isFinite(value) && value > 0.0 && value <= 1.0;
  }

  private static boolean assetMatchesEquipment(DnvRpF109OnBottomStabilityInput input) {
    String equipmentType = input.getEquipmentType().trim();
    if (input.getAssetType() == DnvRpF109OnBottomStabilityInput.AssetType.CABLE) {
      return "Cable".equalsIgnoreCase(equipmentType);
    }
    if (input.getAssetType() == DnvRpF109OnBottomStabilityInput.AssetType.UMBILICAL) {
      return "Umbilical".equalsIgnoreCase(equipmentType);
    }
    return "Pipeline".equalsIgnoreCase(equipmentType) || "AdiabaticPipe".equalsIgnoreCase(equipmentType)
        || "PipeBeggsAndBrills".equalsIgnoreCase(equipmentType) || "FlexiblePipe".equalsIgnoreCase(equipmentType);
  }
}
