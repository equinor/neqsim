package neqsim.thermodynamicoperations.flashops.saturationops;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Test to investigate n-butane performance issue in electrolyte systems.
 *
 * @author ESOL
 */
public class NButaneIssueTest {

  /**
   * Test with n-butane - the problematic case.
   */
  @Test
  @DisplayName("Test WITH n-butane")
  public void testWithNButane() throws Exception {
    System.out.println("=== Test WITH n-butane ===\n");

    SystemInterface fluid = new SystemElectrolyteCPAstatoil(273.15 + 10.0, 50.0);

    fluid.addComponent("water", 0.494505);
    fluid.addComponent("MEG", 0.164835);
    fluid.addComponent("methane", 0.247253);
    fluid.addComponent("ethane", 0.0164835);
    fluid.addComponent("propane", 0.010989);
    fluid.addComponent("i-butane", 0.00549451);
    fluid.addComponent("n-butane", 0.00549451); // n-butane included
    fluid.addComponent("Na+", 0.0274725);
    fluid.addComponent("Cl-", 0.0274725);

    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    fluid.setHydrateCheck(true);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

    long start = System.currentTimeMillis();
    ops.hydrateFormationTemperature();
    long end = System.currentTimeMillis();

    double hydrateTemp = fluid.getTemperature() - 273.15;
    System.out.println("With n-butane:");
    System.out.println("  Hydrate temp: " + hydrateTemp + " °C");
    System.out.println("  Time: " + (end - start) + " ms");
    System.out.println("  Phases: " + fluid.getNumberOfPhases());
  }

  /**
   * Test WITHOUT n-butane - to see if it's faster.
   */
  @Test
  @DisplayName("Test WITHOUT n-butane")
  public void testWithoutNButane() throws Exception {
    System.out.println("=== Test WITHOUT n-butane ===\n");

    SystemInterface fluid = new SystemElectrolyteCPAstatoil(273.15 + 10.0, 50.0);

    fluid.addComponent("water", 0.494505);
    fluid.addComponent("MEG", 0.164835);
    fluid.addComponent("methane", 0.247253);
    fluid.addComponent("ethane", 0.0164835);
    fluid.addComponent("propane", 0.010989);
    fluid.addComponent("i-butane", 0.00549451);
    // No n-butane
    fluid.addComponent("Na+", 0.0274725);
    fluid.addComponent("Cl-", 0.0274725);

    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    fluid.setHydrateCheck(true);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

    long start = System.currentTimeMillis();
    ops.hydrateFormationTemperature();
    long end = System.currentTimeMillis();

    double hydrateTemp = fluid.getTemperature() - 273.15;
    System.out.println("Without n-butane:");
    System.out.println("  Hydrate temp: " + hydrateTemp + " °C");
    System.out.println("  Time: " + (end - start) + " ms");
    System.out.println("  Phases: " + fluid.getNumberOfPhases());
  }

  /**
   * Test n-butane in TPflash to see phase behavior.
   */
  @Test
  @DisplayName("Test TPflash with n-butane at different temperatures")
  public void testTPflashWithNButane() {
    System.out.println("=== TPflash with n-butane at different temperatures ===\n");

    double[] temps = {273.15 + 10, 273.15 - 5, 273.15 - 10, 273.15 - 15};

    for (double temp : temps) {
      SystemInterface fluid = new SystemElectrolyteCPAstatoil(temp, 50.0);

      fluid.addComponent("water", 0.494505);
      fluid.addComponent("MEG", 0.164835);
      fluid.addComponent("methane", 0.247253);
      fluid.addComponent("ethane", 0.0164835);
      fluid.addComponent("propane", 0.010989);
      fluid.addComponent("i-butane", 0.00549451);
      fluid.addComponent("n-butane", 0.00549451);
      fluid.addComponent("Na+", 0.0274725);
      fluid.addComponent("Cl-", 0.0274725);

      fluid.setMixingRule(10);
      fluid.setMultiPhaseCheck(true);

      ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

      long start = System.currentTimeMillis();
      ops.TPflash();
      long end = System.currentTimeMillis();

      System.out.println("T = " + (temp - 273.15) + " °C:");
      System.out.println("  Time: " + (end - start) + " ms");
      System.out.println("  Phases: " + fluid.getNumberOfPhases());
      for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
        System.out.println("    Phase " + i + ": " + fluid.getPhase(i).getPhaseTypeName() + " beta="
            + String.format("%.4f", fluid.getBeta(i)));
      }
      System.out.println();
    }
  }

  /**
   * Check if n-butane causes 3-phase behavior which triggers more stability analysis.
   */
  @Test
  @DisplayName("Compare phase count with and without n-butane")
  public void testPhaseCountComparison() {
    System.out.println("=== Phase count comparison ===\n");

    // WITH n-butane
    SystemInterface fluid1 = new SystemElectrolyteCPAstatoil(273.15 - 10.0, 50.0);
    fluid1.addComponent("water", 0.494505);
    fluid1.addComponent("MEG", 0.164835);
    fluid1.addComponent("methane", 0.247253);
    fluid1.addComponent("ethane", 0.0164835);
    fluid1.addComponent("propane", 0.010989);
    fluid1.addComponent("i-butane", 0.00549451);
    fluid1.addComponent("n-butane", 0.00549451);
    fluid1.addComponent("Na+", 0.0274725);
    fluid1.addComponent("Cl-", 0.0274725);
    fluid1.setMixingRule(10);
    fluid1.setMultiPhaseCheck(true);

    ThermodynamicOperations ops1 = new ThermodynamicOperations(fluid1);
    ops1.TPflash();

    System.out.println("WITH n-butane at -10°C:");
    System.out.println("  Phases: " + fluid1.getNumberOfPhases());
    for (int i = 0; i < fluid1.getNumberOfPhases(); i++) {
      System.out.println("    Phase " + i + ": " + fluid1.getPhase(i).getPhaseTypeName());
      if (fluid1.getPhase(i).hasComponent("n-butane")) {
        System.out
            .println("      n-butane x = " + fluid1.getPhase(i).getComponent("n-butane").getx());
      }
    }

    // WITHOUT n-butane
    SystemInterface fluid2 = new SystemElectrolyteCPAstatoil(273.15 - 10.0, 50.0);
    fluid2.addComponent("water", 0.494505);
    fluid2.addComponent("MEG", 0.164835);
    fluid2.addComponent("methane", 0.247253);
    fluid2.addComponent("ethane", 0.0164835);
    fluid2.addComponent("propane", 0.010989);
    fluid2.addComponent("i-butane", 0.00549451);
    // No n-butane
    fluid2.addComponent("Na+", 0.0274725);
    fluid2.addComponent("Cl-", 0.0274725);
    fluid2.setMixingRule(10);
    fluid2.setMultiPhaseCheck(true);

    ThermodynamicOperations ops2 = new ThermodynamicOperations(fluid2);
    ops2.TPflash();

    System.out.println("\nWITHOUT n-butane at -10°C:");
    System.out.println("  Phases: " + fluid2.getNumberOfPhases());
    for (int i = 0; i < fluid2.getNumberOfPhases(); i++) {
      System.out.println("    Phase " + i + ": " + fluid2.getPhase(i).getPhaseTypeName());
    }
  }

  /**
   * Test if n-butane has missing or problematic parameters.
   */
  @Test
  @DisplayName("Check n-butane component properties")
  public void testNButaneProperties() {
    System.out.println("=== n-butane component properties ===\n");

    SystemInterface fluid = new SystemElectrolyteCPAstatoil(273.15, 50.0);
    fluid.addComponent("n-butane", 1.0);
    fluid.setMixingRule(10);

    fluid.init(0);

    System.out.println("n-butane properties:");
    System.out.println("  TC = " + fluid.getPhase(0).getComponent("n-butane").getTC() + " K");
    System.out.println("  PC = " + fluid.getPhase(0).getComponent("n-butane").getPC() + " bar");
    System.out
        .println("  Acentric = " + fluid.getPhase(0).getComponent("n-butane").getAcentricFactor());
    System.out.println(
        "  Molar mass = " + fluid.getPhase(0).getComponent("n-butane").getMolarMass() + " kg/mol");
    System.out.println(
        "  Is hydrate former: " + fluid.getPhase(0).getComponent("n-butane").isHydrateFormer());
    System.out
        .println("  Ionic charge: " + fluid.getPhase(0).getComponent("n-butane").getIonicCharge());
  }

  /**
   * Test stability analysis iterations with n-butane.
   */
  @Test
  @DisplayName("Profile stability analysis with n-butane")
  public void testStabilityAnalysisWithNButane() {
    System.out.println("=== Stability analysis timing ===\n");

    // At low temperature where stability analysis is most expensive
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(273.15 - 10.0, 50.0);

    fluid.addComponent("water", 0.494505);
    fluid.addComponent("MEG", 0.164835);
    fluid.addComponent("methane", 0.247253);
    fluid.addComponent("ethane", 0.0164835);
    fluid.addComponent("propane", 0.010989);
    fluid.addComponent("i-butane", 0.00549451);
    fluid.addComponent("n-butane", 0.00549451);
    fluid.addComponent("Na+", 0.0274725);
    fluid.addComponent("Cl-", 0.0274725);

    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);

    System.out.println("Number of components: " + fluid.getNumberOfComponents());
    System.out.println("Components:");
    for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
      System.out.println("  " + i + ": " + fluid.getComponent(i).getName() + " (ionic charge: "
          + fluid.getComponent(i).getIonicCharge() + ")");
    }

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

    // Time just the flash
    long start = System.currentTimeMillis();
    ops.TPflash();
    long end = System.currentTimeMillis();

    System.out.println("\nTPflash time: " + (end - start) + " ms");
    System.out.println("Final phases: " + fluid.getNumberOfPhases());
  }
}
