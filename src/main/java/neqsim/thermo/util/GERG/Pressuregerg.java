package neqsim.thermo.util.GERG;
import java.lang.*;
import org.netlib.util.*;



public class Pressuregerg {
	private static final long serialVersionUID = 1000;
// 
// c-----------------------------------------------------------------------
// 
// 
// c  Calculate pressure as a function of temperature and density.  The der
// c  for use in the iterative DensityGERG subroutine (and is only returned
// c
// c  Inputs:
// c       T - Temperature (K)
// c       D - Density (mol/l)
// c     x() - Composition (mole fraction)
// c         Do not send mole percents or mass fractions in the x() array, 
// c         The sum of the compositions in the x() array must be equal to 
// c
// c  Outputs:
// c       P - Pressure (kPa)
// c       Z - Compressibility factor
// c   dPdDsave - d(P)/d(D) [kPa/(mol/l)] (at constant temperature)
// 
// 
// 
// 
// 

public static void pressuregerg (double t,
double d,
double [] x, int _x_offset,
doubleW p,
doubleW z)  {

double [] ar= new double[(3 - 0 + 1) * (3 - 0 + 1)];
if ((   neqsim.thermo.util.GERG.GERG2008_gerg2008.kpol[(1-(1))] != 6)) {
            neqsim.thermo.util.GERG.Setupgerg.setupgerg();
}
    // 
        neqsim.thermo.util.GERG.Alphargerg.alphargerg(0,t,d,x,_x_offset,ar,0);
z.val = (1.e0+ar[(0-(0))+(1-(0)) * (3 - 0 + 1)]);
p.val = (((d*neqsim.thermo.util.GERG.GERG2008_gerg2008.rgerg.val)*t)*z.val);
        neqsim.thermo.util.GERG.GERG2008_gerg2008.dpddsave.val = ((neqsim.thermo.util.GERG.GERG2008_gerg2008.rgerg.val*t)*(((1.e0+(2.e0*ar[(0-(0))+(1-(0)) * (3 - 0 + 1)]))+ar[(0-(0))+(2-(0)) * (3 - 0 + 1)])));
//Dummy.label("neqsim/thermo/GERG/Pressuregerg",999999);
return;
   }
} // End class.
