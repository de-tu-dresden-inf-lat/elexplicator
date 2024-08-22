package de.tu_dresden.lat.evonne;

import java.io.File;

import de.tu_dresden.lat.data.cli.CLIOptions;
import de.tu_dresden.lat.data.cli.CLIOptionsDefaultValues;
import de.tu_dresden.lat.data.cli.CLIOptionsStrings;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FileExistsException;
import org.apache.log4j.Logger;
import org.semanticweb.owlapi.model.OWLAxiom;

import de.tu_dresden.inf.lat.evee.proofs.data.exceptions.ProofGenerationFailedException;
import de.tu_dresden.inf.lat.evee.proofs.interfaces.IProof;
import de.tu_dresden.inf.lat.evee.proofs.json.JsonProofParser;
import de.tu_dresden.lat.data.enums.ExitCode;
import de.tu_dresden.lat.exceptions.ProofRwritingException;
import de.tu_dresden.lat.proofRewriting.ProofRewriter;

/**
 * @author Christian Alrabbaa
 *
 */
public class EvonneProofRewritingGenerator {
	private static final Logger logger = Logger.getLogger(EvonneProofRewritingGenerator.class);
	private static final CLIOptions myOpts = CLIOptions.getInstance();

	private static boolean translateAxioms = true;

	public static void main(String[] args) throws ProofGenerationFailedException, FileExistsException {
		Options options = new Options();

		options.addOption(myOpts.patternDirOptionREQUIRED);

		options.addOption(myOpts.inputJsonProofPathOptionREQUIRED);

		options.addOption(myOpts.outDirOptionREQUIRED);

		options.addOption(myOpts.outFileLabelOptionREQUIRED);

		options.addOption(myOpts.translateAxiom2NLOption);

		options.addOption(myOpts.lemmaTitleOption);

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

		String patternPath = cmd.getOptionValue(CLIOptionsStrings.patternDirOptionLong);
		String proofPath = cmd.getOptionValue(CLIOptionsStrings.inputJsonProofPathOptionLong);
		String lemmaTitleStr = cmd.getOptionValue(CLIOptionsStrings.lemmaTitleOptionLong,
				CLIOptionsDefaultValues.defaultLemmaTitle);

		String outputDirectoryPathStr = cmd.getOptionValue(CLIOptionsStrings.outDirOptionLong);
		String outputFilesNameStr = cmd.getOptionValue(CLIOptionsStrings.outFileLabelOptionLong);

		logger.info("NOTE - The JSON parser can only deal with OWLAxioms");

		File proofFile = new File(proofPath);
		File patternFile = new File(patternPath);

		if (!proofFile.exists()) {
			logger.error("Proof File does not exist -> " + proofFile.getPath());
			throw new FileExistsException();
		}

		if (!patternFile.exists()) {
			logger.error("Pattern File does not exist -> " + patternFile.getPath());
			throw new FileExistsException();
		}

		IProof<OWLAxiom> proof = JsonProofParser.getInstance().fromFile(proofFile);
		IProof<OWLAxiom> pattern = JsonProofParser.getInstance().fromFile(patternFile);
		IProof<OWLAxiom> result;

		try {
			result = ProofRewriter.getInstance().rewrite(proof, pattern, lemmaTitleStr);

			EvonneGMLInputGenerator.iProof2GML(result, outputDirectoryPathStr, outputFilesNameStr, translateAxioms);

			System.exit(ExitCode.terminatedSuccessfully.getValue());

		} catch (ProofRwritingException e) {
			logger.error("Failed to rewrite!\n", e);
		}

	}

}
