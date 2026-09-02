package neqsim.process.equipment.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/** Regression tests for using one {@link Recycle} in steady-state and transient modes. */
class RecycleDynamicModeTest {
  @Test
  void sameRecycleCanTransitionFromSteadyStateToTransientMode() {
    SystemSrkEos fluid = createFluid();
    Stream inlet = new Stream("recycle inlet", fluid);
    inlet.setFlowRate(1000.0, "kg/hr");
    inlet.run();
    Stream outlet = inlet.clone("recycle outlet");

    Recycle recycle = new Recycle("recycle");
    recycle.addStream(inlet);
    recycle.setOutletStream(outlet);
    recycle.run(UUID.randomUUID());
    assertEquals(1000.0, outlet.getFlowRate("kg/hr"), 1.0e-6);

    recycle.setAccelerationMethod(AccelerationMethod.WEGSTEIN);
    inlet.setFlowRate(600.0, "kg/hr");
    inlet.setTemperature(35.0, "C");
    inlet.run();

    // Dynamic mode uses the same object and accepted outlet state. It must also work when the
    // surrounding process marks algebraic transient units as non-steady-state calculations.
    recycle.setCalculateSteadyState(false);
    recycle.runTransient(2.0, UUID.randomUUID());

    assertEquals(600.0, outlet.getFlowRate("kg/hr"), 1.0e-6);
    assertEquals(35.0, outlet.getTemperature("C"), 1.0e-10);
    assertEquals(2.0, recycle.getTime(), 0.0);
    assertEquals(AccelerationMethod.WEGSTEIN, recycle.getAccelerationMethod());
  }

  @Test
  void processUsesConvergedRecycleStateAsFirstTransientDelayState() {
    SystemSrkEos fluid = createFluid();
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    Stream recycleReturn = feed.clone("recycle return");
    recycleReturn.setFlowRate(0.0, "kg/hr");

    Mixer mixer = new Mixer("suction mixer");
    mixer.addStream(feed);
    mixer.addStream(recycleReturn);
    Heater processUnit = new Heater("process unit", mixer.getOutletStream());
    Splitter splitter = new Splitter("splitter", processUnit.getOutletStream(), 2);
    splitter.setSplitFactors(new double[] { 0.8, 0.2 });
    Recycle recycle = new Recycle("recycle");
    recycle.addStream(splitter.getSplitStream(1));
    recycle.setOutletStream(recycleReturn);

    ProcessSystem process = new ProcessSystem("steady-to-dynamic recycle process");
    process.add(feed);
    process.add(recycleReturn);
    process.add(mixer);
    process.add(processUnit);
    process.add(splitter);
    process.add(recycle);

    process.run();
    assertTrue(process.hasRecycles());
    double acceptedRecycleFlow = recycleReturn.getFlowRate("kg/hr");
    assertEquals(250.0, acceptedRecycleFlow, 3.0);

    feed.setFlowRate(800.0, "kg/hr");
    recycle.setCalculateSteadyState(false);
    process.setParallelTransientEnabled(true);
    process.runTransient(1.0, UUID.randomUUID());

    double expectedFirstPassFlow = 800.0 + acceptedRecycleFlow;
    assertEquals(expectedFirstPassFlow, processUnit.getOutletStream().getFlowRate("kg/hr"), 0.1);
    assertEquals(0.2 * expectedFirstPassFlow, recycleReturn.getFlowRate("kg/hr"), 0.1);
  }

  private static SystemSrkEos createFluid() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 10.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    return fluid;
  }
}
