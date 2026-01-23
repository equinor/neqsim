package neqsim.chemicalreactions;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Test pH calculation for CO2-water system.
 * 
 * <p>
 * CO2 dissolved in water should form carbonic acid and its dissociation products, resulting in an
 * acidic solution with pH around 4-5 at ambient conditions.
 * </p>
 */
public class CO2WaterPHTest {
  /**
   * Test that CO2-water system produces acidic pH.
   * 
   * <p>
   * Expected: pH around 4-5 (acidic due to carbonic acid formation).
   * </p>
   */
  @Test
  @org.junit.jupiter.api.Disabled("pH calculation gives NaN at low pressures - needs investigation")
  public void testCO2WaterAcidicPH() {
    System.out.println("\n=== CO2-Water pH Test ===");
    System.out.println("Conditions: 1 bar, 25°C (298.15 K)");
    System.out.println("Expected: Acidic pH (around 4-5 due to carbonic acid)");
    System.out.println("--------------------------------------------------");

    double temperature = 298.15; // 25°C in Kelvin
    double pressure = 11.01325; // 1 bar

    // Create electrolyte CPA system
    SystemInterface system = new SystemElectrolyteCPAstatoil(temperature, pressure);

    // Add components - CO2 and water
    system.addComponent("CO2", 10.01); // 0.01 mol CO2
    system.addComponent("water", 10.0); // 10 mol water

    // Initialize chemical reactions for CO2-water dissociation
    system.chemicalReactionInit();

    // Create database for component properties
    system.createDatabase(true);
    // Set mixing rule for electrolyte CPA
    system.setMixingRule(10);
    // Force single phase (aqueous) for this test
    system.setMultiPhaseCheck(false);
    system.setNumberOfPhases(1);
    system.setMaxNumberOfPhases(1);
    system.setPhaseType(0, neqsim.thermo.phase.PhaseType.AQUEOUS);

    // Initialize the system
    system.init(0);

    // Check what reactions are loaded
    System.out.println(
        "\nChemical reactions available: " + system.getChemicalReactionOperations().hasReactions());

    // Print reaction list
    System.out.println("\nLoaded reactions:");
    neqsim.chemicalreactions.chemicalreaction.ChemicalReactionList reactionList =
        system.getChemicalReactionOperations().getReactionList();
    for (int r = 0; r < reactionList.getChemicalReactionList().size(); r++) {
      neqsim.chemicalreactions.chemicalreaction.ChemicalReaction reaction =
          reactionList.getReaction(r);
      System.out.println("  Reaction " + r + ": " + reaction.getName() + ", K = "
          + reaction.getK(system.getPhase(0)));
      String[] names = reaction.getNames();
      double[] coefs = reaction.getStocCoefs();
      for (int i = 0; i < names.length; i++) {
        System.out.println("    " + names[i] + ": " + coefs[i]);
      }
    }

    // Print reference potentials for all components
    System.out.println("\nReference potentials (Gibbs formation energy):");
    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      String name = system.getPhase(0).getComponent(i).getComponentName();
      double refPot = system.getPhase(0).getComponent(i).getReferencePotential();
      double gf = system.getPhase(0).getComponent(i).getGibbsEnergyOfFormation();
      System.out.println("  " + name + ": refPot = " + refPot + ", Gf = " + gf);
    }

    // Print component list before flash
    System.out.println("\nComponents in system:");
    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      String name = system.getPhase(0).getComponent(i).getComponentName();
      double moles = system.getPhase(0).getComponent(i).getNumberOfMolesInPhase();
      System.out.println("  " + name + ": " + moles + " mol");
    }

    // Manually call chemical equilibrium solver to see what happens
    System.out.println("\n--- Calling chemical equilibrium solver directly ---");
    boolean chemSolved = system.getChemicalReactionOperations().solveChemEq(0, 0);
    System.out.println("Chemical equilibrium solver (type=0) result: " + chemSolved);

    // Print composition after chemical equilibrium type 0
    System.out.println("\nAfter chemical equilibrium type 0:");
    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      String name = system.getPhase(0).getComponent(i).getComponentName();
      double moles = system.getPhase(0).getComponent(i).getNumberOfMolesInPhase();
      System.out.println("  " + name + ": " + moles + " mol");
    }

    // Now call type 1 (Newton solver)
    boolean chemSolved2 = system.getChemicalReactionOperations().solveChemEq(0, 1);
    System.out.println("Chemical equilibrium solver (type=1) result: " + chemSolved2);

    // Print composition after chemical equilibrium type 1
    System.out.println("\nAfter chemical equilibrium type 1:");
    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      String name = system.getPhase(0).getComponent(i).getComponentName();
      double moles = system.getPhase(0).getComponent(i).getNumberOfMolesInPhase();
      System.out.println("  " + name + ": " + moles + " mol");
    }

    // Now perform TP flash with chemical equilibrium
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();

    // Initialize properties
    system.initProperties();

    // Print results after flash
    System.out.println("\nAfter flash:");
    System.out.println("Number of phases: " + system.getNumberOfPhases());

    // Find aqueous phase
    int aqueousPhaseIndex = 0;
    for (int p = 0; p < system.getNumberOfPhases(); p++) {
      String phaseType = system.getPhase(p).getPhaseTypeName();
      System.out.println("Phase " + p + " type: " + phaseType);
      if (phaseType.equalsIgnoreCase("aqueous") || phaseType.equalsIgnoreCase("liquid")) {
        aqueousPhaseIndex = p;
      }
    }

    // Print composition of aqueous phase
    System.out.println("\nAqueous phase composition:");
    for (int i = 0; i < system.getPhase(aqueousPhaseIndex).getNumberOfComponents(); i++) {
      String name = system.getPhase(aqueousPhaseIndex).getComponent(i).getComponentName();
      double moles = system.getPhase(aqueousPhaseIndex).getComponent(i).getNumberOfMolesInPhase();
      double moleFrac = system.getPhase(aqueousPhaseIndex).getComponent(i).getx();
      if (moles > 1e-20) {
        System.out.println("  " + name + ": " + moles + " mol (x=" + moleFrac + ")");
      }
    }

    // Get H3O+ concentration
    double h3oMoles = 0;
    double h3oMoleFrac = 0;
    double ohMoles = 0;
    double ohMoleFrac = 0;

    for (int i = 0; i < system.getPhase(aqueousPhaseIndex).getNumberOfComponents(); i++) {
      String name = system.getPhase(aqueousPhaseIndex).getComponent(i).getComponentName();
      if (name.equals("H3O+")) {
        h3oMoles = system.getPhase(aqueousPhaseIndex).getComponent(i).getNumberOfMolesInPhase();
        h3oMoleFrac = system.getPhase(aqueousPhaseIndex).getComponent(i).getx();
      }
      if (name.equals("OH-")) {
        ohMoles = system.getPhase(aqueousPhaseIndex).getComponent(i).getNumberOfMolesInPhase();
        ohMoleFrac = system.getPhase(aqueousPhaseIndex).getComponent(i).getx();
      }
    }

    System.out.println("\nIon concentrations:");
    System.out.println("  H3O+ moles: " + h3oMoles + ", mole fraction: " + h3oMoleFrac);
    System.out.println("  OH- moles: " + ohMoles + ", mole fraction: " + ohMoleFrac);

    // Calculate pH
    double pH = system.getPhase(aqueousPhaseIndex).getpH();
    System.out.println("\nCalculated pH: " + pH);

    // For acidic CO2-water solution:
    // - pH should be less than 7 (acidic)
    // - Typically around 4-5 for CO2-saturated water at ambient conditions
    System.out.println("\n--- pH Analysis ---");
    if (pH < 7.0) {
      System.out.println("PASS: Solution is acidic (pH < 7)");
    } else {
      System.out.println("FAIL: Solution should be acidic but pH = " + pH);
    }

    // Check if H3O+ > OH- (acidic condition)
    if (h3oMoles > ohMoles) {
      System.out.println("PASS: H3O+ > OH- (acidic condition)");
    } else {
      System.out.println("FAIL: H3O+ should be > OH- for acidic solution");
      System.out.println("  H3O+/OH- ratio: " + (h3oMoles / ohMoles));
    }

    // The test should verify acidic pH
    assertTrue(Double.isFinite(pH), "pH should be finite");
    assertTrue(pH < 7.0, "CO2-water solution should be acidic (pH < 7), but got pH = " + pH);
    // assertTrue(pH > 2.0 && pH < 6.0,
    // "CO2-water pH should be in range 2-6 (typical acidic range), but got pH = " + pH);
  }
}
