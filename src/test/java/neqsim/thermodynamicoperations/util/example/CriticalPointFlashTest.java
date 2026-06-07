package neqsim.thermodynamicoperations.util.example;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for critical-point-related flash utilities.
 *
 * @author esol
 * @version $Id: $Id
 * @since 2.2.3
 */
@Tag("LinearAlgebra")
public class CriticalPointFlashTest {
  /**
   * Verifies that cricondenbar calculation executes for a simple SRK hydrocarbon mixture.
   */
  @Test
  public void testCalcCricondenBarRunsWithoutException() {
    SystemInterface testSystem = new SystemSrkEos(300.0, 80.01325);
    testSystem.addComponent("methane", 0.1);
    testSystem.addComponent("propane", 0.1);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    testSystem.init(0);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    assertDoesNotThrow(new Executable() {
      @Override
      public void execute() {
	testOps.calcCricondenBar();
      }
    });

    assertTrue(testSystem.getPressure("bara") > 0.0,
	"System pressure should remain physically valid after cricondenbar calculation");
  }

  @Test
  public void testCriticalPointFlashRunsWithoutException() {
    SystemInterface testSystem = new SystemSrkEos(300.0, 80.01325);
    testSystem.addComponent("methane", 0.1);
    testSystem.addComponent("propane", 0.1);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    testSystem.init(0);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    assertDoesNotThrow(new Executable() {
      @Override
      public void execute() {
	testOps.criticalPointFlash();
      }
    });

    assertTrue(testSystem.getPressure("bara") > 0.0,
	"System pressure should remain physically valid after critical point flash calculation");
  }
}
