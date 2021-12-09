package neqsim.thermo.util.readwrite;

import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermo.system.SystemInterface;
import java.io.*;
import java.util.ArrayList;

public class EclipseFluidReadWrite {

	public static SystemInterface read(String inputFile) {
		neqsim.thermo.system.SystemInterface fluid = new neqsim.thermo.system.SystemSrkEos(288.15, 1.01325);

		try {

			File file = new File(inputFile);
			BufferedReader br = new BufferedReader(new FileReader(file));
			String st;

			ArrayList<String> names = new ArrayList<String>();
			ArrayList<Double> TC = new ArrayList<Double>();
			ArrayList<Double> PC = new ArrayList<Double>();
			ArrayList<Double> ACF = new ArrayList<Double>();
			ArrayList<Double> MW = new ArrayList<Double>();
			ArrayList<String[]> BIC = new ArrayList<String[]>();

			while ((st = br.readLine()) != null) {
				if (st.equals("CNAMES")) {
					while ((st = br.readLine().replace("/", "")) != null) {
						if (st.startsWith("--")) {
							break;
						}
						names.add(st);
					}
				}
				if (st.equals("TCRIT")) {
					while ((st = br.readLine().replace("/", "")) != null) {
						if (st.startsWith("--")) {
							break;
						}
						// System.out.println("TC" + st);
						TC.add(Double.parseDouble(st));
					}
				}
				if (st.equals("PCRIT")) {
					while ((st = br.readLine().replace("/", "")) != null) {
						if (st.startsWith("--")) {
							break;
						}
						PC.add(Double.parseDouble(st));
					}
				}
				if (st.equals("ACF")) {
					while ((st = br.readLine().replace("/", "")) != null) {
						if (st.startsWith("--")) {
							break;
						}
						ACF.add(Double.parseDouble(st));
					}
				}
				if (st.equals("MW")) {
					while ((st = br.readLine().replace("/", "")) != null) {
						if (st.startsWith("--")) {
							break;
						}
						MW.add(Double.parseDouble(st));
					}
				}
				if (st.equals("BIC")) {
					while ((st = br.readLine().replace("/", "")) != null) {
						if (st.startsWith("--")) {
							break;
						}
						String[] parts = st.split("  ");
						BIC.add(parts);
					}
				}
			}
			for (int counter = 0; counter < names.size(); counter++) {
				fluid.addComponent("methane", 1.0);
				for (int i = 0; i < fluid.getMaxNumberOfPhases(); i++) {
					fluid.getPhase(i).getComponent("methane").setTC(TC.get(counter));
					fluid.getPhase(i).getComponent("methane").setPC(PC.get(counter));
					fluid.getPhase(i).getComponent("methane").setAcentricFactor(ACF.get(counter));
					fluid.getPhase(i).getComponent("methane").setMolarMass(MW.get(counter) / 1000.0);
				}
				fluid.changeComponentName("methane", names.get(counter));

			}
			for (int i = 0; i < fluid.getMaxNumberOfPhases(); i++) {
				for (int k = 0; k < fluid.getNumberOfComponents(); k++) {
					for (int l = 0; l < fluid.getNumberOfComponents(); l++) {
						((PhaseEosInterface) fluid.getPhases()[i]).getMixingRule().setBinaryInteractionParameter(k, l,
								Double.parseDouble(BIC.get(k)[l+1]));
					}
				}
			}
			System.out.println(st);
			fluid.setMixingRule(2);
			fluid.init(0);
			fluid.display();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return fluid;
	}

	public static void main(String[] args) throws Exception {
		EclipseFluidReadWrite.read(
				"C:\\\\Users\\\\esol\\\\OneDrive - Equinor\\\\programming\\\\neqsim\\\\src\\\\main\\\\java\\\\neqsim\\\\thermo\\\\util\\\\readwrite\\\\examplefile.txt");
		// System.out.println(st);
	}

}
