package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.StaticMixer;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.StreamSaturatorUtil;
import neqsim.process.measurementdevice.HydrateEquilibriumTemperatureAnalyser;
import neqsim.process.processmodel.graph.ProcessGraph;
import neqsim.process.processmodel.graph.ProcessNode;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;

/** Regression coverage for reused multiphase state in a feed-forward process. */
public class MultiInputMixerPhaseRegressionTest {
  @Test
  public void reusedMultiInputProcessPreservesAqueousPhaseAfterFlowChange() {
    ProcessSystem sequential = buildProcess();
    sequential.setUseOptimizedExecution(false);
    runChangedInhibitorCase(sequential);

    ProcessSystem optimized = buildProcess();
    runChangedInhibitorCase(optimized);

    assertEquivalentOutletState(sequential, optimized);
    assertRegisteredStreamsAreExecutionBoundaries(optimized);
  }

  private void runChangedInhibitorCase(ProcessSystem process) {
    process.run();

    Stream inhibitor = (Stream) process.getUnit("inhibitor stream");
    inhibitor.setFlowRate(0.1, "kg/hr");
    process.run();
  }

  private void assertEquivalentOutletState(ProcessSystem sequential, ProcessSystem optimized) {
    SystemInterface expected = ((Stream) sequential.getUnit("downstream stream")).getFluid();
    SystemInterface actual = ((Stream) optimized.getUnit("downstream stream")).getFluid();

    assertTrue(actual.hasPhaseType("gas"));
    assertTrue(actual.hasPhaseType("aqueous"));
    assertEquals(expected.getNumberOfPhases(), actual.getNumberOfPhases());
    assertEquals(expected.getTemperature("C"), actual.getTemperature("C"), 1.0e-8);
    assertEquals(expected.getPressure("bara"), actual.getPressure("bara"), 1.0e-8);
    assertEquals(expected.getFlowRate("kg/hr"), actual.getFlowRate("kg/hr"),
        Math.abs(expected.getFlowRate("kg/hr")) * 1.0e-8);
    assertEquals(expected.getPhase("gas").getFlowRate("kg/hr"), actual.getPhase("gas").getFlowRate("kg/hr"),
        Math.abs(expected.getPhase("gas").getFlowRate("kg/hr")) * 1.0e-8);
    assertEquals(expected.getPhase("aqueous").getFlowRate("kg/hr"), actual.getPhase("aqueous").getFlowRate("kg/hr"),
        Math.abs(expected.getPhase("aqueous").getFlowRate("kg/hr")) * 1.0e-6);
    assertArrayEquals(expected.getMolarComposition(), actual.getMolarComposition(), 1.0e-10);
  }

  private void assertRegisteredStreamsAreExecutionBoundaries(ProcessSystem process) {
    ProcessGraph graph = process.buildGraph();
    ProcessGraph.ParallelPartition partition = graph.partitionForParallelExecution();

    assertLevelBefore(process, graph, partition, "saturated gas stream", "multiphase mixer");
    assertLevelBefore(process, graph, partition, "downstream stream", "separator heater");
    assertTrue(partition.getMaxParallelism() > 1,
        "Independent feed and downstream reader branches should remain parallel");
  }

  private void assertLevelBefore(ProcessSystem process, ProcessGraph graph, ProcessGraph.ParallelPartition partition,
      String beforeName, String afterName) {
    ProcessEquipmentInterface beforeUnit = process.getUnit(beforeName);
    ProcessEquipmentInterface afterUnit = process.getUnit(afterName);
    ProcessNode beforeNode = graph.getNode(beforeUnit);
    ProcessNode afterNode = graph.getNode(afterUnit);
    Integer beforeLevel = partition.getNodeToLevel().get(beforeNode);
    Integer afterLevel = partition.getNodeToLevel().get(afterNode);

    assertNotNull(beforeLevel);
    assertNotNull(afterLevel);
    assertTrue(beforeLevel < afterLevel,
        beforeName + " must complete before " + afterName + " reads its mutable state");
  }

  private ProcessSystem buildProcess() {
    SystemInterface feed = new SystemSrkCPAstatoil(298.15, 1.01325);
    String[] names = { "N2", "CO2", "methane", "ethane", "propane", "i-butane", "n-butane", "i-pentane", "n-pentane",
        "c-C5", "22-dim-C3", "n-hexane", "n-heptane", "n-octane", "n-nonane" };
    double[] amounts = { 0.41, 9.249, 73.263, 9.269, 4.75, 0.52, 1.34, 0.29, 0.36, 0.02, 0.02, 0.29, 0.25, 0.05, 0.02 };
    for (int index = 0; index < names.length; index++) {
      feed.addComponent(names[index], amounts[index]);
    }
    feed.addComponent("water", 0.0);
    feed.addComponent("MEG", 0.0);
    feed.setMixingRule(10);
    feed.setMultiPhaseCheck(true);

    ProcessSystem process = new ProcessSystem();
    Stream gas = new Stream("gas stream", feed);
    gas.setFlowRate(168958.0, "Sm3/hr");
    gas.setTemperature(29.0, "C");
    gas.setPressure(74.1, "barg");
    process.add(gas);

    StreamSaturatorUtil saturator = new StreamSaturatorUtil("water saturator", gas);
    process.add(saturator);
    Stream saturatedGas = (Stream) saturator.getOutStream();
    saturatedGas.setName("saturated gas stream");
    process.add(saturatedGas);

    SystemInterface inhibitorFluid = feed.clone();
    double inhibitorMoleFraction = (89.0 / 62.07) / ((89.0 / 62.07) + (11.0 / 18.01528));
    double[] inhibitorComposition = new double[feed.getNumberOfComponents()];
    for (int index = 0; index < feed.getNumberOfComponents(); index++) {
      String componentName = feed.getComponent(index).getName();
      if (componentName.equals("water")) {
        inhibitorComposition[index] = 1.0 - inhibitorMoleFraction;
      } else if (componentName.equals("MEG")) {
        inhibitorComposition[index] = inhibitorMoleFraction;
      }
    }
    inhibitorFluid.setMolarComposition(inhibitorComposition);

    Stream inhibitor = new Stream("inhibitor stream", inhibitorFluid);
    inhibitor.setFlowRate(0.01, "kg/hr");
    inhibitor.setTemperature(29.0, "C");
    inhibitor.setPressure(74.1, "barg");
    process.add(inhibitor);

    StaticMixer mixer = new StaticMixer("multiphase mixer");
    mixer.addStream(inhibitor);
    mixer.addStream(saturatedGas);
    process.add(mixer);

    Heater pipeline = new Heater("downstream heater", mixer.getOutletStream());
    pipeline.setOutPressure(36.2, "barg");
    pipeline.setOutTemperature(5.3, "C");
    process.add(pipeline);

    Stream outlet = (Stream) pipeline.getOutStream();
    outlet.setName("downstream stream");
    process.add(outlet);
    process.add(new HydrateEquilibriumTemperatureAnalyser("hydrate analyser", outlet));

    Heater separatorHeater = new Heater("separator heater", outlet);
    separatorHeater.setOutPressure(35.0, "barg");
    separatorHeater.setOutTemperature(19.0, "C");
    process.add(separatorHeater);
    process.add(new ThreePhaseSeparator("three phase separator", separatorHeater.getOutletStream()));
    return process;
  }
}
