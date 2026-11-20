package neqsim.thermo.util.mineral;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Catalogue of mineral-fluid reactions that limit elemental mercury in petroleum reservoir fluids.
 *
 * <p>
 * Elemental mercury in a reservoir fluid can be held back by two condensed phases: mercury sulfide (cinnabar, or the
 * black polymorph metacinnabar) and liquid mercury. Each reaction is atom-balanced (checked on construction). The
 * sulfide reactions use the CH4/CO2 pair of the reservoir fluid as redox agent, so the equilibrium mercury fugacity
 * follows from the fluid without an assumed hydrogen fugacity. Mercury is the ideal-gas species named "mercury", which
 * is also the NeqSim component name. Use
 * {@link SolidGasReactionEquilibrium#solveForGas(neqsim.thermo.system.SystemInterface, String)} with target "mercury".
 * </p>
 *
 * <table>
 * <caption>Reactions and the limiting mercury fugacity</caption>
 * <tr>
 * <th>Key</th>
 * <th>Reaction</th>
 * <th>f(Hg) proportional to</th>
 * </tr>
 * <tr>
 * <td>liquid-mercury</td>
 * <td>Hg(l) = Hg(g)</td>
 * <td>K (vapour pressure with the Poynting correction)</td>
 * </tr>
 * <tr>
 * <td>cinnabar-hydrogen</td>
 * <td>HgS + H2 = Hg + H2S</td>
 * <td>K f(H2)/f(H2S)</td>
 * </tr>
 * <tr>
 * <td>cinnabar-methane</td>
 * <td>4 HgS + CH4 + 2 H2O = 4 Hg + 4 H2S + CO2</td>
 * <td>(K f(CH4)/f(CO2))^(1/4) / f(H2S)</td>
 * </tr>
 * <tr>
 * <td>cinnabar-pyrite-siderite</td>
 * <td>8 HgS + 4 FeCO3 + CH4 = 8 Hg + 4 FeS2 + 5 CO2 + 2 H2O</td>
 * <td>(K f(CH4)/f(CO2)^5)^(1/8)</td>
 * </tr>
 * </table>
 *
 * <p>
 * The cinnabar-pyrite-siderite reaction is the cinnabar-methane reaction with the H2S fugacity fixed by the
 * pyrite-siderite buffer of {@link MineralH2SBuffers#pyriteSiderite()}. The same four reactions are available for
 * metacinnabar. The equilibria are upper limits: a fluid holds less mercury than the limit when the mercury supply from
 * the source rock was smaller than the sulfide could hold back.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public final class MineralMercuryBuffers {

  /** Private constructor for a utility class. */
  private MineralMercuryBuffers() {
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
   * Selects the mercury sulfide polymorph.
   *
   * @param metacinnabar true for metacinnabar, false for cinnabar
   * @return sulfide species
   */
  private static StandardStateSpecies sulfide(boolean metacinnabar) {
    return metacinnabar ? StandardStateSpecies.metacinnabar() : StandardStateSpecies.cinnabar();
  }

  /**
   * Vaporisation of liquid mercury: Hg(l) = Hg(g). Its equilibrium fugacity is the vapour pressure of mercury with the
   * pressure (Poynting) correction of the liquid.
   *
   * @return reaction
   */
  public static SolidGasReactionEquilibrium liquidMercury() {
    return build("Hg(l) = Hg(g)",
        new StandardStateSpecies[] {StandardStateSpecies.liquidMercury(), StandardStateSpecies.mercuryGas()},
        new double[] {-1.0, 1.0});
  }

  /**
   * Reduction of cinnabar by hydrogen: HgS + H2 = Hg + H2S.
   *
   * @return reaction
   */
  public static SolidGasReactionEquilibrium cinnabarHydrogen() {
    return sulfideHydrogen(false);
  }

  /**
   * Reduction of metacinnabar by hydrogen: HgS + H2 = Hg + H2S.
   *
   * @return reaction
   */
  public static SolidGasReactionEquilibrium metacinnabarHydrogen() {
    return sulfideHydrogen(true);
  }

  /**
   * Reduction of cinnabar by methane: 4 HgS + CH4 + 2 H2O(l) = 4 Hg + 4 H2S + CO2.
   *
   * @return reaction
   */
  public static SolidGasReactionEquilibrium cinnabarMethane() {
    return sulfideMethane(false);
  }

  /**
   * Reduction of metacinnabar by methane: 4 HgS + CH4 + 2 H2O(l) = 4 Hg + 4 H2S + CO2.
   *
   * @return reaction
   */
  public static SolidGasReactionEquilibrium metacinnabarMethane() {
    return sulfideMethane(true);
  }

  /**
   * Cinnabar with the pyrite-siderite H2S buffer: 8 HgS + 4 FeCO3 + CH4 = 8 Hg + 4 FeS2 + 5 CO2 + 2 H2O(l).
   *
   * @return reaction
   */
  public static SolidGasReactionEquilibrium cinnabarPyriteSiderite() {
    return sulfidePyriteSiderite(false);
  }

  /**
   * Metacinnabar with the pyrite-siderite H2S buffer: 8 HgS + 4 FeCO3 + CH4 = 8 Hg + 4 FeS2 + 5 CO2 + 2 H2O(l).
   *
   * @return reaction
   */
  public static SolidGasReactionEquilibrium metacinnabarPyriteSiderite() {
    return sulfidePyriteSiderite(true);
  }

  /**
   * Builds HgS + H2 = Hg + H2S for one polymorph.
   *
   * @param metacinnabar true for metacinnabar, false for cinnabar
   * @return reaction
   */
  private static SolidGasReactionEquilibrium sulfideHydrogen(boolean metacinnabar) {
    return build("HgS(" + sulfide(metacinnabar).getName() + ") + H2 = Hg + H2S",
        new StandardStateSpecies[] {sulfide(metacinnabar), StandardStateSpecies.hydrogenGas(),
            StandardStateSpecies.mercuryGas(), StandardStateSpecies.hydrogenSulfideGas()},
        new double[] {-1.0, -1.0, 1.0, 1.0});
  }

  /**
   * Builds 4 HgS + CH4 + 2 H2O(l) = 4 Hg + 4 H2S + CO2 for one polymorph.
   *
   * @param metacinnabar true for metacinnabar, false for cinnabar
   * @return reaction
   */
  private static SolidGasReactionEquilibrium sulfideMethane(boolean metacinnabar) {
    return build("4 HgS(" + sulfide(metacinnabar).getName() + ") + CH4 + 2 H2O(l) = 4 Hg + 4 H2S + CO2",
        new StandardStateSpecies[] {sulfide(metacinnabar), StandardStateSpecies.methaneGas(),
            StandardStateSpecies.liquidWater(), StandardStateSpecies.mercuryGas(),
            StandardStateSpecies.hydrogenSulfideGas(), StandardStateSpecies.carbonDioxideGas()},
        new double[] {-4.0, -1.0, -2.0, 4.0, 4.0, 1.0});
  }

  /**
   * Builds 8 HgS + 4 FeCO3 + CH4 = 8 Hg + 4 FeS2 + 5 CO2 + 2 H2O(l) for one polymorph.
   *
   * @param metacinnabar true for metacinnabar, false for cinnabar
   * @return reaction
   */
  private static SolidGasReactionEquilibrium sulfidePyriteSiderite(boolean metacinnabar) {
    return build("8 HgS(" + sulfide(metacinnabar).getName() + ") + 4 FeCO3 + CH4 = 8 Hg + 4 FeS2 + 5 CO2 + 2 H2O(l)",
        new StandardStateSpecies[] {sulfide(metacinnabar), StandardStateSpecies.siderite(),
            StandardStateSpecies.methaneGas(), StandardStateSpecies.mercuryGas(), StandardStateSpecies.pyrite(),
            StandardStateSpecies.carbonDioxideGas(), StandardStateSpecies.liquidWater()},
        new double[] {-8.0, -4.0, -1.0, 8.0, 4.0, 5.0, 2.0});
  }

  /**
   * Returns all catalogued reactions by key.
   *
   * @return ordered map of key to reaction
   */
  public static Map<String, SolidGasReactionEquilibrium> all() {
    Map<String, SolidGasReactionEquilibrium> map = new LinkedHashMap<String, SolidGasReactionEquilibrium>();
    map.put("liquid-mercury", liquidMercury());
    map.put("cinnabar-hydrogen", cinnabarHydrogen());
    map.put("cinnabar-methane", cinnabarMethane());
    map.put("cinnabar-pyrite-siderite", cinnabarPyriteSiderite());
    map.put("metacinnabar-hydrogen", metacinnabarHydrogen());
    map.put("metacinnabar-methane", metacinnabarMethane());
    map.put("metacinnabar-pyrite-siderite", metacinnabarPyriteSiderite());
    return map;
  }

  /**
   * Calculates the apparent enthalpy per mole of mercury released, {@code dH_rxn / nu(Hg)}, which sets the slope of ln
   * f(Hg) versus 1/T at fixed fugacities of the other gases.
   *
   * @param reaction reaction containing mercury as a product
   * @param temperature temperature [K]
   * @return enthalpy per mole mercury [J/mol]
   */
  public static double enthalpyPerMoleMercury(SolidGasReactionEquilibrium reaction, double temperature) {
    return reaction.getStandardEnthalpyOfReaction(temperature) / reaction.getCoefficient("mercury");
  }

  /**
   * Calculates the saturation vapour pressure of liquid mercury from the standard-state data of the liquid and the
   * ideal gas, without the pressure correction.
   *
   * @param temperature temperature [K]
   * @return vapour pressure [bara]
   */
  public static double mercuryVapourPressure(double temperature) {
    return Math.exp(liquidMercury().getLnK(temperature));
  }
}
