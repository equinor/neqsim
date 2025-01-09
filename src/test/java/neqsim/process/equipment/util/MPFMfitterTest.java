package neqsim.process.equipment.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.measurementdevice.MultiPhaseMeter;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class MPFMfitterTest {
  @Test
  void testRun() {
    SystemInterface refFluid = new SystemSrkEos(338.15, 50.0);
    refFluid.addComponent("nitrogen", 1.205);
    refFluid.addComponent("CO2", 1.340);
    refFluid.addComponent("methane", 87.974);
    refFluid.addComponent("ethane", 5.258);
    refFluid.addComponent("propane", 3.283);
    refFluid.addComponent("i-butane", 0.082);
    refFluid.addComponent("n-butane", 0.487);
    refFluid.addComponent("i-pentane", 0.056);
    refFluid.addComponent("n-pentane", 1.053);
    refFluid.addComponent("nC10", 4.053);
    refFluid.setMixingRule(2);
    refFluid.setMultiPhaseCheck(true);
    refFluid.init(0);

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
    testFluid.init(0);

    Stream stream_1 = new Stream("Stream1", testFluid);

    MPFMfitter MPFMfitter = new MPFMfitter("MPFM fitter", stream_1);
    MPFMfitter.setReferenceFluidPackage(refFluid);
    MPFMfitter.setTemperature(15.0, "C");
    MPFMfitter.setPressure(ThermodynamicConstantsInterface.referencePressure, "bara");
    MPFMfitter.setReferenceConditions("actual");
    MPFMfitter.setGOR(10.1);

    Stream stream_2 = new Stream("stream_2", MPFMfitter.getOutletStream());

    MultiPhaseMeter multiPhaseMeter2 = new MultiPhaseMeter("test", stream_2);
    multiPhaseMeter2.setTemperature(90.0, "C");
    multiPhaseMeter2.setPressure(60.0, "bara");

    neqsim.process.processmodel.ProcessSystem operations =
        new neqsim.process.processmodel.ProcessSystem();
    operations.add(stream_1);
    operations.add(MPFMfitter);
    operations.add(stream_2);
    operations.add(multiPhaseMeter2);
    operations.run();

    Assertions.assertEquals(8.95393178, multiPhaseMeter2.getMeasuredValue("GOR", ""), 1e-3);
  }
}
