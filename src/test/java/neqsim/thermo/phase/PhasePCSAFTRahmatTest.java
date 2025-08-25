package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import neqsim.thermo.system.SystemPCSAFT;
import neqsim.thermo.component.ComponentPCSAFT;

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
    assertEquals(-2656.5606478696354, value);
  }

  @Test
  @DisabledIfSystemProperty(named = "os.arch", matches = ".*aarch64.*")
  void testF_DISP2_SAFT() {
    double value = p.F_DISP2_SAFT();
    assertEquals(-1929.2979666587207, value);
  }

  @Test
  @DisabledIfSystemProperty(named = "os.arch", matches = ".*aarch64.*")
  void testF_HC_SAFT() {
    double value = p.F_HC_SAFT();
    assertEquals(501.428925899878, value);
  }

  @Test
  @DisabledIfSystemProperty(named = "os.arch", matches = ".*aarch64.*")
  void testCalcF1dispI1() {
    double value = p.calcF1dispI1();
    assertEquals(0.7447173911719432, value);
  }

  @Test
  @DisabledIfSystemProperty(named = "os.arch", matches = ".*aarch64.*")
  void testCalcF1dispI1dN() {
    double value = p.calcF1dispI1dN();
    assertEquals(0.8885712115632445, value);
  }

  @Test
  @DisabledIfSystemProperty(named = "os.arch", matches = ".*aarch64.*")
  void testCalcF1dispI1dNdN() {
    double value = p.calcF1dispI1dNdN();
    assertEquals(-0.3783996289387171, value);
  }

  @Test
  @DisabledIfSystemProperty(named = "os.arch", matches = ".*aarch64.*")
  void testCalcF1dispI1dNdNdN() {
    double value = p.calcF1dispI1dNdNdN();
    assertEquals(-19.504810659834753, value);
  }

  @Test
  @DisabledIfSystemProperty(named = "os.arch", matches = ".*aarch64.*")
  void testCalcF1dispI1dm() {
    double value = p.calcF1dispI1dm();
    assertEquals(-0.04871346995167202, value);
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
    assertEquals(0.5114103946892024, value);
  }

  @Test
  @DisabledIfSystemProperty(named = "os.arch", matches = ".*aarch64.*")
  void testCalcF2dispI2dN() {
    double value = p.calcF2dispI2dN();
    assertEquals(2.075396158614915, value);
  }

  @Test
  @DisabledIfSystemProperty(named = "os.arch", matches = ".*aarch64.*")
  void testCalcF2dispI2dNdN() {
    double value = p.calcF2dispI2dNdN();
    assertEquals(-10.085652314796853, value);
  }

  @Test
  @DisabledIfSystemProperty(named = "os.arch", matches = ".*aarch64.*")
  void testCalcF2dispI2dNdNdN() {
    double value = p.calcF2dispI2dNdNdN();
    assertEquals(53.904528812197945, value);
  }

  @Test
  @DisabledIfSystemProperty(named = "os.arch", matches = ".*aarch64.*")
  void testCalcF2dispI2dm() {
    double value = p.calcF2dispI2dm();
    assertEquals(-0.05282097926626965, value);
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
    assertEquals(0.47149436306641834, value);
  }

  @Test
  @DisabledIfSystemProperty(named = "os.arch", matches = ".*aarch64.*")
  void testCalcF2dispZHCdN() {
    double value = p.calcF2dispZHCdN();
    assertEquals(-5.447711889103666, value);
  }

  @Test
  @DisabledIfSystemProperty(named = "os.arch", matches = ".*aarch64.*")
  void testCalcF2dispZHCdNdN() {
    double value = p.calcF2dispZHCdNdN();
    assertEquals(75.24049982033125, value);
  }

  @Test
  @DisabledIfSystemProperty(named = "os.arch", matches = ".*aarch64.*")
  void testCalcF2dispZHCdNdNdN() {
    double value = p.calcF2dispZHCdNdNdN();
    assertEquals(279935.2725213402, value, 0.001);
  }

  @Test
  @DisabledIfSystemProperty(named = "os.arch", matches = ".*aarch64.*")
  void testCalcF2dispZHCdm() {
    double value = p.calcF2dispZHCdm();
    assertEquals(-0.06098259714, value, 0.001);
  }

  @Test
  void testCalcdF1dispI1dT() {
    double value = p.calcdF1dispI1dT();
    assertEquals(-2.467132676347459E-24, value, 1e-20);
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
    assertEquals(-5.762371785911064E-24, value);
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
    assertEquals(1.51244510048084E-23, value);
  }

  @Test
  @DisabledIfSystemProperty(named = "os.arch", matches = ".*aarch64.*")
  void testdFdTdV() {
    double value = p.dFdTdV();
    assertEquals(-3.672119679672745E-4, value, 1e-12);
  }

  @Test
  @DisabledIfSystemProperty(named = "os.arch", matches = ".*aarch64.*")
  void testdFdTdT() {
    double value = p.dFdTdT();
    assertEquals(-0.7424036008192018, value, 1e-9);
  }

  @Test
  void testdFdT() {
    double value = p.dFdT();
    assertEquals(43.43437720791384, value, 1e-8);
  }

  @Test
  void testComponentdFdNdT() {
    ComponentPCSAFT comp = (ComponentPCSAFT) p.getComponent(0);
    double value =
        comp.dFdNdT(p, p.getNumberOfComponents(), p.getTemperature(), p.getPressure());
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
  void testDF_DISP1_SAFTdT() {}

  @Test
  void testDF_DISP1_SAFTdV() {}

  @Test
  void testDF_DISP1_SAFTdVdV() {}

  @Test
  void testDF_DISP1_SAFTdVdVdV() {}

  @Test
  void testDF_DISP2_SAFTdT() {}

  @Test
  void testDF_DISP2_SAFTdV() {}

  @Test
  void testDF_DISP2_SAFTdVdV() {}

  @Test
  void testDF_DISP2_SAFTdVdVdV() {}

  @Test
  void testDF_HC_SAFTdT() {}

  @Test
  void testDF_HC_SAFTdV() {}

  @Test
  void testDF_HC_SAFTdVdV() {}

  @Test
  void testDF_HC_SAFTdVdVdV() {}

  @Test
  void testDFdT() {}

  @Test
  void testDFdV() {}

  @Test
  void testDFdVdV() {}

  @Test
  void testDFdVdVdV() {}

  @Test
  void testGetAHSSAFT() {}

  @Test
  void testGetDSAFT() {}

  @Test
  void testGetDgHSSAFTdN() {}

  @Test
  void testGetDmeanSAFT() {}

  @Test
  void testGetDnSAFTdV() {}

  @Test
  void testGetF() {}

  @Test
  void testGetF1dispI1() {}

  @Test
  void testGetF1dispSumTerm() {}

  @Test
  void testGetF1dispVolTerm() {}

  @Test
  void testGetF2dispI2() {}

  @Test
  void testGetF2dispSumTerm() {}

  @Test
  void testGetF2dispZHC() {}

  @Test
  void testGetF2dispZHCdN() {}

  @Test
  void testGetF2dispZHCdm() {}

  @Test
  void testGetGhsSAFT() {}

  @Test
  void testGetMmin1SAFT() {}

  @Test
  void testGetNSAFT() {}

  @Test
  void testGetNmSAFT() {}

  @Test
  void testGetVolumeSAFT() {}

  @Test
  void testGetaSAFT() {}

  @Test
  void testGetaSAFTdm() {}

  @Test
  void testGetdDSAFTdT() {}

  @Test
  void testGetmSAFT() {}

  @Test
  void testGetmdSAFT() {}

  @Test
  void testInit() {
    p.init();
  }

  @Test
  void testMolarVolume() {}

  @Test
  void testSetAHSSAFT() {}

  @Test
  void testSetDSAFT() {}

  @Test
  void testSetDgHSSAFTdN() {}

  @Test
  void testSetDmeanSAFT() {}

  @Test
  void testSetDnSAFTdV() {}

  @Test
  void testSetF1dispVolTerm() {}

  @Test
  void testSetF2dispI2() {}

  @Test
  void testSetF2dispSumTerm() {}

  @Test
  void testSetF2dispZHC() {}

  @Test
  void testSetF2dispZHCdm() {}

  @Test
  void testSetGhsSAFT() {}

  @Test
  void testSetMmin1SAFT() {}

  @Test
  void testSetNSAFT() {}

  @Test
  void testSetNmSAFT() {}

  @Test
  void testSetVolumeSAFT() {}

  @Test
  void testSetmSAFT() {}

  @Test
  void testSetmdSAFT() {}
}
