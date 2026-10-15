package neqsim.thermodynamicoperations.flashops;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemThermo;
import neqsim.thermo.system.SystemInterface;

/**
 * Detached validation copies shared by the identical benchmark harness on every compared revision.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
final class FlashBenchmarkStateCopy {
  /** Prevents construction of this benchmark utility. */
  private FlashBenchmarkStateCopy() {
  }

  /**
   * Reads the existing requested root array on revisions predating its public accessor.
   *
   * @param fluid benchmark system
   * @param phase active phase index
   * @return requested root name, or unavailable for a different system implementation
   */
  static String requestedRoot(SystemInterface fluid, int phase) {
    try {
      Object root = fluid.getClass().getMethod("getPhaseType", int.class).invoke(fluid, phase);
      return root == null ? "unavailable" : root.toString();
    } catch (NoSuchMethodException unavailableOnOlderRevision) {
      // The requested-root array predates the public accessor; inspect it without guessing from phase labels.
    } catch (ReflectiveOperationException | SecurityException failure) {
      return "unavailable";
    }
    if (!(fluid instanceof SystemThermo)) {
      return "unavailable";
    }
    try {
      Field field = SystemThermo.class.getDeclaredField("phaseType");
      field.setAccessible(true);
      PhaseType[] requested = (PhaseType[]) field.get(fluid);
      return requested[fluid.getPhaseIndex(phase)].toString();
    } catch (ReflectiveOperationException | SecurityException failure) {
      return "unavailable";
    }
  }

  /**
   * Copies the entire serializable graph without relying on revision-dependent phase clone ownership.
   *
   * <p>
   * This operation belongs outside the measured interval. The snapshot can rebuild transient derivative workspaces on
   * this copy without altering the next timed operation's warm state. A serialization failure is a qualification
   * failure; falling back to a shallow clone would invalidate that guarantee.
   * </p>
   *
   * @param fluid returned flash endpoint
   * @return independent graph with the same returned thermodynamic state
   * @throws IllegalStateException if the complete graph cannot be serialized and restored
   */
  static SystemInterface copy(SystemInterface fluid) {
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
        output.writeObject(fluid);
      }
      try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
        return (SystemInterface) input.readObject();
      }
    } catch (IOException | ClassNotFoundException failure) {
      throw new IllegalStateException("Benchmark validation requires an independent serialized fluid copy", failure);
    }
  }
}
