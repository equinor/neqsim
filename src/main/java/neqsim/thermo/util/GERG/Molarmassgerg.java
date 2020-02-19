
package neqsim.thermo.util.GERG;
import java.lang.*;
import org.netlib.util.*;



public class Molarmassgerg {
	private static final long serialVersionUID = 1000;
// 
// c  Calculate molar mass of the mixture with the compositions contained i
// c
// c  Inputs:
// c     x() - Composition (mole fraction)
// c         Do not send mole percents or mass fractions in the x() array, 
// c         The sum of the compositions in the x() array must be equal to 
// c         The order of the fluids in this array is given at the top of t
// c
// c  Outputs:
// c      Mm - molar mass (g/mol)
//  
// 
// 
// 
// 

public static void molarmassgerg (double [] x, int _x_offset, org.netlib.util.doubleW mm)  {

if ((   neqsim.thermo.util.GERG.GERG2008_gerg2008.kpol[(1-(1))] != 6)) {
            neqsim.thermo.util.GERG.Setupgerg.setupgerg();
}
	mm.val=Sum.sum(x,neqsim.thermo.util.GERG.GERG2008_gerg2008.mmigerg);
//Dummy.label("neqsim/thermo/GERG/Molarmassgerg",999999);
return;
   }
} // End class.
