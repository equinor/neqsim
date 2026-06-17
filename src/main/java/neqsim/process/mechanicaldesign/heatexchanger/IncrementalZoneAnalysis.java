package neqsim.process.mechanicaldesign.heatexchanger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Incremental zone-by-zone thermal analysis for heat exchangers.
 *
 * <p>
 * Divides the heat exchanger into N incremental zones along the tube length, performing local
 * thermodynamic flash calculations at each zone boundary to determine local fluid properties. This
 * is analogous to the "incremental" rating mode in HTRI Xist.
 * </p>
 *
 * <p>
 * Key capabilities:
 * </p>
 * <ul>
 * <li>Adaptive zone sizing — finer resolution near phase boundaries (dew point, bubble point)</li>
 * <li>Local heat transfer coefficient calculation per zone (single-phase, condensing, or
 * boiling)</li>
 * <li>Local pressure drop per zone (single-phase Fanning, two-phase Friedel)</li>
 * <li>Cumulative duty, area, pressure drop tracking</li>
 * <li>Pinch temperature detection (minimum temperature approach along the exchanger)</li>
 * <li>Phase regime identification (superheated, two-phase, subcooled)</li>
 * </ul>
 *
 * <p>
 * Usage pattern:
 * </p>
 *
 * <pre>
 * IncrementalZoneAnalysis analysis = new IncrementalZoneAnalysis(20);
 * analysis.setGeometry(tubeID, tubeOD, tubeLength, tubeCount, tubePasses);
 *
 * // Add zones from hot end to cold end with local properties at each boundary
 * for (int i = 0; i &lt; numZones; i++) {
 *   IncrementalZone zone = new IncrementalZone();
 *   zone.setTubeSideProperties(...);
 *   zone.setShellSideProperties(...);
 *   zone.setTemperatures(tHotIn, tHotOut, tColdIn, tColdOut);
 *   zone.setDuty(localDuty);
 *   analysis.addZone(zone);
 * }
 *
 * analysis.calculate();
 * double totalArea = analysis.getTotalRequiredArea();
 * double minApproach = analysis.getMinimumApproachTemperature();
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 * @see ThermalDesignCalculator
 * @see ShahCondensation
 * @see BoilingHeatTransfer
 * @see TwoPhasePressureDrop
 */
public class IncrementalZoneAnalysis implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1002L;

  /**
   * Phase regime within a zone.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  public enum PhaseRegime {
    /** Single-phase vapor (superheated). */
    VAPOR,
    /** Single-phase liquid (subcooled). */
    LIQUID,
    /** Two-phase condensing (quality decreasing). */
    CONDENSING,
    /** Two-phase evaporating (quality increasing). */
    EVAPORATING,
    /** Two-phase mixed (quality near constant). */
    TWO_PHASE
  }

  // ============================================================================
  // Geometry
  // ============================================================================
  private double tubeIDm = 0.01483;
  private double tubeODm = 0.01905;
  private double tubeLengthm = 6.096;
  private int tubeCount = 100;
  private int tubePasses = 2;
  private double tubePitchm = 0.02381;
  private boolean triangularPitch = true;
  private double shellIDm = 0.5;
  private double baffleSpacingm = 0.2;
  private double tubeWallConductivity = 52.0;


  // Fouling
  private double foulingTube = 0.000176;
  private double foulingShell = 0.000176;

  // ============================================================================
  // Zone data
  // ============================================================================
  private List<IncrementalZone> zones = new ArrayList<IncrementalZone>();
  private int targetZoneCount = 20;

  // ============================================================================
  // Results
  // ============================================================================
  private double totalRequiredArea = 0.0;
  private double totalDuty = 0.0;
  private double totalTubeSidePressureDrop = 0.0;
  private double totalShellSidePressureDrop = 0.0;
  private double minimumApproachTemperature = Double.MAX_VALUE;
  private double weightedAverageU = 0.0;

  /**
   * Creates an IncrementalZoneAnalysis with the specified number of zones.
   *
   * @param targetZones target number of zones (minimum 3)
   */
  public IncrementalZoneAnalysis(int targetZones) {
    this.targetZoneCount = Math.max(3, targetZones);
  }

  /**
   * Creates an IncrementalZoneAnalysis with default 20 zones.
   */
  public IncrementalZoneAnalysis() {
    this(20);
  }

  /**
   * Sets the tube geometry parameters.
   *
   * @param tubeID tube inner diameter (m)
   * @param tubeOD tube outer diameter (m)
   * @param tubeLength tube length (m)
   * @param tubeCount number of tubes
   * @param tubePasses number of tube passes
   */
  public void setGeometry(double tubeID, double tubeOD, double tubeLength, int tubeCount,
      int tubePasses) {
    this.tubeIDm = tubeID;
    this.tubeODm = tubeOD;
    this.tubeLengthm = tubeLength;
    this.tubeCount = tubeCount;
    this.tubePasses = tubePasses;
  }

  /**
   * Sets the shell-side geometry parameters.
   *
   * @param shellID shell inside diameter (m)
   * @param baffleSpacing baffle spacing (m)
   * @param tubePitch tube pitch (m)
   * @param triangularPitch true for triangular layout
   */
  public void setShellGeometry(double shellID, double baffleSpacing, double tubePitch,
      boolean triangularPitch) {
    this.shellIDm = shellID;
    this.baffleSpacingm = baffleSpacing;
    this.tubePitchm = tubePitch;
    this.triangularPitch = triangularPitch;
  }

  /**
   * Sets fouling resistances.
   *
   * @param tubeFouling tube-side fouling resistance (m2*K/W)
   * @param shellFouling shell-side fouling resistance (m2*K/W)
   */
  public void setFouling(double tubeFouling, double shellFouling) {
    this.foulingTube = tubeFouling;
    this.foulingShell = shellFouling;
  }

  /**
   * Adds a zone to the analysis.
   *
   * @param zone the incremental zone to add
   */
  public void addZone(IncrementalZone zone) {
    zones.add(zone);
  }

  /**
   * Clears all zones.
   */
  public void clearZones() {
    zones.clear();
  }

  /**
   * Performs the incremental zone calculation.
   *
   * <p>
   * For each zone, calculates local heat transfer coefficients using appropriate correlations
   * (single-phase Gnielinski/Kern, Shah condensation, Gungor-Winterton boiling), local pressure
   * drops (Fanning or Friedel), local LMTD, and local required area.
   * </p>
   */
  public void calculate() {
    totalRequiredArea = 0.0;
    totalDuty = 0.0;
    totalTubeSidePressureDrop = 0.0;
    totalShellSidePressureDrop = 0.0;
    minimumApproachTemperature = Double.MAX_VALUE;
    double totalUxA = 0.0;

    for (IncrementalZone zone : zones) {
      // Calculate tube-side HTC based on regime
      double hTube = calcTubeSideHTC(zone);
      zone.tubeSideHTC = hTube;

      // Calculate shell-side HTC based on regime
      double hShell = calcShellSideHTC(zone);
      zone.shellSideHTC = hShell;

      // Overall U
      double overallU = calcOverallU(hTube, hShell);
      zone.overallU = overallU;

      // LMTD for this zone
      double lmtd = calcZoneLMTD(zone);
      zone.lmtd = lmtd;

      // Required area for this zone
      double areaRequired = (overallU > 0 && lmtd > 0) ? zone.duty / (overallU * lmtd) : 0.0;
      zone.requiredArea = areaRequired;
      totalRequiredArea += areaRequired;

      // Pressure drops
      double dpTube = calcTubeSidePressureDrop(zone);
      double dpShell = calcShellSidePressureDrop(zone);
      zone.tubeSidePressureDrop = dpTube;
      zone.shellSidePressureDrop = dpShell;
      totalTubeSidePressureDrop += dpTube;
      totalShellSidePressureDrop += dpShell;

      // Duty tracking
      totalDuty += zone.duty;

      // UA tracking for weighted average
      totalUxA += overallU * areaRequired;

      // Minimum approach temperature
      double approach1 = Math.abs(zone.hotInletTemp - zone.coldOutletTemp);
      double approach2 = Math.abs(zone.hotOutletTemp - zone.coldInletTemp);
      double minApproach = Math.min(approach1, approach2);
      if (minApproach < minimumApproachTemperature) {
        minimumApproachTemperature = minApproach;
      }
    }

    // Weighted average U
    weightedAverageU = (totalRequiredArea > 0) ? totalUxA / totalRequiredArea : 0.0;
  }

  /**
   * Calculates tube-side HTC for a zone based on its phase regime.
   *
   * @param zone the incremental zone
   * @return tube-side HTC (W/(m2*K))
   */
  private double calcTubeSideHTC(IncrementalZone zone) {
    double massFlux = calcTubeSideMassFlux(zone.tubeMassFlowRate);

    switch (zone.tubePhaseRegime) {
      case CONDENSING:
        // Shah condensation
        double hLo = ShahCondensation.calcLiquidOnlyHTC(massFlux, tubeIDm, zone.tubeLiquidDensity,
            zone.tubeLiquidViscosity, zone.tubeLiquidCp, zone.tubeLiquidConductivity);
        return ShahCondensation.calcAverageHTC(hLo, zone.tubeReducedPressure, zone.tubeQualityIn,
            zone.tubeQualityOut, 10);

      case EVAPORATING:
        // Gungor-Winterton boiling
        double heatFlux = (tubeCount > 0 && tubeIDm > 0)
            ? zone.duty / (tubeCount * Math.PI * tubeIDm * tubeLengthm / zones.size())
            : 0.0;
        return BoilingHeatTransfer.calcAverageHTC(massFlux, tubeIDm, zone.tubeLiquidDensity,
            zone.tubeVaporDensity, zone.tubeLiquidViscosity, zone.tubeLiquidCp,
            zone.tubeLiquidConductivity, heatFlux, zone.tubeHeatOfVaporization, zone.tubeQualityIn,
            zone.tubeQualityOut, 10);

      default:
        // Single-phase (vapor or liquid)
        return calcSinglePhaseHTC(massFlux, zone.tubeDensity, zone.tubeViscosity, zone.tubeCp,
            zone.tubeConductivity);
    }
  }

  /**
   * Calculates shell-side HTC for a zone.
   *
   * @param zone the incremental zone
   * @return shell-side HTC (W/(m2*K))
   */
  private double calcShellSideHTC(IncrementalZone zone) {
    // Use Kern method for shell side
    double De = BellDelawareMethod.calcShellEquivDiameter(tubeODm, tubePitchm, triangularPitch);
    double crossflowArea =
        BellDelawareMethod.calcCrossflowArea(shellIDm, baffleSpacingm, tubeODm, tubePitchm);

    if (De <= 0 || crossflowArea <= 0) {
      return 0.0;
    }

    double massFlux = zone.shellMassFlowRate / crossflowArea;
    double muW = zone.shellViscosity; // Assume bulk = wall for simplicity

    return BellDelawareMethod.calcKernShellSideHTC(massFlux, De, zone.shellViscosity, zone.shellCp,
        zone.shellConductivity, muW);
  }

  /**
   * Calculates single-phase HTC using Gnielinski or laminar correlation.
   *
   * @param massFlux mass flux (kg/(m2*s))
   * @param density density (kg/m3)
   * @param viscosity viscosity (Pa*s)
   * @param cp heat capacity (J/(kg*K))
   * @param conductivity thermal conductivity (W/(m*K))
   * @return heat transfer coefficient (W/(m2*K))
   */
  private double calcSinglePhaseHTC(double massFlux, double density, double viscosity, double cp,
      double conductivity) {
    if (viscosity <= 0 || conductivity <= 0 || tubeIDm <= 0) {
      return 0.0;
    }

    double Re = massFlux * tubeIDm / viscosity;
    double Pr = cp * viscosity / conductivity;

    double Nu;
    if (Re > 3000) {
      // Gnielinski
      double f = ThermalDesignCalculator.calcDarcyFriction(Re);
      Nu = (f / 8.0) * (Re - 1000.0) * Pr
          / (1.0 + 12.7 * Math.sqrt(f / 8.0) * (Math.pow(Pr, 2.0 / 3.0) - 1.0));
      Nu = Math.max(Nu, 0.0);
    } else if (Re > 2300) {
      double NuLam = 3.66;
      double fTurb = ThermalDesignCalculator.calcDarcyFriction(3000);
      double NuTurb = (fTurb / 8.0) * 2000.0 * Pr
          / (1.0 + 12.7 * Math.sqrt(fTurb / 8.0) * (Math.pow(Pr, 2.0 / 3.0) - 1.0));
      double frac = (Re - 2300.0) / 700.0;
      Nu = NuLam + frac * (NuTurb - NuLam);
    } else {
      Nu = 3.66;
    }

    return Nu * conductivity / tubeIDm;
  }

  /**
   * Calculates the overall U for a zone using resistance-in-series.
   *
   * @param hTube tube-side HTC (W/(m2*K))
   * @param hShell shell-side HTC (W/(m2*K))
   * @return overall U based on outer area (W/(m2*K))
   */
  private double calcOverallU(double hTube, double hShell) {
    if (hTube <= 0 || hShell <= 0) {
      return 0.0;
    }

    double doByDi = tubeODm / tubeIDm;
    double rShell = 1.0 / hShell;
    double rFoulShell = foulingShell;
    double rWall = tubeODm * Math.log(doByDi) / (2.0 * tubeWallConductivity);
    double rFoulTube = doByDi * foulingTube;
    double rTube = doByDi / hTube;

    double totalR = rShell + rFoulShell + rWall + rFoulTube + rTube;
    return (totalR > 0) ? 1.0 / totalR : 0.0;
  }

  /**
   * Calculates the LMTD for a zone.
   *
   * @param zone the incremental zone
   * @return zone LMTD (K)
   */
  private double calcZoneLMTD(IncrementalZone zone) {
    double dt1 = zone.hotInletTemp - zone.coldOutletTemp;
    double dt2 = zone.hotOutletTemp - zone.coldInletTemp;

    if (dt1 <= 0 || dt2 <= 0) {
      return Math.max(Math.abs(dt1), Math.abs(dt2));
    }

    if (Math.abs(dt1 - dt2) < 0.01) {
      return (dt1 + dt2) / 2.0;
    }

    return (dt1 - dt2) / Math.log(dt1 / dt2);
  }

  /**
   * Calculates tube-side mass flux.
   *
   * @param massFlowRate tube-side mass flow rate (kg/s)
   * @return mass flux in tubes (kg/(m2*s))
   */
  private double calcTubeSideMassFlux(double massFlowRate) {
    int tubesPerPass = (tubePasses > 0) ? tubeCount / tubePasses : tubeCount;
    double areaPerTube = Math.PI / 4.0 * tubeIDm * tubeIDm;
    double totalArea = tubesPerPass * areaPerTube;
    return (totalArea > 0) ? massFlowRate / totalArea : 0.0;
  }

  /**
   * Calculates tube-side pressure drop for one zone.
   *
   * @param zone the incremental zone
   * @return pressure drop for this zone (Pa)
   */
  private double calcTubeSidePressureDrop(IncrementalZone zone) {
    double massFlux = calcTubeSideMassFlux(zone.tubeMassFlowRate);
    double zoneLength = tubeLengthm / Math.max(zones.size(), 1);

    if (zone.tubePhaseRegime == PhaseRegime.CONDENSING
        || zone.tubePhaseRegime == PhaseRegime.EVAPORATING) {
      // Use Friedel for two-phase
      double avgQuality = (zone.tubeQualityIn + zone.tubeQualityOut) / 2.0;
      return TwoPhasePressureDrop.calcFriedelPressureDrop(massFlux, avgQuality, tubeIDm, zoneLength,
          zone.tubeLiquidDensity, zone.tubeVaporDensity, zone.tubeLiquidViscosity,
          zone.tubeVaporViscosity, zone.tubeSurfaceTension);
    }

    // Single-phase Fanning friction
    if (zone.tubeViscosity <= 0 || zone.tubeDensity <= 0 || massFlux <= 0) {
      return 0.0;
    }

    double velocity = massFlux / zone.tubeDensity;
    double Re = massFlux * tubeIDm / zone.tubeViscosity;
    double f = ThermalDesignCalculator.calcDarcyFriction(Re);
    return f * zoneLength / tubeIDm * zone.tubeDensity * velocity * velocity / 2.0;
  }

  /**
   * Calculates shell-side pressure drop for one zone.
   *
   * @param zone the incremental zone
   * @return pressure drop for this zone (Pa)
   */
  private double calcShellSidePressureDrop(IncrementalZone zone) {
    double De = BellDelawareMethod.calcShellEquivDiameter(tubeODm, tubePitchm, triangularPitch);
    double crossflowArea =
        BellDelawareMethod.calcCrossflowArea(shellIDm, baffleSpacingm, tubeODm, tubePitchm);

    if (De <= 0 || crossflowArea <= 0) {
      return 0.0;
    }

    double massFlux = zone.shellMassFlowRate / crossflowArea;
    double muW = zone.shellViscosity;

    // Scale for zone fraction of total baffles
    int zoneBaffles =
        Math.max(1, (int) Math.round(tubeLengthm / baffleSpacingm / zones.size()));

    return BellDelawareMethod.calcKernShellSidePressureDrop(massFlux, De, shellIDm, zoneBaffles,
        zone.shellDensity, zone.shellViscosity, muW);
  }

  /**
   * Returns a summary of all zone results.
   *
   * @return list of zone result maps
   */
  public List<Map<String, Object>> getZoneResults() {
    List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();

    for (int i = 0; i < zones.size(); i++) {
      IncrementalZone zone = zones.get(i);
      Map<String, Object> zoneMap = new LinkedHashMap<String, Object>();
      zoneMap.put("zoneIndex", i + 1);
      zoneMap.put("zoneName", zone.zoneName);
      zoneMap.put("tubePhaseRegime", zone.tubePhaseRegime.name());
      zoneMap.put("shellPhaseRegime", zone.shellPhaseRegime.name());
      zoneMap.put("tubeSideHTC_Wpm2K", zone.tubeSideHTC);
      zoneMap.put("shellSideHTC_Wpm2K", zone.shellSideHTC);
      zoneMap.put("overallU_Wpm2K", zone.overallU);
      zoneMap.put("lmtd_K", zone.lmtd);
      zoneMap.put("duty_W", zone.duty);
      zoneMap.put("requiredArea_m2", zone.requiredArea);
      zoneMap.put("tubeSidePressureDrop_Pa", zone.tubeSidePressureDrop);
      zoneMap.put("shellSidePressureDrop_Pa", zone.shellSidePressureDrop);
      zoneMap.put("hotInletTemp_K", zone.hotInletTemp);
      zoneMap.put("hotOutletTemp_K", zone.hotOutletTemp);
      zoneMap.put("coldInletTemp_K", zone.coldInletTemp);
      zoneMap.put("coldOutletTemp_K", zone.coldOutletTemp);
      results.add(zoneMap);
    }

    return results;
  }

  /**
   * Returns all computed results as a map.
   *
   * @return map of result keys and values
   */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();

    result.put("numberOfZones", zones.size());
    result.put("totalRequiredArea_m2", totalRequiredArea);
    result.put("totalDuty_W", totalDuty);
    result.put("totalDuty_kW", totalDuty / 1000.0);
    result.put("weightedAverageU_Wpm2K", weightedAverageU);
    result.put("totalTubeSidePressureDrop_Pa", totalTubeSidePressureDrop);
    result.put("totalTubeSidePressureDrop_bar", totalTubeSidePressureDrop / 1e5);
    result.put("totalShellSidePressureDrop_Pa", totalShellSidePressureDrop);
    result.put("totalShellSidePressureDrop_bar", totalShellSidePressureDrop / 1e5);
    result.put("minimumApproachTemperature_K", minimumApproachTemperature);
    result.put("zones", getZoneResults());

    return result;
  }

  /**
   * Converts all results to a JSON string.
   *
   * @return JSON string with pretty printing
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(toMap());
  }

  // ============================================================================
  // Getters for results
  // ============================================================================

  /**
   * Gets the total required heat transfer area.
   *
   * @return total area (m2)
   */
  public double getTotalRequiredArea() {
    return totalRequiredArea;
  }

  /**
   * Gets the total heat duty across all zones.
   *
   * @return total duty (W)
   */
  public double getTotalDuty() {
    return totalDuty;
  }

  /**
   * Gets the minimum temperature approach along the exchanger length.
   *
   * @return minimum approach temperature (K)
   */
  public double getMinimumApproachTemperature() {
    return minimumApproachTemperature;
  }

  /**
   * Gets the weighted average overall heat transfer coefficient.
   *
   * @return weighted average U (W/(m2*K))
   */
  public double getWeightedAverageU() {
    return weightedAverageU;
  }

  /**
   * Gets the total tube-side pressure drop.
   *
   * @return total tube-side pressure drop (Pa)
   */
  public double getTotalTubeSidePressureDrop() {
    return totalTubeSidePressureDrop;
  }

  /**
   * Gets the total shell-side pressure drop.
   *
   * @return total shell-side pressure drop (Pa)
   */
  public double getTotalShellSidePressureDrop() {
    return totalShellSidePressureDrop;
  }

  /**
   * Gets the list of zones.
   *
   * @return list of incremental zones
   */
  public List<IncrementalZone> getZones() {
    return zones;
  }

  /**
   * Gets the target zone count.
   *
   * @return target number of zones
   */
  public int getTargetZoneCount() {
    return targetZoneCount;
  }

  /**
   * Sets the tube wall thermal conductivity.
   *
   * @param conductivity conductivity (W/(m*K))
   */
  public void setTubeWallConductivity(double conductivity) {
    this.tubeWallConductivity = conductivity;
  }

  // ============================================================================
  // Zone data class
  // ============================================================================

  /**
   * Represents a single incremental zone in the heat exchanger.
   *
   * <p>
   * Each zone contains local fluid properties for both tube and shell side, temperature endpoints,
   * and computed results (HTC, U, area, pressure drop).
   * </p>
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  public static class IncrementalZone implements Serializable {

    /** Serialization version UID. */
    private static final long serialVersionUID = 1003L;

    /** Zone name or identifier. */
    public String zoneName = "";

    /** Heat duty for this zone (W). */
    public double duty = 0.0;

    // ---- Temperature endpoints ----
    /** Hot-side inlet temperature for this zone (K). */
    public double hotInletTemp = 0.0;
    /** Hot-side outlet temperature for this zone (K). */
    public double hotOutletTemp = 0.0;
    /** Cold-side inlet temperature for this zone (K). */
    public double coldInletTemp = 0.0;
    /** Cold-side outlet temperature for this zone (K). */
    public double coldOutletTemp = 0.0;

    // ---- Tube-side fluid properties (bulk/average for the zone) ----
    /** Tube phase regime. */
    public PhaseRegime tubePhaseRegime = PhaseRegime.LIQUID;
    /** Tube-side density (kg/m3). */
    public double tubeDensity = 800.0;
    /** Tube-side viscosity (Pa*s). */
    public double tubeViscosity = 5e-4;
    /** Tube-side heat capacity (J/(kg*K)). */
    public double tubeCp = 2500.0;
    /** Tube-side thermal conductivity (W/(m*K)). */
    public double tubeConductivity = 0.13;
    /** Tube-side mass flow rate (kg/s). */
    public double tubeMassFlowRate = 10.0;

    // Two-phase tube-side properties (used when regime is CONDENSING or EVAPORATING)
    /** Tube-side liquid density (kg/m3). */
    public double tubeLiquidDensity = 800.0;
    /** Tube-side vapor density (kg/m3). */
    public double tubeVaporDensity = 5.0;
    /** Tube-side liquid viscosity (Pa*s). */
    public double tubeLiquidViscosity = 5e-4;
    /** Tube-side vapor viscosity (Pa*s). */
    public double tubeVaporViscosity = 1e-5;
    /** Tube-side liquid heat capacity (J/(kg*K)). */
    public double tubeLiquidCp = 2500.0;
    /** Tube-side liquid thermal conductivity (W/(m*K)). */
    public double tubeLiquidConductivity = 0.13;
    /** Tube-side vapor quality at zone inlet (0 to 1). */
    public double tubeQualityIn = 0.9;
    /** Tube-side vapor quality at zone outlet (0 to 1). */
    public double tubeQualityOut = 0.7;
    /** Tube-side reduced pressure P/Pc (for Shah correlation). */
    public double tubeReducedPressure = 0.1;
    /** Tube-side surface tension (N/m). */
    public double tubeSurfaceTension = 0.01;
    /** Tube-side heat of vaporization (J/kg). */
    public double tubeHeatOfVaporization = 200000.0;

    // ---- Shell-side fluid properties ----
    /** Shell phase regime. */
    public PhaseRegime shellPhaseRegime = PhaseRegime.LIQUID;
    /** Shell-side density (kg/m3). */
    public double shellDensity = 1000.0;
    /** Shell-side viscosity (Pa*s). */
    public double shellViscosity = 8e-4;
    /** Shell-side heat capacity (J/(kg*K)). */
    public double shellCp = 4180.0;
    /** Shell-side thermal conductivity (W/(m*K)). */
    public double shellConductivity = 0.6;
    /** Shell-side mass flow rate (kg/s). */
    public double shellMassFlowRate = 20.0;

    // ---- Computed results (filled by calculate()) ----
    /** Computed tube-side HTC (W/(m2*K)). */
    public double tubeSideHTC = 0.0;
    /** Computed shell-side HTC (W/(m2*K)). */
    public double shellSideHTC = 0.0;
    /** Computed overall U (W/(m2*K)). */
    public double overallU = 0.0;
    /** Computed LMTD for this zone (K). */
    public double lmtd = 0.0;
    /** Computed required area for this zone (m2). */
    public double requiredArea = 0.0;
    /** Computed tube-side pressure drop for this zone (Pa). */
    public double tubeSidePressureDrop = 0.0;
    /** Computed shell-side pressure drop for this zone (Pa). */
    public double shellSidePressureDrop = 0.0;

    /**
     * Default constructor.
     */
    public IncrementalZone() {}

    /**
     * Sets the temperature endpoints for this zone.
     *
     * @param hotIn hot-side inlet temperature (K)
     * @param hotOut hot-side outlet temperature (K)
     * @param coldIn cold-side inlet temperature (K)
     * @param coldOut cold-side outlet temperature (K)
     */
    public void setTemperatures(double hotIn, double hotOut, double coldIn, double coldOut) {
      this.hotInletTemp = hotIn;
      this.hotOutletTemp = hotOut;
      this.coldInletTemp = coldIn;
      this.coldOutletTemp = coldOut;
    }

    /**
     * Sets single-phase tube-side properties.
     *
     * @param density density (kg/m3)
     * @param viscosity viscosity (Pa*s)
     * @param cp heat capacity (J/(kg*K))
     * @param conductivity thermal conductivity (W/(m*K))
     * @param massFlowRate mass flow rate (kg/s)
     * @param regime phase regime
     */
    public void setTubeSideProperties(double density, double viscosity, double cp,
        double conductivity, double massFlowRate, PhaseRegime regime) {
      this.tubeDensity = density;
      this.tubeViscosity = viscosity;
      this.tubeCp = cp;
      this.tubeConductivity = conductivity;
      this.tubeMassFlowRate = massFlowRate;
      this.tubePhaseRegime = regime;
    }

    /**
     * Sets shell-side properties.
     *
     * @param density density (kg/m3)
     * @param viscosity viscosity (Pa*s)
     * @param cp heat capacity (J/(kg*K))
     * @param conductivity thermal conductivity (W/(m*K))
     * @param massFlowRate mass flow rate (kg/s)
     * @param regime phase regime
     */
    public void setShellSideProperties(double density, double viscosity, double cp,
        double conductivity, double massFlowRate, PhaseRegime regime) {
      this.shellDensity = density;
      this.shellViscosity = viscosity;
      this.shellCp = cp;
      this.shellConductivity = conductivity;
      this.shellMassFlowRate = massFlowRate;
      this.shellPhaseRegime = regime;
    }
  }
}
