package neqsim.blackoil;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;

/**
 * Black-oil conversion of a near-critical CO2-rich live oil.
 *
 * <p>
 * Just below the bubble point of a CO2-rich pre-salt type oil both coexisting phases are typed "oil" by the flash. The
 * converter must then separate them by density, otherwise the incipient gas is taken as the oil, the bubble point is
 * reported far too low and Bo jumps to unphysical values near saturation.
 *
 * @author esol
 */
public class BlackOilConverterNearCriticalTest {

  /**
   * Builds 557.8 mol of associated gas (43.2 mol % CO2) recombined with 100 mol of a TBP dead oil, Peng-Robinson.
   *
   * @return the live-oil system at 80 C
   */
  private static SystemInterface co2RichOil() {
    double gas = 557.8;
    double co2 = 0.432;
    String[] hc = {"nitrogen", "methane", "ethane", "propane", "i-butane", "n-butane", "i-pentane", "n-pentane",
        "n-hexane"};
    double[] x = {0.6, 78.0, 9.5, 6.0, 1.2, 2.3, 0.8, 0.9, 0.7};
    double sum = 0.0;
    for (double v : x) {
      sum += v;
    }
    SystemInterface f = new SystemPrEos(273.15 + 80.0, 650.0);
    f.addComponent("CO2", gas * co2);
    for (int i = 0; i < hc.length; i++) {
      f.addComponent(hc[i], gas * (1.0 - co2) * x[i] / sum);
    }
    f.addComponent("n-hexane", 4.0);
    f.addTBPfraction("C7", 8.0, 0.096, 0.738);
    f.addTBPfraction("C9", 10.0, 0.121, 0.765);
    f.addTBPfraction("C12", 14.0, 0.165, 0.800);
    f.addTBPfraction("C16", 14.0, 0.222, 0.835);
    f.addTBPfraction("C22", 18.0, 0.300, 0.870);
    f.addTBPfraction("C30", 14.0, 0.410, 0.905);
    f.addTBPfraction("C45", 18.0, 0.650, 0.950);
    f.setMixingRule("classic");
    f.useVolumeCorrection(true);
    f.setMultiPhaseCheck(true);
    return f;
  }

  /**
   * The bubble point is found above the region where both phases are typed "oil", and Bo is smooth and bounded.
   */
  @Test
  public void testNearCriticalPhasesSeparatedByDensity() {
    double[] grid = {50.0, 150.0, 250.0, 350.0, 400.0, 440.0, 460.0, 520.0, 600.0};
    BlackOilConverter.Result res = BlackOilConverter.convert(co2RichOil(), 273.15 + 80.0, grid, 1.01325, 273.15 + 15.0);
    assertTrue(res.bubblePoint >= 460.0, "bubble point " + res.bubblePoint);
    double previousRs = -1.0;
    for (double p : grid) {
      double bo = res.pvt.Bo(p);
      assertTrue(bo > 1.0 && bo < 2.5, "Bo at " + p + " = " + bo);
      double rs = res.pvt.Rs(p);
      assertTrue(rs >= previousRs - 1e-6, "Rs not monotonic at " + p);
      previousRs = rs;
    }
    assertTrue(res.pvt.Bo(460.0) > res.pvt.Bo(400.0), "Bo must rise towards the bubble point");
  }

  /**
   * Every undersaturated branch of the exported PVTO has Bo falling with pressure, as OPM Flow and Eclipse require.
   *
   * @throws java.io.IOException if the include file cannot be written
   */
  @Test
  public void testExportedPvtoUndersaturatedBranchesAreCompressible() throws java.io.IOException {
    double[] grid = {50.0, 150.0, 250.0, 350.0, 400.0, 440.0, 460.0, 520.0, 600.0};
    BlackOilConverter.Result res = BlackOilConverter.convert(co2RichOil(), 273.15 + 80.0, grid, 1.01325, 273.15 + 15.0);
    String text = neqsim.blackoil.io.EclipseEOSExporter.toString(res.pvt, res.rho_o_sc, res.rho_g_sc, res.rho_w_sc)
        .replace("\r\n", "\n");
    String pvto = text.substring(text.indexOf("PVTO"), text.indexOf("/\n\n", text.indexOf("PVTO")));
    int records = 0;
    for (String record : pvto.split("\n\\s*/\n")) {
      double previousBo = Double.MAX_VALUE;
      for (String line : record.split("\n")) {
        String[] tok = line.trim().split("\\s+");
        if (tok.length < 3 || !Character.isDigit(tok[0].charAt(0))) {
          continue;
        }
        double bo = Double.parseDouble(tok[tok.length - 2]);
        if (previousBo != Double.MAX_VALUE) {
          assertTrue(bo < previousBo, "Bo must fall along an undersaturated branch: " + record);
        }
        previousBo = bo;
      }
      records++;
    }
    assertTrue(records > 3, "PVTO records " + records);
  }
}
