package neqsim.standards.oilquality;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.standards.oilquality.Standard_ASTM_D6377.RvpMethod;
import neqsim.standards.oilquality.Standard_ASTM_D6377.RvpResult;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Synthetic regressions for the declared oil vapor-pressure water basis, not laboratory validation.
 *
 * @author NeqSim
 * @version 1.0
 */
class OilVapourPressureWaterBasisTest extends NeqSimTest {
  /** Verifies the documented partial-pressure arithmetic using rounded external inputs in bar. */
  @Test
  void waterContributionIsScaledForRvpeButNotForTvp() {
    double dryVpcr4Bara = 1.0;
    double waterPsat378Bara = 0.0654;
    double dryRvpeBara = 0.834 * dryVpcr4Bara;
    double saturatedRvpeBara = 0.834 * (dryVpcr4Bara + waterPsat378Bara);
    assertEquals(0.8885436, saturatedRvpeBara, 1.0e-12);
    assertEquals(0.0545436, saturatedRvpeBara - dryRvpeBara, 1.0e-12);
    assertNotEquals(dryRvpeBara + waterPsat378Bara, saturatedRvpeBara, 1.0e-6);
    assertEquals(88.85436, 0.834 * (100.0 * dryVpcr4Bara + 6.54), 1.0e-10);
    double dryTvp30Bara = 1.2;
    assertEquals(1.2424, dryTvp30Bara + 0.0424, 1.0e-12);
    assertEquals(1.2654, dryTvp30Bara + waterPsat378Bara, 1.0e-12);
    assertNotEquals(0.0424, waterPsat378Bara, 1.0e-6);
  }

  /** Executes the dry-result example and verifies method, units, temperature and source state. */
  @Test
  void documentedDryResultAndStreamMethodsAgree() {
    SystemInterface sourceOil = createOil(false, false);
    double[] sourceComposition = sourceOil.getMolarComposition();
    Standard_ASTM_D6377 standard = new Standard_ASTM_D6377(sourceOil.clone());
    standard.setReferenceTemperature(37.8, "C");
    standard.setMethodRVP(RvpMethod.VPCR4_NO_WATER);
    standard.calculate();
    RvpResult dry = standard.getRvpResult();
    assertTrue(dry.isValid());
    assertEquals(RvpMethod.VPCR4_NO_WATER, dry.getMethod());
    assertEquals(37.8, dry.getReferenceTemperatureC(), 1.0e-12);
    assertEquals(standard.getRvpResult(RvpMethod.VPCR4).getValue(), dry.getValue(), 1.0e-6);
    assertEquals(standard.getRvpResult(RvpMethod.RVP_ASTM_D323_73_79).getValue(), dry.getValue(), 1.0e-12);
    assertEquals(0.834 * dry.getValue(), standard.getRvpResult(RvpMethod.RVP_ASTM_D6377).getValue(), 1.0e-6);
    assertEquals(0.752 * dry.getValue() + 0.0607, standard.getRvpResult(RvpMethod.RVP_ASTM_D323_82).getValue(), 1.0e-6);
    assertEquals(100.0 * dry.getValue(), standard.getValue("RVP", "kPa"), 1.0e-9);

    double waterPsat378Bara = 0.0654;
    double rvpeWaterSatBara = 0.834 * (dry.getValue() + waterPsat378Bara);
    assertEquals(0.0545436, rvpeWaterSatBara - 0.834 * dry.getValue(), 1.0e-12);
    StreamInterface dryStream = new Stream("Synthetic dry oil", sourceOil.clone());
    assertEquals(dry.getValue(), dryStream.getRVP(37.8, "C", "bara"), 1.0e-6);
    assertEquals(0.834 * dry.getValue(), dryStream.getRVP(37.8, "C", "bara", "RVP_ASTM_D6377"), 1.0e-6);
    assertEquals(dry.getValue(), dryStream.getRVP(37.8, "C", "bara"), 1.0e-6);
    assertEquals(100.0 * dry.getValue(), dryStream.getRVP(310.95, "K", "kPa", "VPCR4_no_water"), 1.0e-6);
    double tvp30DryBara = dryStream.getTVP(30.0, "C", "bara");
    double tvp30WaterSatBara = tvp30DryBara + 0.0424;
    assertEquals(0.0424, tvp30WaterSatBara - tvp30DryBara, 1.0e-12);
    assertEquals(tvp30DryBara * 100.0, dryStream.getTVP(303.15, "K", "kPa"), 1.0e-6);
    standard.setReferenceTemperature(30.0, "C");
    standard.calculate();
    assertEquals(standard.getValue("TVP", "bara"), tvp30DryBara, 1.0e-6);
    assertEquals(30.0, standard.getRvpResult().getReferenceTemperatureC(), 1.0e-12);
    assertEquals(293.15, sourceOil.getTemperature(), 0.0);
    assertEquals(5.0, sourceOil.getPressure(), 0.0);
    assertArrayEquals(sourceComposition, sourceOil.getMolarComposition(), 0.0);
  }

  /** Verifies all allocated phases when removal does not shift retained component indices. */
  @Test
  void lastComponentWaterRemovalPreservesCharacterizationAndSource() {
    SystemInterface source = createOil(true, false);
    SystemInterface independentSource = createOil(true, false);
    double[] sourceComposition = source.getMolarComposition();
    SystemInterface dry = source.clone();
    dry.removeComponent("water");
    dry.init(0);
    assertFalse(dry.hasComponent("water"));
    assertEquals(source.getClass(), dry.getClass());
    assertEquals(source.getMixingRule(), dry.getMixingRule());
    assertEquals(0.9, dry.getNumberOfMoles(), 1.0e-12);
    for (int phase = 0; phase < source.getMaxNumberOfPhases(); phase++) {
      for (int i = 0; i < dry.getNumberOfComponents(); i++) {
        ComponentInterface before = source.getPhase(phase).getComponent(i);
        ComponentInterface after = dry.getPhase(phase).getComponent(i);
        assertComponentProperties(before, after);
        assertEquals(before.getz() / 0.9, after.getz(), 1.0e-12);
        for (int j = 0; j < dry.getNumberOfComponents(); j++) {
          assertEquals(bip(source, phase, i, j), bip(dry, phase, i, j), 0.0);
        }
      }
    }
    SystemInterface wetWorking = source.clone();
    Standard_ASTM_D6377 wetStandard = new Standard_ASTM_D6377(wetWorking);
    wetStandard.calculate();
    Standard_ASTM_D6377 dryStandard = new Standard_ASTM_D6377(dry);
    dryStandard.calculate();
    assertTrue(wetStandard.getRvpResult().isValid());
    assertTrue(dryStandard.getRvpResult().isValid());
    assertEquals(dryStandard.getRvpResult().getValue(), wetStandard.getRvpResult(RvpMethod.VPCR4_NO_WATER).getValue(),
        1.0e-6);
    assertNotEquals(wetStandard.getRvpResult().getValue(), dryStandard.getRvpResult().getValue(), 1.0e-6);
    wetWorking.initPhysicalProperties("density");
    // At 10 mol% water this fixture returns oil/aqueous, not a valid gas/liquid VPCR4 state.
    // Keep this rejection visible: a positive structured result is not a convergence certificate.
    assertFalse(wetWorking.hasPhaseType("gas"));
    assertNotEquals(0.8, wetWorking.getCorrectedVolumeFraction(0), 1.0e-6);
    dry.initPhysicalProperties("density");
    assertTrue(dry.hasPhaseType("gas") && dry.hasPhaseType("oil"));
    assertEquals(0.8, dry.getCorrectedVolumeFraction(0), 1.0e-6);
    assertEquals(4.0, dry.getCorrectedVolumeFraction(0) / dry.getCorrectedVolumeFraction(1), 3.0e-5);
    assertArrayEquals(sourceComposition, source.getMolarComposition(), 0.0);
    assertEquals(293.15, source.getTemperature(), 0.0);
    assertEquals(5.0, source.getPressure(), 0.0);
    for (int phase = 0; phase < source.getMaxNumberOfPhases(); phase++) {
      for (int i = 0; i < source.getNumberOfComponents(); i++) {
        assertComponentProperties(independentSource.getPhase(phase).getComponent(i),
            source.getPhase(phase).getComponent(i));
        for (int j = 0; j < source.getNumberOfComponents(); j++) {
          assertEquals(bip(independentSource, phase, i, j), bip(source, phase, i, j), 0.0);
        }
      }
    }
  }

  /** Checks three trace-water cases against the same corrected-volume target as the dry case. */
  @Test
  void nativeWetCasesMeetCorrectedVolumeTarget() {
    for (double waterMoleFraction : new double[] {0.00001, 0.001, 0.01}) {
      SystemInterface oil = createOil(true, false);
      double[] composition = oil.getMolarComposition();
      for (int i = 0; i < composition.length - 1; i++) {
        composition[i] = composition[i] / 0.9 * (1.0 - waterMoleFraction);
      }
      composition[composition.length - 1] = waterMoleFraction;
      oil.setMolarComposition(composition);
      Standard_ASTM_D6377 standard = new Standard_ASTM_D6377(oil);
      standard.calculate();
      assertTrue(standard.getRvpResult().isValid());
      assertTrue(oil.hasPhaseType("gas") && oil.hasPhaseType("oil"));
      oil.initPhysicalProperties("density");
      assertEquals(0.8, oil.getCorrectedVolumeFraction(0), 1.0e-6);
      assertEquals(4.0, oil.getCorrectedVolumeFraction(0) / oil.getCorrectedVolumeFraction(1), 3.0e-5);
    }
  }

  /** Demonstrates why the characterization audit must reject an index-shifting removal. */
  @Test
  void auditRejectsMiddleComponentRemovalWithChangedNamedBip() {
    SystemInterface source = createOil(true, true);
    SystemInterface dry = source.clone();
    dry.removeComponent("water");
    dry.init(0);
    for (int phase = 0; phase < source.getMaxNumberOfPhases(); phase++) {
      final int phaseIndex = phase;
      assertThrows(IllegalStateException.class, () -> requireUnchangedBip(source, dry, phaseIndex));
    }
  }

  /**
   * Fails closed when removal changes the named pentane/methane interaction in this fixture.
   *
   * @param source original oil with water at index 1
   * @param dry candidate dry oil with methane shifted from index 2 to index 1
   * @param phase allocated phase index
   * @throws IllegalStateException if the retained interaction is different
   */
  private void requireUnchangedBip(SystemInterface source, SystemInterface dry, int phase) {
    if (bip(source, phase, 0, 2) != bip(dry, phase, 0, 1)) {
      throw new IllegalStateException("Water removal changed the pentane/methane BIP; reject this dry basis");
    }
  }

  /**
   * Builds a synthetic characterized SRK oil with a deliberately nonzero hydrocarbon BIP.
   *
   * @param water whether to include 0.1 mol water
   * @param middle whether water precedes the heavy fraction
   * @return oil at 293.15 K and 5 bara, with 0.9 mol hydrocarbons
   */
  private SystemInterface createOil(boolean water, boolean middle) {
    SystemInterface oil = new SystemSrkEos(293.15, 5.0);
    oil.addComponent("n-pentane", 0.3);
    if (water && middle) {
      oil.addComponent("water", 0.1);
    }
    oil.addComponent("methane", 0.001);
    oil.addComponent("ethane", 0.009);
    oil.addComponent("propane", 0.09);
    oil.addTBPfraction("C11", 0.5, 0.145, 0.82);
    if (water && !middle) {
      oil.addComponent("water", 0.1);
    }
    oil.setMixingRule(2);
    for (int phase = 0; phase < oil.getMaxNumberOfPhases(); phase++) {
      ((PhaseEosInterface) oil.getPhase(phase)).getEosMixingRule().setBinaryInteractionParameter(0,
          water && middle ? 2 : 1, 0.0123);
    }
    oil.init(0);
    return oil;
  }

  /**
   * Reads a dimensionless interaction without mutating a clone's possibly shared matrix.
   *
   * @param fluid configured EOS fluid
   * @param phase allocated phase index
   * @param i first component index
   * @param j second component index
   * @return dimensionless binary interaction parameter
   */
  private double bip(SystemInterface fluid, int phase, int i, int j) {
    return ((PhaseEosInterface) fluid.getPhase(phase)).getEosMixingRule().getBinaryInteractionParameter(i, j);
  }

  /**
   * Checks heavy-component properties, alpha and volume shifts without recalculating characterization.
   *
   * @param before original phase component
   * @param after retained phase component
   */
  private void assertComponentProperties(ComponentInterface before, ComponentInterface after) {
    assertEquals(before.getComponentName(), after.getComponentName());
    assertEquals(before.getTC(), after.getTC(), 0.0);
    assertEquals(before.getPC(), after.getPC(), 0.0);
    assertEquals(before.getAcentricFactor(), after.getAcentricFactor(), 0.0);
    assertEquals(before.getMolarMass(), after.getMolarMass(), 0.0);
    assertEquals(before.getNormalLiquidDensity(), after.getNormalLiquidDensity(), 0.0);
    assertEquals(before.getAttractiveTermNumber(), after.getAttractiveTermNumber());
    assertEquals(before.getAttractiveTerm().getClass(), after.getAttractiveTerm().getClass());
    assertEquals(before.getAttractiveTerm().alpha(310.95), after.getAttractiveTerm().alpha(310.95), 0.0);
    assertEquals(before.getVolumeCorrectionConst(), after.getVolumeCorrectionConst(), 0.0);
    assertEquals(before.getVolumeCorrectionT(), after.getVolumeCorrectionT(), 0.0);
    assertEquals(before.getRacketZ(), after.getRacketZ(), 0.0);
  }
}