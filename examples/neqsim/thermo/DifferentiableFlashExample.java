package neqsim.thermo;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.util.derivatives.DifferentiableFlash;
import neqsim.thermo.util.derivatives.FlashGradients;
import neqsim.thermo.util.derivatives.FugacityJacobian;
import neqsim.thermo.util.derivatives.PropertyGradient;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Example demonstrating differentiable thermodynamics for gradient-based optimization.
 * 
 * <p>
 * This example shows how to compute analytical gradients of flash calculation results using the
 * implicit function theorem. These gradients are exact (not finite differences) and can be used
 * for:
 * </p>
 * <ul>
 * <li>Gradient-based optimization of process conditions</li>
 * <li>Integration with machine learning frameworks (JAX, PyTorch)</li>
 * <li>Sensitivity analysis of thermodynamic properties</li>
 * <li>Model Predictive Control (MPC) applications</li>
 * </ul>
 * 
 * @author ESOL
 * @since 3.0
 */
public class DifferentiableFlashExample {

  /**
   * Main method demonstrating differentiable flash calculations.
   * 
   * @param args command line arguments (not used)
   */
  public static void main(String[] args) {
    // =====================================================
    // Example 1: Basic Flash Gradient Computation
    // =====================================================
    System.out.println("=== Example 1: Basic Flash Gradients ===\n");

    // Create a two-phase system
    SystemInterface system = new SystemSrkEos(250.0, 30.0); // 250 K, 30 bar
    system.addComponent("methane", 0.8);
    system.addComponent("propane", 0.2);
    system.setMixingRule("classic");

    // Run flash calculation
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();

    // Verify we have two phases
    System.out.println("Number of phases: " + system.getNumberOfPhases());
    System.out.println("Vapor fraction (β): " + String.format("%.4f", system.getBeta()));
    System.out.println();

    // Compute analytical gradients using implicit function theorem
    DifferentiableFlash diffFlash = new DifferentiableFlash(system);
    FlashGradients grads = diffFlash.computeFlashGradients();

    if (grads.isValid()) {
      // K-value gradients
      double[] kValues = grads.getKValues();
      double[] dKdT = grads.getDKdT();
      double[] dKdP = grads.getDKdP();

      System.out.println("K-value sensitivities:");
      for (int i = 0; i < system.getNumberOfComponents(); i++) {
        String name = system.getComponent(i).getComponentName();
        System.out.println(String.format("  %s: K=%.4f, dK/dT=%.6f [1/K], dK/dP=%.6f [1/bar]", name,
            kValues[i], dKdT[i], dKdP[i]));
      }
      System.out.println();

      // Vapor fraction gradients
      double dBetadT = grads.getDBetadT();
      double dBetadP = grads.getDBetadP();
      System.out.println("Vapor fraction sensitivities:");
      System.out.println(String.format("  dβ/dT = %.6f [1/K]", dBetadT));
      System.out.println(String.format("  dβ/dP = %.6f [1/bar]", dBetadP));
      System.out.println();
    }

    // =====================================================
    // Example 2: Property Gradients (Density, Cp)
    // =====================================================
    System.out.println("=== Example 2: Property Gradients ===\n");

    // Compute density gradient
    PropertyGradient densityGrad = diffFlash.computePropertyGradient("density");
    System.out.println("Density gradients:");
    System.out
        .println(String.format("  ρ = %.4f %s", densityGrad.getValue(), densityGrad.getUnit()));
    System.out.println(
        String.format("  dρ/dT = %.6f kg/m³/K", densityGrad.getDerivativeWrtTemperature()));
    System.out
        .println(String.format("  dρ/dP = %.6f kg/m³/bar", densityGrad.getDerivativeWrtPressure()));
    System.out.println();

    // Compute heat capacity gradient
    PropertyGradient cpGrad = diffFlash.computePropertyGradient("Cp");
    System.out.println("Heat capacity (Cp) gradients:");
    System.out.println(String.format("  Cp = %.4f %s", cpGrad.getValue(), cpGrad.getUnit()));
    System.out
        .println(String.format("  dCp/dT = %.6f J/mol/K²", cpGrad.getDerivativeWrtTemperature()));
    System.out
        .println(String.format("  dCp/dP = %.6f J/mol/K/bar", cpGrad.getDerivativeWrtPressure()));
    System.out.println();

    // Compute enthalpy gradient
    PropertyGradient enthalpyGrad = diffFlash.computePropertyGradient("enthalpy");
    System.out.println("Enthalpy gradients:");
    System.out
        .println(String.format("  H = %.4f %s", enthalpyGrad.getValue(), enthalpyGrad.getUnit()));
    System.out.println(
        String.format("  dH/dT = %.6f J/mol/K", enthalpyGrad.getDerivativeWrtTemperature()));
    System.out.println(
        String.format("  dH/dP = %.6f J/mol/bar", enthalpyGrad.getDerivativeWrtPressure()));
    System.out.println();

    // =====================================================
    // Example 3: Fugacity Jacobian Access
    // =====================================================
    System.out.println("=== Example 3: Fugacity Jacobian ===\n");

    // Get fugacity Jacobians (computed during flash gradients)
    FugacityJacobian[] jacobians = diffFlash.getFugacityJacobians();

    if (jacobians != null && jacobians.length >= 2) {
      FugacityJacobian jacLiquid = jacobians[0];
      FugacityJacobian jacVapor = jacobians[1];

      System.out.println("Liquid phase fugacity derivatives:");
      double[] dlnPhidT_L = jacLiquid.getDlnPhidT();
      double[] dlnPhidP_L = jacLiquid.getDlnPhidP();
      for (int i = 0; i < system.getNumberOfComponents(); i++) {
        String name = jacLiquid.getComponentNames()[i];
        System.out.println(String.format("  %s: d(ln φ)/dT=%.6f, d(ln φ)/dP=%.6f", name,
            dlnPhidT_L[i], dlnPhidP_L[i]));
      }
      System.out.println();

      System.out.println("Vapor phase fugacity derivatives:");
      double[] dlnPhidT_V = jacVapor.getDlnPhidT();
      double[] dlnPhidP_V = jacVapor.getDlnPhidP();
      for (int i = 0; i < system.getNumberOfComponents(); i++) {
        String name = jacVapor.getComponentNames()[i];
        System.out.println(String.format("  %s: d(ln φ)/dT=%.6f, d(ln φ)/dP=%.6f", name,
            dlnPhidT_V[i], dlnPhidP_V[i]));
      }
      System.out.println();

      // Composition derivatives (Jacobian matrix)
      System.out.println("Composition Jacobian d(ln φ_i)/dn_j for vapor phase:");
      double[][] dlnPhidn = jacVapor.getDlnPhidn();
      System.out.print("         ");
      for (int j = 0; j < system.getNumberOfComponents(); j++) {
        System.out.print(String.format("%12s", jacVapor.getComponentNames()[j]));
      }
      System.out.println();
      for (int i = 0; i < system.getNumberOfComponents(); i++) {
        System.out.print(String.format("%8s ", jacVapor.getComponentNames()[i]));
        for (int j = 0; j < system.getNumberOfComponents(); j++) {
          System.out.print(String.format("%12.4f", dlnPhidn[i][j]));
        }
        System.out.println();
      }
    }
    System.out.println();

    // =====================================================
    // Example 4: Gradient Array for Optimization
    // =====================================================
    System.out.println("=== Example 4: Gradient Arrays for Optimization ===\n");

    // Get flat gradient arrays suitable for optimization libraries
    double[] flatGradients = grads.toFlatArray();
    System.out.println("Flat gradient array (for optimization):");
    System.out.println("  Length: " + flatGradients.length);
    System.out.println("  Format: [dK1/dT, dK2/dT, ..., dK1/dP, dK2/dP, ..., dβ/dT, dβ/dP]");
    System.out.println();

    // Directional derivative example
    // If we increase T by 1 K and decrease P by 0.5 bar simultaneously,
    // what's the change in β?
    double dT = 1.0; // K
    double dP = -0.5; // bar
    double dBeta = grads.getDBetadT() * dT + grads.getDBetadP() * dP;
    System.out.println("Directional derivative example:");
    System.out.println(
        String.format("  If T increases by %.1f K and P decreases by %.1f bar:", dT, Math.abs(dP)));
    System.out.println(String.format("  Expected change in β: %.6f", dBeta));
    System.out.println(
        String.format("  (from β=%.4f to β≈%.4f)", system.getBeta(), system.getBeta() + dBeta));
    System.out.println();

    // =====================================================
    // Example 5: Multi-component System
    // =====================================================
    System.out.println("=== Example 5: Multi-component Natural Gas ===\n");

    SystemInterface gasSystem = new SystemSrkEos(220.0, 50.0);
    gasSystem.addComponent("nitrogen", 0.02);
    gasSystem.addComponent("methane", 0.85);
    gasSystem.addComponent("ethane", 0.08);
    gasSystem.addComponent("propane", 0.03);
    gasSystem.addComponent("n-butane", 0.02);
    gasSystem.setMixingRule("classic");

    ThermodynamicOperations gasOps = new ThermodynamicOperations(gasSystem);
    gasOps.TPflash();

    System.out.println(
        "Natural gas at " + gasSystem.getTemperature() + " K, " + gasSystem.getPressure() + " bar");
    System.out.println("Number of phases: " + gasSystem.getNumberOfPhases());
    System.out.println("Vapor fraction: " + String.format("%.4f", gasSystem.getBeta()));

    if (gasSystem.getNumberOfPhases() >= 2) {
      DifferentiableFlash gasDiffFlash = new DifferentiableFlash(gasSystem);
      FlashGradients gasGrads = gasDiffFlash.computeFlashGradients();

      if (gasGrads.isValid()) {
        System.out.println("\nK-value temperature sensitivities:");
        double[] gasDKdT = gasGrads.getDKdT();
        for (int i = 0; i < gasSystem.getNumberOfComponents(); i++) {
          String name = gasSystem.getComponent(i).getComponentName();
          System.out.println(String.format("  %s: dK/dT = %.6f [1/K]", name, gasDKdT[i]));
        }
      }
    } else {
      System.out.println("(Single phase - no flash gradients available)");
    }

    System.out.println("\n=== Examples Complete ===");
  }
}
