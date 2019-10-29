
package neqsim.thermo.util.GERG;
import org.netlib.util.StringW;
import org.netlib.util.doubleW;
import org.netlib.util.intW;

public final class Densitygerg {
  public static void densitygerg(int paramInt1, double paramDouble1, double paramDouble2, double[] paramArrayOfDouble, int paramInt2, doubleW paramdoubleW, intW paramintW, StringW paramStringW) {
    byte b1 = 0;
    byte b2 = 0;
    boolean bool = false;
    double d1 = 0.0D;
    double d2 = 0.0D;
    double d3 = 0.0D;
    doubleW doubleW1 = new doubleW(0.0D);
    doubleW doubleW2 = new doubleW(0.0D);
    double d4 = 0.0D;
    double d5 = 0.0D;
    double d6 = 0.0D;
    doubleW doubleW3 = new doubleW(0.0D);
    doubleW doubleW4 = new doubleW(0.0D);
    doubleW doubleW5 = new doubleW(0.0D);
    doubleW doubleW6 = new doubleW(0.0D);
    doubleW doubleW7 = new doubleW(0.0D);
    doubleW doubleW8 = new doubleW(0.0D);
    doubleW doubleW9 = new doubleW(0.0D);
    doubleW doubleW10 = new doubleW(0.0D);
    doubleW doubleW11 = new doubleW(0.0D);
    doubleW doubleW12 = new doubleW(0.0D);
    doubleW doubleW13 = new doubleW(0.0D);
    doubleW doubleW14 = new doubleW(0.0D);
    doubleW doubleW15 = new doubleW(0.0D);
    doubleW doubleW16 = new doubleW(0.0D);
    doubleW doubleW17 = new doubleW(0.0D);
    doubleW doubleW18 = new doubleW(0.0D);
    if (!(GERG2008_gerg2008.kpol[0] == 6))
      Setupgerg.setupgerg(); 
    paramintW.val = 0;
    paramStringW.val = " ";
    b2 = 0;
    bool = false;
    if (!(paramDouble2 >= 1.0E-15D)) {
      paramdoubleW.val = 0.0D;
      return;
    } 
    d6 = 1.0E-7D;
    Pseudocriticalpointgerg.pseudocriticalpointgerg(paramArrayOfDouble, paramInt2, doubleW4, doubleW5);
    if (!(paramdoubleW.val <= -1.0E-15D)) {
      paramdoubleW.val = paramDouble2 / GERG2008_gerg2008.rgerg.val / paramDouble1;
      if (!(paramInt1 != 2))
        doubleW5.val *= 3.0D; 
    } else {
      paramdoubleW.val = Math.abs(paramdoubleW.val);
    } 
    d1 = Math.log(paramDouble2);
    d2 = -Math.log(paramdoubleW.val);
    b1 = 1;
    for (byte b3 = 50 - 1 + 1; b3 > 0; b3--) {
      if (d2 < -7 || d2 > 100|| b1 == 20 || b1 == 30 || b1 == 40 || bool == true) {
        bool = false;
        if (!(b2 <= 2))
          break; 
        if (!(++b2 != 1)) {
          doubleW5.val *= 3.0D;
        } else if (!(b2 != 2)) {
          doubleW5.val *= 2.5D;
        } else if (!(b2 != 3)) {
          doubleW5.val *= 2.0D;
        } 
        d2 = -Math.log(paramdoubleW.val);
      } 
      paramdoubleW.val = Math.exp(-d2);
      Pressuregerg.pressuregerg(paramDouble1, paramdoubleW.val, paramArrayOfDouble, paramInt2, doubleW1, doubleW2);
      if (GERG2008_gerg2008.dpddsave.val < 1.0E-15D || doubleW1.val < 1.0E-15D) {
        d3 = 0.1D;
        if (!(paramdoubleW.val <= doubleW5.val))
          d3 = -0.1D; 
        if (!(b1 <= 5))
          d3 /= 2.0D; 
        if (((!(b1 <= 10)) && (!(b1 >= 20))))
          d3 /= 5.0D; 
        d2 += d3;
      } else {
        d4 = -(paramdoubleW.val * GERG2008_gerg2008.dpddsave.val);
        d5 = (Math.log(doubleW1.val) - d1) * doubleW1.val / d4;
        d2 -= d5;
        if (!(Math.abs(d5) >= d6))
          if (!(GERG2008_gerg2008.dpddsave.val >= 0.0D)) {
            bool = true;
          } else {
            paramdoubleW.val = Math.exp(-d2);
            if (!(paramInt1 <= 0)) {
              Propertiesgerg.propertiesgerg(paramDouble1, paramdoubleW.val, paramArrayOfDouble, paramInt2, doubleW3, doubleW2, doubleW6, doubleW7, doubleW8, doubleW9, doubleW10, doubleW11, doubleW12, doubleW13, doubleW14, doubleW15, doubleW16, doubleW17, doubleW18);
              if (doubleW3.val < 0.0D || doubleW6.val < 0.0D || doubleW8.val < 0.0D || doubleW13.val < 0.0D || doubleW14.val < 0.0D || doubleW15.val < 0.0D) {
                paramintW.val = 1;
                paramStringW.val = "Calculation failed to converge in GERG method, ideal gas density returned.";
                paramdoubleW.val = paramDouble2 / GERG2008_gerg2008.rgerg.val / paramDouble1;
                return;
              } 
            } 
            return;
          }  
      } 
      b1++;
    } 
    paramintW.val = 1;
    paramStringW.val = "Calculation failed to converge in GERG method, ideal gas density returned.";
    paramdoubleW.val = paramDouble2 / GERG2008_gerg2008.rgerg.val / paramDouble1;
  }
}