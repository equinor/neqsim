package neqsim.process.engineering;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.process.engineering.dexpi.DexpiEngineeringValidator;
import neqsim.process.processmodel.ProcessSystem;

/** Focused tests for engineering evidence, timing, SIF and exchange assurance. */
class EngineeringAssuranceTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  void requiresAdvancedSifEvidenceAndChecksResponseBudget() {
    SafetyFunctionDesign.Subsystem sensor = new SafetyFunctionDesign.Subsystem("pressure transmitters",
        SafetyFunctionDesign.SubsystemType.SENSOR, 2, 3, 1.0e-6, 0.6, 8760.0, 8.0, 0.05);
    assertFalse(sensor.getMissingFields().isEmpty());
    sensor.setProofTestCoverage(0.95).setMissionTimeHours(87600.0).setCommonCauseGroup("PT-GROUP-001")
        .setArchitecturalConstraints(2, 1).setCertifiedDataReference("CERT-PT-001");
    assertTrue(sensor.getMissingFields().isEmpty());

    ShutdownSequence sequence = new ShutdownSequence("ESD-001", "High-high pressure").setProtectedEquipmentTag("V-001")
        .setSafeState("Isolated").setHazopReference("HAZOP-001").setSrsReference("SRS-001")
        .setResponseTimeBudgetSeconds(5.0).setResetAndRestartDefined(true).addRequirementId("REQ-001")
        .addAction(new ShutdownSequence.Action("ESDV-001", "Close", "CLOSED", 1.0, 5.0));
    assertFalse(sequence.isWithinResponseTimeBudget());
    assertTrue(sequence.getMissingFields().isEmpty());
  }

  @Test
  void validatesEvidenceLinksAgainstProjectObjects() {
    EngineeringProject project = new EngineeringProject("assurance", new ProcessSystem(), new EngineeringDesignBasis());
    project.addEvidenceRecord(new EngineeringEvidenceRecord("DOC-001", "HAZOP", "A").setTitle("Hazard review")
        .setSourceOrganization("Project").linkRequirement("UNKNOWN"));
    assertTrue(project.validate().hasErrors());
  }

  @Test
  void canApplyAControlledXsdDuringDexpiValidation() throws Exception {
    Path xml = temporaryDirectory.resolve("minimal.xml");
    Path xsd = temporaryDirectory.resolve("minimal.xsd");
    Files.write(xml, "<PlantModel><Equipment ID=\"E1\"/></PlantModel>".getBytes(StandardCharsets.UTF_8));
    String schema = "<?xml version=\"1.0\"?><xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">"
        + "<xs:element name=\"PlantModel\"><xs:complexType><xs:sequence>"
        + "<xs:element name=\"Equipment\"><xs:complexType><xs:attribute name=\"ID\" type=\"xs:string\"/>"
        + "</xs:complexType></xs:element></xs:sequence></xs:complexType></xs:element></xs:schema>";
    Files.write(xsd, schema.getBytes(StandardCharsets.UTF_8));

    String validation = DexpiEngineeringValidator.validate(xml, xsd).toJson();
    assertTrue(validation.contains("\"officialXsdValidation\": \"PASSED\""), validation);
  }
}
