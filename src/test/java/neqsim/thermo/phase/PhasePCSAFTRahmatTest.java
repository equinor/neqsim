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
    testSystem.addComponent("methane", 1.0);
    testSystem.addComponent("n-hexane", 10.0001);
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
    assertEquals(-3.4043342994822865, value);
  }

  @Test
  void testF_DISP2_SAFT() {
    double value = p.F_DISP2_SAFT();
    assertEquals(-3.814909891308209, value);
  }

  @Test
  void testF_HC_SAFT() {
    double value = p.F_HC_SAFT();
    assertEquals(0.6241746766202064, value);
  }

  @Test
  void testCalcF1dispI1() {
    double value = p.calcF1dispI1();
    assertEquals(0.6987233400953171, value);
  }

  @Test
  void testCalcF1dispI1dN() {
    double value = p.calcF1dispI1dN();
    assertEquals(0.8636251080330241, value);
  }

  @Test
  void testCalcF1dispI1dNdN() {
    double value = p.calcF1dispI1dNdN();
    assertEquals(1.7511845029582653, value);
  }

  @Test
  void testCalcF1dispI1dNdNdN() {
    double value = p.calcF1dispI1dNdNdN();
    assertEquals(-67.25771342213771, value);
  }

  @Test
  void testCalcF1dispI1dm() {
    double value = p.calcF1dispI1dm();
    assertEquals(-0.05419441760656525, value);
  }

  @Test
  void testCalcF1dispSumTerm() {
    double value = p.calcF1dispSumTerm();
    assertEquals(7.022496186615598E-28, value);
  }

  @Test
  void testCalcF2dispI2() {
    double value = p.calcF2dispI2();
    assertEquals(0.3898434802348004, value);
  }

  @Test
  void testCalcF2dispI2dN() {
    double value = p.calcF2dispI2dN();
    assertEquals(2.584338688968146, value);
  }

  @Test
  void testCalcF2dispI2dNdN() {
    double value = p.calcF2dispI2dNdN();
    assertEquals(-7.8201230061788705, value);
  }

  @Test
  void testCalcF2dispI2dNdNdN() {
    double value = p.calcF2dispI2dNdNdN();
    assertEquals(-139.85014225523986, value);
  }

  @Test
  void testCalcF2dispI2dm() {
    double value = p.calcF2dispI2dm();
    assertEquals(-0.05060155476938381, value);
  }

  @Test
  void testCalcF2dispSumTerm() {
    double value = p.calcF2dispSumTerm();
    assertEquals(1.0974433985688316E-27, value);
  }

  @Test
  void testCalcF2dispZHC() {
    double value = p.calcF2dispZHC();
    assertEquals(0.8954508454156733, value);
  }

  @Test
  void testCalcF2dispZHCdN() {
    double value = p.calcF2dispZHCdN();
    assertEquals(-11.876697166471468, value);
  }

  @Test
  void testCalcF2dispZHCdNdN() {
    double value = p.calcF2dispZHCdNdN();
    assertEquals(194.1247342386087, value);
  }

  @Test
  void testCalcF2dispZHCdNdNdN() {
    double value = p.calcF2dispZHCdNdNdN();
    assertEquals(2459111.1880325177, value);
  }

  @Test
  void testCalcF2dispZHCdm() {
    double value = p.calcF2dispZHCdm();
    assertEquals(-0.02097670842244946, value);
  }

  @Test
  void testCalcdF1dispI1dT() {
    double value = p.calcdF1dispI1dT();
    assertEquals(-3.275063704056996E-25, value);
  }

  @Test
  void testCalcdF1dispSumTermdT() {
    double value = p.calcdF1dispSumTermdT();
    assertEquals(-4.681664124410399E-30, value);
  }

  @Test
  void testCalcdF2dispI2dT() {
    double value = p.calcdF2dispI2dT();
    assertEquals(-9.800402698465971E-25, value);
  }

  @Test
  void testCalcdF2dispSumTermdT() {
    double value = p.calcdF2dispSumTermdT();
    assertEquals(-1.4632578647584427E-29, value);
  }

  @Test
  void testCalcdF2dispZHCdT() {
    double value = p.calcdF2dispZHCdT();
    assertEquals(4.503913592732077E-24, value);
  }

  @Test
  void testCalcdSAFT() {
    double value = p.calcdSAFT();
    assertEquals(1.5637594774229341E-28, value);
  }

  @Test
  void testCalcdmeanSAFT() {
    double value = p.calcdmeanSAFT();
    assertEquals(3.790836816021272E-10, value);
  }

  @Test
  void testCalcmSAFT() {
    double value = p.calcmSAFT();
    assertEquals(2.8705471550258634, value);
  }

  @Test
  void testCalcmdSAFT() {
    double value = p.calcmdSAFT();
    assertEquals(1.5637594774229341E-28, value);
  }

  @Test
  void testCalcmmin1SAFT() {
    double value = p.calcmmin1SAFT();
    assertEquals(1.8705471550258634, value);
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
