package neqsim.process.fielddevelopment.integrated;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for the real-gas material balance, its history match, the Rawlins-Schellhardt deliverability fit and the
 * supply/capacity balance used in reservoir-to-export value-chain studies.
 *
 * @author NeqSim
 * @version 1.0
 */
class GasValueChainSurrogatesTest {

  private static final double T_RES = 343.15;
  private static final GasZFactor Z = new DranchukAbouKassemZ(196.8, 45.9);

  /**
   * The DAK correlation must be close to ideal at low pressure and show the lean-gas dip at reservoir pressure.
   */
  @Test
  void testDakZFactorRange() {
    assertEquals(1.0, Z.z(1.0, T_RES), 0.01);
    double zRes = Z.z(150.0, T_RES);
    assertTrue(zRes > 0.84 && zRes < 0.93, "Z at 150 bara should be about 0.88, was " + zRes);
    assertTrue(Z.z(60.0, T_RES) > zRes, "Z must rise as the reservoir depletes in this range");
  }

  /**
   * A real-gas balance must hold p/z linear in recovery and decline monotonically in pressure.
   */
  @Test
  void testRealGasDriveFollowsPzLine() {
    double pzInitial = 171.6;
    double giip = 1000.0e9;
    RealGasMaterialBalanceDrive drive = new RealGasMaterialBalanceDrive(pzInitial, giip, T_RES, Z, 0.0);
    double previous = drive.getReservoirPressure();
    assertEquals(pzInitial, previous / Z.z(previous, T_RES), 1.0e-6);
    for (int i = 0; i < 6; i++) {
      drive.produce(100.0e9, 365.0);
      double p = drive.getReservoirPressure();
      assertTrue(p < previous, "pressure must fall with production");
      previous = p;
    }
    assertEquals(pzInitial * 0.4, drive.getPOverZ(), 1.0e-9);
    assertEquals(drive.getPOverZ(), previous / Z.z(previous, T_RES), 1.0e-6);
  }

  /**
   * Water influx must slow the pressure decline.
   */
  @Test
  void testSupportSlowsDecline() {
    RealGasMaterialBalanceDrive depletion = new RealGasMaterialBalanceDrive(170.0, 1000.0e9, T_RES, Z, 0.0);
    RealGasMaterialBalanceDrive supported = new RealGasMaterialBalanceDrive(170.0, 1000.0e9, T_RES, Z, 0.4);
    depletion.produce(400.0e9, 1000.0);
    supported.produce(400.0e9, 1000.0);
    assertTrue(supported.getReservoirPressure() > depletion.getReservoirPressure());
  }

  /**
   * Fitting synthetic p/z points that include a bad shut-in reading must recover the GIIP and reject the outlier.
   */
  @Test
  void testPzFitRecoversGiipAndRejectsOutlier() {
    double giip = 1090.0e9;
    double pzInitial = 171.6;
    int n = 14;
    double[] gp = new double[n];
    double[] pz = new double[n];
    for (int i = 0; i < n; i++) {
      gp[i] = giip * 0.6 * i / (n - 1);
      pz[i] = pzInitial * (1.0 - gp[i] / giip) + (i % 2 == 0 ? 0.6 : -0.6);
    }
    pz[5] = 40.0;
    MaterialBalanceHistoryMatch.PzFit fit = MaterialBalanceHistoryMatch.fitPzLine(gp, pz, 2.0);
    assertEquals(giip, fit.getGiip(), 0.03 * giip);
    assertEquals(pzInitial, fit.getInitialPOverZ(), 2.0);
    assertTrue(!fit.getKept()[5], "the 40 bar reading is an outlier");
    assertEquals(n - 1, fit.getKeptCount());
  }

  /**
   * A shut-in wellhead pressure maps to a higher bottom-hole pressure through the gas column.
   */
  @Test
  void testStaticColumnRaisesPressure() {
    double pbh = MaterialBalanceHistoryMatch.staticBottomholePressure(100.0, 0.0173, 1400.0, 315.0, Z);
    assertTrue(pbh > 108.0 && pbh < 118.0, "bottom-hole pressure should be about 10 % above wellhead, was " + pbh);
  }

  /**
   * The Rawlins-Schellhardt fit must recover the generating law and build a monotone deliverability curve.
   */
  @Test
  void testRawlinsSchellhardtFitAndCurve() {
    RawlinsSchellhardtFit truth = new RawlinsSchellhardtFit(8.0e3, 0.72);
    double[] pr = new double[40];
    double[] whp = new double[40];
    double[] q = new double[40];
    for (int i = 0; i < 40; i++) {
      pr[i] = 55.0 + i;
      whp[i] = 36.0 + 0.3 * (i % 7);
      q[i] = truth.rate(pr[i], whp[i]);
    }
    RawlinsSchellhardtFit fit = RawlinsSchellhardtFit.fit(pr, whp, q);
    assertEquals(0.72, fit.getExponent(), 1.0e-6);
    assertEquals(8.0e3, fit.getCoefficient(), 1.0);
    assertEquals(truth.wellheadPressureFor(80.0, 1.5e6), fit.wellheadPressureFor(80.0, 1.5e6), 1.0e-3);

    WellDeliverabilityCurve curve = WellDeliverabilityCurve.fromRawlinsSchellhardt(fit, 80.0, 10.0, 25);
    assertEquals(80.0, curve.getShutInPressure(), 1.0e-9);
    assertTrue(curve.rateAt(30.0) > curve.rateAt(50.0));
    assertEquals(fit.rate(80.0, 39.0), curve.rateAt(40.01325), 0.02 * fit.rate(80.0, 39.0));
  }

  /**
   * The balance must find the crossing of falling supply and rising facility capacity, and report the binding side at
   * the window edges.
   */
  @Test
  void testSupplyCapacityBalance() {
    SupplyCapacityBalance.PressureCapacityCurve capacity = new SupplyCapacityBalance.PressureCapacityCurve(
        new double[] {20.0, 30.0, 40.0}, new double[] {40.0, 60.0, 80.0});
    SupplyCapacityBalance.Result mid = SupplyCapacityBalance.maximiseRate(p -> 100.0 - p, capacity, 20.0, 40.0);
    assertEquals(100.0 / 3.0, mid.getPressure(), 1.0e-6);
    assertEquals(200.0 / 3.0, mid.getRate(), 1.0e-6);

    SupplyCapacityBalance.Result supplyLimited = SupplyCapacityBalance.maximiseRate(p -> 30.0 - 0.1 * p, capacity, 20.0,
        40.0);
    assertEquals(SupplyCapacityBalance.Binding.SUPPLY, supplyLimited.getBinding());
    assertEquals(28.0, supplyLimited.getRate(), 1.0e-9);

    SupplyCapacityBalance.Result facilityLimited = SupplyCapacityBalance.maximiseRate(p -> 200.0, capacity, 20.0, 40.0);
    assertEquals(SupplyCapacityBalance.Binding.FACILITY, facilityLimited.getBinding());
    assertEquals(80.0, facilityLimited.getRate(), 1.0e-9);
  }
}
