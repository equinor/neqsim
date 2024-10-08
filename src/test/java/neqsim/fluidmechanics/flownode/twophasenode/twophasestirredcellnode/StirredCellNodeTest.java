package neqsim.fluidmechanics.flownode.twophasenode.twophasestirredcellnode;

import org.junit.jupiter.api.Test;
import neqsim.fluidmechanics.geometrydefinitions.stirredcell.StirredCell;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class StirredCellNodeTest {
  @Test
  void testUpdate() {
    SystemInterface testSystem = new SystemSrkEos(313.3, 10.01325);
    StirredCell pipe1 = new StirredCell(2.0, 0.05);
    // testSystem.addComponent("CO2", 1, "kg/hr", 0);
    testSystem.addComponent("methane", 10, "kg/hr", 0);
    // testSystem.addComponent("ethane", 1, "kg/hr", 0);
    // testSystem.addComponent("n-hexane", 1.206862204876, "kg/min", 0);
    testSystem.addComponent("nC10", 3, "kg/hr", 1);
    testSystem.setMixingRule(2);

    StirredCellNode test = new StirredCellNode(testSystem, pipe1);
    test.setInterphaseModelType(1);
    test.getFluidBoundary().useFiniteFluxCorrection(true);
    test.getFluidBoundary().useThermodynamicCorrections(true);

    test.setStirrerSpeed(10.0 / 60.0);
    test.setStirrerDiameter(1.0);
    test.setDt(0.0010);

    for (int i = 0; i < 10; i++) {
      test.initFlowCalc();
      test.calcFluxes();
      test.update();
    }
  }
}
