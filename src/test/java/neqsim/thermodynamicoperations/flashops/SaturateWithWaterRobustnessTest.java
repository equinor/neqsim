package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Robustness and accuracy challenge suite for {@link neqsim.thermodynamicoperations.flashops.SaturateWithWater}.
 *
 * <p>
 * Each scenario is validated against an <em>independent</em> equilibrium oracle: a fresh copy of the same fluid is
 * flooded with a large excess of water and TP-flashed. When a free aqueous phase coexists, the hydrocarbon phases are -
 * by definition of phase equilibrium - exactly saturated, so the overall water mole fraction of the non-aqueous phases
 * is the true saturation value. The {@code saturateWithWater()} result is compared against that reference.
 * </p>
 *
 * <p>
 * The suite covers gas-aqueous, oil-aqueous and gas-oil-aqueous systems across a range of temperatures, pressures and
 * equations of state (SRK-CPA, PR, SRK).
 * </p>
 */
public class SaturateWithWaterRobustnessTest {
  private static final Logger logger = LogManager.getLogger(SaturateWithWaterRobustnessTest.class);

  /** Relative tolerance between saturateWithWater() and the independent equilibrium oracle. */
  private static final double REL_TOL = 0.10;

  /**
   * Computes the true saturated overall water mole fraction of the non-aqueous phases by flooding a fresh copy of the
   * fluid with excess water and flashing.
   *
   * @param factory supplies a fresh water-free fluid
   * @return overall water mole fraction across the gas/oil phases at saturation, or {@code NaN} if no hydrocarbon phase
   * is present
   */
  private static double referenceWaterZ(Supplier<SystemInterface> factory) {
    SystemInterface sys = factory.get();
    sys.setMultiPhaseCheck(true);
    sys.addComponent("water", sys.getTotalNumberOfMoles());
    // Re-apply the mixing rule so association/interaction arrays are resized for the new component.
    sys.setMixingRule(sys.getMixingRule());
    ThermodynamicOperations ops = new ThermodynamicOperations(sys);
    ops.TPflash();
    double waterMoles = 0.0;
    double totalMoles = 0.0;
    for (int p = 0; p < sys.getNumberOfPhases(); p++) {
      if (sys.getPhase(p).getType() == PhaseType.AQUEOUS) {
	continue;
      }
      waterMoles += sys.getPhase(p).getComponent("water").getNumberOfMolesInPhase();
      totalMoles += sys.getPhase(p).getNumberOfMolesInPhase();
    }
    return waterMoles / totalMoles;
  }

  /**
   * Runs {@code saturateWithWater()} on the supplied fluid and asserts the result is physically valid and consistent
   * with the independent equilibrium oracle.
   *
   * @param name scenario label for logging
   * @param factory supplies a fresh water-free fluid
   */
  private void checkSaturation(String name, Supplier<SystemInterface> factory) {
    SystemInterface sys = factory.get();
    sys.setMultiPhaseCheck(true);
    ThermodynamicOperations ops = new ThermodynamicOperations(sys);

    long t0 = System.nanoTime();
    try {
      ops.saturateWithWater();
    } catch (RuntimeException ex) {
      logger.error("{}: saturateWithWater threw {}", name, ex.toString());
      throw ex;
    }
    double elapsedMs = (System.nanoTime() - t0) / 1.0e6;

    double waterZ = sys.getComponent("water").getz();
    double reference = referenceWaterZ(factory);
    double relErr = Math.abs(waterZ - reference) / reference;
    logger.info("{}: waterZ={} reference={} relErr={}% time={} ms phases={}", name, waterZ, reference, relErr * 100.0,
	elapsedMs, sys.getNumberOfPhases());

    assertTrue(Double.isFinite(waterZ), name + ": water content is not finite");
    assertTrue(waterZ > 0.0, name + ": water content must be positive");
    // The saturator removes the free aqueous phase; the result must not retain a bulk aqueous
    // phase.
    assertFalse(sys.hasPhaseType(PhaseType.AQUEOUS), name + ": a free aqueous phase remained after saturation");
    assertTrue(relErr < REL_TOL, name + ": saturated water content " + waterZ + " deviates " + (relErr * 100.0)
	+ "% from equilibrium oracle " + reference);
  }

  // ---------------------------------------------------------------------------------------------
  // Gas - aqueous
  // ---------------------------------------------------------------------------------------------

  @Test
  void gasAqueousLowTempHighPressure() {
    checkSaturation("gas-aq -10C/100bar", new Supplier<SystemInterface>() {
      @Override
      public SystemInterface get() {
	SystemInterface s = new SystemSrkCPAstatoil(273.15 - 10.0, 100.0);
	s.addComponent("nitrogen", 1.0);
	s.addComponent("CO2", 2.0);
	s.addComponent("methane", 90.0);
	s.addComponent("ethane", 5.0);
	s.addComponent("propane", 2.0);
	s.setMixingRule(10);
	return s;
      }
    });
  }

  @Test
  void gasAqueousAmbient() {
    checkSaturation("gas-aq 20C/70bar", new Supplier<SystemInterface>() {
      @Override
      public SystemInterface get() {
	SystemInterface s = new SystemSrkCPAstatoil(273.15 + 20.0, 70.0);
	s.addComponent("methane", 90.0);
	s.addComponent("ethane", 7.0);
	s.addComponent("propane", 3.0);
	s.setMixingRule(10);
	return s;
      }
    });
  }

  @Test
  void gasAqueousHotLowPressure() {
    checkSaturation("gas-aq 90C/20bar", new Supplier<SystemInterface>() {
      @Override
      public SystemInterface get() {
	SystemInterface s = new SystemSrkCPAstatoil(273.15 + 90.0, 20.0);
	s.addComponent("methane", 95.0);
	s.addComponent("ethane", 5.0);
	s.setMixingRule(10);
	return s;
      }
    });
  }

  @Test
  void gasAqueousCO2Rich() {
    checkSaturation("CO2-rich gas-aq 25C/80bar", new Supplier<SystemInterface>() {
      @Override
      public SystemInterface get() {
	SystemInterface s = new SystemSrkCPAstatoil(273.15 + 25.0, 80.0);
	s.addComponent("CO2", 70.0);
	s.addComponent("methane", 28.0);
	s.addComponent("nitrogen", 2.0);
	s.setMixingRule(10);
	return s;
      }
    });
  }

  // ---------------------------------------------------------------------------------------------
  // Oil - aqueous
  // ---------------------------------------------------------------------------------------------

  @Test
  void oilAqueousCpa() {
    checkSaturation("oil-aq CPA 20C/150bar", new Supplier<SystemInterface>() {
      @Override
      public SystemInterface get() {
	SystemInterface s = new SystemSrkCPAstatoil(273.15 + 20.0, 150.0);
	s.addComponent("methane", 2.0);
	s.addComponent("n-heptane", 75.0);
	s.addComponent("nC10", 20.0);
	s.setMixingRule(10);
	return s;
      }
    });
  }

  @Test
  void oilAqueousPr() {
    checkSaturation("oil-aq PR 20C/150bar", new Supplier<SystemInterface>() {
      @Override
      public SystemInterface get() {
	SystemInterface s = new SystemPrEos(273.15 + 20.0, 150.0);
	s.addComponent("methane", 2.0);
	s.addComponent("n-heptane", 75.0);
	s.setMixingRule("classic");
	return s;
      }
    });
  }

  @Test
  void oilAqueousHot() {
    checkSaturation("oil-aq 80C/50bar", new Supplier<SystemInterface>() {
      @Override
      public SystemInterface get() {
	SystemInterface s = new SystemSrkCPAstatoil(273.15 + 80.0, 50.0);
	s.addComponent("n-pentane", 30.0);
	s.addComponent("n-heptane", 50.0);
	s.addComponent("nC10", 20.0);
	s.setMixingRule(10);
	return s;
      }
    });
  }

  // ---------------------------------------------------------------------------------------------
  // Gas - oil - aqueous (three phase)
  // ---------------------------------------------------------------------------------------------

  @Test
  void gasOilAqueousThreePhase() {
    checkSaturation("gas-oil-aq 30C/80bar", new Supplier<SystemInterface>() {
      @Override
      public SystemInterface get() {
	SystemInterface s = new SystemSrkCPAstatoil(273.15 + 30.0, 80.0);
	s.addComponent("methane", 60.0);
	s.addComponent("ethane", 5.0);
	s.addComponent("propane", 3.0);
	s.addComponent("n-butane", 2.0);
	s.addComponent("n-heptane", 10.0);
	s.addComponent("nC10", 20.0);
	s.setMixingRule(10);
	return s;
      }
    });
  }

  @Test
  void gasOilAqueousColdThreePhase() {
    checkSaturation("gas-oil-aq 5C/40bar", new Supplier<SystemInterface>() {
      @Override
      public SystemInterface get() {
	SystemInterface s = new SystemSrkCPAstatoil(273.15 + 5.0, 40.0);
	s.addComponent("methane", 50.0);
	s.addComponent("ethane", 4.0);
	s.addComponent("propane", 3.0);
	s.addComponent("n-pentane", 3.0);
	s.addComponent("n-heptane", 15.0);
	s.addComponent("nC10", 25.0);
	s.setMixingRule(10);
	return s;
      }
    });
  }

  // ---------------------------------------------------------------------------------------------
  // Equation-of-state robustness (no oracle comparison, just no-crash + finite)
  // ---------------------------------------------------------------------------------------------

  @Test
  void gasSrkClassicDoesNotCrash() {
    SystemInterface s = new SystemSrkEos(273.15 + 25.0, 60.0);
    s.addComponent("methane", 90.0);
    s.addComponent("ethane", 7.0);
    s.addComponent("propane", 3.0);
    s.setMixingRule("classic");
    s.setMultiPhaseCheck(true);
    ThermodynamicOperations ops = new ThermodynamicOperations(s);
    ops.saturateWithWater();
    double waterZ = s.getComponent("water").getz();
    logger.info("gas SRK classic: waterZ={}", waterZ);
    assertTrue(Double.isFinite(waterZ) && waterZ > 0.0, "SRK classic water content invalid");
  }

  // ---------------------------------------------------------------------------------------------
  // Idempotency: saturating an already-saturated fluid must not move the water content materially.
  // ---------------------------------------------------------------------------------------------

  @Test
  void idempotentOnReSaturation() {
    SystemInterface s = new SystemSrkCPAstatoil(273.15 + 25.0, 70.0);
    s.addComponent("methane", 90.0);
    s.addComponent("ethane", 7.0);
    s.addComponent("propane", 3.0);
    s.setMixingRule(10);
    s.setMultiPhaseCheck(true);
    ThermodynamicOperations ops = new ThermodynamicOperations(s);
    ops.saturateWithWater();
    double first = s.getComponent("water").getz();
    ops.saturateWithWater();
    double second = s.getComponent("water").getz();
    double relErr = Math.abs(second - first) / first;
    logger.info("idempotency: first={} second={} relErr={}%", first, second, relErr * 100.0);
    assertTrue(relErr < 0.05, "re-saturation changed water content by " + (relErr * 100.0) + "% (first=" + first
	+ ", second=" + second + ")");
  }
}
