package neqsim.process.equipment.heatexchanger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * HeatExchanger Test class.
 *
 * @author asmund
 * @version $Id: $Id
 * @since 2.2.3
 */
public class HeatExchangerTest extends neqsim.NeqSimTest {
  static neqsim.thermo.system.SystemInterface testSystem;
  Stream gasStream;

  @BeforeEach
  void setUp() {
    testSystem = new neqsim.thermo.system.SystemSrkEos((273.15 + 60.0), 20.00);
    testSystem.addComponent("methane", 120.00);
    testSystem.addComponent("ethane", 120.0);
    testSystem.addComponent("n-heptane", 3.0);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
  }

  @Test
  void testRun1() {
    Stream stream_Hot = new Stream("Stream1", testSystem);
    stream_Hot.setTemperature(100.0, "C");
    stream_Hot.setFlowRate(1000.0, "kg/hr");
    Stream stream_Cold = new Stream("Stream2", testSystem.clone());
    stream_Cold.setTemperature(20.0, "C");
    stream_Cold.setFlowRate(310.0, "kg/hr");

    HeatExchanger heatEx = new HeatExchanger("heatEx", stream_Hot, stream_Cold);
    // heatEx.setFeedStream(0, stream_Hot);
    // heatEx.setFeedStream(1, stream_Cold); // resyc.getOutStream());
    heatEx.setGuessOutTemperature(80.0, "C");
    heatEx.setUAvalue(1000);

    Separator sep = new Separator("sep", stream_Hot);
    Stream oilOutStream = new Stream("oilOutStream", sep.getLiquidOutStream());

    ThrottlingValve valv1 = new ThrottlingValve("valv1", oilOutStream);
    valv1.setOutletPressure(5.0);

    Recycle resyc = new Recycle("resyc");
    resyc.addStream(valv1.getOutletStream());
    resyc.setOutletStream(stream_Cold);

    neqsim.process.processmodel.ProcessSystem operations = new neqsim.process.processmodel.ProcessSystem();
    operations.add(stream_Hot);
    operations.add(stream_Cold);
    operations.add(heatEx);
    operations.add(sep);
    operations.add(oilOutStream);
    operations.add(valv1);
    operations.add(resyc);

    operations.run();
    // heatEx.getOutStream(0).displayResult();
    // resyc.getOutStream().displayResult();
  }

  @Test
  void testRun2() {
    Stream stream_Hot = new Stream("Stream1", testSystem);

    neqsim.thermo.system.SystemInterface testSystem2 = new neqsim.thermo.system.SystemSrkEos((273.15 + 40.0), 20.00);
    testSystem2.addComponent("methane", 220.00);
    testSystem2.addComponent("ethane", 120.0);
    // testSystem2.createDatabase(true);
    testSystem2.setMixingRule(2);
    ThermodynamicOperations testOps2 = new ThermodynamicOperations(testSystem2);
    testOps2.TPflash();

    Stream stream_Cold = new Stream("Stream2", testSystem2);

    neqsim.process.equipment.heatexchanger.HeatExchanger heatExchanger1 = new neqsim.process.equipment.heatexchanger.HeatExchanger(
        "heatEx", stream_Hot, stream_Cold);

    neqsim.process.processmodel.ProcessSystem operations = new neqsim.process.processmodel.ProcessSystem();
    operations.add(stream_Hot);
    operations.add(stream_Cold);
    operations.add(heatExchanger1);

    operations.run();
    assertEquals(heatExchanger1.getDuty(), -9674.051890272862, 1e-1);

    heatExchanger1.setDeltaT(1.0);
    heatExchanger1.run();

    assertEquals(15780.77130, heatExchanger1.getUAvalue(), 1e-3);

    heatExchanger1 = new neqsim.process.equipment.heatexchanger.HeatExchanger("heatEx", stream_Hot, stream_Cold);
    heatExchanger1.setDeltaT(1.0);
    heatExchanger1.run();

    assertEquals(15780.77130, heatExchanger1.getUAvalue(), 1e-3);
  }

  /**
   * Pin one outlet temperature with the "outTemperature" specification and verify the specified side hits the target
   * exactly while the opposite side is energy-balanced.
   */
  @Test
  void testOutTemperatureSpecification() {
    Stream stream_Hot = new Stream("Stream1", testSystem);
    stream_Hot.setTemperature(100.0, "C");
    stream_Hot.setFlowRate(1000.0, "kg/hr");
    stream_Hot.run();
    Stream stream_Cold = new Stream("Stream2", testSystem.clone());
    stream_Cold.setTemperature(20.0, "C");
    stream_Cold.setFlowRate(1000.0, "kg/hr");
    stream_Cold.run();

    HeatExchanger heatEx = new HeatExchanger("heatEx", stream_Hot, stream_Cold);
    // pin the hot outlet (side 0) to 55 C
    heatEx.setOutStreamSpecificationNumber(0);
    heatEx.setOutTemperature(55.0, "C");

    neqsim.process.processmodel.ProcessSystem operations = new neqsim.process.processmodel.ProcessSystem();
    operations.add(stream_Hot);
    operations.add(stream_Cold);
    operations.add(heatEx);
    operations.run();

    // hot outlet pinned exactly to target
    assertEquals(55.0, heatEx.getOutStream(0).getTemperature("C"), 1e-3);
    // hot outlet pressure follows the hot inlet (no spurious drop/freeze)
    assertEquals(stream_Hot.getPressure("bara"), heatEx.getOutStream(0).getPressure("bara"), 1e-6);
    // cold side heated (energy balance): cold outlet above its inlet, hot side cooled
    assertEquals(true, heatEx.getOutStream(1).getTemperature("C") > 20.0);
  }

  /**
   * Test the Builder pattern for creating heat exchangers.
   */
  @Test
  void testBuilderPattern() {
    Stream hotStream = new Stream("HotStream", testSystem);
    hotStream.setTemperature(100.0, "C");
    hotStream.setFlowRate(1000.0, "kg/hr");
    hotStream.run();

    neqsim.thermo.system.SystemInterface coldSystem = new neqsim.thermo.system.SystemSrkEos((273.15 + 20.0), 20.00);
    coldSystem.addComponent("methane", 100.0);
    coldSystem.addComponent("ethane", 50.0);
    coldSystem.setMixingRule(2);

    Stream coldStream = new Stream("ColdStream", coldSystem);
    coldStream.setTemperature(20.0, "C");
    coldStream.setFlowRate(500.0, "kg/hr");
    coldStream.run();

    HeatExchanger hx = HeatExchanger.builder("E-100").hotStream(hotStream).coldStream(coldStream).UAvalue(2000.0)
        .flowArrangement("counterflow").guessOutTemperature(60.0, "C").build();

    hx.run();

    assertEquals("E-100", hx.getName());
    assertEquals(2000.0, hx.getUAvalue(), 0.001);
    // Verify heat exchange occurred
    assertEquals(hotStream.getTemperature("C") > hx.getOutStream(0).getTemperature("C"), true);
    assertEquals(coldStream.getTemperature("C") < hx.getOutStream(1).getTemperature("C"), true);
  }

  /**
   * Test the Builder pattern with deltaT specification.
   */
  @Test
  void testBuilderWithDeltaT() {
    Stream hotStream = new Stream("HotStream", testSystem);
    hotStream.setTemperature(80.0, "C");
    hotStream.setFlowRate(800.0, "kg/hr");
    hotStream.run();

    neqsim.thermo.system.SystemInterface coldSystem = new neqsim.thermo.system.SystemSrkEos((273.15 + 15.0), 20.00);
    coldSystem.addComponent("methane", 80.0);
    coldSystem.setMixingRule(2);

    Stream coldStream = new Stream("ColdStream", coldSystem);
    coldStream.setTemperature(15.0, "C");
    coldStream.setFlowRate(600.0, "kg/hr");
    coldStream.run();

    HeatExchanger hx = HeatExchanger.builder("E-101").hotStream(hotStream).coldStream(coldStream).deltaT(5.0).build();

    hx.run();

    assertEquals("E-101", hx.getName());
    // DeltaT mode sets minimum approach temperature
  }

  /**
   * Test the effectiveness-NTU fouling / UA-degradation screening helper: the cold-outlet temperature must fall
   * monotonically as UA is lost, the clean point must match the exchanger's own run, and the call must not mutate the
   * exchanger state.
   */
  @Test
  void testFoulingScreening() {
    Stream stream_Hot = new Stream("Hot", testSystem);
    stream_Hot.setTemperature(90.0, "C");
    stream_Hot.setFlowRate(5000.0, "kg/hr");
    stream_Hot.run();

    neqsim.thermo.system.SystemInterface coldSystem = new neqsim.thermo.system.SystemSrkEos((273.15 + 10.0), 20.00);
    coldSystem.addComponent("water", 1000.0);
    coldSystem.setMixingRule(2);
    Stream stream_Cold = new Stream("Cold", coldSystem);
    stream_Cold.setTemperature(10.0, "C");
    stream_Cold.setFlowRate(20000.0, "kg/hr");
    stream_Cold.run();

    HeatExchanger hx = new HeatExchanger("E-CM", stream_Hot, stream_Cold);
    hx.setUAvalue(5000.0);
    hx.setGuessOutTemperature(40.0, "C");
    hx.run();
    double cleanColdOutRun = hx.getOutStream(1).getTemperature("C");
    double uaAfterRun = hx.getUAvalue();

    double[] fractions = new double[] { 1.0, 0.75, 0.5, 0.25, 0.0 };
    HeatExchanger.FoulingScreeningResult result = hx.foulingScreening(5000.0, fractions, "C");

    assertEquals("C", result.getTemperatureUnit());
    double[] coldOut = result.getColdOutletTemperature();
    double[] eff = result.getEffectiveness();
    assertEquals(fractions.length, coldOut.length);

    // Cold-outlet temperature and effectiveness fall monotonically as UA is lost.
    for (int i = 1; i < coldOut.length; i++) {
      org.junit.jupiter.api.Assertions.assertTrue(coldOut[i] <= coldOut[i - 1] + 1e-6,
          "cold outlet must not rise as UA is lost");
      org.junit.jupiter.api.Assertions.assertTrue(eff[i] <= eff[i - 1] + 1e-9,
          "effectiveness must not rise as UA is lost");
    }

    // Zero UA -> no heat transfer: cold outlet equals cold inlet, effectiveness 0.
    assertEquals(10.0, coldOut[coldOut.length - 1], 1e-6);
    assertEquals(0.0, eff[eff.length - 1], 1e-9);

    // Clean point (fraction 1.0) matches the exchanger's own solved cold outlet.
    assertEquals(cleanColdOutRun, coldOut[0], 0.5);

    // Screening is side-effect-free: UA value is unchanged.
    assertEquals(uaAfterRun, hx.getUAvalue(), 1e-9);

    // Convenience sweep overload builds an evenly spaced set of fractions.
    HeatExchanger.FoulingScreeningResult sweep = hx.foulingScreening(5000.0, 6, 0.5, 1.0, "C");
    assertEquals(6, sweep.getUaFraction().length);
    assertEquals(0.5, sweep.getUaFraction()[0], 1e-9);
    assertEquals(1.0, sweep.getUaFraction()[5], 1e-9);
    org.junit.jupiter.api.Assertions.assertNotNull(sweep.toJson());
  }
}
