package neqsim.pvtsimulation.util;

import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Independent final-condition flashes, component conservation and explicit separator-basis tests.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class PvtSeparatorPathTest {
  /**
   * Creates a public synthetic hydrocarbon mixture.
   *
   * @return configured PR mixture
   */
  private SystemInterface sample() {
    SystemInterface fluid = new SystemPrEos(350.0, 100.0);
    fluid.addComponent("methane", 70.0);
    fluid.addComponent("ethane", 10.0);
    fluid.addComponent("propane", 5.0);
    fluid.addComponent("n-decane", 15.0);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /**
   * Checks native chemical-component closure on an independently supplied feed.
   *
   * @param feed unchanged input
   * @param result separator output
   */
  private void checkBalance(SystemInterface feed, PvtSeparatorPath.Result result) {
    double[][] products = result.getComponentMoles();
    for (int i = 0; i < feed.getNumberOfComponents(); i++) {
      Assertions.assertEquals(feed.getComponent(i).getNumberOfmoles(), products[0][i] + products[1][i] + products[2][i],
          1.0e-7);
    }
  }

  /** Checks direct stock-tank flash equivalence and unchanged feed and path configuration. */
  @Test
  void oneStageMatchesIndependentFlash() {
    SystemInterface feed = sample();
    PvtSeparatorPath path = new PvtSeparatorPath(new double[] {1.01325}, new double[] {288.15},
        "synthetic stock tank at 15 C and 1.01325 bara");
    PvtSeparatorPath.Result result = path.separate(feed);
    SystemInterface reference = sample();
    reference.setPressure(1.01325);
    reference.setTemperature(288.15);
    new ThermodynamicOperations(reference).TPflash();
    reference.initProperties();
    double gas = reference.getPhase("gas").getCorrectedVolume();
    double oil = reference.getPhase("oil").getCorrectedVolume();
    Assertions.assertArrayEquals(new double[] {gas, oil, 0.0}, result.getVolumesM3(), 1.0e-10);
    Assertions.assertEquals(gas / oil, result.getGasOilRatio(), 1.0e-8);
    checkBalance(feed, result);
    Assertions.assertEquals(100.0, feed.getPressure());
    Assertions.assertEquals(350.0, feed.getTemperature());
    path.getPressuresBara()[0] = 10.0;
    result.getVolumesM3()[0] = -1.0;
    result.getComponentMoles()[0][0] = -1.0;
    Assertions.assertEquals(1.01325, path.getPressuresBara()[0]);
    Assertions.assertTrue(result.getVolumesM3()[0] > 0.0);
    checkBalance(feed, SerializationUtils.clone(result));
    Assertions.assertEquals(1.01325, result.getFinalPressureBara());
    Assertions.assertEquals(288.15, result.getFinalTemperatureK());
    Assertions.assertEquals(path.getReference(), SerializationUtils.clone(result).getReference());
  }

  /** Verifies condensate from the first-stage offgas is retained in final oil, rather than discarded. */
  @Test
  void retainsCondensateFromOffgas() {
    SystemInterface first = sample();
    first.setPressure(20.0);
    first.setTemperature(400.0);
    new ThermodynamicOperations(first).TPflash();
    SystemInterface offgas = SerializationUtils.clone(first).phaseToSystem("gas");
    SystemInterface liquid = SerializationUtils.clone(first).phaseToSystem("oil");
    for (SystemInterface stream : new SystemInterface[] {offgas, liquid}) {
      stream.setPressure(1.01325);
      stream.setTemperature(288.15);
      new ThermodynamicOperations(stream).TPflash();
      stream.initProperties();
    }
    Assertions.assertTrue(offgas.hasPhaseType("oil"));
    double expectedOil = offgas.getPhase("oil").getCorrectedVolume() + liquid.getPhase("oil").getCorrectedVolume();
    double expectedGas = offgas.getPhase("gas").getCorrectedVolume() + liquid.getPhase("gas").getCorrectedVolume();
    PvtSeparatorPath.Result result = new PvtSeparatorPath(new double[] {20.0, 1.01325}, new double[] {400.0, 288.15},
        "synthetic two-stage offgas-condensation reference").separate(sample());
    Assertions.assertEquals(expectedOil, result.getVolumesM3()[1], 1.0e-10);
    Assertions.assertEquals(expectedGas, result.getVolumesM3()[0], 1.0e-10);
    checkBalance(sample(), result);
  }

  /** Checks three-phase liquid forwarding and water accounting without silently substituting a water model. */
  @Test
  void conservesWaterAndBothHydrocarbonProducts() {
    SystemInterface feed = sample();
    feed.addComponent("water", 10.0);
    feed.setMixingRule("classic");
    feed.setMultiPhaseCheck(true);
    PvtSeparatorPath.Result result = new PvtSeparatorPath(new double[] {10.0, 1.01325}, new double[] {310.0, 288.15},
        "synthetic PR three-phase accounting, not water-solubility qualification").separate(feed);
    checkBalance(feed, result);
    for (double volume : result.getVolumesM3()) {
      Assertions.assertTrue(volume > 0.0);
    }
  }

  /** Checks final-condition basis changes are explicit, and absent oil never gives an invented finite GOR. */
  @Test
  void retainsDeclaredStandardBasisAndUndefinedRatio() {
    SystemInterface gas = new SystemPrEos(300.0, 10.0);
    gas.addComponent("methane", 100.0);
    gas.setMixingRule("classic");
    PvtSeparatorPath.Result cold = new PvtSeparatorPath(new double[] {1.01325}, new double[] {288.15},
        "15 C standard basis").separate(gas);
    PvtSeparatorPath.Result warm = new PvtSeparatorPath(new double[] {1.0133}, new double[] {288.71},
        "Duvernay report declares 15.56 C / 101.33 kPa; explicit separate basis").separate(gas);
    Assertions.assertTrue(Double.isNaN(cold.getGasOilRatio()));
    Assertions.assertEquals(0.0, cold.getVolumesM3()[1]);
    double idealRatio = (288.71 / 1.0133) / (288.15 / 1.01325);
    Assertions.assertEquals(idealRatio, warm.getVolumesM3()[0] / cold.getVolumesM3()[0], 1.0e-4);
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new PvtSeparatorPath(new double[] {10.0, 20.0}, new double[] {300.0, 288.15}, "invalid"));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new PvtSeparatorPath(new double[] {1.0}, new double[] {Double.NaN}, "invalid"));
  }
}
