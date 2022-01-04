/*
 * FlowVisualization.java
 *
 * Created on 26. oktober 2000, 20:07
 */

package neqsim.fluidMechanics.util.fluidMechanicsVisualization.flowSystemVisualization;

import neqsim.fluidMechanics.flowSystem.FlowSystemInterface;
import neqsim.fluidMechanics.util.fluidMechanicsVisualization.flowNodeVisualization.FlowNodeVisualization;
import neqsim.fluidMechanics.util.fluidMechanicsVisualization.flowNodeVisualization.FlowNodeVisualizationInterface;

/**
 * <p>
 * FlowSystemVisualization class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class FlowSystemVisualization implements FlowSystemVisualizationInterface {
    private static final long serialVersionUID = 1000;

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
        System.out.println("nodes " + nodes);
        System.out.println("times " + time);
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
    public void createNetCdfFile(String name) {
        System.out.println("ok...");
        for (int j = 0; j < time; j++) {
            for (int i = 0; i < flowNodes[j].length; i++) {
                System.out.println("time " + time + " pres " + flowNodes[j][i].getPressure(0));
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void setPoints() {}

    /** {@inheritDoc} */
    @Override
    public void displayResult(String name) {}
}
