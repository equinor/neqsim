package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

/** Tests the ammonia viscosity correlation. */
public class PhaseAmmoniaViscosityTest {

  @Test
  public void testGasViscosityAt5bar20C() {
    PhaseAmmoniaEos phase = new PhaseAmmoniaEos();
    phase.setTemperature(293.15);
    phase.setPressure(5.0);
    phase.setType(PhaseType.GAS);
    double visc = phase.getViscosity();
    assertEquals(9.789206e-6, visc, 1e-6);
  }

  @Test
  public void testLiquidViscosityAt10bar20C() {
    PhaseAmmoniaEos phase = new PhaseAmmoniaEos();
    phase.setTemperature(293.15);
    phase.setPressure(10.0);
    phase.setType(PhaseType.LIQUID);
    double visc = phase.getViscosity();
    assertEquals(6.5474e-5, visc, 2e-7);
  }
}
