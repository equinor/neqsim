package neqsim.thermo.util.mineral;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Equilibrium of a heterogeneous reaction between pure solids, liquid water and gases.
 *
 * <p>
 * The standard equilibrium constant follows from phase-specific thermochemistry,
 * {@code ln K(T) = -sum(nu_i G_i(T)) / (R T)}, with ideal-gas standard states at 1 bar and pure condensed phases. At
 * pressure P the gas fugacity quotient at equilibrium is
 * </p>
 *
 * <p>
 * {@code prod_gas (f_i / p0)^nu_i = K(T) * exp(-sum_condensed nu_j I_j(T,P) / (R T)) /
 * prod_condensed a_j^nu_j}
 * </p>
 *
 * <p>
 * where {@code I_j} is the pressure integral of the condensed-phase molar volume and {@code a_j} the activity (water
 * activity for brines, 1 for pure solids). Gas non-ideality is not part of this relation: it enters through the
 * fugacities that {@link #solveGasExchange(SystemInterface, String, String, ExchangeMode)} takes from a NeqSim equation
 * of state. Total pressure cancels only for the ideal-gas part when the gas stoichiometry is balanced; fugacity
 * coefficients still matter.
 * </p>
 *
 * <p>
 * Results are a thermodynamic limit: kinetics, mineral availability, polymorph identity (for example troilite versus
 * mackinawite) and aqueous speciation are outside the model.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class SolidGasReactionEquilibrium implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(SolidGasReactionEquilibrium.class);

  /** Gas constant [J/(mol K)]. */
  private static final double R = ThermodynamicConstantsInterface.R;

  /** Tolerance on the element balance. */
  private static final double ELEMENT_TOLERANCE = 1.0e-9;

  /** How the gas inventory responds to the reaction when solving for equilibrium. */
  public enum ExchangeMode {
    /**
     * The reactant-gas amount is held fixed and only the product-gas amount is adjusted, as for a reservoir fluid
     * buffered by a large reactant inventory.
     */
    FIXED_REACTANT,
    /**
     * Reactant gas is converted to product gas so that their sum is conserved (closed system with excess solid);
     * requires equal gas coefficients.
     */
    CONSERVE_GAS_SUM,
    /**
     * Only the target gas amount is adjusted; all other gas species keep the fugacities given by the fluid (mineral
     * buffer in a large fluid inventory).
     */
    FIXED_OTHERS
  }

  /** Reaction name. */
  private final String name;
  /** Species in the reaction. */
  private final List<StandardStateSpecies> species = new ArrayList<StandardStateSpecies>();
  /** Stoichiometric coefficients, negative for reactants. */
  private final List<Double> coefficients = new ArrayList<Double>();
  /** Activities of condensed species by name, 1 if not set. */
  private final Map<String, Double> activities = new LinkedHashMap<String, Double>();
  /** Maximum number of solver iterations. */
  private int maxIterations = 100;
  /** Convergence tolerance on the logarithm of the fugacity quotient. */
  private double tolerance = 1.0e-9;

  /**
   * Creates an empty reaction.
   *
   * @param name reaction name
   */
  public SolidGasReactionEquilibrium(String name) {
    this.name = name;
  }

  /**
   * Creates FeS(s) + H2O(l) + CO2(g) = FeCO3(s) + H2S(g) with troilite and siderite.
   *
   * @return the reaction
   */
  public static SolidGasReactionEquilibrium ironSulfideToSiderite() {
    SolidGasReactionEquilibrium reaction = new SolidGasReactionEquilibrium(
        "FeS(troilite) + H2O(l) + CO2(g) = FeCO3(s) + H2S(g)");
    reaction.addSpecies(StandardStateSpecies.troilite(), -1.0);
    reaction.addSpecies(StandardStateSpecies.liquidWater(), -1.0);
    reaction.addSpecies(StandardStateSpecies.carbonDioxideGas(), -1.0);
    reaction.addSpecies(StandardStateSpecies.siderite(), 1.0);
    reaction.addSpecies(StandardStateSpecies.hydrogenSulfideGas(), 1.0);
    reaction.checkElementBalance();
    return reaction;
  }

  /**
   * Adds a species to the reaction.
   *
   * @param standardSpecies species
   * @param coefficient stoichiometric coefficient, negative for reactants
   * @return this reaction
   * @throws IllegalArgumentException if the coefficient is zero or the species is already present
   */
  public SolidGasReactionEquilibrium addSpecies(StandardStateSpecies standardSpecies, double coefficient) {
    if (coefficient == 0.0) {
      throw new IllegalArgumentException("Stoichiometric coefficient must be non-zero");
    }
    if (indexOf(standardSpecies.getName()) >= 0) {
      throw new IllegalArgumentException("Species " + standardSpecies.getName() + " is already in the reaction");
    }
    species.add(standardSpecies);
    coefficients.add(coefficient);
    return this;
  }

  /**
   * Sets the activity of a condensed species, for example the water activity of a brine.
   *
   * @param speciesName species name
   * @param activity activity (dimensionless, positive)
   * @throws IllegalArgumentException if the species is unknown, a gas, or the activity is not positive
   */
  public void setActivity(String speciesName, double activity) {
    int index = indexOf(speciesName);
    if (index < 0) {
      throw new IllegalArgumentException("Species " + speciesName + " is not in the reaction");
    }
    if (species.get(index).getPhase() == StandardStateSpecies.Phase.IDEAL_GAS) {
      throw new IllegalArgumentException(
          "Gas activities come from the fluid fugacities; set activity only for condensed phases");
    }
    if (!(activity > 0.0)) {
      throw new IllegalArgumentException("Activity must be positive");
    }
    activities.put(speciesName, activity);
  }

  /**
   * Gets the activity of a condensed species.
   *
   * @param speciesName species name
   * @return activity, 1 if not set
   */
  public double getActivity(String speciesName) {
    Double value = activities.get(speciesName);
    return value == null ? 1.0 : value;
  }

  /**
   * Sets the maximum number of solver iterations.
   *
   * @param maxIterations maximum iterations
   */
  public void setMaxIterations(int maxIterations) {
    this.maxIterations = maxIterations;
  }

  /**
   * Sets the solver tolerance on the logarithm of the fugacity quotient.
   *
   * @param tolerance tolerance
   */
  public void setTolerance(double tolerance) {
    this.tolerance = tolerance;
  }

  /**
   * Gets the reaction name.
   *
   * @return name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the species in the reaction.
   *
   * @return unmodifiable species list
   */
  public List<StandardStateSpecies> getSpecies() {
    return Collections.unmodifiableList(species);
  }

  /**
   * Gets the stoichiometric coefficient of a species.
   *
   * @param speciesName species name
   * @return coefficient, negative for reactants
   * @throws IllegalArgumentException if the species is not in the reaction
   */
  public double getCoefficient(String speciesName) {
    int index = indexOf(speciesName);
    if (index < 0) {
      throw new IllegalArgumentException("Species " + speciesName + " is not in the reaction");
    }
    return coefficients.get(index);
  }

  /**
   * Checks that every element balances.
   *
   * @throws IllegalStateException if the reaction is not atom-balanced
   */
  public void checkElementBalance() {
    Map<String, Double> balance = new LinkedHashMap<String, Double>();
    for (int i = 0; i < species.size(); i++) {
      for (Map.Entry<String, Double> entry : species.get(i).getElements().entrySet()) {
        Double current = balance.get(entry.getKey());
        double value = current == null ? 0.0 : current;
        balance.put(entry.getKey(), value + coefficients.get(i) * entry.getValue());
      }
    }
    for (Map.Entry<String, Double> entry : balance.entrySet()) {
      if (Math.abs(entry.getValue()) > ELEMENT_TOLERANCE) {
        throw new IllegalStateException(
            "Reaction " + name + " is not balanced in element " + entry.getKey() + " (net " + entry.getValue() + ")");
      }
    }
  }

  /**
   * Calculates the change in gas moles.
   *
   * @return sum of gas coefficients
   */
  public double getGasMoleChange() {
    double sum = 0.0;
    for (int i = 0; i < species.size(); i++) {
      if (species.get(i).getPhase() == StandardStateSpecies.Phase.IDEAL_GAS) {
        sum += coefficients.get(i);
      }
    }
    return sum;
  }

  /**
   * Calculates the standard Gibbs energy of reaction at 1 bar.
   *
   * @param temperature temperature [K]
   * @return Gibbs energy of reaction [J/mol]
   */
  public double getStandardGibbsEnergyOfReaction(double temperature) {
    double sum = 0.0;
    for (int i = 0; i < species.size(); i++) {
      sum += coefficients.get(i) * species.get(i).getApparentGibbsEnergy(temperature);
    }
    return sum;
  }

  /**
   * Calculates the standard enthalpy of reaction at 1 bar.
   *
   * @param temperature temperature [K]
   * @return enthalpy of reaction [J/mol]
   */
  public double getStandardEnthalpyOfReaction(double temperature) {
    double sum = 0.0;
    for (int i = 0; i < species.size(); i++) {
      StandardStateSpecies s = species.get(i);
      sum += coefficients.get(i) * (s.getFormationEnthalpy298() + s.getEnthalpyChange(temperature));
    }
    return sum;
  }

  /**
   * Calculates the standard entropy of reaction at 1 bar.
   *
   * @param temperature temperature [K]
   * @return entropy of reaction [J/(mol K)]
   */
  public double getStandardEntropyOfReaction(double temperature) {
    double sum = 0.0;
    for (int i = 0; i < species.size(); i++) {
      sum += coefficients.get(i) * species.get(i).getEntropy(temperature);
    }
    return sum;
  }

  /**
   * Calculates the natural logarithm of the standard equilibrium constant.
   *
   * @param temperature temperature [K]
   * @return ln K
   */
  public double getLnK(double temperature) {
    return -getStandardGibbsEnergyOfReaction(temperature) / (R * temperature);
  }

  /**
   * Calculates the base-10 logarithm of the standard equilibrium constant.
   *
   * @param temperature temperature [K]
   * @return log10 K
   */
  public double getLog10K(double temperature) {
    return getLnK(temperature) / Math.log(10.0);
  }

  /**
   * Propagates the quantified enthalpy and entropy uncertainties to log10 K. Species without a quantified uncertainty
   * are listed by {@link #getSpeciesWithoutUncertainty()} and excluded.
   *
   * @param temperature temperature [K]
   * @return standard uncertainty of log10 K
   */
  public double getLog10KUncertainty(double temperature) {
    double variance = 0.0;
    for (int i = 0; i < species.size(); i++) {
      StandardStateSpecies s = species.get(i);
      double nu = coefficients.get(i);
      if (!Double.isNaN(s.getFormationEnthalpyUncertainty())) {
        variance += Math.pow(nu * s.getFormationEnthalpyUncertainty(), 2.0);
      }
      if (!Double.isNaN(s.getEntropyUncertainty())) {
        variance += Math.pow(nu * temperature * s.getEntropyUncertainty(), 2.0);
      }
    }
    return Math.sqrt(variance) / (R * temperature * Math.log(10.0));
  }

  /**
   * Lists species whose thermochemical uncertainty is not quantified.
   *
   * @return species names
   */
  public List<String> getSpeciesWithoutUncertainty() {
    List<String> names = new ArrayList<String>();
    for (StandardStateSpecies s : species) {
      if (Double.isNaN(s.getFormationEnthalpyUncertainty()) || Double.isNaN(s.getEntropyUncertainty())) {
        names.add(s.getName());
      }
    }
    return names;
  }

  /**
   * Calculates the shift in ln K from condensed-phase volumes between 1 bar and P.
   *
   * @param temperature temperature [K]
   * @param pressure pressure [bara]
   * @return {@code -sum_condensed nu_j I_j / (R T)}
   */
  public double getCondensedPressureCorrection(double temperature, double pressure) {
    double sum = 0.0;
    for (int i = 0; i < species.size(); i++) {
      sum += coefficients.get(i) * species.get(i).getPressureIntegral(temperature, pressure);
    }
    return -sum / (R * temperature);
  }

  /**
   * Calculates the natural logarithm of the equilibrium gas fugacity quotient, {@code
   * prod_gas (f_i/p0)^nu_i}, including condensed-phase pressure and activity terms.
   *
   * @param temperature temperature [K]
   * @param pressure pressure [bara]
   * @return ln of the equilibrium gas fugacity quotient
   */
  public double getLnEquilibriumGasFugacityQuotient(double temperature, double pressure) {
    double value = getLnK(temperature) + getCondensedPressureCorrection(temperature, pressure);
    for (int i = 0; i < species.size(); i++) {
      StandardStateSpecies s = species.get(i);
      if (s.getPhase() != StandardStateSpecies.Phase.IDEAL_GAS) {
        value -= coefficients.get(i) * Math.log(getActivity(s.getName()));
      }
    }
    return value;
  }

  /**
   * Checks whether every species is evaluated inside its tabulated heat-capacity range.
   *
   * @param temperature temperature [K]
   * @return true if no species is extrapolated
   */
  public boolean isWithinDataRange(double temperature) {
    for (StandardStateSpecies s : species) {
      if (!s.isWithinDataRange(temperature)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Builds warnings about extrapolation, metastable references and missing uncertainties.
   *
   * @param temperature temperature [K]
   * @return warning messages
   */
  public List<String> getWarnings(double temperature) {
    List<String> warnings = new ArrayList<String>();
    for (StandardStateSpecies s : species) {
      if (!s.isWithinDataRange(temperature)) {
        warnings.add(String.format("%s heat capacity extrapolated at constant Cp outside %.1f-%.1f K (T = %.2f K)",
            s.getName(), s.getMinimumTemperature(), s.getMaximumTemperature(), temperature));
      }
      if (s.usesMetastableLiquidReference(temperature)) {
        warnings.add(s.getName() + " uses a metastable 1-bar liquid reference above its boiling "
            + "point (IAPWS-IF97 Region 1 continuation)");
      }
    }
    List<String> unquantified = getSpeciesWithoutUncertainty();
    if (!unquantified.isEmpty()) {
      warnings.add("Uncertainty not quantified for " + unquantified + "; log10 K uncertainty is a lower bound");
    }
    return warnings;
  }

  /**
   * Solves for the amount of one gas that satisfies the reaction equilibrium while the fugacities of all other gas
   * species in the reaction follow from the fluid itself.
   *
   * <p>
   * This is the general form for mineral buffers with several gases, for example 4 FeS2 + CH4 + 2 H2O(l) = 4 FeS + 4
   * H2S + CO2, where the CH4 and CO2 fugacities of a reservoir fluid set the buffered H2S fugacity. Only the target gas
   * amount is changed; because the other gases are present in large amounts, their fugacities move only through the
   * small composition change. Fugacities are equal in all phases at equilibrium and are read from phase 0.
   * </p>
   *
   * @param fluid fluid containing every gas species of the reaction (target may start at a trace)
   * @param targetGas component name of the gas to solve for, for example "H2S"
   * @return equilibrium result; reactant fields refer to no partner gas and are NaN
   * @throws IllegalArgumentException if a gas species is missing from the fluid or has zero amount
   */
  public GasExchangeResult solveForGas(SystemInterface fluid, String targetGas) {
    double targetNu = gasCoefficient(targetGas);
    List<String> gases = getGasSpeciesNames();
    for (String gas : gases) {
      if (!fluid.hasComponent(gas)) {
        throw new IllegalArgumentException("Fluid must contain gas species " + gas + " of reaction " + name);
      }
      if (!gas.equals(targetGas) && !(fluid.getComponent(gas).getNumberOfmoles() > 0.0)) {
        throw new IllegalArgumentException("Fluid must contain a positive amount of " + gas);
      }
    }
    SystemInterface work = fluid.clone();
    double temperature = work.getTemperature();
    double pressure = work.getPressure();
    double lnTarget = getLnEquilibriumGasFugacityQuotient(temperature, pressure);
    double targetMoles = work.getComponent(targetGas).getNumberOfmoles();
    if (!(targetMoles > 0.0)) {
      double seed = 1.0e-9 * work.getTotalNumberOfMoles();
      work.addComponent(targetGas, seed - targetMoles);
      targetMoles = seed;
    }
    ThermodynamicOperations ops = new ThermodynamicOperations(work);
    boolean converged = false;
    int iteration = 0;
    double lnQuotient = Double.NaN;
    Map<String, Double> fugacities = new LinkedHashMap<String, Double>();
    Map<String, Double> phis = new LinkedHashMap<String, Double>();
    while (iteration < maxIterations) {
      iteration++;
      ops.TPflash();
      work.init(1);
      lnQuotient = readGasFugacities(work, gases, fugacities, phis);
      double residual = lnTarget - lnQuotient;
      if (Math.abs(residual) < tolerance) {
        converged = true;
        break;
      }
      double lnFactor = Math.max(-Math.log(1.0e3), Math.min(Math.log(1.0e3), residual / targetNu));
      double newMoles = targetMoles * Math.exp(lnFactor);
      work.addComponent(targetGas, newMoles - targetMoles);
      targetMoles = newMoles;
    }
    if (!converged) {
      logger.warn("Reaction {} did not converge in {} iterations", name, maxIterations);
    }
    Double targetPhi = phis.get(targetGas);
    GasExchangeResult result = new GasExchangeResult(this, work, targetGas, null, temperature, pressure, lnTarget,
        lnQuotient, targetPhi == null ? Double.NaN : targetPhi, Double.NaN, iteration, converged,
        ExchangeMode.FIXED_OTHERS);
    result.setGasFugacities(fugacities, phis);
    return result;
  }

  /**
   * Calculates the equilibrium fugacity of one gas from given fugacities of the other gas species.
   *
   * @param targetGas gas to solve for
   * @param otherFugacities fugacities of the other gas species of the reaction [bar]
   * @param temperature temperature [K]
   * @param pressure pressure used for the condensed-phase correction [bara]
   * @return natural logarithm of the target fugacity divided by 1 bar
   * @throws IllegalArgumentException if a fugacity of another gas species is missing or not positive
   */
  public double getLnEquilibriumGasFugacity(String targetGas, Map<String, Double> otherFugacities, double temperature,
      double pressure) {
    double targetNu = gasCoefficient(targetGas);
    double value = getLnEquilibriumGasFugacityQuotient(temperature, pressure);
    for (String gas : getGasSpeciesNames()) {
      if (gas.equals(targetGas)) {
        continue;
      }
      Double f = otherFugacities.get(gas);
      if (f == null || !(f > 0.0)) {
        throw new IllegalArgumentException("Positive fugacity of " + gas + " required");
      }
      value -= getCoefficient(gas) * Math.log(f);
    }
    return value / targetNu;
  }

  /**
   * Lists the gas species of the reaction.
   *
   * @return gas species names in insertion order
   */
  public List<String> getGasSpeciesNames() {
    List<String> names = new ArrayList<String>();
    for (StandardStateSpecies s : species) {
      if (s.getPhase() == StandardStateSpecies.Phase.IDEAL_GAS) {
        names.add(s.getName());
      }
    }
    return names;
  }

  /**
   * Reads the fugacities of the reaction gases from phase 0 and forms the gas fugacity quotient.
   *
   * @param fluid flashed fluid
   * @param gases gas species names
   * @param fugacities output map of fugacities [bar]
   * @param phis output map of fugacity coefficients
   * @return natural logarithm of the gas fugacity quotient
   */
  private double readGasFugacities(SystemInterface fluid, List<String> gases, Map<String, Double> fugacities,
      Map<String, Double> phis) {
    PhaseInterface phase = fluid.getPhase(0);
    double lnQ = 0.0;
    for (String gas : gases) {
      double phi = phase.getComponent(gas).getFugacityCoefficient();
      double f = phase.getComponent(gas).getx() * phi * phase.getPressure();
      fugacities.put(gas, f);
      phis.put(gas, phi);
      lnQ += getCoefficient(gas) * Math.log(f);
    }
    return lnQ;
  }

  /**
   * Solves for the product-gas amount that satisfies the reaction equilibrium in a fluid.
   *
   * <p>
   * The fluid is cloned and flashed at its own temperature and pressure. Fugacities are taken from the equation of
   * state of the fluid, so gas and liquid non-ideality, phase splits and the dissolution of gases in water or oil are
   * all included. The input fluid is not modified.
   * </p>
   *
   * @param fluid fluid containing the product and reactant gas components
   * @param productGas component name of the product gas, for example "H2S"
   * @param reactantGas component name of the reactant gas, for example "CO2"
   * @param mode how the gas inventory responds to the reaction
   * @return equilibrium result
   * @throws IllegalArgumentException if components or reaction gases are missing or inconsistent
   */
  public GasExchangeResult solveGasExchange(SystemInterface fluid, String productGas, String reactantGas,
      ExchangeMode mode) {
    double productNu = gasCoefficient(productGas);
    double reactantNu = gasCoefficient(reactantGas);
    if (productNu <= 0.0 || reactantNu >= 0.0) {
      throw new IllegalArgumentException("Product gas must have a positive and reactant gas a negative coefficient");
    }
    if (countGasSpecies() != 2) {
      throw new IllegalArgumentException("solveGasExchange supports reactions with exactly two gas species");
    }
    if (mode == ExchangeMode.CONSERVE_GAS_SUM && Math.abs(productNu + reactantNu) > 1.0e-12) {
      throw new IllegalArgumentException("CONSERVE_GAS_SUM requires equal product and reactant gas coefficients");
    }
    if (!fluid.hasComponent(productGas) || !fluid.hasComponent(reactantGas)) {
      throw new IllegalArgumentException("Fluid must contain components " + productGas + " and " + reactantGas
          + "; add them (product gas may start at a trace amount)");
    }
    SystemInterface work = fluid.clone();
    double temperature = work.getTemperature();
    double pressure = work.getPressure();
    double lnTarget = getLnEquilibriumGasFugacityQuotient(temperature, pressure);

    double reactantMoles = work.getComponent(reactantGas).getNumberOfmoles();
    if (!(reactantMoles > 0.0)) {
      throw new IllegalArgumentException("Fluid must contain a positive amount of " + reactantGas);
    }
    double productMoles = work.getComponent(productGas).getNumberOfmoles();
    if (!(productMoles > 0.0)) {
      double seed = 1.0e-6 * reactantMoles;
      work.addComponent(productGas, seed - productMoles);
      productMoles = seed;
    }
    double gasSum = productMoles + reactantMoles;

    ThermodynamicOperations ops = new ThermodynamicOperations(work);
    boolean converged = false;
    int iteration = 0;
    double lnQuotient = Double.NaN;
    double[] state = new double[4];
    while (iteration < maxIterations) {
      iteration++;
      ops.TPflash();
      work.init(1);
      readFugacities(work, productGas, reactantGas, state);
      lnQuotient = productNu * Math.log(state[0]) + reactantNu * Math.log(state[1]);
      double residual = lnTarget - lnQuotient;
      if (Math.abs(residual) < tolerance) {
        converged = true;
        break;
      }
      double lnFactor = Math.max(-Math.log(1.0e3), Math.min(Math.log(1.0e3), residual / productNu));
      double newProduct;
      double newReactant = reactantMoles;
      if (mode == ExchangeMode.FIXED_REACTANT) {
        newProduct = productMoles * Math.exp(lnFactor);
      } else {
        double ratio = productMoles / reactantMoles * Math.exp(lnFactor);
        newProduct = gasSum * ratio / (1.0 + ratio);
        newReactant = gasSum - newProduct;
        work.addComponent(reactantGas, newReactant - reactantMoles);
      }
      work.addComponent(productGas, newProduct - productMoles);
      productMoles = newProduct;
      reactantMoles = newReactant;
    }
    if (!converged) {
      logger.warn("Reaction {} did not converge in {} iterations", name, maxIterations);
    }
    return new GasExchangeResult(this, work, productGas, reactantGas, temperature, pressure, lnTarget, lnQuotient,
        state[2], state[3], iteration, converged, mode);
  }

  /**
   * Reads fugacities and fugacity coefficients from the first phase of a flashed fluid.
   *
   * @param fluid flashed fluid
   * @param productGas product gas name
   * @param reactantGas reactant gas name
   * @param state output: product fugacity [bar], reactant fugacity [bar], product and reactant fugacity coefficients
   */
  private static void readFugacities(SystemInterface fluid, String productGas, String reactantGas, double[] state) {
    PhaseInterface phase = fluid.getPhase(0);
    double productPhi = phase.getComponent(productGas).getFugacityCoefficient();
    double reactantPhi = phase.getComponent(reactantGas).getFugacityCoefficient();
    state[0] = phase.getComponent(productGas).getx() * productPhi * phase.getPressure();
    state[1] = phase.getComponent(reactantGas).getx() * reactantPhi * phase.getPressure();
    state[2] = productPhi;
    state[3] = reactantPhi;
  }

  /**
   * Gets the coefficient of a gas species.
   *
   * @param gasName gas species name
   * @return coefficient
   * @throws IllegalArgumentException if the species is not a gas in the reaction
   */
  private double gasCoefficient(String gasName) {
    int index = indexOf(gasName);
    if (index < 0 || species.get(index).getPhase() != StandardStateSpecies.Phase.IDEAL_GAS) {
      throw new IllegalArgumentException(gasName + " is not a gas species of reaction " + name);
    }
    return coefficients.get(index);
  }

  /**
   * Counts gas species.
   *
   * @return number of gas species
   */
  private int countGasSpecies() {
    int count = 0;
    for (StandardStateSpecies s : species) {
      if (s.getPhase() == StandardStateSpecies.Phase.IDEAL_GAS) {
        count++;
      }
    }
    return count;
  }

  /**
   * Finds a species by name.
   *
   * @param speciesName species name
   * @return index, or -1 if absent
   */
  private int indexOf(String speciesName) {
    for (int i = 0; i < species.size(); i++) {
      if (species.get(i).getName().equals(speciesName)) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Result of {@link SolidGasReactionEquilibrium#solveGasExchange(SystemInterface, String, String, ExchangeMode)}.
   *
   * @author esol
   * @version 1.0
   */
  public static class GasExchangeResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    /** Equilibrated fluid (a clone of the input). */
    private final SystemInterface equilibriumFluid;
    /** Product gas name. */
    private final String productGas;
    /** Reactant gas name. */
    private final String reactantGas;
    /** Temperature [K]. */
    private final double temperature;
    /** Pressure [bara]. */
    private final double pressure;
    /** log10 of the standard equilibrium constant. */
    private final double log10K;
    /** Standard uncertainty of log10 K from quantified data. */
    private final double log10KUncertainty;
    /** Condensed-phase pressure correction to ln K. */
    private final double condensedPressureCorrection;
    /** ln of the target gas fugacity quotient. */
    private final double lnTargetQuotient;
    /** ln of the achieved gas fugacity quotient. */
    private final double lnAchievedQuotient;
    /** Product-gas fugacity coefficient in phase 0. */
    private final double productFugacityCoefficient;
    /** Reactant-gas fugacity coefficient in phase 0. */
    private final double reactantFugacityCoefficient;
    /** Iterations used. */
    private final int iterations;
    /** Convergence flag. */
    private final boolean converged;
    /** Exchange mode used. */
    private final ExchangeMode mode;
    /** Warnings. */
    private final List<String> warnings;
    /** Fugacities of all reaction gases at the solution [bar]. */
    private final Map<String, Double> gasFugacities = new LinkedHashMap<String, Double>();
    /** Fugacity coefficients of all reaction gases at the solution. */
    private final Map<String, Double> gasFugacityCoefficients = new LinkedHashMap<String, Double>();

    /**
     * Creates a result.
     *
     * @param reaction solved reaction
     * @param equilibriumFluid equilibrated fluid
     * @param productGas product gas name
     * @param reactantGas reactant gas name
     * @param temperature temperature [K]
     * @param pressure pressure [bara]
     * @param lnTargetQuotient ln target fugacity quotient
     * @param lnAchievedQuotient ln achieved fugacity quotient
     * @param productFugacityCoefficient product fugacity coefficient
     * @param reactantFugacityCoefficient reactant fugacity coefficient
     * @param iterations iterations used
     * @param converged convergence flag
     * @param mode exchange mode
     */
    GasExchangeResult(SolidGasReactionEquilibrium reaction, SystemInterface equilibriumFluid, String productGas,
        String reactantGas, double temperature, double pressure, double lnTargetQuotient, double lnAchievedQuotient,
        double productFugacityCoefficient, double reactantFugacityCoefficient, int iterations, boolean converged,
        ExchangeMode mode) {
      this.equilibriumFluid = equilibriumFluid;
      this.productGas = productGas;
      this.reactantGas = reactantGas;
      this.temperature = temperature;
      this.pressure = pressure;
      this.log10K = reaction.getLog10K(temperature);
      this.log10KUncertainty = reaction.getLog10KUncertainty(temperature);
      this.condensedPressureCorrection = reaction.getCondensedPressureCorrection(temperature, pressure);
      this.lnTargetQuotient = lnTargetQuotient;
      this.lnAchievedQuotient = lnAchievedQuotient;
      this.productFugacityCoefficient = productFugacityCoefficient;
      this.reactantFugacityCoefficient = reactantFugacityCoefficient;
      this.iterations = iterations;
      this.converged = converged;
      this.mode = mode;
      List<String> list = reaction.getWarnings(temperature);
      if (!converged) {
        list.add("Solver did not converge; results are the last iterate");
      }
      this.warnings = Collections.unmodifiableList(list);
    }

    /**
     * Gets a copy of the equilibrated fluid.
     *
     * @return clone of the fluid at equilibrium
     */
    public SystemInterface getEquilibriumFluid() {
      return equilibriumFluid.clone();
    }

    /**
     * Gets the temperature.
     *
     * @return temperature [K]
     */
    public double getTemperature() {
      return temperature;
    }

    /**
     * Gets the pressure.
     *
     * @return pressure [bara]
     */
    public double getPressure() {
      return pressure;
    }

    /**
     * Gets log10 of the standard equilibrium constant.
     *
     * @return log10 K
     */
    public double getLog10K() {
      return log10K;
    }

    /**
     * Gets the standard uncertainty of log10 K from quantified data.
     *
     * @return uncertainty of log10 K
     */
    public double getLog10KUncertainty() {
      return log10KUncertainty;
    }

    /**
     * Gets the condensed-phase pressure correction to ln K.
     *
     * @return correction
     */
    public double getCondensedPressureCorrection() {
      return condensedPressureCorrection;
    }

    /**
     * Gets the target equilibrium gas fugacity quotient.
     *
     * @return fugacity quotient (dimensionless, p0 = 1 bar)
     */
    public double getTargetFugacityQuotient() {
      return Math.exp(lnTargetQuotient);
    }

    /**
     * Gets the achieved gas fugacity quotient.
     *
     * @return fugacity quotient (dimensionless, p0 = 1 bar)
     */
    public double getAchievedFugacityQuotient() {
      return Math.exp(lnAchievedQuotient);
    }

    /**
     * Gets the product-gas fugacity coefficient in phase 0.
     *
     * @return fugacity coefficient
     */
    public double getProductFugacityCoefficient() {
      return productFugacityCoefficient;
    }

    /**
     * Gets the reactant-gas fugacity coefficient in phase 0.
     *
     * @return fugacity coefficient
     */
    public double getReactantFugacityCoefficient() {
      return reactantFugacityCoefficient;
    }

    /**
     * Gets the product-gas mole fraction of the whole fluid.
     *
     * @return mole fraction
     */
    public double getProductMoleFraction() {
      return equilibriumFluid.getComponent(productGas).getz();
    }

    /**
     * Gets the reactant-gas mole fraction of the whole fluid.
     *
     * @return mole fraction, NaN when the result has no partner gas ({@link ExchangeMode#FIXED_OTHERS})
     */
    public double getReactantMoleFraction() {
      if (reactantGas == null) {
        return Double.NaN;
      }
      return equilibriumFluid.getComponent(reactantGas).getz();
    }

    /**
     * Sets the fugacities and fugacity coefficients of all reaction gases at the solution.
     *
     * @param fugacities fugacities [bar]
     * @param phis fugacity coefficients
     */
    void setGasFugacities(Map<String, Double> fugacities, Map<String, Double> phis) {
      gasFugacities.clear();
      gasFugacities.putAll(fugacities);
      gasFugacityCoefficients.clear();
      gasFugacityCoefficients.putAll(phis);
    }

    /**
     * Gets the fugacity of a reaction gas at the solution (only for {@link ExchangeMode#FIXED_OTHERS}).
     *
     * @param gas gas component name
     * @return fugacity [bar], NaN if not recorded
     */
    public double getGasFugacity(String gas) {
      Double value = gasFugacities.get(gas);
      return value == null ? Double.NaN : value;
    }

    /**
     * Gets the fugacity coefficient of a reaction gas at the solution (only for {@link ExchangeMode#FIXED_OTHERS}).
     *
     * @param gas gas component name
     * @return fugacity coefficient, NaN if not recorded
     */
    public double getGasFugacityCoefficient(String gas) {
      Double value = gasFugacityCoefficients.get(gas);
      return value == null ? Double.NaN : value;
    }

    /**
     * Gets the product-gas mole fraction in the gas phase.
     *
     * @return mole fraction, NaN if the fluid has no gas phase
     */
    public double getProductMoleFractionInGas() {
      if (!equilibriumFluid.hasPhaseType("gas")) {
        return Double.NaN;
      }
      return equilibriumFluid.getPhase("gas").getComponent(productGas).getx();
    }

    /**
     * Gets the iterations used.
     *
     * @return iterations
     */
    public int getIterations() {
      return iterations;
    }

    /**
     * Checks convergence.
     *
     * @return true if converged
     */
    public boolean isConverged() {
      return converged;
    }

    /**
     * Gets the exchange mode used.
     *
     * @return mode
     */
    public ExchangeMode getMode() {
      return mode;
    }

    /**
     * Gets warnings about extrapolation, references, uncertainty and convergence.
     *
     * @return unmodifiable warning list
     */
    public List<String> getWarnings() {
      return warnings;
    }
  }
}
