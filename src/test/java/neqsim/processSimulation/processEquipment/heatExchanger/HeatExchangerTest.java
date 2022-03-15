package neqsim.processSimulation.processEquipment.heatExchanger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.util.Recycle;
import neqsim.processSimulation.processEquipment.valve.ThrottlingValve;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * HeatExchanger Test class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 * @since 2.2.3
 */
public class HeatExchangerTest {
  static neqsim.thermo.system.SystemInterface testSystem;
  static Stream gasStream;

  @BeforeEach
  static void setUp() {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 60.0), 20.00);
    testSystem.addComponent("methane", 120.00);
    testSystem.addComponent("ethane", 120.0);
    testSystem.addComponent("n-heptane", 3.0);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
  }

  @Test
  public static void test_Run1(String args[]) {
    Stream stream_Hot = new Stream("Stream1", testSystem);
    Stream stream_Cold = new Stream("Stream1", testSystem.clone());

    HeatExchanger heatEx = new HeatExchanger("heatEx");
    heatEx.setFeedStream(0, stream_Hot);
    heatEx.setFeedStream(1, stream_Cold);// resyc.getOutStream());

    Separator sep = new Separator("sep", stream_Hot);

    Stream oilOutStream = new Stream("oilOutStream", sep.getLiquidOutStream());

    ThrottlingValve valv1 = new ThrottlingValve("valv1", oilOutStream);
    valv1.setOutletPressure(5.0);

    Recycle resyc = new Recycle("resyc");
    resyc.addStream(valv1.getOutStream());
    resyc.setOutletStream(stream_Cold);

    neqsim.processSimulation.processSystem.ProcessSystem operations =
        new neqsim.processSimulation.processSystem.ProcessSystem();
    operations.add(stream_Hot);
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
  public static void test_Run2(String args[]) {
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

    neqsim.processSimulation.processEquipment.heatExchanger.HeatExchanger heatExchanger1 =
        new neqsim.processSimulation.processEquipment.heatExchanger.HeatExchanger("heatEx",
            stream_Hot, stream_Cold);

    neqsim.processSimulation.processSystem.ProcessSystem operations =
        new neqsim.processSimulation.processSystem.ProcessSystem();
    operations.add(stream_Hot);
    operations.add(stream_Cold);
    operations.add(heatExchanger1);

    operations.run();
    // operations.displayResult();
    // heatExchanger1.getOutStream(0).displayResult();
    // heatExchanger1.getOutStream(1).displayResult();
  }
}
