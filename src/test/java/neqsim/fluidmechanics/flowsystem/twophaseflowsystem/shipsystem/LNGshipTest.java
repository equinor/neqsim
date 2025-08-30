package neqsim.fluidmechanics.flowsystem.twophaseflowsystem.shipsystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;

class LNGshipTest {
  LNGship testShip;
  SystemInterface testSystem;

  @BeforeEach
  void setUp() {
    testSystem = new neqsim.thermo.system.SystemSrkEos(100, 1.0);
    testSystem.addComponent("methane", 0.9);
    testSystem.addComponent("nitrogen", 0.1);
    testSystem.init(0);

    testShip = new LNGship(testSystem, 1_000_000, 0.01);
  }

  @Test
  void createSystem() {
    assertEquals(0.7, testShip.getLiquidDensity());
    assertEquals(0.0, testShip.dailyBoilOffVolume);
    assertEquals(0.0, testShip.initialNumberOffMoles);
    testShip.createSystem();
    assertEquals(474.329344756, testShip.getLiquidDensity(), 1e-4);
    assertEquals(10_000.0, testShip.dailyBoilOffVolume, 1e-2);
    assertEquals(2.7513223265419292E10, testShip.initialNumberOffMoles, 1e-2);
  }

  @Test
  public void testSolveSteadyState() {
    assertEquals(-173.15, testShip.getThermoSystem().getTemperature("C"), 1e-2);
    testShip.createSystem();
    testShip.solveSteadyState(1, null);
    assertEquals(-176.43, testShip.getThermoSystem().getTemperature("C"), 1e-2);
    assertEquals(testSystem.getPhase("oil").getComponent("nitrogen").getx(),
        testShip.getThermoSystem().getPhase("oil").getComponent("nitrogen").getx(), 1e-4);
    assertEquals(testSystem.getPhase("oil").getComponent("methane").getx(),
        testShip.getThermoSystem().getPhase("oil").getComponent("methane").getx(), 1e-4);
  }

  @Test
  void solveTransient() {
    assertNull(testShip.volume);
    assertEquals(0.0, testShip.endVolume);
    assertEquals(1_000_000, testShip.totalTankVolume); // Initial cargo volume

    testShip.createSystem();
    testShip.solveSteadyState(0, null);
    testShip.solveTransient(0, null);

    assertEquals(testShip.numberOffTimeSteps, testShip.tankTemperature.length); // Check that the
                                                                                // results have
                                                                                // correct length
    assertEquals(600_000.0, testShip.endVolume);
  }
}
