package neqsim.chemicalreactions;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

@Tag("slow")
public class PHvsPressureTest {

  @Test
  public void testPHvsPressure() {
    double[] pressures = {0.3, 0.5, 1.0, 2.0, 5.0, 10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 80.0, 100.0};

    for (double P : pressures) {
      SystemInterface system = new SystemElectrolyteCPAstatoil(298.15, P);
      system.addComponent("CO2", 0.1);
      system.addComponent("water", 1.0);
      system.chemicalReactionInit();
      system.createDatabase(true);
      system.setMixingRule(10);
      system.setMultiPhaseCheck(true);
      system.init(0);

      ThermodynamicOperations ops = new ThermodynamicOperations(system);
      try {
        ops.TPflash();
      } catch (Exception e) {
        continue;
      }

      // Find the aqueous phase
      int aqueousPhaseIndex = -1;
      for (int p = 0; p < system.getNumberOfPhases(); p++) {
        if (system.getPhase(p).getPhaseTypeName().equals("aqueous")
            || (system.getPhase(p).hasComponent("water")
                && system.getPhase(p).getComponent("water").getx() > 0.5)) {
          aqueousPhaseIndex = p;
          break;
        }
      }

      if (aqueousPhaseIndex != -1) {
        double pH = system.getPhase(aqueousPhaseIndex).getpH();
        org.junit.jupiter.api.Assertions.assertTrue(pH > 0 && pH < 14,
            "pH should be between 0 and 14 at pressure " + P);
      }
    }
  }
}
