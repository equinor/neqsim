package neqsim.process.mechanicaldesign.subsea;

/**
 * Functional screening checks for a subsea umbilical: hydraulic response, chemical delivery, electrical drop, optical
 * budget, weight ratio and J-tube pull-in.
 *
 * <p>
 * All methods are pure static functions with the units given in the method name or JavaDoc. They complement
 * {@link UmbilicalMechanicalDesign} (cross-section and armour design) with the functional checks that are normally
 * documented in an umbilical design basis. Results are screening estimates and do not replace detailed design.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class UmbilicalFunctionalDesign {

  /** Gravitational acceleration in m/s2. */
  private static final double GRAVITY = 9.81;

  /**
   * Private constructor to prevent instantiation of this utility class.
   */
  private UmbilicalFunctionalDesign() {
  }

  /**
   * Calculates the internal volume of a tube.
   *
   * @param innerDiameterMm inner diameter in mm
   * @param lengthM tube length in m
   * @return tube volume in m3
   */
  public static double tubeVolumeM3(double innerDiameterMm, double lengthM) {
    double d = innerDiameterMm / 1000.0;
    return Math.PI / 4.0 * d * d * lengthM;
  }

  /**
   * Calculates the transit time of a fluid through a tube at a given volumetric rate.
   *
   * @param volumeM3 volume in m3
   * @param rateM3PerDay volumetric rate in m3/day
   * @return transit time in days
   * @throws IllegalArgumentException if the rate is not positive
   */
  public static double transitTimeDays(double volumeM3, double rateM3PerDay) {
    if (rateM3PerDay <= 0.0) {
      throw new IllegalArgumentException("rateM3PerDay must be positive");
    }
    return volumeM3 / rateM3PerDay;
  }

  /**
   * Calculates the Darcy friction factor. Laminar flow (Re below 2300) uses 64/Re; otherwise the explicit Churchill
   * (1977) correlation, which is valid for all regimes, is used.
   *
   * @param reynolds Reynolds number (dimensionless)
   * @param relativeRoughness relative roughness, roughness divided by inner diameter (dimensionless)
   * @return Darcy friction factor (dimensionless)
   * @throws IllegalArgumentException if the Reynolds number is not positive
   */
  public static double frictionFactor(double reynolds, double relativeRoughness) {
    if (reynolds <= 0.0) {
      throw new IllegalArgumentException("reynolds must be positive");
    }
    if (reynolds < 2300.0) {
      return 64.0 / reynolds;
    }
    double a = Math.pow(2.457 * Math.log(1.0 / (Math.pow(7.0 / reynolds, 0.9) + 0.27 * relativeRoughness)), 16.0);
    double b = Math.pow(37530.0 / reynolds, 16.0);
    return 8.0 * Math.pow(Math.pow(8.0 / reynolds, 12.0) + 1.0 / Math.pow(a + b, 1.5), 1.0 / 12.0);
  }

  /**
   * Calculates the frictional pressure drop in a tube with the Darcy-Weisbach equation. No elevation term is included.
   *
   * @param flowM3PerHour volumetric flow in m3/h
   * @param innerDiameterMm inner diameter in mm
   * @param lengthM tube length in m
   * @param densityKgM3 fluid density in kg/m3
   * @param viscosityPas dynamic viscosity in Pa s
   * @param roughnessMm absolute wall roughness in mm
   * @return frictional pressure drop in bar
   */
  public static double pressureDropBar(double flowM3PerHour, double innerDiameterMm, double lengthM, double densityKgM3,
      double viscosityPas, double roughnessMm) {
    double d = innerDiameterMm / 1000.0;
    double q = Math.abs(flowM3PerHour) / 3600.0;
    if (q == 0.0) {
      return 0.0;
    }
    double velocity = q / (Math.PI / 4.0 * d * d);
    double reynolds = densityKgM3 * velocity * d / viscosityPas;
    double f = frictionFactor(reynolds, roughnessMm / innerDiameterMm);
    double dp = f * lengthM / d * densityKgM3 * velocity * velocity / 2.0;
    return dp / 1.0e5;
  }

  /**
   * Calculates the laminar hydraulic resistance of a tube (Hagen-Poiseuille).
   *
   * @param innerDiameterMm inner diameter in mm
   * @param lengthM tube length in m
   * @param viscosityPas dynamic viscosity in Pa s
   * @return hydraulic resistance in Pa s/m3
   */
  private static double laminarResistance(double innerDiameterMm, double lengthM, double viscosityPas) {
    double d = innerDiameterMm / 1000.0;
    return 128.0 * viscosityPas * lengthM / (Math.PI * Math.pow(d, 4.0));
  }

  /**
   * Calculates the hydraulic time constant of a distributed laminar line, tau = R C / pi^2 with R the Hagen-Poiseuille
   * resistance and C = V/K the fluid compliance.
   *
   * @param innerDiameterMm inner diameter in mm
   * @param lengthM tube length in m
   * @param viscosityPas dynamic viscosity in Pa s
   * @param bulkModulusPa fluid bulk modulus in Pa
   * @return time constant in seconds
   */
  public static double hydraulicTimeConstantSeconds(double innerDiameterMm, double lengthM, double viscosityPas,
      double bulkModulusPa) {
    double r = laminarResistance(innerDiameterMm, lengthM, viscosityPas);
    double c = tubeVolumeM3(innerDiameterMm, lengthM) / bulkModulusPa;
    return r * c / (Math.PI * Math.PI);
  }

  /**
   * Screening estimate of the response time of a hydraulic valve actuator at the far end of a line: t = V_act/Q + tau,
   * where Q = (supply - required)/R is the laminar flow driven by the excess pressure.
   *
   * @param innerDiameterMm inner diameter in mm
   * @param lengthM tube length in m
   * @param viscosityPas dynamic viscosity in Pa s
   * @param bulkModulusPa fluid bulk modulus in Pa
   * @param actuatorVolumeLitres actuator volume to be displaced in litres
   * @param supplyBar supply pressure in bar
   * @param requiredBar pressure required to actuate in bar
   * @return actuation time in seconds
   * @throws IllegalArgumentException if the supply pressure does not exceed the required pressure
   */
  public static double actuationTimeSeconds(double innerDiameterMm, double lengthM, double viscosityPas,
      double bulkModulusPa, double actuatorVolumeLitres, double supplyBar, double requiredBar) {
    if (supplyBar <= requiredBar) {
      throw new IllegalArgumentException("supplyBar must exceed requiredBar");
    }
    double r = laminarResistance(innerDiameterMm, lengthM, viscosityPas);
    double q = (supplyBar - requiredBar) * 1.0e5 / r;
    double fillTime = actuatorVolumeLitres / 1000.0 / q;
    return fillTime + hydraulicTimeConstantSeconds(innerDiameterMm, lengthM, viscosityPas, bulkModulusPa);
  }

  /**
   * Screening estimate of a single-phase AC electrical line. Only the resistive drop is included; reactance, skin and
   * proximity effects are neglected. The load current is a first estimate P/V at the supply voltage.
   *
   * @param loopResistanceOhmPerKm loop (go and return) resistance in ohm/km
   * @param capacitanceNfPerKm capacitance in nF/km
   * @param lengthKm line length in km
   * @param loadPowerW load power in W
   * @param supplyVoltageV supply voltage in V
   * @param frequencyHz supply frequency in Hz
   * @return array {loadCurrentA, chargingCurrentA, totalCurrentA, voltageDropV, receivedVoltageV}
   */
  public static double[] electricalLine(double loopResistanceOhmPerKm, double capacitanceNfPerKm, double lengthKm,
      double loadPowerW, double supplyVoltageV, double frequencyHz) {
    double loadCurrent = loadPowerW / supplyVoltageV;
    double capacitanceF = capacitanceNfPerKm * 1.0e-9 * lengthKm;
    double chargingCurrent = 2.0 * Math.PI * frequencyHz * capacitanceF * supplyVoltageV;
    double totalCurrent = Math.sqrt(loadCurrent * loadCurrent + chargingCurrent * chargingCurrent);
    double voltageDrop = totalCurrent * loopResistanceOhmPerKm * lengthKm;
    return new double[] {loadCurrent, chargingCurrent, totalCurrent, voltageDrop, supplyVoltageV - voltageDrop};
  }

  /**
   * Calculates the optical loss budget of a fibre link.
   *
   * @param lengthKm fibre length in km
   * @param attenuationDbPerKm fibre attenuation in dB/km
   * @param fibreSplices number of fibre splices
   * @param spliceLossDb loss per splice in dB
   * @param connectorPairs number of connector pairs
   * @param connectorLossDb loss per connector pair in dB
   * @param agingMarginDb ageing and repair margin in dB
   * @return total loss budget in dB
   */
  public static double opticalBudgetDb(double lengthKm, double attenuationDbPerKm, int fibreSplices,
      double spliceLossDb, int connectorPairs, double connectorLossDb, double agingMarginDb) {
    return lengthKm * attenuationDbPerKm + fibreSplices * spliceLossDb + connectorPairs * connectorLossDb
        + agingMarginDb;
  }

  /**
   * Calculates the weight ratio of a fluid-filled umbilical: weight in air divided by the weight of displaced seawater.
   * TR1232 SR-45783 requires a ratio of at least 1.5.
   *
   * @param dryFilledWeightNPerM fluid-filled weight in air in N/m
   * @param outerDiameterMm outer diameter in mm
   * @param seawaterDensityKgM3 seawater density in kg/m3
   * @return weight ratio (dimensionless)
   */
  public static double weightRatio(double dryFilledWeightNPerM, double outerDiameterMm, double seawaterDensityKgM3) {
    double d = outerDiameterMm / 1000.0;
    double displacedNPerM = seawaterDensityKgM3 * GRAVITY * Math.PI / 4.0 * d * d;
    return dryFilledWeightNPerM / displacedNPerM;
  }

  /**
   * Calculates the pull-in tension at the termination head for a J-tube installation, stage by stage: seabed drag,
   * straight inlet section rising at an angle, bend to vertical (capstan effect) and vertical section, then head weight
   * and contingency.
   *
   * @param backTensionKN back tension at the start of the seabed section in kN
   * @param seabedLengthM seabed drag length in m
   * @param seabedFriction seabed friction coefficient
   * @param submergedWeightNPerM submerged weight in N/m
   * @param inletLengthM straight inlet length in m
   * @param inletAngleDeg inlet inclination from horizontal in degrees
   * @param bendRadiusM J-tube bend radius in m
   * @param jTubeFriction friction coefficient in the J-tube
   * @param verticalLengthM vertical J-tube length in m
   * @param headSubmergedWeightKN submerged weight of the pull-in head in kN
   * @param contingencyFactor multiplicative contingency factor
   * @return tension at the termination head in kN
   */
  public static double jTubePullInTensionKN(double backTensionKN, double seabedLengthM, double seabedFriction,
      double submergedWeightNPerM, double inletLengthM, double inletAngleDeg, double bendRadiusM, double jTubeFriction,
      double verticalLengthM, double headSubmergedWeightKN, double contingencyFactor) {
    double w = submergedWeightNPerM / 1000.0;
    double a = Math.toRadians(inletAngleDeg);
    double t1 = backTensionKN + seabedFriction * w * seabedLengthM;
    double t2 = t1 + w * inletLengthM * (Math.sin(a) + jTubeFriction * Math.cos(a));
    double theta = Math.PI / 2.0 - a;
    double t3 = t2 * Math.exp(jTubeFriction * theta) + w * bendRadiusM * Math.cos(a);
    double t4 = t3 + w * verticalLengthM;
    return (t4 + headSubmergedWeightKN) * contingencyFactor;
  }
}
