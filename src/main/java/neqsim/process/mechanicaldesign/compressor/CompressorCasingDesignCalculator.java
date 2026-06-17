package neqsim.process.mechanicaldesign.compressor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Calculator for compressor casing mechanical design per API 617 and ASME Section VIII.
 *
 * <p>
 * Performs detailed casing mechanical analysis including:
 * </p>
 * <ul>
 * <li>Casing wall thickness per ASME Section VIII Div. 1 (UG-27 cylindrical shell formula)</li>
 * <li>Material selection with SMYS/SMTS/allowable stress per ASME II Part D</li>
 * <li>Nozzle load analysis per API 617 Table 3</li>
 * <li>Flange rating verification per ASME B16.5 and B16.47</li>
 * <li>Hydrostatic test pressure per ASME VIII UG-99</li>
 * <li>Corrosion allowance integration</li>
 * <li>NACE MR0175/ISO 15156 material compliance checks</li>
 * <li>Thermal growth and differential expansion analysis</li>
 * <li>Split-line bolt sizing for horizontally-split casings</li>
 * <li>Barrel casing inner/outer barrel sizing</li>
 * </ul>
 *
 * <p>
 * Standards implemented:
 * </p>
 * <ul>
 * <li>API 617 8th Ed. - Axial and Centrifugal Compressors</li>
 * <li>ASME Section VIII Div. 1 - Pressure Vessels (UG-27, UG-99)</li>
 * <li>ASME B16.5 - Pipe Flanges and Flanged Fittings (NPS 1/2 to 24)</li>
 * <li>ASME B16.47 - Large Diameter Steel Flanges (NPS 26 to 60)</li>
 * <li>NACE MR0175/ISO 15156 - Materials for Sour Service</li>
 * <li>ASME II Part D - Material Properties</li>
 * <li>ISO 20816-3 - Vibration Limits for Compressors</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class CompressorCasingDesignCalculator implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  // ============================================================================
  // Input Parameters
  // ============================================================================

  /** Design pressure [MPa]. */
  private double designPressureMPa = 0.0;

  /** Design temperature [C]. */
  private double designTemperatureC = 150.0;

  /** Maximum operating pressure [MPa]. */
  private double maxOperatingPressureMPa = 0.0;

  /** Maximum operating temperature [C]. */
  private double maxOperatingTemperatureC = 100.0;

  /** Minimum operating temperature [C]. */
  private double minOperatingTemperatureC = -29.0;

  /** Ambient temperature [C]. */
  private double ambientTemperatureC = 20.0;

  /** Casing inner diameter [mm]. */
  private double casingInnerDiameterMm = 500.0;

  /** Casing length (bearing span based) [mm]. */
  private double casingLengthMm = 1500.0;

  /** Number of impeller stages in casing. */
  private int numberOfStages = 1;

  /** Material grade for casing. */
  private String materialGrade = "SA-516-70";

  /** Corrosion allowance [mm]. */
  private double corrosionAllowanceMm = 1.5;

  /** Weld joint efficiency per ASME VIII UW-12. */
  private double jointEfficiency = 0.85;

  /** Casing type. */
  private CompressorMechanicalDesign.CasingType casingType =
      CompressorMechanicalDesign.CasingType.HORIZONTALLY_SPLIT;

  /** Whether H2S sour service (NACE MR0175). */
  private boolean sourService = false;

  /** Maximum H2S partial pressure [kPa]. */
  private double h2sPartialPressureKPa = 0.0;

  /** Suction nozzle size [mm NPS]. */
  private double suctionNozzleSizeMm = 200.0;

  /** Discharge nozzle size [mm NPS]. */
  private double dischargeNozzleSizeMm = 150.0;

  /** Shaft power [kW] - for nozzle load estimation. */
  private double shaftPowerKW = 0.0;

  /** Operating speed [rpm]. */
  private double operatingSpeedRPM = 0.0;

  /** Impeller diameter [mm]. */
  private double impellerDiameterMm = 300.0;

  // ============================================================================
  // Material Properties (looked up from grade)
  // ============================================================================

  /** Specified Minimum Yield Strength [MPa]. */
  private double smysMPa = 260.0;

  /** Specified Minimum Tensile Strength [MPa]. */
  private double smtsMPa = 485.0;

  /** Allowable stress at design temperature [MPa]. */
  private double allowableStressMPa = 138.0;

  /** Material density [kg/m3]. */
  private double materialDensity = 7850.0;

  /** Coefficient of thermal expansion [1/C]. */
  private double thermalExpansionCoeff = 1.17e-5;

  /** Elastic modulus [GPa]. */
  private double elasticModulusGPa = 200.0;

  /** Whether material is NACE compliant. */
  private boolean materialIsNaceCompliant = false;

  /** Material type description. */
  private String materialType = "CarbonSteel";

  // ============================================================================
  // Calculated Results - Wall Thickness
  // ============================================================================

  /** Required wall thickness per ASME VIII UG-27 [mm] (before corrosion). */
  private double requiredWallThicknessMm = 0.0;

  /** Minimum wall thickness including corrosion allowance [mm]. */
  private double minimumWallThicknessMm = 0.0;

  /** Selected (nominal) wall thickness [mm]. */
  private double selectedWallThicknessMm = 0.0;

  /** Maximum Allowable Working Pressure [MPa]. */
  private double mawpMPa = 0.0;

  /** Hoop stress at design conditions [MPa]. */
  private double hoopStressMPa = 0.0;

  /** Stress ratio (actual/allowable). */
  private double stressRatio = 0.0;

  // ============================================================================
  // Calculated Results - Hydrostatic Test
  // ============================================================================

  /** Hydrostatic test pressure [MPa]. */
  private double hydroTestPressureMPa = 0.0;

  /** Hydrostatic test factor per ASME VIII UG-99. */
  private double hydroTestFactor = 1.3;

  /** Stress during hydro test [MPa]. */
  private double hydroTestStressMPa = 0.0;

  /** Whether hydro test stress is acceptable. */
  private boolean hydroTestAcceptable = true;

  // ============================================================================
  // Calculated Results - Flange Rating
  // ============================================================================

  /** Selected flange class per ASME B16.5. */
  private int flangeClass = 300;

  /** Flange class pressure rating [barg]. */
  private double flangeRatingBarg = 51.1;

  /** Whether flange rating is adequate. */
  private boolean flangeRatingAdequate = true;

  /** Flange standard used (B16.5 or B16.47). */
  private String flangeStandard = "ASME-B16.5";

  // ============================================================================
  // Calculated Results - Nozzle Loads (API 617 Table 3)
  // ============================================================================

  /** Allowable suction nozzle force [N]. */
  private double suctionNozzleAllowableForceN = 0.0;

  /** Allowable suction nozzle moment [Nm]. */
  private double suctionNozzleAllowableMomentNm = 0.0;

  /** Allowable discharge nozzle force [N]. */
  private double dischargeNozzleAllowableForceN = 0.0;

  /** Allowable discharge nozzle moment [Nm]. */
  private double dischargeNozzleAllowableMomentNm = 0.0;

  /** Nozzle load amplification factor per API 617. */
  private double nozzleLoadFactor = 1.85;

  // ============================================================================
  // Calculated Results - Thermal Growth
  // ============================================================================

  /** Casing axial thermal growth [mm]. */
  private double casingAxialGrowthMm = 0.0;

  /** Casing radial thermal growth [mm]. */
  private double casingRadialGrowthMm = 0.0;

  /** Rotor axial thermal growth [mm]. */
  private double rotorAxialGrowthMm = 0.0;

  /** Differential expansion (casing - rotor) [mm]. */
  private double differentialExpansionMm = 0.0;

  /** Whether differential expansion is acceptable. */
  private boolean thermalGrowthAcceptable = true;

  // ============================================================================
  // Calculated Results - Split-Line Bolts
  // ============================================================================

  /** Required number of split-line bolts. */
  private int splitLineBoltCount = 0;

  /** Split-line bolt diameter [mm]. */
  private double splitLineBoltDiameterMm = 0.0;

  /** Split-line bolt pitch [mm]. */
  private double splitLineBoltPitchMm = 0.0;

  /** Bolt tensile stress [MPa]. */
  private double boltTensileStressMPa = 0.0;

  /** Bolt allowable stress [MPa]. */
  private double boltAllowableStressMPa = 172.0;

  /** Bolt preload force per bolt [N]. */
  private double boltPreloadForceN = 0.0;

  /** Total gasket load [N]. */
  private double totalGasketLoadN = 0.0;

  /** Whether split-line bolt design is adequate. */
  private boolean splitLineBoltsAdequate = true;

  // ============================================================================
  // Calculated Results - Barrel Casing
  // ============================================================================

  /** Barrel outer shell inner diameter [mm]. */
  private double barrelOuterIDMm = 0.0;

  /** Barrel outer shell outer diameter [mm]. */
  private double barrelOuterODMm = 0.0;

  /** Barrel outer shell wall thickness [mm]. */
  private double barrelOuterWallThicknessMm = 0.0;

  /** Barrel inner bundle outer diameter [mm]. */
  private double barrelInnerBundleODMm = 0.0;

  /** Barrel end-cover bolt diameter [mm]. */
  private double barrelEndCoverBoltDiameterMm = 0.0;

  /** Barrel end-cover bolt count. */
  private int barrelEndCoverBoltCount = 0;

  /** Barrel end-cover thickness [mm]. */
  private double barrelEndCoverThicknessMm = 0.0;

  // ============================================================================
  // Calculated Results - NACE Assessment
  // ============================================================================

  /** NACE compliance result: COMPLIANT, NON_COMPLIANT, NOT_APPLICABLE. */
  private String naceComplianceStatus = "NOT_APPLICABLE";

  /** NACE sour service region classification. */
  private String naceRegion = "NONE";

  /** Maximum hardness allowed [HRC] per NACE. */
  private double naceMaxHardnessHRC = 22.0;

  /** List of NACE compliance issues. */
  private List<String> naceIssues = new ArrayList<String>();

  // ============================================================================
  // Tracking
  // ============================================================================

  /** List of applied standards. */
  private List<String> appliedStandards = new ArrayList<String>();

  /** List of design issues/warnings. */
  private List<String> designIssues = new ArrayList<String>();

  // ============================================================================
  // Constructor
  // ============================================================================

  /**
   * Default constructor.
   */
  public CompressorCasingDesignCalculator() {
    // defaults set by field initializers
  }

  // ============================================================================
  // Main Calculation Method
  // ============================================================================

  /**
   * Run all casing design calculations.
   *
   * <p>
   * Executes the full design sequence:
   * </p>
   * <ol>
   * <li>Update material properties from grade lookup</li>
   * <li>Calculate wall thickness per ASME VIII Div. 1 UG-27</li>
   * <li>Calculate MAWP</li>
   * <li>Calculate hydrostatic test pressure per UG-99</li>
   * <li>Verify flange rating per ASME B16.5 / B16.47</li>
   * <li>Calculate nozzle allowable loads per API 617</li>
   * <li>Calculate thermal growth and differential expansion</li>
   * <li>If horizontally-split: calculate split-line bolt design</li>
   * <li>If barrel: calculate outer/inner barrel sizing</li>
   * <li>Perform NACE MR0175 compliance check</li>
   * </ol>
   */
  public void calculate() {
    appliedStandards.clear();
    designIssues.clear();

    updateMaterialProperties();
    calculateWallThickness();
    calculateMAWP();
    calculateHydroTestPressure();
    selectFlangeRating();
    calculateNozzleAllowableLoads();
    calculateThermalGrowth();

    if (casingType == CompressorMechanicalDesign.CasingType.HORIZONTALLY_SPLIT) {
      calculateSplitLineBolts();
    }

    if (casingType == CompressorMechanicalDesign.CasingType.BARREL) {
      calculateBarrelCasing();
    }

    assessNaceCompliance();

    appliedStandards.add("API 617 8th Ed. - Casing Design");
    appliedStandards.add("ASME Section VIII Div. 1 - Pressure Containment");
    appliedStandards.add(flangeStandard + " - Flange Rating");
  }

  // ============================================================================
  // Material Properties
  // ============================================================================

  /**
   * Update material properties from grade lookup using built-in data tables.
   *
   * <p>
   * Looks up SMYS, SMTS, allowable stress, density, thermal expansion, and elastic modulus for the
   * specified material grade from ASME II Part D data.
   * </p>
   */
  private void updateMaterialProperties() {
    // Built-in material data per ASME II Part D
    // Map: grade -> {SMYS_MPa, SMTS_MPa, AllowableStress_MPa, MaxTempC, MinTempC,
    // Density, ThermalExpCoeff, ElasticModulus_GPa, isNACE, materialType}
    if ("SA-516-70".equals(materialGrade)) {
      smysMPa = 260.0;
      smtsMPa = 485.0;
      allowableStressMPa = 138.0;
      materialDensity = 7850.0;
      thermalExpansionCoeff = 1.17e-5;
      elasticModulusGPa = 200.0;
      materialIsNaceCompliant = false;
      materialType = "CarbonSteel";
    } else if ("SA-516-60".equals(materialGrade)) {
      smysMPa = 220.0;
      smtsMPa = 415.0;
      allowableStressMPa = 118.0;
      materialDensity = 7850.0;
      thermalExpansionCoeff = 1.17e-5;
      elasticModulusGPa = 200.0;
      materialIsNaceCompliant = false;
      materialType = "CarbonSteel";
    } else if ("SA-266-Gr2".equals(materialGrade)) {
      smysMPa = 250.0;
      smtsMPa = 485.0;
      allowableStressMPa = 138.0;
      materialDensity = 7850.0;
      thermalExpansionCoeff = 1.17e-5;
      elasticModulusGPa = 200.0;
      materialIsNaceCompliant = false;
      materialType = "CarbonSteelForging";
    } else if ("SA-266-Gr4".equals(materialGrade)) {
      smysMPa = 310.0;
      smtsMPa = 550.0;
      allowableStressMPa = 157.0;
      materialDensity = 7850.0;
      thermalExpansionCoeff = 1.17e-5;
      elasticModulusGPa = 200.0;
      materialIsNaceCompliant = false;
      materialType = "CarbonSteelForging";
    } else if ("SA-350-LF2".equals(materialGrade)) {
      smysMPa = 250.0;
      smtsMPa = 485.0;
      allowableStressMPa = 138.0;
      materialDensity = 7850.0;
      thermalExpansionCoeff = 1.17e-5;
      elasticModulusGPa = 200.0;
      materialIsNaceCompliant = false;
      materialType = "LowAlloyForging";
    } else if ("SA-182-F316L".equals(materialGrade)) {
      smysMPa = 170.0;
      smtsMPa = 485.0;
      allowableStressMPa = 110.0;
      materialDensity = 8000.0;
      thermalExpansionCoeff = 1.60e-5;
      elasticModulusGPa = 193.0;
      materialIsNaceCompliant = true;
      materialType = "AusteniticSS";
    } else if ("SA-182-F304L".equals(materialGrade)) {
      smysMPa = 170.0;
      smtsMPa = 485.0;
      allowableStressMPa = 110.0;
      materialDensity = 8000.0;
      thermalExpansionCoeff = 1.73e-5;
      elasticModulusGPa = 193.0;
      materialIsNaceCompliant = true;
      materialType = "AusteniticSS";
    } else if ("SA-182-F22".equals(materialGrade)) {
      smysMPa = 310.0;
      smtsMPa = 515.0;
      allowableStressMPa = 147.0;
      materialDensity = 7850.0;
      thermalExpansionCoeff = 1.23e-5;
      elasticModulusGPa = 207.0;
      materialIsNaceCompliant = false;
      materialType = "CrMoForging";
    } else if ("Inconel-718".equals(materialGrade)) {
      smysMPa = 1035.0;
      smtsMPa = 1241.0;
      allowableStressMPa = 230.0;
      materialDensity = 8190.0;
      thermalExpansionCoeff = 1.30e-5;
      elasticModulusGPa = 211.0;
      materialIsNaceCompliant = true;
      materialType = "NickelAlloy";
    }
    // Default SA-516-70 properties are used if grade not matched

    // Apply temperature derating for allowable stress
    // ASME II Part D Table 1A: Approximate derating at elevated T
    if (designTemperatureC > 200.0) {
      double deratingFactor = 1.0 - (designTemperatureC - 200.0) * 0.001;
      deratingFactor = Math.max(0.70, deratingFactor);
      allowableStressMPa = allowableStressMPa * deratingFactor;
    }
  }

  // ============================================================================
  // Wall Thickness Calculation - ASME VIII Div.1 UG-27
  // ============================================================================

  /**
   * Calculate required casing wall thickness per ASME Section VIII Div. 1 UG-27.
   *
   * <p>
   * For cylindrical shells under internal pressure:
   * </p>
   * <p>
   * $t = \frac{P \times R}{S \times E - 0.6 \times P}$
   * </p>
   * <p>
   * where P = design pressure, R = inner radius, S = allowable stress, E = joint efficiency.
   * </p>
   */
  private void calculateWallThickness() {
    double pressureMPa = designPressureMPa;
    double innerRadiusMm = casingInnerDiameterMm / 2.0;
    double allowableS = allowableStressMPa;
    double efficiency = jointEfficiency;

    // ASME VIII Div.1 UG-27(c)(1) - Circumferential stress (longitudinal joints)
    double denominator = allowableS * efficiency - 0.6 * pressureMPa;
    if (denominator <= 0) {
      designIssues.add("BLOCKER: Pressure exceeds material capability. "
          + "Select higher strength material or reduce design pressure.");
      requiredWallThicknessMm = casingInnerDiameterMm; // Flag as impossible
      return;
    }

    requiredWallThicknessMm = (pressureMPa * innerRadiusMm) / denominator;

    // Add corrosion allowance
    minimumWallThicknessMm = requiredWallThicknessMm + corrosionAllowanceMm;

    // Apply API 617 minimum 12.7 mm (0.5 inch) for compressor casings
    double api617MinThickness = 12.7;
    minimumWallThicknessMm = Math.max(minimumWallThicknessMm, api617MinThickness);

    // Round up to nearest standard plate thickness (mm)
    selectedWallThicknessMm = roundUpToStandardThickness(minimumWallThicknessMm);

    // Calculate actual hoop stress at selected thickness
    double effectiveThickness = selectedWallThicknessMm - corrosionAllowanceMm;
    if (effectiveThickness > 0) {
      hoopStressMPa = pressureMPa * (innerRadiusMm + 0.6 * effectiveThickness)
          / (efficiency * effectiveThickness);
    }

    stressRatio = hoopStressMPa / allowableStressMPa;

    if (stressRatio > 1.0) {
      designIssues.add("WARNING: Hoop stress ratio " + String.format("%.2f", stressRatio)
          + " exceeds 1.0. Increase wall thickness or select stronger material.");
    }
  }

  /**
   * Round up wall thickness to nearest standard plate thickness.
   *
   * @param thicknessMm minimum required thickness in mm
   * @return next standard thickness in mm
   */
  private double roundUpToStandardThickness(double thicknessMm) {
    // Standard plate thicknesses in mm (metric preferred series)
    double[] standardThicknesses =
        {6.0, 8.0, 10.0, 12.0, 12.7, 14.0, 16.0, 18.0, 20.0, 22.0, 25.0, 28.0, 30.0, 32.0, 35.0,
            38.0, 40.0, 45.0, 50.0, 55.0, 60.0, 65.0, 70.0, 75.0, 80.0, 90.0, 100.0};

    for (double stdThk : standardThicknesses) {
      if (stdThk >= thicknessMm) {
        return stdThk;
      }
    }
    // If beyond standard range, round up to nearest 5mm
    return Math.ceil(thicknessMm / 5.0) * 5.0;
  }

  // ============================================================================
  // MAWP Calculation
  // ============================================================================

  /**
   * Calculate Maximum Allowable Working Pressure (MAWP) per ASME VIII.
   *
   * <p>
   * MAWP is back-calculated from the selected wall thickness. This is the nameplate rating.
   * </p>
   */
  private void calculateMAWP() {
    double effectiveThickness = selectedWallThicknessMm - corrosionAllowanceMm;
    double innerRadiusMm = casingInnerDiameterMm / 2.0;

    if (effectiveThickness <= 0 || innerRadiusMm <= 0) {
      mawpMPa = 0.0;
      return;
    }

    // Inverse of UG-27: P = S*E*t / (R + 0.6*t)
    mawpMPa = (allowableStressMPa * jointEfficiency * effectiveThickness)
        / (innerRadiusMm + 0.6 * effectiveThickness);
  }

  // ============================================================================
  // Hydrostatic Test Pressure - ASME VIII UG-99
  // ============================================================================

  /**
   * Calculate hydrostatic test pressure per ASME VIII UG-99.
   *
   * <p>
   * For Div. 1: Test pressure = 1.3 x MAWP x (stress ratio at test temp / stress ratio at design
   * temp).
   * </p>
   * <p>
   * For API 617 compressors, minimum 1.5 x design pressure is typical.
   * </p>
   */
  private void calculateHydroTestPressure() {
    // ASME VIII UG-99(b): P_test = 1.3 * MAWP * (S_test / S_design)
    // At ambient temperature, S_test >= S_design for most materials
    double stressRatioTestToDesign = 1.0;
    if (designTemperatureC > 100.0) {
      // At elevated temperature, allowable stress is reduced, so ratio > 1
      // Approximate: ratio = S_ambient / S_designTemp
      stressRatioTestToDesign = 1.0 + (designTemperatureC - 100.0) * 0.001;
      stressRatioTestToDesign = Math.min(stressRatioTestToDesign, 1.3);
    }

    hydroTestFactor = 1.3;
    hydroTestPressureMPa = hydroTestFactor * mawpMPa * stressRatioTestToDesign;

    // API 617 typically requires minimum 1.5 x design pressure
    double api617MinTest = 1.5 * designPressureMPa;
    hydroTestPressureMPa = Math.max(hydroTestPressureMPa, api617MinTest);

    // Check stress during hydro test (shall not exceed 90% of SMYS)
    double effectiveThickness = selectedWallThicknessMm - corrosionAllowanceMm;
    double innerRadiusMm = casingInnerDiameterMm / 2.0;
    if (effectiveThickness > 0) {
      hydroTestStressMPa = hydroTestPressureMPa * (innerRadiusMm + 0.6 * effectiveThickness)
          / (jointEfficiency * effectiveThickness);
    }

    double maxHydroStress = 0.90 * smysMPa;
    hydroTestAcceptable = hydroTestStressMPa <= maxHydroStress;

    if (!hydroTestAcceptable) {
      designIssues
          .add("WARNING: Hydrostatic test stress " + String.format("%.1f", hydroTestStressMPa)
              + " MPa exceeds 90% SMYS (" + String.format("%.1f", maxHydroStress) + " MPa).");
    }

    appliedStandards.add("ASME VIII UG-99 - Hydrostatic Test");
  }

  // ============================================================================
  // Flange Rating - ASME B16.5 / B16.47
  // ============================================================================

  /**
   * Select and verify flange rating per ASME B16.5 (NPS 1/2-24) or B16.47 (NPS 26-60).
   *
   * <p>
   * Selects the minimum flange class that exceeds the design pressure at the design temperature.
   * Temperature derating is applied per the ASME B16.5 pressure-temperature tables.
   * </p>
   */
  private void selectFlangeRating() {
    // Determine if we need B16.5 or B16.47
    double maxNozzleSizeMm = Math.max(suctionNozzleSizeMm, dischargeNozzleSizeMm);
    boolean largeBore = maxNozzleSizeMm > 600.0; // > NPS 24

    flangeStandard = largeBore ? "ASME-B16.47" : "ASME-B16.5";

    // ASME B16.5 pressure-temperature ratings at ambient (carbon steel group 1.1)
    // Classes and their ambient pressure ratings [barg]
    int[] classes = {150, 300, 600, 900, 1500, 2500};
    double[] ratingsAtAmbientBarg = {19.6, 51.1, 102.1, 153.0, 255.0, 425.0};

    // Temperature derating factor (approximate Group 1.1 materials)
    double tempDeratingFactor = 1.0;
    if (designTemperatureC > 38.0) {
      // Linear approximation of B16.5 pressure-temperature derating
      tempDeratingFactor = 1.0 - (designTemperatureC - 38.0) * 0.0015;
      tempDeratingFactor = Math.max(0.50, tempDeratingFactor);
    }

    double designPressureBarg = designPressureMPa * 10.0; // MPa to barg

    flangeRatingAdequate = false;
    for (int i = 0; i < classes.length; i++) {
      double derated = ratingsAtAmbientBarg[i] * tempDeratingFactor;
      if (derated >= designPressureBarg) {
        flangeClass = classes[i];
        flangeRatingBarg = derated;
        flangeRatingAdequate = true;
        break;
      }
    }

    if (!flangeRatingAdequate) {
      designIssues.add("BLOCKER: No standard flange class adequate for design pressure "
          + String.format("%.1f", designPressureBarg) + " barg at "
          + String.format("%.0f", designTemperatureC) + " C.");
      flangeClass = 2500;
      flangeRatingBarg = ratingsAtAmbientBarg[5] * tempDeratingFactor;
    }

    appliedStandards.add(flangeStandard + " - Flange Rating Class " + flangeClass);
  }

  // ============================================================================
  // Nozzle Load Analysis - API 617
  // ============================================================================

  /**
   * Calculate allowable nozzle forces and moments per API 617 Table 3.
   *
   * <p>
   * API 617 specifies allowable nozzle loads as a function of nozzle size. For centrifugal
   * compressors, forces are specified for each nozzle connection (suction, discharge, sidestream).
   * </p>
   *
   * <p>
   * Simplified correlation based on API 617 8th Ed. Table 3:
   * </p>
   * <ul>
   * <li>Allowable force scales approximately with D^1.5</li>
   * <li>Allowable moment scales approximately with D^2.5</li>
   * </ul>
   */
  private void calculateNozzleAllowableLoads() {
    // API 617 Table 3 - Allowable nozzle loads for centrifugal compressors
    // Reference values: 200mm (8") nozzle -> F = 8900 N, M = 5400 Nm
    double referenceNozzleMm = 200.0;
    double referenceForceN = 8900.0;
    double referenceMomentNm = 5400.0;

    // Suction nozzle
    double suctionRatio = suctionNozzleSizeMm / referenceNozzleMm;
    suctionNozzleAllowableForceN = referenceForceN * Math.pow(suctionRatio, 1.5);
    suctionNozzleAllowableMomentNm = referenceMomentNm * Math.pow(suctionRatio, 2.5);

    // Discharge nozzle (typically smaller, higher pressure)
    double dischargeRatio = dischargeNozzleSizeMm / referenceNozzleMm;
    dischargeNozzleAllowableForceN = referenceForceN * Math.pow(dischargeRatio, 1.5);
    dischargeNozzleAllowableMomentNm = referenceMomentNm * Math.pow(dischargeRatio, 2.5);

    // Apply amplification factor per API 617
    suctionNozzleAllowableForceN *= nozzleLoadFactor;
    suctionNozzleAllowableMomentNm *= nozzleLoadFactor;
    dischargeNozzleAllowableForceN *= nozzleLoadFactor;
    dischargeNozzleAllowableMomentNm *= nozzleLoadFactor;

    appliedStandards.add("API 617 Table 3 - Nozzle Loads");
  }

  // ============================================================================
  // Thermal Growth and Differential Expansion
  // ============================================================================

  /**
   * Calculate thermal growth and differential expansion between casing and rotor.
   *
   * <p>
   * Thermal growth is critical for compressor alignment. The casing grows from the anchor point
   * (typically drive end bearing), and the rotor grows from its thrust collar. Differential
   * expansion affects internal clearances and must be within acceptable limits.
   * </p>
   *
   * <p>
   * $\Delta L = L \times \alpha \times (T_{operating} - T_{ambient})$
   * </p>
   */
  private void calculateThermalGrowth() {
    double deltaT = maxOperatingTemperatureC - ambientTemperatureC;

    // Casing thermal growth
    casingAxialGrowthMm = casingLengthMm * thermalExpansionCoeff * deltaT;
    casingRadialGrowthMm = (casingInnerDiameterMm / 2.0) * thermalExpansionCoeff * deltaT;

    // Rotor thermal growth (rotor material typically alloy steel = similar CTE)
    // Rotor runs hotter than casing due to gas friction and compression heat
    double rotorThermalCoeff = 1.17e-5; // Alloy steel default
    double rotorDeltaT = deltaT * 1.15; // Rotor ~15% hotter than casing average
    rotorAxialGrowthMm = casingLengthMm * 0.85 * rotorThermalCoeff * rotorDeltaT;

    // Differential expansion (casing - rotor)
    differentialExpansionMm = casingAxialGrowthMm - rotorAxialGrowthMm;

    // Acceptable differential expansion is typically < 2mm for proper seal clearances
    double maxDifferentialMm = 2.0;
    thermalGrowthAcceptable = Math.abs(differentialExpansionMm) < maxDifferentialMm;

    if (!thermalGrowthAcceptable) {
      designIssues.add("WARNING: Differential expansion "
          + String.format("%.2f", Math.abs(differentialExpansionMm)) + " mm exceeds "
          + String.format("%.1f", maxDifferentialMm) + " mm limit. Review seal clearance design.");
    }
  }

  // ============================================================================
  // Split-Line Bolt Calculation (Horizontally-Split Casings)
  // ============================================================================

  /**
   * Calculate split-line bolting for horizontally-split casings.
   *
   * <p>
   * The horizontal joint must resist the internal pressure force tending to separate the casing
   * halves. The bolt load must overcome both the hydrostatic end force and the gasket seating load.
   * </p>
   *
   * <p>
   * The required bolt area is calculated as: $A_{bolt} = \frac{F_{pressure} + F_{gasket}}{S_{bolt}
   * \times N}$
   * </p>
   *
   * <p>
   * where $F_{pressure} = P \times D \times L$ (force on split-line), $F_{gasket}$ = gasket seating
   * force, $S_{bolt}$ = allowable bolt stress, N = number of bolts.
   * </p>
   */
  private void calculateSplitLineBolts() {
    // Pressure force on split line: F = P * projected_area
    // Projected area = casing ID * casing length
    double pressureForceN =
        designPressureMPa * (casingInnerDiameterMm / 1000.0) * (casingLengthMm / 1000.0) * 1e6;

    // Gasket seating force (spiral wound, ~30% of pressure force as rule of thumb)
    totalGasketLoadN = pressureForceN * 0.30;

    // Total required bolt load
    double totalBoltLoadN = pressureForceN + totalGasketLoadN;

    // SA-193 B7 bolt material
    boltAllowableStressMPa = 172.0; // At ambient temperature

    // Temperature derating for bolts
    if (designTemperatureC > 200.0) {
      boltAllowableStressMPa = 172.0 * (1.0 - (designTemperatureC - 200.0) * 0.001);
      boltAllowableStressMPa = Math.max(boltAllowableStressMPa, 120.0);
    }

    // Determine bolt size and count
    // Start with M24 (24mm) and iterate if needed
    double[] standardBoltSizes = {16.0, 20.0, 24.0, 30.0, 36.0, 42.0, 48.0};

    // Bolt pitch should be 2.5-4 x bolt diameter per API 617
    double minPitchFactor = 2.5;
    double maxPitchFactor = 4.0;

    boolean found = false;
    for (double boltSize : standardBoltSizes) {
      // Tensile stress area for metric bolts (approximate: 0.7854 * (d - 0.9382*p)^2)
      double pitch = boltSize <= 24 ? 3.0 : (boltSize <= 36 ? 3.5 : 4.0); // thread pitch
      double tensileArea = 0.7854 * Math.pow(boltSize - 0.9382 * pitch, 2.0); // mm2

      // Bolt pitch along flange
      double boltPitchMm = boltSize * (minPitchFactor + maxPitchFactor) / 2.0;

      // Number of bolts = perimeter / pitch (both sides of split)
      // Total bolt line length = 2 * (casing length + 2 * end cap coverage)
      double boltLineLength = 2.0 * (casingLengthMm + casingInnerDiameterMm * 0.5);
      int numBolts = (int) Math.ceil(boltLineLength / boltPitchMm);
      numBolts = Math.max(numBolts, 8); // Minimum 8 bolts

      // Total bolt area
      double totalBoltArea = numBolts * tensileArea; // mm2

      // Required stress
      double requiredStress = totalBoltLoadN / (totalBoltArea * 1e-6) / 1e6; // MPa

      if (requiredStress <= boltAllowableStressMPa) {
        splitLineBoltDiameterMm = boltSize;
        splitLineBoltCount = numBolts;
        splitLineBoltPitchMm = boltPitchMm;
        boltTensileStressMPa = requiredStress;
        boltPreloadForceN = totalBoltLoadN / numBolts;
        splitLineBoltsAdequate = true;
        found = true;
        break;
      }
    }

    if (!found) {
      splitLineBoltDiameterMm = 48.0;
      splitLineBoltsAdequate = false;
      designIssues
          .add("WARNING: Split-line bolt design may be inadequate. Consider barrel casing.");
    }

    appliedStandards.add("API 617 - Split-Line Bolting");
  }

  // ============================================================================
  // Barrel Casing Design
  // ============================================================================

  /**
   * Calculate barrel casing outer/inner barrel sizing.
   *
   * <p>
   * Barrel casings consist of an outer pressure-containing barrel and an inner bundle (cartridge)
   * that slides out for maintenance. The outer barrel is typically a forged cylinder (SA-266) that
   * avoids the split-line sealing issue at high pressures.
   * </p>
   */
  private void calculateBarrelCasing() {
    // Inner bundle OD = casing ID - clearances (typically 2mm radial clearance)
    barrelInnerBundleODMm = casingInnerDiameterMm - 4.0; // 2mm each side

    // Outer barrel ID = casing ID (same as calculated)
    barrelOuterIDMm = casingInnerDiameterMm;

    // Outer barrel wall thickness = already calculated as selectedWallThickness
    barrelOuterWallThicknessMm = selectedWallThicknessMm;

    // Outer barrel OD
    barrelOuterODMm = barrelOuterIDMm + 2.0 * barrelOuterWallThicknessMm;

    // End cover design - acts as flat head per ASME VIII UG-34
    // t_cover = d * sqrt(C * P / (S * E))
    // C = 0.33 for bolted flat head
    double coverC = 0.33;
    barrelEndCoverThicknessMm = barrelOuterIDMm
        * Math.sqrt(coverC * designPressureMPa / (allowableStressMPa * jointEfficiency));
    barrelEndCoverThicknessMm = Math.max(barrelEndCoverThicknessMm, 25.0); // Min 25mm

    // Round up to standard thickness
    barrelEndCoverThicknessMm = roundUpToStandardThickness(barrelEndCoverThicknessMm);

    // End cover bolting
    // Force on end cover = P * A_cover
    double endCoverForceN =
        designPressureMPa * Math.PI * Math.pow(barrelOuterIDMm / 2.0 / 1000.0, 2.0) * 1e6;

    // Select bolt size (typically M30-M48 for barrel covers)
    double boltSize = 36.0; // M36 default
    double threadPitch = 4.0;
    double tensileArea = 0.7854 * Math.pow(boltSize - 0.9382 * threadPitch, 2.0);

    // Number of bolts on bolt circle
    double boltCircleDiameter = barrelOuterIDMm + barrelOuterWallThicknessMm;
    double boltPitch = boltSize * 3.5;
    barrelEndCoverBoltCount = (int) Math.ceil(Math.PI * boltCircleDiameter / boltPitch);
    barrelEndCoverBoltCount = Math.max(barrelEndCoverBoltCount, 12);

    // Even number of bolts
    if (barrelEndCoverBoltCount % 2 != 0) {
      barrelEndCoverBoltCount++;
    }

    barrelEndCoverBoltDiameterMm = boltSize;

    // Verify bolt stress
    double totalBoltArea = barrelEndCoverBoltCount * tensileArea; // mm2
    double boltStress = endCoverForceN / (totalBoltArea * 1e-6) / 1e6;

    if (boltStress > boltAllowableStressMPa) {
      designIssues.add("WARNING: Barrel end-cover bolt stress " + String.format("%.0f", boltStress)
          + " MPa exceeds allowable " + String.format("%.0f", boltAllowableStressMPa)
          + " MPa. Increase bolt size.");
    }

    appliedStandards.add("ASME VIII UG-34 - Flat Head (Barrel End Cover)");
  }

  // ============================================================================
  // NACE MR0175 / ISO 15156 Compliance
  // ============================================================================

  /**
   * Assess NACE MR0175/ISO 15156 compliance for sour service.
   *
   * <p>
   * NACE MR0175 applies when:
   * </p>
   * <ul>
   * <li>Total pressure &gt; 0.4 MPa abs, AND</li>
   * <li>H2S partial pressure &gt; 0.3 kPa (0.05 psia)</li>
   * </ul>
   *
   * <p>
   * Material requirements per NACE MR0175:
   * </p>
   * <ul>
   * <li>Carbon steel: max 22 HRC hardness, SMYS &lt;= 360 MPa</li>
   * <li>Austenitic SS: generally acceptable (304L, 316L)</li>
   * <li>Duplex SS: acceptable with hardness limits</li>
   * <li>Nickel alloys: Inconel 718 acceptable per NACE</li>
   * </ul>
   */
  private void assessNaceCompliance() {
    naceIssues.clear();

    if (!sourService && h2sPartialPressureKPa <= 0.3) {
      naceComplianceStatus = "NOT_APPLICABLE";
      naceRegion = "NONE";
      return;
    }

    appliedStandards.add("NACE MR0175/ISO 15156 - Sour Service");

    // Determine NACE region based on H2S partial pressure and pH
    // Simplified: assume worst case (Region 3 / SSC Zone)
    if (h2sPartialPressureKPa > 1.0) {
      naceRegion = "SSC_REGION_3";
    } else if (h2sPartialPressureKPa > 0.3) {
      naceRegion = "SSC_REGION_1";
    } else {
      naceRegion = "SSC_REGION_0";
    }

    // Check material compliance
    if (materialIsNaceCompliant) {
      naceComplianceStatus = "COMPLIANT";
    } else {
      // Carbon steels: check hardness and strength limits
      if (materialType.contains("CarbonSteel") || materialType.contains("LowAlloy")) {
        naceMaxHardnessHRC = 22.0;
        if (smysMPa > 360.0) {
          naceIssues.add("Material SMYS " + String.format("%.0f", smysMPa)
              + " MPa exceeds NACE limit of 360 MPa for carbon steel in sour service.");
          naceComplianceStatus = "NON_COMPLIANT";
        } else {
          naceComplianceStatus = "CONDITIONALLY_COMPLIANT";
          naceIssues.add("Carbon steel acceptable if hardness <= 22 HRC "
              + "and proper PWHT applied per NACE MR0175.");
        }
      } else if (materialType.contains("CrMo")) {
        naceMaxHardnessHRC = 22.0;
        if (smysMPa > 520.0) {
          naceComplianceStatus = "NON_COMPLIANT";
          naceIssues.add("CrMo steel SMYS exceeds NACE limit for sour service.");
        } else {
          naceComplianceStatus = "CONDITIONALLY_COMPLIANT";
          naceIssues.add("CrMo acceptable with PWHT and hardness <= 22 HRC.");
        }
      } else {
        naceComplianceStatus = "NON_COMPLIANT";
        naceIssues.add("Material " + materialGrade + " not listed as NACE compliant.");
      }
    }

    if ("NON_COMPLIANT".equals(naceComplianceStatus)) {
      designIssues.add(
          "BLOCKER: Material " + materialGrade + " is not NACE MR0175 compliant for sour service. "
              + "Consider SA-182-F316L, SA-182-F304L, or Inconel-718.");
    }
  }

  // ============================================================================
  // Automatic Material Selection
  // ============================================================================

  /**
   * Automatically select an appropriate casing material based on operating conditions.
   *
   * <p>
   * Selection criteria:
   * </p>
   * <ul>
   * <li>Sour service: NACE-compliant materials (316L, 304L, Inconel-718)</li>
   * <li>Low temperature (&lt; -29C): SA-350-LF2 or austenitic SS</li>
   * <li>High temperature (&gt; 400C): SA-182-F22 or SA-182-F91</li>
   * <li>High pressure (&gt; 100 barg): SA-266-Gr4 forging or barrel casing material</li>
   * <li>Standard service: SA-516-70 (most common)</li>
   * </ul>
   *
   * @return recommended material grade string
   */
  public String recommendMaterial() {
    if (sourService || h2sPartialPressureKPa > 0.3) {
      if (designTemperatureC > 200.0) {
        return "Inconel-718";
      }
      return "SA-182-F316L";
    }

    if (minOperatingTemperatureC < -46.0) {
      return "SA-182-F304L"; // Austenitic for cryogenic
    }
    if (minOperatingTemperatureC < -29.0) {
      return "SA-350-LF2"; // Low temp carbon steel
    }

    if (designTemperatureC > 450.0) {
      return "SA-182-F22"; // CrMo for high temp
    }

    if (designPressureMPa > 10.0) {
      // High pressure - use forging grade
      if (casingType == CompressorMechanicalDesign.CasingType.BARREL) {
        return "SA-266-Gr4";
      }
      return "SA-266-Gr2";
    }

    return "SA-516-70"; // Default - most common
  }

  // ============================================================================
  // Results Export
  // ============================================================================

  /**
   * Convert all results to a map for JSON serialization.
   *
   * @return map of all design results
   */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();

    // Input parameters
    Map<String, Object> inputs = new LinkedHashMap<String, Object>();
    inputs.put("designPressure_MPa", designPressureMPa);
    inputs.put("designPressure_barg", designPressureMPa * 10.0);
    inputs.put("designTemperature_C", designTemperatureC);
    inputs.put("casingInnerDiameter_mm", casingInnerDiameterMm);
    inputs.put("casingLength_mm", casingLengthMm);
    inputs.put("materialGrade", materialGrade);
    inputs.put("corrosionAllowance_mm", corrosionAllowanceMm);
    inputs.put("jointEfficiency", jointEfficiency);
    inputs.put("casingType", casingType != null ? casingType.name() : "UNKNOWN");
    inputs.put("sourService", sourService);
    inputs.put("h2sPartialPressure_kPa", h2sPartialPressureKPa);
    result.put("inputs", inputs);

    // Material properties
    Map<String, Object> material = new LinkedHashMap<String, Object>();
    material.put("grade", materialGrade);
    material.put("type", materialType);
    material.put("SMYS_MPa", smysMPa);
    material.put("SMTS_MPa", smtsMPa);
    material.put("allowableStress_MPa", allowableStressMPa);
    material.put("density_kg_m3", materialDensity);
    material.put("thermalExpansionCoeff_perC", thermalExpansionCoeff);
    material.put("elasticModulus_GPa", elasticModulusGPa);
    material.put("naceCompliant", materialIsNaceCompliant);
    result.put("materialProperties", material);

    // Wall thickness results
    Map<String, Object> wallThk = new LinkedHashMap<String, Object>();
    wallThk.put("requiredThickness_mm", requiredWallThicknessMm);
    wallThk.put("minimumWithCorrosion_mm", minimumWallThicknessMm);
    wallThk.put("selectedThickness_mm", selectedWallThicknessMm);
    wallThk.put("MAWP_MPa", mawpMPa);
    wallThk.put("MAWP_barg", mawpMPa * 10.0);
    wallThk.put("hoopStress_MPa", hoopStressMPa);
    wallThk.put("stressRatio", stressRatio);
    wallThk.put("designCode", "ASME VIII Div.1 UG-27");
    result.put("wallThickness", wallThk);

    // Hydrostatic test
    Map<String, Object> hydro = new LinkedHashMap<String, Object>();
    hydro.put("testPressure_MPa", hydroTestPressureMPa);
    hydro.put("testPressure_barg", hydroTestPressureMPa * 10.0);
    hydro.put("testFactor", hydroTestFactor);
    hydro.put("testStress_MPa", hydroTestStressMPa);
    hydro.put("acceptable", hydroTestAcceptable);
    result.put("hydrostaticTest", hydro);

    // Flange rating
    Map<String, Object> flange = new LinkedHashMap<String, Object>();
    flange.put("standard", flangeStandard);
    flange.put("class", flangeClass);
    flange.put("rating_barg", flangeRatingBarg);
    flange.put("adequate", flangeRatingAdequate);
    result.put("flangeRating", flange);

    // Nozzle loads
    Map<String, Object> nozzle = new LinkedHashMap<String, Object>();
    nozzle.put("suctionNozzleSize_mm", suctionNozzleSizeMm);
    nozzle.put("dischargeNozzleSize_mm", dischargeNozzleSizeMm);
    nozzle.put("suctionAllowableForce_N", suctionNozzleAllowableForceN);
    nozzle.put("suctionAllowableMoment_Nm", suctionNozzleAllowableMomentNm);
    nozzle.put("dischargeAllowableForce_N", dischargeNozzleAllowableForceN);
    nozzle.put("dischargeAllowableMoment_Nm", dischargeNozzleAllowableMomentNm);
    nozzle.put("loadAmplificationFactor", nozzleLoadFactor);
    result.put("nozzleLoads", nozzle);

    // Thermal growth
    Map<String, Object> thermal = new LinkedHashMap<String, Object>();
    thermal.put("casingAxialGrowth_mm", casingAxialGrowthMm);
    thermal.put("casingRadialGrowth_mm", casingRadialGrowthMm);
    thermal.put("rotorAxialGrowth_mm", rotorAxialGrowthMm);
    thermal.put("differentialExpansion_mm", differentialExpansionMm);
    thermal.put("acceptable", thermalGrowthAcceptable);
    result.put("thermalGrowth", thermal);

    // Split-line bolts (if horizontally split)
    if (casingType == CompressorMechanicalDesign.CasingType.HORIZONTALLY_SPLIT) {
      Map<String, Object> bolts = new LinkedHashMap<String, Object>();
      bolts.put("boltCount", splitLineBoltCount);
      bolts.put("boltDiameter_mm", splitLineBoltDiameterMm);
      bolts.put("boltPitch_mm", splitLineBoltPitchMm);
      bolts.put("boltStress_MPa", boltTensileStressMPa);
      bolts.put("boltAllowableStress_MPa", boltAllowableStressMPa);
      bolts.put("preloadPerBolt_N", boltPreloadForceN);
      bolts.put("totalGasketLoad_N", totalGasketLoadN);
      bolts.put("adequate", splitLineBoltsAdequate);
      bolts.put("boltMaterial", "SA-193 B7");
      result.put("splitLineBolts", bolts);
    }

    // Barrel casing (if barrel type)
    if (casingType == CompressorMechanicalDesign.CasingType.BARREL) {
      Map<String, Object> barrel = new LinkedHashMap<String, Object>();
      barrel.put("outerBarrelID_mm", barrelOuterIDMm);
      barrel.put("outerBarrelOD_mm", barrelOuterODMm);
      barrel.put("outerBarrelWallThickness_mm", barrelOuterWallThicknessMm);
      barrel.put("innerBundleOD_mm", barrelInnerBundleODMm);
      barrel.put("endCoverThickness_mm", barrelEndCoverThicknessMm);
      barrel.put("endCoverBoltDiameter_mm", barrelEndCoverBoltDiameterMm);
      barrel.put("endCoverBoltCount", barrelEndCoverBoltCount);
      result.put("barrelCasing", barrel);
    }

    // NACE assessment
    Map<String, Object> nace = new LinkedHashMap<String, Object>();
    nace.put("status", naceComplianceStatus);
    nace.put("region", naceRegion);
    nace.put("maxHardness_HRC", naceMaxHardnessHRC);
    nace.put("issues", naceIssues);
    result.put("naceAssessment", nace);

    // Applied standards
    result.put("appliedStandards", appliedStandards);

    // Design issues
    result.put("designIssues", designIssues);

    return result;
  }

  /**
   * Convert results to JSON string.
   *
   * @return JSON string with all design results
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(toMap());
  }

  // ============================================================================
  // Getters and Setters
  // ============================================================================

  /**
   * Gets the design pressure.
   *
   * @return design pressure in MPa
   */
  public double getDesignPressureMPa() {
    return designPressureMPa;
  }

  /**
   * Sets the design pressure.
   *
   * @param designPressureMPa design pressure in MPa
   */
  public void setDesignPressureMPa(double designPressureMPa) {
    this.designPressureMPa = designPressureMPa;
  }

  /**
   * Sets the design pressure from bara.
   *
   * @param designPressureBara design pressure in bara (barg + 1)
   */
  public void setDesignPressureBara(double designPressureBara) {
    this.designPressureMPa = designPressureBara / 10.0;
  }

  /**
   * Gets the design temperature.
   *
   * @return design temperature in Celsius
   */
  public double getDesignTemperatureC() {
    return designTemperatureC;
  }

  /**
   * Sets the design temperature.
   *
   * @param designTemperatureC design temperature in Celsius
   */
  public void setDesignTemperatureC(double designTemperatureC) {
    this.designTemperatureC = designTemperatureC;
  }

  /**
   * Gets the maximum operating pressure.
   *
   * @return maximum operating pressure in MPa
   */
  public double getMaxOperatingPressureMPa() {
    return maxOperatingPressureMPa;
  }

  /**
   * Sets the maximum operating pressure.
   *
   * @param maxOperatingPressureMPa max operating pressure in MPa
   */
  public void setMaxOperatingPressureMPa(double maxOperatingPressureMPa) {
    this.maxOperatingPressureMPa = maxOperatingPressureMPa;
  }

  /**
   * Gets the maximum operating temperature.
   *
   * @return max operating temperature in Celsius
   */
  public double getMaxOperatingTemperatureC() {
    return maxOperatingTemperatureC;
  }

  /**
   * Sets the maximum operating temperature.
   *
   * @param maxOperatingTemperatureC max operating temperature in Celsius
   */
  public void setMaxOperatingTemperatureC(double maxOperatingTemperatureC) {
    this.maxOperatingTemperatureC = maxOperatingTemperatureC;
  }

  /**
   * Gets the minimum operating temperature.
   *
   * @return min operating temperature in Celsius
   */
  public double getMinOperatingTemperatureC() {
    return minOperatingTemperatureC;
  }

  /**
   * Sets the minimum operating temperature.
   *
   * @param minOperatingTemperatureC min operating temperature in Celsius
   */
  public void setMinOperatingTemperatureC(double minOperatingTemperatureC) {
    this.minOperatingTemperatureC = minOperatingTemperatureC;
  }

  /**
   * Gets the casing inner diameter.
   *
   * @return casing inner diameter in mm
   */
  public double getCasingInnerDiameterMm() {
    return casingInnerDiameterMm;
  }

  /**
   * Sets the casing inner diameter.
   *
   * @param casingInnerDiameterMm casing inner diameter in mm
   */
  public void setCasingInnerDiameterMm(double casingInnerDiameterMm) {
    this.casingInnerDiameterMm = casingInnerDiameterMm;
  }

  /**
   * Gets the casing length.
   *
   * @return casing length in mm
   */
  public double getCasingLengthMm() {
    return casingLengthMm;
  }

  /**
   * Sets the casing length.
   *
   * @param casingLengthMm casing length in mm
   */
  public void setCasingLengthMm(double casingLengthMm) {
    this.casingLengthMm = casingLengthMm;
  }

  /**
   * Gets the material grade.
   *
   * @return material grade string
   */
  public String getMaterialGrade() {
    return materialGrade;
  }

  /**
   * Sets the material grade.
   *
   * @param materialGrade material grade (e.g., "SA-516-70", "SA-182-F316L")
   */
  public void setMaterialGrade(String materialGrade) {
    this.materialGrade = materialGrade;
  }

  /**
   * Gets the corrosion allowance.
   *
   * @return corrosion allowance in mm
   */
  public double getCorrosionAllowanceMm() {
    return corrosionAllowanceMm;
  }

  /**
   * Sets the corrosion allowance.
   *
   * @param corrosionAllowanceMm corrosion allowance in mm
   */
  public void setCorrosionAllowanceMm(double corrosionAllowanceMm) {
    this.corrosionAllowanceMm = corrosionAllowanceMm;
  }

  /**
   * Gets the joint efficiency.
   *
   * @return weld joint efficiency per ASME VIII UW-12
   */
  public double getJointEfficiency() {
    return jointEfficiency;
  }

  /**
   * Sets the joint efficiency.
   *
   * @param jointEfficiency weld joint efficiency (0.0-1.0)
   */
  public void setJointEfficiency(double jointEfficiency) {
    this.jointEfficiency = jointEfficiency;
  }

  /**
   * Gets the casing type.
   *
   * @return casing type enum
   */
  public CompressorMechanicalDesign.CasingType getCasingType() {
    return casingType;
  }

  /**
   * Sets the casing type.
   *
   * @param casingType casing type enum
   */
  public void setCasingType(CompressorMechanicalDesign.CasingType casingType) {
    this.casingType = casingType;
  }

  /**
   * Gets the sour service flag.
   *
   * @return true if sour (H2S) service
   */
  public boolean isSourService() {
    return sourService;
  }

  /**
   * Sets the sour service flag.
   *
   * @param sourService true if sour (H2S) service
   */
  public void setSourService(boolean sourService) {
    this.sourService = sourService;
  }

  /**
   * Gets the H2S partial pressure.
   *
   * @return H2S partial pressure in kPa
   */
  public double getH2sPartialPressureKPa() {
    return h2sPartialPressureKPa;
  }

  /**
   * Sets the H2S partial pressure.
   *
   * @param h2sPartialPressureKPa H2S partial pressure in kPa
   */
  public void setH2sPartialPressureKPa(double h2sPartialPressureKPa) {
    this.h2sPartialPressureKPa = h2sPartialPressureKPa;
  }

  /**
   * Gets the suction nozzle size.
   *
   * @return suction nozzle size in mm
   */
  public double getSuctionNozzleSizeMm() {
    return suctionNozzleSizeMm;
  }

  /**
   * Sets the suction nozzle size.
   *
   * @param suctionNozzleSizeMm suction nozzle size in mm
   */
  public void setSuctionNozzleSizeMm(double suctionNozzleSizeMm) {
    this.suctionNozzleSizeMm = suctionNozzleSizeMm;
  }

  /**
   * Gets the discharge nozzle size.
   *
   * @return discharge nozzle size in mm
   */
  public double getDischargeNozzleSizeMm() {
    return dischargeNozzleSizeMm;
  }

  /**
   * Sets the discharge nozzle size.
   *
   * @param dischargeNozzleSizeMm discharge nozzle size in mm
   */
  public void setDischargeNozzleSizeMm(double dischargeNozzleSizeMm) {
    this.dischargeNozzleSizeMm = dischargeNozzleSizeMm;
  }

  /**
   * Sets the number of stages.
   *
   * @param numberOfStages number of impeller stages
   */
  public void setNumberOfStages(int numberOfStages) {
    this.numberOfStages = numberOfStages;
  }

  /**
   * Sets the impeller diameter.
   *
   * @param impellerDiameterMm impeller diameter in mm
   */
  public void setImpellerDiameterMm(double impellerDiameterMm) {
    this.impellerDiameterMm = impellerDiameterMm;
  }

  /**
   * Sets the ambient temperature.
   *
   * @param ambientTemperatureC ambient temperature in Celsius
   */
  public void setAmbientTemperatureC(double ambientTemperatureC) {
    this.ambientTemperatureC = ambientTemperatureC;
  }

  // --- Result Getters ---

  /**
   * Gets the required wall thickness per ASME VIII.
   *
   * @return required wall thickness in mm (before corrosion allowance)
   */
  public double getRequiredWallThicknessMm() {
    return requiredWallThicknessMm;
  }

  /**
   * Gets the minimum wall thickness including corrosion allowance.
   *
   * @return minimum wall thickness in mm
   */
  public double getMinimumWallThicknessMm() {
    return minimumWallThicknessMm;
  }

  /**
   * Gets the selected (nominal) wall thickness.
   *
   * @return selected wall thickness in mm
   */
  public double getSelectedWallThicknessMm() {
    return selectedWallThicknessMm;
  }

  /**
   * Gets the MAWP.
   *
   * @return Maximum Allowable Working Pressure in MPa
   */
  public double getMawpMPa() {
    return mawpMPa;
  }

  /**
   * Gets the MAWP in barg.
   *
   * @return MAWP in barg
   */
  public double getMawpBarg() {
    return mawpMPa * 10.0;
  }

  /**
   * Gets the hoop stress.
   *
   * @return hoop stress in MPa
   */
  public double getHoopStressMPa() {
    return hoopStressMPa;
  }

  /**
   * Gets the stress ratio.
   *
   * @return stress ratio (actual hoop / allowable)
   */
  public double getStressRatio() {
    return stressRatio;
  }

  /**
   * Gets the hydrostatic test pressure.
   *
   * @return hydrostatic test pressure in MPa
   */
  public double getHydroTestPressureMPa() {
    return hydroTestPressureMPa;
  }

  /**
   * Gets whether hydrostatic test is acceptable.
   *
   * @return true if hydro test stress is acceptable
   */
  public boolean isHydroTestAcceptable() {
    return hydroTestAcceptable;
  }

  /**
   * Gets the flange class.
   *
   * @return ASME B16.5 flange class
   */
  public int getFlangeClass() {
    return flangeClass;
  }

  /**
   * Gets the flange rating.
   *
   * @return flange pressure rating in barg
   */
  public double getFlangeRatingBarg() {
    return flangeRatingBarg;
  }

  /**
   * Gets whether flange rating is adequate.
   *
   * @return true if flange rating exceeds design pressure
   */
  public boolean isFlangeRatingAdequate() {
    return flangeRatingAdequate;
  }

  /**
   * Gets the SMYS.
   *
   * @return specified minimum yield strength in MPa
   */
  public double getSmysMPa() {
    return smysMPa;
  }

  /**
   * Gets the SMTS.
   *
   * @return specified minimum tensile strength in MPa
   */
  public double getSmtsMPa() {
    return smtsMPa;
  }

  /**
   * Gets the allowable stress.
   *
   * @return allowable stress at design temperature in MPa
   */
  public double getAllowableStressMPa() {
    return allowableStressMPa;
  }

  /**
   * Gets the allowable suction nozzle force.
   *
   * @return allowable force in N
   */
  public double getSuctionNozzleAllowableForceN() {
    return suctionNozzleAllowableForceN;
  }

  /**
   * Gets the allowable suction nozzle moment.
   *
   * @return allowable moment in Nm
   */
  public double getSuctionNozzleAllowableMomentNm() {
    return suctionNozzleAllowableMomentNm;
  }

  /**
   * Gets the allowable discharge nozzle force.
   *
   * @return allowable force in N
   */
  public double getDischargeNozzleAllowableForceN() {
    return dischargeNozzleAllowableForceN;
  }

  /**
   * Gets the allowable discharge nozzle moment.
   *
   * @return allowable moment in Nm
   */
  public double getDischargeNozzleAllowableMomentNm() {
    return dischargeNozzleAllowableMomentNm;
  }

  /**
   * Gets the casing axial thermal growth.
   *
   * @return axial growth in mm
   */
  public double getCasingAxialGrowthMm() {
    return casingAxialGrowthMm;
  }

  /**
   * Gets the differential expansion.
   *
   * @return differential expansion (casing - rotor) in mm
   */
  public double getDifferentialExpansionMm() {
    return differentialExpansionMm;
  }

  /**
   * Gets whether thermal growth is acceptable.
   *
   * @return true if differential expansion is within limits
   */
  public boolean isThermalGrowthAcceptable() {
    return thermalGrowthAcceptable;
  }

  /**
   * Gets the split-line bolt count.
   *
   * @return number of split-line bolts
   */
  public int getSplitLineBoltCount() {
    return splitLineBoltCount;
  }

  /**
   * Gets the split-line bolt diameter.
   *
   * @return bolt diameter in mm
   */
  public double getSplitLineBoltDiameterMm() {
    return splitLineBoltDiameterMm;
  }

  /**
   * Gets whether split-line bolts are adequate.
   *
   * @return true if bolt design is adequate
   */
  public boolean isSplitLineBoltsAdequate() {
    return splitLineBoltsAdequate;
  }

  /**
   * Gets the barrel outer shell OD.
   *
   * @return barrel outer OD in mm
   */
  public double getBarrelOuterODMm() {
    return barrelOuterODMm;
  }

  /**
   * Gets the barrel end cover thickness.
   *
   * @return end cover thickness in mm
   */
  public double getBarrelEndCoverThicknessMm() {
    return barrelEndCoverThicknessMm;
  }

  /**
   * Gets the barrel end cover bolt count.
   *
   * @return number of end cover bolts
   */
  public int getBarrelEndCoverBoltCount() {
    return barrelEndCoverBoltCount;
  }

  /**
   * Gets the NACE compliance status.
   *
   * @return NACE status: COMPLIANT, NON_COMPLIANT, CONDITIONALLY_COMPLIANT, NOT_APPLICABLE
   */
  public String getNaceComplianceStatus() {
    return naceComplianceStatus;
  }

  /**
   * Gets the NACE region classification.
   *
   * @return NACE SSC region
   */
  public String getNaceRegion() {
    return naceRegion;
  }

  /**
   * Gets the list of NACE compliance issues.
   *
   * @return list of NACE issues
   */
  public List<String> getNaceIssues() {
    return naceIssues;
  }

  /**
   * Gets the list of applied standards.
   *
   * @return list of applied standard references
   */
  public List<String> getAppliedStandards() {
    return appliedStandards;
  }

  /**
   * Gets the list of design issues.
   *
   * @return list of design issues and warnings
   */
  public List<String> getDesignIssues() {
    return designIssues;
  }

  /**
   * Gets the hydrostatic test pressure in barg.
   *
   * @return hydro test pressure in barg
   */
  public double getHydroTestPressureBarg() {
    return hydroTestPressureMPa * 10.0;
  }

  /**
   * Gets whether the material is NACE compliant.
   *
   * @return true if material is NACE compliant
   */
  public boolean isMaterialNaceCompliant() {
    return materialIsNaceCompliant;
  }

  /**
   * Gets the material type.
   *
   * @return material type description
   */
  public String getMaterialType() {
    return materialType;
  }

  /**
   * Gets the flange standard.
   *
   * @return flange standard (ASME-B16.5 or ASME-B16.47)
   */
  public String getFlangeStandard() {
    return flangeStandard;
  }
}
