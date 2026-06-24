package neqsim.process.safety.risk.sis.nog070;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Nog070SilCatalogue} and {@link Nog070SilDetermination}.
 *
 * @author ESOL
 * @version 1.0
 */
class Nog070SilCatalogueTest {

  @Test
  void hippsHasMinimumSil3() {
    assertEquals(3, Nog070SilCatalogue.getMinimumSil(Nog070SifType.HIPPS_PIPELINE));
  }

  @Test
  void subseaIsolationHasMinimumSil3() {
    assertEquals(3, Nog070SilCatalogue.getMinimumSil(Nog070SifType.ESD_SUBSEA_ISOLATION));
  }

  @Test
  void blowdownHasMinimumSil2() {
    assertEquals(2, Nog070SilCatalogue.getMinimumSil(Nog070SifType.BLOWDOWN_HYDROCARBON_SEGMENT));
  }

  @Test
  void customRequiresExplicitMinimum() {
    assertEquals(0, Nog070SilCatalogue.getMinimumSil(Nog070SifType.CUSTOM));
  }

  @Test
  void catalogueIsImmutable() {
    assertNotNull(Nog070SilCatalogue.getCatalogue());
    assertThrows(UnsupportedOperationException.class,
        () -> Nog070SilCatalogue.getCatalogue().put(Nog070SifType.CUSTOM, Integer.valueOf(5)));
  }

  @Test
  void pfdToSilMapping() {
    assertEquals(0, Nog070SilDetermination.pfdToSil(0.2));
    assertEquals(1, Nog070SilDetermination.pfdToSil(5.0e-2));
    assertEquals(2, Nog070SilDetermination.pfdToSil(5.0e-3));
    assertEquals(3, Nog070SilDetermination.pfdToSil(5.0e-4));
    assertEquals(4, Nog070SilDetermination.pfdToSil(5.0e-5));
  }

  @Test
  void hippsCompliantAtPfd1e4() {
    // PFD 1e-4 -> SIL 3, HIPPS minimum is SIL 3 -> compliant
    Nog070SilDetermination r = Nog070SilDetermination.evaluate(Nog070SifType.HIPPS_PIPELINE, 1.0e-4);
    assertEquals(3, r.getAchievedSil());
    assertEquals(3, r.getMinimumSil());
    assertTrue(r.isCompliant());
  }

  @Test
  void hippsNonCompliantAtPfd5e3() {
    // PFD 5e-3 -> SIL 2, HIPPS minimum is SIL 3 -> not compliant
    Nog070SilDetermination r = Nog070SilDetermination.evaluate(Nog070SifType.HIPPS_PIPELINE, 5.0e-3);
    assertEquals(2, r.getAchievedSil());
    assertEquals(3, r.getMinimumSil());
    assertFalse(r.isCompliant());
  }

  @Test
  void customWithExplicitMinimum() {
    Nog070SilDetermination r = Nog070SilDetermination.evaluate(Nog070SifType.CUSTOM, 5.0e-3, 1);
    assertEquals(2, r.getAchievedSil());
    assertEquals(1, r.getMinimumSil());
    assertTrue(r.isCompliant());
  }

  @Test
  void invalidPfdRejected() {
    assertThrows(IllegalArgumentException.class,
        () -> Nog070SilDetermination.evaluate(Nog070SifType.HIPPS_PIPELINE, 0.0));
    assertThrows(IllegalArgumentException.class,
        () -> Nog070SilDetermination.evaluate(Nog070SifType.HIPPS_PIPELINE, 1.5));
  }

  @Test
  void jsonExportRoundTrip() {
    Nog070SilDetermination r = Nog070SilDetermination.evaluate(Nog070SifType.HIPPS_PIPELINE, 1.0e-4);
    String json = r.toJson();
    assertNotNull(json);
    assertTrue(json.contains("HIPPS_PIPELINE"));
    assertTrue(json.contains("achievedSil"));
  }
}
