package neqsim.process.equipment.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for ConvergenceDiagnostics recycle/adjuster analysis.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class ConvergenceDiagnosticsTest extends neqsim.NeqSimTest {

  @Test
  public void testSimpleProcessDiagnostics() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 60.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("Feed", fluid);
    feed.setFlowRate(50000.0, "kg/hr");

    Separator sep = new Separator("HP Sep", feed);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sep);
    process.run();

    ConvergenceDiagnostics diag = new ConvergenceDiagnostics(process);
    ConvergenceDiagnostics.DiagnosticReport report = diag.analyze();

    assertNotNull(report);
    // Simple process without recycles should show as converged
    assertTrue(report.isConverged(), "Simple process should show as converged");

    String json = report.toJson();
    assertNotNull(json);
    assertFalse(json.isEmpty());
    assertTrue(json.contains("allConverged"), "JSON should contain convergence status");
  }

  @Test
  public void testProcessWithRecycle() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 60.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("Feed", fluid);
    feed.setFlowRate(50000.0, "kg/hr");

    Stream recycleGuess = new Stream("Recycle Guess", fluid.clone());
    recycleGuess.setFlowRate(5000.0, "kg/hr");

    Mixer mixer = new Mixer("Mixer");
    mixer.addStream(feed);
    mixer.addStream(recycleGuess);

    Separator sep = new Separator("HP Sep", mixer.getOutletStream());

    Recycle recycle = new Recycle("Recycle");
    recycle.addStream(sep.getLiquidOutStream());
    recycle.setOutletStream(recycleGuess);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(recycleGuess);
    process.add(mixer);
    process.add(sep);
    process.add(recycle);
    process.run();

    ConvergenceDiagnostics diag = new ConvergenceDiagnostics(process);
    ConvergenceDiagnostics.DiagnosticReport report = diag.analyze();

    assertNotNull(report);

    String json = report.toJson();
    assertNotNull(json);
    assertTrue(json.contains("Recycle"), "Should mention recycle in report");
  }

  @Test
  public void testSuggestionsContent() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 60.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.15);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("Feed", fluid);
    feed.setFlowRate(10000.0, "kg/hr");

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.run();

    ConvergenceDiagnostics diag = new ConvergenceDiagnostics(process);
    ConvergenceDiagnostics.DiagnosticReport report = diag.analyze();

    assertNotNull(report.getSuggestions());
  }
}
