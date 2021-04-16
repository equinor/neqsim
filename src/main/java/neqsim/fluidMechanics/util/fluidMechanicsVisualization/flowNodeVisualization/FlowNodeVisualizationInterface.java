/*
 * FlowNodeIVisualizationInterface.java
 *
 * Created on 5. august 2001, 16:28
 */

package neqsim.fluidMechanics.util.fluidMechanicsVisualization.flowNodeVisualization;

import neqsim.fluidMechanics.flowNode.FlowNodeInterface;

/**
 *
 * @author esol
 * @version
 */
public interface FlowNodeVisualizationInterface {
    public void setData(FlowNodeInterface node);

    public double getPressure(int i);

    public double getTemperature(int i);

    public double getDistanceToCenterOfNode();

    public double getVelocity(int i);

    public double getBulkComposition(int i, int phase);

    public double getInterfaceComposition(int i, int phase);

    public int getNumberOfComponents();

    public double getPhaseFraction(int phase);

    public double getMolarFlux(int i, int phase);

    public double getInterfaceTemperature(int i);

    public double getInterphaseContactLength();

    public double getWallContactLength(int phase);

    public double getReynoldsNumber(int i);

    public double getEffectiveMassTransferCoefficient(int i, int phase);

    public double getEffectiveSchmidtNumber(int i, int phase);
}
