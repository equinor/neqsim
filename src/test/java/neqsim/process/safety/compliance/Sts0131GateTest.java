package neqsim.process.safety.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.safety.risk.sis.nog070.Nog070SifType;
import neqsim.process.safety.risk.sis.nog070.Nog070SilDetermination;

/**
 * Unit tests for {@link Sts0131Gate}.
 *
 * @author ESOL
 * @version 1.0
 */
class Sts0131GateTest {

  @Test
  void emptyGateIsAcceptable() {
    Sts0131Gate gate = new Sts0131Gate();
    assertTrue(gate.isAcceptable());
    assertEquals(0, gate.countFailures());
  }

  @Test
  void psvMarginPassWhen15Percent() {
    Sts0131Gate gate = new Sts0131Gate();
    gate.addPsvSizingMargin(1.0, 1.15, 0.10);
    assertTrue(gate.isAcceptable());
  }

  @Test
  void psvMarginFailWhen5Percent() {
    Sts0131Gate gate = new Sts0131Gate();
    gate.addPsvSizingMargin(1.0, 1.05, 0.10);
    assertFalse(gate.isAcceptable());
    assertEquals(1, gate.countFailures());
  }

  @Test
  void mdmtBelowDesignFails() {
    Sts0131Gate gate = new Sts0131Gate();
    gate.addMdmt(-60.0, -29.0);
    assertFalse(gate.isAcceptable());
  }

  @Test
  void mdmtAboveDesignPasses() {
    Sts0131Gate gate = new Sts0131Gate();
    gate.addMdmt(-20.0, -29.0);
    assertTrue(gate.isAcceptable());
  }

  @Test
  void silDeterminationAggregated() {
    Sts0131Gate gate = new Sts0131Gate();
    Nog070SilDetermination ok = Nog070SilDetermination.evaluate(Nog070SifType.PSD_PROCESS_SEGMENT, 5.0e-3);
    gate.addSil(ok);
    assertTrue(gate.isAcceptable());
  }

  @Test
  void multipleChecksAggregated() {
    Sts0131Gate gate = new Sts0131Gate();
    gate.addPsvSizingMargin(1.0, 1.15, 0.10);
    gate.addMdmt(-20.0, -29.0);
    gate.addCustom("Fire case", true, "Heat input within API 521 envelope");
    assertTrue(gate.isAcceptable());
    assertEquals(3, gate.getFindings().size());
  }

  @Test
  void overallFailsWhenAnyFails() {
    Sts0131Gate gate = new Sts0131Gate();
    gate.addPsvSizingMargin(1.0, 1.15, 0.10);
    gate.addMdmt(-60.0, -29.0);
    assertFalse(gate.isAcceptable());
    assertEquals(1, gate.countFailures());
  }

  @Test
  void jsonExportContainsFindings() {
    Sts0131Gate gate = new Sts0131Gate();
    gate.addCustom("Fire case", true, "OK");
    String json = gate.toJson();
    assertTrue(json.contains("Fire case"));
  }
}
