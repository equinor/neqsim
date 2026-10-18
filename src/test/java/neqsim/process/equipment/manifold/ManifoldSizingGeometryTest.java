package neqsim.process.equipment.manifold;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Checks that auto-sizing uses the bore, retaining each existing screening wall thickness.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class ManifoldSizingGeometryTest extends neqsim.NeqSimTest {
  /** The selected 14-inch header must retain its OD after adding the wall to its bore. */
  @Test
  void autoSizedHeaderAndBranchUseInternalFlowDiameters() {
    Manifold manifold = sizeForHeaderBore(0.32);
    assertEquals(0.3556 - 2.0 * 0.01118, manifold.getHeaderInnerDiameter(), 1e-12);
    assertEquals(0.3556, manifold.getHeaderOuterDiameter(), 1e-12);
    assertEquals(0.01118, manifold.getHeaderWallThickness(), 1e-12);
    assertEquals(0.3239 - 2.0 * 0.01067, manifold.getBranchInnerDiameter(), 1e-12);
    assertEquals(0.3239, manifold.getBranchOuterDiameter(), 1e-12);
    double expectedVelocity = manifold.getMixedStream().getFlowRate("m3/sec")
        / (Math.PI * Math.pow(manifold.getHeaderInnerDiameter(), 2.0) / 4.0);
    assertEquals(expectedVelocity, manifold.getHeaderVelocity(), 1e-10);
  }

  /** A required bore above the actual 14-inch bore must advance to the 16-inch entry. */
  @Test
  void selectionAdvancesWhenOuterDiameterWouldHideInsufficientBore() {
    Manifold manifold = sizeForHeaderBore(0.34);
    assertEquals(0.4064 - 2.0 * 0.01270, manifold.getHeaderInnerDiameter(), 1e-12);
    assertEquals(0.4064, manifold.getHeaderOuterDiameter(), 1e-12);
    assertTrue(manifold.getHeaderVelocity() <= 15.0);
  }

  /**
   * Build a pure-gas case whose required header bore is known analytically.
   *
   * @param requiredBore required inner diameter in meters
   * @return sized manifold with one inlet and one outlet
   */
  private Manifold sizeForHeaderBore(double requiredBore) {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule(2);
    Stream stream = new Stream("inlet", fluid);
    stream.setFlowRate(15.0 * Math.PI * requiredBore * requiredBore / 4.0, "m3/sec");
    stream.run();
    Manifold manifold = new Manifold("manifold");
    manifold.addStream(stream);
    manifold.setSplitFactors(new double[] {1.0});
    manifold.autoSize();
    return manifold;
  }
}
