package neqsim.thermo.system;

import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Example demonstrating SAFT-VR Mie equation of state for multicomponent VLE.
 *
 * <p>
 * The SAFT-VR Mie model (Lafitte et al., 2013, J. Chem. Phys. 139, 154504) uses variable-range Mie
 * potentials for improved accuracy. Available components with dedicated parameters: methane,
 * ethane, propane, n-butane, n-pentane, n-hexane, n-heptane, n-octane, n-nonane, CO2, nitrogen,
 * water.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class SAFTVRMieExample {

  /**
   * Main method demonstrating SAFT-VR Mie VLE calculations.
   *
   * @param args command line arguments (not used)
   */
  public static void main(String[] args) {
    System.out.println("=== SAFT-VR Mie Equation of State Examples ===");
    System.out.println();

    // Example 1: Pure methane gas properties
    pureComponentExample();

    // Example 2: Binary CH4/C2H6 VLE
    binaryVLEExample();

    // Example 3: 5-component natural gas
    naturalGasExample();

    // Example 4: CO2/methane system
    co2MethaneExample();
  }

  /**
   * Pure component properties: methane at various pressures.
   */
  private static void pureComponentExample() {
    System.out.println("--- Example 1: Pure Methane at 300 K ---");
    double T = 300.0;
    double[] pressures = {10, 50, 100, 200};

    System.out.printf("%8s %12s %12s%n", "P (bar)", "rho (kg/m3)", "Z");
    for (double P : pressures) {
      SystemInterface fluid = new SystemSAFTVRMie(T, P);
      fluid.addComponent("methane", 1.0);
      fluid.setMixingRule("classic");
      fluid.init(0);
      fluid.init(1);

      double rho = fluid.getPhase(0).getDensity("kg/m3");
      double z = fluid.getPhase(0).getZ();
      System.out.printf("%8.0f %12.2f %12.4f%n", P, rho, z);
    }
    System.out.println();
  }

  /**
   * Binary VLE: methane/ethane at 250 K.
   */
  private static void binaryVLEExample() {
    System.out.println("--- Example 2: CH4/C2H6 VLE at 250 K, 20 bar ---");
    double T = 250.0;
    double P = 20.0;

    SystemInterface fluid = new SystemSAFTVRMie(T, P);
    fluid.addComponent("methane", 0.40);
    fluid.addComponent("ethane", 0.60);
    fluid.setMixingRule("classic");
    fluid.init(0);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initProperties();

    if (fluid.getNumberOfPhases() >= 2) {
      int liqIdx = 0;
      int gasIdx = 1;
      if (fluid.getPhase(0).getDensity("kg/m3") < fluid.getPhase(1).getDensity("kg/m3")) {
        liqIdx = 1;
        gasIdx = 0;
      }

      System.out.printf("  Vapor fraction: %.4f%n", fluid.getBeta());
      System.out.printf("  Liquid: x_CH4=%.4f x_C2H6=%.4f  rho=%.1f kg/m3%n",
          fluid.getPhase(liqIdx).getComponent("methane").getx(),
          fluid.getPhase(liqIdx).getComponent("ethane").getx(),
          fluid.getPhase(liqIdx).getDensity("kg/m3"));
      System.out.printf("  Gas:    y_CH4=%.4f y_C2H6=%.4f  rho=%.1f kg/m3%n",
          fluid.getPhase(gasIdx).getComponent("methane").getx(),
          fluid.getPhase(gasIdx).getComponent("ethane").getx(),
          fluid.getPhase(gasIdx).getDensity("kg/m3"));
    } else {
      System.out.println("  Single phase result");
    }
    System.out.println();
  }

  /**
   * Five-component natural gas flash.
   */
  private static void naturalGasExample() {
    System.out.println("--- Example 3: 5-Component Natural Gas at 220 K ---");
    double T = 220.0;
    double[] pressures = {10, 20, 30};

    for (double P : pressures) {
      SystemInterface fluid = new SystemSAFTVRMie(T, P);
      fluid.addComponent("nitrogen", 0.02);
      fluid.addComponent("methane", 0.70);
      fluid.addComponent("ethane", 0.12);
      fluid.addComponent("propane", 0.10);
      fluid.addComponent("n-butane", 0.06);
      fluid.setMixingRule("classic");
      fluid.init(0);

      ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
      ops.TPflash();
      fluid.initProperties();

      System.out.printf("  P=%4.0f bar: %d phases", P, fluid.getNumberOfPhases());
      if (fluid.getNumberOfPhases() >= 2) {
        System.out.printf("  beta=%.3f%n", fluid.getBeta());
      } else {
        System.out.println();
      }
    }
    System.out.println();
  }

  /**
   * CO2/methane binary with non-standard Mie exponent.
   */
  private static void co2MethaneExample() {
    System.out.println("--- Example 4: CO2/CH4 VLE at 230 K, 30 bar ---");
    double T = 230.0;
    double P = 30.0;

    SystemInterface fluid = new SystemSAFTVRMie(T, P);
    fluid.addComponent("CO2", 0.30);
    fluid.addComponent("methane", 0.70);
    fluid.setMixingRule("classic");
    fluid.init(0);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initProperties();

    if (fluid.getNumberOfPhases() >= 2) {
      int liqIdx = 0;
      int gasIdx = 1;
      if (fluid.getPhase(0).getDensity("kg/m3") < fluid.getPhase(1).getDensity("kg/m3")) {
        liqIdx = 1;
        gasIdx = 0;
      }

      System.out.printf("  Vapor fraction: %.4f%n", fluid.getBeta());
      System.out.printf("  Liquid: x_CO2=%.4f x_CH4=%.4f%n",
          fluid.getPhase(liqIdx).getComponent("CO2").getx(),
          fluid.getPhase(liqIdx).getComponent("methane").getx());
      System.out.printf("  Gas:    y_CO2=%.4f y_CH4=%.4f%n",
          fluid.getPhase(gasIdx).getComponent("CO2").getx(),
          fluid.getPhase(gasIdx).getComponent("methane").getx());
      System.out.println("  Note: CO2 uses lambda_a=5.055, lambda_r=27.557 (quadrupolar molecule)");
    }
    System.out.println();
    System.out.println("=== All examples complete ===");
  }
}
