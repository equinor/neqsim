package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Regression tests for audited sulfur-recovery component data. */
public class SulfurComponentDataTest extends neqsim.NeqSimTest {
  /** Carbonyl sulfide must use its own identity and NIST-JANAF ideal-gas reference data. */
  @Test
  void testCarbonylSulfideReferenceData() {
    SystemInterface system = new SystemSrkEos(298.15, 1.0);
    system.addComponent("COS", 1.0);
    ComponentInterface cos = system.getComponent("COS");

    assertEquals("463-58-1", cos.getCASnumber());
    assertEquals(-138407.1, cos.getIdealGasEnthalpyOfFormation(), 0.1);
    assertEquals(231.57, cos.getIdealGasAbsoluteEntropy(), 0.01);
    assertEquals(-165650.0, cos.getGibbsEnergyOfFormation(), 0.1);
  }

  /** S8 must no longer carry methane's CAS number or zeroed gas thermochemistry. */
  @Test
  void testS8ReferenceData() {
    SystemInterface system = new SystemSrkEos(298.15, 1.0);
    system.addComponent("S8", 1.0);
    ComponentInterface sulfur = system.getComponent("S8");

    assertEquals("10544-50-0", sulfur.getCASnumber());
    assertEquals(100420.0, sulfur.getIdealGasEnthalpyOfFormation(), 0.1);
    assertEquals(430.31, sulfur.getIdealGasAbsoluteEntropy(), 0.01);
    assertEquals(48170.0, sulfur.getGibbsEnergyOfFormation(), 0.1);
  }

  /** Gibbs-reactor reference rows must remain aligned with the audited component data. */
  @Test
  void testGibbsReactorSulfurRows() throws Exception {
    InputStream input = getClass().getResourceAsStream(
        "/data/GibbsReactDatabase/GibbsReactDatabase.csv");
    assertNotNull(input);
    String s8Row = null;
    String cosRow = null;
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(input, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.startsWith("S8;")) {
          s8Row = line;
        } else if (line.startsWith("COS;")) {
          cosRow = line;
        }
      }
    }
    assertNotNull(s8Row);
    assertNotNull(cosRow);
    String[] s8 = s8Row.split(";");
    String[] cos = cosRow.split(";");
    assertEquals("100,42", s8[13]);
    assertEquals("48,17", s8[14]);
    assertEquals("430,31", s8[15]);
    assertEquals("-138,407", cos[13]);
    assertEquals("-165,65", cos[14]);
    assertEquals("231,57", cos[15]);
  }
}
