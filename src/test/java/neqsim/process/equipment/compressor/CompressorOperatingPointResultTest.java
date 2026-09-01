package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.expander.Expander;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests the immutable, capacity-aware compressor operating-point result.
 *
 * @author NeqSim
 * @version 1.0
 */
public class CompressorOperatingPointResultTest {
  private Compressor compressor;

  /** Creates and solves a chartless compressor case. */
  @BeforeEach
  public void setUp() {
    SystemInterface gas = new SystemSrkEos(298.15, 50.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.07);
    gas.addComponent("propane", 0.03);
    gas.setMixingRule("classic");
    gas.setMultiPhaseCheck(false);

    Stream inlet = new Stream("inlet", gas);
    inlet.setFlowRate(10000.0, "kg/hr");
    inlet.setTemperature(25.0, "C");
    inlet.setPressure(50.0, "bara");
    inlet.run();

    compressor = new Compressor("export compressor", inlet);
    compressor.setOutletPressure(100.0, "bara");
    compressor.setUsePolytropicCalc(true);
    compressor.setPolytropicEfficiency(0.75);
    compressor.run();
  }

  /** Verifies physical values, pressure status, and chartless feasibility. */
  @Test
  public void testChartlessResultIsPhysicalAndFeasible() {
    CompressorOperatingPointResult result = compressor.getOperatingPointResult();

    assertEquals("1.0", result.getSchemaVersion());
    assertEquals("export compressor", result.getCompressorName());
    assertTrue(result.getFlowM3PerHour() > 0.0);
    assertTrue(result.getPolytropicHeadKJPerKg() > 0.0);
    assertTrue(result.getPowerKW() > 0.0);
    assertEquals(50.0, result.getInletPressureBara(), 1.0e-8);
    assertEquals(100.0, result.getRequestedDischargePressureBara(), 1.0e-8);
    assertEquals(100.0, result.getActualDischargePressureBara(), 1.0e-6);
    assertEquals(CompressorOperatingPointResult.PressureTargetStatus.ON_TARGET, result.getPressureTargetStatus());
    assertFalse(result.isChartActive());
    assertTrue(result.isWithinChart());
    assertEquals(0.0, result.getRequiredRecycleFraction(), 0.0);
    assertEquals(0.0, result.getRecyclePowerLossKW(), 0.0);
    assertEquals(CompressorOperatingPointResult.OperatingStatus.VALID, result.getOperatingStatus());
    assertTrue(result.isFeasible());
    assertNotNull(result.getConstraints());
  }

  /** Verifies signed pressure-target classification with a caller-defined tolerance. */
  @Test
  public void testPressureTargetStatusUsesActualOutletPressure() {
    compressor.getOutletStream().setPressure(95.0, "bara");

    CompressorOperatingPointResult result = compressor.getOperatingPointResult(0.02);

    assertEquals(CompressorOperatingPointResult.PressureTargetStatus.BELOW_TARGET, result.getPressureTargetStatus());
    assertEquals(-0.05, result.getDischargePressureErrorFraction(), 1.0e-12);
    assertEquals(CompressorOperatingPointResult.OperatingStatus.PRESSURE_TARGET_NOT_MET, result.getOperatingStatus());
    assertFalse(result.isFeasible());
  }

  /** Verifies that the result reuses the universal capacity and bottleneck API. */
  @Test
  public void testCapacityBottleneckIsPropagated() {
    CapacityConstraint customLimit = new CapacityConstraint("vendorPowerLimit", "kW",
        CapacityConstraint.ConstraintType.HARD).setDesignValue(1000.0).setMaxValue(1000.0).setCurrentValue(1200.0)
        .setSeverity(CapacityConstraint.ConstraintSeverity.HARD);
    compressor.addCapacityConstraint(customLimit);

    CompressorOperatingPointResult result = compressor.getOperatingPointResult();

    assertTrue(result.isCapacityExceeded());
    assertTrue(result.isHardLimitExceeded());
    assertEquals("vendorPowerLimit", result.getLimitingConstraint());
    assertEquals(1.2, result.getMaximumCapacityUtilization(), 1.0e-12);
    assertEquals(CompressorOperatingPointResult.OperatingStatus.CAPACITY_LIMIT, result.getOperatingStatus());
    CompressorOperatingPointResult.ConstraintSnapshot snapshot = result.getConstraints().stream()
        .filter(value -> value.getName().equals("vendorPowerLimit")).findFirst().orElse(null);
    assertNotNull(snapshot);
    assertTrue(snapshot.isEnabled());
    assertTrue(snapshot.isViolated());
    assertEquals(1.2, snapshot.getUtilization(), 1.0e-12);
    assertEquals(-0.2, snapshot.getMargin(), 1.0e-12);
  }

  /** Verifies map margins, recycle screening, JSON, and corrected minimum constraints. */
  @Test
  public void testChartResultIncludesRecycleAndMinimumMargins() {
    CompressorChartGenerator generator = new CompressorChartGenerator(compressor);
    generator.setChartType("interpolate and extrapolate");
    CompressorChartInterface chart = generator.generateCompressorChart("normal curves");
    compressor.setCompressorChart(chart);
    compressor.reinitializeCapacityConstraints();
    compressor.run();

    CompressorOperatingPointResult result = compressor.getOperatingPointResult();

    assertTrue(result.isChartActive());
    assertFalse(Double.isNaN(result.getDistanceToSurge()));
    assertFalse(Double.isNaN(result.getDistanceToStonewall()));
    assertTrue(result.getRequiredRecycleFraction() >= 0.0);
    assertTrue(result.getRequiredRecycleFraction() <= 1.0);
    assertEquals(result.getPowerKW() * result.getRequiredRecycleFraction(), result.getRecyclePowerLossKW(), 1.0e-8);
    assertEquals(result.getRecyclePowerLossKW(), result.getRecycleCoolerDutyKW(), 1.0e-8);

    CapacityConstraint surge = compressor.getCapacityConstraints().get("surgeMargin");
    CapacityConstraint stonewall = compressor.getCapacityConstraints().get("stonewallMargin");
    assertNotNull(surge);
    assertNotNull(stonewall);
    assertTrue(surge.isMinimumConstraint());
    assertTrue(stonewall.isMinimumConstraint());
    assertEquals(compressor.getDistanceToSurge() * 100.0, surge.getCurrentValue(), 1.0e-8);
    assertEquals(compressor.getDistanceToStoneWall() * 100.0, stonewall.getCurrentValue(), 1.0e-8);

    String json = result.toJson();
    assertTrue(json.contains("\"operatingStatus\""));
    assertTrue(json.contains("\"pressureTargetStatus\""));
    assertTrue(json.contains("\"constraints\""));
  }

  /** Verifies that undefined map margins fail closed as violated minimum constraints. */
  @Test
  public void testUndefinedMapMarginsViolateMinimumConstraints() {
    Compressor undefinedMargins = new Compressor("undefined margins", compressor.getInletStream()) {
      private static final long serialVersionUID = 1000L;

      @Override
      public double getDistanceToSurge() {
        return Double.NaN;
      }

      @Override
      public double getDistanceToStoneWall() {
        return Double.POSITIVE_INFINITY;
      }
    };
    undefinedMargins.setOutletPressure(100.0, "bara");
    undefinedMargins.setUsePolytropicCalc(true);
    undefinedMargins.setPolytropicEfficiency(0.75);
    undefinedMargins.run();

    CompressorChartGenerator generator = new CompressorChartGenerator(undefinedMargins);
    generator.setChartType("interpolate and extrapolate");
    undefinedMargins.setCompressorChart(generator.generateCompressorChart("normal curves"));
    undefinedMargins.reinitializeCapacityConstraints();

    CapacityConstraint surge = undefinedMargins.getCapacityConstraints().get("surgeMargin");
    CapacityConstraint stonewall = undefinedMargins.getCapacityConstraints().get("stonewallMargin");
    assertNotNull(surge);
    assertNotNull(stonewall);
    assertEquals(0.0, surge.getCurrentValue(), 0.0);
    assertEquals(0.0, stonewall.getCurrentValue(), 0.0);
    assertTrue(surge.isViolated());
    assertTrue(stonewall.isViolated());

    CompressorOperatingPointResult result = undefinedMargins.getOperatingPointResult();
    assertEquals(CompressorOperatingPointResult.OperatingStatus.CAPACITY_LIMIT, result.getOperatingStatus());
    assertFalse(result.isFeasible());
  }

  /** Verifies that signed recovered power does not invalidate an inherited expander result. */
  @Test
  public void testExpanderRecoveredPowerIsValid() {
    Expander expander = new Expander("turboexpander", compressor.getInletStream());
    expander.setOutletPressure(20.0, "bara");
    expander.setIsentropicEfficiency(0.80);
    expander.run();

    CompressorOperatingPointResult result = expander.getOperatingPointResult();

    assertTrue(result.getPowerKW() < 0.0);
    assertEquals(CompressorOperatingPointResult.OperatingStatus.VALID, result.getOperatingStatus());
    assertTrue(result.isFeasible());
  }

  /** Verifies that the detached result survives Java serialization. */
  @Test
  public void testResultIsSerializable() throws Exception {
    CompressorOperatingPointResult original = compressor.getOperatingPointResult();
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(buffer)) {
      output.writeObject(original);
    }

    CompressorOperatingPointResult restored;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(buffer.toByteArray()))) {
      restored = (CompressorOperatingPointResult) input.readObject();
    }

    assertNotNull(restored);
    assertEquals(original.getCompressorName(), restored.getCompressorName());
    assertEquals(original.getPowerKW(), restored.getPowerKW(), 0.0);
    assertSame(original.getOperatingStatus(), restored.getOperatingStatus());
  }

  /** Verifies every API call and contract shown in the capacity-aware documentation snippet. */
  @Test
  public void testCapacityAwareDocumentationSnippet() {
    CompressorOperatingPointResult result = compressor.getOperatingPointResult(0.02);
    boolean feasible = result.isFeasible();
    String limitingConstraint = result.getLimitingConstraint();
    double recycleLossKW = result.getRecyclePowerLossKW();
    List<CompressorOperatingPointResult.ConstraintSnapshot> constraints = result.getConstraints();
    String resultJson = result.toJson();

    assertTrue(feasible);
    assertNotNull(limitingConstraint);
    assertEquals(0.0, recycleLossKW, 0.0);
    assertEquals(0.02, result.getPressureToleranceFraction(), 0.0);
    assertEquals("1.0", result.getSchemaVersion());
    assertTrue(resultJson.contains("\"schemaVersion\":\"1.0\""));
    assertThrows(UnsupportedOperationException.class, constraints::clear);
    assertThrows(IllegalArgumentException.class, () -> compressor.getOperatingPointResult(-0.01));
  }
}
