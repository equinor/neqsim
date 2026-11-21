package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPitzer;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests the fitted MEG neutral-solute Pitzer dataset against the regression data and hydrate cross-checks.
 *
 * @author NeqSim
 * @version 1.0
 */
class PitzerMegParametersTest {
  private static final double WATER_MOLAR_MASS = 0.01801528;

  /**
   * Builds 1 kg water with MEG and NaCl in a single aqueous Pitzer role phase.
   *
   * @param temperature temperature in K
   * @param megMoles MEG amount in mol
   * @param naclMoles NaCl amount in mol, zero for none
   * @return the initialised aqueous Pitzer phase
   */
  private static PhasePitzer singleAqueous(double temperature, double megMoles, double naclMoles) {
    SystemPitzer system = new SystemPitzer(temperature, 1.0);
    system.addComponent("water", 1.0 / WATER_MOLAR_MASS);
    system.addComponent("MEG", megMoles);
    if (naclMoles > 0.0) {
      system.addComponent("Na+", naclMoles);
      system.addComponent("Cl-", naclMoles);
    }
    system.setMixingRule("classic");
    system.init(0);
    system.setNumberOfPhases(1);
    system.setPhaseIndex(0, 1);
    system.setPhaseType(0, PhaseType.AQUEOUS);
    system.setBeta(0, 1.0);
    system.init(1);
    return (PhasePitzer) system.getPhases()[1];
  }

  /**
   * Mole-fraction water activity of a phase.
   *
   * @param phase Pitzer phase
   * @return water activity
   */
  private static double waterActivity(PhasePitzer phase) {
    int water = phase.getComponent("water").getComponentNumber();
    return phase.getActivityCoefficient(water) * phase.getComponent(water).getx();
  }

  /**
   * Ice-liquid water activity of Koop et al. (2000), Nature 406, 611.
   *
   * @param temperature temperature in K
   * @return water activity in equilibrium with ice
   */
  private static double iceWaterActivity(double temperature) {
    double numerator = 210368.0 + 131.438 * temperature - 3.32373e6 / temperature - 41729.1 * Math.log(temperature);
    return Math.exp(numerator / (8.314462618 * temperature));
  }

  /** Checks the fit against the 298.15 K isopiestic water activities. */
  @Test
  void reproducesIsopiesticWaterActivityAt298K() {
    // Tsurko et al. (2026), J. Chem. Eng. Data 71, 942-959, Table 44; u(a_w) = 0.0004.
    List<double[]> data = new ArrayList<double[]>();
    data.add(new double[] {1.0766, 0.9816});
    data.add(new double[] {3.8601, 0.9347});
    data.add(new double[] {7.1174, 0.8843});
    data.add(new double[] {11.3910, 0.8173});
    data.add(new double[] {15.9500, 0.7728});
    data.add(new double[] {20.0301, 0.7442});
    for (double[] point : data) {
      assertEquals(point[1], waterActivity(singleAqueous(298.15, point[0], 0.0)), 0.005, "m=" + point[0]);
    }
  }

  /** Checks the fit against freezing-point water activities. */
  @Test
  void reproducesFreezingPointWaterActivity() {
    // Tsurko et al. (2026), Table 2 freezing points; a_w at T_f from ice equilibrium.
    double[][] data = {{1.9989, -4.03}, {5.9547, -12.79}, {9.9983, -22.30}, {15.9988, -36.48}};
    for (double[] point : data) {
      double temperature = 273.15 + point[1];
      double calculated = waterActivity(singleAqueous(temperature, point[0], 0.0));
      assertEquals(iceWaterActivity(temperature), calculated, 0.006, "m=" + point[0]);
    }
  }

  /** Checks the MEG-NaCl term against the ternary melting-point equation. */
  @Test
  void naclMegCrossTermsLowerWaterActivity() {
    // Benson et al. (2010) ternary melting correlation, EG:NaCl mass ratio 5, 20 wt% total solute: T_f = -10.63 C.
    double temperature = 273.15 - 10.63;
    PhasePitzer ternary = singleAqueous(temperature, 3.36, 0.713);
    assertEquals(iceWaterActivity(temperature), waterActivity(ternary), 0.006);
    assertTrue(ternary.getParameterDatasetId().contains(PitzerMegParameters.DATASET_ID));
  }

  /** Checks that MEG partitions to the brine and that assumed zero rows are reported. */
  @Test
  void megStaysInTheAqueousPhaseAndDiluteGasRowsAreListed() {
    SystemPitzer system = new SystemPitzer(283.15, 100.0);
    system.addComponent("methane", 90.0);
    system.addComponent("nitrogen", 1.0);
    system.addComponent("water", 1.0 / WATER_MOLAR_MASS);
    system.addComponent("MEG", 0.3 / 0.7 / 0.06207);
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(true);
    new ThermodynamicOperations(system).TPflash();
    PhasePitzer aqueous = null;
    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      if (system.getPhase(i) instanceof PhasePitzer) {
        aqueous = (PhasePitzer) system.getPhase(i);
      }
    }
    assertTrue(aqueous != null);
    double megTotal = system.getPhase(0).getComponent("MEG").getNumberOfmoles();
    double megInAqueous = aqueous.getComponent("MEG").getNumberOfMolesInPhase() / megTotal;
    assertTrue(megInAqueous > 0.999, "MEG fraction in aqueous phase " + megInAqueous);
    assertTrue(aqueous.getMegAssumedZeroRows().contains("LAMBDA:[nitrogen,MEG]"));
  }

  /** Checks that MEG with an unparameterised co-solvent is rejected. */
  @Test
  void megWithMethanolFailsClosed() {
    SystemPitzer system = new SystemPitzer(283.15, 10.0);
    system.addComponent("methane", 10.0);
    system.addComponent("water", 1.0 / WATER_MOLAR_MASS);
    system.addComponent("MEG", 1.0);
    system.addComponent("methanol", 1.0);
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(true);
    assertThrows(RuntimeException.class, () -> new ThermodynamicOperations(system).TPflash());
  }

  /**
   * Compares the Pitzer MEG-inhibited hydrate temperature with SRK-CPA.
   *
   * @throws Exception if a hydrate calculation fails
   */
  @Test
  void pitzerMegHydrateShiftMatchesCpa() throws Exception {
    double megMoles = 0.3 / 0.7 / 0.06207; // 30 wt% MEG on a 1 kg water basis
    SystemInterface pitzer = new SystemPitzer(285.0, 100.0);
    SystemInterface cpa = new SystemSrkCPAstatoil(285.0, 100.0);
    for (SystemInterface fluid : new SystemInterface[] {pitzer, cpa}) {
      fluid.addComponent("methane", 86.0);
      fluid.addComponent("ethane", 7.0);
      fluid.addComponent("propane", 3.0);
      fluid.addComponent("water", 1.0 / WATER_MOLAR_MASS);
      fluid.addComponent("MEG", megMoles);
      if (fluid instanceof SystemPitzer) {
        fluid.setMixingRule("classic");
      } else {
        fluid.setMixingRule(10);
      }
      fluid.setMultiPhaseCheck(true);
      fluid.setHydrateCheck(true);
      new ThermodynamicOperations(fluid).hydrateFormationTemperature();
    }
    // Before the MEG dataset, Pitzer ignored MEG and returned the uninhibited temperature (about 10 K higher).
    assertEquals(cpa.getTemperature(), pitzer.getTemperature(), 1.0);
  }
}
