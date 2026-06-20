package neqsim.process.safety;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link ProcessSafetyScenario#generateFromTopology(ProcessSystem)}.
 */
public class ProcessSafetyScenarioTopologyTest {
  private ProcessSystem process;

  /**
   * Build a small representative flowsheet (valve, separator, compressor, cooler).
   */
  @BeforeEach
  public void setUp() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 60.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(100.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(60.0, "bara");

    ThrottlingValve valve = new ThrottlingValve("inlet-valve", feed);
    valve.setOutletPressure(50.0);

    Separator separator = new Separator("hp-separator", valve.getOutletStream());

    Compressor compressor = new Compressor("export-compressor", separator.getGasOutStream());
    compressor.setOutletPressure(120.0);

    Cooler cooler = new Cooler("export-cooler", compressor.getOutletStream());
    cooler.setOutTemperature(313.15);

    process = new ProcessSystem();
    process.add(feed);
    process.add(valve);
    process.add(separator);
    process.add(compressor);
    process.add(cooler);
  }

  @Test
  public void generatesScenariosForRelevantEquipment() {
    List<ProcessSafetyScenario> scenarios = ProcessSafetyScenario.generateFromTopology(process);
    assertFalse(scenarios.isEmpty(), "should generate at least one scenario");
  }

  @Test
  public void separatorYieldsBlockedOutletScenario() {
    List<ProcessSafetyScenario> scenarios = ProcessSafetyScenario.generateFromTopology(process);
    boolean found = false;
    for (ProcessSafetyScenario s : scenarios) {
      if (s.getName().contains("hp-separator") && s.getBlockedOutletUnits().contains("hp-separator")) {
	found = true;
      }
    }
    assertTrue(found, "separator should yield a blocked-outlet scenario");
  }

  @Test
  public void compressorYieldsUtilityLossScenario() {
    List<ProcessSafetyScenario> scenarios = ProcessSafetyScenario.generateFromTopology(process);
    boolean found = false;
    for (ProcessSafetyScenario s : scenarios) {
      if (s.getName().contains("export-compressor") && s.getUtilityLossUnits().contains("export-compressor")) {
	found = true;
      }
    }
    assertTrue(found, "compressor should yield a utility-loss scenario");
  }

  @Test
  public void coolerYieldsUtilityLossScenario() {
    List<ProcessSafetyScenario> scenarios = ProcessSafetyScenario.generateFromTopology(process);
    boolean found = false;
    for (ProcessSafetyScenario s : scenarios) {
      if (s.getName().contains("export-cooler") && s.getUtilityLossUnits().contains("export-cooler")) {
	found = true;
      }
    }
    assertTrue(found, "cooler should yield a utility-loss scenario");
  }

  @Test
  public void nullProcessSystemThrows() {
    assertThrows(IllegalArgumentException.class, new Executable() {
      @Override
      public void execute() {
	ProcessSafetyScenario.generateFromTopology(null);
      }
    });
  }
}
