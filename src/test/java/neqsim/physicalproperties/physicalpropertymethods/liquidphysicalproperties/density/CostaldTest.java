package neqsim.physicalproperties.physicalpropertymethods.liquidphysicalproperties.density;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class CostaldTest extends neqsim.NeqSimTest {
  static neqsim.thermo.system.SystemInterface testSystem = null;

  @BeforeAll
  public static void setUp() {
    testSystem = new neqsim.thermo.system.SystemSrkEos(344.26, 689.47);
    testSystem.addComponent("ethane", 70.0);
    testSystem.addComponent("nC10", 30.0);
    testSystem.setMixingRule("classic");
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
  }

  @Test
  void testCalcDensity() {
    testSystem.getPhase("oil").getPhysicalProperties().setDensityModel("Costald");
    double costaldDensity = testSystem.getPhase("oil").getPhysicalProperties().calcDensity();
    double costaldVolume = costaldDensity;
    assertEquals(628.6198, costaldVolume, 1e-3);
  }
}
