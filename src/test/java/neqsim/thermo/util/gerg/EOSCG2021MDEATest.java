package neqsim.thermo.util.gerg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.netlib.util.doubleW;

/** Tests EOS-CG-2021 methyl diethanolamine pure-fluid data. */
class EOSCG2021MDEATest {
  @Test
  void mdeaMolarMassUsesEosCg2021ComponentSlot() {
    EOSCG eos = new EOSCG();
    eos.setup();
    double[] composition = new double[29];
    composition[28] = 1.0;
    doubleW molarMass = new doubleW(0.0);

    eos.molarMass(composition, molarMass);

    assertEquals(119.1622, molarMass.val, 1.0e-8);
  }

  @Test
  void mdeaPressureMatchesPublishedImplementationValue() {
    EOSCG eos = new EOSCG();
    eos.setup();
    double[] composition = new double[29];
    composition[28] = 1.0;
    doubleW pressure = new doubleW(0.0);
    doubleW z = new doubleW(0.0);

    eos.pressure(300.0, 8.8, composition, pressure, z);

    assertEquals(34241.92443746, pressure.val, 1.0e-4);
  }
}
