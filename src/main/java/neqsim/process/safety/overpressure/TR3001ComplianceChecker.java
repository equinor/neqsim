package neqsim.process.safety.overpressure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.google.gson.GsonBuilder;

/**
 * Evaluates an {@link OverpressureStudyResult} against the key TR3001 overpressure-protection requirements and the
 * supporting API STD 521 / ASME VIII Div 1 rules, producing an auditable list of {@link ComplianceFinding}s.
 *
 * <p>
 * The checker codifies the TR3001 section 4 relief-philosophy expectations: that credible overpressure causes are
 * identified (section 4.7), that the governing case is selected and documented (section 4.4), that the relief device
 * capacity is adequate for the governing case (section 4.6), that the accumulated-pressure acceptance limit is met
 * (section 2 / ASME VIII Div 1 UG-125), that the fire case uses the 21% accumulation basis (section 4.7.8) and that
 * dynamic determination is considered for transient-limited cases (SR-26565).
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class TR3001ComplianceChecker {

  /**
   * Evaluates the supplied study result and returns the list of compliance findings.
   *
   * @param result the overpressure study result; not null
   * @return an unmodifiable list of {@link ComplianceFinding}
   */
  public List<ComplianceFinding> check(OverpressureStudyResult result) {
    List<ComplianceFinding> findings = new ArrayList<ComplianceFinding>();

    checkCredibleScenarios(result, findings);
    checkGoverningCase(result, findings);
    checkCapacity(result, findings);
    checkAcceptance(result, findings);
    checkFireBasis(result, findings);
    checkDynamicDetermination(result, findings);

    return Collections.unmodifiableList(findings);
  }

  /**
   * Returns true when none of the findings is a {@link ComplianceStatus#FAIL}.
   *
   * @param findings the findings to inspect; not null
   * @return true if no finding is a failure
   */
  public boolean isCompliant(List<ComplianceFinding> findings) {
    for (ComplianceFinding finding : findings) {
      if (finding.getStatus() == ComplianceStatus.FAIL) {
        return false;
      }
    }
    return true;
  }

  /**
   * Serializes a list of compliance findings to a human-readable JSON array for results.json reporting.
   *
   * @param findings the findings to serialize; not null
   * @return JSON representation of the findings
   */
  public static String findingsToJson(List<ComplianceFinding> findings) {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(findings);
  }

  /**
   * Checks that at least one credible overpressure cause is documented (TR3001 section 4.7).
   *
   * @param result the study result; not null
   * @param findings the list to append to; not null
   */
  private void checkCredibleScenarios(OverpressureStudyResult result, List<ComplianceFinding> findings) {
    int credible = 0;
    for (ReliefScenario scenario : result.getScenarios()) {
      if (scenario.isCredible()) {
        credible++;
      }
    }
    if (credible > 0) {
      findings.add(new ComplianceFinding("TR3001 SR-26500 / API 521 4.7", "Credible overpressure causes identified",
          ComplianceStatus.PASS,
          credible + " credible relief scenario(s) documented for " + result.getItem().getName()));
    } else {
      findings.add(new ComplianceFinding("TR3001 SR-26500 / API 521 4.7", "Credible overpressure causes identified",
          ComplianceStatus.FAIL, "No credible relief scenario was documented for " + result.getItem().getName()));
    }
  }

  /**
   * Checks that the governing relief case is selected and documented (TR3001 section 4.4 / API 521 4.4).
   *
   * @param result the study result; not null
   * @param findings the list to append to; not null
   */
  private void checkGoverningCase(OverpressureStudyResult result, List<ComplianceFinding> findings) {
    ReliefScenario governing = result.getGoverningScenario();
    if (governing == null) {
      findings
          .add(new ComplianceFinding("TR3001 SR-26503 / API 521 4.4", "Governing relief case selected and documented",
              ComplianceStatus.FAIL, "No governing case could be selected; the relief load is undefined"));
      return;
    }
    findings.add(new ComplianceFinding("TR3001 SR-26503 / API 521 4.4", "Governing relief case selected and documented",
        ComplianceStatus.PASS,
        "Governing case is '" + governing.getName() + "' (" + governing.getCause().getLabel() + ", "
            + governing.getPhase() + ") at " + String.format("%.3f", governing.getReliefRateKgPerS()) + " kg/s ("
            + String.format("%.0f", governing.getReliefRateKgPerHr()) + " kg/hr)"));
  }

  /**
   * Checks that the relief device capacity is adequate for the governing case (TR3001 section 4.6).
   *
   * @param result the study result; not null
   * @param findings the list to append to; not null
   */
  private void checkCapacity(OverpressureStudyResult result, List<ComplianceFinding> findings) {
    if (result.getGoverningScenario() == null) {
      return;
    }
    if (Double.isNaN(result.getRequiredAreaIn2())) {
      findings.add(new ComplianceFinding("TR3001 SR-26506 / API 520", "Relief device sized for governing case",
          ComplianceStatus.NEEDS_REVIEW,
          "The governing case was not sized in this study (missing fluid-property inputs); complete the sizing"));
      return;
    }
    if (result.isCapacityAdequate()) {
      findings.add(new ComplianceFinding("TR3001 SR-26506 / API 520",
          "Relief device capacity adequate for governing case", ComplianceStatus.PASS,
          "Selected orifice " + result.getRecommendedOrifice() + " ("
              + String.format("%.3f", result.getSelectedAreaIn2()) + " in2) >= required "
              + String.format("%.3f", result.getRequiredAreaIn2()) + " in2"));
    } else {
      findings
          .add(new ComplianceFinding("TR3001 SR-26506 / API 520", "Relief device capacity adequate for governing case",
              ComplianceStatus.FAIL, "Required area " + String.format("%.3f", result.getRequiredAreaIn2())
                  + " in2 exceeds the largest standard orifice; multiple or larger relief devices are required"));
    }
  }

  /**
   * Checks the accumulated-pressure acceptance limit (TR3001 section 2 / ASME VIII Div 1 UG-125).
   *
   * @param result the study result; not null
   * @param findings the list to append to; not null
   */
  private void checkAcceptance(OverpressureStudyResult result, List<ComplianceFinding> findings) {
    AcceptanceResult acceptance = result.getAcceptance();
    if (acceptance == null) {
      return;
    }
    if (acceptance.isAccepted()) {
      findings.add(new ComplianceFinding("TR3001 SR-26510 / ASME VIII UG-125",
          "Accumulated pressure within allowable limit", ComplianceStatus.PASS,
          "Peak " + String.format("%.3f", acceptance.getPeakPressureBara()) + " bara <= allowable "
              + String.format("%.3f", acceptance.getAllowableAccumulatedPressureBara()) + " bara ("
              + acceptance.getBasis() + ")"));
    } else {
      findings.add(new ComplianceFinding("TR3001 SR-26510 / ASME VIII UG-125",
          "Accumulated pressure within allowable limit", ComplianceStatus.FAIL,
          "Peak " + String.format("%.3f", acceptance.getPeakPressureBara()) + " bara exceeds allowable "
              + String.format("%.3f", acceptance.getAllowableAccumulatedPressureBara()) + " bara ("
              + acceptance.getBasis() + ")"));
    }
  }

  /**
   * Checks that a fire governing case uses the 21% accumulation basis (TR3001 section 4.7.8 / API 521 4.4.13).
   *
   * @param result the study result; not null
   * @param findings the list to append to; not null
   */
  private void checkFireBasis(OverpressureStudyResult result, List<ComplianceFinding> findings) {
    ReliefScenario governing = result.getGoverningScenario();
    if (governing == null || governing.getCause() != ReliefCause.FIRE) {
      return;
    }
    AcceptanceResult acceptance = result.getAcceptance();
    if (acceptance != null && acceptance.getAccumulationFraction() >= 0.20) {
      findings.add(new ComplianceFinding("TR3001 SR-26504 / API 521 4.4.13", "Fire case uses 21% accumulation basis",
          ComplianceStatus.PASS, "Fire governing case applies an accumulation fraction of "
              + String.format("%.0f", acceptance.getAccumulationFraction() * 100.0) + "%"));
    } else {
      findings.add(new ComplianceFinding("TR3001 SR-26504 / API 521 4.4.13", "Fire case uses 21% accumulation basis",
          ComplianceStatus.NEEDS_REVIEW,
          "Fire governing case did not apply the expected 21% accumulation basis; verify the acceptance limit"));
    }
  }

  /**
   * Checks whether dynamic determination of the relief load was considered for the governing case (TR3001 SR-26565).
   *
   * @param result the study result; not null
   * @param findings the list to append to; not null
   */
  private void checkDynamicDetermination(OverpressureStudyResult result, List<ComplianceFinding> findings) {
    ReliefScenario governing = result.getGoverningScenario();
    if (governing == null) {
      return;
    }
    boolean transientSensitive = governing.getCause() == ReliefCause.FIRE
        || governing.getCause() == ReliefCause.BLOCKED_OUTLET;
    if (!transientSensitive) {
      return;
    }
    if (governing.isDynamicallyDetermined()) {
      findings.add(new ComplianceFinding("TR3001 SR-26565", "Dynamic determination of relief load considered",
          ComplianceStatus.PASS,
          "Governing " + governing.getCause().getLabel() + " load was determined by dynamic simulation"));
    } else {
      findings.add(new ComplianceFinding("TR3001 SR-26565", "Dynamic determination of relief load considered",
          ComplianceStatus.NEEDS_REVIEW, "Governing " + governing.getCause().getLabel()
              + " load uses a steady-state estimate; consider dynamic determination to avoid over-sizing"));
    }
  }
}
