/*
 * PipeLineInterface.java
 *
 * Created on 21. august 2001, 20:44
 */

package neqsim.processsimulation.processequipment.pipeline;

import neqsim.fluidmechanics.flowsystem.FlowSystemInterface;
import neqsim.processsimulation.SimulationInterface;
import neqsim.processsimulation.processequipment.TwoPortInterface;

/**
 * <p>
 * PipeLineInterface interface.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public interface PipeLineInterface extends SimulationInterface, TwoPortInterface {
  /**
   * <p>
   * setNumberOfLegs.
   * </p>
   *
   * @param number a int
   */
  public void setNumberOfLegs(int number);

  /**
   * <p>
   * setHeightProfile.
   * </p>
   *
   * @param heights an array of type double
   */
  public void setHeightProfile(double[] heights);

  /**
   * <p>
   * setLegPositions.
   * </p>
   *
   * @param positions an array of type double
   */
  public void setLegPositions(double[] positions);

  /**
   * <p>
   * setPipeDiameters.
   * </p>
   *
   * @param diameter an array of type double
   */
  public void setPipeDiameters(double[] diameter);

  /**
   * <p>
   * setPipeWallRoughness.
   * </p>
   *
   * @param rough an array of type double
   */
  public void setPipeWallRoughness(double[] rough);

  /**
   * <p>
   * setOuterTemperatures.
   * </p>
   *
   * @param outerTemp an array of type double
   */
  public void setOuterTemperatures(double[] outerTemp);

  /**
   * <p>
   * setNumberOfNodesInLeg.
   * </p>
   *
   * @param number a int
   */
  public void setNumberOfNodesInLeg(int number);

  /**
   * <p>
   * setOutputFileName.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public void setOutputFileName(String name);

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
   * getPipe.
   * </p>
   *
   * @return a {@link neqsim.fluidmechanics.flowsystem.FlowSystemInterface} object
   */
  public FlowSystemInterface getPipe();
}
