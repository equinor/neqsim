package neqsim.process.equipment.separator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintSource;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Qualification of raw separator constraint values and their engineering basis.
 *
 * @author NeqSim
 * @version 1.0
 */
class SeparatorConstraintBasisTest {
  /**
   * Builds a synthetic gas/oil separator.
   *
   * @return solved separator
   */
  private Separator separator() {
    SystemSrkEos fluid = new SystemSrkEos(303.15, 30.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("n-heptane", 0.2);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(10000.0, "kg/hr");
    feed.run();
    Separator separator = new Separator("separator", feed);
    separator.run();
    return separator;
  }

  /** Verifies built-in values and units are visible without enabling the constraints. */
  @Test
  void builtInLimitsAreDefault() {
    Separator separator = separator();
    for (CapacityConstraint constraint : separator.getCapacityConstraints().values()) {
      assertEquals("default", constraint.getBasis());
      assertEquals("default", constraint.getDataSource());
      assertFalse(constraint.isEnabled());
      assertTrue(Double.isFinite(constraint.getRawValue()));
    }
    CapacityConstraint gas = separator.getCapacityConstraints().get("gasLoadFactor");
    assertEquals(0.11, gas.getDesignLimit(), 1e-10);
    assertEquals("m/s", gas.getUnit());
    assertEquals(gas.getRawValue() / gas.getDesignLimit(), gas.getUtilization(), 1e-10);
    assertEquals(0.15, separator.getCapacityConstraints().get("kValue").getDesignLimit(), 1e-10);
    assertEquals(16000.0, separator.getCapacityConstraints().get("inletMomentum").getDesignLimit(), 1e-10);
  }

  /**
   * Verifies custom minimums stay inverted and explicit datasheet provenance survives serialization.
   *
   * @throws Exception if serialization fails
   */
  @Test
  void explicitLimitsRetainBasisAndMinimumDirection() throws Exception {
    Separator separator = separator();
    separator.setKValueLimit(0.08);
    CapacityConstraint k = separator.getCapacityConstraints().get("kValue");
    assertEquals("custom", k.getBasis());
    k.setSource(ConstraintSource.VENDOR_DATASHEET, "synthetic vessel datasheet");
    k.setEnabled(true);
    assertEquals("datasheet", k.getBasis());
    assertEquals(0.08, k.getDesignLimit(), 1e-10);
    separator.setMinOilRetentionTime(4.0);
    CapacityConstraint retention = separator.getCapacityConstraints().get("oilRetentionTime");
    assertEquals(4.0, retention.getDesignLimit(), 1e-10);
    assertEquals(2.0, retention.getUtilization(2.0), 1e-10);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(separator);
    }
    Separator restored;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      restored = (Separator) input.readObject();
    }
    CapacityConstraint restoredK = restored.getCapacityConstraints().get("kValue");
    assertEquals("datasheet", restoredK.getBasis());
    assertEquals("synthetic vessel datasheet", restoredK.getSourceReference());
    assertEquals(0.08, restoredK.getDesignLimit(), 1e-10);
    assertTrue(restoredK.isEnabled());
    assertEquals(k.getRawValue(), restoredK.getRawValue(), 1e-10);
    assertEquals(4.0, restored.getCapacityConstraints().get("oilRetentionTime").getDesignLimit(), 1e-10);
  }
}
