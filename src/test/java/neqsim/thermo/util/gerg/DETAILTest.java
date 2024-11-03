package neqsim.thermo.util.gerg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.netlib.util.StringW;
import org.netlib.util.doubleW;
import org.netlib.util.intW;

public class DETAILTest {
  private DETAIL detail;

  @BeforeEach
  public void setUp() {
    detail = new DETAIL();
    detail.SetupDetail();
  }

  @Test
  public void testSq() {
    double result = detail.sq(3.0);
    assertEquals(9.0, result, 1e-6);
  }

  @Test
  public void testMolarMassDetail() {
    double[] x = new double[22];
    x[1] = 0.94;
    x[3] = 0.05;
    x[20] = 0.01;
    doubleW Mm = new doubleW(0.0);
    detail.MolarMassDetail(x, Mm);
    assertEquals(17.320946, Mm.val, 1e-3);
  }

  @Test
  public void testPressureDetail() {
    double T = 300.0;
    double D = 10.0;
    double[] x = new double[22];
    x[1] = 0.94;
    x[3] = 0.05;
    x[20] = 0.01;
    doubleW P = new doubleW(0.0);
    doubleW Z = new doubleW(0.0);
    detail.PressureDetail(T, D, x, P, Z);
    assertTrue(P.val > 0);
    assertTrue(Z.val > 0);
  }

  @Test
  public void testDensityDetail() {
    double T = 300.0;
    double P = 1000.0;
    double[] x = new double[22];
    x[1] = 0.94;
    x[3] = 0.05;
    x[20] = 0.01;
    doubleW D = new doubleW(-1.0);
    intW ierr = new intW(0);
    StringW herr = new StringW("");
    detail.DensityDetail(T, P, x, D, ierr, herr);
    assertEquals(0, ierr.val);
    assertTrue(D.val > 0);
  }

  @Test
  public void testPropertiesDetail() {
    double T = 300.0;
    double D = 10.0;
    double[] x = new double[22];
    x[1] = 0.94;
    x[3] = 0.05;
    x[20] = 0.01;
    doubleW P = new doubleW(0.0);
    doubleW Z = new doubleW(0.0);
    doubleW dPdD = new doubleW(0.0);
    doubleW d2PdD2 = new doubleW(0.0);
    doubleW d2PdTD = new doubleW(0.0);
    doubleW dPdT = new doubleW(0.0);
    doubleW U = new doubleW(0.0);
    doubleW H = new doubleW(0.0);
    doubleW S = new doubleW(0.0);
    doubleW Cv = new doubleW(0.0);
    doubleW Cp = new doubleW(0.0);
    doubleW W = new doubleW(0.0);
    doubleW G = new doubleW(0.0);
    doubleW JT = new doubleW(0.0);
    doubleW Kappa = new doubleW(0.0);
    detail.PropertiesDetail(T, D, x, P, Z, dPdD, d2PdD2, d2PdTD, dPdT, U, H, S, Cv, Cp, W, G, JT,
        Kappa);
    assertTrue(P.val > 0);
    assertTrue(Z.val > 0);
    assertFalse(U.val > 0);
    assertFalse(H.val > 0);
    assertTrue(S.val < 0);
    assertTrue(Cv.val > 0);
    assertTrue(Cp.val > 0);
    assertTrue(W.val > 0);
    assertTrue(G.val > 0);
    assertTrue(JT.val != 0);
    assertTrue(Kappa.val > 0);
  }
}
