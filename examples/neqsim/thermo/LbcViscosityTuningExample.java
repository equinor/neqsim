package neqsim.thermo;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Demonstrates how to tune the LBC viscosity model by supplying custom dense-fluid parameters.
 */
public class LbcViscosityTuningExample {
  public static void main(String[] args) {
    // Create a medium-heavy oil system
    SystemInterface system = new SystemSrkEos(323.15, 150.0);
    system.addComponent("methane", 0.5);
    system.addComponent("n-heptane", 0.2);
    system.addComponent("n-decane", 0.2);
    system.addComponent("nC20", 0.1);
    system.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();

    // Use the LBC viscosity model on the oil phase (phase index 1)
    system.getPhase(1).getPhysicalProperties().setViscosityModel("LBC");
    system.initProperties();
    double defaultViscosity = system.getPhase(1).getViscosity();

    System.out.println("Default LBC viscosity = " + defaultViscosity + " Pa·s");

    // Tune the dense-fluid polynomial coefficients (Whitson/Bray-Clark a0–a4 terms)
    double[] tunedParameters = new double[] {0.11, 0.030, 0.065, -0.045, 0.010};
    system.getPhase(1).getPhysicalProperties().setLbcParameters(tunedParameters);

    // You can also tweak a single coefficient after setting the full array
    system.getPhase(1).getPhysicalProperties().setLbcParameter(2, 0.070);

    // Recalculate properties to apply the new LBC parameters
    system.initProperties();
    double tunedViscosity = system.getPhase(1).getViscosity();

    System.out.println("Tuned LBC viscosity    = " + tunedViscosity + " Pa·s");
  }
}
