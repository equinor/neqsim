package neqsim.fluidmechanics.util.fluidmechanicsvisualization.flowsystemvisualization;

import neqsim.fluidmechanics.flowsystem.FlowSystemInterface;
import neqsim.fluidmechanics.util.fluidmechanicsvisualization.flownodevisualization.FlowNodeVisualization;
import neqsim.fluidmechanics.util.fluidmechanicsvisualization.flownodevisualization.FlowNodeVisualizationInterface;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * FlowSystemVisualization class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class FlowSystemVisualization implements FlowSystemVisualizationInterface {
  protected FlowNodeVisualizationInterface[][] flowNodes;
  protected FlowSystemInterface[] flowSystem;
  protected int time = 0;
  protected double[] absTime;

  /**
   * <p>
   * Constructor for FlowSystemVisualization.
   * </p>
   */
  public FlowSystemVisualization() {}

  /**
   * <p>
   * Constructor for FlowSystemVisualization.
   * </p>
   *
   * @param nodes a int
   * @param timeSteps a int
   */
  public FlowSystemVisualization(int nodes, int timeSteps) {
    flowNodes = new FlowNodeVisualization[timeSteps][nodes];
    flowSystem = new FlowSystemInterface[timeSteps];
    absTime = new double[timeSteps];
    for (int i = 0; i < timeSteps; i++) {
      for (int j = 0; j < nodes; j++) {
        flowNodes[i][j] = new FlowNodeVisualization();
      }
    }
    // System.out.println("nodes " + nodes);
    // System.out.println("times " + time);
  }

  /** {@inheritDoc} */
  @Override
  public void setNextData(FlowSystemInterface system) {
    flowSystem[time] = system;
    absTime[time] = 0;
    for (int i = 0; i < flowNodes[time].length; i++) {
      flowNodes[time][i].setData(system.getNode(i));
    }
    time++;
    // System.out.println("time " + time);
  }

  /** {@inheritDoc} */
  @Override
  public void setNextData(FlowSystemInterface system, double abstime) {
    flowSystem[time] = system;
    absTime[time] = abstime;
    for (int i = 0; i < flowNodes[time].length; i++) {
      flowNodes[time][i].setData(system.getNode(i));
    }
    time++;
  }

  /** {@inheritDoc} */
  @Override
  public void setPoints() {}

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult(String name) {}
}
