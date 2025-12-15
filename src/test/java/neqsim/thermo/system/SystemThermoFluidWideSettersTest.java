package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.mixingrule.EosMixingRulesInterface;

public class SystemThermoFluidWideSettersTest extends neqsim.NeqSimTest {
  private SystemInterface system;

  @BeforeEach
  void setUp() {
    system = new SystemSrkEos(303.15, 50.0);
    system.addComponent("methane", 1.0);
    system.addComponent("ethane", 1.0);
    system.createDatabase(true);
    system.setMixingRule(2);
    system.init(0);
  }

  @Test
  void setCriticalParametersAcrossPhases() {
    double newTc = 210.0;
    double newPc = 60.0;
    double newAcentricFactor = 0.12;

    system.setComponentCriticalParameters("methane", newTc, newPc, newAcentricFactor);

    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      ComponentInterface methane = system.getPhase(phase).getComponent("methane");
      assertEquals(newTc, methane.getTC(), 1e-10);
      assertEquals(newPc, methane.getPC(), 1e-10);
      assertEquals(newAcentricFactor, methane.getAcentricFactor(), 1e-10);
    }
  }

  @Test
  void setVolumeCorrectionAcrossPhases() {
    double volumeCorrection = -0.012;

    system.setComponentVolumeCorrection("ethane", volumeCorrection);

    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      ComponentInterface ethane = system.getPhase(phase).getComponent("ethane");
      assertEquals(volumeCorrection, ethane.getVolumeCorrection(), 1e-10);
    }
  }

  @Test
  void setBinaryInteractionAcrossPhases() {
    double kij = 0.145;

    system.setBinaryInteractionParameter("methane", "ethane", kij);

    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      EosMixingRulesInterface mixingRule = (EosMixingRulesInterface) system.getPhase(phase)
          .getMixingRule();
      assertEquals(kij, mixingRule.getBinaryInteractionParameter(0, 1), 1e-10);
    }
  }
}
