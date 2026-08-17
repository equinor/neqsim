package neqsim.process.equipment.mixer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;

class StaticMixerTest {

  private StaticMixer runGasMegMixer(boolean megFirst) {
    neqsim.thermo.system.SystemInterface gasFluid = new SystemSrkCPAstatoil(302.15, 74.1);
    gasFluid.addComponent("methane", 5.0);
    gasFluid.addComponent("water", 0.11833608283886514);
    gasFluid.addComponent("MEG", 0.0);
    gasFluid.setMixingRule(10);
    gasFluid.setMultiPhaseCheck(true);

    neqsim.thermo.system.SystemInterface megFluid = gasFluid.clone();
    megFluid.setMolarComposition(new double[] { 0.0, 0.1099744114900417, 0.8900255885099583 });
    megFluid.setMultiPhaseCheck(false);

    Stream gasStream = new Stream("bulk wet gas", gasFluid);
    gasStream.setFlowRate(168958.0, "Sm3/hr");
    gasStream.setTemperature(29.0, "C");
    gasStream.setPressure(74.1, "barg");
    gasStream.run();

    Stream megStream = new Stream("lean MEG", megFluid);
    megStream.setFlowRate(0.01, "kg/hr");
    megStream.setTemperature(29.0, "C");
    megStream.setPressure(74.1, "barg");
    megStream.run();

    StaticMixer mixer = new StaticMixer("gas/MEG mixer");
    if (megFirst) {
      mixer.addStream(megStream);
      mixer.addStream(gasStream);
    } else {
      mixer.addStream(gasStream);
      mixer.addStream(megStream);
    }
    mixer.run();
    return mixer;
  }

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
  void testReusedMixerRetainsGasPhaseAfterChangingInhibitorFlow() {
    neqsim.thermo.system.SystemInterface gasFluid = new SystemSrkCPAstatoil(278.45, 37.21325);
    gasFluid.addComponent("methane", 5.0);
    gasFluid.addComponent("water", 0.11833608283886514);
    gasFluid.addComponent("MEG", 0.0);
    gasFluid.setMixingRule(10);
    gasFluid.setMultiPhaseCheck(true);

    neqsim.thermo.system.SystemInterface megFluid = gasFluid.clone();
    megFluid.setMolarComposition(new double[] { 0.0, 0.1099744114900417, 0.8900255885099583 });

    Stream gasStream = new Stream("gas", gasFluid);
    gasStream.setFlowRate(168958.0, "Sm3/hr");
    gasStream.setTemperature(29.0, "C");
    gasStream.setPressure(74.1, "barg");

    Stream megStream = new Stream("MEG", megFluid);
    megStream.setFlowRate(0.01, "kg/hr");
    megStream.setTemperature(29.0, "C");
    megStream.setPressure(74.1, "barg");

    StaticMixer mixer = new StaticMixer("reused multiphase mixer");
    mixer.addStream(megStream);
    mixer.addStream(gasStream);

    ProcessSystem process = new ProcessSystem();
    process.add(gasStream);
    process.add(megStream);
    process.add(mixer);
    process.run();

    megStream.setFlowRate(0.1, "kg/hr");
    process.run();

    assertTrue(mixer.getOutletStream().getFluid().hasPhaseType("gas"));
  }

  @Test
  void testInletOrderDoesNotChangeDominantTemplateOrMultiphaseConfiguration() {
    StaticMixer megFirstMixer = runGasMegMixer(true);
    StaticMixer gasFirstMixer = runGasMegMixer(false);

    assertTrue(megFirstMixer.getThermoSystem().doMultiPhaseCheck());
    assertTrue(gasFirstMixer.getThermoSystem().doMultiPhaseCheck());
    assertEquals(gasFirstMixer.getOutletStream().getFlowRate("kg/hr"),
        megFirstMixer.getOutletStream().getFlowRate("kg/hr"), 1.0e-6);
    assertEquals(gasFirstMixer.getOutletStream().getTemperature("K"),
        megFirstMixer.getOutletStream().getTemperature("K"), 1.0e-6);
    assertEquals(gasFirstMixer.getOutletStream().getFluid().getNumberOfPhases(),
        megFirstMixer.getOutletStream().getFluid().getNumberOfPhases());
    assertTrue(megFirstMixer.getOutletStream().getFluid().hasPhaseType("gas"));
    assertFalse(Double.isNaN(megFirstMixer.getOutletStream().getFlowRate("kg/hr")));
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

    double inletMass = stream1.getThermoSystem().getFlowRate("kg/hr") + stream2.getThermoSystem().getFlowRate("kg/hr");
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

    double totalIn = s1.getThermoSystem().getFlowRate("kg/hr") + s2.getThermoSystem().getFlowRate("kg/hr")
        + s3.getThermoSystem().getFlowRate("kg/hr");
    double totalOut = mixer.getOutletStream().getThermoSystem().getFlowRate("kg/hr");
    assertEquals(totalIn, totalOut, totalIn * 1e-4);
  }
}
