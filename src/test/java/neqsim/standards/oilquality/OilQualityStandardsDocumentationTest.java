package neqsim.standards.oilquality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Executes the core oil-quality workflows documented in the standards guide. */
public class OilQualityStandardsDocumentationTest {
  private SystemInterface createDocumentedOil() {
    SystemInterface oil = new SystemSrkEos(273.15 + 15.0, 1.01325);
    oil.addComponent("methane", 0.01);
    oil.addComponent("ethane", 0.02);
    oil.addComponent("propane", 0.03);
    oil.addTBPfraction("C7", 0.15, 0.095, 0.72);
    oil.addTBPfraction("C10", 0.20, 0.135, 0.78);
    oil.addTBPfraction("C20", 0.30, 0.280, 0.85);
    oil.addTBPfraction("C30", 0.29, 0.450, 0.91);
    oil.setMixingRule(2);
    oil.init(0);
    return oil;
  }

  @Test
  void executesDocumentedDensityViscosityAndDistillationWorkflow() {
    SystemInterface oil = createDocumentedOil();

    Standard_ASTM_D4052 density = new Standard_ASTM_D4052(oil);
    density.calculate();
    double densityKgPerM3 = density.getValue("density");
    double specificGravity = density.getValue("SG");
    double apiGravity = density.getValue("API");

    assertTrue(densityKgPerM3 > 600.0 && densityKgPerM3 < 1100.0);
    assertTrue(specificGravity > 0.6 && specificGravity < 1.1);
    assertEquals(141.5 / specificGravity - 131.5, apiGravity, 1.0e-10);
    assertTrue(density.isOnSpec(), "D4052 isOnSpec reports a finite calculation");

    Standard_ASTM_D86 distillation = new Standard_ASTM_D86(oil);
    distillation.calculate();
    double initialBoilingPointC = distillation.getValue("IBP", "C");
    double midpointC = distillation.getValue("T50", "C");
    double finalBoilingPointC = distillation.getValue("FBP", "C");

    assertFalse(Double.isNaN(initialBoilingPointC));
    assertTrue(initialBoilingPointC < midpointC);
    assertTrue(midpointC < finalBoilingPointC);
    assertEquals(distillation.getValue("WatsonK"), distillation.getValue("WatsonK", "K"), 0.0);

    Standard_ASTM_D445 viscosity = new Standard_ASTM_D445(oil);
    viscosity.calculate();
    assertTrue(viscosity.getValue("KV40") > 0.0);
    assertTrue(viscosity.getValue("KV100") > 0.0);
  }

  @Test
  void executesDocumentedSulfurKeysAndUnits() {
    SystemInterface sourOil = new SystemSrkEos(273.15 + 15.0, 1.01325);
    sourOil.addComponent("n-hexane", 0.40);
    sourOil.addComponent("nC10", 0.40);
    sourOil.addComponent("H2S", 0.05);
    sourOil.addTBPfraction("C15", 0.15, 0.206, 0.83);
    sourOil.setMixingRule(2);
    sourOil.init(0);

    Standard_ASTM_D4294 sulfur = new Standard_ASTM_D4294(sourOil);
    sulfur.calculate();
    double sulfurWtPct = sulfur.getValue("sulfur");
    double sulfurPpmw = sulfur.getValue("sulfur", "ppmw");

    assertTrue(sulfurWtPct > 0.0);
    assertEquals(sulfurWtPct, sulfur.getValue("totalSulfur"), 0.0);
    assertEquals(sulfurWtPct * 10000.0, sulfurPpmw, 1.0e-8);
    assertFalse("Unknown".equals(sulfur.getSulfurClassification()));
  }

  @Test
  void executesDocumentedThermodynamicBswBoundary() {
    SystemInterface wetOil = new SystemSrkEos(273.15 + 60.0, 1.01325);
    wetOil.addComponent("n-hexane", 0.30);
    wetOil.addComponent("nC10", 0.50);
    wetOil.addComponent("water", 0.02);
    wetOil.addTBPfraction("C15", 0.18, 0.206, 0.83);
    wetOil.setMixingRule(2);
    wetOil.setMultiPhaseCheck(true);
    wetOil.init(0);

    Standard_BSW bsw = new Standard_BSW(wetOil);
    bsw.setMaxBSW(100.0);
    bsw.calculate();

    double bswVolPct = bsw.getValue("BSW");
    assertTrue(bswVolPct >= 0.0 && bswVolPct <= 100.0);
    assertEquals(bswVolPct, bsw.getValue("waterCut"), 0.0);
    assertTrue(bsw.isOnSpec());
  }
}
