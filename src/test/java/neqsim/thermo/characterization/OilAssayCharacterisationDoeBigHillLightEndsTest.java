package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.thermo.characterization.OilAssayCharacterisation.AssayCut;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Public DOE qualification of composition-resolved C2-C4 assay light ends. */
public class OilAssayCharacterisationDoeBigHillLightEndsTest {
  private static final String[] COMPONENT_NAMES = { "ethane", "propane", "i-butane", "n-butane" };
  private static final double[] DOE_DEBUTANIZATION_WEIGHT_PERCENT = { 0.09, 10.38, 10.21, 45.95 };
  private static final double DOE_C2_C4_SUBSET_WEIGHT_PERCENT = 66.63;
  private static final double DOE_C2_C4_WHOLE_CRUDE_MASS_PERCENT = 1.70;

  @Test
  public void doePianoCompositionNormalizesOnlyTheReportedC2C4Subset() {
    double reportedSubset = 0.0;
    double normalizedSubset = 0.0;
    for (double weightPercent : DOE_DEBUTANIZATION_WEIGHT_PERCENT) {
      reportedSubset += weightPercent;
      normalizedSubset += weightPercent / DOE_C2_C4_SUBSET_WEIGHT_PERCENT;
    }

    assertEquals(DOE_C2_C4_SUBSET_WEIGHT_PERCENT, reportedSubset, 1.0e-12);
    assertEquals(1.0, normalizedSubset, 1.0e-12);
    assertEquals(0.0013507429085997298, DOE_DEBUTANIZATION_WEIGHT_PERCENT[0] / DOE_C2_C4_SUBSET_WEIGHT_PERCENT,
        1.0e-15);
    assertEquals(0.6896292961128622, DOE_DEBUTANIZATION_WEIGHT_PERCENT[3] / DOE_C2_C4_SUBSET_WEIGHT_PERCENT, 1.0e-15);
  }

  @Test
  public void standardComponentsUseAuthoritativeMolarMassAndCloseGasSlice() {
    SystemInterface forward = buildGasSlice(false);
    SystemInterface reverse = buildGasSlice(true);
    assertEquals(COMPONENT_NAMES.length, forward.getNumberOfComponents());
    assertEquals(COMPONENT_NAMES.length, reverse.getNumberOfComponents());

    double expectedMassKg = DOE_C2_C4_WHOLE_CRUDE_MASS_PERCENT / 100.0;
    double reconstructedMassKg = 0.0;
    for (String componentName : COMPONENT_NAMES) {
      ComponentInterface component = forward.getComponent(componentName);
      ComponentInterface reversedComponent = reverse.getComponent(componentName);
      assertNotNull(component);
      assertNotNull(reversedComponent);
      assertTrue(Double.isFinite(component.getMolarMass()));
      assertTrue(component.getMolarMass() > 0.0);
      assertTrue(component.getNumberOfmoles() > 0.0);
      assertEquals(component.getNumberOfmoles(), reversedComponent.getNumberOfmoles(), 1.0e-12);
      reconstructedMassKg += component.getNumberOfmoles() * component.getMolarMass();
      assertFalse(forward.hasComponent("DOE_BH_" + componentName.toUpperCase() + "_PC", false));
    }
    assertEquals(expectedMassKg, reconstructedMassKg, 1.0e-10);
  }

  @Test
  public void invalidStandardComponentsFailBeforeOriginalSystemMutation() {
    SystemInterface unknownSystem = new SystemSrkEos(298.15, 1.01325);
    OilAssayCharacterisation unknownAssay = unknownSystem.getOilAssayCharacterisation();
    unknownAssay.addCut(new AssayCut("Known").withMassFraction(0.5).withStandardComponent("ethane"));
    unknownAssay.addCut(new AssayCut("Unknown").withMassFraction(0.5).withStandardComponent("not-a-neqsim-component"));
    assertThrows(IllegalStateException.class, unknownAssay::apply);
    assertEquals(0, unknownSystem.getNumberOfComponents());

    SystemInterface duplicateSystem = new SystemSrkEos(298.15, 1.01325);
    OilAssayCharacterisation duplicateAssay = duplicateSystem.getOilAssayCharacterisation();
    duplicateAssay.addCut(new AssayCut("EthaneOne").withMassFraction(0.5).withStandardComponent("ethane"));
    duplicateAssay.addCut(new AssayCut("EthaneTwo").withMassFraction(0.5).withStandardComponent("ETHANE"));
    assertThrows(IllegalStateException.class, duplicateAssay::apply);
    assertEquals(0, duplicateSystem.getNumberOfComponents());

    SystemInterface ambiguousSystem = new SystemSrkEos(298.15, 1.01325);
    OilAssayCharacterisation ambiguousAssay = ambiguousSystem.getOilAssayCharacterisation();
    ambiguousAssay.addCut(
        new AssayCut("Ambiguous").withMassFraction(1.0).withSpecificGravity(0.6).withStandardComponent("propane"));
    assertThrows(IllegalStateException.class, ambiguousAssay::apply);
    assertEquals(0, ambiguousSystem.getNumberOfComponents());
  }

  @Test
  public void standardComponentMetadataSurvivesCloneAndRequiresMassBasis() {
    SystemInterface system = new SystemSrkEos(298.15, 1.01325);
    OilAssayCharacterisation assay = system.getOilAssayCharacterisation();
    assay.addCut(new AssayCut("DOE_BH_ETHANE").withMassFraction(1.0).withStandardComponent("ethane"));

    SystemInterface clone = system.clone();
    AssayCut clonedCut = clone.getOilAssayCharacterisation().getCuts().get(0);
    assertTrue(clonedCut.isStandardComponent());
    assertEquals("ethane", clonedCut.getStandardComponentName());

    SystemInterface volumeSystem = new SystemSrkEos(298.15, 1.01325);
    OilAssayCharacterisation volumeAssay = volumeSystem.getOilAssayCharacterisation();
    volumeAssay.addCut(new AssayCut("VolumeEthane").withVolumeFraction(1.0).withStandardComponent("ethane"));
    assertThrows(IllegalStateException.class, volumeAssay::apply);
    assertEquals(0, volumeSystem.getNumberOfComponents());

    assertThrows(IllegalArgumentException.class, () -> new AssayCut("Blank").withStandardComponent(" "));
  }

  private static SystemInterface buildGasSlice(boolean reverseOrder) {
    SystemInterface system = new SystemSrkEos(298.15, 1.01325);
    OilAssayCharacterisation assay = system.getOilAssayCharacterisation();
    assay.setTotalAssayMass(DOE_C2_C4_WHOLE_CRUDE_MASS_PERCENT / 100.0);

    for (int position = 0; position < COMPONENT_NAMES.length; position++) {
      int index = reverseOrder ? COMPONENT_NAMES.length - 1 - position : position;
      String componentName = COMPONENT_NAMES[index];
      double normalizedMassFraction = DOE_DEBUTANIZATION_WEIGHT_PERCENT[index] / DOE_C2_C4_SUBSET_WEIGHT_PERCENT;
      assay.addCut(new AssayCut("DOE_BH_" + componentName.toUpperCase()).withMassFraction(normalizedMassFraction)
          .withStandardComponent(componentName));
    }

    assay.apply();
    return system;
  }
}
