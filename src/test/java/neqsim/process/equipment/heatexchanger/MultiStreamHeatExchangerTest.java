package neqsim.process.equipment.heatexchanger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.expander.Expander;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class MultiStreamHeatExchangerTest {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(MultiStreamHeatExchangerTest.class);

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

    Stream stream_Cold2 = new Stream("Stream3", testSystem.clone());
    stream_Cold2.setTemperature(0.0, "C");
    stream_Cold2.setFlowRate(50.0, "kg/hr");

    MultiStreamHeatExchanger heatEx = new MultiStreamHeatExchanger("heatEx");
    heatEx.addInStream(stream_Hot);
    heatEx.addInStream(stream_Cold);
    heatEx.addInStream(stream_Cold2);
    // heatEx.setUAvalue(1000);
    heatEx.setTemperatureApproach(5);

    neqsim.process.processmodel.ProcessSystem operations =
        new neqsim.process.processmodel.ProcessSystem();
    operations.add(stream_Hot);
    operations.add(stream_Cold);
    operations.add(stream_Cold2);
    operations.add(heatEx);

    operations.run();

    assertEquals(95, heatEx.getOutStream(1).getTemperature("C"), 1e-3);
    assertEquals(95, heatEx.getOutStream(2).getTemperature("C"), 1e-3);
    assertEquals(70.5921794735, heatEx.getOutStream(0).getTemperature("C"), 1e-3);

    heatEx.setUAvalue(1000);

    operations.run();
    assertEquals(97.9926276924096, heatEx.getOutStream(1).getTemperature("C"), 1e-1);
    assertEquals(97.99262769240, heatEx.getOutStream(2).getTemperature("C"), 1e-1);
    assertEquals(69.477801, heatEx.getOutStream(0).getTemperature("C"), 1e-2);
    assertEquals(1000, heatEx.getUAvalue(), 0.1);
  }

  @Test
  void testRun2() {
    neqsim.process.processmodel.ProcessSystem operations =
        new neqsim.process.processmodel.ProcessSystem();

    Stream feed_stream = new Stream("Stream1", testSystem);
    feed_stream.setTemperature(30.0, "C");
    feed_stream.setPressure(75.0, "bara");
    feed_stream.setFlowRate(1000.0, "kg/hr");
    feed_stream.run();
    operations.add(feed_stream);

    Separator separator = new Separator("sep 1", feed_stream);
    operations.add(separator);

    Stream stream_Cold = new Stream("Stream2", testSystem.clone());
    stream_Cold.setTemperature(-5.0, "C");
    stream_Cold.setPressure(50.0, "bara");
    stream_Cold.setFlowRate(310.0, "kg/hr");
    stream_Cold.run();
    operations.add(stream_Cold);

    Stream stream_Cold2 = new Stream("Stream3", testSystem.clone());
    stream_Cold2.setTemperature(-5.0, "C");
    stream_Cold2.setPressure(50.0, "bara");
    stream_Cold2.setFlowRate(50.0, "kg/hr");
    stream_Cold2.run();
    operations.add(stream_Cold2);

    MultiStreamHeatExchanger heatEx = new MultiStreamHeatExchanger("heatEx");
    heatEx.addInStream(separator.getGasOutStream());
    heatEx.addInStream(stream_Cold);
    heatEx.addInStream(stream_Cold2);
    // heatEx.setUAvalue(1000);
    heatEx.setTemperatureApproach(5);
    heatEx.run();
    operations.add(heatEx);

    Separator dewseparator = new Separator("sep 2", heatEx.getOutStream(0));
    dewseparator.run();
    operations.add(dewseparator);

    Expander expander = new Expander("expander", dewseparator.getGasOutStream());
    expander.setOutletPressure(50., "bara");
    expander.run();
    operations.add(expander);

    ThrottlingValve jt_valve = new ThrottlingValve("JT valve", dewseparator.getLiquidOutStream());
    jt_valve.setOutletPressure(50.0, "bara");
    jt_valve.run();
    operations.add(jt_valve);

    Separator separator2 = new Separator("sep 3", expander.getOutletStream());
    separator2.addStream(jt_valve.getOutletStream());
    separator2.run();
    operations.add(separator2);

    Recycle gas_expander_resycle = new neqsim.process.equipment.util.Recycle("gas recycl");
    gas_expander_resycle.addStream(separator2.getGasOutStream());
    gas_expander_resycle.setOutletStream(stream_Cold);
    gas_expander_resycle.setTolerance(1e-3);
    gas_expander_resycle.run();
    operations.add(gas_expander_resycle);

    Recycle liq_expander_resycle = new neqsim.process.equipment.util.Recycle("liq recycl");
    liq_expander_resycle.addStream(separator2.getLiquidOutStream());
    liq_expander_resycle.setOutletStream(stream_Cold2);
    liq_expander_resycle.setTolerance(1e-3);
    liq_expander_resycle.run();
    operations.add(liq_expander_resycle);

    operations.run();

    // separator2.getFluid().prettyPrint();
    // heatEx.getOutStream(0).getFluid().prettyPrint();

    assertEquals(-34.6818572, separator2.getFluid().getTemperature("C"), 1e-3);
    assertEquals(25.0, heatEx.getOutStream(1).getTemperature("C"), 1e-3);

    heatEx.setUAvalue(5000);
    operations.run();

    assertEquals(-26.931795168, separator2.getFluid().getTemperature("C"), 1e-3);
    assertEquals(17.37650429489, heatEx.getOutStream(1).getTemperature("C"), 1e-3);

    heatEx.toJson();
  }
}
