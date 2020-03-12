
package neqsim.thermo.util.GERG;
import java.lang.*;
import org.netlib.util.*;



public class Reducingparametersgerg {
	private static final long serialVersionUID = 1000;
// 
// c-----------------------------------------------------------------------
// 
// c  The following routines are low-level routines that should not be call
// 
// c-----------------------------------------------------------------------
// 
// 
// c  Calculate reducing variables.  Only need to call this if the composit
// c
// c  Inputs:
// c     x() - Composition (mole fraction)
// c
// c  Outputs:
// c      Tr - Reducing temperature (K)
// c      Dr - Reducing density (mol/l)
// 
// 
// 
// c  Check to see if a component fraction has changed.  If x is the same a

public static void reducingparametersgerg (double [] x, int _x_offset,
doubleW tr,
doubleW dr)  {

int i= 0;
int j= 0;
int icheck= 0;
double vr= 0.0d;
double xij= 0.0d;
double f= 0.0d;
icheck = 0;
{
for (i = 1; i <= 21; i++) {
if ((Math.abs((x[(i-(1))+ _x_offset]-neqsim.thermo.util.GERG.GERG2008_gerg2008.xold[(i-(1))])) > 1.e-7)) {
    icheck = 1;
}
                neqsim.thermo.util.GERG.GERG2008_gerg2008.xold[(i-(1))] = x[(i-(1))+ _x_offset];
//Dummy.label("neqsim/thermo/GERG/Reducingparametersgerg",1000000);
}              //  Close for() loop. 
}
if ((icheck == 0))  {
    dr.val = neqsim.thermo.util.GERG.GERG2008_gerg2008.drold.val;
tr.val =    neqsim.thermo.util.GERG.GERG2008_gerg2008.trold.val;
return;
//Dummy.go_to("neqsim/thermo/GERG/Reducingparametersgerg",999999);
}
        neqsim.thermo.util.GERG.GERG2008_gerg2008.told.val = 0.e0;
        neqsim.thermo.util.GERG.GERG2008_gerg2008.trold2.val = 0.e0;
// 
// c  Calculate reducing variables for T and D
dr.val = 0.e0;
vr = 0.e0;
tr.val = 0.e0;
{
for (i = 1; i <= 21; i++) {
if ((x[(i-(1))+ _x_offset] > 1.000000000000000077705399876661079238307e-15))  {
    f = 1.e0;
{
for (j = i; j <= 21; j++) {
if ((x[(j-(1))+ _x_offset] > 1.000000000000000077705399876661079238307e-15))  {
    xij = ((f*((x[(i-(1))+ _x_offset]*x[(j-(1))+ _x_offset])))*((x[(i-(1))+ _x_offset]+x[(j-(1))+ _x_offset])));
vr = (vr+((xij*                 neqsim.thermo.util.GERG.GERG2008_gerg2008.gvij[(i-(1))+(j-(1)) * (21)])/(((neqsim.thermo.util.GERG.GERG2008_gerg2008.bvij[(i-(1))+(j-(1)) * (21)]*x[(i-(1))+ _x_offset])+x[(j-(1))+ _x_offset]))));
tr.val = (tr.val+((xij*         neqsim.thermo.util.GERG.GERG2008_gerg2008.gtij[(i-(1))+(j-(1)) * (21)])/(((neqsim.thermo.util.GERG.GERG2008_gerg2008.btij[(i-(1))+(j-(1)) * (21)]*x[(i-(1))+ _x_offset])+x[(j-(1))+ _x_offset]))));
f = 2.e0;
}
//Dummy.label("neqsim/thermo/GERG/Reducingparametersgerg",1000002);
}              //  Close for() loop. 
}
}
//Dummy.label("neqsim/thermo/GERG/Reducingparametersgerg",1000001);
}              //  Close for() loop. 
}
if ((vr > 1.000000000000000077705399876661079238307e-15)) {
    dr.val = (1.e0/vr);
}
        neqsim.thermo.util.GERG.GERG2008_gerg2008.drold.val = dr.val;
        neqsim.thermo.util.GERG.GERG2008_gerg2008.trold.val = tr.val;
//Dummy.label("neqsim/thermo/GERG/Reducingparametersgerg",999999);
return;
   }
} // End class.
