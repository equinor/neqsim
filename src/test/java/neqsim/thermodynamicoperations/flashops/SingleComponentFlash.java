package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class SingleComponentFlash {


  @Test
  void testConstantPhaseFractionPressureFlash() {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemPrEos(273.15 + 50.0, 15.0);
    testSystem.addComponent("propane", 1.0);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    try {
      testOps.constantPhaseFractionPressureFlash(0.5);
    } catch (Exception ex) {
      System.out.println("error" + ex.toString());
    }
    assertEquals(323.15, testSystem.getTemperature(), 1e-2);
    assertEquals(17.19579859, testSystem.getPressure(), 1e-2);
  }

  @Test
  void testonConstantPhaseFractionTemperatureFlash() {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemPrEos(283.15, 10.0);
    testSystem.addComponent("propane", 1.0);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    try {
      testOps.constantPhaseFractionTemperatureFlash(0.9);
    } catch (Exception ex) {
      System.out.println("error" + ex.toString());
    }
    assertEquals(300.08299597, testSystem.getTemperature(), 1e-2);
    assertEquals(10.0, testSystem.getPressure(), 1e-2);
    // testSystem.prettyPrint();
  }

  @Test
  void testonConstantPhaseFractionTemperatureFlash2() {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemPrEos(293.15, 10.0);
    testSystem.addComponent("ethane", 1.0);
    testSystem.addComponent("propane", 1.0);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    try {
      testOps.constantPhaseFractionTemperatureFlash(0.591);
    } catch (Exception ex) {
      System.out.println("error" + ex.toString());
    }
    assertEquals(279.767487894, testSystem.getTemperature(), 1e-2);
    assertEquals(10.0, testSystem.getPressure(), 1e-2);
    // testSystem.prettyPrint();
  }

  @Test
  void testonConstantPhaseFractionPressureFlash2() {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemPrEos(279.7674878, 10.0);
    testSystem.addComponent("ethane", 1.0);
    testSystem.addComponent("propane", 1.0);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    try {
      testOps.constantPhaseFractionPressureFlash(0.591);
    } catch (Exception ex) {
      System.out.println("error" + ex.toString());
    }
    assertEquals(279.767487894, testSystem.getTemperature(), 1e-2);
    assertEquals(11.95267803, testSystem.getPressure(), 1e-2);
    // testSystem.prettyPrint();
  }

  @Test
  void testonConstantPhaseFractionPressureFlash4() {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemPrEos(279.7674878, 1.0);
    testSystem.addComponent("ethane", 1.0);
    testSystem.addComponent("n-heptane", 1.0);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    try {
      testOps.constantPhaseFractionPressureFlash(0.99591);
    } catch (Exception ex) {
      System.out.println("error" + ex.toString());
    }
    assertEquals(279.767487894, testSystem.getTemperature(), 1e-2);
    assertEquals(0.047926652566, testSystem.getPressure(), 1e-2);
    /// testSystem.prettyPrint();
  }

  @Test
  void testonConstantPhaseFractionTemperatureFlash4() {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemPrEos(299.7674878, 0.047926652566);
    testSystem.addComponent("ethane", 1.0);
    testSystem.addComponent("n-heptane", 1.0);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    try {
      testOps.constantPhaseFractionTemperatureFlash(0.99591);
    } catch (Exception ex) {
      System.out.println("error" + ex.toString());
    }
    assertEquals(301.23082803, testSystem.getTemperature(), 1e-2);
    assertEquals(0.047926652566, testSystem.getPressure(), 1e-2);
    // testSystem.prettyPrint();
  }


  @Test
  void testConstantPhaseFractionPressureFlash3() {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemPrEos(273.15 - 150.0, 15.0);
    testSystem.addComponent("nitrogen", 1.0);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    try {
      testOps.constantPhaseFractionPressureFlash(0.5);
    } catch (Exception ex) {
      System.out.println("error" + ex.toString());
    }
    assertEquals(123.1499999, testSystem.getTemperature(), 1e-2);
    assertEquals(36.58179680, testSystem.getPressure(), 1e-2);
  }

  @Test
  void testProcess1() {

    SystemInterface fluid1 = new SystemPrEos(278.15, 10.0);
    fluid1.addComponent("propane", 1.0);

    Stream stream1 = new Stream("feed stream", fluid1);
    stream1.setTemperature(278.15);
    stream1.setPressure(10.0);
    stream1.setFlowRate(100.0, "kg/sec");
    stream1.run();

    ThrottlingValve valve1 = new ThrottlingValve("control valve 1", stream1);
    valve1.setOutletPressure(3.0);
    valve1.run();
    valve1.getFluid().initProperties();
    // valve1.getOutletStream().getFluid().prettyPrint();
    assertEquals(259.025123, valve1.getOutletStream().getTemperature(), 1e-2);

    Separator separator1 = new Separator("separator 1", valve1.getOutletStream());
    separator1.run();

    Compressor compressor1 = new Compressor("compressor 1", separator1.getGasOutStream());
    compressor1.setOutletPressure(10.0);
    compressor1.run();

    ThrottlingValve liquid_valve1 =
        new ThrottlingValve("liq valve 1", separator1.getLiquidOutStream());
    liquid_valve1.setOutletPressure(1.4);
    liquid_valve1.run();

    assertEquals(238.599901382, liquid_valve1.getOutletStream().getTemperature(), 1e-2);
    // liquid_valve1.getOutletStream().getFluid().prettyPrint();
  }

  @Test
  void testPropaneTankProcessGasAndLiquidFeeds() {
    // runCase(50.0, true, 0.01);
    runCase(10.0, false, 10.0);
  }

  /**
   * Run the tank process for a given feed temperature.
   *
   * @param feedTempC feed temperature in Celsius
   * @param feedIsGas whether the feed enters as gas (otherwise liquid)
   */
  private void runCase(double feedTempC, boolean feedIsGas, double dt) {
    SystemInterface fluid = new SystemSrkEos(feedTempC + 273.15, 10.0);
    fluid.addComponent("propane", 100.0);
    fluid.setMixingRule(2);

    Stream feed = new Stream("feed", fluid);
    feed.setPressure(10.0, "bara");
    feed.setTemperature(feedTempC, "C");
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();

    if (feedIsGas) {
      assertTrue(feed.getFluid().hasPhaseType("gas"));
    } else {
      assertTrue(feed.getFluid().hasPhaseType("oil"));
    }

    ThrottlingValve feedValve = new ThrottlingValve("feed valve", feed);
    feedValve.setOutletPressure(5.0);
    feedValve.setPercentValveOpening(50.0);

    Separator separator = new Separator("propane separator", feedValve.getOutletStream());
    separator.setSeparatorLength(2.0);
    separator.setInternalDiameter(1.0);
    if (feedIsGas) {
      separator.setLiquidLevel(0.000001);
    } else {
      separator.setLiquidLevel(0.5);
    }

    ThrottlingValve gasValve = new ThrottlingValve("gas valve", separator.getGasOutStream());
    gasValve.setOutletPressure(1.0);
    gasValve.setPercentValveOpening(50.0);

    ThrottlingValve liquidValve =
        new ThrottlingValve("liquid valve", separator.getLiquidOutStream());
    liquidValve.setOutletPressure(1.0);
    liquidValve.setPercentValveOpening(50.0);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(feedValve);
    process.add(separator);
    process.add(gasValve);
    process.add(liquidValve);

    process.run();

    // liquidValve.getOutletStream().getFluid().prettyPrint();

    double initialOut = gasValve.getOutletStream().getFlowRate("kg/hr")
        + liquidValve.getOutletStream().getFlowRate("kg/hr");
    assertEquals(feed.getFlowRate("kg/hr"), initialOut, feed.getFlowRate("kg/hr") * 1e-6);

    // close inlet valve and run dynamic simulation
    feedValve.setPercentValveOpening(10.0);
    feedValve.setCalculateSteadyState(false);
    separator.setCalculateSteadyState(false);
    gasValve.setCalculateSteadyState(false);
    liquidValve.setCalculateSteadyState(false);

    int lenghth = 10;
    double[] time = null;
    double[] temp = null;
    time = new double[lenghth];
    temp = new double[lenghth];
    time[0] = 0.0;
    temp[0] = separator.getThermoSystem().getTemperature("C");

    process.setTimeStep(dt);
    for (int i = 0; i < lenghth / 2; i++) {
      process.runTransient();
      time[i] = process.getTime();
      temp[i] = separator.getThermoSystem().getTemperature("C");
      System.out.println(
          "time " + time[i] + " temp " + temp[i] + " sep height " + separator.getLiquidLevel()
              + " flow rate " + feedValve.getOutletStream().getFlowRate("kg/hr") + " pressure "
              + feedValve.getOutletStream().getPressure() + " temp_out "
              + liquidValve.getOutletStream().getTemperature("C"));
    }

    feedValve.setPercentValveOpening(50.0);
    for (int i = 0; i < lenghth / 2; i++) {
      process.runTransient();
      time[i] = process.getTime();
      temp[i] = separator.getThermoSystem().getTemperature("C");
      System.out.println(
          "time " + time[i] + " temp " + temp[i] + " sep height " + separator.getLiquidLevel()
              + " flow rate " + feedValve.getOutletStream().getFlowRate("kg/hr") + " pressure "
              + feedValve.getOutletStream().getPressure() + " temp_out "
              + liquidValve.getOutletStream().getTemperature("C"));
    }

    liquidValve.getOutletStream().getFluid().prettyPrint();
    double finalOut = gasValve.getOutletStream().getFlowRate("kg/hr")
        + liquidValve.getOutletStream().getFlowRate("kg/hr");
    // assertTrue(finalOut < initialOut);
    // assertTrue(finalOut > 0.0);
  }
}
