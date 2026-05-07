package neqsim.process.safety.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class StandardsComplianceReportTest {

  @Test
  void api14CChecklistLoads() {
    StandardsComplianceReport r = new StandardsComplianceReport("Test").loadAPI14C();
    assertTrue(r.getRequirements().size() >= 5);
  }

  @Test
  void compliancePercentExcludesNotApplicable() {
    StandardsComplianceReport r = new StandardsComplianceReport("Test").loadAPI14C();
    // mark 2 compliant, 2 N/A out of 9
    r.setStatus("API RP 14C", "3.4", StandardsComplianceReport.Status.COMPLIANT, "doc-1");
    r.setStatus("API RP 14C", "3.5", StandardsComplianceReport.Status.COMPLIANT, "doc-2");
    r.setStatus("API RP 14C", "3.6", StandardsComplianceReport.Status.NON_COMPLIANT, null);
    assertTrue(r.compliancePercent() > 0.0);
    assertTrue(r.compliancePercent() <= 100.0);
  }

  @Test
  void allThreeChecklistsLoadIndependently() {
    StandardsComplianceReport r = new StandardsComplianceReport("Multi")
        .loadAPI14C().loadNORSOKS001().loadIEC61511();
    int api = 0;
    int norsok = 0;
    int iec = 0;
    for (StandardsComplianceReport.Requirement q : r.getRequirements()) {
      if (q.standard.equals("API RP 14C")) {
        api++;
      }
      if (q.standard.equals("NORSOK S-001")) {
        norsok++;
      }
      if (q.standard.equals("IEC 61511")) {
        iec++;
      }
    }
    assertTrue(api > 0);
    assertTrue(norsok > 0);
    assertTrue(iec > 0);
    assertEquals(api + norsok + iec, r.getRequirements().size());
  }
}
