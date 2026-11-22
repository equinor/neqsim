package neqsim.thermo.util.mineral;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link MineralMercuryBuffers} and the mercury species of {@link StandardStateSpecies}.
 *
 * @author esol
 * @version 1.0
 */
public class MineralMercuryBuffersTest {

  /** The liquid and ideal-gas data reproduce the vapour pressure of Huber, Laesecke and Friend (2006) within 2 %. */
  @Test
  public void testVapourPressureMatchesHuberCorrelation() {
    double[] temperatureC = {0.0, 25.0, 100.0, 200.0, 250.0};
    double[] huberPa = {0.026988, 0.26133, 37.212, 2307.1, 9934.7};
    for (int i = 0; i < temperatureC.length; i++) {
      double calculated = MineralMercuryBuffers.mercuryVapourPressure(273.15 + temperatureC[i]) * 1.0e5;
      assertEquals(huberPa[i], calculated, 0.02 * huberPa[i], "vapour pressure at " + temperatureC[i] + " C");
    }
  }

  /** Every catalogued reaction is atom-balanced and releases mercury. */
  @Test
  public void testCatalogueIsBalancedAndReleasesMercury() {
    Map<String, SolidGasReactionEquilibrium> all = MineralMercuryBuffers.all();
    assertEquals(7, all.size());
    for (Map.Entry<String, SolidGasReactionEquilibrium> entry : all.entrySet()) {
      entry.getValue().checkElementBalance();
      assertTrue(entry.getValue().getCoefficient("mercury") > 0.0, entry.getKey());
    }
  }

  /**
   * The sulfide reaction with the pyrite-siderite buffer is twice the methane reaction minus the H2S buffer, so the
   * equilibrium constants must combine in the same way.
   */
  @Test
  public void testBufferedReactionIsConsistentWithItsParts() {
    double t = 393.15;
    double combined = 2.0 * MineralMercuryBuffers.cinnabarMethane().getLnK(t)
        - MineralH2SBuffers.pyriteSiderite().getLnK(t);
    assertEquals(combined, MineralMercuryBuffers.cinnabarPyriteSiderite().getLnK(t), 1.0e-8 * Math.abs(combined));
  }

  /**
   * Cinnabar is the stable polymorph at reservoir temperature, so it holds mercury to a lower fugacity than
   * metacinnabar; the two become equal near the transition (about 280 °C with these data, 345 °C measured).
   */
  @Test
  public void testCinnabarIsMoreStableThanMetacinnabarAtReservoirTemperature() {
    double t = 393.15;
    double cinnabar = MineralMercuryBuffers.cinnabarHydrogen().getLog10K(t);
    double metacinnabar = MineralMercuryBuffers.metacinnabarHydrogen().getLog10K(t);
    assertTrue(metacinnabar > cinnabar);
    assertTrue(metacinnabar - cinnabar < 0.6, "polymorph difference " + (metacinnabar - cinnabar));
    double perMercury = MineralMercuryBuffers.enthalpyPerMoleMercury(MineralMercuryBuffers.cinnabarHydrogen(), t);
    assertTrue(perMercury > 85.0e3 && perMercury < 100.0e3, "enthalpy per mole mercury " + perMercury);
  }
}
