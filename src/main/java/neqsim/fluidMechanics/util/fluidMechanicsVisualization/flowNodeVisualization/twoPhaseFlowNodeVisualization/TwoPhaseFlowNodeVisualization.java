/*
 * OnePhaseFlowNodeVisualization.java
 *
 * Created on 5. august 2001, 16:29
 */

package neqsim.fluidMechanics.util.fluidMechanicsVisualization.flowNodeVisualization.twoPhaseFlowNodeVisualization;

import neqsim.fluidMechanics.flowNode.FlowNodeInterface;
import neqsim.fluidMechanics.util.fluidMechanicsVisualization.flowNodeVisualization.FlowNodeVisualization;

/**
 *
 * @author esol
 * @version
 */
public class TwoPhaseFlowNodeVisualization extends FlowNodeVisualization {

    private static final long serialVersionUID = 1000;

    /** Creates new OnePhaseFlowNodeVisualization */
    public TwoPhaseFlowNodeVisualization() {
    }

    @Override
	public void setData(FlowNodeInterface node) {
        super.setData(node);
        bulkComposition = new double[2][node.getBulkSystem().getPhases()[0].getNumberOfComponents()];
        effectiveMassTransferCoefficient = new double[2][node.getBulkSystem().getPhases()[0].getNumberOfComponents()];
        effectiveSchmidtNumber = new double[2][node.getBulkSystem().getPhases()[0].getNumberOfComponents()];
        interfaceComposition = new double[2][node.getBulkSystem().getPhases()[0].getNumberOfComponents()];
        molarFlux = new double[2][node.getBulkSystem().getPhases()[0].getNumberOfComponents()];

        for (int i = 0; i < node.getBulkSystem().getPhases()[0].getNumberOfComponents(); i++) {
            bulkComposition[0][i] = node.getBulkSystem().getPhases()[0].getComponents()[i].getx();
            bulkComposition[1][i] = node.getBulkSystem().getPhases()[1].getComponents()[i].getx();
            effectiveMassTransferCoefficient[1][i] = node.getFluidBoundary().getEffectiveMassTransferCoefficient(1, i);
            effectiveMassTransferCoefficient[0][i] = node.getFluidBoundary().getEffectiveMassTransferCoefficient(0, i);
            effectiveSchmidtNumber[0][i] = node.getEffectiveSchmidtNumber(0, i);
            effectiveSchmidtNumber[1][i] = node.getEffectiveSchmidtNumber(1, i);
            // System.out.println("sc " + effectiveSchmidtNumber[1][i]);
            interfaceComposition[0][i] = node.getFluidBoundary().getInterphaseSystem().getPhases()[0].getComponents()[i]
                    .getx();
            interfaceComposition[1][i] = node.getFluidBoundary().getInterphaseSystem().getPhases()[1].getComponents()[i]
                    .getx();
            molarFlux[0][i] = node.getFluidBoundary().getInterphaseMolarFlux(i);
            molarFlux[1][i] = node.getFluidBoundary().getInterphaseMolarFlux(i);
        }

        reynoldsNumber[0] = node.getReynoldsNumber(0);
        reynoldsNumber[1] = node.getReynoldsNumber(1);
        phaseFraction[0] = node.getPhaseFraction(0);
        phaseFraction[1] = node.getPhaseFraction(1);
        interfaceTemperature[0] = node.getFluidBoundary().getInterphaseSystem().getPhases()[0].getTemperature();
        interfaceTemperature[1] = node.getFluidBoundary().getInterphaseSystem().getPhases()[1].getTemperature();
    }

}
