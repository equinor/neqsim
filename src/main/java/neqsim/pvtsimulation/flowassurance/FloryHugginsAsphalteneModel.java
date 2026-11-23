package neqsim.pvtsimulation.flowassurance;

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.characterization.PedersenAsphalteneCharacterization;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Flory-Huggins regular solution model for asphaltene precipitation prediction.
 *
 * <p>
 * This class implements the Flory-Huggins polymer solution theory applied to asphaltene precipitation, a widely-used
 * approach in the petroleum industry. The model treats the oil as a solvent and asphaltenes as a dissolved polymer,
 * using the mismatch in solubility parameters to predict precipitation.
 * </p>
 *
 * <h2>Theoretical Background</h2>
 * <p>
 * The chemical potential of asphaltene in oil is expressed as:
 * </p>
 *
 * <pre>
 * ln(x_a * gamma_a) = ln(phi_a) + (1 - V_a / V_L) * (1 - phi_a) + (V_a / RT) * (delta_a - delta_L) ^ 2 * (1 - phi_a)
 *     ^ 2
 * </pre>
 *
 * <p>
 * Where:
 * </p>
 * <ul>
 * <li>x_a = mole fraction of asphaltene</li>
 * <li>phi_a = volume fraction of asphaltene</li>
 * <li>V_a, V_L = molar volumes of asphaltene and liquid (cm3/mol)</li>
 * <li>delta_a, delta_L = solubility parameters of asphaltene and liquid (MPa^0.5)</li>
 * <li>R = gas constant, T = temperature (K)</li>
 * </ul>
 *
 * <p>
 * Onset occurs when delta_L drops below a critical value (typically when light ends evolve near bubble point).
 * </p>
 *
 * <h2>Options</h2>
 * <ul>
 * <li>Oil solubility parameter: from the density correlation (default) or directly from the equation of state as
 * delta_L = sqrt(-U_res / V) ({@link SolubilityParameterMethod#EOS}).</li>
 * <li>Calibration: {@link #calibrateToOnsetPressure(double, double)} fits delta_a to a measured onset pressure.
 * {@link #calibrateCorrelation(double)} is a heuristic prior that uses no measured data.</li>
 * <li>Live-oil basis (default on): the asphaltene weight fraction is the fraction of the whole fluid; below the bubble
 * point the concentration in the remaining oil rises as gas leaves.</li>
 * <li>Polydisperse asphaltene: {@link #setNumberOfAsphalteneFractions(int)} splits the asphaltene into a gamma
 * molecular-weight distribution; each fraction is compared with its own solubility limit.</li>
 * </ul>
 *
 * <h2>Key Correlations</h2>
 * <ul>
 * <li>Oil solubility parameter from density: delta_L = A * rho + B (Lian et al., 1994)</li>
 * <li>Asphaltene solubility parameter: 19-24 MPa^0.5 (Barton, 1991)</li>
 * <li>Onset condition: delta_L = delta_onset (fitted parameter)</li>
 * </ul>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>Hirschberg, A. et al. (1984). SPE J., 24(3), 283-293.</li>
 * <li>Flory, P.J. (1953). Principles of Polymer Chemistry. Cornell Univ. Press.</li>
 * <li>Huggins, M.L. (1941). J. Chem. Phys., 9, 440.</li>
 * <li>Lian, H., Lin, J.R., Yen, T.F. (1994). Fuel, 73(3), 423-428.</li>
 * <li>Andersen, S.I., Speight, J.G. (1999). J. Petrol. Sci. Eng., 22, 53-66.</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class FloryHugginsAsphalteneModel {

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(FloryHugginsAsphalteneModel.class);

  /** Gas constant in J/(mol K). */
  private static final double R_GAS = 8.314;

  /** Precipitated mass fraction above which the oil counts as unstable in the onset search. */
  private static final double ONSET_THRESHOLD = 1.0e-9;

  /** How the liquid solubility parameter is obtained. */
  public enum SolubilityParameterMethod {
    /** delta_L = A * rho + B from the oil-phase density (Lian et al., 1994). */
    DENSITY_CORRELATION,
    /** delta_L = sqrt(-U_res / V) from the equation of state (cohesive energy density of the oil phase). */
    EOS
  }

  /** Molar volume of asphaltene (cm3/mol). Default for MW=750, rho=1100 kg/m3. */
  private double asphaltMolarVolume = 682.0;

  /** Solubility parameter of asphaltene (MPa^0.5). Typical range: 19-24. */
  private double asphalteneSolubilityParameter = 21.0;

  /** Solubility parameter before the SARA adjustment; NaN when no SARA adjustment has been applied. */
  private double saraBaseSolubilityParameter = Double.NaN;

  /** Molecular weight of asphaltene (g/mol). */
  private double asphalteneMW = 750.0;

  /** Density of asphaltene at standard conditions (kg/m3). */
  private double asphalteneDensity = 1100.0;

  /** Weight fraction of asphaltene in the fluid (live-oil basis by default). */
  private double asphalteneWeightFraction = 0.05;

  /**
   * Coefficient A in delta_L = A * rho + B correlation. Default from Lian et al. (1994): 17.347e-3 MPa^0.5/(kg/m3).
   */
  private double deltaRhoCoeffA = 17.347e-3;

  /**
   * Coefficient B in delta_L = A * rho + B correlation. Default from Lian et al. (1994): 2.904 MPa^0.5.
   */
  private double deltaRhoCoeffB = 2.904;

  /** Pressure search step size for onset detection (bar). */
  private double pressureSearchStep = 2.0;

  /** Whether asphaltene properties were configured from API gravity. */
  private boolean configuredFromAPI = false;

  /** Reference to the thermodynamic system. */
  private SystemInterface system;

  /** Reservoir temperature (K). */
  private double reservoirTemperature;

  /** Liquid solubility parameter method. */
  private SolubilityParameterMethod solubilityParameterMethod = SolubilityParameterMethod.DENSITY_CORRELATION;

  /** Whether the asphaltene concentration follows the oil-phase mass below the bubble point. */
  private boolean liveOilBasis = true;

  /** Number of asphaltene fractions in the gamma split (1 = monodisperse). */
  private int numberOfAsphalteneFractions = 1;

  /** Gamma shape parameter for the polydisperse split. */
  private double distributionShape = 2.5;

  /**
   * Default constructor.
   */
  public FloryHugginsAsphalteneModel() {
  }

  /**
   * Constructor with thermodynamic system.
   *
   * @param system the thermodynamic system
   * @param reservoirTemperature reservoir temperature (K)
   */
  public FloryHugginsAsphalteneModel(SystemInterface system, double reservoirTemperature) {
    this.system = system.clone();
    this.reservoirTemperature = reservoirTemperature;
  }

  /**
   * Configures asphaltene properties based on API gravity.
   *
   * <p>
   * Uses correlations from Akbarzadeh et al. (2005) and Andersen and Speight (1999) to estimate asphaltene molecular
   * weight, density, and solubility parameter from API gravity. Any earlier SARA adjustment is discarded.
   * </p>
   *
   * <ul>
   * <li>Light oils (API &gt; 35): Lower MW (500-700), higher delta (21-23 MPa^0.5)</li>
   * <li>Medium oils (25-35): Medium MW (700-1200), moderate delta (20-21 MPa^0.5)</li>
   * <li>Heavy oils (API &lt; 25): Higher MW (1200-2500), lower delta (19-20 MPa^0.5)</li>
   * </ul>
   *
   * @param apiGravity API gravity of the crude oil
   */
  public void configureFromAPIGravity(double apiGravity) {
    // MW: exponential decrease with API gravity. Fitted to: API=20->2000, API=35->750, API=40->550
    asphalteneMW = Math.max(400.0, Math.min(3000.0, 7270.0 * Math.exp(-0.0645 * apiGravity)));
    // Solubility parameter: increases with API gravity (light paraffinic oils are poor asphaltene solvents).
    asphalteneSolubilityParameter = Math.max(19.5, Math.min(23.0, 19.0 + 0.06 * apiGravity));
    saraBaseSolubilityParameter = Double.NaN;
    asphalteneDensity = 1100.0;
    asphaltMolarVolume = (asphalteneMW / asphalteneDensity) * 1000.0; // cm3/mol
    configuredFromAPI = true;
    logger.info("Configured FH asphaltene for API={}: MW={}, delta={}, Vm={}", apiGravity, asphalteneMW,
        asphalteneSolubilityParameter, asphaltMolarVolume);
  }

  /**
   * Heuristic prior for the delta_L correlation coefficient A. <b>This uses no measured data.</b>
   *
   * <p>
   * The method flashes the fluid at its own pressure and the given temperature, finds the solubility-parameter gap
   * (delta_a - delta_L) at which the configured asphaltene content just reaches its solubility limit in that oil, and
   * sets A so that the actual gap is 0.35 MPa^0.5 smaller. The fluid is then just stable at its own conditions and
   * precipitates once depressurisation lowers delta_L by more than 0.35 MPa^0.5. The resulting onset follows from that
   * choice of margin; when a measured onset pressure is available use {@link #calibrateToOnsetPressure(double, double)}
   * instead.
   * </p>
   *
   * <p>
   * Earlier versions used the liquid-liquid critical point chi_c = r (1 + 1/sqrt(r))^2 / 2 as the reference. That is
   * not the onset condition of this model (solubility against a pure asphaltene phase), so the prior did not place the
   * fluid near its onset.
   * </p>
   *
   * @param temperature reservoir temperature (K). Used for flash and chi calculation.
   */
  public void calibrateCorrelation(double temperature) {
    if (system == null) {
      logger.warn("Cannot calibrate FH correlation: no thermodynamic system set");
      return;
    }
    if (solubilityParameterMethod == SolubilityParameterMethod.EOS) {
      logger.warn("calibrateCorrelation adjusts the density correlation and has no effect with the EOS method");
      return;
    }
    OilState state = oilState(system.getPressure(), temperature);
    if (state == null) {
      logger.warn("No liquid phase found during calibration");
      return;
    }
    if (state.density < 300.0 || state.density > 1200.0) {
      logger.warn("Oil density {} kg/m3 outside valid range for calibration", state.density);
      return;
    }
    double low = 0.0;
    double high = 15.0;
    state.deltaL = asphalteneSolubilityParameter - high;
    if (precipitatedFraction(state, temperature) <= 0.0) {
      logger.warn("No solubility-parameter gap up to 15 MPa^0.5 precipitates the asphaltene; keeping default A");
      return;
    }
    for (int i = 0; i < 100 && high - low > 1e-8; i++) {
      double mid = 0.5 * (low + high);
      state.deltaL = asphalteneSolubilityParameter - mid;
      if (precipitatedFraction(state, temperature) > 0.0) {
        high = mid;
      } else {
        low = mid;
      }
    }
    double onsetGap = 0.5 * (low + high);
    double stabilityBuffer = 0.35; // MPa^0.5
    double targetGap = onsetGap - stabilityBuffer;
    double calibratedA = (asphalteneSolubilityParameter - targetGap - deltaRhoCoeffB) / state.density;
    if (calibratedA > 0.005 && calibratedA < 0.10) {
      deltaRhoCoeffA = calibratedA;
      logger.info("FH heuristic prior: A={}, oilDensity={}, onsetGap={}, targetGap={}", deltaRhoCoeffA, state.density,
          onsetGap, targetGap);
    } else {
      logger.warn("Calibrated A={} outside valid range [0.005, 0.10], keeping default", calibratedA);
    }
  }

  /**
   * Calibrates the asphaltene solubility parameter so that precipitation starts exactly at a measured onset pressure.
   *
   * <p>
   * The liquid solubility parameter at the measured onset does not depend on delta_a, and the dissolved limit falls
   * monotonically as delta_a moves away from delta_L, so delta_a is found by bisection on [delta_L, delta_L + 15]. Any
   * earlier SARA adjustment is replaced by the calibrated value.
   * </p>
   *
   * @param temperature temperature of the measurement (K)
   * @param measuredOnsetPressure measured upper onset pressure (bara)
   * @return the calibrated asphaltene solubility parameter (MPa^0.5)
   * @throws IllegalStateException if no system is set, the flash fails, or no delta_a in the range reproduces the onset
   */
  public double calibrateToOnsetPressure(double temperature, double measuredOnsetPressure) {
    if (system == null) {
      throw new IllegalStateException("Set a thermodynamic system before calibrating to a measured onset");
    }
    if (!(temperature > 0.0) || !(measuredOnsetPressure > 0.0)) {
      throw new IllegalArgumentException("Temperature and onset pressure must be positive");
    }
    OilState state = oilState(measuredOnsetPressure, temperature);
    if (state == null) {
      throw new IllegalStateException("Flash failed at the measured onset pressure " + measuredOnsetPressure + " bara");
    }
    double low = state.deltaL;
    double high = state.deltaL + 15.0;
    double original = asphalteneSolubilityParameter;
    asphalteneSolubilityParameter = high;
    if (precipitatedFraction(state, temperature) <= 0.0) {
      asphalteneSolubilityParameter = original;
      throw new IllegalStateException(
          "No asphaltene solubility parameter up to delta_L + 15 MPa^0.5 gives precipitation at the measured onset");
    }
    for (int i = 0; i < 100 && high - low > 1e-8; i++) {
      double mid = 0.5 * (low + high);
      asphalteneSolubilityParameter = mid;
      if (precipitatedFraction(state, temperature) > 0.0) {
        high = mid;
      } else {
        low = mid;
      }
    }
    asphalteneSolubilityParameter = 0.5 * (low + high);
    saraBaseSolubilityParameter = Double.NaN;
    logger.info("FH calibrated to onset {} bara at {} K: delta_a={}", measuredOnsetPressure, temperature,
        asphalteneSolubilityParameter);
    return asphalteneSolubilityParameter;
  }

  /**
   * Configures asphaltene properties from SARA fractions.
   *
   * <p>
   * Sets the asphaltene weight fraction and scales the asphaltene solubility parameter for extreme resin/asphaltene
   * ratios and very saturated oils. The scaling is applied to the value that was in place before the first SARA call,
   * so calling this method more than once gives the same result as calling it once with the last arguments.
   * </p>
   *
   * @param saturates weight fraction of saturates
   * @param aromatics weight fraction of aromatics
   * @param resins weight fraction of resins
   * @param asphaltenes weight fraction of asphaltenes
   */
  public void configureFromSARA(double saturates, double aromatics, double resins, double asphaltenes) {
    this.asphalteneWeightFraction = asphaltenes;
    if (Double.isNaN(saraBaseSolubilityParameter)) {
      saraBaseSolubilityParameter = asphalteneSolubilityParameter;
    }
    double factor = 1.0;
    double raRatio = (asphaltenes > 0.001) ? resins / asphaltenes : 3.0;
    if (raRatio > 5.0) {
      factor *= 0.97;
    } else if (raRatio > 3.0) {
      factor *= 0.98;
    } else if (raRatio < 1.0) {
      factor *= 1.02;
    }
    double satFrac = saturates / (saturates + aromatics + 1e-10);
    if (satFrac > 0.75) {
      factor *= 1.01;
    }
    asphalteneSolubilityParameter = saraBaseSolubilityParameter * factor;
  }

  /**
   * Sets the pressure search step for onset detection.
   *
   * @param step pressure step (bar)
   */
  public void setPressureSearchStep(double step) {
    this.pressureSearchStep = step;
  }

  /**
   * Returns whether the model was configured from API gravity.
   *
   * @return true if configureFromAPIGravity was called
   */
  public boolean isConfiguredFromAPI() {
    return configuredFromAPI;
  }

  /**
   * Calculates the liquid solubility parameter from density (delta_L = A * rho + B).
   *
   * @param liquidDensity liquid phase density (kg/m3)
   * @return solubility parameter (MPa^0.5)
   */
  public double calculateLiquidSolubilityParameter(double liquidDensity) {
    return deltaRhoCoeffA * liquidDensity + deltaRhoCoeffB;
  }

  /**
   * Calculates the solubility parameter of an equation-of-state phase from its cohesive energy density.
   *
   * <p>
   * delta = sqrt(-U_res / V), with the residual internal energy U_res = H_res - (Z - 1) n R T at the phase temperature
   * and volume. The phase must be initialised (init(2) or a flash followed by initProperties()).
   * </p>
   *
   * @param phase an initialised equation-of-state phase
   * @return solubility parameter (MPa^0.5); NaN if the residual energy is not negative
   */
  public static double calculateEosSolubilityParameter(PhaseInterface phase) {
    double n = phase.getNumberOfMolesInPhase();
    double t = phase.getTemperature();
    double uRes = phase.getHresTP() - (phase.getZ() - 1.0) * n * R_GAS * t;
    double volume = phase.getVolume("m3");
    if (!(uRes < 0.0) || !(volume > 0.0)) {
      return Double.NaN;
    }
    return Math.sqrt(-uRes / volume) / 1.0e3;
  }

  /**
   * Calculates the liquid solubility parameter of a phase with the selected method.
   *
   * @param phase an initialised oil phase
   * @return solubility parameter (MPa^0.5)
   */
  public double calculateLiquidSolubilityParameter(PhaseInterface phase) {
    if (solubilityParameterMethod == SolubilityParameterMethod.EOS) {
      return calculateEosSolubilityParameter(phase);
    }
    return calculateLiquidSolubilityParameter(phase.getDensity("kg/m3"));
  }

  /**
   * Calculates the Flory-Huggins interaction parameter chi.
   *
   * @param deltaLiquid liquid solubility parameter (MPa^0.5)
   * @param temperature temperature (K)
   * @return chi parameter
   */
  public double calculateChiParameter(double deltaLiquid, double temperature) {
    return chi(asphaltMolarVolume, deltaLiquid, temperature);
  }

  /**
   * Interaction parameter for an asphaltene of the given molar volume.
   *
   * @param molarVolume asphaltene molar volume (cm3/mol)
   * @param deltaLiquid liquid solubility parameter (MPa^0.5)
   * @param temperature temperature (K)
   * @return chi
   */
  private double chi(double molarVolume, double deltaLiquid, double temperature) {
    double deltaDiffPa = (asphalteneSolubilityParameter - deltaLiquid) * 1e3; // MPa^0.5 to Pa^0.5
    return molarVolume * 1e-6 * deltaDiffPa * deltaDiffPa / (R_GAS * temperature);
  }

  /**
   * Calculates the maximum dissolved asphaltene volume fraction (the solubility limit against a pure asphaltene phase).
   *
   * <p>
   * Solves ln(phi) + (1 - r)(1 - phi) + chi (1 - phi)^2 = 0 for the non-trivial root, with r = V_a / V_L. The equation
   * is solved in u = ln(phi), which keeps very small solubilities accurate, and the trivial root phi = 1 is excluded
   * explicitly. When the equation has no root below phi = 1 the asphaltene is fully miscible and 1 is returned.
   * </p>
   *
   * @param deltaLiquid liquid solubility parameter (MPa^0.5)
   * @param liquidMolarVolume liquid molar volume (cm3/mol)
   * @param temperature temperature (K)
   * @return maximum dissolved asphaltene volume fraction (0 to 1)
   */
  public double calculateMaxDissolvedFraction(double deltaLiquid, double liquidMolarVolume, double temperature) {
    return solubilityLimit(chi(asphaltMolarVolume, deltaLiquid, temperature), asphaltMolarVolume / liquidMolarVolume);
  }

  /**
   * Solubility limit (volume fraction) for given chi and molar-volume ratio.
   *
   * @param chi interaction parameter
   * @param ratio asphaltene to liquid molar-volume ratio
   * @return dissolved volume fraction limit between 0 and 1
   */
  static double solubilityLimit(double chi, double ratio) {
    // g(u) = u + (1 - r)(1 - e^u) + chi (1 - e^u)^2, u = ln(phi) < 0. g -> -inf as u -> -inf and g ~ r u < 0 just
    // below 0, so a non-trivial root exists only if g has a positive maximum; the solubility is the lower root.
    double bestU = Double.NaN;
    double bestG = Double.NEGATIVE_INFINITY;
    for (int k = 0; k <= 400; k++) {
      double u = -Math.pow(10.0, -9.0 + 12.0 * k / 400.0);
      double value = gSolubility(u, chi, ratio);
      if (value > bestG) {
        bestG = value;
        bestU = u;
      }
    }
    if (!(bestG > 0.0)) {
      return 1.0;
    }
    double low = -1.0e3;
    double high = bestU;
    for (int i = 0; i < 200 && high - low > 1e-13 * Math.max(1.0, Math.abs(low)); i++) {
      double mid = 0.5 * (low + high);
      if (gSolubility(mid, chi, ratio) > 0.0) {
        high = mid;
      } else {
        low = mid;
      }
    }
    return Math.exp(0.5 * (low + high));
  }

  /**
   * Residual of the Flory-Huggins solubility equation in u = ln(phi).
   *
   * @param u natural log of the dissolved volume fraction
   * @param chi interaction parameter
   * @param ratio molar-volume ratio
   * @return residual
   */
  private static double gSolubility(double u, double chi, double ratio) {
    double oneMinusPhi = -Math.expm1(u);
    return u + (1.0 - ratio) * oneMinusPhi + chi * oneMinusPhi * oneMinusPhi;
  }

  /** Oil-phase state at one pressure and temperature. */
  private static final class OilState {
    double density;
    double molarVolume;
    double deltaL;
    double massRatio;
  }

  /**
   * Flashes a working copy and returns the liquid (oil) phase, or null.
   *
   * @param work working fluid at the requested conditions
   * @return oil phase or null
   */
  private static PhaseInterface flashOilPhase(SystemInterface work) {
    try {
      new ThermodynamicOperations(work).TPflash();
      work.initProperties();
    } catch (Exception e) {
      logger.error("Flash failed at P={}, T={}: {}", work.getPressure(), work.getTemperature(), e.getMessage());
      return null;
    }
    if (work.hasPhaseType("oil")) {
      return work.getPhase("oil");
    }
    return work.getNumberOfPhases() > 0 ? work.getPhase(0) : null;
  }

  /**
   * Flashes the fluid and collects the oil-phase properties the model needs.
   *
   * @param pressure pressure (bara)
   * @param temperature temperature (K)
   * @return oil state, or null if the flash failed
   */
  private OilState oilState(double pressure, double temperature) {
    SystemInterface work = system.clone();
    work.setPressure(pressure);
    work.setTemperature(temperature);
    PhaseInterface oil = flashOilPhase(work);
    if (oil == null) {
      return null;
    }
    OilState state = new OilState();
    state.density = oil.getDensity("kg/m3");
    state.molarVolume = oil.getMolarMass() * 1000.0 / (state.density / 1000.0);
    state.deltaL = calculateLiquidSolubilityParameter(oil);
    double totalMass = work.getTotalNumberOfMoles() * work.getMolarMass();
    double oilMass = oil.getNumberOfMolesInPhase() * oil.getMolarMass();
    state.massRatio = oilMass > 0.0 ? totalMass / oilMass : 1.0;
    return state;
  }

  /**
   * Precipitated asphaltene mass fraction (of the oil) for a given oil state.
   *
   * @param state oil state
   * @param temperature temperature (K)
   * @return precipitated mass fraction
   */
  private double precipitatedFraction(OilState state, double temperature) {
    if (Double.isNaN(state.deltaL)) {
      return Double.NaN;
    }
    double total = asphalteneWeightFraction * (liveOilBasis ? state.massRatio : 1.0);
    total = Math.min(total, 1.0);
    double[] mw;
    double[] massShare;
    if (numberOfAsphalteneFractions <= 1) {
      mw = new double[] {asphalteneMW};
      massShare = new double[] {1.0};
    } else {
      double[][] split = PedersenAsphalteneCharacterization.gammaSplit(asphalteneMW, 0.5 * asphalteneMW,
          distributionShape, numberOfAsphalteneFractions);
      mw = split[0];
      massShare = new double[mw.length];
      for (int i = 0; i < mw.length; i++) {
        massShare[i] = split[1][i] * mw[i] / asphalteneMW;
      }
    }
    double precipitated = 0.0;
    for (int i = 0; i < mw.length; i++) {
      double molarVolume = numberOfAsphalteneFractions <= 1 ? asphaltMolarVolume : mw[i] / asphalteneDensity * 1000.0;
      double phi = solubilityLimit(chi(molarVolume, state.deltaL, temperature), molarVolume / state.molarVolume);
      double maxMass = phi * asphalteneDensity / (phi * asphalteneDensity + (1.0 - phi) * state.density);
      double present = total * massShare[i];
      if (present > maxMass) {
        precipitated += present - maxMass;
      }
    }
    return precipitated;
  }

  /**
   * Calculates the precipitated asphaltene mass fraction at given conditions.
   *
   * <p>
   * The asphaltene present in the oil is the configured weight fraction; with the live-oil basis (default) it is scaled
   * by total mass / oil-phase mass, so below the bubble point it rises as gas leaves the oil. Each asphaltene fraction
   * precipitates the amount above its own solubility limit.
   * </p>
   *
   * @param pressure pressure (bara)
   * @param temperature temperature (K)
   * @return mass fraction of the oil that precipitates as asphaltene (0 = none); NaN if no system is set or the flash
   * fails
   */
  public double calculatePrecipitatedFraction(double pressure, double temperature) {
    if (system == null) {
      logger.error("Thermodynamic system not set");
      return Double.NaN;
    }
    OilState state = oilState(pressure, temperature);
    if (state == null) {
      return Double.NaN;
    }
    return precipitatedFraction(state, temperature);
  }

  /**
   * Calculates the upper asphaltene onset pressure.
   *
   * <p>
   * Scans from the fluid's own pressure down to 1 bara in steps of {@link #setPressureSearchStep(double)} and bisects
   * the first interval where any asphaltene precipitates. Onsets within two steps of the minimum pressure are treated
   * as no onset.
   * </p>
   *
   * @param temperature temperature (K)
   * @return onset pressure (bara), or NaN if no onset is found
   */
  public double calculateOnsetPressure(double temperature) {
    if (system == null) {
      logger.error("Thermodynamic system not set");
      return Double.NaN;
    }
    double startP = system.getPressure();
    double minP = 1.0;
    double previousP = startP;
    double onsetP = Double.NaN;
    for (double p = startP; p >= minP; p -= pressureSearchStep) {
      double precip = calculatePrecipitatedFraction(p, temperature);
      if (Double.isNaN(precip)) {
        continue;
      }
      if (precip > ONSET_THRESHOLD) {
        if (p >= startP) {
          logger.info("FH: asphaltene already unstable at the start pressure {} bara", startP);
          return startP;
        }
        double high = previousP;
        double low = p;
        for (int i = 0; i < 30 && high - low > 1e-3; i++) {
          double mid = (high + low) / 2.0;
          double midPrecip = calculatePrecipitatedFraction(mid, temperature);
          if (midPrecip > ONSET_THRESHOLD) {
            low = mid;
          } else {
            high = mid;
          }
        }
        onsetP = (high + low) / 2.0;
        break;
      }
      previousP = p;
    }
    if (!Double.isNaN(onsetP) && onsetP < minP + 2 * pressureSearchStep) {
      logger.info("FH onset at {} bar is near minimum search pressure - treating as no onset", onsetP);
      return Double.NaN;
    }
    return onsetP;
  }

  /**
   * Generates a precipitation curve (wt% precipitated vs pressure).
   *
   * @param temperature temperature (K)
   * @param maxPressure maximum pressure (bara)
   * @param minPressure minimum pressure (bara)
   * @param numPoints number of points
   * @return array {pressures, precipitated wt%}
   */
  public double[][] generatePrecipitationCurve(double temperature, double maxPressure, double minPressure,
      int numPoints) {
    double[][] results = new double[2][numPoints];
    double step = (maxPressure - minPressure) / (numPoints - 1);
    for (int i = 0; i < numPoints; i++) {
      double p = maxPressure - i * step;
      results[0][i] = p;
      double precip = calculatePrecipitatedFraction(p, temperature);
      results[1][i] = Double.isNaN(precip) ? 0.0 : precip * 100.0;
    }
    return results;
  }

  /**
   * Generates the liquid solubility parameter profile vs pressure.
   *
   * @param temperature temperature (K)
   * @param maxPressure maximum pressure (bara)
   * @param minPressure minimum pressure (bara)
   * @param numPoints number of points
   * @return array {pressures, delta_L, delta_a}
   */
  public double[][] generateSolubilityParameterProfile(double temperature, double maxPressure, double minPressure,
      int numPoints) {
    double[][] results = new double[3][numPoints];
    double step = (maxPressure - minPressure) / (numPoints - 1);
    for (int i = 0; i < numPoints; i++) {
      double p = maxPressure - i * step;
      results[0][i] = p;
      results[2][i] = asphalteneSolubilityParameter;
      if (system != null) {
        OilState state = oilState(p, temperature);
        results[1][i] = state == null ? Double.NaN : state.deltaL;
      }
    }
    return results;
  }

  /**
   * Returns a summary map of model parameters and the onset pressure.
   *
   * @param temperature temperature (K)
   * @param maxPressure maximum pressure (bara), not used by the onset search
   * @param minPressure minimum pressure (bara), not used by the onset search
   * @return results map
   */
  public Map<String, Object> getResultsMap(double temperature, double maxPressure, double minPressure) {
    Map<String, Object> results = new LinkedHashMap<String, Object>();
    results.put("model", "Flory-Huggins Regular Solution");
    results.put("asphaltene_MW_gmol", asphalteneMW);
    results.put("asphaltene_density_kgm3", asphalteneDensity);
    results.put("asphaltene_solubility_parameter_MPa05", asphalteneSolubilityParameter);
    results.put("asphaltene_molar_volume_cm3mol", asphaltMolarVolume);
    results.put("asphaltene_weight_fraction", asphalteneWeightFraction);
    results.put("solubility_parameter_method", solubilityParameterMethod.name());
    results.put("live_oil_basis", liveOilBasis);
    results.put("asphaltene_fractions", numberOfAsphalteneFractions);
    results.put("temperature_K", temperature);
    results.put("onset_pressure_bara", calculateOnsetPressure(temperature));
    return results;
  }

  /**
   * Gets the asphaltene molar volume.
   *
   * @return molar volume (cm3/mol)
   */
  public double getAsphaltMolarVolume() {
    return asphaltMolarVolume;
  }

  /**
   * Sets the asphaltene molar volume.
   *
   * @param asphaltMolarVolume molar volume (cm3/mol)
   */
  public void setAsphaltMolarVolume(double asphaltMolarVolume) {
    this.asphaltMolarVolume = asphaltMolarVolume;
  }

  /**
   * Gets the asphaltene solubility parameter.
   *
   * @return solubility parameter (MPa^0.5)
   */
  public double getAsphalteneSolubilityParameter() {
    return asphalteneSolubilityParameter;
  }

  /**
   * Sets the asphaltene solubility parameter. Any earlier SARA adjustment is discarded.
   *
   * @param asphalteneSolubilityParameter solubility parameter (MPa^0.5)
   */
  public void setAsphalteneSolubilityParameter(double asphalteneSolubilityParameter) {
    this.asphalteneSolubilityParameter = asphalteneSolubilityParameter;
    this.saraBaseSolubilityParameter = Double.NaN;
  }

  /**
   * Gets the asphaltene molecular weight.
   *
   * @return molecular weight (g/mol)
   */
  public double getAsphalteneMW() {
    return asphalteneMW;
  }

  /**
   * Sets the asphaltene molecular weight and updates the molar volume.
   *
   * @param asphalteneMW molecular weight (g/mol)
   */
  public void setAsphalteneMW(double asphalteneMW) {
    this.asphalteneMW = asphalteneMW;
    this.asphaltMolarVolume = asphalteneMW / (asphalteneDensity / 1000.0);
  }

  /**
   * Gets the asphaltene density.
   *
   * @return density (kg/m3)
   */
  public double getAsphalteneDensity() {
    return asphalteneDensity;
  }

  /**
   * Sets the asphaltene density and updates the molar volume.
   *
   * @param asphalteneDensity density (kg/m3)
   */
  public void setAsphalteneDensity(double asphalteneDensity) {
    this.asphalteneDensity = asphalteneDensity;
    this.asphaltMolarVolume = asphalteneMW / (asphalteneDensity / 1000.0);
  }

  /**
   * Gets the asphaltene weight fraction.
   *
   * @return weight fraction
   */
  public double getAsphalteneWeightFraction() {
    return asphalteneWeightFraction;
  }

  /**
   * Sets the asphaltene weight fraction of the fluid.
   *
   * @param asphalteneWeightFraction weight fraction
   */
  public void setAsphalteneWeightFraction(double asphalteneWeightFraction) {
    this.asphalteneWeightFraction = asphalteneWeightFraction;
  }

  /**
   * Sets custom coefficients for the delta_L = A * rho + B correlation.
   *
   * @param coeffA coefficient A (MPa^0.5 per kg/m3)
   * @param coeffB coefficient B (MPa^0.5)
   */
  public void setDeltaRhoCorrelation(double coeffA, double coeffB) {
    this.deltaRhoCoeffA = coeffA;
    this.deltaRhoCoeffB = coeffB;
  }

  /**
   * Gets the thermodynamic system.
   *
   * @return the system
   */
  public SystemInterface getSystem() {
    return system;
  }

  /**
   * Sets the thermodynamic system (a clone is stored).
   *
   * @param system the system
   */
  public void setSystem(SystemInterface system) {
    this.system = system.clone();
  }

  /**
   * Gets the reservoir temperature.
   *
   * @return temperature (K)
   */
  public double getReservoirTemperature() {
    return reservoirTemperature;
  }

  /**
   * Sets the reservoir temperature.
   *
   * @param reservoirTemperature temperature (K)
   */
  public void setReservoirTemperature(double reservoirTemperature) {
    this.reservoirTemperature = reservoirTemperature;
  }

  /**
   * Gets the liquid solubility parameter method.
   *
   * @return the method
   */
  public SolubilityParameterMethod getSolubilityParameterMethod() {
    return solubilityParameterMethod;
  }

  /**
   * Sets how the liquid solubility parameter is obtained (default
   * {@link SolubilityParameterMethod#DENSITY_CORRELATION}).
   *
   * @param method the method
   */
  public void setSolubilityParameterMethod(SolubilityParameterMethod method) {
    if (method == null) {
      throw new IllegalArgumentException("Solubility parameter method must not be null");
    }
    this.solubilityParameterMethod = method;
  }

  /**
   * Returns whether the live-oil concentration basis is used.
   *
   * @return true if the asphaltene concentration follows the oil-phase mass
   */
  public boolean isLiveOilBasis() {
    return liveOilBasis;
  }

  /**
   * Sets whether the asphaltene concentration follows the oil-phase mass (default true). With false the configured
   * weight fraction is used unchanged at every pressure, as in earlier versions.
   *
   * @param liveOilBasis true for the live-oil basis
   */
  public void setLiveOilBasis(boolean liveOilBasis) {
    this.liveOilBasis = liveOilBasis;
  }

  /**
   * Gets the number of asphaltene fractions in the gamma split.
   *
   * @return number of fractions (1 = monodisperse)
   */
  public int getNumberOfAsphalteneFractions() {
    return numberOfAsphalteneFractions;
  }

  /**
   * Splits the asphaltene into a gamma molecular-weight distribution (mean = asphaltene MW, minimum = half of it,
   * equal-mole slices). Each fraction has molar volume MW_i / rho_a and is compared with its own solubility limit; the
   * fractions do not interact with each other (a simplification of the full multicomponent Flory-Huggins model).
   *
   * @param numberOfFractions number of fractions (1 restores the monodisperse model, maximum 20)
   */
  public void setNumberOfAsphalteneFractions(int numberOfFractions) {
    if (numberOfFractions < 1 || numberOfFractions > 20) {
      throw new IllegalArgumentException("Number of asphaltene fractions must be between 1 and 20");
    }
    this.numberOfAsphalteneFractions = numberOfFractions;
  }

  /**
   * Gets the gamma shape parameter of the polydisperse split.
   *
   * @return shape parameter
   */
  public double getDistributionShape() {
    return distributionShape;
  }

  /**
   * Sets the gamma shape parameter of the polydisperse split (default 2.5).
   *
   * @param shape shape parameter, must be positive
   */
  public void setDistributionShape(double shape) {
    if (!(shape > 0.0)) {
      throw new IllegalArgumentException("Distribution shape must be positive");
    }
    this.distributionShape = shape;
  }
}
