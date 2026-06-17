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

    assertEquals(6076.08710, (double) result.get("Cv"), 1e-1);
    assertEquals(5256.13071, (double) result.get("Kv"), 1e-1);
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
    assertEquals(6053.87098, (double) result.get("Cv"), 1e-1);
    assertEquals(5236.91261, (double) result.get("Kv"), 1e-1);
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
    assertEquals(6053.87098, (double) result.get("Cv"), 1e-1);
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

  /**
   * Regression test for GitHub issue #1918: gas valve Cv was severely underestimated because actual
   * volumetric flow was used instead of standard volumetric flow per IEC 60534-2-1.
   *
   * Test case from issue: 50 bara, 25C, 90% CH4 / 10% C2H6, 10000 kg/hr. Expected Cv ~16.2 per IEC
   * 60534 (verified against Python 'fluids' library).
   */
  @Test
  public void testGasValveSizingIssue1918() {
    // Conditions from issue: P1=50 bara, P2=25 bara, T=25C, MW=17.45, Z~1.0
    double T = 298.15; // K
    double MW = 17.45; // g/mol (90% CH4, 10% C2H6 approx)
    double gamma = 1.286;
    double Z = 1.0;
    double P1 = 50.0e5; // Pa
    double P2 = 25.0e5; // Pa

    // Convert 10000 kg/hr to actual m3/s at inlet conditions
    double massFlow_kg_s = 10000.0 / 3600.0;
    double MW_kg_mol = MW / 1000.0;
    double n_mol_s = massFlow_kg_s / MW_kg_mol;
    double R = 8.314; // J/(mol*K)
    double Q_actual_m3s = n_mol_s * Z * R * T / P1;

    ControlValveSizing_IEC_60534 sizer = new ControlValveSizing_IEC_60534();
    sizer.setxT(0.75);
    sizer.setFL(0.9);

    Map<String, Object> result =
        sizer.sizeControlValveGas(T, MW, gamma, Z, P1, P2, Q_actual_m3s, 100.0);

    double Cv = (double) result.get("Cv");
    // IEC 60534 correct result: Cv ~ 16.2 (verified with Python fluids library)
    // Old buggy result was ~0.36 (97.8% too low)
    assertEquals(16.2, Cv, 0.5);
  }
}
