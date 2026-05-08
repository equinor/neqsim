package neqsim.process.safety.qra;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.safety.dispersion.ProbitModel;
import neqsim.process.safety.fire.JetFireModel;

class ConsequenceAnalysisEngineTest {

  @Test
  void jetFireIndividualRiskIsPositiveAndBounded() {
    // 30 kg/s leak, 50 MJ/kg HoC, η=0.25 → ~375 MW total, plenty of flux at 10 m
    JetFireModel jet = new JetFireModel(30.0, 50.0e6, 0.25);
    ConsequenceAnalysisEngine e = new ConsequenceAnalysisEngine("Leak", 1.0e-4);
    e.addJetFire(0.05, jet, ProbitModel.thermalFatality(), 60.0);
    double r = e.individualFatalityRiskPerYear(10.0);
    assertTrue(r > 0.0);
    assertTrue(r < 1.0e-4);
  }

  @Test
  void reportContainsScenarioName() {
    JetFireModel jet = new JetFireModel(30.0, 50.0e6, 0.25);
    ConsequenceAnalysisEngine e = new ConsequenceAnalysisEngine("Test scenario", 1.0e-4);
    e.addJetFire(0.05, jet, ProbitModel.thermalFatality(), 60.0);
    assertTrue(e.report(10.0).contains("Test scenario"));
  }
}
