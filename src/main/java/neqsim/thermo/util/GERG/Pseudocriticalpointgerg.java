package neqsim.thermo.util.GERG;
import java.lang.*;
import org.netlib.util.*;



public class Pseudocriticalpointgerg {
	private static final long serialVersionUID = 1000;
// 
// c-----------------------------------------------------------------------
// 
// 
// c  Calculate a pseudo critical point as the mole fraction average of the
// 
// 
// 
// 
// 

public static void pseudocriticalpointgerg (double [] x, int _x_offset,
doubleW tcx,
doubleW dcx)  {

int i= 0;
double vcx= 0.0d;
tcx.val = 0.e0;
vcx = 0.e0;
dcx.val = 0.e0;
{
for (i = 1; i <= 21; i++) {
tcx.val = (tcx.val+(x[(i-(1))+ _x_offset]*neqsim.thermo.util.GERG.GERG2008_gerg2008.tc[(i-(1))]));
vcx = (vcx+(x[(i-(1))+ _x_offset]/neqsim.thermo.util.GERG.GERG2008_gerg2008.dc[(i-(1))]));
//Dummy.label("neqsim/thermo/GERG/Pseudocriticalpointgerg",1000000);
}              //  Close for() loop. 
}
if ((vcx > 1.000000000000000077705399876661079238307e-15)) {
    dcx.val = (1.e0/vcx);
}
    //Dummy.label("neqsim/thermo/GERG/Pseudocriticalpointgerg",999999);
return;
   }
} // End class.
