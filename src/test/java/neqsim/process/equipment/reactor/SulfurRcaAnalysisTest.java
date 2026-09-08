package neqsim.process.equipment.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import org.junit.jupiter.api.Test;

class SulfurRcaAnalysisTest {
  @Test
  void ranksOxygenExposedFeSLocationAboveCleanSurface() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 40.0, 50.0);
    fluid.addComponent("methane", 0.999);
    fluid.addComponent("H2S", 5.0e-4);
    fluid.addComponent("oxygen", 5.0e-4);
    fluid.addComponent("water", 1.0e-5);
    fluid.setMixingRule(2);

    Stream clean = new Stream("clean pipe", fluid.clone());
    Stream scaled = new Stream("scaled pipe", fluid.clone());
    ProcessSystem process = new ProcessSystem();
    process.add(clean);
    process.add(scaled);
    process.run();

    SulfurRcaAnalysis analysis = new SulfurRcaAnalysis(process);
    analysis.setSurfaceState("scaled pipe", new SulfurRcaAnalysis.SurfaceState().setWettedFraction(0.8)
        .setIronSulfideCoverageFraction(0.9).setReactiveIronOxideFraction(0.2).setWallTemperatureC(35.0));
    analysis.run();

    SulfurRcaAnalysis.LocationResult highest = analysis.getHighestRiskLocation();
    assertNotNull(highest);
    assertEquals("scaled pipe", highest.location);
    assertEquals("FeS oxidation during oxygen ingress", highest.dominantMechanism);
    assertTrue(highest.overallScore > 0.1);
    assertTrue(highest.contributingFactors.contains("historical FeS scale available"));
  }

  @Test
  void noOxygenProducesZeroSulfurOxidationOpportunity() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 50.0, 30.0);
    fluid.addComponent("methane", 0.9999);
    fluid.addComponent("H2S", 1.0e-4);
    fluid.setMixingRule(2);

    Stream stream = new Stream("sour stream", fluid);
    ProcessSystem process = new ProcessSystem();
    process.add(stream);
    process.run();

    SulfurRcaAnalysis analysis = new SulfurRcaAnalysis(process);
    analysis.setSurfaceState("sour stream", new SulfurRcaAnalysis.SurfaceState().setIronSulfideCoverageFraction(1.0));
    analysis.run();

    SulfurRcaAnalysis.LocationResult result = analysis.getHighestRiskLocation();
    assertNotNull(result);
    assertEquals(0.0, result.overallScore, 1.0e-12);
  }
}
