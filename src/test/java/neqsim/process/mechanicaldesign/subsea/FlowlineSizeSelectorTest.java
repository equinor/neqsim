package neqsim.process.mechanicaldesign.subsea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Tests the API RP 14E flowline size selector. */
public class FlowlineSizeSelectorTest {
  @Test
  void erosionalVelocityMatchesTheApiRp14eForm() {
    // 1.22 * 100 / sqrt(100) = 12.2 m/s
    assertEquals(12.2, FlowlineSizeSelector.erosionalVelocity(100.0, 100.0), 1.0e-9);
    // Denser fluid gives a lower limit.
    assertTrue(
        FlowlineSizeSelector.erosionalVelocity(800.0, 100.0) < FlowlineSizeSelector.erosionalVelocity(100.0, 100.0));
  }

  @Test
  void aNonPositiveDensityIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> FlowlineSizeSelector.erosionalVelocity(0.0, 100.0));
  }

  @Test
  void theSmallestPassingSizeIsSelected() {
    FlowlineSizeSelector selector = new FlowlineSizeSelector().setMassFlowRate(16.13).setMixtureDensity(108.3)
        .setWallThickness(0.0159);
    Map<String, Object> selected = selector.select();
    assertNotNull(selected);
    List<Map<String, Object>> candidates = selector.getCandidates();

    // Every candidate below the selection must have failed.
    boolean seenSelected = false;
    for (int i = 0; i < candidates.size(); i++) {
      boolean acceptable = ((Boolean) candidates.get(i).get("acceptable")).booleanValue();
      if (!seenSelected && acceptable) {
        assertEquals(candidates.get(i).get("nominalSize_inch"), selected.get("nominalSize_inch"));
        seenSelected = true;
      } else if (!seenSelected) {
        assertFalse(acceptable);
      }
    }
    assertTrue(seenSelected);
  }

  @Test
  void velocityFallsAsTheLineGetsBigger() {
    FlowlineSizeSelector selector = new FlowlineSizeSelector().setMassFlowRate(16.13).setMixtureDensity(108.3);
    selector.select();
    List<Map<String, Object>> candidates = selector.getCandidates();
    for (int i = 1; i < candidates.size(); i++) {
      double previous = ((Double) candidates.get(i - 1).get("velocity_m_per_s")).doubleValue();
      double current = ((Double) candidates.get(i).get("velocity_m_per_s")).doubleValue();
      assertTrue(current < previous, "velocity must fall as diameter grows");
    }
  }

  @Test
  void aTargetVelocityCeilingPushesTheSelectionUp() {
    FlowlineSizeSelector loose = new FlowlineSizeSelector().setMassFlowRate(16.13).setMixtureDensity(108.3);
    double looseSize = ((Double) loose.select().get("nominalSize_inch")).doubleValue();

    FlowlineSizeSelector tight = new FlowlineSizeSelector().setMassFlowRate(16.13).setMixtureDensity(108.3)
        .setTargetVelocity(3.0);
    double tightSize = ((Double) tight.select().get("nominalSize_inch")).doubleValue();
    assertTrue(tightSize > looseSize, "a velocity ceiling must select a larger line");
  }

  @Test
  void anImpossibleDutyReturnsNoSelection() {
    FlowlineSizeSelector selector = new FlowlineSizeSelector().setMassFlowRate(1.0e6).setMixtureDensity(50.0);
    assertNull(selector.select());
  }

  @Test
  void theBasisMustBeSetBeforeSelecting() {
    assertThrows(IllegalStateException.class, () -> new FlowlineSizeSelector().select());
  }

  @Test
  void theBasisCanBeTakenFromAFlashedFluid() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 35.0, 70.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.03);
    fluid.setMixingRule("classic");

    FlowlineSizeSelector selector = new FlowlineSizeSelector().setBasisFromFluid(fluid, 16.0);
    assertNotNull(selector.select());
    assertTrue(selector.getErosionalVelocity() > 0.0);
  }

  @Test
  void jsonCarriesTheBasisAndTheStandard() {
    FlowlineSizeSelector selector = new FlowlineSizeSelector().setMassFlowRate(16.13).setMixtureDensity(108.3);
    selector.select();
    String json = selector.toJson();
    assertTrue(json.contains("API RP 14E"));
    assertTrue(json.contains("erosionalVelocity_m_per_s"));
    assertTrue(json.contains("candidates"));
  }
}
