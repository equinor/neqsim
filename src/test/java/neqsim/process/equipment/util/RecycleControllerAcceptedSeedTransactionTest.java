package neqsim.process.equipment.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Regression evidence for accepted recycle-state seed rollback and restart. */
class RecycleControllerAcceptedSeedTransactionTest extends neqsim.NeqSimTest {
  @Test
  void acceptedSeedMembershipRestoresInPlaceAndSurvivesSerialization() throws Exception {
    RecycleController controller = controllerWithAcceptedSeed();
    Recycle recycle = controller.getRecycles().get(0);
    RecycleController.Snapshot snapshot = controller.captureTransientState();

    controller.init();
    recycle.iterations = 0;
    assertTrue(controller.doSolveRecycle(recycle));
    assertEquals(0, recycle.getIterations(), "the second init must clear the accepted seed");

    controller.restoreTransientState(snapshot);
    assertSame(recycle, controller.getRecycles().get(0));
    recycle.iterations = 0;
    assertTrue(controller.doSolveRecycle(recycle));
    assertEquals(1, recycle.getIterations(), "rollback must restore accepted seed membership");

    RecycleController restarted = roundTrip(controllerWithAcceptedSeed());
    Recycle restartedRecycle = restarted.getRecycles().get(0);
    assertEquals(0, restartedRecycle.getIterations());
    assertTrue(restarted.doSolveRecycle(restartedRecycle));
    assertEquals(1, restartedRecycle.getIterations(), "Java serialization must retain accepted seed membership");
  }

  private static RecycleController controllerWithAcceptedSeed() {
    SystemInterface fluid = new SystemSrkEos(298.15, 30.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");

    Stream inlet = new Stream("accepted seed inlet", fluid.clone());
    inlet.setFlowRate(100.0, "kg/hr");
    inlet.run();
    Stream outlet = new Stream("accepted seed outlet", fluid.clone());
    outlet.setFlowRate(100.0, "kg/hr");
    outlet.run();

    Recycle recycle = new Recycle("accepted seed recycle");
    recycle.addStream(inlet);
    recycle.setOutletStream(outlet);
    recycle.setErrorCompositon(0.0);
    recycle.setErrorFlow(0.0);
    recycle.setErrorTemperature(0.0);
    recycle.setErrorPressure(0.0);
    recycle.iterations = 2;

    RecycleController controller = new RecycleController();
    controller.addRecycle(recycle);
    controller.init();
    assertEquals(0, recycle.getIterations());
    return controller;
  }

  @SuppressWarnings("unchecked")
  private static <T> T roundTrip(T value) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(value);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (T) input.readObject();
    }
  }
}
