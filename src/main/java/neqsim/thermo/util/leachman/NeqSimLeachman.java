package neqsim.thermo.util.leachman;

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
 * NeqSimLeachman class.
 * </p>
 *
 * @author victorigi99
 */
public class NeqSimLeachman {
  PhaseInterface phase = null;
  Leachman Leachman = new Leachman();

  /**
   * Constructor for NeqSimLeachman.
   */
  public NeqSimLeachman() {}

  /**
   * Constructor for NeqSimLeachman.
   * If hydrogenType is provided (non-empty), it uses that; otherwise, it auto-detects
   * based on the phase's component name.
   *
   * @param phase a PhaseInterface representing the stream.
   * @param hydrogenType a String representing the desired hydrogen type; can be empty.
   */
  public NeqSimLeachman(PhaseInterface phase, String hydrogenType) {
    setPhase(phase);
    // If hydrogenType is null or empty, auto-detect based on the phase's component name
    if (hydrogenType == null || hydrogenType.trim().isEmpty()) {
      String componentName = phase.getComponent(0).getComponentName().toLowerCase();
      if (componentName.equals("para-hydrogen")) {
        hydrogenType = "para";
      } else if (componentName.equals("ortho-hydrogen")) {
        hydrogenType = "ortho";
      } else {
        hydrogenType = "normal";
      }
    }
    Leachman.SetupLeachman(hydrogenType);
  }

  /**
   * Get the molar density of the specified phase.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double representing the molar density
   */
  public double getMolarDensity(PhaseInterface phase) {
    return getMolarDensity();
  }

  /**
   * Get the density of the specified phase.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double representing the density
   */
  public double getDensity(PhaseInterface phase) {
    return getMolarDensity() * Leachman.M_L;
  }

  /**
   * Get the density.
   *
   * @return a double representing the density
   */
  public double getDensity() {
    return getMolarDensity() * Leachman.M_L;
  }

  /**
   * Get the pressure.
   *
   * @return a double representing the pressure
   */
  public double getPressure() {
    double moldens = getMolarDensity();
    doubleW P = new doubleW(0.0);
    doubleW Z = new doubleW(0.0);
    Leachman.PressureLeachman(phase.getTemperature(), moldens, P, Z);
    return P.val;
  }

  /**
   * Get the molar density.
   *
   * @return a double representing the molar density
   */
  public double getMolarDensity() {
    int flag = 0;
    intW ierr = new intW(0);
    StringW herr = new StringW("");
    doubleW D = new doubleW(0.0);
    double pressure = phase.getPressure() * 100.0;
    Leachman.DensityLeachman(flag, phase.getTemperature(), pressure, D, ierr, herr);
    return D.val;
  }

  /**
   * Get various thermodynamic properties of the specified phase.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return an array of type double representing various thermodynamic properties
   */
  public double[] propertiesLeachman(PhaseInterface phase) {
    return propertiesLeachman();
  }

  /**
   * Get specific thermodynamic properties of the specified phase.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param properties an array of {@link java.lang.String} objects representing the properties to
   *        retrieve
   * @return an array of type double representing the requested properties
   */
  public double[] getProperties(PhaseInterface phase, String[] properties) {
    double[] allProperties = propertiesLeachman();
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
   * Get various thermodynamic properties.
   *
   * @return an array of type double representing various thermodynamic properties
   */
  public double[] propertiesLeachman() {
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
    Leachman.propertiesLeachman(phase.getTemperature(), dens, p, z, dpdd, d2pdd2, d2pdtd, dpdt, u,
        h, s, cv, cp, w, g, jt, kappa, A);
    double[] properties = new double[] {p.val, z.val, dpdd.val, d2pdd2.val, d2pdtd.val, dpdt.val,
        u.val, h.val, s.val, cv.val, cp.val, w.val, g.val, jt.val, kappa.val};
    return properties;
  }

  /**
   * Setter for the field <code>phase</code>.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public void setPhase(PhaseInterface phase) {
    // 1) Check if the phase contains ONLY hydrogen
    if (phase.getNumberOfComponents() != 1) {
      throw new IllegalArgumentException(
          "Leachman model requires exactly one component (hydrogen). " + "Found "
              + phase.getNumberOfComponents() + " components.");
    }

    // 2) Check the name of that single component
    String componentName = phase.getComponent(0).getComponentName();
    if (!"hydrogen".equalsIgnoreCase(componentName) && !"para-hydrogen".equalsIgnoreCase(componentName)
        && !"ortho-hydrogen".equalsIgnoreCase(componentName)) {
      throw new IllegalArgumentException(
          "Leachman model requires 'hydrogen'. Found: " + componentName);
    }

    // If everything checks out, we can safely set 'this.phase'
    this.phase = phase;
  }


  /**
   * <p>getAlpha0_Leachman.</p>
   *
   * @return an array of {@link org.netlib.util.doubleW} objects
   */
  public doubleW[] getAlpha0_Leachman() {
    // Get temperature and molar density from the phase object
    double temperature = phase.getTemperature();
    double molarDensity = getMolarDensity(phase);

    // Create and initialize a 4x4 array for the derivatives.
    // Assuming doubleW is a simple wrapper with a public field 'val'.
    int rows = 4;
    int cols = 4;
    doubleW[] a0 = new doubleW[rows];
    for (int i = 0; i < rows; i++) {
        a0[i] = new doubleW(0.0);
    }

    // Call the Vega function to fill in the ar array.
    // The first two parameters (itau and idelta) are set to 0.
    Leachman.Alpha0Leachman(temperature, molarDensity, a0);

    // Return the computed dimensionless residual Helmholtz free energy.
    // This is equivalent to alpha_res = A^r/(RT)
    return a0;
} 

/**
 * Get reduced residual helmholtz free energy and its derivatives.
 * The returned array has the following structure:
 * <ul>
 * <li>ar(0,0) - Residual Helmholtz energy (dimensionless, =a/RT)</li>
 * <li>ar(0,1) - delta*partial (ar)/partial(delta)</li>
 * <li>ar(0,2) - delta^2*partial^2(ar)/partial(delta)^2</li>
 * <li>ar(0,3) - delta^3*partial^3(ar)/partial(delta)^3</li>
 * <li>ar(1,0) - tau*partial (ar)/partial(tau)</li>
 * <li>ar(1,1) - tau*delta*partial^2(ar)/partial(tau)/partial(delta)</li>
 * <li>ar(2,0) - tau^2*partial^2(ar)/partial(tau)^2</li>
 * </ul>
 *
 * @return a doubleW[][] representing the reduced residual helmholtz free energy
 */
public doubleW[][] getAlphares_Leachman() {
  // Get temperature and molar density from the phase object
  double temperature = phase.getTemperature();
  double molarDensity = getMolarDensity(phase);

  // Create and initialize a 4x4 array for the derivatives.
  // Assuming doubleW is a simple wrapper with a public field 'val'.
  int rows = 4;
  int cols = 4;
  doubleW[][] ar = new doubleW[rows][cols];
  for (int i = 0; i < rows; i++) {
      for (int j = 0; j < cols; j++) {
          ar[i][j] = new doubleW(0.0);
      }
  }

  // Call the Vega function to fill in the ar array.
  // The first two parameters (itau and idelta) are set to 0.
  Leachman.AlpharLeachman(0, 0, temperature, molarDensity, ar);

  // Return the computed dimensionless residual Helmholtz free energy.
  // This is equivalent to alpha_res = A^r/(RT)
  return ar;
}

  /**
   * main method.
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    // test HitHub
    SystemInterface fluid1 = new SystemSrkEos();
    // fluid1.addComponent("methane", 10.0);
    fluid1.addComponent("hydrogen", 90.0);
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
    fluid1.setTemperature(300);
    fluid1.setPressure(50.0);
    fluid1.init(0);
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid1);
    ops.TPflash();
    // fluid1.display();
    String hydrogenType = "normal";
    System.out.println("density Leachman " + fluid1.getPhase(0).getDensity_Leachman(hydrogenType));

    NeqSimLeachman test = new NeqSimLeachman(fluid1.getPhase("gas"), hydrogenType);
    // fluid1.getPhase("gas").getProperties_GERG2008();
    System.out.println("density " + test.getDensity());
    System.out.println("pressure " + test.getPressure());
    // System.out.println("properties " + test.propertiesGERG());
    double[] properties = test.propertiesLeachman();
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
