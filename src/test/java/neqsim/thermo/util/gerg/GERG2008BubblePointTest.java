package neqsim.thermo.util.gerg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.netlib.util.StringW;
import org.netlib.util.doubleW;
import org.netlib.util.intW;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Check bubble point calculation and verify GERG2008 liquid density at the bubble point.
 */
public class GERG2008BubblePointTest {
  @Test
  public void testBubblePointPressureAndDensity() throws Exception {
    SystemSrkEos fluid = new SystemSrkEos();
    fluid.addComponent("methane", 0.1);
    fluid.addComponent("ethane", 0.1);
    fluid.addComponent("propane", 0.1);
    fluid.addComponent("n-butane", 0.5);
    fluid.setMixingRule("classic");
    fluid.setPressure(10.0, "bara");
    fluid.setTemperature(-50.0, "C");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.bubblePointPressureFlash(false);

    assertEquals(12.23134721162, fluid.getPressure(), 1e-6);

    NeqSimGERG2008 gerg = new NeqSimGERG2008(fluid.getPhase(1));
    double density = gerg.getDensity();

    GERG2008 lib = new GERG2008();
    lib.SetupGERG();
    doubleW D = new doubleW(0.0);
    intW ierr = new intW(0);
    StringW herr = new StringW("");
    lib.DensityGERG(2, fluid.getTemperature(), fluid.getPressure() * 100.0,
        gerg.normalizedGERGComposition, D, ierr, herr);
    double expected = D.val * fluid.getMolarMass() * 1000.0;

    assertEquals(expected, density, expected * 1e-6);
  }
}
