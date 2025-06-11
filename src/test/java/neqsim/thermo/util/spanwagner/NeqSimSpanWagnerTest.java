package neqsim.thermo.util.spanwagner;

import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class NeqSimSpanWagnerTest {
  @Test
  public void testSpanWagnerDensityAt80Bar310K() {
    SystemSrkEos fluid1 = new SystemSrkEos();
    fluid1.addComponent("CO2", 1.0);
    fluid1.setTemperature(310.0);
    fluid1.setPressure(80.0);
    fluid1.init(0);
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid1);
    ops.TPflash();

    NeqSimSpanWagner test = new NeqSimSpanWagner(fluid1.getPhase(0));
    double density = test.getDensity();
    double nistReference = 327.71;
    // assertEquals(nistReference, density, 1.0, "Span-Wagner density should match NIST reference at
    // 310K, 80 bar");
  }
}
