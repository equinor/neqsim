package neqsim.thermo.util.GERG;
import java.lang.*;
import org.netlib.util.*;



public class Alpha0gerg {

// 
// c-----------------------------------------------------------------------
// 
// 
// c  Calculate the ideal gas Helmholtz energy and its derivatives with res
// c  This routine is not needed when only P (or Z) is calculated.
// c
// c  Inputs:
// c       T - Temperature (K)
// c       D - Density (mol/l)
// c     x() - Composition (mole fraction)
// c
// c  Outputs:
// c   a0(0) - Ideal gas Helmholtz energy (dimensionless [i.e., divided by 
// c   a0(1) - tau*partial(a0)/partial(tau)
// c   a0(2) - tau^2*partial^2(a0)/partial(tau)^2
// 
// 
// 
// 
// 
// 
// 

public static void alpha0gerg (double t,
double d,
double [] x, int _x_offset,
double [] a0, int _a0_offset)  {

int i= 0;
int j= 0;
double logt= 0.0d;
double logd= 0.0d;
double loghyp= 0.0d;
double th0t= 0.0d;
double logxd= 0.0d;
double sumhyp0= 0.0d;
double sumhyp1= 0.0d;
double sumhyp2= 0.0d;
double em= 0.0d;
double ep= 0.0d;
double hcn= 0.0d;
double hsn= 0.0d;

if ((d > 1.000000000000000077705399876661079238307e-15))  {
    logd = Math.log(d);
}
else  {
  logd = Math.log(1.000000000000000077705399876661079238307e-15);
}              //  Close else.
logt = Math.log(t);
{
for (i = 1; i <= 21; i++) {
if ((x[(i-(1))+ _x_offset] > 1.000000000000000077705399876661079238307e-15))  {
    logxd = (logd+Math.log(x[(i-(1))+ _x_offset]));
sumhyp0 = 0.e0;
sumhyp1 = 0.e0;
sumhyp2 = 0.e0;
{
for (j = 4; j <= 7; j++) {
if ((                       neqsim.thermo.util.GERG.GERG2008_gerg2008.th0i[(i-(1))+(j-(1)) * (21)] > 1.000000000000000077705399876661079238307e-15))  {
    th0t = (                    neqsim.thermo.util.GERG.GERG2008_gerg2008.th0i[(i-(1))+(j-(1)) * (21)]/t);
ep = Math.exp(th0t);
em = (1.e0/ep);
hsn = (((ep-em))/2.e0);
hcn = (((ep+em))/2.e0);
if (((j == 4) || (j == 6)))  {
    loghyp = Math.log(Math.abs(hsn));
sumhyp0 = (sumhyp0+(                neqsim.thermo.util.GERG.GERG2008_gerg2008.n0i[(i-(1))+(j-(1)) * (21)]*loghyp));
sumhyp1 = (sumhyp1+(((              neqsim.thermo.util.GERG.GERG2008_gerg2008.n0i[(i-(1))+(j-(1)) * (21)]*th0t)*hcn)/hsn));
sumhyp2 = (sumhyp2+(                neqsim.thermo.util.GERG.GERG2008_gerg2008.n0i[(i-(1))+(j-(1)) * (21)]*( Math.pow(((th0t/hsn)), 2))));
}
else  {
  loghyp = Math.log(Math.abs(hcn));
sumhyp0 = (sumhyp0-(                neqsim.thermo.util.GERG.GERG2008_gerg2008.n0i[(i-(1))+(j-(1)) * (21)]*loghyp));
sumhyp1 = (sumhyp1-(((              neqsim.thermo.util.GERG.GERG2008_gerg2008.n0i[(i-(1))+(j-(1)) * (21)]*th0t)*hsn)/hcn));
sumhyp2 = (sumhyp2+(                neqsim.thermo.util.GERG.GERG2008_gerg2008.n0i[(i-(1))+(j-(1)) * (21)]*( Math.pow(((th0t/hcn)), 2))));
}              //  Close else.
}
//Dummy.label("neqsim/thermo/GERG/Alpha0gerg",1000001);
}              //  Close for() loop. 
}
a0[(0-(0))+ _a0_offset] = (a0[(0-(0))+ _a0_offset]+(x[(i-(1))+ _x_offset]*(((((logxd+neqsim.thermo.util.GERG.GERG2008_gerg2008.n0i[(i-(1))+(1-(1)) * (21)])+(neqsim.thermo.util.GERG.GERG2008_gerg2008.n0i[(i-(1))+(2-(1)) * (21)]/t))-(neqsim.thermo.util.GERG.GERG2008_gerg2008.n0i[(i-(1))+(3-(1)) * (21)]*logt))+sumhyp0))));
a0[(1-(0))+ _a0_offset] = (a0[(1-(0))+ _a0_offset]+(x[(i-(1))+ _x_offset]*(((neqsim.thermo.util.GERG.GERG2008_gerg2008.n0i[(i-(1))+(3-(1)) * (21)]+(neqsim.thermo.util.GERG.GERG2008_gerg2008.n0i[(i-(1))+(2-(1)) * (21)]/t))+sumhyp1))));
a0[(2-(0))+ _a0_offset] = (a0[(2-(0))+ _a0_offset]-(x[(i-(1))+ _x_offset]*((neqsim.thermo.util.GERG.GERG2008_gerg2008.n0i[(i-(1))+(3-(1)) * (21)]+sumhyp2))));
}
//Dummy.label("neqsim/thermo/GERG/Alpha0gerg",1000000);
}              //  Close for() loop. 
}
//Dummy.label("neqsim/thermo/GERG/Alpha0gerg",999999);
return;
   }
} // End class.
