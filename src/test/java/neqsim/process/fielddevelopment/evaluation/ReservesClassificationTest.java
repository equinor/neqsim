package neqsim.process.fielddevelopment.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import neqsim.process.fielddevelopment.evaluation.ReservesClassification.ResourceCategory;

/**
 * Unit tests for {@link ReservesClassification} (SPE-PRMS / NPD resource categories).
 *
 * @author NeqSim Community
 * @version 1.0
 */
public class ReservesClassificationTest {

  /**
   * On-production maturity maps to RESERVES (PRMS class 1) with an "ok" warning.
   */
  @Test
  public void testOnProductionIsReserves() {
    ReservesClassification model = new ReservesClassification();
    ReservesClassification.Result result = model.classify("on production");
    assertEquals("on_production", result.getResourceClass());
    assertEquals(ResourceCategory.RESERVES, result.getResourceCategory());
    assertEquals("PRMS class 1 (on production)", result.getPrmsClassRange());
    assertEquals("ok", result.getMaturityWarning());
  }

  /**
   * Reserves stage flagged non-commercial raises the warning to "watch".
   */
  @Test
  public void testReservesNonCommercialRaisesWatch() {
    ReservesClassification model = new ReservesClassification();
    ReservesClassification.Result result =
        model.classify("approved-for-development", Boolean.FALSE);
    assertEquals(ResourceCategory.RESERVES, result.getResourceCategory());
    assertEquals("watch", result.getMaturityWarning());
  }

  /**
   * Development-pending maturity maps to CONTINGENT_RESOURCES (PRMS class 4).
   */
  @Test
  public void testDevelopmentPendingIsContingent() {
    ReservesClassification model = new ReservesClassification();
    ReservesClassification.Result result = model.classify("Development Pending");
    assertEquals(ResourceCategory.CONTINGENT_RESOURCES, result.getResourceCategory());
    assertEquals("PRMS class 4 (development pending)", result.getPrmsClassRange());
    assertEquals("ok", result.getMaturityWarning());
  }

  /**
   * Prospect maturity maps to PROSPECTIVE_RESOURCES (PRMS class 7).
   */
  @Test
  public void testProspectIsProspective() {
    ReservesClassification model = new ReservesClassification();
    ReservesClassification.Result result = model.classify("prospect");
    assertEquals(ResourceCategory.PROSPECTIVE_RESOURCES, result.getResourceCategory());
    assertEquals("PRMS class 7 (prospect)", result.getPrmsClassRange());
  }

  /**
   * An unknown stage is UNRECOVERABLE and flagged unclassified.
   */
  @Test
  public void testUnknownStageIsUnrecoverable() {
    ReservesClassification model = new ReservesClassification();
    ReservesClassification.Result result = model.classify("something else");
    assertEquals(ResourceCategory.UNRECOVERABLE, result.getResourceCategory());
    assertEquals("unclassified", result.getPrmsClassRange());
    assertEquals("unclassified", result.getMaturityWarning());
  }

  /**
   * A blank maturity stage is rejected.
   */
  @Test
  public void testBlankStageRejected() {
    ReservesClassification model = new ReservesClassification();
    assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        model.classify("   ");
      }
    });
  }
}
