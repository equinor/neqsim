package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Verification of the EOS-CG-2021 model against published reference data (2021 and newer).
 *
 * <p>
 * The model was refreshed to EOS-CG-2021 parameters in PR #2255. EOS-CG-2021 retains the pure-fluid
 * reference equation of state for CO2 (Span &amp; Wagner) unchanged, so single-component CO2
 * densities, speed of sound and heat capacities must reproduce the NIST/REFPROP reference values
 * (which implement the identical pure-fluid EOS) to within the density-solver tolerance.
 * </p>
 *
 * <p>
 * Reference data sources:
 * </p>
 * <ul>
 * <li>Neumann, T., Herrig, S., Bell, I.H., Beckmüller, R., Lemmon, E.W., Thol, M., &amp; Span, R.
 * (2023). <i>EOS-CG-2021: A Mixture Model for the Calculation of Thermodynamic Properties of CCS
 * Mixtures</i>. International Journal of Thermophysics, 44, 178.</li>
 * <li>NIST Chemistry WebBook / REFPROP (Span &amp; Wagner CO2 reference EOS), accessed via
 * {@code webbook.nist.gov/cgi/fluid.cgi} for CO2 isotherms at 298.15 K and 313.15 K.</li>
 * </ul>
 *
 * @author neqsim-agent
 * @version 1.0
 */
public class SystemEOSCGEos2021ReferenceTest extends neqsim.NeqSimTest {
  static Logger logger = LogManager.getLogger(SystemEOSCGEos2021ReferenceTest.class);

  /** Relative tolerance for single-phase CO2 density reproduction (0.5 %). */
  private static final double DENSITY_REL_TOL = 5.0e-3;

  /**
   * Computes the single-phase CO2 density with EOS-CG-2021 at the requested state.
   *
   * @param temperature temperature in K (valid range &gt; 216 K, &lt; 1100 K)
   * @param pressure pressure in bara (valid range &gt; 0)
   * @return density in kg/m3 of the stable phase
   */
  private double co2Density(double temperature, double pressure) {
    SystemInterface system = new SystemEOSCGEos(temperature, pressure);
    system.addComponent("CO2", 1.0);
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();
    return system.getPhase(0).getDensity("kg/m3");
  }

  /**
   * Asserts that a calculated value reproduces a reference value within a relative tolerance.
   *
   * @param label descriptive label for the assertion message
   * @param actual the EOS-CG-2021 calculated value
   * @param reference the published reference value
   * @param relTol the allowed relative deviation (fraction)
   */
  private void assertReference(String label, double actual, double reference, double relTol) {
    double deviation = Math.abs(actual - reference) / reference;
    logger.debug(String.format("%s: EOS-CG=%.4f, ref=%.4f, dev=%.4f%%", label, actual, reference,
        deviation * 100.0));
    assertTrue(deviation < relTol,
        () -> String.format(
            "%s: EOS-CG-2021 value %.5f deviates %.4f%% from reference %.5f "
                + "(tolerance %.3f%%)",
            label, actual, deviation * 100.0, reference, relTol * 100.0));
  }

  @Test
  @DisplayName("CO2 gas-phase density at 298.15 K reproduces NIST reference (EOS-CG-2021)")
  public void testCO2GasDensity298() {
    // NIST WebBook (Span-Wagner reference EOS, reproduced by EOS-CG-2021) at 298.15 K.
    assertReference("CO2 density 298.15 K, 10 bar", co2Density(298.15, 10.0), 18.716,
        DENSITY_REL_TOL);
    assertReference("CO2 density 298.15 K, 30 bar", co2Density(298.15, 30.0), 64.091,
        DENSITY_REL_TOL);
    assertReference("CO2 density 298.15 K, 50 bar", co2Density(298.15, 50.0), 131.27,
        DENSITY_REL_TOL);
  }

  @Test
  @DisplayName("CO2 liquid-phase density at 298.15 K reproduces NIST reference (EOS-CG-2021)")
  public void testCO2LiquidDensity298() {
    // NIST WebBook at 298.15 K, liquid branch (above saturation at ~64.3 bar).
    assertReference("CO2 density 298.15 K, 70 bar", co2Density(298.15, 70.0), 743.03,
        DENSITY_REL_TOL);
    assertReference("CO2 density 298.15 K, 90 bar", co2Density(298.15, 90.0), 799.65,
        DENSITY_REL_TOL);
    assertReference("CO2 density 298.15 K, 120 bar", co2Density(298.15, 120.0), 845.47,
        DENSITY_REL_TOL);
  }

  @Test
  @DisplayName("CO2 supercritical dense-phase density at 313.15 K reproduces NIST (EOS-CG-2021)")
  public void testCO2DensePhaseDensity313() {
    // NIST WebBook at 313.15 K (typical CCS dense-phase pipeline temperature).
    assertReference("CO2 density 313.15 K, 20 bar", co2Density(313.15, 20.0), 37.127,
        DENSITY_REL_TOL);
    assertReference("CO2 density 313.15 K, 120 bar", co2Density(313.15, 120.0), 717.76,
        DENSITY_REL_TOL);
    assertReference("CO2 density 313.15 K, 160 bar", co2Density(313.15, 160.0), 794.90,
        DENSITY_REL_TOL);
    assertReference("CO2 density 313.15 K, 200 bar", co2Density(313.15, 200.0), 839.31,
        DENSITY_REL_TOL);
  }

  @Test
  @DisplayName("CO2 caloric properties at 298.15 K, 1 bar reproduce NIST reference (EOS-CG-2021)")
  public void testCO2CaloricProperties() {
    // NIST WebBook (isobaric, 1 bar) for CO2 vapour: at 298 K Cp = 37.435, Cv = 28.928,
    // w = 268.57 m/s; at 299 K Cp = 37.478, Cv = 28.972, w = 268.98 m/s. Interpolated to
    // 298.15 K: Cp = 37.442 J/mol-K, Cv = 28.935 J/mol-K, w = 268.63 m/s.
    double temperature = 298.15;
    double pressure = 1.0;
    SystemInterface system = new SystemEOSCGEos(temperature, pressure);
    system.addComponent("CO2", 1.0);
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.init(3);
    system.initProperties();

    double moles = system.getPhase(0).getNumberOfMolesInPhase();
    double cp = system.getPhase(0).getCp() / moles; // J/mol-K
    double cv = system.getPhase(0).getCv() / moles; // J/mol-K
    double w = system.getPhase(0).getSoundSpeed(); // m/s

    // The GERG-2008/EOS-CG model natively reproduces the NIST reference values above for pure CO2;
    // its Cp/Cv and the derived speed of sound match the NIST data within 0.5 %.
    assertReference("CO2 Cp 298.15 K, 1 bar", cp, 37.442, 5.0e-3);
    assertReference("CO2 Cv 298.15 K, 1 bar", cv, 28.935, 5.0e-3);
    assertReference("CO2 speed of sound 298.15 K, 1 bar", w, 268.63, 5.0e-3);
  }

  @Test
  @DisplayName("EOS-CG-2021 MDEA pure-fluid parameters (Neumann et al. 2022)")
  public void testMdeaPureFluidReference() {
    // Neumann, T., Baumhögger, E., Span, R., Vrabec, J., & Thol, M. (2022). Thermodynamic
    // Properties of Methyl Diethanolamine. Int. J. Thermophys. 43, 10.
    // MDEA occupies EOS-CG-2021 component slot 28; molar mass 119.1622 g/mol.
    neqsim.thermo.util.gerg.EOSCG eos = new neqsim.thermo.util.gerg.EOSCG();
    eos.setup();
    double[] composition = new double[29];
    composition[28] = 1.0;

    org.netlib.util.doubleW molarMass = new org.netlib.util.doubleW(0.0);
    eos.molarMass(composition, molarMass);
    assertEquals(119.1622, molarMass.val, 1.0e-8, "MDEA molar mass must match Neumann (2022)");

    // Liquid-density consistency: p(T=300 K, rho=8.8 mol/dm3) from the MDEA pure-fluid EOS.
    org.netlib.util.doubleW pressure = new org.netlib.util.doubleW(0.0);
    org.netlib.util.doubleW z = new org.netlib.util.doubleW(0.0);
    eos.pressure(300.0, 8.8, composition, pressure, z);
    assertEquals(34241.92443746, pressure.val, 1.0e-4,
        "MDEA pressure must match the EOS-CG-2021 MDEA pure-fluid implementation");
  }

  @Test
  @DisplayName("EOS-CG-2021 CCS dense-phase CO2-rich mixture density is physically consistent")
  public void testCcsCo2RichMixtureDensityConsistency() {
    // CCS-relevant dense-phase stream: 96 % CO2 with 4 % N2 at 313.15 K, 150 bara.
    // EOS-CG-2021 is the recommended model for such mixtures. No single NIST tabulation exists
    // for the blend, so we verify the mixture density lies (physically) below the pure-CO2 dense
    // density at the same state and remains close to it (light impurity, small fraction).
    double temperature = 313.15;
    double pressure = 150.0;

    SystemInterface mixture = new SystemEOSCGEos(temperature, pressure);
    mixture.addComponent("CO2", 0.96);
    mixture.addComponent("nitrogen", 0.04);
    ThermodynamicOperations ops = new ThermodynamicOperations(mixture);
    ops.TPflash();
    mixture.initProperties();
    double mixDensity = mixture.getPhase(0).getDensity("kg/m3");

    double pureCo2Density = co2Density(temperature, pressure);

    logger.debug(String.format("CCS mixture density=%.2f kg/m3, pure CO2=%.2f kg/m3", mixDensity,
        pureCo2Density));

    // N2 lowers the density relative to pure CO2 at the same T,P (lighter, less dense impurity).
    assertTrue(mixDensity < pureCo2Density,
        "4 % N2 must reduce dense-phase density below pure CO2 at the same state");
    // The reduction must be modest for only 4 mol% impurity (within ~15 %).
    assertTrue((pureCo2Density - mixDensity) / pureCo2Density < 0.15,
        "Density reduction for 4 % N2 should be modest");
    // Density must remain in the dense-phase range expected for CCS transport.
    assertTrue(mixDensity > 600.0 && mixDensity < pureCo2Density,
        "CCS dense-phase mixture density should be in a realistic range");
  }
}
