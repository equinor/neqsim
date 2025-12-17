package neqsim.fluidmechanics.flowsystem.twophaseflowsystem.stirredcellsystem;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.fluidmechanics.flownode.twophasenode.twophasestirredcellnode.StirredCellNode;
import neqsim.fluidmechanics.geometrydefinitions.stirredcell.StirredCell;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression tests for non-equilibrium mass transfer in a stirred cell.
 */
public class StirredCellDissolutionTest {
  /**
   * Verifies a small methane gas inventory can dissolve into excess liquid n-decane when starting
   * off-equilibrium (methane-rich gas, methane-lean liquid).
   */
  @Test
  void testMethaneGasDissolvesCompletelyIntoNDecane() {
    SystemInterface system = new SystemSrkEos(305.0, 120.0);
    system.addComponent("methane", 0.02, 0);
    system.addComponent("nC10", 10.0, 1);
    system.createDatabase(true);
    system.setMixingRule(2);
    system.initProperties();

    StirredCellNode node = new StirredCellNode(system, new StirredCell(0.2));
    node.setInterphaseModelType(1);
    node.getFluidBoundary().setMassTransferCalc(true);
    node.setDt(0.05);
    node.initFlowCalc();
    node.init();

    int methaneIndex = node.getBulkSystem().getPhase(0).getComponent("methane").getComponentNumber();
    double initialGasMethane =
        node.getBulkSystem().getPhase(0).getComponent(methaneIndex).getNumberOfMolesInPhase();

    double gasMethane = initialGasMethane;
    for (int step = 0; step < 20000 && gasMethane > 1e-12; step++) {
      node.calcFluxes();

      double flux = node.getFluidBoundary().getInterphaseMolarFlux(methaneIndex);
      double area = node.getInterphaseContactArea();
      double dt = node.getDt();
      double deltaGasMethane = -flux * area * dt;

      if (deltaGasMethane < 0.0 && gasMethane + deltaGasMethane < 0.0) {
        double safeDt = 0.5 * gasMethane / (-flux * area);
        node.setDt(Math.max(1e-8, Math.min(dt, safeDt)));
      }

      node.update();
      gasMethane =
          node.getBulkSystem().getPhase(0).getComponent(methaneIndex).getNumberOfMolesInPhase();
    }

    assertTrue(gasMethane < 1e-6,
        "Expected methane gas phase inventory to dissolve to near-zero moles, got " + gasMethane);
    assertTrue(gasMethane < 1e-4 * initialGasMethane,
        "Expected >99.99% dissolution, initial=" + initialGasMethane + " final=" + gasMethane);
  }
}
