package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import neqsim.thermo.characterization.OilAssayCharacterisation.AssayCut;
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
    double expectedLightMolarMass =
        5.805e-5 * Math.pow(lightBoilingPoint, 2.3776) / Math.pow(0.75, 0.9371);
    ComponentInterface lightComponent = system.getComponent("Light_PC");
    assertNotNull(lightComponent);
    assertEquals(expectedLightMolarMass, lightComponent.getMolarMass(), 1e-8);
    assertEquals(0.4 / expectedLightMolarMass, lightComponent.getNumberOfmoles(), 1e-8);

    double heavyDensity = 141.5 / (25.0 + 131.5) * 0.999016;
    double heavyBoilingPoint = 350.0 + 273.15;
    double expectedHeavyMolarMass =
        5.805e-5 * Math.pow(heavyBoilingPoint, 2.3776) / Math.pow(heavyDensity, 0.9371);
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

  @Test
  public void testExplicitMolarMassSkipsBoilingPointRequirement() {
    SystemInterface system = new SystemSrkEos(298.15, 10.0);
    OilAssayCharacterisation characterisation = system.getOilAssayCharacterisation();
    characterisation.clearCuts();

    // Create a cut with explicit molar mass but no boiling point
    AssayCut cut =
        new AssayCut("ExplicitMW").withMassFraction(1.0).withDensity(0.8).withMolarMass(150.0); // Explicit
                                                                                                // molar
                                                                                                // mass,
                                                                                                // no
                                                                                                // boiling
                                                                                                // point

    characterisation.addCut(cut);
    characterisation.apply(); // Should not throw exception

    ComponentInterface component = system.getComponent("ExplicitMW_PC");
    assertNotNull(component);
    assertEquals(150.0, component.getMolarMass(), 1e-8);
    assertEquals(1.0 / 150.0, component.getNumberOfmoles(), 1e-8);
  }

  @Test
  public void testCalculatedMolarMassStillRequiresBoilingPoint() {
    SystemInterface system = new SystemSrkEos(298.15, 10.0);
    OilAssayCharacterisation characterisation = system.getOilAssayCharacterisation();
    characterisation.clearCuts();

    // Create a cut without explicit molar mass or boiling point - should fail
    AssayCut cut = new AssayCut("NoBoilingPoint").withMassFraction(1.0).withDensity(0.8);
    // No molar mass, no boiling point

    characterisation.addCut(cut);

    // This should throw an exception because boiling point is required for molar mass calculation
    try {
      characterisation.apply();
      assertTrue(false, "Expected IllegalStateException for missing boiling point");
    } catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("Average boiling point missing"));
    }
  }

  @Test
  public void testMixedExplicitAndCalculatedMolarMass() {
    SystemInterface system = new SystemSrkEos(298.15, 10.0);
    OilAssayCharacterisation characterisation = system.getOilAssayCharacterisation();
    characterisation.clearCuts();

    // Cut with explicit molar mass (no boiling point needed)
    AssayCut explicitCut =
        new AssayCut("Explicit").withMassFraction(0.5).withDensity(0.8).withMolarMass(120.0);

    // Cut with calculated molar mass (requires boiling point)
    AssayCut calculatedCut = new AssayCut("Calculated").withMassFraction(0.5).withDensity(0.85)
        .withAverageBoilingPointCelsius(250.0);

    characterisation.addCut(explicitCut);
    characterisation.addCut(calculatedCut);
    characterisation.apply();

    // Verify explicit molar mass component
    ComponentInterface explicitComponent = system.getComponent("Explicit_PC");
    assertNotNull(explicitComponent);
    assertEquals(120.0, explicitComponent.getMolarMass(), 1e-8);

    // Verify calculated molar mass component
    ComponentInterface calculatedComponent = system.getComponent("Calculated_PC");
    assertNotNull(calculatedComponent);
    // The calculated molar mass should be different from 120.0
    assertTrue(Math.abs(calculatedComponent.getMolarMass() - 120.0) > 1.0);
  }
}
