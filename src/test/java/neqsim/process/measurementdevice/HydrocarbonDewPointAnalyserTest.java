package neqsim.process.measurementdevice;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class HydrocarbonDewPointAnalyserTest {
  @Test
  void testHCdewPoint() {
    SystemInterface thermoSystem = new SystemSrkEos(298.0, 50.0);
    thermoSystem.addComponent("methane", 1.0);
    thermoSystem.addComponent("ethane", .01);
    thermoSystem.addComponent("propane", 0.001);
    thermoSystem.addComponent("i-butane", 0.001);
    thermoSystem.addComponent("n-butane", 0.001);
    thermoSystem.addComponent("i-pentane", 0.001);
    thermoSystem.addComponent("n-pentane", 0.0001);
    thermoSystem.addComponent("water", 0.001);
    thermoSystem.setMixingRule("classic");
    Stream stream1 = new Stream("stream 1", thermoSystem);
    HydrocarbonDewPointAnalyser hc_analyser =
        new HydrocarbonDewPointAnalyser("hc analyser", stream1);
    ProcessSystem process1 = new ProcessSystem();
    process1.add(stream1);
    process1.add(hc_analyser);

    process1.run();
    hc_analyser.setReferencePressure(40.0);
    hc_analyser.getMeasuredValue("C");
    Assertions.assertEquals(-14.0173918, hc_analyser.getMeasuredValue("C"), 1e-5);


  }
}
