package de.tu_dresden.lat.evonne;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import de.tu_dresden.inf.lat.generators.GraphMLGenerator;
import org.apache.log4j.Logger;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLEntity;

import de.tu_dresden.inf.lat.evee.data.ProofType;
import de.tu_dresden.inf.lat.evee.proofs.data.Inference;
import de.tu_dresden.inf.lat.evee.proofs.data.Proof;
import de.tu_dresden.inf.lat.evee.proofs.data.exceptions.ProofGenerationFailedException;
import de.tu_dresden.inf.lat.evee.proofs.interfaces.IInference;
import de.tu_dresden.inf.lat.evee.proofs.interfaces.IProof;
import de.tu_dresden.inf.lat.evee.proofs.json.JsonProofParser;
import de.tu_dresden.lat.tools.ProofRefiner;
import org.semanticweb.owlapi.model.OWLOntology;

/**
 * @author Christian Alrabbaa
 *
 */
public class EvonneGMLInputGenerator {

	private static final Logger logger = Logger.getLogger(EvonneGMLInputGenerator.class);

	public static void iProof2GML(IProof<OWLAxiom> proof, String outDirStr, String outFileStr,
								  OWLOntology ontologyOpt, boolean translateAxioms2NL) {
		GraphMLGenerator.setOntology(ontologyOpt);
		iProof2GML(proof,outDirStr,outFileStr,translateAxioms2NL);
	}

	public static void iProof2GML(IProof<OWLAxiom> proof, String outDirStr, String outFileStr,
								  boolean translateAxioms2NL) {
		try {
			GraphMLGenerator.drawProofs(Collections.singletonList(proof),
					System.getProperty("user.dir") + File.separator + outDirStr + File.separator + outFileStr,
					translateAxioms2NL);
		} catch (ParserConfigurationException | TransformerException e) {
			logger.error("Problem generating GML files for the rewritten proof");
			e.printStackTrace();
		}
		logger.info("Done!");
	}

	public static void jSON2GML(String jsonProofPathStr, String outDirStr, String outFileStr, ProofType type,
								Collection<OWLEntity> signature, OWLOntology ontology, boolean translateAxioms2NL)
			throws ProofGenerationFailedException {

		IProof<OWLAxiom> jsonProof = loadProof(jsonProofPathStr);
		jsonProof = ProofRefiner.refineProof(jsonProof, type, signature);
		assert jsonProof != null;
		jsonProof = formatProof(jsonProof);

		try {
			Files.createDirectories(Paths.get(outDirStr));
		} catch (IOException e1) {
			logger.error("Problem creating \"" + outDirStr + "\"");
			e1.printStackTrace();
		}

		try {
			Set<IProof<OWLAxiom>> proofs = new HashSet<>();
			proofs.add(jsonProof);
			//needed to be able to use preferable labels
			GraphMLGenerator.setOntology(ontology);
			GraphMLGenerator.drawProofs(proofs,
					System.getProperty("user.dir") + File.separator + outDirStr + File.separator + outFileStr,
					translateAxioms2NL);
		} catch (ParserConfigurationException | TransformerException e) {
			logger.error("Problem generating GML files");
			e.printStackTrace();
		}
		logger.info("Done!");
	}

	private static IProof<OWLAxiom> formatProof(IProof<OWLAxiom> proof) {
		List<IInference<OWLAxiom>> formattedInferences = new LinkedList<>();
		IInference<OWLAxiom> current;
		for (IInference<OWLAxiom> inf : proof.getInferences()) {
			if (inf.getRuleName().toLowerCase().trim().contains("assert"))
				current = new Inference<>(inf.getConclusion(), "Asserted Conclusion", inf.getPremises());
			else if (inf.getRuleName().toLowerCase().trim().contains("forget"))
				current = new Inference<>(inf.getConclusion(), "Forget", inf.getPremises());
			else if (inf.getRuleName().toLowerCase().trim().contains("direct"))
				current = new Inference<>(inf.getConclusion(), "Direct", inf.getPremises());
			else
				current = inf;

			formattedInferences.add(current);
		}
		return new Proof<>(proof.getFinalConclusion(), formattedInferences);
	}

	private static IProof<OWLAxiom> loadProof(String jsonProofPathStr) {
		File jsonProofFile = new File(jsonProofPathStr);
		if (!jsonProofFile.exists())
			logger.error("Proof file \"" + jsonProofPathStr + "\"was not found!");

		return JsonProofParser.getInstance().fromFile(jsonProofFile);
	}

}
