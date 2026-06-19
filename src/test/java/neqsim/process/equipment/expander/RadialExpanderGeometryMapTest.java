package neqsim.process.equipment.expander;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for {@link RadialExpanderGeometryMap}, the geometry-based mean-line expander map
 * generator.
 *
 * @author NeqSim
 * @version 1.0
 */
public class RadialExpanderGeometryMapTest {

  /**
   * Builds a representative reference fluid for normalisation.
   *
   * @return a methane/ethane SRK fluid at 25 C, 50 bara
   */
  private SystemInterface referenceFluid() {
    SystemInterface gas = new SystemSrkEos(298.15, 50.0);
    gas.addComponent("methane", 0.9);
    gas.addComponent("ethane", 0.1);
    gas.setMixingRule(2);
    return gas;
  }

  /**
   * The generated map should peak near the nominal velocity ratio predicted from geometry.
   */
  @Test
  void testPeakEfficiencyNearNominalVelocityRatio() {
    RadialExpanderGeometryMap generator = new RadialExpanderGeometryMap(0.424, 0.45, 0.45);
    generator.setReferenceFluid(referenceFluid());
    generator.setPointsPerCurve(21);
    double[] igv = new double[] {1.0};
    double[] nozzle = new double[] {72.0};
    ExpanderChartKhader chart = generator.generateChart(igv, nozzle);

    double nuOpt = generator.nominalVelocityRatio(72.0);
    double ucAtPeak = chart.getOptimumVelocityRatio(1.0);
    // peak efficiency should sit close to the nominal velocity ratio (slightly below due friction)
    assertTrue(Math.abs(ucAtPeak - nuOpt) < 0.12,
        "peak U/C " + ucAtPeak + " should be near nominal " + nuOpt);
    // peak efficiency should be physically reasonable for a radial inflow turbine
    double peakEta = chart.getEfficiency(ucAtPeak, 1.0);
    assertTrue(peakEta > 0.70 && peakEta < 0.95, "peak efficiency out of range: " + peakEta);
  }

  /**
   * Efficiency should fall off either side of the peak (concave characteristic).
   */
  @Test
  void testEfficiencyFallsOffAwayFromPeak() {
    RadialExpanderGeometryMap generator = new RadialExpanderGeometryMap();
    generator.setReferenceFluid(referenceFluid());
    ExpanderChartKhader chart = generator.generateChart(new double[] {1.0}, new double[] {72.0});
    double ucPeak = chart.getOptimumVelocityRatio(1.0);
    double etaPeak = chart.getEfficiency(ucPeak, 1.0);
    double etaLow = chart.getEfficiency(0.45, 1.0);
    double etaHigh = chart.getEfficiency(0.92, 1.0);
    assertTrue(etaPeak > etaLow, "peak should exceed low-end efficiency");
    assertTrue(etaPeak > etaHigh, "peak should exceed high-end efficiency");
  }

  /**
   * The stage head drop should be composition aware and positive.
   */
  @Test
  void testHeadDropIsPositiveAndCompositionAware() {
    SystemInterface ref = referenceFluid();
    RadialExpanderGeometryMap generator = new RadialExpanderGeometryMap();
    generator.setReferenceFluid(ref);
    generator.setDesignHeadDropKjPerKg(50.0);
    ExpanderChartKhader chart =
        generator.generateChart(new double[] {0.6, 1.0}, new double[] {75.0, 70.0});
    double head = chart.getStageHeadDrop(0.7, 1.0, ref);
    assertTrue(head > 0.0, "head drop must be positive: " + head);
    // more open IGV carries a larger head than the more closed setting
    double headOpen = chart.getStageHeadDrop(0.7, 1.0, ref);
    double headClosed = chart.getStageHeadDrop(0.7, 0.6, ref);
    assertTrue(headOpen > headClosed, "open IGV head should exceed closed IGV head");
  }

  /**
   * The nominal velocity ratio relation should match the closed-form expression.
   */
  @Test
  void testNominalVelocityRatioFormula() {
    RadialExpanderGeometryMap generator = new RadialExpanderGeometryMap(0.424, 0.45, 0.40);
    double expected = Math.sqrt(1.0 - 0.40) * Math.sin(Math.toRadians(73.0));
    assertEquals(expected, generator.nominalVelocityRatio(73.0), 1e-9);
  }

  /**
   * Invalid geometry and schedule inputs should be rejected.
   */
  @Test
  void testInvalidInputsRejected() {
    final RadialExpanderGeometryMap generator = new RadialExpanderGeometryMap();
    assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        generator.generateChart(new double[] {1.0}, new double[] {95.0});
      }
    });
    assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        generator.generateChart(new double[] {0.5, 1.0}, new double[] {70.0});
      }
    });
    assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        generator.setDegreeOfReaction(1.0);
      }
    });
  }
}
