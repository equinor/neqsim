package neqsim.thermo.util.GERG;
import org.netlib.util.*;

public class GERG2008_gerg2008
{
public static double [] dc= new double[(21)];
public static double [] tc= new double[(21)];
public static double [] mmigerg= new double[(21)];
public static double [] vc3= new double[(21)];
public static double [] tc2= new double[(21)];
public static double [] noik= new double[(21) * (24)];
public static double [] toik= new double[(21) * (24)];
public static double [] cijk= new double[(10) * (12)];
public static double [] eijk= new double[(10) * (12)];
public static double [] gijk= new double[(10) * (12)];
public static double [] nijk= new double[(10) * (12)];
public static double [] tijk= new double[(10) * (12)];
public static double [] btij= new double[(21) * (21)];
public static double [] bvij= new double[(21) * (21)];
public static double [] gtij= new double[(21) * (21)];
public static double [] gvij= new double[(21) * (21)];
public static double [] fij= new double[(21) * (21)];
public static double [] n0i= new double[(21) * (7)];
public static double [] th0i= new double[(21) * (7)];
public static double [] taup= new double[(21) * (24)];
public static double [] taupijk= new double[(21) * (12)];
public static double [] xold= new double[(21)];
public static doubleW drold= new doubleW(0.0d);
public static doubleW trold= new doubleW(0.0d);
public static doubleW told= new doubleW(0.0d);
public static doubleW trold2= new doubleW(0.0d);
public static doubleW dpddsave= new doubleW(0.0d);
public static doubleW rgerg= new doubleW(0.0d);
public static int [] coik= new int[(21) * (24)];
public static int [] doik= new int[(21) * (24)];
public static int [] dijk= new int[(10) * (12)];
public static int [] mnumb= new int[(21) * (21)];
public static int [] kpol= new int[(21)];
public static int [] kexp= new int[(21)];
public static int [] kpolij= new int[(10)];
public static int [] kexpij= new int[(10)];
}
