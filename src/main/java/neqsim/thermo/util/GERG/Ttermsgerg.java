
package neqsim.thermo.util.GERG;
import java.lang.*;
import org.netlib.util.*;



public class Ttermsgerg {

// 
// c-----------------------------------------------------------------------
// 
// 
// c  Calculate temperature dependent parts of the GERG-2008 equation of st
// 
// 
// 
// 
// 

public static void ttermsgerg (double lntau,
double [] x, int _x_offset)  {

int i= 0;
int j= 0;
int k= 0;
int mn= 0;
double [] taup0= new double[(12)];
i = 5;
{
for (k = 1; k <= (neqsim.thermo.util.GERG.GERG2008_gerg2008.kpol[(i-(1))]+neqsim.thermo.util.GERG.GERG2008_gerg2008.kexp[(i-(1))]); k++) {
taup0[(k-(1))] = Math.exp((neqsim.thermo.util.GERG.GERG2008_gerg2008.toik[(i-(1))+(k-(1)) * (21)]*lntau));
//Dummy.label("neqsim/thermo/GERG/Ttermsgerg",1000000);
}              //  Close for() loop. 
}
{
for (i = 1; i <= 21; i++) {
if ((x[(i-(1))+ _x_offset] > 1.000000000000000077705399876661079238307e-15))  {
    if (((((i > 4) && (i != 15.f)) && (i != 18)) && (i != 20)))  {
    {
for (k = 1; k <= (          neqsim.thermo.util.GERG.GERG2008_gerg2008.kpol[(i-(1))]+neqsim.thermo.util.GERG.GERG2008_gerg2008.kexp[(i-(1))]); k++) {
                                neqsim.thermo.util.GERG.GERG2008_gerg2008.taup[(i-(1))+(k-(1)) * (21)] = (neqsim.thermo.util.GERG.GERG2008_gerg2008.noik[(i-(1))+(k-(1)) * (21)]*taup0[(k-(1))]);
//Dummy.label("neqsim/thermo/GERG/Ttermsgerg",1000002);
}              //  Close for() loop. 
}
}
else  {
  {
for (k = 1; k <= (          neqsim.thermo.util.GERG.GERG2008_gerg2008.kpol[(i-(1))]+neqsim.thermo.util.GERG.GERG2008_gerg2008.kexp[(i-(1))]); k++) {
                                neqsim.thermo.util.GERG.GERG2008_gerg2008.taup[(i-(1))+(k-(1)) * (21)] = (neqsim.thermo.util.GERG.GERG2008_gerg2008.noik[(i-(1))+(k-(1)) * (21)]*Math.exp((neqsim.thermo.util.GERG.GERG2008_gerg2008.toik[(i-(1))+(k-(1)) * (21)]*lntau)));
//Dummy.label("neqsim/thermo/GERG/Ttermsgerg",1000003);
}              //  Close for() loop. 
}
}              //  Close else.
}
//Dummy.label("neqsim/thermo/GERG/Ttermsgerg",1000001);
}              //  Close for() loop. 
}
// 
{
for (i = 1; i <= (21-1); i++) {
if ((x[(i-(1))+ _x_offset] > 1.000000000000000077705399876661079238307e-15))  {
    {
for (j = (i+1); j <= 21; j++) {
if ((x[(j-(1))+ _x_offset] > 1.000000000000000077705399876661079238307e-15))  {
    mn =                        neqsim.thermo.util.GERG.GERG2008_gerg2008.mnumb[(i-(1))+(j-(1)) * (21)];
if ((mn >= 0))  {
    {
for (k = 1; k <=                        neqsim.thermo.util.GERG.GERG2008_gerg2008.kpolij[(mn-(1))]; k++) {
                                            neqsim.thermo.util.GERG.GERG2008_gerg2008.taupijk[(mn-(1))+(k-(1)) * (21)] = (neqsim.thermo.util.GERG.GERG2008_gerg2008.nijk[(mn-(1))+(k-(1)) * (10)]*Math.exp((neqsim.thermo.util.GERG.GERG2008_gerg2008.tijk[(mn-(1))+(k-(1)) * (10)]*lntau)));
//Dummy.label("neqsim/thermo/GERG/Ttermsgerg",1000006);
}              //  Close for() loop. 
}
}
}
//Dummy.label("neqsim/thermo/GERG/Ttermsgerg",1000005);
}              //  Close for() loop. 
}
}
//Dummy.label("neqsim/thermo/GERG/Ttermsgerg",1000004);
}              //  Close for() loop. 
}
//Dummy.label("neqsim/thermo/GERG/Ttermsgerg",999999);
return;
   }
} // End class.
