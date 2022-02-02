/**
 * 
 */
package neqsim.thermo.util.readwrite;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

import neqsim.thermo.util.readwrite.EclipseFluidReadWrite;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.common.io.Files;

import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * @author ESOL
 *
 */
class EclipseFluidReadWriteTest {

	static neqsim.thermo.system.SystemInterface testSystem = null;

	File file = new File("src/test/java/neqsim/thermo/util/readwrite");
	String fileA13 = file.getAbsolutePath() + "/A-13.E300";
	String fileA17 = file.getAbsolutePath() + "/A-17.E300";
	String fileA19 = file.getAbsolutePath() + "/A-19.E300";

	/**
	 * @throws java.lang.Exception
	 */
	@BeforeAll
	static void setUpBeforeClass() throws Exception {

	}

	/**
	 * Test method for
	 * {@link neqsim.thermo.util.readwrite.EclipseFluidReadWrite#read(java.lang.String)}.
	 * 
	 * @throws IOException
	 */
	@Test
	void testRead() throws IOException {
		EclipseFluidReadWrite.pseudoName = "_A13";
		testSystem = EclipseFluidReadWrite.read(fileA13);
	}

	@Test
	void testReadAndAddFluids() throws IOException {
		EclipseFluidReadWrite.pseudoName = "_A19";
		testSystem = EclipseFluidReadWrite.read(fileA19);

		EclipseFluidReadWrite.pseudoName = "_A17";
		neqsim.thermo.system.SystemInterface testSystem2 = EclipseFluidReadWrite.read(fileA17);
		testSystem.addFluid(testSystem2);

		EclipseFluidReadWrite.pseudoName = "_A13";
		neqsim.thermo.system.SystemInterface testSystem3 = EclipseFluidReadWrite.read(fileA13);
		testSystem.addFluid(testSystem3);

		ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
		testOps.TPflash();

	}

	@Test
	void testSetComposition() throws IOException {
		EclipseFluidReadWrite.pseudoName = "_A19";
		testSystem = EclipseFluidReadWrite.read(fileA19);

		EclipseFluidReadWrite.pseudoName = "_A17";
		neqsim.thermo.system.SystemInterface testSystem2 = EclipseFluidReadWrite.read(fileA17);
		testSystem.addFluid(testSystem2);

		EclipseFluidReadWrite.pseudoName = "_A13";
		neqsim.thermo.system.SystemInterface testSystem3 = EclipseFluidReadWrite.read(fileA13);
		testSystem.addFluid(testSystem3);

		EclipseFluidReadWrite.pseudoName = "_A13";
		EclipseFluidReadWrite.setComposition(testSystem, fileA13);
		ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
		testOps.TPflash();
		testSystem.display();
		testOps.TPflash();
	}

}
