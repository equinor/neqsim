package neqsim.process.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.NeqSimTest;
import neqsim.process.engineering.dexpi.DexpiEngineeringExporter;
import neqsim.process.engineering.dexpi.DexpiEngineeringExporter.ExportResult;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.pipeline.AdiabaticPipe;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.mechanicaldesign.DesignConditions;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.dexpi.Dexpi20XmlValidator;
import neqsim.process.processmodel.dexpi.Dexpi20ModelInspector;
import neqsim.process.processmodel.dexpi.Dexpi20SemanticValidator;
import neqsim.process.safety.depressurization.DynamicBlowdownFlareStudyDataSource;
import neqsim.process.safety.overpressure.BlockedOutletRelief;
import neqsim.process.safety.overpressure.OverpressureProtectionStudy;
import neqsim.process.safety.overpressure.ProtectedItem;
import neqsim.process.safety.overpressure.ReliefCause;
import neqsim.process.safety.overpressure.ReliefPhase;
import neqsim.process.safety.overpressure.ReliefScenario;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Tests the standards-based engineering and DEXPI package vertical slice. */
class NorsokOffshoreEngineeringBuilderTest extends NeqSimTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  void buildsGovernedRequirementsAndExportsReferencedCompressorMap() throws Exception {
    SystemInterface fluid = new SystemSrkEos(303.15, 50.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.03);
    fluid.setMixingRule(2);

    Stream feed = new Stream("Feed", fluid);
    feed.setFlowRate(1.0, "MSm3/day");
    Separator scrubber = new Separator("20-VG-001", feed);
    scrubber.setDesignConditions(
        new DesignConditions().setDesignPressure(70.0).setReliefSetPressure(68.0).setMaxDesignTemperature(80.0)
            .setMinDesignTemperature(-46.0).setConstructionMaterial("Carbon steel").setCorrosionAllowance(3.0));
    Compressor compressor = new Compressor("20-KA-001", scrubber.getGasOutStream());
    compressor.setOutletPressure(100.0, "bara");
    compressor.setDesignConditions(new DesignConditions().setDesignPressure(120.0).setMaxDesignTemperature(150.0));
    AdiabaticPipe exportLine = new AdiabaticPipe("20-PL-001", compressor.getOutletStream());
    exportLine.setLength(120.0);
    exportLine.setDiameter(0.194);
    exportLine.setPipeWallRoughness(4.5e-5);
    exportLine.setElevation(8.0);

    ProcessSystem process = new ProcessSystem();
    process.setName("Gas compression train");
    process.add(feed);
    process.add(scrubber);
    process.add(compressor);
    process.add(exportLine);
    process.run();

    double[] chartConditions = new double[] { 19.0, 303.15, 50.0, 0.90 };
    double[] speeds = new double[] { 8000.0, 10000.0 };
    double[][] flows = new double[][] { { 4000.0, 6000.0, 8000.0 }, { 5000.0, 7500.0, 10000.0 } };
    double[][] heads = new double[][] { { 55.0, 50.0, 42.0 }, { 85.0, 78.0, 65.0 } };
    double[][] efficiencies = new double[][] { { 70.0, 78.0, 72.0 }, { 71.0, 80.0, 73.0 } };
    compressor.getCompressorChart().setCurves(chartConditions, speeds, flows, heads, efficiencies);
    compressor.getCompressorChart().setUseCompressorChart(true);
    compressor.getAntiSurge().setActive(true);

    EngineeringProject project = NorsokOffshoreEngineeringBuilder.from("Compression engineering model", process)
        .registerProposedInstruments(true).build();

    project.addLineDesignInput(new LineDesignInput("20-PL-001-A", "20-PL-001").setNominalPipeSize("8").setSchedule("80")
        .setMaterialGrade("X65").setPipingClass("HC-600").setInsulationType("Mineral wool")
        .setOuterDiameter(219.1, "mm").setNominalWallThickness(12.7, "mm").setCorrosionAllowance(3.0, "mm")
        .setDesignPressureBara(120.0).setDesignTemperatureC(150.0).setEquivalentFittingsLengthM(18.0)
        .setProposedSupportSpacingM(5.0).addEvidenceReference("LINE-LIST-20-REV-A"));
    project.addLineDesignInput(new LineDesignInput("20-PL-MISSING", "NO-SUCH-PIPE").setNominalPipeSize("8")
        .setSchedule("80").setMaterialGrade("X65").setPipingClass("HC-600").setInsulationType("Mineral wool")
        .setOuterDiameter(219.1, "mm").setNominalWallThickness(12.7, "mm").setCorrosionAllowance(3.0, "mm")
        .setDesignPressureBara(120.0).setDesignTemperatureC(150.0).addEvidenceReference("NEGATIVE-TEST"));

    String pressureSifRequirement = "20-KA-001-DISCHARGE-P-HH";
    for (EngineeringRequirement requirement : project.getRequirements()) {
      if (pressureSifRequirement.equals(requirement.getId())) {
        requirement.setSilTarget("SIL 2", "LOPA-20-001");
      }
    }
    project.addSafetyFunctionDesign(new SafetyFunctionDesign("SIF-20-001", pressureSifRequirement, 2)
        .setLopaReference("LOPA-20-001").setSrsReference("SRS-20-001").setSafeState("Compressor isolated and stopped")
        .addSubsystem(new SafetyFunctionDesign.Subsystem("2oo3 discharge pressure transmitters",
            SafetyFunctionDesign.SubsystemType.SENSOR, 2, 3, 1.0e-6, 0.60, 8760.0, 8.0, 0.05).setProofTestCoverage(0.95)
            .setMissionTimeHours(87600.0).setCommonCauseGroup("PT-20-001").setArchitecturalConstraints(2, 1)
            .setCertifiedDataReference("SIL-CERT-PT-001"))
        .addSubsystem(new SafetyFunctionDesign.Subsystem("1oo1 safety logic solver",
            SafetyFunctionDesign.SubsystemType.LOGIC_SOLVER, 1, 1, 1.0e-7, 0.90, 8760.0, 8.0, 0.0)
            .setProofTestCoverage(0.99).setMissionTimeHours(87600.0).setArchitecturalConstraints(3, 0)
            .setCertifiedDataReference("SIL-CERT-LS-001"))
        .addSubsystem(new SafetyFunctionDesign.Subsystem("1oo1 compressor trip and isolation",
            SafetyFunctionDesign.SubsystemType.FINAL_ELEMENT, 1, 1, 2.0e-6, 0.50, 8760.0, 8.0, 0.0)
            .setProofTestCoverage(0.90).setPartialStrokeTesting(2160.0, 0.60).setMissionTimeHours(87600.0)
            .setArchitecturalConstraints(2, 0).setCertifiedDataReference("SIL-CERT-FE-001")));

    project.addShutdownSequence(new ShutdownSequence("ESD-20-001", "High-high compressor discharge pressure")
        .setProtectedEquipmentTag("20-KA-001").setSafeState("Compressor stopped and isolated")
        .setHazopReference("HAZOP-20-001").setSrsReference("SRS-20-001").setResponseTimeBudgetSeconds(12.0)
        .setResetAndRestartDefined(true).addRequirementId(pressureSifRequirement)
        .addAction(new ShutdownSequence.Action("20-KA-001", "Trip compressor driver", "STOPPED", 0.5, 1.0))
        .addAction(new ShutdownSequence.Action("ESDV-20-001", "Close discharge isolation", "CLOSED", 1.0, 6.0)));

    ProtectedItem protectedScrubber = new ProtectedItem("20-VG-001", 70.0).setReliefSetPressureBara(68.0);
    OverpressureProtectionStudy reliefStudy = new OverpressureProtectionStudy(protectedScrubber)
        .addScenario(new BlockedOutletRelief().setInflowRateKgPerS(feed.getFlowRate("kg/sec"))
            .setReliefPressureBara(68.0).setFluid(feed.getFluid()).calculate())
        .addScenario(new ReliefScenario.Builder("External pool-fire screening", ReliefCause.FIRE)
            .phase(ReliefPhase.VAPOUR).reliefRateKgPerS(2.5).reliefTemperatureK(350.0).molarMassKgPerMol(0.020)
            .compressibility(0.95).specificHeatRatio(1.25).addAssumption("Reviewed wetted-area fire load").build());
    project.addOverpressureStudy(reliefStudy).addReliefScenarioBasis(
        new ReliefScenarioBasis("20-VG-001").require(ReliefCause.BLOCKED_OUTLET).require(ReliefCause.FIRE)
            .setHazardReviewReference("HAZOP-20-001").addEvidenceReference("RELIEF-REGISTER-20-REV-A"));
    project.addBlowdownFlareStudy(DynamicBlowdownFlareStudyDataSource.builder("incomplete-blowdown-study")
        .addGap("Protected equipment inventory is not available.").build());
    project.addBoundary(new EngineeringBoundary("20-CD-001", "20-VG-001", EngineeringBoundary.Type.CLOSED_DRAIN));
    OverpressureProtectionStudy incompleteStudy = new OverpressureProtectionStudy(
        new ProtectedItem("20-KA-001", 120.0).setReliefSetPressureBara(110.0))
        .addScenario(new ReliefScenario.Builder("Incomplete check-valve leakage", ReliefCause.CHECK_VALVE_LEAKAGE)
            .phase(ReliefPhase.VAPOUR).reliefRateKgPerS(1.0).build());
    project.addOverpressureStudy(incompleteStudy)
        .addReliefScenarioBasis(new ReliefScenarioBasis("20-KA-001").require(ReliefCause.CHECK_VALVE_LEAKAGE)
            .setHazardReviewReference("HAZOP-20-002").addEvidenceReference("RELIEF-REGISTER-20-REV-A"));
    project.addReliefDeviceDesignInput(new ReliefDeviceDesignInput("20-PSV-001", "20-VG-001")
        .setSelectedOrificeAreaIn2(10.0).setInletPiping(0.10, 2.0, 2.0).setOutletPiping(0.20, 20.0, 5.0)
        .setConcurrencyGroup("FIRE-ZONE-A").setFireZone("A").setTwoPhaseMethod("API_520_OMEGA_WHEN_APPLICABLE")
        .setEvidenceReference("PSV-DATASHEET-20-001-REV-A"));
    project.addEvidenceRecord(new EngineeringEvidenceRecord("HAZOP-20-001", "HAZOP", "A")
        .setTitle("Compression train HAZOP").setSourceOrganization("Project technical safety")
        .linkEquipment("20-KA-001").linkEquipment("20-VG-001").linkRequirement(pressureSifRequirement));
    project.addEvidenceRecord(new EngineeringEvidenceRecord("LINE-LIST-20", "LINE_LIST", "A")
        .setTitle("Compression line list").setSourceOrganization("Project piping").linkEquipment("20-PL-001"));

    assertFalse(project.getRequirementsForEquipment("20-KA-001").isEmpty());
    assertFalse(process.getMeasurementDevices().isEmpty());
    for (EngineeringRequirement requirement : project.getRequirements()) {
      assertEquals(EngineeringApprovalStatus.REVIEW_REQUIRED, requirement.getApprovalStatus());
      if (requirement.getType() == EngineeringRequirement.Type.TRIP
          && !pressureSifRequirement.equals(requirement.getId())) {
        assertEquals("SIL_UNASSIGNED", requirement.getSilTarget());
      }
    }
    assertTrue(project.getDesignBasis().getStandards().stream()
        .anyMatch(standard -> "NORSOK I-001".equals(standard.getCode())));

    ExportResult result = DexpiEngineeringExporter.export(project, temporaryDirectory.resolve("package"));
    assertTrue(Files.exists(result.getDexpiFile()));
    assertTrue(Files.exists(result.getDexpi20File()));
    Dexpi20XmlValidator.validate(result.getDexpi20File());
    assertTrue(Dexpi20SemanticValidator.validate(result.getDexpi20File()).isValid());
    Dexpi20ModelInspector.ModelSummary nativeSummary = Dexpi20ModelInspector.inspect(result.getDexpi20File());
    assertTrue(nativeSummary.getEquipmentTags().contains("20-KA-001"));
    assertTrue(nativeSummary.getPipingConnectionCount() > 0);
    assertTrue(nativeSummary.getInstrumentationFunctionCount() > 0);
    assertTrue(nativeSummary.getOffPageConnectorCount() > 0);
    assertTrue(nativeSummary.getRepresentationGroupCount() > 0);
    assertTrue(Files.exists(result.getManifestFile()));
    assertTrue(Files.exists(result.getCalculationsFile()));
    assertTrue(Files.exists(result.getCauseAndEffectFile()));
    assertTrue(Files.exists(result.getInteroperabilityReportFile()));
    assertTrue(Files.exists(result.getValidationFile()));
    assertTrue(Files.exists(result.getPackageManifestFile()));
    assertTrue(Files.exists(result.getRegisterFiles().get("instrumentIndex")));
    assertTrue(Files.exists(result.getRegisterFiles().get("reliefRegister")));
    assertTrue(result.getCompressorMapFiles().containsKey("20-KA-001"));

    String xml = new String(Files.readAllBytes(result.getDexpiFile()), StandardCharsets.UTF_8);
    assertTrue(xml.contains("NeqSimEngineeringProject"));
    assertTrue(xml.contains("EngineeringRequirementIds"));
    assertTrue(xml.contains("CompressorPerformanceMapDocument"));
    assertTrue(xml.contains("EngineeringCalculationsDocument"));
    assertTrue(xml.contains("PackageManifestDocument"));
    assertTrue(xml.contains("DeclaredDesignPressureBara"));
    assertTrue(xml.contains("SimulationOperatingPressureBara"));
    assertTrue(xml.contains("datasets/20-KA-001-compressor-map.json"));
    assertTrue(xml.contains("ProcessInstrumentationFunction"));
    assertTrue(xml.contains("SpringLoadedGlobeSafetyValve"));
    assertTrue(xml.contains("ASCV-20KA001"));
    assertTrue(xml.contains("ESDV-SUC-20KA001"));
    assertTrue(xml.contains("ESDV-DIS-20KA001"));
    assertTrue(xml.contains("NRV-20KA001"));
    assertTrue(xml.contains("EngineeringGovernance"));
    assertTrue(xml.contains("VotingArchitecture"));
    assertTrue(xml.contains("SENSOR:2oo3"));
    assertTrue(xml.contains("LOGIC_SOLVER:1oo1"));
    assertTrue(xml.contains("NOT_ASSIGNED"));
    assertTrue(xml.contains("CONNECTED_RECYCLE_TOPOLOGY_REVIEW_REQUIRED"));
    assertTrue(xml.contains("CONNECTED_TO_DEDICATED_EQUIPMENT_NOZZLE_DESTINATION_TIE_IN_REQUIRED"));
    assertTrue(xml.contains("UnresolvedBoundary"));
    assertTrue(xml.contains("FLARE_RELIEF_AND_BLOWDOWN"));
    assertTrue(xml.contains("PROCESS_CONTROL_ISOLATION_AND_RECYCLE"));

    String dexpi20 = new String(Files.readAllBytes(result.getDexpi20File()), StandardCharsets.UTF_8);
    assertTrue(dexpi20.contains("<Model"));
    assertTrue(dexpi20.contains("Core/EngineeringModel"));
    assertTrue(dexpi20.contains("Plant/PlantModel"));
    assertTrue(dexpi20.contains("Plant/ProcessEquipment.CentrifugalCompressor"));
    assertTrue(dexpi20.contains("Plant/Piping.Pipe"));
    assertTrue(dexpi20.contains("Plant/Instrumentation.ProcessInstrumentationFunction"));
    assertTrue(dexpi20.contains("Plant/Piping.FlowOutPipeOffPageConnector"));
    assertTrue(dexpi20.contains("20_CD_001"));

    String manifest = new String(Files.readAllBytes(result.getManifestFile()), StandardCharsets.UTF_8);
    assertTrue(manifest.contains("NORSOK P-002"));
    assertTrue(manifest.contains("SIL_UNASSIGNED"));
    assertTrue(manifest.contains("REVIEW_REQUIRED"));
    assertTrue(manifest.contains("DEXPI_XML_2_0_NATIVE"));
    assertTrue(manifest.contains("PROTEUS_4_1_1_COMPATIBLE"));

    String calculations = new String(Files.readAllBytes(result.getCalculationsFile()), StandardCharsets.UTF_8);
    assertTrue(calculations.contains("neqsim_engineering_calculations.v3"));
    assertTrue(calculations.contains("equipmentMechanicalDesign"));
    assertTrue(calculations.contains("PROJECT_DEFINED_SCENARIOS"));
    assertTrue(calculations.contains("CALCULATED_PSV_SIZE_REVIEW_REQUIRED"));
    assertTrue(calculations.contains("tripSettingEnvelopes"));
    assertTrue(calculations.contains("CALCULATED_FEASIBLE_RANGE_REVIEW_REQUIRED"));
    assertTrue(calculations.contains("materialsAndCorrosionScreening"));
    assertTrue(calculations.contains("pipingLineListAndMechanicalDesign"));
    assertTrue(calculations.contains("asmeB31_3MechanicalScreening"), calculations);
    assertTrue(calculations.contains("silAndVotingVerification"));
    assertTrue(calculations.contains("CALCULATED_PFD_AND_ARCHITECTURE_REVIEW_REQUIRED"));
    assertTrue(calculations.contains("proofTestCoverage"));
    assertTrue(calculations.contains("pfhPerHour"));
    assertTrue(calculations.contains("reliefScenarioCoverage"));
    assertTrue(calculations.contains("SCENARIO_SET_COMPLETE_REVIEW_REQUIRED"));
    assertTrue(calculations.contains("shutdownSequenceVerification"));
    assertTrue(calculations.contains("SEQUENCE_COMPLETE_REVIEW_REQUIRED"));
    assertTrue(calculations.contains("installedReliefDeviceVerification"));
    assertTrue(calculations.contains("reliefDisposalNetworkLoads"));
    assertTrue(calculations.contains("engineeringCoverageMatrix"));
    assertTrue(calculations.contains("engineeringEvidenceStatus"));
    assertTrue(calculations.contains("engineeringReadiness"));
    assertTrue(calculations.contains("completenessPercent"));
    assertTrue(calculations.contains("BLOWDOWN_FLARE_INPUT"));
    assertTrue(calculations.contains("fitnessForConstruction"));
    JsonObject calculationJson = JsonParser.parseString(calculations).getAsJsonObject();
    JsonArray blowdown = calculationJson.getAsJsonArray("dynamicBlowdownAndFlareSizing");
    assertEquals("NOT_CALCULATED_NOT_READY", blowdown.get(0).getAsJsonObject().get("status").getAsString());
    JsonArray readiness = calculationJson.getAsJsonArray("engineeringReadiness");
    JsonObject blowdownReadiness = findReadinessTopic(readiness, "BLOWDOWN_FLARE_INPUT");
    assertEquals(0, blowdownReadiness.get("completedItemCount").getAsInt());
    assertEquals("NOT_READY", blowdownReadiness.get("status").getAsString());
    JsonObject pipingReadiness = findReadinessTopic(readiness, "PIPING_GEOMETRY");
    assertEquals(1, pipingReadiness.get("completedItemCount").getAsInt());
    assertEquals(2, pipingReadiness.get("requiredItemCount").getAsInt());
    assertEquals("PARTIALLY_COMPLETE", pipingReadiness.get("status").getAsString());
    JsonObject incompleteRelief = findReliefCoverage(calculationJson.getAsJsonArray("reliefScenarioCoverage"),
        "20-KA-001");
    assertEquals("SCENARIO_SET_INCOMPLETE", incompleteRelief.get("status").getAsString());
    assertEquals(0, incompleteRelief.get("evaluatedScenarioCount").getAsInt());

    String interoperability = new String(Files.readAllBytes(result.getInteroperabilityReportFile()),
        StandardCharsets.UTF_8);
    assertTrue(interoperability.contains("semanticProfileValidation"));
    assertTrue(interoperability.contains("QUALIFICATION_REQUIRED"));

    String packageManifest = new String(Files.readAllBytes(result.getPackageManifestFile()), StandardCharsets.UTF_8);
    assertTrue(packageManifest.contains("neqsim_engineering_package_manifest.v1"));
    assertTrue(packageManifest.contains("plant.dexpi.xml"));
    assertTrue(packageManifest.contains("sha256"));

    String causeAndEffect = new String(Files.readAllBytes(result.getCauseAndEffectFile()), StandardCharsets.UTF_8);
    assertTrue(causeAndEffect.contains("PROPOSED_FOR_HAZOP_LOPA_AND_DISCIPLINE_REVIEW"));
    assertTrue(causeAndEffect.contains("High-high pressure"));
    assertTrue(causeAndEffect.contains("Trip compressor driver"));
    assertTrue(causeAndEffect.contains("votingArchitecture"));
    assertTrue(causeAndEffect.contains("SENSOR:2oo3"));
    assertTrue(causeAndEffect.contains("safetyFunctionDesigns"));
    assertTrue(causeAndEffect.contains("shutdownSequences"));

    ProcessModel processModel = new ProcessModel();
    processModel.add("compression-area", process);
    List<EngineeringProject> areaProjects = NorsokOffshoreEngineeringBuilder.fromProcessModel("Integrated model",
        processModel, false);
    assertEquals(1, areaProjects.size());
    assertEquals("compression-area", areaProjects.get(0).getProcessSystem().getName());
  }

  private static JsonObject findReadinessTopic(JsonArray readiness, String topic) {
    for (int i = 0; i < readiness.size(); i++) {
      JsonObject item = readiness.get(i).getAsJsonObject();
      if (topic.equals(item.get("topic").getAsString())) {
        return item;
      }
    }
    throw new AssertionError("Readiness topic not found: " + topic);
  }

  private static JsonObject findReliefCoverage(JsonArray coverage, String equipmentTag) {
    for (int i = 0; i < coverage.size(); i++) {
      JsonObject item = coverage.get(i).getAsJsonObject();
      if (equipmentTag.equals(item.get("equipmentTag").getAsString())) {
        return item;
      }
    }
    throw new AssertionError("Relief coverage not found for: " + equipmentTag);
  }
}
