package neqsim.thermo.util.mineral;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import neqsim.thermo.system.SystemInterface;

/**
 * Estimates the original (pre-souring) H2S content of a reservoir fluid from mineral buffering.
 *
 * <p>
 * In clastic reservoirs that contain pyrite and an Fe-bearing carbonate (siderite or ankerite), the H2S fugacity is
 * fixed by 4 FeS2 + CH4 + 3 CO2 + 6 H2O = 4 FeCO3 + 8 H2S, so that
 * {@code 8 ln f(H2S) = ln K(T,P) + ln f(CH4) + 3 ln f(CO2) + 6 ln a(H2O) - 4 ln a(FeCO3)}. The fugacities of CH4 and
 * CO2 are taken from the equation of state of the fluid and the H2S mole fraction is solved so that its fugacity meets
 * the buffer. The default Fe-carbonate activity of 0.38 reproduces the Haaland et al. (1999) correlation for Norwegian
 * Continental Shelf sandstone reservoirs over 60-190 °C when applied to measured reservoir-fluid compositions with SRK.
 * </p>
 *
 * <p>
 * The estimator also checks the phase rule: the buffer requires siderite to be stable relative to magnetite (CO2
 * fugacity above the siderite-magnetite boundary) and pyrite to be stable relative to pyrrhotite. Below about 70 °C the
 * equilibrium is unlikely to be reached and bacterial sulfate reduction may dominate; carbonate reservoirs with
 * anhydrite above about 120-140 °C may exceed the buffer by thermochemical sulfate reduction. Both cases are reported
 * as warnings.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class ReservoirH2SEstimator implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Fe-carbonate activity calibrated to the Haaland et al. (1999) NCS correlation. */
  public static final double NCS_CALIBRATED_FE_CARBONATE_ACTIVITY = 0.38;

  /** Temperature below which mineral equilibrium is unlikely to control H2S [K]. */
  private static final double KINETIC_LIMIT_TEMPERATURE = 343.15;

  /** Temperature above which thermochemical sulfate reduction can add H2S [K]. */
  private static final double TSR_ONSET_TEMPERATURE = 393.15;

  /** Pyrite-siderite buffer reaction. */
  private final SolidGasReactionEquilibrium buffer = MineralH2SBuffers.pyriteSiderite();
  /** Siderite-magnetite boundary reaction. */
  private final SolidGasReactionEquilibrium sideriteMagnetite = MineralH2SBuffers.sideriteMagnetite();
  /** Pyrite-pyrrhotite boundary reaction. */
  private final SolidGasReactionEquilibrium pyritePyrrhotite = MineralH2SBuffers.pyritePyrrhotite();
  /** Fe-carbonate activity. */
  private double feCarbonateActivity = NCS_CALIBRATED_FE_CARBONATE_ACTIVITY;
  /** Formation-water activity. */
  private double waterActivity = 1.0;
  /** Whether the reservoir contains anhydrite or other sulfate minerals. */
  private boolean sulfateMineralsPresent = false;

  /** Creates an estimator with the NCS-calibrated Fe-carbonate activity and pure water. */
  public ReservoirH2SEstimator() {
    applyActivities();
  }

  /**
   * Sets the activity of FeCO3 in the carbonate (1 for pure siderite, lower for ankerite or solid solutions).
   *
   * @param activity activity (0 &lt; a &lt;= 1)
   * @throws IllegalArgumentException if the activity is outside (0, 1]
   */
  public void setFeCarbonateActivity(double activity) {
    if (!(activity > 0.0 && activity <= 1.0)) {
      throw new IllegalArgumentException("Fe-carbonate activity must be in (0, 1]");
    }
    this.feCarbonateActivity = activity;
    applyActivities();
  }

  /**
   * Sets the formation-water activity (about 0.93-0.99 for typical formation brines).
   *
   * @param activity activity (0 &lt; a &lt;= 1)
   * @throws IllegalArgumentException if the activity is outside (0, 1]
   */
  public void setWaterActivity(double activity) {
    if (!(activity > 0.0 && activity <= 1.0)) {
      throw new IllegalArgumentException("Water activity must be in (0, 1]");
    }
    this.waterActivity = activity;
    applyActivities();
  }

  /**
   * Declares whether anhydrite or other sulfate minerals are present (enables the TSR warning).
   *
   * @param present true if sulfate minerals are present
   */
  public void setSulfateMineralsPresent(boolean present) {
    this.sulfateMineralsPresent = present;
  }

  /**
   * Gets the Fe-carbonate activity.
   *
   * @return activity
   */
  public double getFeCarbonateActivity() {
    return feCarbonateActivity;
  }

  /**
   * Gets the water activity.
   *
   * @return activity
   */
  public double getWaterActivity() {
    return waterActivity;
  }

  /** Applies the activities to the buffer and boundary reactions. */
  private void applyActivities() {
    buffer.setActivity("siderite", feCarbonateActivity);
    buffer.setActivity("water", waterActivity);
    sideriteMagnetite.setActivity("siderite", feCarbonateActivity);
    sideriteMagnetite.setActivity("water", waterActivity);
    pyritePyrrhotite.setActivity("water", waterActivity);
  }

  /**
   * Estimates the buffered H2S content of a reservoir fluid at its temperature and pressure.
   *
   * @param fluid reservoir fluid containing methane and CO2 (H2S is added as a trace if absent); not modified
   * @return estimate
   * @throws IllegalArgumentException if the fluid lacks methane or CO2
   */
  public Estimate estimate(SystemInterface fluid) {
    if (!fluid.hasComponent("methane") || !fluid.hasComponent("CO2")) {
      throw new IllegalArgumentException("Fluid must contain methane and CO2");
    }
    SystemInterface work = fluid.clone();
    if (!work.hasComponent("H2S")) {
      work.addComponent("H2S", 1.0e-9 * work.getTotalNumberOfMoles());
      if (work.getMixingRule() != null) {
        work.setMixingRule(work.getMixingRule());
      }
    }
    SolidGasReactionEquilibrium.GasExchangeResult result = buffer.solveForGas(work, "H2S");
    double t = result.getTemperature();
    double p = result.getPressure();
    double fCo2 = result.getGasFugacity("CO2");
    double fCh4 = result.getGasFugacity("methane");
    double fH2s = result.getGasFugacity("H2S");

    double lnQsm = sideriteMagnetite.getLnEquilibriumGasFugacityQuotient(t, p);
    double fCo2Boundary = Math.exp((lnQsm - Math.log(fCh4)) / 11.0);
    Map<String, Double> others = new HashMap<String, Double>();
    others.put("methane", fCh4);
    others.put("CO2", fCo2);
    double fH2sPyritePyrrhotite = Math.exp(pyritePyrrhotite.getLnEquilibriumGasFugacity("H2S", others, t, p));

    List<String> warnings = new ArrayList<String>(result.getWarnings());
    double co2Margin = Math.log10(fCo2 / fCo2Boundary);
    if (co2Margin < 0.0) {
      warnings.add("CO2 fugacity below the siderite-magnetite boundary: siderite is not stable and the "
          + "pyrite-siderite buffer does not apply");
    }
    if (fH2s < fH2sPyritePyrrhotite) {
      warnings.add("Buffered H2S below the pyrite-pyrrhotite boundary: pyrite is not stable");
    }
    if (t < KINETIC_LIMIT_TEMPERATURE) {
      warnings.add("Below about 70 C mineral equilibrium is slow; measured H2S is usually lower (or controlled by "
          + "bacterial sulfate reduction)");
    }
    if (sulfateMineralsPresent && t > TSR_ONSET_TEMPERATURE) {
      warnings.add("Sulfate minerals present above about 120 C: thermochemical sulfate reduction can raise H2S "
          + "above the buffer until iron is exhausted");
    }
    return new Estimate(result, co2Margin, Math.log10(fH2s / fH2sPyritePyrrhotite), feCarbonateActivity, waterActivity,
        warnings);
  }

  /**
   * Result of {@link ReservoirH2SEstimator#estimate(SystemInterface)}.
   *
   * @author esol
   * @version 1.0
   */
  public static class Estimate implements Serializable {
    private static final long serialVersionUID = 1000L;

    /** Underlying gas-exchange result. */
    private final SolidGasReactionEquilibrium.GasExchangeResult result;
    /** log10 of CO2 fugacity over the siderite-magnetite boundary value. */
    private final double sideriteStabilityMargin;
    /** log10 of buffered H2S fugacity over the pyrite-pyrrhotite boundary value. */
    private final double pyriteStabilityMargin;
    /** Fe-carbonate activity used. */
    private final double feCarbonateActivity;
    /** Water activity used. */
    private final double waterActivity;
    /** Warnings. */
    private final List<String> warnings;

    /**
     * Creates an estimate.
     *
     * @param result gas-exchange result
     * @param sideriteStabilityMargin log10 CO2 fugacity margin over the siderite-magnetite boundary
     * @param pyriteStabilityMargin log10 H2S fugacity margin over the pyrite-pyrrhotite boundary
     * @param feCarbonateActivity Fe-carbonate activity
     * @param waterActivity water activity
     * @param warnings warnings
     */
    Estimate(SolidGasReactionEquilibrium.GasExchangeResult result, double sideriteStabilityMargin,
        double pyriteStabilityMargin, double feCarbonateActivity, double waterActivity, List<String> warnings) {
      this.result = result;
      this.sideriteStabilityMargin = sideriteStabilityMargin;
      this.pyriteStabilityMargin = pyriteStabilityMargin;
      this.feCarbonateActivity = feCarbonateActivity;
      this.waterActivity = waterActivity;
      this.warnings = Collections.unmodifiableList(warnings);
    }

    /**
     * Gets the H2S content of the whole reservoir fluid.
     *
     * @return H2S [ppm mol]
     */
    public double getH2SPpm() {
      return result.getProductMoleFraction() * 1.0e6;
    }

    /**
     * Gets the buffered H2S fugacity.
     *
     * @return fugacity [bar]
     */
    public double getH2SFugacity() {
      return result.getGasFugacity("H2S");
    }

    /**
     * Gets the log10 margin of the CO2 fugacity over the siderite-magnetite boundary (positive: siderite stable).
     *
     * @return margin in log10 units
     */
    public double getSideriteStabilityMargin() {
      return sideriteStabilityMargin;
    }

    /**
     * Gets the log10 margin of the buffered H2S fugacity over the pyrite-pyrrhotite boundary (positive: pyrite stable).
     *
     * @return margin in log10 units
     */
    public double getPyriteStabilityMargin() {
      return pyriteStabilityMargin;
    }

    /**
     * Checks whether pyrite and siderite are both stable, so the buffer is thermodynamically consistent.
     *
     * @return true if both stability margins are positive
     */
    public boolean isBufferConsistent() {
      return sideriteStabilityMargin > 0.0 && pyriteStabilityMargin > 0.0;
    }

    /**
     * Checks solver convergence.
     *
     * @return true if converged
     */
    public boolean isConverged() {
      return result.isConverged();
    }

    /**
     * Gets the Fe-carbonate activity used.
     *
     * @return activity
     */
    public double getFeCarbonateActivity() {
      return feCarbonateActivity;
    }

    /**
     * Gets the water activity used.
     *
     * @return activity
     */
    public double getWaterActivity() {
      return waterActivity;
    }

    /**
     * Gets a copy of the equilibrated fluid.
     *
     * @return fluid clone with buffered H2S
     */
    public SystemInterface getFluid() {
      return result.getEquilibriumFluid();
    }

    /**
     * Gets warnings about data range, stability, kinetics and sulfate reduction.
     *
     * @return unmodifiable warning list
     */
    public List<String> getWarnings() {
      return warnings;
    }
  }
}
