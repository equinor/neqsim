package neqsim.processSimulation.processSystem;

import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

public class eclipseDavi extends neqsim.NeqSimTest {
  @Test
  public void runTest() throws InterruptedException {
    neqsim.thermo.util.readwrite.EclipseFluidReadWrite.pseudoName = "";
    SystemInterface fluid1 = neqsim.thermo.util.readwrite.EclipseFluidReadWrite.read(
        "/workspaces/neqsim/src/test/java/neqsim/processSimulation/processSystem/U2_25C_eclipse_format_v2.txt");
    fluid1.getComponent("water").setComponentType("Aqueous");
    fluid1.setPressure(1, "bara");
    fluid1.setTemperature(20, "C");
    fluid1.setMultiPhaseCheck(true);
    fluid1.prettyPrint();

    ThermodynamicOperations testOps = new ThermodynamicOperations(fluid1);
    testOps.TPflash();

    fluid1.prettyPrint();
    



  }
}
