package neqsim.process.mechanicaldesign.heatexchanger;

import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.GsonBuilder;
import neqsim.fluidmechanics.flownode.HeatTransferCoefficientCalculator;

/**
 * Thermal-hydraulic design calculator for shell and tube heat exchangers.
 *
 * <p>
 * Calculates tube-side and shell-side heat transfer coefficients, overall heat transfer coefficient
 * U, pressure drops, and performs zone-by-zone analysis for two-phase services. Integrates
 * correlations from {@link HeatTransferCoefficientCalculator} with shell-and-tube geometry from
 * {@link ShellAndTubeDesignCalculator}.
 * </p>
 *
 * <p>
 * Supported methods:
 * </p>
 * <ul>
 * <li><b>Tube side:</b> Gnielinski (turbulent), Dittus-Boelter (fallback), plus condensation and
 * evaporation correlations</li>
 * <li><b>Shell side:</b> Kern method (simple), Bell-Delaware method (detailed)</li>
 * <li><b>Overall U:</b> Resistance-in-series model including fouling and tube wall</li>
 * <li><b>Pressure drops:</b> Fanning/Kern correlations</li>
 * <li><b>Zone analysis:</b> Divide into sensible/condensing/boiling zones</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 * @see HeatTransferCoefficientCalculator
 * @see BellDelawareMethod
 * @see ShellAndTubeDesignCalculator
 */
public class ThermalDesignCalculator {

  /**
   * Shell-side analysis method selection.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  public enum ShellSideMethod {
    /** Simple Kern method. */
    KERN,
    /** Detailed Bell-Delaware method. */
    BELL_DELAWARE
  }

  // ============================================================================
  // Geometry inputs (set from ShellAndTubeDesignCalculator)
  // ============================================================================
  private double tubeODm = 0.01905;
  private double tubeIDm = 0.01483;
  private double tubeLengthm = 6.096;
  private int tubeCount = 100;
  private int tubePasses = 2;
  private double tubePitchm = 0.02381;
  private boolean triangularPitch = true;
  private double shellIDm = 0.5;
  private double baffleSpacingm = 0.2;
  private int baffleCount = 10;
  private double baffleCut = 0.25;

  // Tube wall thermal conductivity
  private double tubeWallConductivity = 52.0; // W/(m*K) default carbon steel

  // ============================================================================
  // Fluid properties - Tube side
  // ============================================================================
  private double tubeDensity = 800.0; // kg/m3
  private double tubeViscosity = 5e-4; // Pa*s
  private double tubeCp = 2500.0; // J/(kg*K)
  private double tubeConductivity = 0.13; // W/(m*K)
  private double tubeMassFlowRate = 10.0; // kg/s
  private boolean tubeHeating = true;

  // ============================================================================
  // Fluid properties - Shell side
  // ============================================================================
  private double shellDensity = 1.5; // kg/m3
  private double shellViscosity = 1.5e-5; // Pa*s
  private double shellCp = 1100.0; // J/(kg*K)
  private double shellConductivity = 0.025; // W/(m*K)
  private double shellMassFlowRate = 5.0; // kg/s
  private double shellViscosityWall = -1.0; // Pa*s at wall temp, -1 = use bulk

  // ============================================================================
  // Fouling resistances
  // ============================================================================
  private double foulingTube = 0.000176; // m2*K/W
  private double foulingShell = 0.000176; // m2*K/W

  // ============================================================================
  // Results
  // ============================================================================
  private double tubeSideHTC = 0.0; // W/(m2*K)
  private double shellSideHTC = 0.0; // W/(m2*K)
  private double overallU = 0.0; // W/(m2*K) based on outside area
  private double tubeSidePressureDrop = 0.0; // Pa
  private double shellSidePressureDrop = 0.0; // Pa
  private double tubeSideVelocity = 0.0; // m/s
  private double shellSideVelocity = 0.0; // m/s
  private double tubeSideRe = 0.0;
  private double shellSideRe = 0.0;

  // Shell-side correction factors (Bell-Delaware)
  private double Jc = 1.0;
  private double Jl = 1.0;
  private double Jb = 1.0;
  private double Js = 1.0;
  private double Jr = 1.0;

  // Method selection
  private ShellSideMethod shellSideMethod = ShellSideMethod.KERN;

  // Bell-Delaware specific geometry
  private double tubeToBaffleClearance = 0.0004; // m (0.4 mm typical)
  private double shellToBaffleClearance = 0.003; // m (3 mm typical)
  private double bypassArea = 0.0; // m2
  private boolean hasSealing = false;
  private int sealingPairs = 0;

  /**
   * Creates a new ThermalDesignCalculator with default parameters.
   */
  public ThermalDesignCalculator() {}

  /**
   * Performs the full thermal-hydraulic calculation.
   *
   * <p>
   * Calculates tube-side HTC, shell-side HTC, overall U, and pressure drops.
   * </p>
   */
  public void calculate() {
    calculateTubeSide();
    calculateShellSide();
    calculateOverallU();
    calculateTubeSidePressureDrop();
    calculateShellSidePressureDrop();
  }

  /**
   * Calculates tube-side heat transfer coefficient using Gnielinski or Dittus-Boelter correlation.
   */
  private void calculateTubeSide() {
    if (tubeCount <= 0 || tubePasses <= 0 || tubeIDm <= 0) {
      return;
    }

    // Flow area per pass
    double areaPerTube = Math.PI / 4.0 * tubeIDm * tubeIDm;
    int tubesPerPass = tubeCount / tubePasses;
    double totalFlowArea = tubesPerPass * areaPerTube;

    if (totalFlowArea <= 0) {
      return;
    }

    // Tube-side velocity
    tubeSideVelocity = tubeMassFlowRate / (tubeDensity * totalFlowArea);

    // Reynolds number
    tubeSideRe = tubeDensity * tubeSideVelocity * tubeIDm / tubeViscosity;

    // Prandtl number
    double Pr = tubeCp * tubeViscosity / tubeConductivity;

    // Nusselt number
    double Nu;
    if (tubeSideRe > 3000) {
      // Gnielinski correlation (preferred for transitional and turbulent)
      double f = calcDarcyFriction(tubeSideRe);
      Nu = HeatTransferCoefficientCalculator.calculateGnielinskiNusselt(tubeSideRe, Pr, f);
    } else if (tubeSideRe > 2300) {
      // Transition region - interpolate
      double NuLam = HeatTransferCoefficientCalculator.calculateLaminarNusselt(true);
      double f = calcDarcyFriction(3000);
      double NuTurb = HeatTransferCoefficientCalculator.calculateGnielinskiNusselt(3000, Pr, f);
      double frac = (tubeSideRe - 2300.0) / 700.0;
      Nu = NuLam + frac * (NuTurb - NuLam);
    } else {
      // Laminar
      Nu = HeatTransferCoefficientCalculator.calculateLaminarNusselt(true);
    }

    tubeSideHTC = Nu * tubeConductivity / tubeIDm;
  }

  /**
   * Calculates shell-side heat transfer coefficient using the selected method.
   */
  private void calculateShellSide() {
    if (shellSideMethod == ShellSideMethod.BELL_DELAWARE) {
      calculateShellSideBellDelaware();
    } else {
      calculateShellSideKern();
    }
  }

  /**
   * Calculates shell-side HTC using the Kern method.
   */
  private void calculateShellSideKern() {
    double tubeOD = tubeODm;
    double tubePitch = tubePitchm;

    // Shell-side equivalent diameter
    double De = BellDelawareMethod.calcShellEquivDiameter(tubeOD, tubePitch, triangularPitch);
    if (De <= 0) {
      return;
    }

    // Crossflow area
    double crossflowArea =
        BellDelawareMethod.calcCrossflowArea(shellIDm, baffleSpacingm, tubeOD, tubePitch);
    if (crossflowArea <= 0) {
      return;
    }

    // Mass flux
    double massFlux = shellMassFlowRate / crossflowArea;

    // Shell-side velocity
    shellSideVelocity = massFlux / shellDensity;

    // Reynolds number
    shellSideRe = massFlux * De / shellViscosity;

    // Wall viscosity
    double muW = (shellViscosityWall > 0) ? shellViscosityWall : shellViscosity;

    // Kern correlation
    shellSideHTC = BellDelawareMethod.calcKernShellSideHTC(massFlux, De, shellViscosity, shellCp,
        shellConductivity, muW);
  }

  /**
   * Calculates shell-side HTC using the Bell-Delaware method with correction factors.
   */
  private void calculateShellSideBellDelaware() {
    double tubeOD = tubeODm;
    double tubePitch = tubePitchm;

    // Crossflow area
    double crossflowArea =
        BellDelawareMethod.calcCrossflowArea(shellIDm, baffleSpacingm, tubeOD, tubePitch);
    if (crossflowArea <= 0) {
      return;
    }

    // Mass flux
    double massFlux = shellMassFlowRate / crossflowArea;
    shellSideVelocity = massFlux / shellDensity;

    // Reynolds number
    shellSideRe = massFlux * tubeOD / shellViscosity;

    // Wall viscosity
    double muW = (shellViscosityWall > 0) ? shellViscosityWall : shellViscosity;

    // Ideal crossflow HTC
    double hIdeal = BellDelawareMethod.calcIdealCrossflowHTC(massFlux, tubeOD, shellViscosity,
        shellCp, shellConductivity, muW, triangularPitch ? 1.0 : 0.0);

    // Bell-Delaware correction factors
    Jc = BellDelawareMethod.calcJc(baffleCut);

    Jl = BellDelawareMethod.calcJl(tubeToBaffleClearance, shellToBaffleClearance, crossflowArea,
        tubeCount, tubeOD, baffleSpacingm);

    if (bypassArea <= 0) {
      // Estimate bypass area
      double bundleDiam = shellIDm * 0.9; // approximate
      bypassArea = BellDelawareMethod.calcBypassArea(shellIDm, bundleDiam, baffleSpacingm);
    }
    int tubeRowsCrossflow = BellDelawareMethod.estimateTubeRowsCrossflow(shellIDm, baffleCut,
        tubePitch, triangularPitch);
    Jb = BellDelawareMethod.calcJb(bypassArea, crossflowArea, hasSealing, sealingPairs,
        tubeRowsCrossflow);

    Js = BellDelawareMethod.calcJs(baffleSpacingm, baffleSpacingm, baffleSpacingm, baffleCount);

    Jr = BellDelawareMethod.calcJr(shellSideRe, tubeRowsCrossflow);

    // Corrected HTC
    shellSideHTC = BellDelawareMethod.calcCorrectedHTC(hIdeal, Jc, Jl, Jb, Js, Jr);
  }

  /**
   * Calculates the overall heat transfer coefficient based on outside tube area.
   *
   * <p>
   * Resistance model:
   * </p>
   *
   * <pre>
   * 1/U_o = 1/h_o + R_fo + (d_o * ln(d_o/d_i))/(2*k_w) + (d_o/d_i)*(R_fi + 1/h_i)
   * </pre>
   */
  private void calculateOverallU() {
    if (tubeSideHTC <= 0 || shellSideHTC <= 0) {
      overallU = 0.0;
      return;
    }

    double doByDi = tubeODm / tubeIDm;

    // Individual resistances
    double rShell = 1.0 / shellSideHTC;
    double rFoulShell = foulingShell;
    double rWall = tubeODm * Math.log(doByDi) / (2.0 * tubeWallConductivity);
    double rFoulTube = doByDi * foulingTube;
    double rTube = doByDi / tubeSideHTC;

    double totalResistance = rShell + rFoulShell + rWall + rFoulTube + rTube;

    if (totalResistance > 0) {
      overallU = 1.0 / totalResistance;
    }
  }

  /**
   * Calculates tube-side pressure drop.
   *
   * <p>
   * Includes straight-tube friction and return losses:
   * </p>
   *
   * <pre>
   * dP_tube = N_p * (f * L / d_i * rho * v ^ 2 / 2 + 2.5 * rho * v ^ 2 / 2)
   * </pre>
   */
  private void calculateTubeSidePressureDrop() {
    if (tubeSideRe <= 0 || tubeSideVelocity <= 0) {
      tubeSidePressureDrop = 0.0;
      return;
    }

    double f = calcDarcyFriction(tubeSideRe);

    // Straight-tube friction
    double dpFriction =
        f * tubeLengthm / tubeIDm * tubeDensity * tubeSideVelocity * tubeSideVelocity / 2.0;

    // Return losses (2.5 velocity heads per pass)
    double dpReturn = 2.5 * tubeDensity * tubeSideVelocity * tubeSideVelocity / 2.0;

    // Total for all passes
    tubeSidePressureDrop = tubePasses * (dpFriction + dpReturn);
  }

  /**
   * Calculates shell-side pressure drop.
   */
  private void calculateShellSidePressureDrop() {
    if (shellSideVelocity <= 0 || shellDensity <= 0) {
      shellSidePressureDrop = 0.0;
      return;
    }

    if (shellSideMethod == ShellSideMethod.BELL_DELAWARE) {
      calculateShellSidePressureDropBellDelaware();
    } else {
      calculateShellSidePressureDropKern();
    }
  }

  /**
   * Kern shell-side pressure drop calculation.
   */
  private void calculateShellSidePressureDropKern() {
    double De = BellDelawareMethod.calcShellEquivDiameter(tubeODm, tubePitchm, triangularPitch);
    if (De <= 0) {
      return;
    }

    double crossflowArea =
        BellDelawareMethod.calcCrossflowArea(shellIDm, baffleSpacingm, tubeODm, tubePitchm);
    if (crossflowArea <= 0) {
      return;
    }

    double massFlux = shellMassFlowRate / crossflowArea;
    double muW = (shellViscosityWall > 0) ? shellViscosityWall : shellViscosity;

    shellSidePressureDrop = BellDelawareMethod.calcKernShellSidePressureDrop(massFlux, De, shellIDm,
        baffleCount, shellDensity, shellViscosity, muW);
  }

  /**
   * Bell-Delaware shell-side pressure drop with correction factors.
   */
  private void calculateShellSidePressureDropBellDelaware() {
    double crossflowArea =
        BellDelawareMethod.calcCrossflowArea(shellIDm, baffleSpacingm, tubeODm, tubePitchm);
    if (crossflowArea <= 0) {
      return;
    }

    double massFlux = shellMassFlowRate / crossflowArea;
    int tubeRowsCrossflow = BellDelawareMethod.estimateTubeRowsCrossflow(shellIDm, baffleCut,
        tubePitchm, triangularPitch);

    // Ideal crossflow DP per compartment
    double dpIdeal = BellDelawareMethod.calcIdealCrossflowDP(tubeRowsCrossflow, massFlux,
        shellDensity, shellSideRe, triangularPitch);

    // Leakage and bypass correction
    double Rl = BellDelawareMethod.calcRl(tubeToBaffleClearance, shellToBaffleClearance,
        crossflowArea, tubeCount, tubeODm, baffleSpacingm);

    if (bypassArea <= 0) {
      double bundleDiam = shellIDm * 0.9;
      bypassArea = BellDelawareMethod.calcBypassArea(shellIDm, bundleDiam, baffleSpacingm);
    }
    double Rb = BellDelawareMethod.calcRb(bypassArea, crossflowArea, hasSealing, sealingPairs,
        tubeRowsCrossflow);

    // Window DP
    double windowArea = Math.PI / 4.0 * shellIDm * shellIDm * baffleCut * 0.5;
    int tubeRowsWindow = Math.max(1, tubeRowsCrossflow / 3);
    double dpWindow = BellDelawareMethod.calcWindowDP(windowArea, shellMassFlowRate, shellDensity,
        tubeRowsWindow);

    // Total shell-side DP
    double dpCrossflow = dpIdeal * (baffleCount - 1) * Rl * Rb;
    double dpWindows = baffleCount * dpWindow * Rl;
    double dpEndZones = 2.0 * dpIdeal * (1.0 + tubeRowsCrossflow / 5.0) * Rb;

    shellSidePressureDrop = dpCrossflow + dpWindows + dpEndZones;
  }

  /**
   * Performs zone-by-zone analysis for a phase-changing service.
   *
   * <p>
   * Divides the exchanger into zones based on fluid phase: desuperheating, condensing, subcooling
   * (or preheating, boiling, superheating). Computes effective U and required area for each zone.
   * </p>
   *
   * @param zones array of zone definitions with fluid properties per zone
   * @return array of zone results
   */
  public ZoneResult[] calculateZones(ZoneDefinition[] zones) {
    if (zones == null || zones.length == 0) {
      return new ZoneResult[0];
    }

    ZoneResult[] results = new ZoneResult[zones.length];

    for (int i = 0; i < zones.length; i++) {
      ZoneDefinition zone = zones[i];

      // Save current properties
      double savedTubeDensity = tubeDensity;
      double savedTubeViscosity = tubeViscosity;
      double savedTubeCp = tubeCp;
      double savedTubeConductivity = tubeConductivity;
      double savedShellDensity = shellDensity;
      double savedShellViscosity = shellViscosity;
      double savedShellCp = shellCp;
      double savedShellConductivity = shellConductivity;

      // Apply zone-specific properties
      tubeDensity = zone.tubeDensity;
      tubeViscosity = zone.tubeViscosity;
      tubeCp = zone.tubeCp;
      tubeConductivity = zone.tubeConductivity;
      shellDensity = zone.shellDensity;
      shellViscosity = zone.shellViscosity;
      shellCp = zone.shellCp;
      shellConductivity = zone.shellConductivity;

      // Calculate for this zone
      calculate();

      // Store results
      ZoneResult result = new ZoneResult();
      result.zoneName = zone.zoneName;
      result.dutyFraction = zone.dutyFraction;
      result.tubeSideHTC = tubeSideHTC;
      result.shellSideHTC = shellSideHTC;
      result.overallU = overallU;
      result.lmtd = zone.lmtd;
      result.requiredArea = (overallU > 0 && zone.lmtd > 0)
          ? zone.dutyFraction * zone.totalDuty / (overallU * zone.lmtd)
          : 0.0;
      results[i] = result;

      // Restore properties
      tubeDensity = savedTubeDensity;
      tubeViscosity = savedTubeViscosity;
      tubeCp = savedTubeCp;
      tubeConductivity = savedTubeConductivity;
      shellDensity = savedShellDensity;
      shellViscosity = savedShellViscosity;
      shellCp = savedShellCp;
      shellConductivity = savedShellConductivity;
    }

    return results;
  }

  /**
   * Calculates Darcy friction factor using Swamee-Jain for turbulent, 64/Re for laminar.
   *
   * @param Re Reynolds number
   * @return Darcy friction factor
   */
  static double calcDarcyFriction(double Re) {
    if (Re <= 0) {
      return 0.0;
    }
    if (Re < 2300) {
      return 64.0 / Re;
    }
    // Swamee-Jain approximation (smooth pipe, epsilon/D ~ 0)
    double logTerm = Math.log10(5.74 / Math.pow(Re, 0.9));
    return 0.25 / (logTerm * logTerm);
  }

  /**
   * Returns all computed results as a map.
   *
   * @return map of result keys and values
   */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();

    result.put("shellSideMethod", shellSideMethod.name());

    Map<String, Object> tubeResults = new LinkedHashMap<String, Object>();
    tubeResults.put("htc_Wpm2K", tubeSideHTC);
    tubeResults.put("velocity_mps", tubeSideVelocity);
    tubeResults.put("reynoldsNumber", tubeSideRe);
    tubeResults.put("pressureDrop_Pa", tubeSidePressureDrop);
    tubeResults.put("pressureDrop_bar", tubeSidePressureDrop / 1e5);
    result.put("tubeSide", tubeResults);

    Map<String, Object> shellResults = new LinkedHashMap<String, Object>();
    shellResults.put("htc_Wpm2K", shellSideHTC);
    shellResults.put("velocity_mps", shellSideVelocity);
    shellResults.put("reynoldsNumber", shellSideRe);
    shellResults.put("pressureDrop_Pa", shellSidePressureDrop);
    shellResults.put("pressureDrop_bar", shellSidePressureDrop / 1e5);
    if (shellSideMethod == ShellSideMethod.BELL_DELAWARE) {
      Map<String, Object> corrections = new LinkedHashMap<String, Object>();
      corrections.put("Jc_baffleCut", Jc);
      corrections.put("Jl_leakage", Jl);
      corrections.put("Jb_bypass", Jb);
      corrections.put("Js_spacing", Js);
      corrections.put("Jr_adverseGradient", Jr);
      shellResults.put("bellDelawareCorrections", corrections);
    }
    result.put("shellSide", shellResults);

    Map<String, Object> overall = new LinkedHashMap<String, Object>();
    overall.put("overallU_Wpm2K", overallU);
    overall.put("foulingResistanceTube_m2KpW", foulingTube);
    overall.put("foulingResistanceShell_m2KpW", foulingShell);
    overall.put("tubeWallConductivity_WpmK", tubeWallConductivity);
    result.put("overallHeatTransfer", overall);

    return result;
  }

  /**
   * Converts all computed results to a JSON string.
   *
   * @return JSON string with pretty printing
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(toMap());
  }

  // ============================================================================
  // Zone analysis data classes
  // ============================================================================

  /**
   * Defines a thermal zone for zone-by-zone analysis.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  public static class ZoneDefinition {
    /** Name of the zone (e.g., "desuperheating", "condensing", "subcooling"). */
    public String zoneName;
    /** Fraction of total duty in this zone (0 to 1). */
    public double dutyFraction;
    /** Total heat duty (W). */
    public double totalDuty;
    /** LMTD for this zone (K). */
    public double lmtd;
    /** Tube-side density (kg/m3). */
    public double tubeDensity;
    /** Tube-side viscosity (Pa*s). */
    public double tubeViscosity;
    /** Tube-side heat capacity (J/(kg*K)). */
    public double tubeCp;
    /** Tube-side thermal conductivity (W/(m*K)). */
    public double tubeConductivity;
    /** Shell-side density (kg/m3). */
    public double shellDensity;
    /** Shell-side viscosity (Pa*s). */
    public double shellViscosity;
    /** Shell-side heat capacity (J/(kg*K)). */
    public double shellCp;
    /** Shell-side thermal conductivity (W/(m*K)). */
    public double shellConductivity;

    /**
     * Default constructor for ZoneDefinition.
     */
    public ZoneDefinition() {}
  }

  /**
   * Results for a single thermal zone.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  public static class ZoneResult {
    /** Zone name. */
    public String zoneName;
    /** Duty fraction. */
    public double dutyFraction;
    /** Tube-side HTC in this zone (W/(m2*K)). */
    public double tubeSideHTC;
    /** Shell-side HTC in this zone (W/(m2*K)). */
    public double shellSideHTC;
    /** Overall U in this zone (W/(m2*K)). */
    public double overallU;
    /** LMTD for this zone (K). */
    public double lmtd;
    /** Required area for this zone (m2). */
    public double requiredArea;

    /**
     * Default constructor for ZoneResult.
     */
    public ZoneResult() {}
  }

  // ============================================================================
  // Getters for results
  // ============================================================================

  /**
   * Gets the tube-side heat transfer coefficient.
   *
   * @return tube-side HTC (W/(m2*K))
   */
  public double getTubeSideHTC() {
    return tubeSideHTC;
  }

  /**
   * Gets the shell-side heat transfer coefficient.
   *
   * @return shell-side HTC (W/(m2*K))
   */
  public double getShellSideHTC() {
    return shellSideHTC;
  }

  /**
   * Gets the overall heat transfer coefficient based on outside area.
   *
   * @return overall U (W/(m2*K))
   */
  public double getOverallU() {
    return overallU;
  }

  /**
   * Gets the tube-side pressure drop.
   *
   * @return pressure drop (Pa)
   */
  public double getTubeSidePressureDrop() {
    return tubeSidePressureDrop;
  }

  /**
   * Gets the tube-side pressure drop in bar.
   *
   * @return pressure drop (bar)
   */
  public double getTubeSidePressureDropBar() {
    return tubeSidePressureDrop / 1e5;
  }

  /**
   * Gets the shell-side pressure drop.
   *
   * @return pressure drop (Pa)
   */
  public double getShellSidePressureDrop() {
    return shellSidePressureDrop;
  }

  /**
   * Gets the shell-side pressure drop in bar.
   *
   * @return pressure drop (bar)
   */
  public double getShellSidePressureDropBar() {
    return shellSidePressureDrop / 1e5;
  }

  /**
   * Gets the tube-side velocity.
   *
   * @return velocity (m/s)
   */
  public double getTubeSideVelocity() {
    return tubeSideVelocity;
  }

  /**
   * Gets the shell-side velocity.
   *
   * @return velocity (m/s)
   */
  public double getShellSideVelocity() {
    return shellSideVelocity;
  }

  /**
   * Gets the tube-side Reynolds number.
   *
   * @return Reynolds number
   */
  public double getTubeSideRe() {
    return tubeSideRe;
  }

  /**
   * Gets the shell-side Reynolds number.
   *
   * @return Reynolds number
   */
  public double getShellSideRe() {
    return shellSideRe;
  }

  // ============================================================================
  // Setters for geometry
  // ============================================================================

  /**
   * Sets tube outer diameter.
   *
   * @param tubeODm tube OD (m)
   */
  public void setTubeODm(double tubeODm) {
    this.tubeODm = tubeODm;
  }

  /**
   * Sets tube inner diameter.
   *
   * @param tubeIDm tube ID (m)
   */
  public void setTubeIDm(double tubeIDm) {
    this.tubeIDm = tubeIDm;
  }

  /**
   * Sets tube length.
   *
   * @param tubeLengthm tube length (m)
   */
  public void setTubeLengthm(double tubeLengthm) {
    this.tubeLengthm = tubeLengthm;
  }

  /**
   * Sets tube count.
   *
   * @param tubeCount number of tubes
   */
  public void setTubeCount(int tubeCount) {
    this.tubeCount = tubeCount;
  }

  /**
   * Sets number of tube passes.
   *
   * @param tubePasses tube passes
   */
  public void setTubePasses(int tubePasses) {
    this.tubePasses = tubePasses;
  }

  /**
   * Sets tube pitch.
   *
   * @param tubePitchm tube pitch (m)
   */
  public void setTubePitchm(double tubePitchm) {
    this.tubePitchm = tubePitchm;
  }

  /**
   * Sets whether tube layout is triangular.
   *
   * @param triangularPitch true for triangular, false for square
   */
  public void setTriangularPitch(boolean triangularPitch) {
    this.triangularPitch = triangularPitch;
  }

  /**
   * Sets shell inside diameter.
   *
   * @param shellIDm shell ID (m)
   */
  public void setShellIDm(double shellIDm) {
    this.shellIDm = shellIDm;
  }

  /**
   * Sets baffle spacing.
   *
   * @param baffleSpacingm baffle spacing (m)
   */
  public void setBaffleSpacingm(double baffleSpacingm) {
    this.baffleSpacingm = baffleSpacingm;
  }

  /**
   * Sets baffle count.
   *
   * @param baffleCount number of baffles
   */
  public void setBaffleCount(int baffleCount) {
    this.baffleCount = baffleCount;
  }

  /**
   * Sets baffle cut fraction.
   *
   * @param baffleCut baffle cut as fraction (e.g., 0.25)
   */
  public void setBaffleCut(double baffleCut) {
    this.baffleCut = baffleCut;
  }

  /**
   * Sets tube wall thermal conductivity.
   *
   * @param conductivity thermal conductivity (W/(m*K))
   */
  public void setTubeWallConductivity(double conductivity) {
    this.tubeWallConductivity = conductivity;
  }

  // ============================================================================
  // Setters for fluid properties
  // ============================================================================

  /**
   * Sets all tube-side fluid properties.
   *
   * @param density density (kg/m3)
   * @param viscosity dynamic viscosity (Pa*s)
   * @param cp heat capacity (J/(kg*K))
   * @param conductivity thermal conductivity (W/(m*K))
   * @param massFlowRate mass flow rate (kg/s)
   * @param heating true if tube fluid is being heated
   */
  public void setTubeSideFluid(double density, double viscosity, double cp, double conductivity,
      double massFlowRate, boolean heating) {
    this.tubeDensity = density;
    this.tubeViscosity = viscosity;
    this.tubeCp = cp;
    this.tubeConductivity = conductivity;
    this.tubeMassFlowRate = massFlowRate;
    this.tubeHeating = heating;
  }

  /**
   * Sets all shell-side fluid properties.
   *
   * @param density density (kg/m3)
   * @param viscosity dynamic viscosity (Pa*s)
   * @param cp heat capacity (J/(kg*K))
   * @param conductivity thermal conductivity (W/(m*K))
   * @param massFlowRate mass flow rate (kg/s)
   */
  public void setShellSideFluid(double density, double viscosity, double cp, double conductivity,
      double massFlowRate) {
    this.shellDensity = density;
    this.shellViscosity = viscosity;
    this.shellCp = cp;
    this.shellConductivity = conductivity;
    this.shellMassFlowRate = massFlowRate;
  }

  /**
   * Sets the tube-side fouling resistance.
   *
   * @param fouling fouling resistance (m2*K/W)
   */
  public void setFoulingTube(double fouling) {
    this.foulingTube = fouling;
  }

  /**
   * Sets the shell-side fouling resistance.
   *
   * @param fouling fouling resistance (m2*K/W)
   */
  public void setFoulingShell(double fouling) {
    this.foulingShell = fouling;
  }

  /**
   * Sets the shell-side analysis method.
   *
   * @param method KERN or BELL_DELAWARE
   */
  public void setShellSideMethod(ShellSideMethod method) {
    this.shellSideMethod = method;
  }

  /**
   * Gets the shell-side analysis method.
   *
   * @return method KERN or BELL_DELAWARE
   */
  public ShellSideMethod getShellSideMethod() {
    return shellSideMethod;
  }

  /**
   * Sets the shell-side viscosity at wall temperature for viscosity correction.
   *
   * @param viscosity viscosity at wall temperature (Pa*s), or negative to use bulk value
   */
  public void setShellViscosityWall(double viscosity) {
    this.shellViscosityWall = viscosity;
  }

  /**
   * Sets whether sealing strips are installed.
   *
   * @param hasSealing true if sealing strips present
   */
  public void setHasSealing(boolean hasSealing) {
    this.hasSealing = hasSealing;
  }

  /**
   * Sets the number of sealing strip pairs.
   *
   * @param sealingPairs number of pairs
   */
  public void setSealingPairs(int sealingPairs) {
    this.sealingPairs = sealingPairs;
  }

  /**
   * Sets the tube-to-baffle clearance.
   *
   * @param clearance clearance (m)
   */
  public void setTubeToBaffleClearance(double clearance) {
    this.tubeToBaffleClearance = clearance;
  }

  /**
   * Sets the shell-to-baffle clearance.
   *
   * @param clearance clearance (m)
   */
  public void setShellToBaffleClearance(double clearance) {
    this.shellToBaffleClearance = clearance;
  }

  /**
   * Sets the bypass area between bundle and shell.
   *
   * @param area bypass area (m2)
   */
  public void setBypassArea(double area) {
    this.bypassArea = area;
  }
}
