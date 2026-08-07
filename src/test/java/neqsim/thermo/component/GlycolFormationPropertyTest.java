package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression tests for the ideal-gas formation properties of the glycols.
 *
 * <p>
 * TEG and DEG previously carried placeholder values in the component database: both had the enthalpy of formation of
 * water (-242000 J/mol) and the Gibbs energy of formation of carbon dioxide (-394370 J/mol). Any Gibbs-minimisation or
 * heat-of-reaction calculation involving a glycol was therefore wrong. These tests pin the corrected values and, more
 * usefully, assert that the resulting heats of combustion are physically sensible and correctly ordered.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class GlycolFormationPropertyTest {

  /** Tolerance on formation properties [J/mol]. */
  private static final double FORMATION_TOLERANCE = 1.0;

  /** Enthalpy of formation of water that was wrongly assigned to the glycols [J/mol]. */
  private static final double WATER_FORMATION_ENTHALPY = -241818.0;

  /**
   * Build a single-component fluid and return the requested component.
   *
   * @param name component name as used in the component database
   * @return the component
   */
  private ComponentInterface component(String name) {
    SystemInterface fluid = new SystemSrkEos(298.15, 1.0);
    fluid.addComponent(name, 1.0);
    fluid.setMixingRule("classic");
    return fluid.getPhase(0).getComponent(name);
  }

  /**
   * Compute the ideal-gas net heat of combustion of a glycol from database formation enthalpies.
   *
   * <p>
   * The reaction is {@code CnH(2n+2)O(n+1) + O2 -> n CO2 + (n+1) H2O}, with water as vapour, so the result is the net
   * (lower) heating value.
   * </p>
   *
   * @param name glycol component name
   * @param carbonAtoms number of carbon atoms, giving the CO2 stoichiometric coefficient
   * @param waterMoles stoichiometric coefficient of water
   * @return net heat of combustion in J/kg, reported as a positive magnitude
   */
  private double netHeatOfCombustionJPerKg(String name, int carbonAtoms, int waterMoles) {
    ComponentInterface fuel = component(name);
    double co2 = component("CO2").getIdealGasEnthalpyOfFormation();
    double water = component("water").getIdealGasEnthalpyOfFormation();
    double reactionEnthalpy = carbonAtoms * co2 + waterMoles * water - fuel.getIdealGasEnthalpyOfFormation();
    return Math.abs(reactionEnthalpy) / fuel.getMolarMass();
  }

  /**
   * TEG and DEG must no longer carry the placeholder values copied from water and carbon dioxide.
   */
  @Test
  void glycolFormationPropertiesAreNotPlaceholders() {
    double tegEnthalpy = component("TEG").getIdealGasEnthalpyOfFormation();
    double degEnthalpy = component("DEG").getIdealGasEnthalpyOfFormation();

    assertTrue(Math.abs(tegEnthalpy - WATER_FORMATION_ENTHALPY) > 1000.0,
        "TEG must not carry the formation enthalpy of water");
    assertTrue(Math.abs(degEnthalpy - WATER_FORMATION_ENTHALPY) > 1000.0,
        "DEG must not carry the formation enthalpy of water");
    assertTrue(Math.abs(tegEnthalpy - degEnthalpy) > 1000.0,
        "TEG and DEG must not share an identical formation enthalpy");
  }

  /**
   * The corrected ideal-gas formation properties must match the literature values loaded from the database.
   */
  @Test
  void correctedFormationPropertiesAreLoaded() {
    assertEquals(-726500.0, component("TEG").getIdealGasEnthalpyOfFormation(), FORMATION_TOLERANCE,
        "TEG ideal-gas enthalpy of formation");
    assertEquals(-571200.0, component("DEG").getIdealGasEnthalpyOfFormation(), FORMATION_TOLERANCE,
        "DEG ideal-gas enthalpy of formation");
    assertEquals(-474700.0, component("TEG").getIdealGasGibbsEnergyOfFormation(), FORMATION_TOLERANCE,
        "TEG ideal-gas Gibbs energy of formation");
    assertEquals(-402900.0, component("DEG").getIdealGasGibbsEnergyOfFormation(), FORMATION_TOLERANCE,
        "DEG ideal-gas Gibbs energy of formation");
  }

  /**
   * The net heat of combustion of each glycol must fall in the band reported for oxygenated organics, roughly 16 to 24
   * MJ/kg. Before the fix, TEG returned about 25.4 MJ/kg.
   */
  @Test
  void glycolHeatsOfCombustionArePhysicallySensible() {
    double meg = netHeatOfCombustionJPerKg("MEG", 2, 3);
    double deg = netHeatOfCombustionJPerKg("DEG", 4, 5);
    double teg = netHeatOfCombustionJPerKg("TEG", 6, 7);

    assertTrue(meg > 16.0e6 && meg < 20.0e6, "MEG net heat of combustion out of range: " + meg);
    assertTrue(deg > 19.0e6 && deg < 23.0e6, "DEG net heat of combustion out of range: " + deg);
    assertTrue(teg > 20.0e6 && teg < 24.0e6, "TEG net heat of combustion out of range: " + teg);
  }

  /**
   * Heat of combustion per unit mass must increase from MEG to DEG to TEG, because the oxygen-to-carbon ratio of the
   * molecule falls along that series so there is progressively more oxidisable carbon and hydrogen per kilogram.
   */
  @Test
  void heatOfCombustionIncreasesWithGlycolChainLength() {
    double meg = netHeatOfCombustionJPerKg("MEG", 2, 3);
    double deg = netHeatOfCombustionJPerKg("DEG", 4, 5);
    double teg = netHeatOfCombustionJPerKg("TEG", 6, 7);

    assertTrue(meg < deg, "DEG must release more heat per kg than MEG");
    assertTrue(deg < teg, "TEG must release more heat per kg than DEG");
  }
}
