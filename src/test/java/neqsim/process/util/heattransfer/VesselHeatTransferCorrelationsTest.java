package neqsim.process.util.heattransfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link VesselHeatTransferCorrelations}.
 *
 * <p>
 * Validates the dimensionless correlations (Rayleigh, Reynolds, Churchill-Chu, Woodfield) against hand-computed
 * reference values and checks that all methods reject non-physical inputs.
 *
 * @author ESOL
 * @version 1.0
 */
public class VesselHeatTransferCorrelationsTest {

  /** Rayleigh number must equal g*beta*dT*L^3/(nu*alpha). */
  @Test
  public void rayleighMatchesDefinition() {
    double beta = 1.0 / 300.0;
    double dT = 50.0;
    double l = 2.0;
    double nu = 1.5e-5;
    double alpha = 2.0e-5;
    double expected = VesselHeatTransferCorrelations.GRAVITY * beta * dT * Math.pow(l, 3.0) / (nu * alpha);
    double ra = VesselHeatTransferCorrelations.rayleigh(beta, dT, l, nu, alpha);
    assertEquals(expected, ra, expected * 1.0e-9);
    assertTrue(ra > 0.0);
  }

  /** Reynolds number must equal rho*v*d/mu. */
  @Test
  public void reynoldsMatchesDefinition() {
    double rho = 50.0;
    double v = 20.0;
    double d = 0.05;
    double mu = 1.2e-5;
    double expected = rho * v * d / mu;
    assertEquals(expected, VesselHeatTransferCorrelations.reynolds(rho, v, d, mu), expected * 1.0e-9);
  }

  /** Churchill-Chu Nusselt must match the closed-form value for a known Rayleigh/Prandtl pair. */
  @Test
  public void churchillChuMatchesReference() {
    double ra = 1.0e9;
    double pr = 0.7;
    double denom = Math.pow(1.0 + Math.pow(0.492 / pr, 9.0 / 16.0), 8.0 / 27.0);
    double base = 0.825 + 0.387 * Math.pow(ra, 1.0 / 6.0) / denom;
    double expected = base * base;
    assertEquals(expected, VesselHeatTransferCorrelations.churchillChuNusselt(ra, pr), expected * 1.0e-9);
  }

  /** Woodfield filling Nusselt must match 0.56*Re^0.67 + 0.104*Ra^0.352. */
  @Test
  public void woodfieldMatchesReference() {
    double re = 1.0e5;
    double ra = 1.0e10;
    double expected = 0.56 * Math.pow(re, 0.67) + 0.104 * Math.pow(ra, 0.352);
    assertEquals(expected, VesselHeatTransferCorrelations.woodfieldFillingNusselt(re, ra), expected * 1.0e-9);
  }

  /** Heat-transfer coefficient must equal Nu*k/L. */
  @Test
  public void heatTransferCoefficientMatchesDefinition() {
    double nu = 120.0;
    double k = 0.03;
    double l = 2.0;
    assertEquals(nu * k / l, VesselHeatTransferCorrelations.heatTransferCoefficient(nu, k, l), 1.0e-12);
  }

  /** Larger Rayleigh number must give a larger Churchill-Chu Nusselt number. */
  @Test
  public void churchillChuIncreasesWithRayleigh() {
    double low = VesselHeatTransferCorrelations.churchillChuNusselt(1.0e6, 0.7);
    double high = VesselHeatTransferCorrelations.churchillChuNusselt(1.0e10, 0.7);
    assertTrue(high > low);
  }

  /** Non-positive transport properties must be rejected. */
  @Test
  public void rejectsInvalidInputs() {
    assertThrows(IllegalArgumentException.class,
        () -> VesselHeatTransferCorrelations.rayleigh(0.003, 10.0, 1.0, 0.0, 2.0e-5));
    assertThrows(IllegalArgumentException.class,
        () -> VesselHeatTransferCorrelations.reynolds(0.0, 10.0, 0.05, 1.0e-5));
    assertThrows(IllegalArgumentException.class, () -> VesselHeatTransferCorrelations.churchillChuNusselt(-1.0, 0.7));
    assertThrows(IllegalArgumentException.class,
        () -> VesselHeatTransferCorrelations.woodfieldFillingNusselt(-1.0, 1.0e9));
    assertThrows(IllegalArgumentException.class,
        () -> VesselHeatTransferCorrelations.heatTransferCoefficient(100.0, 0.0, 1.0));
  }
}
