package neqsim.process.equipment.compressor;

import java.io.Serializable;

/**
 * Utility class providing correction factors for centrifugal compressor performance curves.
 *
 * <p>
 * This class implements industry-standard correction methods for more accurate compressor
 * performance prediction:
 * </p>
 * <ul>
 * <li><b>Reynolds Number Correction:</b> Adjusts efficiency for viscous effects at off-design
 * conditions</li>
 * <li><b>Mach Number Limitation:</b> Calculates stonewall (choke) flow based on sonic velocity</li>
 * <li><b>Multistage Surge Correction:</b> Adjusts surge line for multistage compressors at reduced
 * speeds</li>
 * </ul>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>API 617 - Axial and Centrifugal Compressors</li>
 * <li>ASME PTC-10 - Performance Test Code for Compressors</li>
 * <li>Ludwig's Applied Process Design for Chemical and Petrochemical Plants, Chapter 18</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class CompressorCurveCorrections implements Serializable {
  private static final long serialVersionUID = 1001L;

  /** Gas constant R = 8314 J/(kmol·K). */
  private static final double R_GAS = 8314.0;

  /** Reference Reynolds number for standard efficiency curves (typically 1e7). */
  private static final double REFERENCE_REYNOLDS = 1.0e7;

  /** Exponent for Reynolds correction (typically 0.1-0.2 for centrifugal compressors). */
  private static final double REYNOLDS_EXPONENT = 0.1;

  /** Critical Mach number threshold for stonewall onset. */
  private static final double CRITICAL_MACH = 0.9;

  /** Maximum Mach number (choke condition). */
  private static final double MAX_MACH = 1.0;

  /** Private constructor - utility class. */
  private CompressorCurveCorrections() {
    // Utility class - no instantiation
  }

  /**
   * Calculate Reynolds number correction factor for polytropic efficiency.
   *
   * <p>
   * The efficiency of centrifugal compressors varies with Reynolds number due to viscous losses in
   * the boundary layers. This correction is based on the correlation:
   * </p>
   * 
   * <pre>
   * η_corrected / η_reference = (Re_actual / Re_reference)^n
   * </pre>
   *
   * <p>
   * Where n is typically 0.1-0.2 for centrifugal compressors (API 617 suggests using surface
   * roughness correlations for more accuracy).
   * </p>
   *
   * @param actualReynolds Actual Reynolds number at operating conditions
   * @param referenceReynolds Reference Reynolds number for the performance map (default 1e7)
   * @return Efficiency correction factor (multiply by reference efficiency)
   */
  public static double calculateReynoldsEfficiencyCorrection(double actualReynolds,
      double referenceReynolds) {
    if (actualReynolds <= 0 || referenceReynolds <= 0) {
      return 1.0; // No correction if invalid input
    }

    // Limit correction to reasonable range (0.9 to 1.05)
    double correction = Math.pow(actualReynolds / referenceReynolds, REYNOLDS_EXPONENT);
    return Math.max(0.9, Math.min(1.05, correction));
  }

  /**
   * Calculate Reynolds number correction factor using default reference Reynolds number.
   *
   * @param actualReynolds Actual Reynolds number at operating conditions
   * @return Efficiency correction factor
   */
  public static double calculateReynoldsEfficiencyCorrection(double actualReynolds) {
    return calculateReynoldsEfficiencyCorrection(actualReynolds, REFERENCE_REYNOLDS);
  }

  /**
   * Calculate compressor Reynolds number.
   *
   * <p>
   * Reynolds number for compressors is typically defined as:
   * </p>
   * 
   * <pre>
   * Re = (u * D) / ν
   * </pre>
   *
   * <p>
   * Where u is tip speed, D is impeller diameter, and ν is kinematic viscosity.
   * </p>
   *
   * @param tipSpeed Impeller tip speed in m/s
   * @param impellerDiameter Impeller diameter in m
   * @param kinematicViscosity Kinematic viscosity in m²/s
   * @return Reynolds number (dimensionless)
   */
  public static double calculateReynoldsNumber(double tipSpeed, double impellerDiameter,
      double kinematicViscosity) {
    if (kinematicViscosity <= 0) {
      return REFERENCE_REYNOLDS; // Return reference value if viscosity is invalid
    }
    return (tipSpeed * impellerDiameter) / kinematicViscosity;
  }

  /**
   * Calculate impeller tip speed from rotational speed and diameter.
   *
   * @param rpm Rotational speed in revolutions per minute
   * @param diameter Impeller diameter in m
   * @return Tip speed in m/s
   */
  public static double calculateTipSpeed(double rpm, double diameter) {
    return Math.PI * diameter * rpm / 60.0;
  }

  /**
   * Calculate Mach number at compressor inlet or impeller eye.
   *
   * <p>
   * The Mach number is the ratio of gas velocity to sonic velocity:
   * </p>
   * 
   * <pre>
   * Ma = V / c
   * </pre>
   *
   * @param gasVelocity Gas velocity in m/s
   * @param sonicVelocity Speed of sound in the gas in m/s
   * @return Mach number (dimensionless)
   */
  public static double calculateMachNumber(double gasVelocity, double sonicVelocity) {
    if (sonicVelocity <= 0) {
      return 0.0;
    }
    return gasVelocity / sonicVelocity;
  }

  /**
   * Calculate sonic velocity (speed of sound) for an ideal gas.
   *
   * <p>
   * The speed of sound in an ideal gas is:
   * </p>
   * 
   * <pre>
   * c = sqrt(k * R * T / M)
   * </pre>
   *
   * <p>
   * Where k is the heat capacity ratio, R is the gas constant, T is temperature, and M is molar
   * mass.
   * </p>
   *
   * @param kappa Heat capacity ratio (Cp/Cv)
   * @param temperature Temperature in K
   * @param molarMass Molar mass in kg/kmol
   * @param compressibilityFactor Z-factor (use 1.0 for ideal gas)
   * @return Speed of sound in m/s
   */
  public static double calculateSonicVelocity(double kappa, double temperature, double molarMass,
      double compressibilityFactor) {
    if (molarMass <= 0 || temperature <= 0) {
      return 400.0; // Return typical value if invalid input
    }
    return Math.sqrt(kappa * compressibilityFactor * R_GAS * temperature / molarMass);
  }

  /**
   * Calculate maximum flow (stonewall/choke) based on Mach number limitation.
   *
   * <p>
   * The stonewall flow is limited by the sonic velocity at the impeller inlet. When the flow
   * velocity approaches the speed of sound (Ma ≈ 1), no further increase in flow is possible
   * regardless of downstream pressure reduction.
   * </p>
   *
   * <p>
   * The maximum flow can be estimated from:
   * </p>
   * 
   * <pre>
   * Q_max = A * c * Ma_critical
   * </pre>
   *
   * <p>
   * Where A is the flow area, c is sonic velocity, and Ma_critical ≈ 0.9-1.0.
   * </p>
   *
   * @param designFlow Design flow rate in m³/hr
   * @param sonicVelocity Speed of sound at inlet conditions in m/s
   * @param designMachNumber Mach number at design flow (typically 0.6-0.8)
   * @return Maximum (stonewall) flow in m³/hr
   */
  public static double calculateStonewallFlow(double designFlow, double sonicVelocity,
      double designMachNumber) {
    if (designMachNumber <= 0 || designMachNumber >= MAX_MACH) {
      return designFlow * 1.3; // Default 30% above design
    }
    // Scale flow based on the ratio of critical Mach to design Mach
    double maxFlowRatio = CRITICAL_MACH / designMachNumber;
    return designFlow * Math.min(maxFlowRatio, 1.5); // Cap at 150% of design
  }

  /**
   * Calculate stonewall flow correction for gas molecular weight.
   *
   * <p>
   * Heavier gases have lower sonic velocities, which means they choke at lower flow rates.
   * </p>
   *
   * @param referenceStonewallFlow Stonewall flow at reference conditions in m³/hr
   * @param referenceMolarMass Reference gas molar mass in kg/kmol
   * @param actualMolarMass Actual gas molar mass in kg/kmol
   * @param referenceKappa Reference heat capacity ratio
   * @param actualKappa Actual heat capacity ratio
   * @return Corrected stonewall flow in m³/hr
   */
  public static double correctStonewallFlowForGas(double referenceStonewallFlow,
      double referenceMolarMass, double actualMolarMass, double referenceKappa,
      double actualKappa) {
    // Sonic velocity ratio
    double sonicRatio =
        Math.sqrt((actualKappa * referenceMolarMass) / (referenceKappa * actualMolarMass));
    return referenceStonewallFlow * sonicRatio;
  }

  /**
   * Calculate surge flow correction for multistage compressors at reduced speeds.
   *
   * <p>
   * For multistage compressors, the simple fan law prediction for surge (Q_surge ∝ N) becomes
   * inaccurate at reduced speeds. This is because:
   * </p>
   * <ul>
   * <li>Volume reduction per stage is less at lower speeds</li>
   * <li>Later stages handle more than their rated volume</li>
   * <li>Surge can be initiated by different stages at different speeds</li>
   * </ul>
   *
   * <p>
   * This correction shifts the surge line to higher flows at reduced speeds, which better matches
   * observed behavior in multistage machines.
   * </p>
   *
   * @param surgeFanLawFlow Surge flow predicted by simple fan law in m³/hr
   * @param speedRatio Actual speed / Design speed (0 to 1)
   * @param numberOfStages Number of compression stages
   * @return Corrected surge flow in m³/hr
   */
  public static double calculateMultistageSurgeCorrection(double surgeFanLawFlow, double speedRatio,
      int numberOfStages) {
    if (numberOfStages <= 1 || speedRatio >= 1.0) {
      return surgeFanLawFlow; // No correction needed for single stage or full speed
    }

    // Correction factor increases as speed decreases and with more stages
    // Based on empirical correlations from Ludwig's Applied Process Design
    double speedFactor = Math.max(0.5, speedRatio); // Limit to avoid extreme corrections
    double stageFactor = 1.0 + 0.05 * (numberOfStages - 1); // ~5% per additional stage

    // The correction shifts surge flow higher at reduced speeds
    // At 70% speed with 5 stages: ~20% increase in surge flow
    double correctionFactor = 1.0 + (1.0 - speedFactor) * (stageFactor - 1.0) * 2.0;

    return surgeFanLawFlow * Math.min(correctionFactor, 1.4); // Cap at 40% increase
  }

  /**
   * Calculate corrected surge head for multistage compressors at reduced speeds.
   *
   * <p>
   * Similar to flow, the surge head may not follow simple fan law (H ∝ N²) exactly for multistage
   * machines at reduced speeds.
   * </p>
   *
   * @param surgeFanLawHead Surge head predicted by simple fan law in kJ/kg
   * @param speedRatio Actual speed / Design speed
   * @param numberOfStages Number of compression stages
   * @return Corrected surge head in kJ/kg
   */
  public static double calculateMultistageSurgeHeadCorrection(double surgeFanLawHead,
      double speedRatio, int numberOfStages) {
    if (numberOfStages <= 1 || speedRatio >= 1.0) {
      return surgeFanLawHead;
    }

    // The head at surge may be slightly lower than fan law prediction at reduced speeds
    double speedFactor = Math.max(0.5, speedRatio);
    double stageFactor = 1.0 + 0.02 * (numberOfStages - 1);

    double correctionFactor = 1.0 - (1.0 - speedFactor) * (stageFactor - 1.0) * 0.5;

    return surgeFanLawHead * Math.max(correctionFactor, 0.9); // Floor at 10% reduction
  }

  /**
   * Apply efficiency correction for operating away from best efficiency point (BEP).
   *
   * <p>
   * Compressor efficiency decreases as operating point moves away from BEP. This method calculates
   * a parabolic efficiency profile:
   * </p>
   * 
   * <pre>
   * η = η_max * [1 - k * (Q/Q_design - 1)²]
   * </pre>
   *
   * @param maxEfficiency Maximum (BEP) efficiency as fraction (0-1)
   * @param flowRatio Actual flow / Design flow
   * @param curveFactor Shape factor k (typically 0.3-0.5 for centrifugal compressors)
   * @return Efficiency at the specified flow ratio
   */
  public static double calculateEfficiencyAtFlow(double maxEfficiency, double flowRatio,
      double curveFactor) {
    double deviation = flowRatio - 1.0;
    double reduction = curveFactor * deviation * deviation;
    return maxEfficiency * Math.max(0.5, 1.0 - reduction); // Floor at 50% of max
  }

  /**
   * Calculate combined efficiency correction.
   *
   * <p>
   * This method combines Reynolds number correction and flow-based efficiency variation.
   * </p>
   *
   * @param baseEfficiency Base polytropic efficiency at design point (fraction 0-1)
   * @param actualReynolds Actual Reynolds number
   * @param flowRatio Actual flow / Design flow
   * @return Corrected efficiency (fraction 0-1)
   */
  public static double calculateCorrectedEfficiency(double baseEfficiency, double actualReynolds,
      double flowRatio) {
    double reynoldsCorrection = calculateReynoldsEfficiencyCorrection(actualReynolds);
    double flowCorrection = calculateEfficiencyAtFlow(1.0, flowRatio, 0.4) / 1.0;
    return baseEfficiency * reynoldsCorrection * flowCorrection;
  }

  /**
   * Get the reference Reynolds number used for efficiency corrections.
   *
   * @return Reference Reynolds number
   */
  public static double getReferenceReynolds() {
    return REFERENCE_REYNOLDS;
  }

  /**
   * Get the critical Mach number for stonewall calculations.
   *
   * @return Critical Mach number
   */
  public static double getCriticalMach() {
    return CRITICAL_MACH;
  }
}
