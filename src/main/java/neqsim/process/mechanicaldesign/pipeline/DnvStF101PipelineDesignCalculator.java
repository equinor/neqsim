package neqsim.process.mechanicaldesign.pipeline;

import java.util.ArrayList;
import java.util.List;
import neqsim.process.mechanicaldesign.pipeline.DnvStF101LimitStateCheck.LimitState;

/**
 * Pure calculator for transparent DNV-ST-F101 pipeline limit-state screening.
 *
 * <p>
 * The calculator is suitable for option screening and independent implementation review. It is not a clause-complete
 * conformity assessment and does not replace a licensed standard, project design basis, detailed finite-element
 * analysis, installation analysis, or independent verification.
 * </p>
 */
public final class DnvStF101PipelineDesignCalculator {
  private DnvStF101PipelineDesignCalculator() {
    // Utility class.
  }

  /**
   * Calculate all screening checks from a readiness-approved input.
   *
   * @param input complete design input
   * @return immutable screening assessment
   * @throws IllegalArgumentException when a required numeric input is invalid
   */
  public static DnvStF101PipelineAssessment calculate(DnvStF101PipelineDesignInput input) {
    validate(input);

    double diameter = input.getOutsideDiameterM();
    double thickness = input.getNominalWallThicknessM() * (1.0 - input.getFabricationToleranceFraction())
        - input.getCorrosionAllowanceM();
    double deratedSmys = input.getSmysMPa() * input.getStrengthDeratingFactor();
    double deratedSmts = input.getSmtsMPa() * input.getStrengthDeratingFactor();
    double characteristicStrength = Math.min(deratedSmys * input.getSmysStrengthFactor(),
        deratedSmts * input.getSmtsStrengthFactor());
    double gamma = input.getMaterialResistanceFactor() * input.getSafetyClass().getResistanceFactor();

    double burstCharacteristic = 2.0 * thickness * characteristicStrength / (Math.sqrt(3.0) * (diameter - thickness));
    double burstResistance = burstCharacteristic / gamma;
    double operatingDemand = positiveDifference(input.getLocalOperatingPressureMPa(), input.getExternalPressureMPa());
    double incidentalDemand = positiveDifference(input.getLocalIncidentalPressureMPa(), input.getExternalPressureMPa());
    double testDemand = positiveDifference(input.getSystemTestPressureMPa(), input.getTestExternalPressureMPa());

    double elasticCollapse = 2.0 * input.getYoungsModulusMPa()
        / (1.0 - input.getPoissonRatio() * input.getPoissonRatio()) * Math.pow(thickness / diameter, 3.0);
    double plasticCollapse = 2.0 * deratedSmys * input.getFabricationFactor() * thickness / diameter;
    double collapseCharacteristic = solveCollapsePressure(elasticCollapse, plasticCollapse, input.getOvalityFraction(),
        diameter / thickness);
    double collapseResistance = collapseCharacteristic / gamma;
    double externalPressureDemand = positiveDifference(input.getExternalPressureMPa(),
        input.getMinimumInternalPressureMPa());

    double propagationCharacteristic = 35.0 * deratedSmys * input.getFabricationFactor()
        * Math.pow(thickness / diameter, 2.5);
    double propagationResistance = propagationCharacteristic / gamma;

    double pipeInnerDiameter = diameter - 2.0 * thickness;
    double steelArea = Math.PI / 4.0 * (diameter * diameter - pipeInnerDiameter * pipeInnerDiameter);
    double plasticSectionModulus = (Math.pow(diameter, 3.0) - Math.pow(pipeInnerDiameter, 3.0)) / 6.0;
    double axialResistanceKN = steelArea * deratedSmys * 1000.0 / gamma;
    double momentResistanceKNm = plasticSectionModulus * deratedSmys * 1000.0 / gamma;
    double equivalentMomentKNm = Math.hypot(input.getDesignBendingMomentKNm(), input.getDesignTorsionMomentKNm());
    double axialUtilization = Math.abs(input.getDesignAxialForceKN()) / axialResistanceKN;
    double momentUtilization = equivalentMomentKNm / momentResistanceKNm;
    double pressureUtilization = incidentalDemand / burstResistance;
    double loadInteraction = Math.sqrt(axialUtilization * axialUtilization + momentUtilization * momentUtilization
        + pressureUtilization * pressureUtilization);

    double fatigueDamage = calculateFatigueDamage(input);
    double designFatigueDamage = fatigueDamage * input.getFatigueDesignFactor();
    double installationStrain = Math.abs(input.getInstallationAxialStrainFraction())
        + Math.abs(input.getInstallationBendingStrainFraction()) + input.getAccumulatedPlasticStrainFraction();

    List<DnvStF101LimitStateCheck> checks = new ArrayList<DnvStF101LimitStateCheck>();
    checks.add(check(LimitState.OPERATING_PRESSURE_CONTAINMENT, operatingDemand, burstResistance, "MPa",
        "differential-pressure burst screening",
        "Uses characteristic corroded thickness and temperature-derated strength."));
    checks.add(check(LimitState.INCIDENTAL_PRESSURE_CONTAINMENT, incidentalDemand, burstResistance, "MPa",
        "local-incidental-pressure burst screening", "Incidental pressure remains a separate load case."));
    checks.add(check(LimitState.SYSTEM_TEST_PRESSURE_CONTAINMENT, testDemand, burstResistance, "MPa",
        "system-test pressure containment screening",
        "Test pressure and test external pressure are explicit project inputs."));
    checks.add(check(LimitState.EXTERNAL_PRESSURE_COLLAPSE, externalPressureDemand, collapseResistance, "MPa",
        "elastic-plastic collapse interaction", "Ovality and fabrication route factor enter the collapse resistance."));
    checks.add(check(LimitState.PROPAGATION_BUCKLING, externalPressureDemand, propagationResistance, "MPa",
        "propagation-pressure screening", "A failed check requires buckle-arrestor or wall-thickness evaluation."));
    checks.add(check(LimitState.LOCAL_BUCKLING_LOAD_INTERACTION, loadInteraction, 1.0, "ratio",
        "screening interaction envelope", "Combines axial, bending/torsion, and pressure utilizations; clause-level "
            + "load-case analysis remains external."));
    checks.add(check(LimitState.FATIGUE, designFatigueDamage, 1.0, "damage ratio",
        "Palmgren-Miner screening with caller-supplied S-N curve",
        "Curve selection, weld detail, environment, mean stress, and SCF require project review."));
    checks.add(check(LimitState.OVALITY, input.getOvalityFraction(), input.getMaximumAllowableOvalityFraction(),
        "fraction", "ovality screening", "Measured or specified ovality is retained independently of collapse."));
    checks.add(check(LimitState.INSTALLATION_STRAIN, installationStrain, input.getAllowableInstallationStrainFraction(),
        "strain fraction", "installation strain accumulation screening",
        "Axial, bending, and accumulated plastic strain remain explicit inputs."));

    return new DnvStF101PipelineAssessment(input.getEdition().getDisplayName(), thickness, deratedSmys, deratedSmts,
        burstResistance, collapseResistance, propagationResistance, fatigueDamage, installationStrain, checks);
  }

  private static DnvStF101LimitStateCheck check(LimitState limitState, double demand, double resistance, String unit,
      String method, String note) {
    return new DnvStF101LimitStateCheck(limitState, demand, resistance, unit, method, note);
  }

  private static double calculateFatigueDamage(DnvStF101PipelineDesignInput input) {
    double damage = 0.0;
    for (DnvStF101PipelineDesignInput.FatigueBin bin : input.getFatigueSpectrum()) {
      double effectiveStressRange = bin.getStressRangeMPa() * input.getFatigueStressConcentrationFactor();
      double allowableCycles = Math.pow(10.0, input.getFatigueSnLogA())
          / Math.pow(effectiveStressRange, input.getFatigueSnSlope());
      damage += bin.getCycles() / allowableCycles;
    }
    return damage;
  }

  private static double solveCollapsePressure(double elasticPressure, double plasticPressure, double ovality,
      double diameterThicknessRatio) {
    double lower = 0.0;
    double upper = Math.min(elasticPressure, plasticPressure);
    for (int iteration = 0; iteration < 100; iteration++) {
      double candidate = 0.5 * (lower + upper);
      double residual = collapseResidual(candidate, elasticPressure, plasticPressure, ovality, diameterThicknessRatio);
      if (residual > 0.0) {
        lower = candidate;
      } else {
        upper = candidate;
      }
    }
    return 0.5 * (lower + upper);
  }

  private static double collapseResidual(double pressure, double elasticPressure, double plasticPressure,
      double ovality, double diameterThicknessRatio) {
    return (pressure - elasticPressure) * (pressure * pressure - plasticPressure * plasticPressure)
        - pressure * elasticPressure * plasticPressure * ovality * diameterThicknessRatio;
  }

  private static double positiveDifference(double high, double low) {
    return Math.max(0.0, high - low);
  }

  private static void validate(DnvStF101PipelineDesignInput input) {
    if (input == null) {
      throw new IllegalArgumentException("input must not be null");
    }
    if (input.getEdition() == null || input.getSafetyClass() == null || input.getFabricationRoute() == null) {
      throw new IllegalArgumentException("edition, safety class, and fabrication route are required");
    }
    requirePositive(input.getOutsideDiameterM(), "outside diameter");
    requirePositive(input.getNominalWallThicknessM(), "nominal wall thickness");
    requireNonNegative(input.getCorrosionAllowanceM(), "corrosion allowance");
    requireFraction(input.getFabricationToleranceFraction(), "fabrication tolerance");
    double characteristicThickness = input.getNominalWallThicknessM() * (1.0 - input.getFabricationToleranceFraction())
        - input.getCorrosionAllowanceM();
    requirePositive(characteristicThickness, "characteristic wall thickness");
    if (2.0 * characteristicThickness >= input.getOutsideDiameterM()) {
      throw new IllegalArgumentException("characteristic wall thickness must be below half the diameter");
    }
    requirePositive(input.getSmysMPa(), "SMYS");
    requirePositive(input.getSmtsMPa(), "SMTS");
    requirePositive(input.getYoungsModulusMPa(), "Young's modulus");
    if (!Double.isFinite(input.getPoissonRatio()) || input.getPoissonRatio() <= 0.0 || input.getPoissonRatio() >= 0.5) {
      throw new IllegalArgumentException("Poisson ratio must be above zero and below 0.5");
    }
    requirePositiveAtMostOne(input.getStrengthDeratingFactor(), "strength derating factor");
    requirePositiveAtMostOne(input.getSmysStrengthFactor(), "SMYS strength factor");
    requirePositiveAtMostOne(input.getSmtsStrengthFactor(), "SMTS strength factor");
    requireAtLeastOne(input.getMaterialResistanceFactor(), "material resistance factor");
    requirePositiveAtMostOne(input.getFabricationFactor(), "fabrication factor");
    requireFraction(input.getOvalityFraction(), "ovality");
    requirePositive(input.getMaximumAllowableOvalityFraction(), "maximum allowable ovality");
    requireNonNegative(input.getLocalOperatingPressureMPa(), "local operating pressure");
    requireNonNegative(input.getLocalIncidentalPressureMPa(), "local incidental pressure");
    requireNonNegative(input.getExternalPressureMPa(), "external pressure");
    requireNonNegative(input.getMinimumInternalPressureMPa(), "minimum internal pressure");
    requirePositive(input.getSystemTestPressureMPa(), "system test pressure");
    requireNonNegative(input.getTestExternalPressureMPa(), "test external pressure");
    if (input.getLocalIncidentalPressureMPa() < input.getLocalOperatingPressureMPa()) {
      throw new IllegalArgumentException("local incidental pressure must not be below operating pressure");
    }
    requireFinite(input.getDesignAxialForceKN(), "design axial force");
    requireFinite(input.getDesignBendingMomentKNm(), "design bending moment");
    requireFinite(input.getDesignTorsionMomentKNm(), "design torsion moment");
    requireFinite(input.getInstallationAxialStrainFraction(), "installation axial strain");
    requireFinite(input.getInstallationBendingStrainFraction(), "installation bending strain");
    requireNonNegative(input.getAccumulatedPlasticStrainFraction(), "accumulated plastic strain");
    requirePositive(input.getAllowableInstallationStrainFraction(), "allowable installation strain");
    requireFinite(input.getFatigueSnLogA(), "fatigue S-N intercept");
    requirePositive(input.getFatigueSnSlope(), "fatigue S-N slope");
    requirePositive(input.getFatigueStressConcentrationFactor(), "fatigue stress concentration factor");
    requireAtLeastOne(input.getFatigueDesignFactor(), "fatigue design factor");
    if (input.getFatigueSpectrum().isEmpty()) {
      throw new IllegalArgumentException("fatigue spectrum must not be empty");
    }
    for (DnvStF101PipelineDesignInput.FatigueBin bin : input.getFatigueSpectrum()) {
      requirePositive(bin.getStressRangeMPa(), "fatigue stress range");
      requireNonNegative(bin.getCycles(), "fatigue cycle count");
    }
  }

  private static void requirePositive(double value, String name) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(name + " must be finite and positive");
    }
  }

  private static void requireNonNegative(double value, String name) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(name + " must be finite and non-negative");
    }
  }

  private static void requireFraction(double value, String name) {
    if (!Double.isFinite(value) || value < 0.0 || value >= 1.0) {
      throw new IllegalArgumentException(name + " must be in the interval [0, 1)");
    }
  }

  private static void requireFinite(double value, String name) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException(name + " must be finite");
    }
  }

  private static void requireAtLeastOne(double value, String name) {
    if (!Double.isFinite(value) || value < 1.0) {
      throw new IllegalArgumentException(name + " must be finite and at least one");
    }
  }

  private static void requirePositiveAtMostOne(double value, String name) {
    if (!Double.isFinite(value) || value <= 0.0 || value > 1.0) {
      throw new IllegalArgumentException(name + " must be above zero and at most one");
    }
  }
}
