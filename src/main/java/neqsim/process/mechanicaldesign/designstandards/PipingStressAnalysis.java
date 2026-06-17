package neqsim.process.mechanicaldesign.designstandards;

import java.io.Serializable;

/**
 * Piping stress analysis utilities per ASME B31.3 (Process Piping).
 *
 * <p>
 * Provides calculations for thermal expansion stress, pipe support spacing (span tables), sustained
 * stress checks, and flexibility analysis. These are screening-level calculations suitable for
 * preliminary design; detailed analysis should use dedicated piping stress software.
 * </p>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>ASME B31.3: Process Piping</li>
 * <li>ASME B31.1: Power Piping</li>
 * </ul>
 *
 * @author esol
 * @version 1.0
 */
public class PipingStressAnalysis implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Steel coefficient of thermal expansion at 20C in mm/(m*C). */
  private static final double ALPHA_STEEL = 0.012;

  /** Young's modulus for carbon steel at ambient in MPa. */
  private static final double E_STEEL_MPA = 200000.0;

  /** Density of steel in kg/m3. */
  private static final double RHO_STEEL = 7850.0;

  /** Density of water for hydro-test in kg/m3. */
  private static final double RHO_WATER = 1000.0;

  /** Gravitational acceleration in m/s2. */
  private static final double G = 9.81;

  /**
   * Calculate thermal expansion of a pipe run.
   *
   * @param lengthM pipe length in meters
   * @param installTempC installation temperature in Celsius
   * @param operatingTempC operating temperature in Celsius
   * @param coefficientMmMC thermal expansion coefficient in mm/(m*C), default 0.012 for CS
   * @return thermal expansion in mm
   */
  public static double thermalExpansion(double lengthM, double installTempC, double operatingTempC,
      double coefficientMmMC) {
    return lengthM * coefficientMmMC * (operatingTempC - installTempC);
  }

  /**
   * Calculate thermal expansion using default carbon steel coefficient.
   *
   * @param lengthM pipe length in meters
   * @param installTempC installation temperature in Celsius
   * @param operatingTempC operating temperature in Celsius
   * @return thermal expansion in mm
   */
  public static double thermalExpansion(double lengthM, double installTempC,
      double operatingTempC) {
    return thermalExpansion(lengthM, installTempC, operatingTempC, ALPHA_STEEL);
  }

  /**
   * Calculate allowable thermal expansion stress range per ASME B31.3 Eq. (1a).
   *
   * <p>
   * S_A = f * (1.25 * S_c + 0.25 * S_h)
   * </p>
   *
   * @param scMPa basic allowable stress at minimum (cold) temperature in MPa
   * @param shMPa basic allowable stress at maximum (hot) temperature in MPa
   * @param f stress range reduction factor (1.0 for less than 7000 cycles)
   * @return allowable expansion stress range in MPa
   */
  public static double allowableExpansionStressRange(double scMPa, double shMPa, double f) {
    return f * (1.25 * scMPa + 0.25 * shMPa);
  }

  /**
   * Calculate sustained (longitudinal) stress per ASME B31.3.
   *
   * <p>
   * S_L = P*D/(4*t) + 0.75*i*M_A/Z
   * </p>
   *
   * @param pressureMPa internal design pressure in MPa
   * @param pipeODMm pipe outer diameter in mm
   * @param wallThicknessMm pipe wall thickness in mm
   * @param bendingMomentNm sustained bending moment at point in N*m (use 0 for straight pipe)
   * @param stressIntensificationFactor SIF (1.0 for straight pipe)
   * @return sustained stress in MPa
   */
  public static double sustainedStress(double pressureMPa, double pipeODMm, double wallThicknessMm,
      double bendingMomentNm, double stressIntensificationFactor) {
    double dM = pipeODMm / 1000.0;
    double tM = wallThicknessMm / 1000.0;
    double z = sectionModulus(pipeODMm, wallThicknessMm);

    double pressureStress = pressureMPa * dM / (4.0 * tM);
    double bendingStress = 0.75 * stressIntensificationFactor * bendingMomentNm / (z * 1.0e-9);
    // Convert Z from mm3 to m3: z_m3 = z_mm3 * 1e-9
    bendingStress = 0.75 * stressIntensificationFactor * bendingMomentNm / (z * 1.0e-9) / 1.0e6;

    return pressureStress + bendingStress;
  }

  /**
   * Calculate section modulus of pipe cross-section.
   *
   * @param pipeODMm pipe outer diameter in mm
   * @param wallThicknessMm pipe wall thickness in mm
   * @return section modulus in mm3
   */
  public static double sectionModulus(double pipeODMm, double wallThicknessMm) {
    double od = pipeODMm;
    double id = pipeODMm - 2.0 * wallThicknessMm;
    return Math.PI * (Math.pow(od, 4) - Math.pow(id, 4)) / (32.0 * od);
  }

  /**
   * Calculate moment of inertia of pipe cross-section.
   *
   * @param pipeODMm pipe outer diameter in mm
   * @param wallThicknessMm pipe wall thickness in mm
   * @return moment of inertia in mm4
   */
  public static double momentOfInertia(double pipeODMm, double wallThicknessMm) {
    double od = pipeODMm;
    double id = pipeODMm - 2.0 * wallThicknessMm;
    return Math.PI * (Math.pow(od, 4) - Math.pow(id, 4)) / 64.0;
  }

  /**
   * Calculate maximum allowable support span for a pipe.
   *
   * <p>
   * Based on simple-beam deflection formula with a maximum allowable midspan deflection of 3mm and
   * bending stress limit. Returns the lesser of deflection-limited and stress-limited spans.
   * </p>
   *
   * @param pipeODMm pipe outer diameter in mm
   * @param wallThicknessMm pipe wall thickness in mm
   * @param contentDensityKgM3 density of pipe contents in kg/m3
   * @param insulationThicknessMm insulation thickness in mm (0 if none)
   * @param insulationDensityKgM3 insulation density in kg/m3 (0 if none)
   * @param maxDeflectionMm maximum allowable midspan deflection in mm (typically 3)
   * @param allowableStressMPa allowable bending stress in MPa
   * @return maximum support span in meters
   */
  public static double maxSupportSpan(double pipeODMm, double wallThicknessMm,
      double contentDensityKgM3, double insulationThicknessMm, double insulationDensityKgM3,
      double maxDeflectionMm, double allowableStressMPa) {

    double odM = pipeODMm / 1000.0;
    double idM = (pipeODMm - 2.0 * wallThicknessMm) / 1000.0;
    double insOdM = odM + 2.0 * insulationThicknessMm / 1000.0;

    // Weight per meter
    double pipeWeightPerM = RHO_STEEL * Math.PI * (odM * odM - idM * idM) / 4.0 * G;
    double contentWeightPerM = contentDensityKgM3 * Math.PI * idM * idM / 4.0 * G;
    double insWeightPerM =
        insulationDensityKgM3 * Math.PI * (insOdM * insOdM - odM * odM) / 4.0 * G;
    double totalWeightPerM = pipeWeightPerM + contentWeightPerM + insWeightPerM;

    if (totalWeightPerM <= 0) {
      return 12.0; // default max span
    }

    double iMm4 = momentOfInertia(pipeODMm, wallThicknessMm);
    double iM4 = iMm4 * 1.0e-12;
    double zMm3 = sectionModulus(pipeODMm, wallThicknessMm);

    // Deflection-limited span: delta = 5*w*L^4 / (384*E*I)
    // L = (384*E*I*delta / (5*w))^0.25
    double deflLimitedSpan = Math.pow(
        384.0 * E_STEEL_MPA * 1.0e6 * iM4 * (maxDeflectionMm / 1000.0) / (5.0 * totalWeightPerM),
        0.25);

    // Stress-limited span: M = w*L^2/8, sigma = M/Z
    // L = sqrt(8 * sigma * Z / w)
    double stressLimitedSpan =
        Math.sqrt(8.0 * allowableStressMPa * 1.0e6 * (zMm3 * 1.0e-9) / totalWeightPerM);

    double span = Math.min(deflLimitedSpan, stressLimitedSpan);
    return Math.min(span, 12.0); // practical max 12m
  }

  /**
   * Calculate required expansion loop length for a straight pipe run.
   *
   * <p>
   * Using the simplified formula: L_loop = sqrt(3 * E * D * delta / S_A)
   * </p>
   *
   * @param expansionMm total thermal expansion to absorb in mm
   * @param pipeODMm pipe outer diameter in mm
   * @param allowableStressMPa allowable expansion stress in MPa
   * @return required loop leg length in meters
   */
  public static double expansionLoopLength(double expansionMm, double pipeODMm,
      double allowableStressMPa) {
    double dM = pipeODMm / 1000.0;
    double deltaM = expansionMm / 1000.0;

    double legLength = Math.sqrt(3.0 * E_STEEL_MPA * dM * deltaM / allowableStressMPa);
    return legLength / 1000.0; // mm to m
  }

  /**
   * Check whether code stress requirements are met.
   *
   * @param sustainedStressMPa calculated sustained stress in MPa
   * @param expansionStressMPa calculated expansion stress in MPa
   * @param allowableHotMPa allowable stress at hot temperature in MPa
   * @param allowableExpansionMPa allowable expansion stress range in MPa
   * @return true if both sustained and expansion stresses are within limits
   */
  public static boolean codeStressCheck(double sustainedStressMPa, double expansionStressMPa,
      double allowableHotMPa, double allowableExpansionMPa) {
    return sustainedStressMPa <= allowableHotMPa && expansionStressMPa <= allowableExpansionMPa;
  }
}
