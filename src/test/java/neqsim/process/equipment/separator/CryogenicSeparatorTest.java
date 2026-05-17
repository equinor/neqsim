package neqsim.process.equipment.separator;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link CryogenicSeparator}.
 *
 * @author NeqSim
 */
class CryogenicSeparatorTest {

  @Test
  void testCleanCryogenicStream() {
    // Gas with very low CO2 and no water
    SystemInterface cleanGas = new SystemSrkEos(273.15 - 120.0, 35.0);
    cleanGas.addComponent("methane", 0.95);
    cleanGas.addComponent("ethane", 0.04);
    cleanGas.addComponent("nitrogen", 0.01);
    cleanGas.setMixingRule("classic");

    Stream feed = new Stream("cryo_feed", cleanGas);
    feed.setFlowRate(50000.0, "kg/hr");
    feed.setTemperature(-120.0, "C");
    feed.setPressure(35.0, "bara");

    CryogenicSeparator cryoSep = new CryogenicSeparator("Cryo Sep", feed);
    cryoSep.setMaxCO2MolFrac(0.0001);
    cryoSep.setMaxWaterPpm(0.1);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(cryoSep);
    process.run();

    // Clean gas should not have freeze-out risk
    assertFalse(cryoSep.hasCO2FreezeOutRisk(), "Clean gas should not have CO2 freeze-out risk");
    assertFalse(cryoSep.hasWaterIceRisk(), "Clean gas should not have water ice risk");
  }

  @Test
  void testCO2ContaminatedStream() {
    // Gas with too much CO2 at cryogenic temperature
    SystemInterface dirtyGas = new SystemSrkEos(273.15 - 80.0, 35.0);
    dirtyGas.addComponent("methane", 0.90);
    dirtyGas.addComponent("ethane", 0.04);
    dirtyGas.addComponent("CO2", 0.05);
    dirtyGas.addComponent("nitrogen", 0.01);
    dirtyGas.setMixingRule("classic");

    Stream feed = new Stream("dirty_feed", dirtyGas);
    feed.setFlowRate(50000.0, "kg/hr");
    feed.setTemperature(-80.0, "C");
    feed.setPressure(35.0, "bara");

    CryogenicSeparator cryoSep = new CryogenicSeparator("Cryo Sep 2", feed);
    cryoSep.setMaxCO2MolFrac(0.0001);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(cryoSep);
    process.run();

    // 5% CO2 at -80 C should flag freeze-out risk
    assertTrue(cryoSep.hasCO2FreezeOutRisk(), "5% CO2 at -80C should trigger freeze-out risk");
    assertTrue(cryoSep.getCO2MolFrac() > 0.04, "CO2 mole fraction should be detected");
  }

  @Test
  void testAccessors() {
    CryogenicSeparator sep = new CryogenicSeparator("test");
    sep.setMaxCO2MolFrac(0.0005);
    assertEquals(0.0005, sep.getMaxCO2MolFrac(), 1e-10);

    sep.setMaxWaterPpm(0.5);
    assertEquals(0.5, sep.getMaxWaterPpm(), 1e-10);

    assertFalse(sep.isSolidPhaseDetected());
    assertFalse(sep.hasHeavyHCFreezeOutRisk());
  }
}
