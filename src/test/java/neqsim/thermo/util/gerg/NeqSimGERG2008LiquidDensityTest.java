package neqsim.thermo.util.gerg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.netlib.util.StringW;
import org.netlib.util.doubleW;
import org.netlib.util.intW;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Verify that GERG2008 density evaluation works for liquid phases.
 */
public class NeqSimGERG2008LiquidDensityTest {
  @Test
  public void testPureButaneLiquidDensity() throws Exception {
    double temperature = 298.15; // K
    double pressure = 10.0; // bar
    SystemInterface system = new SystemSrkEos(temperature, pressure);
    system.addComponent("n-butane", 1.0);
    new ThermodynamicOperations(system).TPflash();

    NeqSimGERG2008 gerg = new NeqSimGERG2008(system.getPhase(0));
    double density = gerg.getDensity();

    // Reference density from direct GERG2008 call
    GERG2008 lib = new GERG2008();
    lib.SetupGERG();
    doubleW D = new doubleW(0.0);
    intW ierr = new intW(0);
    StringW herr = new StringW("");
    lib.DensityGERG(2, temperature, pressure * 100.0, gerg.normalizedGERGComposition, D, ierr, herr);
    double expected = D.val * system.getMolarMass() * 1000.0;

    assertEquals(expected, density, expected * 1e-6);
  }
}
