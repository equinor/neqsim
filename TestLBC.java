import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class TestLBC {
  public static void main(String[] args) {
    double T = 300.0;
    double P = 1.0;
    SystemSrkEos testSystem = new SystemSrkEos(T, P);
    testSystem.addComponent("n-heptane", 1.0);
    testSystem.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
    ops.TPflash();
    testSystem.getPhase("oil").getPhysicalProperties().setViscosityModel("LBC");
    testSystem.initProperties();
    System.out.println("Phase: " + testSystem.getPhase(0).getType());
    System.out.println("LBC viscosity: " + testSystem.getPhase(0).getPhysicalProperties().getViscosity());
    System.out.println("Density: " + testSystem.getPhase(0).getPhysicalProperties().getDensity());
    System.out.println("Molar mass: " + testSystem.getPhase(0).getMolarMass());
  }
}
