package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.util.gerg.NeqSimEOSCG;

public class SystemEOSCGEosSO2Test {
  @Test
  public void testSO2Density() {
    SystemInterface fluid = new SystemEOSCGEos(298.15, 50.0); // 50 bar
    fluid.addComponent("SO2", 1.0);
    fluid.init(0);
    fluid.init(1);

    double density = fluid.getDensity("kg/m3");
    NeqSimEOSCG eosCg = new NeqSimEOSCG(fluid.getPhase(0));

    // At 298.15 K and 50 bar EOS-CG selects the dense SO2 root. A newly constructed
    // single-phase system retains its default GAS label until a phase-stability flash, so density
    // validation must not infer the root from that label.
    assertTrue(density > 1300.0 && density < 1500.0, "EOS-CG should return the dense SO2 root at 298.15 K and 50 bar");
    assertEquals(5000.0, eosCg.getPressure(), 1.0e-4,
        "The selected EOS-CG density must reproduce the specified pressure in kPa");
  }
}
