package neqsim.process.processmodel.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.ProcessEquipmentInterface;
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

  private static final class EnergyUnit extends ProcessEquipmentBaseClass {
    private static final long serialVersionUID = 1000L;

    EnergyUnit(String name, EnergyPortDirection direction, EnergyPortMode mode, EnergyStream stream) {
      super(name);
      registerEnergyPort("energy", EnergyType.SHAFT_WORK, direction, mode);
      connectEnergyStream("energy", stream);
    }

    @Override
    public void run(UUID id) {
      setCalculationIdentifier(id);
    }
  }
}
