package neqsim.thermo.util.spanwagner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemGERG2008Eos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSpanWagnerEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression tests comparing Span–Wagner and GERG‑2008 properties for CO₂.
 */
public class SpanWagnerGERGComparisonTest {
  private double[] getSpanWagnerProps(double temperature, double pressure) {
    SystemInterface sys = new SystemSpanWagnerEos(temperature, pressure);
    sys.init(0);
    sys.init(1);
    sys.init(2);
    sys.init(3);
    PhaseInterface phase = sys.getPhase(0);
    double moles = phase.getNumberOfMolesInPhase();
    return new double[] {phase.getCp() / moles, phase.getCv() / moles, phase.getSoundSpeed(),
        phase.getJouleThomsonCoefficient(), phase.getDensity()};
  }

  private double[] getGERGProps(double temperature, double pressure) {
    SystemInterface sys = new SystemGERG2008Eos(temperature, pressure);
    sys.addComponent("CO2", 1.0);
    sys.createDatabase(true);
    sys.setMixingRule(2);
    new ThermodynamicOperations(sys).TPflash();
    sys.initProperties();
    PhaseInterface phase = sys.getPhase(0);
    double moles = phase.getNumberOfMolesInPhase();
    return new double[] {phase.getCp() / moles, phase.getCv() / moles, phase.getSoundSpeed(),
        phase.getJouleThomsonCoefficient(), phase.getDensity()};
  }

  private void compareAtPressure(double pressure) {
    double temperature = 298.15;
    double[] span = getSpanWagnerProps(temperature, pressure);
    double[] gerg = getGERGProps(temperature, pressure);
    for (int i = 0; i < span.length; i++) {
      assertEquals(gerg[i], span[i], Math.abs(gerg[i]) * 1e-2 + 1e-8);
    }
  }

  @Test
  public void testLowPressure() {
    compareAtPressure(10.0);
  }

  @Test
  public void testHighPressure() {
    compareAtPressure(100.0);
  }
}
