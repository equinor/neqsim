package neqsim.processSimulation.processSystem;

import org.junit.jupiter.api.Test;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemUMRPRUMCEos;

public class SystemUMRPRUMCEosAsyncMultiSimTest extends neqsim.NeqSimTest {
  neqsim.thermo.system.SystemInterface testSystem = null;
  ProcessSystem processOps = null;
  

   public void setUpBeforeClass(){
    testSystem = new SystemUMRPRUMCEos(298.0, 10.0);
    testSystem.addComponent("CO2", 0.00971);
    testSystem.addComponent("nitrogen", 0.00591);
    testSystem.addComponent("methane", 0.948);
    testSystem.addComponent("ethane", 0.0251);
    testSystem.addComponent("propane", 0.00406);
    testSystem.addComponent("i-butane", 0.00148);
    testSystem.addComponent("n-butane", 0.00101);
    testSystem.addComponent("i-pentane", 0.000782);
    testSystem.addComponent("n-pentane", 0.000433);
    testSystem.addComponent("2-m-C5", 0.000407);
    testSystem.addComponent("3-m-C5", 0.000128);
    testSystem.addComponent("n-hexane", 0.000213);
    testSystem.addComponent("n-heptane", 0.000854);
    testSystem.addComponent("c-hexane", 0.000157);
    testSystem.addComponent("c-C7", 0.000608);
    testSystem.addComponent("benzene", 4.64E-05);
    testSystem.addComponent("n-octane", 4.64E-05);
    testSystem.addComponent("toluene", 0.0);
    testSystem.addComponent("c-C8", 0.0);
    testSystem.addComponent("n-nonane", 0.0);
    testSystem.addComponent("m-Xylene", 0.0);
    testSystem.addComponent("nC10", 0.0);
    testSystem.addComponent("nC11", 0.0);
    testSystem.addComponent("nC12", 0.0);
    testSystem.setMixingRule("HV", "UNIFAC_UMRPRU");

    processOps = new ProcessSystem();

    StreamInterface inletStream = new Stream("inlet stream", testSystem);
    inletStream.setPressure(55.0, "bara");
    inletStream.setTemperature(35.0, "C");
    inletStream.setFlowRate(25.0, "MSm3/day");
    processOps.add(inletStream);

   }

   @Test
   public void testAsync() {
    for (int numberOfProcessToRun = 0; numberOfProcessToRun < 5; numberOfProcessToRun++){
      setUpBeforeClass();
      processOps.run();
      System.out.println(numberOfProcessToRun);
    }
   }


}
