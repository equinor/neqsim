package neqsim.processSimulation.processSystem;

import org.junit.jupiter.api.Test;
import neqsim.processSimulation.processEquipment.mixer.StaticPhaseMixer;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class MassTransferTest extends neqsim.NeqSimTest {

  @Test
  public void runProcess() throws InterruptedException {
    SystemInterface thermoSystem = new SystemSrkEos(273.15 + 15, 60.0);
    thermoSystem.addComponent("nitrogen", 0.01);
    thermoSystem.addComponent("CO2", 0.019);
    thermoSystem.addComponent("methane", 0.925);
    thermoSystem.addComponent("ethane", 0.045);
    thermoSystem.setMixingRule("classic");

    SystemInterface ethaneSystem = ((SystemInterface) thermoSystem).clone();
    ethaneSystem.setMolarComposition(new double[] {0, 0, 0, 1.0});

    Stream feedStream = new Stream("feed stream", thermoSystem);
    feedStream.setFlowRate(10.0 * 24, "MSm3/day");

    Stream ethaneStream = new Stream("ethane stream", ethaneSystem);
    feedStream.setFlowRate(200000.0, "kg/hr");

    StaticPhaseMixer mainMixer = new StaticPhaseMixer("gas ethane mixer");
    mainMixer.addStream(feedStream);
    mainMixer.addStream(ethaneStream);

    neqsim.processSimulation.processEquipment.util.NeqSimUnit pipeline =
        new neqsim.processSimulation.processEquipment.util.NeqSimUnit(mainMixer.getOutletStream(),
            "pipeline", "stratified");
    pipeline.setLength(0.1);
    pipeline.setID(1.0);

    neqsim.processSimulation.processSystem.ProcessSystem operations =
        new neqsim.processSimulation.processSystem.ProcessSystem();
    operations.add(feedStream);
    operations.add(ethaneStream);
    operations.add(mainMixer);
    operations.add(pipeline);

    operations.run();

    pipeline.getOutletStream().getFluid().prettyPrint();

  }
}
