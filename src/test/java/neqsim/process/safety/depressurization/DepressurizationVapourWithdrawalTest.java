package neqsim.process.safety.depressurization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.safety.depressurization.DepressurizationSimulator.DepressurizationResult;
import neqsim.process.safety.depressurization.DepressurizationSimulator.WithdrawalMode;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Vapour withdrawal from a two-phase blowdown inventory.
 *
 * <p>
 * A blowdown valve sits on top of the vessel, so a separator that holds live oil discharges vapour while the liquid
 * flashes. Discharging the bulk composition instead is silently wrong: the mass balance still closes.
 *
 * @author ESOL
 * @version 1.0
 */
public class DepressurizationVapourWithdrawalTest {

  /** Separator-like inventory: rich gas over a live oil holdup. */
  private SystemInterface twoPhaseInventory() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 60.0, 60.0);
    fluid.addComponent("nitrogen", 0.5);
    fluid.addComponent("CO2", 1.5);
    fluid.addComponent("methane", 45.0);
    fluid.addComponent("ethane", 6.0);
    fluid.addComponent("propane", 4.0);
    fluid.addComponent("n-butane", 3.0);
    fluid.addComponent("n-pentane", 3.0);
    fluid.addComponent("n-heptane", 12.0);
    fluid.addComponent("n-decane", 25.0);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  /** Lean gas kept above its dew point for the whole run, so it stays single phase. */
  private SystemInterface leanGas() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 60.0, 60.0);
    fluid.addComponent("methane", 97.0);
    fluid.addComponent("ethane", 3.0);
    fluid.setMixingRule("classic");
    return fluid;
  }

  private DepressurizationSimulator simulator(SystemInterface fluid, WithdrawalMode mode) {
    DepressurizationSimulator sim = new DepressurizationSimulator(fluid, 40.0, 0.04, 0.85, 6.0e5);
    sim.setTimeStep(2.0);
    sim.setMaxTime(1800.0);
    sim.setStopPressure(6.2e5);
    sim.setWithdrawalMode(mode);
    return sim;
  }

  @Test
  public void vapourPhaseIsSelectedByDensityNotByLabel() {
    SystemInterface fluid = twoPhaseInventory();
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initProperties();

    int index = DepressurizationSimulator.vapourPhaseIndex(fluid);
    assertTrue(index >= 0, "a vapour phase must be found in a two-phase inventory");
    double rhoVapour = fluid.getPhase(index).getDensity("kg/m3");
    for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
      assertTrue(rhoVapour <= fluid.getPhase(i).getDensity("kg/m3") + 1.0e-9,
          "the selected phase must be the lightest one");
    }
    assertTrue(fluid.getPhase(index).getMolarMass("gr/mol") < 45.0,
        "a hydrocarbon blowdown stream must be light; heavy means a liquid phase was picked");
  }

  @Test
  public void singlePhaseInventoryIsUnaffectedByTheWithdrawalMode() {
    // stop while the gas is still well above its dew point - a real gas does condense deep into a
    // blowdown, and AUTO is then right to switch
    DepressurizationSimulator autoSim = simulator(leanGas(), WithdrawalMode.AUTO);
    autoSim.setStopPressure(30.0e5);
    DepressurizationSimulator bulkSim = simulator(leanGas(), WithdrawalMode.BULK);
    bulkSim.setStopPressure(30.0e5);

    DepressurizationResult auto = autoSim.run();
    DepressurizationResult bulk = bulkSim.run();

    assertFalse(auto.vapourWithdrawalUsed, "a single-phase gas has no liquid to leave behind");
    assertEquals(bulk.pressureBara.get(bulk.pressureBara.size() - 1),
        auto.pressureBara.get(auto.pressureBara.size() - 1), 1.0e-6,
        "AUTO must reproduce BULK exactly when the inventory is single phase");
    assertEquals(bulk.massKg.get(bulk.massKg.size() - 1), auto.massKg.get(auto.massKg.size() - 1), 1.0e-6);
  }

  @Test
  public void vapourWithdrawalKeepsLiquidInTheVesselAndReleasesMore() {
    DepressurizationResult vapour = simulator(twoPhaseInventory(), WithdrawalMode.VAPOUR).run();
    DepressurizationResult bulk = simulator(twoPhaseInventory(), WithdrawalMode.BULK).run();

    assertTrue(vapour.vapourWithdrawalUsed, "the two-phase inventory must trigger vapour withdrawal");
    assertFalse(bulk.vapourWithdrawalUsed, "BULK must never switch to vapour withdrawal");

    double vapourStart = vapour.massKg.get(0);
    double vapourEnd = vapour.massKg.get(vapour.massKg.size() - 1);
    assertTrue(vapourEnd > 0.0, "the liquid holdup must remain in the vessel");
    assertTrue(vapourEnd < vapourStart, "some inventory must leave");

    // Bulk withdrawal drains heavy liquid through the valve, so it empties the vessel far more than
    // physically possible for a top-mounted blowdown valve.
    double bulkEnd = bulk.massKg.get(bulk.massKg.size() - 1);
    assertTrue(vapourEnd > bulkEnd, "vapour withdrawal must leave more mass behind than bulk withdrawal");
  }

  @Test
  public void autoModeUsesVapourWithdrawalForATwoPhaseInventory() {
    DepressurizationResult auto = simulator(twoPhaseInventory(), WithdrawalMode.AUTO).run();
    assertTrue(auto.vapourWithdrawalUsed, "AUTO must select vapour withdrawal when the inventory is multiphase");
    assertTrue(auto.pressureMonotonicNonIncreasing, "pressure must not rise during blowdown");
    assertTrue(auto.massMonotonicNonIncreasing, "inventory must not grow during blowdown");
  }
}
