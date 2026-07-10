package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.AccelerationMethod;
import neqsim.process.equipment.util.Recycle;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the recycle-acceleration convenience methods on {@link ProcessSystem} and {@link ProcessModel}.
 */
class RecycleAccelerationConvenienceTest {
  private static StreamInterface makeStream(String name) {
    SystemInterface fluid = new SystemSrkEos(298.15, 10.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream stream = new Stream(name, fluid);
    stream.setFlowRate(10.0, "kg/hr");
    return stream;
  }

  private static ProcessSystem makeProcessWithRecycles(String prefix, int numberOfRecycles) {
    ProcessSystem process = new ProcessSystem();
    for (int i = 0; i < numberOfRecycles; i++) {
      Recycle recycle = new Recycle(prefix + " recycle " + i);
      recycle.addStream(makeStream(prefix + " feed " + i));
      process.add(recycle);
    }
    return process;
  }

  @Test
  void testProcessSystemSetsWegsteinOnAllRecycles() {
    ProcessSystem process = makeProcessWithRecycles("A", 3);
    // add a non-recycle unit to confirm it is ignored
    process.add(makeStream("plain feed"));

    int updated = process.setRecycleAccelerationMethod(AccelerationMethod.WEGSTEIN);

    assertEquals(3, updated);
    for (neqsim.process.equipment.ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (unit instanceof Recycle) {
        assertEquals(AccelerationMethod.WEGSTEIN, ((Recycle) unit).getAccelerationMethod());
      }
    }
  }

  @Test
  void testProcessSystemSetsMinimumFlow() {
    ProcessSystem process = makeProcessWithRecycles("A", 2);

    int updated = process.setRecycleMinimumFlow(5.0);

    assertEquals(2, updated);
    for (neqsim.process.equipment.ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (unit instanceof Recycle) {
        assertEquals(5.0, ((Recycle) unit).getMinimumFlow(), 1e-12);
      }
    }
  }

  @Test
  void testProcessSystemNullMethodThrows() {
    ProcessSystem process = makeProcessWithRecycles("A", 1);
    assertThrows(IllegalArgumentException.class, () -> process.setRecycleAccelerationMethod(null));
  }

  @Test
  void testProcessSystemNegativeMinimumFlowThrows() {
    ProcessSystem process = makeProcessWithRecycles("A", 1);
    assertThrows(IllegalArgumentException.class, () -> process.setRecycleMinimumFlow(-1.0));
  }

  @Test
  void testProcessModelSetsWegsteinAcrossAllAreas() {
    ProcessModel model = new ProcessModel();
    model.add("area A", makeProcessWithRecycles("A", 2));
    model.add("area B", makeProcessWithRecycles("B", 3));

    int updated = model.setRecycleAccelerationMethod(AccelerationMethod.WEGSTEIN);

    assertEquals(5, updated);
    for (ProcessSystem area : model.getAllProcesses()) {
      for (neqsim.process.equipment.ProcessEquipmentInterface unit : area.getUnitOperations()) {
        if (unit instanceof Recycle) {
          assertEquals(AccelerationMethod.WEGSTEIN, ((Recycle) unit).getAccelerationMethod());
        }
      }
    }
  }

  @Test
  void testProcessModelSetsMinimumFlowAcrossAllAreas() {
    ProcessModel model = new ProcessModel();
    model.add("area A", makeProcessWithRecycles("A", 1));
    model.add("area B", makeProcessWithRecycles("B", 2));

    int updated = model.setRecycleMinimumFlow(7.5);

    assertEquals(3, updated);
  }
}
