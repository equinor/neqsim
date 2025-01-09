package neqsim.process.measurementdevice.simpleflowregime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;

public class SevereSlugTest {
  @Test
  void testCheckFlowRegime1() {
    neqsim.thermo.system.SystemInterface testSystem2 =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 15.0), 10);
    testSystem2.addComponent("methane", 0.00015, "MSm^3/day");
    testSystem2.addComponent("n-heptane", 0.0015, "MSm^3/day");
    testSystem2.addComponent("propane", 0.00015, "MSm^3/day");
    testSystem2.addComponent("water", 0.00015, "MSm^3/day");
    testSystem2.setMixingRule(2);
    testSystem2.setMultiPhaseCheck(true);
    testSystem2.init(0);

    Stream inputStream3 = new Stream("test stream", testSystem2);
    SevereSlugAnalyser mySevereSlug6 =
        new SevereSlugAnalyser("tmp", inputStream3, 0.05, 167, 7.7, 0.1);
    assertEquals(mySevereSlug6.getPredictedFlowRegime(), "Severe Slug", "");
    assertEquals(0.19085996383839476, mySevereSlug6.getMeasuredValue(), 1e-1, "");
  }
}

