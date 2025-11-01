package neqsim.process.mechanicaldesign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import neqsim.process.equipment.pipeline.Pipeline;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.mechanicaldesign.MechanicalDesignMarginResult;
import neqsim.process.mechanicaldesign.data.CsvMechanicalDesignDataSource;
import neqsim.process.mechanicaldesign.pipeline.PipelineMechanicalDesign;
import neqsim.process.mechanicaldesign.separator.SeparatorMechanicalDesign;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MechanicalDesignDataSourceTest {
  private static Path csvPath;

  @BeforeAll
  static void setup() {
    csvPath = Paths.get("src", "test", "resources", "design_limits_test.csv");
  }

  @Test
  void pipelineDesignLoadsCsvAndComputesMargins() {
    Pipeline pipeline = new Pipeline("TestPipeline");
    PipelineMechanicalDesign design = new PipelineMechanicalDesign(pipeline);
    design.setDesignDataSource(new CsvMechanicalDesignDataSource(csvPath));
    design.setCompanySpecificDesignStandards("TestCo");
    design.setMaxOperationPressure(120.0);
    design.setMinOperationPressure(15.0);
    design.setMaxOperationTemperature(360.0);
    design.setMinOperationTemperature(265.0);
    design.setCorrosionAllowance(3.5);
    design.setJointEfficiency(0.97);

    MechanicalDesignMarginResult margins = design.validateOperatingEnvelope();

    assertEquals(150.0, design.getDesignMaxPressureLimit(), 1e-8);
    assertEquals(10.0, design.getDesignMinPressureLimit(), 1e-8);
    assertEquals(410.0, design.getDesignMaxTemperatureLimit(), 1e-8);
    assertEquals(250.0, design.getDesignMinTemperatureLimit(), 1e-8);
    assertEquals(3.0, design.getDesignCorrosionAllowance(), 1e-8);
    assertEquals(0.95, design.getDesignJointEfficiency(), 1e-8);

    assertEquals(30.0, margins.getMaxPressureMargin(), 1e-8);
    assertEquals(5.0, margins.getMinPressureMargin(), 1e-8);
    assertEquals(50.0, margins.getMaxTemperatureMargin(), 1e-8);
    assertEquals(15.0, margins.getMinTemperatureMargin(), 1e-8);
    assertEquals(0.5, margins.getCorrosionAllowanceMargin(), 1e-8);
    assertEquals(0.02, margins.getJointEfficiencyMargin(), 1e-8);
    assertTrue(margins.isWithinDesignEnvelope());
  }

  @Test
  void separatorDesignLoadsCsvAndComputesMargins() {
    Separator separator = new Separator("TestSeparator");
    SeparatorMechanicalDesign design = new SeparatorMechanicalDesign(separator);
    design.setDesignDataSource(new CsvMechanicalDesignDataSource(csvPath));
    design.setCompanySpecificDesignStandards("TestCo");
    design.setMaxOperationPressure(100.0);
    design.setMinOperationPressure(7.0);
    design.setMaxOperationTemperature(370.0);
    design.setMinOperationTemperature(270.0);
    design.setCorrosionAllowance(6.5);
    design.setJointEfficiency(0.92);

    MechanicalDesignMarginResult margins = design.validateOperatingEnvelope();

    assertEquals(110.0, design.getDesignMaxPressureLimit(), 1e-8);
    assertEquals(5.0, design.getDesignMinPressureLimit(), 1e-8);
    assertEquals(390.0, design.getDesignMaxTemperatureLimit(), 1e-8);
    assertEquals(260.0, design.getDesignMinTemperatureLimit(), 1e-8);

    assertEquals(10.0, margins.getMaxPressureMargin(), 1e-8);
    assertEquals(2.0, margins.getMinPressureMargin(), 1e-8);
    assertEquals(20.0, margins.getMaxTemperatureMargin(), 1e-8);
    assertEquals(10.0, margins.getMinTemperatureMargin(), 1e-8);
    assertEquals(0.5, margins.getCorrosionAllowanceMargin(), 1e-8);
    assertEquals(0.02, margins.getJointEfficiencyMargin(), 1e-8);
    assertTrue(margins.isWithinDesignEnvelope());
  }
}
