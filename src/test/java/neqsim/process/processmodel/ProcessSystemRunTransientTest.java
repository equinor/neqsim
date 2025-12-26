package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.SimulationInterface;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.controllerdevice.ControllerDeviceInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.util.SetPoint;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.measurementdevice.CompressorMonitor;
import neqsim.process.measurementdevice.LevelTransmitter;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.measurementdevice.VolumeFlowTransmitter;
import neqsim.thermo.system.SystemInterface;

public class ProcessSystemRunTransientTest extends neqsim.NeqSimTest {
  private static final org.apache.logging.log4j.Logger logger =
      org.apache.logging.log4j.LogManager.getLogger(ProcessSystemRunTransientTest.class);
  ProcessSystem p;

  @BeforeEach
  public void setUp() {
    p = new ProcessSystem();
  }

  @Test
  public void testGetName() {
    String name = "TestProsess";
    p.setName(name);
    Assertions.assertEquals(name, p.getName());
  }

  @Test
  void testGetTime() {}

  @Test
  void testGetTimeStep() {}

  private SystemInterface getTestSystem() {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 10.00);
    testSystem.addComponent("methane", 0.900);
    testSystem.addComponent("ethane", 0.100);
    testSystem.addComponent("n-heptane", 1.00);
    testSystem.setMixingRule(2);
    return testSystem;
  }

  @Test
  public void testDynamicCalculation() {
    neqsim.thermo.system.SystemInterface testSystem = getTestSystem();

    Stream stream1 = new Stream("Stream1", testSystem);
    stream1.setFlowRate(50.0, "kg/hr");
    stream1.setPressure(10.0, "bara");

    ThrottlingValve valve1 = new ThrottlingValve("valve_1", stream1);
    valve1.setOutletPressure(5.0);
    valve1.setPercentValveOpening(40);

    Separator separator1 = new Separator("sep 1");
    separator1.addStream(valve1.getOutletStream());
    separator1.setInternalDiameter(1.0);
    separator1.setSeparatorLength(2.5);
    separator1.setLiquidLevel(0.5);
    separator1.setCalculateSteadyState(true);

    ThrottlingValve valve2 = new ThrottlingValve("valve_2", separator1.getLiquidOutStream());
    valve2.setOutletPressure(1.0);
    valve2.setPercentValveOpening(50);

    ThrottlingValve valve3 = new ThrottlingValve("valve_3", separator1.getGasOutStream());
    valve3.setOutletPressure(1.0);
    valve3.setPercentValveOpening(50);
    valve3.setMinimumValveOpening(1.0);

    VolumeFlowTransmitter flowTransmitter = new VolumeFlowTransmitter(stream1);
    flowTransmitter.setUnit("kg/hr");
    flowTransmitter.setMaximumValue(100.0);
    flowTransmitter.setMinimumValue(1.0);

    ControllerDeviceInterface flowController = new ControllerDeviceBaseClass();
    flowController.setTransmitter(flowTransmitter);
    flowController.setReverseActing(true);
    flowController.setControllerSetPoint(73.5);
    flowController.setControllerParameters(0.2, 100.0, 0.0);

    p.add(stream1);
    p.add(valve1);
    p.add(separator1);
    p.add(valve2);
    p.add(valve3);
    p.add(flowTransmitter);
    valve1.setController(flowController);
    valve1.setCalculateSteadyState(false);

    p.run();

    // transient behaviour
    p.setTimeStep(20.0);
    for (int i = 0; i < 200; i++) {
      // System.out.println("volume flow " + flowTransmitter.getMeasuredValue() + " valve opening "
      // + valve1.getPercentValveOpening() + " pressure "
      // + separator1.getGasOutStream().getPressure());
      p.runTransient();
      for (SimulationInterface sim : p.getUnitOperations()) {
        assertEquals(sim.getCalculationIdentifier(), p.getCalculationIdentifier());
      }
    }
    assertEquals(73.49473569951421, flowTransmitter.getMeasuredValue(), 10.0);
  }

  @Test
  public void testDynamicCalculation2() {
    neqsim.thermo.system.SystemInterface testSystem2 =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 10.00);
    testSystem2.addComponent("methane", 1.1);
    testSystem2.addComponent("n-heptane", 1.001);
    testSystem2.setMixingRule(2);

    neqsim.thermo.system.SystemInterface testSystem3 =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 10.00);
    testSystem3.addComponent("methane", 1.1);
    testSystem3.addComponent("n-heptane", 0.001);
    testSystem3.setMixingRule(2);

    Stream stream1 = new Stream("Stream1", testSystem2);
    stream1.setFlowRate(1090.0, "kg/hr");
    stream1.setPressure(10.0, "bara");
    stream1.setTemperature(25.0, "C");

    Stream streamPurge = new Stream("StreamPurge", testSystem3);
    streamPurge.setFlowRate(50.0, "kg/hr");
    streamPurge.setPressure(10.0, "bara");
    streamPurge.setTemperature(25.0, "C");

    ThrottlingValve valve1 = new ThrottlingValve("valve_1", stream1);
    valve1.setOutletPressure(7.0);
    valve1.setPercentValveOpening(50);
    valve1.setCalculateSteadyState(false);

    ThrottlingValve valvePurge = new ThrottlingValve("valve_purge", streamPurge);
    valvePurge.setOutletPressure(7.0);
    valvePurge.setPercentValveOpening(50);
    valvePurge.setCalculateSteadyState(false);

    Separator separator1 = new Separator("separator_1");
    separator1.addStream(valve1.getOutletStream());
    separator1.addStream(valvePurge.getOutletStream());
    separator1.setCalculateSteadyState(false);
    separator1.setSeparatorLength(3.0);
    separator1.setInternalDiameter(0.8);
    separator1.setLiquidLevel(0.5);

    ThrottlingValve valve2 = new ThrottlingValve("valve_2", separator1.getLiquidOutStream());
    valve2.setOutletPressure(1.0);
    valve2.setPercentValveOpening(50);
    valve2.setCalculateSteadyState(false);
    valve2.setMinimumValveOpening(1.0);

    ThrottlingValve valve3 = new ThrottlingValve("valve_3", separator1.getGasOutStream());
    valve3.setOutletPressure(1.0);
    valve3.setPercentValveOpening(10);
    valve3.setCalculateSteadyState(false);
    valve3.setMinimumValveOpening(1.0);

    LevelTransmitter separatorLevelTransmitter =
        new LevelTransmitter("separatorLevelTransmitter1", separator1);
    separatorLevelTransmitter.setMaximumValue(0.8);
    separatorLevelTransmitter.setMinimumValue(0.2);

    ControllerDeviceInterface separatorLevelController = new ControllerDeviceBaseClass();
    separatorLevelController.setReverseActing(false);
    separatorLevelController.setTransmitter(separatorLevelTransmitter);
    separatorLevelController.setControllerSetPoint(0.45);
    separatorLevelController.setControllerParameters(2.0, 500.0, 0.0);

    PressureTransmitter separatorPressureTransmitter =
        new PressureTransmitter(separator1.getGasOutStream());
    separatorPressureTransmitter.setUnit("bar");
    separatorPressureTransmitter.setMaximumValue(10.0);
    separatorPressureTransmitter.setMinimumValue(1.0);

    ControllerDeviceInterface separatorPressureController = new ControllerDeviceBaseClass();
    separatorPressureController.setTransmitter(separatorPressureTransmitter);
    separatorPressureController.setReverseActing(false);
    separatorPressureController.setControllerSetPoint(7.0);
    separatorPressureController.setControllerParameters(1, 100, 0.0);

    p.add(stream1);
    p.add(streamPurge);
    p.add(valve1);
    p.add(valvePurge);
    p.add(separator1);
    p.add(valve2);
    p.add(valve3);

    // add transmitters
    p.add(separatorLevelTransmitter);
    valve2.setController(separatorLevelController);

    p.add(separatorPressureTransmitter);
    valve3.setController(separatorPressureController);

    p.run();
    for (SimulationInterface sim : p.getUnitOperations()) {
      assertEquals(sim.getCalculationIdentifier(), p.getCalculationIdentifier());
    }

    // p.displayResult();
    p.setTimeStep(10.0);
    for (int i = 0; i < 250; i++) {
      // System.out.println("pressure " + separator1.getGasOutStream().getPressure() + " flow "
      // + separator1.getGasOutStream().getFlowRate("kg/hr") + " sepr height "
      // + separatorLevelTransmitter.getMeasuredValue() + "valve2 opening "
      // + valve2.getPercentValveOpening() + "valve3 opening " + valve3.getPercentValveOpening());
      p.runTransient();
      for (SimulationInterface sim : p.getUnitOperations()) {
        assertEquals(p.getCalculationIdentifier(), sim.getCalculationIdentifier());
      }
    }
    assertEquals(0.4470214843750001, separatorLevelTransmitter.getMeasuredValue(), 0.1);
  }

  @Test
  public void testDynamicCompressor() {
    neqsim.thermo.system.SystemInterface testSystem2 =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 10.00);
    testSystem2.addComponent("methane", 1.1);
    testSystem2.addComponent("ethane", 0.1);
    testSystem2.setMixingRule(2);

    Stream stream1 = new Stream("Stream1", testSystem2);
    stream1.setFlowRate(501.0, "kg/hr");
    stream1.setPressure(100.0, "bara");
    stream1.setTemperature(55.0, "C");

    ThrottlingValve valve1 = new ThrottlingValve("valve_1", stream1);
    valve1.setOutletPressure(50.0);
    valve1.setPercentValveOpening(50);
    valve1.setCalculateSteadyState(false);

    Separator separator1 = new Separator("separator_1");
    separator1.addStream(valve1.getOutletStream());
    separator1.setCalculateSteadyState(false);
    separator1.setSeparatorLength(3.0);
    separator1.setInternalDiameter(0.8);
    separator1.setLiquidLevel(0.0);

    Compressor compressor1 = new Compressor("comp1", separator1.getGasOutStream());
    compressor1.setCalculateSteadyState(false);
    compressor1.setOutletPressure(100.0);

    Separator separator2 = new Separator("separator_2");
    separator2.addStream(compressor1.getOutletStream());
    separator2.setCalculateSteadyState(false);
    separator2.setSeparatorLength(3.0);
    separator2.setInternalDiameter(0.8);
    separator2.setLiquidLevel(0.0);

    ThrottlingValve valve2 = new ThrottlingValve("valve_2", separator2.getGasOutStream());
    valve2.setOutletPressure(50.0);
    valve2.setPercentValveOpening(50);
    valve2.setCalculateSteadyState(false);

    p.add(stream1);
    p.add(valve1);
    p.add(separator1);
    p.add(compressor1);
    p.add(separator2);
    p.add(valve2);

    p.run();
    /*
     * System.out.println(" speed " + compressor1.getSpeed() + "feed flow " +
     * stream1.getFlowRate("kg/hr") + " compressor flow rate " +
     * compressor1.getInletStream().getFlowRate("kg/hr") + " out flow " +
     * valve2.getOutletStream().getFlowRate("kg/hr") + " delta p " +
     * (compressor1.getOutletStream().getPressure() - compressor1.getInletStream().getPressure()) +
     * " pres inn " + compressor1.getInletStream().getPressure() + " pres out " +
     * compressor1.getOutletStream().getPressure());
     */
    assertEquals(100.0, compressor1.getOutletStream().getPressure(), 0.01);

    neqsim.process.equipment.compressor.CompressorChartGenerator compchartgenerator =
        new neqsim.process.equipment.compressor.CompressorChartGenerator(compressor1);
    compressor1.setCompressorChart(compchartgenerator.generateCompressorChart("normal"));
    compressor1.getCompressorChart().setUseCompressorChart(true);
    // compressor1.setCalculateSteadyState(true);
    p.run();
    /*
     * System.out.println(" speed " + compressor1.getSpeed() + "feed flow " +
     * stream1.getFlowRate("kg/hr") + " compressor flow rate " +
     * compressor1.getInletStream().getFlowRate("kg/hr") + " out flow " +
     * valve2.getOutletStream().getFlowRate("kg/hr") + " delta p " +
     * (compressor1.getOutletStream().getPressure() - compressor1.getInletStream().getPressure()) +
     * " pres inn " + compressor1.getInletStream().getPressure() + " pres out " +
     * compressor1.getOutletStream().getPressure());
     */

    assertEquals(102.7, compressor1.getOutletStream().getPressure(), 2.01);
    assertEquals(50.0, separator1.getGasOutStream().getPressure(), 0.01);
    // System.out.println("speed " + compressor1.getSpeed());
    p.setTimeStep(10.0);
    p.runTransient();
    /*
     * System.out.println(" speed " + compressor1.getSpeed() + "feed flow " +
     * stream1.getFlowRate("kg/hr") + " compressor flow rate " +
     * compressor1.getInletStream().getFlowRate("kg/hr") + " out flow " +
     * valve2.getOutletStream().getFlowRate("kg/hr") + " delta p " +
     * (compressor1.getOutletStream().getPressure() - compressor1.getInletStream().getPressure()) +
     * " pres inn " + compressor1.getInletStream().getPressure() + " pres out " +
     * compressor1.getOutletStream().getPressure());
     */
    compressor1.setSpeed(compressor1.getSpeed() + 500);
    for (int i = 0; i < 200; i++) {
      /*
       * System.out.println("time " + i + " speed " + compressor1.getSpeed() + "feed flow " +
       * stream1.getFlowRate("kg/hr") + " compressor flow rate " +
       * compressor1.getInletStream().getFlowRate("kg/hr") + " out flow " +
       * valve2.getOutletStream().getFlowRate("kg/hr") + " delta p " +
       * (compressor1.getOutletStream().getPressure() - compressor1.getInletStream().getPressure())
       * + " pres inn " + compressor1.getInletStream().getPressure() + " pres out " +
       * compressor1.getOutletStream().getPressure());
       */
      p.runTransient();
    }

    compressor1.setSpeed(compressor1.getSpeed() - 500);
    for (int i = 0; i < 200; i++) {
      /*
       * System.out.println("time " + i + " speed " + compressor1.getSpeed() + "feed flow " +
       * stream1.getFlowRate("kg/hr") + " compressor flow rate " +
       * compressor1.getInletStream().getFlowRate("kg/hr") + " out flow " +
       * valve2.getOutletStream().getFlowRate("kg/hr") + " delta p " +
       * (compressor1.getOutletStream().getPressure() - compressor1.getInletStream().getPressure())
       * + " pres inn " + compressor1.getInletStream().getPressure() + " pres out " +
       * compressor1.getOutletStream().getPressure());
       */
      p.runTransient();
    }
  }

  @Test
  public void testDynamicCompressor22() {
    neqsim.thermo.system.SystemInterface testSystem2 =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 10.00);
    testSystem2.addComponent("methane", 1.1);
    testSystem2.addComponent("ethane", 0.1);
    testSystem2.setMixingRule(2);

    Stream stream1 = new Stream("Stream1", testSystem2);
    stream1.setFlowRate(501.0, "kg/hr");
    stream1.setPressure(100.0, "bara");
    stream1.setTemperature(55.0, "C");

    ThrottlingValve valve1 = new ThrottlingValve("valve_1", stream1);
    valve1.setOutletPressure(50.0);
    valve1.setPercentValveOpening(50);
    valve1.setCalculateSteadyState(false);

    Compressor compressor1 = new Compressor("comp", valve1.getOutletStream());
    compressor1.setCalculateSteadyState(false);
    compressor1.setOutletPressure(100.0);

    ThrottlingValve valve2 = new ThrottlingValve("valve_2", compressor1.getOutletStream());
    valve2.setOutletPressure(50.0);
    valve2.setPercentValveOpening(50);
    valve2.setCalculateSteadyState(false);

    p.add(stream1);
    p.add(valve1);
    p.add(compressor1);
    p.add(valve2);

    p.run();
    /*
     * System.out.println(" steady staate no compressor curves....."); System.out.println(" speed "
     * + compressor1.getSpeed() + "feed flow " + stream1.getFlowRate("kg/hr") +
     * " compressor flow rate " + compressor1.getInletStream().getFlowRate("kg/hr") + " out flow " +
     * valve2.getOutletStream().getFlowRate("kg/hr") + " delta p " +
     * (compressor1.getOutletStream().getPressure() - compressor1.getInletStream().getPressure()) +
     * " pres inn " + compressor1.getInletStream().getPressure() + " pres out " +
     * compressor1.getOutletStream().getPressure());
     */
    assertEquals(100.0, compressor1.getOutletStream().getPressure(), 0.01);

    // System.out.println("steady state with compressor curves.....");
    neqsim.process.equipment.compressor.CompressorChartGenerator compchartgenerator =
        new neqsim.process.equipment.compressor.CompressorChartGenerator(compressor1);
    compressor1.setCompressorChart(compchartgenerator.generateCompressorChart("normal"));
    compressor1.getCompressorChart().setUseCompressorChart(true);
    // compressor1.setCalculateSteadyState(true);
    p.run();
    /*
     * System.out.println(" speed " + compressor1.getSpeed() + "feed flow " +
     * stream1.getFlowRate("kg/hr") + " compressor flow rate " +
     * compressor1.getInletStream().getFlowRate("kg/hr") + " out flow " +
     * valve2.getOutletStream().getFlowRate("kg/hr") + " delta p " +
     * (compressor1.getOutletStream().getPressure() - compressor1.getInletStream().getPressure()) +
     * " pres inn " + compressor1.getInletStream().getPressure() + " pres out " +
     * compressor1.getOutletStream().getPressure());
     */

    // System.out.println("dynamic first step state with compressor curves.....");
    p.setTimeStep(1.0);
    p.runTransient();
    /*
     * System.out.println(" speed " + compressor1.getSpeed() + "feed flow " +
     * stream1.getFlowRate("kg/hr") + " compressor flow rate " +
     * compressor1.getInletStream().getFlowRate("kg/hr") + " out flow " +
     * valve2.getOutletStream().getFlowRate("kg/hr") + " delta p " +
     * (compressor1.getOutletStream().getPressure() - compressor1.getInletStream().getPressure()) +
     * " pres inn " + compressor1.getInletStream().getPressure() + " pres out " +
     * compressor1.getOutletStream().getPressure());
     */

    // System.out.println("dynamic seccond step state with compressor curves.....");
    p.runTransient();
    /*
     * System.out.println(" speed " + compressor1.getSpeed() + "feed flow " +
     * stream1.getFlowRate("kg/hr") + " compressor flow rate " +
     * compressor1.getInletStream().getFlowRate("kg/hr") + " out flow " +
     * valve2.getOutletStream().getFlowRate("kg/hr") + " delta p " +
     * (compressor1.getOutletStream().getPressure() - compressor1.getInletStream().getPressure()) +
     * " pres inn " + compressor1.getInletStream().getPressure() + " pres out " +
     * compressor1.getOutletStream().getPressure());
     */
  }

  @Test
  public void testDynamicCompressorSpeedControl() {
    neqsim.thermo.system.SystemInterface testSystem2 =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 10.00);
    testSystem2.addComponent("methane", 1.1);
    testSystem2.addComponent("ethane", 0.1);
    testSystem2.setMixingRule(2);

    Stream stream1 = new Stream("Stream1", testSystem2);
    stream1.setFlowRate(501.0, "kg/hr");
    stream1.setPressure(100.0, "bara");
    stream1.setTemperature(55.0, "C");

    ThrottlingValve valve1 = new ThrottlingValve("valve_1", stream1);
    valve1.setOutletPressure(50.0);
    valve1.setPercentValveOpening(50);
    valve1.setCalculateSteadyState(false);

    Separator separator1 = new Separator("separator_1");
    separator1.addStream(valve1.getOutletStream());
    separator1.setCalculateSteadyState(false);
    separator1.setSeparatorLength(3.0);
    separator1.setInternalDiameter(0.8);
    separator1.setLiquidLevel(0.0);

    Compressor compressor1 = new Compressor("comp1", separator1.getGasOutStream());
    compressor1.setCalculateSteadyState(false);
    compressor1.setOutletPressure(100.0);

    Separator separator2 = new Separator("separator_2");
    separator2.addStream(compressor1.getOutletStream());
    separator2.setCalculateSteadyState(false);
    separator2.setSeparatorLength(3.0);
    separator2.setInternalDiameter(0.8);
    separator2.setLiquidLevel(0.0);

    ThrottlingValve valve2 = new ThrottlingValve("valve_2", separator2.getGasOutStream());
    valve2.setOutletPressure(50.0);
    valve2.setPercentValveOpening(50);
    valve2.setCalculateSteadyState(false);

    PressureTransmitter separatorPressureTransmitter =
        new PressureTransmitter(separator2.getGasOutStream());

    ControllerDeviceInterface speedController = new ControllerDeviceBaseClass();
    speedController.setReverseActing(true);
    speedController.setTransmitter(separatorPressureTransmitter);
    speedController.setControllerSetPoint(100.0);
    speedController.setControllerParameters(0.1, 500.0, 0.0);

    p.add(stream1);
    p.add(valve1);
    p.add(separator1);
    p.add(compressor1);
    p.add(separator2);
    p.add(separatorPressureTransmitter);
    p.add(valve2);
    compressor1.setController(speedController);

    p.run();
    /*
     * System.out.println(" speed " + compressor1.getSpeed() + "feed flow " +
     * stream1.getFlowRate("kg/hr") + " compressor flow rate " +
     * compressor1.getInletStream().getFlowRate("kg/hr") + " out flow " +
     * valve2.getOutletStream().getFlowRate("kg/hr") + " delta p " +
     * (compressor1.getOutletStream().getPressure() - compressor1.getInletStream().getPressure()) +
     * " pres inn " + compressor1.getInletStream().getPressure() + " pres out " +
     * compressor1.getOutletStream().getPressure());
     */
    assertEquals(100.0, compressor1.getOutletStream().getPressure(), 0.01);

    neqsim.process.equipment.compressor.CompressorChartGenerator compchartgenerator =
        new neqsim.process.equipment.compressor.CompressorChartGenerator(compressor1);
    compressor1.setCompressorChart(compchartgenerator.generateCompressorChart("normal"));
    compressor1.getCompressorChart().setUseCompressorChart(true);
    // compressor1.setCalculateSteadyState(true);
    p.run();
    /*
     * System.out.println(" speed " + compressor1.getSpeed() + "feed flow " +
     * stream1.getFlowRate("kg/hr") + " compressor flow rate " +
     * compressor1.getInletStream().getFlowRate("kg/hr") + " out flow " +
     * valve2.getOutletStream().getFlowRate("kg/hr") + " delta p " +
     * (compressor1.getOutletStream().getPressure() - compressor1.getInletStream().getPressure()) +
     * " pres inn " + compressor1.getInletStream().getPressure() + " pres out " +
     * compressor1.getOutletStream().getPressure());
     */

    assertEquals(102.7, compressor1.getOutletStream().getPressure(), 2.01);
    assertEquals(50.0, separator1.getGasOutStream().getPressure(), 0.01);
    // System.out.println("speed " + compressor1.getSpeed());
    p.setTimeStep(1.0);
    p.runTransient();
    /*
     * System.out.println(" speed " + compressor1.getSpeed() + "feed flow " +
     * stream1.getFlowRate("kg/hr") + " compressor flow rate " +
     * compressor1.getInletStream().getFlowRate("kg/hr") + " out flow " +
     * valve2.getOutletStream().getFlowRate("kg/hr") + " delta p " +
     * (compressor1.getOutletStream().getPressure() - compressor1.getInletStream().getPressure()) +
     * " pres inn " + compressor1.getInletStream().getPressure() + " pres out " +
     * compressor1.getOutletStream().getPressure());
     */
    // compressor1.setSpeed(compressor1.getSpeed() + 500);
    for (int i = 0; i < 200; i++) {
      /*
       * System.out.println("time " + i + " speed " + compressor1.getSpeed() + "feed flow " +
       * stream1.getFlowRate("kg/hr") + " compressor flow rate " +
       * compressor1.getInletStream().getFlowRate("kg/hr") + " out flow " +
       * valve2.getOutletStream().getFlowRate("kg/hr") + " delta p " +
       * (compressor1.getOutletStream().getPressure() - compressor1.getInletStream().getPressure())
       * + " pres inn " + compressor1.getInletStream().getPressure() + " pres out " +
       * compressor1.getOutletStream().getPressure());
       */
      p.runTransient();
    }
    speedController.setControllerSetPoint(120.0);
    for (int i = 0; i < 500; i++) {
      /*
       * System.out.println("time " + i + " speed " + compressor1.getSpeed() + "feed flow " +
       * stream1.getFlowRate("kg/hr") + " compressor flow rate " +
       * compressor1.getInletStream().getFlowRate("kg/hr") + " out flow " +
       * valve2.getOutletStream().getFlowRate("kg/hr") + " delta p " +
       * (compressor1.getOutletStream().getPressure() - compressor1.getInletStream().getPressure())
       * + " pres inn " + compressor1.getInletStream().getPressure() + " pres out " +
       * compressor1.getOutletStream().getPressure());
       */
      p.runTransient();
    }
  }

  @Test
  public void testSeparatorLevelRegulationExtremes() {
    SystemInterface wellfluid = new neqsim.thermo.system.SystemSrkEos(273.15 + 40.0, 9.0);
    wellfluid.addComponent("CO2", 1.5870);
    wellfluid.addComponent("methane", 52.51);
    wellfluid.addComponent("ethane", 6.24);
    wellfluid.addComponent("propane", 4.23);
    wellfluid.addComponent("i-butane", 0.855);
    wellfluid.addComponent("n-butane", 2.213);
    wellfluid.addComponent("i-pentane", 1.124);
    wellfluid.addComponent("n-pentane", 1.271);
    wellfluid.addComponent("n-hexane", 2.289);
    wellfluid.addTBPfraction("C7+_cut1", 0.8501, 108.47 / 1000.0, 0.7411);
    wellfluid.addTBPfraction("C7+_cut2", 1.2802, 120.4 / 1000.0, 0.755);
    wellfluid.addTBPfraction("C7+_cut3", 1.6603, 133.64 / 1000.0, 0.7695);
    wellfluid.addTBPfraction("C7+_cut4", 6.5311, 164.70 / 1000.0, 0.799);
    wellfluid.addTBPfraction("C7+_cut5", 6.3311, 215.94 / 1000.0, 0.8387);
    wellfluid.addTBPfraction("C7+_cut6", 4.9618, 273.34 / 1000.0, 0.8754);
    wellfluid.addTBPfraction("C7+_cut7", 2.9105, 334.92 / 1000.0, 0.90731);
    wellfluid.addTBPfraction("C7+_cut8", 3.0505, 412.79 / 1000.0, 0.9575);
    wellfluid.setMixingRule(2);

    Stream wellStream = new Stream("well stream", wellfluid);
    wellStream.setFlowRate(400.0, "kg/hr");
    wellStream.setPressure(9.0, "bara");
    wellStream.setTemperature(40.0, "C");

    ThrottlingValve inletValve = new ThrottlingValve("LCV-00", wellStream);
    inletValve.setPercentValveOpening(80.0);
    inletValve.setOutletPressure(8.0);
    inletValve.setCalculateSteadyState(false);

    Separator separator = new Separator("V-001", inletValve.getOutletStream());
    separator.setCalculateSteadyState(false);
    separator.setOrientation("vertical");
    separator.setSeparatorLength(5.0);
    separator.setInternalDiameter(1.0);
    separator.setLiquidLevel(0.2);

    ThrottlingValve liquidValve = new ThrottlingValve("LCV-001", separator.getLiquidOutStream());
    liquidValve.setPercentValveOpening(25.0);
    liquidValve.setOutletPressure(2.0);
    liquidValve.setCalculateSteadyState(false);
    liquidValve.setMinimumValveOpening(1.0);

    ThrottlingValve gasValve = new ThrottlingValve("PCV-001", separator.getGasOutStream());
    gasValve.setPercentValveOpening(55.0);
    gasValve.setOutletPressure(2.0);
    gasValve.setCalculateSteadyState(false);
    gasValve.setMinimumValveOpening(0.01);

    LevelTransmitter levelTransmitter = new LevelTransmitter(separator);
    levelTransmitter.setMaximumValue(0.99);
    levelTransmitter.setMinimumValue(0.01);

    ControllerDeviceBaseClass levelController = new ControllerDeviceBaseClass();
    levelController.setTransmitter(levelTransmitter);
    levelController.setReverseActing(false);
    levelController.setControllerSetPoint(0.75);
    levelController.setControllerParameters(20.0, 300.0, 0.0);
    levelController.setOutputLimits(0.0, 100.0);

    PressureTransmitter pressureTransmitter = new PressureTransmitter(separator.getGasOutStream());
    pressureTransmitter.setUnit("bar");
    pressureTransmitter.setMaximumValue(50.0);
    pressureTransmitter.setMinimumValue(1.0);

    ControllerDeviceBaseClass pressureController = new ControllerDeviceBaseClass();
    pressureController.setTransmitter(pressureTransmitter);
    pressureController.setReverseActing(false);
    pressureController.setControllerSetPoint(7.0);
    pressureController.setControllerParameters(10.0, 2000.0, 0.0);
    pressureController.setOutputLimits(0.0, 100.0);

    ProcessSystem process = new ProcessSystem("level regulation");
    process.add(wellStream);
    process.add(inletValve);
    process.add(separator);
    process.add(liquidValve);
    process.add(gasValve);
    process.add(levelTransmitter);
    process.add(pressureTransmitter);

    liquidValve.setController(levelController);
    gasValve.setController(pressureController);

    process.run();
    process.setTimeStep(50.0);

    for (int i = 0; i < 200; i++) {
      process.runTransient();
    }
    double highLevel = separator.getLiquidLevel();
    assertTrue(highLevel > 0.30);

    levelController.setControllerSetPoint(0.15);
    for (int i = 0; i < 200; i++) {
      process.runTransient();
    }
    double lowLevel = separator.getLiquidLevel();
    assertTrue(lowLevel < highLevel - 0.10);
  }

  @Test
  public void testAntiSurgeControl() {
    neqsim.thermo.system.SystemInterface testSystem2 =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 10.00);
    testSystem2.addComponent("methane", 1.1);
    testSystem2.addComponent("ethane", 0.1);
    testSystem2.setMixingRule(2);

    Stream stream1 = new Stream("Stream1", testSystem2);
    stream1.setFlowRate(500.0, "kg/hr");
    stream1.setPressure(100.0, "bara");
    stream1.setTemperature(55.0, "C");

    ThrottlingValve valve1 = new ThrottlingValve("valve_1", stream1);
    valve1.setOutletPressure(50.0);
    valve1.setPercentValveOpening(20);
    valve1.setCalculateSteadyState(false);

    Stream resycstream = stream1.clone("recycle stream");
    resycstream.setFlowRate(0.01, "kg/hr");

    Separator separator1 = new Separator("separator_1");
    separator1.addStream(valve1.getOutletStream());
    separator1.addStream(resycstream);
    separator1.setCalculateSteadyState(false);
    separator1.setSeparatorLength(3.0);
    separator1.setInternalDiameter(0.8);
    separator1.setLiquidLevel(0.0);

    Compressor compressor1 = new Compressor("comp", separator1.getGasOutStream());
    compressor1.setCalculateSteadyState(false);
    compressor1.setOutletPressure(100.0);
    CompressorMonitor surgemonitor = new CompressorMonitor(compressor1);
    surgemonitor.setMaximumValue(5.0);
    surgemonitor.setMinimumValue(-5.0);

    Cooler aftercooler = new Cooler("after cooler", compressor1.getOutletStream());
    aftercooler.setOutTemperature(30.0, "C");
    aftercooler.setCalculateSteadyState(false);

    Separator separator2 = new Separator("separator_2");
    separator2.addStream(aftercooler.getOutletStream());
    separator2.setCalculateSteadyState(false);
    separator2.setSeparatorLength(3.0);
    separator2.setInternalDiameter(0.5);
    separator2.setLiquidLevel(0.0);

    Stream gasfromsep2 = new Stream("gas from sep", separator2.getGasOutStream());

    Splitter splitter = new Splitter("splitter1", gasfromsep2);
    splitter.setSplitFactors(new double[] {0.99, 0.01});
    splitter.setCalculateSteadyState(false);

    ThrottlingValve recycleValve =
        new ThrottlingValve("anti surge valve", splitter.getSplitStream(1));
    recycleValve.setPressure(50.0);
    recycleValve.setCalculateSteadyState(false);
    recycleValve.setMinimumValveOpening(1.0);
    recycleValve.setPercentValveOpening(10);

    SetPoint pressureset =
        new SetPoint("HP pump set", recycleValve, "pressure", separator1.getGasOutStream());

    Recycle recycle = new Recycle("recycle 1");
    recycle.addStream(recycleValve.getOutletStream());
    recycle.setOutletStream(resycstream);
    recycle.setFlowTolerance(1e-4);

    ThrottlingValve valve2 = new ThrottlingValve("valve_2", splitter.getSplitStream(0));
    valve2.setOutletPressure(50.0);
    valve2.setPercentValveOpening(50);
    valve2.setCalculateSteadyState(false);
    valve2.setMinimumValveOpening(1.0);

    ControllerDeviceInterface surgeController = new ControllerDeviceBaseClass();
    surgeController.setReverseActing(true);
    surgeController.setTransmitter(surgemonitor);
    surgeController.setControllerSetPoint(0.0);
    surgeController.setControllerParameters(1.0, 200.0, 10.0);
    surgeController.setActive(true);

    p.add(stream1);
    p.add(resycstream);
    p.add(valve1);
    p.add(separator1);
    p.add(compressor1);
    p.add(surgemonitor);
    p.add(aftercooler);
    p.add(separator2);
    p.add(gasfromsep2);
    p.add(splitter);
    p.add(recycleValve);
    p.add(pressureset);
    p.add(recycle);
    p.add(valve2);
    recycleValve.setController(surgeController);

    p.run();
    assertEquals(100.0, compressor1.getOutletStream().getPressure(), 0.01);
    recycleValve.setCv(valve2.getCv());
    /*
     * System.out.println(" speed " + compressor1.getSpeed() + "feed flow " +
     * stream1.getFlowRate("kg/hr") + " compressor flow rate " +
     * compressor1.getInletStream().getFlowRate("kg/hr") + " out flow " +
     * valve2.getOutletStream().getFlowRate("kg/hr") + " delta p " +
     * (compressor1.getOutletStream().getPressure() - compressor1.getInletStream().getPressure()) +
     * " pres inn " + compressor1.getInletStream().getPressure() + " pres out " +
     * compressor1.getOutletStream().getPressure() + " distancetosurge ");
     */
    neqsim.process.equipment.compressor.CompressorChartGenerator compchartgenerator =
        new neqsim.process.equipment.compressor.CompressorChartGenerator(compressor1);
    compressor1.setCompressorChart(compchartgenerator.generateCompressorChart("normal"));
    compressor1.getCompressorChart().setUseCompressorChart(true);
    p.run();
    /*
     * System.out.println(" speed " + compressor1.getSpeed() + "feed flow " +
     * stream1.getFlowRate("kg/hr") + " compressor flow rate " +
     * compressor1.getInletStream().getFlowRate("kg/hr") + " out flow " +
     * valve2.getOutletStream().getFlowRate("kg/hr") + " delta p " +
     * (compressor1.getOutletStream().getPressure() - compressor1.getInletStream().getPressure()) +
     * " pres inn " + compressor1.getInletStream().getPressure() + " pres out " +
     * compressor1.getOutletStream().getPressure() + " distancetosurge " +
     * surgemonitor.getMeasuredValue("distance to surge") + " antisurgeflow " +
     * recycleValve.getOutletStream().getFlowRate("kg/hr") + " antisurgevalveopening " +
     * recycleValve.getPercentValveOpening() + " compressorouttemperature " +
     * compressor1.getOutStream().getTemperature("C") + " power " + compressor1.getPower("kW"));
     */

    // System.out.println("speed " + compressor1.getSpeed());
    p.setTimeStep(1.0);

    recycleValve.setPercentValveOpening(1.0);
    valve2.setPercentValveOpening(100.0);

    for (int i = 0; i < 100; i++) {
      /*
       * System.out.println("time " + i + " speed " + compressor1.getSpeed() + "feed flow " +
       * stream1.getFlowRate("kg/hr") + " compressor flow rate " +
       * compressor1.getInletStream().getFlowRate("kg/hr") + " out flow " +
       * valve2.getOutletStream().getFlowRate("kg/hr") + " delta p " +
       * (compressor1.getOutletStream().getPressure() - compressor1.getInletStream().getPressure())
       * + " pres inn " + compressor1.getInletStream().getPressure() + " pres out " +
       * compressor1.getOutletStream().getPressure() + " distancetosurge " +
       * surgemonitor.getMeasuredValue("distance to surge") + " antisurgeflow " +
       * recycleValve.getOutletStream().getFlowRate("kg/hr") + " antisurgevalveopening " +
       * recycleValve.getPercentValveOpening() + " compressorouttemperature " +
       * compressor1.getOutStream().getTemperature("C") + " surgeflow " +
       * compressor1.getCompressorChart().getSurgeCurve()
       * .getSurgeFlow(compressor1.getPolytropicFluidHead()) + " compressor flow rate " +
       * compressor1.getInletStream().getFlowRate("m3/hr") + " fluid head " +
       * compressor1.getPolytropicFluidHead() + " power " + compressor1.getPower("kW"));
       */
      p.runTransient();
    }

    valve1.setPercentValveOpening(1.0);
    valve2.setPercentValveOpening(1.0);

    for (int i = 0; i < 100; i++) {
      /*
       * System.out.println("time " + i + " speed " + compressor1.getSpeed() + "feed flow " +
       * stream1.getFlowRate("kg/hr") + " compressor flow rate " +
       * compressor1.getInletStream().getFlowRate("kg/hr") + " out flow " +
       * valve2.getOutletStream().getFlowRate("kg/hr") + " delta p " +
       * (compressor1.getOutletStream().getPressure() - compressor1.getInletStream().getPressure())
       * + " pres inn " + compressor1.getInletStream().getPressure() + " pres out " +
       * compressor1.getOutletStream().getPressure() + " distancetosurge " +
       * surgemonitor.getMeasuredValue("distance to surge") + " antisurgeflow " +
       * recycleValve.getOutletStream().getFlowRate("kg/hr") + " antisurgevalveopening " +
       * recycleValve.getPercentValveOpening() + " compressorouttemperature " +
       * compressor1.getOutStream().getTemperature("C") + " surgeflow " +
       * compressor1.getCompressorChart().getSurgeCurve()
       * .getSurgeFlow(compressor1.getPolytropicFluidHead()) + " compressor flow rate " +
       * compressor1.getInletStream().getFlowRate("m3/hr") + " fluid head " +
       * compressor1.getPolytropicFluidHead() + " power " + compressor1.getPower("kW"));
       */
      p.runTransient();
    }

    valve1.setPercentValveOpening(50.0);
    valve2.setPercentValveOpening(50.0);

    for (int i = 0; i < 100; i++) {
      /*
       * System.out.println("time " + i + " speed " + compressor1.getSpeed() + "feed flow " +
       * stream1.getFlowRate("kg/hr") + " compressor flow rate " +
       * compressor1.getInletStream().getFlowRate("kg/hr") + " out flow " +
       * valve2.getOutletStream().getFlowRate("kg/hr") + " delta p " +
       * (compressor1.getOutletStream().getPressure() - compressor1.getInletStream().getPressure())
       * + " pres inn " + compressor1.getInletStream().getPressure() + " pres out " +
       * compressor1.getOutletStream().getPressure() + " distancetosurge " +
       * surgemonitor.getMeasuredValue("distance to surge") + " antisurgeflow " +
       * recycleValve.getOutletStream().getFlowRate("kg/hr") + " antisurgevalveopening " +
       * recycleValve.getPercentValveOpening() + " compressorouttemperature " +
       * compressor1.getOutStream().getTemperature("C") + " surgeflow " +
       * compressor1.getCompressorChart().getSurgeCurve()
       * .getSurgeFlow(compressor1.getPolytropicFluidHead()) + " compressor flow rate " +
       * compressor1.getInletStream().getFlowRate("m3/hr") + " fluid head " +
       * compressor1.getPolytropicFluidHead() + " power " + compressor1.getPower("kW"));
       */

      p.runTransient();
    }
  }

  @Test
  public void testValveRegulator() {
    neqsim.thermo.system.SystemSrkEos fluid1 =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 10.00);
    fluid1.addComponent("methane", 0.900);
    fluid1.addComponent("ethane", 0.100);
    fluid1.addComponent("n-heptane", 1.00);
    fluid1.setMixingRule("classic");

    neqsim.process.equipment.stream.Stream stream1 =
        new neqsim.process.equipment.stream.Stream("Stream1", fluid1);
    stream1.setFlowRate(510.0, "kg/hr");
    stream1.setPressure(10.0, "bara");
    stream1.setCalculateSteadyState(true);

    neqsim.process.equipment.valve.ThrottlingValve valve1 =
        new neqsim.process.equipment.valve.ThrottlingValve("valve_1", stream1);
    valve1.setOutletPressure(5.0);
    valve1.setPercentValveOpening(30.0);
    valve1.setCalculateSteadyState(false);

    neqsim.process.equipment.separator.Separator separator1 =
        new neqsim.process.equipment.separator.Separator("separator_1");
    separator1.addStream(valve1.getOutletStream());
    separator1.setCalculateSteadyState(true);

    neqsim.process.equipment.compressor.Compressor compressor1 =
        new neqsim.process.equipment.compressor.Compressor("compressor_1");
    compressor1.setInletStream(separator1.getGasOutStream());
    compressor1.setOutletPressure(10.0, "bara");
    compressor1.setCalculateSteadyState(true);

    neqsim.process.processmodel.ProcessSystem p = new neqsim.process.processmodel.ProcessSystem();
    p.add(stream1);
    p.add(valve1);
    p.add(separator1);
    p.add(compressor1);

    p.run();
    double compPower1 = compressor1.getPower("kW");
    valve1.setPercentValveOpening(30 - 25.0);

    p.runTransient(1.0);
    double compPower2 = compressor1.getPower("kW");
    assertEquals(1.777610523, compPower1 - compPower2, 0.0001);

    p.runTransient(12.0);
    compPower2 = compressor1.getPower("kW");
    assertEquals(1.77761052380, compPower1 - compPower2, 0.0001);
  }


  @Test
  public void testDynamicCalculation1() {
    neqsim.thermo.system.SystemSrkEos fluid1 =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 10.00);
    fluid1.addComponent("methane", 0.900);
    fluid1.addComponent("ethane", 0.100);
    fluid1.addComponent("n-heptane", 1.00);
    fluid1.addComponent("nC10", 0.0);
    fluid1.setMixingRule("classic");

    neqsim.process.equipment.stream.Stream stream1 =
        new neqsim.process.equipment.stream.Stream("Stream1", fluid1);
    stream1.setFlowRate(51.0, "kg/hr");
    stream1.setPressure(10.0, "bara");
    stream1.setCalculateSteadyState(true);

    neqsim.process.equipment.valve.ThrottlingValve valve1 =
        new neqsim.process.equipment.valve.ThrottlingValve("valve_1", stream1);
    valve1.setOutletPressure(5.0);
    valve1.setPercentValveOpening(30.0);
    valve1.setCalculateSteadyState(false);

    neqsim.process.equipment.separator.Separator separator1 =
        new neqsim.process.equipment.separator.Separator("separator_1");
    separator1.addStream(valve1.getOutletStream());
    separator1.setSeparatorLength(10.3);
    separator1.setInternalDiameter(0.2);
    separator1.setLiquidLevel(0.5);
    separator1.setCalculateSteadyState(false);


    neqsim.process.equipment.valve.ThrottlingValve valve2 =
        new neqsim.process.equipment.valve.ThrottlingValve("valve_2",
            separator1.getLiquidOutStream());
    valve2.setOutletPressure(1.0);
    valve2.setPercentValveOpening(30.0);
    valve2.setCalculateSteadyState(false);

    neqsim.process.equipment.valve.ThrottlingValve valve3 =
        new neqsim.process.equipment.valve.ThrottlingValve("valve_3", separator1.getGasOutStream());
    valve3.setOutletPressure(1.0);
    valve3.setPercentValveOpening(30.0);
    valve3.setCalculateSteadyState(false);

    neqsim.process.measurementdevice.VolumeFlowTransmitter flowTransmitter =
        new neqsim.process.measurementdevice.VolumeFlowTransmitter(stream1);
    flowTransmitter.setUnit("kg/hr");
    flowTransmitter.setMaximumValue(100.0);
    flowTransmitter.setMinimumValue(1.0);


    neqsim.process.processmodel.ProcessSystem p = new neqsim.process.processmodel.ProcessSystem();
    p.add(stream1);
    p.add(valve1);
    p.add(separator1);
    p.add(valve2);
    p.add(valve3);
    p.add(flowTransmitter);

    p.run();

    assertEquals(10.27401660647343, valve1.getCv("SI"), 1e-3);
    assertEquals(0.08694025035, valve2.getCv("SI"), 1e-3);
    assertEquals(6.6462943915, valve3.getCv("SI"), 1e-3);

    assertEquals(11.87676319, valve1.getCv("US"), 1e-3);
    assertEquals(0.100502929408, valve2.getCv("US"), 1e-3);
    assertEquals(7.6831163166211, valve3.getCv("US"), 1e-3);

    logger.debug("sep pres {}", separator1.getPressure("bara"));

    double deltaTsec = 10.0;
    p.runTransient(deltaTsec);

    logger.debug("sep pres {}", separator1.getPressure("bara"));

    for (int i = 0; i < 10; i++) {
      logger.debug(
          "time {} sep pres {} valve1 opening {} valve2 opening {} valve3 opening {} flow {}", i,
          separator1.getPressure("bara"), valve1.getPercentValveOpening(),
          valve2.getPercentValveOpening(), valve3.getPercentValveOpening(),
          flowTransmitter.getMeasuredValue());
      p.runTransient(deltaTsec);
    }

    valve1.setPercentValveOpening(10.0);

    for (int i = 0; i < 100; i++) {
      logger.debug(
          "time {} sep pres {} valve1 opening {} valve2 opening {} valve3 opening {} liq_level {} flow {}",
          i, separator1.getPressure("bara"), valve1.getPercentValveOpening(),
          valve2.getPercentValveOpening(), valve3.getPercentValveOpening(),
          separator1.getLiquidLevel(), flowTransmitter.getMeasuredValue());
      p.runTransient(deltaTsec);
    }
  }
}
