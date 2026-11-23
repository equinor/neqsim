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

  /** Reference temperature (K) for the asphaltene solubility parameter. */
  private double solubilityParameterReferenceTemperature = 298.15;

  /** Temperature coefficient of the asphaltene solubility parameter (MPa^0.5/K). */
  private double solubilityParameterTemperatureCoefficient;

  /** Precipitated weight fraction that defines the onset detection limit. */
  private double onsetDetectionThreshold = 0.001;

  /** Whether the asphaltene solubility parameter was calibrated to measured onset data. */
  private boolean calibratedToOnsetData;

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
    solubilityParameterTemperatureCoefficient = 0.0;
    solubilityParameterReferenceTemperature = 298.15;
    calibratedToOnsetData = false;
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
    double deltaAsphaltene = getAsphalteneSolubilityParameter(temperature);
    state.deltaL = deltaAsphaltene - high;
    if (precipitatedFraction(state, temperature) <= 0.0) {
      logger.warn("No solubility-parameter gap up to 15 MPa^0.5 precipitates the asphaltene; keeping default A");
      return;
    }
    for (int i = 0; i < 100 && high - low > 1e-8; i++) {
      double mid = 0.5 * (low + high);
      state.deltaL = deltaAsphaltene - mid;
      if (precipitatedFraction(state, temperature) > 0.0) {
        high = mid;
      } else {
        low = mid;
      }
    }
    double onsetGap = 0.5 * (low + high);
    double stabilityBuffer = 0.35; // MPa^0.5
    double targetGap = onsetGap - stabilityBuffer;
    double calibratedA = (deltaAsphaltene - targetGap - deltaRhoCoeffB) / state.density;
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
    if (!(temperature > 0.0) || Double.isInfinite(temperature) || !(measuredOnsetPressure > 0.0)
        || Double.isInfinite(measuredOnsetPressure)) {
      throw new IllegalArgumentException("Temperature and onset pressure must be positive");
    }
    OilState state = oilState(measuredOnsetPressure, temperature);
    if (state == null) {
      throw new IllegalStateException("Flash failed at the measured onset pressure " + measuredOnsetPressure + " bara");
    }
    double calibratedParameter = calculateRequiredSolubilityParameter(temperature, measuredOnsetPressure);
    if (Double.isNaN(calibratedParameter)) {
      throw new IllegalArgumentException(
          "Onset at " + measuredOnsetPressure + " bara and " + temperature + " K cannot be reproduced by the model");
    }
    asphalteneSolubilityParameter = calibratedParameter;
    solubilityParameterReferenceTemperature = temperature;
    saraBaseSolubilityParameter = Double.NaN;
    calibratedToOnsetData = true;
    logger.info("FH calibrated to onset {} bara at {} K: delta_a={}", measuredOnsetPressure, temperature,
        asphalteneSolubilityParameter);
    return asphalteneSolubilityParameter;
  }

  /**
   * Calculates the asphaltene solubility parameter that reproduces one measured onset pressure.
   *
   * @param temperature temperature of the measured onset (K)
   * @param onsetPressure measured upper onset pressure (bara)
   * @return required asphaltene solubility parameter (MPa^0.5), or NaN if the condition cannot be reproduced
   * @throws IllegalStateException if no thermodynamic system is set or asphaltene content does not exceed the onset
   * threshold
   */
  public double calculateRequiredSolubilityParameter(double temperature, double onsetPressure) {
    if (system == null) {
      throw new IllegalStateException("Thermodynamic system not set");
    }
    if (!(temperature > 0.0) || Double.isInfinite(temperature) || !(onsetPressure > 0.0)
        || Double.isInfinite(onsetPressure)) {
      throw new IllegalArgumentException("Temperature and onset pressure must be positive");
    }
    if (!(asphalteneWeightFraction > onsetDetectionThreshold)) {
      throw new IllegalStateException("Asphaltene weight fraction must exceed the onset detection threshold");
    }
    OilState state = oilState(onsetPressure, temperature);
    if (state == null || Double.isNaN(state.deltaL)) {
      return Double.NaN;
    }
    double low = state.deltaL;
    double high = state.deltaL + 15.0;
    if (precipitatedFraction(state, temperature, high) <= onsetDetectionThreshold) {
      return Double.NaN;
    }
    for (int i = 0; i < 100 && high - low > 1.0e-8; i++) {
      double mid = 0.5 * (low + high);
      if (precipitatedFraction(state, temperature, mid) > onsetDetectionThreshold) {
        high = mid;
      } else {
        low = mid;
      }
    }
    return 0.5 * (low + high);
  }

  /**
   * Regresses the asphaltene solubility parameter and its temperature coefficient to onset measurements.
   *
   * @param temperatures temperatures of measured onsets (K)
   * @param onsetPressures measured upper onset pressures (bara)
   * @return fit result containing fitted parameters and pressure deviations
   * @throws IllegalArgumentException if arrays are null, empty, unequal in length, or a point cannot be reproduced
   * @throws IllegalStateException if the system is not set or the asphaltene weight fraction is below threshold
   */
  public OnsetFitResult fitToOnsetData(double[] temperatures, double[] onsetPressures) {
    if (temperatures == null || onsetPressures == null || temperatures.length == 0
        || temperatures.length != onsetPressures.length) {
      throw new IllegalArgumentException("temperatures and onsetPressures must be non-empty and of equal length");
    }
    double[] required = new double[temperatures.length];
    double meanTemperature = 0.0;
    double meanParameter = 0.0;
    double maximumPressure = 0.0;
    for (int i = 0; i < temperatures.length; i++) {
      required[i] = calculateRequiredSolubilityParameter(temperatures[i], onsetPressures[i]);
      if (Double.isNaN(required[i])) {
        throw new IllegalArgumentException(
            "Onset at " + onsetPressures[i] + " bara and " + temperatures[i] + " K cannot be reproduced by the model");
      }
      meanTemperature += temperatures[i] / temperatures.length;
      meanParameter += required[i] / temperatures.length;
      maximumPressure = Math.max(maximumPressure, onsetPressures[i]);
    }
    double sumTemperatureSquares = 0.0;
    double sumTemperatureParameter = 0.0;
    for (int i = 0; i < temperatures.length; i++) {
      double temperatureDelta = temperatures[i] - meanTemperature;
      sumTemperatureSquares += temperatureDelta * temperatureDelta;
      sumTemperatureParameter += temperatureDelta * (required[i] - meanParameter);
    }
    if (sumTemperatureSquares > 1.0e-8) {
      solubilityParameterTemperatureCoefficient = sumTemperatureParameter / sumTemperatureSquares;
    }
    asphalteneSolubilityParameter = meanParameter;
    solubilityParameterReferenceTemperature = meanTemperature;
    saraBaseSolubilityParameter = Double.NaN;
    calibratedToOnsetData = true;
    double startPressure = Math.max(system.getPressure(), 1.25 * maximumPressure);
    double[] predicted = new double[temperatures.length];
    for (int i = 0; i < temperatures.length; i++) {
      predicted[i] = calculateOnsetPressure(temperatures[i], startPressure, 1.0);
    }
    return new OnsetFitResult(asphalteneSolubilityParameter, meanTemperature, solubilityParameterTemperatureCoefficient,
        temperatures, onsetPressures, required, predicted);
  }

  /**
   * Immutable result of fitting the Flory-Huggins model to onset-pressure measurements.
   *
   * @author ESOL
   * @version 1.0
   */
  public static class OnsetFitResult implements java.io.Serializable {
    private static final long serialVersionUID = 1000L;
    private final double solubilityParameter;
    private final double referenceTemperature;
    private final double temperatureCoefficient;
    private final double[] temperatures;
    private final double[] measuredOnsetPressures;
    private final double[] requiredSolubilityParameters;
    private final double[] predictedOnsetPressures;

    /**
     * Creates a fit result with defensive copies of all arrays.
     *
     * @param solubilityParameter fitted parameter at the reference temperature (MPa^0.5)
     * @param referenceTemperature reference temperature (K)
     * @param temperatureCoefficient temperature coefficient (MPa^0.5/K)
     * @param temperatures measurement temperatures (K)
     * @param measuredOnsetPressures measured onset pressures (bara)
     * @param requiredSolubilityParameters parameters required to reproduce each measurement (MPa^0.5)
     * @param predictedOnsetPressures predicted onset pressures (bara)
     */
    OnsetFitResult(double solubilityParameter, double referenceTemperature, double temperatureCoefficient,
        double[] temperatures, double[] measuredOnsetPressures, double[] requiredSolubilityParameters,
        double[] predictedOnsetPressures) {
      this.solubilityParameter = solubilityParameter;
      this.referenceTemperature = referenceTemperature;
      this.temperatureCoefficient = temperatureCoefficient;
      this.temperatures = temperatures.clone();
      this.measuredOnsetPressures = measuredOnsetPressures.clone();
      this.requiredSolubilityParameters = requiredSolubilityParameters.clone();
      this.predictedOnsetPressures = predictedOnsetPressures.clone();
    }

    /**
     * Gets the fitted solubility parameter at the reference temperature.
     *
     * @return solubility parameter (MPa^0.5)
     */
    public double getSolubilityParameter() {
      return solubilityParameter;
    }

    /**
     * Gets the reference temperature.
     *
     * @return reference temperature (K)
     */
    public double getReferenceTemperature() {
      return referenceTemperature;
    }

    /**
     * Gets the fitted temperature coefficient.
     *
     * @return temperature coefficient (MPa^0.5/K)
     */
    public double getTemperatureCoefficient() {
      return temperatureCoefficient;
    }

    /**
     * Gets the measured temperatures.
     *
     * @return measurement temperatures (K)
     */
    public double[] getTemperatures() {
      return temperatures.clone();
    }

    /**
     * Gets the measured onset pressures.
     *
     * @return onset pressures (bara)
     */
    public double[] getMeasuredOnsetPressures() {
      return measuredOnsetPressures.clone();
    }

    /**
     * Gets the parameters required to reproduce each measurement.
     *
     * @return required parameters (MPa^0.5)
     */
    public double[] getRequiredSolubilityParameters() {
      return requiredSolubilityParameters.clone();
    }

    /**
     * Gets onset pressures recalculated with the fitted parameters.
     *
     * @return predicted onset pressures (bara)
     */
    public double[] getPredictedOnsetPressures() {
      return predictedOnsetPressures.clone();
    }

    /**
     * Calculates the mean absolute deviation between predictions and measurements.
     *
     * @return average absolute deviation (bar), NaN if any prediction is NaN
     */
    public double getAverageAbsoluteDeviation() {
      double sum = 0.0;
      for (int i = 0; i < measuredOnsetPressures.length; i++) {
        double deviation = Math.abs(predictedOnsetPressures[i] - measuredOnsetPressures[i]);
        if (Double.isNaN(deviation)) {
          return Double.NaN;
        }
        sum += deviation;
      }
      return sum / measuredOnsetPressures.length;
    }

    /**
     * Calculates the maximum absolute deviation between predictions and measurements.
     *
     * @return maximum absolute deviation (bar), NaN if any prediction is NaN
     */
    public double getMaximumAbsoluteDeviation() {
      double maximum = 0.0;
      for (int i = 0; i < measuredOnsetPressures.length; i++) {
        double deviation = Math.abs(predictedOnsetPressures[i] - measuredOnsetPressures[i]);
        if (Double.isNaN(deviation)) {
          return Double.NaN;
        }
        maximum = Math.max(maximum, deviation);
      }
      return maximum;
    }
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
    calibratedToOnsetData = false;
  }

  /**
   * Sets the pressure search step for onset detection.
   *
   * @param step pressure step (bar)
   */
  public void setPressureSearchStep(double step) {
    if (!(step > 0.0) || Double.isInfinite(step)) {
      throw new IllegalArgumentException("Pressure search step must be positive and finite");
    }
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
    return chi(molarVolume, deltaLiquid, temperature, getAsphalteneSolubilityParameter(temperature));
  }

  /**
   * Calculates the Flory-Huggins interaction parameter for a specified asphaltene solubility parameter.
   *
   * @param molarVolume asphaltene molar volume (cm3/mol)
   * @param deltaLiquid liquid solubility parameter (MPa^0.5)
   * @param temperature temperature (K)
   * @param deltaAsphaltene asphaltene solubility parameter at the given temperature (MPa^0.5)
   * @return chi interaction parameter
   */
  private double chi(double molarVolume, double deltaLiquid, double temperature, double deltaAsphaltene) {
    double deltaDiffPa = (deltaAsphaltene - deltaLiquid) * 1e3; // MPa^0.5 to Pa^0.5
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
    if (!(chi > 0.0) || !(ratio > 0.0) || Double.isInfinite(chi) || Double.isInfinite(ratio)) {
      return 1.0;
    }
    // The stationary points satisfy 2*chi*phi^2 + (ratio - 1 - 2*chi)*phi + 1 = 0.
    // Evaluating these points analytically avoids missing a narrow positive maximum near criticality.
    double a = 2.0 * chi;
    double b = ratio - 1.0 - 2.0 * chi;
    double discriminant = b * b - 4.0 * a;
    if (!(discriminant >= 0.0)) {
      return 1.0;
    }
    double sqrtDiscriminant = Math.sqrt(discriminant);
    double q = -0.5 * (b + Math.copySign(sqrtDiscriminant, b));
    if (q == 0.0) {
      return 1.0;
    }
    double[] stationaryPoints = {q / a, 1.0 / q};
    double bestPhi = Double.NaN;
    double bestG = Double.NEGATIVE_INFINITY;
    for (int i = 0; i < stationaryPoints.length; i++) {
      double phi = stationaryPoints[i];
      if (phi > 0.0 && phi < 1.0) {
        double oneMinusPhi = 1.0 - phi;
        double value = Math.log(phi) + (1.0 - ratio) * oneMinusPhi + chi * oneMinusPhi * oneMinusPhi;
        if (value > bestG) {
          bestG = value;
          bestPhi = phi;
        }
      }
    }
    if (!(bestG > 0.0)) {
      return 1.0;
    }
    double low = -1.0e3;
    double high = Math.log(bestPhi);
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
    return precipitatedFraction(state, temperature, getAsphalteneSolubilityParameter(temperature));
  }

  /**
   * Calculates precipitated mass fraction for a candidate asphaltene solubility parameter.
   *
   * @param state oil-phase state
   * @param temperature temperature (K)
   * @param deltaAsphaltene asphaltene solubility parameter (MPa^0.5)
   * @return precipitated mass fraction
   */
  private double precipitatedFraction(OilState state, double temperature, double deltaAsphaltene) {
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
      double phi = solubilityLimit(chi(molarVolume, state.deltaL, temperature, deltaAsphaltene),
          molarVolume / state.molarVolume);
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
   * Scans from the fluid's pressure down to 1 bara and bisects the first interval where the precipitated fraction
   * exceeds the configured onset threshold.
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
    return calculateOnsetPressure(temperature, system.getPressure(), 1.0);
  }

  /**
   * Calculates the upper onset pressure in a requested pressure window.
   *
   * @param temperature temperature (K)
   * @param startPressure highest pressure to scan (bara)
   * @param minPressure lowest pressure to scan (bara)
   * @return onset pressure (bara), or NaN if no onset is found
   * @throws IllegalArgumentException if pressures are non-positive or the start pressure is below the minimum
   */
  public double calculateOnsetPressure(double temperature, double startPressure, double minPressure) {
    if (system == null) {
      logger.error("Thermodynamic system not set");
      return Double.NaN;
    }
    if (!(startPressure > 0.0) || !(minPressure > 0.0) || startPressure < minPressure) {
      throw new IllegalArgumentException("Pressure window must be positive and startPressure must be >= minPressure");
    }
    double previousPressure = startPressure;
    boolean previousWasStable = false;
    for (double pressure = startPressure; pressure >= minPressure;) {
      double precipitated = calculatePrecipitatedFraction(pressure, temperature);
      if (Double.isNaN(precipitated)) {
        previousWasStable = false;
      } else if (precipitated > onsetDetectionThreshold) {
        if (!previousWasStable) {
          return pressure;
        }
        return bisectUpperOnset(previousPressure, pressure, temperature);
      } else {
        previousPressure = pressure;
        previousWasStable = true;
      }
      if (pressure == minPressure) {
        break;
      }
      pressure = Math.max(minPressure, pressure - pressureSearchStep);
    }
    return Double.NaN;
  }

  /**
   * Calculates the lower onset pressure where precipitated asphaltene redissolves during depressurization.
   *
   * @param temperature temperature (K)
   * @param startPressure highest pressure to scan (bara)
   * @param minPressure lowest pressure to scan (bara)
   * @return lower onset pressure (bara), or NaN if no upper onset or redissolution is found
   */
  public double calculateLowerOnsetPressure(double temperature, double startPressure, double minPressure) {
    double upperOnset = calculateOnsetPressure(temperature, startPressure, minPressure);
    if (Double.isNaN(upperOnset)) {
      return Double.NaN;
    }
    double unstablePressure = upperOnset;
    for (double pressure = Math.max(minPressure, upperOnset - pressureSearchStep); pressure >= minPressure;) {
      double precipitated = calculatePrecipitatedFraction(pressure, temperature);
      if (Double.isNaN(precipitated)) {
        unstablePressure = Double.NaN;
      } else if (precipitated > onsetDetectionThreshold) {
        unstablePressure = pressure;
      } else if (!Double.isNaN(unstablePressure)) {
        return bisectLowerOnset(pressure, unstablePressure, temperature);
      }
      if (pressure == minPressure) {
        break;
      }
      pressure = Math.max(minPressure, pressure - pressureSearchStep);
    }
    return Double.NaN;
  }

  /**
   * Bisects an upper onset bracket with stable high pressure and unstable low pressure.
   *
   * @param stablePressure stable pressure (bara)
   * @param unstablePressure unstable pressure (bara)
   * @param temperature temperature (K)
   * @return onset pressure (bara)
   */
  private double bisectUpperOnset(double stablePressure, double unstablePressure, double temperature) {
    double high = stablePressure;
    double low = unstablePressure;
    for (int i = 0; i < 50 && high - low > 1.0e-3; i++) {
      double middle = 0.5 * (high + low);
      double precipitated = calculatePrecipitatedFraction(middle, temperature);
      if (Double.isNaN(precipitated)) {
        return Double.NaN;
      }
      if (precipitated > onsetDetectionThreshold) {
        low = middle;
      } else {
        high = middle;
      }
    }
    return 0.5 * (high + low);
  }

  /**
   * Bisects a lower onset bracket with stable low pressure and unstable high pressure.
   *
   * @param stablePressure stable pressure (bara)
   * @param unstablePressure unstable pressure (bara)
   * @param temperature temperature (K)
   * @return lower onset pressure (bara)
   */
  private double bisectLowerOnset(double stablePressure, double unstablePressure, double temperature) {
    double low = stablePressure;
    double high = unstablePressure;
    for (int i = 0; i < 50 && high - low > 1.0e-3; i++) {
      double middle = 0.5 * (high + low);
      double precipitated = calculatePrecipitatedFraction(middle, temperature);
      if (Double.isNaN(precipitated)) {
        return Double.NaN;
      }
      if (precipitated > onsetDetectionThreshold) {
        high = middle;
      } else {
        low = middle;
      }
    }
    return 0.5 * (high + low);
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
      results[2][i] = getAsphalteneSolubilityParameter(temperature);
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
   * @param maxPressure maximum pressure for onset search (bara)
   * @param minPressure minimum pressure for onset search (bara)
   * @return results map
   */
  public Map<String, Object> getResultsMap(double temperature, double maxPressure, double minPressure) {
    Map<String, Object> results = new LinkedHashMap<String, Object>();
    results.put("model", "Flory-Huggins Regular Solution");
    results.put("asphaltene_MW_gmol", asphalteneMW);
    results.put("asphaltene_density_kgm3", asphalteneDensity);
    results.put("asphaltene_solubility_parameter_MPa05", getAsphalteneSolubilityParameter(temperature));
    results.put("asphaltene_solubility_parameter_reference_temperature_K", solubilityParameterReferenceTemperature);
    results.put("asphaltene_solubility_parameter_temperature_coefficient_MPa05_per_K",
        solubilityParameterTemperatureCoefficient);
    results.put("calibrated_to_onset_data", calibratedToOnsetData);
    results.put("asphaltene_molar_volume_cm3mol", asphaltMolarVolume);
    results.put("asphaltene_weight_fraction", asphalteneWeightFraction);
    results.put("onset_detection_threshold", onsetDetectionThreshold);
    results.put("solubility_parameter_method", solubilityParameterMethod.name());
    results.put("live_oil_basis", liveOilBasis);
    results.put("asphaltene_fractions", numberOfAsphalteneFractions);
    results.put("temperature_K", temperature);
    results.put("onset_pressure_bara", calculateOnsetPressure(temperature, maxPressure, minPressure));
    results.put("lower_onset_pressure_bara", calculateLowerOnsetPressure(temperature, maxPressure, minPressure));
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
    this.calibratedToOnsetData = false;
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
   * Gets the asphaltene solubility parameter at a specified temperature.
   *
   * @param temperature temperature (K)
   * @return solubility parameter at the requested temperature (MPa^0.5)
   */
  public double getAsphalteneSolubilityParameter(double temperature) {
    return asphalteneSolubilityParameter
        + solubilityParameterTemperatureCoefficient * (temperature - solubilityParameterReferenceTemperature);
  }

  /**
   * Sets the asphaltene solubility parameter. Any earlier SARA adjustment is discarded.
   *
   * @param asphalteneSolubilityParameter solubility parameter (MPa^0.5)
   */
  public void setAsphalteneSolubilityParameter(double asphalteneSolubilityParameter) {
    this.asphalteneSolubilityParameter = asphalteneSolubilityParameter;
    this.saraBaseSolubilityParameter = Double.NaN;
    this.calibratedToOnsetData = false;
  }

  /**
   * Sets a linear temperature dependence for the asphaltene solubility parameter.
   *
   * @param referenceTemperature reference temperature (K)
   * @param temperatureCoefficient temperature coefficient (MPa^0.5/K)
   * @throws IllegalArgumentException if either value is not finite or the reference temperature is not positive
   */
  public void setSolubilityParameterTemperatureDependence(double referenceTemperature, double temperatureCoefficient) {
    if (!(referenceTemperature > 0.0) || Double.isInfinite(referenceTemperature) || Double.isNaN(referenceTemperature)
        || Double.isInfinite(temperatureCoefficient) || Double.isNaN(temperatureCoefficient)) {
      throw new IllegalArgumentException("Reference temperature must be positive and both values must be finite");
    }
    this.solubilityParameterReferenceTemperature = referenceTemperature;
    this.solubilityParameterTemperatureCoefficient = temperatureCoefficient;
    this.calibratedToOnsetData = false;
  }

  /**
   * Gets the reference temperature of the asphaltene solubility parameter.
   *
   * @return reference temperature (K)
   */
  public double getSolubilityParameterReferenceTemperature() {
    return solubilityParameterReferenceTemperature;
  }

  /**
   * Gets the temperature coefficient of the asphaltene solubility parameter.
   *
   * @return temperature coefficient (MPa^0.5/K)
   */
  public double getSolubilityParameterTemperatureCoefficient() {
    return solubilityParameterTemperatureCoefficient;
  }

  /**
   * Sets the precipitated weight fraction that defines onset detection.
   *
   * @param threshold onset detection threshold (weight fraction)
   * @throws IllegalArgumentException if the threshold is negative or not finite
   */
  public void setOnsetDetectionThreshold(double threshold) {
    if (!(threshold >= 0.0) || Double.isInfinite(threshold)) {
      throw new IllegalArgumentException("Onset detection threshold must be non-negative and finite");
    }
    this.onsetDetectionThreshold = threshold;
  }

  /**
   * Gets the precipitated weight fraction that defines onset detection.
   *
   * @return onset detection threshold (weight fraction)
   */
  public double getOnsetDetectionThreshold() {
    return onsetDetectionThreshold;
  }

  /**
   * Checks whether onset data have been fitted to the current model parameters.
   *
   * @return true if onset data have been calibrated and not invalidated by a later parameter change
   */
  public boolean isCalibratedToOnsetData() {
    return calibratedToOnsetData;
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
    this.calibratedToOnsetData = false;
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
    this.calibratedToOnsetData = false;
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
    this.calibratedToOnsetData = false;
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
    this.calibratedToOnsetData = false;
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
    this.calibratedToOnsetData = false;
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
    this.calibratedToOnsetData = false;
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
