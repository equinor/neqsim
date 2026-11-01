import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import neqsim.process.equipment.pipeline.twophasepipe.closure.GeometryCalculator;

/**
 * Serialize the unchanged four-field pressure geometry for cross-version binary comparison.
 *
 * @author NeqSim
 * @version 1.0
 */
public final class SerializationProbe {
  /**
   * Write a canonical legacy-compatible object without invoking new methods.
   *
   * @param args binary output path
   * @throws Exception if output fails
   */
  public static void main(String[] args) throws Exception {
    try (ObjectOutputStream output = new ObjectOutputStream(Files.newOutputStream(Paths.get(args[0])))) {
      output.writeObject(new GeometryCalculator().calculateStratifiedPressureMoments(0.3, 1.0148));
    }
  }
}
