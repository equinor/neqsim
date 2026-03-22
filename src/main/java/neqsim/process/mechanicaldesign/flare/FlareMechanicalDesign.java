package neqsim.process.mechanicaldesign.flare;

import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.flare.Flare;
import neqsim.process.mechanicaldesign.MechanicalDesign;

/**
 * Mechanical design for flare systems per API 521/537.
 *
 * <p>
 * Covers flare tip sizing, radiation contour estimation, stack height determination, and flare
 * header hydraulics. The design uses the Brzustowski-Sommer / API 521 single-point-source model for
 * thermal radiation calculations.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class FlareMechanicalDesign extends MechanicalDesign {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  // ============================================================================
  // Design Parameters
  // ============================================================================

  /** Tip diameter in meters. */
  private double tipDiameter = 0.0;

  /** Stack height in meters. */
  private double stackHeight = 0.0;

  /** Flare header diameter in meters. */
  private double headerDiameter = 0.0;

  /** Maximum allowable radiation at grade level in kW/m2 (API 521 Table 5). */
  private double maxRadiationAtGrade = 6.31;

  /** Maximum allowable radiation at property line in kW/m2. */
  private double maxRadiationAtPropertyLine = 1.58;

  /** Radiant fraction of total heat release. */
  private double radiantFraction = 0.20;

  /** Maximum Mach number at flare tip (API 521 recommendation). */
  private double maxTipMachNumber = 0.5;

  /** Maximum header velocity in m/s (API 521). */
  private double maxHeaderVelocity = 0.5 * 340.0; // 50% of sonic

  /** Minimum purge velocity in m/s to prevent flashback. */
  private double minPurgeVelocity = 0.12;

  /** Wind speed for radiation calculation in m/s. */
  private double designWindSpeed = 10.0;

  /** Flame tilt angle in radians (computed). */
  private double flameTiltAngle = 0.0;

  /** Flame length in meters (computed). */
  private double flameLength = 0.0;

  /** Radiation distance at max grade-level radiation (computed) in meters. */
  private double radiationDistanceAtGrade = 0.0;

  /** Design heat release rate in MW. */
  private double designHeatReleaseMW = 0.0;

  /** Maximum backpressure at any relief device in kPa gauge. */
  private double maxBackpressureKPag = 0.0;

  /** Stack material. */
  private String stackMaterial = "SS-310";

  /** Stack wall thickness in mm (computed). */
  private double stackWallThickness = 0.0;

  /** Estimated stack weight in kg. */
  private double stackWeight = 0.0;

  /** Pilot gas consumption in Sm3/hr. */
  private double pilotGasConsumption = 0.0;

  /** Smoke suppression steam rate in kg/hr (0 if smokeless). */
  private double smokeSuppressingSteamRate = 0.0;

  /**
   * Constructor for FlareMechanicalDesign.
   *
   * @param equipment the flare equipment
   */
  public FlareMechanicalDesign(ProcessEquipmentInterface equipment) {
    super(equipment);
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    super.calcDesign();

    Flare flare = (Flare) getProcessEquipment();
    if (flare.getInletStream() == null || flare.getInletStream().getThermoSystem() == null) {
      return;
    }

    double massFlowKgS = flare.getInletStream().getFlowRate("kg/sec");
    double gasDensity = flare.getInletStream().getThermoSystem().getDensity("kg/m3");
    double molecularWeight = flare.getInletStream().getThermoSystem().getMolarMass("kg/mol") * 1000;
    double temperature = flare.getInletStream().getTemperature("K");

    // Lower calorific value
    double lcvJKg = flare.getLCV();

    // Total heat release
    designHeatReleaseMW = (massFlowKgS * lcvJKg) / 1.0e6;

    // === Flare Tip Sizing (API 521 Section 5.4) ===
    // Sonic velocity approximation: c = sqrt(gamma * R * T / M)
    double gamma = 1.25; // typical HC gas
    double R = 8314.0; // J/(kmol*K)
    double sonicVelocity = Math.sqrt(gamma * R * temperature / molecularWeight);
    double maxTipVelocity = maxTipMachNumber * sonicVelocity;

    // Required tip area: A = mdot / (rho * v)
    double requiredTipArea = massFlowKgS / (gasDensity * maxTipVelocity);
    tipDiameter = Math.sqrt(4.0 * requiredTipArea / Math.PI);
    tipDiameter = Math.max(tipDiameter, 0.1); // minimum 100mm

    // === Flame Length (Brzustowski correlation) ===
    // L = 0.006 * Q^0.478 where Q = heat release in BTU/hr
    double heatReleaseBtuHr = designHeatReleaseMW * 3.412e6;
    flameLength = 0.006 * Math.pow(heatReleaseBtuHr, 0.478);
    flameLength = Math.max(flameLength, 5.0);

    // === Flame Tilt (wind effect) ===
    double exitVelocity = massFlowKgS / (gasDensity * Math.PI * tipDiameter * tipDiameter / 4.0);
    double velocityRatio = designWindSpeed / Math.max(exitVelocity, 0.01);
    flameTiltAngle = Math.atan(velocityRatio);

    // === Stack Height (API 521 radiation model) ===
    // Single point source: q = F * Q / (4 * pi * R^2)
    // R = sqrt(F * Q / (4 * pi * q_max))
    double totalHeatW = designHeatReleaseMW * 1.0e6;
    double radiantHeatW = radiantFraction * totalHeatW;

    // Minimum distance from radiation constraint at grade level
    double maxRadiationWm2 = maxRadiationAtGrade * 1000.0;
    radiationDistanceAtGrade = Math.sqrt(radiantHeatW / (4.0 * Math.PI * maxRadiationWm2));

    // Stack height: H = sqrt(R^2 - D^2) where D is horizontal offset
    // Simplified: H = R (assumes directly below)
    stackHeight = radiationDistanceAtGrade;
    stackHeight = Math.max(stackHeight, 10.0); // minimum 10m

    // === Flare Header Sizing ===
    double volumeFlowM3s = massFlowKgS / Math.max(gasDensity, 0.01);
    double requiredHeaderArea = volumeFlowM3s / maxHeaderVelocity;
    headerDiameter = Math.sqrt(4.0 * requiredHeaderArea / Math.PI);
    headerDiameter = Math.max(headerDiameter, 0.1);

    // === Stack Wall Thickness (ASME B31.3 pressure containment) ===
    double internalPressureMPa = 0.1; // flare systems are near-atmospheric
    double allowableStressMPa = 103.0; // SS-310 at high temp
    double stackRadius = tipDiameter / 2.0 + 0.05; // stack OD > tip
    stackWallThickness = (internalPressureMPa * stackRadius * 1000.0)
        / (allowableStressMPa * getJointEfficiency() - 0.6 * internalPressureMPa);
    stackWallThickness = Math.max(stackWallThickness, 6.0); // minimum 6mm

    // === Stack Weight Estimation ===
    double steelDensity = 7850.0;
    double stackOD = tipDiameter + 0.1;
    double shellArea = Math.PI * stackOD * stackHeight;
    stackWeight = shellArea * (stackWallThickness / 1000.0) * steelDensity;
    // Add 30% for supports, ladder, platform, piping
    stackWeight *= 1.30;

    // === Pilot Gas and Steam ===
    pilotGasConsumption = 2.0 * (tipDiameter / 0.3); // ~2 Sm3/hr per 300mm tip
    if (designHeatReleaseMW > 5.0) {
      // Steam for smokeless operation (Lbs steam per Lbs HC)
      smokeSuppressingSteamRate = massFlowKgS * 0.4 * 3600.0; // ~0.4 kg steam/kg HC
    }

    // === Set base class fields ===
    innerDiameter = tipDiameter;
    outerDiameter = tipDiameter + 2.0 * stackWallThickness / 1000.0;
    wallThickness = stackWallThickness;
    tantanLength = stackHeight;
    setWeightTotal(stackWeight);
  }

  /**
   * Gets the calculated tip diameter.
   *
   * @return tip diameter in meters
   */
  public double getTipDiameter() {
    return tipDiameter;
  }

  /**
   * Gets the stack height.
   *
   * @return stack height in meters
   */
  public double getStackHeight() {
    return stackHeight;
  }

  /**
   * Gets the header diameter.
   *
   * @return header diameter in meters
   */
  public double getHeaderDiameter() {
    return headerDiameter;
  }

  /**
   * Gets the flame length.
   *
   * @return flame length in meters
   */
  public double getFlameLength() {
    return flameLength;
  }

  /**
   * Gets the radiation distance at grade.
   *
   * @return radiation distance in meters
   */
  public double getRadiationDistanceAtGrade() {
    return radiationDistanceAtGrade;
  }

  /**
   * Gets the design heat release rate.
   *
   * @return heat release in MW
   */
  public double getDesignHeatReleaseMW() {
    return designHeatReleaseMW;
  }

  /**
   * Gets the stack weight.
   *
   * @return weight in kg
   */
  public double getStackWeight() {
    return stackWeight;
  }

  /**
   * Gets the pilot gas consumption.
   *
   * @return consumption in Sm3/hr
   */
  public double getPilotGasConsumption() {
    return pilotGasConsumption;
  }

  /**
   * Gets the steam rate for smoke suppression.
   *
   * @return steam rate in kg/hr
   */
  public double getSmokeSuppressingSteamRate() {
    return smokeSuppressingSteamRate;
  }

  /**
   * Sets the maximum allowable radiation at grade level.
   *
   * @param maxRadiation kW/m2 (API 521 Table 5 default 6.31)
   */
  public void setMaxRadiationAtGrade(double maxRadiation) {
    this.maxRadiationAtGrade = maxRadiation;
  }

  /**
   * Sets the radiant fraction.
   *
   * @param fraction radiant fraction (0-1)
   */
  public void setRadiantFraction(double fraction) {
    this.radiantFraction = fraction;
  }

  /**
   * Sets the maximum Mach number at flare tip.
   *
   * @param mach Mach number (default 0.5 per API 521)
   */
  public void setMaxTipMachNumber(double mach) {
    this.maxTipMachNumber = mach;
  }

  /**
   * Sets the design wind speed.
   *
   * @param windSpeed wind speed in m/s
   */
  public void setDesignWindSpeed(double windSpeed) {
    this.designWindSpeed = windSpeed;
  }

  /**
   * Sets the stack material.
   *
   * @param material material designation (e.g., "SS-310", "SS-316L")
   */
  public void setStackMaterial(String material) {
    this.stackMaterial = material;
  }

  /**
   * Gets the flame tilt angle.
   *
   * @return tilt angle in radians
   */
  public double getFlameTiltAngle() {
    return flameTiltAngle;
  }

  /**
   * Gets the stack wall thickness.
   *
   * @return wall thickness in mm
   */
  public double getStackWallThickness() {
    return stackWallThickness;
  }

  /**
   * Gets the stack material.
   *
   * @return material designation string
   */
  public String getStackMaterial() {
    return stackMaterial;
  }
}
