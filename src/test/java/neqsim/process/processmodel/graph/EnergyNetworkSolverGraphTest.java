package neqsim.process.processmodel.graph;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.energy.EnergyNetworkSolver;
import neqsim.process.equipment.stream.EnergyBus;
import neqsim.process.equipment.stream.EnergyPortDirection;
import neqsim.process.equipment.stream.EnergyPortMode;
import neqsim.process.equipment.stream.EnergyType;
import neqsim.process.processmodel.ProcessSystem;

class EnergyNetworkSolverGraphTest {

  @Test
  void testSolverIsOrderedBetweenProducerAndSpecificationConsumer() {
    EnergyBus bus = new EnergyBus("bus", EnergyType.ELECTRICAL);
    EnergyUnit producer = new EnergyUnit("producer", EnergyPortDirection.OUTPUT, EnergyPortMode.CALCULATED, bus);
    EnergyUnit consumer = new EnergyUnit("consumer", EnergyPortDirection.INPUT, EnergyPortMode.SPECIFICATION, bus);
    EnergyNetworkSolver solver = new EnergyNetworkSolver("network solver", bus);
    ProcessSystem process = new ProcessSystem();
    process.add(consumer);
    process.add(solver);
    process.add(producer);

    List<ProcessEquipmentInterface> order = ProcessGraphBuilder.buildGraph(process).getCalculationOrder();

    assertTrue(order.contains(producer));
    assertTrue(order.contains(solver));
    assertTrue(order.contains(consumer));
    assertTrue(order.indexOf(producer) < order.indexOf(solver));
    assertTrue(order.indexOf(solver) < order.indexOf(consumer));
  }

  private static final class EnergyUnit extends ProcessEquipmentBaseClass {
    private static final long serialVersionUID = 1000L;

    /**
     * Creates a graph-test energy unit.
     *
     * @param name unit name
     * @param direction port direction
     * @param mode port mode
     * @param bus connected bus
     */
    private EnergyUnit(String name, EnergyPortDirection direction, EnergyPortMode mode, EnergyBus bus) {
      super(name);
      registerEnergyPort("power", EnergyType.ELECTRICAL, direction, mode);
      connectEnergyStream("power", bus, mode);
    }

    /** {@inheritDoc} */
    @Override
    public void run(UUID id) {
      setCalculationIdentifier(id);
    }
  }
}
