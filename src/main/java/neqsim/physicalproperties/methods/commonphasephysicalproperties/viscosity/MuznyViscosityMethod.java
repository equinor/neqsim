package neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity;

import neqsim.physicalproperties.system.PhysicalProperties;

public class MuznyViscosityMethod extends Viscosity{
  /**
   * <p>
   * Constructor for MuznyViscosityMethod.
   * </p>
   *
   * @param phase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public MuznyViscosityMethod(PhysicalProperties phase) {
    super(phase);
  }

  /** {@inheritDoc} */
  @Override
  public double calcViscosity() {
    // Check if there are other components than helium
    if (phase.getPhase().getNumberOfComponents() > 1 
      || !phase.getPhase().getComponent(0).getName().equalsIgnoreCase("hydrogen")) {
      throw new Error("Muzny viscosity model only supports PURE HYDROGEN.");
    }

    double T = phase.getPhase().getTemperature();
    double rho = phase.getPhase().getDensity_Leachman();

    double[] a = {2.09630e-1, -4.55274e-1, 1.43602e-1, -3.35325e-2, 2.76981e-3};
    double[] b = {-0.1870, 2.4871, 3.7151, -11.0972, 9.0965, -3.8292, 0.5166};
    double[] c = {0, 6.43449673, 4.56334068e-2, 2.32797868e-1, 9.58326120e-1, 1.27941189e-1, 3.63576595e-1};
    double Tc = 33.145;       //[K] (Source: NIST)
    double rho_sc = 90.909090909;      //[kg/m^3]
    double M = 2.01588;   //[g/mol] molar mass
    double sigma = 0.297;   //[nm] scaling parameter
    double epsilon_kb = 30.41;   //[K] scaling parameter

    double Tr = T / Tc;
    double rho_r = rho / rho_sc;
    double T_star = T * 1/epsilon_kb;

    double sstar = 0.0;  //creating sstar object temporary
    for (int i = 0; i < a.length; i++) {
      sstar += a[i] * Math.pow(Math.log(T_star), i);
    }
    double Sstar = Math.exp(sstar);

    double Bstar_eta = 0.0;  //creating Bstar_eta object
    for (int i = 0; i < b.length; i++) {
      Bstar_eta += b[i] * Math.pow(T_star, -i);
    }

    double B_eta = Bstar_eta * Math.pow(sigma, 3);

    double eta_0 = (0.021357 * Math.pow(M*T, 0.5)) / (Math.pow(sigma, 2) *Sstar);
    double eta_1 = B_eta * eta_0;

    double eta = eta_0 + eta_1*rho + c[1]*Math.pow(rho_r,2) * Math.exp(c[2]*Tr + c[3]/Tr + (c[4]*Math.pow(rho_r,2))/(c[5]+Tr) + c[6]*Math.pow(rho_r,6));
    
    return eta * Math.pow(10, -6);    // [Pa*s]; 
  }
}
