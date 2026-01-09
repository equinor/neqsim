package neqsim.pvtsimulation.simulation;

import org.junit.jupiter.api.Test;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSoreideWhitson;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Debug tests to understand why EoS methods return 0 for Rsw.
 */
public class SolutionGasWaterRatioDebugTest {

  @Test
  void debugRswFromSolutionClass() {
    System.out.println(
        "\n================================================================================");
    System.out.println("DEBUG: SolutionGasWaterRatio class - Testing EoS methods");
    System.out.println(
        "================================================================================");

    // Create a gas system like in the verification test
    SystemInterface gas = new SystemSrkCPAstatoil(350.0, 100.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.10);
    gas.setMixingRule(10);

    System.out.println("\nInput gas system:");
    System.out.println("  Number of components: " + gas.getNumberOfComponents());
    for (int i = 0; i < gas.getNumberOfComponents(); i++) {
      System.out.println("    Component " + i + ": " + gas.getComponent(i).getComponentName()
          + " z=" + gas.getComponent(i).getz());
    }

    SolutionGasWaterRatio rswCalc = new SolutionGasWaterRatio(gas);
    rswCalc.setTemperaturesAndPressures(new double[] {350.0}, new double[] {100.0});
    rswCalc.setSalinity(0.0);

    // Test McCain first
    System.out.println("\nTesting McCain method:");
    rswCalc.setCalculationMethod(SolutionGasWaterRatio.CalculationMethod.MCCAIN);
    rswCalc.runCalc();
    System.out.println("  Rsw = " + rswCalc.getRsw(0) + " Sm³/Sm³");

    // Test Søreide-Whitson
    System.out.println("\nTesting Søreide-Whitson method:");
    rswCalc.setCalculationMethod(SolutionGasWaterRatio.CalculationMethod.SOREIDE_WHITSON);
    rswCalc.runCalc();
    System.out.println("  Rsw = " + rswCalc.getRsw(0) + " Sm³/Sm³");

    // Test Electrolyte CPA
    System.out.println("\nTesting Electrolyte CPA method:");
    rswCalc.setCalculationMethod(SolutionGasWaterRatio.CalculationMethod.ELECTROLYTE_CPA);
    rswCalc.runCalc();
    System.out.println("  Rsw = " + rswCalc.getRsw(0) + " Sm³/Sm³");
  }

  @Test
  void debugSoreideWhitsonFlash() {
    System.out.println(
        "\n================================================================================");
    System.out.println("DEBUG: Søreide-Whitson Flash Calculation");
    System.out.println(
        "================================================================================");

    double temperatureK = 350.0;
    double pressureBara = 100.0;

    // Create Søreide-Whitson system - use specific type
    // Copy the exact pattern from SoreideWhitsonSystemTest
    SystemSoreideWhitson system = new SystemSoreideWhitson(temperatureK, pressureBara);

    // Add components using the same pattern as the working test
    system.addComponent("nitrogen", 0.1, "mole/sec");
    system.addComponent("CO2", 0.2, "mole/sec");
    system.addComponent("methane", 0.3, "mole/sec");
    system.addComponent("ethane", 0.3, "mole/sec");
    system.addComponent("water", 0.1, "mole/sec");
    system.addSalinity(0, "mole/sec");
    system.setTotalFlowRate(15, "mole/sec");

    // Set mixing rule
    system.setMixingRule(11);
    system.setMultiPhaseCheck(true);
    system.setPressure(pressureBara, "bara");
    system.setTemperature(temperatureK, "K");

    System.out.println("\nBefore flash:");
    System.out.println("  Number of components: " + system.getNumberOfComponents());
    for (int i = 0; i < system.getNumberOfComponents(); i++) {
      System.out.println("    Component " + i + ": " + system.getComponent(i).getComponentName());
    }

    // Run flash
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();

    System.out.println("\nAfter flash:");
    System.out.println("  Number of phases: " + system.getNumberOfPhases());

    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      System.out.println("\n  Phase " + i + ": " + system.getPhase(i).getType());
      System.out.println("    Phase mole fraction (beta): " + system.getPhase(i).getBeta());
      for (int j = 0; j < system.getPhase(i).getNumberOfComponents(); j++) {
        String compName = system.getPhase(i).getComponent(j).getComponentName();
        double x = system.getPhase(i).getComponent(j).getx();
        double z = system.getPhase(i).getComponent(j).getz();
        System.out.printf("      %s: x = %.6f, z = %.6f%n", compName, x, z);
      }
    }

    // Calculate Rsw manually
    int aqueousPhaseIndex = -1;
    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      if (system.getPhase(i).getType() == PhaseType.AQUEOUS) {
        aqueousPhaseIndex = i;
        break;
      }
    }

    if (aqueousPhaseIndex < 0) {
      // Find phase with highest water content
      double maxWaterFrac = 0.0;
      for (int i = 0; i < system.getNumberOfPhases(); i++) {
        for (int j = 0; j < system.getPhase(i).getNumberOfComponents(); j++) {
          String compName = system.getPhase(i).getComponent(j).getComponentName();
          if (compName.equalsIgnoreCase("water")) {
            double waterMoleFrac = system.getPhase(i).getComponent(j).getx();
            System.out.printf("  Phase %d water mole fraction: %.6f%n", i, waterMoleFrac);
            if (waterMoleFrac > maxWaterFrac && waterMoleFrac > 0.5) {
              maxWaterFrac = waterMoleFrac;
              aqueousPhaseIndex = i;
            }
          }
        }
      }
    }

    System.out.println("\n  Identified aqueous phase index: " + aqueousPhaseIndex);

    if (aqueousPhaseIndex >= 0) {
      double waterMoleFrac = 0.0;
      double gasMoleFrac = 0.0;

      for (int j = 0; j < system.getPhase(aqueousPhaseIndex).getNumberOfComponents(); j++) {
        String compName = system.getPhase(aqueousPhaseIndex).getComponent(j).getComponentName();
        double x = system.getPhase(aqueousPhaseIndex).getComponent(j).getx();

        if (compName.equalsIgnoreCase("water")) {
          waterMoleFrac += x;
        } else {
          gasMoleFrac += x;
        }
      }

      System.out.printf("\n  In aqueous phase: water mol frac = %.6f, gas mol frac = %.6f%n",
          waterMoleFrac, gasMoleFrac);

      if (waterMoleFrac > 0 && gasMoleFrac > 0) {
        double gasToWaterMolarRatio = gasMoleFrac / waterMoleFrac;
        double stdTemp = ThermodynamicConstantsInterface.standardStateTemperature;
        double stdPres = ThermodynamicConstantsInterface.referencePressure;
        double molarVolumeGasStd = ThermodynamicConstantsInterface.R * stdTemp / (stdPres * 100.0);
        double molarVolumeWaterStd = 18.015 / 1000.0 / 1000.0;
        double rswValue = gasToWaterMolarRatio * molarVolumeGasStd / molarVolumeWaterStd;

        System.out.printf("  Calculated Rsw: %.6f Sm³/Sm³%n", rswValue);
      }
    }
  }

  @Test
  void debugCPAFlash() {
    System.out.println(
        "\n================================================================================");
    System.out.println("DEBUG: CPA Flash Calculation");
    System.out.println(
        "================================================================================");

    double temperatureK = 350.0;
    double pressureBara = 100.0;

    // Create CPA system
    SystemInterface system = new SystemSrkCPAstatoil(temperatureK, pressureBara);

    // Add components
    system.addComponent("methane", 1.0);
    system.addComponent("water", 100.0);

    // Create database and set mixing rule
    system.createDatabase(true);
    system.setMixingRule(10);
    system.setMultiPhaseCheck(true);

    System.out.println("\nBefore flash:");
    System.out.println("  Number of components: " + system.getNumberOfComponents());
    for (int i = 0; i < system.getNumberOfComponents(); i++) {
      System.out.println("    Component " + i + ": " + system.getComponent(i).getComponentName());
    }

    // Run flash
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();

    System.out.println("\nAfter flash:");
    System.out.println("  Number of phases: " + system.getNumberOfPhases());

    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      System.out.println("\n  Phase " + i + ": " + system.getPhase(i).getType());
      System.out.println("    Phase mole fraction (beta): " + system.getPhase(i).getBeta());
      for (int j = 0; j < system.getPhase(i).getNumberOfComponents(); j++) {
        String compName = system.getPhase(i).getComponent(j).getComponentName();
        double x = system.getPhase(i).getComponent(j).getx();
        double z = system.getPhase(i).getComponent(j).getz();
        System.out.printf("      %s: x = %.6f, z = %.6f%n", compName, x, z);
      }
    }

    // Find aqueous phase and calculate Rsw
    int aqueousPhaseIndex = -1;
    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      if (system.getPhase(i).getType() == PhaseType.AQUEOUS) {
        aqueousPhaseIndex = i;
        break;
      }
    }

    if (aqueousPhaseIndex < 0) {
      // Find phase with highest water content
      double maxWaterFrac = 0.0;
      for (int i = 0; i < system.getNumberOfPhases(); i++) {
        for (int j = 0; j < system.getPhase(i).getNumberOfComponents(); j++) {
          String compName = system.getPhase(i).getComponent(j).getComponentName();
          if (compName.equalsIgnoreCase("water")) {
            double waterMoleFrac = system.getPhase(i).getComponent(j).getx();
            System.out.printf("  Phase %d water mole fraction: %.6f%n", i, waterMoleFrac);
            if (waterMoleFrac > maxWaterFrac && waterMoleFrac > 0.5) {
              maxWaterFrac = waterMoleFrac;
              aqueousPhaseIndex = i;
            }
          }
        }
      }
    }

    System.out.println("\n  Identified aqueous phase index: " + aqueousPhaseIndex);

    if (aqueousPhaseIndex >= 0) {
      double waterMoleFrac = 0.0;
      double gasMoleFrac = 0.0;

      for (int j = 0; j < system.getPhase(aqueousPhaseIndex).getNumberOfComponents(); j++) {
        String compName = system.getPhase(aqueousPhaseIndex).getComponent(j).getComponentName();
        double x = system.getPhase(aqueousPhaseIndex).getComponent(j).getx();

        if (compName.equalsIgnoreCase("water")) {
          waterMoleFrac += x;
        } else {
          gasMoleFrac += x;
        }
      }

      System.out.printf("\n  In aqueous phase: water mol frac = %.6f, gas mol frac = %.6f%n",
          waterMoleFrac, gasMoleFrac);

      if (waterMoleFrac > 0 && gasMoleFrac > 0) {
        double gasToWaterMolarRatio = gasMoleFrac / waterMoleFrac;
        double stdTemp = ThermodynamicConstantsInterface.standardStateTemperature;
        double stdPres = ThermodynamicConstantsInterface.referencePressure;
        double molarVolumeGasStd = ThermodynamicConstantsInterface.R * stdTemp / (stdPres * 100.0);
        double molarVolumeWaterStd = 18.015 / 1000.0 / 1000.0;
        double rswValue = gasToWaterMolarRatio * molarVolumeGasStd / molarVolumeWaterStd;

        System.out.printf("  Calculated Rsw: %.6f Sm³/Sm³%n", rswValue);
      }
    }
  }

  @Test
  void debugElectrolyteCPAFlash() {
    System.out.println(
        "\n================================================================================");
    System.out.println("DEBUG: Electrolyte CPA Flash Calculation");
    System.out.println(
        "================================================================================");

    double temperatureK = 350.0;
    double pressureBara = 100.0;
    double salinityMolal = 0.5;

    // Create Electrolyte CPA system
    SystemInterface system = new SystemElectrolyteCPAstatoil(temperatureK, pressureBara);

    // Add components
    system.addComponent("methane", 1.0);
    system.addComponent("water", 100.0);

    // Add salt as ions
    double molesWater = 100.0;
    double kgWater = molesWater * 18.015 / 1000.0;
    double molesNaCl = salinityMolal * kgWater;
    system.addComponent("Na+", molesNaCl);
    system.addComponent("Cl-", molesNaCl);

    // Create database and set mixing rule
    system.createDatabase(true);
    system.setMixingRule(10);
    system.setMultiPhaseCheck(true);

    System.out.println("\nBefore flash:");
    System.out.println("  Number of components: " + system.getNumberOfComponents());
    for (int i = 0; i < system.getNumberOfComponents(); i++) {
      System.out.println("    Component " + i + ": " + system.getComponent(i).getComponentName());
    }

    // Run flash
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();

    System.out.println("\nAfter flash:");
    System.out.println("  Number of phases: " + system.getNumberOfPhases());

    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      System.out.println("\n  Phase " + i + ": " + system.getPhase(i).getType());
      System.out.println("    Phase mole fraction (beta): " + system.getPhase(i).getBeta());
      for (int j = 0; j < system.getPhase(i).getNumberOfComponents(); j++) {
        String compName = system.getPhase(i).getComponent(j).getComponentName();
        double x = system.getPhase(i).getComponent(j).getx();
        double z = system.getPhase(i).getComponent(j).getz();
        System.out.printf("      %s: x = %.6f, z = %.6f%n", compName, x, z);
      }
    }

    // Find aqueous phase and calculate Rsw
    int aqueousPhaseIndex = -1;
    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      if (system.getPhase(i).getType() == PhaseType.AQUEOUS) {
        aqueousPhaseIndex = i;
        break;
      }
    }

    if (aqueousPhaseIndex < 0) {
      // Find phase with highest water content
      double maxWaterFrac = 0.0;
      for (int i = 0; i < system.getNumberOfPhases(); i++) {
        for (int j = 0; j < system.getPhase(i).getNumberOfComponents(); j++) {
          String compName = system.getPhase(i).getComponent(j).getComponentName();
          if (compName.equalsIgnoreCase("water")) {
            double waterMoleFrac = system.getPhase(i).getComponent(j).getx();
            System.out.printf("  Phase %d water mole fraction: %.6f%n", i, waterMoleFrac);
            if (waterMoleFrac > maxWaterFrac && waterMoleFrac > 0.5) {
              maxWaterFrac = waterMoleFrac;
              aqueousPhaseIndex = i;
            }
          }
        }
      }
    }

    System.out.println("\n  Identified aqueous phase index: " + aqueousPhaseIndex);

    if (aqueousPhaseIndex >= 0) {
      double waterMoleFrac = 0.0;
      double gasMoleFrac = 0.0;

      for (int j = 0; j < system.getPhase(aqueousPhaseIndex).getNumberOfComponents(); j++) {
        String compName = system.getPhase(aqueousPhaseIndex).getComponent(j).getComponentName();
        double x = system.getPhase(aqueousPhaseIndex).getComponent(j).getx();

        if (compName.equalsIgnoreCase("water")) {
          waterMoleFrac += x;
        } else if (!compName.equalsIgnoreCase("Na+") && !compName.equalsIgnoreCase("Cl-")) {
          gasMoleFrac += x;
        }
      }

      System.out.printf("\n  In aqueous phase: water mol frac = %.6f, gas mol frac = %.6f%n",
          waterMoleFrac, gasMoleFrac);

      if (waterMoleFrac > 0 && gasMoleFrac > 0) {
        double gasToWaterMolarRatio = gasMoleFrac / waterMoleFrac;
        double stdTemp = ThermodynamicConstantsInterface.standardStateTemperature;
        double stdPres = ThermodynamicConstantsInterface.referencePressure;
        double molarVolumeGasStd = ThermodynamicConstantsInterface.R * stdTemp / (stdPres * 100.0);
        double molarVolumeWaterStd = 18.015 / 1000.0 / 1000.0;
        double rswValue = gasToWaterMolarRatio * molarVolumeGasStd / molarVolumeWaterStd;

        System.out.printf("  Calculated Rsw: %.6f Sm³/Sm³%n", rswValue);
      }
    }
  }
}
