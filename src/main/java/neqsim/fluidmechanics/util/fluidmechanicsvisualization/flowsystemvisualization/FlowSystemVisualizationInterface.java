package neqsim.fluidmechanics.util.fluidmechanicsvisualization.flowsystemvisualization;

import neqsim.fluidmechanics.flowsystem.FlowSystemInterface;

/**
 * FlowSystemVisualizationInterface interface.
 *
 * @author esol
 * @version $Id: $Id
 */
public interface FlowSystemVisualizationInterface {
  /**
   * setPoints.
   */
  public void setPoints();

  /**
   * displayResult.
   *
   * @param name a {@link java.lang.String} object
   */
  public void displayResult(String name);

  /**
   * setNextData.
   *
   * @param system a {@link neqsim.fluidmechanics.flowsystem.FlowSystemInterface} object
   */
  public void setNextData(FlowSystemInterface system);

  /**
   * setNextData.
   *
   * @param system a {@link neqsim.fluidmechanics.flowsystem.FlowSystemInterface} object
   * @param abstime a double
   */
  public void setNextData(FlowSystemInterface system, double abstime);
}
