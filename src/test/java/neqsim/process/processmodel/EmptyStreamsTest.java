package neqsim.process.processmodel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.Recycle;
import neqsim.thermo.system.SystemInterface;

/**
 * Class for testing ProcessSystem class.
 */
public class EmptyStreamsTest extends neqsim.NeqSimTest {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(EmptyStreamsTest.class);

  @Test
  void testEmptyStream() {
    SystemInterface fluid1 = new neqsim.thermo.system.SystemPrEos(273.15 + 20, 10.0);
    fluid1.addComponent("methane", 90.0);
    fluid1.addComponent("ethane", 10.0);
    fluid1.setMixingRule("classic");

    ProcessSystem operations = new ProcessSystem();

    StreamInterface stream1 = new Stream("Stream1", fluid1);
    stream1.setTemperature(20.0, "C");
    stream1.setPressure(10.0, "bara");
    stream1.run();
    operations.add(stream1);

    StreamInterface stream2 = stream1.clone("Stream2");

    Separator separator1 = new Separator("Separator", stream1);
    separator1.run();
    operations.add(separator1);

    Pump pump1 = new Pump("pump1", separator1.getLiquidOutStream());
    pump1.setOutletPressure(30.0, "bara");
    pump1.run();
    operations.add(pump1);

    Mixer mixer1 = new Mixer("Mixer");
    mixer1.addStream(pump1.getOutletStream());
    mixer1.run();
    operations.add(mixer1);

    Recycle recycle1 = new Recycle("Recycle 1");
    recycle1.addStream(mixer1.getOutletStream());
    recycle1.setOutletStream(stream2);
    recycle1.run();
    operations.add(recycle1);
  }

  @Test
  void testEmptyStream2() {
    SystemInterface fluid1 = new neqsim.thermo.system.SystemPrEos(273.15 + 20, 10.0);
    fluid1.addComponent("methane", 90.0);
    fluid1.addComponent("ethane", 10.0);
    fluid1.addComponent("water", 1.0e-6);
    fluid1.setMixingRule("classic");
    fluid1.setMultiPhaseCheck(true);

    SystemInterface fluid2 = new neqsim.thermo.system.SystemPrEos(273.15 + 20, 10.0);
    fluid2.addComponent("methane", 0.0);
    fluid2.addComponent("ethane", 0.0);
    fluid2.addComponent("water", 1.0);
    fluid2.setMixingRule("classic");
    fluid2.setMultiPhaseCheck(true);

    ProcessSystem operations = new ProcessSystem();

    StreamInterface stream1 = new Stream("Stream1", fluid1);
    stream1.setTemperature(20.0, "C");
    stream1.setPressure(10.0, "bara");
    stream1.run();
    operations.add(stream1);

    StreamInterface stream3 = stream1.clone("Stream3");

    StreamInterface stream2 = new Stream("Stream2", fluid2);
    stream2.run();
    operations.add(stream2);

    ThreePhaseSeparator separator1 = new ThreePhaseSeparator("Separator", stream1);
    separator1.run();
    operations.add(separator1);

    Pump pump1 = new Pump("pump1", separator1.getLiquidOutStream());
    pump1.setOutletPressure(30.0, "bara");
    pump1.run();
    operations.add(pump1);

    Mixer mixer1 = new Mixer("Mixer");
    mixer1.addStream(pump1.getOutletStream());
    mixer1.addStream(stream2);
    mixer1.run();
    operations.add(mixer1);

    Recycle recycle1 = new Recycle("Recycle 1");
    recycle1.addStream(mixer1.getOutletStream());
    recycle1.setOutletStream(stream3);
    recycle1.run();
    operations.add(recycle1);

    stream3.run();

    // stream3.getFluid().prettyPrint();
    // System.out.println("flow rate " + stream3.getFlowRate("kg/hr"));
    // System.out.println(recycle1.isActive());
    operations.run();
  }

  @Test
  void testEmptyHeater() {

    ProcessSystem operations = new ProcessSystem();

    SystemInterface fluid1 = new neqsim.thermo.system.SystemPrEos(273.15 + 20, 10.0);
    fluid1.addComponent("methane", 90.0);
    fluid1.addComponent("ethane", 10.0);
    fluid1.addComponent("water", 1.0e-6);
    fluid1.setMixingRule("classic");
    fluid1.setMultiPhaseCheck(true);

    StreamInterface stream1 = new Stream("Stream1", fluid1);
    stream1.setTemperature(20.0, "C");
    stream1.setPressure(10.0, "bara");
    stream1.setFlowRate(1e-60, "kg/hr");
    stream1.run();
    operations.add(stream1);

    Heater heater1 = new Heater("Heater", stream1);
    heater1.setOutTemperature(30.0, "C");
    heater1.run();
    operations.add(heater1);
  }

  @Test
  void testSingleStreamMixer() {

    ProcessSystem operations = new ProcessSystem();

    SystemInterface fluid1 = new neqsim.thermo.system.SystemPrEos(273.15 + 20, 10.0);
    fluid1.addComponent("methane", 90.0);
    fluid1.addComponent("ethane", 10.0);
    fluid1.addComponent("nC10", 10.0);
    fluid1.addTBPfraction("C10", 10.0, 150.0 / 1000.0, 0.9);
    fluid1.addComponent("water", 10.0e-2);
    fluid1.setMixingRule("classic");
    fluid1.setMultiPhaseCheck(true);

    StreamInterface stream1 = new Stream("Stream1", fluid1);
    stream1.setTemperature(20.0, "C");
    stream1.setPressure(10.0, "bara");
    stream1.setFlowRate(10, "kg/hr");
    stream1.run();
    operations.add(stream1);
    // stream1.getFluid().prettyPrint();

    ThreePhaseSeparator separator1 = new ThreePhaseSeparator("Separator", stream1);
    separator1.run();
    operations.add(separator1);

    Mixer mixer1 = new Mixer("Mixer");
    mixer1.addStream(separator1.getOilOutStream());
    mixer1.run();
    operations.add(mixer1);

    Mixer mixer2 = new Mixer("Mixer2");
    mixer2.addStream(separator1.getWaterOutStream());
    mixer2.run();
    operations.add(mixer2);

    System.out.println(mixer2.getOutletStream().getFlowRate("kg/hr"));
    // mixer2.getOutletStream().getFluid().prettyPrint();
  }

  @Test
  void testEmptyStream22() {
    SystemInterface fluid1 = new neqsim.thermo.system.SystemPrEos(273.15 + 20, 10.0);
    fluid1.addComponent("methane", 90.0);
    fluid1.addComponent("ethane", 10.0);
    fluid1.addComponent("water", 1.0e-6);
    fluid1.setMixingRule("classic");
    fluid1.setMultiPhaseCheck(true);

    SystemInterface fluid2 = new neqsim.thermo.system.SystemPrEos(273.15 + 20, 10.0);
    fluid2.addComponent("methane", 0.0);
    fluid2.addComponent("ethane", 0.0);
    fluid2.addComponent("water", 1.0);
    fluid2.setMixingRule("classic");
    fluid2.setMultiPhaseCheck(true);

    ProcessSystem operations = new ProcessSystem();

    StreamInterface stream1 = new Stream("Stream1", fluid1);
    stream1.setTemperature(20.0, "C");
    stream1.setPressure(10.0, "bara");
    stream1.run();
    operations.add(stream1);

    StreamInterface stream3 = stream1.clone("Stream3");

    StreamInterface stream2 = new Stream("Stream2", fluid2);
    stream2.setFlowRate(1.0e-30, "kg/hr");
    stream2.run();
    operations.add(stream2);

    ThreePhaseSeparator separator1 = new ThreePhaseSeparator("Separator", stream1);
    separator1.run();
    operations.add(separator1);

    Pump pump1 = new Pump("pump1", separator1.getLiquidOutStream());
    pump1.setOutletPressure(30.0, "bara");
    pump1.run();
    operations.add(pump1);

    Mixer mixer1 = new Mixer("Mixer");
    mixer1.addStream(stream2);
    mixer1.addStream(pump1.getOutletStream());
    mixer1.run();
    operations.add(mixer1);

    Recycle recycle1 = new Recycle("Recycle 1");
    recycle1.addStream(mixer1.getOutletStream());
    recycle1.setOutletStream(stream3);
    recycle1.run();
    operations.add(recycle1);

    stream3.run();

    // stream3.getFluid().prettyPrint();
    // System.out.println("flow rate " + stream3.getFlowRate("kg/hr"));
    // System.out.println(recycle1.isActive());
    operations.run();
  }
}
