package neqsim.thermo.util.mineral;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Catalogue of mineral-fluid reactions that can buffer or generate H2S in petroleum reservoirs.
 *
 * <p>
 * Each reaction is atom-balanced (checked on construction) and uses the CH4/CO2 pair of the reservoir fluid as redox
 * agent, so the equilibrium H2S fugacity follows from the fluid itself without an assumed hydrogen or oxygen fugacity.
 * Liquid water enters as a condensed species with activity (set brine water activity with
 * {@link SolidGasReactionEquilibrium#setActivity(String, double)}). Use
 * {@link SolidGasReactionEquilibrium#solveForGas(neqsim.thermo.system.SystemInterface, String)} with target "H2S".
 * </p>
 *
 * <table>
 * <caption>Reactions and the buffered H2S fugacity</caption>
 * <tr>
 * <th>Key</th>
 * <th>Reaction</th>
 * <th>f(H2S) proportional to</th>
 * </tr>
 * <tr>
 * <td>pyrite-pyrrhotite</td>
 * <td>4 FeS2 + CH4 + 2 H2O = 4 FeS + 4 H2S + CO2</td>
 * <td>(K f(CH4)/f(CO2))^(1/4)</td>
 * </tr>
 * <tr>
 * <td>pyrite-siderite</td>
 * <td>4 FeS2 + CH4 + 3 CO2 + 6 H2O = 4 FeCO3 + 8 H2S</td>
 * <td>(K f(CH4) f(CO2)^3)^(1/8)</td>
 * </tr>
 * <tr>
 * <td>pyrite-magnetite</td>
 * <td>6 FeS2 + CH4 + 10 H2O = 2 Fe3O4 + 12 H2S + CO2</td>
 * <td>(K f(CH4)/f(CO2))^(1/12)</td>
 * </tr>
 * <tr>
 * <td>pyrrhotite-magnetite</td>
 * <td>12 FeS + CO2 + 14 H2O = 4 Fe3O4 + 12 H2S + CH4</td>
 * <td>(K f(CO2)/f(CH4))^(1/12)</td>
 * </tr>
 * <tr>
 * <td>pyrrhotite-siderite</td>
 * <td>FeS + CO2 + H2O = FeCO3 + H2S</td>
 * <td>K f(CO2)</td>
 * </tr>
 * <tr>
 * <td>anhydrite-TSR</td>
 * <td>CaSO4 + CH4 = CaCO3 + H2S + H2O</td>
 * <td>K f(CH4) (thermodynamic limit of sulfate reduction)</td>
 * </tr>
 * </table>
 *
 * <p>
 * Troilite stands in for pyrrhotite. The equilibria are thermodynamic limits; whether a buffer controls a reservoir
 * depends on which minerals coexist with the fluid and on reaction rates.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public final class MineralH2SBuffers {

  /** Private constructor for a utility class. */
  private MineralH2SBuffers() {
  }

  /**
   * Builds a reaction from species and coefficients and checks its element balance.
   *
   * @param name reaction name
   * @param species species
   * @param coefficients coefficients, negative for reactants
   * @return balanced reaction
   */
  private static SolidGasReactionEquilibrium build(String name, StandardStateSpecies[] species, double[] coefficients) {
    SolidGasReactionEquilibrium reaction = new SolidGasReactionEquilibrium(name);
    for (int i = 0; i < species.length; i++) {
      reaction.addSpecies(species[i], coefficients[i]);
    }
    reaction.checkElementBalance();
    return reaction;
  }

  /**
   * Pyrite-pyrrhotite buffer reduced by methane: 4 FeS2 + CH4 + 2 H2O(l) = 4 FeS + 4 H2S + CO2.
   *
   * @return reaction
   */
  public static SolidGasReactionEquilibrium pyritePyrrhotite() {
    return build("4 FeS2 + CH4 + 2 H2O(l) = 4 FeS + 4 H2S + CO2",
        new StandardStateSpecies[] {StandardStateSpecies.pyrite(), StandardStateSpecies.methaneGas(),
            StandardStateSpecies.liquidWater(), StandardStateSpecies.troilite(),
            StandardStateSpecies.hydrogenSulfideGas(), StandardStateSpecies.carbonDioxideGas()},
        new double[] {-4.0, -1.0, -2.0, 4.0, 4.0, 1.0});
  }

  /**
   * Pyrite-siderite buffer reduced by methane: 4 FeS2 + CH4 + 3 CO2 + 6 H2O(l) = 4 FeCO3 + 8 H2S.
   *
   * @return reaction
   */
  public static SolidGasReactionEquilibrium pyriteSiderite() {
    return build("4 FeS2 + CH4 + 3 CO2 + 6 H2O(l) = 4 FeCO3 + 8 H2S",
        new StandardStateSpecies[] {StandardStateSpecies.pyrite(), StandardStateSpecies.methaneGas(),
            StandardStateSpecies.carbonDioxideGas(), StandardStateSpecies.liquidWater(),
            StandardStateSpecies.siderite(), StandardStateSpecies.hydrogenSulfideGas()},
        new double[] {-4.0, -1.0, -3.0, -6.0, 4.0, 8.0});
  }

  /**
   * Pyrite-magnetite buffer reduced by methane: 6 FeS2 + CH4 + 10 H2O(l) = 2 Fe3O4 + 12 H2S + CO2.
   *
   * @return reaction
   */
  public static SolidGasReactionEquilibrium pyriteMagnetite() {
    return build("6 FeS2 + CH4 + 10 H2O(l) = 2 Fe3O4 + 12 H2S + CO2",
        new StandardStateSpecies[] {StandardStateSpecies.pyrite(), StandardStateSpecies.methaneGas(),
            StandardStateSpecies.liquidWater(), StandardStateSpecies.magnetite(),
            StandardStateSpecies.hydrogenSulfideGas(), StandardStateSpecies.carbonDioxideGas()},
        new double[] {-6.0, -1.0, -10.0, 2.0, 12.0, 1.0});
  }

  /**
   * Pyrrhotite-magnetite buffer: 12 FeS + CO2 + 14 H2O(l) = 4 Fe3O4 + 12 H2S + CH4.
   *
   * @return reaction
   */
  public static SolidGasReactionEquilibrium pyrrhotiteMagnetite() {
    return build("12 FeS + CO2 + 14 H2O(l) = 4 Fe3O4 + 12 H2S + CH4",
        new StandardStateSpecies[] {StandardStateSpecies.troilite(), StandardStateSpecies.carbonDioxideGas(),
            StandardStateSpecies.liquidWater(), StandardStateSpecies.magnetite(),
            StandardStateSpecies.hydrogenSulfideGas(), StandardStateSpecies.methaneGas()},
        new double[] {-12.0, -1.0, -14.0, 4.0, 12.0, 1.0});
  }

  /**
   * Pyrrhotite-siderite reaction: FeS + CO2 + H2O(l) = FeCO3 + H2S.
   *
   * @return reaction
   */
  public static SolidGasReactionEquilibrium pyrrhotiteSiderite() {
    return SolidGasReactionEquilibrium.ironSulfideToSiderite();
  }

  /**
   * Overall thermochemical sulfate reduction by methane: CaSO4 + CH4 = CaCO3 + H2S + H2O(l). Its equilibrium lies far
   * to the right, so H2S from this route is controlled by kinetics and sulfate supply, not by equilibrium.
   *
   * @return reaction
   */
  public static SolidGasReactionEquilibrium anhydriteSulfateReduction() {
    return build("CaSO4 + CH4 = CaCO3 + H2S + H2O(l)",
        new StandardStateSpecies[] {StandardStateSpecies.anhydrite(), StandardStateSpecies.methaneGas(),
            StandardStateSpecies.calcite(), StandardStateSpecies.hydrogenSulfideGas(),
            StandardStateSpecies.liquidWater()},
        new double[] {-1.0, -1.0, 1.0, 1.0, 1.0});
  }

  /**
   * Siderite-magnetite boundary (no sulfur): 12 FeCO3 + 2 H2O(l) = 4 Fe3O4 + 11 CO2 + CH4. Fixes the CO2 fugacity at
   * which siderite and magnetite coexist for a given CH4 fugacity; used for mineral stability diagrams. Not part of
   * {@link #all()} because it does not involve H2S.
   *
   * @return reaction
   */
  public static SolidGasReactionEquilibrium sideriteMagnetite() {
    return build("12 FeCO3 + 2 H2O(l) = 4 Fe3O4 + 11 CO2 + CH4",
        new StandardStateSpecies[] {StandardStateSpecies.siderite(), StandardStateSpecies.liquidWater(),
            StandardStateSpecies.magnetite(), StandardStateSpecies.carbonDioxideGas(),
            StandardStateSpecies.methaneGas()},
        new double[] {-12.0, -2.0, 4.0, 11.0, 1.0});
  }

  /**
   * Returns all catalogued reactions by key.
   *
   * @return ordered map of key to reaction
   */
  public static Map<String, SolidGasReactionEquilibrium> all() {
    Map<String, SolidGasReactionEquilibrium> map = new LinkedHashMap<String, SolidGasReactionEquilibrium>();
    map.put("pyrite-pyrrhotite", pyritePyrrhotite());
    map.put("pyrite-siderite", pyriteSiderite());
    map.put("pyrite-magnetite", pyriteMagnetite());
    map.put("pyrrhotite-magnetite", pyrrhotiteMagnetite());
    map.put("pyrrhotite-siderite", pyrrhotiteSiderite());
    map.put("anhydrite-TSR", anhydriteSulfateReduction());
    return map;
  }

  /**
   * Calculates the apparent enthalpy per mole of H2S produced, {@code dH_rxn / nu(H2S)}, which sets the slope of ln
   * f(H2S) versus 1/T at fixed fugacities of the other gases.
   *
   * @param reaction reaction containing H2S as a product
   * @param temperature temperature [K]
   * @return enthalpy per mole H2S [J/mol]
   */
  public static double enthalpyPerMoleH2S(SolidGasReactionEquilibrium reaction, double temperature) {
    return reaction.getStandardEnthalpyOfReaction(temperature) / reaction.getCoefficient("H2S");
  }
}
