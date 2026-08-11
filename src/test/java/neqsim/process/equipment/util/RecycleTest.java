package neqsim.process.equipment.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class RecycleTest {
  @Test
  void testMixStreamMultiPhasePreservesLiquidMoles() {
    SystemInterface feedFluid = new SystemSrkEos(280.0, 50.0);
    feedFluid.addComponent("methane", 0.7);
    feedFluid.addComponent("n-heptane", 0.3);
    feedFluid.setMixingRule("classic");
    Stream inletStream = new Stream("inletStream", feedFluid);
    inletStream.run();

    SystemInterface tearFluid = new SystemSrkEos(280.0, 50.0);
    tearFluid.addComponent("methane", 0.5);
    tearFluid.addComponent("n-heptane", 0.5);
    tearFluid.setMixingRule("classic");
    Stream tearStream = new Stream("tearStream", tearFluid);
    tearStream.run();

    Recycle recycle = new Recycle("testRecycle");
    recycle.addStream(inletStream);
    recycle.addStream(tearStream);

    recycle.setOutStream(inletStream.clone());
    recycle.mixStream();

    double totalHeptaneInlet = inletStream.getThermoSystem().getComponent("n-heptane").getNumberOfmoles();
    double totalHeptaneTear = tearStream.getThermoSystem().getComponent("n-heptane").getNumberOfmoles();
    double mixedHeptane = recycle.getMixedStream().getThermoSystem().getComponent("n-heptane").getNumberOfmoles();

    Assertions.assertEquals(totalHeptaneInlet + totalHeptaneTear, mixedHeptane, 1e-6,
        "Recycle mixStream must preserve component moles from both gas and liquid phases of tear streams");
  }
}
