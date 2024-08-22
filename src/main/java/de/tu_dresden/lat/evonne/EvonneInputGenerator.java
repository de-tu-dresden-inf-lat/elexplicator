package de.tu_dresden.lat.evonne;

import java.io.File;
import java.util.Collection;

import de.tu_dresden.inf.lat.model.tools.ToOWLTools;
import de.tu_dresden.lat.data.cli.CLIOptionsDefaultValues;
import de.tu_dresden.lat.data.cli.CLIOptionsStrings;
import de.tu_dresden.lat.data.cli.CLIOptions;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;

import de.tu_dresden.inf.lat.evee.data.ProofType;
import de.tu_dresden.inf.lat.evee.proofs.data.exceptions.ProofGenerationFailedException;

/**
 * @author Christian Alrabbaa
 *
 */
public class EvonneInputGenerator {

	private static final CLIOptions myOpts = CLIOptions.getInstance();
	private static boolean translateAxioms = true;

	public static void main(String[] args) throws ProofGenerationFailedException {
		Options options = new Options();

		options.addOption(myOpts.inputJsonProofPathOptionREQUIRED);

		options.addOption(myOpts.outDirOptionREQUIRED);

		options.addOption(myOpts.outFileLabelOptionREQUIRED);

		options.addOption(myOpts.proofTypeOptionREQUIRED);

		options.addOption(myOpts.signatureFilePathOption);

		options.addOption(myOpts.ontologyPathOption);

		options.addOption(myOpts.translateAxiom2NLOption);

		CommandLine cmd = null;

		try {
			cmd = myOpts.parser.parse(options, args);
		} catch (ParseException e) {
			System.out.println(e.getMessage());
			myOpts.formatter.printHelp("utility-name", options);

			System.exit(1);
		}

		if (cmd.hasOption(CLIOptionsStrings.translateAxiom2NLOptionShort))
			translateAxioms = false;

		String jsonProofPathStr = cmd.getOptionValue(CLIOptionsStrings.inputJsonProofPathOptionLong);
		String outputDirectoryPathStr = cmd.getOptionValue(CLIOptionsStrings.outDirOptionLong,
				CLIOptionsDefaultValues.defaultOutputDirectoryStr);
		String outputFilesNameStr = cmd.getOptionValue(CLIOptionsStrings.outFileLabelOptionLong,
				CLIOptionsDefaultValues.defaultOutputFileNameStr);

		String ontologyPathStr = cmd.getOptionValue(CLIOptionsStrings.ontologyPathOptionLong);
		String proofTypeStr = cmd.getOptionValue(CLIOptionsStrings.proofTypeOptionLong);

		OWLOntology ontology;
		try {
			ontology = OWLManager
					.createOWLOntologyManager().loadOntologyFromOntologyDocument(new File(ontologyPathStr));
		} catch (OWLOntologyCreationException e) {
			throw new RuntimeException(e);
		}

		Collection<OWLEntity> signature = null;
		if (cmd.hasOption(CLIOptionsStrings.signatureFilePathOptionShort)) {
			File sigFile = new File(cmd.getOptionValue(CLIOptionsStrings.signatureFilePathOptionLong));
			if (sigFile.exists())
				signature = ToOWLTools.getInstance().getOWLEntityFromFile(sigFile, ontology);
		}

		EvonneGMLInputGenerator.jSON2GML(jsonProofPathStr, outputDirectoryPathStr, outputFilesNameStr,
				ProofType.getTypeValue(proofTypeStr), signature, ontology, translateAxioms);
	}

}
