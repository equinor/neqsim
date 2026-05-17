package neqsim.process.equipment.mixer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

class StaticMixerTest {

  @Test
  void testMixTwoStreamsEqualFlow() {
    neqsim.thermo.system.SystemInterface sys1 = new SystemSrkEos(298.0, 50.0);
    sys1.addComponent("methane", 100.0);
    sys1.setMixingRule("classic");

    neqsim.thermo.system.SystemInterface sys2 = new SystemSrkEos(298.0, 50.0);
    sys2.addComponent("methane", 100.0);
    sys2.setMixingRule("classic");

    Stream stream1 = new Stream("stream1", sys1);
    stream1.setPressure(50.0, "bara");
    stream1.setTemperature(25.0, "C");
    stream1.setFlowRate(5.0, "MSm3/day");

    Stream stream2 = new Stream("stream2", sys2);
    stream2.setPressure(50.0, "bara");
    stream2.setTemperature(25.0, "C");
    stream2.setFlowRate(5.0, "MSm3/day");

    ProcessSystem process = new ProcessSystem();
    process.add(stream1);
    process.add(stream2);

    StaticMixer mixer = new StaticMixer("mixer");
    mixer.addStream(stream1);
    mixer.addStream(stream2);
    process.add(mixer);

    process.run();

    double outletFlow = mixer.getOutletStream().getThermoSystem().getFlowRate("MSm3/day");
    // Sum of two 5 MSm3/day streams should be ~10
    assertEquals(10.0, outletFlow, 0.5);
  }

  @Test
  void testMixTwoDifferentCompositions() {
    neqsim.thermo.system.SystemInterface sys1 = new SystemSrkEos(298.0, 50.0);
    sys1.addComponent("methane", 100.0);
    sys1.addComponent("ethane", 0.0);
    sys1.setMixingRule("classic");

    neqsim.thermo.system.SystemInterface sys2 = new SystemSrkEos(298.0, 50.0);
    sys2.addComponent("methane", 0.0);
    sys2.addComponent("ethane", 100.0);
    sys2.setMixingRule("classic");

    Stream stream1 = new Stream("stream1", sys1);
    stream1.setPressure(50.0, "bara");
    stream1.setTemperature(25.0, "C");
    stream1.setFlowRate(5.0, "MSm3/day");

    Stream stream2 = new Stream("stream2", sys2);
    stream2.setPressure(50.0, "bara");
    stream2.setTemperature(25.0, "C");
    stream2.setFlowRate(5.0, "MSm3/day");

    ProcessSystem process = new ProcessSystem();
    process.add(stream1);
    process.add(stream2);

    StaticMixer mixer = new StaticMixer("mixer");
    mixer.addStream(stream1);
    mixer.addStream(stream2);
    process.add(mixer);

    process.run();

    // Mixed stream should contain both components
    assertNotNull(mixer.getOutletStream().getThermoSystem().getPhase(0).getComponent("methane"));
    assertNotNull(mixer.getOutletStream().getThermoSystem().getPhase(0).getComponent("ethane"));
  }

  @Test
  void testMassBalance() {
    neqsim.thermo.system.SystemInterface sys1 = new SystemSrkEos(298.0, 50.0);
    sys1.addComponent("methane", 90.0);
    sys1.addComponent("ethane", 10.0);
    sys1.setMixingRule("classic");

    neqsim.thermo.system.SystemInterface sys2 = new SystemSrkEos(298.0, 50.0);
    sys2.addComponent("methane", 70.0);
    sys2.addComponent("ethane", 30.0);
    sys2.setMixingRule("classic");

    Stream stream1 = new Stream("stream1", sys1);
    stream1.setPressure(50.0, "bara");
    stream1.setTemperature(25.0, "C");
    stream1.setFlowRate(3.0, "MSm3/day");

    Stream stream2 = new Stream("stream2", sys2);
    stream2.setPressure(50.0, "bara");
    stream2.setTemperature(25.0, "C");
    stream2.setFlowRate(7.0, "MSm3/day");

    ProcessSystem process = new ProcessSystem();
    process.add(stream1);
    process.add(stream2);

    StaticMixer mixer = new StaticMixer("mixer");
    mixer.addStream(stream1);
    mixer.addStream(stream2);
    process.add(mixer);

    process.run();

    double inletMass = stream1.getThermoSystem().getFlowRate("kg/hr")
        + stream2.getThermoSystem().getFlowRate("kg/hr");
    double outletMass = mixer.getOutletStream().getThermoSystem().getFlowRate("kg/hr");

    assertEquals(inletMass, outletMass, inletMass * 1e-4);
  }

  @Test
  void testMixDifferentTemperatures() {
    neqsim.thermo.system.SystemInterface sys1 = new SystemSrkEos(298.0, 50.0);
    sys1.addComponent("methane", 100.0);
    sys1.setMixingRule("classic");

    neqsim.thermo.system.SystemInterface sys2 = new SystemSrkEos(298.0, 50.0);
    sys2.addComponent("methane", 100.0);
    sys2.setMixingRule("classic");

    Stream hotStream = new Stream("hot", sys1);
    hotStream.setPressure(50.0, "bara");
    hotStream.setTemperature(80.0, "C");
    hotStream.setFlowRate(5.0, "MSm3/day");

    Stream coldStream = new Stream("cold", sys2);
    coldStream.setPressure(50.0, "bara");
    coldStream.setTemperature(20.0, "C");
    coldStream.setFlowRate(5.0, "MSm3/day");

    ProcessSystem process = new ProcessSystem();
    process.add(hotStream);
    process.add(coldStream);

    StaticMixer mixer = new StaticMixer("mixer");
    mixer.addStream(hotStream);
    mixer.addStream(coldStream);
    process.add(mixer);

    process.run();

    double mixedTempC = mixer.getOutletStream().getTemperature("C");
    // Mixed temperature should be between 20 and 80 C
    assertTrue(mixedTempC > 20.0, "Mixed temp should be above cold stream temp");
    assertTrue(mixedTempC < 80.0, "Mixed temp should be below hot stream temp");
    // For equal flows of same composition, should be close to average
    assertEquals(50.0, mixedTempC, 5.0);
  }

  @Test
  void testToJson() {
    neqsim.thermo.system.SystemInterface sys1 = new SystemSrkEos(298.0, 50.0);
    sys1.addComponent("methane", 100.0);
    sys1.setMixingRule("classic");

    Stream stream1 = new Stream("stream1", sys1);
    stream1.setPressure(50.0, "bara");
    stream1.setTemperature(25.0, "C");
    stream1.setFlowRate(5.0, "MSm3/day");

    ProcessSystem process = new ProcessSystem();
    process.add(stream1);

    StaticMixer mixer = new StaticMixer("mixer");
    mixer.addStream(stream1);
    process.add(mixer);

    process.run();

    String json = mixer.toJson();
    assertNotNull(json);
    assertTrue(json.length() > 0);
  }

  @Test
  void testThreeStreamMix() {
    neqsim.thermo.system.SystemInterface sys1 = new SystemSrkEos(298.0, 50.0);
    sys1.addComponent("methane", 100.0);
    sys1.setMixingRule("classic");

    neqsim.thermo.system.SystemInterface sys2 = new SystemSrkEos(298.0, 50.0);
    sys2.addComponent("methane", 100.0);
    sys2.setMixingRule("classic");

    neqsim.thermo.system.SystemInterface sys3 = new SystemSrkEos(298.0, 50.0);
    sys3.addComponent("methane", 100.0);
    sys3.setMixingRule("classic");

    Stream s1 = new Stream("s1", sys1);
    s1.setPressure(50.0, "bara");
    s1.setTemperature(25.0, "C");
    s1.setFlowRate(2.0, "MSm3/day");

    Stream s2 = new Stream("s2", sys2);
    s2.setPressure(50.0, "bara");
    s2.setTemperature(25.0, "C");
    s2.setFlowRate(3.0, "MSm3/day");

    Stream s3 = new Stream("s3", sys3);
    s3.setPressure(50.0, "bara");
    s3.setTemperature(25.0, "C");
    s3.setFlowRate(5.0, "MSm3/day");

    ProcessSystem process = new ProcessSystem();
    process.add(s1);
    process.add(s2);
    process.add(s3);

    StaticMixer mixer = new StaticMixer("mixer");
    mixer.addStream(s1);
    mixer.addStream(s2);
    mixer.addStream(s3);
    process.add(mixer);

    process.run();

    double totalIn = s1.getThermoSystem().getFlowRate("kg/hr")
        + s2.getThermoSystem().getFlowRate("kg/hr") + s3.getThermoSystem().getFlowRate("kg/hr");
    double totalOut = mixer.getOutletStream().getThermoSystem().getFlowRate("kg/hr");
    assertEquals(totalIn, totalOut, totalIn * 1e-4);
  }
}
