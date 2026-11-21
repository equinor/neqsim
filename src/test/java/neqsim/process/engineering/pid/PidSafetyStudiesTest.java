package neqsim.process.engineering.pid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.NorsokOffshoreEngineeringBuilder;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Runs the screening safety studies on the synthesized P&amp;ID of a HP/LP separation train and checks the physics
 * results written back to the relief valves, blowdown valves and trips.
 */
public class PidSafetyStudiesTest {
  /**
   * Synthesizes the complete P&amp;ID proposal with standard relief and blowdown connections.
   *
   * @param project engineering project
   * @return P&amp;ID proposal
   */
  private static PidDesignModel proposal(EngineeringProject project) {
    PidDesignModel model = PidDesignSynthesizer.synthesize(project,
        new PidDesignBasis("NORSOK-COMPLETE-PID-PROPOSALS", "20"), NorsokPidRuleCatalog.completeProposals());
    PidStandardConnectionProposals.complete(model, project.getEngineeringProcessSystem());
    return model;
  }

  /**
   * Returns the element with a tag prefix on one equipment item.
   *
   * @param model proposal
   * @param equipment equipment tag
   * @param type element type
   * @param purpose proposal purpose fragment, or null
   * @return element
   */
  private static PidElement find(PidDesignModel model, String equipment, PidElementType type, String purpose) {
    for (PidElement element : model.getElementsForEquipment(equipment)) {
      if (element.getType() == type && (purpose == null
          || String.valueOf(element.getAttributes().get("proposalPurpose")).contains(purpose))) {
        return element;
      }
    }
    throw new AssertionError("no " + type + " on " + equipment);
  }

  /**
   * Relief, blowdown, trip set points and SIL screening are calculated from the simulation and written back without
   * overwriting project inputs.
   */
  @Test
  void studiesSizeSafeguardsFromTheSimulation() {
    ProcessSystem process = ProfessionalPidDrawingStandardsTest.process();
    EngineeringProject project = NorsokOffshoreEngineeringBuilder.from("Safety train", process, "2.0.1")
        .projectId("SAFE-PID").build();
    PidDesignModel model = proposal(project);
    PidElement projectTrip = find(model, "20-VA-001", PidElementType.TRIP, "LEVEL-HH");
    projectTrip.attribute("setPoint", "80 % of range (project)");

    PidSafetyStudies.Result result = PidSafetyStudies.run(model, project.getEngineeringProcessSystem(),
        PidSafetyStudies.Policy.screening());
    assertFalse(result.getUpdatedTags().isEmpty());
    assertEquals("80 % of range (project)", projectTrip.getAttributes().get("setPoint"), "project input kept");

    // HP separator relief: set at the proposed design pressure, sized for the governing case.
    PidElement hpPsv = find(model, "20-VA-001", PidElementType.SAFETY_RELIEF_VALVE, null);
    double operatingBarg = 60.0 - 1.01325;
    double set = ((Number) hpPsv.getAttributes().get("studySetPressureBarg")).doubleValue();
    assertEquals(Math.max(operatingBarg * 1.1, operatingBarg + 3.5), set, 0.1);
    assertTrue(((Number) hpPsv.getAttributes().get("studyRelievingRateKgPerH")).doubleValue() > 0.0);
    assertTrue(String.valueOf(hpPsv.getAttributes().get("studyOrifice")).matches("[D-T]|[0-9]+ x T"));
    assertTrue(String.valueOf(hpPsv.getAttributes().get(PidSafetyStudies.REFERENCE))
        .startsWith(PidSafetyStudies.EVIDENCE_PREFIX));

    // LP separator relief: gas blow-by through the failed-open letdown valve is a calculated case.
    PidElement lpPsv = find(model, "20-VA-002", PidElementType.SAFETY_RELIEF_VALVE, null);
    assertTrue(String.valueOf(lpPsv.getAttributes().get("studyReliefCases")).contains("GAS_BLOWBY via 20-LV-001"));

    // Blowdown meets the API 521 criterion within 15 minutes.
    PidElement bdv = find(model, "20-VA-001", PidElementType.BLOWDOWN_VALVE, null);
    assertTrue(((Number) bdv.getAttributes().get("studyTimeToTargetS")).doubleValue() <= 900.0);
    assertTrue(((Number) bdv.getAttributes().get("studyOrificeDiameterMm")).doubleValue() > 0.0);
    assertEquals("FAIL_OPEN_PROPOSAL", bdv.getAttributes().get("failurePosition"), "blowdown valves fail open");

    // High-high pressure trip sits between operating pressure and relief set pressure, with a verified SIL target.
    PidElement pshh = find(model, "20-VA-001", PidElementType.TRIP, "PRESSURE-HH");
    double trip = Double.parseDouble(String.valueOf(pshh.getAttributes().get("setPoint")).replace(" barg", ""));
    assertTrue(trip > operatingBarg && trip < set, "trip " + trip);
    assertTrue(String.valueOf(pshh.getAttributes().get("silTarget")).startsWith("SIL 2"));
    assertEquals("TARGET_MET_SCREENING", pshh.getAttributes().get("studySilVerification"));
    assertTrue(((Number) pshh.getAttributes().get("studyPfdAvg")).doubleValue() < 1.0e-2);

    // The compressor segments have no geometry in the model: that stays an explicit open finding.
    boolean volumeFinding = false;
    for (Map<String, Object> finding : result.getFindings()) {
      volumeFinding |= "BLOWDOWN_SEGMENT_VOLUME_REQUIRED".equals(finding.get("code"));
    }
    assertTrue(volumeFinding);

    JsonObject report = JsonParser.parseString(PidSafetyStudies.reportJson(model, result)).getAsJsonObject();
    assertEquals("SCREENING_REVIEW_REQUIRED", report.get("studyStatus").getAsString());
    assertTrue(report.getAsJsonObject("flareLoad").get("flareDesignLoadKgPerH").getAsDouble() > 0.0);
    assertEquals("NEQSIM_SCREENING_DEFAULTS", report.getAsJsonObject("policy").get("name").getAsString());

    // The HAZOP preparation quotes the calculated safeguards.
    String hazop = PidHazopStudyRunner.run(project, model).toJson();
    assertTrue(hazop.contains("quantifiedSafeguardEvidence"));
    assertTrue(hazop.contains("gas blow-by"));
  }

  /**
   * A declared compressor segment volume lets the study size that blowdown valve.
   */
  @Test
  void declaredSegmentVolumeSizesCompressorBlowdown() {
    ProcessSystem process = ProfessionalPidDrawingStandardsTest.process();
    EngineeringProject project = NorsokOffshoreEngineeringBuilder.from("Safety train", process, "2.0.1")
        .projectId("SAFE-PID").build();
    PidDesignModel model = proposal(project);
    PidSafetyStudies.Policy policy = PidSafetyStudies.Policy.screening().segmentVolume("23-KA-001", 2.0);
    PidSafetyStudies.Result result = PidSafetyStudies.run(model, project.getEngineeringProcessSystem(), policy);
    PidElement bdv = find(model, "23-KA-001", PidElementType.BLOWDOWN_VALVE, null);
    assertTrue(bdv.getAttributes().get("studyOrificeDiameterMm") instanceof Number,
        bdv.getAttributes() + " " + result.getFindings());
  }
}
