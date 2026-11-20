package neqsim.thermo.util.mineral;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.util.mercury.MercuryPhaseEquilibrium;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Estimates the upper limits of elemental mercury in a reservoir fluid from mineral equilibrium.
 *
 * <p>
 * Two condensed phases can hold mercury back in a reservoir. Mercury sulfide (cinnabar or metacinnabar, or mercury held
 * as a dilute sulfide component in pyrite) limits the mercury fugacity through
 * {@code 4 HgS + CH4 + 2 H2O = 4 Hg + 4 H2S + CO2}, so that
 * {@code 4 ln f(Hg) = ln K(T,P) + ln f(CH4) + 2 ln a(H2O) + 4 ln a(HgS) - 4 ln f(H2S) - ln f(CO2)}. Liquid mercury
 * limits it at the vapour pressure with the pressure correction. The fugacities of CH4, CO2 and H2S come from the
 * equation of state of the fluid. Mercury is treated as a dilute solute: its fugacity coefficient in each fluid phase
 * is taken at trace content, and the mercury content of the whole fluid at a limit is the limiting fugacity divided by
 * the fugacity coefficient and pressure, summed over the phases. The error is of the order of the mercury mole
 * fraction.
 * </p>
 *
 * <p>
 * The H2S fugacity is either that of the fluid (measured H2S) or that of the pyrite and Fe-carbonate buffer used by
 * {@link ReservoirH2SEstimator}, in which case the reaction becomes
 * {@code 8 HgS + 4 FeCO3 + CH4 = 8 Hg + 4 FeS2 + 5 CO2 + 2 H2O} and no H2S measurement is needed.
 * </p>
 *
 * <p>
 * The results are limits, not predictions. The sulfide limit rises by about a factor of 20 per 30 °C, so that in cool
 * reservoirs (below about 80-100 °C) the sulfide can hold mercury to low levels, while in hot reservoirs the limit lies
 * far above what source rocks supply and the mercury content is set by supply. A measured content is compared with the
 * limit through the saturation index of {@link Estimate#getSulfideSaturationIndex()}. A mercury sulfide activity below
 * one represents mercury held as a trace component of pyrite or other sulfides.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class ReservoirMercuryEstimator implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Name of the mercury component and gas species. */
  private static final String MERCURY = MercuryPhaseEquilibrium.MERCURY;

  /** Molar mass of mercury [kg/mol]. */
  private static final double MERCURY_MOLAR_MASS = 0.20059;

  /** Temperature below which the sulfide equilibrium is unlikely to be reached on cooling [K]. */
  private static final double KINETIC_LIMIT_TEMPERATURE = 333.15;

  /** Mercury mole fraction used to evaluate the dilute-solution fugacity coefficients. */
  private static final double TRACE_MOLE_FRACTION = 1.0e-10;

  /** Mercury mole fraction above which the dilute-solution treatment is no longer accurate. */
  private static final double DILUTE_LIMIT = 1.0e-3;

  /** Mercury sulfide polymorph. */
  public enum SulfidePhase {
    /** Trigonal red HgS, the stable polymorph below about 345 °C. */
    CINNABAR,
    /** Cubic black HgS, metastable and about three times more soluble at reservoir temperature. */
    METACINNABAR
  }

  /** Source of the H2S fugacity used in the sulfide limit. */
  public enum HydrogenSulfideBasis {
    /** H2S fugacity of the fluid as given (measured H2S must be part of the fluid). */
    FLUID,
    /** H2S fugacity fixed by the pyrite and Fe-carbonate buffer of the reservoir rock. */
    PYRITE_SIDERITE_BUFFER
  }

  /** Mercury sulfide polymorph. */
  private SulfidePhase sulfidePhase = SulfidePhase.CINNABAR;
  /** Source of the H2S fugacity. */
  private HydrogenSulfideBasis hydrogenSulfideBasis = HydrogenSulfideBasis.PYRITE_SIDERITE_BUFFER;
  /** Activity of HgS in the sulfide that holds the mercury. */
  private double mercurySulfideActivity = 1.0;
  /** Fe-carbonate activity. */
  private double feCarbonateActivity = ReservoirH2SEstimator.NCS_CALIBRATED_FE_CARBONATE_ACTIVITY;
  /** Formation-water activity. */
  private double waterActivity = 1.0;

  /** Creates an estimator for pure cinnabar with the H2S fugacity of the pyrite and Fe-carbonate buffer. */
  public ReservoirMercuryEstimator() {
  }

  /**
   * Sets the mercury sulfide polymorph.
   *
   * @param phase cinnabar or metacinnabar
   * @throws IllegalArgumentException if the phase is null
   */
  public void setSulfidePhase(SulfidePhase phase) {
    if (phase == null) {
      throw new IllegalArgumentException("Sulfide phase must not be null");
    }
    this.sulfidePhase = phase;
  }

  /**
   * Sets where the H2S fugacity of the sulfide limit comes from.
   *
   * @param basis the fluid itself or the pyrite and Fe-carbonate buffer
   * @throws IllegalArgumentException if the basis is null
   */
  public void setHydrogenSulfideBasis(HydrogenSulfideBasis basis) {
    if (basis == null) {
      throw new IllegalArgumentException("Hydrogen sulfide basis must not be null");
    }
    this.hydrogenSulfideBasis = basis;
  }

  /**
   * Sets the activity of HgS in the sulfide phase: 1 for pure cinnabar or metacinnabar, lower when mercury is a trace
   * component of pyrite or another sulfide. The sulfide limit is proportional to this activity.
   *
   * @param activity activity (0 &lt; a &lt;= 1)
   * @throws IllegalArgumentException if the activity is outside (0, 1]
   */
  public void setMercurySulfideActivity(double activity) {
    if (!(activity > 0.0 && activity <= 1.0)) {
      throw new IllegalArgumentException("Mercury sulfide activity must be in (0, 1]");
    }
    this.mercurySulfideActivity = activity;
  }

  /**
   * Sets the activity of FeCO3 in the carbonate, used with the pyrite and Fe-carbonate buffer.
   *
   * @param activity activity (0 &lt; a &lt;= 1)
   * @throws IllegalArgumentException if the activity is outside (0, 1]
   */
  public void setFeCarbonateActivity(double activity) {
    if (!(activity > 0.0 && activity <= 1.0)) {
      throw new IllegalArgumentException("Fe-carbonate activity must be in (0, 1]");
    }
    this.feCarbonateActivity = activity;
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
  }

  /**
   * Gets the mercury sulfide polymorph.
   *
   * @return polymorph
   */
  public SulfidePhase getSulfidePhase() {
    return sulfidePhase;
  }

  /**
   * Gets the source of the H2S fugacity.
   *
   * @return basis
   */
  public HydrogenSulfideBasis getHydrogenSulfideBasis() {
    return hydrogenSulfideBasis;
  }

  /**
   * Gets the mercury sulfide activity.
   *
   * @return activity
   */
  public double getMercurySulfideActivity() {
    return mercurySulfideActivity;
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

  /**
   * Builds the sulfide reaction for the selected polymorph and H2S basis with the activities applied.
   *
   * @return reaction
   */
  private SolidGasReactionEquilibrium sulfideReaction() {
    boolean meta = sulfidePhase == SulfidePhase.METACINNABAR;
    String sulfideName = meta ? "metacinnabar" : "cinnabar";
    SolidGasReactionEquilibrium reaction;
    if (hydrogenSulfideBasis == HydrogenSulfideBasis.PYRITE_SIDERITE_BUFFER) {
      reaction = meta ? MineralMercuryBuffers.metacinnabarPyriteSiderite()
          : MineralMercuryBuffers.cinnabarPyriteSiderite();
      reaction.setActivity("siderite", feCarbonateActivity);
    } else {
      reaction = meta ? MineralMercuryBuffers.metacinnabarMethane() : MineralMercuryBuffers.cinnabarMethane();
    }
    reaction.setActivity("water", waterActivity);
    reaction.setActivity(sulfideName, mercurySulfideActivity);
    return reaction;
  }

  /**
   * Estimates the mercury limits of a reservoir fluid at its temperature and pressure.
   *
   * @param fluid reservoir fluid containing methane and CO2, and H2S when the H2S basis is the fluid. Mercury is added
   * as a trace if absent; if present, its content is compared with the limits. The fluid is not modified.
   * @return estimate
   * @throws IllegalArgumentException if the fluid lacks methane or CO2, or lacks H2S with the fluid H2S basis
   */
  public Estimate estimate(SystemInterface fluid) {
    if (!fluid.hasComponent("methane") || !fluid.hasComponent("CO2")) {
      throw new IllegalArgumentException("Fluid must contain methane and CO2");
    }
    boolean fluidBasis = hydrogenSulfideBasis == HydrogenSulfideBasis.FLUID;
    if (fluidBasis && (!fluid.hasComponent("H2S") || !(fluid.getComponent("H2S").getNumberOfmoles() > 0.0))) {
      throw new IllegalArgumentException(
          "Fluid must contain H2S when the H2S basis is the fluid; use the pyrite-siderite buffer otherwise");
    }
    SystemInterface work = fluid.clone();
    double measured = Double.NaN;
    if (work.hasComponent(MERCURY)) {
      double moles = work.getComponent(MERCURY).getNumberOfmoles();
      if (moles > 0.0) {
        measured = moles / work.getTotalNumberOfMoles();
      }
      double otherMoles = work.getTotalNumberOfMoles() - moles;
      work.addComponent(MERCURY, TRACE_MOLE_FRACTION * otherMoles - moles);
    } else {
      work.addComponent(MERCURY, TRACE_MOLE_FRACTION * work.getTotalNumberOfMoles());
      if (work.getMixingRule() != null) {
        work.setMixingRule(work.getMixingRule());
      }
    }
    new ThermodynamicOperations(work).TPflash();
    work.init(1);
    double temperature = work.getTemperature();
    double pressure = work.getPressure();

    // Fugacities are equal in all phases at equilibrium; read the reaction gases from the first phase.
    PhaseInterface first = work.getPhase(0);
    Map<String, Double> fugacities = new HashMap<String, Double>();
    String[] gases = fluidBasis ? new String[] {"methane", "CO2", "H2S"} : new String[] {"methane", "CO2"};
    for (String gas : gases) {
      fugacities.put(gas, first.getComponent(gas).getx() * first.getComponent(gas).getFugacityCoefficient() * pressure);
    }
    // Dilute solution: mole fraction of mercury in the whole fluid per unit mercury fugacity.
    double contentPerFugacity = 0.0;
    for (int p = 0; p < work.getNumberOfPhases(); p++) {
      PhaseInterface phase = work.getPhase(p);
      contentPerFugacity += work.getBeta(p) / (phase.getComponent(MERCURY).getFugacityCoefficient() * pressure);
    }
    double mercuryPhi = first.getComponent(MERCURY).getFugacityCoefficient();

    SolidGasReactionEquilibrium sulfide = sulfideReaction();
    SolidGasReactionEquilibrium liquid = MineralMercuryBuffers.liquidMercury();
    double sulfideFugacity = Math.exp(sulfide.getLnEquilibriumGasFugacity(MERCURY, fugacities, temperature, pressure));
    double liquidFugacity = Math
        .exp(liquid.getLnEquilibriumGasFugacity(MERCURY, new HashMap<String, Double>(), temperature, pressure));
    double sulfideLimit = sulfideFugacity * contentPerFugacity;
    double liquidLimit = liquidFugacity * contentPerFugacity;

    double h2sFugacity;
    double h2sPpm;
    List<String> warnings = new ArrayList<String>(sulfide.getWarnings(temperature));
    if (fluidBasis) {
      h2sFugacity = fugacities.get("H2S");
      h2sPpm = fluid.getComponent("H2S").getNumberOfmoles() / fluid.getTotalNumberOfMoles() * 1.0e6;
    } else {
      ReservoirH2SEstimator h2s = new ReservoirH2SEstimator();
      h2s.setFeCarbonateActivity(feCarbonateActivity);
      h2s.setWaterActivity(waterActivity);
      ReservoirH2SEstimator.Estimate buffered = h2s.estimate(fluid);
      h2sFugacity = buffered.getH2SFugacity();
      h2sPpm = buffered.getH2SPpm();
      if (!buffered.isBufferConsistent()) {
        warnings.add("Pyrite and Fe-carbonate are not both stable at these conditions: the buffered H2S fugacity, "
            + "and with it the sulfide limit, does not apply");
      }
    }
    if (sulfideLimit > liquidLimit) {
      warnings.add("Mercury sulfide is not stable relative to liquid mercury at these conditions: the liquid mercury "
          + "limit applies");
    }
    if (Math.min(sulfideLimit, liquidLimit) > DILUTE_LIMIT) {
      warnings.add("Mercury limit above 0.1 mol %: the dilute-solution treatment is approximate");
    }
    if (temperature < KINETIC_LIMIT_TEMPERATURE) {
      warnings.add("Below about 60 C the sulfide equilibrium is slow; a fluid that cooled after charge can hold more "
          + "mercury than the sulfide limit at the present temperature");
    }
    warnings.add("The sulfide and liquid values are upper limits; the actual mercury content is at or below the lower "
        + "limit and is set by supply from the source rock when the limit is high");
    double controlling = Math.min(Math.min(sulfideLimit, liquidLimit), DILUTE_LIMIT);
    double otherMoles = work.getTotalNumberOfMoles() - work.getComponent(MERCURY).getNumberOfmoles();
    work.addComponent(MERCURY,
        controlling / (1.0 - controlling) * otherMoles - work.getComponent(MERCURY).getNumberOfmoles());
    return new Estimate(work, temperature, pressure, sulfideLimit, liquidLimit, sulfideFugacity, liquidFugacity,
        mercuryPhi, sulfide.getLog10KUncertainty(temperature) / Math.abs(sulfide.getCoefficient(MERCURY)), measured,
        h2sFugacity, h2sPpm, sulfidePhase, hydrogenSulfideBasis, mercurySulfideActivity, warnings);
  }

  /**
   * Finds the temperature at which a mercury content equals the sulfide limit of the fluid.
   *
   * <p>
   * The sulfide limit rises monotonically with temperature at fixed composition and pressure. Above the returned
   * temperature the sulfide cannot hold the given mercury content back; below it the fluid is supersaturated and
   * mercury sulfide can form. The search uses bisection in temperature with the full equation-of-state solution.
   * </p>
   *
   * @param fluid reservoir fluid at its pressure (its temperature is not used); not modified
   * @param mercuryMoleFraction mercury content of the reservoir fluid [mole fraction]
   * @param lowTemperature lower bound of the search [K]
   * @param highTemperature upper bound of the search [K]
   * @return temperature [K], or {@code Double.NaN} if the content lies outside the limits between the bounds
   * @throws IllegalArgumentException if the content is not positive or the bounds are not increasing
   */
  public double sulfideSaturationTemperature(SystemInterface fluid, double mercuryMoleFraction, double lowTemperature,
      double highTemperature) {
    if (!(mercuryMoleFraction > 0.0)) {
      throw new IllegalArgumentException("Mercury content must be positive");
    }
    if (!(lowTemperature > 0.0 && highTemperature > lowTemperature)) {
      throw new IllegalArgumentException("Temperature bounds must satisfy 0 < low < high");
    }
    double target = Math.log(mercuryMoleFraction);
    double lo = lowTemperature;
    double hi = highTemperature;
    double fLo = Math.log(sulfideLimitAt(fluid, lo)) - target;
    double fHi = Math.log(sulfideLimitAt(fluid, hi)) - target;
    if (fLo > 0.0 || fHi < 0.0) {
      return Double.NaN;
    }
    for (int i = 0; i < 60 && hi - lo > 0.01; i++) {
      double mid = 0.5 * (lo + hi);
      if (Math.log(sulfideLimitAt(fluid, mid)) - target < 0.0) {
        lo = mid;
      } else {
        hi = mid;
      }
    }
    return 0.5 * (lo + hi);
  }

  /**
   * Finds the sulfide saturation temperature between 20 and 250 °C.
   *
   * @param fluid reservoir fluid at its pressure; not modified
   * @param mercuryMoleFraction mercury content of the reservoir fluid [mole fraction]
   * @return temperature [K], or {@code Double.NaN} if outside the range
   * @throws IllegalArgumentException if the content is not positive
   */
  public double sulfideSaturationTemperature(SystemInterface fluid, double mercuryMoleFraction) {
    return sulfideSaturationTemperature(fluid, mercuryMoleFraction, 293.15, 523.15);
  }

  /**
   * Sulfide limit of a copy of the fluid at another temperature and the same pressure.
   *
   * @param fluid reservoir fluid; not modified
   * @param temperature temperature [K]
   * @return sulfide limit [mole fraction]
   */
  private double sulfideLimitAt(SystemInterface fluid, double temperature) {
    SystemInterface work = fluid.clone();
    work.setTemperature(temperature);
    return estimate(work).getSulfideLimitMoleFraction();
  }

  /**
   * Converts a mercury mole fraction of a fluid to a concentration.
   *
   * @param moleFraction mercury mole fraction
   * @param unit "ug/Sm3" (per standard cubic metre of ideal gas at 15 °C and 1.01325 bara), "ug/Nm3" (0 °C and 1.01325
   * bara) or "ppb" (mole fraction times 1e9)
   * @return concentration in the given unit
   * @throws IllegalArgumentException if the unit is not supported
   */
  public static double convertMoleFraction(double moleFraction, String unit) {
    if (!"ug/Sm3".equals(unit) && !"ug/Nm3".equals(unit) && !"ppb".equals(unit)) {
      throw new IllegalArgumentException("unit " + unit + " is not supported; use ug/Sm3, ug/Nm3 or ppb");
    }
    return MercuryPhaseEquilibrium.convertMercuryConcentration(moleFraction, MERCURY_MOLAR_MASS, 1.0, unit);
  }

  /**
   * Result of {@link ReservoirMercuryEstimator#estimate(SystemInterface)}.
   *
   * @author esol
   * @version 1.0
   */
  public static class Estimate implements Serializable {
    private static final long serialVersionUID = 1000L;

    /** Fluid holding mercury at the controlling limit. */
    private final SystemInterface limitFluid;
    /** Temperature [K]. */
    private final double temperature;
    /** Pressure [bara]. */
    private final double pressure;
    /** Mercury mole fraction at the sulfide limit. */
    private final double sulfideLimit;
    /** Mercury mole fraction at the liquid mercury limit. */
    private final double liquidLimit;
    /** Mercury fugacity at the sulfide limit [bar]. */
    private final double sulfideFugacity;
    /** Mercury fugacity at the liquid mercury limit [bar]. */
    private final double liquidFugacity;
    /** Fugacity coefficient of mercury at trace content in the first phase. */
    private final double mercuryFugacityCoefficient;
    /** Standard uncertainty of log10 of the sulfide limit from the thermochemical data. */
    private final double sulfideLimitLog10Uncertainty;
    /** Mercury mole fraction of the fluid as given, NaN if it held no mercury. */
    private final double measured;
    /** H2S fugacity used in the sulfide limit [bar]. */
    private final double h2sFugacity;
    /** H2S content used in the sulfide limit [ppm mol]. */
    private final double h2sPpm;
    /** Sulfide polymorph. */
    private final SulfidePhase sulfidePhase;
    /** H2S basis. */
    private final HydrogenSulfideBasis hydrogenSulfideBasis;
    /** Mercury sulfide activity. */
    private final double mercurySulfideActivity;
    /** Warnings. */
    private final List<String> warnings;

    /**
     * Creates an estimate.
     *
     * @param limitFluid fluid holding mercury at the controlling limit
     * @param temperature temperature [K]
     * @param pressure pressure [bara]
     * @param sulfideLimit mercury mole fraction at the sulfide limit
     * @param liquidLimit mercury mole fraction at the liquid mercury limit
     * @param sulfideFugacity mercury fugacity at the sulfide limit [bar]
     * @param liquidFugacity mercury fugacity at the liquid mercury limit [bar]
     * @param mercuryFugacityCoefficient fugacity coefficient of mercury at trace content in the first phase
     * @param sulfideLimitLog10Uncertainty standard uncertainty of log10 of the sulfide limit
     * @param measured mercury mole fraction of the fluid as given, NaN if none
     * @param h2sFugacity H2S fugacity used [bar]
     * @param h2sPpm H2S content used [ppm mol]
     * @param sulfidePhase sulfide polymorph
     * @param hydrogenSulfideBasis H2S basis
     * @param mercurySulfideActivity mercury sulfide activity
     * @param warnings warnings
     */
    Estimate(SystemInterface limitFluid, double temperature, double pressure, double sulfideLimit, double liquidLimit,
        double sulfideFugacity, double liquidFugacity, double mercuryFugacityCoefficient,
        double sulfideLimitLog10Uncertainty, double measured, double h2sFugacity, double h2sPpm,
        SulfidePhase sulfidePhase, HydrogenSulfideBasis hydrogenSulfideBasis, double mercurySulfideActivity,
        List<String> warnings) {
      this.limitFluid = limitFluid;
      this.temperature = temperature;
      this.pressure = pressure;
      this.sulfideLimit = sulfideLimit;
      this.liquidLimit = liquidLimit;
      this.sulfideFugacity = sulfideFugacity;
      this.liquidFugacity = liquidFugacity;
      this.mercuryFugacityCoefficient = mercuryFugacityCoefficient;
      this.sulfideLimitLog10Uncertainty = sulfideLimitLog10Uncertainty;
      this.measured = measured;
      this.h2sFugacity = h2sFugacity;
      this.h2sPpm = h2sPpm;
      this.sulfidePhase = sulfidePhase;
      this.hydrogenSulfideBasis = hydrogenSulfideBasis;
      this.mercurySulfideActivity = mercurySulfideActivity;
      this.warnings = Collections.unmodifiableList(warnings);
    }

    /**
     * Gets the mercury content of the whole reservoir fluid at the sulfide limit.
     *
     * @return mercury [mole fraction]
     */
    public double getSulfideLimitMoleFraction() {
      return sulfideLimit;
    }

    /**
     * Gets the mercury content of the whole reservoir fluid at the liquid mercury limit.
     *
     * @return mercury [mole fraction]
     */
    public double getLiquidLimitMoleFraction() {
      return liquidLimit;
    }

    /**
     * Gets the lower of the sulfide and liquid mercury limits.
     *
     * @return mercury [mole fraction]
     */
    public double getLimitMoleFraction() {
      return Math.min(sulfideLimit, liquidLimit);
    }

    /**
     * Gets the sulfide limit as a concentration.
     *
     * @param unit "ug/Sm3", "ug/Nm3" or "ppb", see
     * {@link ReservoirMercuryEstimator#convertMoleFraction(double, String)}
     * @return mercury at the sulfide limit, per standard volume of the whole fluid taken as ideal gas
     * @throws IllegalArgumentException if the unit is not supported
     */
    public double getSulfideLimit(String unit) {
      return convertMoleFraction(sulfideLimit, unit);
    }

    /**
     * Gets the liquid mercury limit as a concentration.
     *
     * @param unit "ug/Sm3", "ug/Nm3" or "ppb", see
     * {@link ReservoirMercuryEstimator#convertMoleFraction(double, String)}
     * @return mercury at the liquid mercury limit, per standard volume of the whole fluid taken as ideal gas
     * @throws IllegalArgumentException if the unit is not supported
     */
    public double getLiquidLimit(String unit) {
      return convertMoleFraction(liquidLimit, unit);
    }

    /**
     * Gets the lower of the two limits as a concentration.
     *
     * @param unit "ug/Sm3", "ug/Nm3" or "ppb", see
     * {@link ReservoirMercuryEstimator#convertMoleFraction(double, String)}
     * @return mercury at the controlling limit, per standard volume of the whole fluid taken as ideal gas
     * @throws IllegalArgumentException if the unit is not supported
     */
    public double getLimit(String unit) {
      return convertMoleFraction(getLimitMoleFraction(), unit);
    }

    /**
     * Names the condensed phase that sets the lower limit.
     *
     * @return "cinnabar", "metacinnabar" or "liquid mercury"
     */
    public String getControllingPhase() {
      if (liquidLimit < sulfideLimit) {
        return "liquid mercury";
      }
      return sulfidePhase == SulfidePhase.METACINNABAR ? "metacinnabar" : "cinnabar";
    }

    /**
     * Checks whether the fluid given to the estimator held mercury.
     *
     * @return true if a mercury content was given
     */
    public boolean hasMeasuredMercury() {
      return !Double.isNaN(measured);
    }

    /**
     * Gets the mercury content of the fluid given to the estimator.
     *
     * @return mercury [mole fraction], NaN if the fluid held no mercury
     */
    public double getMeasuredMoleFraction() {
      return measured;
    }

    /**
     * Gets the saturation index of the given mercury content relative to the sulfide limit, {@code
     * log10(content / limit)}. Negative values mean the sulfide cannot hold the mercury back (supply-limited fluid);
     * values near zero mean the fluid is at the sulfide limit; positive values mean supersaturation.
     *
     * @return saturation index, NaN if the fluid held no mercury
     */
    public double getSulfideSaturationIndex() {
      return Math.log10(measured / sulfideLimit);
    }

    /**
     * Gets the saturation index of the given mercury content relative to liquid mercury, {@code log10(content /
     * limit)}. A positive value means free liquid mercury at reservoir conditions.
     *
     * @return saturation index, NaN if the fluid held no mercury
     */
    public double getLiquidSaturationIndex() {
      return Math.log10(measured / liquidLimit);
    }

    /**
     * Gets the mercury sulfide activity at which the given mercury content would be at equilibrium, {@code a(HgS) x
     * content / limit}. For a fluid in equilibrium with mercury held in pyrite this is the HgS activity in the pyrite.
     *
     * @return apparent mercury sulfide activity, NaN if the fluid held no mercury
     */
    public double getApparentMercurySulfideActivity() {
      return mercurySulfideActivity * measured / sulfideLimit;
    }

    /**
     * Gets the mercury fugacity at the sulfide limit.
     *
     * @return fugacity [bar]
     */
    public double getSulfideMercuryFugacity() {
      return sulfideFugacity;
    }

    /**
     * Gets the mercury fugacity at the liquid mercury limit (vapour pressure with the pressure correction).
     *
     * @return fugacity [bar]
     */
    public double getLiquidMercuryFugacity() {
      return liquidFugacity;
    }

    /**
     * Gets the fugacity coefficient of mercury at trace content in the first fluid phase.
     *
     * @return fugacity coefficient
     */
    public double getMercuryFugacityCoefficient() {
      return mercuryFugacityCoefficient;
    }

    /**
     * Gets the standard uncertainty of log10 of the sulfide limit that follows from the enthalpy and entropy
     * uncertainties of the minerals (species without a quantified uncertainty are excluded).
     *
     * @return standard uncertainty in log10 units
     */
    public double getSulfideLimitLog10Uncertainty() {
      return sulfideLimitLog10Uncertainty;
    }

    /**
     * Gets the H2S fugacity used in the sulfide limit.
     *
     * @return fugacity [bar]
     */
    public double getH2SFugacity() {
      return h2sFugacity;
    }

    /**
     * Gets the H2S content used in the sulfide limit.
     *
     * @return H2S [ppm mol]
     */
    public double getH2SPpm() {
      return h2sPpm;
    }

    /**
     * Gets the sulfide polymorph used.
     *
     * @return polymorph
     */
    public SulfidePhase getSulfidePhase() {
      return sulfidePhase;
    }

    /**
     * Gets the H2S basis used.
     *
     * @return basis
     */
    public HydrogenSulfideBasis getHydrogenSulfideBasis() {
      return hydrogenSulfideBasis;
    }

    /**
     * Gets the mercury sulfide activity used.
     *
     * @return activity
     */
    public double getMercurySulfideActivity() {
      return mercurySulfideActivity;
    }

    /**
     * Gets the temperature of the estimate.
     *
     * @return temperature [K]
     */
    public double getTemperature() {
      return temperature;
    }

    /**
     * Gets the pressure of the estimate.
     *
     * @return pressure [bara]
     */
    public double getPressure() {
      return pressure;
    }

    /**
     * Gets a copy of the fluid holding mercury at the lower of the two limits, for flashing to separator conditions
     * with {@link MercuryPhaseEquilibrium}.
     *
     * @return fluid clone with mercury at the controlling limit
     */
    public SystemInterface getFluid() {
      return limitFluid.clone();
    }

    /**
     * Gets warnings about data range, stability and kinetics.
     *
     * @return unmodifiable warning list
     */
    public List<String> getWarnings() {
      return warnings;
    }
  }
}
