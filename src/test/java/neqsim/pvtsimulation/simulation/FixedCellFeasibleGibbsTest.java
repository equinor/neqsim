package neqsim.pvtsimulation.simulation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Method;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import neqsim.physicalproperties.PhysicalPropertyType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Same-inventory Gibbs qualification for the retained ternary precision regressions.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class FixedCellFeasibleGibbsTest {
  /** Aggregate numerical qualification evidence retained in the test report. */
  private static final Logger logger = LogManager.getLogger(FixedCellFeasibleGibbsTest.class);

  /** Frozen component inventories from the unmodified-master negative histories, mol. */
  private static final double[][] INVENTORIES = {{.1065743133509231, .02008830385442146, .04684103007088043},
      {.1077394874686004, .02016485446850729, .04684930533912084},
      {.07808208975944088, .010604342195146058, .03980899435609955}};

  /**
   * Constructs a new, independently initialized public-API input.
   *
   * @param index frozen case number
   * @param scale extensive sample multiplier
   * @return declared unshifted classic SRK sample
   */
  private SystemInterface sample(int index, double scale) {
    SystemInterface fluid = new SystemSrkEos(index == 2 ? 325 : 300, 10);
    String[] names = {"methane", "n-butane", "n-heptane"};
    for (int i = 0; i < names.length; i++) {
      fluid.addComponent(names[i], scale * INVENTORIES[index][i]);
    }
    fluid.setMixingRule("classic");
    fluid.useVolumeCorrection(false);
    fluid.setMultiPhaseCheck(true);
    fluid.init(0);
    return fluid;
  }

  /** The three recorded states must complete through the public entry point without a warm-state fixture. */
  @Test
  void freshPublicReplaysPreserveEveryOriginalGateAndCaller() {
    for (int index = 0; index < INVENTORIES.length; index++) {
      for (double scale : new double[] {.001, 1, 1000}) {
        SystemInterface fluid = sample(index, scale);
        byte[] before = SerializationUtils.serialize(fluid);
        ConstantVolumeDepletion.FixedCellResult result = assertDoesNotThrow(() -> new ConstantVolumeDepletion(fluid)
            .runDryPhaseChangingFixedCellDepletion(fluid.getTemperature(), new double[] {10, 9.9}));
        double[] cumulative = new double[3];
        for (DepletionSnapshot snapshot : result.getSnapshots()) {
          SystemInterface cell = snapshot.getRetainedFluid();
          assertTrue(cell.hasPhaseType("gas"));
          assertTrue(cell.hasPhaseType("oil"));
          assertEquals(result.getReferenceVolumeM3(), cell.getCorrectedVolume(), 1e-12 * result.getReferenceVolumeM3());
          double[] removed = snapshot.getProducedComponentMoles();
          for (int i = 0; i < 3; i++) {
            cumulative[i] += removed[i];
            double initial = scale * INVENTORIES[index][i];
            assertEquals(initial, cumulative[i] + cell.getComponent(i).getNumberOfmoles(),
                1e-10 * Math.max(1, initial));
            double phases = 0;
            for (int p = 0; p < cell.getNumberOfPhases(); p++) {
              phases += cell.getPhase(p).getNumberOfMolesInPhase() * cell.getPhase(p).getComponent(i).getx();
            }
            assertEquals(cell.getComponent(i).getNumberOfmoles(), phases, 1e-12 * Math.max(1, initial));
          }
          for (int i = 0; i < 3; i++) {
            double gas = cell.getPhase("gas").getComponent(i).getx()
                * cell.getPhase("gas").getComponent(i).getFugacityCoefficient();
            double oil = cell.getPhase("oil").getComponent(i).getx()
                * cell.getPhase("oil").getComponent(i).getFugacityCoefficient();
            assertTrue(Math.abs(Math.log(gas / oil)) <= 1e-12);
          }
        }
        assertArrayEquals(before, SerializationUtils.serialize(fluid));
      }
    }
  }

  /**
   * A registered but absent component must remain absent during energy-reference construction.
   *
   * @throws Exception if diagnostic reflection fails
   */
  @Test
  void zeroInventoryComponentIsNeverInvented() throws Exception {
    SystemInterface fluid = new SystemSrkEos(275, 60);
    fluid.addComponent("methane", .95);
    fluid.addComponent("n-butane", .05);
    fluid.addComponent("n-heptane", 0);
    fluid.setMixingRule("classic");
    fluid.useVolumeCorrection(false);
    fluid.setMultiPhaseCheck(true);
    fluid.init(0);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(1);
    fluid.initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
    byte[] before = SerializationUtils.serialize(fluid);
    Method reference = ConstantVolumeDepletion.class.getDeclaredMethod("fixedCellFeasibleGibbsReference",
        SystemInterface.class, double[].class);
    reference.setAccessible(true);
    double energy = (Double) reference.invoke(null, fluid, DepletionSnapshot.inventory(fluid));
    assertTrue(Double.isFinite(energy));
    assertEquals(0, fluid.getComponent(2).getNumberOfmoles(), 0);
    assertArrayEquals(before, SerializationUtils.serialize(fluid));
  }

  /**
   * Independent allocation construction verifies feasibility, identity and the unchanged energy allowance.
   *
   * @throws Exception if diagnostic reflection fails
   */
  @Test
  void reconstructedReferenceUsesStoredInventoryWithoutChangingInput() throws Exception {
    Method reference = ConstantVolumeDepletion.class.getDeclaredMethod("fixedCellFeasibleGibbsReference",
        SystemInterface.class, double[].class);
    Method refine = ConstantVolumeDepletion.class.getDeclaredMethod("refineFixedCellEquilibrium",
        SystemInterface.class);
    reference.setAccessible(true);
    refine.setAccessible(true);
    for (int index = 0; index < INVENTORIES.length; index++) {
      SystemInterface fluid = sample(index, 1);
      new ThermodynamicOperations(fluid).TPflash();
      fluid.init(1);
      fluid.initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
      byte[] before = SerializationUtils.serialize(fluid);
      double[] total = DepletionSnapshot.inventory(fluid);
      double[] frozenFeasibleEnergy = {188.9332353482397, 195.08971582874142, 210.61723808302997};
      double expected = frozenFeasibleEnergy[index];
      double actual = (Double) reference.invoke(null, fluid, total);
      assertEquals(expected, actual, 1e-9);
      assertArrayEquals(before, SerializationUtils.serialize(fluid));
      refine.invoke(null, fluid);
      assertTrue(fluid.getGibbsEnergy() <= actual + 1e-10 * Math.max(1, Math.abs(actual)));
    }
  }

  /**
   * Qualifies copying across declared EOS controls using analytic extensivity and fresh homogeneous systems.
   *
   * @throws Exception if diagnostic reflection fails
   */
  @Test
  void eosControlsPreserveExtensivityBranchesAndCallerState() throws Exception {
    Method reference = ConstantVolumeDepletion.class.getDeclaredMethod("fixedCellFeasibleGibbsReference",
        SystemInterface.class, double[].class);
    reference.setAccessible(true);
    int controls = 0;
    double maxExtensiveErrorPerScale = 0;
    double maxFreshErrorPerScale = 0;
    for (boolean pengRobinson : new boolean[] {false, true}) {
      for (boolean corrected : new boolean[] {false, true}) {
        for (int index = 0; index < INVENTORIES.length; index++) {
          for (double scale : new double[] {.001, 1, 1000}) {
            for (int nearby = -1; nearby <= 1; nearby++) {
              double temperature = (index == 2 ? 325 : 300) + 2 * nearby;
              double pressure = 10 + .2 * nearby;
              String context = "PR=" + pengRobinson + ", corrected=" + corrected + ", case=" + index + ", scale="
                  + scale + ", T=" + temperature + ", P=" + pressure;
              double[] amounts = INVENTORIES[index].clone();
              for (int i = 0; i < amounts.length; i++) {
                amounts[i] *= scale;
              }
              SystemInterface fluid = controlSample(pengRobinson, corrected, temperature, pressure, amounts);
              new ThermodynamicOperations(fluid).TPflash();
              fluid.init(3);
              fluid.initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
              assertEquals(2, fluid.getNumberOfPhases(), context);
              assertTrue(fluid.hasPhaseType("gas"), context);
              assertTrue(fluid.hasPhaseType("oil"), context);
              byte[] before = SerializationUtils.serialize(fluid);
              double[] phaseTotals = new double[amounts.length];
              for (int p = 0; p < fluid.getNumberOfPhases(); p++) {
                for (int i = 0; i < amounts.length; i++) {
                  phaseTotals[i] += fluid.getPhase(p).getNumberOfMolesInPhase()
                      * fluid.getPhase(p).getComponent(i).getx();
                }
              }
              double energy = fluid.getGibbsEnergy();
              assertEquals(energy, (Double) reference.invoke(null, fluid, phaseTotals), 0, context);
              assertArrayEquals(before, SerializationUtils.serialize(fluid), context);
              double[] scaled = phaseTotals.clone();
              double[] redistributed = phaseTotals.clone();
              double[] factors = {1.000002, .999997, 1.000001};
              for (int i = 0; i < amounts.length; i++) {
                scaled[i] *= 1.125;
                redistributed[i] *= factors[i];
              }
              double extensive = (Double) reference.invoke(null, fluid, scaled);
              maxExtensiveErrorPerScale = Math.max(maxExtensiveErrorPerScale,
                  Math.abs(extensive - 1.125 * energy) / scale);
              assertEquals(1.125 * energy, extensive, 1e-9 * scale, context);
              assertArrayEquals(before, SerializationUtils.serialize(fluid), context);
              double expected = freshHomogeneousEnergy(fluid, pengRobinson, corrected, redistributed);
              double actual = (Double) reference.invoke(null, fluid, redistributed);
              maxFreshErrorPerScale = Math.max(maxFreshErrorPerScale, Math.abs(expected - actual) / scale);
              assertEquals(expected, actual, 1e-9 * scale, context);
              assertArrayEquals(before, SerializationUtils.serialize(fluid), context);
              assertEquals(actual, (Double) reference.invoke(null, fluid, redistributed), 0, context);
              assertArrayEquals(before, SerializationUtils.serialize(fluid), context);
              controls++;
            }
          }
        }
      }
    }
    assertEquals(108, controls);
    logger.info("Feasible Gibbs controls={}; max extensive error/scale={} J; max fresh-phase error/scale={} J",
        controls, maxExtensiveErrorPerScale, maxFreshErrorPerScale);
  }

  /**
   * Constructs the explicitly declared control EOS from names and amounts, without copying any system.
   *
   * @param pengRobinson selects PR instead of SRK
   * @param corrected whether volume correction is enabled
   * @param temperature temperature, K
   * @param pressure absolute pressure, bara
   * @param amounts methane/n-butane/n-heptane amounts, mol
   * @return new configured system
   */
  private SystemInterface controlSample(boolean pengRobinson, boolean corrected, double temperature, double pressure,
      double[] amounts) {
    SystemInterface result = pengRobinson ? new SystemPrEos(temperature, pressure)
        : new SystemSrkEos(temperature, pressure);
    String[] names = {"methane", "n-butane", "n-heptane"};
    for (int i = 0; i < names.length; i++) {
      result.addComponent(names[i], amounts[i]);
    }
    result.setMixingRule("classic");
    result.useVolumeCorrection(corrected);
    result.setMultiPhaseCheck(true);
    result.init(0);
    return result;
  }

  /**
   * Independently constructs each homogeneous phase from its normalized allocation at the target totals.
   *
   * @param source equilibrium phase fractions and compositions, never modified
   * @param pengRobinson selects the declared control EOS
   * @param corrected whether volume correction is enabled
   * @param target target named component amounts, mol
   * @return sum of fresh homogeneous phase Gibbs energies, J
   */
  private double freshHomogeneousEnergy(SystemInterface source, boolean pengRobinson, boolean corrected,
      double[] target) {
    double[][] fractions = new double[source.getNumberOfPhases()][target.length];
    for (int i = 0; i < target.length; i++) {
      double sum = 0;
      for (int p = 0; p < fractions.length; p++) {
        fractions[p][i] = source.getPhase(p).getNumberOfMolesInPhase() * source.getPhase(p).getComponent(i).getx();
        sum += fractions[p][i];
      }
      for (int p = 0; p < fractions.length; p++) {
        fractions[p][i] /= sum;
      }
    }
    double energy = 0;
    double[] reconstructed = new double[target.length];
    for (int p = 0; p < fractions.length; p++) {
      double[] amounts = new double[target.length];
      for (int i = 0; i < amounts.length; i++) {
        amounts[i] = target[i] * fractions[p][i];
        assertTrue(amounts[i] >= 0);
      }
      SystemInterface phase = controlSample(pengRobinson, corrected, source.getTemperature(), source.getPressure(),
          amounts);
      phase.setForceSinglePhase(source.getPhase(p).getType());
      phase.init(3);
      phase.initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
      assertEquals(source.getPhase(p).getType(), phase.getPhase(0).getType());
      assertEquals(source.getTemperature(), phase.getTemperature(), 0);
      assertEquals(source.getPressure(), phase.getPressure(), 0);
      assertTrue(Double.isFinite(phase.getCorrectedVolume()) && phase.getCorrectedVolume() > 0);
      assertTrue(Double.isFinite(phase.getGibbsEnergy()));
      energy += phase.getGibbsEnergy();
      for (int i = 0; i < target.length; i++) {
        reconstructed[i] += phase.getPhase(0).getNumberOfMolesInPhase() * phase.getPhase(0).getComponent(i).getx();
      }
    }
    for (int i = 0; i < target.length; i++) {
      assertEquals(target[i], reconstructed[i], 64 * Math.ulp(Math.max(1, Math.abs(target[i]))));
    }
    return energy;
  }

}
