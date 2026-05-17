package neqsim.process.equipment.reactor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.thermo.system.SystemInterface;

/**
 * Simple stoichiometric reaction model for bio-processing.
 *
 * <p>
 * Defines a reaction by listing reactant and product stoichiometric coefficients, a limiting
 * reactant, and a fractional conversion (X). This provides a straightforward way to model
 * fermentation, enzymatic hydrolysis, and other bio-chemical conversions without requiring full
 * Gibbs minimization.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * // Glucose fermentation to ethanol
 * StoichiometricReaction rxn = new StoichiometricReaction("EthanolFermentation");
 * rxn.addReactant("glucose", 1.0);
 * rxn.addProduct("ethanol", 2.0);
 * rxn.addProduct("CO2", 2.0);
 * rxn.setLimitingReactant("glucose");
 * rxn.setConversion(0.90); // 90% conversion
 *
 * // Apply to a thermo system
 * rxn.react(system);
 * </pre>
 *
 * @author NeqSim team
 * @version 1.0
 */
public class StoichiometricReaction implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Name of this reaction. */
  private String name;

  /** Stoichiometric coefficients keyed by component name. Negative for reactants. */
  private Map<String, Double> stoichiometry = new LinkedHashMap<String, Double>();

  /** Name of the limiting reactant component. */
  private String limitingReactant;

  /** Fractional conversion of the limiting reactant (0.0 to 1.0). */
  private double conversion = 1.0;

  /** Whether the reaction is on a molar (true) or mass (false) basis. */
  private boolean molarBasis = true;

  /**
   * Constructor for StoichiometricReaction.
   *
   * @param name the reaction name
   */
  public StoichiometricReaction(String name) {
    this.name = name;
  }

  /**
   * Add a reactant to this reaction.
   *
   * @param componentName name matching NeqSim component database
   * @param coefficient positive stoichiometric coefficient (will be stored as negative internally)
   */
  public void addReactant(String componentName, double coefficient) {
    stoichiometry.put(componentName, -Math.abs(coefficient));
  }

  /**
   * Add a product to this reaction.
   *
   * @param componentName name matching NeqSim component database
   * @param coefficient positive stoichiometric coefficient
   */
  public void addProduct(String componentName, double coefficient) {
    stoichiometry.put(componentName, Math.abs(coefficient));
  }

  /**
   * Set the limiting reactant by component name.
   *
   * @param componentName name of limited reactant
   */
  public void setLimitingReactant(String componentName) {
    this.limitingReactant = componentName;
  }

  /**
   * Get the limiting reactant name.
   *
   * @return limiting reactant
   */
  public String getLimitingReactant() {
    return limitingReactant;
  }

  /**
   * Set fractional conversion of the limiting reactant.
   *
   * @param conversion value between 0.0 and 1.0
   */
  public void setConversion(double conversion) {
    if (conversion < 0.0 || conversion > 1.0) {
      throw new IllegalArgumentException("Conversion must be between 0 and 1, got " + conversion);
    }
    this.conversion = conversion;
  }

  /**
   * Get the fractional conversion.
   *
   * @return conversion value
   */
  public double getConversion() {
    return conversion;
  }

  /**
   * Set whether this reaction is on a molar basis (default) or mass basis.
   *
   * @param molarBasis true for molar, false for mass
   */
  public void setMolarBasis(boolean molarBasis) {
    this.molarBasis = molarBasis;
  }

  /**
   * Check if reaction is molar-based.
   *
   * @return true if molar basis
   */
  public boolean isMolarBasis() {
    return molarBasis;
  }

  /**
   * Get the stoichiometry map.
   *
   * @return map of component name to stoichiometric coefficient (negative = reactant)
   */
  public Map<String, Double> getStoichiometry() {
    return stoichiometry;
  }

  /**
   * Get the reaction name.
   *
   * @return reaction name
   */
  public String getName() {
    return name;
  }

  /**
   * Apply this reaction to the given thermodynamic system.
   *
   * <p>
   * Computes moles reacted based on limiting reactant amount and conversion, then adjusts all
   * component mole counts according to stoichiometry.
   * </p>
   *
   * @param system the thermodynamic system to react
   * @return moles of limiting reactant consumed
   */
  public double react(SystemInterface system) {
    if (limitingReactant == null || limitingReactant.trim().isEmpty()) {
      throw new IllegalStateException("Limiting reactant not set for reaction '" + name + "'");
    }

    Double limitingCoeff = stoichiometry.get(limitingReactant);
    if (limitingCoeff == null) {
      throw new IllegalStateException(
          "Limiting reactant '" + limitingReactant + "' not found in reaction stoichiometry");
    }

    // Get current moles of limiting reactant
    double limitingMoles = 0.0;
    try {
      limitingMoles = system.getComponent(limitingReactant).getNumberOfmoles();
    } catch (Exception ex) {
      throw new IllegalStateException(
          "Component '" + limitingReactant + "' not found in thermodynamic system");
    }

    // Moles reacted of limiting reactant
    double molesReacted = limitingMoles * conversion;

    // The stoichiometric coefficient of the limiting reactant (negative value)
    double absLimitingCoeff = Math.abs(limitingCoeff);

    // Apply changes to each component
    for (Map.Entry<String, Double> entry : stoichiometry.entrySet()) {
      String compName = entry.getKey();
      double stoichCoeff = entry.getValue();

      // Change in moles = molesReacted * (stoichCoeff / absLimitingCoeff)
      double deltaMoles = molesReacted * (stoichCoeff / absLimitingCoeff);

      try {
        system.addComponent(compName, deltaMoles);
      } catch (Exception ex) {
        // Component might not exist in the system - skip silently
        // This can happen for products not yet in the system
      }
    }

    return molesReacted;
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("Reaction: ").append(name).append(" | ");

    List<String> reactantParts = new ArrayList<String>();
    List<String> productParts = new ArrayList<String>();

    for (Map.Entry<String, Double> entry : stoichiometry.entrySet()) {
      double coeff = entry.getValue();
      String compName = entry.getKey();
      double absCoeff = Math.abs(coeff);
      String coeffStr = (absCoeff == 1.0) ? "" : String.valueOf(absCoeff) + " ";
      if (coeff < 0) {
        reactantParts.add(coeffStr + compName);
      } else {
        productParts.add(coeffStr + compName);
      }
    }

    sb.append(join(reactantParts, " + "));
    sb.append(" -> ");
    sb.append(join(productParts, " + "));
    sb.append(" | X=").append(conversion);
    sb.append(" (limiting: ").append(limitingReactant).append(")");
    return sb.toString();
  }

  /**
   * Join strings with a delimiter (Java 8 compatible).
   *
   * @param parts list of strings
   * @param delimiter the delimiter
   * @return joined string
   */
  private static String join(List<String> parts, String delimiter) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < parts.size(); i++) {
      if (i > 0) {
        sb.append(delimiter);
      }
      sb.append(parts.get(i));
    }
    return sb.toString();
  }
}
