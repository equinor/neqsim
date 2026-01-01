package neqsim.chemicalreactions;

import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class PHvsPressureTest {

  @Test
  public void testPHvsPressure() {
    try (java.io.PrintWriter writer = new java.io.PrintWriter("ph_data.csv")) {
      writer.println("Pressure_bar,pH");
      System.out.println("\n=== pH vs Pressure Data Generation ===");

      // Logarithmic spacing for pressure might be nice, but let's stick to the list
      double[] pressures =
          {0.3, 0.5, 1.0, 2.0, 5.0, 10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 80.0, 100.0};

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
          System.out.println("Flash failed at " + P + " bar: " + e.getMessage());
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
          writer.println(String.format(java.util.Locale.US, "%.4f,%.4f", P, pH));
          System.out.println(String.format("P=%.4f, pH=%.4f", P, pH));
        } else {
          writer.println(P + ",NaN");
        }
      }
    } catch (java.io.FileNotFoundException e) {
      e.printStackTrace();
    }
  }
}
