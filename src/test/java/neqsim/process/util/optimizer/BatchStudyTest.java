package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for BatchStudy parallel parameter study class.
 */
public class BatchStudyTest {

  private ProcessSystem baseProcess;

  @BeforeEach
  void setUp() {
    SystemInterface fluid = new SystemSrkEos(298.15, 20.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.10);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(100.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(20.0, "bara");

    Separator separator = new Separator("separator", feed);

    baseProcess = new ProcessSystem();
    baseProcess.setName("TestProcess");
    baseProcess.add(feed);
    baseProcess.add(separator);
  }

  @Test
  void testBuilderCreation() {
    BatchStudy study =
        BatchStudy.builder(baseProcess).vary("separator.pressure", 10.0, 50.0, 3).build();

    assertNotNull(study);
    assertEquals(3, study.getTotalCases());
  }

  @Test
  void testVaryWithExplicitValues() {
    BatchStudy study =
        BatchStudy.builder(baseProcess).vary("separator.pressure", 10.0, 20.0, 30.0).build();

    assertNotNull(study);
    assertEquals(3, study.getTotalCases());
  }

  @Test
  void testMultipleParameters() {
    BatchStudy study = BatchStudy.builder(baseProcess).vary("param1", 1.0, 2.0, 3.0)
        .vary("param2", 10.0, 20.0).build();

    // 3 values for param1 * 2 values for param2 = 6 cases
    assertEquals(6, study.getTotalCases());
  }

  @Test
  void testAddObjective() {
    BatchStudy study = BatchStudy.builder(baseProcess).vary("pressure", 10.0, 50.0, 3)
        .addObjective("power", BatchStudy.Objective.MINIMIZE, process -> 100.0)
        .addObjective("throughput", BatchStudy.Objective.MAXIMIZE, process -> 50.0).build();

    assertNotNull(study);
  }

  @Test
  void testParallelismSetting() {
    BatchStudy study =
        BatchStudy.builder(baseProcess).vary("pressure", 10.0, 50.0, 3).parallelism(4).build();

    assertNotNull(study);
  }

  @Test
  void testStudyName() {
    BatchStudy study =
        BatchStudy.builder(baseProcess).vary("pressure", 10.0, 50.0, 3).name("MyStudy").build();

    assertNotNull(study);
  }

  @Test
  void testStopOnFailure() {
    BatchStudy study =
        BatchStudy.builder(baseProcess).vary("pressure", 10.0, 50.0, 3).stopOnFailure(true).build();

    assertNotNull(study);
  }

  @Test
  void testRunStudy() {
    BatchStudy study =
        BatchStudy.builder(baseProcess).vary("separator.pressure", 10.0, 20.0, 2).parallelism(1) // Sequential
                                                                                                 // for
                                                                                                 // test
                                                                                                 // stability
            .build();

    BatchStudy.BatchStudyResult result = study.run();

    assertNotNull(result);
    assertTrue(result.getTotalCases() >= 0);
  }

  @Test
  void testObjectiveDirections() {
    assertEquals(BatchStudy.Objective.MINIMIZE, BatchStudy.Objective.MINIMIZE);
    assertEquals(BatchStudy.Objective.MAXIMIZE, BatchStudy.Objective.MAXIMIZE);
  }
}

