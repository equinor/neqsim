package neqsim.thermo.util.mineral;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link MineralH2SBuffers}.
 *
 * @author esol
 * @version 1.0
 */
public class MineralH2SBuffersTest {
  /** log10 Kf at 298.15 K, Robie and Hemingway (1995) USGS Bulletin 2131. */
  private static final double PYRITE = 28.05;
  private static final double TROILITE = 18.03;
  private static final double MAGNETITE = 177.42;
  private static final double SIDERITE = 119.63;
  private static final double CALCITE = 197.70;
  private static final double ANHYDRITE = 231.57;
  private static final double H2S = 5.86;
  private static final double CO2 = 69.09;
  private static final double WATER = 41.55;
  /** Methane, from NIST dfG of -50.765 kJ/mol. */
  private static final double METHANE = 50764.6 / (8.3144621 * 298.15 * Math.log(10.0));

  /** All catalogued reactions are atom-balanced and have H2S as product. */
  @Test
  public void testCatalogueBalanced() {
    for (SolidGasReactionEquilibrium r : MineralH2SBuffers.all().values()) {
      r.checkElementBalance();
      assertTrue(r.getCoefficient("H2S") > 0.0, r.getName());
    }
  }

  /** log10 K at 298.15 K agrees with independent USGS formation constants. */
  @Test
  public void testLogKAgainstUsgs() {
    assertEquals(4 * SIDERITE + 8 * H2S - 4 * PYRITE - METHANE - 3 * CO2 - 6 * WATER,
        MineralH2SBuffers.pyriteSiderite().getLog10K(298.15), 0.4);
    assertEquals(2 * MAGNETITE + 12 * H2S + CO2 - 6 * PYRITE - METHANE - 10 * WATER,
        MineralH2SBuffers.pyriteMagnetite().getLog10K(298.15), 0.4);
    assertEquals(CALCITE + H2S + WATER - ANHYDRITE - METHANE,
        MineralH2SBuffers.anhydriteSulfateReduction().getLog10K(298.15), 0.3);
    // JANAF troilite dfH (-101.67) versus USGS table (-102.6 kJ/mol) gives about 0.65 per 4 FeS
    assertEquals(4 * TROILITE + 4 * H2S + CO2 - 4 * PYRITE - METHANE - 2 * WATER,
        MineralH2SBuffers.pyritePyrrhotite().getLog10K(298.15), 1.0);
  }

  /**
   * The slope of ln f(H2S) versus 1/T at fixed CH4 and CO2 fugacities equals minus the enthalpy per mole H2S over R
   * (condensed-phase pressure term excluded by evaluating at 1 bar).
   */
  @Test
  public void testVantHoffSlopePerMoleH2S() {
    SolidGasReactionEquilibrium r = MineralH2SBuffers.pyriteSiderite();
    Map<String, Double> f = new HashMap<String, Double>();
    f.put("methane", 200.0);
    f.put("CO2", 5.0);
    double t = 400.0;
    double h = 0.01;
    double slope = (r.getLnEquilibriumGasFugacity("H2S", f, t + h, 1.0)
        - r.getLnEquilibriumGasFugacity("H2S", f, t - h, 1.0)) / (1.0 / (t + h) - 1.0 / (t - h));
    double dhPerH2S = MineralH2SBuffers.enthalpyPerMoleH2S(r, t);
    assertEquals(-dhPerH2S / 8.3144621, slope, 1e-3 * Math.abs(slope));
    assertTrue(dhPerH2S > 40.0e3 && dhPerH2S < 70.0e3, "dH per H2S " + dhPerH2S);
  }

  /** Pyrite plus siderite buffers H2S in a reservoir gas; H2S rises strongly with temperature. */
  @Test
  public void testPyriteSideriteInReservoirGas() {
    SolidGasReactionEquilibrium r = MineralH2SBuffers.pyriteSiderite();
    double[] ppm = new double[2];
    double[] temps = {90.0, 170.0};
    for (int i = 0; i < 2; i++) {
      SystemInterface fluid = new SystemSrkEos(273.15 + temps[i], 300.0);
      fluid.addComponent("methane", 0.90);
      fluid.addComponent("ethane", 0.06);
      fluid.addComponent("CO2", 0.04);
      fluid.addComponent("H2S", 1.0e-9);
      fluid.setMixingRule("classic");
      SolidGasReactionEquilibrium.GasExchangeResult res = r.solveForGas(fluid, "H2S");
      assertTrue(res.isConverged());
      ppm[i] = res.getProductMoleFraction() * 1.0e6;
    }
    assertTrue(ppm[1] / ppm[0] > 10.0, "ratio " + ppm[1] / ppm[0]);
    assertTrue(ppm[0] > 0.01 && ppm[1] < 1000.0, ppm[0] + " " + ppm[1]);
  }
}
