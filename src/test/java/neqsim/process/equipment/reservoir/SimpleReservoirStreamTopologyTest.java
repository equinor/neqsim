package neqsim.process.equipment.reservoir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.graph.ProcessGraph;
import neqsim.process.processmodel.graph.ProcessGraphBuilder;
import neqsim.process.processmodel.graph.ProcessNode;
import neqsim.thermo.system.SystemPrEos;

/** Verifies that a reservoir and its owned streams cannot be scheduled concurrently. */
class SimpleReservoirStreamTopologyTest extends neqsim.NeqSimTest {
  @Test
  void producerAndInjectorStreamsDefineExecutionDependencies() throws InterruptedException {
    SystemPrEos fluid = new SystemPrEos(373.15, 100.0);
    fluid.addComponent("water", 3.599);
    fluid.addComponent("nitrogen", 0.599);
    fluid.addComponent("CO2", 0.51);
    fluid.addComponent("methane", 62.8);
    fluid.addComponent("n-heptane", 12.8);
    fluid.setMixingRule(2);
    fluid.setMultiPhaseCheck(true);
    SimpleReservoir reservoir = new SimpleReservoir("reservoir");
    reservoir.setReservoirFluid(fluid, 1.0e9, 10.0, 1.0e8);
    StreamInterface gas = reservoir.addGasProducer("gas");
    StreamInterface oil = reservoir.addOilProducer("oil");
    StreamInterface water = reservoir.addWaterProducer("water");
    StreamInterface gasInjection = reservoir.addGasInjector("gas injection");
    StreamInterface waterInjection = reservoir.addWaterInjector("water injection");
    List<StreamInterface> producers = Arrays.asList(gas, oil, water);
    List<StreamInterface> injectors = Arrays.asList(gasInjection, waterInjection);
    assertEquals(producers, reservoir.getOutletStreams());
    assertEquals(injectors, reservoir.getInletStreams());
    reservoir.getOutletStreams().clear();
    reservoir.getInletStreams().clear();
    assertEquals(producers, reservoir.getOutletStreams(), "Returned lists must not mutate the reservoir");
    assertEquals(injectors, reservoir.getInletStreams());

    ProcessSystem process = new ProcessSystem();
    process.add(reservoir);
    for (StreamInterface stream : producers) {
      process.add(stream);
    }
    for (StreamInterface stream : injectors) {
      process.add(stream);
    }
    ProcessGraph graph = ProcessGraphBuilder.buildGraph(process);
    Map<ProcessNode, Integer> levels = graph.partitionForParallelExecution().getNodeToLevel();
    int reservoirLevel = levels.get(graph.getNode(reservoir));
    for (StreamInterface stream : producers) {
      assertTrue(levels.get(graph.getNode(stream)) > reservoirLevel,
          "Producer must run after the reservoir that owns and flashes it");
    }
    for (StreamInterface stream : injectors) {
      assertTrue(levels.get(graph.getNode(stream)) < reservoirLevel, "Injector must run before reservoir");
    }
    process.runParallel();
    for (StreamInterface stream : producers) {
      assertTrue(Double.isFinite(stream.getFlowRate("kg/hr")));
      assertTrue(stream.getFlowRate("kg/hr") > 0.0);
    }
  }
}
