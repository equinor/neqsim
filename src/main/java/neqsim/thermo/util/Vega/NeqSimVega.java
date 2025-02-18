package neqsim.thermo.util.Vega;

import org.netlib.util.StringW;
import org.netlib.util.doubleW;
import org.netlib.util.intW;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * NeqSimVega class.
 * </p>
 *
 * @author [author]
 */
public class NeqSimVega {
  PhaseInterface phase = null;
  Vega Vega = new Vega();

  /**
   * <p>
   * Constructor for NeqSimGERG2008.
   * </p>
   */
  public NeqSimVega() {}

  /**
   * <p>
   * Constructor for NeqSimGERG2008.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public NeqSimVega(PhaseInterface phase) {
    this.setPhase(phase);
    if (Double.isNaN(Vega.R) || Vega.R == 0) {
      Vega.SetupVega();
    }
  }

  /**
   * <p>
   * getMolarDensity.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double getMolarDensity(PhaseInterface phase) {
    // this.setPhase(phase);
    return getMolarDensity();
  }

  /**
   * <p>
   * getDensity.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double getDensity(PhaseInterface phase) {
    // this.setPhase(phase);
    // return getMolarDensity() * getMolarMass() * 1000.0;
    return getMolarDensity() * Vega.M;
  }

  /**
   * <p>
   * getDensity.
   * </p>
   *
   * @return a double
   */
  public double getDensity() {
    return getMolarDensity() * Vega.M;
    // return getMolarDensity() * getMolarMass()* 1000.0;
  }

  /**
   * <p>
   * getPressure.
   * </p>
   *
   * @return a double
   */
  public double getPressure() {
    double moldens = getMolarDensity();
    doubleW P = new doubleW(0.0);
    doubleW Z = new doubleW(0.0);
    Vega.PressureVega(phase.getTemperature(), moldens, P, Z);
    return P.val;
  }

  /**
   * <p>
   * getMolarMass.
   * </p>
   *
   * @return a double
   */
  // public double getMolarMass() {
  // doubleW mm = new doubleW(0.0);
  // GERG2008.MolarMassGERG(normalizedGERGComposition, mm);
  // return mm.val / 1.0e3;
  // }

  /**
   * <p>
   * getMolarDensity.
   * </p>
   *
   * @return a double
   */
  public double getMolarDensity() {
    int flag = 0;
    intW ierr = new intW(0);
    StringW herr = new StringW("");
    doubleW D = new doubleW(0.0);
    double pressure = phase.getPressure() * 100.0;
    Vega.DensityVega(flag, phase.getTemperature(), pressure, D, ierr, herr);
    return D.val;
  }

  /**
   * <p>
   * propertiesGERG.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return an array of type double
   */
  public double[] propertiesVega(PhaseInterface phase) {
    // this.setPhase(phase);
    return propertiesVega();
  }

  /**
   * <p>
   * getProperties.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param properties an array of {@link java.lang.String} objects
   * @return an array of type double
   */
  public double[] getProperties(PhaseInterface phase, String[] properties) {
    double[] allProperties = propertiesVega();
    double[] returnProperties = new double[properties.length];

    for (int i = 0; i < properties.length; i++) {
      switch (properties[i]) {
        case "density":
          returnProperties[i] = allProperties[0];
          break;
        case "Cp":
          returnProperties[i] = allProperties[1];
          break;
        case "Cv":
          returnProperties[i] = allProperties[2];
          break;
        case "soundSpeed":
          returnProperties[i] = allProperties[3];
          break;
        default:
          break;
      }
    }
    return returnProperties;
  }

  /**
   * <p>
   * propertiesGERG.
   * </p>
   *
   * @return an array of type double
   */
  public double[] propertiesVega() {
    doubleW p = new doubleW(0.0);
    doubleW z = new doubleW(0.0);
    doubleW dpdd = new doubleW(0.0);
    doubleW d2pdd2 = new doubleW(0.0);
    doubleW d2pdtd = new doubleW(0.0);
    doubleW dpdt = new doubleW(0.0);
    doubleW u = new doubleW(0.0);
    doubleW h = new doubleW(0.0);
    doubleW s = new doubleW(0.0);
    doubleW cv = new doubleW(0.0);
    doubleW cp = new doubleW(0.0);
    doubleW w = new doubleW(0.0);
    doubleW g = new doubleW(0.0);
    doubleW jt = new doubleW(0.0);
    doubleW kappa = new doubleW(0.0);
    doubleW A = new doubleW(0.0);
    double dens = getMolarDensity();
    // neqsim.thermo.GERG.Densitygerg.densitygerg(0, 0, 0, arg3, 0, arg5, arg6,
    // arg7);
    Vega.propertiesVega(phase.getTemperature(), dens, p, z, dpdd, d2pdd2, d2pdtd, dpdt, u, h, s, cv,
        cp, w, g, jt, kappa, A);
    double[] properties = new double[] {p.val, z.val, dpdd.val, d2pdd2.val, d2pdtd.val, dpdt.val,
        u.val, h.val, s.val, cv.val, cp.val, w.val, g.val, jt.val, kappa.val};
    return properties;
  }

  /**
   * <p>
   * Setter for the field <code>phase</code>.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public void setPhase(PhaseInterface phase) {
    // 1) Check if the phase contains ONLY hydrogen
    if (phase.getNumberOfComponents() != 1) {
      throw new IllegalArgumentException("Vega model requires exactly one component (helium). "
          + "Found " + phase.getNumberOfComponents() + " components.");
    }

    // 2) Check the name of that single component
    String componentName = phase.getComponent(0).getComponentName();
    if (!"helium".equalsIgnoreCase(componentName)) {
      throw new IllegalArgumentException("Vega model requires 'helium'. Found: " + componentName);
    }

    // If everything checks out, we can safely set 'this.phase'
    this.phase = phase;
  }

  /**
   * <p>
   * normalizeComposition.
   * </p>
   */
  /*
   * public void normalizeComposition() { double result = 0; for (double value :
   * notNormalizedGERGComposition) { result += value; } for (int k = 0; k <
   * normalizedGERGComposition.length; k++) { normalizedGERGComposition[k] =
   * notNormalizedGERGComposition[k] / result; } }
   */

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    // test HitHub
    SystemInterface fluid1 = new SystemSrkEos();
    // fluid1.addComponent("methane", 10.0);
    fluid1.addComponent("helium", 90.0);
    // fluid1.addComponent("CO2", 1.0);
    // fluid1.addComponent("ethane", 10.0);
    // fluid1.addComponent("propane", 3.0);
    // fluid1.addComponent("n-butane", 1.0);
    // fluid1.addComponent("oxygen", 1.0);
    /*
     * fluid1.addComponent("n-butane", 0.006304); fluid1.addComponent("i-butane", 0.003364);
     * fluid1.addComponent("n-pentane", 0.001005); fluid1.addComponent("i-pentane", 0.000994);
     * fluid1.addComponent("n-hexane", 0.000369); fluid1.addComponent("n-heptane", 0.000068);
     * fluid1.addComponent("n-octane", 0.000008);
     */
    // fluid//1.addComponent("ethane", 5.0);
    // fluid1.addComponent("propane", 5.0);
    // fluid1.addTBPfraction("C8", 0.01, 211.0/1000.0, 0.82);
    fluid1.setTemperature(273.15);
    fluid1.setPressure(10.0);// bar
    fluid1.init(0);
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid1);
    ops.TPflash();
    // fluid1.display();
    // String hydrogenType = "normal";
    System.out.println("density Vega " + fluid1.getPhase(0).getDensity_Vega());

    NeqSimVega test = new NeqSimVega(fluid1.getPhase(0));
    // fluid1.getPhase("gas").getProperties_Leachman("normal");
    System.out.println("density " + test.getDensity());
    System.out.println("pressure " + test.getPressure());
    // System.out.println("properties " + test.propertiesGERG());
    double[] properties = test.propertiesVega();
    System.out.println("Pressure [kPa]:            " + properties[0]);
    System.out.println("Compressibility factor:            " + properties[1]);
    System.out.println("d(P)/d(rho) [kPa/(mol/l)]            " + properties[2]);
    System.out.println("d^2(P)/d(rho)^2 [kPa/(mol/l)^2]:            " + properties[3]);
    System.out.println("d2(P)/d2(T) [kPa/K]:             " + properties[4]);
    System.out.println("d(P)/d(T) [kPa/K]:             " + properties[5]);
    System.out.println("Energy [J/mol]:             " + properties[6]);
    System.out.println("Enthalpy [J/mol]:             " + properties[7]);
    System.out.println("Entropy [J/mol-K]:             " + properties[8]);
    System.out.println("Isochoric heat capacity [J/mol-K]:             " + properties[9]);
    System.out.println("Isobaric heat capacity [J/mol-K]:            " + properties[10]);
    System.out.println("Speed of sound [m/s]:            " + properties[11]);
    System.out.println("Gibbs energy [J/mol]:            " + properties[12]);
    System.out.println("Joule-Thomson coefficient [K/kPa]:            " + properties[13]);
    System.out.println("Isentropic exponent:           " + properties[14]);
  }
}
