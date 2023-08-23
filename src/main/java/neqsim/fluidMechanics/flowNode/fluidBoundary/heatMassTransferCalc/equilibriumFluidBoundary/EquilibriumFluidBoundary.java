package neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.equilibriumFluidBoundary;

import neqsim.fluidMechanics.flowNode.FlowNodeInterface;
import neqsim.fluidMechanics.flowNode.twoPhaseNode.twoPhasePipeFlowNode.AnnularFlow;
import neqsim.fluidMechanics.geometryDefinitions.pipe.PipeData;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * EquilibriumFluidBoundary class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class EquilibriumFluidBoundary
    extends neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.FluidBoundary {
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for EquilibriumFluidBoundary.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public EquilibriumFluidBoundary(SystemInterface system) {
    super(system);
    interphaseOps = new ThermodynamicOperations(interphaseSystem);
    // interphaseOps.TPflash();
  }

  /**
   * <p>
   * Constructor for EquilibriumFluidBoundary.
   * </p>
   *
   * @param flowNode a {@link neqsim.fluidMechanics.flowNode.FlowNodeInterface} object
   */
  public EquilibriumFluidBoundary(FlowNodeInterface flowNode) {
    super(flowNode);
    interphaseOps = new ThermodynamicOperations(interphaseSystem);
    // interphaseOps.TPflash();
  }

  /** {@inheritDoc} */
  @Override
  public void solve() {
    getInterphaseOpertions().TPflash();
    getBulkSystemOpertions().TPflash();
  }

  /** {@inheritDoc} */
  @Override
  public double[] calcFluxes() {
    for (int i = 0; i < bulkSystem.getPhases()[0].getNumberOfComponents(); i++) {
      nFlux.set(i, 0, 0);
    }
    return nFlux.getArray()[0];
  }

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @SuppressWarnings("unused")
  public static void main(String[] args) {
    System.out.println("Starter.....");
    SystemSrkEos testSystem = new SystemSrkEos(295.3, 11.0);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    PipeData pipe1 = new PipeData(1.0, 0.55);

    testSystem.addComponent("methane", 100.152181, 1);
    testSystem.addComponent("water", 10.362204876, 0);
    testSystem.setMixingRule(2);

    FlowNodeInterface test = new AnnularFlow(testSystem, pipe1);
    test.init();

    EquilibriumFluidBoundary test2 = new EquilibriumFluidBoundary(test);
    test2.solve();
  }
}
