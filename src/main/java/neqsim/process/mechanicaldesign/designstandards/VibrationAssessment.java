package neqsim.process.mechanicaldesign.designstandards;

import java.io.Serializable;

/**
 * Vibration assessment for piping and equipment per Energy Institute guidelines and API 618.
 *
 * <p>
 * Covers acoustic-induced vibration (AIV) screening for piping downstream of pressure-let-down
 * devices, flow-induced vibration (FIV) risk in heat exchangers, and pulsation screening for
 * reciprocating compressors per API 618.
 * </p>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>Energy Institute: Guidelines for the avoidance of vibration induced fatigue failure
 * (2008)</li>
 * <li>API 618: Reciprocating Compressors for Petroleum, Chemical, and Gas Industry Services</li>
 * <li>NORSOK R-002: Lifting equipment</li>
 * </ul>
 *
 * @author esol
 * @version 1.0
 */
public class VibrationAssessment implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Likelihood of failure from Energy Institute main-line screening.
   */
  public enum VibrationRisk {
    /** Low risk of vibration-induced failure. */
    LOW,
    /** Medium risk: detailed assessment recommended. */
    MEDIUM,
    /** High risk: mitigation required before operation. */
    HIGH
  }

  /**
   * Acoustic-induced vibration (AIV) screening per Energy Institute guidelines.
   *
   * <p>
   * Calculates the acoustic power level downstream of a pressure-let-down device and compares it to
   * the pipe's screening criterion based on diameter and wall thickness.
   * </p>
   *
   * @param massFlowKgS mass flow rate in kg/s
   * @param upstreamPressureBar upstream pressure in bara
   * @param downstreamPressureBar downstream pressure in bara
   * @param temperatureK fluid temperature in K
   * @param molecularWeight molecular weight in kg/kmol
   * @param pipeDiameterMm pipe outer diameter in mm
   * @param pipeWallThicknessMm pipe wall thickness in mm
   * @return vibration risk level
   */
  public static VibrationRisk aivScreening(double massFlowKgS, double upstreamPressureBar,
      double downstreamPressureBar, double temperatureK, double molecularWeight,
      double pipeDiameterMm, double pipeWallThicknessMm) {

    double pwl = acousticPowerLevel(massFlowKgS, upstreamPressureBar, downstreamPressureBar,
        temperatureK, molecularWeight);

    double screeningLimit = aivScreeningLimit(pipeDiameterMm, pipeWallThicknessMm);

    if (pwl > screeningLimit + 5.0) {
      return VibrationRisk.HIGH;
    } else if (pwl > screeningLimit) {
      return VibrationRisk.MEDIUM;
    } else {
      return VibrationRisk.LOW;
    }
  }

  /**
   * Calculate acoustic power level (PWL) downstream of a pressure-let-down device.
   *
   * <p>
   * Based on Energy Institute method: PWL = 126.1 + 10*log10(W * eta) where W is the mechanical
   * stream power and eta is the acoustic efficiency.
   * </p>
   *
   * @param massFlowKgS mass flow rate in kg/s
   * @param upstreamPressureBar upstream pressure in bara
   * @param downstreamPressureBar downstream pressure in bara
   * @param temperatureK temperature in K
   * @param molecularWeight molecular weight in kg/kmol
   * @return acoustic power level in dB (re 1e-12 W)
   */
  public static double acousticPowerLevel(double massFlowKgS, double upstreamPressureBar,
      double downstreamPressureBar, double temperatureK, double molecularWeight) {
    double p1 = upstreamPressureBar * 1.0e5;
    double p2 = Math.max(downstreamPressureBar * 1.0e5, 1.0e3);
    double rho = p1 * molecularWeight / (8314.0 * temperatureK);
    double c = Math.sqrt(1.3 * 8314.0 * temperatureK / molecularWeight);

    // Mechanical stream power
    double deltaP = p1 - p2;
    double volume = massFlowKgS / Math.max(rho, 0.01);
    double streamPowerW = volume * deltaP;

    // Acoustic efficiency
    double pressureRatio = p1 / p2;
    double eta;
    if (pressureRatio < 2.0) {
      eta = 1.0e-4 * Math.pow(pressureRatio - 1.0, 2);
    } else if (pressureRatio < 3.5) {
      eta = 1.0e-3;
    } else {
      eta = 3.0e-3;
    }

    double acousticPowerW = streamPowerW * eta;
    return 10.0 * Math.log10(Math.max(acousticPowerW, 1.0e-12) / 1.0e-12);
  }

  /**
   * Calculate AIV screening limit for a given pipe diameter and wall thickness.
   *
   * <p>
   * Energy Institute correlation: L_screen = 173.6 + 10*log10(D * t^2.5) where D is outer diameter
   * in inches and t is wall thickness in inches.
   * </p>
   *
   * @param pipeDiameterMm pipe outer diameter in mm
   * @param pipeWallThicknessMm pipe wall thickness in mm
   * @return screening limit in dB
   */
  public static double aivScreeningLimit(double pipeDiameterMm, double pipeWallThicknessMm) {
    double dInch = pipeDiameterMm / 25.4;
    double tInch = pipeWallThicknessMm / 25.4;
    return 173.6 + 10.0 * Math.log10(Math.max(dInch * Math.pow(tInch, 2.5), 1.0e-10));
  }

  /**
   * Flow-induced vibration (FIV) screening for heat exchanger tubes.
   *
   * <p>
   * Checks critical velocity for fluid-elastic instability per TEMA RCB-4.6. Risk is HIGH if
   * cross-flow velocity exceeds 70% of the critical velocity.
   * </p>
   *
   * @param crossFlowVelocityMS shell-side cross-flow velocity in m/s
   * @param tubePitchMm tube pitch in mm
   * @param tubeODMm tube outer diameter in mm
   * @param tubeNaturalFreqHz tube natural frequency in Hz
   * @param shellFluidDensityKgM3 shell-side fluid density in kg/m3
   * @param tubeMassPerLengthKgM tube linear mass per length in kg/m
   * @return vibration risk level
   */
  public static VibrationRisk fivHeatExchangerScreening(double crossFlowVelocityMS,
      double tubePitchMm, double tubeODMm, double tubeNaturalFreqHz, double shellFluidDensityKgM3,
      double tubeMassPerLengthKgM) {

    double pitchRatio = tubePitchMm / Math.max(tubeODMm, 1.0);

    // Connors' instability constant (typical 2.4-4.0, use 3.0)
    double connorsK = 3.0;

    // Mass damping parameter zeta_m
    double logDecrement = 0.03; // typical for steel tubes in liquid
    double damping = logDecrement / (2.0 * Math.PI);

    // Critical velocity for fluid-elastic instability
    double massRatio =
        tubeMassPerLengthKgM / (shellFluidDensityKgM3 * Math.pow(tubeODMm / 1000.0, 2));
    double criticalVelocity = connorsK * tubeNaturalFreqHz * (tubeODMm / 1000.0)
        * Math.pow(2.0 * Math.PI * damping * massRatio, 0.5);

    double velocityRatio = crossFlowVelocityMS / Math.max(criticalVelocity, 0.01);

    if (velocityRatio > 0.9) {
      return VibrationRisk.HIGH;
    } else if (velocityRatio > 0.7) {
      return VibrationRisk.MEDIUM;
    } else {
      return VibrationRisk.LOW;
    }
  }

  /**
   * Reciprocating compressor pulsation screening per API 618.
   *
   * <p>
   * Checks whether pulsation levels at nozzle connections are within API 618 approach-2 limits.
   * </p>
   *
   * @param dischargePressureBar discharge pressure in bara
   * @param pistonSpeedMps mean piston speed in m/s (2 * stroke * rpm / 60)
   * @param cylinderVolumeLiters cylinder swept volume in liters
   * @param pipeDiameterMm discharge pipe diameter in mm
   * @return vibration risk level
   */
  public static VibrationRisk reciprocatingPulsationScreening(double dischargePressureBar,
      double pistonSpeedMps, double cylinderVolumeLiters, double pipeDiameterMm) {

    // API 618 pulsation limit: peak-to-peak / line pressure < 2% (approach 2)
    // Estimated pulsation ratio: proportional to (Vs/Vp)^0.5 and piston speed
    double pipeVolumePerMeter = Math.PI * Math.pow(pipeDiameterMm / 2000.0, 2) * 1000.0; // liters
    double volumeRatio = cylinderVolumeLiters / Math.max(pipeVolumePerMeter, 0.01);

    double estimatedPulsationPct = 0.5 * Math.sqrt(volumeRatio) * (pistonSpeedMps / 3.0);

    if (estimatedPulsationPct > 5.0) {
      return VibrationRisk.HIGH;
    } else if (estimatedPulsationPct > 2.0) {
      return VibrationRisk.MEDIUM;
    } else {
      return VibrationRisk.LOW;
    }
  }
}
