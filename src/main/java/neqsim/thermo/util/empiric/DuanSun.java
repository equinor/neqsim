package neqsim.thermo.util.empiric;

public class DuanSun {
	private static final long serialVersionUID = 1000;
	double[] c = new double[] {0.1, 0.2, 0.3, 0.4, 0.5};
	double[] d = new double[] {0.1, 0.2, 0.3, 0.4, 0.5};
	
	public DuanSun() {
		
	}
	
	public double calcCO2solubility(double temperature, double pressure, double salinity) {
		double T=temperature;
		double P=pressure;
		double S=salinity;
		double Tc1=304.2; double Tc2=647.29;double Pc1=73.825;double Pc2=220.85;
		double c1=0; double c2=0;double c3=0; double c4=0;double c5=0; double c6=0;double c7=0; double c8=0; double c9=0; double c10=0;double c11=0; double c12=0;double c13=0; double c14=0;double c15=0;
//		double c[]= {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0};
		double parcdpsat[]= {85.530,-3481.3,-11.336,0.021505,1.0};
//		for (int i=0;i<parcdpsat.length;i++)
//		{
//			System.out.println(parcdpsat[i]);	
//		} 
		double PCO2sat=0.0;
		PCO2sat=Math.exp(parcdpsat[0]+(parcdpsat[1]/T)+(parcdpsat[2]*Math.log(T))+(parcdpsat[3]*(Math.pow(T,parcdpsat[4]))))/100000;
//		System.out.println(PCO2sat);
		if(T>=273.0 && T<305.0 && P<=PCO2sat)
		{
		c1=1.0;c2=4.7586835*Math.pow(10.0,-3.0);c3=-3.3569963*Math.pow(10.0,-6.0);c4=0.0;c5=-1.3179396;c6=-3.8389101*Math.pow(10.0,-6.0);c7=0.0;c8=2.2815104*Math.pow(10.0,-3.0);c9=0.0;c10=0.0;c11=0.0;c12=0.0;c13=0.0;c14=0.0;c15=0.0;
		}else if ((T>=305.0 && T<=405.0 && P<=(75.0+(T-305.0)*1.25)))
		{
	    c1=1.0;c2=4.7586835*Math.pow(10.0,-3.0);c3=-3.3569963*Math.pow(10.0,-6.0);c4=0.0;c5=-1.3179396;c6=-3.8389101*Math.pow(10.0,-6.0);c7=0.0;c8=2.2815104*Math.pow(10.0,-3.0);c9=0.0;c10=0.0;c11=0.0;c12=0.0;c13=0.0;c14=0.0;c15=0.0;
		}else if (T>405.0 && P<=200.0)
		{
		c1=1.0;c2=4.7586835*Math.pow(10.0,-3.0);c3=-3.3569963*Math.pow(10.0,-6.0);c4=0.0;c5=-1.3179396;c6=-3.8389101*Math.pow(10.0,-6.0);c7=0.0;c8=2.2815104*Math.pow(10.0,-3.0);c9=0.0;c10=0.0;c11=0.0;c12=0.0;c13=0.0;c14=0.0;c15=0.0;
		}else if (T>=273.0 && T<305.0 && P<=1000 && P>PCO2sat)
		{
		c1=-7.1734882*Math.pow(10.0,-1.0);c2=1.5985379*Math.pow(10.0,-4.0);c3=-4.9286471*Math.pow(10.0,-7.0);c4=0.0;c5=0.0;c6=-2.7855285*Math.pow(10.0,-7.0);c7=1.1877015*Math.pow(10.0,-9.0);c8=0.0;c9=0.0;c10=0.0;c11=0.0;c12=-96.539512;c13=4.4774938*Math.pow(10.0,-1.0);c14=101.81078;c15=5.3783879*Math.pow(10,-6.0);
		}else if (T>=305.0 && T<=340.0 && P<=1000.0 && P>(75.0+(T-305.0))*1.25)
		{
		c1=-7.1734882*Math.pow(10.0,-1.0);c2=1.5985379*Math.pow(10.0,-4.0);c3=-4.9286471*Math.pow(10.0,-7.0);c4=0.0;c5=0.0;c6=-2.7855285*Math.pow(10.0,-7.0);c7=1.1877015*Math.pow(10.0,-9.0);c8=0.0;c9=0.0;c10=0.0;c11=0.0;c12=-96.539512;c13=4.4774938*Math.pow(10.0,-1.0);c14=101.81078;c15=5.3783879*Math.pow(10,-6.0);
		}else if (T>=273.0 && T<=340.0 && P>1000.0)
		{
		c1=-6.5129019*Math.pow(10.0,-2.0);c2=-2.1429977*Math.pow(10.0,-4.0);c3=-1.144493*Math.pow(10.0,-6.0);c4=0.0;c5=0.0;c6=-1.1558081*Math.pow(10.0,-7.0);c7=1.195237*Math.pow(10.0,-9.0);c8=0.0;c9=0.0;c10=0.0;c11=0.0;c12=-221.34306;c13=0.0;c14=71.820393;c15=6.6089246*Math.pow(10,-6.0);	
		}else if (T>340 && T<405 && P<=1000.0 && P>(75.0+(T-305.0)*1.25))
		{
		c1=5.0383896;c2=-4.4257744*Math.pow(10.0,-3);c3=0.0;c4=1.9572733;c5=0.0;c6=2.4223436*Math.pow(10.0,-6.0);c7=0.0;c8=-9.3796135*Math.pow(10.0,-4.0);c9=-1.5026030;c10=3.027224*Math.pow(10.0,-3.0);c11=-31.377342;c12=-12.847063;c13=0.0;c14=0.0;c15=-1.5056648*Math.pow(10,-5.0);	
		}else if (T>=405.0 && T<=435.0 && P<=1000.0 && P>200.0)
		{
		c1=5.0383896;c2=-4.4257744*Math.pow(10.0,-3);c3=0.0;c4=1.9572733;c5=0.0;c6=2.4223436*Math.pow(10.0,-6.0);c7=0.0;c8=-9.3796135*Math.pow(10.0,-4.0);c9=-1.5026030;c10=3.027224*Math.pow(10.0,-3.0);c11=-31.377342;c12=-12.847063;c13=0.0;c14=0.0;c15=-1.5056648*Math.pow(10,-5.0);	
		}else if (T>340 && T<=435.0 && P>1000.0)
		{
		c1=-16.063152;c2=-2.705799*Math.pow(10.0,-3);c3=0.0;c4=1.4119239*Math.pow(10.0,-1.0);c5=0.0;c6=8.1132965*Math.pow(10.0,-7.0);c7=0.0;c8=-1.1453082*Math.pow(10.0,-4.0);c9=2.3895671;c10=5.0527457*Math.pow(10.0,-4.0);c11=-17.76346;c12=985.92232;c13=0.0;c14=0.0;c15=-5.4965256*Math.pow(10,-7.0);	
		}else if (T>435.0 && P>200.0)	
		{
		c1=-1.569349*Math.pow(10.0,-1.0);c2=4.4621407*Math.pow(10.0,-4);c3=-9.1080591*Math.pow(10.0,-7.0);c4=0.0;c5=0.0;c6=1.0647399*Math.pow(10.0,-7.0);c7=2.4273357*Math.pow(10.0,-10.0);c8=0.0;c9=3.5874255*Math.pow(10.0,-1.0);c10=6.331971*Math.pow(10.0,-5.0);c11=-249.89661;c12=0.0;c13=0.0;c14=888.768;c15=-6.6348003*Math.pow(10,-7.0);	
		}
		
//		System.out.println(c1);
//		System.out.println("PCO2sat = " + PCO2sat);
		
		double fCO2=0.0;
		fCO2=c1+(c2+c3*T+c4/T+c5/(T-150.0))*P+(c6+c7*T+c8/T)*Math.pow(P,2)+(c9+c10*T+c11/T)*Math.log(P)+(c12+c13*T)/P+c14/T+c15*Math.pow(T,2);
	//	System.out.println("fCO2 = " + fCO2);
		
		double chempotliqCO2RT=0.0;
		chempotliqCO2RT=28.9447706-0.0354581768*T-4770.67077/T+1.02782768*Math.pow(10.0,-5.0)*Math.pow(T,2.0)+33.8126098/(630.0-T)+0.009040371*P-0.00114934*P*Math.log(T)-0.307405726*P/T-0.090730149*P/(630.0-T)+0.000932713*Math.pow(P,2)/(Math.pow((630.0-T),2));
//		System.out.println("chempotliqCO2RT = " + chempotliqCO2RT);
		
		double lamdaCO2Na=0.0;
		lamdaCO2Na=-0.411370585+0.000607632*T+97.5347708/T-0.023762247*P/T+0.017065624*P/(630.0-T)+1.41335834*Math.pow(10.0,-5.0)*T*Math.log(P);
//		System.out.println("lamdaCO2Na = " + lamdaCO2Na);
	
		double zetaCO2NaCl=0.0; 
		zetaCO2NaCl=0.00033639-1.9829898*Math.pow(10.0,-5.0)*T+0.002122208*P/T-0.005248733*P/(630.-T);
//		System.out.println("zetaCO2NaCl = " + zetaCO2NaCl);

	    double tH2O=0.0;
	    tH2O=(T-Tc2)/Tc2;
	    
	    double PH2O=0.0;
	    PH2O=(Pc2*T/Tc2)*(1.0-38.640844*Math.pow(-tH2O,1.9)+5.8948420*tH2O+59.876516*Math.pow(tH2O,2.0)+26.654627*Math.pow(tH2O,3.0)+10.637097*Math.pow(tH2O,4.0));
	
        double yCO2=0.0;
        yCO2=(P-PH2O)/P;
        
        double mCO2=0.0;
        mCO2=(yCO2*P)/Math.exp((-Math.log(fCO2)+chempotliqCO2RT+(2.0)*lamdaCO2Na*S+zetaCO2NaCl*Math.pow(S,2.0)));
     //   System.out.println("mCO2 = " + mCO2 + " mol/kg solvent ");		
        
        double xCO2=0.0;
        xCO2=mCO2/(1000./18.+mCO2);		
        //System.out.println("xCO2 = " + xCO2 + " mole fraction ");	
		
		return xCO2;
	}

	
	public static void main(String[] args) {
		System.out.println("helo world from DuanSun...");
		
		
		DuanSun testDuanSun = new DuanSun();
		
		double CO2solubility = testDuanSun.calcCO2solubility(298.15, 10.0 , 2.0);
		
		System.out.println("CO2solubility "+ CO2solubility + " mol/mol");
	}
}
