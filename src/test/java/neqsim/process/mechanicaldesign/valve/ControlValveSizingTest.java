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
    ControlValveSizing_IEC_60534 sizemet = new ControlValveSizing_IEC_60534();
    Map<String, Object> result = sizemet.sizeControlValve(
        ControlValveSizing_IEC_60534.FluidType.LIQUID, 1000.0, 0.0, 1.0, 100.0, 200.0, 500000.0,
        400000.0, 10.0, null, null, null, 0.9, 0.8, 0.0, true, true, true, 100);

    assertNotNull(result);
    assertTrue(result.containsKey("FF"));
    assertTrue(result.containsKey("choked"));
    assertTrue(result.containsKey("Kv"));
  }

  @Test
  public void testSizeControlValveGas2() {
    ControlValveSizing_IEC_60534 sizemet = new ControlValveSizing_IEC_60534();
    sizemet.setxT(0.136);

    Map<String, Object> result =
        sizemet.sizeControlValveGas(300.0, 28.97, 1.4, 0.9, 500000.0, 400000.0, 10.0, 100.0);

    assertNotNull(result);
    assertTrue(result.containsKey("Y"));
    assertTrue(result.containsKey("Kv"));

    assertEquals(1217.11932474, (double) result.get("Cv"), 1e-3);
    assertEquals(1052.8713881, (double) result.get("Kv"), 1e-3);
  }

  @Test
  public void testSizeControlValveGas() {
    ControlValveSizing_IEC_60534 sizemet = new ControlValveSizing_IEC_60534();
    Map<String, Object> result = sizemet.sizeControlValve(
        ControlValveSizing_IEC_60534.FluidType.GAS, 300.0, 28.97, 0.01, 1.4, 0.9, 500000.0,
        400000.0, 10.0, null, null, null, 0.9, 0.8, 0.136, true, true, true, 100);

    assertNotNull(result);
    assertTrue(result.containsKey("choked"));
    assertTrue(result.containsKey("Y"));
    assertTrue(result.containsKey("Kv"));
    assertEquals(1212.66914, (double) result.get("Cv"), 1e-3);
    assertEquals(1049.02175413, (double) result.get("Kv"), 1e-3);
  }

  @Test
  public void testInvalidFluidType() {
    ControlValveSizing_IEC_60534 sizemet = new ControlValveSizing_IEC_60534();
    Exception exception = assertThrows(IllegalArgumentException.class, () -> {
      sizemet.sizeControlValve(null, 300.0, 28.97, 0.01, 1.4, 0.9, 500000.0, 400000.0, 10.0, null,
          null, null, 0.9, 0.8, 0.7, true, true, true, 100);
    });

    assertEquals("Invalid fluid type", exception.getMessage());
  }

  @Test
  public void testSizeControlValveGasFullOutput() {
    ControlValveSizing_IEC_60534 sizemet = new ControlValveSizing_IEC_60534();
    Map<String, Object> result = sizemet.sizeControlValve(
        ControlValveSizing_IEC_60534.FluidType.GAS, 300.0, 28.97, 0.01, 1.4, 0.9, 500000.0,
        400000.0, 10.0, null, null, null, 0.9, 0.8, 0.7, true, true, true, 100);

    assertNotNull(result);
    assertTrue(result.containsKey("choked"));
    assertTrue(result.containsKey("Y"));
    assertTrue(result.containsKey("Kv"));
    assertTrue(result.containsKey("Cv"));
    assertEquals(1212.66914777, (double) result.get("Cv"), 1e-3);
  }

  @Test
  public void testSizeControlValveGasChokedFlow() {
    ControlValveSizing_IEC_60534 sizemet = new ControlValveSizing_IEC_60534();
    Map<String, Object> result = sizemet.sizeControlValve(
        ControlValveSizing_IEC_60534.FluidType.GAS, 300.0, 28.97, 0.01, 1.4, 0.9, 500000.0,
        100000.0, 10.0, null, null, null, 0.9, 0.8, 0.7, true, true, true, 100);

    assertNotNull(result);
    assertTrue(result.containsKey("choked"));
    assertTrue((boolean) result.get("choked"));
  }

  @Test
  public void testSizeControlValveGasNonChokedFlow() {
    ControlValveSizing_IEC_60534 sizemet = new ControlValveSizing_IEC_60534();
    Map<String, Object> result = sizemet.sizeControlValve(
        ControlValveSizing_IEC_60534.FluidType.GAS, 300.0, 28.97, 0.01, 1.4, 0.9, 500000.0,
        490000.0, 10.0, null, null, null, 0.9, 0.8, 0.7, true, true, true, 100);

    assertNotNull(result);
    assertTrue(result.containsKey("choked"));
    assertTrue(!(boolean) result.get("choked"));
  }
}
