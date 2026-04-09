package neqsim.process.mechanicaldesign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.mechanicaldesign.StudyClass.DeliverableType;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for StudyClass, EngineeringDeliverablesPackage, and orchestrator integration.
 *
 * @author esol
 */
class EngineeringDeliverablesPackageTest {
  static ProcessSystem process;

  @BeforeAll
  static void setUp() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 30.0, 60.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("ethane", 0.1);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-butane", 0.03);
    fluid.addComponent("water", 0.02);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream feed = new Stream("Feed", fluid);
    feed.setFlowRate(50000.0, "kg/hr");
    feed.setTemperature(30.0, "C");
    feed.setPressure(60.0, "bara");

    Separator hpSep = new Separator("HP-Sep", feed);
    Stream gasOut = new Stream("Gas Out", hpSep.getGasOutStream());

    Compressor comp = new Compressor("Comp-1", gasOut);
    comp.setOutletPressure(120.0);

    Cooler cooler = new Cooler("After Cooler", comp.getOutletStream());
    cooler.setOutTemperature(273.15 + 40.0);

    ThrottlingValve valve = new ThrottlingValve("JT Valve", cooler.getOutletStream());
    valve.setOutletPressure(30.0);

    Heater heater = new Heater("Reboiler", hpSep.getLiquidOutStream());
    heater.setOutTemperature(273.15 + 100.0);

    process = new ProcessSystem();
    process.add(feed);
    process.add(hpSep);
    process.add(gasOut);
    process.add(comp);
    process.add(cooler);
    process.add(valve);
    process.add(heater);
    process.run();
  }

  // ============================================================================
  // StudyClass Enum Tests
  // ============================================================================
  @Nested
  @DisplayName("StudyClass Enum Tests")
  class StudyClassTests {
    @Test
    @DisplayName("Class A should require all deliverables")
    void classAShouldRequireAll() {
      Set<DeliverableType> required = StudyClass.CLASS_A.getRequiredDeliverables();
      assertEquals(DeliverableType.values().length, required.size());
      for (DeliverableType type : DeliverableType.values()) {
        assertTrue(StudyClass.CLASS_A.requires(type), "Class A should require " + type);
      }
    }

    @Test
    @DisplayName("Class B should require PFD, thermal, fire only")
    void classBShouldRequireSubset() {
      assertTrue(StudyClass.CLASS_B.requires(DeliverableType.PFD));
      assertTrue(StudyClass.CLASS_B.requires(DeliverableType.THERMAL_UTILITIES));
      assertTrue(StudyClass.CLASS_B.requires(DeliverableType.FIRE_SCENARIOS));
      assertFalse(StudyClass.CLASS_B.requires(DeliverableType.ALARM_TRIP_SCHEDULE));
      assertFalse(StudyClass.CLASS_B.requires(DeliverableType.SPARE_PARTS));
      assertFalse(StudyClass.CLASS_B.requires(DeliverableType.NOISE_ASSESSMENT));
    }

    @Test
    @DisplayName("Class C should require PFD only")
    void classCShouldRequirePfdOnly() {
      Set<DeliverableType> required = StudyClass.CLASS_C.getRequiredDeliverables();
      assertEquals(1, required.size());
      assertTrue(StudyClass.CLASS_C.requires(DeliverableType.PFD));
    }

    @Test
    @DisplayName("Should have display names")
    void shouldHaveDisplayNames() {
      assertNotNull(StudyClass.CLASS_A.getDisplayName());
      assertNotNull(StudyClass.CLASS_B.getDisplayName());
      assertNotNull(StudyClass.CLASS_C.getDisplayName());
      assertTrue(StudyClass.CLASS_A.getDisplayName().contains("FEED"));
    }

    @Test
    @DisplayName("DeliverableType should have display names")
    void deliverableTypeShouldHaveDisplayNames() {
      for (DeliverableType type : DeliverableType.values()) {
        assertNotNull(type.getDisplayName());
        assertFalse(type.getDisplayName().trim().isEmpty());
      }
    }

    @Test
    @DisplayName("Required deliverables should be unmodifiable")
    void requiredDeliverablesShouldBeUnmodifiable() {
      Set<DeliverableType> required = StudyClass.CLASS_A.getRequiredDeliverables();
      assertThrows(UnsupportedOperationException.class, () -> required.add(DeliverableType.PFD));
    }
  }

  // ============================================================================
  // EngineeringDeliverablesPackage Tests
  // ============================================================================
  @Nested
  @DisplayName("EngineeringDeliverablesPackage Tests")
  class PackageTests {
    @Test
    @DisplayName("Should throw for null process system")
    void shouldThrowForNullProcess() {
      assertThrows(IllegalArgumentException.class,
          () -> new EngineeringDeliverablesPackage(null, StudyClass.CLASS_A));
    }

    @Test
    @DisplayName("Should default to CLASS_A for null study class")
    void shouldDefaultToClassA() {
      EngineeringDeliverablesPackage pkg = new EngineeringDeliverablesPackage(process, null);
      assertEquals(StudyClass.CLASS_A, pkg.getStudyClass());
    }

    @Test
    @DisplayName("Should not be generated before calling generate()")
    void shouldNotBeGeneratedInitially() {
      EngineeringDeliverablesPackage pkg =
          new EngineeringDeliverablesPackage(process, StudyClass.CLASS_A);
      assertFalse(pkg.isGenerated());
      assertFalse(pkg.isComplete());
    }

    @Test
    @DisplayName("Class A should generate all 8 deliverables")
    void classAShouldGenerateAll() {
      EngineeringDeliverablesPackage pkg =
          new EngineeringDeliverablesPackage(process, StudyClass.CLASS_A);
      pkg.generate();

      assertTrue(pkg.isGenerated());
      assertTrue(pkg.isComplete());
      assertEquals(8, pkg.getSuccessCount());
      assertTrue(pkg.getFailedDeliverables().isEmpty());
    }

    @Test
    @DisplayName("Class B should generate 5 deliverables")
    void classBShouldGenerateThree() {
      EngineeringDeliverablesPackage pkg =
          new EngineeringDeliverablesPackage(process, StudyClass.CLASS_B);
      pkg.generate();

      assertTrue(pkg.isGenerated());
      assertTrue(pkg.isComplete());
      assertEquals(5, pkg.getSuccessCount());
    }

    @Test
    @DisplayName("Class C should generate 1 deliverable")
    void classCShouldGenerateOne() {
      EngineeringDeliverablesPackage pkg =
          new EngineeringDeliverablesPackage(process, StudyClass.CLASS_C);
      pkg.generate();

      assertTrue(pkg.isGenerated());
      assertTrue(pkg.isComplete());
      assertEquals(1, pkg.getSuccessCount());
    }

    @Test
    @DisplayName("Should produce PFD DOT output")
    void shouldProducePfd() {
      EngineeringDeliverablesPackage pkg =
          new EngineeringDeliverablesPackage(process, StudyClass.CLASS_A);
      pkg.generate();

      String dot = pkg.getPfdDot();
      assertNotNull(dot);
      assertTrue(dot.contains("digraph"));
    }

    @Test
    @DisplayName("Should produce thermal utility summary")
    void shouldProduceThermalUtilities() {
      EngineeringDeliverablesPackage pkg =
          new EngineeringDeliverablesPackage(process, StudyClass.CLASS_A);
      pkg.generate();

      ThermalUtilitySummary util = pkg.getThermalUtilities();
      assertNotNull(util);
    }

    @Test
    @DisplayName("Should produce alarm/trip schedule")
    void shouldProduceAlarmTrip() {
      EngineeringDeliverablesPackage pkg =
          new EngineeringDeliverablesPackage(process, StudyClass.CLASS_A);
      pkg.generate();

      AlarmTripScheduleGenerator alarms = pkg.getAlarmTripSchedule();
      assertNotNull(alarms);
      assertFalse(alarms.getEntries().isEmpty());
    }

    @Test
    @DisplayName("Should produce spare parts inventory")
    void shouldProduceSpares() {
      EngineeringDeliverablesPackage pkg =
          new EngineeringDeliverablesPackage(process, StudyClass.CLASS_A);
      pkg.generate();

      SparePartsInventory spares = pkg.getSparePartsInventory();
      assertNotNull(spares);
      assertFalse(spares.getEntries().isEmpty());
    }

    @Test
    @DisplayName("Should produce fire scenario JSON")
    void shouldProduceFireScenarios() {
      EngineeringDeliverablesPackage pkg =
          new EngineeringDeliverablesPackage(process, StudyClass.CLASS_A);
      pkg.generate();

      String json = pkg.getFireScenarioJson();
      assertNotNull(json);
      assertTrue(json.contains("fireScenario"));
    }

    @Test
    @DisplayName("Should produce noise assessment JSON")
    void shouldProduceNoiseAssessment() {
      EngineeringDeliverablesPackage pkg =
          new EngineeringDeliverablesPackage(process, StudyClass.CLASS_A);
      pkg.generate();

      String json = pkg.getNoiseAssessmentJson();
      assertNotNull(json);
      assertTrue(json.contains("compressorSwlDbA"));
    }

    @Test
    @DisplayName("Class B should not produce alarm/trip or spare parts")
    void classBShouldSkipAlarmAndSpares() {
      EngineeringDeliverablesPackage pkg =
          new EngineeringDeliverablesPackage(process, StudyClass.CLASS_B);
      pkg.generate();

      assertNull(pkg.getAlarmTripSchedule());
      assertNull(pkg.getSparePartsInventory());
      assertNull(pkg.getNoiseAssessmentJson());
    }

    @Test
    @DisplayName("Should produce comprehensive JSON")
    void shouldProduceJson() {
      EngineeringDeliverablesPackage pkg =
          new EngineeringDeliverablesPackage(process, StudyClass.CLASS_A);
      pkg.generate();

      String json = pkg.toJson();
      assertNotNull(json);
      assertTrue(json.contains("CLASS_A"));
      assertTrue(json.contains("deliverableStatus"));
      assertTrue(json.contains("thermalUtilities"));
      assertTrue(json.contains("pfdDot"));
    }

    @Test
    @DisplayName("Status map should have entries for all required deliverables")
    void statusMapShouldCoverAllRequired() {
      EngineeringDeliverablesPackage pkg =
          new EngineeringDeliverablesPackage(process, StudyClass.CLASS_A);
      pkg.generate();

      Map<DeliverableType, EngineeringDeliverablesPackage.DeliverableStatus> statusMap =
          pkg.getStatusMap();
      assertEquals(8, statusMap.size());
      for (EngineeringDeliverablesPackage.DeliverableStatus status : statusMap.values()) {
        assertTrue(status.isSuccess());
        assertTrue(status.getDurationMs() >= 0);
        assertNotNull(status.getMessage());
      }
    }

    @Test
    @DisplayName("toString should include study class and status")
    void toStringShouldBeDescriptive() {
      EngineeringDeliverablesPackage pkg =
          new EngineeringDeliverablesPackage(process, StudyClass.CLASS_A);
      pkg.generate();

      String str = pkg.toString();
      assertTrue(str.contains("FEED"));
      assertTrue(str.contains("generated"));
    }
  }

  // ============================================================================
  // Orchestrator Integration Tests
  // ============================================================================
  @Nested
  @DisplayName("Orchestrator Integration Tests")
  class OrchestratorIntegrationTests {
    @Test
    @DisplayName("Should generate deliverables when study class is set")
    void shouldGenerateDeliverablesWithStudyClass() {
      FieldDevelopmentDesignOrchestrator orch =
          new FieldDevelopmentDesignOrchestrator(process, "DELIV-001");
      orch.setStudyClass(StudyClass.CLASS_A);
      orch.runCompleteDesignWorkflow();

      EngineeringDeliverablesPackage pkg = orch.getEngineeringDeliverables();
      assertNotNull(pkg, "Deliverables should be generated when study class is set");
      assertTrue(pkg.isGenerated());
      assertTrue(pkg.isComplete());
      assertEquals(StudyClass.CLASS_A, pkg.getStudyClass());
    }

    @Test
    @DisplayName("Should not generate deliverables when study class is null")
    void shouldNotGenerateDeliverablesWithoutStudyClass() {
      FieldDevelopmentDesignOrchestrator orch =
          new FieldDevelopmentDesignOrchestrator(process, "NO-DELIV");
      // study class is null by default
      orch.runCompleteDesignWorkflow();

      assertNull(orch.getEngineeringDeliverables());
    }

    @Test
    @DisplayName("Report should include deliverables section")
    void reportShouldIncludeDeliverables() {
      FieldDevelopmentDesignOrchestrator orch =
          new FieldDevelopmentDesignOrchestrator(process, "RPT-001");
      orch.setStudyClass(StudyClass.CLASS_A);
      orch.runCompleteDesignWorkflow();

      String report = orch.generateDesignReport();
      assertTrue(report.contains("ENGINEERING DELIVERABLES"));
      assertTrue(report.contains("Class A"));
      assertTrue(report.contains("COMPLETE"));
    }

    @Test
    @DisplayName("Workflow history should include deliverables step")
    void workflowHistoryShouldIncludeDeliverablesStep() {
      FieldDevelopmentDesignOrchestrator orch =
          new FieldDevelopmentDesignOrchestrator(process, "HIST-001");
      orch.setStudyClass(StudyClass.CLASS_B);
      orch.runCompleteDesignWorkflow();

      boolean hasDeliverablesStep = false;
      for (FieldDevelopmentDesignOrchestrator.WorkflowStep step : orch.getWorkflowHistory()) {
        if (step.getStepName().contains("Deliverables")) {
          hasDeliverablesStep = true;
          break;
        }
      }
      assertTrue(hasDeliverablesStep,
          "Workflow history should contain an Engineering Deliverables step");
    }

    @Test
    @DisplayName("setStudyClass should support method chaining")
    void setStudyClassShouldChain() {
      FieldDevelopmentDesignOrchestrator orch =
          new FieldDevelopmentDesignOrchestrator(process, "CHAIN-001");
      FieldDevelopmentDesignOrchestrator result = orch.setStudyClass(StudyClass.CLASS_B);
      assertEquals(orch, result);
      assertEquals(StudyClass.CLASS_B, orch.getStudyClass());
    }

    @Test
    @DisplayName("Class B orchestrator should produce 5 deliverables")
    void classBOrchestratorShouldProduceThree() {
      FieldDevelopmentDesignOrchestrator orch =
          new FieldDevelopmentDesignOrchestrator(process, "CLASSB-001");
      orch.setStudyClass(StudyClass.CLASS_B);
      orch.runCompleteDesignWorkflow();

      EngineeringDeliverablesPackage pkg = orch.getEngineeringDeliverables();
      assertNotNull(pkg);
      assertEquals(5, pkg.getSuccessCount());
    }
  }
}
