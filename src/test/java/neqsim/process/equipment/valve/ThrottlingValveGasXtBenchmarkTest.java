package neqsim.process.equipment.valve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Benchmarks the gas pressure drop of {@link ThrottlingValve} against the closed-form IEC 60534-2-1 equation and
 * documents the sensitivity to the terminal pressure-drop ratio factor xT.
 *
 * <p>
 * Background: a PEPR root-cause study of an injection-gas control valve (8 in globe, Cv 650, flow 85 t/h, pressure
 * ratio dP/P1 = 0.14) found that the default {@code xT = 0.137} of the sizing method produced about twice the pressure
 * drop of the same valve with {@code xT = 0.7}, which is the order of magnitude vendors quote for cage-guided globe
 * valves. The default is therefore not neutral for gas valves operating at pressure ratios above about 0.1 and must be
 * set from vendor data.
 * </p>
 *
 * @author ESOL
 */
public class ThrottlingValveGasXtBenchmarkTest {
  /** Valve coefficient conversion used by NeqSim (Cv = 1.156 Kv). */
  private static final double KV_TO_CV = 1.156;
  /** IEC 60534-2-1 constant N9 for standard m3/h, kPa and K. */
  private static final double N9 = 24.6;

  private static SystemInterface gas(double temperatureC, double pressureBara) {
    SystemInterface fluid = new SystemSrkEos(273.15 + temperatureC, pressureBara);
    fluid.addComponent("methane", 0.81);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.065);
    fluid.addComponent("CO2", 0.02);
    fluid.addComponent("nitrogen", 0.005);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /**
   * Pressure drop that NeqSim computes for a given mass flow through a valve with fixed Cv.
   *
   * @param flowKgPerHour mass flow [kg/h]
   * @param cv valve coefficient [US Cv]
   * @param p1Bara inlet pressure [bara]
   * @param temperatureC inlet temperature [C]
   * @param xT terminal pressure-drop ratio factor, or {@code NaN} to keep the NeqSim default
   * @return pressure drop [bar]
   */
  private static double neqsimDeltaP(double flowKgPerHour, double cv, double p1Bara, double temperatureC, double xT) {
    Stream in = new Stream("in", gas(temperatureC, p1Bara));
    in.setFlowRate(flowKgPerHour, "kg/hr");
    in.setTemperature(temperatureC, "C");
    in.setPressure(p1Bara, "bara");
    in.run();
    ThrottlingValve valve = new ThrottlingValve("valve", in);
    if (!Double.isNaN(xT)) {
      valve.getMechanicalDesign().getValveSizingMethod().setxT(xT);
    }
    valve.setCv(cv);
    valve.setPercentValveOpening(100.0);
    valve.setIsCalcOutPressure(true);
    valve.run();
    return p1Bara - valve.getOutletStream().getPressure("bara");
  }

  /**
   * Closed-form IEC 60534-2-1 mass flow for the same fluid state as the NeqSim stream.
   *
   * @param dP pressure drop [bar]
   * @param cv valve coefficient [US Cv]
   * @param p1Bara inlet pressure [bara]
   * @param temperatureC inlet temperature [C]
   * @param xT terminal pressure-drop ratio factor
   * @return mass flow [kg/h]
   */
  private static double iecMassFlow(double dP, double cv, double p1Bara, double temperatureC, double xT) {
    Stream in = new Stream("ref", gas(temperatureC, p1Bara));
    in.setFlowRate(1000.0, "kg/hr");
    in.setTemperature(temperatureC, "C");
    in.setPressure(p1Bara, "bara");
    in.run();
    SystemInterface f = in.getFluid();
    double mw = f.getMolarMass() * 1000.0;
    double z = f.getZ();
    double gamma = f.getPhase(0).getGamma();
    double fGamma = gamma / 1.4;
    double x = Math.min(dP / p1Bara, fGamma * xT);
    double y = Math.max(1.0 - x / (3.0 * fGamma * xT), 2.0 / 3.0);
    double kv = cv / KV_TO_CV;
    double qStd = kv * N9 * (p1Bara * 100.0) * y * Math.sqrt(x / (mw * (273.15 + temperatureC) * z));
    double rhoStd = 101.325 * mw / (8.314462 * 273.15);
    return qStd * rhoStd;
  }

  /**
   * Bisection for the pressure drop at which the closed-form flow equals the target flow.
   */
  private static double iecDeltaP(double flowKgPerHour, double cv, double p1Bara, double temperatureC, double xT) {
    double lo = 1.0e-3;
    double hi = 0.6 * p1Bara;
    for (int i = 0; i < 80; i++) {
      double mid = 0.5 * (lo + hi);
      if (iecMassFlow(mid, cv, p1Bara, temperatureC, xT) < flowKgPerHour) {
        lo = mid;
      } else {
        hi = mid;
      }
    }
    return 0.5 * (lo + hi);
  }

  /** Low pressure ratio (dP/P1 about 0.013): NeqSim must agree with the closed form within 6 percent. */
  @Test
  public void lowPressureRatioAgreesWithIecClosedForm() {
    double p1 = 156.6;
    double cv = 459.7;
    double flow = 210548.0;
    double dpNeqsim = neqsimDeltaP(flow, cv, p1, 50.0, 0.7);
    double dpIec = iecDeltaP(flow, cv, p1, 50.0, 0.7);
    assertEquals(dpIec, dpNeqsim, 0.06 * dpIec, "NeqSim dP " + dpNeqsim + " vs IEC " + dpIec);
  }

  /** Moderate pressure ratio (dP/P1 about 0.14): within 10 percent of the closed form when xT is set. */
  @Test
  public void moderatePressureRatioAgreesWithIecClosedForm() {
    double p1 = 181.36;
    double cv = 50.04;
    double flow = 84850.0;
    double dpNeqsim = neqsimDeltaP(flow, cv, p1, 50.0, 0.7);
    double dpIec = iecDeltaP(flow, cv, p1, 50.0, 0.7);
    assertEquals(dpIec, dpNeqsim, 0.10 * dpIec, "NeqSim dP " + dpNeqsim + " vs IEC " + dpIec);
  }

  /** The default xT (0.137) gives a clearly larger pressure drop than a vendor-typical xT of 0.7 at dP/P1 of 0.14. */
  @Test
  public void defaultXtDominatesPressureDropAtModeratePressureRatio() {
    double p1 = 181.36;
    double cv = 50.04;
    double flow = 84850.0;
    double dpExplicit = neqsimDeltaP(flow, cv, p1, 50.0, 0.7);
    double dpDefault = neqsimDeltaP(flow, cv, p1, 50.0, Double.NaN);
    assertTrue(dpDefault > 1.5 * dpExplicit,
        "default xT should inflate dP; explicit " + dpExplicit + " default " + dpDefault);
  }
}
