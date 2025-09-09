package neqsim.process.equipment.reactor;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ejml.simple.SimpleMatrix;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * GibbsReactor class.
 * </p>
 *
 * @author Sviatoslav Eroshkin
 * @version $Id: $Id
 */
public class GibbsReactor extends TwoPortEquipment {
  /**
   * Get the absolute mass balance error (difference between inlet and outlet) in kg/sec.
   *
   * @return absolute difference in total mass flow rate (kg/sec)
   */
  public double getMassBalanceError() {
    try {
      double inletMass = getInletStream().getThermoSystem().getFlowRate("kg/sec");
      double outletMass = getOutletStream().getThermoSystem().getFlowRate("kg/sec");
      return 100 * Math.abs(inletMass - outletMass) / inletMass;
    } catch (Exception e) {
      logger.debug("WARNING: Could not calculate mass balance error: {}", e.getMessage());
      return Double.NaN;
    }
  }

  /**
   * Returns true if the absolute mass balance error is less than 1e-3 kg/sec.
   *
   * @return true if mass balance is converged, false otherwise
   */
  public boolean getMassBalanceConverged() {
    double error = getMassBalanceError();
    return !Double.isNaN(error) && error < 1e-3;
  }

  // Thread-local reusable system for fugacity calculations to minimize cloning
  private final ThreadLocal<neqsim.thermo.system.SystemInterface> tempFugacitySystem =
      new ThreadLocal<>();

  /**
   * Get the cumulative enthalpy of reaction (sum of dH for all iterations).
   *
   * @return enthalpyOfReactions in kJ
   */
  public double getEnthalpyOfReactions() {
    return enthalpyOfReactions;
  }

  /**
   * Get the cumulative temperature change during the reaction (sum of dT for all iterations).
   *
   * @return temperatureChange in K
   */
  public double getTemperatureChange() {
    return temperatureChange;
  }

  /**
   * Get the reactor power (default: W, negative enthalpyOfReactions*1000).
   *
   * @return Power in Watts (W)
   */
  public double getPower() {
    return -enthalpyOfReactions * 1000.0;
  }


  /**
   * Get the reactor power in the specified unit ("W", "kW", or "MW").
   *
   * @param unit Power unit: "W", "kW", or "MW" (case-insensitive, default is W)
   * @return Power in the specified unit
   */
  public double getPower(String unit) {
    if (unit == null)
      return getPower();
    switch (unit.trim().toLowerCase()) {
      case "kw":
        return -enthalpyOfReactions;
      case "mw":
        return -enthalpyOfReactions / 1000.0;
      case "w":
      default:
        return -enthalpyOfReactions * 1000.0;
    }
  }

  /**
   * Calculate the total enthalpy of a mixture: sum_i n_i * enthalpy_i(T).
   *
   * @param componentNames List of component names (order matches n_i)
   * @param n List of moles for each component
   * @param T Temperature in K
   * @param componentMap Map from component name (lowercase) to GibbsComponent
   * @return Total enthalpy (kJ)
   */
  public double calculateMixtureEnthalpy(List<String> componentNames, List<Double> n, double T,
      Map<String, GibbsComponent> componentMap) {
    double totalH = 0.0;
    for (int i = 0; i < componentNames.size(); i++) {
      String compName = componentNames.get(i);
      GibbsComponent comp = componentMap.get(compName.toLowerCase());
      if (comp == null) {
        throw new IllegalArgumentException(
            "Component '" + compName + "' not found in gibbsReactDatabase.");
      }
      totalH += n.get(i) * comp.calculateEnthalpy(T, i);
    }
    logger.info("Mixture enthalpy at T=" + T + " K: " + totalH + " kJ");
    return totalH;
  }

  /**
   * Calculate the total Gibbs energy of a mixture: sum_i n_i * gibbs_i(T).
   *
   * @param componentNames List of component names (order matches n_i)
   * @param n List of moles for each component
   * @param T Temperature in K
   * @param componentMap Map from component name (lowercase) to GibbsComponent
   * @return Total Gibbs energy (kJ)
   */
  public double calculateMixtureGibbsEnergy(List<String> componentNames, List<Double> n,
      Map<String, GibbsComponent> componentMap, double T) {
    double totalG = 0.0;
    for (int i = 0; i < componentNames.size(); i++) {
      String compName = componentNames.get(i);
      GibbsComponent comp = componentMap.get(compName.toLowerCase());
      if (comp == null) {
        throw new IllegalArgumentException(
            "Component '" + compName + "' not found in gibbsReactDatabase.");
      }
      totalG += n.get(i) * comp.calculateGibbsEnergy(T, i);
    }
    logger.info("Mixture Gibbs energy at T=" + T + " K: " + totalG + " kJ");
    return totalG;
  }

  /**
   * Get the last calculated mixture enthalpy (kJ).
   *
   * @return a double
   */
  public double getMixtureEnthalpy() {
    double T = system != null ? system.getTemperature() : REFERENCE_TEMPERATURE;
    return calculateMixtureEnthalpy(processedComponents, inlet_mole, T, componentMap);
  }

  /**
   * Get the last calculated mixture Gibbs energy (kJ).
   *
   * @return a double
   */
  public double getMixtureGibbsEnergy() {
    double T = system != null ? system.getTemperature() : REFERENCE_TEMPERATURE;
    return calculateMixtureGibbsEnergy(processedComponents, inlet_mole, componentMap, T);
  }

  /**
   * Calculate the total standard enthalpy of a mixture: sum_i n_i * enthalpy_i(T).
   *
   * @param componentNames List of component names (order matches n_i)
   * @param n List of moles for each component
   * @param componentMap Map from component name (lowercase) to GibbsComponent
   * @return Total enthalpy (kJ)
   */
  public double calculateMixtureEnthalpyStandard(List<String> componentNames, List<Double> n,
      Map<String, GibbsComponent> componentMap) {
    double totalH = 0.0;
    for (int i = 0; i < componentNames.size(); i++) {
      String compName = componentNames.get(i);
      GibbsComponent comp = componentMap.get(compName.toLowerCase());
      if (comp == null) {
        throw new IllegalArgumentException(
            "Component '" + compName + "' not found in gibbsReactDatabase.");
      }
      totalH += n.get(i) * comp.calculateEnthalpy(REFERENCE_TEMPERATURE, i); // Use reference
                                                                             // temperature for
                                                                             // standard enthalpy
    }
    return totalH;
  }

  /**
   * Calculate the total standard enthalpy of a mixture: sum_i n_i * enthalpy_i(T).
   *
   * @param componentNames List of component names (order matches n_i)
   * @param n List of moles for each component
   * @param componentMap Map from component name (lowercase) to GibbsComponent
   * @return Total enthalpy (kJ)
   * @param T a double
   */
  public double calculateMixtureEnthalpy(List<String> componentNames, List<Double> n,
      Map<String, GibbsComponent> componentMap, double T) {
    double totalH = 0.0;
    for (int i = 0; i < componentNames.size(); i++) {
      String compName = componentNames.get(i);
      GibbsComponent comp = componentMap.get(compName.toLowerCase());
      if (comp == null) {
        throw new IllegalArgumentException(
            "Component '" + compName + "' not found in gibbsReactDatabase.");
      }
      totalH += n.get(i) * comp.calculateEnthalpy(T, i); // Use 298.15K for standard enthalpy
    }
    return totalH;
  }

  public enum EnergyMode {
    ISOTHERMAL, ADIABATIC
  }

  private EnergyMode energyMode = EnergyMode.ADIABATIC;

  /**
   * Set the energy mode of the reactor (isothermal or adiabatic).
   *
   * @param mode EnergyMode.ISOTHERMAL or EnergyMode.ADIABATIC
   */
  public void setEnergyMode(EnergyMode mode) {
    this.energyMode = mode;
  }

  /**
   * Set the energy mode of the reactor using a string (case-insensitive). Accepts "adiabatic" or
   * "isothermal" (case-insensitive).
   *
   * @param mode String representing the energy mode
   * @throws java.lang.IllegalArgumentException if the mode is not recognized
   */
  public void setEnergyMode(String mode) {
    if (mode == null)
      throw new IllegalArgumentException("Energy mode string cannot be null");
    switch (mode.trim().toLowerCase()) {
      case "adiabatic":
        setEnergyMode(EnergyMode.ADIABATIC);
        break;
      case "isothermal":
        setEnergyMode(EnergyMode.ISOTHERMAL);
        break;
      default:
        throw new IllegalArgumentException("Unknown energy mode: " + mode);
    }
  }

  /**
   * Gets the current energy mode of the reactor.
   *
   * @return the current EnergyMode (ISOTHERMAL or ADIABATIC)
   */
  public EnergyMode getEnergyMode() {
    return energyMode;
  }

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Reference temperature in Kelvin for thermodynamic calculations. */
  private static final double REFERENCE_TEMPERATURE = 298.15;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(GibbsReactor.class);

  private String method = "DirectGibbsMinimization";
  private boolean useAllDatabaseSpecies = false;
  private List<GibbsComponent> gibbsDatabase = new ArrayList<>();
  private Map<String, GibbsComponent> componentMap = new HashMap<>();

  // Results from the last calculation
  private double[] lambda = new double[7]; // O, N, C, H, S, Ar, Z
  private Map<String, Double> lagrangeContributions = new HashMap<>();
  private String[] elementNames = {"O", "N", "C", "H", "S", "Ar", "Z"};
  private List<String> processedComponents = new ArrayList<>();
  private Map<String, Double> objectiveFunctionValues = new HashMap<>();

  // Mole balance calculations
  private Map<String, Double> initialMoles = new HashMap<>();
  private Map<String, Double> finalMoles = new HashMap<>();
  private double[] elementMoleBalanceIn = new double[7]; // Total moles of each element in
  private double[] elementMoleBalanceOut = new double[7]; // Total moles of each element out
  private double[] elementMoleBalanceDiff = new double[7]; // Difference (out - in) for each element

  // Mole lists for calculations
  private List<Double> inlet_mole = new ArrayList<>();
  private List<Double> outlet_mole = new ArrayList<>();

  // Precomputed index map for processedComponents
  private Map<String, Integer> processedComponentIndexMap = new HashMap<>();

  // Objective minimization vector
  private double[] objectiveMinimizationVector;
  private List<String> objectiveMinimizationVectorLabels = new ArrayList<>();

  // Jacobian matrix for Newton-Raphson method
  private double[][] jacobianMatrix;
  private double[][] jacobianInverse;
  private List<String> jacobianRowLabels = new ArrayList<>();
  private List<String> jacobianColLabels = new ArrayList<>();

  // Newton-Raphson iteration control
  private int maxIterations = 5000;
  private double convergenceTolerance = 1e-6;
  private double dampingComposition = 0.001; // Default damping factor for composition updates
  private int actualIterations = 0;
  private boolean converged = false;
  private double finalConvergenceError = 0.0;

  private SystemInterface system;
  private double inletEnthalpy;
  private double outletEnthalpy;
  private double dT = 0.0;
  private int tempUpdateIter = 0;
  double enthalpyOld = 0.0;
  private double enthalpyOfReactions = 0.0;
  private double temperatureChange = 0.0;
  private double deltaNorm = 0.0;

  private double GOLD = 0.0;
  private double G = 0.0;
  private double dG = 0.0;


  /**
   * Constructor for GibbsReactor.
   *
   * @param name Name of GibbsReactor
   */
  public GibbsReactor(String name) {
    super(name);
    loadGibbsDatabase();
  }

  /**
   * Constructor for GibbsReactor.
   *
   * @param name Name of GibbsReactor
   * @param stream Stream to set as inlet Stream. A clone of stream is set as outlet stream.
   */
  public GibbsReactor(String name, StreamInterface stream) {
    super(name, stream);
    loadGibbsDatabase();
  }

  /**
   * Inner class to represent a component in the Gibbs reaction database.
   */
  public class GibbsComponent {
    private String molecule;
    private double[] elements = new double[7]; // O, N, C, H, S, Ar, Z
    private double[] heatCapacityCoeffs = new double[4]; // A, B, C, D
    private double deltaHf298; // Enthalpy of formation at 298K
    private double deltaGf298; // Gibbs energy of formation at 298K
    private double deltaSf298; // Entropy of formation at 298K
    // Extra coefficients for advanced Gibbs calculations
    private double coeffAg;
    private double coeffBg;
    private double coeffCg;
    private double coeffDg;
    private double coeffEg;
    private double coeffFg;
    private double coeffAh;
    private double coeffBh;
    private double coeffCh;
    private double coeffDh;
    private double coeffEh;
    private double coeffGh;

    /**
     * Constructs a GibbsComponent.
     *
     * @param molecule the molecule name
     * @param elements array of element counts
     * @param heatCapacityCoeffs array of heat capacity coefficients
     * @param deltaHf298 standard enthalpy of formation at 298 K
     * @param deltaGf298 standard Gibbs energy of formation at 298 K
     * @param deltaSf298 standard entropy at 298 K
     * @param coeffAg coefficient Ag for Gibbs calculations
     * @param coeffBg coefficient Bg for Gibbs calculations
     * @param coeffCg coefficient Cg for Gibbs calculations
     * @param coeffDg coefficient Dg for Gibbs calculations
     * @param coeffEg coefficient Eg for Gibbs calculations
     * @param coeffFg coefficient Fg for Gibbs calculations
     * @param coeffAh coefficient Ah for Gibbs calculations
     * @param coeffBh coefficient Bh for Gibbs calculations
     * @param coeffCh coefficient Ch for Gibbs calculations
     * @param coeffDh coefficient Dh for Gibbs calculations
     * @param coeffEh coefficient Eh for Gibbs calculations
     * @param coeffGh coefficient Gh for Gibbs calculations
     */
    public GibbsComponent(String molecule, double[] elements, double[] heatCapacityCoeffs,
        double deltaHf298, double deltaGf298, double deltaSf298, double coeffAg, double coeffBg,
        double coeffCg, double coeffDg, double coeffEg, double coeffFg, double coeffAh,
        double coeffBh, double coeffCh, double coeffDh, double coeffEh, double coeffGh) {
      this.molecule = molecule;
      this.elements = elements.clone();
      this.heatCapacityCoeffs = heatCapacityCoeffs.clone();
      this.deltaHf298 = deltaHf298;
      this.deltaGf298 = deltaGf298;
      this.deltaSf298 = deltaSf298;
      // Extra coefficients for advanced Gibbs calculations
      this.coeffAg = coeffAg;
      this.coeffBg = coeffBg;
      this.coeffCg = coeffCg;
      this.coeffDg = coeffDg;
      this.coeffEg = coeffEg;
      this.coeffFg = coeffFg;
      this.coeffAh = coeffAh;
      this.coeffBh = coeffBh;
      this.coeffCh = coeffCh;
      this.coeffDh = coeffDh;
      this.coeffEh = coeffEh;
      this.coeffGh = coeffGh;
    }

    /**
     * Get the entropy of formation at 298K (deltaSf298) in J/(mol·K).
     *
     * @return Entropy of formation at 298K
     */
    public double getDeltaSf298() {
      return deltaSf298;
    }

    public String getMolecule() {
      return molecule;
    }

    public double[] getElements() {
      return elements.clone();
    }

    public double[] getHeatCapacityCoeffs() {
      return heatCapacityCoeffs.clone();
    }

    public double getDeltaHf298() {
      return deltaHf298;
    }

    public double getDeltaGf298() {
      return deltaGf298;
    }

    /**
     * Calculates Gibbs energy for a component.
     *
     * @param temperature temperature in K
     * @param compNumber component index
     * @return Gibbs energy
     */
    public double calculateGibbsEnergy(double temperature, int compNumber) {
      double T = temperature;
      // If any Gibbs coefficients are not NaN, use polynomial formula
      if (!Double.isNaN(coeffAg) || !Double.isNaN(coeffBg) || !Double.isNaN(coeffCg)
          || !Double.isNaN(coeffDg) || !Double.isNaN(coeffEg) || !Double.isNaN(coeffFg)) {
        double gibbsPoly = (Double.isNaN(coeffAg) ? 0.0 : coeffAg) * Math.pow(T, 5)
            + (Double.isNaN(coeffBg) ? 0.0 : coeffBg) * Math.pow(T, 4)
            + (Double.isNaN(coeffCg) ? 0.0 : coeffCg) * Math.pow(T, 3)
            + (Double.isNaN(coeffDg) ? 0.0 : coeffDg) * Math.pow(T, 2)
            + (Double.isNaN(coeffEg) ? 0.0 : coeffEg) * T + (Double.isNaN(coeffFg) ? 0.0 : coeffFg);
        return gibbsPoly;
      }
      // Otherwise, use standard calculation with I and J functions
      // ΔG°f/RT = ΔG°f/RTR + (1/R)[J/T - ΔA×ln(T) - ΔB/2×T - ΔC/6×T² - ΔD/12×T³]
      double R = 8.314462618e-3; // kJ/(mol·K)

      // Reference term: ΔG°f/RTR
      double deltaGf_RT_ref = deltaGf298 / (R * REFERENCE_TEMPERATURE);

      // Calculate I at current temperature and reference temperature
      double I = calculateI(compNumber); // I calculated with current temperature coefficients
      double J = calculateJ(compNumber);

      // Get corrected coefficients for current temperature calculation
      double[] correctedCoeffs = calculateCorrectedHeatCapacityCoeffs(compNumber);
      double dA = correctedCoeffs[0];
      double dB = correctedCoeffs[1];
      double dC = correctedCoeffs[2];
      double dD = correctedCoeffs[3];

      double deltaGf_RT = deltaGf_RT_ref + I + (1 / R) * (J / T + (-dA * Math.log(T) - dB / 2.0 * T
          - dC / 6.0 * Math.pow(T, 2) - dD / 12.0 * Math.pow(T, 3)) / 1000);
      double deltaGf = deltaGf_RT * R * T;

      return deltaGf;
    }

    /**
     * Calculate corrected heat capacity coefficients dA, dB, dC, dD by subtracting elemental
     * contributions. dA = A - nO*AO - nN*AN - nC*AC - nH*AH - nS*AS
     * 
     * @param compNumber component index
     * @return array of corrected coefficients [dA, dB, dC, dD]
     */
    public double[] calculateCorrectedHeatCapacityCoeffs(int compNumber) {
      // Calculate corrected heat capacity coefficients using separate function
      double A = system.getComponent(compNumber).getCpA();
      double B = system.getComponent(compNumber).getCpB();
      double C = system.getComponent(compNumber).getCpC();
      double D = system.getComponent(compNumber).getCpD();

      // Element heat capacity coefficients [A, B, C, D]
      double[] cpO = {12.73, 7.60E-03, -3.58E-06, 6.56E-10};
      double[] cpN = {14.4415, -7.85E-04, 4.04E-06, -1.44E-09};
      double[] cpC = {8.43, 0.00E+00, 0.00E+00, 0.00E+00};
      double[] cpH = {14.544, -9.60E-04, 2.00E-06, -4.35E-10};
      double[] cpS = {17.815, 0.001, 0.000, 0.000};

      // Calculate dA, dB, dC, dD by subtracting elemental contributions
      // dA = A - nO*AO - nN*AN - nC*AC - nH*AH - nS*AS
      double dA = A - (elements[0] * cpO[0]) - (elements[1] * cpN[0]) - (elements[2] * cpC[0])
          - (elements[3] * cpH[0]) - (elements[4] * cpS[0]);

      double dB = B - (elements[0] * cpO[1]) - (elements[1] * cpN[1]) - (elements[2] * cpC[1])
          - (elements[3] * cpH[1]) - (elements[4] * cpS[1]);

      double dC = C - (elements[0] * cpO[2]) - (elements[1] * cpN[2]) - (elements[2] * cpC[2])
          - (elements[3] * cpH[2]) - (elements[4] * cpS[2]);

      double dD = D - (elements[0] * cpO[3]) - (elements[1] * cpN[3]) - (elements[2] * cpC[3])
          - (elements[3] * cpH[3]) - (elements[4] * cpS[3]);

      return new double[] {dA, dB, dC, dD};
    }

    /**
     * Calculate the corrected formation enthalpy term J. J = ΔH°f - ΔA*TR - ΔB/2*TR² - ΔC/3*TR³ -
     * ΔD/4*TR⁴ where TR is the reference temperature and ΔA, ΔB, ΔC, ΔD are corrected heat capacity
     * coefficients.
     * 
     * @param compNumber component index
     * @return corrected formation enthalpy term J
     */
    public double calculateJ(int compNumber) {
      // Get corrected heat capacity coefficients
      double[] correctedCoeffs = calculateCorrectedHeatCapacityCoeffs(compNumber);
      double dA = correctedCoeffs[0];
      double dB = correctedCoeffs[1];
      double dC = correctedCoeffs[2];
      double dD = correctedCoeffs[3];

      // Calculate J = ΔH°f - ΔA*TR - ΔB/2*TR² - ΔC/3*TR³ - ΔD/4*TR⁴
      double J =
          deltaHf298 - (dA * REFERENCE_TEMPERATURE - dB / 2.0 * Math.pow(REFERENCE_TEMPERATURE, 2)
              - dC / 3.0 * Math.pow(REFERENCE_TEMPERATURE, 3)
              - dD / 4.0 * Math.pow(REFERENCE_TEMPERATURE, 4)) / 1000;

      return J;
    }

    /**
     * Calculate the I term for thermodynamic calculations. I = (1/R) × [J/TR + ΔA×ln(TR) + ΔB/2×TR
     * + ΔC/6×TR² + ΔD/12×TR³] where R is the gas constant, TR is the reference temperature, and J
     * is the corrected formation enthalpy term.
     * 
     * @param compNumber component index
     * @return the I term
     */
    public double calculateI(int compNumber) {
      // Gas constant in kJ/(mol·K)
      double R = 8.314462618e-3;

      // Get corrected heat capacity coefficients
      double[] correctedCoeffs = calculateCorrectedHeatCapacityCoeffs(compNumber);
      double dA = correctedCoeffs[0];
      double dB = correctedCoeffs[1];
      double dC = correctedCoeffs[2];
      double dD = correctedCoeffs[3];

      // Calculate J term
      double J = calculateJ(compNumber);

      // Calculate I = (1/R) × [J/TR + ΔA×ln(TR) + ΔB/2×TR + ΔC/6×TR² + ΔD/12×TR³]
      double I = (1.0 / R) * (-(J / REFERENCE_TEMPERATURE) + (dA * Math.log(REFERENCE_TEMPERATURE)
          + dB / 2.0 * REFERENCE_TEMPERATURE + dC / 6.0 * Math.pow(REFERENCE_TEMPERATURE, 2)
          + dD / 12.0 * Math.pow(REFERENCE_TEMPERATURE, 3)) / 1000);

      return I;
    }

    /**
     * Calculates enthalpy for a component.
     *
     * @param temperature temperature in K
     * @param compNumber component index
     * @return enthalpy
     */
    public double calculateEnthalpy(double temperature, int compNumber) {
      double T = temperature;
      // If any enthalpy coefficients are not NaN, use polynomial formula
      if (!Double.isNaN(coeffAh) || !Double.isNaN(coeffBh) || !Double.isNaN(coeffCh)
          || !Double.isNaN(coeffDh) || !Double.isNaN(coeffEh) || !Double.isNaN(coeffGh)) {
        double enthalpyPoly = (Double.isNaN(coeffAh) ? 0.0 : coeffAh) * Math.pow(T, 5)
            + (Double.isNaN(coeffBh) ? 0.0 : coeffBh) * Math.pow(T, 4)
            + (Double.isNaN(coeffCh) ? 0.0 : coeffCh) * Math.pow(T, 3)
            + (Double.isNaN(coeffDh) ? 0.0 : coeffDh) * Math.pow(T, 2)
            + (Double.isNaN(coeffEh) ? 0.0 : coeffEh) * Math.pow(T, 1)
            + (Double.isNaN(coeffGh) ? 0.0 : coeffGh);
        return enthalpyPoly;
      }
      // Otherwise, use standard calculation

      // Calculate enthalpy using corrected heat capacity coefficients
      // ΔH = ΔHf + ΔA(T - T0) + ΔB/2(T² - T0²) + ΔC/3(T³ - T0³) + ΔD/4(T⁴ - T0⁴)
      double[] correctedCoeffs = calculateCorrectedHeatCapacityCoeffs(compNumber);
      double dA = correctedCoeffs[0];
      double dB = correctedCoeffs[1];
      double dC = correctedCoeffs[2];
      double dD = correctedCoeffs[3];

      double deltaH = deltaHf298 + (dA * (T - REFERENCE_TEMPERATURE)
          + dB / 2.0 * (Math.pow(T, 2) - Math.pow(REFERENCE_TEMPERATURE, 2))
          + dC / 3.0 * (Math.pow(T, 3) - Math.pow(REFERENCE_TEMPERATURE, 3))
          + dD / 4.0 * (Math.pow(T, 4) - Math.pow(REFERENCE_TEMPERATURE, 4))) / 1000;

      return deltaH;
    }

    /**
     * Calculates entropy for a component.
     *
     * @param temperature temperature in K
     * @param compNumber component index
     * @return entropy in J/(mol·K)
     */
    public double calculateEntropy(double temperature, int compNumber) {
      // Fallback to manual calculation if NeqSim method fails
      double T = temperature;

      // Calculate heat capacity
      double Cp = calculateHeatCapacity(T, compNumber);

      // S(T) = Sref + Cp*ln(T/Tref)
      return (deltaSf298 + Cp * Math.log(T / REFERENCE_TEMPERATURE)) / 1000;
    }

    /**
     * Calculates heat capacity for a component.
     *
     * @param temperature temperature in K
     * @param compNumber component index
     * @return heat capacity in J/(mol·K)
     */
    public double calculateHeatCapacity(double temperature, int compNumber) {
      // Get heat capacity from NeqSim's component
      double cp0 = system.getComponent(compNumber).getCp0(temperature);

      return cp0; // NeqSim returns Cp0 in J/(mol·K)
    }
  }



  /**
   * Load the Gibbs reaction database from resources.
   */
  private void loadGibbsDatabase() {
    try {
      // Load main Gibbs database
      InputStream inputStream =
          getClass().getResourceAsStream("/data/GibbsReactDatabase/GibbsReactDatabase.csv");
      if (inputStream == null) {
        inputStream = getClass()
            .getResourceAsStream("/neqsim/data/GibbsReactDatabase/GibbsReactDatabase.csv");
      }
      if (inputStream == null) {
        inputStream = getClass().getResourceAsStream("/neqsim/data/GibbsReactDatabase.csv");
      }
      if (inputStream == null) {
        logger.warn("Could not find GibbsReactDatabase.csv in resources");
        return;
      }

      // Load extra coefficients from DatabaseGibbsFreeEnergyCoeff.csv
      Map<String, double[]> extraCoeffMap = new HashMap<>();
      try {
        InputStream coeffStream = getClass()
            .getResourceAsStream("/data/GibbsReactDatabase/DatabaseGibbsFreeEnergyCoeff.csv");
        if (coeffStream != null) {
          Scanner coeffScanner = new Scanner(coeffStream);
          if (coeffScanner.hasNextLine())
            coeffScanner.nextLine(); // skip header
          while (coeffScanner.hasNextLine()) {
            String line = coeffScanner.nextLine().trim();
            if (line.isEmpty() || line.startsWith("#"))
              continue;
            String[] parts = line.split(";");
            if (parts.length == 13) {
              String compName = parts[0].trim().toLowerCase();
              double[] coeffs = new double[12];
              // Initialize all coefficients with NaN
              for (int j = 0; j < 12; j++) {
                coeffs[j] = Double.NaN;
              }
              for (int i = 0; i < 12; i++) {
                coeffs[i] = Double.parseDouble(parts[i + 1].replace(",", "."));
              }
              extraCoeffMap.put(compName, coeffs);
            }
          }
          coeffScanner.close();
        }
      } catch (Exception e) {
        logger.warn("Could not load extra Gibbs coefficients: " + e.getMessage());
      }

      Scanner scanner = new Scanner(inputStream);
      if (scanner.hasNextLine())
        scanner.nextLine(); // skip header
      while (scanner.hasNextLine()) {
        String line = scanner.nextLine().trim();
        if (line.isEmpty() || line.startsWith("#"))
          continue;
        String[] parts = line.split(";");
        // Handle both old format (15 parts, 6 elements) and new format (16 parts, 7 elements)
        if (parts.length >= 15) {
          try {
            final String molecule = parts[0].trim();
            double[] elements = new double[7];

            // Determine number of elements based on parts length
            // New format: Component + 7 elements (O,N,C,H,S,Ar,Z) + other data = 15+ parts
            // Old format: Component + 6 elements (O,N,C,H,S,Ar) + other data = 14+ parts
            int numElements = (parts.length >= 15) ? 7 : 6;
            logger.debug("Loading component: " + molecule + " with " + numElements
                + " elements (parts.length=" + parts.length + ")");

            // Debug logging for ionic species - show raw parts
            if (molecule.contains("+") || molecule.contains("-")) {
              StringBuilder partsStr = new StringBuilder();
              for (int i = 0; i < Math.min(parts.length, 10); i++) {
                partsStr.append("parts[").append(i).append("]=").append(parts[i]).append(" ");
              }
             // System.out.println(
               //   "DATABASE LOADING - Raw parts for " + molecule + ": " + partsStr.toString());
             // System.out.println("DATABASE LOADING - parts.length=" + parts.length
                //  + ", numElements=" + numElements);
            }

            // Parse available elements
            for (int i = 0; i < numElements; i++) {
              String value = parts[i + 1].trim().replace(",", ".");
              elements[i] = Double.parseDouble(value);
              if (molecule.contains("+") || molecule.contains("-")) {
               //System.out.println("DATABASE LOADING - Element[" + i + "] (" + elementNames[i]
                //    + ") = " + value + " -> " + elements[i]);
              }
            }

            // If old format (6 elements), set Z element to 0
            if (numElements == 6) {
              elements[6] = 0.0; // Z element defaults to 0
              if (molecule.contains("+") || molecule.contains("-")) {
                System.out.println("DATABASE LOADING - Old format detected, setting Z to 0.0");
              }
            }

            // Debug logging for ionic species
            if (molecule.contains("+") || molecule.contains("-")) {
              System.out.println("DATABASE LOADING - Final elements for " + molecule + ": O="
                  + elements[0] + ", N=" + elements[1] + ", C=" + elements[2] + ", H=" + elements[3]
                  + ", S=" + elements[4] + ", Ar=" + elements[5] + ", Z=" + elements[6]);
            }

            double[] heatCapCoeffs = new double[4];
            int heatCapStartIndex = numElements + 1; // 7 for new format, 6 for old format
            for (int i = 0; i < 4; i++) {
              String value = parts[i + heatCapStartIndex].trim().replace(",", ".");
              heatCapCoeffs[i] = Double.parseDouble(value);
            }

            int thermoStartIndex = heatCapStartIndex + 4;
            String deltaHf298Str = parts[thermoStartIndex].trim().replace(",", ".");
            String deltaGf298Str = parts[thermoStartIndex + 1].trim().replace(",", ".");
            String deltaSf298Str = parts[thermoStartIndex + 2].trim().replace(",", ".");
            double deltaHf298 = Double.parseDouble(deltaHf298Str);
            double deltaGf298 = Double.parseDouble(deltaGf298Str);
            double deltaSf298 = Double.parseDouble(deltaSf298Str);

            // Get extra coefficients if available, default to NaN array if not found
            double[] defaultCoeffs = new double[12];
            for (int k = 0; k < 12; k++) {
              defaultCoeffs[k] = Double.NaN;
            }
            double[] coeffs = extraCoeffMap.getOrDefault(molecule.toLowerCase(), defaultCoeffs);
            GibbsComponent component = new GibbsComponent(molecule, elements, heatCapCoeffs,
                deltaHf298, deltaGf298, deltaSf298, coeffs.length > 0 ? coeffs[0] : Double.NaN,
                coeffs.length > 1 ? coeffs[1] : Double.NaN,
                coeffs.length > 2 ? coeffs[2] : Double.NaN,
                coeffs.length > 3 ? coeffs[3] : Double.NaN,
                coeffs.length > 4 ? coeffs[4] : Double.NaN,
                coeffs.length > 5 ? coeffs[5] : Double.NaN,
                coeffs.length > 6 ? coeffs[6] : Double.NaN,
                coeffs.length > 7 ? coeffs[7] : Double.NaN,
                coeffs.length > 8 ? coeffs[8] : Double.NaN,
                coeffs.length > 9 ? coeffs[9] : Double.NaN,
                coeffs.length > 10 ? coeffs[10] : Double.NaN,
                coeffs.length > 11 ? coeffs[11] : Double.NaN);
            gibbsDatabase.add(component);
            componentMap.put(molecule.toLowerCase(), component);
            logger.debug("Loaded component: " + molecule);
          } catch (NumberFormatException e) {
            logger.warn("Error parsing line: " + line + " - " + e.getMessage());
          }
        }
      }
      scanner.close();
      logger.info("Loaded " + gibbsDatabase.size() + " components from Gibbs database");
    } catch (Exception e) {
      logger.error("Error loading Gibbs database: " + e.getMessage());
    }
  }

  /**
   * Set whether to use all database species or only species in the system.
   *
   * @param useAllDatabaseSpecies true to use all database species, false to use only system species
   */
  public void setUseAllDatabaseSpecies(boolean useAllDatabaseSpecies) {
    this.useAllDatabaseSpecies = useAllDatabaseSpecies;
  }

  /**
   * Get whether using all database species or only species in the system.
   *
   * @return true if using all database species, false if using only system species
   */
  public boolean getUseAllDatabaseSpecies() {
    return useAllDatabaseSpecies;
  }

  /**
   * Get the method used for Gibbs minimization.
   *
   * @return The method name
   */
  public String getMethod() {
    return method;
  }

  /**
   * Set the method used for Gibbs minimization.
   *
   * @param method The method name
   */
  public void setMethod(String method) {
    this.method = method;
  }

  /**
   * Get the Lagrange contributions (legacy method).
   *
   * @return Map of Lagrange contributions
   */
  public Map<String, Double> getLagrangeContributions() {
    return new HashMap<>(lagrangeContributions);
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    // Clear thread-local temp system to avoid cross-test contamination
    tempFugacitySystem.remove();
    system = getInletStream().getThermoSystem().clone();



    // Store initial moles for each component
    initialMoles.clear();
    inlet_mole.clear();
    outlet_mole.clear();

    for (int i = 0; i < system.getNumberOfComponents(); i++) {
      String compName = system.getComponent(i).getComponentName();
      double moles = system.getComponent(i).getNumberOfMolesInPhase();
      initialMoles.put(compName, moles);
      inlet_mole.add(moles);
    }

    // Calculate initial element mole balance
    calculateElementMoleBalance(system, elementMoleBalanceIn, true);

    // Perform Gibbs minimization
    if (useAllDatabaseSpecies) {
      // Add all database species to system
      for (GibbsComponent component : gibbsDatabase) {
        try {
          system.addComponent(component.getMolecule(), 1E-6);
        } catch (Exception e) {
          logger
              .debug("Could not add component " + component.getMolecule() + ": " + e.getMessage());
        }
      }
    }


    // Minimize Gibbs energy
    performGibbsMinimization(system);

    // Enforce minimum concentrations
    enforceMinimumConcentrations(system);

    // Store final moles for each component
    finalMoles.clear();
    processedComponents.clear();
    outlet_mole.clear(); // Clear and repopulate with actual final values

    processedComponentIndexMap.clear();
    for (int i = 0; i < system.getNumberOfComponents(); i++) {
      String compName = system.getComponent(i).getComponentName();
      double moles = system.getComponent(i).getNumberOfMolesInPhase();
      finalMoles.put(compName, moles);
      processedComponents.add(compName);
      processedComponentIndexMap.put(compName, i);

      // Update outlet_mole with actual final values, enforcing minimum
      double outletMoles = Math.max(moles, 1E-6);
      outlet_mole.add(outletMoles);
    }

    // Calculate final element mole balance
    calculateElementMoleBalance(system, elementMoleBalanceOut, false);

    // Calculate difference (outlet - inlet)
    for (int i = 0; i < elementNames.length; i++) {
      elementMoleBalanceDiff[i] = elementMoleBalanceOut[i] - elementMoleBalanceIn[i];
    }

    // Debug logging for element balance
    logger.debug("=== Element Balance (mol/sec) ===");
    for (int i = 0; i < elementNames.length; i++) {
      logger.debug(String.format("%s: IN=%.6e, OUT=%.6e, DIFF=%.6e", elementNames[i],
          elementMoleBalanceIn[i], elementMoleBalanceOut[i], elementMoleBalanceDiff[i]));
    }

    // Calculate objective function values
    calculateObjectiveFunctionValues(system);

    solveGibbsEquilibrium();

    // Set outlet stream
    // getOutletStream().setThermoSystem(system);
    getOutletStream().run(id);

    // Mass balance check at the end
    if (!getMassBalanceConverged()) {
      logger.debug(
          "WARNING: Mass balance not converged in GibbsReactor. Consider decreasing the iteration step (damping factor) for better closure.");
    }
  }



  /**
   * Perform Gibbs free energy minimization.
   *
   * @param system The thermodynamic system
   */
  private void performGibbsMinimization(SystemInterface system) {
    // Set iteration to 1
    final int iteration = 1;

    // Create initial guess for moles
    final Map<String, Double> initialGuess = new HashMap<>();
    for (int i = 0; i < system.getNumberOfComponents(); i++) {
      String compName = system.getComponent(i).getComponentName();
      initialGuess.put(compName, system.getComponent(i).getNumberOfMolesInPhase());
    }

    // Check if component is in system
    for (int i = 0; i < system.getNumberOfComponents(); i++) {
      String compName = system.getComponent(i).getComponentName();

      if (initialGuess.containsKey(compName)) {
        double currentMoles = system.getComponent(i).getNumberOfMolesInPhase();
        if (currentMoles < 1E-6) {
          system.addComponent(i, 1E-6 - currentMoles, 0);
        }
      }
    }


    logger.info("Gibbs minimization completed for iteration " + iteration);
  }

  /**
   * Calculate element mole balance for a system.
   *
   * @param system The thermodynamic system
   * @param elementBalance Array to store the element balance
   * @param isInput true if this is input balance, false if output balance
   */
  private void calculateElementMoleBalance(SystemInterface system, double[] elementBalance,
      boolean isInput) {
    // Reset balance
    for (int i = 0; i < elementBalance.length; i++) {
      elementBalance[i] = 0.0;
    }

    // Process each component using appropriate mole list
    for (int i = 0; i < system.getNumberOfComponents(); i++) {
      String compName = system.getComponent(i).getComponentName();

      // Use inlet_mole for input, outlet_mole for output
      double moles;
      if (isInput) {
        moles = (i < inlet_mole.size()) ? inlet_mole.get(i) : 0.0;
      } else {
        moles = (i < outlet_mole.size()) ? outlet_mole.get(i) : 0.0;
      }

      // Get element composition from database
      GibbsComponent comp = componentMap.get(compName.toLowerCase());
      if (comp == null) {
        logger.debug("WARNING: Component '" + compName
            + "' not found in gibbsReactDatabase. Skipping element balance for this component.");
        continue;
      }
      double[] elements = comp.getElements();
      logger.debug("Component " + compName + " elements: O=" + elements[0] + ", N=" + elements[1]
          + ", C=" + elements[2] + ", H=" + elements[3] + ", S=" + elements[4] + ", Ar="
          + elements[5] + ", Z=" + elements[6]);
      for (int j = 0; j < elementNames.length; j++) {
        elementBalance[j] += elements[j] * moles;
      }
    }
  }

  /**
   * Calculate objective function values for each component.
   *
   * @param system The thermodynamic system
   */
  private void calculateObjectiveFunctionValues(SystemInterface system) {
    objectiveFunctionValues.clear();

    double T = system.getTemperature();
    double RT = 8.314462618e-3 * T; // kJ/mol

    // Calculate total moles from outlet_mole list
    double totalMoles = 0.0;
    for (Double moles : outlet_mole) {
      totalMoles += moles;
    }

    // Calculate for each component
    for (int i = 0; i < system.getNumberOfComponents(); i++) {
      String compName = system.getComponent(i).getComponentName();

      // Use outlet_mole for formulas
      double moles = (i < outlet_mole.size()) ? outlet_mole.get(i) : 1E-6;

      // Get Gibbs component
      GibbsComponent comp = componentMap.get(compName.toLowerCase());
      if (comp == null) {
        // System.err.println("WARNING: Component '" + compName
        // + "' not found in gibbsReactDatabase. Skipping objective function value for this
        // component.");
        continue;
      }
      // Calculate Gibbs energy of formation
      double Gf0 = comp.calculateGibbsEnergy(T, i);

      // Calculate fugacity coefficient (assume 1 for now)
      double[] phi = getFugacityCoefficient(0);

      // Calculate mole fraction
      double yi = moles / totalMoles;

      // Calculate Lagrange multiplier contribution
      double lagrangeSum = 0.0;
      double[] elements = comp.getElements();
      for (int j = 0; j < lambda.length; j++) {
        lagrangeSum += lambda[j] * elements[j];
      }

      // Calculate objective function: F = Gf0 + RT*ln(phi) + RT*ln(yi) + RT*ln(P/Pref) -
      // lagrangeSum
      double F = Gf0 + RT * Math.log(phi[i]) + RT * Math.log(yi)
          + RT * Math.log(system.getPressure("bara") / 1.0) - lagrangeSum;
      objectiveFunctionValues.put(compName, F);
    }
  }

  /**
   * Set a Lagrange multiplier value.
   *
   * @param index The element index (0=O, 1=N, 2=C, 3=H, 4=S, 5=Ar)
   * @param value The Lagrange multiplier value
   */
  public void setLagrangeMultiplier(int index, double value) {
    if (index >= 0 && index < lambda.length) {
      lambda[index] = value;
    }
  }

  /**
   * Get the Lagrange multiplier values.
   *
   * @return Array of Lagrange multiplier values [O, N, C, H, S, Ar]
   */
  public double[] getLagrangianMultipliers() {
    return lambda.clone();
  }

  /**
   * Get the element names.
   *
   * @return Array of element names
   */
  public String[] getElementNames() {
    return elementNames.clone();
  }

  /**
   * Get the Lagrange multiplier contributions for each component.
   *
   * @return Map with component names and their Lagrange multiplier contributions
   */
  public Map<String, Map<String, Double>> getLagrangeMultiplierContributions() {
    Map<String, Map<String, Double>> contributions = new HashMap<>();

    // Use the components that were actually processed in the last run
    List<String> componentsToProcess =
        processedComponents.isEmpty() ? new ArrayList<>(finalMoles.keySet()) : processedComponents;

    for (String compName : componentsToProcess) {
      Map<String, Double> compContributions = new HashMap<>();

      // Get element composition from database
      GibbsComponent comp = componentMap.get(compName.toLowerCase());
      if (comp == null) {
        // System.err.println("WARNING: Component '" + compName
        // + "' not found in gibbsReactDatabase. Skipping Lagrange multiplier contributions for this
        // component.");
        continue;
      }
      double[] elements = comp.getElements();
      double totalContribution = 0.0;

      for (int i = 0; i < elementNames.length; i++) {
        double contribution = lambda[i] * elements[i];
        compContributions.put(elementNames[i], contribution);
        totalContribution += contribution;
      }

      compContributions.put("TOTAL", totalContribution);
      contributions.put(compName, compContributions);
    }

    return contributions;
  }

  /**
   * Get the objective function values for each component.
   *
   * @return Map with component names and their objective function values
   */
  public Map<String, Double> getObjectiveFunctionValues() {
    return new HashMap<>(objectiveFunctionValues);
  }

  /**
   * Get the element mole balance for input streams.
   *
   * @return Array of total moles for each element in input [O, N, C, H, S, Ar]
   */
  public double[] getElementMoleBalanceIn() {
    return elementMoleBalanceIn.clone();
  }

  /**
   * Get the element mole balance for output streams.
   *
   * @return Array of total moles for each element in output [O, N, C, H, S, Ar]
   */
  public double[] getElementMoleBalanceOut() {
    return elementMoleBalanceOut.clone();
  }

  /**
   * Get the element mole balance difference (in - out).
   *
   * @return Array of mole differences for each element [O, N, C, H, S, Ar]
   */
  public double[] getElementMoleBalanceDiff() {
    return elementMoleBalanceDiff.clone();
  }

  /**
   * Get detailed mole balance information for each component.
   *
   * @return Map with component names and their element contributions to mole balance
   */
  public Map<String, Map<String, Double>> getDetailedMoleBalance() {
    Map<String, Map<String, Double>> detailedBalance = new HashMap<>();

    for (int i = 0; i < processedComponents.size(); i++) {
      String compName = processedComponents.get(i);
      GibbsComponent comp = componentMap.get(compName.toLowerCase());

      // Use inlet_mole and outlet_mole lists
      Double molesIn = (i < inlet_mole.size()) ? inlet_mole.get(i) : 0.0;
      Double molesOut = (i < outlet_mole.size()) ? outlet_mole.get(i) : 1E-6;

      if (comp == null) {
        // System.err.println("WARNING: Component '" + compName
        // + "' not found in gibbsReactDatabase. Skipping detailed mole balance for this
        // component.");
        continue;
      }
      Map<String, Double> componentBalance = new HashMap<>();
      final double[] elements = comp.getElements(); // [O, N, C, H, S, Ar]

      // Store initial and final moles
      componentBalance.put("MOLES_IN", molesIn);
      componentBalance.put("MOLES_OUT", molesOut);
      componentBalance.put("MOLES_DIFF", molesOut - molesIn); // outlet - inlet for mass balance

      // Calculate element contributions using outlet - inlet difference
      for (int j = 0; j < elementNames.length; j++) {
        double elementIn = elements[j] * molesIn;
        double elementOut = elements[j] * molesOut;
        double elementDiff = elementOut - elementIn; // outlet - inlet

        componentBalance.put(elementNames[j] + "_IN", elementIn);
        componentBalance.put(elementNames[j] + "_OUT", elementOut);
        componentBalance.put(elementNames[j] + "_DIFF", elementDiff);
      }

      detailedBalance.put(compName, componentBalance);
    }

    return detailedBalance;
  }

  /**
   * Calculate the objective minimization vector. This vector contains the F values for each
   * component and the mass balance constraints. The system is in equilibrium when this vector is
   * zero. Only includes elements that are actually present in the system.
   */
  private void calculateObjectiveMinimizationVector() {
    // Find which elements are actually present in the system
    List<Integer> activeElements = findActiveElements();

    int numComponents = processedComponents.size();
    int numActiveElements = activeElements.size();

    // Create vector: [F_values, mass_balance_constraints]
    objectiveMinimizationVector = new double[numComponents + numActiveElements];
    objectiveMinimizationVectorLabels.clear();

    // First part: F values for each component
    for (int i = 0; i < numComponents; i++) {
      String compName = processedComponents.get(i);
      Double fValue = objectiveFunctionValues.get(compName);
      objectiveMinimizationVector[i] = (fValue != null) ? fValue : 0.0;
      objectiveMinimizationVectorLabels.add("F_" + compName);
    }

    // Second part: Mass balance constraints (only for active elements)
    for (int i = 0; i < numActiveElements; i++) {
      int elementIndex = activeElements.get(i);
      objectiveMinimizationVector[numComponents + i] = elementMoleBalanceDiff[elementIndex];
      objectiveMinimizationVectorLabels.add("Balance_" + elementNames[elementIndex]);
    }
  }

  /**
   * Get the objective minimization vector.
   *
   * @return The objective minimization vector
   */
  public double[] getObjectiveMinimizationVector() {
    calculateObjectiveMinimizationVector();
    return objectiveMinimizationVector.clone();
  }

  /**
   * Get the labels for the objective minimization vector.
   *
   * @return List of labels for each element in the objective minimization vector
   */
  public List<String> getObjectiveMinimizationVectorLabels() {
    return new ArrayList<>(objectiveMinimizationVectorLabels);
  }

  /**
   * Calculate the Jacobian matrix for the Newton-Raphson method. The Jacobian represents the
   * derivatives of the objective function with respect to the variables. Only includes elements
   * that are actually present in the system to avoid singular matrices.
   */
  private void calculateJacobian() {
    if (processedComponents.isEmpty()) {
      return;
    }

    // Find which elements are actually present in the system
    List<Integer> activeElements = findActiveElements();

    int numComponents = processedComponents.size();
    int numActiveElements = activeElements.size();
    int totalVars = numComponents + numActiveElements; // components + active Lagrange multipliers

    jacobianMatrix = new double[totalVars][totalVars];
    jacobianRowLabels.clear();
    jacobianColLabels.clear();

    // Set up labels
    for (int i = 0; i < numComponents; i++) {
      jacobianRowLabels.add("F_" + processedComponents.get(i));
      jacobianColLabels.add("n_" + processedComponents.get(i));
    }
    for (int i = 0; i < numActiveElements; i++) {
      int elementIndex = activeElements.get(i);
      jacobianRowLabels.add("Balance_" + elementNames[elementIndex]);
      jacobianColLabels.add("lambda_" + elementNames[elementIndex]);
    }

    SystemInterface system = getOutletStream().getThermoSystem();
    double T = system.getTemperature();
    double RT = 8.314462618e-3 * T; // kJ/mol


    // Calculate total moles for mole fraction derivatives using outlet_mole
    double totalMoles = 0.0;
    for (Double moles : outlet_mole) {
      totalMoles += moles;
    }

    // Fill Jacobian matrix
    for (int i = 0; i < numComponents; i++) {
      String compI = processedComponents.get(i);

      // Use outlet_mole for calculations, but with a minimum value to avoid numerical issues
      double ni = (i < outlet_mole.size()) ? outlet_mole.get(i) : 1E-6;
      double niForJacobian = Math.max(ni, 1e-6); // Use minimum of 1e-6 for Jacobian calculation
      system.init(3);
      for (int j = 0; j < numComponents; j++) {
        if (i == j) {
          // Diagonal elements: ∂f_i/∂n_i = RT * (1/n_i - 1/n_total)
          jacobianMatrix[i][j] = RT * (1.0 / niForJacobian - 1.0 / totalMoles
              + system.getPhase(0).getComponent(i).getdfugdn(j));
        } else {
          // Off-diagonal elements: ∂f_i/∂n_j = -RT/n_total
          jacobianMatrix[i][j] = -RT / totalMoles + system.getPhase(0).getComponent(i).getdfugdn(j);
        }
      }

      // Derivatives with respect to Lagrange multipliers (only active elements)
      GibbsComponent gibbsComp = componentMap.get(compI.toLowerCase());
      if (gibbsComp != null) {
        double[] elements = gibbsComp.getElements();
        for (int k = 0; k < numActiveElements; k++) {
          int elementIndex = activeElements.get(k);
          jacobianMatrix[i][numComponents + k] = -elements[elementIndex];
        }
      }
    }

    // Mass balance constraint derivatives (only for active elements)
    for (int i = 0; i < numActiveElements; i++) {
      int elementIndex = activeElements.get(i);
      for (int j = 0; j < numComponents; j++) {
        String compName = processedComponents.get(j);
        GibbsComponent gibbsComp = componentMap.get(compName.toLowerCase());
        if (gibbsComp != null) {
          double[] elements = gibbsComp.getElements();
          jacobianMatrix[numComponents + i][j] = elements[elementIndex];
        }
      }

      // Derivatives with respect to Lagrange multipliers are zero
      for (int k = 0; k < numActiveElements; k++) {
        jacobianMatrix[numComponents + i][numComponents + k] = 0.0;
      }
    }

    // Calculate inverse
    jacobianInverse = calculateJacobianInverse();
  }

  /**
   * Get the Jacobian matrix.
   *
   * @return The Jacobian matrix
   */
  public double[][] getJacobianMatrix() {
    calculateJacobian();

    if (jacobianMatrix == null) {
      return null;
    }

    // Return a deep copy to prevent external modification
    double[][] copy = new double[jacobianMatrix.length][];
    for (int i = 0; i < jacobianMatrix.length; i++) {
      copy[i] = jacobianMatrix[i].clone();
    }
    return copy;
  }

  /**
   * Get the Jacobian row labels.
   *
   * @return List of row labels for the Jacobian matrix
   */
  public List<String> getJacobianRowLabels() {
    return new ArrayList<>(jacobianRowLabels);
  }

  /**
   * Get the Jacobian column labels.
   *
   * @return List of column labels for the Jacobian matrix
   */
  public List<String> getJacobianColLabels() {
    return new ArrayList<>(jacobianColLabels);
  }

  /**
   * Get the Jacobian inverse matrix.
   *
   * @return The Jacobian inverse matrix, or null if it couldn't be calculated
   */
  public double[][] getJacobianInverse() {
    if (jacobianInverse == null) {
      return null;
    }

    // Return a deep copy to prevent external modification
    double[][] copy = new double[jacobianInverse.length][];
    for (int i = 0; i < jacobianInverse.length; i++) {
      copy[i] = jacobianInverse[i].clone();
    }
    return copy;
  }

  /**
   * Calculate the inverse of the Jacobian matrix using JAMA Matrix library.
   *
   * @return Inverse matrix, or null if matrix is singular
   */
  private double[][] calculateJacobianInverse() {
    if (jacobianMatrix == null) {
      return null;
    }
    try {
      // Only create SimpleMatrix objects once per call, not in a loop
      SimpleMatrix ejmlMatrix = new SimpleMatrix(jacobianMatrix);
      SimpleMatrix inverseMatrix = ejmlMatrix.invert();
      int nRows = inverseMatrix.numRows();
      int nCols = inverseMatrix.numCols();
      double[][] result = new double[nRows][nCols];
      double[] data = inverseMatrix.getDDRM().getData();
      for (int i = 0; i < nRows; i++) {
        for (int j = 0; j < nCols; j++) {
          result[i][j] = data[i * nCols + j];
        }
      }
      return result;
    } catch (RuntimeException e) {
      logger.warn("Jacobian matrix is singular or nearly singular: " + e.getMessage());
      return null;
    }
  }

  /**
   * Enforce minimum concentration threshold to prevent numerical issues. If any component has moles
   * less than 1E-6, it will be set to 1E-6.
   *
   * @param system The thermodynamic system to check and modify
   */
  private void enforceMinimumConcentrations(SystemInterface system) {
    double minConcentration = 1e-6;
    boolean modified = false;

    for (int i = 0; i < system.getNumberOfComponents(); i++) {
      String compName = system.getComponent(i).getComponentName();
      // Only enforce minimum if component is in the Gibbs database
      if (componentMap.get(compName.toLowerCase()) == null) {
        continue;
      }
      double currentMoles = system.getComponent(i).getNumberOfMolesInPhase();

      if (currentMoles < minConcentration) {
        logger.info("Component " + compName + " has very low concentration (" + currentMoles
            + "), setting to minimum: " + minConcentration);
        system.addComponent(i, minConcentration - currentMoles, 0);
        modified = true;
      }
    }

    if (modified) {
      // Reinitialize the system after modifying concentrations
      logger.info("System reinitialized after enforcing minimum concentrations");
    }
  }

  /**
   * Get the inlet mole list.
   *
   * @return List of inlet moles for each component
   */
  public List<Double> getInletMole() {
    return Collections.unmodifiableList(inlet_mole);
  }

  /**
   * Get the outlet mole list.
   *
   * @return List of outlet moles for each component (with 0 values replaced by 1E-6)
   */
  public List<Double> getOutletMole() {
    return Collections.unmodifiableList(outlet_mole);
  }

  /**
   * Get the inlet mole list for debugging.
   *
   * @return List of inlet moles
   */
  public List<Double> getInletMoles() {
    return Collections.unmodifiableList(inlet_mole);
  }

  /**
   * Get the outlet mole list for debugging.
   *
   * @return List of outlet moles
   */
  public List<Double> getOutletMoles() {
    return Collections.unmodifiableList(outlet_mole);
  }

  /**
   * Print loaded database components for debugging.
   */
  public void printDatabaseComponents() {
    // System.out.println("\n=== Loaded Database Components ===");
    // System.out.println("Total components in database: " + gibbsDatabase.size());

    for (GibbsComponent comp : gibbsDatabase) {
      String molecule = comp.getMolecule();
      double[] elements = comp.getElements();
      // System.out.printf(" %s: O=%.1f, N=%.1f, C=%.1f, H=%.1f, S=%.1f, Ar=%.1f%n", molecule,
      // elements[0], elements[1], elements[2], elements[3], elements[4], elements[5]);
    }

    // System.out.println("\nComponent map keys:");
    for (String key : componentMap.keySet()) {
      // System.out.println(" '" + key + "'");
    }
  }

  /**
   * Find which elements are actually present in the system (have non-zero coefficients).
   *
   * @return List of indices of active elements
   */
  private List<Integer> findActiveElements() {
    List<Integer> activeElements = new ArrayList<>();

    // Check each element to see if any component has a non-zero coefficient
    for (int elementIndex = 0; elementIndex < elementNames.length; elementIndex++) {
      boolean elementPresent = false;

      for (String compName : processedComponents) {
        GibbsComponent comp = componentMap.get(compName.toLowerCase());
        if (comp == null) {
          // System.err.println("WARNING: Component '" + compName
          // + "' not found in gibbsReactDatabase. Skipping active element check for this
          // component.");
          continue;
        }
        double[] elements = comp.getElements();
        if (Math.abs(elements[elementIndex]) > 1E-6) {
          elementPresent = true;
          break;
        }
      }

      if (elementPresent) {
        activeElements.add(elementIndex);
      }
    }

    return activeElements;
  }

  /**
   * Get the indices of active elements (elements that have non-zero coefficients in any component).
   *
   * @return List of active element indices
   */
  private List<Integer> getActiveElementIndices() {
    List<Integer> activeIndices = new ArrayList<>();

    for (int elementIndex = 0; elementIndex < elementNames.length; elementIndex++) {
      boolean hasNonZero = false;

      for (String compName : processedComponents) {
        GibbsComponent comp = componentMap.get(compName.toLowerCase());
        if (comp == null) {
          // System.err.println("WARNING: Component '" + compName
          // + "' not found in gibbsReactDatabase. Skipping active element index check for this
          // component.");
          continue;
        }
        double[] elements = comp.getElements();
        if (Math.abs(elements[elementIndex]) > 1e-10) {
          hasNonZero = true;
          break;
        }
      }

      if (hasNonZero) {
        activeIndices.add(elementIndex);
      }
    }

    return activeIndices;
  }

  /**
   * Verify that the Jacobian inverse is correct by multiplying J * J^-1. Should return the identity
   * matrix if the inverse is correct.
   *
   * @return True if the inverse is correct (within tolerance)
   */
  public boolean verifyJacobianInverse() {
    if (jacobianMatrix == null || jacobianInverse == null) {
      return false;
    }

    try {
      // Only create SimpleMatrix objects once per call, not in a loop
      SimpleMatrix jacobianMatrixEJML = new SimpleMatrix(jacobianMatrix);
      SimpleMatrix jacobianInverseEJML = new SimpleMatrix(jacobianInverse);
      SimpleMatrix resultMatrix = jacobianMatrixEJML.mult(jacobianInverseEJML);
      double[] resultData = resultMatrix.getDDRM().getData();
      double tolerance = 1e-10;
      int n = jacobianMatrix.length;
      for (int i = 0; i < n; i++) {
        for (int j = 0; j < n; j++) {
          double value = resultData[i * n + j];
          double expected = (i == j) ? 1.0 : 0.0;
          if (Math.abs(value - expected) > tolerance) {
            logger.warn("Jacobian inverse verification failed at [" + i + "," + j + "]: "
                + "expected " + expected + ", got " + value);
            return false;
          }
        }
      }
      return true;
    } catch (RuntimeException e) {
      logger.warn("Error during Jacobian inverse verification: " + e.getMessage());
      return false;
    }
  }

  /**
   * Perform one Newton-Raphson iteration step to calculate the delta vector (dX). Uses the formula:
   * dX = -J^(-1) * F where J is the Jacobian matrix and F is the objective function vector.
   *
   * @return The delta vector (dX) for updating variables, or null if calculation fails
   */
  public double[] performNewtonRaphsonIteration() {
    // Calculate the Jacobian matrix and its inverse
    calculateJacobian();

    if (jacobianMatrix == null || jacobianInverse == null) {
      logger.warn("Cannot perform Newton-Raphson iteration: Jacobian or its inverse is null");
      return null;
    }

    // Get the objective function vector F
    double[] objectiveVector = getObjectiveMinimizationVector();

    if (objectiveVector == null || objectiveVector.length != jacobianInverse.length) {
      logger.warn("Objective vector size mismatch with Jacobian matrix");
      return null;
    }

    try {
      // Only create SimpleMatrix objects once per call, not in a loop
      SimpleMatrix jacobianInverseEJML = new SimpleMatrix(jacobianInverse);
      SimpleMatrix objectiveVectorEJML =
          new SimpleMatrix(objectiveVector.length, 1, true, objectiveVector);
      SimpleMatrix deltaXMatrix = jacobianInverseEJML.mult(objectiveVectorEJML).scale(-1.0);
      int nRows = deltaXMatrix.numRows();
      double[] result = new double[nRows];
      double[] data = deltaXMatrix.getDDRM().getData();
      for (int i = 0; i < nRows; i++) {
        result[i] = data[i];
      }
      return result;
    } catch (RuntimeException e) {
      logger.warn("Error during Newton-Raphson iteration calculation: " + e.getMessage());
      return null;
    }
  }

  /**
   * Perform a Newton-Raphson iteration update. Updates outlet compositions with damping factor and
   * Lagrange multipliers directly.
   *
   * @param deltaX The delta vector from Newton-Raphson iteration
   * @param alphaComposition Damping factor for composition updates (e.g., 0.0001)
   * @return True if update was successful, false otherwise
   */
  public boolean performIterationUpdate(double[] deltaX, double alphaComposition) {
    if (deltaX == null || outlet_mole.isEmpty()) {
      logger.warn("Cannot perform iteration update: deltaX or outlet_mole is null/empty");
      return false;
    }

    int numComponents = processedComponents.size();
    List<Integer> activeElementIndices = getActiveElementIndices();
    int numActiveElements = activeElementIndices.size();

    if (deltaX.length != numComponents + numActiveElements) {
      logger.warn("Delta vector size mismatch: expected " + (numComponents + numActiveElements)
          + ", got " + deltaX.length);
      return false;
    }

    // Update outlet compositions with damping factor
    // System.out.println("\n=== Updating Outlet Compositions ===");
    deltaNorm = 0.0;
    for (int i = 0; i < numComponents; i++) {
      String compName = processedComponents.get(i);
      // Only update if component is in the Gibbs database
      if (componentMap.get(compName.toLowerCase()) == null) {
        // Not in database, skip update to keep moles unchanged
        continue;
      }
      double oldValue = outlet_mole.get(i);
      double deltaComposition = deltaX[i];
      double newValue = oldValue + deltaComposition * alphaComposition;

      // Ensure non-negative values and minimum concentration
      newValue = Math.max(newValue, 1e-15);

      outlet_mole.set(i, newValue);

      // System.out.printf(" %s: %12.6e → %12.6e (Δ = %12.6e, α*Δ = %12.6e)%n",
      // compName, oldValue, newValue, deltaComposition,
      // deltaComposition * alphaComposition);
      deltaNorm += Math.pow(deltaComposition * alphaComposition, 2);
    }
    deltaNorm = Math.sqrt(deltaNorm);

    // Update Lagrange multipliers directly (no damping)
    // System.out.println("\n=== Updating Lagrange Multipliers ===");
    for (int i = 0; i < numActiveElements; i++) {
      int elementIndex = activeElementIndices.get(i);
      double oldValue = lambda[elementIndex];
      double deltaLambda = deltaX[numComponents + i];
      double newValue = oldValue + deltaLambda;

      lambda[elementIndex] = newValue;

      // System.out.printf(" λ[%s]: %12.6e → %12.6e (Δ = %12.6e)%n", elementNames[elementIndex],
      // oldValue, newValue, deltaLambda);
    }
    deltaNorm = Math.sqrt(deltaNorm);

    // Show mass balance for each element
    // System.out.println("\n=== Mass Balance (element-wise, OUT - IN) ===");
    // for (int i = 0; i < elementNames.length; i++) {
    // System.out.printf(" %s: %12.6e\n", elementNames[i], elementMoleBalanceDiff[i]);
    // }

    // Show total norm of delta vector
    // System.out.printf("\n=== Total Norm of Δ (composition): %12.6e ===\n", deltaNorm);


    // System.out.printf("\n=== Current Temperature: %.4f K ===\n", system.getTemperature());

    // Print enthalpy of reaction after temperature
    // System.out.printf("\n=== Enthalpy of Reaction: %.6f kJ ===\n", enthalpyOfReactions);

    // Update the system with new compositions
    return updateSystemWithNewCompositions();
  }

  /**
   * Update the thermodynamic system with the new outlet compositions.
   *
   * @return True if update was successful, false otherwise
   */
  private boolean updateSystemWithNewCompositions() {
    try {
      //SystemInterface system = getInletStream().getThermoSystem();

      // Update component moles in the system
      for (int i = 0; i < processedComponents.size(); i++) {
        String compName = processedComponents.get(i);
        // Only update if component is in the Gibbs database
        if (componentMap.get(compName.toLowerCase()) == null) {
          // Not in database, skip update to keep moles unchanged
          continue;
        }
        double newMoles = outlet_mole.get(i);

        // Find component index in system
        int compIndex = -1;
        for (int j = 0; j < system.getNumberOfComponents(); j++) {
          if (compName.equals(system.getComponent(j).getComponentName())) {
            compIndex = j;
            break;
          }
        }

        if (compIndex >= 0) {
          // Set new moles
          double currentMoles = system.getComponent(compIndex).getNumberOfMolesInPhase();
          double molesToAdd = newMoles - currentMoles;
          if ((molesToAdd < 0.0) && (Math.abs(Math.abs(molesToAdd) - currentMoles)) < 1e-6) {
            // Prevent removing more moles than present
            molesToAdd = -currentMoles + 1e-6; // leave a tiny amount to avoid zero
          }
          if (Math.abs(molesToAdd) > 1e-15) {
            system.addComponent(compIndex, molesToAdd, 0);
          }
        }
      getOutletStream().setThermoSystem(system);
      }


      // Recalculate objective function values with new compositions and Lagrange multipliers
      calculateObjectiveFunctionValues(system);

      // Recalculate element mole balances
      calculateElementMoleBalance(system, elementMoleBalanceOut, false);
      for (int i = 0; i < elementNames.length; i++) {
        elementMoleBalanceDiff[i] = elementMoleBalanceOut[i] - elementMoleBalanceIn[i];
      }

      // Debug logging for element balance during iterations
      logger.debug("--- Element Balance During Iteration ---");
      for (int i = 0; i < elementNames.length; i++) {
        // Always show Z element, and others only if significant differences
        if (elementNames[i].equals("Z") || Math.abs(elementMoleBalanceDiff[i]) > 1e-10) {
          logger.debug(String.format("%s: IN=%.6e, OUT=%.6e, DIFF=%.6e", elementNames[i],
              elementMoleBalanceIn[i], elementMoleBalanceOut[i], elementMoleBalanceDiff[i]));
        }
      }

      return true;

    } catch (Exception e) {
      logger.error("Error updating system with new compositions: " + e.getMessage());
      return false;
    }
  }

  /**
   * Get the fugacity coefficient array for all components in a specified phase using the current
   * outlet composition. Uses direct phase composition assignment for efficiency.
   *
   * @param phaseNameOrIndex Name or index of the phase (e.g., "gas", "oil", "aqueous", or 0/1/2)
   * @return Fugacity coefficient (phi) array for all components in the specified phase, or
   *         Double.NaN if not found
   */
  public double[] getFugacityCoefficient(Object phaseNameOrIndex) {

    int phaseIndex = 0;
    if (phaseNameOrIndex instanceof Integer) {
      phaseIndex = (Integer) phaseNameOrIndex;
    } else if (phaseNameOrIndex instanceof String) {
      String phaseName = ((String) phaseNameOrIndex).toLowerCase();
      for (int i = 0; i < system.getNumberOfPhases(); i++) {
        String name = system.getPhase(i).getPhaseTypeName().toLowerCase();
        if (name.contains(phaseName)) {
          phaseIndex = i;
          break;
        }
      }
    }

    // Get fugacity coefficients for all components in the selected phase
    int numComponents = system.getNumberOfComponents();
    double[] phiArray = new double[numComponents];
    for (int i = 0; i < numComponents; i++) {
      phiArray[i] = system.getPhase(phaseIndex).getComponent(i).getFugacityCoefficient();
    }
    return phiArray;
  }


  /**
   * Set maximum number of Newton-Raphson iterations.
   *
   * @param maxIterations Maximum number of iterations
   */
  public void setMaxIterations(int maxIterations) {
    this.maxIterations = maxIterations;
  }

  /**
   * Get maximum number of Newton-Raphson iterations.
   *
   * @return Maximum number of iterations
   */
  public int getMaxIterations() {
    return maxIterations;
  }

  /**
   * Set convergence tolerance for Newton-Raphson iterations.
   *
   * @param convergenceTolerance Convergence tolerance
   */
  public void setConvergenceTolerance(double convergenceTolerance) {
    this.convergenceTolerance = convergenceTolerance;
  }

  /**
   * Get convergence tolerance for Newton-Raphson iterations.
   *
   * @return Convergence tolerance
   */
  public double getConvergenceTolerance() {
    return convergenceTolerance;
  }

  /**
   * Set damping factor for composition updates in Newton-Raphson iterations.
   *
   * @param dampingComposition Damping factor (typically 0.0001 to 0.01)
   */
  public void setDampingComposition(double dampingComposition) {
    this.dampingComposition = dampingComposition;
  }

  /**
   * Get damping factor for composition updates.
   *
   * @return Damping factor for composition updates
   */
  public double getDampingComposition() {
    return dampingComposition;
  }

  /**
   * Get actual number of iterations performed.
   *
   * @return Actual iterations performed
   */
  public int getActualIterations() {
    return actualIterations;
  }

  /**
   * Check if iterations converged.
   *
   * @return true if converged, false otherwise
   */
  public boolean hasConverged() {
    return converged;
  }

  /**
   * Get final convergence error.
   *
   * @return Final convergence error
   */
  public double getFinalConvergenceError() {
    return finalConvergenceError;
  }

  /**
   * Solve Gibbs equilibrium using Newton-Raphson iterations with specified step size.
   *
   * @param alphaComposition Step size for composition updates
   * @return true if converged, false otherwise
   */
  public boolean solveGibbsEquilibrium(double alphaComposition) {
    converged = false;
    actualIterations = 0;
    finalConvergenceError = Double.MAX_VALUE;

    logger.info("Starting Gibbs equilibrium solution with Newton-Raphson iterations");
    logger.info("Maximum iterations: " + maxIterations);
    logger.info("Convergence tolerance: " + convergenceTolerance);
    logger.info("Composition step size: " + alphaComposition);

    for (int iteration = 1; iteration <= maxIterations; iteration++) {
      actualIterations = iteration;

      // Calculate current objective function values
      SystemInterface outletSystem = getOutletStream().getThermoSystem();
      calculateObjectiveFunctionValues(outletSystem);

      // Debug log component, Gibbs energy, enthalpy, and entropy for every iteration
      logger.debug("Iteration {} component properties:", iteration);
      for (int i = 0; i < outletSystem.getNumberOfComponents(); i++) {
        String compName = outletSystem.getComponent(i).getComponentName();
        GibbsComponent comp = componentMap.get(compName.toLowerCase());
        if (comp != null) {
          double T = outletSystem.getTemperature();
          double gibbs = comp.calculateGibbsEnergy(T, i);
          double enthalpy = comp.calculateEnthalpy(T, i);
          double entropy = comp.calculateEntropy(T, i);
          logger.debug(String.format(
              "Component: %s, GibbsEnergy: %.2f kJ/mol, Enthalpy: %.2f kJ/mol, Entropy: %.2f kJ/(mol·K)",
              compName, gibbs, enthalpy, entropy));
        }
      }

      // Calculate F vector norm for convergence check
      Map<String, Double> fValues = getObjectiveFunctionValues();
      double fNorm = 0.0;
      for (Double value : fValues.values()) {
        fNorm += value * value;
      }
      fNorm = Math.sqrt(fNorm);

      logger.debug("Iteration " + iteration + ": F vector norm = " + fNorm);

      // Perform Newton-Raphson iteration step
      double[] deltaX = performNewtonRaphsonIteration();

      if (deltaX == null) {
        logger.warn("Newton-Raphson iteration failed at iteration " + iteration);
        finalConvergenceError = fNorm;
        return false;
      }

      // Calculate delta vector norm
      double deltaXNorm = 0.0;
      for (double value : deltaX) {
        deltaXNorm += value * value;
      }
      deltaXNorm = Math.sqrt(deltaXNorm);

      logger.debug("Iteration " + iteration + ": Delta vector norm = " + deltaXNorm);
      logger.debug("deltaXNorm (full update vector): {}", deltaXNorm);

      if (iteration == 1) {
        G = calculateMixtureGibbsEnergy(processedComponents, outlet_mole, componentMap,
            system.getTemperature());
        logger.debug("Initial Gibbs energy G = " + G);
        GOLD = G;
      } else {
        GOLD = G;
        G = calculateMixtureGibbsEnergy(processedComponents, outlet_mole, componentMap,
            system.getTemperature());
        dG = G - GOLD;
        logger.debug("Gibbs energy change dG = " + dG);
      }



      if (energyMode == EnergyMode.ADIABATIC) {
        if (iteration == 1) {
          inletEnthalpy = calculateMixtureEnthalpy(processedComponents, outlet_mole, componentMap,
              system.getTemperature());
          enthalpyOld = inletEnthalpy;
        } else {
          outletEnthalpy = calculateMixtureEnthalpy(processedComponents, outlet_mole, componentMap,
              system.getTemperature());
          double dH;
          dH = outletEnthalpy - enthalpyOld;
          enthalpyOfReactions += dH;
          enthalpyOld = outletEnthalpy;
          double T_out = system.getTemperature() - dH * 1000 / (system.getCp("J/K"));
          dT = Math.abs(T_out - system.getTemperature());
          if (dT > 1000) {
            throw new RuntimeException(
                "Temperature change per iteration (dT) exceeded 1000 K. Please reduce the step of iteration (alphaComposition or damping factor).");
          }
          temperatureChange += dT;
          system.setTemperature(T_out);
          system.init(3);
          this.getOutletStream().getThermoSystem().setTemperature(system.getTemperature());
        }
      }



      // Check convergence (require minimum 100 iterations)
      if ((deltaXNorm < convergenceTolerance && iteration >= 100) || iteration == maxIterations) {// ||
                                                                                                  // (dG
                                                                                                  // >
                                                                                                  // 0
                                                                                                  // &&
                                                                                                  // iteration
                                                                                                  // >=
                                                                                                  // 100))
                                                                                                  // {
        logger.info((deltaXNorm < convergenceTolerance ? "Converged" : "Max iterations reached")
            + " at iteration " + iteration + " with delta norm = " + deltaXNorm);
        converged = deltaXNorm < convergenceTolerance;
        finalConvergenceError = deltaXNorm;
        updateSystemWithNewCompositions();
        this.getOutletStream().getThermoSystem().setTemperature(system.getTemperature());
        if (iteration == maxIterations) {
          logger.warn(
              "Maximum number of iterations reached without convergence. Please increase the maximum number of iterations (maxIterations) and try again.");
        }
        return true;
      }

      // Perform iteration update
      boolean updateSuccess = performIterationUpdate(deltaX, alphaComposition);
      if (!updateSuccess) {
        logger.warn("Iteration update failed at iteration " + iteration);
        finalConvergenceError = deltaXNorm;
        return false;
      }

      // Debug logging for element balance during iterations
      SystemInterface currentOutletSystem = getOutletStream().getThermoSystem();
      calculateElementMoleBalance(currentOutletSystem, elementMoleBalanceOut, false);
      logger.debug("Iteration " + iteration + " element balance:");
      for (int i = 0; i < elementNames.length; i++) {
        double diff = elementMoleBalanceOut[i] - elementMoleBalanceIn[i];
        // Always show Z element, and others only if significant differences
        if (elementNames[i].equals("Z") || Math.abs(diff) > 1e-10) {
          logger.debug(String.format("  %s: IN=%.6e, OUT=%.6e, DIFF=%.6e", elementNames[i],
              elementMoleBalanceIn[i], elementMoleBalanceOut[i], diff));
        }
      }

      finalConvergenceError = deltaXNorm;
    }

    // Maximum iterations reached without convergence
    logger.warn("Maximum iterations (" + maxIterations + ") reached without convergence");
    logger.warn("Final convergence error: " + finalConvergenceError);

    return false;
  }

  /**
   * Solve Gibbs equilibrium using Newton-Raphson iterations with default damping factor.
   *
   * @return true if converged, false otherwise
   */
  public boolean solveGibbsEquilibrium() {
    return solveGibbsEquilibrium(dampingComposition); // Use configured damping factor
  }
}
