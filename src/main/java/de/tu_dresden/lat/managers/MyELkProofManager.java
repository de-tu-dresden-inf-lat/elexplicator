package de.tu_dresden.lat.managers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collection;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import de.tu_dresden.inf.lat.exceptions.EntityCheckerException;
import de.tu_dresden.inf.lat.generators.DotGraphGenerator;
import de.tu_dresden.inf.lat.generators.GraphMLGenerator;
import de.tu_dresden.inf.lat.model.interfaces.IGenerator;
import de.tu_dresden.inf.lat.model.tools.ToOWLTools;
import org.apache.log4j.Logger;
import org.liveontologies.puli.DynamicProof;
import org.semanticweb.elk.owlapi.proofs.ElkOwlInference;
import org.semanticweb.elk.owlapi.proofs.ElkOwlProof;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import com.google.common.collect.Sets;

import de.tu_dresden.inf.lat.evee.data.ProofType;
import de.tu_dresden.inf.lat.evee.inOut.SerialisedProofWriter;
import de.tu_dresden.inf.lat.evee.proofGenerators.ELKProofGenerator;
import de.tu_dresden.inf.lat.evee.proofs.data.exceptions.ProofGenerationFailedException;
import de.tu_dresden.inf.lat.evee.proofs.interfaces.IProof;
import de.tu_dresden.inf.lat.evee.proofs.json.JsonProofWriter;
import de.tu_dresden.inf.lat.evee.tools.GeneralTools;
import de.tu_dresden.inf.lat.prettyPrinting.formatting.SimpleOWLFormatter;
import de.tu_dresden.lat.data.enums.ExitCode;
import de.tu_dresden.lat.data.enums.OutputType;
import de.tu_dresden.lat.tools.AxiomChecker;

/**
 * @author Christian Alrabbaa
 *
 */

public class MyELkProofManager implements IGenerator {

	private static final Logger logger = Logger.getLogger(MyELkProofManager.class);
	private static final ToOWLTools oWLTools = ToOWLTools.getInstance();

	private final OWLOntology ontology;

	/**
	 * Constructor to initialize MyELkProofManager with a given ontology.
	 *
	 * @param ontology The OWLOntology to be used.
	 */
	public MyELkProofManager(OWLOntology ontology) {
		this.ontology = ontology;
	}

	/**
	 * Generates proofs for the given axiom string and specified parameters.
	 *
	 * @param axiomStr The string representation of the axiom for which proofs are generated.
	 * @param fileName The base name of the output file.
	 * @param outDirStr The directory where output files will be saved.
	 * @param mode The output type (e.g., Text, Graph, JSON).
	 * @param type The type of proof to generate (e.g., MinimalSizeGraph, TreeProof).
	 * @param signature The collection of OWL entities used in the proof.
	 * @param generatePNG Flag to determine if PNG images should be generated.
	 * @param translateAxioms2NL Flag to determine if axioms should be translated to natural language.
	 * @return ExitCode indicating the success or failure of the operation.
	 * @throws OWLOntologyCreationException If the ontology creation fails.
	 * @throws IOException If an I/O error occurs.
	 * @throws ProofGenerationFailedException If proof generation fails.
	 * @throws EntityCheckerException If entity checking fails.
	 */
	public ExitCode getProofs(String axiomStr, String fileName, String outDirStr, OutputType mode, ProofType type,
							  Collection<OWLEntity> signature, boolean generatePNG, boolean translateAxioms2NL)
			throws OWLOntologyCreationException, IOException, ProofGenerationFailedException, EntityCheckerException {

		OWLAxiom axiom = oWLTools.getOWLAxiomFromStr(axiomStr, this.ontology);
		return getProofs(axiom, fileName, outDirStr, mode, type, signature, generatePNG, translateAxioms2NL);
	}

	/**
	 * Generates proofs for the given axiom and specified parameters.
	 *
	 * @param conclusion The OWLAxiom for which proofs are generated.
	 * @param fileName The base name of the output file.
	 * @param outDirStr The directory where output files will be saved.
	 * @param mode The output type (e.g., Text, Graph, JSON).
	 * @param type The type of proof to generate (e.g., MinimalSizeGraph, TreeProof).
	 * @param signature The collection of OWL entities used in the proof.
	 * @param generatePNG Flag to determine if PNG images should be generated.
	 * @param translateAxioms2NL Flag to determine if axioms should be translated to natural language.
	 * @return ExitCode indicating the success or failure of the operation.
	 * @throws OWLOntologyCreationException If the ontology creation fails.
	 * @throws IOException If an I/O error occurs.
	 * @throws ProofGenerationFailedException If proof generation fails.
	 */
	@SuppressWarnings("unchecked")
	public ExitCode getProofs(OWLAxiom conclusion, String fileName, String outDirStr, OutputType mode, ProofType type,
							  Collection<OWLEntity> signature, boolean generatePNG, boolean translateAxioms2NL)
			throws OWLOntologyCreationException, IOException, ProofGenerationFailedException {

		ELKProofGenerator elkPG = new ELKProofGenerator(this.ontology);

		if (!AxiomChecker.isInEL(conclusion)) {
			logger.info("Axiom type is not supported by " + ELKProofGenerator.class.getSimpleName());
			return ExitCode.NotSupportedAxiom;
		}

		String fileExtension = jsonFileExtension;

		if (elkPG.getReasoner().isEntailed(conclusion))
			logger.info("IS " + "\"" + SimpleOWLFormatter.format(conclusion) + "\"" + " ENTAILED -> TRUE");
		else {
			logger.info("IS " + "\"" + SimpleOWLFormatter.format(conclusion) + "\"" + " ENTAILED -> FALSE");
			return ExitCode.NotEntailed;
		}

		logger.info("Generating proof(s)");

		elkPG.resetExploredAxiomsSet();

		DynamicProof<ElkOwlInference> proof = ElkOwlProof.create(elkPG.getReasoner(), conclusion);

		if (outDirStr.isEmpty())
			outDirStr = proofDirectory;

		Files.createDirectories(Paths.get(outDirStr));

		Instant start, finish;
		Collection<IProof<OWLAxiom>> proofsCollection;

		start = Instant.now();

		if (OutputType.isRecursive(mode)) {
			proofsCollection = elkPG.getRecursiveProofs(conclusion, proof);
		} else {
			if (type == ProofType.MinimalSizeGraph)
				proofsCollection = Sets.newHashSet(elkPG.getMinimalHyperProof(conclusion, proof));
			else
				proofsCollection = Sets.newHashSet(elkPG.getTreeProof(conclusion, type, signature));
		}

		finish = Instant.now();

		logger.info(GeneralTools.getDuration(start, finish));

		if (mode != OutputType.Graph) {
			if (mode == OutputType.RecursiveJSON || mode == OutputType.NonRecursiveJSON) {
				if (proofsCollection.size() > 1)
					new JsonProofWriter<OWLAxiom>().writeToFile(proofsCollection,
							outDirStr + File.separator + fileName);
				else
					new JsonProofWriter<OWLAxiom>().writeToFile(proofsCollection.iterator().next(),
							outDirStr + File.separator + fileName);
			} else if (mode == OutputType.Text) {
				SerialisedProofWriter.getInstance().writeSerializedProofs(conclusion, proofsCollection,
						new File(outDirStr + File.separator + fileName + dlFileExtension));

				fileExtension = dlFileExtension;
			}

			logger.info("Proof file -> \"" + System.getProperty("user.dir") + File.separator + outDirStr
					+ File.separator + fileName + fileExtension);

		} else {
			logger.info("Generating proof graph");

			if (generatePNG) {
				start = Instant.now();

				// TODO fix me, remove the set
				DotGraphGenerator.drawProofGraph(proofsCollection.iterator().next(),
						outDirStr + File.separator + fileName);

				finish = Instant.now();

				logger.info(GeneralTools.getDuration(start, finish));

				logger.info("Graph file -> \"" + System.getProperty("user.dir") + File.separator + fileName
						+ proofFileExtension + graphFileExtension);
			} else
				logger.info("Skipping PNG!");

			try {
				GraphMLGenerator.setOntology(this.ontology);
				GraphMLGenerator.drawProofs(proofsCollection,
						System.getProperty("user.dir") + File.separator + outDirStr + File.separator + fileName,
						translateAxioms2NL);

				logger.info("GraphML file(s) were created!");

			} catch (ParserConfigurationException | TransformerException e) {
				logger.error("Failed to generate GraphML files!");
				e.printStackTrace();
			}

		}

		return ExitCode.Entailed;
	}
}
