package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;

/**
 * Regression test for the TLAB glycol-rig TEG regeneration column.
 *
 * <p>
 * This single-stage reboiled/condensed column previously reported a reboiler duty of only ~4-6 kW and a global energy
 * imbalance of ~ -20 kW because the phase-split out-streams built by {@link SimpleTray} lost their single-phase
 * designation and phase type (a liquid outlet was evaluated on the vapour EOS root). The reference rig snapshot lists a
 * reboiler power of 24.38 kW. After the fix in {@code SimpleTray.scalePhaseSystemToNormalizedMoles} the column
 * conserves energy globally and reports a physically correct reboiler duty.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class TegRegenerationEnergyBalanceTest {

  /**
   * Builds the TLAB TEG regeneration column and asserts that (1) the reboiler duty is physically correct (close to the
   * 24.38 kW reference rig value) and (2) the column conserves energy globally (reboiler + condenser duty equals the
   * change in stream enthalpy across the column).
   */
  @Test
  public void tegRegenerationColumnConservesEnergy() {
    double pBottom = 0.2 + 1.01325;

    SystemInterface feedTeg = new SystemSrkCPAstatoil(273.15 + 145.0, pBottom);
    feedTeg.addComponent("nitrogen", 0.00005);
    feedTeg.addComponent("water", 0.19995);
    feedTeg.addComponent("TEG", 0.8);
    feedTeg.setMixingRule(10);
    feedTeg.setMultiPhaseCheck(true);

    Stream teg = new Stream("TEG to regenerator", feedTeg);
    teg.setFlowRate(400.0, "kg/hr");
    teg.setTemperature(145.0, "C");
    teg.setPressure(pBottom, "bara");

    SystemInterface gasFluid = feedTeg.clone();
    gasFluid.setMolarComposition(new double[] { 1.0, 0.0, 0.0 });
    Stream gas = new Stream("gas to reboiler", gasFluid);
    gas.setFlowRate(13.0, "kg/hr");
    gas.setTemperature(199.0, "C");
    gas.setPressure(pBottom, "bara");

    ProcessSystem process = new ProcessSystem();
    process.add(teg);
    process.add(gas);

    DistillationColumn column = new DistillationColumn("TEG regeneration column", 1, true, true);
    column.addFeedStream(teg, 1);
    column.getReboiler().setOutTemperature(273.15 + 209.0);
    column.getCondenser().setOutTemperature(273.15 + 91.0);
    column.getTray(1).addStream(gas);
    column.setTopPressure(pBottom - 0.02);
    column.setBottomPressure(pBottom);
    process.add(column);

    teg.run();
    gas.run();
    process.run();

    double qReb = column.getReboiler().getDuty();
    double qCond = column.getCondenser().getDuty();

    double hIn = teg.getFluid().getEnthalpy() + gas.getFluid().getEnthalpy();
    double hOut = column.getGasOutStream().getFluid().getEnthalpy()
        + column.getLiquidOutStream().getFluid().getEnthalpy();

    // Reboiler duty must be physically correct (reference rig snapshot: 24.38 kW), not the
    // previously under-reported ~4-6 kW. Use a generous +/- band around the reference value.
    assertEquals(24.38e3, qReb, 3.0e3, "Reboiler duty should be close to the 24.38 kW reference value");
    assertTrue(qReb > 20.0e3, "Reboiler duty must exceed 20 kW (regression guard against ~4-6 kW under-report)");

    // Global energy balance over the whole column: Q_reb + Q_cond == H_out - H_in.
    double imbalance = (qReb + qCond) - (hOut - hIn);
    double relativeImbalance = Math.abs(imbalance) / Math.abs(hIn);
    assertTrue(relativeImbalance < 0.02,
        "Column must conserve energy globally; relative imbalance was " + relativeImbalance);
  }
}
