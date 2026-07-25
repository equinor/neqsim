package neqsim.process.processmodel.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.EnergyBus;
import neqsim.process.equipment.stream.EnergyPortDirection;
import neqsim.process.equipment.stream.EnergyPortMode;
import neqsim.process.equipment.stream.EnergyStream;
import neqsim.process.equipment.stream.EnergyType;
import neqsim.process.processmodel.ProcessSystem;

class EnergyStreamGraphTest {

  @Test
  void testCalculatedEnergyPortOrdersSpecificationConsumer() {
    EnergyStream shaft = new EnergyStream("shared-shaft", EnergyType.SHAFT_WORK);
    EnergyUnit expander = new EnergyUnit("expander", EnergyPortDirection.OUTPUT, EnergyPortMode.CALCULATED, shaft);
    EnergyUnit compressor = new EnergyUnit("compressor", EnergyPortDirection.INPUT, EnergyPortMode.SPECIFICATION,
        shaft);
    ProcessSystem process = new ProcessSystem();
    process.add(compressor);
    process.add(expander);

    ProcessGraph graph = ProcessGraphBuilder.buildGraph(process);

    ProcessEdge energyEdge = graph.getEdges().stream().filter(edge -> edge.getEdgeType() == ProcessEdge.EdgeType.ENERGY)
        .findFirst().orElseThrow(() -> new AssertionError("Expected an energy dependency"));
    assertSame(shaft, energyEdge.getEnergyStream());
    assertEquals("expander", energyEdge.getSource().getName());
    assertEquals("compressor", energyEdge.getTarget().getName());

    List<ProcessEquipmentInterface> order = graph.getCalculationOrder();
    assertTrue(order.indexOf(expander) < order.indexOf(compressor));
  }

  @Test
  void testGraphExecutionIsIndependentOfInsertionOrder() {
    EnergyStream heat = new EnergyStream("recovered heat", EnergyType.HEAT);
    List<String> runOrder = new ArrayList<String>();
    EnergyUnit producer = new EnergyUnit("producer", EnergyPortDirection.OUTPUT, EnergyPortMode.CALCULATED, heat,
        runOrder);
    EnergyUnit consumer = new EnergyUnit("consumer", EnergyPortDirection.INPUT, EnergyPortMode.SPECIFICATION, heat,
        runOrder);
    ProcessSystem process = new ProcessSystem();
    process.add(consumer);
    process.add(producer);
    process.setUseOptimizedExecution(false);
    process.setUseGraphBasedExecution(true);

    process.runSequential(UUID.randomUUID());

    assertEquals(Arrays.asList("producer", "consumer"), runOrder);
  }

  @Test
  void testEnergyBusSupportsMultipleProducersAndConsumers() {
    EnergyBus bus = new EnergyBus("electrical bus", EnergyType.ELECTRICAL);
    EnergyUnit solar = new EnergyUnit("solar", EnergyPortDirection.OUTPUT, EnergyPortMode.CALCULATED, bus);
    EnergyUnit wind = new EnergyUnit("wind", EnergyPortDirection.OUTPUT, EnergyPortMode.CALCULATED, bus);
    EnergyUnit electrolyzer = new EnergyUnit("electrolyzer", EnergyPortDirection.INPUT, EnergyPortMode.SPECIFICATION,
        bus);
    EnergyUnit heater = new EnergyUnit("heater", EnergyPortDirection.INPUT, EnergyPortMode.SPECIFICATION, bus);
    ProcessSystem process = new ProcessSystem();
    process.add(electrolyzer);
    process.add(heater);
    process.add(solar);
    process.add(wind);

    ProcessGraph graph = ProcessGraphBuilder.buildGraph(process);

    long energyEdges = graph.getEdges().stream().filter(edge -> edge.getEdgeType() == ProcessEdge.EdgeType.ENERGY)
        .count();
    assertEquals(4L, energyEdges);
  }

  @Test
  void testPointToPointStreamRejectsMultipleConsumers() {
    EnergyStream shaft = new EnergyStream("single shaft", EnergyType.SHAFT_WORK);
    EnergyUnit expander = new EnergyUnit("expander", EnergyPortDirection.OUTPUT, EnergyPortMode.CALCULATED, shaft);
    EnergyUnit compressorA = new EnergyUnit("compressor A", EnergyPortDirection.INPUT, EnergyPortMode.SPECIFICATION,
        shaft);
    EnergyUnit compressorB = new EnergyUnit("compressor B", EnergyPortDirection.INPUT, EnergyPortMode.SPECIFICATION,
        shaft);
    ProcessSystem process = new ProcessSystem();
    process.add(expander);
    process.add(compressorA);
    process.add(compressorB);

    assertThrows(IllegalStateException.class, () -> ProcessGraphBuilder.buildGraph(process));
  }

  private static final class EnergyUnit extends ProcessEquipmentBaseClass {
    private static final long serialVersionUID = 1000L;
    private final List<String> runOrder;

    EnergyUnit(String name, EnergyPortDirection direction, EnergyPortMode mode, EnergyStream stream) {
      this(name, direction, mode, stream, null);
    }

    EnergyUnit(String name, EnergyPortDirection direction, EnergyPortMode mode, EnergyStream stream,
        List<String> runOrder) {
      super(name);
      this.runOrder = runOrder;
      registerEnergyPort("energy", stream.getEnergyType(), direction, mode);
      connectEnergyStream("energy", stream);
    }

    @Override
    public void run(UUID id) {
      if (runOrder != null) {
        runOrder.add(getName());
      }
      setCalculationIdentifier(id);
    }
  }
}
