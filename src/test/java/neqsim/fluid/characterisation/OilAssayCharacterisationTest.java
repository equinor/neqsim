package neqsim.fluid.characterisation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import neqsim.fluid.characterisation.OilAssayCharacterisation.AssayCut;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class OilAssayCharacterisationTest {

  @Test
  public void testAssayApplicationAddsPseudoComponents() {
    SystemInterface system = new SystemSrkEos(298.15, 10.0);
    OilAssayCharacterisation characterisation = system.getOilAssayCharacterisation();
    characterisation.clearCuts();

    AssayCut light = new AssayCut("Light").withWeightPercent(40.0).withDensity(0.75)
        .withAverageBoilingPointCelsius(200.0);
    AssayCut heavy = new AssayCut("Heavy").withVolumePercent(60.0).withApiGravity(25.0)
        .withAverageBoilingPointCelsius(350.0);

    characterisation.addCut(light);
    characterisation.addCut(heavy);
    characterisation.apply();

    double lightBoilingPoint = 200.0 + 273.15;
    double expectedLightMolarMass = 5.805e-5 * Math.pow(lightBoilingPoint, 2.3776)
        / Math.pow(0.75, 0.9371);
    ComponentInterface lightComponent = system.getComponent("Light_PC");
    assertNotNull(lightComponent);
    assertEquals(expectedLightMolarMass, lightComponent.getMolarMass(), 1e-8);
    assertEquals(0.4 / expectedLightMolarMass, lightComponent.getNumberOfmoles(), 1e-8);

    double heavyDensity = 141.5 / (25.0 + 131.5) * 0.999016;
    double heavyBoilingPoint = 350.0 + 273.15;
    double expectedHeavyMolarMass = 5.805e-5 * Math.pow(heavyBoilingPoint, 2.3776)
        / Math.pow(heavyDensity, 0.9371);
    ComponentInterface heavyComponent = system.getComponent("Heavy_PC");
    assertNotNull(heavyComponent);
    assertEquals(expectedHeavyMolarMass, heavyComponent.getMolarMass(), 1e-8);
    assertEquals(0.6 / expectedHeavyMolarMass, heavyComponent.getNumberOfmoles(), 1e-8);
  }

  @Test
  public void testTotalMassScaling() {
    SystemInterface system = new SystemSrkEos(298.15, 10.0);
    OilAssayCharacterisation characterisation = system.getOilAssayCharacterisation();
    characterisation.clearCuts();

    AssayCut cut = new AssayCut("Assay").withMassFraction(1.0).withDensity(0.82)
        .withAverageBoilingPointCelsius(300.0);
    characterisation.addCut(cut);
    characterisation.setTotalAssayMass(2.0);
    characterisation.apply();

    double boilingPoint = 300.0 + 273.15;
    double expectedMolarMass = 5.805e-5 * Math.pow(boilingPoint, 2.3776) / Math.pow(0.82, 0.9371);
    ComponentInterface component = system.getComponent("Assay_PC");
    assertNotNull(component);
    assertEquals(2.0 / expectedMolarMass, component.getNumberOfmoles(), 1e-8);
  }

  @Test
  public void testCharacterisationClonedWithSystem() {
    SystemInterface system = new SystemSrkEos(298.15, 10.0);
    OilAssayCharacterisation original = system.getOilAssayCharacterisation();
    original.clearCuts();
    original.addCut(new AssayCut("CloneTest").withMassFraction(1.0).withDensity(0.85)
        .withAverageBoilingPointCelsius(310.0));

    SystemInterface cloned = system.clone();
    OilAssayCharacterisation cloneCharacterisation = cloned.getOilAssayCharacterisation();
    assertTrue(cloneCharacterisation.getCuts().size() == 1);
    cloneCharacterisation.apply();

    assertTrue(Arrays.asList(cloned.getComponentNames()).contains("CloneTest_PC"));
    assertFalse(Arrays.asList(system.getComponentNames()).contains("CloneTest_PC"));
  }
}
