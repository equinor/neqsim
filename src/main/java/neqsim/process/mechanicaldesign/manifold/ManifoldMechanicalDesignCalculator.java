package neqsim.process.mechanicaldesign.manifold;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Calculator for manifold mechanical design based on industry standards.
 *
 * <p>
 * This class provides methods to calculate wall thickness, velocity limits, support design,
 * vibration analysis, and structural analysis for manifolds including:
 * </p>
 * <ul>
 * <li>Topside manifolds (offshore platforms)</li>
 * <li>Onshore manifolds (process facilities)</li>
 * <li>Subsea manifolds (production and injection)</li>
 * </ul>
 *
 * <p>
 * Design standards applied:
 * </p>
 * <ul>
 * <li>ASME B31.3 - Process Piping (topside/onshore)</li>
 * <li>DNV-ST-F101 - Submarine Pipeline Systems (subsea)</li>
 * <li>API RP 14E - Erosional Velocity</li>
 * <li>NORSOK L-002 - Piping System Layout</li>
 * <li>API RP 17A - Subsea Production Systems</li>
 * <li>DNV-RP-F112 - Duplex Stainless Steel</li>
 * </ul>
 *
 * @author ASMF
 * @version 1.0
 */
public class ManifoldMechanicalDesignCalculator implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  // ============ Design Code Constants ============
  /** ASME B31.3 Process Piping. */
  public static final String ASME_B31_3 = "ASME-B31.3";
  /** DNV-ST-F101 Submarine Pipeline Systems. */
  public static final String DNV_ST_F101 = "DNV-ST-F101";
  /** NORSOK L-002 Piping System Layout. */
  public static final String NORSOK_L002 = "NORSOK-L-002";
  /** API RP 17A Subsea Production Systems. */
  public static final String API_RP_17A = "API-RP-17A";

  // ============ Manifold Configuration ============
  /** Manifold location type (TOPSIDE, ONSHORE, SUBSEA). */
  private ManifoldLocation location = ManifoldLocation.TOPSIDE;

  /** Manifold type (PRODUCTION, INJECTION, TEST, PIGGING). */
  private ManifoldType manifoldType = ManifoldType.PRODUCTION;

  /** Number of inlet headers. */
  private int numberOfInlets = 1;

  /** Number of outlet headers. */
  private int numberOfOutlets = 2;

  /** Number of valves on manifold. */
  private int numberOfValves = 4;

  /** Has pig launcher/receiver capability. */
  private boolean hasPigging = false;

  // ============ Geometry Parameters ============
  /** Header pipe outer diameter in meters. */
  private double headerOuterDiameter = 0.3048;

  /** Header pipe wall thickness in meters. */
  private double headerWallThickness = 0.0127;

  /** Branch pipe outer diameter in meters. */
  private double branchOuterDiameter = 0.1524;

  /** Branch pipe wall thickness in meters. */
  private double branchWallThickness = 0.00711;

  /** Header length in meters. */
  private double headerLength = 5.0;

  /** Manifold overall length in meters. */
  private double overallLength = 8.0;

  /** Manifold overall width in meters. */
  private double overallWidth = 4.0;

  /** Manifold overall height in meters. */
  private double overallHeight = 3.0;

  // ============ Design Conditions ============
  /** Design pressure in MPa. */
  private double designPressure = 10.0;

  /** Design temperature in Celsius. */
  private double designTemperature = 60.0;

  /** Minimum design temperature in Celsius. */
  private double minDesignTemperature = -10.0;

  /** Operating pressure in MPa. */
  private double operatingPressure = 8.0;

  /** Test pressure in MPa. */
  private double testPressure = 15.0;

  /** Water depth for subsea in meters. */
  private double waterDepth = 0.0;

  /** External pressure at seabed in MPa. */
  private double externalPressure = 0.0;

  // ============ Material Properties ============
  /** Material grade. */
  private String materialGrade = "A106-B";

  /** Specified Minimum Yield Strength in MPa. */
  private double smys = 241.0;

  /** Specified Minimum Tensile Strength in MPa. */
  private double smts = 414.0;

  /** Young's modulus in GPa. */
  private double youngsModulus = 207.0;

  /** Steel density in kg/m3. */
  private double steelDensity = 7850.0;

  /** Thermal expansion coefficient in 1/°C. */
  private double thermalExpansion = 12e-6;

  // ============ Design Factors ============
  /** Design factor (0.72 typical). */
  private double designFactor = 0.72;

  /** Weld joint efficiency. */
  private double jointEfficiency = 1.0;

  /** Corrosion allowance in meters. */
  private double corrosionAllowance = 0.003;

  /** Fabrication tolerance factor. */
  private double fabricationTolerance = 0.875;

  /** Safety class factor (for subsea). */
  private double safetyClassFactor = 1.138;

  // ============ Velocity Parameters ============
  /** Erosional C-factor. */
  private double erosionalCFactor = 100.0;

  /** Maximum gas velocity in m/s. */
  private double maxGasVelocity = 20.0;

  /** Maximum liquid velocity in m/s. */
  private double maxLiquidVelocity = 5.0;

  /** Maximum multiphase velocity in m/s. */
  private double maxMultiphaseVelocity = 15.0;

  /** Mixture density in kg/m3. */
  private double mixtureDensity = 100.0;

  /** Mass flow rate in kg/s. */
  private double massFlowRate = 10.0;

  /** Liquid volume fraction (0-1). */
  private double liquidFraction = 0.1;

  // ============ Calculated Results ============
  /** Minimum required header wall thickness in meters. */
  private double minHeaderWallThickness = 0.0;

  /** Minimum required branch wall thickness in meters. */
  private double minBranchWallThickness = 0.0;

  /** Hoop stress in header in MPa. */
  private double headerHoopStress = 0.0;

  /** Allowable stress in MPa. */
  private double allowableStress = 0.0;

  /** Header flow velocity in m/s. */
  private double headerVelocity = 0.0;

  /** Branch flow velocity in m/s. */
  private double branchVelocity = 0.0;

  /** Erosional velocity in m/s. */
  private double erosionalVelocity = 0.0;

  /** Reinforcement required at branch connections. */
  private boolean reinforcementRequired = false;

  /** Reinforcement pad thickness in meters. */
  private double reinforcementPadThickness = 0.0;

  /** Total dry weight in kg. */
  private double totalDryWeight = 0.0;

  /** Total submerged weight in kg/m (for subsea). */
  private double submergedWeight = 0.0;

  /** Support spacing in meters. */
  private double supportSpacing = 3.0;

  /** Number of supports required. */
  private int numberOfSupports = 0;

  // ============ Vibration Parameters ============
  /** Acoustic power level in W. */
  private double acousticPowerLevel = 0.0;

  /** AIV likelihood of failure (0-1). */
  private double aivLikelihoodOfFailure = 0.0;

  /** Natural frequency of header in Hz. */
  private double naturalFrequency = 0.0;

  /** Vortex shedding frequency in Hz. */
  private double vortexSheddingFrequency = 0.0;

  // ============ Compliance Flags ============
  /** Velocity check passed. */
  private boolean velocityCheckPassed = true;

  /** Wall thickness check passed. */
  private boolean wallThicknessCheckPassed = true;

  /** Reinforcement check passed. */
  private boolean reinforcementCheckPassed = true;

  /** Vibration check passed. */
  private boolean vibrationCheckPassed = true;

  /** List of applied standards. */
  private List<String> appliedStandards = new ArrayList<String>();

  // ============ ASME B31.3 Material Allowable Stresses (MPa) ============
  private static final Map<String, double[]> ASME_ALLOWABLE_STRESSES;

  // ============ Subsea Material Grades ============
  private static final Map<String, double[]> SUBSEA_MATERIAL_PROPERTIES;

  static {
    // Material -> [Allowable at 20C, 100C, 200C, 300C, 400C]
    ASME_ALLOWABLE_STRESSES = new HashMap<String, double[]>();
    ASME_ALLOWABLE_STRESSES.put("A106-B", new double[] {138.0, 138.0, 138.0, 132.0, 121.0});
    ASME_ALLOWABLE_STRESSES.put("A312-TP316", new double[] {138.0, 115.0, 103.0, 92.0, 84.0});
    ASME_ALLOWABLE_STRESSES.put("A312-TP316L", new double[] {115.0, 103.0, 92.0, 83.0, 76.0});
    ASME_ALLOWABLE_STRESSES.put("A790-S31803", new double[] {207.0, 192.0, 177.0, 165.0, 0.0});
    ASME_ALLOWABLE_STRESSES.put("A790-S32750", new double[] {241.0, 226.0, 211.0, 197.0, 0.0});

    // Subsea grades: [SMYS (MPa), SMTS (MPa), Density (kg/m3)]
    SUBSEA_MATERIAL_PROPERTIES = new HashMap<String, double[]>();
    SUBSEA_MATERIAL_PROPERTIES.put("X52", new double[] {359.0, 455.0, 7850.0});
    SUBSEA_MATERIAL_PROPERTIES.put("X60", new double[] {414.0, 517.0, 7850.0});
    SUBSEA_MATERIAL_PROPERTIES.put("X65", new double[] {448.0, 531.0, 7850.0});
    SUBSEA_MATERIAL_PROPERTIES.put("X70", new double[] {483.0, 565.0, 7850.0});
    SUBSEA_MATERIAL_PROPERTIES.put("22Cr-Duplex", new double[] {450.0, 620.0, 7800.0});
    SUBSEA_MATERIAL_PROPERTIES.put("25Cr-SuperDuplex", new double[] {550.0, 750.0, 7800.0});
    SUBSEA_MATERIAL_PROPERTIES.put("6Mo", new double[] {300.0, 650.0, 8000.0});
    SUBSEA_MATERIAL_PROPERTIES.put("Inconel-625", new double[] {414.0, 827.0, 8440.0});
  }

  /**
   * Manifold location enumeration.
   */
  public enum ManifoldLocation {
    /** Topside on offshore platform. */
    TOPSIDE,
    /** Onshore process facility. */
    ONSHORE,
    /** Subsea on seabed. */
    SUBSEA
  }

  /**
   * Manifold type enumeration.
   */
  public enum ManifoldType {
    /** Production manifold. */
    PRODUCTION,
    /** Injection manifold (water, gas). */
    INJECTION,
    /** Test manifold. */
    TEST,
    /** Pigging manifold. */
    PIGGING,
    /** Distribution manifold. */
    DISTRIBUTION
  }

  /**
   * Default constructor.
   */
  public ManifoldMechanicalDesignCalculator() {}

  // ============================================================================
  // WALL THICKNESS CALCULATIONS
  // ============================================================================

  /**
   * Calculate minimum wall thickness based on location and design code.
   *
   * @return minimum wall thickness in meters
   */
  public double calculateMinimumWallThickness() {
    switch (location) {
      case SUBSEA:
        return calculateWallThicknessDNV();
      case TOPSIDE:
      case ONSHORE:
      default:
        return calculateWallThicknessASME();
    }
  }

  /**
   * Calculate wall thickness per ASME B31.3.
   *
   * @return minimum wall thickness in meters
   */
  public double calculateWallThicknessASME() {
    calculateAllowableStress();

    double P = designPressure;
    double D = headerOuterDiameter;
    double S = allowableStress;
    double E = jointEfficiency;
    double Y = 0.4; // Coefficient for ferritic steel below 482C

    // ASME B31.3 Eq. 3a
    double tm = (P * D) / (2 * (S * E + P * Y));
    double tmin = tm / fabricationTolerance + corrosionAllowance;

    minHeaderWallThickness = tmin;
    appliedStandards.add("ASME B31.3 - Wall Thickness");
    return minHeaderWallThickness;
  }

  /**
   * Calculate wall thickness per DNV-ST-F101 for subsea.
   *
   * @return minimum wall thickness in meters
   */
  public double calculateWallThicknessDNV() {
    // Calculate external pressure at seabed
    externalPressure = waterDepth * 1025 * 9.81 / 1e6; // MPa

    double P = designPressure;
    double D = headerOuterDiameter;
    double fy = smys;
    double gammaM = 1.15; // Material resistance factor
    double gammaSC = safetyClassFactor;
    double alphaU = 1.0;

    // DNV-ST-F101 pressure containment
    double t1 = (P * D) / (2 * fy * alphaU / (gammaM * gammaSC));

    // Add corrosion and fabrication allowances
    double tmin = t1 / fabricationTolerance + corrosionAllowance;

    // Check minimum handling thickness
    tmin = Math.max(tmin, 0.00635); // 6.35mm min per DNV

    minHeaderWallThickness = tmin;
    appliedStandards.add("DNV-ST-F101 - Wall Thickness");
    return minHeaderWallThickness;
  }

  /**
   * Calculate branch pipe wall thickness.
   *
   * @return minimum branch wall thickness in meters
   */
  public double calculateBranchWallThickness() {
    double savedOD = headerOuterDiameter;
    headerOuterDiameter = branchOuterDiameter;

    double thickness = calculateMinimumWallThickness();

    headerOuterDiameter = savedOD;
    minBranchWallThickness = thickness;

    return minBranchWallThickness;
  }

  // ============================================================================
  // REINFORCEMENT CALCULATIONS
  // ============================================================================

  /**
   * Calculate branch connection reinforcement per ASME B31.3.
   *
   * @return required reinforcement area in m2
   */
  public double calculateBranchReinforcement() {
    double Dh = headerOuterDiameter;
    double th = headerWallThickness;
    double Db = branchOuterDiameter;
    double tb = branchWallThickness;

    // Required thickness
    double trh = minHeaderWallThickness;
    double trb = minBranchWallThickness;

    // Area replacement per ASME B31.3
    // A1 = Area removed = (Db - 2*tb) * th
    double areaRemoved = (Db - 2 * tb) * th;

    // A2 = Excess area in header = (2 * d2 + tb) * (th - trh - c)
    double d2 = Math.min(Db / 2.0, th + 2.5 * tb);
    double excessHeader = (2 * d2 + tb) * Math.max(0, th - trh - corrosionAllowance);

    // A3 = Excess area in branch = 2 * L4 * (tb - trb - c)
    double L4 = 2.5 * tb;
    double excessBranch = 2 * L4 * Math.max(0, tb - trb - corrosionAllowance);

    double totalExcess = excessHeader + excessBranch;

    if (totalExcess < areaRemoved) {
      reinforcementRequired = true;
      double deficiency = areaRemoved - totalExcess;
      // Pad thickness = deficiency / (2 * d2)
      reinforcementPadThickness = deficiency / (2 * d2);
    } else {
      reinforcementRequired = false;
      reinforcementPadThickness = 0.0;
    }

    reinforcementCheckPassed = !reinforcementRequired || reinforcementPadThickness < 0.025;
    appliedStandards.add("ASME B31.3 - Branch Reinforcement");

    return reinforcementRequired ? reinforcementPadThickness : 0.0;
  }

  // ============================================================================
  // VELOCITY CALCULATIONS
  // ============================================================================

  /**
   * Calculate erosional velocity per API RP 14E.
   *
   * @return erosional velocity in m/s
   */
  public double calculateErosionalVelocity() {
    if (mixtureDensity <= 0) {
      mixtureDensity = 1.0;
    }
    erosionalVelocity = erosionalCFactor / Math.sqrt(mixtureDensity);
    appliedStandards.add("API RP 14E - Erosional Velocity");
    return erosionalVelocity;
  }

  /**
   * Calculate header flow velocity.
   *
   * @return header velocity in m/s
   */
  public double calculateHeaderVelocity() {
    double id = headerOuterDiameter - 2 * headerWallThickness;
    double area = Math.PI * id * id / 4.0;
    headerVelocity = massFlowRate / (mixtureDensity * area);
    return headerVelocity;
  }

  /**
   * Calculate branch flow velocity.
   *
   * @return branch velocity in m/s (per branch)
   */
  public double calculateBranchVelocity() {
    double id = branchOuterDiameter - 2 * branchWallThickness;
    double area = Math.PI * id * id / 4.0;
    double flowPerBranch = massFlowRate / numberOfOutlets;
    branchVelocity = flowPerBranch / (mixtureDensity * area);
    return branchVelocity;
  }

  /**
   * Check velocity limits.
   *
   * @return true if velocities are acceptable
   */
  public boolean checkVelocityLimits() {
    calculateErosionalVelocity();
    calculateHeaderVelocity();
    calculateBranchVelocity();

    velocityCheckPassed = true;

    double maxVel;
    if (liquidFraction < 0.01) {
      maxVel = Math.min(0.8 * erosionalVelocity, maxGasVelocity);
    } else if (liquidFraction > 0.99) {
      maxVel = Math.min(0.8 * erosionalVelocity, maxLiquidVelocity);
    } else {
      maxVel = Math.min(0.8 * erosionalVelocity, maxMultiphaseVelocity);
    }

    if (headerVelocity > maxVel || branchVelocity > maxVel) {
      velocityCheckPassed = false;
    }

    return velocityCheckPassed;
  }

  // ============================================================================
  // STRESS CALCULATIONS
  // ============================================================================

  /**
   * Calculate allowable stress.
   *
   * @return allowable stress in MPa
   */
  public double calculateAllowableStress() {
    if (location == ManifoldLocation.SUBSEA) {
      // For subsea, use SMYS-based design
      allowableStress = smys / safetyClassFactor;
    } else {
      // For topside/onshore, use ASME B31.3
      double[] stresses = ASME_ALLOWABLE_STRESSES.get(materialGrade);
      if (stresses == null) {
        stresses = ASME_ALLOWABLE_STRESSES.get("A106-B");
      }

      double temp = designTemperature;
      if (temp <= 20) {
        allowableStress = stresses[0];
      } else if (temp <= 100) {
        allowableStress = stresses[0] + (stresses[1] - stresses[0]) * (temp - 20) / 80;
      } else if (temp <= 200) {
        allowableStress = stresses[1] + (stresses[2] - stresses[1]) * (temp - 100) / 100;
      } else if (temp <= 300) {
        allowableStress = stresses[2] + (stresses[3] - stresses[2]) * (temp - 200) / 100;
      } else {
        allowableStress = stresses[3] + (stresses[4] - stresses[3]) * (temp - 300) / 100;
      }
    }

    appliedStandards.add("ASME B31.3 Table A-1 - Allowable Stress");
    return allowableStress;
  }

  /**
   * Calculate hoop stress in header.
   *
   * @return hoop stress in MPa
   */
  public double calculateHoopStress() {
    double P = designPressure;
    double D = headerOuterDiameter;
    double t = headerWallThickness - corrosionAllowance;

    headerHoopStress = P * D / (2 * t);
    return headerHoopStress;
  }

  // ============================================================================
  // SUPPORT CALCULATIONS
  // ============================================================================

  /**
   * Calculate support spacing for manifold.
   *
   * @return recommended support spacing in meters
   */
  public double calculateSupportSpacing() {
    if (location == ManifoldLocation.SUBSEA) {
      // Subsea manifolds typically on dedicated structure
      supportSpacing = overallLength;
      numberOfSupports = 1;
    } else {
      // Topside/onshore - per NORSOK L-002
      double od = headerOuterDiameter;

      if (od <= 0.1143) {
        supportSpacing = 2.7;
      } else if (od <= 0.2191) {
        supportSpacing = 3.7;
      } else if (od <= 0.3239) {
        supportSpacing = 4.3;
      } else {
        supportSpacing = 5.0;
      }

      numberOfSupports = (int) Math.ceil(headerLength / supportSpacing) + 1;
    }

    appliedStandards.add("NORSOK L-002 - Support Spacing");
    return supportSpacing;
  }

  // ============================================================================
  // VIBRATION CALCULATIONS
  // ============================================================================

  /**
   * Calculate acoustic power level for AIV screening.
   *
   * @param upstreamPressure upstream pressure in bara
   * @param downstreamPressure downstream pressure in bara
   * @return acoustic power level in W
   */
  public double calculateAcousticPowerLevel(double upstreamPressure, double downstreamPressure) {
    double p1 = upstreamPressure * 1e5;
    double p2 = downstreamPressure * 1e5;
    double tempK = designTemperature + 273.15;
    double pressureRatio = Math.min(1.0, Math.max(0, (p1 - p2) / p1));

    acousticPowerLevel =
        3.2e-9 * massFlowRate * p1 * Math.pow(pressureRatio, 3.6) * Math.pow(tempK / 273.15, 0.8);

    appliedStandards.add("Energy Institute Guidelines - AIV");
    return acousticPowerLevel;
  }

  /**
   * Calculate natural frequency of header pipe.
   *
   * @return natural frequency in Hz
   */
  public double calculateNaturalFrequency() {
    double D = headerOuterDiameter;
    double t = headerWallThickness;
    double id = D - 2 * t;

    double I = Math.PI / 64.0 * (Math.pow(D, 4) - Math.pow(id, 4));
    double area = Math.PI / 4.0 * (D * D - id * id);
    double massPerMeter = steelDensity * area + mixtureDensity * Math.PI * id * id / 4.0;

    double E = youngsModulus * 1e9;
    double L = supportSpacing;

    naturalFrequency = (Math.PI / 2.0) * Math.sqrt(E * I / (massPerMeter * Math.pow(L, 4)));

    return naturalFrequency;
  }

  // ============================================================================
  // WEIGHT CALCULATIONS
  // ============================================================================

  /**
   * Calculate total dry weight of manifold.
   *
   * @return dry weight in kg
   */
  public double calculateDryWeight() {
    // Header weight
    double D = headerOuterDiameter;
    double t = headerWallThickness;
    double id = D - 2 * t;
    double headerArea = Math.PI / 4.0 * (D * D - id * id);
    double headerWeight = headerArea * headerLength * steelDensity;

    // Branch weights
    double Db = branchOuterDiameter;
    double tb = branchWallThickness;
    double idb = Db - 2 * tb;
    double branchArea = Math.PI / 4.0 * (Db * Db - idb * idb);
    double branchLength = 1.5; // Assume 1.5m per branch
    double branchWeight =
        branchArea * branchLength * steelDensity * (numberOfInlets + numberOfOutlets);

    // Valve weights (estimate based on size)
    double valveWeight = numberOfValves * 50.0 * Math.pow(D / 0.1, 2);

    // Structure weight (20% of pipe weight for subsea, 10% for topside)
    double structureFactor = (location == ManifoldLocation.SUBSEA) ? 0.20 : 0.10;
    double structureWeight = (headerWeight + branchWeight) * structureFactor;

    totalDryWeight = headerWeight + branchWeight + valveWeight + structureWeight;

    // Add reinforcement pads if required
    if (reinforcementRequired) {
      int numBranches = numberOfInlets + numberOfOutlets;
      double padWeight = numBranches * Math.PI * branchOuterDiameter * reinforcementPadThickness
          * branchOuterDiameter * steelDensity;
      totalDryWeight += padWeight;
    }

    return totalDryWeight;
  }

  /**
   * Calculate submerged weight for subsea manifold.
   *
   * @return submerged weight in kg
   */
  public double calculateSubmergedWeight() {
    if (location != ManifoldLocation.SUBSEA) {
      submergedWeight = 0.0;
      return submergedWeight;
    }

    calculateDryWeight();

    // Calculate displaced volume
    double structureVolume = overallLength * overallWidth * overallHeight * 0.1; // 10% solid
    double buoyancy = structureVolume * 1025; // Seawater density

    submergedWeight = totalDryWeight - buoyancy;
    return submergedWeight;
  }

  // ============================================================================
  // DESIGN VERIFICATION
  // ============================================================================

  /**
   * Perform complete design verification.
   *
   * @return true if all checks pass
   */
  public boolean performDesignVerification() {
    boolean allPassed = true;

    // Wall thickness check
    calculateMinimumWallThickness();
    calculateBranchWallThickness();
    wallThicknessCheckPassed = headerWallThickness >= minHeaderWallThickness
        && branchWallThickness >= minBranchWallThickness;
    allPassed = allPassed && wallThicknessCheckPassed;

    // Velocity check
    allPassed = allPassed && checkVelocityLimits();

    // Reinforcement check
    calculateBranchReinforcement();
    allPassed = allPassed && reinforcementCheckPassed;

    // Support and weight calculations
    calculateSupportSpacing();
    calculateDryWeight();

    if (location == ManifoldLocation.SUBSEA) {
      calculateSubmergedWeight();
    }

    return allPassed;
  }

  // ============================================================================
  // JSON OUTPUT
  // ============================================================================

  /**
   * Convert results to Map for JSON serialization.
   *
   * @return map of all calculated values
   */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new HashMap<String, Object>();

    // Configuration
    Map<String, Object> config = new HashMap<String, Object>();
    config.put("location", location.name());
    config.put("manifoldType", manifoldType.name());
    config.put("numberOfInlets", numberOfInlets);
    config.put("numberOfOutlets", numberOfOutlets);
    config.put("numberOfValves", numberOfValves);
    config.put("hasPigging", hasPigging);
    result.put("configuration", config);

    // Geometry
    Map<String, Object> geometry = new HashMap<String, Object>();
    geometry.put("headerOuterDiameter_m", headerOuterDiameter);
    geometry.put("headerWallThickness_m", headerWallThickness);
    geometry.put("branchOuterDiameter_m", branchOuterDiameter);
    geometry.put("branchWallThickness_m", branchWallThickness);
    geometry.put("headerLength_m", headerLength);
    geometry.put("overallLength_m", overallLength);
    geometry.put("overallWidth_m", overallWidth);
    geometry.put("overallHeight_m", overallHeight);
    result.put("geometry", geometry);

    // Design conditions
    Map<String, Object> conditions = new HashMap<String, Object>();
    conditions.put("designPressure_MPa", designPressure);
    conditions.put("designTemperature_C", designTemperature);
    conditions.put("operatingPressure_MPa", operatingPressure);
    conditions.put("testPressure_MPa", testPressure);
    conditions.put("waterDepth_m", waterDepth);
    result.put("designConditions", conditions);

    // Material
    Map<String, Object> material = new HashMap<String, Object>();
    material.put("materialGrade", materialGrade);
    material.put("smys_MPa", smys);
    material.put("smts_MPa", smts);
    material.put("corrosionAllowance_m", corrosionAllowance);
    result.put("material", material);

    // Wall thickness results
    Map<String, Object> wallThickness = new HashMap<String, Object>();
    wallThickness.put("minHeaderWallThickness_m", minHeaderWallThickness);
    wallThickness.put("minBranchWallThickness_m", minBranchWallThickness);
    wallThickness.put("actualHeaderThickness_m", headerWallThickness);
    wallThickness.put("actualBranchThickness_m", branchWallThickness);
    wallThickness.put("wallThicknessCheckPassed", wallThicknessCheckPassed);
    result.put("wallThicknessAnalysis", wallThickness);

    // Velocity results
    Map<String, Object> velocity = new HashMap<String, Object>();
    velocity.put("headerVelocity_m_s", headerVelocity);
    velocity.put("branchVelocity_m_s", branchVelocity);
    velocity.put("erosionalVelocity_m_s", erosionalVelocity);
    velocity.put("maxAllowedVelocity_m_s",
        liquidFraction < 0.01 ? maxGasVelocity : maxMultiphaseVelocity);
    velocity.put("velocityCheckPassed", velocityCheckPassed);
    result.put("velocityAnalysis", velocity);

    // Reinforcement results
    Map<String, Object> reinforcement = new HashMap<String, Object>();
    reinforcement.put("reinforcementRequired", reinforcementRequired);
    reinforcement.put("reinforcementPadThickness_m", reinforcementPadThickness);
    reinforcement.put("reinforcementCheckPassed", reinforcementCheckPassed);
    result.put("reinforcementAnalysis", reinforcement);

    // Weight results
    Map<String, Object> weight = new HashMap<String, Object>();
    weight.put("totalDryWeight_kg", totalDryWeight);
    weight.put("submergedWeight_kg", submergedWeight);
    result.put("weightAnalysis", weight);

    // Support results
    Map<String, Object> support = new HashMap<String, Object>();
    support.put("supportSpacing_m", supportSpacing);
    support.put("numberOfSupports", numberOfSupports);
    result.put("supportAnalysis", support);

    result.put("appliedStandards", appliedStandards);

    return result;
  }

  /**
   * Convert to JSON string.
   *
   * @return JSON string
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(toMap());
  }

  // ============================================================================
  // GETTERS AND SETTERS
  // ============================================================================

  public ManifoldLocation getLocation() {
    return location;
  }

  public void setLocation(ManifoldLocation location) {
    this.location = location;
  }

  public ManifoldType getManifoldType() {
    return manifoldType;
  }

  public void setManifoldType(ManifoldType manifoldType) {
    this.manifoldType = manifoldType;
  }

  public int getNumberOfInlets() {
    return numberOfInlets;
  }

  public void setNumberOfInlets(int numberOfInlets) {
    this.numberOfInlets = numberOfInlets;
  }

  public int getNumberOfOutlets() {
    return numberOfOutlets;
  }

  public void setNumberOfOutlets(int numberOfOutlets) {
    this.numberOfOutlets = numberOfOutlets;
  }

  public int getNumberOfValves() {
    return numberOfValves;
  }

  public void setNumberOfValves(int numberOfValves) {
    this.numberOfValves = numberOfValves;
  }

  public double getHeaderOuterDiameter() {
    return headerOuterDiameter;
  }

  public void setHeaderOuterDiameter(double headerOuterDiameter) {
    this.headerOuterDiameter = headerOuterDiameter;
  }

  public double getHeaderWallThickness() {
    return headerWallThickness;
  }

  public void setHeaderWallThickness(double headerWallThickness) {
    this.headerWallThickness = headerWallThickness;
  }

  public double getBranchOuterDiameter() {
    return branchOuterDiameter;
  }

  public void setBranchOuterDiameter(double branchOuterDiameter) {
    this.branchOuterDiameter = branchOuterDiameter;
  }

  public double getBranchWallThickness() {
    return branchWallThickness;
  }

  public void setBranchWallThickness(double branchWallThickness) {
    this.branchWallThickness = branchWallThickness;
  }

  public double getDesignPressure() {
    return designPressure;
  }

  public void setDesignPressure(double designPressure) {
    this.designPressure = designPressure;
  }

  public double getDesignTemperature() {
    return designTemperature;
  }

  public void setDesignTemperature(double designTemperature) {
    this.designTemperature = designTemperature;
  }

  public double getWaterDepth() {
    return waterDepth;
  }

  public void setWaterDepth(double waterDepth) {
    this.waterDepth = waterDepth;
  }

  public String getMaterialGrade() {
    return materialGrade;
  }

  public void setMaterialGrade(String materialGrade) {
    this.materialGrade = materialGrade;
    double[] props = SUBSEA_MATERIAL_PROPERTIES.get(materialGrade);
    if (props != null) {
      this.smys = props[0];
      this.smts = props[1];
      this.steelDensity = props[2];
    }
  }

  public double getSmys() {
    return smys;
  }

  public void setSmys(double smys) {
    this.smys = smys;
  }

  public double getSmts() {
    return smts;
  }

  public void setSmts(double smts) {
    this.smts = smts;
  }

  public double getDesignFactor() {
    return designFactor;
  }

  public void setDesignFactor(double designFactor) {
    this.designFactor = designFactor;
  }

  public double getJointEfficiency() {
    return jointEfficiency;
  }

  public void setJointEfficiency(double jointEfficiency) {
    this.jointEfficiency = jointEfficiency;
  }

  public double getCorrosionAllowance() {
    return corrosionAllowance;
  }

  public void setCorrosionAllowance(double corrosionAllowance) {
    this.corrosionAllowance = corrosionAllowance;
  }

  public double getErosionalCFactor() {
    return erosionalCFactor;
  }

  public void setErosionalCFactor(double erosionalCFactor) {
    this.erosionalCFactor = erosionalCFactor;
  }

  public double getMassFlowRate() {
    return massFlowRate;
  }

  public void setMassFlowRate(double massFlowRate) {
    this.massFlowRate = massFlowRate;
  }

  public double getMixtureDensity() {
    return mixtureDensity;
  }

  public void setMixtureDensity(double mixtureDensity) {
    this.mixtureDensity = mixtureDensity;
  }

  public double getLiquidFraction() {
    return liquidFraction;
  }

  public void setLiquidFraction(double liquidFraction) {
    this.liquidFraction = liquidFraction;
  }

  public double getHeaderLength() {
    return headerLength;
  }

  public void setHeaderLength(double headerLength) {
    this.headerLength = headerLength;
  }

  public double getOverallLength() {
    return overallLength;
  }

  public void setOverallLength(double overallLength) {
    this.overallLength = overallLength;
  }

  public double getOverallWidth() {
    return overallWidth;
  }

  public void setOverallWidth(double overallWidth) {
    this.overallWidth = overallWidth;
  }

  public double getOverallHeight() {
    return overallHeight;
  }

  public void setOverallHeight(double overallHeight) {
    this.overallHeight = overallHeight;
  }

  public double getMinHeaderWallThickness() {
    return minHeaderWallThickness;
  }

  public double getMinBranchWallThickness() {
    return minBranchWallThickness;
  }

  public double getAllowableStress() {
    return allowableStress;
  }

  public double getHeaderVelocity() {
    return headerVelocity;
  }

  public double getBranchVelocity() {
    return branchVelocity;
  }

  public double getErosionalVelocity() {
    return erosionalVelocity;
  }

  public boolean isVelocityCheckPassed() {
    return velocityCheckPassed;
  }

  public boolean isWallThicknessCheckPassed() {
    return wallThicknessCheckPassed;
  }

  public boolean isReinforcementCheckPassed() {
    return reinforcementCheckPassed;
  }

  public boolean isReinforcementRequired() {
    return reinforcementRequired;
  }

  public double getReinforcementPadThickness() {
    return reinforcementPadThickness;
  }

  public double getTotalDryWeight() {
    return totalDryWeight;
  }

  public double getSubmergedWeight() {
    return submergedWeight;
  }

  public double getSupportSpacing() {
    return supportSpacing;
  }

  public int getNumberOfSupports() {
    return numberOfSupports;
  }

  public List<String> getAppliedStandards() {
    return appliedStandards;
  }

  public void setSafetyClassFactor(double safetyClassFactor) {
    this.safetyClassFactor = safetyClassFactor;
  }

  public double getSafetyClassFactor() {
    return safetyClassFactor;
  }

  public void setHasPigging(boolean hasPigging) {
    this.hasPigging = hasPigging;
  }

  public boolean isHasPigging() {
    return hasPigging;
  }
}
