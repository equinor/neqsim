package neqsim.thermo.util.GERG;

import java.lang.*;
import org.netlib.util.*;

public class Examplegerg {

// c  Example program for calling routines for the GERG-2008 equation of st
// 
// 
// c  input composition (must be in the same order as below)

	public static void examplegerg() {

		intW ierr = new intW(0);
		double[] x = new double[(21)];
//double mm= 0.0d;
		doubleW mm = new doubleW(0.0d);
		double t = 0.0d;
		double p = 0.0d;
		doubleW d = new doubleW(0.0d);
		doubleW pp = new doubleW(0.0d);
		doubleW z = new doubleW(0.0d);
		doubleW dpdd = new doubleW(0.0d);
		doubleW d2pdd2 = new doubleW(0.0d);
		doubleW d2pdtd = new doubleW(0.0d);
		doubleW dpdt = new doubleW(0.0d);
		doubleW u = new doubleW(0.0d);
		doubleW h = new doubleW(0.0d);
		doubleW s = new doubleW(0.0d);
		doubleW cv = new doubleW(0.0d);
		doubleW cp = new doubleW(0.0d);
		doubleW w = new doubleW(0.0d);
		doubleW g = new doubleW(0.0d);
		doubleW jt = new doubleW(0.0d);
		doubleW k = new doubleW(0.0d);
		StringW herr = new StringW(
				"                                                                                                                                                                                                                                                               ");
		java.util.Vector __io_vec = new java.util.Vector();
		x[(1 - (1))] = 0.77824e0;
		x[(2 - (1))] = 0.02e0;
		x[(3 - (1))] = 0.06e0;
		x[(4 - (1))] = 0.08e0;
		x[(5 - (1))] = 0.03e0;
		x[(6 - (1))] = 0.0015e0;
		x[(7 - (1))] = 0.003e0;
		x[(8 - (1))] = 0.0005e0;
		x[(9 - (1))] = 0.00165e0;
		x[(10 - (1))] = 0.00215e0;
		x[(11 - (1))] = 0.00088e0;
		x[(12 - (1))] = 0.00024e0;
		x[(13 - (1))] = 0.00015e0;
		x[(14 - (1))] = 0.00009e0;
		x[(15 - (1))] = 0.004e0;
		x[(16 - (1))] = 0.005e0;
		x[(17 - (1))] = 0.002e0;
		x[(18 - (1))] = 0.0001e0;
		x[(19 - (1))] = 0.0025e0;
		x[(20 - (1))] = 0.007e0;
		x[(21 - (1))] = 0.001e0;
// 
		s.val = (double) (1.0f);
		if ((Math.abs((s.val - 1.e0)) > 0.00001e0)) {
			org.netlib.util.Util.pause("Composition sum<>1");
		}
		//
// c  T (K) and P (kPa) inputs
		t = (double) (400);
		p = (double) (50000);
// 
		__io_vec.clear();
		__io_vec.addElement(new String("Inputs-----"));
		Util.f77write(null, __io_vec);
		__io_vec.clear();
		__io_vec.addElement(new String("Temperature [K]:                   "));
		__io_vec.addElement(new Float((float) (t)));
		Util.f77write(null, __io_vec);
		__io_vec.clear();
		__io_vec.addElement(new String("Pressure [kPa]:                    "));
		__io_vec.addElement(new Float((float) (p)));
		Util.f77write(null, __io_vec);
// 
		__io_vec.clear();
		__io_vec.addElement(new String("Outputs-----"));
		Util.f77write(null, __io_vec);
// 
// c  Get molar mass.
		neqsim.thermo.util.GERG.Molarmassgerg.molarmassgerg(x, 0, mm);
		__io_vec.clear();
		__io_vec.addElement(new String("Molar mass [g/mol]:                "));
		__io_vec.addElement(new Float((float) (mm.val)));
		Util.f77write(null, __io_vec);
// 
// c  Get molar density at T and P.
		neqsim.thermo.util.GERG.Densitygerg.densitygerg(0, t, p, x, 0, d, ierr, herr);
		__io_vec.clear();
		__io_vec.addElement(new String("Error number:                      "));
		__io_vec.addElement(new Integer(ierr.val));
		Util.f77write(null, __io_vec);
		__io_vec.clear();
		__io_vec.addElement(new String("Molar density [mol/l]:             "));
		__io_vec.addElement(new Float((float) (d.val)));
		Util.f77write(null, __io_vec);
// 
// c  Get pressure from T and D.
		neqsim.thermo.util.GERG.Pressuregerg.pressuregerg(t, d.val, x, 0, pp, z);
		__io_vec.clear();
		__io_vec.addElement(new String("Pressure [kPa]:                    "));
		__io_vec.addElement(new Float((float) (pp.val)));
		Util.f77write(null, __io_vec);
// 
// c  Get all other properties at T and D.
// c  For T and P inputs, the DensityGERG routine must be called first to g
		neqsim.thermo.util.GERG.Propertiesgerg.propertiesgerg(t, d.val, x, 0, pp, z, dpdd, d2pdd2, d2pdtd, dpdt, u, h,
				s, cv, cp, w, g, jt, k);
		__io_vec.clear();
		__io_vec.addElement(new String("Compressibility factor:            "));
		__io_vec.addElement(new Float((float) (z.val)));
		Util.f77write(null, __io_vec);
		__io_vec.clear();
		__io_vec.addElement(new String("d(P)/d(rho) [kPa/(mol/l)]:         "));
		__io_vec.addElement(new Float((float) (dpdd.val)));
		Util.f77write(null, __io_vec);
		__io_vec.clear();
		__io_vec.addElement(new String("d^2(P)/d(rho)^2 [kPa/(mol/l)^2]:   "));
		__io_vec.addElement(new Float((float) (d2pdd2.val)));
		Util.f77write(null, __io_vec);
		__io_vec.clear();
		__io_vec.addElement(new String("d(P)/d(T) [kPa/K]:                 "));
		__io_vec.addElement(new Float((float) (dpdt.val)));
		Util.f77write(null, __io_vec);
		__io_vec.clear();
		__io_vec.addElement(new String("Energy [J/mol]:                    "));
		__io_vec.addElement(new Float((float) (u.val)));
		Util.f77write(null, __io_vec);
		__io_vec.clear();
		__io_vec.addElement(new String("Enthalpy [J/mol]:                  "));
		__io_vec.addElement(new Float((float) (h.val)));
		Util.f77write(null, __io_vec);
		__io_vec.clear();
		__io_vec.addElement(new String("Entropy [J/mol-K]:                 "));
		__io_vec.addElement(new Float((float) (s.val)));
		Util.f77write(null, __io_vec);
		__io_vec.clear();
		__io_vec.addElement(new String("Isochoric heat capacity [J/mol-K]: "));
		__io_vec.addElement(new Float((float) (cv.val)));
		Util.f77write(null, __io_vec);
		__io_vec.clear();
		__io_vec.addElement(new String("Isobaric heat capacity [J/mol-K]:  "));
		__io_vec.addElement(new Float((float) (cp.val)));
		Util.f77write(null, __io_vec);
		__io_vec.clear();
		__io_vec.addElement(new String("Speed of sound [m/s]:              "));
		__io_vec.addElement(new Float((float) (w.val)));
		Util.f77write(null, __io_vec);
		__io_vec.clear();
		__io_vec.addElement(new String("Gibbs energy [J/mol]:              "));
		__io_vec.addElement(new Float((float) (g.val)));
		Util.f77write(null, __io_vec);
		__io_vec.clear();
		__io_vec.addElement(new String("Joule-Thomson coefficient [K/kPa]: "));
		__io_vec.addElement(new Float((float) (jt.val)));
		Util.f77write(null, __io_vec);
		__io_vec.clear();
		__io_vec.addElement(new String("Isentropic exponent:               "));
		__io_vec.addElement(new Float((float) (k.val)));
		Util.f77write(null, __io_vec);
// 
		org.netlib.util.Util.pause();
//Dummy.label("neqsim/thermo/GERG/Examplegerg",999999);
		return;
	}

	public static void main(String[] args) {
		Examplegerg.examplegerg();
	}
} // End class.
