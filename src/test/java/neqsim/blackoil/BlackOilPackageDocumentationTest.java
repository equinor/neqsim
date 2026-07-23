package neqsim.blackoil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import neqsim.blackoil.BlackOilPVTTable.Record;
import neqsim.blackoil.io.CMGEOSExporter;
import neqsim.blackoil.io.EclipseEOSExporter;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import org.junit.jupiter.api.Test;

/**
 * Executes the API calls shown in the black-oil package documentation.
 *
 * @author esol
 * @version 1.0
 */
public class BlackOilPackageDocumentationTest {
  /**
   * Verifies table interpolation and the documented standard-volume flash.
   */
  @Test
  void tableInterpolationAndFlashProduceConsistentResults() {
    List<Record> records = Arrays.asList(
        new Record(100.0, 100.0, 1.30, 1.2e-3, 8.0e-3, 1.6e-5, 0.0, 1.01,
            1.0e-3),
        new Record(200.0, 140.0, 1.40, 9.0e-4, 5.0e-3, 1.8e-5, 0.0, 1.02,
            9.0e-4),
        new Record(300.0, 160.0, 1.45, 8.0e-4, 3.0e-3, 2.0e-5, 0.0, 1.03,
            8.0e-4));

    BlackOilPVTTable pvt = new BlackOilPVTTable(records, 250.0);

    assertEquals(130.0, pvt.Rs(175.0), 1.0e-12);
    assertEquals(1.375, pvt.Bo(175.0), 1.0e-12);
    assertEquals(150.0, pvt.RsEffective(300.0), 1.0e-12);

    BlackOilFlash flash = new BlackOilFlash(pvt, 850.0, 0.85, 1000.0);
    BlackOilFlashResult result =
        flash.flash(200.0, 373.15, 1000.0, 150000.0, 500.0);

    assertEquals(1000.0, result.O_std, 1.0e-12);
    assertEquals(10000.0, result.Gf_std, 1.0e-9);
    assertEquals(1400.0, result.V_o, 1.0e-9);
    assertEquals(50.0, result.V_g, 1.0e-9);
    assertEquals(510.0, result.V_w, 1.0e-9);
    assertTrue(result.rho_o > 0.0);
    assertTrue(result.rho_g > 0.0);
    assertTrue(result.rho_w > 0.0);
    assertTrue(result.mu_o > 0.0);
    assertTrue(result.mu_g > 0.0);
    assertTrue(result.mu_w > 0.0);
  }

  /**
   * Verifies compositional conversion and both documented text exporters.
   */
  @Test
  void compositionalConversionProducesExportableTables() {
    SystemInterface oil = new SystemPrEos(373.15, 300.0);
    oil.addComponent("nitrogen", 0.005);
    oil.addComponent("CO2", 0.010);
    oil.addComponent("methane", 0.350);
    oil.addComponent("ethane", 0.070);
    oil.addComponent("propane", 0.065);
    oil.addComponent("i-butane", 0.025);
    oil.addComponent("n-butane", 0.040);
    oil.addComponent("i-pentane", 0.020);
    oil.addComponent("n-pentane", 0.025);
    oil.addComponent("n-hexane", 0.050);
    oil.addComponent("n-heptane", 0.080);
    oil.addComponent("n-octane", 0.080);
    oil.addComponent("n-nonane", 0.060);
    oil.addComponent("nC10", 0.120);
    oil.setMixingRule("classic");
    oil.useVolumeCorrection(true);
    oil.setMultiPhaseCheck(true);

    double[] pressureGrid = {
        25.0, 50.0, 100.0, 150.0, 200.0, 250.0, 300.0
    };
    BlackOilConverter.Result converted =
        BlackOilConverter.convert(oil, 373.15, pressureGrid, 1.01325, 288.15);

    assertTrue(Double.isFinite(converted.rho_o_sc));
    assertTrue(converted.rho_o_sc > 0.0);
    assertTrue(Double.isFinite(converted.rho_g_sc));
    assertTrue(converted.rho_g_sc > 0.0);
    assertTrue(Double.isFinite(converted.pvt.Bo(100.0)));
    assertTrue(converted.pvt.Bo(100.0) > 0.0);
    assertTrue(Double.isFinite(converted.pvt.mu_o(100.0)));
    assertTrue(converted.pvt.mu_o(100.0) > 0.0);

    String eclipseDeck =
        EclipseEOSExporter.toString(converted.pvt, converted.rho_o_sc,
            converted.rho_g_sc, 1000.0);
    String cmgDeck =
        CMGEOSExporter.toString(converted.pvt, converted.rho_o_sc,
            converted.rho_g_sc, 1000.0);

    assertTrue(eclipseDeck.contains("DENSITY"));
    assertTrue(eclipseDeck.contains("PVTO"));
    assertTrue(eclipseDeck.contains("PVTG"));
    assertTrue(cmgDeck.contains("IMEX"));
    assertTrue(cmgDeck.contains("*DENSITY"));
  }
}
