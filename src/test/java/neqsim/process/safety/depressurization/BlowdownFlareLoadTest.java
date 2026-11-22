package neqsim.process.safety.depressurization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import neqsim.process.safety.depressurization.DynamicBlowdownFlareStudyDataSource.BlowdownSource;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.flare.Flare;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.safety.depressurization.BlowdownFlareLoad.Discharge;
import neqsim.process.safety.depressurization.DepressurizationSimulator.DepressurizationResult;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Conservation and peak-time regressions for material-resolved transient flare loads.
 *
 * @author NeqSim
 * @version 1.0
 */
class BlowdownFlareLoadTest {
  /**
   * Build and flash a test feed.
   *
   * @param names ordered component names
   * @param amounts molar rates in mol/s
   * @param pressure feed pressure in bara
   * @return equilibrated SRK fluid at 300 K
   */
  private static SystemInterface fluid(String[] names, double[] amounts, double pressure) {
    SystemInterface fluid = new SystemSrkEos(300.0, pressure);
    for (int i = 0; i < names.length; i++) {
      fluid.addComponent(names[i], amounts[i]);
    }
    fluid.setMixingRule("classic");
    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(2);
    return fluid;
  }

  /**
   * Source history at fixed specific enthalpy, with the given interval-average rates.
   *
   * @param fluid one-mole-per-second fluid
   * @param rates total molar rates for successive intervals
   * @return source history
   */
  private static Discharge history(SystemInterface fluid, double... rates) {
    Discharge source = new Discharge(fluid);
    for (double rate : rates) {
      source.add(fluid.getMolarComposition(), rate, fluid.getEnthalpy() * rate);
    }
    return source;
  }

  /** Nitrogen governs the first mass peak, methane the second heat peak. */
  @Test
  void diverseGasSourcesHaveIndependentPeakTimesAndAreOrderInvariant() {
    SystemInterface nitrogen = fluid(new String[] {"nitrogen"}, new double[] {1}, 1.5);
    SystemInterface methane = fluid(new String[] {"methane"}, new double[] {1}, 1.5);
    Discharge inert = history(nitrogen, 30.0, 1.0);
    Discharge fuel = history(methane, 0.05, 2.0);
    Map<String, Discharge> sources = new LinkedHashMap<>();
    sources.put("inert", inert);
    sources.put("fuel", fuel);
    Map<String, Object> forward = new BlowdownFlareLoad(sources, 1.5, 2).toMap();
    sources.clear();
    sources.put("fuel", fuel);
    sources.put("inert", inert);
    Map<String, Object> reverse = new BlowdownFlareLoad(sources, 1.5, 2).toMap();
    for (String quantity : new String[] {"peakHeatDutyW", "peakCO2EmissionKgPerS", "peakMolarFlowMolePerS",
        "totalMassToFlareKg", "cumulativeHeatReleasedGJ", "cumulativeCO2EmissionKg"}) {
      assertEquals(forward.get(quantity), reverse.get(quantity), quantity);
    }
    assertEquals(2.0, number(forward, "peakTimeS"), 0.0);
    assertEquals(4.0, number(forward, "peakHeatDutyTimeS"), 0.0);
    assertEquals(4.0, number(forward, "peakCO2EmissionTimeS"), 0.0);
    assertEquals(2 * (31 * nitrogen.getMolarMass() + 2.05 * methane.getMolarMass()),
        number(forward, "totalMassToFlareKg"), 1e-8);
    assertEquals(2 * 2.05 * 0.04401, number(forward, "cumulativeCO2EmissionKg"), 1e-8);
    Flare pure = new Flare("methane reference", new Stream("fuel", methane));
    pure.run();
    assertEquals(2.0 * pure.getHeatDuty(), number(forward, "peakHeatDutyW"), 1e-4);
    assertEquals(2 * 2.05 * pure.getHeatDuty() * 1e-9, number(forward, "cumulativeHeatReleasedGJ"), 1e-12);
  }

  /** Independently flashed two-phase outlet partitions mass and enthalpy between tip and knockout. */
  @Test
  void onlyEquilibriumGasGoesToTheTip() {
    SystemInterface mixed = fluid(new String[] {"methane", "n-decane"}, new double[] {0.65, 0.35}, 2.0);
    assertEquals(2, mixed.getNumberOfPhases());
    SystemInterface gas = mixed.phaseToSystem("gas");
    Map<String, Discharge> sources = new LinkedHashMap<>();
    sources.put("wet", history(mixed, 1.0));
    Map<String, Object> result = new BlowdownFlareLoad(sources, 2.0, 3).toMap();
    assertEquals(3 * gas.getFlowRate("kg/sec"), number(result, "totalMassToFlareKg"), 1e-7);
    assertTrue(number(result, "totalLiquidToKnockoutKg") > 0);
    assertEquals(3 * mixed.getMolarMass(),
        number(result, "totalMassToFlareKg") + number(result, "totalLiquidToKnockoutKg"), 1e-8);
    assertEquals(3 * mixed.getEnthalpy(),
        number(result, "cumulativeGasEnthalpyJ") + number(result, "cumulativeKnockoutEnthalpyJ"), 1.0);
    Flare expected = new Flare("gas reference", new Stream("gas", gas));
    expected.run();
    assertEquals(expected.getHeatDuty(), number(result, "peakHeatDutyW"), expected.getHeatDuty() * 1e-6);
  }

  /** The integrated discharge supplied to the header equals the transient vessel's inventory loss. */
  @Test
  void integratedAcceptedWithdrawalsMatchVesselInventory() {
    SystemInterface feed = fluid(new String[] {"methane", "ethane"}, new double[] {0.9, 0.1}, 40.0);
    Discharge discharge = new Discharge(feed);
    DepressurizationResult vessel = new DepressurizationSimulator(feed.clone(), 2.0, 0.012, 0.72, 1.5e5)
        .setTimeStep(2.0).setMaxTime(6.0).run(discharge);
    Map<String, Discharge> sources = new LinkedHashMap<>();
    sources.put("vessel", discharge);
    Map<String, Object> result = new BlowdownFlareLoad(sources, 1.5, 2.0).toMap();
    double inventoryChange = vessel.massKg.get(0) - vessel.massKg.get(vessel.massKg.size() - 1);
    assertEquals(inventoryChange, number(result, "totalMassToHeaderKg"), 1e-8);
  }

  /** A governed run carries actual discharged vapor composition through to a source-order-invariant flare handoff. */
  @Test
  @SuppressWarnings("unchecked")
  void governedHandoffCarriesActualVaporWithdrawal() {
    SystemInterface rich = fluid(new String[] {"methane", "n-decane"}, new double[] {0.65, 0.35}, 40.0);
    SystemInterface inert = fluid(new String[] {"nitrogen"}, new double[] {1.0}, 40.0);
    BlowdownSource wet = BlowdownSource.builder("wet", rich).vesselVolumeM3(2).orificeDiameterM(0.012)
        .dischargeCoefficient(0.72).backPressureBara(1.5).build();
    BlowdownSource dry = BlowdownSource.builder("inert", inert).vesselVolumeM3(2).orificeDiameterM(0.012)
        .dischargeCoefficient(0.72).backPressureBara(1.5).build();
    DynamicBlowdownFlareStudyRunner runner = DynamicBlowdownFlareStudyRunner.builder().timeStepSeconds(1)
        .maxTimeSeconds(2).build();
    Map<String, Object> first = runner
        .run(DynamicBlowdownFlareStudyDataSource.builder("forward").addSource(wet).addSource(dry).build())
        .getFlareLoadHandoff();
    Map<String, Object> second = runner
        .run(DynamicBlowdownFlareStudyDataSource.builder("reverse").addSource(dry).addSource(wet).build())
        .getFlareLoadHandoff();
    Map<String, Object> load = (Map<String, Object>) first.get("flareLoad");
    assertEquals(load.get("peakHeatDutyW"), ((Map<String, Object>) second.get("flareLoad")).get("peakHeatDutyW"));
    assertTrue(number(load, "peakHeatDutyW") > 0.0);
    Map<String, Object> profile = (Map<String, Object>) load.get("profile");
    List<String> names = java.util.Arrays.asList((String[]) profile.get("componentNames"));
    double[] withdrawn = ((List<double[]>) profile.get("headerComponentMolesPerS")).get(1);
    double methane = withdrawn[names.indexOf("methane")];
    double decane = withdrawn[names.indexOf(rich.getComponent(1).getName())];
    assertEquals(rich.getPhase(DepressurizationSimulator.vapourPhaseIndex(rich)).getComponent("methane").getx(),
        methane / (methane + decane), 1e-7);
  }

  /**
   * Read a numerical result field.
   *
   * @param result emitted result map
   * @param key quantity name
   * @return reported value
   */
  private static double number(Map<String, Object> result, String key) {
    return ((Number) result.get(key)).doubleValue();
  }
}
