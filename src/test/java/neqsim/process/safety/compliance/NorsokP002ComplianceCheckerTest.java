package neqsim.process.safety.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link NorsokP002ComplianceChecker}.
 *
 * @author ESOL
 * @version 1.0
 */
class NorsokP002ComplianceCheckerTest {

  @Test
  void flareMachWithinLimit() {
    NorsokP002ComplianceChecker c = new NorsokP002ComplianceChecker().checkFlareLineMach("Header", 0.5);
    assertTrue(c.isCompliant());
  }

  @Test
  void flareMachAboveLimit() {
    NorsokP002ComplianceChecker c = new NorsokP002ComplianceChecker().checkFlareLineMach("Header", 0.85);
    assertFalse(c.isCompliant());
    assertEquals(1, c.countNonCompliant());
  }

  @Test
  void blowdownRhoV2WithinLimit() {
    NorsokP002ComplianceChecker c = new NorsokP002ComplianceChecker().checkBlowdownRhoV2("BDV-1", 150000.0);
    assertTrue(c.isCompliant());
  }

  @Test
  void blowdownRhoV2AboveLimit() {
    NorsokP002ComplianceChecker c = new NorsokP002ComplianceChecker().checkBlowdownRhoV2("BDV-1", 250000.0);
    assertFalse(c.isCompliant());
  }

  @Test
  void multipleChecksAggregate() {
    NorsokP002ComplianceChecker c = new NorsokP002ComplianceChecker().checkFlareLineMach("Header", 0.5)
	.checkBlowdownRhoV2("BDV-1", 150000.0).checkVentGasVelocity("Vent", 45.0).checkLiquidCarryOver("V-100", 1.0e-4)
	.checkErosionalVelocity("Line-200", 80000.0).recordDepressurisationValve("BDV-2", true, "Sized for fire case")
	.recordDrainSlope("CD-1", true, "1:100 slope OK");
    assertTrue(c.isCompliant());
    assertEquals(7, c.getFindings().size());
  }

  @Test
  void overallFailsWhenAnyFails() {
    NorsokP002ComplianceChecker c = new NorsokP002ComplianceChecker().checkFlareLineMach("Header", 0.5)
	.checkBlowdownRhoV2("BDV-1", 250000.0);
    assertFalse(c.isCompliant());
    assertEquals(1, c.countNonCompliant());
  }

  @Test
  void jsonExportContainsCriteria() {
    NorsokP002ComplianceChecker c = new NorsokP002ComplianceChecker().checkFlareLineMach("Header", 0.5);
    assertTrue(c.toJson().contains("FLARE_LINE_MACH_07"));
  }
}
