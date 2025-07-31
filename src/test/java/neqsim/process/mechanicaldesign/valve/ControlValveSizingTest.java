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
        400000.0, 10.0, null, null, null, 0.9, 0.8, 0.0, true, true, true);

    assertNotNull(result);
    assertTrue(result.containsKey("FF"));
    assertTrue(result.containsKey("choked"));
    assertTrue(result.containsKey("Kv"));
  }

  @Test
  public void testSizeControlValveGas2() {
    ControlValveSizing sizemet = new ControlValveSizing();

    Map<String, Object> result =
        sizemet.sizeControlValveGas(300.0, 28.97, 1.4, 0.9, 500000.0, 400000.0, 10.0, 0.136);

    assertNotNull(result);
    assertTrue(result.containsKey("Y"));
    assertTrue(result.containsKey("Kv"));

    assertEquals(1003.662306, (double) result.get("Cv"), 1e-3);
    assertEquals(868.219988, (double) result.get("Kv"), 1e-3);
  }

  @Test
  public void testSizeControlValveGas() {
    ControlValveSizing_IEC_60534 sizemet = new ControlValveSizing_IEC_60534();
    Map<String, Object> result = sizemet.sizeControlValve(
        ControlValveSizing_IEC_60534.FluidType.GAS, 300.0, 28.97, 0.01, 1.4, 0.9, 500000.0,
        400000.0, 10.0, null, null, null, 0.9, 0.8, 0.136, true, true, true);

    assertNotNull(result);
    assertTrue(result.containsKey("choked"));
    assertTrue(result.containsKey("Y"));
    assertTrue(result.containsKey("Kv"));
    assertEquals(1217.1193247, (double) result.get("Cv"), 1e-3);
    assertEquals(1052.871388185, (double) result.get("Kv"), 1e-3);
  }

  @Test
  public void testInvalidFluidType() {
    ControlValveSizing_IEC_60534 sizemet = new ControlValveSizing_IEC_60534();
    Exception exception = assertThrows(IllegalArgumentException.class, () -> {
      sizemet.sizeControlValve(null, 300.0, 28.97, 0.01, 1.4, 0.9, 500000.0, 400000.0, 10.0, null,
          null, null, 0.9, 0.8, 0.7, true, true, true);
    });

    assertEquals("Invalid fluid type", exception.getMessage());
  }

  @Test
  public void testSizeControlValveGasFullOutput() {
    ControlValveSizing_IEC_60534 sizemet = new ControlValveSizing_IEC_60534();
    Map<String, Object> result =
        sizemet.sizeControlValve(ControlValveSizing_IEC_60534.FluidType.GAS, 300.0, 28.97, 0.01,
            1.4, 0.9, 500000.0, 400000.0, 10.0, null, null, null, 0.9, 0.8, 0.7, true, true, true);

    assertNotNull(result);
    assertTrue(result.containsKey("choked"));
    assertTrue(result.containsKey("Y"));
    assertTrue(result.containsKey("Kv"));
    assertTrue(result.containsKey("Cv"));
    assertEquals(739.540647247, (double) result.get("Cv"), 1e-3);
  }

  @Test
  public void testSizeControlValveGasChokedFlow() {
    ControlValveSizing_IEC_60534 sizemet = new ControlValveSizing_IEC_60534();
    Map<String, Object> result =
        sizemet.sizeControlValve(ControlValveSizing_IEC_60534.FluidType.GAS, 300.0, 28.97, 0.01,
            1.4, 0.9, 500000.0, 100000.0, 10.0, null, null, null, 0.9, 0.8, 0.7, true, true, true);

    assertNotNull(result);
    assertTrue(result.containsKey("choked"));
    assertTrue((boolean) result.get("choked"));
  }

  @Test
  public void testSizeControlValveGasNonChokedFlow() {
    ControlValveSizing_IEC_60534 sizemet = new ControlValveSizing_IEC_60534();
    Map<String, Object> result =
        sizemet.sizeControlValve(ControlValveSizing_IEC_60534.FluidType.GAS, 300.0, 28.97, 0.01,
            1.4, 0.9, 500000.0, 490000.0, 10.0, null, null, null, 0.9, 0.8, 0.7, true, true, true);

    assertNotNull(result);
    assertTrue(result.containsKey("choked"));
    assertTrue(!(boolean) result.get("choked"));
  }
}
