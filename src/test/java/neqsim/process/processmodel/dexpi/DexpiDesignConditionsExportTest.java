package neqsim.process.processmodel.dexpi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.mechanicaldesign.DesignConditions;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the {@link DesignConditions} holder and its DEXPI export through {@link DexpiXmlWriter}.
 */
class DexpiDesignConditionsExportTest extends NeqSimTest {

  /**
   * Verifies the {@link DesignConditions} accessors, isSet helpers and empty detection.
   */
  @Test
  void testDesignConditionsAccessors() {
    DesignConditions dc = new DesignConditions();
    assertTrue(dc.isEmpty());

    dc.setDesignPressure(120.0).setMaxDesignTemperature(180.0).setMinDesignTemperature(-46.0)
        .setReliefSetPressure(132.0).setCorrosionAllowance(3.0).setConstructionMaterial("Duplex 22Cr")
        .setFailureAction(DesignConditions.FailureAction.FAIL_CLOSED);

    assertFalse(dc.isEmpty());
    assertEquals(120.0, dc.getDesignPressure(), 1e-9);
    assertTrue(dc.isDesignPressureSet());
    assertEquals(180.0, dc.getMaxDesignTemperature(), 1e-9);
    assertEquals(-46.0, dc.getMinDesignTemperature(), 1e-9);
    assertEquals(132.0, dc.getReliefSetPressure(), 1e-9);
    assertEquals(3.0, dc.getCorrosionAllowance(), 1e-9);
    assertEquals("Duplex 22Cr", dc.getConstructionMaterial());
    assertEquals(DesignConditions.FailureAction.FAIL_CLOSED, dc.getFailureAction());
    assertTrue(dc.isFailureActionSet());
    assertNotNull(dc.toJson());
  }

  /**
   * Verifies that declared design conditions on a process equipment item are exported to the DEXPI XML as a
   * {@code Set="DesignConditions"} attribute group.
   *
   * @throws IOException if writing fails
   */
  @Test
  void testDesignConditionsExportedToDexpi() throws IOException {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule(2);
    fluid.init(0);
    Stream feed = new Stream("feed", fluid);
    feed.setPressure(50.0, "bara");
    feed.setTemperature(30.0, "C");
    feed.setFlowRate(1.0, "MSm3/day");

    Separator sep = new Separator("HP-Sep", feed);
    DesignConditions dc = sep.getDesignConditions();
    dc.setDesignPressure(120.0).setMaxDesignTemperature(180.0).setMinDesignTemperature(-46.0)
        .setReliefSetPressure(132.0).setConstructionMaterial("Duplex 22Cr")
        .setFailureAction(DesignConditions.FailureAction.FAIL_CLOSED);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sep);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DexpiXmlWriter.write(process, out);
    String xml = out.toString(StandardCharsets.UTF_8.name());

    assertTrue(xml.contains("Set=\"DesignConditions\""), "Should contain DesignConditions set");
    assertTrue(xml.contains("DesignPressure"), "Should contain DesignPressure attribute");
    assertTrue(xml.contains("ReliefSetPressure"), "Should contain ReliefSetPressure attribute");
    assertTrue(xml.contains("MinimumDesignTemperature"), "Should contain MinimumDesignTemperature attribute");
    assertTrue(xml.contains("ConstructionMaterial"), "Should contain ConstructionMaterial");
    assertTrue(xml.contains("FAIL_CLOSED"), "Should contain the failure action value");
  }
}
