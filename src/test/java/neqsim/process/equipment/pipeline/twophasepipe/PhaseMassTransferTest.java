package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

/** Unit tests for the immutable phase-resolved mass-transfer result. */
class PhaseMassTransferTest {
  @Test
  void testRejectsNonConservativeSources() {
    assertThrows(IllegalArgumentException.class, () -> new PhaseMassTransfer(-0.2, 0.1, 0.0, true, true, null));
  }

  @Test
  void testSerializationPreservesSourcesAndMetadata() throws Exception {
    PhaseMassTransfer expected = new PhaseMassTransfer(-0.2, 0.05, 0.15, true, true, null);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ObjectOutputStream output = new ObjectOutputStream(bytes);
    output.writeObject(expected);
    output.close();
    ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    PhaseMassTransfer actual = (PhaseMassTransfer) input.readObject();
    input.close();

    assertEquals(expected.getGasSourceKgPerMetreSecond(), actual.getGasSourceKgPerMetreSecond(), 0.0);
    assertEquals(expected.getOilSourceKgPerMetreSecond(), actual.getOilSourceKgPerMetreSecond(), 0.0);
    assertEquals(expected.getWaterSourceKgPerMetreSecond(), actual.getWaterSourceKgPerMetreSecond(), 0.0);
    assertTrue(actual.isFlashConverged());
    assertTrue(actual.isApplicable());
    assertEquals(0.0, actual.getTotalSourceKgPerMetreSecond(), 1.0e-15);
  }

  @Test
  void testZeroResultCanReportInapplicableFlash() {
    PhaseMassTransfer result = PhaseMassTransfer.zero(false, false, "flash failed");

    assertFalse(result.isFlashConverged());
    assertFalse(result.isApplicable());
    assertEquals("flash failed", result.getErrorMessage());
    assertEquals(0.0, result.getTotalSourceKgPerMetreSecond(), 0.0);
  }
}
