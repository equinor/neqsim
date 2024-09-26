package neqsim.fluidMechanics.flowNode.twoPhaseNode.twoPhaseStirredCellNode;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import neqsim.fluidMechanics.geometryDefinitions.stirredCell.StirredCell;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

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

  @Test
  void testStirredCell2() {
    SystemInterface testSystem = new SystemSrkEos(313.3, 70.01325);
    StirredCell cell1 = new StirredCell(0.1, 0.05);
    testSystem.addComponent("methane", 0.6457851061152181, "kg/min");
    testSystem.addComponent("ethane", 0.1206862204876, "kg/min");
    testSystem.addComponent("propane", 0.206862204876, "kg/min");
    testSystem.addComponent("nC10", 10.206862204876, "kg/min");
    testSystem.setMixingRule(2);

    ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
    ops.TPflash();

    testSystem.prettyPrint();

    testSystem.setPressure(10.0);
    testSystem.init(1);

    testSystem.prettyPrint();

    StirredCellNode test = new StirredCellNode(testSystem, cell1);
    test.setInterphaseModelType(1);
    test.getFluidBoundary().useFiniteFluxCorrection(true);
    test.getFluidBoundary().useThermodynamicCorrections(true);
    test.setStirrerSpeed(0.1);
    test.setStirrerDiameter(0.1);
    test.setDt(1.0);

    test.initFlowCalc();

    ArrayList<Double> Kvalue_C1 = new ArrayList<Double>();
    ArrayList<Double> Kvalue_C2 = new ArrayList<Double>();
    ArrayList<Double> Kvalue_C3 = new ArrayList<Double>();
    ArrayList<Double> Kvalue_C10 = new ArrayList<Double>();
    ArrayList<Double> gas_mole_fraction = new ArrayList<Double>();

    for (int i = 0; i < 10000; i++) {
      Kvalue_C1.add(test.getBulkSystem().getPhase(0).getComponent("methane").getx()
          / test.getBulkSystem().getPhase(1).getComponent("methane").getx());
      Kvalue_C2.add(test.getBulkSystem().getPhase(0).getComponent("ethane").getx()
          / test.getBulkSystem().getPhase(1).getComponent("ethane").getx());
      Kvalue_C3.add(test.getBulkSystem().getPhase(0).getComponent("propane").getx()
          / test.getBulkSystem().getPhase(1).getComponent("propane").getx());
      Kvalue_C10.add(test.getBulkSystem().getPhase(0).getComponent("nC10").getx()
          / test.getBulkSystem().getPhase(1).getComponent("nC10").getx());
      gas_mole_fraction.add(test.getBulkSystem().getPhase(0).getNumberOfMolesInPhase());
      test.initFlowCalc();
      test.calcFluxes();
      test.update();
    }

    // for (int i = 0; i < 100; i++) {
    // System.out.println("Kvalue_C1: " + Kvalue_C1);
    // System.out.println("Kvalue_C3: " + Kvalue_C2);
    // System.out.println("Kvalue_C3: " + Kvalue_C3);
    // System.out.println("Kvalue_C10: " + Kvalue_C10);
    System.out.println("gas_mole_fraction: " + gas_mole_fraction);

    ops.TPflash();

    testSystem.prettyPrint();
    // }
  }
}
