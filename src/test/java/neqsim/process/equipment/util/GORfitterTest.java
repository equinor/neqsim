package neqsim.process.equipment.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.measurementdevice.MultiPhaseMeter;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class GORfitterTest {
  @Test
  void testMain() {
    SystemInterface testFluid = new SystemSrkEos(338.15, 50.0);
    testFluid.addComponent("nitrogen", 1.205);
    testFluid.addComponent("CO2", 1.340);
    testFluid.addComponent("methane", 87.974);
    testFluid.addComponent("ethane", 5.258);
    testFluid.addComponent("propane", 3.283);
    testFluid.addComponent("i-butane", 0.082);
    testFluid.addComponent("n-butane", 0.487);
    testFluid.addComponent("i-pentane", 0.056);
    testFluid.addComponent("n-pentane", 1.053);
    testFluid.addComponent("nC10", 4.053);
    testFluid.setMixingRule(2);
    testFluid.setMultiPhaseCheck(true);

    testFluid.setTemperature(90.0, "C");
    testFluid.setPressure(60.0, "bara");
    testFluid.setTotalFlowRate(1e6, "kg/hr");

    Stream stream_1 = new Stream("Stream1", testFluid);

    MultiPhaseMeter multiPhaseMeter = new MultiPhaseMeter("test", stream_1);
    multiPhaseMeter.setTemperature(90.0, "C");
    multiPhaseMeter.setPressure(60.0, "bara");

    GORfitter gORFItter = new GORfitter("test", stream_1);
    gORFItter.setTemperature(15.0, "C");
    gORFItter.setPressure(ThermodynamicConstantsInterface.referencePressure, "bara");
    gORFItter.setReferenceConditions("actual");
    // gORFItter.setGVF(0.1);
    gORFItter.setGOR(10.1);

    Stream stream_2 = new Stream("stream_2", gORFItter.getOutletStream());

    MultiPhaseMeter multiPhaseMeter2 = new MultiPhaseMeter("test", stream_2);
    multiPhaseMeter2.setTemperature(90.0, "C");
    multiPhaseMeter2.setPressure(60.0, "bara");

    neqsim.process.processmodel.ProcessSystem operations =
        new neqsim.process.processmodel.ProcessSystem();
    operations.add(stream_1);
    operations.add(multiPhaseMeter);
    operations.add(gORFItter);
    operations.add(stream_2);
    operations.add(multiPhaseMeter2);
    operations.run();

    // This stream was inlet to MPM 2 (from fitter)
    stream_2.setTemperature(15.0, "C");
    stream_2.setPressure(1.01325, "bara");
    stream_2.run();
    // mimic MPM

    double gor_sm3gas_sm3oil = stream_2.getFluid().getPhase("gas").getFlowRate("Sm3/hr")
        / stream_2.getFluid().getPhase("oil").getFlowRate("Sm3/hr");

    double gor_sm3gas_sm3oil_corrected = stream_2.getFluid().getPhase("gas").getCorrectedVolume()
        / stream_2.getFluid().getPhase("oil").getCorrectedVolume();

    System.out.println("Stream 2 (results outside MPM) " + " GOR sm3/sm3 " + gor_sm3gas_sm3oil
        + " GOR Corrected by volume " + gor_sm3gas_sm3oil_corrected);

    System.out.println("Stream 2 (results outside MPM) getPhase(gas).getCorrectedVolume() "
        + stream_2.getFluid().getPhase("gas").getCorrectedVolume());
    System.out.println("Stream 2 (results outside MPM) getPhase(oil).getCorrectedVolume() "
        + stream_2.getFluid().getPhase("oil").getCorrectedVolume());

    // Assertions.assertEquals(51.3073530232923, multiPhaseMeter.getMeasuredValue("GOR", ""),
    // 1e-12);
    // Assertions.assertEquals(3106.7708277963447, multiPhaseMeter.getMeasuredValue("GOR_std", ""),
    // 1e-12);
    Assertions.assertEquals(10.1, multiPhaseMeter2.getMeasuredValue("GOR", ""), 1e-10);
    Assertions.assertEquals(682.1045749623208, multiPhaseMeter2.getMeasuredValue("GOR_std", ""),
        1e-8); // the value of GOR sm3/sm3 3.48551599242607 is quite far if we take by flow
               // getStandardFlow
    Assertions.assertEquals(1000000.0, stream_2.getFlowRate("kg/hr"), 1e-8);
  }
}
