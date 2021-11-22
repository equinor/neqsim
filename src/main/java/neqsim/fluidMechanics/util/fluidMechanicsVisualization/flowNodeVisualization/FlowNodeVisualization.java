/*
 * FlowNodeVisualization.java
 *
 * Created on 5. august 2001, 16:27
 */

package neqsim.fluidMechanics.util.fluidMechanicsVisualization.flowNodeVisualization;

import neqsim.fluidMechanics.flowNode.FlowNodeInterface;

/**
 *
 * @author esol
 * @version
 */
public class FlowNodeVisualization implements FlowNodeVisualizationInterface {
    private static final long serialVersionUID = 1000;

    public double[] temperature = new double[2];
    public double[] reynoldsNumber = new double[2];
    public double[] interfaceTemperature = new double[2];
    public double[] pressure = new double[2];
    public double[] velocity = new double[2];
    public double[] phaseFraction = new double[2];
    public double[] wallContactLength = new double[2];
    public double[][] bulkComposition, interfaceComposition, effectiveMassTransferCoefficient,
            effectiveSchmidtNumber;
    public double[][] molarFlux;
    public double interphaseContactLength = 0.0;
    public double nodeCenter;
    public int numberOfComponents = 0;

    /** Creates new FlowNodeVisualization */
    public FlowNodeVisualization() {}

    @Override
    public void setData(FlowNodeInterface node) {
        temperature[0] = node.getBulkSystem().getPhases()[0].getTemperature();
        temperature[1] = node.getBulkSystem().getPhases()[1].getTemperature();
        pressure[0] = node.getBulkSystem().getPhases()[0].getPressure();
        pressure[1] = node.getBulkSystem().getPhases()[1].getPressure();
        velocity[0] = node.getVelocity(0);
        velocity[1] = node.getVelocity(1);
        reynoldsNumber[0] = node.getReynoldsNumber(0);
        interphaseContactLength = node.getInterphaseContactLength(0);
        wallContactLength[0] = node.getWallContactLength(0);
        wallContactLength[1] = node.getWallContactLength(1);
        numberOfComponents = node.getBulkSystem().getPhases()[0].getNumberOfComponents();
        nodeCenter = node.getDistanceToCenterOfNode();
    }

    @Override
    public int getNumberOfComponents() {
        return numberOfComponents;
    }

    @Override
    public double getInterphaseContactLength() {
        return interphaseContactLength;
    }

    @Override
    public double getWallContactLength(int phase) {
        return wallContactLength[phase];
    }

    @Override
    public double getPressure(int i) {
        return pressure[i];
    }

    @Override
    public double getReynoldsNumber(int i) {
        return reynoldsNumber[i];
    }

    @Override
    public double getDistanceToCenterOfNode() {
        return nodeCenter;
    }

    @Override
    public double getTemperature(int i) {
        return temperature[i];
    }

    @Override
    public double getInterfaceTemperature(int i) {
        return interfaceTemperature[i];
    }

    @Override
    public double getVelocity(int i) {
        return velocity[i];
    }

    @Override
    public double getBulkComposition(int i, int phase) {
        return bulkComposition[phase][i];
    }

    @Override
    public double getInterfaceComposition(int i, int phase) {
        return interfaceComposition[phase][i];
    }

    @Override
    public double getMolarFlux(int i, int phase) {
        return molarFlux[phase][i];
    }

    @Override
    public double getEffectiveMassTransferCoefficient(int i, int phase) {
        return effectiveMassTransferCoefficient[phase][i];
    }

    @Override
    public double getEffectiveSchmidtNumber(int i, int phase) {
        return effectiveSchmidtNumber[phase][i];
    }

    @Override
    public double getPhaseFraction(int phase) {
        return phaseFraction[phase];
    }
}
