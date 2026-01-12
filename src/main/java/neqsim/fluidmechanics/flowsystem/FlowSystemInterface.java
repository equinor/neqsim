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
 * <p>
 * FlowSystemInterface interface.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface FlowSystemInterface {
  /**
   * <p>
   * init.
   * </p>
   */
  public void init();

  /**
   * <p>
   * setNodes.
   * </p>
   */
  public void setNodes();

  /**
   * <p>
   * solveTransient.
   * </p>
   *
   * @param type a int
   */
  public default void solveTransient(int type) {
    solveTransient(type, UUID.randomUUID());
  }

  /**
   * <p>
   * solveTransient.
   * </p>
   *
   * @param type a int
   * @param id Calculation identifier
   */
  public void solveTransient(int type, UUID id);

  /**
   * <p>
   * getTimeSeries.
   * </p>
   *
   * @return a {@link neqsim.fluidmechanics.util.timeseries.TimeSeries} object
   */
  public TimeSeries getTimeSeries();

  /**
   * <p>
   * getDisplay.
   * </p>
   *
   * @return a
   *         {@link neqsim.fluidmechanics.util.fluidmechanicsvisualization.flowsystemvisualization.FlowSystemVisualizationInterface}
   *         object
   */
  public FlowSystemVisualizationInterface getDisplay();

  /**
   * <p>
   * getSolver.
   * </p>
   *
   * @return a {@link neqsim.fluidmechanics.flowsolver.FlowSolverInterface} object
   */
  public FlowSolverInterface getSolver();

  /**
   * <p>
   * getInletTemperature.
   * </p>
   *
   * @return a double
   */
  public double getInletTemperature();

  /**
   * <p>
   * getInletPressure.
   * </p>
   *
   * @return a double
   */
  public double getInletPressure();

  /**
   * <p>
   * setNumberOfLegs.
   * </p>
   *
   * @param numberOfLegs a int
   */
  public void setNumberOfLegs(int numberOfLegs);

  /**
   * <p>
   * getNumberOfLegs.
   * </p>
   *
   * @return a int
   */
  public int getNumberOfLegs();

  /**
   * <p>
   * setNumberOfNodesInLeg.
   * </p>
   *
   * @param numberOfNodesInLeg a int
   */
  public void setNumberOfNodesInLeg(int numberOfNodesInLeg);

  /**
   * <p>
   * getNumberOfNodesInLeg.
   * </p>
   *
   * @param i a int
   * @return a int
   */
  public int getNumberOfNodesInLeg(int i);

  /**
   * <p>
   * setLegHeights.
   * </p>
   *
   * @param legHeights an array of type double
   */
  public void setLegHeights(double[] legHeights);

  /**
   * <p>
   * getLegHeights.
   * </p>
   *
   * @return an array of type double
   */
  public double[] getLegHeights();

  /**
   * <p>
   * setLegPositions.
   * </p>
   *
   * @param legPositions an array of type double
   */
  public void setLegPositions(double[] legPositions);

  /**
   * <p>
   * createSystem.
   * </p>
   */
  public void createSystem();

  /**
   * <p>
   * getNode.
   * </p>
   *
   * @param i a int
   * @return a {@link neqsim.fluidmechanics.flownode.FlowNodeInterface} object
   */
  public FlowNodeInterface getNode(int i);

  /**
   * <p>
   * getSystemLength.
   * </p>
   *
   * @return a double
   */
  public double getSystemLength();

  /**
   * <p>
   * setLegOuterHeatTransferCoefficients.
   * </p>
   *
   * @param coefs an array of type double
   */
  public void setLegOuterHeatTransferCoefficients(double[] coefs);

  /**
   * <p>
   * setLegWallHeatTransferCoefficients.
   * </p>
   *
   * @param coefs an array of type double
   */
  public void setLegWallHeatTransferCoefficients(double[] coefs);

  /**
   * <p>
   * setEquipmentGeometry.
   * </p>
   *
   * @param equipmentGeometry an array of
   *        {@link neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface} objects
   */
  public void setEquipmentGeometry(GeometryDefinitionInterface[] equipmentGeometry);

  /**
   * <p>
   * getTotalNumberOfNodes.
   * </p>
   *
   * @return a int
   */
  public int getTotalNumberOfNodes();

  /**
   * <p>
   * calcFluxes.
   * </p>
   */
  public void calcFluxes();

  /**
   * <p>
   * setEndPressure.
   * </p>
   *
   * @param inletPressure a double
   */
  public void setEndPressure(double inletPressure);

  /**
   * <p>
   * setInletThermoSystem.
   * </p>
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public void setInletThermoSystem(SystemInterface thermoSystem);

  /**
   * <p>
   * solveSteadyState.
   * </p>
   *
   * @param type a int 1: just mass, 2: mass and energy, 3: mass, energy and energy impulse and
   *        components
   */
  public default void solveSteadyState(int type) {
    solveSteadyState(type, UUID.randomUUID());
  }

  /**
   * <p>
   * solveSteadyState.
   * </p>
   *
   * @param type a int 1: just mass, 2: mass and energy, 3: mass, energy and energy impulse and
   *        components
   * @param id Calculation identifier
   */
  public void solveSteadyState(int type, UUID id);

  /**
   * <p>
   * getFlowNodes.
   * </p>
   *
   * @return an array of {@link neqsim.fluidmechanics.flownode.FlowNodeInterface} objects
   */
  public FlowNodeInterface[] getFlowNodes();

  /**
   * <p>
   * print.
   * </p>
   */
  public void print();

  /**
   * <p>
   * setLegOuterTemperatures.
   * </p>
   *
   * @param temps an array of type double
   */
  public void setLegOuterTemperatures(double[] temps);

  /**
   * <p>
   * getTotalMolarMassTransferRate.
   * </p>
   *
   * @param component a int
   * @return a double
   */
  public double getTotalMolarMassTransferRate(int component);

  /**
   * <p>
   * getTotalMolarMassTransferRate.
   * </p>
   *
   * @param component a int
   * @param lastNode a int
   * @return a double
   */
  public double getTotalMolarMassTransferRate(int component, int lastNode);

  /**
   * <p>
   * getTotalPressureDrop.
   * </p>
   *
   * @return a double
   */
  public double getTotalPressureDrop();

  /**
   * <p>
   * getTotalPressureDrop.
   * </p>
   *
   * @param lastNode a int
   * @return a double
   */
  public double getTotalPressureDrop(int lastNode);

  /**
   * <p>
   * setInitialFlowPattern.
   * </p>
   *
   * @param flowPattern a {@link java.lang.String} object
   */
  public void setInitialFlowPattern(String flowPattern);

  /**
   * <p>
   * setFlowPattern.
   * </p>
   *
   * @param flowPattern a {@link java.lang.String} object
   */
  public void setFlowPattern(String flowPattern);

  /**
   * <p>
   * setEquilibriumMassTransfer.
   * </p>
   *
   * @param test a boolean
   */
  public void setEquilibriumMassTransfer(boolean test);

  /**
   * <p>
   * setEquilibriumHeatTransfer.
   * </p>
   *
   * @param test a boolean
   */
  public void setEquilibriumHeatTransfer(boolean test);

  /**
   * <p>
   * Set the advection scheme for compositional tracking.
   * </p>
   *
   * <p>
   * Different schemes offer trade-offs between accuracy and stability. Higher-order schemes reduce
   * numerical dispersion but may require smaller time steps. TVD schemes provide a good balance by
   * using flux limiters to prevent oscillations.
   * </p>
   *
   * @param scheme the advection scheme to use
   * @see neqsim.fluidmechanics.flowsolver.AdvectionScheme
   */
  public default void setAdvectionScheme(neqsim.fluidmechanics.flowsolver.AdvectionScheme scheme) {
    // Default implementation - subclasses should override
  }

  /**
   * <p>
   * Get the current advection scheme for compositional tracking.
   * </p>
   *
   * @return the current advection scheme
   */
  public default neqsim.fluidmechanics.flowsolver.AdvectionScheme getAdvectionScheme() {
    return neqsim.fluidmechanics.flowsolver.AdvectionScheme.FIRST_ORDER_UPWIND;
  }
}
