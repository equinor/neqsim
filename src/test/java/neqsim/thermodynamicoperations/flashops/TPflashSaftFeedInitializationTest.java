package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemSAFTVRMie;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Fresh and preinitialized feeds must solve the same conserved equilibrium. */
class TPflashSaftFeedInitializationTest {
  private SystemSAFTVRMie feed(double temperature) {
    SystemSAFTVRMie system = new SystemSAFTVRMie(temperature, 30.0);
    system.addComponent("methane", 0.6);
    system.addComponent("n-butane", 0.4);
    system.setMixingRule("classic");
    return system;
  }

  @Test
  void freshFeedMatchesInitializedFeedAtTwoTemperatures() {
    for (double temperature : new double[] {200.0, 250.0}) {
      SystemSAFTVRMie reference = feed(temperature);
      reference.init(0);
      new TPflashSAFT(reference).run();
      assertEquals(2, reference.getNumberOfPhases());
      for (boolean direct : new boolean[] {false, true}) {
        SystemSAFTVRMie system = feed(temperature);
        if (direct) {
          new TPflashSAFT(system).run();
        } else {
          new ThermodynamicOperations(system).TPflash();
        }
        assertEquals(2, system.getNumberOfPhases());
        assertEquals(reference.getBeta(), system.getBeta(), 1e-8);
        for (int i = 0; i < 2; i++) {
          double total = 0;
          for (int phase = 0; phase < 2; phase++) {
            total += system.getPhase(phase).getBeta() * system.getPhase(phase).getComponent(i).getx();
          }
          assertEquals(i == 0 ? 0.6 : 0.4, total, 1e-6);
        }
      }
    }
  }
}
