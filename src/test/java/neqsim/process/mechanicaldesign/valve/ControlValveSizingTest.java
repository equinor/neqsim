package neqsim.process.mechanicaldesign.valve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ControlValveSizingTest {
  @Test
  public void testSizeControlValveLiquid() {
    Map<String, Object> result = ControlValveSizing_IEC_60534.sizeControlValve(
        ControlValveSizing_IEC_60534.FluidType.LIQUID, 1000.0, 0.0, 1.0, 100.0, 200.0, 500000.0,
        400000.0, 10.0, null, null, null, 0.9, 0.8, 0.0, true, true, true);

    assertNotNull(result);
    assertTrue(result.containsKey("FF"));
    assertTrue(result.containsKey("choked"));
    assertTrue(result.containsKey("Kv"));
  }

  @Test
  public void testSizeControlValveGas() {
    Map<String, Object> result = ControlValveSizing_IEC_60534.sizeControlValve(
        ControlValveSizing_IEC_60534.FluidType.GAS, 300.0, 28.97, 0.01, 1.4, 0.9, 500000.0,
        400000.0, 10.0, null, null, null, 0.9, 0.8, 0.7, true, true, true);

    assertNotNull(result);
    assertTrue(result.containsKey("choked"));
    assertTrue(result.containsKey("Y"));
    assertTrue(result.containsKey("Kv"));
  }

  @Test
  public void testInvalidFluidType() {
    Exception exception = assertThrows(IllegalArgumentException.class, () -> {
      ControlValveSizing_IEC_60534.sizeControlValve(null, 300.0, 28.97, 0.01, 1.4, 0.9, 500000.0,
          400000.0, 10.0, null, null, null, 0.9, 0.8, 0.7, true, true, true);
    });

    assertEquals("Invalid fluid type", exception.getMessage());
  }

  @Test
  public void testSizeControlValveGasFullOutput() {
    Map<String, Object> result = ControlValveSizing_IEC_60534.sizeControlValve(
        ControlValveSizing_IEC_60534.FluidType.GAS, 300.0, 28.97, 0.01, 1.4, 0.9, 500000.0,
        400000.0, 10.0, null, null, null, 0.9, 0.8, 0.7, true, true, true);

    assertNotNull(result);
    assertTrue(result.containsKey("choked"));
    assertTrue(result.containsKey("Y"));
    assertTrue(result.containsKey("Kv"));
    assertTrue(result.containsKey("Cv"));
  }

  @Test
  public void testSizeControlValveGasChokedFlow() {
    Map<String, Object> result = ControlValveSizing_IEC_60534.sizeControlValve(
        ControlValveSizing_IEC_60534.FluidType.GAS, 300.0, 28.97, 0.01, 1.4, 0.9, 500000.0,
        100000.0, 10.0, null, null, null, 0.9, 0.8, 0.7, true, true, true);

    assertNotNull(result);
    assertTrue(result.containsKey("choked"));
    assertTrue((boolean) result.get("choked"));
  }

  @Test
  public void testSizeControlValveGasNonChokedFlow() {
    Map<String, Object> result = ControlValveSizing_IEC_60534.sizeControlValve(
        ControlValveSizing_IEC_60534.FluidType.GAS, 300.0, 28.97, 0.01, 1.4, 0.9, 500000.0,
        490000.0, 10.0, null, null, null, 0.9, 0.8, 0.7, true, true, true);

    assertNotNull(result);
    assertTrue(result.containsKey("choked"));
    assertTrue(!(boolean) result.get("choked"));
  }
}
