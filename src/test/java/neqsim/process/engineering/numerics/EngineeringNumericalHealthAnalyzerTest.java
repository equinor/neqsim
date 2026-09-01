package neqsim.process.engineering.numerics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import neqsim.process.engineering.numerics.EngineeringNumericalHealthReport.Status;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import org.junit.jupiter.api.Test;

/** Tests fail-closed numerical health and explicit engineering closure evidence. */
class EngineeringNumericalHealthAnalyzerTest {

  @Test
  void solvedProcessWithApplicableMassClosureIsHealthy() {
    EngineeringNumericalHealthReport report = EngineeringNumericalHealthAnalyzer.analyze(process());

    assertEquals(Status.HEALTHY, report.getStatus());
    assertTrue(report.isAcceptableForEngineering());
    assertTrue(report.toJson().contains("massClosure"));
  }

  @Test
  void requiredEvidenceMustBePresentAndWithinTolerance() {
    EngineeringNumericalHealthCriteria criteria = EngineeringNumericalHealthCriteria.builder()
        .requireEnergyClosure(true).requireEquationResiduals(true).requireSensitivityEvidence(true).build();

    EngineeringNumericalHealthReport incomplete = new EngineeringNumericalHealthAnalyzer(process(), criteria).analyze();
    EngineeringNumericalHealthReport healthy = new EngineeringNumericalHealthAnalyzer(process(), criteria)
        .addEnergyClosure("whole-process", 0.02, 100.0, "kW")
        .addEquationResidual("recycle-flow", 1.0e-7, 1.0e-6, "kg/sec")
        .sensitivityJacobian(new double[][] { { 1.0, 0.1 }, { 0.2, 2.0 } }).analyze();

    assertEquals(Status.INCOMPLETE, incomplete.getStatus());
    assertEquals(Status.HEALTHY, healthy.getStatus());
  }

  @Test
  void closureOrRankFailureBlocksEngineeringAcceptance() {
    EngineeringNumericalHealthReport report = new EngineeringNumericalHealthAnalyzer(process(),
        EngineeringNumericalHealthCriteria.defaults()).addEnergyClosure("whole-process", 2.0, 100.0, "kW")
        .sensitivityJacobian(new double[][] { { 1.0, 2.0 }, { 2.0, 4.0 } }).analyze();

    assertEquals(Status.FAILED, report.getStatus());
    assertFalse(report.isAcceptableForEngineering());
    assertEquals(1, report.getJacobianHealth().getNumericalRank());
  }

  private ProcessSystem process() {
    SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("FEED", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    Heater heater = new Heater("HEATER", feed);
    heater.setOutTemperature(310.0);
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(heater);
    process.run();
    return process;
  }
}
