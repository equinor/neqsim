package neqsim.thermo.util.GERG;
import java.lang.*;
import org.netlib.util.*;



public class Alphargerg {

// 
// c-----------------------------------------------------------------------
// 
// 
// c  Calculate dimensionless residual Helmholtz energy and its derivatives
// c
// c  Inputs:
// c   iprop - Set to 1 to return all derivatives or 0 to return only press
// c       T - Temperature (K)
// c       D - Density (mol/l)
// c     x() - Composition (mole fraction)
// c
// c  Outputs:
// c   ar(0,0) - Residual Helmholtz energy (dimensionless, =a/RT)
// c   ar(0,1) -     del*partial  (ar)/partial(del)
// c   ar(0,2) -   del^2*partial^2(ar)/partial(del)^2
// c   ar(0,3) -   del^3*partial^3(ar)/partial(del)^3
// c   ar(1,0) -     tau*partial  (ar)/partial(tau)
// c   ar(1,1) - tau*del*partial^2(ar)/partial(tau)/partial(del)
// c   ar(2,0) -   tau^2*partial^2(ar)/partial(tau)^2
// 
// 
// 
// 
// c  Set up del, tau, log(tau), and the first 7 calculations for del^i

public static void alphargerg (int iprop,
double t,
double d,
double [] x, int _x_offset,
double [] ar, int _ar_offset)  {

int i= 0;
int j= 0;
int k= 0;
int mn= 0;
doubleW tr= new doubleW(0.0d);
doubleW dr= new doubleW(0.0d);
double del= 0.0d;
double tau= 0.0d;
double lntau= 0.0d;
double ex= 0.0d;
double ex2= 0.0d;
double ex3= 0.0d;
double cij0= 0.0d;
double eij0= 0.0d;
double [] delp= new double[(7)];
double [] expd= new double[(7)];
double ndt= 0.0d;
double ndtd= 0.0d;
double ndtt= 0.0d;
double xijf= 0.0d;

        neqsim.thermo.util.GERG.Reducingparametersgerg.reducingparametersgerg(x,_x_offset,tr,dr);
del = (d/dr.val);
tau = (tr.val/t);
lntau = Math.log(tau);
delp[(1-(1))] = del;
expd[(1-(1))] = Math.exp((-(delp[(1-(1))])));
{
for (i = 2; i <= 7; i++) {
delp[(i-(1))] = (delp[((i-1)-(1))]*del);
expd[(i-(1))] = Math.exp((-(delp[(i-(1))])));
//Dummy.label("neqsim/thermo/GERG/Alphargerg",1000000);
}              //  Close for() loop. 
}
// 
// c  If temperature has changed, calculate temperature dependent parts
if (((Math.abs((t-neqsim.thermo.util.GERG.GERG2008_gerg2008.told.val)) > 1.e-7) || (Math.abs((tr.val-neqsim.thermo.util.GERG.GERG2008_gerg2008.trold2.val)) > 1.e-7))) {
            neqsim.thermo.util.GERG.Ttermsgerg.ttermsgerg(lntau,x,_x_offset);
}
        neqsim.thermo.util.GERG.GERG2008_gerg2008.told.val = t;
        neqsim.thermo.util.GERG.GERG2008_gerg2008.trold2.val = tr.val;
// 
// c  Calculate pure fluid contributions
{
for (i = 1; i <= 21; i++) {
if ((x[(i-(1))+ _x_offset] > 1.000000000000000077705399876661079238307e-15))  {
    {
for (k = 1; k <=        neqsim.thermo.util.GERG.GERG2008_gerg2008.kpol[(i-(1))]; k++) {
ndt = ((x[(i-(1))+ _x_offset]*delp[((int)(neqsim.thermo.util.GERG.GERG2008_gerg2008.doik[(i-(1))+(k-(1)) * (21)])-(1))])*neqsim.thermo.util.GERG.GERG2008_gerg2008.taup[(i-(1))+(k-(1)) * (21)]);
ndtd = (ndt*                neqsim.thermo.util.GERG.GERG2008_gerg2008.doik[(i-(1))+(k-(1)) * (21)]);
ar[(0-(0))+(1-(0)) * (3 - 0 + 1)+ _ar_offset] = (ar[(0-(0))+(1-(0)) * (3 - 0 + 1)+ _ar_offset]+ndtd);
ar[(0-(0))+(2-(0)) * (3 - 0 + 1)+ _ar_offset] = (ar[(0-(0))+(2-(0)) * (3 - 0 + 1)+ _ar_offset]+(ndtd*((neqsim.thermo.util.GERG.GERG2008_gerg2008.doik[(i-(1))+(k-(1)) * (21)]-1.e0))));
if ((iprop > 0))  {
    ndtt = (ndt*                neqsim.thermo.util.GERG.GERG2008_gerg2008.toik[(i-(1))+(k-(1)) * (21)]);
ar[(0-(0))+(0-(0)) * (3 - 0 + 1)+ _ar_offset] = (ar[(0-(0))+(0-(0)) * (3 - 0 + 1)+ _ar_offset]+ndt);
ar[(1-(0))+(0-(0)) * (3 - 0 + 1)+ _ar_offset] = (ar[(1-(0))+(0-(0)) * (3 - 0 + 1)+ _ar_offset]+ndtt);
ar[(2-(0))+(0-(0)) * (3 - 0 + 1)+ _ar_offset] = (ar[(2-(0))+(0-(0)) * (3 - 0 + 1)+ _ar_offset]+(ndtt*((neqsim.thermo.util.GERG.GERG2008_gerg2008.toik[(i-(1))+(k-(1)) * (21)]-1.e0))));
ar[(1-(0))+(1-(0)) * (3 - 0 + 1)+ _ar_offset] = (ar[(1-(0))+(1-(0)) * (3 - 0 + 1)+ _ar_offset]+(ndtt*neqsim.thermo.util.GERG.GERG2008_gerg2008.doik[(i-(1))+(k-(1)) * (21)]));
ar[(1-(0))+(2-(0)) * (3 - 0 + 1)+ _ar_offset] = (ar[(1-(0))+(2-(0)) * (3 - 0 + 1)+ _ar_offset]+((ndtt*neqsim.thermo.util.GERG.GERG2008_gerg2008.doik[(i-(1))+(k-(1)) * (21)])*((neqsim.thermo.util.GERG.GERG2008_gerg2008.doik[(i-(1))+(k-(1)) * (21)]-1.e0))));
ar[(0-(0))+(3-(0)) * (3 - 0 + 1)+ _ar_offset] = (ar[(0-(0))+(3-(0)) * (3 - 0 + 1)+ _ar_offset]+((ndtd*((neqsim.thermo.util.GERG.GERG2008_gerg2008.doik[(i-(1))+(k-(1)) * (21)]-1.e0)))*((neqsim.thermo.util.GERG.GERG2008_gerg2008.doik[(i-(1))+(k-(1)) * (21)]-2.e0))));
}
//Dummy.label("neqsim/thermo/GERG/Alphargerg",1000002);
}              //  Close for() loop. 
}
{
for (k = (1+            neqsim.thermo.util.GERG.GERG2008_gerg2008.kpol[(i-(1))]); k <= (neqsim.thermo.util.GERG.GERG2008_gerg2008.kpol[(i-(1))]+neqsim.thermo.util.GERG.GERG2008_gerg2008.kexp[(i-(1))]); k++) {
ndt = (((x[(i-(1))+ _x_offset]*delp[((int)(neqsim.thermo.util.GERG.GERG2008_gerg2008.doik[(i-(1))+(k-(1)) * (21)])-(1))])*neqsim.thermo.util.GERG.GERG2008_gerg2008.taup[(i-(1))+(k-(1)) * (21)])*expd[((int)(neqsim.thermo.util.GERG.GERG2008_gerg2008.coik[(i-(1))+(k-(1)) * (21)])-(1))]);
ex = (                      neqsim.thermo.util.GERG.GERG2008_gerg2008.coik[(i-(1))+(k-(1)) * (21)]*delp[((int)(neqsim.thermo.util.GERG.GERG2008_gerg2008.coik[(i-(1))+(k-(1)) * (21)])-(1))]);
ex2 = (                     neqsim.thermo.util.GERG.GERG2008_gerg2008.doik[(i-(1))+(k-(1)) * (21)]-ex);
ex3 = (ex2*((ex2-1.e0)));
ar[(0-(0))+(1-(0)) * (3 - 0 + 1)+ _ar_offset] = (ar[(0-(0))+(1-(0)) * (3 - 0 + 1)+ _ar_offset]+(ndt*ex2));
ar[(0-(0))+(2-(0)) * (3 - 0 + 1)+ _ar_offset] = (ar[(0-(0))+(2-(0)) * (3 - 0 + 1)+ _ar_offset]+(ndt*((ex3-(neqsim.thermo.util.GERG.GERG2008_gerg2008.coik[(i-(1))+(k-(1)) * (21)]*ex)))));
if ((iprop > 0))  {
    ndtt = (ndt*                neqsim.thermo.util.GERG.GERG2008_gerg2008.toik[(i-(1))+(k-(1)) * (21)]);
ar[(0-(0))+(0-(0)) * (3 - 0 + 1)+ _ar_offset] = (ar[(0-(0))+(0-(0)) * (3 - 0 + 1)+ _ar_offset]+ndt);
ar[(1-(0))+(0-(0)) * (3 - 0 + 1)+ _ar_offset] = (ar[(1-(0))+(0-(0)) * (3 - 0 + 1)+ _ar_offset]+ndtt);
ar[(2-(0))+(0-(0)) * (3 - 0 + 1)+ _ar_offset] = (ar[(2-(0))+(0-(0)) * (3 - 0 + 1)+ _ar_offset]+(ndtt*((neqsim.thermo.util.GERG.GERG2008_gerg2008.toik[(i-(1))+(k-(1)) * (21)]-1.e0))));
ar[(1-(0))+(1-(0)) * (3 - 0 + 1)+ _ar_offset] = (ar[(1-(0))+(1-(0)) * (3 - 0 + 1)+ _ar_offset]+(ndtt*ex2));
ar[(1-(0))+(2-(0)) * (3 - 0 + 1)+ _ar_offset] = (ar[(1-(0))+(2-(0)) * (3 - 0 + 1)+ _ar_offset]+(ndtt*((ex3-(neqsim.thermo.util.GERG.GERG2008_gerg2008.coik[(i-(1))+(k-(1)) * (21)]*ex)))));
ar[(0-(0))+(3-(0)) * (3 - 0 + 1)+ _ar_offset] = (ar[(0-(0))+(3-(0)) * (3 - 0 + 1)+ _ar_offset]+(ndt*(((ex3*((ex2-2.e0)))-((ex*((((3.e0*ex2)-3.e0)+neqsim.thermo.util.GERG.GERG2008_gerg2008.coik[(i-(1))+(k-(1)) * (21)])))*neqsim.thermo.util.GERG.GERG2008_gerg2008.coik[(i-(1))+(k-(1)) * (21)])))));
}
//Dummy.label("neqsim/thermo/GERG/Alphargerg",1000003);
}              //  Close for() loop. 
}
}
//Dummy.label("neqsim/thermo/GERG/Alphargerg",1000001);
}              //  Close for() loop. 
}
// 
// c  Calculate mixture contributions
{
for (i = 1; i <= (21-1); i++) {
if ((x[(i-(1))+ _x_offset] > 1.000000000000000077705399876661079238307e-15))  {
    {
for (j = (i+1); j <= 21; j++) {
if ((x[(j-(1))+ _x_offset] > 1.000000000000000077705399876661079238307e-15))  {
    mn =                        neqsim.thermo.util.GERG.GERG2008_gerg2008.mnumb[(i-(1))+(j-(1)) * (21)];
if ((mn >= 0))  {
    xijf = ((x[(i-(1))+ _x_offset]*x[(j-(1))+ _x_offset])*neqsim.thermo.util.GERG.GERG2008_gerg2008.fij[(i-(1))+(j-(1)) * (21)]);
{
for (k = 1; k <=                        neqsim.thermo.util.GERG.GERG2008_gerg2008.kpolij[(mn-(1))]; k++) {
ndt = ((xijf*delp[((int)(                   neqsim.thermo.util.GERG.GERG2008_gerg2008.dijk[(mn-(1))+(k-(1)) * (10)])-(1))])*neqsim.thermo.util.GERG.GERG2008_gerg2008.taupijk[(mn-(1))+(k-(1)) * (21)]);
ndtd = (ndt*                                neqsim.thermo.util.GERG.GERG2008_gerg2008.dijk[(mn-(1))+(k-(1)) * (10)]);
ar[(0-(0))+(1-(0)) * (3 - 0 + 1)+ _ar_offset] = (ar[(0-(0))+(1-(0)) * (3 - 0 + 1)+ _ar_offset]+ndtd);
ar[(0-(0))+(2-(0)) * (3 - 0 + 1)+ _ar_offset] = (ar[(0-(0))+(2-(0)) * (3 - 0 + 1)+ _ar_offset]+(ndtd*((neqsim.thermo.util.GERG.GERG2008_gerg2008.dijk[(mn-(1))+(k-(1)) * (10)]-1.e0))));
if ((iprop > 0))  {
    ndtt = (ndt*                                neqsim.thermo.util.GERG.GERG2008_gerg2008.tijk[(mn-(1))+(k-(1)) * (10)]);
ar[(0-(0))+(0-(0)) * (3 - 0 + 1)+ _ar_offset] = (ar[(0-(0))+(0-(0)) * (3 - 0 + 1)+ _ar_offset]+ndt);
ar[(1-(0))+(0-(0)) * (3 - 0 + 1)+ _ar_offset] = (ar[(1-(0))+(0-(0)) * (3 - 0 + 1)+ _ar_offset]+ndtt);
ar[(2-(0))+(0-(0)) * (3 - 0 + 1)+ _ar_offset] = (ar[(2-(0))+(0-(0)) * (3 - 0 + 1)+ _ar_offset]+(ndtt*((neqsim.thermo.util.GERG.GERG2008_gerg2008.tijk[(mn-(1))+(k-(1)) * (10)]-1.e0))));
ar[(1-(0))+(1-(0)) * (3 - 0 + 1)+ _ar_offset] = (ar[(1-(0))+(1-(0)) * (3 - 0 + 1)+ _ar_offset]+(ndtt*neqsim.thermo.util.GERG.GERG2008_gerg2008.dijk[(mn-(1))+(k-(1)) * (10)]));
ar[(1-(0))+(2-(0)) * (3 - 0 + 1)+ _ar_offset] = (ar[(1-(0))+(2-(0)) * (3 - 0 + 1)+ _ar_offset]+((ndtt*neqsim.thermo.util.GERG.GERG2008_gerg2008.dijk[(mn-(1))+(k-(1)) * (10)])*((neqsim.thermo.util.GERG.GERG2008_gerg2008.dijk[(mn-(1))+(k-(1)) * (10)]-1.e0))));
ar[(0-(0))+(3-(0)) * (3 - 0 + 1)+ _ar_offset] = (ar[(0-(0))+(3-(0)) * (3 - 0 + 1)+ _ar_offset]+((ndtd*((neqsim.thermo.util.GERG.GERG2008_gerg2008.dijk[(mn-(1))+(k-(1)) * (10)]-1.e0)))*((neqsim.thermo.util.GERG.GERG2008_gerg2008.dijk[(mn-(1))+(k-(1)) * (10)]-2.e0))));
}
//Dummy.label("neqsim/thermo/GERG/Alphargerg",1000006);
}              //  Close for() loop. 
}
{
for (k = (1+                            neqsim.thermo.util.GERG.GERG2008_gerg2008.kpolij[(mn-(1))]); k <= (neqsim.thermo.util.GERG.GERG2008_gerg2008.kpolij[(mn-(1))]+neqsim.thermo.util.GERG.GERG2008_gerg2008.kexpij[(mn-(1))]); k++) {
cij0 = (                                    neqsim.thermo.util.GERG.GERG2008_gerg2008.cijk[(mn-(1))+(k-(1)) * (10)]*delp[(2-(1))]);
eij0 = (                                    neqsim.thermo.util.GERG.GERG2008_gerg2008.eijk[(mn-(1))+(k-(1)) * (10)]*del);
ndt = (((xijf*                              neqsim.thermo.util.GERG.GERG2008_gerg2008.nijk[(mn-(1))+(k-(1)) * (10)])*delp[((int)(neqsim.thermo.util.GERG.GERG2008_gerg2008.dijk[(mn-(1))+(k-(1)) * (10)])-(1))])*Math.exp((((cij0+eij0)+neqsim.thermo.util.GERG.GERG2008_gerg2008.gijk[(mn-(1))+(k-(1)) * (10)])+(neqsim.thermo.util.GERG.GERG2008_gerg2008.tijk[(mn-(1))+(k-(1)) * (10)]*lntau))));
ex = ((                                     neqsim.thermo.util.GERG.GERG2008_gerg2008.dijk[(mn-(1))+(k-(1)) * (10)]+(2.e0*cij0))+eij0);
ex2 = ((((ex*ex)-                           neqsim.thermo.util.GERG.GERG2008_gerg2008.dijk[(mn-(1))+(k-(1)) * (10)])+(2.e0*cij0)));
ar[(0-(0))+(1-(0)) * (3 - 0 + 1)+ _ar_offset] = (ar[(0-(0))+(1-(0)) * (3 - 0 + 1)+ _ar_offset]+(ndt*ex));
ar[(0-(0))+(2-(0)) * (3 - 0 + 1)+ _ar_offset] = (ar[(0-(0))+(2-(0)) * (3 - 0 + 1)+ _ar_offset]+(ndt*ex2));
if ((iprop > 0))  {
    ndtt = (ndt*                                neqsim.thermo.util.GERG.GERG2008_gerg2008.tijk[(mn-(1))+(k-(1)) * (10)]);
ar[(0-(0))+(0-(0)) * (3 - 0 + 1)+ _ar_offset] = (ar[(0-(0))+(0-(0)) * (3 - 0 + 1)+ _ar_offset]+ndt);
ar[(1-(0))+(0-(0)) * (3 - 0 + 1)+ _ar_offset] = (ar[(1-(0))+(0-(0)) * (3 - 0 + 1)+ _ar_offset]+ndtt);
ar[(2-(0))+(0-(0)) * (3 - 0 + 1)+ _ar_offset] = (ar[(2-(0))+(0-(0)) * (3 - 0 + 1)+ _ar_offset]+(ndtt*((neqsim.thermo.util.GERG.GERG2008_gerg2008.tijk[(mn-(1))+(k-(1)) * (10)]-1.e0))));
ar[(1-(0))+(1-(0)) * (3 - 0 + 1)+ _ar_offset] = (ar[(1-(0))+(1-(0)) * (3 - 0 + 1)+ _ar_offset]+(ndtt*ex));
ar[(1-(0))+(2-(0)) * (3 - 0 + 1)+ _ar_offset] = (ar[(1-(0))+(2-(0)) * (3 - 0 + 1)+ _ar_offset]+(ndtt*ex2));
ar[(0-(0))+(3-(0)) * (3 - 0 + 1)+ _ar_offset] = (ar[(0-(0))+(3-(0)) * (3 - 0 + 1)+ _ar_offset]+(ndt*(((ex*((ex2-(2e0*((neqsim.thermo.util.GERG.GERG2008_gerg2008.dijk[(mn-(1))+(k-(1)) * (10)]-(2.e0*cij0)))))))+(2.e0*neqsim.thermo.util.GERG.GERG2008_gerg2008.dijk[(mn-(1))+(k-(1)) * (10)])))));
}
//Dummy.label("neqsim/thermo/GERG/Alphargerg",1000007);
}              //  Close for() loop. 
}
}
}
//Dummy.label("neqsim/thermo/GERG/Alphargerg",1000005);
}              //  Close for() loop. 
}
}
//Dummy.label("neqsim/thermo/GERG/Alphargerg",1000004);
}              //  Close for() loop. 
}
//Dummy.label("neqsim/thermo/GERG/Alphargerg",999999);
return;
   }
} // End class.
