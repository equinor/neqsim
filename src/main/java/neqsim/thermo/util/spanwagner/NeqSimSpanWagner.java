// File: src/main/java/neqsim/thermo/util/spanwagner/NeqSimSpanWagner.java
package neqsim.thermo.util.spanwagner;

import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

public class NeqSimSpanWagner {
  private PhaseInterface phase = null;
  private SpanWagner spanWagner = new SpanWagner();
  private boolean isInitialized = false;

  public NeqSimSpanWagner(PhaseInterface phase) {
    this.setPhase(phase);
    if (!isInitialized) {
      spanWagner.setup();
      isInitialized = true;
    }
  }

  public void setPhase(PhaseInterface phase) {
    if (phase.getNumberOfComponents() != 1
        || !("co2".equalsIgnoreCase(phase.getComponent(0).getComponentName())
            || "carbondioxide".equalsIgnoreCase(phase.getComponent(0).getComponentName()))) {
      throw new IllegalArgumentException("Span-Wagner model requires pure CO2.");
    }
    this.phase = phase;
  }

  public double getMolarDensity() {
    int[] ierr = {0};
    String[] herr = {""};
    double[] D = {0.0};
    spanWagner.density(0, phase.getTemperature(), phase.getPressure() * 100.0, D, ierr, herr);
    if (ierr[0] != 0) {
      System.err.println("NeqSimSpanWagner Warning: " + herr[0]);
    }
    return D[0];
  }

  public double getDensity() {
    return getMolarDensity() * spanWagner.M;
  }

  public double[] getAllProperties() {
    double[] p = new double[1], z = new double[1], dpdd = new double[1], d2pdd2 = new double[1],
        d2pdtd = new double[1], dpdt = new double[1], u = new double[1], h = new double[1],
        s = new double[1], cv = new double[1], cp = new double[1], w = new double[1],
        g = new double[1], jt = new double[1], kappa = new double[1], A = new double[1];

    spanWagner.properties(phase.getTemperature(), getMolarDensity(), p, z, dpdd, d2pdd2, d2pdtd,
        dpdt, u, h, s, cv, cp, w, g, jt, kappa, A);

    return new double[] {p[0], z[0], dpdd[0], d2pdd2[0], d2pdtd[0], dpdt[0], u[0], h[0], s[0],
        cv[0], cp[0], w[0], g[0], jt[0], kappa[0]};
  }

  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    SystemInterface fluid1 = new SystemSrkEos();
    fluid1.addComponent("CO2", 1.0);
    fluid1.setTemperature(310.0);
    fluid1.setPressure(80.0);
    fluid1.init(0);
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid1);
    ops.TPflash();

    NeqSimSpanWagner test = new NeqSimSpanWagner(fluid1.getPhase(0));

    System.out.println("--- Testing Span-Wagner EOS for CO2 ---");
    System.out.println("Input T: " + fluid1.getTemperature() + " K");
    System.out.println("Input P: " + fluid1.getPressure() + " bar (8 MPa)");

    double density = test.getDensity();
    System.out.printf("Calculated Density [kg/m^3]:        %.4f%n", density);
    System.out.println("  (NIST Reference @ 310K, 8MPa is ~327.71 kg/m^3)");

    if (Math.abs(density - 327.71) > 1.0) {
      System.out.println("\n*** WARNING: Calculation deviates significantly from reference! ***");
    } else {
      System.out.println(
          "\n*** Calculation result is close to reference. Implementation is correct. ***");
    }

    System.out.println("\n--- All Thermodynamic Properties ---");
    double[] properties = test.getAllProperties();
    System.out.printf("Pressure [kPa]:                     %.4f%n", properties[0]);
    System.out.printf("Compressibility factor (Z):         %.4f%n", properties[1]);
    System.out.printf("Enthalpy [J/mol]:                   %.4f%n", properties[7]);
    System.out.printf("Entropy [J/mol-K]:                  %.4f%n", properties[8]);
    System.out.printf("Isochoric heat capacity (Cv) [J/mol-K]:  %.4f%n", properties[9]);
    System.out.printf("Isobaric heat capacity (Cp) [J/mol-K]:   %.4f%n", properties[10]);
    System.out.printf("Speed of sound [m/s]:               %.4f%n", properties[11]);
    System.out.printf("Joule-Thomson coefficient [K/kPa]:  %.4f%n", properties[13]);
  }
}
