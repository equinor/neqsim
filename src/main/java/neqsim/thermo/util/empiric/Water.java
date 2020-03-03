package neqsim.thermo.util.empiric;

public class Water {
	private static final long serialVersionUID = 1000;
	// Correlation fo Kell (1975) for density of air free water at 1 atmosphere
	public static double waterDensity(double temperature) {
		double Tcelcius = temperature-273.15;
		return (999.83952 + 16.945176*Tcelcius - 7.9870401e-3*Tcelcius*Tcelcius - 46.170461e-6*Tcelcius*Tcelcius*Tcelcius +
				105.56302e-9*Tcelcius*Tcelcius*Tcelcius*Tcelcius -280.54253e-12*Tcelcius*Tcelcius*Tcelcius*Tcelcius*Tcelcius)/
				(1.0+16.897850e-3*Tcelcius);
	}

	public double density() {
			return 1000.0;
	}

	public static void main(String[] args) {
		Water testWater = new Water();
		System.out.println("water density " + testWater.waterDensity(273.15+4));
	}

}
