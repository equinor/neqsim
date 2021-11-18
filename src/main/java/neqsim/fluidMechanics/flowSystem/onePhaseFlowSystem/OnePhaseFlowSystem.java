

package neqsim.fluidMechanics.flowSystem.onePhaseFlowSystem;

import neqsim.fluidMechanics.flowSystem.FlowSystem;
import neqsim.fluidMechanics.geometryDefinitions.pipe.PipeData;
import neqsim.thermo.system.SystemInterface;

public abstract class OnePhaseFlowSystem extends FlowSystem {
    private static final long serialVersionUID = 1000;

    // public FluidMechanicsInterface[] flowNode;
    public PipeData pipe;

    public OnePhaseFlowSystem() {}

    public OnePhaseFlowSystem(SystemInterface system) {}

    public static void main(String[] args) {
        System.out.println("Hei der!");
    }
}
