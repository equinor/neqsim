package neqsim.util.unit;

import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class UnitsTest {
  @Test
  void testActivateUnits() {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemPrEos(298.0, 10.0);
    testSystem.addComponent("nitrogen", 0.01);
    testSystem.addComponent("CO2", 0.01);
    testSystem.addComponent("methane", 0.68);
    testSystem.addComponent("ethane", 0.1);
    testSystem.setMixingRule("classic");
    neqsim.thermo.ThermodynamicModelTest testModel =
        new neqsim.thermo.ThermodynamicModelTest(testSystem);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    neqsim.util.unit.Units.activateDefaultUnits();
    testSystem.createTable("results");
    neqsim.util.unit.Units.activateFieldUnits();
    testSystem.createTable("results");
    neqsim.util.unit.Units.activateSIUnits();
    testSystem.createTable("results");
    neqsim.util.unit.Units.activateMetricUnits();
    testSystem.createTable("results");
  }
}
