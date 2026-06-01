package neqsim.process.research;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reaction route option for process research candidate generation.
 *
 * <p>
 * A reaction option records the reactor model, expected product component, and operating metadata
 * used to generate reactor-containing candidate flowsheets. It deliberately avoids pretending to be
 * a full reaction-network generator; stoichiometry and kinetics can be added around this class as
 * the route library matures.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class ReactionOption {
  private final String name;
  private String reactorType = "GibbsReactor";
  private String expectedProductComponent;
  private String energyMode = "ISOTHERMAL";
  private final Map<String, Double> stoichiometry = new LinkedHashMap<>();
  private double reactorTemperatureK = Double.NaN;
  private double reactorPressureBara = Double.NaN;

  /**
   * Creates a reaction option.
   *
   * @param name reaction route name; must be non-empty
   */
  public ReactionOption(String name) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("Reaction option name cannot be empty");
    }
    this.name = name;
  }

  /**
   * Sets the reactor type used in generated JSON.
   *
   * @param reactorType reactor type, e.g. GibbsReactor, PlugFlowReactor, or StirredTankReactor
   * @return this reaction option
   */
  public ReactionOption setReactorType(String reactorType) {
    this.reactorType = reactorType;
    return this;
  }

  /**
   * Sets the expected product component used for ranking.
   *
   * @param componentName product component name
   * @return this reaction option
   */
  public ReactionOption setExpectedProductComponent(String componentName) {
    this.expectedProductComponent = componentName;
    return this;
  }

  /**
   * Sets the reactor energy mode.
   *
   * @param energyMode energy mode string accepted by the reactor, e.g. ISOTHERMAL
   * @return this reaction option
   */
  public ReactionOption setEnergyMode(String energyMode) {
    this.energyMode = energyMode;
    return this;
  }

  /**
   * Sets the reactor temperature.
   *
   * @param temperatureK reactor temperature in Kelvin
   * @return this reaction option
   */
  public ReactionOption setReactorTemperature(double temperatureK) {
    this.reactorTemperatureK = temperatureK;
    return this;
  }

  /**
   * Sets the reactor pressure.
   *
   * @param pressureBara reactor pressure in bara
   * @return this reaction option
   */
  public ReactionOption setReactorPressure(double pressureBara) {
    this.reactorPressureBara = pressureBara;
    return this;
  }

  /**
   * Adds a stoichiometric coefficient for traceability.
   *
   * @param componentName component name
   * @param coefficient negative for reactants and positive for products
   * @return this reaction option
   */
  public ReactionOption addStoichiometricCoefficient(String componentName, double coefficient) {
    if (componentName != null && !componentName.trim().isEmpty()) {
      stoichiometry.put(componentName, coefficient);
    }
    return this;
  }

  /**
   * Gets the reaction route name.
   *
   * @return route name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the reactor type.
   *
   * @return reactor type
   */
  public String getReactorType() {
    return reactorType;
  }

  /**
   * Gets the expected product component.
   *
   * @return component name, or null if not specified
   */
  public String getExpectedProductComponent() {
    return expectedProductComponent;
  }

  /**
   * Gets the energy mode.
   *
   * @return energy mode
   */
  public String getEnergyMode() {
    return energyMode;
  }

  /**
   * Gets the reactor temperature.
   *
   * @return reactor temperature in Kelvin, or NaN if not specified
   */
  public double getReactorTemperatureK() {
    return reactorTemperatureK;
  }

  /**
   * Gets the reactor pressure.
   *
   * @return reactor pressure in bara, or NaN if not specified
   */
  public double getReactorPressureBara() {
    return reactorPressureBara;
  }

  /**
   * Gets stoichiometric coefficients.
   *
   * @return unmodifiable component coefficient map
   */
  public Map<String, Double> getStoichiometry() {
    return Collections.unmodifiableMap(stoichiometry);
  }
}
