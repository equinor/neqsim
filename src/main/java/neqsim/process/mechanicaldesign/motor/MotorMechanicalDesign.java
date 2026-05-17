package neqsim.process.mechanicaldesign.motor;

import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.electricaldesign.ElectricalDesign;
import neqsim.process.electricaldesign.components.ElectricalMotor;

/**
 * Mechanical design of electric motors per IEC 60034, IEEE 841, and NORSOK E-001.
 *
 * <p>
 * Covers the physical/mechanical aspects of electric motor design:
 * </p>
 * <ul>
 * <li>Foundation loads (static + dynamic) per IEC 60034-14</li>
 * <li>Cooling system classification per IEC 60034-6 (IC codes)</li>
 * <li>Bearing selection and L10 life per ISO 281</li>
 * <li>Vibration limits per ISO 10816-3 / IEC 60034-14</li>
 * <li>Noise limits per IEC 60034-9 and NORSOK S-002</li>
 * <li>Enclosure and IP protection per IEC 60034-5</li>
 * <li>Environmental derating per IEC 60034-1 (altitude, temperature)</li>
 * <li>Motor weight and dimensional estimation</li>
 * </ul>
 *
 * <p>
 * This class is designed to work alongside the {@link ElectricalDesign} which handles the
 * electrical sizing (power, voltage, current, efficiency class). Together they provide a complete
 * motor specification.
 * </p>
 *
 * <p>
 * <strong>Standards Reference</strong>
 * </p>
 * <table>
 * <caption>Applicable standards for motor mechanical design</caption>
 * <tr>
 * <th>Standard</th>
 * <th>Scope</th>
 * </tr>
 * <tr>
 * <td>IEC 60034-1</td>
 * <td>Rating and performance</td>
 * </tr>
 * <tr>
 * <td>IEC 60034-5</td>
 * <td>Degrees of protection (IP code)</td>
 * </tr>
 * <tr>
 * <td>IEC 60034-6</td>
 * <td>Methods of cooling (IC code)</td>
 * </tr>
 * <tr>
 * <td>IEC 60034-9</td>
 * <td>Noise limits</td>
 * </tr>
 * <tr>
 * <td>IEC 60034-14</td>
 * <td>Mechanical vibration</td>
 * </tr>
 * <tr>
 * <td>IEC 60034-30-1</td>
 * <td>Efficiency classes (IE1-IE4)</td>
 * </tr>
 * <tr>
 * <td>IEEE 841</td>
 * <td>Petroleum/chemical industry motors</td>
 * </tr>
 * <tr>
 * <td>ISO 10816-3</td>
 * <td>Vibration evaluation (industrial machines)</td>
 * </tr>
 * <tr>
 * <td>ISO 281</td>
 * <td>Rolling bearing life calculation</td>
 * </tr>
 * <tr>
 * <td>NORSOK E-001</td>
 * <td>Electrical systems</td>
 * </tr>
 * <tr>
 * <td>NORSOK S-002</td>
 * <td>Working environment (noise limits)</td>
 * </tr>
 * </table>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class MotorMechanicalDesign implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  // ============================================================================
  // IEC 60034-9 Sound Power Level Limits (dB(A)) for TEFC, 4-pole at 50 Hz
  // ============================================================================
  /** Sound power limits for small motors in dB(A). */
  private static final double[][] NOISE_LIMITS_4P_50HZ = {
      // {maxPowerKW, soundPowerLevelDbA}
      {1.1, 73}, {2.2, 78}, {5.5, 83}, {11, 87}, {22, 90}, {37, 93}, {55, 95}, {90, 97}, {132, 99},
      {200, 101}, {315, 103}, {500, 105}, {1000, 108}, {5000, 113}, {10000, 116}};

  // ============================================================================
  // ISO 10816-3 Vibration Severity Zones
  // ============================================================================
  /** Zone A/B boundary for Group 2 (15-300 kW) in mm/s RMS. */
  private static final double VIB_GROUP2_ZONE_AB = 2.3;
  /** Zone B/C boundary for Group 2 in mm/s RMS. */
  private static final double VIB_GROUP2_ZONE_BC = 4.5;
  /** Zone C/D boundary for Group 2 in mm/s RMS. */
  private static final double VIB_GROUP2_ZONE_CD = 7.1;

  /** Zone A/B boundary for Group 1 (above 300 kW) in mm/s RMS. */
  private static final double VIB_GROUP1_ZONE_AB = 3.5;
  /** Zone B/C boundary for Group 1 in mm/s RMS. */
  private static final double VIB_GROUP1_ZONE_BC = 7.1;
  /** Zone C/D boundary for Group 1 in mm/s RMS. */
  private static final double VIB_GROUP1_ZONE_CD = 11.0;

  // ============================================================================
  // Design Input
  // ============================================================================
  /** Shaft power required from the driven equipment in kW. */
  private double shaftPowerKW = 0.0;

  /** Rated speed in RPM. */
  private double ratedSpeedRPM = 1475.0;

  /** Number of poles (2, 4, 6, 8). */
  private int poles = 4;

  /** Supply frequency in Hz. */
  private double frequencyHz = 50.0;

  /** Ambient temperature in Celsius. */
  private double ambientTemperatureC = 40.0;

  /** Installation altitude in meters above sea level. */
  private double altitudeM = 0.0;

  /** Whether the motor has a VFD. */
  private boolean hasVFD = false;

  /** Motor standard: "IEC" or "NEMA". */
  private String motorStandard = "IEC";

  /** Hazardous area zone (-1 = non-hazardous, 0, 1, 2). */
  private int hazardousZone = -1;

  /** Gas group for Ex classification (IIA, IIB, IIC). */
  private String gasGroup = "IIA";

  // ============================================================================
  // Design Outputs — Foundation
  // ============================================================================
  /** Motor weight in kg. */
  private double motorWeightKg = 0.0;

  /** Static foundation load in kN. */
  private double staticFoundationLoadKN = 0.0;

  /** Dynamic foundation load in kN (due to unbalance). */
  private double dynamicFoundationLoadKN = 0.0;

  /** Total foundation load in kN (static + dynamic). */
  private double totalFoundationLoadKN = 0.0;

  /** Minimum foundation weight ratio (concrete/motor per IEEE 841). */
  private double foundationWeightRatio = 3.0;

  /** Required foundation mass in kg. */
  private double requiredFoundationMassKg = 0.0;

  /** Foundation type recommendation. */
  private String foundationType = "";

  // ============================================================================
  // Design Outputs — Vibration
  // ============================================================================
  /** Vibration grade per IEC 60034-14 (A or B). */
  private String vibrationGrade = "A";

  /** Maximum vibration velocity in mm/s RMS at bearing housing. */
  private double maxVibrationMmS = 0.0;

  /** Vibration zone per ISO 10816-3 (A, B, C, or D). */
  private String vibrationZone = "";

  // ============================================================================
  // Design Outputs — Cooling
  // ============================================================================
  /** IC code per IEC 60034-6. */
  private String coolingCode = "IC411";

  /** Cooling method description. */
  private String coolingDescription = "";

  /** Heat dissipation in kW. */
  private double heatDissipationKW = 0.0;

  /** Cooling air flow required in m3/s (for fan-cooled). */
  private double coolingAirFlowM3s = 0.0;

  // ============================================================================
  // Design Outputs — Bearings
  // ============================================================================
  /** Bearing type. */
  private String bearingType = "deep groove ball";

  /** Drive-end bearing designation. */
  private String deBearingDesignation = "";

  /** Non-drive-end bearing designation. */
  private String ndeBearingDesignation = "";

  /** Calculated L10 bearing life in hours. */
  private double bearingL10LifeHours = 0.0;

  /** Minimum L10 bearing life requirement per IEEE 841 (hours). */
  private double minBearingLifeHours = 26280.0; // 3 years

  /** Lubrication recommendation. */
  private String lubricationMethod = "grease";

  /** Re-greasing interval in hours. */
  private double regreaseIntervalHours = 0.0;

  // ============================================================================
  // Design Outputs — Noise
  // ============================================================================
  /** Sound power level in dB(A). */
  private double soundPowerLevelDbA = 0.0;

  /** Sound pressure level at 1m in dB(A). */
  private double soundPressureLevelAt1mDbA = 0.0;

  /** IEC 60034-9 noise limit in dB(A). */
  private double noiseLimitDbA = 0.0;

  /** Whether noise meets IEC 60034-9 limit. */
  private boolean noiseWithinLimit = true;

  /** Whether noise meets NORSOK S-002 limit (83 dB(A) at 1m). */
  private boolean noiseWithinNorsokLimit = true;

  // ============================================================================
  // Design Outputs — Enclosure / Protection
  // ============================================================================
  /** IP protection rating per IEC 60034-5. */
  private String ipRating = "IP55";

  /** Enclosure type description. */
  private String enclosureType = "TEFC";

  /** Ex protection marking (empty if non-hazardous). */
  private String exMarking = "";

  // ============================================================================
  // Design Outputs — Derating
  // ============================================================================
  /** Altitude derating factor (1.0 = no derating). */
  private double altitudeDeratingFactor = 1.0;

  /** Temperature derating factor (1.0 = no derating). */
  private double temperatureDeratingFactor = 1.0;

  /** Combined derating factor. */
  private double combinedDeratingFactor = 1.0;

  /** Derated motor output power in kW. */
  private double deratedPowerKW = 0.0;

  // ============================================================================
  // Design Outputs — Overall
  // ============================================================================
  /** Motor length in mm (estimated). */
  private double motorLengthMm = 0.0;

  /** Motor width in mm (estimated). */
  private double motorWidthMm = 0.0;

  /** Motor height in mm (estimated). */
  private double motorHeightMm = 0.0;

  /** Shaft height in mm (center of shaft above base). */
  private double shaftHeightMm = 0.0;

  /** List of applied standards. */
  private List<String> appliedStandards = new ArrayList<String>();

  /** List of design notes/warnings. */
  private List<String> designNotes = new ArrayList<String>();

  /**
   * Default constructor.
   */
  public MotorMechanicalDesign() {}

  /**
   * Constructor with shaft power.
   *
   * @param shaftPowerKW required shaft power in kW
   */
  public MotorMechanicalDesign(double shaftPowerKW) {
    this.shaftPowerKW = shaftPowerKW;
  }

  /**
   * Construct from an existing ElectricalDesign (reads motor data).
   *
   * @param electricalDesign the electrical design to read motor params from
   */
  public MotorMechanicalDesign(ElectricalDesign electricalDesign) {
    if (electricalDesign != null && electricalDesign.getMotor() != null) {
      ElectricalMotor motor = electricalDesign.getMotor();
      this.shaftPowerKW = motor.getRatedPowerKW();
      this.ratedSpeedRPM = motor.getRatedSpeedRPM();
      this.poles = motor.getPoles();
      this.frequencyHz = motor.getFrequencyHz();
      this.hasVFD = electricalDesign.isUseVFD();
    }
  }

  /**
   * Run all mechanical design calculations.
   *
   * <p>
   * Calculates foundation loads, vibration limits, cooling requirements, bearing selection, noise
   * assessment, enclosure selection, and environmental derating.
   * </p>
   */
  public void calcDesign() {
    if (shaftPowerKW <= 0) {
      return;
    }

    appliedStandards.clear();
    designNotes.clear();

    calcDerating();
    calcMotorDimensions();
    calcFoundation();
    calcCooling();
    calcBearings();
    calcVibration();
    calcNoise();
    calcEnclosure();

    appliedStandards.add("IEC 60034-1 (Rating and performance)");
    appliedStandards.add("IEC 60034-5 (Degrees of protection)");
    appliedStandards.add("IEC 60034-6 (Methods of cooling)");
    appliedStandards.add("IEC 60034-9 (Noise limits)");
    appliedStandards.add("IEC 60034-14 (Mechanical vibration)");
    appliedStandards.add("ISO 10816-3 (Vibration evaluation)");
    appliedStandards.add("ISO 281 (Rolling bearing life)");
    if ("IEC".equals(motorStandard)) {
      appliedStandards.add("IEC 60034-30-1 (Efficiency classes)");
    } else {
      appliedStandards.add("NEMA MG 1 (Motors and generators)");
    }
    if (hazardousZone >= 0) {
      appliedStandards.add("IEC 60079 (Explosive atmospheres)");
    }
    appliedStandards.add("IEEE 841 (Petroleum/chemical industry motors)");
    appliedStandards.add("NORSOK E-001 (Electrical systems)");
    appliedStandards.add("NORSOK S-002 (Working environment)");
  }

  /**
   * Calculate environmental derating per IEC 60034-1.
   *
   * <p>
   * Standard rating is for ambient max 40 deg C at altitudes up to 1000m. Above these limits, the
   * motor output must be derated.
   * </p>
   */
  private void calcDerating() {
    // Altitude derating per IEC 60034-1 Clause 6
    if (altitudeM > 1000.0) {
      // Approximately 1% per 100m above 1000m
      altitudeDeratingFactor = 1.0 - 0.01 * ((altitudeM - 1000.0) / 100.0);
      altitudeDeratingFactor = Math.max(altitudeDeratingFactor, 0.7); // practical limit
      designNotes.add("Motor derated for altitude " + altitudeM + "m per IEC 60034-1 Clause 6");
    } else {
      altitudeDeratingFactor = 1.0;
    }

    // Temperature derating per IEC 60034-1
    // Standard is 40 deg C ambient; above this derate ~2.5% per degree above 40 C
    if (ambientTemperatureC > 40.0) {
      temperatureDeratingFactor = 1.0 - 0.025 * (ambientTemperatureC - 40.0);
      temperatureDeratingFactor = Math.max(temperatureDeratingFactor, 0.6);
      designNotes
          .add("Motor derated for ambient " + ambientTemperatureC + " deg C per IEC 60034-1");
    } else {
      temperatureDeratingFactor = 1.0;
    }

    combinedDeratingFactor = altitudeDeratingFactor * temperatureDeratingFactor;
    deratedPowerKW = shaftPowerKW / combinedDeratingFactor;
  }

  /**
   * Estimate motor dimensions and weight based on rated power.
   *
   * <p>
   * Approximations based on IEC frame sizes with TEFC enclosure.
   * </p>
   */
  private void calcMotorDimensions() {
    // Weight estimation (kg) — industry correlations
    if (shaftPowerKW <= 7.5) {
      motorWeightKg = shaftPowerKW * 5.0 + 15.0;
    } else if (shaftPowerKW <= 75) {
      motorWeightKg = shaftPowerKW * 3.5 + 26.0;
    } else if (shaftPowerKW <= 375) {
      motorWeightKg = shaftPowerKW * 2.5 + 100.0;
    } else if (shaftPowerKW <= 2000) {
      motorWeightKg = shaftPowerKW * 1.8 + 362.0;
    } else {
      motorWeightKg = shaftPowerKW * 1.5 + 962.0;
    }

    // Shaft height (center of shaft above base) per IEC frame size
    if (shaftPowerKW <= 1.5) {
      shaftHeightMm = 90;
    } else if (shaftPowerKW <= 5.5) {
      shaftHeightMm = 132;
    } else if (shaftPowerKW <= 22) {
      shaftHeightMm = 180;
    } else if (shaftPowerKW <= 90) {
      shaftHeightMm = 280;
    } else if (shaftPowerKW <= 315) {
      shaftHeightMm = 355;
    } else if (shaftPowerKW <= 1000) {
      shaftHeightMm = 450;
    } else {
      shaftHeightMm = 560;
    }

    // Approximate overall dimensions
    motorHeightMm = shaftHeightMm * 1.5;
    motorWidthMm = shaftHeightMm * 1.2;
    motorLengthMm = shaftHeightMm * 2.5;

    // For high-speed 2-pole motors, length is longer relative to height
    if (poles == 2) {
      motorLengthMm *= 1.2;
      motorWidthMm *= 0.9;
    }
    // For low-speed 6/8-pole motors, wider but shorter
    if (poles >= 6) {
      motorLengthMm *= 0.9;
      motorWidthMm *= 1.15;
    }
  }

  /**
   * Calculate foundation loads per IEC 60034-14 and IEEE 841.
   */
  private void calcFoundation() {
    // Static load: motor weight
    staticFoundationLoadKN = motorWeightKg * 9.81 / 1000.0;

    // Dynamic load: unbalance force at running speed
    // Residual unbalance per IEC 60034-14 Grade A:
    // e_per = 6300 / speed (microns) for Grade A
    double ePerMicrons = 6300.0 / ratedSpeedRPM;
    // Unbalance force F = m * e * omega^2
    double rotorMass = motorWeightKg * 0.3; // rotor ~30% of total weight
    double omega = 2.0 * Math.PI * ratedSpeedRPM / 60.0;
    double unbalanceForce = rotorMass * (ePerMicrons * 1e-6) * omega * omega;
    dynamicFoundationLoadKN = unbalanceForce / 1000.0;

    // Total foundation load
    totalFoundationLoadKN = staticFoundationLoadKN + dynamicFoundationLoadKN;

    // Foundation mass per IEEE 841 minimum 3:1 ratio
    requiredFoundationMassKg = motorWeightKg * foundationWeightRatio;

    // Foundation type recommendation
    if (shaftPowerKW <= 37) {
      foundationType = "Steel baseplate on concrete pad";
    } else if (shaftPowerKW <= 315) {
      foundationType = "Concrete block foundation";
    } else {
      foundationType = "Reinforced concrete block with dynamic analysis";
      designNotes
          .add("Large motor (" + shaftPowerKW + " kW) — dynamic foundation analysis recommended");
    }
  }

  /**
   * Calculate cooling requirements per IEC 60034-6.
   */
  private void calcCooling() {
    // Estimate motor efficiency for loss calculation
    double efficiency = 0.90;
    if (shaftPowerKW > 7.5) {
      efficiency = 0.93;
    }
    if (shaftPowerKW > 75) {
      efficiency = 0.95;
    }
    if (shaftPowerKW > 375) {
      efficiency = 0.96;
    }

    // Heat dissipation = input power - shaft power = shaft power * (1/eff - 1)
    heatDissipationKW = shaftPowerKW * (1.0 / efficiency - 1.0);

    // Cooling air flow for TEFC motors
    // Rough estimate: 0.05 m3/s per kW of losses for 30 C temp rise
    coolingAirFlowM3s = heatDissipationKW * 0.05;

    // Select cooling code per IEC 60034-6
    if (shaftPowerKW <= 315) {
      coolingCode = "IC411"; // Surface-cooled by frame-mounted fan
      coolingDescription = "Totally enclosed fan-cooled (TEFC)";
    } else if (shaftPowerKW <= 2000) {
      coolingCode = "IC611"; // Air-to-air heat exchanger (TEAAC)
      coolingDescription = "Totally enclosed air-to-air cooled (TEAAC)";
    } else {
      coolingCode = "IC81W"; // Water-cooled
      coolingDescription = "Totally enclosed water-cooled (TEWC)";
      designNotes.add("Water cooling required — ensure CW supply at design conditions");
    }

    // VFD operation reduces fan cooling at low speed
    if (hasVFD && "IC411".equals(coolingCode)) {
      designNotes
          .add("VFD operation — consider independent fan (IC416) for operation below 50% speed");
    }
  }

  /**
   * Calculate bearing selection and L10 life per ISO 281.
   */
  private void calcBearings() {
    // Bearing type selection based on motor size
    if (shaftPowerKW <= 150) {
      bearingType = "deep groove ball";
      deBearingDesignation = estimateBallBearing(shaftPowerKW, true);
      ndeBearingDesignation = estimateBallBearing(shaftPowerKW, false);
      lubricationMethod = "grease";
    } else if (shaftPowerKW <= 1000) {
      bearingType = "deep groove ball (DE) / cylindrical roller (NDE)";
      deBearingDesignation = estimateBallBearing(shaftPowerKW, true);
      ndeBearingDesignation = estimateRollerBearing(shaftPowerKW);
      lubricationMethod = "grease";
    } else {
      bearingType = "sleeve / tilting-pad";
      deBearingDesignation = "Sleeve bearing";
      ndeBearingDesignation = "Sleeve bearing";
      lubricationMethod = "forced oil";
      designNotes.add("Large motor — oil lubrication system required per IEEE 841");
    }

    // L10 life calculation per ISO 281 (simplified)
    // L10h = (C/P)^p * 10^6 / (60 * n) where p=3 for ball, p=10/3 for roller
    double dynamicLoadRatingC = estimateDynamicLoadRating(shaftPowerKW);
    double equivalentLoadP = staticFoundationLoadKN * 1000.0 * 0.5; // N, rough approx
    equivalentLoadP = Math.max(equivalentLoadP, 100.0); // minimum

    double exponent = "deep groove ball".equals(bearingType) ? 3.0 : (10.0 / 3.0);
    double loadRatio = dynamicLoadRatingC / equivalentLoadP;
    bearingL10LifeHours = Math.pow(loadRatio, exponent) * 1.0e6 / (60.0 * ratedSpeedRPM);
    bearingL10LifeHours = Math.min(bearingL10LifeHours, 200000); // practical cap

    // Check against IEEE 841 minimum (3 years = 26280 hours)
    if (bearingL10LifeHours < minBearingLifeHours) {
      designNotes.add("Bearing L10 life (" + (int) bearingL10LifeHours
          + " h) below IEEE 841 minimum (" + (int) minBearingLifeHours + " h)");
    }

    // Re-greasing interval (rule of thumb)
    if ("grease".equals(lubricationMethod)) {
      // Approx: re-grease interval = 10000 / (speed/1000)^2 hours
      double speedFactor = ratedSpeedRPM / 1000.0;
      regreaseIntervalHours = 10000.0 / (speedFactor * speedFactor);
      regreaseIntervalHours = Math.max(regreaseIntervalHours, 2000);
      regreaseIntervalHours = Math.min(regreaseIntervalHours, 20000);
    }
  }

  /**
   * Estimate ball bearing designation based on motor power.
   *
   * @param powerKW motor power in kW
   * @param isDriveEnd true for drive end, false for non-drive end
   * @return bearing designation string
   */
  private String estimateBallBearing(double powerKW, boolean isDriveEnd) {
    // Approximate bore size from shaft diameter estimate
    if (powerKW <= 7.5) {
      return isDriveEnd ? "6207" : "6205";
    } else if (powerKW <= 30) {
      return isDriveEnd ? "6310" : "6308";
    } else if (powerKW <= 90) {
      return isDriveEnd ? "6314" : "6312";
    } else if (powerKW <= 200) {
      return isDriveEnd ? "6318" : "6316";
    } else {
      return isDriveEnd ? "6322" : "6320";
    }
  }

  /**
   * Estimate roller bearing designation for NDE.
   *
   * @param powerKW motor power in kW
   * @return bearing designation string
   */
  private String estimateRollerBearing(double powerKW) {
    if (powerKW <= 200) {
      return "NU314";
    } else if (powerKW <= 500) {
      return "NU318";
    } else {
      return "NU322";
    }
  }

  /**
   * Estimate bearing dynamic load rating in N.
   *
   * @param powerKW motor power in kW
   * @return dynamic load rating in N
   */
  private double estimateDynamicLoadRating(double powerKW) {
    if (powerKW <= 7.5) {
      return 25000.0;
    } else if (powerKW <= 30) {
      return 52000.0;
    } else if (powerKW <= 90) {
      return 95000.0;
    } else if (powerKW <= 200) {
      return 143000.0;
    } else if (powerKW <= 500) {
      return 208000.0;
    } else {
      return 300000.0;
    }
  }

  /**
   * Calculate vibration limits per IEC 60034-14 and ISO 10816-3.
   */
  private void calcVibration() {
    // IEC 60034-14 maximum vibration velocity for Grade A
    if (shaftPowerKW <= 15) {
      maxVibrationMmS = 1.6; // Grade A, rigid mounting
    } else if (shaftPowerKW <= 300) {
      maxVibrationMmS = 2.5; // Grade A
    } else {
      maxVibrationMmS = 3.5; // Grade A for large machines
    }

    // Determine ISO 10816-3 vibration zone
    if (shaftPowerKW <= 300) {
      // Group 2: 15-300 kW
      if (maxVibrationMmS <= VIB_GROUP2_ZONE_AB) {
        vibrationZone = "A";
      } else if (maxVibrationMmS <= VIB_GROUP2_ZONE_BC) {
        vibrationZone = "B";
      } else if (maxVibrationMmS <= VIB_GROUP2_ZONE_CD) {
        vibrationZone = "C";
      } else {
        vibrationZone = "D";
      }
    } else {
      // Group 1: above 300 kW
      if (maxVibrationMmS <= VIB_GROUP1_ZONE_AB) {
        vibrationZone = "A";
      } else if (maxVibrationMmS <= VIB_GROUP1_ZONE_BC) {
        vibrationZone = "B";
      } else if (maxVibrationMmS <= VIB_GROUP1_ZONE_CD) {
        vibrationZone = "C";
      } else {
        vibrationZone = "D";
      }
    }

    vibrationGrade = "A"; // IEC 60034-14 Grade A per NORSOK E-001
  }

  /**
   * Calculate noise levels per IEC 60034-9 and check NORSOK S-002.
   */
  private void calcNoise() {
    // Estimate sound power level from IEC 60034-9 limits for TEFC, 4-pole, 50 Hz
    noiseLimitDbA = 73.0;
    for (double[] entry : NOISE_LIMITS_4P_50HZ) {
      if (shaftPowerKW <= entry[0]) {
        noiseLimitDbA = entry[1];
        break;
      }
    }

    // For 2-pole motors, add approximately 7 dB
    // For 6-pole motors, subtract approximately 3 dB
    double poleCorrection = 0.0;
    if (poles == 2) {
      poleCorrection = 7.0;
    } else if (poles == 6) {
      poleCorrection = -3.0;
    } else if (poles == 8) {
      poleCorrection = -5.0;
    }

    // Estimated sound power level (assume motor is near the limit)
    soundPowerLevelDbA = noiseLimitDbA + poleCorrection;

    // Sound pressure level at 1m distance
    // Lp = Lw - 10*log10(2*pi*r^2) ≈ Lw - 8 for r=1m
    soundPressureLevelAt1mDbA = soundPowerLevelDbA - 8.0;

    noiseWithinLimit = soundPowerLevelDbA <= (noiseLimitDbA + poleCorrection);
    noiseWithinNorsokLimit = soundPressureLevelAt1mDbA <= 83.0;

    if (!noiseWithinNorsokLimit) {
      designNotes.add("Noise level (" + String.format("%.0f", soundPressureLevelAt1mDbA)
          + " dB(A) at 1m) exceeds NORSOK S-002 limit of 83 dB(A)");
    }

    if (hasVFD) {
      designNotes.add("VFD may increase motor noise by 3-8 dB(A) due to harmonic excitation "
          + "— consider output filter or dU/dt reactor");
    }
  }

  /**
   * Determine enclosure and protection rating.
   */
  private void calcEnclosure() {
    // Base enclosure
    enclosureType = "TEFC";
    ipRating = "IP55";

    // Hazardous area — select appropriate Ex protection
    if (hazardousZone == 0) {
      exMarking = "Ex d " + gasGroup + " T3 Gb";
      ipRating = "IP66";
      enclosureType = "Flameproof (Ex d)";
      designNotes.add("Zone 0 — Ex d flameproof motor required per IEC 60079-1");
    } else if (hazardousZone == 1) {
      exMarking = "Ex d " + gasGroup + " T3 Gb";
      ipRating = "IP55";
      enclosureType = "Flameproof (Ex d)";
    } else if (hazardousZone == 2) {
      exMarking = "Ex nA " + gasGroup + " T3 Gc";
      ipRating = "IP55";
      enclosureType = "Non-sparking (Ex nA)";
    }

    // IEEE 841 requirements for petroleum industry
    if (shaftPowerKW >= 0.75 && hazardousZone < 0) {
      ipRating = "IP55"; // minimum per IEEE 841
    }

    // Outdoor / marine environment
    if (ambientTemperatureC > 40.0 || altitudeM > 0) {
      designNotes.add("IP55 minimum per IEEE 841 for petroleum/chemical service");
    }
  }

  /**
   * Generate comprehensive JSON report of motor mechanical design.
   *
   * @return JSON string with all design data
   */
  public String toJson() {
    Map<String, Object> map = toMap();
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(map);
  }

  /**
   * Convert all design data to a map for report generation.
   *
   * @return ordered map of all design parameters
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();

    // Design input
    Map<String, Object> input = new LinkedHashMap<String, Object>();
    input.put("shaftPowerKW", shaftPowerKW);
    input.put("ratedSpeedRPM", ratedSpeedRPM);
    input.put("poles", poles);
    input.put("frequencyHz", frequencyHz);
    input.put("ambientTemperatureC", ambientTemperatureC);
    input.put("altitudeM", altitudeM);
    input.put("hasVFD", hasVFD);
    input.put("motorStandard", motorStandard);
    input.put("hazardousZone", hazardousZone);
    map.put("designInput", input);

    // Derating
    Map<String, Object> derating = new LinkedHashMap<String, Object>();
    derating.put("altitudeDeratingFactor", altitudeDeratingFactor);
    derating.put("temperatureDeratingFactor", temperatureDeratingFactor);
    derating.put("combinedDeratingFactor", combinedDeratingFactor);
    derating.put("deratedPowerKW", deratedPowerKW);
    map.put("derating", derating);

    // Motor dimensions
    Map<String, Object> dimensions = new LinkedHashMap<String, Object>();
    dimensions.put("motorWeightKg", motorWeightKg);
    dimensions.put("motorLengthMm", motorLengthMm);
    dimensions.put("motorWidthMm", motorWidthMm);
    dimensions.put("motorHeightMm", motorHeightMm);
    dimensions.put("shaftHeightMm", shaftHeightMm);
    map.put("dimensions", dimensions);

    // Foundation
    Map<String, Object> foundation = new LinkedHashMap<String, Object>();
    foundation.put("staticLoadKN", staticFoundationLoadKN);
    foundation.put("dynamicLoadKN", dynamicFoundationLoadKN);
    foundation.put("totalLoadKN", totalFoundationLoadKN);
    foundation.put("foundationWeightRatio", foundationWeightRatio);
    foundation.put("requiredFoundationMassKg", requiredFoundationMassKg);
    foundation.put("foundationType", foundationType);
    map.put("foundation", foundation);

    // Vibration
    Map<String, Object> vibration = new LinkedHashMap<String, Object>();
    vibration.put("vibrationGrade", vibrationGrade);
    vibration.put("maxVibrationMmS", maxVibrationMmS);
    vibration.put("vibrationZone_ISO10816", vibrationZone);
    map.put("vibration", vibration);

    // Cooling
    Map<String, Object> cooling = new LinkedHashMap<String, Object>();
    cooling.put("coolingCode_IEC60034_6", coolingCode);
    cooling.put("coolingDescription", coolingDescription);
    cooling.put("heatDissipationKW", heatDissipationKW);
    cooling.put("coolingAirFlowM3s", coolingAirFlowM3s);
    map.put("cooling", cooling);

    // Bearings
    Map<String, Object> bearings = new LinkedHashMap<String, Object>();
    bearings.put("bearingType", bearingType);
    bearings.put("driveEndBearing", deBearingDesignation);
    bearings.put("nonDriveEndBearing", ndeBearingDesignation);
    bearings.put("L10LifeHours", bearingL10LifeHours);
    bearings.put("minL10Required_IEEE841_hours", minBearingLifeHours);
    bearings.put("lubricationMethod", lubricationMethod);
    if ("grease".equals(lubricationMethod)) {
      bearings.put("regreaseIntervalHours", regreaseIntervalHours);
    }
    map.put("bearings", bearings);

    // Noise
    Map<String, Object> noise = new LinkedHashMap<String, Object>();
    noise.put("soundPowerLevelDbA", soundPowerLevelDbA);
    noise.put("soundPressureAt1mDbA", soundPressureLevelAt1mDbA);
    noise.put("noiseLimitDbA_IEC60034_9", noiseLimitDbA);
    noise.put("withinIECLimit", noiseWithinLimit);
    noise.put("withinNorsokS002Limit_83dBA", noiseWithinNorsokLimit);
    map.put("noise", noise);

    // Enclosure
    Map<String, Object> enclosure = new LinkedHashMap<String, Object>();
    enclosure.put("ipRating", ipRating);
    enclosure.put("enclosureType", enclosureType);
    if (exMarking != null && !exMarking.trim().isEmpty()) {
      enclosure.put("exMarking", exMarking);
    }
    map.put("enclosure", enclosure);

    // Applied standards
    map.put("appliedStandards", appliedStandards);

    // Design notes/warnings
    if (!designNotes.isEmpty()) {
      map.put("designNotes", designNotes);
    }

    return map;
  }

  // ============================================================================
  // Getters and Setters
  // ============================================================================

  /**
   * Gets the shaft power in kW.
   *
   * @return shaft power in kW
   */
  public double getShaftPowerKW() {
    return shaftPowerKW;
  }

  /**
   * Sets the shaft power in kW.
   *
   * @param shaftPowerKW shaft power in kW
   */
  public void setShaftPowerKW(double shaftPowerKW) {
    this.shaftPowerKW = shaftPowerKW;
  }

  /**
   * Gets the rated speed.
   *
   * @return speed in RPM
   */
  public double getRatedSpeedRPM() {
    return ratedSpeedRPM;
  }

  /**
   * Sets the rated speed.
   *
   * @param ratedSpeedRPM speed in RPM
   */
  public void setRatedSpeedRPM(double ratedSpeedRPM) {
    this.ratedSpeedRPM = ratedSpeedRPM;
  }

  /**
   * Gets the number of poles.
   *
   * @return number of poles
   */
  public int getPoles() {
    return poles;
  }

  /**
   * Sets the number of poles.
   *
   * @param poles number of poles (2, 4, 6, 8)
   */
  public void setPoles(int poles) {
    this.poles = poles;
  }

  /**
   * Sets the ambient temperature in Celsius.
   *
   * @param ambientTemperatureC ambient temperature in Celsius
   */
  public void setAmbientTemperatureC(double ambientTemperatureC) {
    this.ambientTemperatureC = ambientTemperatureC;
  }

  /**
   * Gets the ambient temperature in Celsius.
   *
   * @return ambient temperature in Celsius
   */
  public double getAmbientTemperatureC() {
    return ambientTemperatureC;
  }

  /**
   * Sets the installation altitude above sea level.
   *
   * @param altitudeM altitude in meters
   */
  public void setAltitudeM(double altitudeM) {
    this.altitudeM = altitudeM;
  }

  /**
   * Gets the installation altitude.
   *
   * @return altitude in meters
   */
  public double getAltitudeM() {
    return altitudeM;
  }

  /**
   * Sets whether the motor uses a VFD.
   *
   * @param hasVFD true if VFD is used
   */
  public void setHasVFD(boolean hasVFD) {
    this.hasVFD = hasVFD;
  }

  /**
   * Checks if motor has a VFD.
   *
   * @return true if VFD is used
   */
  public boolean isHasVFD() {
    return hasVFD;
  }

  /**
   * Sets the motor standard.
   *
   * @param motorStandard "IEC" or "NEMA"
   */
  public void setMotorStandard(String motorStandard) {
    this.motorStandard = motorStandard;
  }

  /**
   * Gets the motor standard.
   *
   * @return motor standard
   */
  public String getMotorStandard() {
    return motorStandard;
  }

  /**
   * Sets the hazardous area zone.
   *
   * @param hazardousZone zone (-1=safe, 0, 1, or 2)
   */
  public void setHazardousZone(int hazardousZone) {
    this.hazardousZone = hazardousZone;
  }

  /**
   * Sets the gas group for Ex classification.
   *
   * @param gasGroup gas group (IIA, IIB, or IIC)
   */
  public void setGasGroup(String gasGroup) {
    this.gasGroup = gasGroup;
  }

  /**
   * Gets the motor weight.
   *
   * @return motor weight in kg
   */
  public double getMotorWeightKg() {
    return motorWeightKg;
  }

  /**
   * Gets the total foundation load.
   *
   * @return total foundation load in kN
   */
  public double getTotalFoundationLoadKN() {
    return totalFoundationLoadKN;
  }

  /**
   * Gets the required foundation mass.
   *
   * @return foundation mass in kg
   */
  public double getRequiredFoundationMassKg() {
    return requiredFoundationMassKg;
  }

  /**
   * Gets the foundation type recommendation.
   *
   * @return foundation type
   */
  public String getFoundationType() {
    return foundationType;
  }

  /**
   * Gets the vibration zone per ISO 10816-3.
   *
   * @return vibration zone (A, B, C, or D)
   */
  public String getVibrationZone() {
    return vibrationZone;
  }

  /**
   * Gets the maximum vibration in mm/s RMS.
   *
   * @return maximum vibration in mm/s
   */
  public double getMaxVibrationMmS() {
    return maxVibrationMmS;
  }

  /**
   * Gets the cooling code per IEC 60034-6.
   *
   * @return IC code
   */
  public String getCoolingCode() {
    return coolingCode;
  }

  /**
   * Gets the heat dissipation.
   *
   * @return heat dissipation in kW
   */
  public double getHeatDissipationKW() {
    return heatDissipationKW;
  }

  /**
   * Gets the bearing L10 life.
   *
   * @return bearing life in hours
   */
  public double getBearingL10LifeHours() {
    return bearingL10LifeHours;
  }

  /**
   * Gets the sound pressure level at 1m.
   *
   * @return SPL at 1m in dB(A)
   */
  public double getSoundPressureLevelAt1mDbA() {
    return soundPressureLevelAt1mDbA;
  }

  /**
   * Checks if noise is within NORSOK S-002 limit.
   *
   * @return true if within limit
   */
  public boolean isNoiseWithinNorsokLimit() {
    return noiseWithinNorsokLimit;
  }

  /**
   * Gets the IP protection rating.
   *
   * @return IP rating string
   */
  public String getIpRating() {
    return ipRating;
  }

  /**
   * Gets the Ex marking.
   *
   * @return Ex marking string, empty if non-hazardous
   */
  public String getExMarking() {
    return exMarking;
  }

  /**
   * Gets the combined derating factor.
   *
   * @return combined derating factor (0-1)
   */
  public double getCombinedDeratingFactor() {
    return combinedDeratingFactor;
  }

  /**
   * Gets the derated power.
   *
   * @return derated power in kW
   */
  public double getDeratedPowerKW() {
    return deratedPowerKW;
  }

  /**
   * Gets the list of applied standards.
   *
   * @return list of standards
   */
  public List<String> getAppliedStandards() {
    return appliedStandards;
  }

  /**
   * Gets the list of design notes and warnings.
   *
   * @return list of notes
   */
  public List<String> getDesignNotes() {
    return designNotes;
  }

  /**
   * Sets the frequency in Hz.
   *
   * @param frequencyHz frequency in Hz
   */
  public void setFrequencyHz(double frequencyHz) {
    this.frequencyHz = frequencyHz;
  }

  /**
   * Sets the foundation weight ratio.
   *
   * @param ratio foundation/motor weight ratio (default 3.0 per IEEE 841)
   */
  public void setFoundationWeightRatio(double ratio) {
    this.foundationWeightRatio = ratio;
  }

  /**
   * Gets the bearing type.
   *
   * @return bearing type description
   */
  public String getBearingType() {
    return bearingType;
  }

  /**
   * Gets the enclosure type.
   *
   * @return enclosure type description
   */
  public String getEnclosureType() {
    return enclosureType;
  }

  /**
   * Gets the cooling description.
   *
   * @return cooling description
   */
  public String getCoolingDescription() {
    return coolingDescription;
  }

  /**
   * Gets the sound power level in dB(A).
   *
   * @return sound power level in dB(A)
   */
  public double getSoundPowerLevelDbA() {
    return soundPowerLevelDbA;
  }
}
