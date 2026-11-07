package neqsim.process.engineering;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.engineering.pid.PidDesignModel;
import neqsim.process.engineering.pid.PidNativeControlSystem;
import neqsim.process.engineering.pid.PidSimulationBindings;
import neqsim.process.util.scenario.CompressorAutomationStudy.Design;

/**
 * Protects output-path validation and the boundary between serialized data and runtime verification authority.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class CompressorAutomationHealthContractTest {
  /** A filesystem root cannot be used as a new workflow output directory or reach parent-directory creation. */
  @Test
  void rejectsParentlessOutputBeforeConstructingTheNativeTrain() {
    Path root = Paths.get("").toAbsolutePath().getRoot();
    IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
        () -> CompressorAutomationWorkflow.run(new Design(), root));
    assertTrue(failure.getMessage().contains("must have a parent"));
  }

  /**
   * Runtime receipts cannot be restored without their governed source identities and receive execution authority.
   *
   * @throws Exception if the private runtime verifier fixture cannot be constructed or serialized
   */
  @Test
  void rejectsDeserializationOfRuntimeOnlyReceiptObservers() throws Exception {
    Class<?> observerType = Class.forName(CompressorAutomationWorkflow.class.getName() + "$ReceiptObserver");
    Constructor<?> constructor = observerType.getDeclaredConstructor(EngineeringProject.class, PidDesignModel.class,
        PidSimulationBindings.class, PidNativeControlSystem.ConfiguredControlSystem.class, Map.class);
    constructor.setAccessible(true);
    Object observer = constructor.newInstance(null, null, null, null, null);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(observer);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      InvalidObjectException failure = assertThrows(InvalidObjectException.class, input::readObject);
      assertTrue(failure.getMessage().contains("rebuilt from governed inputs"));
    }
  }
}
