/*
 * NumericalDerivative.java
 *
 * Created on 28. juli 2000, 15:39
 */

package neqsim.MathLib.nonLinearSolver;

import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>
 * NumericalDerivative class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class NumericalDerivative implements java.io.Serializable {
  private static final long serialVersionUID = 1000;

  final static double CON = 1.4;
  final static double CON2 = CON * CON;
  final static double BIG = 1 * Math.pow(10, 30);
  final static int NTAB = 100;
  final static double SAFE = 2;

  /**
   * <p>
   * Constructor for NumericalDerivative.
   * </p>
   */
  public NumericalDerivative() {}

  /**
   * <p>
   * fugcoefDiffPres.
   * </p>
   *
   * @param component a {@link neqsim.thermo.component.ComponentInterface} object
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public static double fugcoefDiffPres(ComponentInterface component, PhaseInterface phase,
      int numberOfComponents, double temperature, double pressure) {
    double ans = 00001;
    // double errt, fac, hh, err = 0.0000000001;
    // double h = pressure / 50;

    // if(h==0.0){System.out.println("h must be larger than 0!");}
    // double[][] a = new double[NTAB][NTAB];

    // hh = h;

    // a[0][0] = (Math.log(component.fugcoef(phase, numberOfComponents, temperature,
    // pressure+hh))-Math.log(component.fugcoef(phase, numberOfComponents,
    // temperature, pressure-hh)))/(2*hh);
    // err = BIG;
    // // System.out.println("hei1 : " + ans);

    // for (int i=1;i<=NTAB-1;i++){
    // // System.out.println("hei " + ans);
    // hh/=CON;
    // a[0][i] = (Math.log(component.fugcoef(phase, numberOfComponents, temperature,
    // pressure+hh))-Math.log(component.fugcoef(phase, numberOfComponents,
    // temperature, pressure-hh)))/(2*hh);
    // fac = CON2;
    // for(int j=1;j<=i;j++){
    // a[j][i] =(a[j-1][i]*fac-a[j-1][i-1])/(fac-1.0);
    // fac = CON2*fac;
    // errt= Math.max(Math.abs(a[j][i]-a[j-1][i]),Math.abs(a[j][i]-a[j-1][i-1]));
    // // System.out.println("errt : " +errt);

    // if(errt<=err){
    // err=errt;
    // ans=a[j][i];
    // }
    // // System.out.println("ans " + ans);
    // }

    // if(Math.abs(a[i][i]-a[i-1][i-1])>=SAFE*err) break;
    // }
    // // System.out.println("ans " + ans);
    return ans;
  }

  /**
   * <p>
   * fugcoefDiffTemp.
   * </p>
   *
   * @param component a {@link neqsim.thermo.component.ComponentInterface} object
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @param phasetype a int
   * @return a double
   */
  public static double fugcoefDiffTemp(ComponentInterface component, PhaseInterface phase,
      int numberOfComponents, double temperature, double pressure, int phasetype) {
    double ans = 0.000001;
    // double errt, fac, hh, err = 0.00000000000001;
    // double h = temperature / 50;

    // if(h==0.0){System.out.println("h must be larger than 0!");}
    // double[][] a = new double[NTAB][NTAB];

    // hh = h;

    // a[0][0] = (Math.log(component.fugcoef(phase, numberOfComponents,
    // temperature+hh, pressure))-Math.log(component.fugcoef(phase,
    // numberOfComponents, temperature-hh, pressure)))/(2*hh);
    // err = BIG;
    // // System.out.println("hei1 : " + ans);

    // for (int i=1;i<=NTAB-1;i++){
    // // System.out.println("hei " + ans);
    // hh/=CON;
    // a[0][i] = (Math.log(component.fugcoef(phase, numberOfComponents,
    // temperature+hh, pressure))-Math.log(component.fugcoef(phase,
    // numberOfComponents, temperature-hh, pressure)))/(2*hh);
    // fac = CON2;
    // for(int j=1;j<=i;j++){
    // a[j][i] =(a[j-1][i]*fac-a[j-1][i-1])/(fac-1.0);
    // fac = CON2*fac;
    // errt= Math.max(Math.abs(a[j][i]-a[j-1][i]),Math.abs(a[j][i]-a[j-1][i-1]));
    // // System.out.println("errt : " +errt);

    // if(errt<=err){
    // err=errt;
    // ans=a[j][i];
    // }
    // // System.out.println("ans " + ans);
    // }

    // if(Math.abs(a[i][i]-a[i-1][i-1])>=SAFE*err) break;
    // }
    return ans;
  }
}
