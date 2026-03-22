package neqsim.process.mechanicaldesign.heatexchanger;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.mechanicaldesign.heatexchanger.TEMAStandard.BaffleType;
import neqsim.process.mechanicaldesign.heatexchanger.TEMAStandard.RearHeadType;
import neqsim.process.mechanicaldesign.heatexchanger.TEMAStandard.StandardTubeSize;
import neqsim.process.mechanicaldesign.heatexchanger.TEMAStandard.TEMAClass;
import neqsim.process.mechanicaldesign.heatexchanger.TEMAStandard.TEMAConfiguration;
import neqsim.process.mechanicaldesign.heatexchanger.TEMAStandard.TubePitchPattern;
import neqsim.util.database.NeqSimProcessDesignDataBase;

/**
 * Shell and tube heat exchanger design calculator per TEMA standards.
 *
 * <p>
 * This calculator performs detailed mechanical design for shell and tube heat exchangers following
 * TEMA (Tubular Exchanger Manufacturers Association) standards. It calculates:
 * <ul>
 * <li>Tube bundle geometry (tube count, length, passes)</li>
 * <li>Shell dimensions and type selection</li>
 * <li>Baffle configuration and spacing</li>
 * <li>Thermal-hydraulic performance estimates</li>
 * <li>Weight and cost estimates</li>
 * </ul>
 *
 * <h2>TEMA Classes</h2>
 * <ul>
 * <li>Class R: Severe service (refineries, petrochemical)</li>
 * <li>Class C: Moderate service (chemical, process)</li>
 * <li>Class B: General service (HVAC, commercial)</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * ShellAndTubeDesignCalculator calc = new ShellAndTubeDesignCalculator();
 * calc.setTemaDesignation("AES");
 * calc.setTemaClass(TEMAClass.R);
 * calc.setRequiredArea(150.0); // m2
 * calc.setShellSidePressure(30.0); // bara
 * calc.setTubeSidePressure(15.0); // bara
 * calc.setDesignTemperature(200.0); // °C
 * calc.calculate();
 *
 * System.out.println("Shell ID: " + calc.getShellInsideDiameter() + " mm");
 * System.out.println("Tube count: " + calc.getTubeCount());
 * System.out.println("Total weight: " + calc.getTotalWeight() + " kg");
 * }</pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 * @see TEMAStandard
 */
public class ShellAndTubeDesignCalculator {

  // Input parameters
  private String temaDesignation = "AES";
  private TEMAClass temaClass = TEMAClass.R;
  private double requiredArea = 100.0; // m2
  private double shellSidePressure = 10.0; // bara
  private double tubeSidePressure = 10.0; // bara
  private double designTemperature = 150.0; // °C
  private String shellMaterial = "Carbon Steel SA-516-70";
  private String tubeMaterial = "Carbon Steel SA-179";
  private double corrosionAllowanceShell = 3.175; // mm (1/8")
  private double corrosionAllowanceTube = 0.0; // mm

  // Tube parameters
  private StandardTubeSize tubeSize = StandardTubeSize.TUBE_3_4_INCH;
  private double tubeWallThickness = 2.108; // mm (BWG 14)
  private double tubeLength = 6096.0; // mm (20 ft)
  private int tubePasses = 2;
  private TubePitchPattern pitchPattern = TubePitchPattern.TRIANGULAR_30;
  private double tubePitchRatio = 1.25;

  // Baffle parameters
  private BaffleType baffleType = BaffleType.SINGLE_SEGMENTAL;
  private double baffleCut = 0.25; // 25% cut
  private int baffleCount = 0; // Calculated

  // Calculated results
  private double shellInsideDiameter = 0.0; // mm
  private double shellOutsideDiameter = 0.0; // mm
  private double shellWallThickness = 0.0; // mm
  private int tubeCount = 0;
  private double actualArea = 0.0; // m2
  private double baffleSpacing = 0.0; // mm
  private double tubePitch = 0.0; // mm

  // Weights (kg)
  private double shellWeight = 0.0;
  private double tubeWeight = 0.0;
  private double tubesheetWeight = 0.0;
  private double headWeight = 0.0;
  private double baffleWeight = 0.0;
  private double totalDryWeight = 0.0;
  private double operatingWeight = 0.0;

  // Cost estimates
  private double materialCost = 0.0;
  private double fabricationCost = 0.0;
  private double totalCost = 0.0;

  // ASME VIII pressure design results
  private double tubesheetThicknessUHX = 0.0; // mm per UHX-13
  private double nozzleReinforcementArea = 0.0; // mm2 per UG-37
  private boolean nozzleReinforcementAdequate = false;
  private double mawpShellSide = 0.0; // bara back-calculated MAWP
  private double mawpTubeSide = 0.0; // bara
  private double hydroTestPressureShell = 0.0; // bara per UG-99
  private double hydroTestPressureTube = 0.0; // bara
  private double shellJointEfficiency = 0.85;

  // Material grade tracking
  private String shellMaterialGrade = "SA-516-70";
  private String tubeMaterialGrade = "SA-179";

  // Material properties from database
  private double shellAllowableStress = 138.0; // MPa
  private double tubeAllowableStress = 117.0; // MPa
  private double tubeThermalConductivity = 52.0; // W/mK
  private double shellSmys = 260.0; // MPa
  private double tubeSmys = 228.0; // MPa

  // NACE / sour service assessment
  private boolean sourServiceAssessment = false;
  private boolean shellNACECompliant = false;
  private boolean tubeNACECompliant = false;
  private double h2sPartialPressure = 0.0; // bar
  private boolean sourServiceRequired = false;
  private int shellMaxHardnessHV = 248;
  private int tubeMaxHardnessHV = 248;
  private List<String> appliedStandards = new ArrayList<String>();
  private List<String> naceIssues = new ArrayList<String>();

  // Nozzle parameters for reinforcement check
  private double nozzleDiameter = 150.0; // mm (default 6 inch)
  private double nozzleWallThickness = 7.11; // mm (Sch 40)

  /**
   * Creates a new ShellAndTubeDesignCalculator with default parameters.
   */
  public ShellAndTubeDesignCalculator() {
    // Default constructor
  }

  /**
   * Creates a calculator from existing heat exchanger equipment.
   *
   * @param equipment heat exchanger equipment
   */
  public ShellAndTubeDesignCalculator(ProcessEquipmentInterface equipment) {
    if (equipment instanceof HeatExchanger) {
      HeatExchanger hx = (HeatExchanger) equipment;
      // Extract process conditions
      if (hx.getOutletStream() != null) {
        this.tubeSidePressure = hx.getOutletStream().getPressure();
        this.designTemperature = hx.getOutletStream().getTemperature() - 273.15;
      }
    }
  }

  /**
   * Performs the complete TEMA-based design calculation.
   */
  public void calculate() {
    // Get TEMA configuration
    TEMAConfiguration config = TEMAStandard.getConfiguration(temaDesignation);
    if (config == null) {
      config = TEMAStandard.createConfiguration(temaDesignation.charAt(0),
          temaDesignation.charAt(1), temaDesignation.charAt(2));
    }

    // Load material properties from database
    loadMaterialProperties();

    // Calculate tube geometry
    calculateTubeBundle();

    // Calculate shell dimensions
    calculateShellDimensions(config);

    // Calculate baffles
    calculateBaffles();

    // ASME VIII calculations
    calculateTubesheetThicknessUHX();
    calculateNozzleReinforcement();
    calculateMAWP();
    calculateHydroTestPressure();

    // NACE sour service assessment
    if (sourServiceAssessment) {
      performNACEAssessment();
    }

    // Calculate weights
    calculateWeights(config);

    // Calculate costs
    calculateCosts(config);
  }

  /**
   * Calculates tube bundle parameters.
   */
  private void calculateTubeBundle() {
    double tubeOD = tubeSize.getOuterDiameterMm();
    double tubeID = tubeOD - 2.0 * tubeWallThickness;

    // Tube pitch
    tubePitch = tubeOD * tubePitchRatio;

    // Area per tube (outside surface)
    double areaPerTube = Math.PI * tubeOD / 1000.0 * tubeLength / 1000.0; // m2

    // Required tube count
    tubeCount = (int) Math.ceil(requiredArea / areaPerTube);

    // Round to nearest even number for tube passes
    if (tubeCount % tubePasses != 0) {
      tubeCount = ((tubeCount / tubePasses) + 1) * tubePasses;
    }

    // Actual area
    actualArea = tubeCount * areaPerTube;
  }

  /**
   * Calculates shell dimensions.
   *
   * @param config TEMA configuration
   */
  private void calculateShellDimensions(TEMAConfiguration config) {
    double tubeOD = tubeSize.getOuterDiameterMm();

    // Estimate shell diameter from tube count
    // Using tube count layout formula inverted
    double areaPerTube;
    int layoutAngle = pitchPattern.getLayoutAngle();
    if (layoutAngle == 30 || layoutAngle == 60) {
      areaPerTube = tubePitch * tubePitch * Math.sqrt(3.0) / 2.0;
    } else {
      areaPerTube = tubePitch * tubePitch;
    }

    // Pass lane factor
    double passLaneFactor = 1.0 - 0.05 * (tubePasses - 1);

    // Required bundle area
    double bundleArea = tubeCount * areaPerTube / passLaneFactor;

    // Bundle diameter
    double bundleDiameter = 2.0 * Math.sqrt(bundleArea / Math.PI);

    // Shell clearance (depends on rear head type)
    double clearance;
    RearHeadType rearHead = config.getRearHead();
    if (rearHead == RearHeadType.T) {
      clearance = 90.0; // Pull-through floating head
    } else if (rearHead == RearHeadType.S) {
      clearance = 35.0; // Split ring floating head
    } else if (rearHead == RearHeadType.U) {
      clearance = 15.0; // U-tube
    } else {
      clearance = 10.0; // Fixed tubesheet
    }

    shellInsideDiameter = bundleDiameter + 2.0 * tubeOD + clearance;

    // Round to standard shell size
    shellInsideDiameter = roundToStandardShellSize(shellInsideDiameter);

    // Shell wall thickness (simplified ASME calculation)
    double designPressure = Math.max(shellSidePressure, 1.0) * 1.1; // 10% margin
    double allowableStress = getAllowableStress(shellMaterial, designTemperature);
    double jointEfficiency = 0.85; // Typical for spot-examined welds

    // Circumferential stress formula: t = P*R / (S*E - 0.6*P)
    double shellRadius = shellInsideDiameter / 2.0;
    shellWallThickness = (designPressure * 0.1 * shellRadius)
        / (allowableStress * jointEfficiency - 0.6 * designPressure * 0.1);
    shellWallThickness += corrosionAllowanceShell;

    // Minimum wall thickness per TEMA
    double minWall = temaClass.getMinTubeWallMm();
    shellWallThickness = Math.max(shellWallThickness, minWall);

    // Round up to standard plate thickness
    shellWallThickness = roundToStandardPlateThickness(shellWallThickness);

    shellOutsideDiameter = shellInsideDiameter + 2.0 * shellWallThickness;
  }

  /**
   * Calculates baffle parameters.
   */
  private void calculateBaffles() {
    // Baffle spacing limits
    double minSpacing = TEMAStandard.getMinBaffleSpacing(shellInsideDiameter, temaClass);
    double maxSpacing = TEMAStandard.getMaxBaffleSpacing(shellInsideDiameter);
    double maxTubeSpan =
        TEMAStandard.getMaxUnsupportedSpan(tubeSize.getOuterDiameterMm(), tubeMaterial);

    // Target spacing (balance heat transfer vs pressure drop)
    // Typically 0.3-0.5 of shell diameter
    double targetSpacing = shellInsideDiameter * 0.4;

    // Constrain to limits
    baffleSpacing = Math.max(minSpacing, Math.min(targetSpacing, maxSpacing));
    baffleSpacing = Math.min(baffleSpacing, maxTubeSpan);

    // Calculate baffle count
    // Tube length minus inlet/outlet spacing
    double effectiveLength = tubeLength - 2.0 * baffleSpacing;
    baffleCount = (int) Math.max(1, Math.floor(effectiveLength / baffleSpacing));
  }

  /**
   * Calculates component weights.
   *
   * @param config TEMA configuration
   */
  private void calculateWeights(TEMAConfiguration config) {
    double steelDensity = 7850.0; // kg/m3

    // Shell weight
    double shellVolume = Math.PI / 4.0
        * (Math.pow(shellOutsideDiameter / 1000.0, 2) - Math.pow(shellInsideDiameter / 1000.0, 2))
        * tubeLength / 1000.0;
    shellWeight = shellVolume * steelDensity;

    // Tube weight
    double tubeOD = tubeSize.getOuterDiameterMm();
    double tubeID = tubeOD - 2.0 * tubeWallThickness;
    double tubeVolume = Math.PI / 4.0
        * (Math.pow(tubeOD / 1000.0, 2) - Math.pow(tubeID / 1000.0, 2)) * tubeLength / 1000.0;
    tubeWeight = tubeCount * tubeVolume * steelDensity;

    // Tubesheet weight (2 for fixed, 1 for U-tube)
    double tubesheetThickness = estimateTubesheetThickness();
    double tubesheetVolume =
        Math.PI / 4.0 * Math.pow(shellOutsideDiameter / 1000.0, 2) * tubesheetThickness / 1000.0;
    int tubesheetCount = (config.getRearHead() == RearHeadType.U) ? 1 : 2;
    tubesheetWeight = tubesheetCount * tubesheetVolume * steelDensity;

    // Head weight (approximate as hemispherical)
    double headThickness = shellWallThickness * 0.5; // Hemispherical is thinner
    double headVolume =
        2.0 * Math.PI / 3.0 * (Math.pow((shellOutsideDiameter / 2.0 + headThickness) / 1000.0, 3)
            - Math.pow(shellOutsideDiameter / 2.0 / 1000.0, 3));
    headWeight = 2 * headVolume * steelDensity;

    // Baffle weight
    double baffleThickness = 6.35; // 1/4" typical
    double baffleArea =
        Math.PI / 4.0 * Math.pow(shellInsideDiameter / 1000.0, 2) * (1.0 - baffleCut);
    double baffleVolume = baffleArea * baffleThickness / 1000.0;
    baffleWeight = baffleCount * baffleVolume * steelDensity;

    // Total dry weight
    totalDryWeight = shellWeight + tubeWeight + tubesheetWeight + headWeight + baffleWeight;

    // Operating weight (add water as approximation)
    double waterDensity = 1000.0; // kg/m3
    double shellSideVolume =
        Math.PI / 4.0 * Math.pow(shellInsideDiameter / 1000.0, 2) * tubeLength / 1000.0
            - tubeCount * Math.PI / 4.0 * Math.pow(tubeOD / 1000.0, 2) * tubeLength / 1000.0;
    double tubeSideVolume =
        tubeCount * Math.PI / 4.0 * Math.pow(tubeID / 1000.0, 2) * tubeLength / 1000.0;
    operatingWeight = totalDryWeight + (shellSideVolume + tubeSideVolume) * waterDensity;
  }

  /**
   * Estimates tubesheet thickness per TEMA/ASME.
   *
   * @return tubesheet thickness in mm
   */
  private double estimateTubesheetThickness() {
    // Simplified TEMA formula
    double P = Math.max(shellSidePressure, tubeSidePressure) * 0.1; // MPa
    double G = shellInsideDiameter; // Gasket diameter ≈ shell ID for simplicity
    double S = getAllowableStress(shellMaterial, designTemperature);

    // TEMA formula: t = G * sqrt(0.785 * P / S)
    double thickness = G * Math.sqrt(0.785 * P / S);

    // Add ligament efficiency factor for tube holes
    thickness /= 0.7;

    // Add corrosion allowance
    thickness += corrosionAllowanceShell;

    // Minimum per TEMA
    double minThickness = 19.05; // 3/4" for Class R
    if (temaClass == TEMAClass.C) {
      minThickness = 15.875; // 5/8"
    } else if (temaClass == TEMAClass.B) {
      minThickness = 12.7; // 1/2"
    }

    return Math.max(thickness, minThickness);
  }

  /**
   * Calculates cost estimates.
   *
   * @param config TEMA configuration
   */
  private void calculateCosts(TEMAConfiguration config) {
    // Material costs ($/kg)
    double carbonSteelPrice = 1.5;
    double stainlessPrice = 5.0;

    double shellMaterialPrice = carbonSteelPrice;
    double tubeMaterialPrice = carbonSteelPrice;

    if (shellMaterial.toUpperCase().contains("STAINLESS")) {
      shellMaterialPrice = stainlessPrice;
    }
    if (tubeMaterial.toUpperCase().contains("STAINLESS")) {
      tubeMaterialPrice = stainlessPrice;
    }

    // Material cost
    materialCost = shellWeight * shellMaterialPrice + tubeWeight * tubeMaterialPrice
        + (tubesheetWeight + headWeight + baffleWeight) * shellMaterialPrice;

    // Fabrication cost (typically 2-4x material cost for heat exchangers)
    double fabFactor = 2.5 * config.getCostFactor() * temaClass.getCostFactor();
    fabricationCost = materialCost * fabFactor;

    // Total cost
    totalCost = materialCost + fabricationCost;
  }

  /**
   * Gets allowable stress for material at temperature. Uses database value if loaded, otherwise
   * falls back to simplified ASME Section II tables.
   *
   * @param material material name
   * @param temperature temperature in degrees C
   * @return allowable stress in MPa
   */
  private double getAllowableStress(String material, double temperature) {
    double baseStress;
    if (material.equals(shellMaterial)) {
      baseStress = shellAllowableStress;
    } else if (material.equals(tubeMaterial)) {
      baseStress = tubeAllowableStress;
    } else {
      baseStress = 137.9;
    }

    // Temperature derating (simplified ASME II Part D)
    if (temperature > 400) {
      baseStress *= 0.55;
    } else if (temperature > 300) {
      baseStress *= 0.70;
    } else if (temperature > 200) {
      baseStress *= 0.85;
    }

    return baseStress;
  }

  /**
   * Loads material properties from HeatExchangerTubeMaterials database table.
   */
  private void loadMaterialProperties() {
    loadMaterialFromDB(shellMaterialGrade, true);
    loadMaterialFromDB(tubeMaterialGrade, false);
  }

  /**
   * Loads a single material's properties from the database.
   *
   * @param materialGrade the material grade to look up
   * @param isShell true if this is a shell material, false for tube
   */
  private void loadMaterialFromDB(String materialGrade, boolean isShell) {
    try (NeqSimProcessDesignDataBase database = new NeqSimProcessDesignDataBase();
        ResultSet rs = database
            .getResultSet("SELECT * FROM HeatExchangerTubeMaterials WHERE MaterialGrade='"
                + materialGrade + "'")) {
      if (rs.next()) {
        double allowable = rs.getDouble("AllowableStress_MPa");
        double smys = rs.getDouble("SMYS_MPa");
        double thermalCond = rs.getDouble("ThermalConductivity_WpmK");
        String naceComp = rs.getString("NACECompliant");
        boolean nace = "Yes".equalsIgnoreCase(naceComp);

        if (isShell) {
          shellAllowableStress = allowable;
          shellSmys = smys;
          shellNACECompliant = nace;
          shellMaterial = rs.getString("Specification") + " " + materialGrade;
        } else {
          tubeAllowableStress = allowable;
          tubeSmys = smys;
          tubeThermalConductivity = thermalCond;
          tubeNACECompliant = nace;
          tubeMaterial = rs.getString("Specification") + " " + materialGrade;
        }
      }
    } catch (Exception ex) {
      // Use default values if database lookup fails
    }
  }

  /**
   * Calculates tubesheet thickness per ASME VIII UHX-13 for fixed tubesheet exchangers.
   */
  private void calculateTubesheetThicknessUHX() {
    double P = Math.max(shellSidePressure, tubeSidePressure) * 0.1; // MPa
    double G = shellInsideDiameter; // mm gasket diameter
    double S = getAllowableStress(shellMaterial, designTemperature);

    // UHX-13 simplified: t = G * sqrt(F * P / S)
    // F depends on ligament efficiency and fixed vs floating
    double tubeOD = tubeSize.getOuterDiameterMm();
    double ligamentEfficiency = 1.0 - tubeOD / (tubeOD * tubePitchRatio);
    ligamentEfficiency = Math.max(ligamentEfficiency, 0.25);
    double F = 0.785 / ligamentEfficiency;

    double thickness = G * Math.sqrt(F * P / S);
    thickness += corrosionAllowanceShell;

    // TEMA minimums per class
    double minThickness = 19.05;
    if (temaClass == TEMAClass.C) {
      minThickness = 15.875;
    } else if (temaClass == TEMAClass.B) {
      minThickness = 12.7;
    }

    tubesheetThicknessUHX = Math.max(thickness, minThickness);
    appliedStandards.add("ASME VIII Div.1 UHX-13 (Tubesheet)");
  }

  /**
   * Calculates nozzle reinforcement per ASME VIII UG-37.
   */
  private void calculateNozzleReinforcement() {
    double P = Math.max(shellSidePressure, tubeSidePressure) * 0.1; // MPa
    double S = getAllowableStress(shellMaterial, designTemperature);
    double E = shellJointEfficiency;

    // Required area removed: A_required = d * t_r * F + 2 * t_n * t_r * (1 - fr)
    // where t_r = required thickness without nozzle, d = nozzle finished diameter
    // F = correction factor (1.0 for internal pressure on shell)
    double t_r = (P * shellInsideDiameter / 2.0) / (S * E - 0.6 * P);
    double d = nozzleDiameter; // finished nozzle ID
    double requiredArea = d * t_r * 1.0;

    // Available reinforcement from shell excess thickness:
    // A1 = (E * t - F * t_r) * d (larger of two sides)
    double t = shellWallThickness - corrosionAllowanceShell;
    double A1 = Math.max((t - t_r), 0.0) * d * 2.0;

    // Available reinforcement from nozzle wall:
    // A2 = (t_n - t_rn) * 5t (outward) + (t_n - c_n) * 5t (inward)
    double t_rn = (P * nozzleDiameter / 2.0) / (S * E - 0.6 * P);
    double A2 = Math.max((nozzleWallThickness - t_rn), 0.0) * 2.0 * 2.5 * t;

    double totalAvailable = A1 + A2;
    nozzleReinforcementArea = totalAvailable;
    nozzleReinforcementAdequate = totalAvailable >= requiredArea;
    appliedStandards.add("ASME VIII Div.1 UG-37 (Nozzle Reinforcement)");
  }

  /**
   * Back-calculates Maximum Allowable Working Pressure (MAWP) for shell and tube sides.
   */
  private void calculateMAWP() {
    double S = getAllowableStress(shellMaterial, designTemperature);
    double E = shellJointEfficiency;
    double t_shell = shellWallThickness - corrosionAllowanceShell;
    double R_shell = shellInsideDiameter / 2.0;

    // MAWP shell = S*E*t / (R + 0.6*t) per UG-27
    double mawpMPa = (S * E * t_shell) / (R_shell + 0.6 * t_shell);
    mawpShellSide = mawpMPa * 10.0; // convert to bara

    // MAWP tube side (based on tube wall)
    double S_tube = getAllowableStress(tubeMaterial, designTemperature);
    double tubeOD = tubeSize.getOuterDiameterMm();
    double t_tube = tubeWallThickness - corrosionAllowanceTube;
    double R_tube = (tubeOD - 2.0 * tubeWallThickness) / 2.0;
    double mawpTubeMPa = (S_tube * 1.0 * t_tube) / (R_tube + 0.6 * t_tube);
    mawpTubeSide = mawpTubeMPa * 10.0;

    appliedStandards.add("ASME VIII Div.1 UG-27 (MAWP)");
  }

  /**
   * Calculates hydrostatic test pressure per ASME VIII UG-99.
   */
  private void calculateHydroTestPressure() {
    // UG-99(b): P_test = 1.3 * MAWP * (S_test_temp / S_design_temp)
    // For simplicity, ratio of allowable stresses at test vs design temperature ~ 1.0
    // unless design temp is well above ambient
    double stressRatio = 1.0;
    if (designTemperature > 100) {
      // Allowable stress at ambient is typically higher
      stressRatio = getAllowableStress(shellMaterial, 20.0)
          / getAllowableStress(shellMaterial, designTemperature);
    }

    hydroTestPressureShell = 1.3 * mawpShellSide * stressRatio;
    hydroTestPressureTube = 1.3 * mawpTubeSide * stressRatio;

    appliedStandards.add("ASME VIII Div.1 UG-99 (Hydrostatic Test)");
  }

  /**
   * Performs NACE MR0175/ISO 15156 sour service assessment.
   */
  private void performNACEAssessment() {
    naceIssues.clear();

    // Determine if sour service is required per NACE MR0175
    // Sour service threshold: H2S partial pressure greater than 0.3 kPa (0.003 bar)
    sourServiceRequired = h2sPartialPressure > 0.003;

    if (!sourServiceRequired) {
      appliedStandards.add("NACE MR0175/ISO 15156 (Not Required - H2S pp below threshold)");
      return;
    }

    // Check shell material NACE compliance
    if (!shellNACECompliant) {
      naceIssues.add("Shell material " + shellMaterialGrade
          + " is not NACE MR0175 compliant for sour service");
    }

    // Check tube material NACE compliance
    if (!tubeNACECompliant) {
      naceIssues.add("Tube material " + tubeMaterialGrade
          + " is not NACE MR0175 compliant for sour service");
    }

    // Hardness limits per NORSOK M-001 / NACE MR0175
    if (shellMaxHardnessHV > 248) {
      naceIssues.add("Shell hardness " + shellMaxHardnessHV + " HV exceeds 248 HV limit");
    }
    if (tubeMaxHardnessHV > 248) {
      naceIssues.add("Tube hardness " + tubeMaxHardnessHV + " HV exceeds 248 HV limit");
    }

    appliedStandards.add("NACE MR0175/ISO 15156 (Sour Service Assessment)");
    if (!naceIssues.isEmpty()) {
      appliedStandards.add("NORSOK M-001 Rev.6 (Material Hardness Limits)");
    }
  }

  /**
   * Rounds to standard shell sizes (pipe sizes).
   *
   * @param diameter diameter in mm
   * @return standard diameter
   */
  private double roundToStandardShellSize(double diameter) {
    // Standard pipe sizes in mm (NPS converted)
    double[] standardSizes = {168.3, 219.1, 273.0, 323.9, 355.6, 406.4, 457.2, 508.0, 558.8, 609.6,
        660.4, 711.2, 762.0, 812.8, 863.6, 914.4, 965.2, 1016.0, 1066.8, 1117.6, 1168.4, 1219.2,
        1270.0, 1320.8, 1371.6, 1422.4, 1473.2, 1524.0, 1574.8, 1625.6, 1676.4, 1727.2, 1778.0,
        1828.8, 1879.6, 1930.4, 1981.2, 2032.0};

    for (double size : standardSizes) {
      if (size >= diameter) {
        return size;
      }
    }
    return standardSizes[standardSizes.length - 1];
  }

  /**
   * Rounds to standard plate thickness.
   *
   * @param thickness thickness in mm
   * @return standard thickness
   */
  private double roundToStandardPlateThickness(double thickness) {
    double[] standardThicknesses = {3.175, 4.763, 6.35, 7.938, 9.525, 12.7, 15.875, 19.05, 22.225,
        25.4, 31.75, 38.1, 44.45, 50.8};

    for (double t : standardThicknesses) {
      if (t >= thickness) {
        return t;
      }
    }
    return standardThicknesses[standardThicknesses.length - 1];
  }

  // Getters and Setters

  /**
   * Sets TEMA designation (e.g., "AES").
   *
   * @param designation TEMA designation
   */
  public void setTemaDesignation(String designation) {
    this.temaDesignation = designation;
  }

  /**
   * Gets TEMA designation.
   *
   * @return TEMA designation
   */
  public String getTemaDesignation() {
    return temaDesignation;
  }

  /**
   * Sets TEMA class.
   *
   * @param temaClass TEMA class
   */
  public void setTemaClass(TEMAClass temaClass) {
    this.temaClass = temaClass;
  }

  /**
   * Gets TEMA class.
   *
   * @return TEMA class
   */
  public TEMAClass getTemaClass() {
    return temaClass;
  }

  /**
   * Sets required heat transfer area in m2.
   *
   * @param area required area
   */
  public void setRequiredArea(double area) {
    this.requiredArea = area;
  }

  /**
   * Gets required area.
   *
   * @return required area in m2
   */
  public double getRequiredArea() {
    return requiredArea;
  }

  /**
   * Gets actual calculated area.
   *
   * @return actual area in m2
   */
  public double getActualArea() {
    return actualArea;
  }

  /**
   * Sets shell side design pressure in bara.
   *
   * @param pressure pressure
   */
  public void setShellSidePressure(double pressure) {
    this.shellSidePressure = pressure;
  }

  /**
   * Sets tube side design pressure in bara.
   *
   * @param pressure pressure
   */
  public void setTubeSidePressure(double pressure) {
    this.tubeSidePressure = pressure;
  }

  /**
   * Sets design temperature in °C.
   *
   * @param temperature temperature
   */
  public void setDesignTemperature(double temperature) {
    this.designTemperature = temperature;
  }

  /**
   * Sets tube size.
   *
   * @param tubeSize standard tube size
   */
  public void setTubeSize(StandardTubeSize tubeSize) {
    this.tubeSize = tubeSize;
  }

  /**
   * Sets tube length in mm.
   *
   * @param length tube length
   */
  public void setTubeLength(double length) {
    this.tubeLength = length;
  }

  /**
   * Sets number of tube passes.
   *
   * @param passes tube passes
   */
  public void setTubePasses(int passes) {
    this.tubePasses = passes;
  }

  /**
   * Gets shell inside diameter in mm.
   *
   * @return shell ID
   */
  public double getShellInsideDiameter() {
    return shellInsideDiameter;
  }

  /**
   * Gets shell outside diameter in mm.
   *
   * @return shell OD
   */
  public double getShellOutsideDiameter() {
    return shellOutsideDiameter;
  }

  /**
   * Gets shell wall thickness in mm.
   *
   * @return shell wall thickness
   */
  public double getShellWallThickness() {
    return shellWallThickness;
  }

  /**
   * Gets tube count.
   *
   * @return tube count
   */
  public int getTubeCount() {
    return tubeCount;
  }

  /**
   * Gets baffle count.
   *
   * @return baffle count
   */
  public int getBaffleCount() {
    return baffleCount;
  }

  /**
   * Gets baffle spacing in mm.
   *
   * @return baffle spacing
   */
  public double getBaffleSpacing() {
    return baffleSpacing;
  }

  /**
   * Gets total dry weight in kg.
   *
   * @return dry weight
   */
  public double getTotalDryWeight() {
    return totalDryWeight;
  }

  /**
   * Gets operating weight in kg.
   *
   * @return operating weight
   */
  public double getOperatingWeight() {
    return operatingWeight;
  }

  /**
   * Gets total estimated cost in USD.
   *
   * @return total cost
   */
  public double getTotalCost() {
    return totalCost;
  }

  /**
   * Converts results to a map.
   *
   * @return map of results
   */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();

    // Configuration
    result.put("temaDesignation", temaDesignation);
    result.put("temaClass", temaClass.name());

    // Shell dimensions
    Map<String, Object> shell = new LinkedHashMap<String, Object>();
    shell.put("insideDiameter_mm", shellInsideDiameter);
    shell.put("outsideDiameter_mm", shellOutsideDiameter);
    shell.put("wallThickness_mm", shellWallThickness);
    shell.put("material", shellMaterial);
    shell.put("materialGrade", shellMaterialGrade);
    shell.put("allowableStress_MPa", shellAllowableStress);
    shell.put("smys_MPa", shellSmys);
    shell.put("jointEfficiency", shellJointEfficiency);
    result.put("shell", shell);

    // Tube bundle
    Map<String, Object> tubes = new LinkedHashMap<String, Object>();
    tubes.put("count", tubeCount);
    tubes.put("outerDiameter_mm", tubeSize.getOuterDiameterMm());
    tubes.put("wallThickness_mm", tubeWallThickness);
    tubes.put("length_mm", tubeLength);
    tubes.put("passes", tubePasses);
    tubes.put("pitch_mm", tubePitch);
    tubes.put("pitchPattern", pitchPattern.getDescription());
    tubes.put("material", tubeMaterial);
    tubes.put("materialGrade", tubeMaterialGrade);
    tubes.put("allowableStress_MPa", tubeAllowableStress);
    tubes.put("thermalConductivity_WpmK", tubeThermalConductivity);
    result.put("tubes", tubes);

    // Baffles
    Map<String, Object> baffles = new LinkedHashMap<String, Object>();
    baffles.put("type", baffleType.getDescription());
    baffles.put("count", baffleCount);
    baffles.put("spacing_mm", baffleSpacing);
    baffles.put("cut", baffleCut);
    result.put("baffles", baffles);

    // Area
    Map<String, Object> area = new LinkedHashMap<String, Object>();
    area.put("required_m2", requiredArea);
    area.put("actual_m2", actualArea);
    area.put("margin", (actualArea - requiredArea) / requiredArea);
    result.put("area", area);

    // ASME VIII pressure design
    Map<String, Object> pressureDesign = new LinkedHashMap<String, Object>();
    pressureDesign.put("tubesheetThickness_mm", tubesheetThicknessUHX);
    pressureDesign.put("mawpShellSide_bara", mawpShellSide);
    pressureDesign.put("mawpTubeSide_bara", mawpTubeSide);
    pressureDesign.put("hydroTestPressureShell_bara", hydroTestPressureShell);
    pressureDesign.put("hydroTestPressureTube_bara", hydroTestPressureTube);
    pressureDesign.put("nozzleReinforcementArea_mm2", nozzleReinforcementArea);
    pressureDesign.put("nozzleReinforcementAdequate", nozzleReinforcementAdequate);
    result.put("pressureDesign", pressureDesign);

    // NACE / sour service
    if (sourServiceAssessment) {
      Map<String, Object> nace = new LinkedHashMap<String, Object>();
      nace.put("sourServiceRequired", sourServiceRequired);
      nace.put("h2sPartialPressure_bar", h2sPartialPressure);
      nace.put("shellNACECompliant", shellNACECompliant);
      nace.put("tubeNACECompliant", tubeNACECompliant);
      nace.put("shellMaxHardness_HV", shellMaxHardnessHV);
      nace.put("tubeMaxHardness_HV", tubeMaxHardnessHV);
      nace.put("issues", naceIssues);
      result.put("naceAssessment", nace);
    }

    // Applied standards
    result.put("appliedStandards", appliedStandards);

    // Weights
    Map<String, Object> weights = new LinkedHashMap<String, Object>();
    weights.put("shell_kg", shellWeight);
    weights.put("tubes_kg", tubeWeight);
    weights.put("tubesheets_kg", tubesheetWeight);
    weights.put("heads_kg", headWeight);
    weights.put("baffles_kg", baffleWeight);
    weights.put("totalDry_kg", totalDryWeight);
    weights.put("operating_kg", operatingWeight);
    result.put("weights", weights);

    // Costs
    Map<String, Object> costs = new LinkedHashMap<String, Object>();
    costs.put("material_USD", materialCost);
    costs.put("fabrication_USD", fabricationCost);
    costs.put("total_USD", totalCost);
    result.put("costs", costs);

    return result;
  }

  /**
   * Converts results to JSON.
   *
   * @return JSON string
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(toMap());
  }

  // ============================================================================
  // ASME VIII / NACE / Material Getters and Setters
  // ============================================================================

  /**
   * Gets tubesheet thickness per ASME UHX-13.
   *
   * @return tubesheet thickness in mm
   */
  public double getTubesheetThicknessUHX() {
    return tubesheetThicknessUHX;
  }

  /**
   * Gets MAWP for the shell side per ASME UG-27.
   *
   * @return MAWP in bara
   */
  public double getMawpShellSide() {
    return mawpShellSide;
  }

  /**
   * Gets MAWP for the tube side.
   *
   * @return MAWP in bara
   */
  public double getMawpTubeSide() {
    return mawpTubeSide;
  }

  /**
   * Gets hydrostatic test pressure for the shell side per UG-99.
   *
   * @return hydro test pressure in bara
   */
  public double getHydroTestPressureShell() {
    return hydroTestPressureShell;
  }

  /**
   * Gets hydrostatic test pressure for the tube side per UG-99.
   *
   * @return hydro test pressure in bara
   */
  public double getHydroTestPressureTube() {
    return hydroTestPressureTube;
  }

  /**
   * Gets whether the nozzle reinforcement is adequate per UG-37.
   *
   * @return true if adequate
   */
  public boolean isNozzleReinforcementAdequate() {
    return nozzleReinforcementAdequate;
  }

  /**
   * Gets the nozzle reinforcement available area.
   *
   * @return available reinforcement area in mm2
   */
  public double getNozzleReinforcementArea() {
    return nozzleReinforcementArea;
  }

  /**
   * Sets the shell material grade for property lookup.
   *
   * @param grade material grade (e.g., "SA-516-70")
   */
  public void setShellMaterialGrade(String grade) {
    this.shellMaterialGrade = grade;
  }

  /**
   * Gets the shell material grade.
   *
   * @return shell material grade
   */
  public String getShellMaterialGrade() {
    return shellMaterialGrade;
  }

  /**
   * Sets the tube material grade for property lookup.
   *
   * @param grade material grade (e.g., "SA-179", "SA-213-TP316")
   */
  public void setTubeMaterialGrade(String grade) {
    this.tubeMaterialGrade = grade;
  }

  /**
   * Gets the tube material grade.
   *
   * @return tube material grade
   */
  public String getTubeMaterialGrade() {
    return tubeMaterialGrade;
  }

  /**
   * Gets the tube thermal conductivity.
   *
   * @return thermal conductivity in W/mK
   */
  public double getTubeThermalConductivity() {
    return tubeThermalConductivity;
  }

  /**
   * Enables or disables sour service assessment per NACE MR0175.
   *
   * @param enabled true to enable NACE assessment
   */
  public void setSourServiceAssessment(boolean enabled) {
    this.sourServiceAssessment = enabled;
  }

  /**
   * Gets whether sour service assessment is enabled.
   *
   * @return true if NACE assessment is enabled
   */
  public boolean isSourServiceAssessment() {
    return sourServiceAssessment;
  }

  /**
   * Sets the H2S partial pressure for sour service determination.
   *
   * @param pressure H2S partial pressure in bar
   */
  public void setH2sPartialPressure(double pressure) {
    this.h2sPartialPressure = pressure;
  }

  /**
   * Gets H2S partial pressure.
   *
   * @return H2S partial pressure in bar
   */
  public double getH2sPartialPressure() {
    return h2sPartialPressure;
  }

  /**
   * Gets whether sour service is required per NACE MR0175.
   *
   * @return true if H2S levels require sour service materials
   */
  public boolean isSourServiceRequired() {
    return sourServiceRequired;
  }

  /**
   * Gets the list of NACE compliance issues.
   *
   * @return list of issue descriptions
   */
  public List<String> getNaceIssues() {
    return naceIssues;
  }

  /**
   * Gets the list of applied design standards.
   *
   * @return list of standard references
   */
  public List<String> getAppliedStandards() {
    return appliedStandards;
  }

  /**
   * Sets shell joint efficiency per ASME VIII.
   *
   * @param efficiency joint efficiency (0.65 to 1.0)
   */
  public void setShellJointEfficiency(double efficiency) {
    this.shellJointEfficiency = efficiency;
  }

  /**
   * Gets shell joint efficiency.
   *
   * @return joint efficiency
   */
  public double getShellJointEfficiency() {
    return shellJointEfficiency;
  }

  /**
   * Sets the nozzle diameter for reinforcement calculations.
   *
   * @param diameter nozzle diameter in mm
   */
  public void setNozzleDiameter(double diameter) {
    this.nozzleDiameter = diameter;
  }

  /**
   * Sets the nozzle wall thickness.
   *
   * @param thickness nozzle wall thickness in mm
   */
  public void setNozzleWallThickness(double thickness) {
    this.nozzleWallThickness = thickness;
  }

  /**
   * Gets whether the shell material is NACE compliant.
   *
   * @return true if compliant
   */
  public boolean isShellNACECompliant() {
    return shellNACECompliant;
  }

  /**
   * Gets whether the tube material is NACE compliant.
   *
   * @return true if compliant
   */
  public boolean isTubeNACECompliant() {
    return tubeNACECompliant;
  }
}
