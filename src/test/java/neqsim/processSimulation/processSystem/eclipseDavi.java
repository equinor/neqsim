package neqsim.processSimulation.processSystem;

import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;

public class eclipseDavi extends neqsim.NeqSimTest {
  @Test
  public void runTest() throws InterruptedException {
    neqsim.thermo.util.readwrite.EclipseFluidReadWrite.pseudoName = "";
    SystemInterface fluid1 = neqsim.thermo.util.readwrite.EclipseFluidReadWrite.read(
        "/workspaces/neqsim/src/test/java/neqsim/processSimulation/processSystem/U2_eclipse_format 2.txt");



  }
}
