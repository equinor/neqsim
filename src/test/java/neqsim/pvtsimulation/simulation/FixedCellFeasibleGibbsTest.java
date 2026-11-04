package neqsim.pvtsimulation.simulation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Method;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;
import neqsim.physicalproperties.PhysicalPropertyType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Same-inventory Gibbs qualification for the retained ternary precision regressions.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class FixedCellFeasibleGibbsTest {
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

}
