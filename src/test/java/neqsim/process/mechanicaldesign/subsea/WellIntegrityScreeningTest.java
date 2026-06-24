package neqsim.process.mechanicaldesign.subsea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link WellIntegrityScreening}.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class WellIntegrityScreeningTest {

  /**
   * A normal annulus below the elevated band screens as acceptable.
   */
  @Test
  void testNormalAnnulusAcceptable() {
    WellIntegrityScreening screening = new WellIntegrityScreening("WELL-A1");
    screening.setWellType("OIL_PRODUCER");
    screening.addAnnulus(new WellIntegrityScreening.AnnulusReading("A", 2.0, 80.0).setBleedsToZero(true));
    WellIntegrityScreening.IntegrityDisposition disposition = screening.screen();
    assertEquals(WellIntegrityScreening.IntegrityDisposition.ACCEPTABLE, disposition);
    assertEquals(WellIntegrityScreening.AnnulusClassification.NORMAL, screening.getAnnulusResults().get("A"));
  }

  /**
   * Pressure at or above MAASP drives an intervention disposition.
   */
  @Test
  void testExceedsMaaspRequiresIntervention() {
    WellIntegrityScreening screening = new WellIntegrityScreening("WELL-A1");
    screening.setWellType("OIL_PRODUCER");
    screening.addAnnulus(new WellIntegrityScreening.AnnulusReading("A", 85.0, 80.0));
    WellIntegrityScreening.IntegrityDisposition disposition = screening.screen();
    assertEquals(WellIntegrityScreening.IntegrityDisposition.INTERVENTION_REQUIRED, disposition);
    assertEquals(WellIntegrityScreening.AnnulusClassification.EXCEEDS_MAASP, screening.getAnnulusResults().get("A"));
  }

  /**
   * An annulus that rebuilds after bleed-down is flagged as sustained casing pressure.
   */
  @Test
  void testSustainedCasingPressureDetected() {
    WellIntegrityScreening screening = new WellIntegrityScreening("WELL-A1");
    screening.addAnnulus(
        new WellIntegrityScreening.AnnulusReading("A", 30.0, 80.0).setBleedsToZero(false).setRebuildsAfterBleed(true));
    WellIntegrityScreening.IntegrityDisposition disposition = screening.screen();
    assertEquals(WellIntegrityScreening.IntegrityDisposition.INTERVENTION_REQUIRED, disposition);
    assertEquals(WellIntegrityScreening.AnnulusClassification.SUSTAINED_CASING_PRESSURE,
        screening.getAnnulusResults().get("A"));
  }

  /**
   * Elevated but sub-MAASP pressure screens as monitor.
   */
  @Test
  void testElevatedAnnulusMonitor() {
    WellIntegrityScreening screening = new WellIntegrityScreening("WELL-A1");
    screening.addAnnulus(new WellIntegrityScreening.AnnulusReading("A", 40.0, 80.0).setBleedsToZero(true));
    WellIntegrityScreening.IntegrityDisposition disposition = screening.screen();
    assertEquals(WellIntegrityScreening.IntegrityDisposition.MONITOR, disposition);
    assertEquals(WellIntegrityScreening.AnnulusClassification.ELEVATED, screening.getAnnulusResults().get("A"));
  }

  /**
   * A failed barrier element escalates the disposition to intervention.
   */
  @Test
  void testFailedBarrierEscalates() {
    WellBarrierSchematic schematic = new WellBarrierSchematic();
    schematic.setWellType("OIL_PRODUCER");
    BarrierEnvelope primary = new BarrierEnvelope("Primary");
    BarrierElement tubing = new BarrierElement(BarrierElement.ElementType.TUBING, "Tubing");
    tubing.setStatus(BarrierElement.Status.FAILED);
    primary.addElement(tubing);
    primary.addElement(new BarrierElement(BarrierElement.ElementType.DHSV, "SCSSV"));
    schematic.setPrimaryEnvelope(primary);

    WellIntegrityScreening screening = new WellIntegrityScreening("WELL-A1");
    screening.setWellType("OIL_PRODUCER");
    screening.addAnnulus(new WellIntegrityScreening.AnnulusReading("A", 2.0, 80.0).setBleedsToZero(true));
    screening.setBarrierSchematic(schematic);
    WellIntegrityScreening.IntegrityDisposition disposition = screening.screen();
    assertEquals(WellIntegrityScreening.IntegrityDisposition.INTERVENTION_REQUIRED, disposition);
  }

  /**
   * No evidence yields an insufficient-data disposition.
   */
  @Test
  void testInsufficientData() {
    WellIntegrityScreening screening = new WellIntegrityScreening("WELL-A1");
    WellIntegrityScreening.IntegrityDisposition disposition = screening.screen();
    assertEquals(WellIntegrityScreening.IntegrityDisposition.INSUFFICIENT_DATA, disposition);
  }

  /**
   * The summary map carries the disposition, review flag, and annulus list.
   */
  @Test
  void testToMap() {
    WellIntegrityScreening screening = new WellIntegrityScreening("WELL-A1");
    screening.addAnnulus(new WellIntegrityScreening.AnnulusReading("A", 2.0, 80.0).setBleedsToZero(true));
    screening.screen();
    Map<String, Object> map = screening.toMap();
    assertEquals("WELL-A1", map.get("wellId"));
    assertTrue((Boolean) map.get("reviewRequired"));
    assertEquals("ACCEPTABLE", map.get("disposition"));
    assertFalse(((java.util.List<?>) map.get("annuli")).isEmpty());
  }
}
