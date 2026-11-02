package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression tests for MEG contamination of a TEG regeneration system described with SRK-CPA (statoil) and mixing rule
 * 10.
 *
 * <p>
 * The tests document the database behaviour used to screen a TEG dehydration unit whose glycol is contaminated with
 * monoethylene glycol: the pure MEG vapour pressure against NIST data, the bubble point of a water-free TEG/MEG mixture
 * at still pressure and the equilibrium water content of the reboiler liquid.
 * </p>
 *
 * @author ESOL
 */
public class CpaTegMegWaterRegenerationTest {
  /** Molar mass of MEG in g/mol. */
  private static final double MW_MEG = 62.068;
  /** Molar mass of TEG in g/mol. */
  private static final double MW_TEG = 150.17;
  /** Molar mass of water in g/mol. */
  private static final double MW_WATER = 18.015;

  /**
   * Builds a CPA fluid of water, MEG and TEG from mass fractions.
   *
   * @param temperatureK temperature in K
   * @param pressureBara pressure in bara
   * @param megFractionWaterFree MEG mass fraction of the water-free glycol
   * @param waterWtPct water content in wt%
   * @return the fluid
   */
  private static SystemInterface glycol(double temperatureK, double pressureBara, double megFractionWaterFree,
      double waterWtPct) {
    SystemInterface fluid = new SystemSrkCPAstatoil(temperatureK, pressureBara);
    double glycolMass = 1.0 - waterWtPct / 100.0;
    fluid.addComponent("water", Math.max(waterWtPct / 100.0 / MW_WATER, 1e-30));
    fluid.addComponent("MEG", Math.max(glycolMass * megFractionWaterFree / MW_MEG, 1e-30));
    fluid.addComponent("TEG", Math.max(glycolMass * (1.0 - megFractionWaterFree) / MW_TEG, 1e-30));
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(false);
    return fluid;
  }

  /**
   * Pure MEG vapour pressure at 160 C compared with the NIST WebBook Antoine equation (Jones and Tamplin, 1952).
   *
   * @throws Exception if the flash fails
   */
  @Test
  void megVapourPressureAgreesWithNist() throws Exception {
    double temperatureK = 433.15;
    SystemInterface fluid = glycol(temperatureK, 0.5, 1.0, 0.0);
    new ThermodynamicOperations(fluid).bubblePointPressureFlash(false);
    double nist = Math.pow(10.0, 4.97012 - 1914.951 / (temperatureK - 84.996));
    assertEquals(nist, fluid.getPressure(), 0.2 * nist, "MEG vapour pressure within 20 % of NIST at 160 C");
  }

  /**
   * A 65/35 TEG/MEG mixture boils well below pure TEG but does not boil at a 197 C reboiler at 1.41 bara.
   *
   * @throws Exception if the flash fails
   */
  @Test
  void megLowersTheWaterFreeBubblePoint() throws Exception {
    SystemInterface teg = glycol(470.0, 1.41, 0.0, 0.0);
    new ThermodynamicOperations(teg).bubblePointTemperatureFlash();
    SystemInterface mix = glycol(470.0, 1.41, 0.35, 0.0);
    new ThermodynamicOperations(mix).bubblePointTemperatureFlash();
    double tegC = teg.getTemperature() - 273.15;
    double mixC = mix.getTemperature() - 273.15;
    assertTrue(tegC > 280.0, "pure TEG bubble point above 280 C at 1.41 bara, got " + tegC);
    assertTrue(mixC < tegC - 40.0, "35 % MEG lowers the bubble point by more than 40 K, got " + mixC);
    assertTrue(mixC > 197.0, "35 % MEG glycol does not boil at a 197 C reboiler, got " + mixC);
  }

  /**
   * At the same reboiler temperature and pressure the MEG-contaminated glycol holds less water in equilibrium, because
   * MEG takes part of the vapour pressure.
   *
   * @throws Exception if the flash fails
   */
  @Test
  void megReducesTheReboilerEquilibriumWater() throws Exception {
    double temperatureK = 273.15 + 196.5;
    double pressure = 1.41;
    double tegWater = equilibriumWater(temperatureK, pressure, 0.0);
    double mixWater = equilibriumWater(temperatureK, pressure, 0.35);
    assertTrue(tegWater > 1.0 && tegWater < 3.0, "TEG reboiler water 1-3 wt%, got " + tegWater);
    assertTrue(mixWater < tegWater, "MEG lowers the equilibrium water, got " + mixWater + " vs " + tegWater);
  }

  /**
   * Water wt% at which the bubble pressure equals the reboiler pressure (bisection).
   *
   * @param temperatureK reboiler temperature in K
   * @param pressure reboiler pressure in bara
   * @param megFraction MEG mass fraction of the water-free glycol
   * @return water wt%
   * @throws Exception if the flash fails
   */
  private static double equilibriumWater(double temperatureK, double pressure, double megFraction) throws Exception {
    double lo = 0.0;
    double hi = 20.0;
    for (int i = 0; i < 30; i++) {
      double mid = 0.5 * (lo + hi);
      SystemInterface fluid = glycol(temperatureK, pressure, megFraction, mid);
      new ThermodynamicOperations(fluid).bubblePointPressureFlash(false);
      if (fluid.getPressure() > pressure) {
        hi = mid;
      } else {
        lo = mid;
      }
    }
    return lo;
  }
}
