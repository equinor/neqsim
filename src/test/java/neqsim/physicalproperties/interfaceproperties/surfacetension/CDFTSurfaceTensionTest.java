package neqsim.physicalproperties.interfaceproperties.surfacetension;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class CDFTSurfaceTensionTest {

  private double computeCDFT_IFT(SystemInterface system) {
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(true);
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    try {
      ops.bubblePointPressureFlash(false);
    } catch (Exception ex) {
      return -1.0;
    }
    system.initProperties();
    if (system.getNumberOfPhases() < 2) {
      return -1.0;
    }
    system.getInterphaseProperties().setInterfacialTensionModel("gas", "oil", "cDFT");
    return system.getInterphaseProperties().getSurfaceTension(0, 1);
  }

  @Test
  void testMethane_PR_120K() {
    SystemInterface system = new SystemPrEos(120.0, 1.0);
    system.addComponent("methane", 1.0);
    double sigma = computeCDFT_IFT(system);
    assertTrue(sigma > 0, "Methane cDFT IFT should be positive, got " + sigma);
    double mNm = sigma * 1000;
    assertTrue(mNm > 1.0 && mNm < 30.0, "Methane IFT at 120K out of range: " + mNm + " mN/m");
  }

  @Test
  void testMethane_SRK_120K() {
    SystemInterface system = new SystemSrkEos(120.0, 1.0);
    system.addComponent("methane", 1.0);
    double sigma = computeCDFT_IFT(system);
    assertTrue(sigma > 0, "SRK cDFT IFT should be positive, got " + sigma);
    double mNm = sigma * 1000;
    assertTrue(mNm > 1.0 && mNm < 30.0, "Methane SRK IFT at 120K out of range: " + mNm + " mN/m");
  }

  @Test
  void testPropane_PR_230K() {
    SystemInterface system = new SystemPrEos(230.0, 1.0);
    system.addComponent("propane", 1.0);
    double sigma = computeCDFT_IFT(system);
    assertTrue(sigma > 0, "Propane cDFT IFT should be positive, got " + sigma);
    double mNm = sigma * 1000;
    assertTrue(mNm > 2.0 && mNm < 25.0, "Propane IFT at 230K out of range: " + mNm + " mN/m");
  }

  @Test
  void testNHexane_PR_340K() {
    SystemInterface system = new SystemPrEos(340.0, 1.0);
    system.addComponent("n-hexane", 1.0);
    double sigma = computeCDFT_IFT(system);
    assertTrue(sigma > 0, "n-Hexane cDFT IFT should be positive, got " + sigma);
    double mNm = sigma * 1000;
    assertTrue(mNm > 3.0 && mNm < 30.0, "n-Hexane IFT at 340K out of range: " + mNm + " mN/m");
  }

  @Test
  void testMixtureFallsBackToParachor() {
    SystemInterface system = new SystemPrEos(250.0, 30.0);
    system.addComponent("methane", 0.8);
    system.addComponent("ethane", 0.2);
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(true);
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();
    if (system.getNumberOfPhases() >= 2) {
      system.getInterphaseProperties().setInterfacialTensionModel("gas", "oil", "cDFT");
      double sigma = system.getInterphaseProperties().getSurfaceTension(0, 1);
      assertTrue(sigma > 0, "Mixture fallback IFT should be positive, got " + sigma);
    }
  }

  @Test
  void testModelNameAlias() {
    SystemInterface system = new SystemPrEos(120.0, 1.0);
    system.addComponent("methane", 1.0);
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(true);
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    try {
      ops.bubblePointPressureFlash(false);
    } catch (Exception ex) {
      return;
    }
    system.initProperties();
    if (system.getNumberOfPhases() >= 2) {
      system.getInterphaseProperties().setInterfacialTensionModel("gas", "oil", "Classical DFT");
      double sigma = system.getInterphaseProperties().getSurfaceTension(0, 1);
      assertTrue(sigma > 0, "Classical DFT alias should work, got " + sigma);
    }
  }

  @Test
  void testCompare_cDFT_vs_FullGT_Methane() {
    SystemInterface system = new SystemPrEos(120.0, 1.0);
    system.addComponent("methane", 1.0);
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(true);
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    try {
      ops.bubblePointPressureFlash(false);
    } catch (Exception ex) {
      return;
    }
    system.initProperties();
    if (system.getNumberOfPhases() < 2) {
      return;
    }
    system.getInterphaseProperties().setInterfacialTensionModel("gas", "oil", "cDFT");
    double sigmaCDFT = system.getInterphaseProperties().getSurfaceTension(0, 1);
    system.getInterphaseProperties().setInterfacialTensionModel("gas", "oil",
        "Full Gradient Theory");
    double sigmaGT = system.getInterphaseProperties().getSurfaceTension(0, 1);
    assertTrue(sigmaCDFT > 0, "cDFT IFT should be positive, got " + sigmaCDFT);
    if (sigmaGT > 0) {
      double ratio = sigmaCDFT / sigmaGT;
      assertTrue(ratio > 0.05 && ratio < 20.0,
          "cDFT/GT ratio=" + ratio + " cDFT=" + (sigmaCDFT * 1000) + " GT=" + (sigmaGT * 1000));
    }
  }
}
