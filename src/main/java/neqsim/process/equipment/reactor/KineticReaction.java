package neqsim.process.equipment.reactor;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.thermo.system.SystemInterface;

/**
 * General kinetic reaction model for plug flow and stirred tank reactors.
 *
 * <p>
 * Supports power-law, Langmuir-Hinshelwood-Hougen-Watson (LHHW), and reversible equilibrium rate
 * expressions with Arrhenius temperature dependence. Reaction stoichiometry and kinetic parameters
 * are defined independently, allowing flexible modeling of homogeneous and heterogeneous catalytic
 * reactions.
 * </p>
 *
 * <p>
 * The rate constant follows the modified Arrhenius form:
 * </p>
 *
 * <p>
 * k(T) = A * T^n * exp(-Ea / (R*T))
 * </p>
 *
 * <p>
 * For power-law kinetics the volumetric rate is:
 * </p>
 *
 * <p>
 * r = k(T) * prod(Ci^alpha_i) for irreversible reactions
 * </p>
 *
 * <p>
 * r = k(T) * [prod(Ci^alpha_i) - prod(Cj^beta_j) / Keq(T)] for reversible reactions
 * </p>
 *
 * <p>
 * For LHHW kinetics:
 * </p>
 *
 * <p>
 * r = k(T) * (driving force) / (adsorption term)^m
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * KineticReaction rxn = new KineticReaction("NH3 synthesis");
 * rxn.setRateType(KineticReaction.RateType.POWER_LAW);
 * rxn.addReactant("nitrogen", 1, 1.0);
 * rxn.addReactant("hydrogen", 3, 1.5);
 * rxn.addProduct("ammonia", 2);
 * rxn.setPreExponentialFactor(8.849e14);
 * rxn.setActivationEnergy(170000.0);
 * rxn.setHeatOfReaction(-92400.0);
 * </pre>
 *
 * @author esol
 * @version 1.0
 */
public class KineticReaction implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  /** Universal gas constant [J/(mol*K)]. */
  private static final double R_GAS = 8.31446;

  /**
   * Rate expression type.
   */
  public enum RateType {
    /** Power-law: r = k * prod(Ci^alpha_i). */
    POWER_LAW,
    /** Langmuir-Hinshelwood-Hougen-Watson. */
    LHHW,
    /** Equilibrium-limited (use GibbsReactor instead for full equilibrium). */
    EQUILIBRIUM
  }

  /**
   * Rate basis for heterogeneous vs homogeneous reactions.
   */
  public enum RateBasis {
    /** mol/(m3_reactor * s) - homogeneous volume basis. */
    VOLUME,
    /** mol/(kg_catalyst * s) - heterogeneous catalyst mass basis. */
    CATALYST_MASS,
    /** mol/(m2_catalyst * s) - surface area basis. */
    CATALYST_AREA
  }

  /** Name of this reaction. */
  private String name;

  /** Rate expression type. */
  private RateType rateType = RateType.POWER_LAW;

  /** Rate basis. */
  private RateBasis rateBasis = RateBasis.VOLUME;

  /** Stoichiometric coefficients: negative for reactants, positive for products. */
  private Map<String, Double> stoichiometry = new LinkedHashMap<String, Double>();

  /** Reaction orders (kinetic exponents) for reactants. */
  private Map<String, Double> reactionOrders = new LinkedHashMap<String, Double>();

  /** Reaction orders for products (used in reversible reactions). */
  private Map<String, Double> productOrders = new LinkedHashMap<String, Double>();

  /** Pre-exponential factor A in rate constant [units depend on rate law and basis]. */
  private double preExponentialFactor = 1.0e10;

  /** Activation energy Ea [J/mol]. */
  private double activationEnergy = 100000.0;

  /** Temperature exponent n in modified Arrhenius k = A * T^n * exp(-Ea/RT). */
  private double temperatureExponent = 0.0;

  /** Heat of reaction [J/mol] based on stoichiometry of first reactant. Negative = exothermic. */
  private double heatOfReaction = 0.0;

  /** Whether the reaction is reversible. */
  private boolean reversible = false;

  /**
   * Equilibrium constant correlation coefficients. ln(Keq) = eqCoeffs[0] + eqCoeffs[1]/T +
   * eqCoeffs[2]*ln(T) + eqCoeffs[3]*T.
   */
  private double[] eqCoeffs = new double[4];

  /** LHHW adsorption terms: component name to [Ki, adsorption order]. */
  private Map<String, double[]> adsorptionTerms = new LinkedHashMap<String, double[]>();

  /** LHHW adsorption exponent m (denominator raised to power m). */
  private int adsorptionExponent = 1;

  /** LHHW adsorption pre-exponential factor. */
  private double adsorptionPreExpFactor = 1.0;

  /** LHHW adsorption activation energy [J/mol]. */
  private double adsorptionActivationEnergy = 0.0;

  /**
   * Constructor for KineticReaction.
   *
   * @param name the reaction name
   */
  public KineticReaction(String name) {
    this.name = name;
  }

  /**
   * Add a reactant with stoichiometric coefficient and reaction order.
   *
   * @param componentName component name matching NeqSim database
   * @param stoichCoeff positive stoichiometric coefficient (stored internally as negative)
   * @param order kinetic order for this species in the rate expression
   */
  public void addReactant(String componentName, double stoichCoeff, double order) {
    stoichiometry.put(componentName, -Math.abs(stoichCoeff));
    reactionOrders.put(componentName, order);
  }

  /**
   * Add a product with stoichiometric coefficient.
   *
   * @param componentName component name matching NeqSim database
   * @param stoichCoeff positive stoichiometric coefficient
   */
  public void addProduct(String componentName, double stoichCoeff) {
    stoichiometry.put(componentName, Math.abs(stoichCoeff));
  }

  /**
   * Add a product with stoichiometric coefficient and reverse reaction order.
   *
   * @param componentName component name matching NeqSim database
   * @param stoichCoeff positive stoichiometric coefficient
   * @param reverseOrder kinetic order in reverse rate expression (for reversible reactions)
   */
  public void addProduct(String componentName, double stoichCoeff, double reverseOrder) {
    stoichiometry.put(componentName, Math.abs(stoichCoeff));
    productOrders.put(componentName, reverseOrder);
  }

  /**
   * Calculate the reaction rate at given conditions.
   *
   * <p>
   * For POWER_LAW: r = k(T) * prod(Ci^alpha_i) [- prod(Cj^beta_j)/Keq(T) if reversible]
   * </p>
   *
   * <p>
   * For LHHW: r = k(T) * drivingForce / (adsorptionTerm)^m
   * </p>
   *
   * @param system the thermodynamic system with current T, P, composition
   * @param phaseIndex phase to evaluate concentrations in (0=combined, typically 0 or gas phase)
   * @return reaction rate in units consistent with rateBasis
   */
  public double calculateRate(SystemInterface system, int phaseIndex) {
    double temperature = system.getTemperature();
    double rateConstant = calculateRateConstant(temperature);

    if (rateType == RateType.POWER_LAW || rateType == RateType.EQUILIBRIUM) {
      return calculatePowerLawRate(system, phaseIndex, rateConstant, temperature);
    } else if (rateType == RateType.LHHW) {
      return calculateLHHWRate(system, phaseIndex, rateConstant, temperature);
    }
    return 0.0;
  }

  /**
   * Calculate the Arrhenius rate constant at given temperature.
   *
   * <p>
   * k(T) = A * T^n * exp(-Ea / (R*T))
   * </p>
   *
   * @param temperature temperature in Kelvin
   * @return rate constant k(T)
   */
  public double calculateRateConstant(double temperature) {
    double tPower = 1.0;
    if (Math.abs(temperatureExponent) > 1e-10) {
      tPower = Math.pow(temperature, temperatureExponent);
    }
    return preExponentialFactor * tPower * Math.exp(-activationEnergy / (R_GAS * temperature));
  }

  /**
   * Calculate the equilibrium constant at given temperature.
   *
   * <p>
   * ln(Keq) = a + b/T + c*ln(T) + d*T
   * </p>
   *
   * @param temperature temperature in Kelvin
   * @return equilibrium constant Keq
   */
  public double calculateEquilibriumConstant(double temperature) {
    double lnKeq = eqCoeffs[0] + eqCoeffs[1] / temperature + eqCoeffs[2] * Math.log(temperature)
        + eqCoeffs[3] * temperature;
    return Math.exp(lnKeq);
  }

  /**
   * Calculate power-law rate.
   *
   * @param system thermodynamic system
   * @param phaseIndex phase index
   * @param rateConstant pre-calculated k(T)
   * @param temperature temperature [K]
   * @return rate [units per rateBasis]
   */
  private double calculatePowerLawRate(SystemInterface system, int phaseIndex, double rateConstant,
      double temperature) {
    // Forward rate: k * prod(Ci^alpha_i)
    double forwardRate = rateConstant;
    for (Map.Entry<String, Double> entry : reactionOrders.entrySet()) {
      String comp = entry.getKey();
      double order = entry.getValue();
      double concentration = getConcentration(system, phaseIndex, comp);
      if (concentration <= 0.0 && order > 0.0) {
        return 0.0;
      }
      forwardRate *= Math.pow(Math.max(concentration, 0.0), order);
    }

    if (!reversible) {
      return forwardRate;
    }

    // Reverse rate: prod(Cj^beta_j) / Keq(T)
    double keq = calculateEquilibriumConstant(temperature);
    if (keq <= 0.0) {
      return forwardRate;
    }

    double reverseProduct = 1.0;
    for (Map.Entry<String, Double> entry : productOrders.entrySet()) {
      String comp = entry.getKey();
      double order = entry.getValue();
      double concentration = getConcentration(system, phaseIndex, comp);
      reverseProduct *= Math.pow(Math.max(concentration, 1e-30), order);
    }

    return forwardRate - rateConstant * reverseProduct / keq;
  }

  /**
   * Calculate LHHW rate.
   *
   * @param system thermodynamic system
   * @param phaseIndex phase index
   * @param rateConstant pre-calculated k(T)
   * @param temperature temperature [K]
   * @return LHHW rate
   */
  private double calculateLHHWRate(SystemInterface system, int phaseIndex, double rateConstant,
      double temperature) {
    // Numerator: same as power-law forward rate
    double numerator = rateConstant;
    for (Map.Entry<String, Double> entry : reactionOrders.entrySet()) {
      String comp = entry.getKey();
      double order = entry.getValue();
      double concentration = getConcentration(system, phaseIndex, comp);
      if (concentration <= 0.0 && order > 0.0) {
        return 0.0;
      }
      numerator *= Math.pow(Math.max(concentration, 0.0), order);
    }

    // Denominator: (1 + sum(Ki * Ci^ni))^m
    double denomSum = 1.0;
    double kAds =
        adsorptionPreExpFactor * Math.exp(-adsorptionActivationEnergy / (R_GAS * temperature));
    for (Map.Entry<String, double[]> entry : adsorptionTerms.entrySet()) {
      String comp = entry.getKey();
      double[] params = entry.getValue(); // [Ki_factor, adsorption order]
      double concentration = getConcentration(system, phaseIndex, comp);
      denomSum += params[0] * kAds * Math.pow(Math.max(concentration, 0.0), params[1]);
    }

    double denominator = Math.pow(denomSum, adsorptionExponent);
    if (denominator < 1e-30) {
      denominator = 1e-30;
    }

    return numerator / denominator;
  }

  /**
   * Get molar concentration of a component in the specified phase.
   *
   * @param system thermodynamic system
   * @param phaseIndex phase index
   * @param componentName component name
   * @return concentration in mol/m3
   */
  private double getConcentration(SystemInterface system, int phaseIndex, String componentName) {
    if (!system.hasComponent(componentName)) {
      return 0.0;
    }
    try {
      double moleFraction = system.getPhase(phaseIndex).getComponent(componentName).getx();
      double molarDensity = system.getPhase(phaseIndex).getDensity("mol/m3");
      return moleFraction * molarDensity;
    } catch (Exception ex) {
      return 0.0;
    }
  }

  /**
   * Get the stoichiometric coefficient for a component.
   *
   * @param componentName component name
   * @return stoichiometric coefficient (negative for reactants, positive for products)
   */
  public double getStoichiometricCoefficient(String componentName) {
    Double coeff = stoichiometry.get(componentName);
    return coeff != null ? coeff : 0.0;
  }

  /**
   * Get the stoichiometry map.
   *
   * @return map of component name to stoichiometric coefficient
   */
  public Map<String, Double> getStoichiometry() {
    return stoichiometry;
  }

  /**
   * Get the name of this reaction.
   *
   * @return reaction name
   */
  public String getName() {
    return name;
  }

  /**
   * Set the rate expression type.
   *
   * @param rateType POWER_LAW, LHHW, or EQUILIBRIUM
   */
  public void setRateType(RateType rateType) {
    this.rateType = rateType;
  }

  /**
   * Get the rate expression type.
   *
   * @return rate type
   */
  public RateType getRateType() {
    return rateType;
  }

  /**
   * Set the rate basis.
   *
   * @param rateBasis VOLUME, CATALYST_MASS, or CATALYST_AREA
   */
  public void setRateBasis(RateBasis rateBasis) {
    this.rateBasis = rateBasis;
  }

  /**
   * Get the rate basis.
   *
   * @return rate basis
   */
  public RateBasis getRateBasis() {
    return rateBasis;
  }

  /**
   * Set the pre-exponential factor A.
   *
   * @param preExponentialFactor A in k = A*T^n*exp(-Ea/RT)
   */
  public void setPreExponentialFactor(double preExponentialFactor) {
    this.preExponentialFactor = preExponentialFactor;
  }

  /**
   * Get the pre-exponential factor.
   *
   * @return A
   */
  public double getPreExponentialFactor() {
    return preExponentialFactor;
  }

  /**
   * Set the activation energy.
   *
   * @param activationEnergy Ea in J/mol
   */
  public void setActivationEnergy(double activationEnergy) {
    this.activationEnergy = activationEnergy;
  }

  /**
   * Get the activation energy.
   *
   * @return Ea [J/mol]
   */
  public double getActivationEnergy() {
    return activationEnergy;
  }

  /**
   * Set the temperature exponent for modified Arrhenius.
   *
   * @param temperatureExponent n in k = A*T^n*exp(-Ea/RT)
   */
  public void setTemperatureExponent(double temperatureExponent) {
    this.temperatureExponent = temperatureExponent;
  }

  /**
   * Get the temperature exponent.
   *
   * @return n
   */
  public double getTemperatureExponent() {
    return temperatureExponent;
  }

  /**
   * Set the heat of reaction.
   *
   * @param heatOfReaction delta H_rxn in J/mol (negative = exothermic)
   */
  public void setHeatOfReaction(double heatOfReaction) {
    this.heatOfReaction = heatOfReaction;
  }

  /**
   * Get the heat of reaction.
   *
   * @return delta H_rxn [J/mol]
   */
  public double getHeatOfReaction() {
    return heatOfReaction;
  }

  /**
   * Set whether the reaction is reversible.
   *
   * @param reversible true for reversible reaction
   */
  public void setReversible(boolean reversible) {
    this.reversible = reversible;
  }

  /**
   * Check if reaction is reversible.
   *
   * @return true if reversible
   */
  public boolean isReversible() {
    return reversible;
  }

  /**
   * Set equilibrium constant correlation coefficients.
   *
   * <p>
   * ln(Keq) = a + b/T + c*ln(T) + d*T
   * </p>
   *
   * @param a constant term
   * @param b coefficient for 1/T
   * @param c coefficient for ln(T)
   * @param d coefficient for T
   */
  public void setEquilibriumConstantCorrelation(double a, double b, double c, double d) {
    this.eqCoeffs[0] = a;
    this.eqCoeffs[1] = b;
    this.eqCoeffs[2] = c;
    this.eqCoeffs[3] = d;
    this.reversible = true;
  }

  /**
   * Add an LHHW adsorption term for a component.
   *
   * @param componentName component name
   * @param kiFactor relative adsorption constant factor
   * @param adsorptionOrder adsorption concentration order
   */
  public void addAdsorptionTerm(String componentName, double kiFactor, double adsorptionOrder) {
    adsorptionTerms.put(componentName, new double[] {kiFactor, adsorptionOrder});
  }

  /**
   * Set the LHHW adsorption exponent.
   *
   * @param exponent power m for denominator (adsorption term)^m
   */
  public void setAdsorptionExponent(int exponent) {
    this.adsorptionExponent = exponent;
  }

  /**
   * Set the LHHW adsorption pre-exponential factor.
   *
   * @param factor pre-exponential factor for adsorption constant
   */
  public void setAdsorptionPreExpFactor(double factor) {
    this.adsorptionPreExpFactor = factor;
  }

  /**
   * Set the LHHW adsorption activation energy.
   *
   * @param energy activation energy for adsorption [J/mol]
   */
  public void setAdsorptionActivationEnergy(double energy) {
    this.adsorptionActivationEnergy = energy;
  }

  /**
   * Get the adsorption exponent.
   *
   * @return m
   */
  public int getAdsorptionExponent() {
    return adsorptionExponent;
  }
}
