package neqsim.process.measurementdevice;

import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class HydrocarbonDewPointAnalyserTest {
  @Test
  void testGetMethod() {
    SystemInterface thermoSystem = new SystemSrkEos(298.0, 50.0);
    thermoSystem.addComponent("methane", 1.0);
    thermoSystem.addComponent("ethane", 1.0);
    thermoSystem.addComponent("propane", 1.0);
    thermoSystem.addComponent("i-butane", 1.0);
    thermoSystem.addComponent("n-butane", 1.0);
    thermoSystem.addComponent("i-pentane", 1.0);
    thermoSystem.addComponent("n-pentane", 1.0);

    Stream stream1 = new Stream("stream 1", thermoSystem);
    HydrocarbonDewPointAnalyser hc_analyser =
        new HydrocarbonDewPointAnalyser("hc analyser", stream1);
    ProcessSystem process1 = new ProcessSystem();
    process1.add(stream1);
    process1.add(hc_analyser);

    process1.run();

  }
}
