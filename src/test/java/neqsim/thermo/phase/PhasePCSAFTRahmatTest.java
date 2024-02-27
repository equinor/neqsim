package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemPCSAFT;

public class PhasePCSAFTRahmatTest {
  static PhasePCSAFTRahmat p;
  static SystemPCSAFT testSystem;

  @BeforeEach
  void setUp() {
    testSystem = new SystemPCSAFT(150.0, 10.0);
    testSystem.addComponent("methane", 100.0);
    testSystem.addComponent("n-hexane", 1000.0001);
    testSystem.setMixingRule(1);
    testSystem.createDatabase(true);
    testSystem.init(0);
    testSystem.init(3);

    p = (PhasePCSAFTRahmat) testSystem.getPhase(0);
  }

  @Test
  void testAddcomponent() {
    p = new PhasePCSAFTRahmat();

    Assertions.assertEquals(0, p.getNumberOfComponents());

    p.addComponent("ethane", 0, 0, 0);
    Assertions.assertEquals(1, p.getNumberOfComponents());

    p.addComponent("methane", 0, 0, 0);
    Assertions.assertEquals(2, p.getNumberOfComponents());
  }

  @Test
  void testF_DISP1_SAFT() {
    double value = p.F_DISP1_SAFT();
    assertEquals(-593.232372549217, value);
  }

  @Test
  void testF_DISP2_SAFT() {
    double value = p.F_DISP2_SAFT();
    assertEquals(-633.8617101154254, value);
  }

  @Test
  void testF_HC_SAFT() {
    double value = p.F_HC_SAFT();
    assertEquals(109.10070086992114, value);
  }

  @Test
  void testCalcF1dispI1() {
    double value = p.calcF1dispI1();
    assertEquals(0.7039321962898899, value);
  }

  @Test
  void testCalcF1dispI1dN() {
    double value = p.calcF1dispI1dN();
    assertEquals(0.8729608222468556, value);
  }

  @Test
  void testCalcF1dispI1dNdN() {
    double value = p.calcF1dispI1dNdN();
    assertEquals(1.3690787135656468, value);
  }

  @Test
  void testCalcF1dispI1dNdNdN() {
    double value = p.calcF1dispI1dNdNdN();
    assertEquals(-60.233815812511885, value);
  }

  @Test
  void testCalcF1dispI1dm() {
    double value = p.calcF1dispI1dm();
    assertEquals(-0.053549895709883576, value);
  }

  @Test
  void testCalcF1dispSumTerm() {
    double value = p.calcF1dispSumTerm();
    assertEquals(7.022486947548597E-28, value);
  }

  @Test
  void testCalcF2dispI2() {
    double value = p.calcF2dispI2();
    assertEquals(0.40519775405102854, value);
  }

  @Test
  void testCalcF2dispI2dN() {
    double value = p.calcF2dispI2dN();
    assertEquals(2.535051300634048, value);
  }

  @Test
  void testCalcF2dispI2dNdN() {
    double value = p.calcF2dispI2dNdN();
    assertEquals(-8.594085139826845, value);
  }

  @Test
  void testCalcF2dispI2dNdNdN() {
    double value = p.calcF2dispI2dNdNdN();
    assertEquals(-118.18119091396187, value);
  }

  @Test
  void testCalcF2dispI2dm() {
    double value = p.calcF2dispI2dm();
    assertEquals(-0.0505631231034932, value);
  }

  @Test
  void testCalcF2dispSumTerm() {
    double value = p.calcF2dispSumTerm();
    assertEquals(1.0974418484311756E-27, value);
  }

  @Test
  void testCalcF2dispZHC() {
    double value = p.calcF2dispZHC();
    assertEquals(0.8275770986522495, value);
  }

  @Test
  void testCalcF2dispZHCdN() {
    double value = p.calcF2dispZHCdN();
    assertEquals(-10.778804485725638, value);
  }

  @Test
  void testCalcF2dispZHCdNdN() {
    double value = p.calcF2dispZHCdNdN();
    assertEquals(172.4903319088591, value);
  }

  @Test
  void testCalcF2dispZHCdNdNdN() {
    double value = p.calcF2dispZHCdNdNdN();
    assertEquals(1869552.2739122098, value);
  }

  @Test
  void testCalcF2dispZHCdm() {
    double value = p.calcF2dispZHCdm();
    assertEquals(-0.03234376503647885, value);
  }

  @Test
  void testCalcdF1dispI1dT() {
    double value = p.calcdF1dispI1dT();
    assertEquals(-5.726124600545992E-25, value);
  }

  @Test
  void testCalcdF1dispSumTermdT() {
    double value = p.calcdF1dispSumTermdT();
    assertEquals(-4.6816579650323984E-30, value);
  }

  @Test
  void testCalcdF2dispI2dT() {
    double value = p.calcdF2dispI2dT();
    assertEquals(-1.6628489213118322E-24, value);
  }

  @Test
  void testCalcdF2dispSumTermdT() {
    double value = p.calcdF2dispSumTermdT();
    assertEquals(-1.4632557979082342E-29, value);
  }

  @Test
  void testCalcdF2dispZHCdT() {
    double value = p.calcdF2dispZHCdT();
    assertEquals(7.070270274051911E-24, value);
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
