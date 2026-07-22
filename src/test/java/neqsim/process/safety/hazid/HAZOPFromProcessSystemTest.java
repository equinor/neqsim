package neqsim.process.safety.hazid;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * Tests for {@link HAZOPTemplate#fromProcessSystem(ProcessSystem)}.
 */
public class HAZOPFromProcessSystemTest {
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
  public void generatesOneNodePerUnitOperation() {
    List<HAZOPTemplate> nodes = HAZOPTemplate.fromProcessSystem(process);
    assertEquals(5, nodes.size());
  }

  @Test
  public void separatorNodeSeedsLevelAndPressureDeviations() {
    List<HAZOPTemplate> nodes = HAZOPTemplate.fromProcessSystem(process);
    HAZOPTemplate separatorNode = findNode(nodes, "hp-separator");
    boolean hasLevel = false;
    boolean hasPressure = false;
    for (HAZOPTemplate.HAZOPDeviation d : separatorNode.getDeviations()) {
      if (d.parameter == HAZOPTemplate.Parameter.LEVEL) {
        hasLevel = true;
      }
      if (d.parameter == HAZOPTemplate.Parameter.PRESSURE) {
        hasPressure = true;
      }
    }
    assertTrue(hasLevel, "separator node should seed LEVEL deviations");
    assertTrue(hasPressure, "separator node should seed PRESSURE deviations");
  }

  @Test
  public void compressorNodeSeedsFlowReverseDeviation() {
    List<HAZOPTemplate> nodes = HAZOPTemplate.fromProcessSystem(process);
    HAZOPTemplate compressorNode = findNode(nodes, "export-compressor");
    boolean hasReverseFlow = false;
    for (HAZOPTemplate.HAZOPDeviation d : compressorNode.getDeviations()) {
      if (d.guideWord == HAZOPTemplate.GuideWord.REVERSE && d.parameter == HAZOPTemplate.Parameter.FLOW) {
        hasReverseFlow = true;
      }
    }
    assertTrue(hasReverseFlow, "compressor node should seed REVERSE FLOW deviation");
  }

  @Test
  public void designIntentReferencesStreamNames() {
    List<HAZOPTemplate> nodes = HAZOPTemplate.fromProcessSystem(process);
    HAZOPTemplate separatorNode = findNode(nodes, "hp-separator");
    assertTrue(separatorNode.getDesignIntent().contains("hp-separator"), "design intent should mention the unit name");
  }

  @Test
  public void nullProcessSystemThrows() {
    assertThrows(IllegalArgumentException.class, new Executable() {
      @Override
      public void execute() {
        HAZOPTemplate.fromProcessSystem(null);
      }
    });
  }

  @Test
  public void seededDeviationsUseTbdPlaceholders() {
    List<HAZOPTemplate> nodes = HAZOPTemplate.fromProcessSystem(process);
    HAZOPTemplate node = nodes.get(0);
    assertFalse(node.getDeviations().isEmpty(), "node should have seeded deviations");
    for (HAZOPTemplate.HAZOPDeviation d : node.getDeviations()) {
      assertEquals("TBD", d.cause);
      assertEquals("TBD", d.consequence);
      assertEquals("TBD", d.safeguard);
    }
  }

  /**
   * Find the HAZOP node whose id contains the given unit name.
   *
   * @param nodes the node list
   * @param unitName the unit name to search for
   * @return the matching node
   */
  private HAZOPTemplate findNode(List<HAZOPTemplate> nodes, String unitName) {
    for (HAZOPTemplate node : nodes) {
      if (node.getNodeId().contains(unitName)) {
        return node;
      }
    }
    throw new IllegalStateException("node not found for unit " + unitName);
  }
}
