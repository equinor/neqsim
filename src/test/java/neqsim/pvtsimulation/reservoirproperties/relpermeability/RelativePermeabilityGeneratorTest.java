package neqsim.pvtsimulation.reservoirproperties.relpermeability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for relative permeability table generation.
 */
class RelativePermeabilityGeneratorTest {

  // ========== COREY MODEL TESTS ==========

  @Test
  void testCoreyLinearExponent() {
    // Corey exponent = 1 should give linear Kr
    double kr = RelativePermeabilityGenerator.coreyCurve(0.5, 1.0);
    assertEquals(0.5, kr, 1e-10);
  }

  @Test
  void testCoreyQuadraticExponent() {
    double kr = RelativePermeabilityGenerator.coreyCurve(0.5, 2.0);
    assertEquals(0.25, kr, 1e-10);
  }

  @Test
  void testCoreyEndpoints() {
    assertEquals(0.0, RelativePermeabilityGenerator.coreyCurve(0.0, 2.0), 1e-10);
    assertEquals(1.0, RelativePermeabilityGenerator.coreyCurve(1.0, 2.0), 1e-10);
  }

  @Test
  void testCoreyClamping() {
    // Negative saturation should be clamped to 0
    assertEquals(0.0, RelativePermeabilityGenerator.coreyCurve(-0.1, 2.0), 1e-10);
    // Saturation > 1 should be clamped to 1
    assertEquals(1.0, RelativePermeabilityGenerator.coreyCurve(1.1, 2.0), 1e-10);
  }

  // ========== LET MODEL TESTS ==========

  @Test
  void testLetEndpoints() {
    assertEquals(0.0, RelativePermeabilityGenerator.letCurve(0.0, 2.0, 1.0, 2.0), 1e-10);
    assertEquals(1.0, RelativePermeabilityGenerator.letCurve(1.0, 2.0, 1.0, 2.0), 1e-10);
  }

  @Test
  void testLetMidpoint() {
    // L=E=T=1: should give Sn/(Sn + 1-Sn) = Sn
    double kr = RelativePermeabilityGenerator.letCurve(0.5, 1.0, 1.0, 1.0);
    assertEquals(0.5, kr, 1e-10);
  }

  @Test
  void testLetSymmetry() {
    // With L=T and E=1, curve should be symmetric: f(0.5) = 0.5
    double kr = RelativePermeabilityGenerator.letCurve(0.5, 2.0, 1.0, 2.0);
    assertEquals(0.5, kr, 1e-10, "LET with L=T and E=1 should be symmetric");
  }

  @Test
  void testLetMonotonicity() {
    // Kr should increase monotonically
    double prev = 0.0;
    for (int i = 1; i <= 10; i++) {
      double sn = i / 10.0;
      double kr = RelativePermeabilityGenerator.letCurve(sn, 2.5, 1.25, 1.75);
      assertTrue(kr >= prev, "LET Kr should be monotonically increasing at " + sn);
      prev = kr;
    }
  }

  // ========== SWOF TABLE TESTS ==========

  @Test
  void testSWOFCoreySaturationRange() {
    RelativePermeabilityGenerator gen = new RelativePermeabilityGenerator();
    gen.setTableType(RelPermTableType.SWOF);
    gen.setModelFamily(RelPermModelFamily.COREY);
    gen.setSwc(0.15);
    gen.setSorw(0.20);
    gen.setKroMax(1.0);
    gen.setKrwMax(0.25);
    gen.setNo(2.5);
    gen.setNw(1.5);
    gen.setRows(25);

    Map<String, double[]> table = gen.generate();

    assertNotNull(table.get("Sw"));
    assertNotNull(table.get("Krw"));
    assertNotNull(table.get("Krow"));
    assertNotNull(table.get("Pcow"));

    double[] sw = table.get("Sw");
    double[] krw = table.get("Krw");
    double[] krow = table.get("Krow");

    assertEquals(25, sw.length);

    // Saturation range check
    assertEquals(0.15, sw[0], 1e-10, "First Sw should be Swc");
    assertEquals(0.80, sw[24], 1e-10, "Last Sw should be 1-Sorw");

    // Endpoint kr check
    assertEquals(0.0, krw[0], 1e-10, "Krw at Swc should be 0");
    assertEquals(0.25, krw[24], 1e-10, "Krw at 1-Sorw should be KrwMax");
    assertEquals(1.0, krow[0], 1e-10, "Krow at Swc should be KroMax");
    assertEquals(0.0, krow[24], 1e-10, "Krow at 1-Sorw should be 0");
  }

  @Test
  void testSWOFCoreyMonotonicity() {
    RelativePermeabilityGenerator gen = new RelativePermeabilityGenerator();
    gen.setTableType(RelPermTableType.SWOF);
    gen.setModelFamily(RelPermModelFamily.COREY);
    gen.setSwc(0.10);
    gen.setSorw(0.15);
    gen.setNo(3.0);
    gen.setNw(2.0);
    gen.setRows(50);

    Map<String, double[]> table = gen.generate();
    double[] krw = table.get("Krw");
    double[] krow = table.get("Krow");

    // Krw should increase monotonically
    for (int i = 1; i < krw.length; i++) {
      assertTrue(krw[i] >= krw[i - 1], "Krw should be monotonically increasing at index " + i);
    }

    // Krow should decrease monotonically
    for (int i = 1; i < krow.length; i++) {
      assertTrue(krow[i] <= krow[i - 1], "Krow should be monotonically decreasing at index " + i);
    }
  }

  @Test
  void testSWOFLetTable() {
    RelativePermeabilityGenerator gen = new RelativePermeabilityGenerator();
    gen.setTableType(RelPermTableType.SWOF);
    gen.setModelFamily(RelPermModelFamily.LET);
    gen.setSwc(0.15);
    gen.setSorw(0.20);
    gen.setKroMax(1.0);
    gen.setKrwMax(0.3);
    gen.setLo(2.5);
    gen.setEo(1.25);
    gen.setTo(1.75);
    gen.setLw(1.2);
    gen.setEw(1.5);
    gen.setTw(2.0);
    gen.setRows(30);

    Map<String, double[]> table = gen.generate();

    double[] krw = table.get("Krw");
    double[] krow = table.get("Krow");

    assertEquals(0.0, krw[0], 1e-10);
    assertEquals(0.3, krw[29], 1e-10);
    assertEquals(1.0, krow[0], 1e-10);
    assertEquals(0.0, krow[29], 1e-10);
  }

  // ========== SGOF TABLE TESTS ==========

  @Test
  void testSGOFCoreyTable() {
    RelativePermeabilityGenerator gen = new RelativePermeabilityGenerator();
    gen.setTableType(RelPermTableType.SGOF);
    gen.setModelFamily(RelPermModelFamily.COREY);
    gen.setSwc(0.20);
    gen.setSorg(0.15);
    gen.setKroMax(1.0);
    gen.setKrgMax(0.8);
    gen.setNg(2.0);
    gen.setNog(3.0);
    gen.setRows(20);

    Map<String, double[]> table = gen.generate();

    double[] sg = table.get("Sg");
    double[] krg = table.get("Krg");
    double[] krog = table.get("Krog");

    assertEquals(20, sg.length);
    assertEquals(0.0, sg[0], 1e-10, "First Sg should be 0");
    assertEquals(0.65, sg[19], 1e-2, "Last Sg should be 1-Swc-Sorg");

    assertEquals(0.0, krg[0], 1e-10, "Krg at Sg=0 should be 0");
    assertEquals(0.8, krg[19], 1e-10, "Krg at max Sg should be KrgMax");
    assertEquals(1.0, krog[0], 1e-10, "Krog at Sg=0 should be KroMax");
    assertEquals(0.0, krog[19], 1e-10, "Krog at max Sg should be 0");
  }

  // ========== SOF3 TABLE TESTS ==========

  @Test
  void testSOF3Table() {
    RelativePermeabilityGenerator gen = new RelativePermeabilityGenerator();
    gen.setTableType(RelPermTableType.SOF3);
    gen.setSwc(0.20);
    gen.setSorw(0.15);
    gen.setSorg(0.10);
    gen.setKroMax(1.0);
    gen.setNo(2.0);
    gen.setNog(2.0);
    gen.setRows(15);

    Map<String, double[]> table = gen.generate();

    assertNotNull(table.get("So"));
    assertNotNull(table.get("Krow"));
    assertNotNull(table.get("Krog"));
    assertEquals(15, table.get("So").length);

    // Endpoints
    assertEquals(0.0, table.get("Krow")[0], 1e-10);
    assertEquals(0.0, table.get("Krog")[0], 1e-10);
    assertEquals(1.0, table.get("Krow")[14], 1e-10);
    assertEquals(1.0, table.get("Krog")[14], 1e-10);
  }

  // ========== ECLIPSE KEYWORD EXPORT ==========

  @Test
  void testEclipseKeywordOutput() {
    RelativePermeabilityGenerator gen = new RelativePermeabilityGenerator();
    gen.setTableType(RelPermTableType.SWOF);
    gen.setModelFamily(RelPermModelFamily.COREY);
    gen.setSwc(0.15);
    gen.setSorw(0.20);
    gen.setNo(2.0);
    gen.setNw(2.0);
    gen.setRows(5);

    String eclipseOutput = gen.toEclipseKeyword();

    assertTrue(eclipseOutput.startsWith("SWOF"), "Output should start with keyword");
    assertTrue(eclipseOutput.contains("Sw"), "Should contain Sw column header");
    assertTrue(eclipseOutput.contains("Krw"), "Should contain Krw column header");
    assertTrue(eclipseOutput.contains("Krow"), "Should contain Krow column header");
    assertTrue(eclipseOutput.endsWith("/\n"), "Should end with / terminator");
  }

  // ========== VALIDATION TESTS ==========

  @Test
  void testMinimumRowsValidation() {
    RelativePermeabilityGenerator gen = new RelativePermeabilityGenerator();
    assertThrows(IllegalArgumentException.class, () -> gen.setRows(2));
  }

  @Test
  void testKrValuesInRange() {
    RelativePermeabilityGenerator gen = new RelativePermeabilityGenerator();
    gen.setTableType(RelPermTableType.SWOF);
    gen.setSwc(0.15);
    gen.setSorw(0.20);
    gen.setKroMax(0.8);
    gen.setKrwMax(0.3);
    gen.setNo(3.0);
    gen.setNw(2.5);
    gen.setRows(50);

    Map<String, double[]> table = gen.generate();
    double[] krw = table.get("Krw");
    double[] krow = table.get("Krow");

    for (int i = 0; i < krw.length; i++) {
      assertTrue(krw[i] >= 0.0 && krw[i] <= 0.3 + 1e-10,
          "Krw should be in [0, KrwMax] at index " + i + ": " + krw[i]);
      assertTrue(krow[i] >= 0.0 && krow[i] <= 0.8 + 1e-10,
          "Krow should be in [0, KroMax] at index " + i + ": " + krow[i]);
    }
  }

  @Test
  void testCriticalWaterSaturation() {
    // When Swcr > Swc, water flow starts later
    RelativePermeabilityGenerator gen = new RelativePermeabilityGenerator();
    gen.setTableType(RelPermTableType.SWOF);
    gen.setSwc(0.10);
    gen.setSwcr(0.20);
    gen.setSorw(0.20);
    gen.setKrwMax(0.5);
    gen.setNw(2.0);
    gen.setNo(2.0);
    gen.setRows(50);

    Map<String, double[]> table = gen.generate();
    double[] sw = table.get("Sw");
    double[] krw = table.get("Krw");

    // Krw should be zero (or near zero) until Sw reaches Swcr
    for (int i = 0; i < sw.length; i++) {
      if (sw[i] < 0.19) {
        assertTrue(krw[i] < 0.02,
            "Krw should be near zero below Swcr at Sw=" + sw[i] + ": " + krw[i]);
      }
    }
  }

  // ========== GETTER/SETTER TESTS ==========

  @Test
  void testGettersSetters() {
    RelativePermeabilityGenerator gen = new RelativePermeabilityGenerator();

    gen.setSwc(0.15);
    assertEquals(0.15, gen.getSwc(), 1e-10);

    gen.setSorw(0.20);
    assertEquals(0.20, gen.getSorw(), 1e-10);

    gen.setSorg(0.10);
    assertEquals(0.10, gen.getSorg(), 1e-10);

    gen.setSgcr(0.05);
    assertEquals(0.05, gen.getSgcr(), 1e-10);

    gen.setKroMax(0.9);
    assertEquals(0.9, gen.getKroMax(), 1e-10);

    gen.setKrwMax(0.3);
    assertEquals(0.3, gen.getKrwMax(), 1e-10);

    gen.setKrgMax(0.7);
    assertEquals(0.7, gen.getKrgMax(), 1e-10);

    gen.setNo(3.5);
    assertEquals(3.5, gen.getNo(), 1e-10);

    gen.setNw(2.5);
    assertEquals(2.5, gen.getNw(), 1e-10);

    gen.setNg(1.5);
    assertEquals(1.5, gen.getNg(), 1e-10);

    gen.setNog(2.8);
    assertEquals(2.8, gen.getNog(), 1e-10);

    gen.setLo(2.5);
    assertEquals(2.5, gen.getLo(), 1e-10);
    gen.setEo(1.25);
    assertEquals(1.25, gen.getEo(), 1e-10);
    gen.setTo(1.75);
    assertEquals(1.75, gen.getTo(), 1e-10);

    gen.setLw(1.2);
    assertEquals(1.2, gen.getLw(), 1e-10);
    gen.setEw(0.8);
    assertEquals(0.8, gen.getEw(), 1e-10);
    gen.setTw(2.1);
    assertEquals(2.1, gen.getTw(), 1e-10);

    gen.setLg(1.5);
    assertEquals(1.5, gen.getLg(), 1e-10);
    gen.setEg(1.1);
    assertEquals(1.1, gen.getEg(), 1e-10);
    gen.setTg(2.3);
    assertEquals(2.3, gen.getTg(), 1e-10);

    gen.setLog(2.2);
    assertEquals(2.2, gen.getLog(), 1e-10);
    gen.setEog(0.9);
    assertEquals(0.9, gen.getEog(), 1e-10);
    gen.setTog(1.8);
    assertEquals(1.8, gen.getTog(), 1e-10);
  }
}
