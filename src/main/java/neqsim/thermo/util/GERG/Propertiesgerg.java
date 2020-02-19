package neqsim.thermo.util.GERG;
import java.lang.*;
import org.netlib.util.*;



public class Propertiesgerg {
	private static final long serialVersionUID = 1000;
// 
// c-----------------------------------------------------------------------
// 
// 
// c  Calculate thermodynamic properties as a function of temperature and d
// c  If the density is not known, call subroutine DensityGERG first with t
// c  Many of the formulas below do not appear in Part 2 of AGA 8, but rath
// c
// c  Inputs:
// c       T - Temperature (K)
// c       D - Density (mol/l)
// c     x() - Composition (mole fraction)
// c
// c  Outputs:
// c       P - Pressure (kPa)
// c       Z - Compressibility factor
// c    dPdD - First derivative of pressure with respect to density [kPa/(m
// c  d2PdD2 - Second derivative of pressure with respect to density [kPa/(
// c  d2PdTD - Second derivative of pressure with respect to temperature an
// c    dPdT - First derivative of pressure with respect to temperature (kP
// c       U - Internal energy (J/mol)
// c       H - Enthalpy (J/mol)
// c       S - Entropy [J/(mol-K)]
// c      Cv - Isochoric heat capacity [J/(mol-K)]
// c      Cp - Isobaric heat capacity [J/(mol-K)]
// c       W - Speed of sound (m/s)
// c       G - Gibbs energy (J/mol)
// c      JT - Joule-Thomson coefficient (K/kPa)
// c   Kappa - Isentropic Exponent
// c       A - Helmholtz energy (J/mol)
// 
// 
// 
// 

public static void propertiesgerg (double t,
double d,
double [] x, int _x_offset,
doubleW p,
doubleW z,
doubleW dpdd,
doubleW d2pdd2,
doubleW d2pdtd,
doubleW dpdt,
doubleW u,
doubleW h,
doubleW s,
doubleW cv,
doubleW cp,
doubleW w,
doubleW g,
doubleW jt,
doubleW kappa)  {

double a= 0.0d;
double [] a0= new double[(2 - 0 + 1)];
double [] ar= new double[(3 - 0 + 1) * (3 - 0 + 1)];
doubleW mm= new doubleW(0.0);
double r= 0.0d;
double rt= 0.0d;
if ((   neqsim.thermo.util.GERG.GERG2008_gerg2008.kpol[(1-(1))] != 6)) {
            neqsim.thermo.util.GERG.Setupgerg.setupgerg();
}
    // 
// c  Calculate molar mass
        neqsim.thermo.util.GERG.Molarmassgerg.molarmassgerg(x,_x_offset,mm);
// 
// c  Calculate the ideal gas Helmholtz energy, and its first and second de
        neqsim.thermo.util.GERG.Alpha0gerg.alpha0gerg(t,d,x,_x_offset,a0,0);
// 
// c  Calculate the real gas Helmholtz energy, and its derivatives with res
        neqsim.thermo.util.GERG.Alphargerg.alphargerg(1,t,d,x,_x_offset,ar,0);
// 
r =     neqsim.thermo.util.GERG.GERG2008_gerg2008.rgerg.val;
rt = (r*t);
z.val = (1.e0+ar[(0-(0))+(1-(0)) * (3 - 0 + 1)]);
p.val = ((d*rt)*z.val);
dpdd.val = (rt*(((1.e0+(2.e0*ar[(0-(0))+(1-(0)) * (3 - 0 + 1)]))+ar[(0-(0))+(2-(0)) * (3 - 0 + 1)])));
dpdt.val = ((d*r)*(((1.e0+ar[(0-(0))+(1-(0)) * (3 - 0 + 1)])-ar[(1-(0))+(1-(0)) * (3 - 0 + 1)])));
d2pdtd.val = (r*(((((1.e0+(2.e0*ar[(0-(0))+(1-(0)) * (3 - 0 + 1)]))+ar[(0-(0))+(2-(0)) * (3 - 0 + 1)])-(2.e0*ar[(1-(0))+(1-(0)) * (3 - 0 + 1)]))-ar[(1-(0))+(2-(0)) * (3 - 0 + 1)])));
a = (rt*((a0[(0-(0))]+ar[(0-(0))+(0-(0)) * (3 - 0 + 1)])));
g.val = (rt*((((1.e0+ar[(0-(0))+(1-(0)) * (3 - 0 + 1)])+a0[(0-(0))])+ar[(0-(0))+(0-(0)) * (3 - 0 + 1)])));
u.val = (rt*((a0[(1-(0))]+ar[(1-(0))+(0-(0)) * (3 - 0 + 1)])));
h.val = (rt*((((1.e0+ar[(0-(0))+(1-(0)) * (3 - 0 + 1)])+a0[(1-(0))])+ar[(1-(0))+(0-(0)) * (3 - 0 + 1)])));
s.val = (r*((((a0[(1-(0))]+ar[(1-(0))+(0-(0)) * (3 - 0 + 1)])-a0[(0-(0))])-ar[(0-(0))+(0-(0)) * (3 - 0 + 1)])));
cv.val = (-((r*((a0[(2-(0))]+ar[(2-(0))+(0-(0)) * (3 - 0 + 1)])))));
if ((d > 1.000000000000000077705399876661079238307e-15))  {
    cp.val = (cv.val+((t*( Math.pow(((dpdt.val/d)), 2)))/dpdd.val));
d2pdd2.val = ((rt*((((2.e0*ar[(0-(0))+(1-(0)) * (3 - 0 + 1)])+(4.e0*ar[(0-(0))+(2-(0)) * (3 - 0 + 1)]))+ar[(0-(0))+(3-(0)) * (3 - 0 + 1)])))/d);
jt.val = (((((((t/d)*dpdt.val)/dpdd.val)-1.e0))/cp.val)/d);
}
else  {
  cp.val = (cv.val+r);
d2pdd2.val = 0.e0;
jt.val = 1e+20;
}              //  Close else.
w.val = ((((1000.e0*cp.val)/cv.val)*dpdd.val)/mm.val);
if ((w.val < 0.e0)) {
    w.val = 0.e0;
}
    w.val = Math.sqrt(w.val);
kappa.val = ((( Math.pow(w.val, 2))*mm.val)/(((rt*1000.e0)*z.val)));
//Dummy.label("neqsim/thermo/GERG/Propertiesgerg",999999);
return;
   }
} // End class.
