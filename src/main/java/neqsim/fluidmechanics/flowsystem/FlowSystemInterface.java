/*
 * FlowSystemInterface.java
 *
 * Created on 11. desember 2000, 17:17
 */

package neqsim.fluidmechanics.flowsystem;

import java.util.UUID;
import neqsim.fluidmechanics.flownode.FlowNodeInterface;
import neqsim.fluidmechanics.flowsolver.FlowSolverInterface;
import neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface;
import neqsim.fluidmechanics.util.fluidmechanicsvisualization.flowsystemvisualization.FlowSystemVisualizationInterface;
import neqsim.fluidmechanics.util.timeseries.TimeSeries;
import neqsim.thermo.system.SystemInterface;

/**
 * FlowSystemInterface interface.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface FlowSystemInterface {
  /**
   * init.
   */
  public void init();

  /**
   * setNodes.
   */
  public void setNodes();

  /**
   * solveTransient.
   *
   * @param type a int
   */
  public default void solveTransient(int type) {
    solveTransient(type, UUID.randomUUID());
  }

  /**
   * solveTransient.
   *
   * @param type a int
   * @param id Calculation identifier
   */
  public void solveTransient(int type, UUID id);

  /**
   * getTimeSeries.
   *
   * @return a {@link neqsim.fluidmechanics.util.timeseries.TimeSeries} object
   */
  public TimeSeries getTimeSeries();

  /**
   * getDisplay.
   *
   * @return a
   * {@link neqsim.fluidmechanics.util.fluidmechanicsvisualization.flowsystemvisualization.FlowSystemVisualizationInterface}
   * object
   */
  public FlowSystemVisualizationInterface getDisplay();

  /**
   * getSolver.
   *
   * @return a {@link neqsim.fluidmechanics.flowsolver.FlowSolverInterface} object
   */
  public FlowSolverInterface getSolver();

  /**
   * getInletTemperature.
   *
   * @return a double
   */
  public double getInletTemperature();

  /**
   * getInletPressure.
   *
   * @return a double
   */
  public double getInletPressure();

  /**
   * setNumberOfLegs.
   *
   * @param numberOfLegs a int
   */
  public void setNumberOfLegs(int numberOfLegs);

  /**
   * getNumberOfLegs.
   *
   * @return a int
   */
  public int getNumberOfLegs();

  /**
   * setNumberOfNodesInLeg.
   *
   * @param numberOfNodesInLeg a int
   */
  public void setNumberOfNodesInLeg(int numberOfNodesInLeg);

  /**
   * getNumberOfNodesInLeg.
   *
   * @param i a int
   * @return a int
   */
  public int getNumberOfNodesInLeg(int i);

  /**
   * setLegHeights.
   *
   * @param legHeights an array of type double
   */
  public void setLegHeights(double[] legHeights);

  /**
   * getLegHeights.
   *
   * @return an array of type double
   */
  public double[] getLegHeights();

  /**
   * setLegPositions.
   *
   * @param legPositions an array of type double
   */
  public void setLegPositions(double[] legPositions);

  /**
   * createSystem.
   */
  public void createSystem();

  /**
   * getNode.
   *
   * @param i a int
   * @return a {@link neqsim.fluidmechanics.flownode.FlowNodeInterface} object
   */
  public FlowNodeInterface getNode(int i);

  /**
   * getSystemLength.
   *
   * @return a double
   */
  public double getSystemLength();

  /**
   * setLegOuterHeatTransferCoefficients.
   *
   * @param coefs an array of type double
   */
  public void setLegOuterHeatTransferCoefficients(double[] coefs);

  /**
   * setLegWallHeatTransferCoefficients.
   *
   * @param coefs an array of type double
   */
  public void setLegWallHeatTransferCoefficients(double[] coefs);

  /**
   * setEquipmentGeometry.
   *
   * @param equipmentGeometry an array of {@link neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface}
   * objects
   */
  public void setEquipmentGeometry(GeometryDefinitionInterface[] equipmentGeometry);

  /**
   * getTotalNumberOfNodes.
   *
   * @return a int
   */
  public int getTotalNumberOfNodes();

  /**
   * calcFluxes.
   */
  public void calcFluxes();

  /**
   * setEndPressure.
   *
   * @param inletPressure a double
   */
  public void setEndPressure(double inletPressure);

  /**
   * setInletThermoSystem.
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public void setInletThermoSystem(SystemInterface thermoSystem);

  /**
   * solveSteadyState.
   *
   * @param type a int 1: just mass, 2: mass and energy, 3: mass, energy and energy impulse and components
   */
  public default void solveSteadyState(int type) {
    solveSteadyState(type, UUID.randomUUID());
  }

  /**
   * solveSteadyState.
   *
   * @param type a int 1: just mass, 2: mass and energy, 3: mass, energy and energy impulse and components
   * @param id Calculation identifier
   */
  public void solveSteadyState(int type, UUID id);

  /**
   * getFlowNodes.
   *
   * @return an array of {@link neqsim.fluidmechanics.flownode.FlowNodeInterface} objects
   */
  public FlowNodeInterface[] getFlowNodes();

  /**
   * print.
   */
  public void print();

  /**
   * setLegOuterTemperatures.
   *
   * @param temps an array of type double
   */
  public void setLegOuterTemperatures(double[] temps);

  /**
   * getTotalMolarMassTransferRate.
   *
   * @param component a int
   * @return a double
   */
  public double getTotalMolarMassTransferRate(int component);

  /**
   * getTotalMolarMassTransferRate.
   *
   * @param component a int
   * @param lastNode a int
   * @return a double
   */
  public double getTotalMolarMassTransferRate(int component, int lastNode);

  /**
   * getTotalPressureDrop.
   *
   * @return a double
   */
  public double getTotalPressureDrop();

  /**
   * getTotalPressureDrop.
   *
   * @param lastNode a int
   * @return a double
   */
  public double getTotalPressureDrop(int lastNode);

  /**
   * setInitialFlowPattern.
   *
   * @param flowPattern a {@link java.lang.String} object
   */
  public void setInitialFlowPattern(String flowPattern);

  /**
   * setFlowPattern.
   *
   * @param flowPattern a {@link java.lang.String} object
   */
  public void setFlowPattern(String flowPattern);

  /**
   * setEquilibriumMassTransfer.
   *
   * @param test a boolean
   */
  public void setEquilibriumMassTransfer(boolean test);

  /**
   * setEquilibriumHeatTransfer.
   *
   * @param test a boolean
   */
  public void setEquilibriumHeatTransfer(boolean test);

  /**
   * Set the advection scheme for compositional tracking.
   *
   * <p>
   * Different schemes offer trade-offs between accuracy and stability. Higher-order schemes reduce numerical dispersion
   * but may require smaller time steps. TVD schemes provide a good balance by using flux limiters to prevent
   * oscillations.
   * </p>
   *
   * @param scheme the advection scheme to use
   * @see neqsim.fluidmechanics.flowsolver.AdvectionScheme
   */
  public default void setAdvectionScheme(neqsim.fluidmechanics.flowsolver.AdvectionScheme scheme) {
    // Default implementation - subclasses should override
  }

  /**
   * Get the current advection scheme for compositional tracking.
   *
   * @return the current advection scheme
   */
  public default neqsim.fluidmechanics.flowsolver.AdvectionScheme getAdvectionScheme() {
    return neqsim.fluidmechanics.flowsolver.AdvectionScheme.FIRST_ORDER_UPWIND;
  }
}
