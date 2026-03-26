package neqsim.process.mechanicaldesign.designstandards;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Fire protection design utilities per NORSOK S-001 and API 2510 / API 521.
 *
 * <p>
 * Provides calculations for:
 * </p>
 * <ul>
 * <li>Passive fire protection (PFP) thickness for structural steel and vessels</li>
 * <li>Firewater demand estimation</li>
 * <li>Depressuring segment identification and blowdown time</li>
 * <li>Jet fire and pool fire thermal radiation</li>
 * </ul>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>NORSOK S-001: Technical Safety</li>
 * <li>API 521: Pressure-relieving and Depressuring Systems</li>
 * <li>API 2510: Design and Construction of LPG Installations</li>
 * <li>PD 7974-1: Application of fire safety engineering (pool fire model)</li>
 * </ul>
 *
 * @author esol
 * @version 1.0
 */
public class FireProtectionDesign implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Stefan-Boltzmann constant in W/(m2*K4). */
  private static final double STEFAN_BOLTZMANN = 5.67e-8;

  /** Standard heat flux for hydrocarbon pool fire per API 521, kW/m2. */
  private static final double HC_POOL_FIRE_HEAT_FLUX = 45.0;

  /** Standard heat flux for jet fire per NORSOK S-001, kW/m2. */
  private static final double JET_FIRE_HEAT_FLUX = 250.0;

  /** Temperature limit for structural steel without PFP in Celsius. */
  private static final double STEEL_CRITICAL_TEMP_C = 400.0;

  /**
   * Calculate PFP thickness required to keep steel below critical temperature.
   *
   * <p>
   * Uses one-dimensional steady-state heat conduction through an insulation layer exposed to fire
   * temperature on one side and critical steel temperature on the other.
   * </p>
   *
   * @param fireTempC fire temperature in Celsius (typically 1100 for HC fire)
   * @param steelCriticalTempC maximum allowable steel temperature in Celsius (default 400)
   * @param exposureTimeMin fire rating time in minutes (e.g. H-60, H-120)
   * @param pfpConductivity PFP thermal conductivity in W/(m*K), typical 0.12 for cementitious
   * @param sectionFactorM section factor Hp/A in 1/m for steel member
   * @return required PFP thickness in mm
   */
  public static double pfpThickness(double fireTempC, double steelCriticalTempC,
      double exposureTimeMin, double pfpConductivity, double sectionFactorM) {
    double timeSec = exposureTimeMin * 60.0;
    double steelDensity = 7850.0;
    double steelSpecificHeat = 520.0; // J/(kg*K)

    double deltaT = fireTempC - steelCriticalTempC;
    if (deltaT <= 0) {
      return 0.0;
    }

    // Energy to raise steel to critical temp: Q = rho * cp * deltaT / Hp_A
    double energyPerArea =
        steelDensity * steelSpecificHeat * (steelCriticalTempC - 20.0) / sectionFactorM;

    // Steady-state conduction: q = k * deltaT / thickness => thickness = k * deltaT * t / Q
    double thickness = pfpConductivity * deltaT * timeSec / energyPerArea;
    double thicknessMm = thickness * 1000.0;

    // Minimum practical thickness 10 mm
    return Math.max(thicknessMm, 10.0);
  }

  /**
   * Calculate PFP thickness for a vessel or pipe.
   *
   * <p>
   * Simplified calculation assuming H-120 rating and standard HC fire curve for uninsulated
   * equipment.
   * </p>
   *
   * @param vesselODMm vessel or pipe outer diameter in mm
   * @param wallThicknessMm steel wall thickness in mm
   * @param fireRatingMinutes required fire rating in minutes (60, 90, or 120)
   * @param pfpConductivity PFP thermal conductivity in W/(m*K)
   * @return required PFP thickness in mm
   */
  public static double vesselPfpThickness(double vesselODMm, double wallThicknessMm,
      double fireRatingMinutes, double pfpConductivity) {
    double sectionFactor = 1000.0 / wallThicknessMm; // approximate for thin cylindrical shell
    return pfpThickness(1100.0, STEEL_CRITICAL_TEMP_C, fireRatingMinutes, pfpConductivity,
        sectionFactor);
  }

  /**
   * Estimate firewater demand for an area.
   *
   * <p>
   * Based on NORSOK S-001 requirements for deluge and hydrant systems.
   * </p>
   *
   * @param protectedAreaM2 equipment surface area requiring water spray in m2
   * @param applicationRateLpmM2 water application rate in L/(min*m2), typical 10 for vessels
   * @param numberOfHydrants number of fire hydrants to operate simultaneously (typically 2-3)
   * @param hydrantFlowLpm flow per hydrant in L/min (typically 1200 for NORSOK)
   * @return total firewater demand in m3/hr
   */
  public static double firewaterDemand(double protectedAreaM2, double applicationRateLpmM2,
      int numberOfHydrants, double hydrantFlowLpm) {
    double delugeDemandLpm = protectedAreaM2 * applicationRateLpmM2;
    double hydrantDemandLpm = numberOfHydrants * hydrantFlowLpm;
    double totalLpm = delugeDemandLpm + hydrantDemandLpm;
    return totalLpm * 60.0 / 1000.0; // convert L/min to m3/hr
  }

  /**
   * Calculate emergency depressuring (blowdown) time estimate.
   *
   * <p>
   * Uses API 521 / NORSOK S-001 guidance for reducing pressure to below the level where stress
   * rupture would occur under fire exposure. Target is typically 6.9 barg (100 psig) within 15
   * minutes.
   * </p>
   *
   * @param inventoryKg total hydrocarbon inventory in the segment in kg
   * @param initialPressureBara initial operating pressure in bara
   * @param targetPressureBara target blowdown pressure (typically 6.9 barg + 1 = 7.9 bara)
   * @param orificeAreaMm2 blowdown orifice area in mm2
   * @param molecularWeightKgKmol gas molecular weight in kg/kmol
   * @param temperatureK gas temperature in Kelvin
   * @return estimated blowdown time in minutes
   */
  public static double blowdownTime(double inventoryKg, double initialPressureBara,
      double targetPressureBara, double orificeAreaMm2, double molecularWeightKgKmol,
      double temperatureK) {
    // Simplified ideal-gas orifice model
    double R = 8314.0; // J/(kmol*K)
    double gamma = 1.3; // typical HC gas

    double areaSqM = orificeAreaMm2 * 1.0e-6;
    double criticalRatio = Math.pow(2.0 / (gamma + 1.0), gamma / (gamma - 1.0));

    // Choked flow mass rate at initial pressure
    double pPa = initialPressureBara * 1.0e5;
    double massFlowKgS =
        areaSqM * pPa * Math.sqrt(gamma * molecularWeightKgKmol / (R * temperatureK))
            * Math.pow(criticalRatio, (gamma + 1.0) / (2.0 * (gamma - 1.0)));

    if (massFlowKgS <= 0) {
      return Double.MAX_VALUE;
    }

    // Approximate blowdown time assuming exponential pressure decay
    double pressureRatio = targetPressureBara / initialPressureBara;
    double timeS = -inventoryKg * Math.log(pressureRatio) / massFlowKgS;

    return timeS / 60.0;
  }

  /**
   * Check whether blowdown meets NORSOK S-001 15-minute requirement.
   *
   * @param inventoryKg total hydrocarbon inventory in kg
   * @param initialPressureBara initial pressure in bara
   * @param targetPressureBara target pressure in bara (default 7.9)
   * @param orificeAreaMm2 blowdown orifice area in mm2
   * @param molecularWeightKgKmol gas molecular weight in kg/kmol
   * @param temperatureK gas temperature in Kelvin
   * @return true if blowdown completes within 15 minutes
   */
  public static boolean meetsBlowdownRequirement(double inventoryKg, double initialPressureBara,
      double targetPressureBara, double orificeAreaMm2, double molecularWeightKgKmol,
      double temperatureK) {
    double time = blowdownTime(inventoryKg, initialPressureBara, targetPressureBara, orificeAreaMm2,
        molecularWeightKgKmol, temperatureK);
    return time <= 15.0;
  }

  /**
   * Calculate thermal radiation from a point-source fire model.
   *
   * <p>
   * q = F * Q_fire / (4 * pi * r^2)
   * </p>
   *
   * @param heatReleaseRateKW total fire heat release rate in kW
   * @param fractionRadiated fraction of heat radiated (0.15-0.35 typical)
   * @param distanceM distance from fire center in meters
   * @return incident thermal radiation in kW/m2
   */
  public static double pointSourceRadiation(double heatReleaseRateKW, double fractionRadiated,
      double distanceM) {
    if (distanceM <= 0) {
      return Double.MAX_VALUE;
    }
    return fractionRadiated * heatReleaseRateKW / (4.0 * Math.PI * distanceM * distanceM);
  }

  /**
   * Determine safe separation distance for a given radiation threshold.
   *
   * @param heatReleaseRateKW total fire heat release rate in kW
   * @param fractionRadiated fraction of heat radiated (typically 0.2 for gas)
   * @param thresholdKwM2 acceptable radiation level in kW/m2 (4.7 for escape, 12.5 for equipment)
   * @return minimum safe distance in meters
   */
  public static double safeDistance(double heatReleaseRateKW, double fractionRadiated,
      double thresholdKwM2) {
    if (thresholdKwM2 <= 0) {
      return Double.MAX_VALUE;
    }
    return Math.sqrt(fractionRadiated * heatReleaseRateKW / (4.0 * Math.PI * thresholdKwM2));
  }

  /**
   * Calculate pool fire heat release rate.
   *
   * <p>
   * Q = m_dot * A * deltaH_c * chi
   * </p>
   *
   * @param poolDiameterM pool diameter in meters
   * @param massBurningRateKgM2s mass burning rate in kg/(m2*s), typical 0.055 for LNG
   * @param heatOfCombustionKJKg heat of combustion in kJ/kg
   * @param combustionEfficiency combustion efficiency (typically 0.8-0.95)
   * @return heat release rate in kW
   */
  public static double poolFireHeatRelease(double poolDiameterM, double massBurningRateKgM2s,
      double heatOfCombustionKJKg, double combustionEfficiency) {
    double area = Math.PI * poolDiameterM * poolDiameterM / 4.0;
    return massBurningRateKgM2s * area * heatOfCombustionKJKg * combustionEfficiency;
  }

  /**
   * Calculate jet fire flame length using the Chamberlain model.
   *
   * <p>
   * L = 0.08 * (Q_total)^0.4, where Q is the total heat release rate in kW. The 0.08 coefficient
   * and 0.4 exponent give flame lengths consistent with Shell FRED predictions for gaseous releases
   * up to about 50 kg/s.
   * </p>
   *
   * @param massReleaseKgS release rate in kg/s
   * @param heatOfCombustionKJKg lower heating value in kJ/kg
   * @return jet fire flame length in meters
   */
  public static double jetFireFlameLength(double massReleaseKgS, double heatOfCombustionKJKg) {
    double hrrKW = massReleaseKgS * heatOfCombustionKJKg;
    if (hrrKW <= 0) {
      return 0.0;
    }
    return 0.08 * Math.pow(hrrKW, 0.4);
  }

  /**
   * Calculate BLEVE fireball diameter using CCPS correlation.
   *
   * <p>
   * D = 5.8 * M^(1/3), where M is the flammable liquid mass in kg.
   * </p>
   *
   * @param flammableMassKg total flammable liquid mass in the vessel in kg
   * @return fireball diameter in meters
   */
  public static double bleveFireballDiameter(double flammableMassKg) {
    if (flammableMassKg <= 0) {
      return 0.0;
    }
    return 5.8 * Math.pow(flammableMassKg, 1.0 / 3.0);
  }

  /**
   * Calculate BLEVE fireball duration using CCPS correlation.
   *
   * <p>
   * t = 0.45 * M^(1/3), where M is the flammable mass in kg.
   * </p>
   *
   * @param flammableMassKg total flammable liquid mass in kg
   * @return fireball duration in seconds
   */
  public static double bleveFireballDuration(double flammableMassKg) {
    if (flammableMassKg <= 0) {
      return 0.0;
    }
    return 0.45 * Math.pow(flammableMassKg, 1.0 / 3.0);
  }

  /**
   * Calculate BLEVE peak overpressure using TNT-equivalence method.
   *
   * <p>
   * Converts stored energy to TNT equivalent and looks up scaled overpressure. A simplified Hopkin
   * diagram is implemented via the Sachs-Glasstone model.
   * </p>
   *
   * @param pressureBara vessel failure pressure in bara
   * @param volumeM3 vessel volume in m3
   * @param distanceM distance from vessel in meters
   * @return estimated peak overpressure in kPa
   */
  public static double bleveOverpressure(double pressureBara, double volumeM3, double distanceM) {
    if (distanceM <= 0) {
      return Double.MAX_VALUE;
    }
    // Brode equation for burst energy: E = (P - P_atm) * V / (gamma - 1)
    double gamma = 1.4;
    double pressurePa = (pressureBara - 1.01325) * 1e5;
    double energyJ = pressurePa * volumeM3 / (gamma - 1.0);

    // TNT equivalent: 1 kg TNT = 4.68 MJ
    double tntMassKg = energyJ / 4.68e6;
    if (tntMassKg <= 0) {
      return 0.0;
    }

    // Scaled distance Z = R / W^(1/3)
    double scaledDistance = distanceM / Math.pow(tntMassKg, 1.0 / 3.0);

    // Sachs-Glasstone simplified lookup: p_peak = 1772 / Z^3 + 114 / Z^(3/2)
    double overpressurePsi =
        1772.0 / Math.pow(scaledDistance, 3.0) + 114.0 / Math.pow(scaledDistance, 1.5);
    return overpressurePsi * 6.895; // convert to kPa
  }

  /**
   * Execute a comprehensive fire scenario assessment for a single equipment item.
   *
   * <p>
   * Calculates pool fire, jet fire, and BLEVE impacts based on equipment parameters.
   * </p>
   *
   * @param equipmentName equipment tag name
   * @param inventoryKg hydrocarbon inventory in kg
   * @param operatingPressureBara operating pressure in bara
   * @param vesselVolumeM3 vessel volume in m3 (0 if pipe/compact)
   * @param poolDiameterM estimated pool diameter in meters
   * @param releaseRateKgS leak rate in kg/s for jet fire
   * @param heatingValueKJKg lower heating value in kJ/kg
   * @param massBurningRateKgM2s pool fire burning rate in kg/(m2*s), typically 0.055
   * @return fire scenario assessment result
   */
  public static FireScenarioResult assessFireScenarios(String equipmentName, double inventoryKg,
      double operatingPressureBara, double vesselVolumeM3, double poolDiameterM,
      double releaseRateKgS, double heatingValueKJKg, double massBurningRateKgM2s) {
    FireScenarioResult result = new FireScenarioResult(equipmentName);

    // Pool fire
    double poolHRR =
        poolFireHeatRelease(poolDiameterM, massBurningRateKgM2s, heatingValueKJKg, 0.85);
    double poolSafeDist = safeDistance(poolHRR, 0.2, 4.7);
    result.poolFireHeatReleaseKW = poolHRR;
    result.poolFireSafeDistanceM = poolSafeDist;

    // Jet fire
    double jetLength = jetFireFlameLength(releaseRateKgS, heatingValueKJKg);
    double jetHRR = releaseRateKgS * heatingValueKJKg;
    double jetSafeDist = safeDistance(jetHRR, 0.25, 4.7);
    result.jetFireFlameLengthM = jetLength;
    result.jetFireSafeDistanceM = jetSafeDist;

    // BLEVE (only if vessel with liquid inventory)
    if (vesselVolumeM3 > 0 && inventoryKg > 0) {
      result.bleveFireballDiameterM = bleveFireballDiameter(inventoryKg);
      result.bleveFireballDurationS = bleveFireballDuration(inventoryKg);
      result.bleveOverpressureAt50mKPa =
          bleveOverpressure(operatingPressureBara, vesselVolumeM3, 50.0);
    }

    return result;
  }

  /**
   * Holds results from a comprehensive fire scenario assessment.
   *
   * @author esol
   * @version 1.0
   */
  public static class FireScenarioResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    /** Equipment name. */
    public String equipmentName;

    /** Pool fire heat release rate in kW. */
    public double poolFireHeatReleaseKW;
    /** Pool fire safe distance to 4.7 kW/m2 escape threshold in meters. */
    public double poolFireSafeDistanceM;

    /** Jet fire flame length in meters. */
    public double jetFireFlameLengthM;
    /** Jet fire safe distance to 4.7 kW/m2 threshold in meters. */
    public double jetFireSafeDistanceM;

    /** BLEVE fireball diameter in meters. */
    public double bleveFireballDiameterM;
    /** BLEVE fireball duration in seconds. */
    public double bleveFireballDurationS;
    /** BLEVE overpressure at 50 m distance in kPa. */
    public double bleveOverpressureAt50mKPa;

    /**
     * Creates a fire scenario result for the named equipment.
     *
     * @param equipmentName equipment tag name
     */
    public FireScenarioResult(String equipmentName) {
      this.equipmentName = equipmentName;
    }

    /**
     * Exports the fire scenario result to JSON.
     *
     * @return JSON string
     */
    public String toJson() {
      JsonObject obj = new JsonObject();
      obj.addProperty("equipment", equipmentName);

      JsonObject pool = new JsonObject();
      pool.addProperty("heatReleaseKW", poolFireHeatReleaseKW);
      pool.addProperty("safeDistanceM", poolFireSafeDistanceM);
      obj.add("poolFire", pool);

      JsonObject jet = new JsonObject();
      jet.addProperty("flameLengthM", jetFireFlameLengthM);
      jet.addProperty("safeDistanceM", jetFireSafeDistanceM);
      obj.add("jetFire", jet);

      JsonObject bleve = new JsonObject();
      bleve.addProperty("fireballDiameterM", bleveFireballDiameterM);
      bleve.addProperty("fireballDurationS", bleveFireballDurationS);
      bleve.addProperty("overpressureAt50mKPa", bleveOverpressureAt50mKPa);
      obj.add("bleve", bleve);

      return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
          .toJson(obj);
    }
  }

  /**
   * Run fire scenario assessment for a list of equipment and return combined JSON report.
   *
   * @param scenarios list of fire scenario results
   * @return JSON array string with all scenario results
   */
  public static String fireScenarioReport(List<FireScenarioResult> scenarios) {
    JsonArray arr = new JsonArray();
    for (FireScenarioResult s : scenarios) {
      arr.add(com.google.gson.JsonParser.parseString(s.toJson()));
    }
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(arr);
  }
}
