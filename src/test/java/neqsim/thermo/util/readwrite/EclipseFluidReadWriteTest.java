/**
 * 
 */
package neqsim.thermo.util.readwrite;

import static org.junit.jupiter.api.Assertions.*;
import neqsim.thermo.util.readwrite.EclipseFluidReadWrite;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * @author ESOL
 *
 */
class EclipseFluidReadWriteTest {

	static neqsim.thermo.system.SystemInterface testSystem = null;

	/**
	 * @throws java.lang.Exception
	 */
	@BeforeAll
	static void setUpBeforeClass() throws Exception {
		
	}

	/**
	 * Test method for
	 * {@link neqsim.thermo.util.readwrite.EclipseFluidReadWrite#read(java.lang.String)}.
	 */
	@Test
	void testRead() {
		 testSystem = EclipseFluidReadWrite.read(
	                "C:\\\\Users\\\\esol\\\\OneDrive - Equinor\\\\programming\\\\neqsim\\\\src\\\\main\\\\java\\\\neqsim\\\\thermo\\\\util\\\\readwrite\\\\examplefileMet.txt");

	}
	
	@Test
	void testReadAndAddFluids() {
		 EclipseFluidReadWrite.pseudoName="_A19";
		 testSystem = EclipseFluidReadWrite.read("C:\\temp\\Eclipse_Comp_ML_A-19.E300");
		 
		 EclipseFluidReadWrite.pseudoName="_A17";
		 neqsim.thermo.system.SystemInterface testSystem2 = EclipseFluidReadWrite.read("C:\\temp\\Eclipse_Comp_ML_A-17.E300");
		 testSystem.addFluid(testSystem2);
		 
		 EclipseFluidReadWrite.pseudoName="_A13";
		 neqsim.thermo.system.SystemInterface testSystem3 = EclipseFluidReadWrite.read("C:\\temp\\Eclipse_Comp_ML_A-13.E300");
		 testSystem.addFluid(testSystem3);
		 
		 ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
	     testOps.TPflash();
	}
	
	@Test
	void testSetComposition() {
		 EclipseFluidReadWrite.pseudoName="_A19";
		 testSystem = EclipseFluidReadWrite.read("C:\\temp\\Eclipse_Comp_ML_A-19.E300");
		 
		 EclipseFluidReadWrite.pseudoName="_A17";
		 neqsim.thermo.system.SystemInterface testSystem2 = EclipseFluidReadWrite.read("C:\\temp\\Eclipse_Comp_ML_A-17.E300");
		 testSystem.addFluid(testSystem2);
		 
		 EclipseFluidReadWrite.pseudoName="_A13";
		 neqsim.thermo.system.SystemInterface testSystem3 = EclipseFluidReadWrite.read("C:\\temp\\Eclipse_Comp_ML_A-13.E300");
		 testSystem.addFluid(testSystem3);
		 
		 EclipseFluidReadWrite.pseudoName="_A13";
		 EclipseFluidReadWrite.setComposition(testSystem, "C:\\temp\\Eclipse_Comp_ML_A-13.E300");
		 ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
	     testOps.TPflash();
	}
	

}
