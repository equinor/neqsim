package neqsim.thermo.system;

import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class SystemBnsPrintTest {
  @Test
  public void printAll() {
    SystemBnsEos sys = new SystemBnsEos();
    sys.setTemperature(300.0);
    sys.setPressure(100.0);
    sys.setAssociatedGas(false);
    sys.setRelativeDensity(0.65);
    sys.setComposition(0.02, 0.0, 0.01, 0.0);
    sys.useVolumeCorrection(true);
    sys.setMixingRule(12);
    ThermodynamicOperations ops = new ThermodynamicOperations(sys);
    ops.TPflash();
    sys.initProperties();
    System.out.println("=== Test 100bar ===");
    System.out.println("Z = " + sys.getPhase(0).getZvolcorr());
    System.out.println("density = " + sys.getDensity("kg/m3"));
    System.out.println("cp = " + sys.getPhase(0).getCp("J/molK"));
    System.out.println("cv = " + sys.getPhase(0).getCv("J/molK"));
    System.out.println("jt = " + sys.getPhase(0).getJouleThomsonCoefficient() * 10.0);
    double gamma = sys.getPhase(0).getCp("J/molK") / sys.getPhase(0).getCv("J/molK");
    double speed = Math.sqrt(gamma * sys.getPhase(0).getZ()
        * neqsim.thermo.ThermodynamicConstantsInterface.R * sys.getTemperature()
        / sys.getMolarMass());
    System.out.println("speed = " + speed);
    System.out.println("MW = " + sys.getMolarMass());

    SystemBnsEos sys2 = new SystemBnsEos();
    sys2.setTemperature(48.88889 + 273.15);
    sys2.setPressure(13.78948965 * 10.0);
    sys2.setRelativeDensity(0.8);
    sys2.setComposition(0.2, 0.1, 0.02, 0.1);
    sys2.useVolumeCorrection(true);
    sys2.setMixingRule(12);
    ThermodynamicOperations ops2 = new ThermodynamicOperations(sys2);
    ops2.TPflash();
    sys2.initProperties();
    sys2.init(3);
    System.out.println("=== Test Python ===");
    System.out.println("Z = " + sys2.getZvolcorr());
    System.out.println("density = " + sys2.getDensity("kg/m3"));
    System.out.println("jt = " + sys2.getPhase(0).getJouleThomsonCoefficient() * 10.0);
    System.out.println("cp = " + sys2.getPhase(0).getCp("J/molK"));
    System.out.println("MW = " + sys2.getMolarMass());
  }
}