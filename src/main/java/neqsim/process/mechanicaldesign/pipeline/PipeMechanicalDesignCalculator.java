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

  /** Map of API 5L material grades to SMYS (MPa). */
  private static final Map<String, double[]> API_5L_GRADES;

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
    result.put("materialProperties", material);

    // Design factors
    Map<String, Object> factors = new java.util.LinkedHashMap<String, Object>();
    factors.put("designFactor_F", designFactor);
    factors.put("jointFactor_E", jointFactor);
    factors.put("temperatureDerating_T", temperatureDerating);
    factors.put("weldJointEfficiency", weldJointEfficiency);
    factors.put("locationClass", locationClass);
    result.put("designFactors", factors);

    // Calculated results
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
