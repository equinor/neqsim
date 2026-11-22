package neqsim.process.equipment.expander;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests of the nozzle swallowing capacity, JT bypass and shaft closure extensions of {@link TurboExpanderCompressor}.
 *
 * <p>
 * The reference values were produced with an independent reference implementation of the same model (train A) on the
 * same SRK fluid, inlet state (58.02 bara, -14.75 C), outlet pressure (44.66 bara) and recompressor suction (42.84
 * bara, 17.04 C, 125 kg/s).
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class TurboExpanderCapacityTest {
  private static final double K_CAP = 0.008338212556881344;
  private static final double N_IGV = 0.6731895724062562;

  /**
   * Build the lean reference dry gas.
   *
   * @param name stream name
   * @param tC temperature [C]
   * @param pBara pressure [bara]
   * @param kgPerS mass flow [kg/s]
   * @return the running stream
   */
  private static Stream gasStream(String name, double tC, double pBara, double kgPerS) {
    SystemInterface gas = new SystemSrkEos(273.15 + tC, pBara);
    gas.addComponent("nitrogen", 0.0085);
    gas.addComponent("CO2", 0.0157);
    gas.addComponent("methane", 0.8700);
    gas.addComponent("ethane", 0.0780);
    gas.addComponent("propane", 0.0210);
    gas.addComponent("i-butane", 0.0030);
    gas.addComponent("n-butane", 0.0038);
    gas.setMixingRule(2);
    Stream s = new Stream(name, gas);
    s.setFlowRate(kgPerS, "kg/sec");
    s.setTemperature(tC, "C");
    s.setPressure(pBara, "bara");
    s.run();
    return s;
  }

  /**
   * Build the calibrated reference TEX train A with the native maps.
   *
   * @param name unit name
   * @param feedKgPerS expander feed flow [kg/s]
   * @return the configured, not yet run, unit
   */
  private static TurboExpanderCompressor referenceCase(String name, double feedKgPerS) {
    TurboExpanderCompressor tex = new TurboExpanderCompressor(name,
        gasStream(name + " feed", -14.75, 58.02, feedKgPerS));
    tex.setCompressorFeedStream(gasStream(name + " rc", 17.04, 42.84, 125.0));
    tex.setExpanderOutPressure(44.66);
    tex.setImpellerDiameter(0.424);
    tex.setDesignSpeed(6850.0);
    tex.setBearingLossPower(0.0);
    tex.setExpanderEfficiencyParabola(0.7382732140491723, 0.5411696256210843, 7.878218532391427, 0.3);
    tex.setCompressorHeadPolynomial(-398.26721079260204, -230.31926246373416, 32.04846454157856);
    tex.setCompressorEfficiencyPolynomial(-199.5741236890545, 8.750879074203963, 0.7700219417378728, 1.0, 0.3, 0.85);
    tex.setShaftClosureFactor(1.0666599729165251);
    tex.setSpeedLimits(3000.0, 7200.0);
    tex.setNozzleCapacityModel(K_CAP, N_IGV);
    return tex;
  }

  /**
   * Build the original legacy-test feed gas stream.
   *
   * @param name stream name
   * @param tC temperature [C]
   * @param pBara pressure [bara]
   * @param kgPerHr mass flow [kg/hr]
   * @return the running stream
   */
  private static Stream legacyGasStream(String name, double tC, double pBara, double kgPerHr) {
    SystemInterface gas = new SystemSrkEos(273.15 + 42.0, 10.0);
    gas.addComponent("nitrogen", 0.006);
    gas.addComponent("CO2", 0.014);
    gas.addComponent("methane", 0.862);
    gas.addComponent("ethane", 0.08);
    gas.addComponent("propane", 0.03);
    gas.addComponent("i-butane", 0.0024);
    gas.addComponent("n-butane", 0.004);
    gas.addComponent("n-hexane", 0.0015);
    gas.setMixingRule(2);
    gas.init(0);
    Stream s = new Stream(name, gas);
    s.setFlowRate(kgPerHr, "kg/hr");
    s.setTemperature(tC, "C");
    s.setPressure(pBara, "bara");
    s.run();
    return s;
  }

  /** Legacy configuration without any of the new options is unchanged and the new KPIs stay inert. */
  @Test
  void legacyConfigurationIsBackwardCompatible() {
    TurboExpanderCompressor tex = new TurboExpanderCompressor("legacy", legacyGasStream("f", -23.0, 60.95, 456000.0));
    tex.setCompressorFeedStream(legacyGasStream("fc", 17.0, 42.0, 423448.0));
    tex.setUCcurve(
        new double[] {0.9964751359624449, 0.7590835113213541, 0.984295619176559, 0.8827799803397821, 0.9552460269880922,
            1.0},
        new double[] {0.984090909090909, 0.796590909090909, 0.9931818181818183, 0.9363636363636364, 0.9943181818181818,
            1.0});
    tex.setQNEfficiencycurve(new double[] {0.5, 0.7, 0.85, 1.0, 1.2, 1.4, 1.6},
        new double[] {0.88, 0.91, 0.95, 1.0, 0.97, 0.85, 0.6});
    tex.setQNHeadcurve(new double[] {0.5, 0.8, 1.0, 1.2, 1.4, 1.6}, new double[] {1.1, 1.05, 1.0, 0.9, 0.7, 0.4});
    tex.setImpellerDiameter(0.424);
    tex.setDesignSpeed(6850.0);
    tex.setExpanderDesignIsentropicEfficiency(0.88);
    tex.setDesignUC(0.7);
    tex.setDesignQn(0.03328);
    tex.setExpanderOutPressure(42.0);
    tex.setCompressorDesignPolytropicEfficiency(0.81);
    tex.setCompressorDesignPolytropicHead(20.47);
    tex.setMaximumIGVArea(1.637e4);
    tex.run();

    Assertions.assertEquals(6614.447912, tex.getSpeed(), 0.5);
    Assertions.assertEquals(3001861.63337, tex.getPowerExpander(), 500);
    Assertions.assertFalse(tex.isNozzleCapacityEnabled());
    Assertions.assertFalse(tex.isJtBypassEnabled());
    Assertions.assertEquals(0.0, tex.getNozzleCapacity(), 0.0);
    Assertions.assertEquals(0.0, tex.getJtBypassFraction(), 0.0);
    Assertions.assertFalse(tex.isCapacityLimited());
    Assertions.assertEquals("ok", tex.getSpeedStatus());
    Assertions.assertEquals(1.0, tex.getShaftClosureFactor(), 0.0);
    Assertions.assertEquals(tex.getPowerCompressor() + tex.getBearingLoss(), tex.getPowerExpander(),
        1e-3 * tex.getPowerExpander());
    Assertions.assertTrue(tex.getSpeedRatioUC0() > 0.3 && tex.getSpeedRatioUC0() < 1.0);
    Assertions.assertEquals(tex.getSpeed() / 9000.0, tex.getSpeedUtilisation(), 1e-12);
    Assertions.assertEquals(-40.8000256, tex.getExpanderOutletStream().getTemperature("C"), 0.1);
  }

  /** Capacity scales with sqrt(rho P) and with IGV^n. */
  @Test
  void nozzleCapacityScalesWithDensityPressureAndIgv() {
    TurboExpanderCompressor tex = new TurboExpanderCompressor("cap", gasStream("f", -14.75, 58.02, 100.0));
    tex.setNozzleCapacityModel(K_CAP, N_IGV);
    double base = tex.calcNozzleCapacity(1.0, 60.0, 50.0);
    Assertions.assertEquals(2.0, tex.calcNozzleCapacity(1.0, 60.0, 200.0) / base, 1e-12);
    Assertions.assertEquals(2.0, tex.calcNozzleCapacity(1.0, 240.0, 50.0) / base, 1e-12);
    Assertions.assertEquals(K_CAP * Math.sqrt(60.0 * 50.0e5), base, 1e-9);
    Assertions.assertEquals(Math.pow(0.5, N_IGV), tex.calcNozzleCapacity(0.5, 60.0, 50.0) / base, 1e-12);
    double m = 0.6 * base;
    double igv = tex.calcRequiredIgvOpening(m, 60.0, 50.0);
    Assertions.assertEquals(m, tex.calcNozzleCapacity(igv, 60.0, 50.0), 1e-9);
    Assertions.assertTrue(tex.calcRequiredIgvOpening(1.2 * base, 60.0, 50.0) > 1.0);

    tex.setNozzleCapacityScale(1.088);
    Assertions.assertEquals(1.088, tex.calcNozzleCapacity(1.0, 60.0, 50.0) / base, 1e-12);

    TurboExpanderCompressor tab = new TurboExpanderCompressor("tab", gasStream("f2", -14.75, 58.02, 100.0));
    tab.setNozzleCapacityModel(K_CAP, new double[] {1.0, 0.5, 0.0}, new double[] {1.0, 0.4, 0.0});
    Assertions.assertEquals(0.4, tab.calcNozzleFlowFunction(0.5), 1e-12);
    double mt = 0.7 * tab.calcNozzleCapacity(1.0, 60.0, 50.0);
    double igvTab = tab.calcRequiredIgvOpening(mt, 60.0, 50.0);
    Assertions.assertEquals(mt, tab.calcNozzleCapacity(igvTab, 60.0, 50.0), 1e-6);
  }

  /** Without a bypass an over-capacity flow is flagged, with a bypass the surplus is throttled and mixed. */
  @Test
  void capacityLimitedFlowGoesThroughJtBypass() {
    TurboExpanderCompressor tex = referenceCase("limited", 185.8178146);
    tex.setIgvControlMode(true);
    tex.setIGVopening(1.0);
    tex.run();
    Assertions.assertTrue(tex.isCapacityLimited());
    Assertions.assertTrue(tex.getNozzleCapacityUtilisation() > 1.0);
    Assertions.assertEquals(0.0, tex.getJtBypassFlow(), 0.0);
    double wheelOnlyT = tex.getExpanderOutletStream().getTemperature("C");

    TurboExpanderCompressor byp = referenceCase("bypass", 185.8178146);
    byp.setIgvControlMode(true);
    byp.setIGVopening(1.0);
    byp.setJtBypassEnabled(true);
    byp.run();
    double cap = byp.getNozzleCapacity();
    Assertions.assertTrue(byp.isCapacityLimited());
    Assertions.assertEquals(cap, byp.getNozzleCapacityFullOpen(), 1e-9);
    Assertions.assertEquals(185.8178146, byp.getExpanderMassFlow() + byp.getJtBypassFlow(), 1e-9);
    Assertions.assertEquals(cap, byp.getExpanderMassFlow(), 1e-9);
    Assertions.assertEquals((185.8178146 - cap) / 185.8178146, byp.getJtBypassFraction(), 1e-12);
    Assertions.assertEquals(185.8178146, byp.getExpanderOutletStream().getFlowRate("kg/sec"), 1e-6);
    Assertions.assertEquals(0.0, byp.getMassBalance("kg/sec"), 1e-6);
    // JT gas is warmer than wheel exhaust, so the mix is warmer than the wheel outlet
    Assertions
        .assertTrue(byp.getExpanderOutletStream().getTemperature("C") > byp.getExpanderWheelOutletTemperature("C"));
    Assertions.assertTrue(byp.getExpanderOutletStream().getTemperature("C") < 0.0);
    Assertions.assertTrue(byp.getExpanderWheelOutletTemperature("C") < wheelOnlyT + 5.0);
    Assertions.assertTrue(byp.getPowerExpander() < tex.getPowerExpander());
  }

  /** The shaft power balance closes with closure factor and bearing loss. */
  @Test
  void shaftPowerBalanceCloses() {
    TurboExpanderCompressor tex = referenceCase("shaft", 108.0);
    tex.setBearingLossPower(20.0e3);
    tex.run();
    double residual = tex.getShaftClosureFactor() * tex.getPowerExpander() - tex.getPowerCompressor()
        - tex.getBearingLoss();
    Assertions.assertEquals(0.0, residual, 1e-4 * tex.getPowerExpander());
    Assertions.assertEquals(residual, tex.getShaftPowerResidual(), 1.0);
    Assertions.assertEquals(20.0e3 * Math.pow(tex.getSpeed() / 6850.0, 2.0), tex.getBearingLoss(), 1.0);
    Assertions.assertEquals("ok", tex.getSpeedStatus());
    Assertions.assertTrue(tex.getSpeedUtilisation() > 0.5 && tex.getSpeedUtilisation() < 1.0);

    TurboExpanderCompressor ideal = referenceCase("shaft ideal", 108.0);
    ideal.setShaftClosureFactor(1.0);
    ideal.run();
    // a larger closure factor needs less expander power, so a lower speed
    Assertions.assertTrue(tex.getSpeed() != ideal.getSpeed());
  }

  /** The speed solver flags the upper speed limit. */
  @Test
  void speedLimitIsFlagged() {
    TurboExpanderCompressor tex = referenceCase("fast", 108.0);
    tex.setSpeedLimits(3000.0, 4000.0);
    tex.run();
    Assertions.assertEquals("speed_max", tex.getSpeedStatus());
    Assertions.assertEquals(4000.0, tex.getSpeed(), 1e-9);
    Assertions.assertEquals(1.0, tex.getSpeedUtilisation(), 1e-9);
  }

  /** Cross-check against the independent reference TEX model (train A, same gas and inlet state). */
  @Test
  void matchesCalibratedReference() {
    // Reference: h_s 22.5876 kJ/kg, rho_in 64.7225 kg/m3, full-open capacity 161.5807 kg/s
    TurboExpanderCompressor auto = referenceCase("auto", 108.0);
    auto.run();
    Assertions.assertEquals(161.58070836, auto.getNozzleCapacityFullOpen(), 0.01 * 161.58070836);
    Assertions.assertEquals(0.54966031, auto.getRequiredIgvOpening(), 0.01 * 0.54966031);
    Assertions.assertEquals(0.54966031, auto.getIGVopening(), 0.01 * 0.54966031);
    Assertions.assertFalse(auto.isCapacityLimited());
    Assertions.assertEquals(-30.12829, auto.getExpanderOutletStream().getTemperature("C"), 0.01 * 30.12829);
    Assertions.assertEquals(0.73768, auto.getExpanderIsentropicEfficiency(), 0.01 * 0.73768);
    Assertions.assertEquals(5098.01, auto.getSpeed(), 0.01 * 5098.01);
    Assertions.assertEquals(0.53249, auto.getSpeedRatioUC0(), 0.01 * 0.53249);
    Assertions.assertEquals(1799.54e3, auto.getPowerExpander(), 0.01 * 1799.54e3);

    TurboExpanderCompressor over = referenceCase("over", 185.81781461838713);
    over.setIgvControlMode(true);
    over.setIGVopening(1.0);
    over.setJtBypassEnabled(true);
    over.run();
    Assertions.assertTrue(over.isCapacityLimited());
    Assertions.assertEquals(161.58070836, over.getNozzleCapacity(), 0.01 * 161.58070836);
    Assertions.assertEquals(24.237106, over.getJtBypassFlow(), 0.05 * 24.237106);
    Assertions.assertEquals(-29.72246, over.getExpanderWheelOutletTemperature("C"), 0.01 * 29.72246);
    Assertions.assertEquals(-28.99840, over.getExpanderOutletStream().getTemperature("C"), 0.01 * 28.99840);
    Assertions.assertEquals(0.68725, over.getExpanderIsentropicEfficiency(), 0.01 * 0.68725);
    Assertions.assertEquals(5951.55, over.getSpeed(), 0.01 * 5951.55);
  }

  /** Two trains with different maps and the JSON report. */
  @Test
  void perTrainMapsAndJsonReport() {
    TurboExpanderCompressor a = referenceCase("TXA", 108.0);
    TurboExpanderCompressor b = referenceCase("TXB", 108.0);
    b.copyPerformanceMapFrom(a);
    b.setNozzleCapacityScale(1.0879352566990097);
    b.setShaftClosureFactor(1.1421373391996092);
    b.setCompressorEfficiencyPolynomial(-199.5741236890545, 8.750879074203963, 0.7700219417378728, 0.8425743697232664,
        0.3, 0.85);
    a.run();
    b.run();
    Assertions.assertEquals(1.0879352566990097, b.getNozzleCapacityFullOpen() / a.getNozzleCapacityFullOpen(), 1e-9);
    Assertions.assertTrue(b.getRequiredIgvOpening() < a.getRequiredIgvOpening());
    Assertions.assertTrue(Math.abs(a.getSpeed() - b.getSpeed()) > 1.0);

    JsonObject json = JsonParser.parseString(a.toJson()).getAsJsonObject();
    Assertions.assertTrue(json.has("nozzleCapacityUtilisation"));
    Assertions.assertTrue(json.has("jtBypassFraction"));
    Assertions.assertTrue(json.has("speedRatioUC0"));
    Assertions.assertTrue(json.has("speedUtilisation"));
    Assertions.assertTrue(json.has("shaftPowerResidual"));
    Assertions.assertTrue(json.has("expanderDischargeTemperature"));
    Assertions.assertEquals(a.getRequiredIgvOpening(), json.get("igvOpening").getAsDouble(), 1e-9);
    Assertions.assertEquals(a.getSpeedUtilisation(), json.get("speedUtilisation").getAsDouble(), 1e-9);
  }
}
