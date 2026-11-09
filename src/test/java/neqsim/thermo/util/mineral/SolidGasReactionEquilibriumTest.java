package neqsim.thermo.util.mineral;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link StandardStateSpecies} and {@link SolidGasReactionEquilibrium}.
 *
 * @author esol
 * @version 1.0
 */
public class SolidGasReactionEquilibriumTest {

  /**
   * Creates a dry methane-CO2 gas with trace H2S.
   *
   * @param temperatureC temperature [C]
   * @param pressure pressure [bara]
   * @return fluid
   */
  private static SystemInterface gas(double temperatureC, double pressure) {
    SystemInterface fluid = new SystemSrkEos(273.15 + temperatureC, pressure);
    fluid.addComponent("methane", 0.95);
    fluid.addComponent("CO2", 0.05);
    fluid.addComponent("H2S", 1.0e-6);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /**
   * Troilite from JANAF against the independent USGS (Robie and Hemingway, 1995) table. The two compilations treat the
   * 411 K transition differently, so entropies differ by 2-3 J/(mol K) between 400 and 500 K; the tolerance reflects
   * that documented dataset difference.
   */
  @Test
  public void testTroiliteAgainstUsgsTable() {
    StandardStateSpecies troilite = StandardStateSpecies.troilite();
    assertEquals(60.30, troilite.getEntropy(298.15), 0.5);
    assertEquals(50.49, troilite.getHeatCapacity(298.15), 1.0);
    assertEquals(76.87, troilite.getEntropy(400.0), 3.5);
    assertEquals(102.86, troilite.getEntropy(500.0), 3.5);
  }

  /** JANAF gases agree with the independent USGS high-temperature tables. */
  @Test
  public void testGasesAgainstUsgsTables() {
    assertEquals(225.28, StandardStateSpecies.carbonDioxideGas().getEntropy(400.0), 0.2);
    assertEquals(234.85, StandardStateSpecies.carbonDioxideGas().getEntropy(500.0), 0.2);
    assertEquals(216.01, StandardStateSpecies.hydrogenSulfideGas().getEntropy(400.0), 0.2);
    assertEquals(224.11, StandardStateSpecies.hydrogenSulfideGas().getEntropy(500.0), 0.2);
  }

  /**
   * log10 K at 298.15 K against a USGS-only sum of formation constants: siderite 119.63, H2S 5.86, troilite 18.03,
   * liquid water 41.55 and CO2 69.09 give -3.18.
   */
  @Test
  public void testLogKAgainstUsgsFormationConstants() {
    SolidGasReactionEquilibrium reaction = SolidGasReactionEquilibrium.ironSulfideToSiderite();
    double usgs = 119.63 + 5.86 - 18.03 - 41.55 - 69.09;
    assertEquals(usgs, reaction.getLog10K(298.15), 0.25);
  }

  /** Reference-state anchors of the other species. */
  @Test
  public void testReferenceAnchors() {
    assertEquals(82.27, StandardStateSpecies.siderite().getHeatCapacity(298.15), 0.15);
    assertEquals(95.47, StandardStateSpecies.siderite().getEntropy(298.15), 1e-9);
    assertEquals(69.95, StandardStateSpecies.liquidWater().getEntropy(298.15), 0.05);
    assertEquals(213.79, StandardStateSpecies.carbonDioxideGas().getEntropy(298.15), 0.05);
    assertEquals(205.77, StandardStateSpecies.hydrogenSulfideGas().getEntropy(298.15), 0.05);
    assertEquals(0.0, StandardStateSpecies.siderite().getEnthalpyChange(298.15), 1e-9);
  }

  /** Liquid-water pressure integral is close to V dP with a small compressibility reduction. */
  @Test
  public void testWaterPressureIntegral() {
    StandardStateSpecies water = StandardStateSpecies.liquidWater();
    double integral = water.getPressureIntegral(298.15, 500.0);
    assertTrue(integral > 880.0 && integral < 902.0, "water integral " + integral);
    assertEquals(29.38e-6 * 499.0e5, StandardStateSpecies.siderite().getPressureIntegral(350.0, 500.0), 1e-9);
    assertFalse(water.usesMetastableLiquidReference(298.15));
    assertTrue(water.usesMetastableLiquidReference(450.0));
  }

  /** The FeS-siderite reaction is balanced and an unbalanced reaction is rejected. */
  @Test
  public void testElementBalance() {
    SolidGasReactionEquilibrium reaction = SolidGasReactionEquilibrium.ironSulfideToSiderite();
    assertEquals(0.0, reaction.getGasMoleChange(), 1e-12);
    SolidGasReactionEquilibrium bad = new SolidGasReactionEquilibrium("bad");
    bad.addSpecies(StandardStateSpecies.troilite(), -1.0);
    bad.addSpecies(StandardStateSpecies.siderite(), 1.0);
    assertThrows(IllegalStateException.class, bad::checkElementBalance);
  }

  /** Gibbs-Helmholtz and van't Hoff consistency of the reaction functions. */
  @Test
  public void testThermodynamicConsistency() {
    SolidGasReactionEquilibrium reaction = SolidGasReactionEquilibrium.ironSulfideToSiderite();
    double t = 350.0;
    double dg = reaction.getStandardGibbsEnergyOfReaction(t);
    double dh = reaction.getStandardEnthalpyOfReaction(t);
    double ds = reaction.getStandardEntropyOfReaction(t);
    assertEquals(dh - t * ds, dg, 1e-6);
    double h = 0.01;
    double slope = (reaction.getLnK(t + h) - reaction.getLnK(t - h)) / (1.0 / (t + h) - 1.0 / (t - h));
    assertEquals(-dh / 8.3144621, slope, Math.abs(dh / 8.3144621) * 1e-4);
    assertTrue(reaction.isWithinDataRange(373.15));
    assertFalse(reaction.isWithinDataRange(273.15));
    assertTrue(reaction.getLog10KUncertainty(373.15) > 0.5);
  }

  /** At low pressure the gas is nearly ideal: y(H2S)/y(CO2) equals the target quotient. */
  @Test
  public void testIdealGasLimit() {
    SolidGasReactionEquilibrium reaction = SolidGasReactionEquilibrium.ironSulfideToSiderite();
    SolidGasReactionEquilibrium.GasExchangeResult result = reaction.solveGasExchange(gas(80.0, 1.0), "H2S", "CO2",
        SolidGasReactionEquilibrium.ExchangeMode.FIXED_REACTANT);
    assertTrue(result.isConverged());
    double ratio = result.getProductMoleFraction() / result.getReactantMoleFraction();
    assertEquals(result.getTargetFugacityQuotient(), ratio, 0.01 * ratio);
  }

  /** High-pressure solve converges on the EOS fugacity quotient without modifying the input. */
  @Test
  public void testHighPressureFixedReactant() {
    SystemInterface fluid = gas(100.0, 500.0);
    SolidGasReactionEquilibrium reaction = SolidGasReactionEquilibrium.ironSulfideToSiderite();
    SolidGasReactionEquilibrium.GasExchangeResult result = reaction.solveGasExchange(fluid, "H2S", "CO2",
        SolidGasReactionEquilibrium.ExchangeMode.FIXED_REACTANT);
    assertTrue(result.isConverged());
    assertEquals(result.getTargetFugacityQuotient(), result.getAchievedFugacityQuotient(),
        1e-7 * result.getTargetFugacityQuotient());
    assertEquals(0.05, result.getEquilibriumFluid().getComponent("CO2").getNumberOfmoles(), 1e-12);
    assertEquals(1.0e-6, fluid.getComponent("H2S").getNumberOfmoles(), 1e-15);
    assertTrue(result.getProductFugacityCoefficient() != result.getReactantFugacityCoefficient());
  }

  /** Conserving the CO2 + H2S sum keeps the gas inventory constant. */
  @Test
  public void testConserveGasSum() {
    SolidGasReactionEquilibrium reaction = SolidGasReactionEquilibrium.ironSulfideToSiderite();
    SolidGasReactionEquilibrium.GasExchangeResult result = reaction.solveGasExchange(gas(150.0, 300.0), "H2S", "CO2",
        SolidGasReactionEquilibrium.ExchangeMode.CONSERVE_GAS_SUM);
    assertTrue(result.isConverged());
    SystemInterface eq = result.getEquilibriumFluid();
    assertEquals(0.05 + 1.0e-6, eq.getComponent("CO2").getNumberOfmoles() + eq.getComponent("H2S").getNumberOfmoles(),
        1e-12);
  }

  /** Mirrors the example in docs/thermo/solid_gas_reaction_equilibrium.md. */
  @Test
  public void testDocumentationExample() {
    SolidGasReactionEquilibrium reaction = SolidGasReactionEquilibrium.ironSulfideToSiderite();
    double log10K = reaction.getLog10K(298.15);

    SystemInterface fluid = new SystemSrkEos(273.15 + 100.0, 500.0);
    fluid.addComponent("methane", 0.95);
    fluid.addComponent("CO2", 0.05);
    fluid.addComponent("H2S", 1.0e-6);
    fluid.setMixingRule("classic");

    SolidGasReactionEquilibrium.GasExchangeResult result = reaction.solveGasExchange(fluid, "H2S", "CO2",
        SolidGasReactionEquilibrium.ExchangeMode.FIXED_REACTANT);
    double h2sPpm = result.getProductMoleFraction() * 1.0e6;
    List<String> warnings = result.getWarnings();

    assertEquals(-3.05, log10K, 0.005);
    assertTrue(result.isConverged());
    assertTrue(h2sPpm > 0.0);
    assertFalse(warnings.isEmpty());
  }

  /** The general FIXED_OTHERS solver reproduces FIXED_REACTANT and the analytic fugacity. */
  @Test
  public void testSolveForGasMatchesFixedReactant() {
    SolidGasReactionEquilibrium reaction = SolidGasReactionEquilibrium.ironSulfideToSiderite();
    SystemInterface fluid = gas(120.0, 400.0);
    SolidGasReactionEquilibrium.GasExchangeResult fixed = reaction.solveGasExchange(fluid, "H2S", "CO2",
        SolidGasReactionEquilibrium.ExchangeMode.FIXED_REACTANT);
    SolidGasReactionEquilibrium.GasExchangeResult general = reaction.solveForGas(fluid, "H2S");
    assertTrue(general.isConverged());
    assertEquals(SolidGasReactionEquilibrium.ExchangeMode.FIXED_OTHERS, general.getMode());
    assertEquals(fixed.getProductMoleFraction(), general.getProductMoleFraction(),
        1e-6 * fixed.getProductMoleFraction());
    assertTrue(Double.isNaN(general.getReactantMoleFraction()));

    Map<String, Double> others = new HashMap<String, Double>();
    others.put("CO2", general.getGasFugacity("CO2"));
    double lnF = reaction.getLnEquilibriumGasFugacity("H2S", others, 393.15, 400.0);
    assertEquals(general.getGasFugacity("H2S"), Math.exp(lnF), 1e-6 * general.getGasFugacity("H2S"));
    assertEquals(general.getGasFugacity("H2S"),
        general.getProductMoleFraction() * general.getGasFugacityCoefficient("H2S") * 400.0,
        0.05 * general.getGasFugacity("H2S"));
  }

  /** Water activity below one lowers the equilibrium quotient in proportion. */
  @Test
  public void testWaterActivity() {
    SolidGasReactionEquilibrium reaction = SolidGasReactionEquilibrium.ironSulfideToSiderite();
    double pure = reaction.getLnEquilibriumGasFugacityQuotient(373.15, 500.0);
    reaction.setActivity("water", 0.9);
    double brine = reaction.getLnEquilibriumGasFugacityQuotient(373.15, 500.0);
    assertEquals(Math.log(0.9), brine - pure, 1e-12);
    assertThrows(IllegalArgumentException.class, () -> reaction.setActivity("CO2", 0.5));
  }
}
