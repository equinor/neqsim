package neqsim.process.mechanicaldesign.heatexchanger;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Dynamic fouling model for heat exchangers based on the Ebert-Panchal (1997) threshold fouling
 * correlation.
 *
 * <p>
 * Predicts time-dependent fouling resistance growth as a function of velocity, wall temperature,
 * and fluid properties. Unlike the traditional fixed-resistance TEMA approach, this model captures:
 * </p>
 * <ul>
 * <li>The threshold condition below which fouling is negligible</li>
 * <li>The asymptotic fouling behavior as deposition and removal reach equilibrium</li>
 * <li>The effect of velocity (shear) on removal rate</li>
 * <li>The effect of wall temperature on chemical reaction rate (Arrhenius)</li>
 * </ul>
 *
 * <p>
 * The fouling rate is modeled as deposition minus removal:
 * </p>
 *
 * <pre>
 * dRf/dt = alpha * Re^beta * exp(-E / (R * Tw)) - gamma * tau_w * Rf
 * </pre>
 *
 * <p>
 * where:
 * </p>
 * <ul>
 * <li>alpha, beta = deposition rate constants (depend on crude type)</li>
 * <li>E = activation energy (J/mol)</li>
 * <li>R = universal gas constant (8.314 J/(mol*K))</li>
 * <li>Tw = wall temperature (K)</li>
 * <li>gamma = removal rate constant</li>
 * <li>tau_w = wall shear stress (Pa)</li>
 * <li>Rf = current fouling resistance (m2*K/W)</li>
 * </ul>
 *
 * <p>
 * At steady state, Rf_asymptotic = alpha * Re^beta * exp(-E/(R*Tw)) / (gamma * tau_w).
 * </p>
 *
 * <p>
 * The Kern-Seaton (1959) asymptotic model is also included as a simpler alternative:
 * </p>
 *
 * <pre>
 * Rf(t) = Rf_max * (1 - exp(-t / tau))
 * </pre>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>Ebert, W. and Panchal, C.B. (1997). "Analysis of Exxon Crude-Oil Slip-Stream Coking Data."
 * Fouling Mitigation of Industrial Heat-Exchange Equipment, Begell House, 451-460.</li>
 * <li>Kern, D.Q. and Seaton, R.E. (1959). "A Theoretical Analysis of Thermal Surface Fouling."
 * British Chemical Engineering, 4(5), 258-262.</li>
 * <li>Polley, G.T., Wilson, D.I., Yeap, B.L., and Pugh, S.J. (2002). "Evaluation of laboratory
 * crude oil threshold fouling data for application to refinery pre-heat trains." Applied Thermal
 * Engineering, 22, 777-788.</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 * @see ThermalDesignCalculator
 */
public class FoulingModel implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  /** Universal gas constant (J/(mol*K)). */
  private static final double R_GAS = 8.314;

  /**
   * Fouling model type selection.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  public enum FoulingModelType {
    /** Fixed resistance from TEMA tables (default, legacy behavior). */
    FIXED,
    /** Ebert-Panchal threshold fouling model. */
    EBERT_PANCHAL,
    /** Kern-Seaton asymptotic model. */
    KERN_SEATON
  }

  // Model selection
  private FoulingModelType modelType = FoulingModelType.FIXED;

  // ============================================================================
  // Fixed model parameters (TEMA)
  // ============================================================================
  private double fixedFoulingResistance = 0.000176; // m2*K/W (water default)

  // ============================================================================
  // Ebert-Panchal parameters
  // ============================================================================
  /** Deposition pre-exponential constant. Typical: 0.2-1.0 for crude oils. */
  private double alpha = 0.5;

  /** Reynolds number exponent. Typical: -0.66 for crude oils. */
  private double beta = -0.66;

  /** Activation energy (J/mol). Typical: 28000-68000 for crude oils. */
  private double activationEnergy = 48000.0;

  /** Removal rate constant (1/(Pa*s)). Typical: 3e-9 to 8e-9. */
  private double gamma = 5e-9;

  // ============================================================================
  // Kern-Seaton parameters
  // ============================================================================
  /** Maximum (asymptotic) fouling resistance (m2*K/W). */
  private double rfMax = 0.0005;

  /** Time constant (hours). */
  private double timeConstant = 2000.0;

  // ============================================================================
  // Operating conditions
  // ============================================================================
  private double reynoldsNumber = 10000.0;
  private double wallTemperature = 473.15; // K (200 C)
  private double wallShearStress = 10.0; // Pa
  private double velocity = 1.5; // m/s
  private double fluidDensity = 800.0; // kg/m3
  private double fluidViscosity = 5e-4; // Pa*s
  private double tubeID = 0.01905; // m

  // ============================================================================
  // Current fouling state
  // ============================================================================
  private double currentFoulingResistance = 0.0; // m2*K/W
  private double operatingTimeHours = 0.0;
  private double foulingRate = 0.0; // m2*K/(W*hr)

  /**
   * Constructs a FoulingModel with default FIXED type.
   */
  public FoulingModel() {}

  /**
   * Constructs a FoulingModel with specified type.
   *
   * @param type fouling model type
   */
  public FoulingModel(FoulingModelType type) {
    this.modelType = type;
  }

  /**
   * Gets the current fouling resistance for the configured model and conditions.
   *
   * @return fouling resistance (m2*K/W)
   */
  public double getFoulingResistance() {
    switch (modelType) {
      case EBERT_PANCHAL:
        return currentFoulingResistance;
      case KERN_SEATON:
        return calcKernSeatonResistance(operatingTimeHours);
      default:
        return fixedFoulingResistance;
    }
  }

  /**
   * Gets the fouling resistance at a specified operating time.
   *
   * @param timeHours operating time in hours
   * @return fouling resistance (m2*K/W)
   */
  public double getFoulingResistance(double timeHours) {
    switch (modelType) {
      case EBERT_PANCHAL:
        return calcEbertPanchalResistance(timeHours);
      case KERN_SEATON:
        return calcKernSeatonResistance(timeHours);
      default:
        return fixedFoulingResistance;
    }
  }

  /**
   * Calculates the instantaneous fouling rate dRf/dt using the Ebert-Panchal model.
   *
   * <pre>
   * dRf/dt = alpha * Re^beta * exp(-E/(R*Tw)) - gamma * tau_w * Rf
   * </pre>
   *
   * @param currentRf current fouling resistance (m2*K/W)
   * @return fouling rate (m2*K/(W*hr))
   */
  public double calcEbertPanchalFoulingRate(double currentRf) {
    if (wallTemperature <= 0 || reynoldsNumber <= 0) {
      return 0.0;
    }

    // Deposition rate
    double deposition = alpha * Math.pow(reynoldsNumber, beta)
        * Math.exp(-activationEnergy / (R_GAS * wallTemperature));

    // Removal rate
    double removal = gamma * wallShearStress * currentRf;

    // Net fouling rate (convert from per second to per hour)
    foulingRate = (deposition - removal) * 3600.0;

    return foulingRate;
  }

  /**
   * Calculates the fouling resistance at a given time using Ebert-Panchal model with simple Euler
   * integration.
   *
   * @param timeHours operating time (hours)
   * @return fouling resistance (m2*K/W)
   */
  public double calcEbertPanchalResistance(double timeHours) {
    if (timeHours <= 0) {
      return 0.0;
    }

    // Analytical solution approximation using asymptotic behaviour
    double depositionRate = alpha * Math.pow(reynoldsNumber, beta)
        * Math.exp(-activationEnergy / (R_GAS * wallTemperature));

    double removalCoeff = gamma * wallShearStress;

    if (removalCoeff <= 0) {
      // No removal: linear growth
      return depositionRate * timeHours * 3600.0;
    }

    // Asymptotic fouling resistance
    double rfAsymptotic = depositionRate / removalCoeff;

    // Time constant (seconds)
    double tauSeconds = 1.0 / removalCoeff;
    double tauHours = tauSeconds / 3600.0;

    // Exponential approach to asymptote (analytical solution)
    return rfAsymptotic * (1.0 - Math.exp(-timeHours / tauHours));
  }

  /**
   * Calculates the fouling resistance at a given time using the Kern-Seaton asymptotic model.
   *
   * <pre>
   * Rf(t) = Rf_max * (1 - exp(-t / tau))
   * </pre>
   *
   * @param timeHours operating time (hours)
   * @return fouling resistance (m2*K/W)
   */
  public double calcKernSeatonResistance(double timeHours) {
    if (timeHours <= 0 || rfMax <= 0 || timeConstant <= 0) {
      return 0.0;
    }

    return rfMax * (1.0 - Math.exp(-timeHours / timeConstant));
  }

  /**
   * Calculates the threshold wall temperature below which no fouling occurs.
   *
   * <p>
   * At the threshold, deposition rate equals removal rate at zero fouling:
   * </p>
   *
   * <pre>
   * alpha * Re^beta * exp(-E/(R*Tw_threshold)) = 0
   * </pre>
   *
   * <p>
   * In practice, the threshold is the temperature at which dRf/dt becomes negligibly small. Uses
   * the Polley (2002) threshold condition: fouling rate at Rf=0 equals a target threshold rate.
   * </p>
   *
   * @param thresholdRate target fouling rate at Rf=0 (m2*K/(W*s)), typical: 1e-10 to 1e-9
   * @return threshold wall temperature (K)
   */
  public double calcThresholdTemperature(double thresholdRate) {
    if (alpha <= 0 || thresholdRate <= 0 || reynoldsNumber <= 0) {
      return 0.0;
    }

    double reTermLn = Math.log(thresholdRate / (alpha * Math.pow(reynoldsNumber, beta)));

    if (reTermLn >= 0) {
      return Double.MAX_VALUE; // Always exceeds threshold
    }

    return -activationEnergy / (R_GAS * reTermLn);
  }

  /**
   * Calculates the threshold velocity above which fouling is suppressed.
   *
   * <p>
   * Uses the condition that the removal rate exceeds the deposition rate. Requires a diameter and
   * fluid properties to convert between velocity, Reynolds number, and shear stress.
   * </p>
   *
   * @return threshold velocity (m/s), or 0 if fouling cannot be suppressed at this temperature
   */
  public double calcThresholdVelocity() {
    if (wallTemperature <= 0 || fluidDensity <= 0 || fluidViscosity <= 0 || tubeID <= 0) {
      return 0.0;
    }

    // Binary search for velocity where Rf_asymptotic approaches TEMA default
    double vLow = 0.1;
    double vHigh = 10.0;

    for (int i = 0; i < 50; i++) {
      double vMid = (vLow + vHigh) / 2.0;
      double Re = fluidDensity * vMid * tubeID / fluidViscosity;
      double f = ThermalDesignCalculator.calcDarcyFriction(Re);
      double tau = f / 8.0 * fluidDensity * vMid * vMid;

      double deposition =
          alpha * Math.pow(Re, beta) * Math.exp(-activationEnergy / (R_GAS * wallTemperature));
      double rfAsymptotic = (gamma * tau > 0) ? deposition / (gamma * tau) : Double.MAX_VALUE;

      if (rfAsymptotic > fixedFoulingResistance) {
        vLow = vMid;
      } else {
        vHigh = vMid;
      }
    }

    return (vLow + vHigh) / 2.0;
  }

  /**
   * Updates the operating conditions from fluid properties.
   *
   * @param velocity fluid velocity (m/s)
   * @param density fluid density (kg/m3)
   * @param viscosity fluid dynamic viscosity (Pa*s)
   * @param wallTemp wall temperature (K)
   * @param innerDiameter tube inner diameter (m)
   */
  public void updateConditions(double velocity, double density, double viscosity, double wallTemp,
      double innerDiameter) {
    this.velocity = velocity;
    this.fluidDensity = density;
    this.fluidViscosity = viscosity;
    this.wallTemperature = wallTemp;
    this.tubeID = innerDiameter;

    // Derived quantities
    this.reynoldsNumber = density * velocity * innerDiameter / viscosity;
    double f = ThermalDesignCalculator.calcDarcyFriction(reynoldsNumber);
    this.wallShearStress = f / 8.0 * density * velocity * velocity;
  }

  /**
   * Advances the fouling state by a time step using Euler integration.
   *
   * @param timeStepHours time step in hours
   */
  public void advanceTime(double timeStepHours) {
    if (modelType == FoulingModelType.EBERT_PANCHAL) {
      double rate = calcEbertPanchalFoulingRate(currentFoulingResistance);
      currentFoulingResistance += rate * timeStepHours;
      currentFoulingResistance = Math.max(0.0, currentFoulingResistance);
    }
    operatingTimeHours += timeStepHours;
  }

  /**
   * Resets the fouling state (e.g., after cleaning).
   */
  public void reset() {
    currentFoulingResistance = 0.0;
    operatingTimeHours = 0.0;
    foulingRate = 0.0;
  }

  /**
   * Calculates the asymptotic fouling resistance for current conditions.
   *
   * @return asymptotic fouling resistance (m2*K/W)
   */
  public double getAsymptoticFoulingResistance() {
    switch (modelType) {
      case EBERT_PANCHAL:
        double deposition = alpha * Math.pow(reynoldsNumber, beta)
            * Math.exp(-activationEnergy / (R_GAS * wallTemperature));
        double removalCoeff = gamma * wallShearStress;
        return (removalCoeff > 0) ? deposition / removalCoeff : Double.MAX_VALUE;
      case KERN_SEATON:
        return rfMax;
      default:
        return fixedFoulingResistance;
    }
  }

  /**
   * Predicts the time to reach a target fouling resistance.
   *
   * @param targetRf target fouling resistance (m2*K/W)
   * @return estimated time in hours, or -1 if target exceeds asymptotic value
   */
  public double predictTimeToFouling(double targetRf) {
    double rfAsymptotic = getAsymptoticFoulingResistance();

    if (targetRf >= rfAsymptotic) {
      return -1.0;
    }

    switch (modelType) {
      case EBERT_PANCHAL:
        double removalCoeff = gamma * wallShearStress;
        double tauHours = (removalCoeff > 0) ? 1.0 / (removalCoeff * 3600.0) : Double.MAX_VALUE;
        return -tauHours * Math.log(1.0 - targetRf / rfAsymptotic);
      case KERN_SEATON:
        return -timeConstant * Math.log(1.0 - targetRf / rfMax);
      default:
        return 0.0;
    }
  }

  /**
   * Returns all fouling model results as a map.
   *
   * @return map of result keys and values
   */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();

    result.put("modelType", modelType.name());
    result.put("currentFoulingResistance_m2KpW", getFoulingResistance());
    result.put("asymptoticFoulingResistance_m2KpW", getAsymptoticFoulingResistance());
    result.put("operatingTime_hours", operatingTimeHours);

    if (modelType == FoulingModelType.EBERT_PANCHAL) {
      Map<String, Object> ebParams = new LinkedHashMap<String, Object>();
      ebParams.put("alpha", alpha);
      ebParams.put("beta", beta);
      ebParams.put("activationEnergy_Jpmol", activationEnergy);
      ebParams.put("gamma_1pPas", gamma);
      ebParams.put("wallTemperature_K", wallTemperature);
      ebParams.put("reynoldsNumber", reynoldsNumber);
      ebParams.put("wallShearStress_Pa", wallShearStress);
      ebParams.put("foulingRate_m2KpWhr", foulingRate);
      result.put("ebertPanchalParameters", ebParams);

      double thresholdT = calcThresholdTemperature(1e-10);
      result.put("thresholdWallTemperature_K", thresholdT);
      result.put("thresholdWallTemperature_C", thresholdT - 273.15);
    }

    if (modelType == FoulingModelType.KERN_SEATON) {
      Map<String, Object> ksParams = new LinkedHashMap<String, Object>();
      ksParams.put("rfMax_m2KpW", rfMax);
      ksParams.put("timeConstant_hours", timeConstant);
      result.put("kernSeatonParameters", ksParams);
    }

    return result;
  }

  /**
   * Converts all fouling model results to a JSON string.
   *
   * @return JSON string with pretty printing
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(toMap());
  }

  // ============================================================================
  // Pre-configured factory methods for common fluids
  // ============================================================================

  /**
   * Creates an Ebert-Panchal fouling model configured for typical crude oil service.
   *
   * <p>
   * Uses parameters from Polley et al. (2002) for North Sea crude:
   * </p>
   * <ul>
   * <li>alpha = 0.5</li>
   * <li>beta = -0.66</li>
   * <li>E = 48,000 J/mol</li>
   * <li>gamma = 5e-9 1/(Pa*s)</li>
   * </ul>
   *
   * @return configured FoulingModel for crude oil
   */
  public static FoulingModel createCrudeOilModel() {
    FoulingModel model = new FoulingModel(FoulingModelType.EBERT_PANCHAL);
    model.alpha = 0.5;
    model.beta = -0.66;
    model.activationEnergy = 48000.0;
    model.gamma = 5e-9;
    return model;
  }

  /**
   * Creates an Ebert-Panchal fouling model for heavy crude oil / high-asphaltene service.
   *
   * <p>
   * Higher activation energy and deposition rate than light crude:
   * </p>
   * <ul>
   * <li>alpha = 1.0</li>
   * <li>beta = -0.66</li>
   * <li>E = 68,000 J/mol</li>
   * <li>gamma = 3e-9 1/(Pa*s)</li>
   * </ul>
   *
   * @return configured FoulingModel for heavy crude
   */
  public static FoulingModel createHeavyCrudeModel() {
    FoulingModel model = new FoulingModel(FoulingModelType.EBERT_PANCHAL);
    model.alpha = 1.0;
    model.beta = -0.66;
    model.activationEnergy = 68000.0;
    model.gamma = 3e-9;
    return model;
  }

  /**
   * Creates a Kern-Seaton model for cooling water service.
   *
   * @param rfMaxValue maximum fouling resistance (m2*K/W), typical 0.0002-0.0005
   * @param tauHours time constant (hours), typical 1000-4000
   * @return configured FoulingModel for cooling water
   */
  public static FoulingModel createCoolingWaterModel(double rfMaxValue, double tauHours) {
    FoulingModel model = new FoulingModel(FoulingModelType.KERN_SEATON);
    model.rfMax = rfMaxValue;
    model.timeConstant = tauHours;
    return model;
  }

  // ============================================================================
  // Getters and setters
  // ============================================================================

  /**
   * Gets the fouling model type.
   *
   * @return model type
   */
  public FoulingModelType getModelType() {
    return modelType;
  }

  /**
   * Sets the fouling model type.
   *
   * @param modelType model type to use
   */
  public void setModelType(FoulingModelType modelType) {
    this.modelType = modelType;
  }

  /**
   * Sets the fixed fouling resistance for the FIXED model type.
   *
   * @param resistance fouling resistance (m2*K/W)
   */
  public void setFixedFoulingResistance(double resistance) {
    this.fixedFoulingResistance = resistance;
  }

  /**
   * Gets the fixed fouling resistance.
   *
   * @return fouling resistance (m2*K/W)
   */
  public double getFixedFoulingResistance() {
    return fixedFoulingResistance;
  }

  /**
   * Sets Ebert-Panchal deposition constant alpha.
   *
   * @param alpha deposition pre-exponential constant
   */
  public void setAlpha(double alpha) {
    this.alpha = alpha;
  }

  /**
   * Sets Ebert-Panchal Reynolds exponent beta.
   *
   * @param beta Reynolds number exponent (typically negative, e.g. -0.66)
   */
  public void setBeta(double beta) {
    this.beta = beta;
  }

  /**
   * Sets Ebert-Panchal activation energy.
   *
   * @param energy activation energy (J/mol)
   */
  public void setActivationEnergy(double energy) {
    this.activationEnergy = energy;
  }

  /**
   * Sets Ebert-Panchal removal rate constant.
   *
   * @param gamma removal rate constant (1/(Pa*s))
   */
  public void setGamma(double gamma) {
    this.gamma = gamma;
  }

  /**
   * Sets Kern-Seaton maximum fouling resistance.
   *
   * @param rfMax maximum fouling resistance (m2*K/W)
   */
  public void setRfMax(double rfMax) {
    this.rfMax = rfMax;
  }

  /**
   * Sets Kern-Seaton time constant.
   *
   * @param timeConstant time constant (hours)
   */
  public void setTimeConstant(double timeConstant) {
    this.timeConstant = timeConstant;
  }

  /**
   * Gets the current operating time.
   *
   * @return operating time (hours)
   */
  public double getOperatingTimeHours() {
    return operatingTimeHours;
  }

  /**
   * Gets the current fouling rate.
   *
   * @return fouling rate (m2*K/(W*hr))
   */
  public double getFoulingRate() {
    return foulingRate;
  }
}
