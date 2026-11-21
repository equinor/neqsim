package neqsim.process.equipment.tank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression tests for {@link VesselDepressurization} with gas, oil and water and calculated heat transfer.
 *
 * <p>
 * Covers three defects found in a subsea tie-back separator blowdown study: {@code runSimulation} without a preceding
 * {@code run()} collapsed the pressure in the first step; the liquid film coefficient threw "Heat capacity, viscosity,
 * and thermal conductivity must be positive" because transport properties were never refreshed during the transient;
 * and an initial liquid level scaled each liquid phase to the full liquid volume, doubling the liquid inventory of a
 * gas/oil/water feed.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class VesselDepressurizationMultiLiquidTest {
  /**
   * Creates a gas/oil/water feed stream.
   *
   * @param water moles of water to add (0 for a dry fluid)
   * @return the feed stream, already run
   */
  private Stream feed(double water) {
    SystemInterface fluid = new SystemSrkEos(330.0, 40.0);
    fluid.addComponent("methane", 70.0);
    fluid.addComponent("propane", 5.0);
    fluid.addComponent("n-heptane", 10.0);
    fluid.addComponent("n-decane", 5.0);
    if (water > 0.0) {
      fluid.addComponent("water", water);
    }
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    Stream stream = new Stream("feed", fluid);
    stream.setFlowRate(1000.0, "kg/hr");
    stream.run();
    return stream;
  }

  /**
   * Creates a horizontal separator-sized vessel with transient wall heat transfer.
   *
   * @param feed feed stream
   * @param level initial liquid level fraction (0 for none)
   * @return the configured vessel
   */
  private VesselDepressurization vessel(Stream feed, double level) {
    VesselDepressurization v = new VesselDepressurization("V-2001", feed);
    v.setVesselGeometry(12.0, 3.2, VesselDepressurization.VesselOrientation.HORIZONTAL);
    if (level > 0.0) {
      v.setInitialLiquidLevel(level);
      v.setTwoPhaseHeatTransfer(true);
    }
    v.setVesselProperties(0.045, 7850.0, 490.0, 45.0);
    v.setCalculationType(VesselDepressurization.CalculationType.ENERGY_BALANCE);
    v.setHeatTransferType(VesselDepressurization.HeatTransferType.TRANSIENT_WALL);
    v.setBackPressure(1.5);
    v.setAmbientTemperature(278.15);
    v.setOrificeDiameter(0.045);
    v.setDischargeCoefficient(0.84);
    return v;
  }

  /** runSimulation without run() first initialises the inventory instead of collapsing the pressure. */
  @Test
  void runSimulationInitialisesInventory() {
    VesselDepressurization v = vessel(feed(0.0), 0.0);
    VesselDepressurization.SimulationResult r = v.runSimulation(20.0, 5.0);
    List<Double> p = r.getPressure();
    assertEquals(40.0, p.get(0), 0.5);
    assertTrue(p.get(1) > 35.0, "first step pressure " + p.get(1));
    assertTrue(p.get(p.size() - 1) < p.get(0), "pressure must fall during blowdown");
  }

  /** Gas/oil/water with a liquid level and calculated heat transfer runs and starts at the feed pressure. */
  @Test
  void threePhaseBlowdownWithLiquidLevel() {
    Stream feed = feed(25.0);
    assertEquals(3, feed.getFluid().getNumberOfPhases());
    VesselDepressurization v = vessel(feed, 0.45);
    VesselDepressurization.SimulationResult r = v.runSimulation(10.0, 5.0);
    List<Double> p = r.getPressure();
    assertEquals(40.0, p.get(0), 0.5);
    for (int i = 1; i < p.size(); i++) {
      assertTrue(p.get(i) < p.get(i - 1) + 1e-6, "pressure rose at step " + i + ": " + p);
      assertTrue(p.get(i) > 30.0, "pressure jumped at step " + i + ": " + p);
    }
    double level = v.getLiquidLevel();
    assertTrue(level > 0.3 && level < 0.6, "liquid level fraction " + level);
  }
}
