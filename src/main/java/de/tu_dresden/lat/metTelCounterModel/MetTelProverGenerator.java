package de.tu_dresden.lat.metTelCounterModel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import de.tu_dresden.inf.lat.counterExample.tools.Segmenter;
import de.tu_dresden.inf.lat.model.interfaces.IProverGenerator;
import de.tu_dresden.inf.lat.model.tools.GeneralTools;
import de.tu_dresden.inf.lat.model.tools.ToMetTools;
import de.tu_dresden.inf.lat.model.tools.ToOWLTools;
import org.apache.log4j.Logger;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLEquivalentClassesAxiom;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;

import de.tu_dresden.lat.metTelCounterModel.preliminaries.Calculus;
import de.tu_dresden.lat.metTelCounterModel.preliminaries.Properties;
import de.tu_dresden.lat.metTelCounterModel.preliminaries.Syntax;
import de.tu_dresden.lat.tools.Cleaner;

/**
 * @author Christian Alrabbaa
 *
 */

public class MetTelProverGenerator implements IProverGenerator {

	private static final Logger logger = Logger.getLogger(MetTelProverGenerator.class);
	private static final ToOWLTools owlTools = ToOWLTools.getInstance();
	private static final ToMetTools metTools = ToMetTools.getInstance();

	private static final Calculus solverCalculus = Calculus.getInstance();
	private static final Syntax solverSyntax = Syntax.getInstance();
	private static final Properties solverProperties = Properties.getInstance();

	public MetTelProverGenerator() {
	}

	private static class LazyHolder {
		static MetTelProverGenerator instance = new MetTelProverGenerator();
	}

	public static MetTelProverGenerator getInstance() {
		return LazyHolder.instance;
	}

	/**
	 * @param ontology
	 * @param outStream
	 */
	public void OWL2MetRules(OWLOntology ontology, OWLEntity lhs, FileOutputStream outStream) {

		OWLOntology s = null;

		try {
			s = Segmenter.getSegmentAsOntology(ontology, lhs, IRI.create(""));
		} catch (OWLOntologyCreationException e) {
			e.printStackTrace();
		}

		ontology = s;

		if (logger.isDebugEnabled()) {
			logger.debug("Axioms Concerning the LHS of the conclusion");
			ontology.getAxioms().forEach(x -> {
				logger.debug(x);
			});

			logger.debug("Axioms' count = " + ontology.getAxioms().size());
		}

		// add a rule for every TBox axiom
		ontology.getAxioms().forEach(axiom -> {
			// GeneralTools.writeTo("\n //" + axiom + "\n", outStream);
			owlTools.getSimplifiedAxiom(axiom).forEach(expanded -> {
				String meTAxiom = metTools.getMetCalculusRule(expanded);
				writeTo(meTAxiom, axiom, outStream);
			});
		});
	}

	/**
	 * @param ontology
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws OWLOntologyCreationException
	 */
	public void generateProver(OWLOntology ontology, OWLAxiom conclusion)
			throws IOException, InterruptedException, OWLOntologyCreationException {

		Files.createDirectories(Paths.get(modelDirectory));

		// Populate the specification file
		File metSpecsFile = GeneralTools.createFile(specificationsFile);
		FileOutputStream specsOutStream = new FileOutputStream(metSpecsFile, true);

		solverProperties.appendALCOProperties(specsOutStream);
		solverSyntax.appendALCOsyntax(specsOutStream);
		// some default calculus
		GeneralTools.writeTo("tableau " + NAME + " {\n @l P / @l P priority 1$;\n}", specsOutStream);

		specsOutStream.close();

		// Create Solver if it does not exist
		createSolver();

		// Populate the calculus extension
		File metTboxFile = GeneralTools.createFile(calculusExtensionFile);
		FileOutputStream tboxOutStream = new FileOutputStream(metTboxFile, true);

		solverCalculus.appendALCOInitCalculus(tboxOutStream);
		GeneralTools.writeTo("\n//TBOX\n", tboxOutStream);

		OWLClassExpression lhs = owlTools.getLHS(conclusion);

		// handle conjunction on the LHS of an axiom in order to create a module
		OWLEquivalentClassesAxiom newAxiom = owlTools.getOWLEquivalenceAxiom(ClassAlias.getClassAlias(), lhs);

		try {
			owlTools.addAxiom(newAxiom, ontology);
		} catch (OWLOntologyStorageException e) {
			e.printStackTrace();
		}

		OWL2MetRules(ontology, ClassAlias.getClassAlias(), tboxOutStream);

		Cleaner.clean(proverDirectory + File.separator + NAME);

	}

	private void createSolver() throws IOException, InterruptedException {

		File jarFile = (new File(proverJarFile));

		if (jarFile.exists())
			return;

		Process p = Runtime.getRuntime().exec("java -jar " + proverDirectory + File.separator + "mettel2.jar -i "
				+ specificationsFile + " -d " + proverDirectory);
		p.waitFor();

		GeneralTools.printCommandOutput(p);

		if (p.exitValue() == 0) {
			logger.info("Prover \"jar\" file -> \"" + proverJarFile + "\"");

			(new File(NAME + ".jar")).renameTo(jarFile);
			(new File(NAME + ".tokens"))
					.renameTo(new File(proverJarFile.substring(0, proverJarFile.lastIndexOf(".")) + ".tockens"));
		}

		else
			logger.fatal("Prover \"jar\" file was not created!");

	}

	/**
	 * @param str
	 * @param object
	 * @param outStream
	 */
	private void writeTo(String str, OWLObject object, FileOutputStream outStream) {

		if (str.isEmpty())
			logger.info("[" + object + "] was ignored!");
		else
			GeneralTools.writeTo(str, outStream);
	}
}
