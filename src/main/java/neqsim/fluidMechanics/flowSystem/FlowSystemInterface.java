/*
 * FlowSystemInterface.java
 *
 * Created on 11. desember 2000, 17:17
 */

package neqsim.fluidMechanics.flowSystem;

import java.util.UUID;
import neqsim.fluidMechanics.flowNode.FlowNodeInterface;
import neqsim.fluidMechanics.flowSolver.FlowSolverInterface;
import neqsim.fluidMechanics.geometryDefinitions.GeometryDefinitionInterface;
import neqsim.fluidMechanics.util.fluidMechanicsDataHandeling.FileWriterInterface;
import neqsim.fluidMechanics.util.fluidMechanicsVisualization.flowSystemVisualization.FlowSystemVisualizationInterface;
import neqsim.fluidMechanics.util.timeSeries.TimeSeries;
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
   * @return a {@link neqsim.fluidMechanics.util.timeSeries.TimeSeries} object
   */
  public TimeSeries getTimeSeries();

  /**
   * <p>
   * getDisplay.
   * </p>
   *
   * @return a
   *         {@link neqsim.fluidMechanics.util.fluidMechanicsVisualization.flowSystemVisualization.FlowSystemVisualizationInterface}
   *         object
   */
  public FlowSystemVisualizationInterface getDisplay();

  /**
   * <p>
   * getFileWriter.
   * </p>
   *
   * @param i a int
   * @return a {@link neqsim.fluidMechanics.util.fluidMechanicsDataHandeling.FileWriterInterface}
   *         object
   */
  public FileWriterInterface getFileWriter(int i);

  /**
   * <p>
   * getSolver.
   * </p>
   *
   * @return a {@link neqsim.fluidMechanics.flowSolver.FlowSolverInterface} object
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
   * @param legHeights an array of {@link double} objects
   */
  public void setLegHeights(double[] legHeights);

  /**
   * <p>
   * getLegHeights.
   * </p>
   *
   * @return an array of {@link double} objects
   */
  public double[] getLegHeights();

  /**
   * <p>
   * setLegPositions.
   * </p>
   *
   * @param legPositions an array of {@link double} objects
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
   * @return a {@link neqsim.fluidMechanics.flowNode.FlowNodeInterface} object
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
   * @param coefs an array of {@link double} objects
   */
  public void setLegOuterHeatTransferCoefficients(double[] coefs);

  /**
   * <p>
   * setLegWallHeatTransferCoefficients.
   * </p>
   *
   * @param coefs an array of {@link double} objects
   */
  public void setLegWallHeatTransferCoefficients(double[] coefs);

  /**
   * <p>
   * setEquipmentGeometry.
   * </p>
   *
   * @param equipmentGeometry an array of
   *        {@link neqsim.fluidMechanics.geometryDefinitions.GeometryDefinitionInterface} objects
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
   * @return an array of {@link neqsim.fluidMechanics.flowNode.FlowNodeInterface} objects
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
   * @param temps an array of {@link double} objects
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
}
