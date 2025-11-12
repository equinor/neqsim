import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class test_bubble_point {
  public static void main(String[] args) {
    try {
      // Recreate the failing test scenario
      SystemInterface fluidSystem = new SystemSrkEos(273.15 + 60.98, 58.61 + 1.01325);

      // Add components similar to the test (simplified version)
      fluidSystem.addComponent("methane", 50.0);
      fluidSystem.addComponent("ethane", 10.0);
      fluidSystem.addComponent("propane", 5.0);
      fluidSystem.addTBPfraction("C10", 20.0, 150.0 / 1000.0, 750.0);
      fluidSystem.addTBPfraction("C20", 10.0, 280.0 / 1000.0, 850.0);
      fluidSystem.addComponent("water", 10.0);

      fluidSystem.init(0);
      fluidSystem.setMixingRule(2);
      fluidSystem.useVolumeCorrection(true);
      fluidSystem.setMultiPhaseCheck(true);

      System.out.println("Initial pressure: " + fluidSystem.getPressure() + " bar");
      System.out.println("Temperature: " + fluidSystem.getTemperature() + " K");

      ThermodynamicOperations operations = new ThermodynamicOperations(fluidSystem);
      operations.TPflash();

      System.out.println("After TP flash pressure: " + fluidSystem.getPressure() + " bar");

      // Try bubble point calculation
      SystemInterface bubbleFluid = fluidSystem.clone();
      ThermodynamicOperations bubbleOps = new ThermodynamicOperations(bubbleFluid);

      System.out.println("Starting bubble point calculation...");
      bubbleOps.bubblePointPressureFlash(false);

      System.out.println("Bubble point pressure: " + bubbleFluid.getPressure() + " bara");
      System.out.println("SUCCESS!");
    } catch (Exception e) {
      System.err.println("ERROR: " + e.getMessage());
      e.printStackTrace();
    }
  }
}
