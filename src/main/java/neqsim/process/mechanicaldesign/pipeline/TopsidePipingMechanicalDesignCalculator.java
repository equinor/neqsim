package neqsim.process.mechanicaldesign.pipeline;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Calculator for topside piping mechanical design based on industry standards.
 *
 * <p>
 * This class provides methods to calculate wall thickness, velocity limits, support spacing,
 * vibration analysis, and stress analysis for topside (offshore platform and onshore facility)
 * piping according to various design codes including:
 * </p>
 * <ul>
 * <li>ASME B31.3 - Process Piping</li>
 * <li>API RP 14E - Erosional Velocity</li>
 * <li>Energy Institute Guidelines - Acoustic Induced Vibration (AIV)</li>
 * <li>NORSOK L-002 - Piping System Layout, Design and Structural Analysis</li>
 * </ul>
 *
 * @author ASMF
 * @version 1.0
 */
public class TopsidePipingMechanicalDesignCalculator extends PipeMechanicalDesignCalculator {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  // ============ Velocity Parameters ============
  /** Actual flow velocity in m/s. */
  private double actualVelocity = 0.0;

  /** Erosional velocity per API RP 14E in m/s. */
  private double erosionalVelocity = 0.0;

  /** C-factor for erosional velocity (typically 100-150). */
  private double erosionalCFactor = 100.0;

  /** Maximum recommended gas velocity in m/s. */
  private double maxGasVelocity = 20.0;

  /** Maximum recommended liquid velocity in m/s. */
  private double maxLiquidVelocity = 3.0;

  /** Maximum recommended multiphase velocity in m/s. */
  private double maxMultiphaseVelocity = 15.0;

  /** Insulation density in kg/m3 (mineral wool ~80, PUF ~40). */
  private double insulationDensity = 80.0;

  /** Noise velocity limit in m/s. */
  private double noiseVelocityLimit = 40.0;

  // ============ Flow Properties ============
  /** Flow rate in kg/s. */
  private double massFlowRate = 0.0;

  /** Volumetric flow rate in m3/s. */
  private double volumetricFlowRate = 0.0;

  /** Mixture density in kg/m3. */
  private double mixtureDensity = 100.0;

  /** Gas density in kg/m3. */
  private double gasDensity = 50.0;

  /** Liquid density in kg/m3. */
  private double liquidDensity = 800.0;

  /** Liquid volume fraction (0-1). */
  private double liquidFraction = 0.0;

  /** Gas viscosity in Pa-s. */
  private double gasViscosity = 1.5e-5;

  /** Liquid viscosity in Pa-s. */
  private double liquidViscosity = 0.001;

  // ============ Vibration Parameters ============
  /** Acoustic power level in W. */
  private double acousticPowerLevel = 0.0;

  /** Likelihood of failure from AIV (0-1). */
  private double aivLikelihoodOfFailure = 0.0;

  /** Flow-induced vibration screening number. */
  private double fivScreeningNumber = 0.0;

  /** Natural frequency of pipe span in Hz. */
  private double pipeNaturalFrequency = 0.0;

  /** Vortex shedding frequency in Hz. */
  private double vortexSheddingFrequency = 0.0;

  /** Lock-in velocity range lower bound in m/s. */
  private double lockInVelocityLower = 0.0;

  /** Lock-in velocity range upper bound in m/s. */
  private double lockInVelocityUpper = 0.0;

  // ============ Support Parameters ============
  /** Calculated support spacing in meters. */
  private double calculatedSupportSpacing = 0.0;

  /** Maximum allowed deflection in mm. */
  private double maxAllowedDeflection = 12.5;

  /** Pipe weight per unit length in kg/m (including contents and insulation). */
  private double totalWeightPerMeter = 0.0;

  /** Second moment of area in m4. */
  private double momentOfInertia = 0.0;

  /** Section modulus in m3. */
  private double sectionModulus = 0.0;

  // ============ Stress Parameters ============
  /** Sustained stress (pressure + weight) in MPa. */
  private double sustainedStress = 0.0;

  /** Thermal expansion stress in MPa. */
  private double thermalExpansionStress = 0.0;

  /** Occasional stress (wind, earthquake) in MPa. */
  private double occasionalStress = 0.0;

  /** Combined stress in MPa. */
  private double combinedStress = 0.0;

  /** Allowable stress per ASME B31.3 in MPa. */
  private double allowableStress = 0.0;

  /** Stress intensity factor for fittings. */
  private double stressIntensificationFactor = 1.0;

  /** Flexibility factor. */
  private double flexibilityFactor = 1.0;

  // ============ Thermal Expansion ============
  /** Installation temperature in Celsius. */
  private double installationTemperature = 20.0;

  /** Operating temperature in Celsius. */
  private double operatingTemperature = 50.0;

  /** Free thermal expansion in mm. */
  private double freeExpansion = 0.0;

  /** Expansion loop length required in meters. */
  private double requiredLoopLength = 0.0;

  /** Anchor force in kN. */
  private double anchorForce = 0.0;

  // ============ Service-Specific Parameters ============
  /** Service type string. */
  private String serviceType = "PROCESS_GAS";

  /** Is clean service (no solids). */
  private boolean cleanService = true;

  /** Contains H2S. */
  private boolean sourService = false;

  /** Contains CO2. */
  private boolean co2Service = false;

  /** Sand content in kg/m3. */
  private double sandContent = 0.0;

  // ============ Standards Compliance ============
  /** List of applied standards. */
  private List<String> appliedStandards = new ArrayList<String>();

  /** Velocity check passed. */
  private boolean velocityCheckPassed = true;

  /** Vibration check passed. */
  private boolean vibrationCheckPassed = true;

  /** Stress check passed. */
  private boolean stressCheckPassed = true;

  /** Support spacing check passed. */
  private boolean supportCheckPassed = true;

  // ============ Standard Pipe Sizes (NPS to OD in meters) ============
  private static final Map<String, double[]> STANDARD_PIPE_DIMENSIONS;

  // ============ ASME B31.3 Table A-1: Basic Allowable Stresses (MPa) ============
  private static final Map<String, double[]> ASME_B31_3_ALLOWABLE_STRESSES;

  static {
    // NPS -> [OD (m), SCH40 thickness (m), SCH80 thickness (m)]
    STANDARD_PIPE_DIMENSIONS = new HashMap<String, double[]>();
    STANDARD_PIPE_DIMENSIONS.put("2", new double[] {0.0603, 0.00391, 0.00554});
    STANDARD_PIPE_DIMENSIONS.put("3", new double[] {0.0889, 0.00549, 0.00762});
    STANDARD_PIPE_DIMENSIONS.put("4", new double[] {0.1143, 0.00602, 0.00851});
    STANDARD_PIPE_DIMENSIONS.put("6", new double[] {0.1683, 0.00711, 0.01097});
    STANDARD_PIPE_DIMENSIONS.put("8", new double[] {0.2191, 0.00823, 0.01270});
    STANDARD_PIPE_DIMENSIONS.put("10", new double[] {0.2731, 0.00927, 0.01270});
    STANDARD_PIPE_DIMENSIONS.put("12", new double[] {0.3239, 0.01048, 0.01270});
    STANDARD_PIPE_DIMENSIONS.put("14", new double[] {0.3556, 0.01118, 0.01270});
    STANDARD_PIPE_DIMENSIONS.put("16", new double[] {0.4064, 0.01270, 0.01588});
    STANDARD_PIPE_DIMENSIONS.put("18", new double[] {0.4572, 0.01422, 0.01778});
    STANDARD_PIPE_DIMENSIONS.put("20", new double[] {0.5080, 0.01270, 0.01588});
    STANDARD_PIPE_DIMENSIONS.put("24", new double[] {0.6096, 0.01422, 0.01778});

    // Material -> [Allowable at 20C, at 100C, at 200C, at 300C, at 400C] in MPa
    ASME_B31_3_ALLOWABLE_STRESSES = new HashMap<String, double[]>();
    ASME_B31_3_ALLOWABLE_STRESSES.put("A106-B", new double[] {138.0, 138.0, 138.0, 132.0, 121.0});
    ASME_B31_3_ALLOWABLE_STRESSES.put("A106-C", new double[] {159.0, 159.0, 159.0, 152.0, 139.0});
    ASME_B31_3_ALLOWABLE_STRESSES.put("A333-6", new double[] {138.0, 138.0, 138.0, 132.0, 121.0});
    ASME_B31_3_ALLOWABLE_STRESSES.put("A312-TP304", new double[] {138.0, 115.0, 101.0, 90.0, 82.0});
    ASME_B31_3_ALLOWABLE_STRESSES.put("A312-TP316", new double[] {138.0, 115.0, 103.0, 92.0, 84.0});
    ASME_B31_3_ALLOWABLE_STRESSES.put("A312-TP316L", new double[] {115.0, 103.0, 92.0, 83.0, 76.0});
    ASME_B31_3_ALLOWABLE_STRESSES.put("A312-TP321", new double[] {138.0, 115.0, 103.0, 93.0, 85.0});
    ASME_B31_3_ALLOWABLE_STRESSES.put("A312-TP347",
        new double[] {138.0, 127.0, 114.0, 105.0, 97.0});
    ASME_B31_3_ALLOWABLE_STRESSES.put("A790-S31803",
        new double[] {207.0, 192.0, 177.0, 165.0, 0.0}); // Duplex
    ASME_B31_3_ALLOWABLE_STRESSES.put("A790-S32750",
        new double[] {241.0, 226.0, 211.0, 197.0, 0.0}); // Super duplex
  }

  /**
   * Default constructor.
   */
  public TopsidePipingMechanicalDesignCalculator() {
    super();
    setDesignCode(ASME_B31_3);
  }

  // ============================================================================
  // VELOCITY CALCULATIONS
  // ============================================================================

  /**
   * Calculate erosional velocity per API RP 14E.
   *
   * <p>
   * Formula: Ve = C / sqrt(rhom), where:
   * </p>
   * <ul>
   * <li>Ve = erosional velocity in m/s</li>
   * <li>C = empirical constant (100 for continuous service, 150 for intermittent)</li>
   * <li>rhom = gas/liquid mixture density in kg/m3</li>
   * </ul>
   *
   * @return erosional velocity in m/s
   */
  public double calculateErosionalVelocity() {
    if (mixtureDensity <= 0) {
      mixtureDensity = 1.0;
    }
    erosionalVelocity = erosionalCFactor / Math.sqrt(mixtureDensity);
    appliedStandards.add("API-RP-14E - Erosional Velocity");
    return erosionalVelocity;
  }

  /**
   * Calculate erosional velocity with sand correction.
   *
   * @param sandRate sand production rate in kg/day
   * @return erosional velocity in m/s
   */
  public double calculateErosionalVelocityWithSand(double sandRate) {
    if (sandRate > 0) {
      double reductionFactor = Math.max(0.7, 1.0 - 0.1 * Math.log10(sandRate + 1));
      erosionalCFactor = 100.0 * reductionFactor;
      cleanService = false;
    }
    return calculateErosionalVelocity();
  }

  /**
   * Calculate actual flow velocity.
   *
   * @return actual velocity in m/s
   */
  public double calculateActualVelocity() {
    double id = getOuterDiameter() - 2.0 * getNominalWallThickness();
    if (id <= 0) {
      id = getOuterDiameter() * 0.9;
    }
    double area = Math.PI * id * id / 4.0;
    if (area > 0 && mixtureDensity > 0) {
      actualVelocity = massFlowRate / (mixtureDensity * area);
    } else if (area > 0) {
      actualVelocity = volumetricFlowRate / area;
    }
    return actualVelocity;
  }

  /**
   * Check if velocity is within acceptable limits.
   *
   * @return true if velocity is acceptable
   */
  public boolean checkVelocityLimits() {
    calculateActualVelocity();
    calculateErosionalVelocity();

    velocityCheckPassed = true;

    // Check against erosional velocity (factor of 0.8 for safety)
    if (actualVelocity > 0.8 * erosionalVelocity) {
      velocityCheckPassed = false;
    }

    // Check against service-specific limits
    if (liquidFraction < 0.01) {
      // Gas service
      if (actualVelocity > maxGasVelocity) {
        velocityCheckPassed = false;
      }
    } else if (liquidFraction > 0.99) {
      // Liquid service
      if (actualVelocity > maxLiquidVelocity) {
        velocityCheckPassed = false;
      }
    } else {
      // Multiphase
      if (actualVelocity > maxMultiphaseVelocity) {
        velocityCheckPassed = false;
      }
    }

    return velocityCheckPassed;
  }

  /**
   * Calculate minimum pipe diameter for given flow.
   *
   * @return minimum diameter in meters
   */
  public double calculateMinimumDiameter() {
    calculateErosionalVelocity();
    double maxVel = Math.min(0.8 * erosionalVelocity, maxGasVelocity);
    if (liquidFraction > 0.99) {
      maxVel = maxLiquidVelocity;
    } else if (liquidFraction > 0.01) {
      maxVel = maxMultiphaseVelocity;
    }

    double area = massFlowRate / (mixtureDensity * maxVel);
    return Math.sqrt(4.0 * area / Math.PI);
  }

  // ============================================================================
  // VIBRATION CALCULATIONS
  // ============================================================================

  /**
   * Calculate acoustic power level for AIV screening.
   *
   * @param upstreamPressure upstream pressure in bara
   * @param downstreamPressure downstream pressure in bara
   * @param temperature temperature in Celsius
   * @param molecularWeight molecular weight in kg/kmol
   * @return acoustic power level in Watts
   */
  public double calculateAcousticPowerLevel(double upstreamPressure, double downstreamPressure,
      double temperature, double molecularWeight) {
    double p1 = upstreamPressure * 1e5;
    double p2 = downstreamPressure * 1e5;
    double tempK = temperature + 273.15;
    double mdot = massFlowRate;

    double pressureRatio = (p1 - p2) / p1;
    if (pressureRatio > 1.0) {
      pressureRatio = 1.0;
    }
    if (pressureRatio < 0) {
      pressureRatio = 0;
    }

    acousticPowerLevel =
        3.2e-9 * mdot * p1 * Math.pow(pressureRatio, 3.6) * Math.pow(tempK / 273.15, 0.8);

    appliedStandards.add("Energy Institute Guidelines - AIV Assessment");
    return acousticPowerLevel;
  }

  /**
   * Calculate likelihood of failure from AIV.
   *
   * @param branchDiameter branch diameter in meters
   * @param mainPipeDiameter main pipe diameter in meters
   * @return likelihood of failure (0.0-1.0)
   */
  public double calculateAIVLikelihoodOfFailure(double branchDiameter, double mainPipeDiameter) {
    double thickness = getNominalWallThickness();
    if (thickness <= 0) {
      thickness = mainPipeDiameter * 0.05;
    }

    double externalDiameter = mainPipeDiameter + 2 * thickness;
    double dt_ratio = externalDiameter / thickness;
    double screeningParam = acousticPowerLevel * Math.pow(dt_ratio, 2);

    if (screeningParam < 1e4) {
      aivLikelihoodOfFailure = 0.1;
    } else if (screeningParam < 1e5) {
      aivLikelihoodOfFailure = 0.3;
    } else if (screeningParam < 1e6) {
      aivLikelihoodOfFailure = 0.6;
    } else {
      aivLikelihoodOfFailure = 0.9;
    }

    vibrationCheckPassed = aivLikelihoodOfFailure < 0.5;
    return aivLikelihoodOfFailure;
  }

  /**
   * Calculate flow-induced vibration (FIV) screening number.
   *
   * @param spanLength pipe span length between supports in meters
   * @return FIV screening number
   */
  public double calculateFIVScreening(double spanLength) {
    calculateMomentOfInertia();
    calculateTotalWeight();

    double id = getOuterDiameter() - 2.0 * getNominalWallThickness();
    double area = Math.PI * (getOuterDiameter() * getOuterDiameter() - id * id) / 4.0;

    double massPerMeter = totalWeightPerMeter;
    if (massPerMeter <= 0) {
      massPerMeter = getSteelDensity() * area + mixtureDensity * Math.PI * id * id / 4.0;
    }

    double E = getYoungsModulus() * 1e6;
    pipeNaturalFrequency =
        (Math.PI / 2.0) * Math.sqrt(E * momentOfInertia / (massPerMeter * Math.pow(spanLength, 4)));

    double strouhalNumber = 0.2;
    double externalVelocity = 0.0;

    vortexSheddingFrequency = strouhalNumber * externalVelocity / getOuterDiameter();

    double rhoV2 = mixtureDensity * actualVelocity * actualVelocity;
    fivScreeningNumber = rhoV2 * Math.pow(spanLength / getOuterDiameter(), 4)
        / (getYoungsModulus() * 1e6 * getNominalWallThickness() / getOuterDiameter());

    appliedStandards.add("Energy Institute Guidelines - FIV Screening");
    return fivScreeningNumber;
  }

  /**
   * Check for lock-in risk between vortex shedding and pipe natural frequency.
   *
   * @return true if lock-in risk exists
   */
  public boolean checkLockInRisk() {
    lockInVelocityLower = 0.8 * pipeNaturalFrequency * getOuterDiameter() / 0.2;
    lockInVelocityUpper = 1.2 * pipeNaturalFrequency * getOuterDiameter() / 0.2;

    return (actualVelocity >= lockInVelocityLower && actualVelocity <= lockInVelocityUpper);
  }

  // ============================================================================
  // SUPPORT SPACING CALCULATIONS
  // ============================================================================

  /**
   * Calculate pipe support spacing based on deflection and stress limits.
   *
   * @return recommended support spacing in meters
   */
  public double calculateSupportSpacing() {
    calculateMomentOfInertia();
    calculateTotalWeight();
    calculateAllowableStress();

    double E = getYoungsModulus() * 1e6;
    double I = momentOfInertia;
    double w = totalWeightPerMeter * 9.81;

    // Based on deflection limit
    double deltaMax = maxAllowedDeflection / 1000.0;
    double L_deflection = Math.pow(deltaMax * 384.0 * E * I / (5.0 * w), 0.25);

    // Based on bending stress limit
    double sigma = allowableStress * 1e6 * 0.5;
    double L_stress = Math.sqrt(8.0 * sigma * sectionModulus / w);

    calculatedSupportSpacing = Math.min(L_deflection, L_stress);
    calculatedSupportSpacing = Math.max(calculatedSupportSpacing, 1.0);
    calculatedSupportSpacing = Math.min(calculatedSupportSpacing, 12.0);

    appliedStandards.add("NORSOK L-002 - Pipe Support Spacing");
    return calculatedSupportSpacing;
  }

  /**
   * Calculate support spacing per ASME B31.3 simplified method.
   *
   * @return support spacing in meters
   */
  public double calculateSupportSpacingASME() {
    double od = getOuterDiameter();

    if (od <= 0.0603) {
      calculatedSupportSpacing = 2.1;
    } else if (od <= 0.1143) {
      calculatedSupportSpacing = 2.7;
    } else if (od <= 0.1683) {
      calculatedSupportSpacing = 3.4;
    } else if (od <= 0.2191) {
      calculatedSupportSpacing = 3.7;
    } else if (od <= 0.3239) {
      calculatedSupportSpacing = 4.3;
    } else if (od <= 0.4064) {
      calculatedSupportSpacing = 4.6;
    } else if (od <= 0.5080) {
      calculatedSupportSpacing = 5.2;
    } else {
      calculatedSupportSpacing = 5.8;
    }

    if (liquidFraction > 0.5 && liquidDensity > 1000) {
      calculatedSupportSpacing *= 0.85;
    }

    if (getInsulationThickness() > 0.05) {
      calculatedSupportSpacing *= 0.90;
    }

    appliedStandards.add("ASME B31.3 Table 121.5 - Support Spacing");
    return calculatedSupportSpacing;
  }

  /**
   * Calculate number of supports required.
   *
   * @param pipeLength total pipe length in meters
   * @return number of supports
   */
  public int calculateNumberOfSupports(double pipeLength) {
    if (calculatedSupportSpacing <= 0) {
      calculateSupportSpacing();
    }
    return (int) Math.ceil(pipeLength / calculatedSupportSpacing) + 1;
  }

  // ============================================================================
  // STRESS CALCULATIONS
  // ============================================================================

  /**
   * Calculate allowable stress per ASME B31.3.
   *
   * @return allowable stress in MPa
   */
  public double calculateAllowableStress() {
    double[] stresses = ASME_B31_3_ALLOWABLE_STRESSES.get(getMaterialGrade());
    if (stresses == null) {
      stresses = ASME_B31_3_ALLOWABLE_STRESSES.get("A106-B");
    }

    double temp = getDesignTemperature();
    if (temp <= 20) {
      allowableStress = stresses[0];
    } else if (temp <= 100) {
      allowableStress = stresses[0] + (stresses[1] - stresses[0]) * (temp - 20) / 80;
    } else if (temp <= 200) {
      allowableStress = stresses[1] + (stresses[2] - stresses[1]) * (temp - 100) / 100;
    } else if (temp <= 300) {
      allowableStress = stresses[2] + (stresses[3] - stresses[2]) * (temp - 200) / 100;
    } else if (temp <= 400) {
      allowableStress = stresses[3] + (stresses[4] - stresses[3]) * (temp - 300) / 100;
    } else {
      allowableStress = stresses[4];
    }

    appliedStandards.add("ASME B31.3 Table A-1 - Allowable Stress");
    return allowableStress;
  }

  /**
   * Calculate sustained stress per ASME B31.3.
   *
   * @param supportSpacing span between supports in meters
   * @return sustained stress in MPa
   */
  public double calculateSustainedStress(double supportSpacing) {
    calculateMomentOfInertia();
    calculateTotalWeight();

    double P = getDesignPressure();
    double Do = getOuterDiameter();
    double t = getNominalWallThickness();

    double pressureStress = P * Do / (2.0 * t);

    double w = totalWeightPerMeter * 9.81;
    double M = w * supportSpacing * supportSpacing / 8.0;
    double weightStress = M / sectionModulus / 1e6;

    sustainedStress = pressureStress + weightStress;
    stressCheckPassed = sustainedStress <= allowableStress;

    return sustainedStress;
  }

  /**
   * Calculate thermal expansion stress per ASME B31.3.
   *
   * @param anchoredLength length between anchors in meters
   * @return thermal expansion stress in MPa
   */
  public double calculateThermalExpansionStress(double anchoredLength) {
    calculateMomentOfInertia();

    double E = getYoungsModulus();
    double alpha = getThermalExpansion();
    double deltaT = operatingTemperature - installationTemperature;

    freeExpansion = alpha * anchoredLength * deltaT * 1000;
    thermalExpansionStress = E * alpha * Math.abs(deltaT);

    double deltaL = Math.abs(freeExpansion) / 1000;
    requiredLoopLength = Math.sqrt(3.0 * getOuterDiameter() * deltaL / 0.03);

    double area = Math.PI * (getOuterDiameter() * getOuterDiameter()
        - Math.pow(getOuterDiameter() - 2 * getNominalWallThickness(), 2)) / 4.0;
    anchorForce = thermalExpansionStress * 1e6 * area / 1000;

    double Sc = allowableStress;
    double Sh = allowableStress;
    double f = 1.0;
    double Sa = f * (1.25 * Sc + 0.25 * Sh);

    if (thermalExpansionStress > Sa) {
      stressCheckPassed = false;
    }

    appliedStandards.add("ASME B31.3 - Thermal Expansion Stress");
    return thermalExpansionStress;
  }

  /**
   * Calculate occasional stress per ASME B31.3.
   *
   * @param windLoad wind load in N/m
   * @param supportSpacing support spacing in meters
   * @return occasional stress in MPa
   */
  public double calculateOccasionalStress(double windLoad, double supportSpacing) {
    calculateMomentOfInertia();

    double M = windLoad * supportSpacing * supportSpacing / 8.0;
    occasionalStress = M / sectionModulus / 1e6;

    if (sustainedStress + occasionalStress > 1.33 * allowableStress) {
      stressCheckPassed = false;
    }

    return occasionalStress;
  }

  /**
   * Calculate combined stress for design check.
   *
   * @return combined stress in MPa
   */
  public double calculateCombinedStress() {
    combinedStress = sustainedStress;
    return combinedStress;
  }

  // ============================================================================
  // HELPER CALCULATIONS
  // ============================================================================

  /**
   * Calculate second moment of area (moment of inertia) for pipe cross-section.
   *
   * @return moment of inertia in m4
   */
  public double calculateMomentOfInertia() {
    double Do = getOuterDiameter();
    double Di = Do - 2.0 * getNominalWallThickness();
    if (Di <= 0) {
      Di = Do * 0.9;
    }

    momentOfInertia = Math.PI / 64.0 * (Math.pow(Do, 4) - Math.pow(Di, 4));
    sectionModulus = momentOfInertia / (Do / 2.0);

    return momentOfInertia;
  }

  /**
   * Calculate total pipe weight per meter including contents and insulation.
   *
   * @return total weight in kg/m
   */
  public double calculateTotalWeight() {
    double Do = getOuterDiameter();
    double Di = Do - 2.0 * getNominalWallThickness();
    if (Di <= 0) {
      Di = Do * 0.9;
    }

    double steelArea = Math.PI / 4.0 * (Do * Do - Di * Di);
    double steelWeight = steelArea * getSteelDensity();

    double contentArea = Math.PI / 4.0 * Di * Di;
    double contentWeight = contentArea * mixtureDensity;

    double insulationWeight = 0.0;
    if (getInsulationThickness() > 0) {
      double insulOD = Do + 2.0 * getInsulationThickness();
      double insulArea = Math.PI / 4.0 * (insulOD * insulOD - Do * Do);
      insulationWeight = insulArea * getInsulationDensity();
    }

    totalWeightPerMeter = steelWeight + contentWeight + insulationWeight;
    return totalWeightPerMeter;
  }

  /**
   * Calculate wind load on pipe.
   *
   * @param windSpeed wind speed in m/s
   * @return wind load in N/m
   */
  public double calculateWindLoad(double windSpeed) {
    double airDensity = 1.225;
    double Cd = 1.0;
    double Do = getOuterDiameter();
    if (getInsulationThickness() > 0) {
      Do += 2.0 * getInsulationThickness();
    }

    return 0.5 * airDensity * windSpeed * windSpeed * Cd * Do;
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

    allPassed = allPassed && checkVelocityLimits();

    double minThickness = calculateMinimumWallThickness();
    if (getNominalWallThickness() < minThickness) {
      allPassed = false;
    }

    double supportSpacing = calculateSupportSpacing();
    supportCheckPassed = true;

    calculateAllowableStress();
    calculateSustainedStress(supportSpacing);
    calculateThermalExpansionStress(100.0);

    allPassed = allPassed && stressCheckPassed;

    return allPassed;
  }

  // ============================================================================
  // JSON OUTPUT
  // ============================================================================

  /**
   * Convert all results to a Map for JSON serialization.
   *
   * @return map of all calculated values
   */
  @Override
  public Map<String, Object> toMap() {
    Map<String, Object> result = super.toMap();

    Map<String, Object> velocityResults = new HashMap<String, Object>();
    velocityResults.put("actualVelocity_m_s", actualVelocity);
    velocityResults.put("erosionalVelocity_m_s", erosionalVelocity);
    velocityResults.put("erosionalCFactor", erosionalCFactor);
    velocityResults.put("maxGasVelocity_m_s", maxGasVelocity);
    velocityResults.put("maxLiquidVelocity_m_s", maxLiquidVelocity);
    velocityResults.put("velocityRatio",
        erosionalVelocity > 0 ? actualVelocity / erosionalVelocity : 0);
    velocityResults.put("velocityCheckPassed", velocityCheckPassed);
    result.put("velocityAnalysis", velocityResults);

    Map<String, Object> vibrationResults = new HashMap<String, Object>();
    vibrationResults.put("acousticPowerLevel_W", acousticPowerLevel);
    vibrationResults.put("aivLikelihoodOfFailure", aivLikelihoodOfFailure);
    vibrationResults.put("fivScreeningNumber", fivScreeningNumber);
    vibrationResults.put("pipeNaturalFrequency_Hz", pipeNaturalFrequency);
    vibrationResults.put("vortexSheddingFrequency_Hz", vortexSheddingFrequency);
    vibrationResults.put("lockInVelocityRange_m_s",
        "[" + lockInVelocityLower + ", " + lockInVelocityUpper + "]");
    vibrationResults.put("vibrationCheckPassed", vibrationCheckPassed);
    result.put("vibrationAnalysis", vibrationResults);

    Map<String, Object> supportResults = new HashMap<String, Object>();
    supportResults.put("calculatedSupportSpacing_m", calculatedSupportSpacing);
    supportResults.put("maxAllowedDeflection_mm", maxAllowedDeflection);
    supportResults.put("totalWeightPerMeter_kg_m", totalWeightPerMeter);
    supportResults.put("momentOfInertia_m4", momentOfInertia);
    supportResults.put("sectionModulus_m3", sectionModulus);
    supportResults.put("supportCheckPassed", supportCheckPassed);
    result.put("supportAnalysis", supportResults);

    Map<String, Object> stressResults = new HashMap<String, Object>();
    stressResults.put("allowableStress_MPa", allowableStress);
    stressResults.put("sustainedStress_MPa", sustainedStress);
    stressResults.put("thermalExpansionStress_MPa", thermalExpansionStress);
    stressResults.put("occasionalStress_MPa", occasionalStress);
    stressResults.put("combinedStress_MPa", combinedStress);
    stressResults.put("stressIntensificationFactor", stressIntensificationFactor);
    stressResults.put("stressCheckPassed", stressCheckPassed);
    result.put("stressAnalysis", stressResults);

    Map<String, Object> thermalResults = new HashMap<String, Object>();
    thermalResults.put("installationTemperature_C", installationTemperature);
    thermalResults.put("operatingTemperature_C", operatingTemperature);
    thermalResults.put("freeExpansion_mm", freeExpansion);
    thermalResults.put("requiredLoopLength_m", requiredLoopLength);
    thermalResults.put("anchorForce_kN", anchorForce);
    result.put("thermalExpansion", thermalResults);

    result.put("appliedStandards", appliedStandards);
    result.put("serviceType", serviceType);
    result.put("cleanService", cleanService);
    result.put("sourService", sourService);

    return result;
  }

  /**
   * Convert results to JSON string.
   *
   * @return JSON string
   */
  @Override
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(toMap());
  }

  // ============================================================================
  // GETTERS AND SETTERS
  // ============================================================================

  /**
   * Get actual velocity.
   *
   * @return actual velocity in m/s
   */
  public double getActualVelocity() {
    return actualVelocity;
  }

  /**
   * Get erosional velocity.
   *
   * @return erosional velocity in m/s
   */
  public double getErosionalVelocity() {
    return erosionalVelocity;
  }

  /**
   * Get erosional C-factor.
   *
   * @return C-factor
   */
  public double getErosionalCFactor() {
    return erosionalCFactor;
  }

  /**
   * Set erosional C-factor.
   *
   * @param erosionalCFactor C-factor (typically 100-150)
   */
  public void setErosionalCFactor(double erosionalCFactor) {
    this.erosionalCFactor = erosionalCFactor;
  }

  /**
   * Get maximum gas velocity limit.
   *
   * @return max gas velocity in m/s
   */
  public double getMaxGasVelocity() {
    return maxGasVelocity;
  }

  /**
   * Set maximum gas velocity limit.
   *
   * @param maxGasVelocity max gas velocity in m/s
   */
  public void setMaxGasVelocity(double maxGasVelocity) {
    this.maxGasVelocity = maxGasVelocity;
  }

  /**
   * Get maximum liquid velocity limit.
   *
   * @return max liquid velocity in m/s
   */
  public double getMaxLiquidVelocity() {
    return maxLiquidVelocity;
  }

  /**
   * Set maximum liquid velocity limit.
   *
   * @param maxLiquidVelocity max liquid velocity in m/s
   */
  public void setMaxLiquidVelocity(double maxLiquidVelocity) {
    this.maxLiquidVelocity = maxLiquidVelocity;
  }

  /**
   * Get mass flow rate.
   *
   * @return mass flow rate in kg/s
   */
  public double getMassFlowRate() {
    return massFlowRate;
  }

  /**
   * Set mass flow rate.
   *
   * @param massFlowRate mass flow rate in kg/s
   */
  public void setMassFlowRate(double massFlowRate) {
    this.massFlowRate = massFlowRate;
  }

  /**
   * Get mixture density.
   *
   * @return mixture density in kg/m3
   */
  public double getMixtureDensity() {
    return mixtureDensity;
  }

  /**
   * Set mixture density.
   *
   * @param mixtureDensity mixture density in kg/m3
   */
  public void setMixtureDensity(double mixtureDensity) {
    this.mixtureDensity = mixtureDensity;
  }

  /**
   * Get liquid fraction.
   *
   * @return liquid volume fraction (0-1)
   */
  public double getLiquidFraction() {
    return liquidFraction;
  }

  /**
   * Set liquid fraction.
   *
   * @param liquidFraction liquid volume fraction (0-1)
   */
  public void setLiquidFraction(double liquidFraction) {
    this.liquidFraction = liquidFraction;
  }

  /**
   * Get calculated support spacing.
   *
   * @return support spacing in meters
   */
  public double getSupportSpacing() {
    return calculatedSupportSpacing;
  }

  /**
   * Get allowable stress.
   *
   * @return allowable stress in MPa
   */
  public double getAllowableStress() {
    return allowableStress;
  }

  /**
   * Get installation temperature.
   *
   * @return installation temperature in Celsius
   */
  public double getInstallationTemperature() {
    return installationTemperature;
  }

  /**
   * Set installation temperature.
   *
   * @param installationTemperature installation temperature in Celsius
   */
  public void setInstallationTemperature(double installationTemperature) {
    this.installationTemperature = installationTemperature;
  }

  /**
   * Get operating temperature.
   *
   * @return operating temperature in Celsius
   */
  public double getOperatingTemperature() {
    return operatingTemperature;
  }

  /**
   * Set operating temperature.
   *
   * @param operatingTemperature operating temperature in Celsius
   */
  public void setOperatingTemperature(double operatingTemperature) {
    this.operatingTemperature = operatingTemperature;
  }

  /**
   * Get free thermal expansion.
   *
   * @return free expansion in mm
   */
  public double getFreeExpansion() {
    return freeExpansion;
  }

  /**
   * Get required loop length.
   *
   * @return required expansion loop leg length in meters
   */
  public double getRequiredLoopLength() {
    return requiredLoopLength;
  }

  /**
   * Get anchor force.
   *
   * @return anchor force in kN
   */
  public double getAnchorForce() {
    return anchorForce;
  }

  /**
   * Get service type.
   *
   * @return service type string
   */
  public String getServiceType() {
    return serviceType;
  }

  /**
   * Set service type.
   *
   * @param serviceType service type string
   */
  public void setServiceType(String serviceType) {
    this.serviceType = serviceType;
  }

  /**
   * Check if velocity check passed.
   *
   * @return true if passed
   */
  public boolean isVelocityCheckPassed() {
    return velocityCheckPassed;
  }

  /**
   * Check if vibration check passed.
   *
   * @return true if passed
   */
  public boolean isVibrationCheckPassed() {
    return vibrationCheckPassed;
  }

  /**
   * Check if stress check passed.
   *
   * @return true if passed
   */
  public boolean isStressCheckPassed() {
    return stressCheckPassed;
  }

  /**
   * Get list of applied standards.
   *
   * @return list of standard references
   */
  public List<String> getAppliedStandards() {
    return appliedStandards;
  }

  /**
   * Set liquid density.
   *
   * @param liquidDensity liquid density in kg/m3
   */
  public void setLiquidDensity(double liquidDensity) {
    this.liquidDensity = liquidDensity;
  }

  /**
   * Get liquid density.
   *
   * @return liquid density in kg/m3
   */
  public double getLiquidDensity() {
    return liquidDensity;
  }

  /**
   * Set gas density.
   *
   * @param gasDensity gas density in kg/m3
   */
  public void setGasDensity(double gasDensity) {
    this.gasDensity = gasDensity;
  }

  /**
   * Get gas density.
   *
   * @return gas density in kg/m3
   */
  public double getGasDensity() {
    return gasDensity;
  }

  /**
   * Set sour service flag.
   *
   * @param sourService true if H2S present
   */
  public void setSourService(boolean sourService) {
    this.sourService = sourService;
  }

  /**
   * Check if sour service.
   *
   * @return true if H2S present
   */
  public boolean isSourService() {
    return sourService;
  }

  /**
   * Set CO2 service flag.
   *
   * @param co2Service true if CO2 present
   */
  public void setCo2Service(boolean co2Service) {
    this.co2Service = co2Service;
  }

  /**
   * Check if CO2 service.
   *
   * @return true if CO2 present
   */
  public boolean isCo2Service() {
    return co2Service;
  }

  /**
   * Get insulation density.
   *
   * @return insulation density in kg/m3
   */
  public double getInsulationDensity() {
    return insulationDensity;
  }

  /**
   * Set insulation density.
   *
   * @param insulationDensity insulation density in kg/m3
   */
  public void setInsulationDensity(double insulationDensity) {
    this.insulationDensity = insulationDensity;
  }
}
