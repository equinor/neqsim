package neqsim.process.equipment.reservoir;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.blackoil.BlackOilPVTTable.Record;
import neqsim.pvtsimulation.reservoirproperties.materialbalance.GeneralizedMaterialBalance;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests original-EOS PVT with the recorded SimpleReservoir depletion history.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class GeneralizedReservoirSurveillanceTest {
  /**
   * Creates a gas reservoir from the supplied original or tuned fluid without mutating it.
   *
   * @param original original EOS fluid
   * @return depleted reservoir with its initial point retained
   */
  private SimpleReservoir deplete(SystemInterface original) {
    SimpleReservoir reservoir = new SimpleReservoir("synthetic history");
    reservoir.setReservoirFluid(original.clone(), 1.0e6, 0.0, 0.0);
    reservoir.addGasProducer("producer").setFlowRate(5000.0, "Sm3/day");
    reservoir.run();
    for (int i = 0; i < 4; i++) {
      reservoir.runTransient(30.0 * 86400.0);
    }
    return reservoir;
  }

  /**
   * Evaluates EOS Bg independently on the tank's exact 15 C standard-volume basis.
   *
   * @param original original or previously tuned EOS
   * @param pressures measured reservoir pressures (bara)
   * @return pressure-aligned PVT records
   */
  private Record[] evaluatePvt(SystemInterface original, double[] pressures) {
    SystemInterface standard = original.clone();
    standard.setTemperature(288.15);
    standard.setPressure(1.01325);
    new ThermodynamicOperations(standard).TPflash();
    standard.init(1);
    double standardVolume = standard.getVolume("m3");
    Record[] pvt = new Record[pressures.length];
    for (int i = 0; i < pressures.length; i++) {
      SystemInterface sample = original.clone();
      sample.setPressure(pressures[i]);
      new ThermodynamicOperations(sample).TPflash();
      sample.init(1);
      double bg = sample.getVolume("m3") / standardVolume;
      pvt[i] = new Record(pressures[i], 0.0, 1.0, Double.NaN, bg, Double.NaN, 0.0, 1.0, Double.NaN);
    }
    return pvt;
  }

  /** Checks the original-fluid to forward-history to inverse-volume chain against known inventory. */
  @Test
  void originalEosAndTankHistoryRecoverOgip() {
    SystemInterface original = new SystemPrEos(373.15, 250.0);
    original.addComponent("methane", 1.0);
    original.setMixingRule("classic");
    SimpleReservoir reservoir = deplete(original);
    double[] pressures = reservoir.getPressureHistory();
    Record[] pvt = evaluatePvt(original, pressures);
    double[] support = new double[pressures.length];
    GeneralizedMaterialBalance.Result fit = reservoir.getSurveillance()
        .generalizedMaterialBalance(pvt, support, support).fitInitiallyGas();
    Assertions.assertEquals(reservoir.getOGIP("Sm3"), fit.getOgip(), reservoir.getOGIP("Sm3") * 1.0e-6);
    Assertions.assertEquals(0.0, fit.getOoip());
    Assertions.assertTrue(fit.getRmsResidual() < 0.01);
    Assertions.assertEquals(250.0, original.getPressure(), 1.0e-12);
    Assertions.assertEquals(1.0, original.getTotalNumberOfMoles(), 1.0e-12);
    pvt[1] = pvt[0];
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> reservoir.getSurveillance().generalizedMaterialBalance(pvt, support, support));
  }

  /** Ensures incomplete historical arrays and unrecorded injection cannot produce a silent fit. */
  @Test
  void rejectsUnalignedHistoryAndUnrecordedInjection() {
    SystemInterface original = new SystemPrEos(373.15, 250.0);
    original.addComponent("methane", 1.0);
    original.setMixingRule("classic");
    SimpleReservoir reservoir = deplete(original);
    Record[] pvt = evaluatePvt(original, reservoir.getPressureHistory());
    double[] support = new double[pvt.length];
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> reservoir.getSurveillance().generalizedMaterialBalance(pvt, new double[1], support));
    reservoir.addGasInjector("unrecorded injection");
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> reservoir.getSurveillance().generalizedMaterialBalance(pvt, support, support));
    SimpleReservoir truncated = deplete(original);
    truncated.clearProductionHistory();
    truncated.recordProductionHistoryPoint();
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> truncated.getSurveillance().generalizedMaterialBalance(pvt, support, support));
  }
}
