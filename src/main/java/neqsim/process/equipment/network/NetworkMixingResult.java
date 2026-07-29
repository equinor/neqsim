package neqsim.process.equipment.network;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Conservative component-flow result for one network mixing node.
 *
 * <p>
 * Component rates are molar flow rates in mol/s. The result records the inlet totals used to construct the
 * thermodynamic state and the numerical closure error after converting the mixed composition back to component flow.
 * </p>
 */
public final class NetworkMixingResult implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String nodeName;
  private final double totalMassFlowKgS;
  private final double totalMolarFlowMolS;
  private final double pressurePa;
  private final double temperatureK;
  private final double maxComponentBalanceResidualMolS;
  private final double maxComponentMassBalanceResidualKgS;
  private final Map<String, Double> componentMolarFlowMolS;

  /**
   * Create a node mixing result.
   *
   * @param nodeName network node name
   * @param totalMassFlowKgS total inlet mass flow in kg/s
   * @param totalMolarFlowMolS total inlet molar flow in mol/s
   * @param pressurePa mixed-state pressure in Pa absolute
   * @param temperatureK mixed-state temperature in K
   * @param maxComponentBalanceResidualMolS maximum absolute component balance residual in mol/s
   * @param maxComponentMassBalanceResidualKgS maximum absolute component mass balance residual in kg/s
   * @param componentMolarFlowMolS component molar inlet flows in mol/s
   */
  public NetworkMixingResult(String nodeName, double totalMassFlowKgS, double totalMolarFlowMolS, double pressurePa,
      double temperatureK, double maxComponentBalanceResidualMolS, double maxComponentMassBalanceResidualKgS,
      Map<String, Double> componentMolarFlowMolS) {
    this.nodeName = nodeName;
    this.totalMassFlowKgS = totalMassFlowKgS;
    this.totalMolarFlowMolS = totalMolarFlowMolS;
    this.pressurePa = pressurePa;
    this.temperatureK = temperatureK;
    this.maxComponentBalanceResidualMolS = maxComponentBalanceResidualMolS;
    this.maxComponentMassBalanceResidualKgS = maxComponentMassBalanceResidualKgS;
    this.componentMolarFlowMolS = new LinkedHashMap<String, Double>(componentMolarFlowMolS);
  }

  /**
   * Get the node name.
   *
   * @return node name
   */
  public String getNodeName() {
    return nodeName;
  }

  /**
   * Get total inlet mass flow.
   *
   * @return mass flow in kg/s
   */
  public double getTotalMassFlowKgS() {
    return totalMassFlowKgS;
  }

  /**
   * Get total inlet molar flow.
   *
   * @return molar flow in mol/s
   */
  public double getTotalMolarFlowMolS() {
    return totalMolarFlowMolS;
  }

  /**
   * Get the mixed-state pressure.
   *
   * @return pressure in Pa absolute
   */
  public double getPressurePa() {
    return pressurePa;
  }

  /**
   * Get the mixed-state temperature.
   *
   * @return temperature in K
   */
  public double getTemperatureK() {
    return temperatureK;
  }

  /**
   * Get the maximum component balance residual.
   *
   * @return maximum absolute residual in mol/s
   */
  public double getMaxComponentBalanceResidualMolS() {
    return maxComponentBalanceResidualMolS;
  }

  /**
   * Get the maximum component mass balance residual.
   *
   * @return maximum absolute residual in kg/s
   */
  public double getMaxComponentMassBalanceResidualKgS() {
    return maxComponentMassBalanceResidualKgS;
  }

  /**
   * Get component molar inlet flows.
   *
   * @return immutable component-name to mol/s map
   */
  public Map<String, Double> getComponentMolarFlowMolS() {
    return Collections.unmodifiableMap(componentMolarFlowMolS);
  }
}
