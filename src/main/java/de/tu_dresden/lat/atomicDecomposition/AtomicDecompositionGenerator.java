package de.tu_dresden.lat.atomicDecomposition;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

import de.tu_dresden.inf.lat.counterExample.tools.Segmenter;
import org.apache.log4j.Logger;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.OWLXMLDocumentFormat;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLDocumentFormat;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;

import de.tu_dresden.lat.tools.StreamConsumer;

/**
 * @author Christian Alrabbaa
 *
 */
public class AtomicDecompositionGenerator {
	private static final Logger logger = Logger.getLogger(AtomicDecompositionGenerator.class);

	private static final String aDJarPathStr = "externalTools" + File.separator + "AD" + File.separator
			+ "adStarGenerator.jar";

	private AtomicDecompositionGenerator() {
	}

	private static class LazyHolder {
		static AtomicDecompositionGenerator instance = new AtomicDecompositionGenerator();
	}

	public static AtomicDecompositionGenerator getInstance() {
		return LazyHolder.instance;
	}

	public void getADOfModuleOf(OWLOntology ontology, OWLAxiom axiom, String outDirStr, String fileNameStr,
			boolean onlyModule) throws IOException {
		if (outDirStr.isEmpty())
			outDirStr = "generatedAtomicDecomposition";
		if (fileNameStr.isEmpty())
			fileNameStr = "atomicDecomposition";

		String moduleFileName = onlyModule?fileNameStr: "module_"+ fileNameStr;

		Files.createDirectories(Paths.get(outDirStr));

		try {
			logger.info("Extracting a module with a seed signature = " + axiom.getSignature());
			createAndSaveModule(ontology, axiom, outDirStr, moduleFileName);
		} catch (OWLOntologyCreationException e) {
			logger.error("Failed to create the module");
			e.printStackTrace();
		} catch (OWLOntologyStorageException e) {
			logger.error("Failed to save the module");
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			logger.error("Failed to create the module file");
			e.printStackTrace();
		}

		if (onlyModule) {
			logger.info("Done!");
			return;
		}

		String atomicDecompositionFileName = fileNameStr;

		logger.info("Generating the atomic decomposition of the module");
		int tc = runAtomicDecompositionTool(outDirStr, atomicDecompositionFileName);
		if (tc == 0) {
			logger.info(" The atomic decomposition was created successfully");
		} else {
			System.out.println(tc);
			logger.error("Failed to create the atomic decomposition");
		}
	}

	private int runAtomicDecompositionTool(String outDirStr, String fileNameStr) {
		Process p;
		int tc = -1;

		try {
			p = Runtime.getRuntime().exec("java -cp " + aDJarPathStr + " EverythingForGivenOntology " + outDirStr
					+ File.separator + fileNameStr + " " + outDirStr + " " + fileNameStr);

			StreamConsumer errSC = new StreamConsumer(p.getErrorStream(), "ERR");
			StreamConsumer outSC = new StreamConsumer(p.getInputStream(), "OUT");

			errSC.start();
			outSC.start();

			tc = p.waitFor();
		} catch (IOException e) {
			logger.error("Failed to run the AD tool");
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		return tc;
	}

	private void createAndSaveModule(OWLOntology ontology, OWLAxiom axiom, String outDirStr, String fileNameStr)
			throws OWLOntologyCreationException, OWLOntologyStorageException, IOException {

		OWLOntology module = Segmenter.getStarModule(ontology, axiom.getSignature(),
				ontology.getOntologyID().getOntologyIRI().isPresent() ? ontology.getOntologyID().getOntologyIRI().get()
						: IRI.create(""));

		logger.info("STAR MODULE");

		OutputStream outputstream =
				Files.newOutputStream(new File(outDirStr + File.separator + fileNameStr).toPath());
		OWLDocumentFormat ontologyFormat = new OWLXMLDocumentFormat();

		OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
		manager.saveOntology(module, ontologyFormat, outputstream);

	}
}
