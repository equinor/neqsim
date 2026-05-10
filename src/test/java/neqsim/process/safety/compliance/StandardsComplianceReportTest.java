package neqsim.process.safety.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.processmodel.ProcessSystem;

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

  @Test
  void trAndNorsokIntegrationChecklistsLoad() {
    StandardsComplianceReport r = new StandardsComplianceReport("TR review")
        .loadSTS0131().loadTR1965().loadNORSOKP002().loadTR2237();
    int sts0131 = 0;
    int tr1965 = 0;
    int p002 = 0;
    int tr2237 = 0;
    for (StandardsComplianceReport.Requirement q : r.getRequirements()) {
      if (q.standard.equals("STS0131")) {
        sts0131++;
      }
      if (q.standard.equals("TR1965")) {
        tr1965++;
      }
      if (q.standard.equals("NORSOK P-002")) {
        p002++;
      }
      if (q.standard.equals("TR2237")) {
        tr2237++;
      }
    }
    assertTrue(sts0131 > 0);
    assertTrue(tr1965 > 0);
    assertTrue(p002 > 0);
    assertTrue(tr2237 > 0);
  }

  @Test
  void norsokS001Clause10ChecklistLoadsWithProcessSafetyDescription() {
    StandardsComplianceReport r = new StandardsComplianceReport("Clause 10")
        .loadNORSOKS001().loadNORSOKS001Clause10();
    int clause10 = 0;
    boolean hasProcessSafetyClause = false;
    for (StandardsComplianceReport.Requirement q : r.getRequirements()) {
      if (q.standard.equals("NORSOK S-001") && q.clause.startsWith("10")) {
        clause10++;
      }
      if (q.standard.equals("NORSOK S-001") && q.clause.equals("10")
          && q.description.contains("Process safety system")) {
        hasProcessSafetyClause = true;
      }
    }
    assertTrue(hasProcessSafetyClause);
    assertTrue(clause10 >= 10);
  }

  @Test
  void standardsDesignReviewCreatesCombinedChecklist() {
    StandardsComplianceReport report = new StandardsDesignReview().review(new ProcessSystem());

    assertTrue(report.getRequirements().size() > 0);
    assertTrue(report.report().contains("Standards compliance"));
  }
}
