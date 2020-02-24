package neqsim.thermo.util.empiric;

public class DuanSun {
	
	double[] c = new double[] {0.1, 0.2, 0.3, 0.4, 0.5};
	double[] d = new double[] {0.1, 0.2, 0.3, 0.4, 0.5};
	
	public DuanSun() {
		
	}
	
	public double calcCO2solubility(double temperature, double pressure, double salinity) {
		double CO2solubility = 0.0;

		CO2solubility = c[0]+ c[1]*temperature + c[2] + d[0];
		
		return CO2solubility;
	}

	
	public static void main(String[] args) {
		System.out.println("helo world from DuanSun...");
		
		
		DuanSun testDuanSun = new DuanSun();
		
		double CO2solubility = testDuanSun.calcCO2solubility(298.15, 10.0 , 2.0);
		
		System.out.println("CO2solubility "+ CO2solubility + " mol/mol");
	}
}
