package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseEos;
import neqsim.thermo.phase.PhaseGEInterface;
import neqsim.thermo.phase.PhaseGEVanLaarAcid;
import neqsim.thermo.phase.PhasePureComponentSolid;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.util.empiric.NitricSulfuricAcidVaporPressure;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.exception.IsNaNException;

/** Tests the reusable EOS-GE topology and thermodynamic-operation support. */
@Tag("slow")
public class SystemEosGEOperationsTest extends neqsim.NeqSimTest {
  /** Number of pascals per bar. */
  private static final double PASCALS_PER_BAR = 1.0e5;

  /**
   * Build a material two-phase CO2/acid system inside the source model's temperature range.
   *
   * @return configured EOS-GE system
   */
  private SystemVanLaarActivitySRK createTwoPhaseSystem() {
    SystemVanLaarActivitySRK system = new SystemVanLaarActivitySRK(273.15, 1.0);
    system.addComponent("CO2", 10.0);
    system.addComponent("water", 0.70);
    system.addComponent("nitric acid", 0.15);
    system.addComponent("sulfuric acid", 0.15);
    system.createDatabase(true);
    system.setMixingRule("classic");
    return system;
  }

  /** Shared EOS-GE construction keeps EOS, GE and optional solid phases in defined roles. */
  @Test
  public void testReusablePhaseTopologyAndClone() {
    SystemVanLaarActivitySRK system = new SystemVanLaarActivitySRK(273.15, 1.0, true);

    assertTrue(system.getEquationOfStatePhase() instanceof PhaseEos);
    assertTrue(system.isExcessGibbsEnergyPhase(1));
    assertTrue(system.getPhase(1) instanceof PhaseGEVanLaarAcid);
    assertTrue(system.getPhase(2) instanceof PhaseGEInterface);
    assertNotSame(system.getPhase(1), system.getPhase(2));
    assertTrue(system.getPhase(3) instanceof PhasePureComponentSolid);

    SystemVanLaarActivitySRK clone = system.clone();
    assertTrue(clone.getEquationOfStatePhase() instanceof PhaseEos);
    assertTrue(clone.isExcessGibbsEnergyPhase(1));
    assertNotSame(system.getPhase(0), clone.getPhase(0));
    assertNotSame(system.getPhase(1), clone.getPhase(1));
  }

  /** Established Wilson, NRTL and UNIFAC systems use the same reusable phase topology. */
  @Test
  public void testEstablishedEosGeSystemsUseSharedTopology() {
    SystemEosGE[] systems = new SystemEosGE[] { new SystemGEWilson(273.15, 1.0), new SystemNRTL(273.15, 1.0),
        new SystemUNIFAC(273.15, 1.0), new SystemUNIFACpsrk(273.15, 1.0) };

    for (SystemEosGE system : systems) {
      assertTrue(system.getEquationOfStatePhase() instanceof PhaseEos);
      assertTrue(system.isExcessGibbsEnergyPhase(1));
    }
    assertEquals("Wilson-GE-model", systems[0].getModelName());
  }

  /** TP, PH, PS and TV flashes return finite states and recover their extensive specifications. */
  @Test
  public void testTpPhPsAndTvFlashes() {
    SystemVanLaarActivitySRK reference = createTwoPhaseSystem();
    ThermodynamicOperations referenceOperations = new ThermodynamicOperations(reference);
    referenceOperations.TPflash();
    reference.initProperties();

    assertEquals(2, reference.getNumberOfPhases());
    assertTrue(reference.getPhase(0) instanceof PhaseEos);
    assertTrue(reference.getPhase(1) instanceof PhaseGEVanLaarAcid);
    assertEquals(1.0, reference.getBeta(0) + reference.getBeta(1), 1.0e-12);
    assertTrue(Double.isFinite(reference.getPhase(1).getdPdVTn()));
    assertTrue(reference.getPhase(1).getdPdVTn() < 0.0);
    assertEquals(1.0e-12, reference.getPhase(1).getIsothermalCompressibility(), 1.0e-18);
    assertTrue(Double.isFinite(reference.getdPdVtn()));
    assertTrue(Double.isFinite(reference.getKappa()));

    double targetTemperature = reference.getTemperature();
    double targetPressure = reference.getPressure();
    double targetEnthalpy = reference.getEnthalpy();
    double targetEntropy = reference.getEntropy();
    double targetVolume = reference.getVolume();
    assertTrue(Double.isFinite(targetEnthalpy));
    assertTrue(Double.isFinite(targetEntropy));
    assertTrue(Double.isFinite(targetVolume) && targetVolume > 0.0);

    SystemVanLaarActivitySRK phSystem = reference.clone();
    phSystem.setTemperature(targetTemperature + 3.0);
    new ThermodynamicOperations(phSystem).PHflash(targetEnthalpy);
    phSystem.initProperties();
    assertEquals(targetEnthalpy, phSystem.getEnthalpy(), Math.max(1.0e-6, Math.abs(targetEnthalpy) * 1.0e-5));
    assertEquals(targetTemperature, phSystem.getTemperature(), 0.1);

    SystemVanLaarActivitySRK psSystem = reference.clone();
    psSystem.setTemperature(targetTemperature + 3.0);
    new ThermodynamicOperations(psSystem).PSflash(targetEntropy);
    psSystem.initProperties();
    assertEquals(targetEntropy, psSystem.getEntropy(), Math.max(1.0e-6, Math.abs(targetEntropy) * 1.0e-5));
    assertEquals(targetTemperature, psSystem.getTemperature(), 0.1);

    SystemVanLaarActivitySRK tvSystem = reference.clone();
    tvSystem.setPressure(targetPressure * 1.1);
    new ThermodynamicOperations(tvSystem).TVflash(targetVolume);
    tvSystem.initProperties();
    assertEquals(targetVolume, tvSystem.getVolume(), Math.abs(targetVolume) * 1.0e-5);
    assertEquals(targetPressure, tvSystem.getPressure(), 1.0e-3);
  }

  /** Bubble and dew pressure flashes reproduce deterministic low-pressure gamma-phi boundaries. */
  @Test
  public void testBubbleAndDewPressureFlashes() throws IsNaNException {
    double temperature = 273.15;
    double[] liquidComposition = NitricSulfuricAcidVaporPressure.moleFractionsFromMassFractions(60.0, 20.0, 20.0);
    double[] partialPressuresBar = new double[] {
        NitricSulfuricAcidVaporPressure.partialPressureWater(liquidComposition[0], liquidComposition[1],
            liquidComposition[2], temperature) / PASCALS_PER_BAR,
        NitricSulfuricAcidVaporPressure.partialPressureNitricAcid(liquidComposition[0], liquidComposition[1],
            liquidComposition[2], temperature) / PASCALS_PER_BAR,
        NitricSulfuricAcidVaporPressure.partialPressureSulfuricAcid(liquidComposition[0], liquidComposition[1],
            liquidComposition[2], temperature) / PASCALS_PER_BAR };
    double expectedPressure = partialPressuresBar[0] + partialPressuresBar[1] + partialPressuresBar[2];

    SystemVanLaarActivitySRK bubbleSystem = new SystemVanLaarActivitySRK(temperature, 1.0);
    bubbleSystem.addComponent("water", liquidComposition[0]);
    bubbleSystem.addComponent("nitric acid", liquidComposition[1]);
    bubbleSystem.addComponent("sulfuric acid", liquidComposition[2]);
    bubbleSystem.createDatabase(true);
    bubbleSystem.setMixingRule("classic");
    new ThermodynamicOperations(bubbleSystem).bubblePointPressureFlash(false);
    assertEquals(expectedPressure, bubbleSystem.getPressure(), expectedPressure * 0.02);
    for (int componentIndex = 0; componentIndex < partialPressuresBar.length; componentIndex++) {
      assertEquals(partialPressuresBar[componentIndex] / expectedPressure,
          bubbleSystem.getPhase(0).getComponent(componentIndex).getx(), 2.0e-3);
    }

    double pureWaterPressure = NitricSulfuricAcidVaporPressure.pureVaporPressureWater(temperature) / PASCALS_PER_BAR;
    SystemVanLaarActivitySRK dewSystem = new SystemVanLaarActivitySRK(temperature, 1.0);
    dewSystem.addComponent("water", 1.0);
    dewSystem.createDatabase(true);
    dewSystem.setMixingRule("classic");
    new ThermodynamicOperations(dewSystem).dewPointPressureFlash();
    assertEquals(pureWaterPressure, dewSystem.getPressure(), pureWaterPressure * 0.02);
    assertTrue(dewSystem.getPhase(0) instanceof PhaseEos);
    assertTrue(dewSystem.getPhase(1) instanceof PhaseGEVanLaarAcid);
  }

  /** A disappearing GE phase retains finite limiting compressibilities at exactly zero volume. */
  @Test
  public void testGePhaseCompressibilitiesAtZeroVolume() {
    PhaseGEVanLaarAcid tracePhase = new PhaseGEVanLaarAcid();
    tracePhase.setPressure(50.0);

    assertEquals(0.0, tracePhase.getTotalVolume(), 0.0);
    assertEquals(0.0, tracePhase.getCompressibilityX(), 0.0);
    assertEquals(-5.0e-11, tracePhase.getCompressibilityY(), 1.0e-24);
    assertEquals(1.0e-12, tracePhase.getIsothermalCompressibility(), 0.0);
    assertTrue(Double.isFinite(tracePhase.getCompressibilityX()));
    assertTrue(Double.isFinite(tracePhase.getCompressibilityY()));
    assertTrue(Double.isFinite(tracePhase.getIsothermalCompressibility()));
  }

  /** GE phase properties honour the extensive heat-capacity contract and the constant-density volume API. */
  @Test
  public void testGePhaseExtensiveHeatCapacityAndMolarVolume() throws Exception {
    SystemVanLaarActivitySRK system = createTwoPhaseSystem();
    new ThermodynamicOperations(system).TPflash();
    system.initProperties();

    PhaseGEVanLaarAcid liquid = (PhaseGEVanLaarAcid) system.getPhase(1);
    double molarHeatCapacity = 0.0;
    for (int componentIndex = 0; componentIndex < liquid.getNumberOfComponents(); componentIndex++) {
      molarHeatCapacity += liquid.getComponent(componentIndex).getx()
          * liquid.getComponent(componentIndex).getPureComponentCpLiquid(liquid.getTemperature());
    }
    double expectedHeatCapacity = molarHeatCapacity * liquid.getNumberOfMolesInPhase();

    assertEquals(expectedHeatCapacity, liquid.getCp(), Math.abs(expectedHeatCapacity) * 1.0e-12);
    assertEquals(liquid.getCp() * liquid.getTemperature(), liquid.getEnthalpy(),
        Math.abs(liquid.getEnthalpy()) * 1.0e-12);
    assertEquals(liquid.getMolarVolume(),
        liquid.molarVolume(liquid.getPressure(), liquid.getTemperature(), 0.0, 0.0, PhaseType.LIQUID), 0.0);
  }

  /** Direct gamma-phi acceptance rejects a material role-correct split whose component fugacities disagree. */
  @Test
  public void testGammaPhiAcceptanceRejectsFugacityInconsistentMaterialSplit() {
    SystemVanLaarActivitySRK system = createTwoPhaseSystem();
    new ThermodynamicOperations(system).TPflash();
    system.initProperties();

    double waterFraction = system.getPhase(0).getComponent("water").getx();
    system.getPhase(0).getComponent("water").setx(Math.min(0.1, waterFraction * 100.0));

    assertFalse(system.finishGammaPhiFlash(0.0, 1.0e-10),
        "Correct carrier/activity phase roles must not mask unequal material-component fugacities");
  }

  /** Direct gamma-phi acceptance rejects zero K values before logarithmic iteration can consume them. */
  @Test
  public void testGammaPhiAcceptanceRejectsZeroKValue() {
    SystemVanLaarActivitySRK system = createTwoPhaseSystem();
    new ThermodynamicOperations(system).TPflash();
    system.initProperties();
    system.getPhase(0).getComponent(0).setK(0.0);
    system.getPhase(1).getComponent(0).setK(0.0);

    assertFalse(system.finishGammaPhiFlash(0.0, 1.0e-10));
  }

  /** Direct EOS-GE flashes fail fast instead of silently bypassing requested solid processing. */
  @Test
  public void testDirectGammaPhiRejectsSolidPhaseCheck() {
    SystemVanLaarActivitySRK system = new SystemVanLaarActivitySRK(273.15, 1.0, true);
    system.addComponent("CO2", 10.0);
    system.addComponent("water", 0.70);
    system.addComponent("nitric acid", 0.15);
    system.addComponent("sulfuric acid", 0.15);
    system.createDatabase(true);
    system.setMixingRule("classic");

    UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
        () -> new ThermodynamicOperations(system).TPflash());
    assertTrue(exception.getMessage().contains("solid or wax"));
  }

  /** Direct EOS-GE flashes fail fast instead of silently bypassing requested wax processing. */
  @Test
  public void testDirectGammaPhiRejectsWaxCheck() {
    SystemVanLaarActivitySRK system = createTwoPhaseSystem();
    system.setMultiphaseWaxCheck(true);

    UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
        () -> new ThermodynamicOperations(system).TPflash());
    assertTrue(exception.getMessage().contains("solid or wax"));
  }

}
