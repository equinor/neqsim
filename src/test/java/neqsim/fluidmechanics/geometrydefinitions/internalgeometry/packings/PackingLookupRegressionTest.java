package neqsim.fluidmechanics.geometrydefinitions.internalgeometry.packings;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient.interphasetwophase.interphasereactorflow.InterphasePackedBed;
import neqsim.fluidmechanics.flownode.twophasenode.twophasereactorflownode.TwoPhasePackedBedFlowNode;
import neqsim.fluidmechanics.geometrydefinitions.reactor.ReactorData;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression tests for real packing table keys and finite packed-bed transport.
 *
 * @author NeqSim
 * @version 1.0
 */
public class PackingLookupRegressionTest {
  /** Load the requested metal and plastic 50 mm rows rather than constructor defaults. */
  @Test
  public void testMaterialAndSizeResolveRealRows() {
    PallRingPacking metal = new PallRingPacking("metal", 50);
    PallRingPacking plastic = new PallRingPacking("plastic", 50);
    Assertions.assertEquals(0.05, metal.getSize(), 1.0e-12);
    Assertions.assertEquals(112.6, metal.getSurfaceAreaPrVolume(), 1.0e-12);
    Assertions.assertEquals(0.951, metal.getVoidFractionPacking(), 1.0e-12);
    Assertions.assertEquals(0.05, plastic.getSize(), 1.0e-12);
    Assertions.assertEquals(111.1, plastic.getSurfaceAreaPrVolume(), 1.0e-12);
    Assertions.assertEquals(0.919, plastic.getVoidFractionPacking(), 1.0e-12);
    PallRingPacking smaller = new PallRingPacking("metal", 25);
    Assertions.assertEquals(0.025, smaller.getSize(), 1.0e-12);
    Assertions.assertEquals(223.5, smaller.getSurfaceAreaPrVolume(), 1.0e-12);
  }

  /** Defaults for legacy class names must select real random packing geometry. */
  @Test
  public void testLegacyDefaultsAvoidPlaceholderGeometry() {
    Assertions.assertEquals(0.05, new PallRingPacking().getSize(), 1.0e-12);
    Assertions.assertEquals(0.025, new RachigRingPacking().getSize(), 1.0e-12);
    Assertions.assertEquals(0.025, new BerlSaddlePacking().getSize(), 1.0e-12);
    Assertions.assertEquals(185.4, new RachigRingPacking("ceramic", 25).getSurfaceAreaPrVolume(), 1.0e-12);
  }

  /** A missing material, size or family must fail instead of appearing to load data. */
  @Test
  public void testUnknownPackingFailsAtSelection() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> new PallRingPacking("metal", 51));
    Assertions.assertThrows(IllegalArgumentException.class, () -> new PallRingPacking("wood", 50));
    Assertions.assertThrows(IllegalArgumentException.class, () -> new PallRingPacking("metal", 250));
    ReactorData geometry = new ReactorData(0.25, 0.025);
    Assertions.assertThrows(IllegalArgumentException.class, () -> geometry.setPackingType("pallrign", "metal", 50));
  }

  /** Exercise both interphase mass-transfer correlations on the bundled example's packing path. */
  @Test
  public void testPackedBedTransferCoefficientsAreFiniteAndPositive() {
    SystemSrkEos fluid = new SystemSrkEos(313.3, 20.0);
    fluid.addComponent("methane", 0.1, 0);
    fluid.addComponent("nC10", 0.01, 1);
    fluid.setMixingRule(2);
    fluid.init(3);
    fluid.initPhysicalProperties();
    ReactorData geometry = new ReactorData(0.25, 0.025);
    geometry.setPackingType("pallring", "metal", 50);
    TwoPhasePackedBedFlowNode node = new TwoPhasePackedBedFlowNode(fluid, geometry);
    node.initFlowCalc();
    InterphasePackedBed transport = new InterphasePackedBed(node);
    double gasCoefficient = transport.calcInterphaseMassTransferCoefficient(0, 0.7, node);
    double liquidCoefficient = transport.calcInterphaseMassTransferCoefficient(1, 1000.0, node);
    Assertions.assertTrue(Double.isFinite(gasCoefficient) && gasCoefficient > 0.0,
        "Gas-side transfer coefficient must be finite and positive: " + gasCoefficient);
    Assertions.assertTrue(Double.isFinite(liquidCoefficient) && liquidCoefficient > 0.0,
        "Liquid-side transfer coefficient must be finite and positive: " + liquidCoefficient);
  }
}
