package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.dynamics.TransientStateParticipant;
import neqsim.process.dynamics.TransientStepTransaction;
import neqsim.process.dynamics.TransientTransactionCoverage;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.util.BroydenAccelerator;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.util.RecycleController;

/**
 * Quantitative rollback and restart tests for shared recycle-controller orchestration state.
 */
public class RecycleControllerTransientStateTransactionTest extends neqsim.NeqSimTest {
  private static final double TOLERANCE = 1.0e-12;

  @Test
  void processTransactionRestoresControllerAndCoordinatedAcceleratorInPlace() {
    ProcessSystem process = new ProcessSystem("recycle-controller-transaction");
    process.add(new TransactionalUnit("covered-unit"));

    Recycle first = new Recycle("first");
    first.setPriority(20);
    Recycle second = new Recycle("second");
    second.setPriority(80);
    process.recycleController.addRecycle(first);
    process.recycleController.addRecycle(second);
    process.recycleController.init();
    process.recycleController.setUseCoordinatedAcceleration(true);

    BroydenAccelerator accelerator = process.recycleController.getCoordinatedAccelerator();
    accelerator.setDelayIterations(0);
    accelerateThreeTimes(accelerator);
    int capturedIterations = accelerator.getIterationCount();
    double capturedResidual = accelerator.getResidualNorm();
    double[][] capturedJacobian = accelerator.getInverseJacobian();

    TransientTransactionCoverage coverage = process.getTransientTransactionCoverage();
    assertTrue(coverage.isComplete());
    assertEquals(1, coverage.getProcessElementCount());
    assertEquals(1, coverage.getParticipantCount());

    TransientStepTransaction transaction = process.beginTransientStepTransaction();
    process.recycleController.setCurrentPriorityLevel(999);
    process.recycleController.setUseCoordinatedAcceleration(false);
    accelerator.setRelaxationFactor(0.25);
    accelerator.accelerate(new double[] { 1.4, 2.3 }, new double[] { 1.6, 2.5 });
    transaction.rollback();

    assertEquals(TransientStepTransaction.Status.ROLLED_BACK, transaction.getStatus());
    assertEquals(20, process.recycleController.getCurrentPriorityLevel());
    assertTrue(process.recycleController.isUseCoordinatedAcceleration());
    assertSame(accelerator, process.recycleController.getCoordinatedAccelerator());
    assertEquals(capturedIterations, accelerator.getIterationCount());
    assertEquals(capturedResidual, accelerator.getResidualNorm(), TOLERANCE);
    assertEquals(1.0, accelerator.getRelaxationFactor(), TOLERANCE);
    assertMatrixEquals(capturedJacobian, accelerator.getInverseJacobian());
    assertSame(first, process.recycleController.getRecycles().get(0));
    assertSame(second, process.recycleController.getRecycles().get(1));
  }

  @Test
  void directSnapshotRestoresStructureConfigurationAndForeignSnapshotIsRejected() {
    RecycleController controller = configuredController();
    RecycleController.Snapshot snapshot = controller.captureTransientState();
    List<Recycle> capturedRecycles = controller.getRecycles();
    BroydenAccelerator capturedAccelerator = controller.getCoordinatedAccelerator();
    int capturedIterations = capturedAccelerator.getIterationCount();
    double[][] capturedJacobian = capturedAccelerator.getInverseJacobian();

    controller.clear();
    controller.setUseCoordinatedAcceleration(false);
    controller.restoreTransientState(snapshot);

    assertEquals(2, controller.getRecycleCount());
    assertSame(capturedRecycles.get(0), controller.getRecycles().get(0));
    assertSame(capturedRecycles.get(1), controller.getRecycles().get(1));
    assertEquals(20, controller.getCurrentPriorityLevel());
    assertTrue(controller.isUseCoordinatedAcceleration());
    assertSame(capturedAccelerator, controller.getCoordinatedAccelerator());
    assertEquals(capturedIterations, capturedAccelerator.getIterationCount());
    assertMatrixEquals(capturedJacobian, capturedAccelerator.getInverseJacobian());

    RecycleController foreign = configuredController();
    assertThrows(IllegalArgumentException.class, () -> foreign.restoreTransientState(snapshot));
    assertThrows(NullPointerException.class, () -> controller.restoreTransientState(null));
  }

  @Test
  void javaSerializationPreservesExactCoordinatedSolverContinuation() throws Exception {
    RecycleController original = configuredController();
    RecycleController restarted = roundTrip(original);

    assertEquals(original.getTransientStateIdentity(), restarted.getTransientStateIdentity());
    assertEquals(original.getRecycleCount(), restarted.getRecycleCount());
    assertEquals(original.getCurrentPriorityLevel(), restarted.getCurrentPriorityLevel());
    assertNotNull(restarted.getCoordinatedAccelerator());
    assertEquals(original.getCoordinatedAccelerator().getIterationCount(),
        restarted.getCoordinatedAccelerator().getIterationCount());
    assertEquals(original.getCoordinatedAccelerator().getResidualNorm(),
        restarted.getCoordinatedAccelerator().getResidualNorm(), TOLERANCE);
    assertMatrixEquals(original.getCoordinatedAccelerator().getInverseJacobian(),
        restarted.getCoordinatedAccelerator().getInverseJacobian());

    double[] input = { 1.4, 2.3 };
    double[] output = { 1.6, 2.5 };
    assertArrayEquals(original.getCoordinatedAccelerator().accelerate(input, output),
        restarted.getCoordinatedAccelerator().accelerate(input, output), TOLERANCE);
  }

  private static RecycleController configuredController() {
    RecycleController controller = new RecycleController();
    Recycle first = new Recycle("first");
    first.setPriority(20);
    Recycle second = new Recycle("second");
    second.setPriority(80);
    controller.addRecycle(first);
    controller.addRecycle(second);
    controller.init();
    controller.setUseCoordinatedAcceleration(true);
    controller.getCoordinatedAccelerator().setDelayIterations(0);
    accelerateThreeTimes(controller.getCoordinatedAccelerator());
    return controller;
  }

  private static void accelerateThreeTimes(BroydenAccelerator accelerator) {
    accelerator.accelerate(new double[] { 1.0, 2.0 }, new double[] { 1.2, 2.2 });
    accelerator.accelerate(new double[] { 1.2, 2.2 }, new double[] { 1.3, 2.25 });
    accelerator.accelerate(new double[] { 1.3, 2.25 }, new double[] { 1.35, 2.28 });
  }

  private static void assertMatrixEquals(double[][] expected, double[][] actual) {
    assertEquals(expected.length, actual.length);
    for (int row = 0; row < expected.length; row++) {
      assertArrayEquals(expected[row], actual[row], TOLERANCE);
    }
  }

  @SuppressWarnings("unchecked")
  private static <T extends Serializable> T roundTrip(T value) throws IOException, ClassNotFoundException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(value);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (T) input.readObject();
    }
  }

  /** Minimal covered process element used to isolate shared orchestration rollback. */
  private static final class TransactionalUnit extends ProcessEquipmentBaseClass
      implements TransientStateParticipant<TransactionalUnit.Snapshot> {
    private static final long serialVersionUID = 1000L;
    private final String stateIdentity = UUID.randomUUID().toString();

    private TransactionalUnit(String name) {
      super(name);
    }

    @Override
    public void run(UUID id) {
      setCalculationIdentifier(id);
    }

    @Override
    public String getTransientStateIdentity() {
      return stateIdentity;
    }

    @Override
    public Snapshot captureTransientState() {
      return new Snapshot(getCalculationIdentifier());
    }

    @Override
    public void restoreTransientState(Snapshot snapshot) {
      setCalculationIdentifier(snapshot.calculationIdentifier);
    }

    /** Immutable unit snapshot. */
    private static final class Snapshot implements Serializable {
      private static final long serialVersionUID = 1000L;
      private final UUID calculationIdentifier;

      private Snapshot(UUID calculationIdentifier) {
        this.calculationIdentifier = calculationIdentifier;
      }
    }
  }
}
