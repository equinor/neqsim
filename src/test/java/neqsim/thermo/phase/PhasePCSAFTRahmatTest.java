package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import neqsim.thermo.component.ComponentPCSAFT;
import neqsim.thermo.system.SystemPCSAFT;

/**
 * Regression values at the physical pressure root. SaftDerivativeConsistencyTest and the
 * coldRootSatisfiesThermodynamicIdentities test independently qualify the corrected derivatives.
 */
public class PhasePCSAFTRahmatTest {
  static PhasePCSAFTRahmat p;
  static PhasePCSAFTRahmat p2;
  static SystemPCSAFT testSystem;

  @BeforeEach
  void setUp() {
    testSystem = new SystemPCSAFT(150.0, 10.0);
    testSystem.addComponent("methane", 100.0);
    testSystem.addComponent("n-hexane", 1000.0001);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(1);
    testSystem.init(0);
    testSystem.init(3);

    p = (PhasePCSAFTRahmat) testSystem.getPhase(0);
  }

  private PhasePCSAFTRahmat shifted(double temperature, double molarVolume) {
    PhasePCSAFTRahmat copy = p.clone();
    copy.setTemperature(temperature);
    copy.setMolarVolume(molarVolume);
    for (int i = 0; i < copy.getNumberOfComponents(); i++) {
      copy.getComponent(i).init(temperature, copy.getPressure(), copy.getNumberOfMolesInPhase(), 1.0, 1);
    }
    copy.volInit();
    return copy;
  }

  @Test
  void coldRootSatisfiesThermodynamicIdentities() {
    Assertions.assertTrue(p.getNSAFT() > 0 && p.getNSAFT() < 1);
    assertEquals(testSystem.getPressure(), p.calcPressure(), 1e-7);
    double dt = 0.01;
    PhasePCSAFTRahmat plus = shifted(p.getTemperature() + dt, p.getMolarVolume());
    PhasePCSAFTRahmat minus = shifted(p.getTemperature() - dt, p.getMolarVolume());
    assertEquals((plus.getF() - minus.getF()) / (2 * dt), p.dFdT(), Math.abs(p.dFdT()) * 1e-6);
    assertEquals((plus.dFdT() - minus.dFdT()) / (2 * dt), p.dFdTdT(), Math.abs(p.dFdTdT()) * 1e-6);
    assertEquals((plus.dFdV() - minus.dFdV()) / (2 * dt), p.dFdTdV(), Math.abs(p.dFdTdV()) * 1e-6);
    double dv = p.getMolarVolume() * 1e-5;
    plus = shifted(p.getTemperature(), p.getMolarVolume() + dv);
    minus = shifted(p.getTemperature(), p.getMolarVolume() - dv);
    assertEquals((plus.getF() - minus.getF()) / (2 * dv * p.getNumberOfMolesInPhase()), p.dFdV(),
        Math.abs(p.dFdV()) * 1e-6);
    assertEquals((plus.dFdV() - minus.dFdV()) / (2 * dv * p.getNumberOfMolesInPhase()), p.dFdVdV(),
        Math.abs(p.dFdVdV()) * 1e-6);
  }

  @Test
  void testAddcomponent() {
    p2 = new PhasePCSAFTRahmat();

    Assertions.assertEquals(0, p2.getNumberOfComponents());

    p2.addComponent("ethane", 0, 0, 0);
    Assertions.assertEquals(1, p2.getNumberOfComponents());

    p2.addComponent("methane", 0, 0, 1);
    Assertions.assertEquals(2, p2.getNumberOfComponents());
  }

  @Test
  @DisabledIfSystemProperty(named = "os.arch", matches = ".*aarch64.*")
  void testF_DISP1_SAFT() {
    double value = p.F_DISP1_SAFT();
    assertEquals(-29409.534870394025, value, 0.00029409534870394026);
  }

  @Test
  @DisabledIfSystemProperty(named = "os.arch", matches = ".*aarch64.*")
  void testF_DISP2_SAFT() {
    double value = p.F_DISP2_SAFT();
    assertEquals(-1496.9067973083284, value, 1.4969067973083285e-05);
  }

  @Test
  @DisabledIfSystemProperty(named = "os.arch", matches = ".*aarch64.*")
  void testF_HC_SAFT() {
    double value = p.F_HC_SAFT();
    assertEquals(10716.299737052395, value, 0.00010716299737052395);
  }

  @Test
  @DisabledIfSystemProperty(named = "os.arch", matches = ".*aarch64.*")
  void testCalcF1dispI1() {
    double value = p.calcF1dispI1();
    assertEquals(1.0396905487320574, value, 1.0396905487320574e-08);
  }

  @Test
  @DisabledIfSystemProperty(named = "os.arch", matches = ".*aarch64.*")
  void testCalcF1dispI1dN() {
    double value = p.calcF1dispI1dN();
    assertEquals(0.22767816841676414, value, 2.2767816841676413e-09);
  }

  @Test
  @DisabledIfSystemProperty(named = "os.arch", matches = ".*aarch64.*")
  void testCalcF1dispI1dNdN() {
    double value = p.calcF1dispI1dNdN();
    assertEquals(-3.7457414519089127, value, 3.745741451908913e-08);
  }

  @Test
  @DisabledIfSystemProperty(named = "os.arch", matches = ".*aarch64.*")
  void testCalcF1dispI1dNdNdN() {
    double value = p.calcF1dispI1dNdNdN();
    assertEquals(25.58197529720826, value, 2.558197529720826e-07);
  }

  @Test
  @DisabledIfSystemProperty(named = "os.arch", matches = ".*aarch64.*")
  void testCalcF1dispI1dm() {
    double value = p.calcF1dispI1dm();
    assertEquals(-0.003017762564942697, value, 3.017762564942697e-11);
  }

  @Test
  void testCalcF1dispSumTerm() {
    double value = p.calcF1dispSumTerm();
    assertEquals(7.022486947548597E-28, value);
  }

  @Test
  @DisabledIfSystemProperty(named = "os.arch", matches = ".*aarch64.*")
  void testCalcF2dispI2() {
    double value = p.calcF2dispI2();
    assertEquals(2.751316283823095, value, 2.751316283823095e-08);
  }

  @Test
  @DisabledIfSystemProperty(named = "os.arch", matches = ".*aarch64.*")
  void testCalcF2dispI2dN() {
    double value = p.calcF2dispI2dN();
    assertEquals(4.565684123478775, value, 4.565684123478775e-08);
  }

  @Test
  @DisabledIfSystemProperty(named = "os.arch", matches = ".*aarch64.*")
  void testCalcF2dispI2dNdN() {
    double value = p.calcF2dispI2dNdN();
    assertEquals(-161.4380320511848, value, 1.614380320511848e-06);
  }

  @Test
  @DisabledIfSystemProperty(named = "os.arch", matches = ".*aarch64.*")
  void testCalcF2dispI2dNdNdN() {
    double value = p.calcF2dispI2dNdNdN();
    assertEquals(-2994.0784863821027, value, 2.9940784863821028e-05);
  }

  @Test
  @DisabledIfSystemProperty(named = "os.arch", matches = ".*aarch64.*")
  void testCalcF2dispI2dm() {
    double value = p.calcF2dispI2dm();
    assertEquals(0.35812797615489245, value, 3.5812797615489247e-09);
  }

  @Test
  void testCalcF2dispSumTerm() {
    double value = p.calcF2dispSumTerm();
    assertEquals(1.0974418484311756E-27, value);
  }

  @Test
  @DisabledIfSystemProperty(named = "os.arch", matches = ".*aarch64.*")
  void testCalcF2dispZHC() {
    double value = p.calcF2dispZHC();
    assertEquals(0.008575218020305647, value, 8.575218020305647e-11);
  }

  @Test
  @DisabledIfSystemProperty(named = "os.arch", matches = ".*aarch64.*")
  void testCalcF2dispZHCdN() {
    double value = p.calcF2dispZHCdN();
    assertEquals(-0.08442240167059856, value, 8.442240167059856e-10);
  }

  @Test
  @DisabledIfSystemProperty(named = "os.arch", matches = ".*aarch64.*")
  void testCalcF2dispZHCdNdN() {
    double value = p.calcF2dispZHCdNdN();
    assertEquals(0.7476995110653688, value, 7.476995110653687e-09);
  }

  @Test
  @DisabledIfSystemProperty(named = "os.arch", matches = ".*aarch64.*")
  void testCalcF2dispZHCdNdNdN() {
    double value = p.calcF2dispZHCdNdNdN();
    assertEquals(-6.314753507217411, value, 6.314753507217411e-08);
  }

  @Test
  @DisabledIfSystemProperty(named = "os.arch", matches = ".*aarch64.*")
  void testCalcF2dispZHCdm() {
    double value = p.calcF2dispZHCdm();
    assertEquals(-0.00277595592922911, value, 2.77595592922911e-11);
  }

  @Test
  void testCalcdF1dispI1dT() {
    double value = p.calcdF1dispI1dT();
    assertEquals(-1.16726766358242E-5, value, 1.16726766358242e-13);
  }

  @Test
  void testCalcdF1dispSumTermdT() {
    double value = p.calcdF1dispSumTermdT();
    assertEquals(-4.6816579650323984E-30, value);
  }

  @Test
  @DisabledIfSystemProperty(named = "os.arch", matches = ".*aarch64.*")
  void testCalcdF2dispI2dT() {
    double value = p.calcdF2dispI2dT();
    assertEquals(-2.340749434400326E-4, value, 2.3407494344003262e-12);
  }

  @Test
  void testCalcdF2dispSumTermdT() {
    double value = p.calcdF2dispSumTermdT();
    assertEquals(-1.4632557979082342E-29, value);
  }

  @Test
  @DisabledIfSystemProperty(named = "os.arch", matches = ".*aarch64.*")
  void testCalcdF2dispZHCdT() {
    double value = p.calcdF2dispZHCdT();
    assertEquals(4.328194496526025E-6, value, 4.3281944965260254e-14);
  }

  @Test
  @DisabledIfSystemProperty(named = "os.arch", matches = ".*aarch64.*")
  void testdFdTdV() {
    double value = p.dFdTdV();
    assertEquals(-0.012707708611563808, value, 1.270770861156381e-10);
  }

  @Test
  @DisabledIfSystemProperty(named = "os.arch", matches = ".*aarch64.*")
  void testdFdTdT() {
    double value = p.dFdTdT();
    assertEquals(-3.054562369032611, value, 3.054562369032611e-08);
  }

  @Disabled("TODO: not working per 19.06.2060")
  @Test
  @Tag("failing")
  void testdFdT() {
    double value = p.dFdT();
    assertEquals(43.43437720791384, value, 1e-8);
  }

  @Disabled("TODO: not working per 19.06.2060")
  @Test
  @Tag("failing")
  void testComponentdFdNdT() {
    ComponentPCSAFT comp = (ComponentPCSAFT) p.getComponent(0);
    double value = comp.dFdNdT(p, p.getNumberOfComponents(), p.getTemperature(), p.getPressure());
    assertEquals(0.007581918136656541, value, 1e-12);
  }

  @Test
  void testCalcdSAFT() {
    double value = p.calcdSAFT();
    assertEquals(1.5637585192262192E-28, value);
  }

  @Test
  void testCalcdmeanSAFT() {
    double value = p.calcdmeanSAFT();
    assertEquals(3.7908367828047096E-10, value);
  }

  @Test
  void testCalcmSAFT() {
    double value = p.calcmSAFT();
    assertEquals(2.8705454715504115, value);
  }

  @Test
  void testCalcmdSAFT() {
    double value = p.calcmdSAFT();
    assertEquals(1.5637585192262194E-28, value);
  }

  @Test
  void testCalcmmin1SAFT() {
    double value = p.calcmmin1SAFT();
    assertEquals(1.8705454715504115, value);
  }

  @Test
  void testDF_DISP1_SAFTdT() {
  }

  @Test
  void testDF_DISP1_SAFTdV() {
  }

  @Test
  void testDF_DISP1_SAFTdVdV() {
  }

  @Test
  void testDF_DISP1_SAFTdVdVdV() {
  }

  @Test
  void testDF_DISP2_SAFTdT() {
  }

  @Test
  void testDF_DISP2_SAFTdV() {
  }

  @Test
  void testDF_DISP2_SAFTdVdV() {
  }

  @Test
  void testDF_DISP2_SAFTdVdVdV() {
  }

  @Test
  void testDF_HC_SAFTdT() {
  }

  @Test
  void testDF_HC_SAFTdV() {
  }

  @Test
  void testDF_HC_SAFTdVdV() {
  }

  @Test
  void testDF_HC_SAFTdVdVdV() {
  }

  @Test
  void testDFdT() {
  }

  @Test
  void testDFdV() {
  }

  @Test
  void testDFdVdV() {
  }

  @Test
  void testDFdVdVdV() {
  }

  @Test
  void testGetAHSSAFT() {
  }

  @Test
  void testGetDSAFT() {
  }

  @Test
  void testGetDgHSSAFTdN() {
  }

  @Test
  void testGetDmeanSAFT() {
  }

  @Test
  void testGetDnSAFTdV() {
  }

  @Test
  void testGetF() {
  }

  @Test
  void testGetF1dispI1() {
  }

  @Test
  void testGetF1dispSumTerm() {
  }

  @Test
  void testGetF1dispVolTerm() {
  }

  @Test
  void testGetF2dispI2() {
  }

  @Test
  void testGetF2dispSumTerm() {
  }

  @Test
  void testGetF2dispZHC() {
  }

  @Test
  void testGetF2dispZHCdN() {
  }

  @Test
  void testGetF2dispZHCdm() {
  }

  @Test
  void testGetGhsSAFT() {
  }

  @Test
  void testGetMmin1SAFT() {
  }

  @Test
  void testGetNSAFT() {
  }

  @Test
  void testGetNmSAFT() {
  }

  @Test
  void testGetVolumeSAFT() {
  }

  @Test
  void testGetaSAFT() {
  }

  @Test
  void testGetaSAFTdm() {
  }

  @Test
  void testGetdDSAFTdT() {
  }

  @Test
  void testGetmSAFT() {
  }

  @Test
  void testGetmdSAFT() {
  }

  @Test
  void testInit() {
    p.init();
  }

  @Test
  void testMolarVolume() {
  }

  @Test
  void testSetAHSSAFT() {
  }

  @Test
  void testSetDSAFT() {
  }

  @Test
  void testSetDgHSSAFTdN() {
  }

  @Test
  void testSetDmeanSAFT() {
  }

  @Test
  void testSetDnSAFTdV() {
  }

  @Test
  void testSetF1dispVolTerm() {
  }

  @Test
  void testSetF2dispI2() {
  }

  @Test
  void testSetF2dispSumTerm() {
  }

  @Test
  void testSetF2dispZHC() {
  }

  @Test
  void testSetF2dispZHCdm() {
  }

  @Test
  void testSetGhsSAFT() {
  }

  @Test
  void testSetMmin1SAFT() {
  }

  @Test
  void testSetNSAFT() {
  }

  @Test
  void testSetNmSAFT() {
  }

  @Test
  void testSetVolumeSAFT() {
  }

  @Test
  void testSetmSAFT() {
  }

  @Test
  void testSetmdSAFT() {
  }
}
