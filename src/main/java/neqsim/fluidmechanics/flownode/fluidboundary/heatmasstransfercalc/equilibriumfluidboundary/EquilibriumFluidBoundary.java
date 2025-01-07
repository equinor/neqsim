package neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.equilibriumfluidboundary;

import neqsim.fluidmechanics.flownode.FlowNodeInterface;
import neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.AnnularFlow;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * EquilibriumFluidBoundary class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class EquilibriumFluidBoundary
    extends neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.FluidBoundary {
  /** Serialization version UID. */
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
   * @param flowNode a {@link neqsim.fluidmechanics.flownode.FlowNodeInterface} object
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
  @ExcludeFromJacocoGeneratedReport
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
