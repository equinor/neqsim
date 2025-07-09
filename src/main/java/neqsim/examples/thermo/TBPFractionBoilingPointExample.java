package neqsim.examples.thermo;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Example demonstrating the new addTBPfractionFromBoilingPoint method.
 */
public class TBPFractionBoilingPointExample {
  
  public static void main(String[] args) {
    // Create a thermo system
    SystemInterface thermoSystem = new SystemSrkEos(273.15 + 80, 50.0);
    
    // Add pure components
    thermoSystem.addComponent("methane", 10.0);
    thermoSystem.addComponent("ethane", 5.0);
    thermoSystem.addComponent("propane", 3.0);
    
    // Traditional method: requires molar mass AND density
    thermoSystem.addTBPfraction("C7_traditional", 1.0, 0.109, 0.6912);
    
    // New method: only requires boiling point and molar mass
    // The density is calculated automatically using TBP correlations
    thermoSystem.addTBPfractionFromBoilingPoint("C7_new", 1.0, 371.6, 0.109);
    
    // Example with other fractions using boiling point data
    thermoSystem.addTBPfractionFromBoilingPoint("C8", 1.0, 398.8, 0.114);
    thermoSystem.addTBPfractionFromBoilingPoint("C9", 1.0, 423.9, 0.128);
    thermoSystem.addTBPfractionFromBoilingPoint("C10", 1.0, 447.3, 0.142);
    
    // Initialize the system
    thermoSystem.createDatabase(true);
    thermoSystem.setMixingRule(9);
    thermoSystem.init(0);
    
    // Display results
    System.out.println("System components:");
    for (int i = 0; i < thermoSystem.getNumberOfComponents(); i++) {
      System.out.printf("Component %d: %s\n", i+1, thermoSystem.getComponent(i).getComponentName());
      System.out.printf("  Molar mass: %.3f kg/mol\n", thermoSystem.getComponent(i).getMolarMass());
      System.out.printf("  Density: %.4f g/cm³\n", thermoSystem.getComponent(i).getNormalLiquidDensity());
      System.out.printf("  Critical T: %.1f K\n", thermoSystem.getComponent(i).getTC());
      System.out.printf("  Critical P: %.1f bar\n", thermoSystem.getComponent(i).getPC());
      System.out.printf("  Normal BP: %.1f K\n", thermoSystem.getComponent(i).getNormalBoilingPoint());
      System.out.println();
    }
  }
}
