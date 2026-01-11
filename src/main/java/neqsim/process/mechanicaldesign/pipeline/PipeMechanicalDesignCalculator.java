package neqsim.process.mechanicaldesign.pipeline;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Calculator for pipeline mechanical design based on industry standards.
 *
 * <p>
 * This class provides methods to calculate wall thickness, pressure ratings, and stress analysis
 * according to various pipeline design codes including:
 * </p>
 * <ul>
 * <li>ASME B31.3 - Process Piping</li>
 * <li>ASME B31.4 - Pipeline Transportation of Liquids</li>
 * <li>ASME B31.8 - Gas Transmission and Distribution</li>
 * <li>DNV-OS-F101 - Submarine Pipeline Systems</li>
 * <li>API 5L - Line Pipe Specifications</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * 
 * <pre>{@code
 * PipeMechanicalDesignCalculator calc = new PipeMechanicalDesignCalculator();
 * calc.setDesignPressure(150.0); // bara
 * calc.setDesignTemperature(80.0); // Celsius
 * calc.setOuterDiameter(0.508); // 20 inch
 * calc.setMaterialGrade("X65");
 * calc.setDesignCode("ASME_B31_8");
 * calc.setLocationClass(2);
 *
 * double wallThickness = calc.calculateMinimumWallThickness();
 * double maop = calc.calculateMAOP();
 * }</pre>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class PipeMechanicalDesignCalculator implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  // ============ Design Codes ============
  /** ASME B31.3 - Process Piping. */
  public static final String ASME_B31_3 = "ASME_B31_3";
  /** ASME B31.4 - Liquid Transportation. */
  public static final String ASME_B31_4 = "ASME_B31_4";
  /** ASME B31.8 - Gas Transmission. */
  public static final String ASME_B31_8 = "ASME_B31_8";
  /** DNV-OS-F101 - Submarine Pipelines. */
  public static final String DNV_OS_F101 = "DNV_OS_F101";

  // ============ Input Parameters ============
  /** Design pressure in MPa. */
  private double designPressure = 10.0;
  /** Design temperature in Celsius. */
  private double designTemperature = 50.0;
  /** Pipe outer diameter in meters. */
  private double outerDiameter = 0.2032;
  /** Pipe inner diameter in meters (calculated). */
  private double innerDiameter = 0.0;
  /** Nominal wall thickness in meters. */
  private double nominalWallThickness = 0.0;
  /** Corrosion allowance in meters. */
  private double corrosionAllowance = 0.003; // 3mm default
  /** Fabrication tolerance factor (typically 0.875 for seamless). */
  private double fabricationTolerance = 0.875;

  // ============ Material Properties ============
  /** Material grade (e.g., "X52", "X65", "X70"). */
  private String materialGrade = "X65";
  /** Specified Minimum Yield Strength in MPa. */
  private double smys = 448.0; // X65 default
  /** Specified Minimum Tensile Strength in MPa. */
  private double smts = 531.0; // X65 default
  /** Young's modulus in MPa. */
  private double youngsModulus = 207000.0;
  /** Poisson's ratio. */
  private double poissonsRatio = 0.3;
  /** Thermal expansion coefficient in 1/K. */
  private double thermalExpansion = 11.7e-6;

  // ============ Design Factors ============
  /** Design code to use. */
  private String designCode = ASME_B31_8;
  /** Location class (1-4 for ASME B31.8). */
  private int locationClass = 1;
  /** Design factor (F). */
  private double designFactor = 0.72;
  /** Longitudinal joint factor (E). */
  private double jointFactor = 1.0;
  /** Temperature derating factor (T). */
  private double temperatureDerating = 1.0;
  /** Weld joint efficiency. */
  private double weldJointEfficiency = 1.0;

  // ============ Pipeline Physical Properties ============
  /** Pipeline length in meters. */
  private double pipelineLength = 1000.0;
  /** Steel density in kg/m3. */
  private double steelDensity = 7850.0;
  /** Number of pipe joints (12m standard length). */
  private int numberOfJoints = 0;
  /** Number of field welds. */
  private int numberOfFieldWelds = 0;

  // ============ Coating and Insulation ============
  /** External coating type (e.g., "3LPE", "FBE", "CTE"). */
  private String coatingType = "3LPE";
  /** External coating thickness in meters. */
  private double coatingThickness = 0.003; // 3mm default
  /** Coating density in kg/m3. */
  private double coatingDensity = 950.0; // PE density
  /** Insulation type (e.g., "PUF", "mineral wool", "none"). */
  private String insulationType = "none";
  /** Insulation thickness in meters. */
  private double insulationThickness = 0.0;
  /** Insulation density in kg/m3. */
  private double insulationDensity = 80.0;
  /** Insulation thermal conductivity in W/(m·K). */
  private double insulationConductivity = 0.025;

  // ============ Concrete Weight Coating (Subsea) ============
  /** Concrete weight coating thickness in meters. */
  private double concreteCoatingThickness = 0.0;
  /** Concrete density in kg/m3. */
  private double concreteDensity = 3040.0; // Standard subsea concrete
  /** Required negative buoyancy in N/m. */
  private double requiredNegativeBuoyancy = 0.0;

  // ============ Installation Parameters ============
  /** Installation method (e.g., "S-lay", "J-lay", "Reel-lay", "Onshore"). */
  private String installationMethod = "Onshore";
  /** Water depth in meters. */
  private double waterDepth = 0.0;
  /** Burial depth in meters. */
  private double burialDepth = 1.0;
  /** Ambient temperature in Celsius. */
  private double ambientTemperature = 15.0;
  /** Seawater density in kg/m3. */
  private double seawaterDensity = 1025.0;

  // ============ Support and Expansion ============
  /** Pipe support spacing in meters. */
  private double supportSpacing = 6.0;
  /** Number of pipe supports. */
  private int numberOfSupports = 0;
  /** Expansion loop length in meters. */
  private double expansionLoopLength = 0.0;
  /** Number of expansion loops required. */
  private int numberOfExpansionLoops = 0;
  /** Anchor spacing in meters. */
  private double anchorSpacing = 100.0;
  /** Number of anchors. */
  private int numberOfAnchors = 0;

  // ============ Flanges and Connections ============
  /** Flange class per ASME B16.5. */
  private int flangeClass = 300;
  /** Number of flange pairs. */
  private int numberOfFlangePairs = 0;
  /** Number of valves. */
  private int numberOfValves = 0;
  /** Valve type (e.g., "Ball", "Gate", "Check"). */
  private String valveType = "Ball";

  // ============ Calculated Results ============
  /** Calculated minimum wall thickness in meters. */
  private double minimumWallThickness = 0.0;
  /** Maximum Allowable Operating Pressure in MPa. */
  private double maop = 0.0;
  /** Calculated hoop stress in MPa. */
  private double hoopStress = 0.0;
  /** Calculated longitudinal stress in MPa. */
  private double longitudinalStress = 0.0;
  /** Calculated von Mises stress in MPa. */
  private double vonMisesStress = 0.0;

  // ============ Additional Detailed Design Results ============
  /** External pressure at seabed in MPa. */
  private double externalPressure = 0.0;
  /** Collapse pressure in MPa. */
  private double collapsePressure = 0.0;
  /** Propagation buckling pressure in MPa. */
  private double propagationBucklingPressure = 0.0;
  /** Upheaval buckling force in N. */
  private double upheavalBucklingForce = 0.0;
  /** Lateral buckling force in N. */
  private double lateralBucklingForce = 0.0;
  /** Span length for free spans in meters. */
  private double allowableSpanLength = 0.0;
  /** Fatigue life in years. */
  private double fatigueLife = 0.0;
  /** Bending radius for field bends in meters. */
  private double minimumBendRadius = 0.0;

  // ============ Weight and Buoyancy Results ============
  /** Pipe steel weight per meter in kg/m. */
  private double steelWeightPerMeter = 0.0;
  /** Coating weight per meter in kg/m. */
  private double coatingWeightPerMeter = 0.0;
  /** Insulation weight per meter in kg/m. */
  private double insulationWeightPerMeter = 0.0;
  /** Concrete weight per meter in kg/m. */
  private double concreteWeightPerMeter = 0.0;
  /** Total dry weight per meter in kg/m. */
  private double totalDryWeightPerMeter = 0.0;
  /** Content weight per meter in kg/m. */
  private double contentWeightPerMeter = 0.0;
  /** Submerged weight per meter in kg/m (negative = buoyant). */
  private double submergedWeightPerMeter = 0.0;
  /** Total pipeline dry weight in kg. */
  private double totalPipelineWeight = 0.0;

  // ============ Surface Area Results ============
  /** External surface area per meter in m2/m. */
  private double externalSurfaceAreaPerMeter = 0.0;
  /** Internal surface area per meter in m2/m. */
  private double internalSurfaceAreaPerMeter = 0.0;
  /** Total external surface area in m2. */
  private double totalExternalSurfaceArea = 0.0;

  // ============ Cost Estimation Parameters ============
  /** Steel price in USD/kg. */
  private double steelPricePerKg = 1.50;
  /** Coating price in USD/m2. */
  private double coatingPricePerM2 = 25.0;
  /** Insulation price in USD/m3. */
  private double insulationPricePerM3 = 150.0;
  /** Concrete price in USD/m3. */
  private double concretePricePerM3 = 200.0;
  /** Field weld cost in USD per weld. */
  private double fieldWeldCost = 2500.0;
  /** Flange pair cost in USD. */
  private double flangePairCost = 5000.0;
  /** Valve cost in USD. */
  private double valveCost = 10000.0;
  /** Support cost in USD each. */
  private double supportCost = 500.0;
  /** Anchor cost in USD each. */
  private double anchorCost = 2000.0;
  /** Installation cost in USD/m (varies by method). */
  private double installationCostPerMeter = 500.0;
  /** Testing and commissioning cost percentage. */
  private double testingCostPercentage = 0.05;
  /** Engineering and design cost percentage. */
  private double engineeringCostPercentage = 0.10;
  /** Contingency percentage. */
  private double contingencyPercentage = 0.15;

  // ============ Cost Estimation Results ============
  /** Steel material cost in USD. */
  private double steelMaterialCost = 0.0;
  /** Coating cost in USD. */
  private double coatingCost = 0.0;
  /** Insulation cost in USD. */
  private double insulationCost = 0.0;
  /** Concrete coating cost in USD. */
  private double concreteCost = 0.0;
  /** Welding cost in USD. */
  private double weldingCost = 0.0;
  /** Flanges and fittings cost in USD. */
  private double flangesAndFittingsCost = 0.0;
  /** Valves cost in USD. */
  private double valvesCost = 0.0;
  /** Supports and anchors cost in USD. */
  private double supportsAndAnchorsCost = 0.0;
  /** Installation cost in USD. */
  private double installationCost = 0.0;
  /** Total direct cost in USD. */
  private double totalDirectCost = 0.0;
  /** Engineering cost in USD. */
  private double engineeringCost = 0.0;
  /** Testing cost in USD. */
  private double testingCost = 0.0;
  /** Contingency cost in USD. */
  private double contingencyCost = 0.0;
  /** Total project cost in USD. */
  private double totalProjectCost = 0.0;

  // ============ Labor Estimation ============
  /** Welding manhours per joint. */
  private double weldingManhoursPerJoint = 8.0;
  /** Installation manhours per meter. */
  private double installationManhoursPerMeter = 2.0;
  /** Total labor manhours. */
  private double totalLaborManhours = 0.0;

  /** Map of API 5L material grades to SMYS (MPa). */
  private static final Map<String, double[]> API_5L_GRADES;

  /** Map of nominal pipe sizes to OD in meters (API 5L / ASME B36.10). */
  private static final Map<String, Double> STANDARD_PIPE_SIZES;

  /** Map of ASME B16.5 flange pressure-temperature ratings at 38°C in MPa. */
  private static final Map<Integer, Double> FLANGE_CLASS_RATINGS;

  /** Standard pipe joint length in meters. */
  private static final double STANDARD_JOINT_LENGTH = 12.2; // 40 feet

  static {
    API_5L_GRADES = new HashMap<String, double[]>();
    // Grade -> [SMYS (MPa), SMTS (MPa)] per API 5L 46th Edition
    API_5L_GRADES.put("A25", new double[] {172, 310}); // 25000 psi yield
    API_5L_GRADES.put("A", new double[] {207, 331}); // 30000 psi yield
    API_5L_GRADES.put("B", new double[] {241, 414}); // 35000 psi yield
    API_5L_GRADES.put("X42", new double[] {290, 414}); // 42000 psi yield
    API_5L_GRADES.put("X46", new double[] {317, 434}); // 46000 psi yield
    API_5L_GRADES.put("X52", new double[] {359, 455}); // 52000 psi yield - common for process
    API_5L_GRADES.put("X56", new double[] {386, 490}); // 56000 psi yield
    API_5L_GRADES.put("X60", new double[] {414, 517}); // 60000 psi yield - high strength
    API_5L_GRADES.put("X65", new double[] {448, 531}); // 65000 psi yield - offshore standard
    API_5L_GRADES.put("X70", new double[] {483, 565}); // 70000 psi yield - high pressure
    API_5L_GRADES.put("X80", new double[] {552, 621}); // 80000 psi yield - very high strength
    API_5L_GRADES.put("X90", new double[] {620, 695}); // 90000 psi yield - ultra high strength
    API_5L_GRADES.put("X100", new double[] {690, 760}); // 100000 psi yield - maximum strength
    API_5L_GRADES.put("X120", new double[] {827, 931}); // 120000 psi yield - extreme applications

    // Standard pipe sizes per API 5L / ASME B36.10 (NPS -> OD in meters)
    STANDARD_PIPE_SIZES = new HashMap<String, Double>();
    STANDARD_PIPE_SIZES.put("2", 0.0603); // 2"
    STANDARD_PIPE_SIZES.put("3", 0.0889); // 3"
    STANDARD_PIPE_SIZES.put("4", 0.1143); // 4"
    STANDARD_PIPE_SIZES.put("6", 0.1683); // 6"
    STANDARD_PIPE_SIZES.put("8", 0.2191); // 8"
    STANDARD_PIPE_SIZES.put("10", 0.2731); // 10"
    STANDARD_PIPE_SIZES.put("12", 0.3239); // 12"
    STANDARD_PIPE_SIZES.put("14", 0.3556); // 14"
    STANDARD_PIPE_SIZES.put("16", 0.4064); // 16"
    STANDARD_PIPE_SIZES.put("18", 0.4572); // 18"
    STANDARD_PIPE_SIZES.put("20", 0.5080); // 20"
    STANDARD_PIPE_SIZES.put("24", 0.6096); // 24"
    STANDARD_PIPE_SIZES.put("26", 0.6604); // 26"
    STANDARD_PIPE_SIZES.put("28", 0.7112); // 28"
    STANDARD_PIPE_SIZES.put("30", 0.7620); // 30"
    STANDARD_PIPE_SIZES.put("32", 0.8128); // 32"
    STANDARD_PIPE_SIZES.put("34", 0.8636); // 34"
    STANDARD_PIPE_SIZES.put("36", 0.9144); // 36"
    STANDARD_PIPE_SIZES.put("40", 1.0160); // 40"
    STANDARD_PIPE_SIZES.put("42", 1.0668); // 42"
    STANDARD_PIPE_SIZES.put("44", 1.1176); // 44"
    STANDARD_PIPE_SIZES.put("48", 1.2192); // 48"

    // ASME B16.5 flange class ratings at 38°C (100°F) in MPa for carbon steel
    FLANGE_CLASS_RATINGS = new HashMap<Integer, Double>();
    FLANGE_CLASS_RATINGS.put(150, 1.93); // 280 psi
    FLANGE_CLASS_RATINGS.put(300, 5.07); // 735 psi
    FLANGE_CLASS_RATINGS.put(400, 6.76); // 980 psi
    FLANGE_CLASS_RATINGS.put(600, 10.13); // 1470 psi
    FLANGE_CLASS_RATINGS.put(900, 15.20); // 2205 psi
    FLANGE_CLASS_RATINGS.put(1500, 25.33); // 3675 psi
    FLANGE_CLASS_RATINGS.put(2500, 42.22); // 6125 psi
  }

  /**
   * Default constructor.
   */
  public PipeMechanicalDesignCalculator() {}

  /**
   * Constructor with basic parameters.
   *
   * @param outerDiameter pipe outer diameter in meters
   * @param designPressure design pressure in MPa
   * @param designTemperature design temperature in Celsius
   */
  public PipeMechanicalDesignCalculator(double outerDiameter, double designPressure,
      double designTemperature) {
    this.outerDiameter = outerDiameter;
    this.designPressure = designPressure;
    this.designTemperature = designTemperature;
  }

  // ============================================================================
  // MAIN CALCULATION METHODS
  // ============================================================================

  /**
   * Calculate minimum required wall thickness based on selected design code.
   *
   * @return minimum wall thickness in meters
   */
  public double calculateMinimumWallThickness() {
    updateMaterialProperties();
    updateDesignFactors();

    double tMin = 0.0;

    if (ASME_B31_8.equals(designCode)) {
      tMin = calculateWallThicknessASMEB318();
    } else if (ASME_B31_4.equals(designCode)) {
      tMin = calculateWallThicknessASMEB314();
    } else if (ASME_B31_3.equals(designCode)) {
      tMin = calculateWallThicknessASMEB313();
    } else if (DNV_OS_F101.equals(designCode)) {
      tMin = calculateWallThicknessDNV();
    } else {
      // Default to Barlow formula with safety factor
      tMin = calculateWallThicknessBarlow(0.72);
    }

    // Add corrosion allowance and apply fabrication tolerance
    minimumWallThickness = (tMin + corrosionAllowance) / fabricationTolerance;
    innerDiameter = outerDiameter - 2.0 * minimumWallThickness;

    return minimumWallThickness;
  }

  /**
   * Calculate Maximum Allowable Operating Pressure (MAOP).
   *
   * @return MAOP in MPa
   */
  public double calculateMAOP() {
    updateMaterialProperties();
    updateDesignFactors();

    double wallThick = nominalWallThickness > 0 ? nominalWallThickness : minimumWallThickness;
    if (wallThick <= 0) {
      wallThick = calculateMinimumWallThickness();
    }

    // Apply fabrication tolerance
    double t = wallThick * fabricationTolerance - corrosionAllowance;

    if (ASME_B31_8.equals(designCode)) {
      // Barlow formula rearranged for pressure
      maop = 2.0 * smys * t * designFactor * jointFactor * temperatureDerating / outerDiameter;
    } else if (ASME_B31_4.equals(designCode)) {
      maop = 2.0 * smys * t * designFactor * jointFactor * temperatureDerating / outerDiameter;
    } else if (ASME_B31_3.equals(designCode)) {
      double S = smys / 3.0; // Allowable stress per B31.3
      maop = 2.0 * S * weldJointEfficiency * t / outerDiameter;
    } else if (DNV_OS_F101.equals(designCode)) {
      // DNV uses characteristic wall thickness
      double t1 = t * 0.95; // 5% percentile
      maop = 2.0 * t1 * smys * designFactor / (outerDiameter - t1);
    } else {
      maop = 2.0 * smys * t * 0.72 / outerDiameter;
    }

    return maop;
  }

  /**
   * Calculate test pressure for hydrostatic testing.
   *
   * @return test pressure in MPa
   */
  public double calculateTestPressure() {
    double calcMaop = maop > 0 ? maop : calculateMAOP();

    if (ASME_B31_8.equals(designCode)) {
      // Class 1: 1.25 × MAOP, Class 2-4: 1.40 × MAOP
      return locationClass == 1 ? calcMaop * 1.25 : calcMaop * 1.40;
    } else if (ASME_B31_4.equals(designCode)) {
      return calcMaop * 1.25;
    } else if (ASME_B31_3.equals(designCode)) {
      return calcMaop * 1.50;
    } else if (DNV_OS_F101.equals(designCode)) {
      return calcMaop * 1.15; // System pressure test
    } else {
      return calcMaop * 1.25;
    }
  }

  // ============================================================================
  // STRESS CALCULATION METHODS
  // ============================================================================

  /**
   * Calculate hoop (circumferential) stress.
   *
   * <p>
   * Uses the Barlow formula for thin-walled cylinders: σh = P × D / (2 × t)
   * </p>
   *
   * @param pressure internal pressure in MPa
   * @return hoop stress in MPa
   */
  public double calculateHoopStress(double pressure) {
    double wallThick = nominalWallThickness > 0 ? nominalWallThickness : minimumWallThickness;
    if (wallThick <= 0) {
      throw new IllegalStateException("Wall thickness must be set or calculated first");
    }

    // Apply tolerance and subtract corrosion allowance
    double t = wallThick * fabricationTolerance - corrosionAllowance;
    hoopStress = pressure * outerDiameter / (2.0 * t);

    return hoopStress;
  }

  /**
   * Calculate longitudinal (axial) stress.
   *
   * <p>
   * For restrained pipe: σL = ν × σh - E × α × ΔT + P × D / (4 × t)
   * </p>
   *
   * @param pressure internal pressure in MPa
   * @param deltaT temperature change from installation in Celsius
   * @param restrained true if pipe is restrained (buried or anchored)
   * @return longitudinal stress in MPa
   */
  public double calculateLongitudinalStress(double pressure, double deltaT, boolean restrained) {
    double wallThick = nominalWallThickness > 0 ? nominalWallThickness : minimumWallThickness;
    if (wallThick <= 0) {
      throw new IllegalStateException("Wall thickness must be set or calculated first");
    }

    double t = wallThick * fabricationTolerance - corrosionAllowance;
    double hoop = calculateHoopStress(pressure);

    if (restrained) {
      // Restrained pipe: thermal + Poisson effect + end cap
      double thermalStress = youngsModulus * thermalExpansion * deltaT;
      double poissonEffect = poissonsRatio * hoop;
      double endCapStress = pressure * outerDiameter / (4.0 * t);
      longitudinalStress = poissonEffect - thermalStress + endCapStress;
    } else {
      // Unrestrained: only pressure end load
      longitudinalStress = pressure * outerDiameter / (4.0 * t);
    }

    return longitudinalStress;
  }

  /**
   * Calculate von Mises equivalent stress.
   *
   * <p>
   * σvm = √(σh² + σL² - σh × σL + 3τ²)
   * </p>
   *
   * @param pressure internal pressure in MPa
   * @param deltaT temperature change from installation in Celsius
   * @param restrained true if pipe is restrained
   * @return von Mises stress in MPa
   */
  public double calculateVonMisesStress(double pressure, double deltaT, boolean restrained) {
    double hoop = calculateHoopStress(pressure);
    double longitudinal = calculateLongitudinalStress(pressure, deltaT, restrained);

    vonMisesStress = Math.sqrt(hoop * hoop + longitudinal * longitudinal - hoop * longitudinal);

    return vonMisesStress;
  }

  /**
   * Check if design is within allowable stress limits.
   *
   * @return true if von Mises stress is within 90% of SMYS
   */
  public boolean isDesignSafe() {
    if (vonMisesStress <= 0) {
      calculateVonMisesStress(designPressure, 0, true);
    }
    return vonMisesStress < (0.9 * smys);
  }

  /**
   * Calculate safety margin as percentage of SMYS.
   *
   * @return safety margin (e.g., 0.2 means 20% margin)
   */
  public double calculateSafetyMargin() {
    if (vonMisesStress <= 0) {
      calculateVonMisesStress(designPressure, 0, true);
    }
    return (smys - vonMisesStress) / smys;
  }

  // ============================================================================
  // WEIGHT AND SURFACE AREA CALCULATIONS
  // ============================================================================

  /**
   * Calculate all weight and surface area values.
   *
   * <p>
   * This method calculates steel weight, coating weight, insulation weight, concrete weight,
   * surface areas, and buoyancy for subsea applications.
   * </p>
   */
  public void calculateWeightsAndAreas() {
    double wallThick = nominalWallThickness > 0 ? nominalWallThickness : minimumWallThickness;
    if (wallThick <= 0) {
      calculateMinimumWallThickness();
      wallThick = minimumWallThickness;
    }

    double id = outerDiameter - 2.0 * wallThick;

    // Steel weight per meter
    double steelArea = Math.PI * (outerDiameter * outerDiameter - id * id) / 4.0;
    steelWeightPerMeter = steelArea * steelDensity;

    // External surface area per meter
    externalSurfaceAreaPerMeter = Math.PI * outerDiameter;
    internalSurfaceAreaPerMeter = Math.PI * id;

    // Coating weight (external coating)
    double coatingOD = outerDiameter + 2.0 * coatingThickness;
    double coatingArea = Math.PI * (coatingOD * coatingOD - outerDiameter * outerDiameter) / 4.0;
    coatingWeightPerMeter = coatingArea * coatingDensity;

    // Insulation weight
    if (insulationThickness > 0) {
      double insulationOD = coatingOD + 2.0 * insulationThickness;
      double insulationArea = Math.PI * (insulationOD * insulationOD - coatingOD * coatingOD) / 4.0;
      insulationWeightPerMeter = insulationArea * insulationDensity;
    } else {
      insulationWeightPerMeter = 0.0;
    }

    // Concrete weight coating (subsea)
    if (concreteCoatingThickness > 0) {
      double baseOD = outerDiameter + 2.0 * coatingThickness + 2.0 * insulationThickness;
      double concreteOD = baseOD + 2.0 * concreteCoatingThickness;
      double concreteArea = Math.PI * (concreteOD * concreteOD - baseOD * baseOD) / 4.0;
      concreteWeightPerMeter = concreteArea * concreteDensity;
    } else {
      concreteWeightPerMeter = 0.0;
    }

    // Total dry weight
    totalDryWeightPerMeter = steelWeightPerMeter + coatingWeightPerMeter + insulationWeightPerMeter
        + concreteWeightPerMeter;

    // Total surface areas
    totalExternalSurfaceArea = externalSurfaceAreaPerMeter * pipelineLength;

    // Total pipeline weight
    totalPipelineWeight = totalDryWeightPerMeter * pipelineLength;
  }

  /**
   * Calculate submerged weight for subsea pipelines.
   *
   * @param contentDensity density of pipe contents in kg/m3
   * @return submerged weight per meter in kg/m (negative = buoyant)
   */
  public double calculateSubmergedWeight(double contentDensity) {
    calculateWeightsAndAreas();

    double wallThick = nominalWallThickness > 0 ? nominalWallThickness : minimumWallThickness;
    double id = outerDiameter - 2.0 * wallThick;

    // Content weight
    double contentArea = Math.PI * id * id / 4.0;
    contentWeightPerMeter = contentArea * contentDensity;

    // Total outer diameter with all coatings
    double totalOD = outerDiameter + 2.0 * coatingThickness + 2.0 * insulationThickness
        + 2.0 * concreteCoatingThickness;

    // Displaced water weight (buoyancy)
    double displacedVolume = Math.PI * totalOD * totalOD / 4.0;
    double buoyancyPerMeter = displacedVolume * seawaterDensity;

    // Submerged weight = dry weight + contents - buoyancy
    submergedWeightPerMeter = totalDryWeightPerMeter + contentWeightPerMeter - buoyancyPerMeter;

    return submergedWeightPerMeter;
  }

  /**
   * Calculate required concrete coating thickness for on-bottom stability.
   *
   * @param contentDensity density of pipe contents in kg/m3
   * @param targetSubmergedWeight target submerged weight in kg/m
   * @return required concrete coating thickness in meters
   */
  public double calculateRequiredConcreteThickness(double contentDensity,
      double targetSubmergedWeight) {
    // Iterative calculation for concrete thickness
    double testThickness = 0.0;
    double step = 0.005; // 5mm steps

    for (int i = 0; i < 100; i++) {
      this.concreteCoatingThickness = testThickness;
      double submerged = calculateSubmergedWeight(contentDensity);

      if (submerged >= targetSubmergedWeight) {
        return testThickness;
      }
      testThickness += step;
    }

    return testThickness; // Max thickness reached
  }

  /**
   * Calculate number of pipe joints and welds.
   */
  public void calculateJointsAndWelds() {
    numberOfJoints = (int) Math.ceil(pipelineLength / STANDARD_JOINT_LENGTH);
    numberOfFieldWelds = numberOfJoints - 1; // One less weld than joints
  }

  // ============================================================================
  // DETAILED DESIGN CALCULATIONS
  // ============================================================================

  /**
   * Calculate pipe support spacing for above-ground pipelines.
   *
   * <p>
   * Based on beam deflection limits and stress criteria per ASME B31.3.
   * </p>
   *
   * @param maxDeflection maximum allowable deflection in meters
   * @return recommended support spacing in meters
   */
  public double calculateSupportSpacing(double maxDeflection) {
    calculateWeightsAndAreas();

    double wallThick = nominalWallThickness > 0 ? nominalWallThickness : minimumWallThickness;
    double id = outerDiameter - 2.0 * wallThick;

    // Moment of inertia for pipe cross-section
    double momentOfInertia = Math.PI * (Math.pow(outerDiameter, 4) - Math.pow(id, 4)) / 64.0;

    // Total weight per meter including contents (assume water for hydrostatic test)
    double totalWeight = totalDryWeightPerMeter + (Math.PI * id * id / 4.0) * 1000.0;
    double w = totalWeight * 9.81; // Convert to N/m

    // Maximum span based on deflection: delta = 5*w*L^4 / (384*E*I)
    // Solving for L: L = (384*E*I*delta / (5*w))^0.25
    double E = youngsModulus * 1e6; // Convert MPa to Pa
    supportSpacing = Math.pow(384.0 * E * momentOfInertia * maxDeflection / (5.0 * w), 0.25);

    // Limit to practical values
    if (supportSpacing > 12.0) {
      supportSpacing = 12.0;
    }
    if (supportSpacing < 1.0) {
      supportSpacing = 1.0;
    }

    numberOfSupports = (int) Math.ceil(pipelineLength / supportSpacing) + 1;

    return supportSpacing;
  }

  /**
   * Calculate expansion loop length for thermal expansion.
   *
   * @param deltaT temperature change from installation in Celsius
   * @param loopType "U-loop", "Z-loop", or "L-loop"
   * @return required loop leg length in meters
   */
  public double calculateExpansionLoopLength(double deltaT, String loopType) {
    // Thermal expansion
    double expansion = thermalExpansion * deltaT * anchorSpacing;

    // Allowable bending stress (typically 0.67 * SMYS)
    double allowableStress = 0.67 * smys * 1e6; // Pa

    double wallThick = nominalWallThickness > 0 ? nominalWallThickness : minimumWallThickness;
    double E = youngsModulus * 1e6; // Pa

    // For U-loop: L = sqrt(3 * E * D * delta / allowableStress)
    if ("U-loop".equalsIgnoreCase(loopType)) {
      expansionLoopLength = Math.sqrt(3.0 * E * outerDiameter * expansion / allowableStress);
    } else if ("Z-loop".equalsIgnoreCase(loopType)) {
      expansionLoopLength = Math.sqrt(2.0 * E * outerDiameter * expansion / allowableStress);
    } else {
      // L-loop
      expansionLoopLength = Math.sqrt(1.5 * E * outerDiameter * expansion / allowableStress);
    }

    // Calculate number of expansion loops
    numberOfExpansionLoops = (int) Math.ceil(pipelineLength / anchorSpacing);
    numberOfAnchors = numberOfExpansionLoops + 1;

    return expansionLoopLength;
  }

  /**
   * Calculate minimum bend radius for field bends.
   *
   * @return minimum bend radius in meters
   */
  public double calculateMinimumBendRadius() {
    // Per API 5L and ASME B31.8: minimum radius = 18 * D for cold bends
    // Hot bends: minimum radius = 5 * D
    minimumBendRadius = 18.0 * outerDiameter;
    return minimumBendRadius;
  }

  /**
   * Select appropriate flange class based on design conditions.
   *
   * @return selected flange class per ASME B16.5
   */
  public int selectFlangeClass() {
    // Find minimum class that meets pressure requirement
    int[] classes = {150, 300, 400, 600, 900, 1500, 2500};

    for (int cls : classes) {
      Double rating = FLANGE_CLASS_RATINGS.get(cls);
      if (rating != null && rating >= designPressure) {
        flangeClass = cls;
        return cls;
      }
    }

    flangeClass = 2500; // Maximum available
    return flangeClass;
  }

  /**
   * Calculate external pressure at seabed.
   *
   * @return external pressure in MPa
   */
  public double calculateExternalPressure() {
    // P = rho * g * h
    externalPressure = seawaterDensity * 9.81 * waterDepth / 1e6; // MPa
    return externalPressure;
  }

  /**
   * Calculate collapse pressure per DNV-OS-F101.
   *
   * <p>
   * Uses the elastic-plastic collapse formula for combined external pressure and bending.
   * </p>
   *
   * @return collapse pressure in MPa
   */
  public double calculateCollapsePressure() {
    double wallThick = nominalWallThickness > 0 ? nominalWallThickness : minimumWallThickness;
    double t = wallThick * fabricationTolerance;
    double dOverT = outerDiameter / t;

    // Elastic collapse pressure
    double E = youngsModulus; // MPa
    double Pel = 2.0 * E * Math.pow(t / outerDiameter, 3) / (1.0 - poissonsRatio * poissonsRatio);

    // Plastic collapse pressure
    double Pp = 2.0 * smys * t / outerDiameter;

    // Combined collapse (DNV formula)
    // (Pc - Pel) * (Pc^2 - Pp^2) = Pc * Pel * Pp * fo
    // Simplified: Pc ≈ Pel for high D/t, Pc ≈ Pp for low D/t
    if (dOverT > 30) {
      collapsePressure = Pel * 0.85; // Elastic dominated
    } else if (dOverT < 15) {
      collapsePressure = Pp * 0.85; // Plastic dominated
    } else {
      // Transition region
      collapsePressure = 0.85 * Math.sqrt(Pel * Pp);
    }

    return collapsePressure;
  }

  /**
   * Calculate propagation buckling pressure.
   *
   * @return propagation buckling pressure in MPa
   */
  public double calculatePropagationBucklingPressure() {
    double wallThick = nominalWallThickness > 0 ? nominalWallThickness : minimumWallThickness;
    double t = wallThick * fabricationTolerance;

    // Propagation pressure per DNV-OS-F101
    // Pp = 35 * SMYS * (t/D)^2.5
    propagationBucklingPressure = 35.0 * smys * Math.pow(t / outerDiameter, 2.5);

    return propagationBucklingPressure;
  }

  /**
   * Calculate allowable free span length for subsea pipelines.
   *
   * @param currentVelocity current velocity in m/s
   * @return maximum allowable span length in meters
   */
  public double calculateAllowableSpanLength(double currentVelocity) {
    calculateWeightsAndAreas();

    double wallThick = nominalWallThickness > 0 ? nominalWallThickness : minimumWallThickness;
    double id = outerDiameter - 2.0 * wallThick;

    // Moment of inertia
    double momentOfInertia = Math.PI * (Math.pow(outerDiameter, 4) - Math.pow(id, 4)) / 64.0;

    // Effective mass per unit length (steel + added mass)
    double addedMass = Math.PI * outerDiameter * outerDiameter * seawaterDensity / 4.0;
    double effectiveMass = steelWeightPerMeter + addedMass;

    // Natural frequency for simply supported beam
    // fn = (pi/2) * sqrt(E*I / (m*L^4))
    // For Strouhal number St = 0.2, vortex shedding frequency fv = St * V / D
    // Avoid resonance: fn > 1.3 * fv

    double St = 0.2;
    double fv = St * currentVelocity / outerDiameter;
    double targetFn = 1.3 * fv;

    // Solving for L: L = (pi^2 * E * I / (4 * m * fn^2))^0.25
    double E = youngsModulus * 1e6; // Pa
    if (targetFn > 0) {
      allowableSpanLength = Math.pow(
          Math.PI * Math.PI * E * momentOfInertia / (4.0 * effectiveMass * targetFn * targetFn),
          0.25);
    } else {
      allowableSpanLength = 50.0; // Default maximum
    }

    // Limit to practical values
    if (allowableSpanLength > 100.0) {
      allowableSpanLength = 100.0;
    }

    return allowableSpanLength;
  }

  /**
   * Estimate fatigue life based on DNV-RP-C203.
   *
   * @param stressRange stress range per cycle in MPa
   * @param numberOfCycles expected number of cycles per year
   * @return estimated fatigue life in years
   */
  public double estimateFatigueLife(double stressRange, double numberOfCycles) {
    // S-N curve parameters for seawater with cathodic protection (DNV-RP-C203, D curve)
    double logA = 11.764;
    double m = 3.0;

    // N = 10^(logA) / S^m
    double N = Math.pow(10.0, logA) / Math.pow(stressRange, m);

    // Fatigue life in years
    fatigueLife = N / numberOfCycles;

    return fatigueLife;
  }

  /**
   * Calculate insulation thickness for heat loss control.
   *
   * @param inletTemperature fluid inlet temperature in Celsius
   * @param minArrivalTemperature minimum arrival temperature in Celsius
   * @param massFlowRate mass flow rate in kg/s
   * @param specificHeat fluid specific heat in J/(kg·K)
   * @return required insulation thickness in meters
   */
  public double calculateInsulationThickness(double inletTemperature, double minArrivalTemperature,
      double massFlowRate, double specificHeat) {
    // Allowable heat loss per meter
    double deltaT = inletTemperature - minArrivalTemperature;
    double allowableHeatLoss = massFlowRate * specificHeat * deltaT / pipelineLength;

    // Heat transfer: Q = U * A * LMTD
    // For cylindrical pipe: U = k / (r * ln(r2/r1))
    // Simplified: find thickness where heat loss equals allowable

    double r1 = outerDiameter / 2.0 + coatingThickness;
    double k = insulationConductivity;
    double LMTD =
        (inletTemperature - ambientTemperature + minArrivalTemperature - ambientTemperature) / 2.0;

    // Iterative solution
    double thickness = 0.025; // Start with 25mm
    for (int i = 0; i < 20; i++) {
      double r2 = r1 + thickness;
      double U = k / (r2 * Math.log(r2 / r1));
      double heatLoss = U * 2.0 * Math.PI * r2 * LMTD;

      if (heatLoss <= allowableHeatLoss) {
        break;
      }
      thickness += 0.025;
    }

    insulationThickness = thickness;
    return insulationThickness;
  }

  // ============================================================================
  // COST ESTIMATION CALCULATIONS
  // ============================================================================

  /**
   * Calculate complete project cost estimate.
   *
   * <p>
   * This method calculates all cost components including materials, fabrication, installation,
   * engineering, testing, and contingency.
   * </p>
   *
   * @return total project cost in USD
   */
  public double calculateProjectCost() {
    // Ensure weights and quantities are calculated
    calculateWeightsAndAreas();
    calculateJointsAndWelds();
    selectFlangeClass();

    // Support spacing if above ground
    if ("Onshore".equalsIgnoreCase(installationMethod) && waterDepth <= 0) {
      calculateSupportSpacing(0.01); // 10mm deflection limit
    }

    // Material costs
    steelMaterialCost = totalPipelineWeight * steelPricePerKg;
    coatingCost = totalExternalSurfaceArea * coatingPricePerM2;

    if (insulationThickness > 0) {
      double insulationVolume = Math.PI
          * ((outerDiameter + 2 * coatingThickness + 2 * insulationThickness)
              * (outerDiameter + 2 * coatingThickness + 2 * insulationThickness)
              - (outerDiameter + 2 * coatingThickness) * (outerDiameter + 2 * coatingThickness))
          / 4.0 * pipelineLength;
      insulationCost = insulationVolume * insulationPricePerM3;
    } else {
      insulationCost = 0.0;
    }

    if (concreteCoatingThickness > 0) {
      double concreteVolume = concreteWeightPerMeter / concreteDensity * pipelineLength;
      concreteCost = concreteVolume * concretePricePerM3;
    } else {
      concreteCost = 0.0;
    }

    // Fabrication costs
    weldingCost = numberOfFieldWelds * fieldWeldCost;
    flangesAndFittingsCost = numberOfFlangePairs * flangePairCost;
    valvesCost = numberOfValves * valveCost;
    supportsAndAnchorsCost = numberOfSupports * supportCost + numberOfAnchors * anchorCost;

    // Installation costs
    updateInstallationCostPerMeter();
    installationCost = pipelineLength * installationCostPerMeter;

    // Total direct cost
    totalDirectCost = steelMaterialCost + coatingCost + insulationCost + concreteCost + weldingCost
        + flangesAndFittingsCost + valvesCost + supportsAndAnchorsCost + installationCost;

    // Indirect costs
    engineeringCost = totalDirectCost * engineeringCostPercentage;
    testingCost = totalDirectCost * testingCostPercentage;
    contingencyCost = totalDirectCost * contingencyPercentage;

    // Total project cost
    totalProjectCost = totalDirectCost + engineeringCost + testingCost + contingencyCost;

    return totalProjectCost;
  }

  /**
   * Update installation cost per meter based on installation method.
   */
  private void updateInstallationCostPerMeter() {
    if ("S-lay".equalsIgnoreCase(installationMethod)) {
      installationCostPerMeter = 800.0 + waterDepth * 2.0;
    } else if ("J-lay".equalsIgnoreCase(installationMethod)) {
      installationCostPerMeter = 1200.0 + waterDepth * 3.0;
    } else if ("Reel-lay".equalsIgnoreCase(installationMethod)) {
      installationCostPerMeter = 600.0 + waterDepth * 1.5;
    } else if ("HDD".equalsIgnoreCase(installationMethod)) {
      // Horizontal Directional Drilling
      installationCostPerMeter = 1500.0;
    } else {
      // Onshore conventional
      installationCostPerMeter = 300.0;
      if (burialDepth > 0) {
        installationCostPerMeter += burialDepth * 50.0;
      }
    }

    // Adjust for pipe size
    double diameterFactor = outerDiameter / 0.5; // Normalize to 20" pipe
    installationCostPerMeter *= diameterFactor;
  }

  /**
   * Calculate labor manhours estimate.
   *
   * @return total labor manhours
   */
  public double calculateLaborManhours() {
    calculateJointsAndWelds();

    double weldingHours = numberOfFieldWelds * weldingManhoursPerJoint;
    double installationHours = pipelineLength * installationManhoursPerMeter;
    double supportHours = numberOfSupports * 4.0; // 4 hours per support
    double testingHours = pipelineLength * 0.5; // 0.5 hours per meter

    totalLaborManhours = weldingHours + installationHours + supportHours + testingHours;

    return totalLaborManhours;
  }

  /**
   * Select standard nominal pipe size.
   *
   * @return standard NPS size string
   */
  public String selectStandardPipeSize() {
    String selectedSize = "";
    double minDiff = Double.MAX_VALUE;

    for (Map.Entry<String, Double> entry : STANDARD_PIPE_SIZES.entrySet()) {
      double diff = Math.abs(entry.getValue() - outerDiameter);
      if (diff < minDiff) {
        minDiff = diff;
        selectedSize = entry.getKey();
      }
    }

    // Update OD to standard size if within 5%
    if (minDiff / outerDiameter < 0.05) {
      outerDiameter = STANDARD_PIPE_SIZES.get(selectedSize);
    }

    return selectedSize;
  }

  /**
   * Generate Bill of Materials (BOM).
   *
   * @return list of BOM items as maps
   */
  public java.util.List<Map<String, Object>> generateBillOfMaterials() {
    calculateWeightsAndAreas();
    calculateJointsAndWelds();
    selectFlangeClass();
    calculateSupportSpacing(0.01);

    java.util.List<Map<String, Object>> bom = new java.util.ArrayList<Map<String, Object>>();

    // Line pipe
    Map<String, Object> pipe = new java.util.LinkedHashMap<String, Object>();
    pipe.put("item", "Line Pipe");
    pipe.put("description",
        String.format("API 5L %s, OD %.0fmm x %.1fmm WT", materialGrade, outerDiameter * 1000,
            (nominalWallThickness > 0 ? nominalWallThickness : minimumWallThickness) * 1000));
    pipe.put("quantity", numberOfJoints);
    pipe.put("unit", "joints");
    pipe.put("weight_kg", totalPipelineWeight);
    pipe.put("unitCost_USD", steelPricePerKg * steelWeightPerMeter * STANDARD_JOINT_LENGTH);
    pipe.put("totalCost_USD", steelMaterialCost);
    bom.add(pipe);

    // Coating
    Map<String, Object> coating = new java.util.LinkedHashMap<String, Object>();
    coating.put("item", "External Coating");
    coating.put("description",
        String.format("%s coating, %.1fmm thick", coatingType, coatingThickness * 1000));
    coating.put("quantity", totalExternalSurfaceArea);
    coating.put("unit", "m2");
    coating.put("unitCost_USD", coatingPricePerM2);
    coating.put("totalCost_USD", coatingCost);
    bom.add(coating);

    // Insulation (if applicable)
    if (insulationThickness > 0) {
      Map<String, Object> insulation = new java.util.LinkedHashMap<String, Object>();
      insulation.put("item", "Insulation");
      insulation.put("description",
          String.format("%s insulation, %.0fmm thick", insulationType, insulationThickness * 1000));
      insulation.put("quantity", pipelineLength);
      insulation.put("unit", "m");
      insulation.put("totalCost_USD", insulationCost);
      bom.add(insulation);
    }

    // Concrete coating (if applicable)
    if (concreteCoatingThickness > 0) {
      Map<String, Object> concrete = new java.util.LinkedHashMap<String, Object>();
      concrete.put("item", "Concrete Weight Coating");
      concrete.put("description",
          String.format("CWC, %.0fmm thick", concreteCoatingThickness * 1000));
      concrete.put("quantity", pipelineLength);
      concrete.put("unit", "m");
      concrete.put("totalCost_USD", concreteCost);
      bom.add(concrete);
    }

    // Flanges
    if (numberOfFlangePairs > 0) {
      Map<String, Object> flanges = new java.util.LinkedHashMap<String, Object>();
      flanges.put("item", "Flanges");
      flanges.put("description", String.format("ASME B16.5 Class %d WN flanges", flangeClass));
      flanges.put("quantity", numberOfFlangePairs * 2);
      flanges.put("unit", "each");
      flanges.put("totalCost_USD", flangesAndFittingsCost);
      bom.add(flanges);
    }

    // Valves
    if (numberOfValves > 0) {
      Map<String, Object> valves = new java.util.LinkedHashMap<String, Object>();
      valves.put("item", "Valves");
      valves.put("description", String.format("%s valve, Class %d", valveType, flangeClass));
      valves.put("quantity", numberOfValves);
      valves.put("unit", "each");
      valves.put("totalCost_USD", valvesCost);
      bom.add(valves);
    }

    // Supports
    if (numberOfSupports > 0) {
      Map<String, Object> supports = new java.util.LinkedHashMap<String, Object>();
      supports.put("item", "Pipe Supports");
      supports.put("description", "Steel pipe supports with saddles");
      supports.put("quantity", numberOfSupports);
      supports.put("unit", "each");
      supports.put("totalCost_USD", numberOfSupports * supportCost);
      bom.add(supports);
    }

    // Anchors
    if (numberOfAnchors > 0) {
      Map<String, Object> anchors = new java.util.LinkedHashMap<String, Object>();
      anchors.put("item", "Pipe Anchors");
      anchors.put("description", "Fixed anchor points");
      anchors.put("quantity", numberOfAnchors);
      anchors.put("unit", "each");
      anchors.put("totalCost_USD", numberOfAnchors * anchorCost);
      bom.add(anchors);
    }

    return bom;
  }

  // ============================================================================
  // PRIVATE CALCULATION METHODS FOR EACH STANDARD
  // ============================================================================

  /**
   * Calculate wall thickness per ASME B31.8 (Gas Transmission).
   *
   * <p>
   * t = P × D / (2 × S × F × E × T)
   * </p>
   *
   * @return minimum wall thickness in meters
   */
  private double calculateWallThicknessASMEB318() {
    return designPressure * outerDiameter
        / (2.0 * smys * designFactor * jointFactor * temperatureDerating);
  }

  /**
   * Calculate wall thickness per ASME B31.4 (Liquid Transportation).
   *
   * <p>
   * Same formula as B31.8 but different design factors.
   * </p>
   *
   * @return minimum wall thickness in meters
   */
  private double calculateWallThicknessASMEB314() {
    return designPressure * outerDiameter
        / (2.0 * smys * designFactor * jointFactor * temperatureDerating);
  }

  /**
   * Calculate wall thickness per ASME B31.3 (Process Piping).
   *
   * <p>
   * t = P × D / (2 × (S × E + P × Y))
   * </p>
   *
   * @return minimum wall thickness in meters
   */
  private double calculateWallThicknessASMEB313() {
    double S = smys / 3.0; // Allowable stress (1/3 of SMYS)
    double Y = 0.4; // Coefficient for materials below 482°C
    return designPressure * outerDiameter / (2.0 * (S * weldJointEfficiency + designPressure * Y));
  }

  /**
   * Calculate wall thickness per DNV-OS-F101 (Submarine Pipelines).
   *
   * <p>
   * Uses characteristic material properties with safety factors.
   * </p>
   *
   * @return minimum wall thickness in meters
   */
  private double calculateWallThicknessDNV() {
    // Material resistance factor
    double gammaM = 1.15;
    // Safety class factor (Medium = 1.04)
    double gammaSC = 1.04;
    // Use 2/3 of SMYS as characteristic strength
    double fy = smys / gammaM;

    return designPressure * outerDiameter * gammaSC / (2.0 * fy * designFactor);
  }

  /**
   * Calculate wall thickness using basic Barlow formula.
   *
   * @param safetyFactor safety factor to apply
   * @return minimum wall thickness in meters
   */
  private double calculateWallThicknessBarlow(double safetyFactor) {
    return designPressure * outerDiameter / (2.0 * smys * safetyFactor);
  }

  // ============================================================================
  // PROPERTY UPDATE METHODS
  // ============================================================================

  /**
   * Update material properties based on material grade.
   */
  private void updateMaterialProperties() {
    if (API_5L_GRADES.containsKey(materialGrade)) {
      double[] props = API_5L_GRADES.get(materialGrade);
      smys = props[0];
      smts = props[1];
    }

    // Temperature derating per ASME B31.8
    if (designTemperature > 121) {
      if (designTemperature <= 149) {
        temperatureDerating = 0.967;
      } else if (designTemperature <= 177) {
        temperatureDerating = 0.933;
      } else if (designTemperature <= 204) {
        temperatureDerating = 0.900;
      } else if (designTemperature <= 232) {
        temperatureDerating = 0.867;
      } else {
        temperatureDerating = 0.833;
      }
    } else {
      temperatureDerating = 1.0;
    }
  }

  /**
   * Update design factors based on design code and location class.
   */
  private void updateDesignFactors() {
    if (ASME_B31_8.equals(designCode)) {
      // Design factor F based on location class
      switch (locationClass) {
        case 1:
          designFactor = 0.72;
          break;
        case 2:
          designFactor = 0.60;
          break;
        case 3:
          designFactor = 0.50;
          break;
        case 4:
          designFactor = 0.40;
          break;
        default:
          designFactor = 0.72;
      }
    } else if (ASME_B31_4.equals(designCode)) {
      designFactor = 0.72; // Standard for liquid lines
    } else if (ASME_B31_3.equals(designCode)) {
      designFactor = 1.0; // Uses allowable stress approach
    } else if (DNV_OS_F101.equals(designCode)) {
      // Usage factor based on safety class
      designFactor = 0.77; // Medium safety class
    }
  }

  /**
   * Generate a design summary report.
   *
   * @return formatted design summary string
   */
  public String generateDesignReport() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== Pipeline Mechanical Design Report ===\n");
    sb.append(String.format("Design Code: %s\n", designCode));
    sb.append(String.format("Material Grade: %s (API 5L)\n", materialGrade));
    sb.append("\n--- Design Parameters ---\n");
    sb.append(String.format("Design Pressure: %.2f MPa (%.1f bar)\n", designPressure,
        designPressure * 10));
    sb.append(String.format("Design Temperature: %.1f °C\n", designTemperature));
    sb.append(String.format("Outer Diameter: %.1f mm\n", outerDiameter * 1000));
    sb.append(String.format("Corrosion Allowance: %.1f mm\n", corrosionAllowance * 1000));
    sb.append("\n--- Material Properties ---\n");
    sb.append(String.format("SMYS: %.0f MPa\n", smys));
    sb.append(String.format("SMTS: %.0f MPa\n", smts));
    sb.append("\n--- Design Factors ---\n");
    sb.append(String.format("Design Factor (F): %.2f\n", designFactor));
    sb.append(String.format("Joint Factor (E): %.2f\n", jointFactor));
    sb.append(String.format("Temperature Derating (T): %.3f\n", temperatureDerating));
    sb.append(String.format("Fabrication Tolerance: %.3f\n", fabricationTolerance));
    sb.append("\n--- Results ---\n");
    sb.append(String.format("Minimum Wall Thickness: %.2f mm\n", minimumWallThickness * 1000));
    sb.append(String.format("MAOP: %.2f MPa (%.1f bar)\n", maop, maop * 10));
    sb.append(String.format("Test Pressure: %.2f MPa (%.1f bar)\n", calculateTestPressure(),
        calculateTestPressure() * 10));
    if (vonMisesStress > 0) {
      sb.append(String.format("Hoop Stress: %.1f MPa (%.1f%% SMYS)\n", hoopStress,
          100 * hoopStress / smys));
      sb.append(String.format("Von Mises Stress: %.1f MPa (%.1f%% SMYS)\n", vonMisesStress,
          100 * vonMisesStress / smys));
      sb.append(String.format("Safety Margin: %.1f%%\n", 100 * calculateSafetyMargin()));
    }
    sb.append("==========================================\n");
    return sb.toString();
  }

  // ============================================================================
  // GETTERS AND SETTERS
  // ============================================================================

  /**
   * Get design pressure.
   *
   * @return design pressure in MPa
   */
  public double getDesignPressure() {
    return designPressure;
  }

  /**
   * Set design pressure.
   *
   * @param designPressure design pressure in MPa
   */
  public void setDesignPressure(double designPressure) {
    this.designPressure = designPressure;
  }

  /**
   * Set design pressure with unit.
   *
   * @param pressure design pressure value
   * @param unit pressure unit ("MPa", "bar", "bara", "psi")
   */
  public void setDesignPressure(double pressure, String unit) {
    String lowerUnit = unit.toLowerCase().trim();
    if ("mpa".equals(lowerUnit)) {
      this.designPressure = pressure;
    } else if ("bar".equals(lowerUnit) || "bara".equals(lowerUnit)) {
      this.designPressure = pressure * 0.1;
    } else if ("psi".equals(lowerUnit) || "psig".equals(lowerUnit)) {
      this.designPressure = pressure * 0.00689476;
    } else {
      this.designPressure = pressure;
    }
  }

  /**
   * Get design temperature.
   *
   * @return design temperature in Celsius
   */
  public double getDesignTemperature() {
    return designTemperature;
  }

  /**
   * Set design temperature.
   *
   * @param designTemperature design temperature in Celsius
   */
  public void setDesignTemperature(double designTemperature) {
    this.designTemperature = designTemperature;
  }

  /**
   * Get outer diameter.
   *
   * @return outer diameter in meters
   */
  public double getOuterDiameter() {
    return outerDiameter;
  }

  /**
   * Set outer diameter.
   *
   * @param outerDiameter outer diameter in meters
   */
  public void setOuterDiameter(double outerDiameter) {
    this.outerDiameter = outerDiameter;
  }

  /**
   * Set outer diameter with unit.
   *
   * @param diameter outer diameter value
   * @param unit diameter unit ("m", "mm", "inch", "in")
   */
  public void setOuterDiameter(double diameter, String unit) {
    String lowerUnit = unit.toLowerCase().trim();
    if ("m".equals(lowerUnit)) {
      this.outerDiameter = diameter;
    } else if ("mm".equals(lowerUnit)) {
      this.outerDiameter = diameter / 1000.0;
    } else if ("inch".equals(lowerUnit) || "in".equals(lowerUnit)) {
      this.outerDiameter = diameter * 0.0254;
    } else {
      this.outerDiameter = diameter;
    }
  }

  /**
   * Get inner diameter.
   *
   * @return inner diameter in meters
   */
  public double getInnerDiameter() {
    if (innerDiameter <= 0 && minimumWallThickness > 0) {
      innerDiameter = outerDiameter - 2.0 * minimumWallThickness;
    }
    return innerDiameter;
  }

  /**
   * Get nominal wall thickness.
   *
   * @return nominal wall thickness in meters
   */
  public double getNominalWallThickness() {
    return nominalWallThickness;
  }

  /**
   * Set nominal wall thickness.
   *
   * @param nominalWallThickness nominal wall thickness in meters
   */
  public void setNominalWallThickness(double nominalWallThickness) {
    this.nominalWallThickness = nominalWallThickness;
    this.innerDiameter = outerDiameter - 2.0 * nominalWallThickness;
  }

  /**
   * Set nominal wall thickness with unit.
   *
   * @param thickness wall thickness value
   * @param unit thickness unit ("m", "mm", "inch")
   */
  public void setNominalWallThickness(double thickness, String unit) {
    String lowerUnit = unit.toLowerCase().trim();
    if ("m".equals(lowerUnit)) {
      this.nominalWallThickness = thickness;
    } else if ("mm".equals(lowerUnit)) {
      this.nominalWallThickness = thickness / 1000.0;
    } else if ("inch".equals(lowerUnit) || "in".equals(lowerUnit)) {
      this.nominalWallThickness = thickness * 0.0254;
    } else {
      this.nominalWallThickness = thickness;
    }
    this.innerDiameter = outerDiameter - 2.0 * this.nominalWallThickness;
  }

  /**
   * Get corrosion allowance.
   *
   * @return corrosion allowance in meters
   */
  public double getCorrosionAllowance() {
    return corrosionAllowance;
  }

  /**
   * Set corrosion allowance.
   *
   * @param corrosionAllowance corrosion allowance in meters
   */
  public void setCorrosionAllowance(double corrosionAllowance) {
    this.corrosionAllowance = corrosionAllowance;
  }

  /**
   * Set corrosion allowance with unit.
   *
   * @param allowance corrosion allowance value
   * @param unit unit ("m", "mm")
   */
  public void setCorrosionAllowance(double allowance, String unit) {
    String lowerUnit = unit.toLowerCase().trim();
    if ("m".equals(lowerUnit)) {
      this.corrosionAllowance = allowance;
    } else if ("mm".equals(lowerUnit)) {
      this.corrosionAllowance = allowance / 1000.0;
    } else {
      this.corrosionAllowance = allowance;
    }
  }

  /**
   * Get fabrication tolerance factor.
   *
   * @return fabrication tolerance factor
   */
  public double getFabricationTolerance() {
    return fabricationTolerance;
  }

  /**
   * Set fabrication tolerance factor.
   *
   * @param fabricationTolerance fabrication tolerance factor (e.g., 0.875 for seamless pipe)
   */
  public void setFabricationTolerance(double fabricationTolerance) {
    this.fabricationTolerance = fabricationTolerance;
  }

  /**
   * Get material grade.
   *
   * @return material grade (e.g., "X65")
   */
  public String getMaterialGrade() {
    return materialGrade;
  }

  /**
   * Set material grade per API 5L.
   *
   * @param materialGrade material grade (e.g., "X42", "X52", "X65", "X70", "X80")
   */
  public void setMaterialGrade(String materialGrade) {
    this.materialGrade = materialGrade;
    updateMaterialProperties();
  }

  /**
   * Get Specified Minimum Yield Strength (SMYS).
   *
   * @return SMYS in MPa
   */
  public double getSmys() {
    return smys;
  }

  /**
   * Set Specified Minimum Yield Strength manually.
   *
   * @param smys SMYS in MPa
   */
  public void setSmys(double smys) {
    this.smys = smys;
  }

  /**
   * Get Specified Minimum Tensile Strength (SMTS).
   *
   * @return SMTS in MPa
   */
  public double getSmts() {
    return smts;
  }

  /**
   * Set Specified Minimum Tensile Strength manually.
   *
   * @param smts SMTS in MPa
   */
  public void setSmts(double smts) {
    this.smts = smts;
  }

  /**
   * Get design code.
   *
   * @return design code string
   */
  public String getDesignCode() {
    return designCode;
  }

  /**
   * Set design code.
   *
   * @param designCode design code (use constants like ASME_B31_8)
   */
  public void setDesignCode(String designCode) {
    this.designCode = designCode;
    updateDesignFactors();
  }

  /**
   * Get location class.
   *
   * @return location class (1-4)
   */
  public int getLocationClass() {
    return locationClass;
  }

  /**
   * Set location class for ASME B31.8.
   *
   * @param locationClass location class 1-4
   */
  public void setLocationClass(int locationClass) {
    this.locationClass = Math.max(1, Math.min(4, locationClass));
    updateDesignFactors();
  }

  /**
   * Get design factor.
   *
   * @return design factor
   */
  public double getDesignFactor() {
    return designFactor;
  }

  /**
   * Set design factor (overrides automatic calculation).
   *
   * @param designFactor design factor
   */
  public void setDesignFactor(double designFactor) {
    this.designFactor = designFactor;
  }

  /**
   * Get longitudinal joint factor.
   *
   * @return joint factor
   */
  public double getJointFactor() {
    return jointFactor;
  }

  /**
   * Set longitudinal joint factor.
   *
   * @param jointFactor joint factor (1.0 for seamless, 0.85 for ERW)
   */
  public void setJointFactor(double jointFactor) {
    this.jointFactor = jointFactor;
  }

  /**
   * Get weld joint efficiency.
   *
   * @return weld joint efficiency
   */
  public double getWeldJointEfficiency() {
    return weldJointEfficiency;
  }

  /**
   * Set weld joint efficiency for B31.3.
   *
   * @param weldJointEfficiency weld joint efficiency (0-1)
   */
  public void setWeldJointEfficiency(double weldJointEfficiency) {
    this.weldJointEfficiency = weldJointEfficiency;
  }

  /**
   * Get calculated minimum wall thickness.
   *
   * @return minimum wall thickness in meters
   */
  public double getMinimumWallThickness() {
    if (minimumWallThickness <= 0) {
      calculateMinimumWallThickness();
    }
    return minimumWallThickness;
  }

  /**
   * Get calculated MAOP.
   *
   * @return MAOP in MPa
   */
  public double getMaop() {
    if (maop <= 0) {
      calculateMAOP();
    }
    return maop;
  }

  /**
   * Get MAOP in specified unit.
   *
   * @param unit pressure unit ("MPa", "bar", "psi")
   * @return MAOP in specified unit
   */
  public double getMaop(String unit) {
    double maopVal = getMaop();
    String lowerUnit = unit.toLowerCase().trim();
    if ("mpa".equals(lowerUnit)) {
      return maopVal;
    } else if ("bar".equals(lowerUnit) || "bara".equals(lowerUnit)) {
      return maopVal * 10;
    } else if ("psi".equals(lowerUnit) || "psig".equals(lowerUnit)) {
      return maopVal * 145.038;
    }
    return maopVal;
  }

  /**
   * Get calculated hoop stress.
   *
   * @return hoop stress in MPa
   */
  public double getHoopStress() {
    return hoopStress;
  }

  /**
   * Get calculated von Mises stress.
   *
   * @return von Mises stress in MPa
   */
  public double getVonMisesStress() {
    return vonMisesStress;
  }

  /**
   * Get Young's modulus.
   *
   * @return Young's modulus in MPa
   */
  public double getYoungsModulus() {
    return youngsModulus;
  }

  /**
   * Set Young's modulus.
   *
   * @param youngsModulus Young's modulus in MPa
   */
  public void setYoungsModulus(double youngsModulus) {
    this.youngsModulus = youngsModulus;
  }

  /**
   * Get Poisson's ratio.
   *
   * @return Poisson's ratio
   */
  public double getPoissonsRatio() {
    return poissonsRatio;
  }

  /**
   * Set Poisson's ratio.
   *
   * @param poissonsRatio Poisson's ratio
   */
  public void setPoissonsRatio(double poissonsRatio) {
    this.poissonsRatio = poissonsRatio;
  }

  /**
   * Get thermal expansion coefficient.
   *
   * @return thermal expansion coefficient in 1/K
   */
  public double getThermalExpansion() {
    return thermalExpansion;
  }

  /**
   * Set thermal expansion coefficient.
   *
   * @param thermalExpansion thermal expansion coefficient in 1/K
   */
  public void setThermalExpansion(double thermalExpansion) {
    this.thermalExpansion = thermalExpansion;
  }

  // ============================================================================
  // GETTERS AND SETTERS - PIPELINE PHYSICAL PROPERTIES
  // ============================================================================

  /**
   * Get pipeline length.
   *
   * @return pipeline length in meters
   */
  public double getPipelineLength() {
    return pipelineLength;
  }

  /**
   * Set pipeline length.
   *
   * @param pipelineLength pipeline length in meters
   */
  public void setPipelineLength(double pipelineLength) {
    this.pipelineLength = pipelineLength;
  }

  /**
   * Get steel density.
   *
   * @return steel density in kg/m3
   */
  public double getSteelDensity() {
    return steelDensity;
  }

  /**
   * Set steel density.
   *
   * @param steelDensity steel density in kg/m3
   */
  public void setSteelDensity(double steelDensity) {
    this.steelDensity = steelDensity;
  }

  // ============================================================================
  // GETTERS AND SETTERS - COATING AND INSULATION
  // ============================================================================

  /**
   * Get external coating type.
   *
   * @return coating type
   */
  public String getCoatingType() {
    return coatingType;
  }

  /**
   * Set external coating type.
   *
   * @param coatingType coating type (e.g., "3LPE", "FBE", "CTE")
   */
  public void setCoatingType(String coatingType) {
    this.coatingType = coatingType;
  }

  /**
   * Get external coating thickness.
   *
   * @return coating thickness in meters
   */
  public double getCoatingThickness() {
    return coatingThickness;
  }

  /**
   * Set external coating thickness.
   *
   * @param coatingThickness coating thickness in meters
   */
  public void setCoatingThickness(double coatingThickness) {
    this.coatingThickness = coatingThickness;
  }

  /**
   * Get insulation type.
   *
   * @return insulation type
   */
  public String getInsulationType() {
    return insulationType;
  }

  /**
   * Set insulation type.
   *
   * @param insulationType insulation type (e.g., "PUF", "mineral wool", "none")
   */
  public void setInsulationType(String insulationType) {
    this.insulationType = insulationType;
  }

  /**
   * Get insulation thickness.
   *
   * @return insulation thickness in meters
   */
  public double getInsulationThickness() {
    return insulationThickness;
  }

  /**
   * Set insulation thickness.
   *
   * @param insulationThickness insulation thickness in meters
   */
  public void setInsulationThickness(double insulationThickness) {
    this.insulationThickness = insulationThickness;
  }

  /**
   * Get concrete weight coating thickness.
   *
   * @return concrete coating thickness in meters
   */
  public double getConcreteCoatingThickness() {
    return concreteCoatingThickness;
  }

  /**
   * Set concrete weight coating thickness.
   *
   * @param concreteCoatingThickness concrete coating thickness in meters
   */
  public void setConcreteCoatingThickness(double concreteCoatingThickness) {
    this.concreteCoatingThickness = concreteCoatingThickness;
  }

  // ============================================================================
  // GETTERS AND SETTERS - INSTALLATION PARAMETERS
  // ============================================================================

  /**
   * Get installation method.
   *
   * @return installation method
   */
  public String getInstallationMethod() {
    return installationMethod;
  }

  /**
   * Set installation method.
   *
   * @param installationMethod installation method (e.g., "S-lay", "J-lay", "Reel-lay", "Onshore")
   */
  public void setInstallationMethod(String installationMethod) {
    this.installationMethod = installationMethod;
  }

  /**
   * Get water depth.
   *
   * @return water depth in meters
   */
  public double getWaterDepth() {
    return waterDepth;
  }

  /**
   * Set water depth.
   *
   * @param waterDepth water depth in meters
   */
  public void setWaterDepth(double waterDepth) {
    this.waterDepth = waterDepth;
  }

  /**
   * Get burial depth.
   *
   * @return burial depth in meters
   */
  public double getBurialDepth() {
    return burialDepth;
  }

  /**
   * Set burial depth.
   *
   * @param burialDepth burial depth in meters
   */
  public void setBurialDepth(double burialDepth) {
    this.burialDepth = burialDepth;
  }

  /**
   * Get ambient temperature.
   *
   * @return ambient temperature in Celsius
   */
  public double getAmbientTemperature() {
    return ambientTemperature;
  }

  /**
   * Set ambient temperature.
   *
   * @param ambientTemperature ambient temperature in Celsius
   */
  public void setAmbientTemperature(double ambientTemperature) {
    this.ambientTemperature = ambientTemperature;
  }

  // ============================================================================
  // GETTERS AND SETTERS - FLANGES AND VALVES
  // ============================================================================

  /**
   * Get flange class.
   *
   * @return flange class per ASME B16.5
   */
  public int getFlangeClass() {
    return flangeClass;
  }

  /**
   * Set flange class.
   *
   * @param flangeClass flange class (150, 300, 600, 900, 1500, 2500)
   */
  public void setFlangeClass(int flangeClass) {
    this.flangeClass = flangeClass;
  }

  /**
   * Get number of flange pairs.
   *
   * @return number of flange pairs
   */
  public int getNumberOfFlangePairs() {
    return numberOfFlangePairs;
  }

  /**
   * Set number of flange pairs.
   *
   * @param numberOfFlangePairs number of flange pairs
   */
  public void setNumberOfFlangePairs(int numberOfFlangePairs) {
    this.numberOfFlangePairs = numberOfFlangePairs;
  }

  /**
   * Get number of valves.
   *
   * @return number of valves
   */
  public int getNumberOfValves() {
    return numberOfValves;
  }

  /**
   * Set number of valves.
   *
   * @param numberOfValves number of valves
   */
  public void setNumberOfValves(int numberOfValves) {
    this.numberOfValves = numberOfValves;
  }

  /**
   * Get valve type.
   *
   * @return valve type
   */
  public String getValveType() {
    return valveType;
  }

  /**
   * Set valve type.
   *
   * @param valveType valve type (e.g., "Ball", "Gate", "Check")
   */
  public void setValveType(String valveType) {
    this.valveType = valveType;
  }

  // ============================================================================
  // GETTERS AND SETTERS - SUPPORTS AND EXPANSION
  // ============================================================================

  /**
   * Get anchor spacing.
   *
   * @return anchor spacing in meters
   */
  public double getAnchorSpacing() {
    return anchorSpacing;
  }

  /**
   * Set anchor spacing.
   *
   * @param anchorSpacing anchor spacing in meters
   */
  public void setAnchorSpacing(double anchorSpacing) {
    this.anchorSpacing = anchorSpacing;
  }

  // ============================================================================
  // GETTERS AND SETTERS - COST ESTIMATION RATES
  // ============================================================================

  /**
   * Get steel price per kg.
   *
   * @return steel price in USD/kg
   */
  public double getSteelPricePerKg() {
    return steelPricePerKg;
  }

  /**
   * Set steel price per kg.
   *
   * @param steelPricePerKg steel price in USD/kg
   */
  public void setSteelPricePerKg(double steelPricePerKg) {
    this.steelPricePerKg = steelPricePerKg;
  }

  /**
   * Get coating price per m2.
   *
   * @return coating price in USD/m2
   */
  public double getCoatingPricePerM2() {
    return coatingPricePerM2;
  }

  /**
   * Set coating price per m2.
   *
   * @param coatingPricePerM2 coating price in USD/m2
   */
  public void setCoatingPricePerM2(double coatingPricePerM2) {
    this.coatingPricePerM2 = coatingPricePerM2;
  }

  /**
   * Get field weld cost.
   *
   * @return field weld cost in USD per weld
   */
  public double getFieldWeldCost() {
    return fieldWeldCost;
  }

  /**
   * Set field weld cost.
   *
   * @param fieldWeldCost field weld cost in USD per weld
   */
  public void setFieldWeldCost(double fieldWeldCost) {
    this.fieldWeldCost = fieldWeldCost;
  }

  /**
   * Get contingency percentage.
   *
   * @return contingency percentage (e.g., 0.15 for 15%)
   */
  public double getContingencyPercentage() {
    return contingencyPercentage;
  }

  /**
   * Set contingency percentage.
   *
   * @param contingencyPercentage contingency percentage (e.g., 0.15 for 15%)
   */
  public void setContingencyPercentage(double contingencyPercentage) {
    this.contingencyPercentage = contingencyPercentage;
  }

  // ============================================================================
  // GETTERS - CALCULATED RESULTS (Read-Only)
  // ============================================================================

  /**
   * Get steel weight per meter.
   *
   * @return steel weight in kg/m
   */
  public double getSteelWeightPerMeter() {
    return steelWeightPerMeter;
  }

  /**
   * Get total dry weight per meter.
   *
   * @return total dry weight in kg/m
   */
  public double getTotalDryWeightPerMeter() {
    return totalDryWeightPerMeter;
  }

  /**
   * Get submerged weight per meter.
   *
   * @return submerged weight in kg/m (negative = buoyant)
   */
  public double getSubmergedWeightPerMeter() {
    return submergedWeightPerMeter;
  }

  /**
   * Get total pipeline weight.
   *
   * @return total pipeline dry weight in kg
   */
  public double getTotalPipelineWeight() {
    return totalPipelineWeight;
  }

  /**
   * Get total external surface area.
   *
   * @return total external surface area in m2
   */
  public double getTotalExternalSurfaceArea() {
    return totalExternalSurfaceArea;
  }

  /**
   * Get number of pipe joints.
   *
   * @return number of pipe joints
   */
  public int getNumberOfJoints() {
    return numberOfJoints;
  }

  /**
   * Get number of field welds.
   *
   * @return number of field welds
   */
  public int getNumberOfFieldWelds() {
    return numberOfFieldWelds;
  }

  /**
   * Get support spacing.
   *
   * @return support spacing in meters
   */
  public double getSupportSpacing() {
    return supportSpacing;
  }

  /**
   * Get number of supports.
   *
   * @return number of supports
   */
  public int getNumberOfSupports() {
    return numberOfSupports;
  }

  /**
   * Get collapse pressure.
   *
   * @return collapse pressure in MPa
   */
  public double getCollapsePressure() {
    return collapsePressure;
  }

  /**
   * Get fatigue life.
   *
   * @return fatigue life in years
   */
  public double getFatigueLife() {
    return fatigueLife;
  }

  /**
   * Get total project cost.
   *
   * @return total project cost in USD
   */
  public double getTotalProjectCost() {
    return totalProjectCost;
  }

  /**
   * Get total direct cost.
   *
   * @return total direct cost in USD
   */
  public double getTotalDirectCost() {
    return totalDirectCost;
  }

  /**
   * Get total labor manhours.
   *
   * @return total labor manhours
   */
  public double getTotalLaborManhours() {
    return totalLaborManhours;
  }

  // ============================================================================
  // JSON EXPORT
  // ============================================================================

  /**
   * Export mechanical design data to JSON format.
   *
   * <p>
   * This method creates a structured JSON representation of the pipeline mechanical design
   * including design parameters, material properties, design factors, and calculated results.
   * </p>
   *
   * @return JSON string representation of the mechanical design
   */
  public String toJson() {
    com.google.gson.GsonBuilder builder = new com.google.gson.GsonBuilder();
    builder.setPrettyPrinting();
    builder.serializeSpecialFloatingPointValues();
    return builder.create().toJson(toMap());
  }

  /**
   * Export mechanical design data to compact JSON format.
   *
   * @return compact JSON string
   */
  public String toCompactJson() {
    com.google.gson.GsonBuilder builder = new com.google.gson.GsonBuilder();
    builder.serializeSpecialFloatingPointValues();
    return builder.create().toJson(toMap());
  }

  /**
   * Convert mechanical design data to a Map for JSON serialization.
   *
   * @return Map containing design data
   */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new java.util.LinkedHashMap<String, Object>();

    // Equipment identification
    result.put("equipmentType", "Pipeline");
    result.put("designCode", designCode);
    result.put("materialGrade", materialGrade);
    result.put("pipelineLength_m", pipelineLength);

    // Design parameters
    Map<String, Object> designParams = new java.util.LinkedHashMap<String, Object>();
    designParams.put("designPressure_MPa", designPressure);
    designParams.put("designPressure_bar", designPressure * 10);
    designParams.put("designTemperature_C", designTemperature);
    designParams.put("outerDiameter_m", outerDiameter);
    designParams.put("outerDiameter_mm", outerDiameter * 1000);
    designParams.put("corrosionAllowance_mm", corrosionAllowance * 1000);
    designParams.put("fabricationTolerance", fabricationTolerance);
    result.put("designParameters", designParams);

    // Material properties
    Map<String, Object> material = new java.util.LinkedHashMap<String, Object>();
    material.put("smys_MPa", smys);
    material.put("smts_MPa", smts);
    material.put("youngsModulus_MPa", youngsModulus);
    material.put("poissonsRatio", poissonsRatio);
    material.put("thermalExpansion_perK", thermalExpansion);
    material.put("steelDensity_kgm3", steelDensity);
    result.put("materialProperties", material);

    // Design factors
    Map<String, Object> factors = new java.util.LinkedHashMap<String, Object>();
    factors.put("designFactor_F", designFactor);
    factors.put("jointFactor_E", jointFactor);
    factors.put("temperatureDerating_T", temperatureDerating);
    factors.put("weldJointEfficiency", weldJointEfficiency);
    factors.put("locationClass", locationClass);
    result.put("designFactors", factors);

    // Calculated results - Basic Design
    Map<String, Object> results = new java.util.LinkedHashMap<String, Object>();
    results.put("minimumWallThickness_mm", minimumWallThickness * 1000);
    results.put("nominalWallThickness_mm", nominalWallThickness * 1000);
    results.put("innerDiameter_mm", innerDiameter * 1000);
    results.put("maop_MPa", maop);
    results.put("maop_bar", maop * 10);
    results.put("testPressure_MPa", calculateTestPressure());
    results.put("testPressure_bar", calculateTestPressure() * 10);
    results.put("hoopStress_MPa", hoopStress);
    results.put("longitudinalStress_MPa", longitudinalStress);
    results.put("vonMisesStress_MPa", vonMisesStress);
    if (smys > 0) {
      results.put("hoopStressRatio_percentSMYS", 100 * hoopStress / smys);
      results.put("vonMisesRatio_percentSMYS", 100 * vonMisesStress / smys);
      results.put("safetyMargin_percent", 100 * calculateSafetyMargin());
    }
    results.put("designIsSafe", isDesignSafe());
    result.put("calculatedResults", results);

    // Coating and Insulation
    Map<String, Object> coatingData = new java.util.LinkedHashMap<String, Object>();
    coatingData.put("coatingType", coatingType);
    coatingData.put("coatingThickness_mm", coatingThickness * 1000);
    coatingData.put("insulationType", insulationType);
    coatingData.put("insulationThickness_mm", insulationThickness * 1000);
    coatingData.put("concreteCoatingThickness_mm", concreteCoatingThickness * 1000);
    result.put("coatingAndInsulation", coatingData);

    // Weight and Buoyancy
    Map<String, Object> weightData = new java.util.LinkedHashMap<String, Object>();
    weightData.put("steelWeight_kgm", steelWeightPerMeter);
    weightData.put("coatingWeight_kgm", coatingWeightPerMeter);
    weightData.put("insulationWeight_kgm", insulationWeightPerMeter);
    weightData.put("concreteWeight_kgm", concreteWeightPerMeter);
    weightData.put("totalDryWeight_kgm", totalDryWeightPerMeter);
    weightData.put("contentWeight_kgm", contentWeightPerMeter);
    weightData.put("submergedWeight_kgm", submergedWeightPerMeter);
    weightData.put("totalPipelineWeight_kg", totalPipelineWeight);
    result.put("weightAndBuoyancy", weightData);

    // Surface Areas
    Map<String, Object> surfaceData = new java.util.LinkedHashMap<String, Object>();
    surfaceData.put("externalSurfaceArea_m2m", externalSurfaceAreaPerMeter);
    surfaceData.put("internalSurfaceArea_m2m", internalSurfaceAreaPerMeter);
    surfaceData.put("totalExternalSurfaceArea_m2", totalExternalSurfaceArea);
    result.put("surfaceAreas", surfaceData);

    // Joints and Welds
    Map<String, Object> jointsData = new java.util.LinkedHashMap<String, Object>();
    jointsData.put("numberOfJoints", numberOfJoints);
    jointsData.put("numberOfFieldWelds", numberOfFieldWelds);
    jointsData.put("standardJointLength_m", STANDARD_JOINT_LENGTH);
    result.put("jointsAndWelds", jointsData);

    // Flanges and Connections
    Map<String, Object> connectionsData = new java.util.LinkedHashMap<String, Object>();
    connectionsData.put("flangeClass", flangeClass);
    connectionsData.put("numberOfFlangePairs", numberOfFlangePairs);
    connectionsData.put("numberOfValves", numberOfValves);
    connectionsData.put("valveType", valveType);
    result.put("flangesAndConnections", connectionsData);

    // Supports and Expansion
    Map<String, Object> supportsData = new java.util.LinkedHashMap<String, Object>();
    supportsData.put("supportSpacing_m", supportSpacing);
    supportsData.put("numberOfSupports", numberOfSupports);
    supportsData.put("expansionLoopLength_m", expansionLoopLength);
    supportsData.put("numberOfExpansionLoops", numberOfExpansionLoops);
    supportsData.put("anchorSpacing_m", anchorSpacing);
    supportsData.put("numberOfAnchors", numberOfAnchors);
    result.put("supportsAndExpansion", supportsData);

    // Detailed Design Results
    Map<String, Object> detailedDesign = new java.util.LinkedHashMap<String, Object>();
    detailedDesign.put("minimumBendRadius_m", minimumBendRadius);
    detailedDesign.put("externalPressure_MPa", externalPressure);
    detailedDesign.put("collapsePressure_MPa", collapsePressure);
    detailedDesign.put("propagationBucklingPressure_MPa", propagationBucklingPressure);
    detailedDesign.put("allowableSpanLength_m", allowableSpanLength);
    detailedDesign.put("fatigueLife_years", fatigueLife);
    result.put("detailedDesignResults", detailedDesign);

    // Installation Parameters
    Map<String, Object> installData = new java.util.LinkedHashMap<String, Object>();
    installData.put("installationMethod", installationMethod);
    installData.put("waterDepth_m", waterDepth);
    installData.put("burialDepth_m", burialDepth);
    installData.put("ambientTemperature_C", ambientTemperature);
    result.put("installationParameters", installData);

    // Cost Estimation
    Map<String, Object> costData = new java.util.LinkedHashMap<String, Object>();
    costData.put("steelMaterialCost_USD", steelMaterialCost);
    costData.put("coatingCost_USD", coatingCost);
    costData.put("insulationCost_USD", insulationCost);
    costData.put("concreteCost_USD", concreteCost);
    costData.put("weldingCost_USD", weldingCost);
    costData.put("flangesAndFittingsCost_USD", flangesAndFittingsCost);
    costData.put("valvesCost_USD", valvesCost);
    costData.put("supportsAndAnchorsCost_USD", supportsAndAnchorsCost);
    costData.put("installationCost_USD", installationCost);
    costData.put("totalDirectCost_USD", totalDirectCost);
    costData.put("engineeringCost_USD", engineeringCost);
    costData.put("testingCost_USD", testingCost);
    costData.put("contingencyCost_USD", contingencyCost);
    costData.put("totalProjectCost_USD", totalProjectCost);
    result.put("costEstimation", costData);

    // Labor Estimation
    Map<String, Object> laborData = new java.util.LinkedHashMap<String, Object>();
    laborData.put("weldingManhoursPerJoint", weldingManhoursPerJoint);
    laborData.put("installationManhoursPerMeter", installationManhoursPerMeter);
    laborData.put("totalLaborManhours", totalLaborManhours);
    result.put("laborEstimation", laborData);

    // Cost Rate Assumptions
    Map<String, Object> rateData = new java.util.LinkedHashMap<String, Object>();
    rateData.put("steelPrice_USDkg", steelPricePerKg);
    rateData.put("coatingPrice_USDm2", coatingPricePerM2);
    rateData.put("insulationPrice_USDm3", insulationPricePerM3);
    rateData.put("concretePrice_USDm3", concretePricePerM3);
    rateData.put("fieldWeldCost_USD", fieldWeldCost);
    rateData.put("flangePairCost_USD", flangePairCost);
    rateData.put("valveCost_USD", valveCost);
    rateData.put("supportCost_USD", supportCost);
    rateData.put("anchorCost_USD", anchorCost);
    rateData.put("installationCostPerMeter_USD", installationCostPerMeter);
    rateData.put("engineeringCostPercentage", engineeringCostPercentage);
    rateData.put("testingCostPercentage", testingCostPercentage);
    rateData.put("contingencyPercentage", contingencyPercentage);
    result.put("costRateAssumptions", rateData);

    return result;
  }

  // ============================================================================
  // DATABASE INTEGRATION (DEPRECATED - USE PipelineMechanicalDesign INSTEAD)
  // ============================================================================

  /**
   * Load material properties from database.
   *
   * <p>
   * This method queries the NeqSim process design database for material properties based on the
   * specified material grade. Supported tables: MaterialPipeProperties.
   * </p>
   *
   * @param grade API 5L material grade (e.g., "X52", "X65", "X70")
   * @deprecated Use {@link PipelineMechanicalDesign#loadMaterialFromDatabase(String)} instead. The
   *             PipelineMechanicalDesign class provides centralized database access via
   *             PipelineMechanicalDesignDataSource, following the same pattern as separators.
   */
  @Deprecated
  public void loadMaterialFromDatabase(String grade) {
    this.materialGrade = grade;

    try (neqsim.util.database.NeqSimProcessDesignDataBase database =
        new neqsim.util.database.NeqSimProcessDesignDataBase()) {
      String query = "SELECT * FROM MaterialPipeProperties WHERE grade='" + grade + "'";
      try (java.sql.ResultSet dataSet = database.getResultSet(query)) {
        if (dataSet.next()) {
          String smysStr = dataSet.getString("minimumYeildStrength");
          if (smysStr != null && !smysStr.trim().isEmpty()) {
            // Convert from psi to MPa if needed (database may store in psi)
            double smysValue = Double.parseDouble(smysStr);
            // Assume database stores in psi, convert to MPa
            this.smys = smysValue * 0.00689476;
          }
          // Try to get SMTS if available
          try {
            String smtsStr = dataSet.getString("minimumTensileStrength");
            if (smtsStr != null && !smtsStr.trim().isEmpty()) {
              this.smts = Double.parseDouble(smtsStr) * 0.00689476;
            }
          } catch (java.sql.SQLException e) {
            // Column doesn't exist, use default calculation
            this.smts = smys * 1.15;
          }
        }
      }
    } catch (Exception e) {
      // Fall back to built-in API 5L data
      updateMaterialProperties();
    }
  }

  /**
   * Load design factors from database based on company identifier.
   *
   * <p>
   * This method queries the TechnicalRequirements_Process table for company-specific design
   * factors.
   * </p>
   *
   * @param company company identifier (e.g., "Equinor", "Statoil")
   * @deprecated Use {@link PipelineMechanicalDesign#loadDesignFactorsFromDatabase()} instead. The
   *             PipelineMechanicalDesign class provides centralized database access via
   *             PipelineMechanicalDesignDataSource, following the same pattern as separators.
   */
  @Deprecated
  public void loadDesignFactorsFromDatabase(String company) {
    try (neqsim.util.database.NeqSimProcessDesignDataBase database =
        new neqsim.util.database.NeqSimProcessDesignDataBase()) {
      String query = "SELECT * FROM TechnicalRequirements_Process WHERE EQUIPMENTTYPE='Pipeline'"
          + " AND Company='" + company + "'";
      try (java.sql.ResultSet dataSet = database.getResultSet(query)) {
        while (dataSet.next()) {
          String specName = dataSet.getString("SPECIFICATION");
          String maxValue = dataSet.getString("MAXVALUE");
          if (specName != null && maxValue != null) {
            if ("designFactor".equalsIgnoreCase(specName)) {
              this.designFactor = Double.parseDouble(maxValue);
            } else if ("jointFactor".equalsIgnoreCase(specName)) {
              this.jointFactor = Double.parseDouble(maxValue);
            } else if ("corrosionAllowance".equalsIgnoreCase(specName)) {
              this.corrosionAllowance = Double.parseDouble(maxValue) / 1000.0; // mm to m
            }
          }
        }
      }
    } catch (Exception e) {
      // Fall back to defaults, log warning
      org.apache.logging.log4j.LogManager.getLogger(PipeMechanicalDesignCalculator.class)
          .warn("Could not load design factors from database for company: " + company, e);
    }
  }

  /**
   * Load all design data from database.
   *
   * <p>
   * This method loads both material properties and design factors from the database.
   * </p>
   *
   * @param materialGrade API 5L material grade
   * @param company company identifier
   * @deprecated Use {@link PipelineMechanicalDesign#loadFromDatabase()} instead. The
   *             PipelineMechanicalDesign class provides centralized database access via
   *             PipelineMechanicalDesignDataSource, following the same pattern as separators.
   */
  @Deprecated
  public void loadFromDatabase(String materialGrade, String company) {
    loadMaterialFromDatabase(materialGrade);
    loadDesignFactorsFromDatabase(company);
  }
}
