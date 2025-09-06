package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
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
    testSystem.prettyPrint();
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
    testSystem.prettyPrint();
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
    testSystem.prettyPrint();
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
    testSystem.prettyPrint();
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
    testSystem.prettyPrint();
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



}
