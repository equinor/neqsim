package neqsim.thermo.util.gerg;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.ThermodynamicModelTest;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

class GERG2008ConsistencyTest {
  @Test
  void testThermodynamicConsistency() {
    SystemInterface system = new neqsim.thermo.system.SystemGERG2008Eos(298.15, 10.0);
    system.addComponent("methane", 0.8);
    system.addComponent("ethane", 0.2);
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    system.init(0);
    ops.TPflash();
    system.init(3);

    ThermodynamicModelTest modelTest = new ThermodynamicModelTest(system);
    assertTrue(modelTest.checkFugacityCoefficientsDP());
    assertTrue(modelTest.checkFugacityCoefficientsDT());
  }

  @Test
  void testCompositionalDerivatives() {
    SystemInterface system = new neqsim.thermo.system.SystemGERG2008Eos(293.15, 50.0);
    system.addComponent("methane", 0.7);
    system.addComponent("CO2", 0.2);
    system.addComponent("ethane", 0.1);
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    system.init(0);
    ops.TPflash();
    system.init(3);

    ThermodynamicModelTest modelTest = new ThermodynamicModelTest(system);
    assertTrue(modelTest.checkFugacityCoefficientsDn());
  }
}
