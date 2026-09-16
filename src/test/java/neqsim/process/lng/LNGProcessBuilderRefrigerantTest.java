package neqsim.process.lng;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the refrigerant configuration added to {@link LNGProcessBuilder}.
 *
 * <p>
 * Specific liquefaction power is dominated by how well the refrigerant boiling curve matches the natural-gas cooling
 * curve. Before these settings existed the mixed-refrigerant inventory was hard-wired and the SMR template landed far
 * outside the published band carried by {@link LNGProcessBenchmark}, which made it unusable for a plant power estimate.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class LNGProcessBuilderRefrigerantTest {
  /**
   * Builds a representative pretreated LNG feed gas.
   *
   * @return feed fluid
   */
  private SystemInterface feed() {
    SystemInterface fluid = new SystemSrkEos(298.15, 60.0);
    fluid.addComponent("nitrogen", 0.005);
    fluid.addComponent("methane", 0.930);
    fluid.addComponent("ethane", 0.045);
    fluid.addComponent("propane", 0.015);
    fluid.addComponent("n-butane", 0.005);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /**
   * Builds an SMR model with a calibrated refrigerant inventory.
   *
   * @return built model
   */
  private LNGProcessModel calibratedSmr() {
    return new LNGProcessBuilder().setName("SMR calibrated").setCycle(LNGProcessCycle.SMR).setFeedFluid(feed())
        .setFeedFlowRate(100000.0).setFeedTemperature(25.0).setFeedPressure(60.0)
        .setTargetLiquefactionTemperature(-158.0)
        .setRefrigerantComposition(new String[] { "nitrogen", "methane", "ethane", "propane", "i-butane" },
            new double[] { 0.08, 0.34, 0.34, 0.12, 0.12 })
        .setRefrigerantCirculationRatio(2.5).setRefrigerantSuctionPressure(4.0).setRefrigerantDischargePressure(45.0)
        .build();
  }

  /** A calibrated refrigerant inventory must place the SMR inside its published band. */
  @Test
  void calibratedRefrigerantLandsInsideThePublishedBand() {
    LNGProcessModel.Result result = calibratedSmr().run();
    double specificEnergy = result.getSpecificEnergyKWhPerKgLNG();
    LNGProcessBenchmark.Benchmark band = LNGProcessBenchmark.get(LNGProcessCycle.SMR);

    assertTrue(specificEnergy > 0.0, "specific energy must be positive");
    assertTrue(specificEnergy >= band.getMinimumSpecificEnergy() && specificEnergy <= band.getMaximumSpecificEnergy(),
        "calibrated SMR specific energy " + specificEnergy + " kWh/kg is outside the published band ["
            + band.getMinimumSpecificEnergy() + ", " + band.getMaximumSpecificEnergy() + "]");
  }

  /** Circulation ratio must change the compressor duty, proving the setting is wired through. */
  @Test
  void circulationRatioChangesCompressorPower() {
    LNGProcessModel.Result low = new LNGProcessBuilder().setName("SMR low").setCycle(LNGProcessCycle.SMR)
        .setFeedFluid(feed()).setFeedFlowRate(100000.0).setFeedTemperature(25.0).setFeedPressure(60.0)
        .setTargetLiquefactionTemperature(-158.0)
        .setRefrigerantComposition(new String[] { "nitrogen", "methane", "ethane", "propane", "i-butane" },
            new double[] { 0.08, 0.34, 0.34, 0.12, 0.12 })
        .setRefrigerantCirculationRatio(2.0).setRefrigerantSuctionPressure(4.0).setRefrigerantDischargePressure(45.0)
        .build().run();

    LNGProcessModel.Result high = new LNGProcessBuilder().setName("SMR high").setCycle(LNGProcessCycle.SMR)
        .setFeedFluid(feed()).setFeedFlowRate(100000.0).setFeedTemperature(25.0).setFeedPressure(60.0)
        .setTargetLiquefactionTemperature(-158.0)
        .setRefrigerantComposition(new String[] { "nitrogen", "methane", "ethane", "propane", "i-butane" },
            new double[] { 0.08, 0.34, 0.34, 0.12, 0.12 })
        .setRefrigerantCirculationRatio(3.0).setRefrigerantSuctionPressure(4.0).setRefrigerantDischargePressure(45.0)
        .build().run();

    assertTrue(high.getCompressorPowerKW() > low.getCompressorPowerKW(),
        "more refrigerant circulation must consume more compressor power");
  }

  /** Omitting the settings must keep the previous behaviour. */
  @Test
  void defaultsRemainAvailableWhenNothingIsConfigured() {
    LNGProcessModel.Result result = new LNGProcessBuilder().setName("SMR default").setCycle(LNGProcessCycle.SMR)
        .setFeedFluid(feed()).setFeedFlowRate(100000.0).setFeedTemperature(25.0).setFeedPressure(60.0)
        .setTargetLiquefactionTemperature(-158.0).build().run();

    assertNotNull(result);
    assertTrue(result.getCapacityMTPA() > 0.0);
    assertTrue(result.getCompressorPowerKW() > 0.0);
  }

  /** Invalid refrigerant input must be rejected at configuration time. */
  @Test
  void invalidRefrigerantInputIsRejected() {
    LNGProcessBuilder builder = new LNGProcessBuilder();
    assertThrows(IllegalArgumentException.class,
        () -> builder.setRefrigerantComposition(new String[] { "methane" }, new double[] { 1.0, 2.0 }));
    assertThrows(IllegalArgumentException.class,
        () -> builder.setRefrigerantComposition(new String[] { "methane", "ethane" }, new double[] { 0.0, 0.0 }));
    assertThrows(IllegalArgumentException.class,
        () -> builder.setRefrigerantComposition(new String[] { "methane", "ethane" }, new double[] { 1.0, -0.5 }));
    assertThrows(IllegalArgumentException.class, () -> builder.setRefrigerantCirculationRatio(0.0));
    assertThrows(IllegalArgumentException.class, () -> builder.setRefrigerantSuctionPressure(-1.0));
    assertThrows(IllegalArgumentException.class, () -> builder.setRefrigerantDischargePressure(0.0));
  }

  /** The configured cooling temperature must reach the refrigerant compression train. */
  @Test
  void coolingTemperatureIsApplied() {
    LNGProcessModel model = new LNGProcessBuilder().setName("SMR cooling").setCycle(LNGProcessCycle.SMR)
        .setFeedFluid(feed()).setFeedFlowRate(100000.0).setFeedTemperature(25.0).setFeedPressure(60.0)
        .setTargetLiquefactionTemperature(-158.0).setRefrigerantCoolingTemperature(18.0).build();

    boolean found = false;
    for (neqsim.process.equipment.ProcessEquipmentInterface unit : model.getEquipment()) {
      if (unit instanceof neqsim.process.equipment.heatexchanger.Cooler && unit.getName().contains("aftercooler")) {
        assertEquals(18.0 + 273.15,
            ((neqsim.process.equipment.heatexchanger.Cooler) unit).getSpecifiedOutletTemperature(), 1.0e-6);
        found = true;
      }
    }
    assertTrue(found, "no refrigerant aftercooler found in the built model");
  }
}
