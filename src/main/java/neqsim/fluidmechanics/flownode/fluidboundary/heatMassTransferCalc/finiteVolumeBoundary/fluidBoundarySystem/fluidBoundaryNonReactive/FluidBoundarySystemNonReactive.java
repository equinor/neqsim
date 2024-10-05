package neqsim.fluidmechanics.flownode.fluidboundary.heatMassTransferCalc.finiteVolumeBoundary.fluidBoundarySystem.fluidBoundaryNonReactive;

import neqsim.fluidmechanics.flownode.FlowNodeInterface;
import neqsim.fluidmechanics.flownode.fluidboundary.heatMassTransferCalc.FluidBoundaryInterface;
import neqsim.fluidmechanics.flownode.fluidboundary.heatMassTransferCalc.finiteVolumeBoundary.fluidBoundaryNode.fluidBoundaryNonReactiveNode.FluidBoundaryNodeNonReactive;
import neqsim.fluidmechanics.flownode.fluidboundary.heatMassTransferCalc.finiteVolumeBoundary.fluidBoundarySystem.FluidBoundarySystem;
import neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.StratifiedFlowNode;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;
import neqsim.thermo.ThermodynamicConstantsInterface;
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
   *        {@link neqsim.fluidmechanics.flownode.fluidboundary.heatMassTransferCalc.FluidBoundaryInterface}
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
    SystemInterface testSystem = new SystemFurstElectrolyteEos(275.3, ThermodynamicConstantsInterface.referencePressure);
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
