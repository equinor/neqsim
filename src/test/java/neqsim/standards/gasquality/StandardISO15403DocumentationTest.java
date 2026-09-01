package neqsim.standards.gasquality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Executes and verifies the ISO 15403 documentation workflow. */
class StandardISO15403DocumentationTest extends NeqSimTest {
  @Test
  void documentedWorkflowMatchesCorrelationAndCompositionTrends() {
    Standard_ISO15403 base = new Standard_ISO15403(createCng(0.92, 0.01, 0.01));
    base.calculate();
    double baseMon = base.getValue("MON");
    double baseNm = base.getValue("NM");

    Standard_ISO15403 carbonDioxideCase = new Standard_ISO15403(createCng(0.90, 0.03, 0.01));
    carbonDioxideCase.calculate();
    double carbonDioxideNm = carbonDioxideCase.getValue("NM");

    Standard_ISO15403 nitrogenCase = new Standard_ISO15403(createCng(0.90, 0.01, 0.03));
    nitrogenCase.calculate();
    double nitrogenNm = nitrogenCase.getValue("NM");

    assertEquals(128.18474, baseMon, 1.0e-10);
    assertEquals(81.8069493, baseNm, 1.0e-10);
    assertEquals(83.0627410, carbonDioxideNm, 1.0e-10);
    assertEquals(78.6037889, nitrogenNm, 1.0e-10);
    assertTrue(carbonDioxideNm > baseNm);
    assertTrue(nitrogenNm < baseNm);
    assertEquals("", base.getUnit("MON"));
    assertTrue(base.isOnSpec());
  }

  @Test
  void pureMethaneAnchorAndUnsupportedAliasMatchCurrentImplementation() {
    SystemInterface methane = new SystemSrkEos(288.15, 200.0);
    methane.addComponent("methane", 1.0);
    methane.init(0);

    Standard_ISO15403 standard = new Standard_ISO15403(methane);
    standard.calculate();

    assertEquals(137.78, standard.getValue("MON"), 1.0e-12);
    assertEquals(95.6721, standard.getValue("NM"), 1.0e-12);
    assertThrows(RuntimeException.class, () -> standard.getValue("MN"));
  }

  private SystemInterface createCng(double methane, double carbonDioxide, double nitrogen) {
    SystemInterface gas = new SystemSrkEos(288.15, 200.0);
    gas.addComponent("methane", methane);
    gas.addComponent("ethane", 0.04);
    gas.addComponent("propane", 0.01);
    gas.addComponent("n-butane", 0.005);
    gas.addComponent("i-butane", 0.005);
    gas.addComponent("CO2", carbonDioxide);
    gas.addComponent("nitrogen", nitrogen);
    gas.init(0);
    return gas;
  }
}
