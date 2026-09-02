package neqsim.process.equipment.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

class TransientRecycleTest {
  @Test
  void testRecycleCanBeIsolatedAndThenTransportCurrentState() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 10.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");

    Stream inlet = new Stream("recycle inlet", fluid);
    inlet.setFlowRate(1000.0, "kg/hr");
    inlet.run();
    Stream outlet = inlet.clone("recycle outlet");
    TransientRecycle recycle = new TransientRecycle("dynamic recycle", inlet, outlet);

    recycle.run(UUID.randomUUID());
    assertEquals(0.0, outlet.getFlowRate("kg/hr"), 1.0e-12);

    inlet.setFlowRate(600.0, "kg/hr");
    inlet.setTemperature(35.0, "C");
    inlet.run();
    recycle.setEnabled(true);
    recycle.runTransient(2.0, UUID.randomUUID());

    assertEquals(600.0, outlet.getFlowRate("kg/hr"), 600.0e-10);
    assertEquals(35.0, outlet.getTemperature("C"), 1.0e-10);
    assertEquals(2.0, recycle.getTime(), 0.0);
  }

  @Test
  void testProcessSystemRunsDelayedLoopInInsertionOrder() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 10.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    Stream recycleReturn = feed.clone("recycle return");
    recycleReturn.setFlowRate(0.0, "kg/hr");
    Mixer mixer = new Mixer("suction mixer");
    mixer.addStream(feed);
    mixer.addStream(recycleReturn);
    Heater processUnit = new Heater("process unit", mixer.getOutletStream());
    TransientRecycle recycle = new TransientRecycle("delayed recycle", processUnit.getOutletStream(), recycleReturn);

    ProcessSystem process = new ProcessSystem("delayed recycle process");
    process.add(feed);
    process.add(recycleReturn);
    process.add(mixer);
    process.add(processUnit);
    process.add(feed.clone("independent stream 1"));
    process.add(feed.clone("independent stream 2"));
    process.add(feed.clone("independent stream 3"));
    process.add(recycle);

    assertTrue(process.hasTransientRecycles());
    assertTrue(process.getExecutionStrategyExplanation().contains("sequential (transport-delay recycle)"));
    process.run();
    assertEquals(1000.0, processUnit.getOutletStream().getFlowRate("kg/hr"), 1.0e-6);
  }
}
