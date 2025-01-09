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
 * <p>
 * HeatExchanger Test class.
 * </p>
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

    neqsim.process.processmodel.ProcessSystem operations =
        new neqsim.process.processmodel.ProcessSystem();
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

    neqsim.thermo.system.SystemInterface testSystem2 =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 40.0), 20.00);
    testSystem2.addComponent("methane", 220.00);
    testSystem2.addComponent("ethane", 120.0);
    // testSystem2.createDatabase(true);
    testSystem2.setMixingRule(2);
    ThermodynamicOperations testOps2 = new ThermodynamicOperations(testSystem2);
    testOps2.TPflash();

    Stream stream_Cold = new Stream("Stream2", testSystem2);

    neqsim.process.equipment.heatexchanger.HeatExchanger heatExchanger1 =
        new neqsim.process.equipment.heatexchanger.HeatExchanger("heatEx", stream_Hot, stream_Cold);

    neqsim.process.processmodel.ProcessSystem operations =
        new neqsim.process.processmodel.ProcessSystem();
    operations.add(stream_Hot);
    operations.add(stream_Cold);
    operations.add(heatExchanger1);

    operations.run();
    assertEquals(heatExchanger1.getDuty(), -9674.051890272862, 1e-1);

    heatExchanger1.setDeltaT(1.0);
    heatExchanger1.run();

    assertEquals(15780.77130, heatExchanger1.getUAvalue(), 1e-3);

    heatExchanger1 =
        new neqsim.process.equipment.heatexchanger.HeatExchanger("heatEx", stream_Hot, stream_Cold);
    heatExchanger1.setDeltaT(1.0);
    heatExchanger1.run();

    assertEquals(15780.77130, heatExchanger1.getUAvalue(), 1e-3);
  }
}
