package neqsim.util.validation;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.distillation.DistillationColumn;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.tank.Tank;
import neqsim.process.equipment.util.Adjuster;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Comprehensive tests for validateSetup() implementations across process equipment classes.
 *
 * <p>
 * These tests verify that equipment validation catches common configuration errors before
 * simulation execution, providing actionable remediation hints.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
class EquipmentValidationTest {

  private SystemInterface validFluid;
  private Stream validStream;

  @BeforeEach
  void setUp() {
    validFluid = new SystemSrkEos(298.15, 10.0);
    validFluid.addComponent("methane", 0.7);
    validFluid.addComponent("ethane", 0.2);
    validFluid.addComponent("propane", 0.1);
    validFluid.setMixingRule("classic");
    validFluid.init(0);

    validStream = new Stream("ValidStream", validFluid.clone());
    validStream.run();
  }

  // ============================================================
  // Stream Tests
  // ============================================================
  @Nested
  @DisplayName("Stream validateSetup() Tests")
  class StreamValidationTests {

    @Test
    @DisplayName("Valid stream passes validation")
    void testValidStreamPasses() {
      ValidationResult result = validStream.validateSetup();
      assertTrue(result.isValid(), "Valid stream should pass: " + result.getReport());
    }

    @Test
    @DisplayName("Stream without fluid fails validation")
    void testStreamWithoutFluidFails() {
      Stream emptyStream = new Stream("EmptyStream");
      ValidationResult result = emptyStream.validateSetup();
      assertFalse(result.isValid());
      assertTrue(result.getReport().contains("no thermodynamic system")
          || result.getReport().contains("no components"));
    }

    @Test
    @DisplayName("Stream with low temperature fails validation")
    void testStreamLowTemperatureFails() {
      SystemInterface coldFluid = new SystemSrkEos(0.5, 10.0);
      coldFluid.addComponent("methane", 1.0);
      Stream coldStream = new Stream("ColdStream", coldFluid);
      ValidationResult result = coldStream.validateSetup();
      assertFalse(result.isValid());
      assertTrue(result.getReport().contains("temperature too low")
          || result.getReport().toLowerCase().contains("temperature"));
    }

    @Test
    @DisplayName("Stream validation provides remediation hints")
    void testStreamValidationProvidesRemediation() {
      Stream emptyStream = new Stream("EmptyStream");
      ValidationResult result = emptyStream.validateSetup();
      String report = result.getReport();
      // Should contain remediation hints
      assertTrue(report.contains("new Stream") || report.contains("thermoSystem")
          || report.contains("addComponent"));
    }
  }

  // ============================================================
  // Separator Tests
  // ============================================================
  @Nested
  @DisplayName("Separator validateSetup() Tests")
  class SeparatorValidationTests {

    @Test
    @DisplayName("Valid separator passes validation")
    void testValidSeparatorPasses() {
      Separator separator = new Separator("TestSeparator", validStream);
      ValidationResult result = separator.validateSetup();
      assertTrue(result.isValid(), "Valid separator should pass: " + result.getReport());
    }

    @Test
    @DisplayName("Separator without inlet stream fails validation")
    void testSeparatorWithoutInletFails() {
      Separator separator = new Separator("EmptySeparator");
      ValidationResult result = separator.validateSetup();
      assertFalse(result.isValid());
      assertTrue(
          result.getReport().contains("inlet stream") || result.getReport().contains("stream"));
    }

    @Test
    @DisplayName("Separator validation provides remediation hints")
    void testSeparatorValidationProvidesRemediation() {
      Separator separator = new Separator("EmptySeparator");
      ValidationResult result = separator.validateSetup();
      String report = result.getReport();
      assertTrue(report.contains("setInletStream") || report.contains("addStream"));
    }
  }

  // ============================================================
  // Mixer Tests
  // ============================================================
  @Nested
  @DisplayName("Mixer validateSetup() Tests")
  class MixerValidationTests {

    @Test
    @DisplayName("Valid mixer with multiple streams passes validation")
    void testValidMixerPasses() {
      Mixer mixer = new Mixer("TestMixer");
      mixer.addStream(validStream);
      Stream stream2 = validStream.clone("Stream2");
      mixer.addStream(stream2);
      ValidationResult result = mixer.validateSetup();
      assertTrue(result.isValid(), "Valid mixer should pass: " + result.getReport());
    }

    @Test
    @DisplayName("Mixer without streams fails validation")
    void testMixerWithoutStreamsFails() {
      Mixer mixer = new Mixer("EmptyMixer");
      ValidationResult result = mixer.validateSetup();
      assertFalse(result.isValid());
      assertTrue(result.getReport().contains("stream"));
    }

    @Test
    @DisplayName("Mixer with single stream generates warning")
    void testMixerSingleStreamWarns() {
      Mixer mixer = new Mixer("SingleStreamMixer");
      mixer.addStream(validStream);
      ValidationResult result = mixer.validateSetup();
      // Should be valid but have a warning
      assertTrue(result.isValid());
      assertTrue(result.hasWarnings() || result.getWarnings().size() > 0);
    }
  }

  // ============================================================
  // Splitter Tests
  // ============================================================
  @Nested
  @DisplayName("Splitter validateSetup() Tests")
  class SplitterValidationTests {

    @Test
    @DisplayName("Valid splitter passes validation")
    void testValidSplitterPasses() {
      Splitter splitter = new Splitter("TestSplitter", validStream, 2);
      splitter.setSplitFactors(new double[] {0.5, 0.5});
      ValidationResult result = splitter.validateSetup();
      assertTrue(result.isValid(), "Valid splitter should pass: " + result.getReport());
    }

    @Test
    @DisplayName("Splitter without inlet stream fails validation")
    void testSplitterWithoutInletFails() {
      Splitter splitter = new Splitter("EmptySplitter");
      ValidationResult result = splitter.validateSetup();
      assertFalse(result.isValid());
      assertTrue(
          result.getReport().contains("inlet stream") || result.getReport().contains("stream"));
    }

    @Test
    @DisplayName("Splitter with inlet stream passes")
    void testSplitterWithInletPasses() {
      Splitter splitter = new Splitter("TestSplitter", validStream);
      ValidationResult result = splitter.validateSetup();
      assertTrue(result.isValid(), "Splitter with inlet should pass: " + result.getReport());
    }
  }

  // ============================================================
  // Tank Tests
  // ============================================================
  @Nested
  @DisplayName("Tank validateSetup() Tests")
  class TankValidationTests {

    @Test
    @DisplayName("Valid tank passes validation")
    void testValidTankPasses() {
      Tank tank = new Tank("TestTank", validStream);
      ValidationResult result = tank.validateSetup();
      assertTrue(result.isValid(), "Valid tank should pass: " + result.getReport());
    }

    @Test
    @DisplayName("Tank without inlet stream fails validation")
    void testTankWithoutInletFails() {
      Tank tank = new Tank("EmptyTank");
      ValidationResult result = tank.validateSetup();
      assertFalse(result.isValid());
      assertTrue(
          result.getReport().contains("inlet stream") || result.getReport().contains("stream"));
    }
  }

  // ============================================================
  // TwoPortEquipment Tests (via ThrottlingValve)
  // ============================================================
  @Nested
  @DisplayName("TwoPortEquipment validateSetup() Tests")
  class TwoPortValidationTests {

    @Test
    @DisplayName("Valid valve passes validation")
    void testValidValvePasses() {
      ThrottlingValve valve = new ThrottlingValve("TestValve", validStream);
      valve.setOutletPressure(5.0);
      ValidationResult result = valve.validateSetup();
      assertTrue(result.isValid(), "Valid valve should pass: " + result.getReport());
    }

    @Test
    @DisplayName("Valve without inlet stream fails validation")
    void testValveWithoutInletFails() {
      ThrottlingValve valve = new ThrottlingValve("EmptyValve");
      ValidationResult result = valve.validateSetup();
      assertFalse(result.isValid());
      assertTrue(
          result.getReport().contains("inlet stream") || result.getReport().contains("stream"));
    }
  }

  // ============================================================
  // DistillationColumn Tests
  // ============================================================
  @Nested
  @DisplayName("DistillationColumn validateSetup() Tests")
  class DistillationColumnValidationTests {

    @Test
    @DisplayName("Valid column passes validation")
    void testValidColumnPasses() {
      DistillationColumn column = new DistillationColumn("TestColumn", 5, true, true);
      column.addFeedStream(validStream, 3);
      ValidationResult result = column.validateSetup();
      assertTrue(result.isValid(), "Valid column should pass: " + result.getReport());
    }

    @Test
    @DisplayName("Column without feed streams fails validation")
    void testColumnWithoutFeedsFails() {
      DistillationColumn column = new DistillationColumn("EmptyColumn", 5, true, true);
      ValidationResult result = column.validateSetup();
      assertFalse(result.isValid());
      assertTrue(
          result.getReport().contains("feed stream") || result.getReport().contains("stream"));
    }

    @Test
    @DisplayName("Column without condenser or reboiler generates warning")
    void testColumnNoCondenserReboilerWarns() {
      DistillationColumn column = new DistillationColumn("BasicColumn", 5, false, false);
      column.addFeedStream(validStream, 3);
      ValidationResult result = column.validateSetup();
      assertTrue(result.hasWarnings() || result.getWarnings().size() > 0);
    }
  }

  // ============================================================
  // Recycle Tests
  // ============================================================
  @Nested
  @DisplayName("Recycle validateSetup() Tests")
  class RecycleValidationTests {

    @Test
    @DisplayName("Valid recycle passes validation")
    void testValidRecyclePasses() {
      Recycle recycle = new Recycle("TestRecycle");
      recycle.addStream(validStream);
      recycle.setOutletStream(validStream.clone("RecycleOut"));
      ValidationResult result = recycle.validateSetup();
      assertTrue(result.isValid(), "Valid recycle should pass: " + result.getReport());
    }

    @Test
    @DisplayName("Recycle without streams fails validation")
    void testRecycleWithoutStreamsFails() {
      Recycle recycle = new Recycle("EmptyRecycle");
      ValidationResult result = recycle.validateSetup();
      assertFalse(result.isValid());
      assertTrue(result.getReport().contains("stream"));
    }
  }

  // ============================================================
  // Adjuster Tests
  // ============================================================
  @Nested
  @DisplayName("Adjuster validateSetup() Tests")
  class AdjusterValidationTests {

    @Test
    @DisplayName("Adjuster without adjusted equipment fails validation")
    void testAdjusterWithoutAdjustedFails() {
      Adjuster adjuster = new Adjuster("EmptyAdjuster");
      ValidationResult result = adjuster.validateSetup();
      assertFalse(result.isValid());
      assertTrue(result.getReport().contains("adjusted equipment")
          || result.getReport().contains("adjusted"));
    }

    @Test
    @DisplayName("Adjuster with adjusted equipment passes")
    void testAdjusterWithAdjustedPasses() {
      Adjuster adjuster = new Adjuster("TestAdjuster");
      adjuster.setAdjustedVariable(validStream, "flow rate", "kg/hr");
      adjuster.setTargetValue(100.0);
      ValidationResult result = adjuster.validateSetup();
      assertTrue(result.isValid(),
          "Adjuster with adjusted equipment should pass: " + result.getReport());
    }
  }

  // ============================================================
  // ProcessSystem Validation Tests
  // ============================================================
  @Nested
  @DisplayName("ProcessSystem validateSetup() Tests")
  class ProcessSystemValidationTests {

    @Test
    @DisplayName("Valid process system passes validation")
    void testValidProcessSystemPasses() {
      neqsim.process.processmodel.ProcessSystem process =
          new neqsim.process.processmodel.ProcessSystem("TestProcess");
      process.add(validStream);
      Separator separator = new Separator("TestSeparator", validStream);
      process.add(separator);

      ValidationResult result = process.validateSetup();
      assertTrue(result.isValid(), "Valid process should pass: " + result.getReport());
    }

    @Test
    @DisplayName("Empty process system fails validation")
    void testEmptyProcessSystemFails() {
      neqsim.process.processmodel.ProcessSystem process =
          new neqsim.process.processmodel.ProcessSystem("EmptyProcess");

      ValidationResult result = process.validateSetup();
      assertFalse(result.isValid());
      assertTrue(result.getReport().contains("No unit operations"));
    }

    @Test
    @DisplayName("Process system with invalid equipment fails validation")
    void testProcessSystemWithInvalidEquipmentFails() {
      neqsim.process.processmodel.ProcessSystem process =
          new neqsim.process.processmodel.ProcessSystem("TestProcess");
      process.add(validStream);
      Separator emptySeparator = new Separator("EmptySeparator"); // No inlet stream
      process.add(emptySeparator);

      ValidationResult result = process.validateSetup();
      assertFalse(result.isValid());
      assertTrue(result.getReport().contains("EmptySeparator"));
    }

    @Test
    @DisplayName("ProcessSystem.validateAll() returns individual results")
    void testProcessSystemValidateAll() {
      neqsim.process.processmodel.ProcessSystem process =
          new neqsim.process.processmodel.ProcessSystem("TestProcess");
      process.add(validStream);
      Separator separator = new Separator("TestSeparator", validStream);
      process.add(separator);

      java.util.Map<String, ValidationResult> results = process.validateAll();

      assertEquals(2, results.size());
      assertTrue(results.containsKey("ValidStream"));
      assertTrue(results.containsKey("TestSeparator"));
    }

    @Test
    @DisplayName("ProcessSystem.isReadyToRun() returns correct status")
    void testProcessSystemIsReadyToRun() {
      neqsim.process.processmodel.ProcessSystem validProcess =
          new neqsim.process.processmodel.ProcessSystem("ValidProcess");
      validProcess.add(validStream);
      assertTrue(validProcess.isReadyToRun());

      neqsim.process.processmodel.ProcessSystem emptyProcess =
          new neqsim.process.processmodel.ProcessSystem("EmptyProcess");
      assertFalse(emptyProcess.isReadyToRun());
    }

    @Test
    @DisplayName("Process system already prevents duplicate equipment names")
    void testProcessSystemPreventsDuplicateNames() {
      neqsim.process.processmodel.ProcessSystem process =
          new neqsim.process.processmodel.ProcessSystem("TestProcess");

      Stream stream1 = new Stream("DuplicateName", validFluid.clone());
      Stream stream2 = new Stream("DuplicateName", validFluid.clone());
      process.add(stream1);

      // ProcessSystem throws RuntimeException when adding duplicate names
      Exception exception = assertThrows(RuntimeException.class, () -> {
        process.add(stream2);
      });
      // Verify exception message mentions the duplicate name
      assertTrue(exception.getMessage().contains("DuplicateName")
          || exception.getCause().getMessage().contains("DuplicateName"));
    }
  }

  // ============================================================
  // SimulationValidator Integration Tests
  // ============================================================
  @Nested
  @DisplayName("SimulationValidator Integration Tests")
  class SimulationValidatorTests {

    @Test
    @DisplayName("SimulationValidator.validate() works with Separator")
    void testSimulationValidatorSeparator() {
      Separator separator = new Separator("TestSeparator", validStream);
      ValidationResult result = SimulationValidator.validate(separator);
      assertTrue(result.isValid());
    }

    @Test
    @DisplayName("SimulationValidator.validate() detects errors")
    void testSimulationValidatorDetectsErrors() {
      Separator emptySeparator = new Separator("EmptySeparator");
      ValidationResult result = SimulationValidator.validate(emptySeparator);
      assertFalse(result.isValid());
    }

    @Test
    @DisplayName("SimulationValidator works with null input")
    void testSimulationValidatorNull() {
      ValidationResult result = SimulationValidator.validate(null);
      assertFalse(result.isValid());
    }

    @Test
    @DisplayName("SimulationValidator.validate() works with ProcessSystem")
    void testSimulationValidatorProcessSystem() {
      neqsim.process.processmodel.ProcessSystem process =
          new neqsim.process.processmodel.ProcessSystem("TestProcess");
      process.add(validStream);

      ValidationResult result = SimulationValidator.validate(process);
      assertTrue(result.isValid());
    }
  }

  // ============================================================
  // ProcessModel Validation Tests
  // ============================================================
  @Nested
  @DisplayName("ProcessModel validateSetup() Tests")
  class ProcessModelValidationTests {

    @Test
    @DisplayName("Empty ProcessModel fails validation")
    void testEmptyProcessModelFails() {
      neqsim.process.processmodel.ProcessModel model =
          new neqsim.process.processmodel.ProcessModel();
      ValidationResult result = model.validateSetup();
      assertFalse(result.isValid());
      assertTrue(result.getReport().contains("no processes"));
    }

    @Test
    @DisplayName("Valid ProcessModel passes validation")
    void testValidProcessModelPasses() {
      neqsim.process.processmodel.ProcessModel model =
          new neqsim.process.processmodel.ProcessModel();
      neqsim.process.processmodel.ProcessSystem process =
          new neqsim.process.processmodel.ProcessSystem("Process1");
      process.add(validStream);
      model.add("Process1", process);

      ValidationResult result = model.validateSetup();
      assertTrue(result.isValid(), "Valid model should pass: " + result.getReport());
    }

    @Test
    @DisplayName("ProcessModel with invalid process fails validation")
    void testProcessModelWithInvalidProcessFails() {
      neqsim.process.processmodel.ProcessModel model =
          new neqsim.process.processmodel.ProcessModel();
      neqsim.process.processmodel.ProcessSystem emptyProcess =
          new neqsim.process.processmodel.ProcessSystem("EmptyProcess");
      model.add("EmptyProcess", emptyProcess);

      ValidationResult result = model.validateSetup();
      assertFalse(result.isValid());
    }

    @Test
    @DisplayName("ProcessModel.validateAll() returns per-process results")
    void testProcessModelValidateAll() {
      neqsim.process.processmodel.ProcessModel model =
          new neqsim.process.processmodel.ProcessModel();

      neqsim.process.processmodel.ProcessSystem process1 =
          new neqsim.process.processmodel.ProcessSystem("ValidProcess");
      process1.add(validStream);
      model.add("ValidProcess", process1);

      neqsim.process.processmodel.ProcessSystem process2 =
          new neqsim.process.processmodel.ProcessSystem("EmptyProcess");
      model.add("EmptyProcess", process2);

      java.util.Map<String, ValidationResult> results = model.validateAll();

      assertTrue(results.containsKey("ProcessModel"));
      assertTrue(results.containsKey("ValidProcess"));
      assertTrue(results.containsKey("EmptyProcess"));
      assertTrue(results.get("ValidProcess").isValid());
      assertFalse(results.get("EmptyProcess").isValid());
    }

    @Test
    @DisplayName("ProcessModel.isReadyToRun() returns correct status")
    void testProcessModelIsReadyToRun() {
      neqsim.process.processmodel.ProcessModel validModel =
          new neqsim.process.processmodel.ProcessModel();
      neqsim.process.processmodel.ProcessSystem process =
          new neqsim.process.processmodel.ProcessSystem("ValidProcess");
      process.add(validStream);
      validModel.add("ValidProcess", process);
      assertTrue(validModel.isReadyToRun());

      neqsim.process.processmodel.ProcessModel emptyModel =
          new neqsim.process.processmodel.ProcessModel();
      assertFalse(emptyModel.isReadyToRun());
    }

    @Test
    @DisplayName("ProcessModel.getValidationReport() provides formatted output")
    void testProcessModelValidationReport() {
      neqsim.process.processmodel.ProcessModel model =
          new neqsim.process.processmodel.ProcessModel();
      neqsim.process.processmodel.ProcessSystem emptyProcess =
          new neqsim.process.processmodel.ProcessSystem("EmptyProcess");
      model.add("EmptyProcess", emptyProcess);

      String report = model.getValidationReport();
      assertTrue(report.contains("ProcessModel Validation Report"));
      assertTrue(report.contains("EmptyProcess"));
      assertTrue(report.contains("issue"));
    }

    @Test
    @DisplayName("ProcessModel aggregates validation from multiple processes")
    void testProcessModelAggregatesValidation() {
      neqsim.process.processmodel.ProcessModel model =
          new neqsim.process.processmodel.ProcessModel();

      // Add two empty processes
      neqsim.process.processmodel.ProcessSystem process1 =
          new neqsim.process.processmodel.ProcessSystem("Empty1");
      neqsim.process.processmodel.ProcessSystem process2 =
          new neqsim.process.processmodel.ProcessSystem("Empty2");
      model.add("Empty1", process1);
      model.add("Empty2", process2);

      ValidationResult result = model.validateSetup();
      assertFalse(result.isValid());
      // Should have issues from both processes
      String report = result.getReport();
      assertTrue(report.contains("Empty1") || report.contains("[Empty1]"));
      assertTrue(report.contains("Empty2") || report.contains("[Empty2]"));
    }
  }
}
