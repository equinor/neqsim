/*
 * FluidBoundarySystemReactive.java
 *
 * Created on 8. august 2001, 13:56
 */

package neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.finiteVolumeBoundary.fluidBoundarySystem.fluidBoundarySystemReactive;

import neqsim.fluidMechanics.flowNode.FlowNodeInterface;
import neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.FluidBoundaryInterface;
import neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.finiteVolumeBoundary.fluidBoundaryNode.fluidBoundaryReactiveNode.FluidBoundaryNodeReactive;
import neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.finiteVolumeBoundary.fluidBoundarySystem.FluidBoundarySystem;
import neqsim.fluidMechanics.flowNode.twoPhaseNode.twoPhasePipeFlowNode.StratifiedFlowNode;
import neqsim.fluidMechanics.geometryDefinitions.pipe.PipeData;
import neqsim.thermo.system.SystemFurstElectrolyteEos;
import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author  esol
 * @version
 */
public class FluidBoundarySystemReactive extends FluidBoundarySystem{

    private static final long serialVersionUID = 1000;
    
    
    /** Creates new FluidBoundarySystemReactive */
    public FluidBoundarySystemReactive() {
    }
    
    public FluidBoundarySystemReactive(FluidBoundaryInterface boundary){
        super(boundary);
        reactive = true;
    }
    
    public void createSystem(){
        nodes = new FluidBoundaryNodeReactive[numberOfNodes];
        super.createSystem();
        
        for(int i=0;i<numberOfNodes;i++){
            nodes[i] = new FluidBoundaryNodeReactive(boundary.getInterphaseSystem());
        }
        System.out.println("system created...");
    }
    
    public static void main(String[] args){
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
        test.calcFluxes();
      /*  test.getFluidBoundary().getEnhancementFactor().getNumericInterface().createSystem();
        test.getFluidBoundary().getEnhancementFactor().getNumericInterface().solve();
        System.out.println("enhancement " +
        test.getFluidBoundary().getEnhancementFactor().getNumericInterface().getEnhancementFactor(0));
         **/
    }
    
}
