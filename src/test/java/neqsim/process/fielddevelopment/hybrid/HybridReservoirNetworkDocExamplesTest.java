package neqsim.process.fielddevelopment.hybrid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.fielddevelopment.reservoir.VlpProvider;

/**
 * Runs the code shown in {@code docs/fielddevelopment/HYBRID_RESERVOIR_NETWORK_MODEL.md} so the documented example
 * stays executable.
 *
 * @author ESOL
 * @version 1.0
 */
class HybridReservoirNetworkDocExamplesTest {
  /** The quick-start example: two wells on a manifold, a riser, a choke change and a facility capacity. */
  @Test
  void quickStartExampleRuns() {
    // Reservoir-state proxies post-processed from a reservoir simulator run (here: typed in).
    double[] cumulativeOil = {0.0, 1.0e6, 2.0e6, 3.0e6}; // Sm3
    ReservoirStateProxy stateA = new ReservoirStateProxy("A", cumulativeOil, new double[] {250.0, 225.0, 205.0, 190.0})
        .setWaterCut(new double[] {0.0, 0.10, 0.25, 0.40}).setGasOilRatio(new double[] {150.0, 150.0, 180.0, 240.0})
        .setProductivityIndex(new double[] {20.0, 18.0, 16.0, 14.0});
    ReservoirStateProxy stateB = new ReservoirStateProxy("B", cumulativeOil, new double[] {250.0, 230.0, 212.0, 198.0})
        .setWaterCut(new double[] {0.0, 0.05, 0.15, 0.30}).setGasOilRatio(new double[] {150.0, 150.0, 160.0, 190.0})
        .setProductivityIndex(new double[] {15.0, 14.0, 13.0, 12.0});

    // Lift models on the oil rate, water cut and gas-oil ratio basis. In a study these are VFPPROD tables
    // (VfpTableLookup.fromFile(path).onOilBasis()) or live models (TubingVlpProvider, FlowlineVlpProvider).
    VlpProvider tubing = (rate, wellhead, waterCut, gor, alq) -> wellhead + 90.0 + 40.0 * waterCut
        + 3.0e-5 * rate * rate;
    VlpProvider flowline = (rate, outlet, waterCut, gor, alq) -> outlet + 3.0e-6 * Math.pow(rate / (1.0 - waterCut), 2);
    VlpProvider riser = (rate, outlet, waterCut, gor, alq) -> outlet + 1.5e-6 * Math.pow(rate / (1.0 - waterCut), 2);

    // Network: wells -> manifold -> riser base -> separator at 30 bara.
    GatheringNetwork network = new GatheringNetwork("SEPARATOR", 30.0);
    network.addBranch("RISER", "RISER_BASE", "SEPARATOR", riser);
    network.addBranch("FLOWLINE", "MANIFOLD", "RISER_BASE", flowline);
    HybridWell wellA = new HybridWell("A", stateA, tubing);
    HybridWell wellB = new HybridWell("B", stateB, tubing);
    network.connectWell(wellA, "MANIFOLD");
    network.connectWell(wellB, "MANIFOLD");

    HybridProductionModel model = new HybridProductionModel("example", network);
    HybridStepResult start = model.solve();
    assertTrue(start.isConverged());

    // What happens to well B when well A is choked to half its rate?
    double rateB = start.getWell("B").getOilRate();
    wellA.setOilRateTarget(0.5 * start.getWell("A").getOilRate());
    HybridStepResult choked = model.solve();
    assertTrue(choked.getWell("B").getOilRate() > rateB);
    assertEquals("RATE_TARGET", choked.getWell("A").getControlMode());

    // Two years in monthly steps with a gas-handling capacity.
    model.setFacilityConstraints(
        new FacilityConstraints().setCapacity(FacilityConstraints.Quantity.GAS, 0.9 * choked.getFieldGasRate()));
    List<HybridStepResult> profile = model.run(730.0, 30.4);
    HybridStepResult end = profile.get(profile.size() - 1);
    assertTrue(end.isConverged());
    assertTrue(end.getFieldOilRate() < choked.getFieldOilRate());
    assertTrue(end.getWell("A").getCumulativeOil() > 0.0);
    assertTrue(profile.get(1).getFieldGasRate() <= 0.9 * choked.getFieldGasRate() * 1.002);
  }
}
