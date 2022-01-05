package neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.finiteVolumeBoundary.fluidBoundarySystem.fluidBoundaryNonReactive;

import neqsim.fluidMechanics.flowNode.FlowNodeInterface;
import neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.FluidBoundaryInterface;
import neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.finiteVolumeBoundary.fluidBoundaryNode.fluidBoundaryNonReactiveNode.FluidBoundaryNodeNonReactive;
import neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.finiteVolumeBoundary.fluidBoundarySystem.FluidBoundarySystem;
import neqsim.fluidMechanics.flowNode.twoPhaseNode.twoPhasePipeFlowNode.StratifiedFlowNode;
import neqsim.fluidMechanics.geometryDefinitions.pipe.PipeData;
import neqsim.thermo.system.SystemFurstElectrolyteEos;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * FluidBoundarySystemNonReactive class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class FluidBoundarySystemNonReactive extends FluidBoundarySystem {
    private static final long serialVersionUID = 1000;

    /**
     * <p>
     * Constructor for FluidBoundarySystemNonReactive.
     * </p>
     */
    public FluidBoundarySystemNonReactive() {}

    /**
     * <p>
     * Constructor for FluidBoundarySystemNonReactive.
     * </p>
     *
     * @param boundary a
     *        {@link neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.FluidBoundaryInterface}
     *        object
     */
    public FluidBoundarySystemNonReactive(FluidBoundaryInterface boundary) {
        super(boundary);
    }

    /** {@inheritDoc} */
    @Override
    public void createSystem() {
        nodes = new FluidBoundaryNodeNonReactive[numberOfNodes];
        super.createSystem();

        for (int i = 0; i < numberOfNodes; i++) {
            nodes[i] = new FluidBoundaryNodeNonReactive(boundary.getInterphaseSystem());
        }
        System.out.println("system created...");
    }

    /**
     * <p>
     * main.
     * </p>
     *
     * @param args an array of {@link java.lang.String} objects
     */
    public static void main(String[] args) {
        SystemInterface testSystem = new SystemFurstElectrolyteEos(275.3, 1.01325);
        PipeData pipe1 = new PipeData(10.0, 0.025);

        testSystem.addComponent("methane", 0.061152181, 0);
        testSystem.addComponent("water", 0.1862204876, 1);
        testSystem.chemicalReactionInit();
        testSystem.setMixingRule(2);
        testSystem.init_x_y();

        FlowNodeInterface test = new StratifiedFlowNode(testSystem, pipe1);
        test.setInterphaseModelType(10);

        test.initFlowCalc();
        test.calcFluxes();
        test.getFluidBoundary().setEnhancementType(0);
        // test.getFluidBoundary().getEnhancementFactor().getNumericInterface().createSystem();
        // test.getFluidBoundary().getEnhancementFactor().getNumericInterface().solve();
        // System.out.println("enhancement " +
        // test.getFluidBoundary().getEnhancementFactor().getNumericInterface().getEnhancementFactor(0));
    }
}
