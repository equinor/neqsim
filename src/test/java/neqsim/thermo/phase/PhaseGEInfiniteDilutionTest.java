package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemDuanSun;
import neqsim.thermo.system.SystemGEWilson;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemNRTL;

/** Regression coverage for the actual component topology used by infinite dilution. */
class PhaseGEInfiniteDilutionTest extends neqsim.NeqSimTest {
  @Test
  void unaryGeSystemsDoNotReadAnAbsentSecondComponent() {
    for (SystemInterface system : new SystemInterface[] {new SystemNRTL(313.15, 1.0), new SystemGEWilson(313.15, 1.0),
        new SystemDuanSun(313.15, 1.0)}) {
      if (system.getNumberOfComponents() == 0) {
        system.addComponent("CO2", 1.0);
      }
      system.setMixingRule("classic");
      system.init(0);
      system.init(1);
      assertEquals(1.0, ((PhaseGE) system.getPhase(1)).getActivityCoefficientInfDil(0), 1.0e-10,
          system.getClass().getSimpleName());
    }
  }

  @Test
  void ternaryNrtlIncludesThirdSolventAndReturnsRequestedSolute() {
    SystemNRTL system = new SystemNRTL(313.15, 1.0);
    system.addComponent("methanol", 1.0);
    system.addComponent("ethanol", 1.0);
    system.addComponent("acetone", 1.0);
    system.setMixingRule("classic");
    system.init(0);
    PhaseGENRTL phase = (PhaseGENRTL) system.getPhase(1);
    phase.setAlpha(new double[3][3]);
    double[][] interaction = new double[3][3];
    interaction[1][2] = phase.getTemperature();
    phase.setDij(interaction);
    // alpha=0 and only tau(ethanol,acetone)=1: at dilute ethanol,
    // equal methanol/acetone solvents give ln(gamma_ethanol)=x_acetone=1/2.
    assertEquals(Math.exp(0.5), phase.getActivityCoefficientInfDil(1), 1.0e-9);
    for (int i = 0; i < 3; i++) {
      assertEquals(1.0 / 3.0, phase.getComponent(i).getx(), 1.0e-12, "source composition must remain unchanged");
    }
  }
}
