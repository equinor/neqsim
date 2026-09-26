package neqsim.thermo.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.component.ComponentGEUnifac;
import neqsim.thermo.component.ComponentGEWilson;
import neqsim.thermo.component.ComponentGEInterface;
import neqsim.thermo.mixingrule.EosMixingRulesInterface;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermo.phase.PhaseAmmoniaEos;
import neqsim.thermo.phase.PhaseGENRTL;
import neqsim.thermo.phase.PhaseGEUnifac;
import neqsim.thermo.phase.PhaseGERG2008Eos;
import neqsim.thermo.phase.PhaseIdealGas;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseLeachmanEos;
import neqsim.thermo.phase.PhaseSpanWagnerEos;
import neqsim.thermo.phase.PhaseVegaEos;
import neqsim.thermo.phase.PhasePrEos;
import neqsim.thermo.phase.PhaseSrkEos;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemGEWilson;
import neqsim.thermo.system.SystemAmmoniaEos;
import neqsim.thermo.system.SystemGERG2008Eos;
import neqsim.thermo.system.SystemIdealGas;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemLeachmanEos;
import neqsim.thermo.system.SystemSpanWagnerEos;
import neqsim.thermo.system.SystemVegaEos;
import neqsim.thermo.system.SystemNRTL;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemUMRPRUEos;
import neqsim.thermo.system.SystemUNIFAC;
import neqsim.thermo.system.SystemUNIFACpsrk;
import neqsim.thermo.util.gerg.GERG2008Type;
import neqsim.thermo.util.gerg.NeqSimGERG2008;
import neqsim.thermo.util.leachman.NeqSimLeachman;
import neqsim.thermo.util.Vega.NeqSimVega;

/** Nearby-state checks complement fixed anchors; all comparisons drive production APIs. */
class ModelSpecStateTest extends neqsim.NeqSimTest {
  private static final double[][] SRK_STATES = {{280.0, 10.0, 0.9785422334202201, 0.9786921663056776},
      {320.0, 50.0, 0.9433373091166810, 0.9413806064762114}, {300.0, 30.0, 0.9523798940724555, 0.9523599084051405},
      {280.0, 10.0, 0.9785422334202201, 0.9786921663056776}};
  private static final double[][] PR_STATES = {{280.0, 10.0, 0.9731154698320194, 0.9733220882952629},
      {320.0, 50.0, 0.9228085822220202, 0.9208014010997394}, {300.0, 30.0, 0.9382462505658514, 0.9383996714610695},
      {280.0, 10.0, 0.9731154698320194, 0.9733220882952629}};

  @Test
  void wilsonRefreshesStoredValueAfterCompositionChanges() {
    SystemGEWilson system = new SystemGEWilson(298.15, 1.0);
    system.addComponent("methanol", 0.5);
    system.addComponent("water", 0.5);
    system.setMixingRule("classic");
    system.init(0);
    PhaseInterface liquid = system.getPhase(1);
    for (int i = 0; i < 2; i++) {
      liquid.getcomponentArray()[i] = new ComponentGEWilson(i == 0 ? "methanol" : "water", 0.5, 0.5, i) {
        private static final long serialVersionUID = 1L;

        @Override
        public double getCharEnergyParamter(PhaseInterface phase, int first, int second) {
          return first == second ? 1.0 : first == 0 ? 2.0 : 0.5;
        }
      };
      liquid.getComponent(i).setx(0.5);
    }
    ComponentGEWilson methanol = (ComponentGEWilson) liquid.getComponent(0);
    double first = methanol.getGamma(liquid, 2, 298.15, 1.0, PhaseType.LIQUID);
    assertEquals(Math.exp(1.0 / 3.0) / 1.5, first, 1e-12);
    liquid.getComponent(0).setx(0.2);
    liquid.getComponent(1).setx(0.8);
    double second = methanol.getGamma(liquid, 2, 298.15, 1.0, PhaseType.LIQUID);
    assertEquals(Math.exp(4.0 / 9.0) / 1.8, second, 1e-12);
    assertEquals(second, methanol.getGamma(), 1e-12);
    assertEquals(Math.log(second), methanol.getLnGamma(), 1e-12);
    assertNotEquals(first, second);
  }

  @Test
  void nrtlRefreshesStoredValuesAcrossCompositionAndTemperatureChanges() {
    SystemNRTL system = new SystemNRTL(298.15, 1.0);
    system.addComponent("methanol", 0.2);
    system.addComponent("water", 0.8);
    system.setMixingRule("classic");
    system.init(0);
    PhaseGENRTL phase = (PhaseGENRTL) system.getPhase(1);
    phase.setAlpha(new double[][] {{0.0, 0.3}, {0.3, 0.0}});
    phase.setDij(new double[][] {{0.0, 200.0}, {-100.0, 0.0}});

    double[][] states = {{298.15, 0.2}, {323.15, 0.8}, {298.15, 0.5}, {298.15, 0.2}};
    double firstGamma = Double.NaN;
    for (double[] state : states) {
      phase.setTemperature(state[0]);
      phase.getComponent(0).setx(state[1]);
      phase.getComponent(1).setx(1.0 - state[1]);
      double[] expected = nrtl(state[1], state[0]);
      double excess = phase.getExcessGibbsEnergy(phase, 2, state[0], 1.0, PhaseType.LIQUID)
          / phase.getNumberOfMolesInPhase();
      assertEquals(expected[0], ((ComponentGEInterface) phase.getComponent(0)).getGamma(), 1e-12);
      assertEquals(expected[1], ((ComponentGEInterface) phase.getComponent(1)).getGamma(), 1e-12);
      assertEquals(Math.log(expected[0]), ((ComponentGEInterface) phase.getComponent(0)).getLnGamma(), 1e-12);
      assertEquals(Math.log(expected[1]), ((ComponentGEInterface) phase.getComponent(1)).getLnGamma(), 1e-12);
      assertEquals(expected[2], excess, 1e-9);
      if (Double.isNaN(firstGamma)) {
        firstGamma = expected[0];
      }
    }
    assertEquals(firstGamma, ((ComponentGEInterface) phase.getComponent(0)).getGamma(), 1e-12,
        "returning to the initial state must restore the initial activity coefficient");
    assertEquals(Math.log(firstGamma), ((ComponentGEInterface) phase.getComponent(0)).getLnGamma(), 1e-12,
        "returning to the initial state must restore the initial logarithmic activity coefficient");
  }

  @Test
  void nrtlParameterMatricesFollowDeclaredComponentOrder() {
    PhaseGENRTL ordered = nrtlPhase(false);
    PhaseGENRTL reversed = nrtlPhase(true);
    double orderedExcess = ordered.getExcessGibbsEnergy(ordered, 2, 298.15, 1.0, PhaseType.LIQUID);
    double reversedExcess = reversed.getExcessGibbsEnergy(reversed, 2, 298.15, 1.0, PhaseType.LIQUID);
    assertEquals(orderedExcess, reversedExcess, 1e-10);
    for (String component : new String[] {"methanol", "water"}) {
      double first = ((ComponentGEInterface) ordered.getComponent(component)).getGamma();
      double second = ((ComponentGEInterface) reversed.getComponent(component)).getGamma();
      ModelSpecFixtures.positive(first, component);
      assertEquals(first, second, 1e-12, component);
      assertEquals(((ComponentGEInterface) ordered.getComponent(component)).getLnGamma(),
          ((ComponentGEInterface) reversed.getComponent(component)).getLnGamma(), 1e-12,
          component + " logarithmic activity coefficient");
      assertEquals(Math.log(first), ((ComponentGEInterface) ordered.getComponent(component)).getLnGamma(), 1e-12,
          component);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"UNIFAC", "PSRK", "UMR"})
  void binaryGroupModelsAreOrderIndependentAndReinitializable(String model) {
    SystemInterface ordered = groupSystem(model, false);
    SystemInterface reversed = groupSystem(model, true);
    for (double temperature : new double[] {290.0, 310.0, 290.0}) {
      ordered.setTemperature(temperature);
      reversed.setTemperature(temperature);
      ordered.init(0);
      reversed.init(0);
      for (String component : new String[] {"methanol", "water"}) {
        double first = gamma(ordered, component, model);
        double second = gamma(reversed, component, model);
        ModelSpecFixtures.positive(first, component);
        assertEquals(first, second, 1e-10, component);
      }
    }
  }

  @Test
  void classicUnifacRefreshesPublishedNonidealState() {
    SystemInterface system = groupSystem("UNIFAC", false);
    double[][] states = {{298.15, 0.2}, {323.15, 0.8}, {298.15, 0.5}, {298.15, 0.2}};
    for (double[] state : states) {
      system.setTemperature(state[0]);
      system.setMolarComposition(new double[] {state[1], 1.0 - state[1]});
      system.init(0);
      PhaseGEUnifac phase = (PhaseGEUnifac) system.getPhase(1);
      double[] expected = ModelSpecHarnessTest.originalUnifac(state[1], state[0]);
      double excess = phase.getExcessGibbsEnergy(phase, 2, state[0], 1.0, PhaseType.LIQUID)
          / phase.getNumberOfMolesInPhase();
      assertEquals(expected[0], ((ComponentGEInterface) phase.getComponent("methanol")).getGamma(), 3.5e-4);
      assertEquals(expected[1], ((ComponentGEInterface) phase.getComponent("water")).getGamma(), 3.5e-4);
      assertEquals(expected[2], ((ComponentGEInterface) phase.getComponent("methanol")).getLnGamma(), 2.5e-4);
      assertEquals(expected[3], ((ComponentGEInterface) phase.getComponent("water")).getLnGamma(), 2.5e-4);
      assertEquals(expected[4], excess, 0.25);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void cubicModelsRefreshPublishedStateAtNearbyConditions(boolean pengRobinson) {
    SystemInterface system = cubicSystem(pengRobinson, 280.0, 10.0, 1.0);
    double[][] states = pengRobinson ? PR_STATES : SRK_STATES;
    Class<?> phaseType = pengRobinson ? PhasePrEos.class : PhaseSrkEos.class;
    for (double[] state : states) {
      system.setTemperature(state[0]);
      system.setPressure(state[1]);
      system.init(1);
      PhaseInterface phase = system.getPhase(0);
      assertEquals(phaseType, phase.getClass());
      double fugacityCoefficient = phase.getComponent(0).getFugacityCoefficient();
      assertEquals(state[3], fugacityCoefficient, 1e-12);
      assertEquals(state[2], phase.getZ(), 1e-12);
      assertEquals(fugacityCoefficient, phase.getComponent(0).getFugacityCoefficient(), 0.0,
          "reading Z must not stale or replace stored phi");
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void cubicCaloricIdentitiesUseOneConsistentExtensiveBasis(boolean pengRobinson) {
    SystemInterface system = cubicSystem(pengRobinson, 300.0, 30.0, 2.0);
    system.init(3);
    PhaseInterface phase = system.getPhase(0);
    double moles = phase.getNumberOfMolesInPhase();
    double enthalpy = phase.getEnthalpy();
    double internalEnergy = phase.getInternalEnergy();
    double entropy = phase.getEntropy();
    double gibbsEnergy = phase.getGibbsEnergy();
    double pressureVolume = phase.getPressure() * phase.getMolarVolume() * moles;
    assertTrue(Double.isFinite(enthalpy) && Double.isFinite(internalEnergy) && Double.isFinite(entropy)
        && Double.isFinite(gibbsEnergy) && Double.isFinite(pressureVolume));
    assertEquals(enthalpy, internalEnergy + pressureVolume, Math.max(1e-9, Math.abs(enthalpy) * 1e-12));
    assertEquals(gibbsEnergy, enthalpy - phase.getTemperature() * entropy,
        Math.max(1e-9, Math.abs(gibbsEnergy) * 1e-12));
    assertEquals(enthalpy / moles, phase.getEnthalpy("J/mol"), 1e-12);
    assertEquals(internalEnergy / moles, phase.getInternalEnergy("J/mol"), 1e-12);
    assertEquals(entropy / moles, phase.getEntropy("J/molK"), 1e-12);
    system.init(3);
    assertEquals(enthalpy, phase.getEnthalpy(), Math.max(1e-9, Math.abs(enthalpy) * 1e-12));
    assertEquals(gibbsEnergy, phase.getGibbsEnergy(), Math.max(1e-9, Math.abs(gibbsEnergy) * 1e-12));
  }

  @Test
  void gergReferenceStateRefreshesAcrossTemperatureAndPressureChanges() {
    SystemGERG2008Eos system = gergReferenceSystem();
    system.init(0);
    system.init(1);
    PhaseGERG2008Eos phase = (PhaseGERG2008Eos) system.getPhase(0);
    assertEquals(GERG2008Type.STANDARD, phase.getGergModelType());
    double[] reference = phase.getProperties_GERG2008();
    NeqSimGERG2008 gerg = new NeqSimGERG2008(phase, GERG2008Type.STANDARD);
    double referenceDensity = gerg.getMolarDensity();
    assertEquals(12.79828626082062, referenceDensity, 1e-10);
    assertEquals(1.174690666383717, reference[1], 1e-12);
    assertEquals(1160.280160510973, phase.getEnthalpy() / phase.getNumberOfMolesInPhase(), 1e-7);
    assertEquals(-2746.492901212530, phase.getInternalEnergy() / phase.getNumberOfMolesInPhase(), 1e-7);
    assertEquals(-38.57590392409089, phase.getEntropy() / phase.getNumberOfMolesInPhase(), 1e-9);
    assertEquals(16590.64173014733, phase.getGibbsEnergy() / phase.getNumberOfMolesInPhase(), 1e-7);

    system.setTemperature(350.0);
    system.setPressure(100.0);
    system.init(1);
    double[] nearby = phase.getProperties_GERG2008();
    double nearbyDensity = new NeqSimGERG2008(phase, GERG2008Type.STANDARD).getMolarDensity();
    assertNotEquals(reference[1], nearby[1]);
    assertNotEquals(referenceDensity, nearbyDensity);
    for (double value : nearby) {
      assertTrue(Double.isFinite(value));
    }

    system.init(1);
    double[] repeated = phase.getProperties_GERG2008();
    assertEquals(nearbyDensity, new NeqSimGERG2008(phase, GERG2008Type.STANDARD).getMolarDensity(), 0.0);
    for (int i = 0; i < nearby.length; i++) {
      assertEquals(nearby[i], repeated[i], 0.0, "GERG repeat property " + i);
    }

    system.setTemperature(400.0);
    system.setPressure(500.0);
    system.init(1);
    double[] returned = phase.getProperties_GERG2008();
    assertEquals(referenceDensity, new NeqSimGERG2008(phase, GERG2008Type.STANDARD).getMolarDensity(), 1e-10);
    for (int i = 0; i < reference.length; i++) {
      assertEquals(reference[i], returned[i], Math.max(1e-12, Math.abs(reference[i]) * 1e-12),
          "GERG returned property " + i);
    }
  }

  @Test
  void idealGasRefreshesExactAndCaloricStateBeforeReturningToReference() {
    SystemIdealGas system = new SystemIdealGas(298.15, 1.0);
    system.addComponent("argon", 1.0);
    system.init(1);
    PhaseIdealGas phase = (PhaseIdealGas) system.getPhase(0);
    double firstDensity = phase.getDensity("mol/m3") / 1000.0;
    double firstCp = phase.getCp("J/molK");
    double firstSoundSpeed = phase.getSoundSpeed();
    assertEquals(1.0, phase.getZ(), 0.0);
    assertEquals(1.0, phase.getComponent(0).fugcoef(phase), 0.0);
    assertEquals(0.0, phase.getJouleThomsonCoefficient(), 0.0);

    system.setTemperature(600.0);
    system.setPressure(5.0);
    system.init(1);
    double nearbyDensity = phase.getDensity("mol/m3") / 1000.0;
    double nearbyCp = phase.getCp("J/molK");
    double nearbySoundSpeed = phase.getSoundSpeed();
    assertNotEquals(firstDensity, nearbyDensity);
    assertNotEquals(firstCp, nearbyCp);
    assertNotEquals(firstSoundSpeed, nearbySoundSpeed);
    assertEquals(1.0, phase.getZ(), 0.0);
    assertEquals(1.0, phase.getComponent(0).fugcoef(phase), 0.0);
    assertEquals(0.0, phase.getJouleThomsonCoefficient(), 0.0);

    system.init(1);
    assertEquals(nearbyDensity, phase.getDensity("mol/m3") / 1000.0, 0.0);
    assertEquals(nearbyCp, phase.getCp("J/molK"), 0.0);
    assertEquals(nearbySoundSpeed, phase.getSoundSpeed(), 0.0);

    system.setTemperature(298.15);
    system.setPressure(1.0);
    system.init(1);
    assertEquals(firstDensity, phase.getDensity("mol/m3") / 1000.0, 0.0);
    assertEquals(firstCp, phase.getCp("J/molK"), 0.0);
    assertEquals(firstSoundSpeed, phase.getSoundSpeed(), 0.0);
  }

  @Test
  void ammoniaRefreshesGasAndLiquidStateBeforeReturningToReference() {
    SystemAmmoniaEos system = new SystemAmmoniaEos(293.15, 5.0);
    system.setNumberOfPhases(1);
    system.setMaxNumberOfPhases(1);
    system.setForcePhaseTypes(true);

    double[] first = ammoniaState(system, 293.15, 5.0, PhaseType.GAS);
    double[] hotGas = ammoniaState(system, 400.0, 50.0, PhaseType.GAS);
    double[] warmLiquid = ammoniaState(system, 293.15, 10.0, PhaseType.LIQUID);
    double[] coldLiquid = ammoniaState(system, 280.0, 10.0, PhaseType.LIQUID);
    assertNotEquals(first[0], hotGas[0], "gas density must refresh");
    assertNotEquals(hotGas[0], warmLiquid[0], "phase-forced density must refresh");
    assertNotEquals(warmLiquid[1], coldLiquid[1], "liquid enthalpy must refresh");

    double[] returned = ammoniaState(system, 293.15, 5.0, PhaseType.GAS);
    for (int i = 0; i < first.length; i++) {
      assertEquals(first[i], returned[i], Math.max(1e-12, Math.abs(first[i]) * 1e-12),
          "ammonia returned property " + i);
    }
  }

  @Test
  void leachmanRefreshesGasAndLiquidStateBeforeReturningToReference() {
    SystemLeachmanEos system = new SystemLeachmanEos(300.0, 10.0);
    system.setNumberOfPhases(1);
    system.setMaxNumberOfPhases(1);
    system.setForcePhaseTypes(true);

    double[] first = leachmanState(system, 300.0, 10.0, PhaseType.GAS);
    double[] coldGas = leachmanState(system, 100.0, 50.0, PhaseType.GAS);
    double[] warmLiquid = leachmanState(system, 25.0, 10.0, PhaseType.LIQUID);
    double[] coldLiquid = leachmanState(system, 20.0, 5.0, PhaseType.LIQUID);
    assertNotEquals(first[0], coldGas[0], "gas density must refresh");
    assertNotEquals(coldGas[0], warmLiquid[0], "phase-forced density must refresh");
    assertNotEquals(warmLiquid[1], coldLiquid[1], "liquid enthalpy must refresh");

    double[] returned = leachmanState(system, 300.0, 10.0, PhaseType.GAS);
    for (int i = 0; i < first.length; i++) {
      assertEquals(first[i], returned[i], Math.max(1e-12, Math.abs(first[i]) * 1e-12),
          "Leachman returned property " + i);
    }
  }

  private static double[] leachmanState(SystemLeachmanEos system, double temperature, double pressure,
      PhaseType phaseType) {
    system.setTemperature(temperature);
    system.setPressure(pressure);
    system.setPhaseType(0, phaseType);
    system.init(3);
    assertEquals(PhaseLeachmanEos.class, system.getPhase(0).getClass());
    PhaseLeachmanEos phase = (PhaseLeachmanEos) system.getPhase(0);
    NeqSimLeachman leachman = new NeqSimLeachman(phase, "normal");
    double[] raw = leachman.propertiesLeachman();
    double density = leachman.getMolarDensity();
    double enthalpy = phase.getEnthalpy("J/mol");
    double internalEnergy = phase.getInternalEnergy("J/mol");
    double entropy = phase.getEntropy("J/molK");
    double gibbsEnergy = phase.getGibbsEnergy() / phase.getNumberOfMolesInPhase();
    double cp = phase.getCp("J/molK");
    double cv = phase.getCv("J/molK");
    double soundSpeed = phase.getSoundSpeed();
    double jouleThomson = phase.getJouleThomsonCoefficient() / 1000.0;
    double kappa = raw[14];
    double z = phase.getZ();
    for (double value : new double[] {density, enthalpy, internalEnergy, entropy, gibbsEnergy, cp, cv, soundSpeed,
        jouleThomson, kappa, z}) {
      assertTrue(Double.isFinite(value));
    }
    assertTrue(density > 0.0 && cp > cv && cv > 0.0 && soundSpeed > 0.0 && kappa > 0.0 && z > 0.0,
        "invalid Leachman state: density=" + density + ", cp=" + cp + ", cv=" + cv + ", sound=" + soundSpeed
            + ", kappa=" + kappa + ", Z=" + z);
    assertEquals(enthalpy, internalEnergy + pressure * 100.0 / density, Math.max(1e-9, Math.abs(enthalpy) * 1e-12));
    assertEquals(gibbsEnergy, enthalpy - temperature * entropy, Math.max(1e-9, Math.abs(gibbsEnergy) * 1e-12));
    double[] values = {density, enthalpy, internalEnergy, entropy, gibbsEnergy, cp, cv, soundSpeed, jouleThomson, kappa,
        z};
    system.init(3);
    double[] repeated = leachmanStateWithoutInit((PhaseLeachmanEos) system.getPhase(0));
    for (int i = 0; i < values.length; i++) {
      assertEquals(values[i], repeated[i], Math.max(1e-15, Math.abs(values[i]) * 1e-14),
          "Leachman repeat property " + i);
    }
    return values;
  }

  private static double[] leachmanStateWithoutInit(PhaseLeachmanEos phase) {
    NeqSimLeachman leachman = new NeqSimLeachman(phase, "normal");
    double[] raw = leachman.propertiesLeachman();
    return new double[] {leachman.getMolarDensity(), phase.getEnthalpy("J/mol"), phase.getInternalEnergy("J/mol"),
        phase.getEntropy("J/molK"), phase.getGibbsEnergy() / phase.getNumberOfMolesInPhase(), phase.getCp("J/molK"),
        phase.getCv("J/molK"), phase.getSoundSpeed(), phase.getJouleThomsonCoefficient() / 1000.0, raw[14],
        phase.getZ()};
  }

  @Test
  void vegaRefreshesGasStateBeforeReturningToReference() {
    SystemVegaEos system = new SystemVegaEos(300.0, 10.0);
    system.setNumberOfPhases(1);
    system.setMaxNumberOfPhases(1);
    system.setForcePhaseTypes(true);

    double[] first = vegaState(system, 300.0, 10.0);
    double[] warmDense = vegaState(system, 250.0, 25.0);
    double[] coolDense = vegaState(system, 150.0, 50.0);
    double[] coldDense = vegaState(system, 100.0, 50.0);
    assertNotEquals(first[0], warmDense[0], "Vega density must refresh");
    assertNotEquals(warmDense[0], coolDense[0], "Vega density must refresh again");
    assertNotEquals(coolDense[1], coldDense[1], "Vega enthalpy must refresh");

    double[] returned = vegaState(system, 300.0, 10.0);
    for (int i = 0; i < first.length; i++) {
      assertEquals(first[i], returned[i], Math.max(1e-12, Math.abs(first[i]) * 1e-12), "Vega returned property " + i);
    }
  }

  private static double[] vegaState(SystemVegaEos system, double temperature, double pressure) {
    system.setTemperature(temperature);
    system.setPressure(pressure);
    system.setPhaseType(0, PhaseType.GAS);
    system.init(3);
    assertEquals(PhaseVegaEos.class, system.getPhase(0).getClass());
    PhaseVegaEos phase = (PhaseVegaEos) system.getPhase(0);
    double[] values = vegaStateWithoutInit(phase);
    double density = values[0];
    double enthalpy = values[1];
    double internalEnergy = values[2];
    double entropy = values[3];
    double gibbsEnergy = values[4];
    double cp = values[5];
    double cv = values[6];
    double soundSpeed = values[7];
    double kappa = values[9];
    double z = values[10];
    assertTrue(density > 0.0 && cp > cv && cv > 0.0 && soundSpeed > 0.0 && kappa > 0.0 && z > 0.0,
        "invalid Vega state: density=" + density + ", cp=" + cp + ", cv=" + cv + ", sound=" + soundSpeed + ", kappa="
            + kappa + ", Z=" + z);
    assertEquals(enthalpy, internalEnergy + pressure * 100.0 / density, Math.max(1e-9, Math.abs(enthalpy) * 1e-12));
    assertEquals(gibbsEnergy, enthalpy - temperature * entropy, Math.max(1e-9, Math.abs(gibbsEnergy) * 1e-12));
    system.init(3);
    double[] repeated = vegaStateWithoutInit((PhaseVegaEos) system.getPhase(0));
    for (int i = 0; i < values.length; i++) {
      assertEquals(values[i], repeated[i], Math.max(1e-15, Math.abs(values[i]) * 1e-14), "Vega repeat property " + i);
    }
    return values;
  }

  private static double[] vegaStateWithoutInit(PhaseVegaEos phase) {
    NeqSimVega vega = new NeqSimVega(phase);
    double[] raw = vega.propertiesVega();
    double[] values = {vega.getMolarDensity(), phase.getEnthalpy("J/mol"), phase.getInternalEnergy("J/mol"),
        phase.getEntropy("J/molK"), phase.getGibbsEnergy() / phase.getNumberOfMolesInPhase(), phase.getCp("J/molK"),
        phase.getCv("J/molK"), phase.getSoundSpeed(), phase.getJouleThomsonCoefficient() / 1000.0, raw[14],
        phase.getZ()};
    for (double value : values) {
      assertTrue(Double.isFinite(value));
    }
    return values;
  }

  @Test
  void spanWagnerRefreshesGasLiquidAndSupercriticalStateBeforeReturningToReference() {
    SystemSpanWagnerEos system = new SystemSpanWagnerEos(300.0, 10.0);
    system.setNumberOfPhases(1);
    system.setMaxNumberOfPhases(1);
    system.setForcePhaseTypes(true);

    double[] first = spanWagnerState(system, 300.0, 10.0, PhaseType.GAS);
    double[] liquid = spanWagnerState(system, 280.0, 50.0, PhaseType.LIQUID);
    double[] supercritical = spanWagnerState(system, 320.0, 80.0, PhaseType.GAS);
    double[] denseSupercritical = spanWagnerState(system, 350.0, 200.0, PhaseType.GAS);
    assertNotEquals(first[0], liquid[0], "Span-Wagner density must refresh");
    assertNotEquals(liquid[0], supercritical[0], "Span-Wagner density must refresh again");
    assertNotEquals(supercritical[1], denseSupercritical[1], "Span-Wagner enthalpy must refresh");

    double[] returned = spanWagnerState(system, 300.0, 10.0, PhaseType.GAS);
    for (int i = 0; i < first.length; i++) {
      assertEquals(first[i], returned[i], 0.0, "Span-Wagner returned property " + i);
    }
  }

  private static double[] spanWagnerState(SystemSpanWagnerEos system, double temperature, double pressure,
      PhaseType phaseType) {
    system.setTemperature(temperature);
    system.setPressure(pressure);
    system.setPhaseType(0, phaseType);
    system.init(3);
    assertEquals(PhaseSpanWagnerEos.class, system.getPhase(0).getClass());
    PhaseSpanWagnerEos phase = (PhaseSpanWagnerEos) system.getPhase(0);
    double[] values = spanWagnerStateWithoutInit(phase);
    double density = values[0];
    double enthalpy = values[1];
    double internalEnergy = values[2];
    double entropy = values[3];
    double gibbsEnergy = values[4];
    double cp = values[5];
    double cv = values[6];
    double soundSpeed = values[7];
    double z = values[9];
    double phi = values[10];
    assertTrue(density > 0.0 && cp > cv && cv > 0.0 && soundSpeed > 0.0 && z > 0.0 && phi > 0.0,
        "invalid Span-Wagner state: density=" + density + ", cp=" + cp + ", cv=" + cv + ", sound=" + soundSpeed + ", Z="
            + z + ", phi=" + phi);
    assertEquals(enthalpy, internalEnergy + pressure * 1.0e5 / density, Math.max(1e-9, Math.abs(enthalpy) * 1e-12));
    assertEquals(gibbsEnergy, enthalpy - temperature * entropy, Math.max(1e-9, Math.abs(gibbsEnergy) * 1e-12));
    assertEquals(1.0, density * phase.getMolarVolume() / 1.0e5, 1e-12);

    system.init(3);
    double[] repeated = spanWagnerStateWithoutInit((PhaseSpanWagnerEos) system.getPhase(0));
    for (int i = 0; i < values.length; i++) {
      assertEquals(values[i], repeated[i], 0.0, "Span-Wagner repeat property " + i);
    }
    return values;
  }

  private static double[] spanWagnerStateWithoutInit(PhaseSpanWagnerEos phase) {
    double[] values = {phase.getDensity("mol/m3"), phase.getEnthalpy("J/mol"), phase.getInternalEnergy("J/mol"),
        phase.getEntropy("J/molK"), phase.getGibbsEnergy() / phase.getNumberOfMolesInPhase(), phase.getCp("J/molK"),
        phase.getCv("J/molK"), phase.getSoundSpeed(), phase.getJouleThomsonCoefficient(), phase.getZ(),
        phase.getComponent(0).getFugacityCoefficient(), phase.getMolarVolume(), phase.getdPdTVn(), phase.getdPdVTn()};
    for (double value : values) {
      assertTrue(Double.isFinite(value));
    }
    return values;
  }

  private static double[] ammoniaState(SystemAmmoniaEos system, double temperature, double pressure,
      PhaseType phaseType) {
    system.setTemperature(temperature);
    system.setPressure(pressure);
    system.setPhaseType(0, phaseType);
    system.init(3);
    assertEquals(PhaseAmmoniaEos.class, system.getPhase(0).getClass());
    PhaseAmmoniaEos phase = (PhaseAmmoniaEos) system.getPhase(0);
    double density = 1.0e5 / phase.getMolarVolume() / 1000.0;
    double enthalpy = phase.getEnthalpy("J/mol");
    double internalEnergy = phase.getInternalEnergy("J/mol");
    double cp = phase.getCp("J/molK");
    double cv = phase.getCv("J/molK");
    double soundSpeed = phase.getSoundSpeed();
    double compressibility = phase.getIsothermalCompressibility();
    double jouleThomson = phase.getJouleThomsonCoefficient() / 100.0;
    for (double value : new double[] {density, enthalpy, internalEnergy, cp, cv, soundSpeed, compressibility,
        jouleThomson}) {
      assertTrue(Double.isFinite(value));
    }
    assertTrue(density > 0.0 && cp > cv && cv > 0.0 && soundSpeed > 0.0 && compressibility > 0.0,
        "invalid ammonia state: density=" + density + ", cp=" + cp + ", cv=" + cv + ", sound=" + soundSpeed + ", kappa="
            + compressibility);
    assertEquals(enthalpy, internalEnergy + pressure * 100.0 / density, Math.max(1e-9, Math.abs(enthalpy) * 1e-12));
    double[] values = {density, enthalpy, internalEnergy, cp, cv, soundSpeed, compressibility, jouleThomson};
    system.init(3);
    assertEquals(PhaseAmmoniaEos.class, system.getPhase(0).getClass());
    PhaseAmmoniaEos repeated = (PhaseAmmoniaEos) system.getPhase(0);
    double[] repeatedValues = {1.0e5 / repeated.getMolarVolume() / 1000.0, repeated.getEnthalpy("J/mol"),
        repeated.getInternalEnergy("J/mol"), repeated.getCp("J/molK"), repeated.getCv("J/molK"),
        repeated.getSoundSpeed(), repeated.getIsothermalCompressibility(),
        repeated.getJouleThomsonCoefficient() / 100.0};
    for (int i = 0; i < values.length; i++) {
      assertEquals(values[i], repeatedValues[i], Math.max(1e-15, Math.abs(values[i]) * 1e-14),
          "ammonia repeat property " + i);
    }
    return values;
  }

  private static SystemGERG2008Eos gergReferenceSystem() {
    SystemGERG2008Eos system = new SystemGERG2008Eos(400.0, 500.0);
    String[] names = {"methane", "nitrogen", "CO2", "ethane", "propane", "i-butane", "n-butane", "i-pentane",
        "n-pentane", "n-hexane", "n-heptane", "n-octane", "n-nonane", "nC10", "hydrogen", "oxygen", "CO", "water",
        "H2S", "helium", "argon"};
    double[] amounts = {0.77824, 0.02, 0.06, 0.08, 0.03, 0.0015, 0.003, 0.0005, 0.00165, 0.00215, 0.00088, 0.00024,
        0.00015, 0.00009, 0.004, 0.005, 0.002, 0.0001, 0.0025, 0.007, 0.001};
    for (int i = 0; i < names.length; i++) {
      system.addComponent(names[i], amounts[i]);
    }
    return system;
  }

  private static SystemInterface groupSystem(String model, boolean reverse) {
    SystemInterface system = "PSRK".equals(model) ? new SystemUNIFACpsrk(290.0, 1.0)
        : "UMR".equals(model) ? new SystemUMRPRUEos(290.0, 1.0) : new SystemUNIFAC(290.0, 1.0);
    system.addComponent(reverse ? "water" : "methanol", reverse ? 0.7 : 0.3);
    system.addComponent(reverse ? "methanol" : "water", reverse ? 0.3 : 0.7);
    if ("UMR".equals(model)) {
      system.setMixingRule("HV", "UNIFAC_UMRPRU");
    } else {
      system.setMixingRule("classic");
    }
    system.init(0);
    return system;
  }

  private static SystemInterface cubicSystem(boolean pengRobinson, double temperature, double pressure, double moles) {
    SystemInterface system = pengRobinson ? new SystemPrEos(temperature, pressure)
        : new SystemSrkEos(temperature, pressure);
    system.addComponent("methane", moles);
    system.setMixingRule("classic");
    return system;
  }

  private static PhaseGENRTL nrtlPhase(boolean reverse) {
    SystemNRTL system = new SystemNRTL(298.15, 1.0);
    system.addComponent(reverse ? "water" : "methanol", reverse ? 0.8 : 0.2);
    system.addComponent(reverse ? "methanol" : "water", reverse ? 0.2 : 0.8);
    system.setMixingRule("classic");
    system.init(0);
    PhaseGENRTL phase = (PhaseGENRTL) system.getPhase(1);
    phase.setAlpha(new double[][] {{0.0, 0.3}, {0.3, 0.0}});
    phase.setDij(reverse ? new double[][] {{0.0, -100.0}, {200.0, 0.0}} : new double[][] {{0.0, 200.0}, {-100.0, 0.0}});
    return phase;
  }

  private static double gamma(SystemInterface system, String name, String model) {
    PhaseInterface phase = system.getPhase(1);
    if ("UMR".equals(model)) {
      system.init(1);
      phase = ((EosMixingRulesInterface) ((PhaseEosInterface) phase).getMixingRule()).getGEPhase();
    }
    ComponentGEUnifac component = (ComponentGEUnifac) phase.getComponent(name);
    assertTrue(component.getUnifacGroups().length > 0);
    double result = component.getGamma(phase, 2, system.getTemperature(), system.getPressure(), phase.getType());
    assertEquals(result, component.getGamma(), 1e-12);
    assertEquals(Math.log(result), component.getLnGamma(), 1e-12);
    return result;
  }

  private static double[] nrtl(double methanolFraction, double temperature) {
    double[] x = {methanolFraction, 1.0 - methanolFraction};
    double[][] alpha = {{0.0, 0.3}, {0.3, 0.0}};
    double[][] interaction = {{0.0, 200.0}, {-100.0, 0.0}};
    double[] gamma = new double[2];
    for (int i = 0; i < 2; i++) {
      double numerator = 0.0;
      double denominator = 0.0;
      for (int j = 0; j < 2; j++) {
        double tau = interaction[j][i] / temperature;
        double g = Math.exp(-alpha[j][i] * tau);
        numerator += x[j] * tau * g;
        denominator += x[j] * g;
      }
      double second = 0.0;
      for (int j = 0; j < 2; j++) {
        double tau = interaction[i][j] / temperature;
        double g = Math.exp(-alpha[i][j] * tau);
        double column = 0.0;
        double weightedColumn = 0.0;
        for (int k = 0; k < 2; k++) {
          double tauKj = interaction[k][j] / temperature;
          double gKj = Math.exp(-alpha[k][j] * tauKj);
          column += x[k] * gKj;
          weightedColumn += x[k] * tauKj * gKj;
        }
        second += x[j] * g / column * (tau - weightedColumn / column);
      }
      gamma[i] = Math.exp(numerator / denominator + second);
    }
    double excess = 8.3144621 * temperature * (x[0] * Math.log(gamma[0]) + x[1] * Math.log(gamma[1]));
    return new double[] {gamma[0], gamma[1], excess};
  }
}
