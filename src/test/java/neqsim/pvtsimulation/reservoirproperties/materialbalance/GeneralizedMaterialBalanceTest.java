package neqsim.pvtsimulation.reservoirproperties.materialbalance;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.blackoil.BlackOilPVTTable.Record;

/**
 * Independent surface-component and reservoir-volume checks for the generalized balance.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class GeneralizedMaterialBalanceTest {
  /** Initial PVT; all volumes use the same standard conditions. */
  private static final Record INITIAL = pvt(300.0, 100.0, 1.6, 0.004, 0.0002);

  /**
   * Creates a PVT record; unused viscosity fields deliberately carry no invented values.
   *
   * @param pressure average reservoir pressure (bara)
   * @param rs solution gas ratio
   * @param bo oil formation volume factor
   * @param bg gas formation volume factor
   * @param rv vaporized oil ratio
   * @return PVT record
   */
  private static Record pvt(double pressure, double rs, double bo, double bg, double rv) {
    return new Record(pressure, rs, bo, Double.NaN, bg, Double.NaN, rv, 1.02, Double.NaN);
  }

  /**
   * Generates history from independent remaining phase inventories and a volume closure. This does not call the
   * production implementation to manufacture its fitting targets.
   *
   * @param oil initial free-oil stock-tank component (Sm3)
   * @param gas initial free-gas standard component (Sm3)
   * @param support whether to include water injection, aquifer influx and compressibility
   * @return synthetic history
   */
  private GeneralizedMaterialBalance history(double oil, double gas, boolean support) {
    GeneralizedMaterialBalance balance = new GeneralizedMaterialBalance(INITIAL);
    Record[] records = {pvt(260.0, 80.0, 1.5, 0.005, 0.00016), pvt(210.0, 60.0, 1.4, 0.008, 0.00012),
        pvt(160.0, 40.0, 1.3, 0.012, 0.00008), pvt(110.0, 20.0, 1.2, 0.020, 0.00004)};
    double initialVolume = oil * INITIAL.Bo + gas * INITIAL.Bg;
    double initialOil = oil + INITIAL.Rv * gas;
    double initialGas = gas + INITIAL.Rs * oil;
    for (int i = 0; i < records.length; i++) {
      Record current = records[i];
      double fraction = support ? 0.001 * (i + 1) : 0.0;
      double influx = support ? 0.002 * initialVolume * (i + 1) : 0.0;
      // Negative net water withdrawal represents injection, separate from aquifer influx.
      double netWater = support ? -0.001 * initialVolume * (i + 1) : 0.0;
      double remainingVolume = initialVolume * (1.0 - fraction) - influx + netWater * current.Bw;
      double oilVolume = remainingVolume * (oil == 0.0 ? 0.01 * (i + 1) : 0.58 - 0.08 * i);
      double gasVolume = remainingVolume - oilVolume;
      double freeOil = oilVolume / current.Bo;
      double freeGas = gasVolume / current.Bg;
      double netOil = initialOil - (freeOil + current.Rv * freeGas);
      double netGas = initialGas - (freeGas + current.Rs * freeOil);
      balance.addObservation(current, netOil, netGas, netWater, influx, fraction);
    }
    return balance;
  }

  /** Checks recovery of both free phases and both total surface components. */
  @Test
  void recoversOilAndGasIncludingVaporizedOil() {
    GeneralizedMaterialBalance.Result result = history(1.0e7, 2.0e9, false).fitOilAndGas();
    Assertions.assertEquals(1.0e7, result.getInitialFreeOil(), 1.0e-6);
    Assertions.assertEquals(2.0e9, result.getInitialFreeGas(), 1.0e-4);
    Assertions.assertEquals(1.04e7, result.getOoip(), 1.0e-6);
    Assertions.assertEquals(3.0e9, result.getOgip(), 1.0e-4);
    Assertions.assertEquals(0.5, result.getGasCapRatio(), 1.0e-10);
    Assertions.assertTrue(result.getRmsResidual() < 1.0e-6);
    Assertions.assertTrue(Double.isFinite(result.getConditionNumber()));
    double[] residuals = result.getResiduals();
    residuals[0] = 1.0e20;
    Assertions.assertTrue(Math.abs(result.getResiduals()[0]) < 1.0e-6);
  }

  /** Checks recovery without confounding known aquifer and water-injection support. */
  @Test
  void accountsForKnownSupportAndNegativeNetWithdrawal() {
    GeneralizedMaterialBalance.Result result = history(1.0e7, 2.0e9, true).fitOilAndGas();
    Assertions.assertEquals(1.0e7, result.getInitialFreeOil(), 1.0e-6);
    Assertions.assertEquals(2.0e9, result.getInitialFreeGas(), 1.0e-4);
  }

  /** Checks a gas-condensate initial state with no free liquid. */
  @Test
  void initiallyGasRetainsCondensateInPlace() {
    GeneralizedMaterialBalance.Result result = history(0.0, 2.0e9, false).fitInitiallyGas();
    Assertions.assertEquals(2.0e9, result.getOgip(), 1.0e-4);
    Assertions.assertEquals(4.0e5, result.getOoip(), 1.0e-6);
    Assertions.assertEquals(0.0, result.getInitialFreeOil());
    Assertions.assertEquals(Double.POSITIVE_INFINITY, result.getGasCapRatio());
  }

  /** Checks the initially-oil and known-gas-cap constraints. */
  @Test
  void constrainedFitsRecoverSpecifiedInitialState() {
    GeneralizedMaterialBalance.Result oil = history(1.0e7, 0.0, false).fitInitiallyOil();
    Assertions.assertEquals(1.0e7, oil.getOoip(), 1.0e-6);
    Assertions.assertEquals(1.0e9, oil.getOgip(), 1.0e-4);
    GeneralizedMaterialBalance.Result cap = history(1.0e7, 2.0e9, true).fitKnownGasCap(0.5);
    Assertions.assertEquals(1.04e7, cap.getOoip(), 1.0e-6);
    Assertions.assertEquals(3.0e9, cap.getOgip(), 1.0e-4);
  }

  /** Checks numerical scaling across very different inventory magnitudes. */
  @Test
  void fitIsIndependentOfInventoryMagnitude() {
    for (double scale : new double[] {1.0e-8, 1.0, 1.0e8}) {
      GeneralizedMaterialBalance.Result result = history(1.0e7 * scale, 2.0e9 * scale, true).fitOilAndGas();
      Assertions.assertEquals(1.04e7 * scale, result.getOoip(), 1.0e-6 * scale);
      Assertions.assertEquals(3.0e9 * scale, result.getOgip(), 1.0e-4 * scale);
    }
  }

  /** Checks the independent conventional black-oil limiting equation when Rv is zero. */
  @Test
  void reducesToConventionalOilBalanceAtZeroRv() {
    Record initial = pvt(300.0, 100.0, 1.5, 0.004, 0.0);
    GeneralizedMaterialBalance balance = new GeneralizedMaterialBalance(initial);
    double expectedOil = 1.0e7;
    for (int i = 1; i <= 4; i++) {
      Record current = pvt(300.0 - 40.0 * i, 100.0 - 15.0 * i, 1.5 - 0.04 * i, 0.004 + 0.002 * i, 0.0);
      double expansion = OilMaterialBalance.eo(current.Bo, initial.Bo, initial.Rs, current.Rs, current.Bg);
      double np = 1.0e5 * i;
      double gp = (expectedOil * expansion - np * current.Bo) / current.Bg + np * current.Rs;
      balance.addObservation(current, np, gp, 0.0, 0.0, 0.0);
    }
    Assertions.assertEquals(expectedOil, balance.fitInitiallyOil().getOoip(), 1.0e-6);
  }

  /** Rejects an apparently perfect history with inseparable gas/oil expansion signals. */
  @Test
  void rejectsCollinearAndUnphysicalFits() {
    GeneralizedMaterialBalance balance = new GeneralizedMaterialBalance(pvt(300.0, 0.0, 1.0, 0.01, 0.0));
    balance.addObservation(pvt(200.0, 0.0, 1.1, 0.02, 0.0), 1.0e5, 1.0e6, 0.0, 0.0, 0.0);
    balance.addObservation(pvt(100.0, 0.0, 1.2, 0.03, 0.0), 2.0e5, 2.0e6, 0.0, 0.0, 0.0);
    Assertions.assertThrows(IllegalArgumentException.class, balance::fitOilAndGas);
    GeneralizedMaterialBalance negative = new GeneralizedMaterialBalance(INITIAL);
    Record current = pvt(200.0, 60.0, 1.4, 0.008, 0.00012);
    negative.addObservation(current, -1.0e6, -1.0e8, 0.0, 0.0, 0.0);
    negative.addObservation(current, -2.0e6, -2.0e8, 0.0, 0.0, 0.0);
    Assertions.assertThrows(IllegalArgumentException.class, negative::fitInitiallyOil);
  }

  /** Rejects positive best-fit volumes that cannot cover the reported component withdrawal. */
  @Test
  void rejectsExhaustedComponentInventory() {
    GeneralizedMaterialBalance balance = new GeneralizedMaterialBalance(pvt(300.0, 0.0, 1.0, 0.004, 0.0));
    balance.addObservation(pvt(200.0, 0.0, 1.0, 0.08, 0.0), 0.0, 5.0e8, 0.0, 0.0, 0.0);
    balance.addObservation(pvt(150.0, 0.0, 1.0, 0.008, 0.0), 0.0, 3.0e9, 0.0, 0.0, 0.0);
    Assertions.assertThrows(IllegalArgumentException.class, balance::fitInitiallyGas);
  }

  /** Rejects missing input, non-finite data, insufficient history and degenerate phase splits. */
  @Test
  void rejectsInvalidPvtAndHistory() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> new GeneralizedMaterialBalance(null));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new GeneralizedMaterialBalance(pvt(300.0, 100.0, 1.6, 0.004, 0.01)));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new GeneralizedMaterialBalance(pvt(Double.NaN, 100.0, 1.6, 0.004, 0.0)));
    GeneralizedMaterialBalance balance = new GeneralizedMaterialBalance(INITIAL);
    Assertions.assertThrows(IllegalArgumentException.class, balance::fitInitiallyGas);
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> balance.addObservation(INITIAL, Double.NaN, 0.0, 0.0, 0.0, 0.0));
    Assertions.assertThrows(IllegalArgumentException.class, () -> balance.fitKnownGasCap(-1.0));
    balance.addObservation(INITIAL, 0.0, 0.0, 0.0, 0.0, 0.0);
    balance.addObservation(INITIAL, 0.0, 0.0, 0.0, 0.0, 0.0);
    Assertions.assertThrows(IllegalArgumentException.class, balance::fitInitiallyGas);
  }
}
