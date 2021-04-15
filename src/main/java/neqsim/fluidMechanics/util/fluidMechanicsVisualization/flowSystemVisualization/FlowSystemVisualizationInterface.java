/*
 * FlowVisualizationInterface.java
 *
 * Created on 26. oktober 2000, 20:06
 */

package neqsim.fluidMechanics.util.fluidMechanicsVisualization.flowSystemVisualization;

import neqsim.fluidMechanics.flowSystem.FlowSystemInterface;

/**
 *
 * @author esol
 * @version
 */
public interface FlowSystemVisualizationInterface {
    public void setPoints();

    public void displayResult(String name);

    public void setNextData(FlowSystemInterface system);

    public void setNextData(FlowSystemInterface system, double abstime);

    public void createNetCdfFile(String name);
}
