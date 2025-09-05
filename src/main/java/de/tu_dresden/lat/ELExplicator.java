package de.tu_dresden.lat;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;

import com.google.common.collect.Sets;
import de.tu_dresden.inf.lat.counterExample.data.ModelFormat;
import de.tu_dresden.inf.lat.counterExample.data.ModelType;
import de.tu_dresden.inf.lat.evee.general.data.exceptions.ModelGenerationException;
import de.tu_dresden.inf.lat.evee.proofs.data.exceptions.ProofGenerationException;
import de.tu_dresden.inf.lat.exceptions.EntityCheckerException;
import de.tu_dresden.inf.lat.model.interfaces.IModelGenerator;
import de.tu_dresden.inf.lat.model.interfaces.IProverGenerator;
import de.tu_dresden.inf.lat.model.tools.ToOWLTools;
import de.tu_dresden.lat.data.cli.CLIOptionsDefaultValues;
import de.tu_dresden.lat.data.cli.CLIOptionsStrings;
import de.tu_dresden.lat.data.cli.CLIOptions;
import de.tu_dresden.lat.data.names.ConcreteDomainName;
import de.tu_dresden.lat.data.names.ReasonerName;
import de.tu_dresden.lat.evonne.EvonneCDProofGenerator;
import de.tu_dresden.lat.metTelCounterModel.MetTelModelGenerator;
import de.tu_dresden.lat.metTelCounterModel.MetTelProverGenerator;
import de.tu_dresden.lat.tools.Helper;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;

import de.tu_dresden.inf.lat.evee.data.ProofType;
import de.tu_dresden.lat.api.ElExplicatorApplication;
import de.tu_dresden.lat.atomicDecomposition.AtomicDecompositionGenerator;
import de.tu_dresden.lat.data.enums.ExitCode;
import de.tu_dresden.lat.data.enums.OutputType;
import de.tu_dresden.lat.data.enums.SortMethod;
import de.tu_dresden.lat.diagnoses.ASPMinimalDiagnoses;
import de.tu_dresden.lat.diagnoses.ComputeRepair;
import de.tu_dresden.lat.diagnoses.HelperFunctions;
import de.tu_dresden.lat.managers.MyELKModelManager;
import de.tu_dresden.lat.managers.MyELkProofManager;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

/**
 * @author Christian Alrabbaa
 *
 */
public class ELExplicator {

	private static final CLIOptions myOpts = CLIOptions.getInstance();
	public static boolean keep = false,
			genPNG = true,
			translateAxioms = true,
			exportMapper = false;

	public static void main(String[] args) throws OWLOntologyCreationException, IOException, ProofGenerationException, EntityCheckerException, ParserConfigurationException, TransformerException, OWLOntologyStorageException {
		System.setOut(new PrintStream(System.out, true, "UTF-8")); // Set the output stream to UTF-8

		Options options = new Options();

		options.addOption(myOpts.ontologyPathOptionREQUIRED);

		options.addOption(myOpts.conclusionAxiomOptionREQUIRED);

		options.addOption(myOpts.constraintsPathOption);

		options.addOption(myOpts.outFileLabelOption);

		options.addOption(myOpts.outDirOption);

		options.addOption(myOpts.keepGeneratedStuffOption);

		options.addOption(myOpts.proofOutTypeOption);

		options.addOption(myOpts.proofTypeOption);

		options.addOption(myOpts.modelTypeOption);

		options.addOption(myOpts.modelFormatOption);

		options.addOption(myOpts.concreteDomainNameOption);

		options.addOption(myOpts.reasonerNameOption);

		options.addOption(myOpts.exampleReasonerOption);

		options.addOption(myOpts.mDsOption);

		options.addOption(myOpts.adOption);

		options.addOption(myOpts.moduleOption);

		options.addOption(myOpts.signatureFilePathOption);

		options.addOption(myOpts.generatePNGOption);

		options.addOption(myOpts.translateAxiom2NLOption);

		options.addOption(myOpts.exportMapperOption);

		options.addOption(myOpts.diagnosisOption);

		options.addOption(myOpts.repairOption);

		options.addOption(myOpts.interestingAxiomOption);

		options.addOption(myOpts.liveSortOption);

		options.addOption(myOpts.sortMethodOption);

		CommandLine cmd = null;

		try {
			cmd = myOpts.parser.parse(options, args);
		} catch (ParseException e) {
			System.err.println(e.getMessage());
			myOpts.formatter.printHelp("utility-name", options);

			System.exit(1);
		}

		String ontologyPathStr = cmd.getOptionValue(CLIOptionsStrings.ontologyPathOptionLong);

		String constraintsPathStr = cmd.getOptionValue(CLIOptionsStrings.constraintsPathOptionLong,
				CLIOptionsDefaultValues.defaultConstraintsFilePathStr);

		String conclusionAxiomStr = cmd.getOptionValue(CLIOptionsStrings.conclusionAxiomOptionLong);

		String outputFileNameStr = cmd.getOptionValue(CLIOptionsStrings.outFileLabelOptionLong,
				CLIOptionsDefaultValues.defaultOutputFileNameStr);

		String outTypeStr = cmd.getOptionValue(CLIOptionsStrings.proofOutTypeOptionLong,
				CLIOptionsDefaultValues.defaultOutputTypeStr);

		String proofTypeStr = cmd.getOptionValue(CLIOptionsStrings.proofTypeOptionLong,
				CLIOptionsDefaultValues.defaultProofTypeStr);

		String modelTypeStr = cmd.getOptionValue(CLIOptionsStrings.modelTypeOptionLong, CLIOptionsDefaultValues.defaultModelTypeStr);

		String modelFormatStr = cmd.getOptionValue(CLIOptionsStrings.modelFormatOptionLong,
				CLIOptionsDefaultValues.defaultModelFormatStr);

		String outDirStr = cmd.getOptionValue(CLIOptionsStrings.outDirOptionLong,
				CLIOptionsDefaultValues.defaultOutputDirectoryStr);

		String concreteDomainNameStr = cmd.getOptionValue(CLIOptionsStrings.concreteDomainNameOptionLong);

		String reasonerNameStr = cmd.getOptionValue(CLIOptionsStrings.reasonerNameOptionLong,
				CLIOptionsDefaultValues.defaultReasonerStr);

		String sortMethodStr = cmd.getOptionValue(CLIOptionsStrings.sortMethodOptionLong, 
				CLIOptionsDefaultValues.defaultSortMethodOptionStr);

		if (cmd.hasOption(CLIOptionsStrings.keepGeneratedStuffOptionShort))
			keep = true;

		if (cmd.hasOption(CLIOptionsStrings.generatePNGOptionShort))
			genPNG = false;

		if (cmd.hasOption(CLIOptionsStrings.translateAxiom2NLOptionShort))
			translateAxioms = false;

		if (cmd.hasOption(CLIOptionsStrings.exportMapperOptionShort))
			exportMapper = true;

		Files.createDirectories(Paths.get(outDirStr));

		OWLOntology ontology = OWLManager.createOWLOntologyManager()
				.loadOntologyFromOntologyDocument(new File(ontologyPathStr));

		OWLAxiom axiom = ToOWLTools.getInstance().getOWLAxiomFromStr(conclusionAxiomStr, ontology);

		if (cmd.hasOption(CLIOptionsStrings.adOptionShort)) {
			AtomicDecompositionGenerator.getInstance().getADOfModuleOf(ontology, axiom, outDirStr, outputFileNameStr,
					false);
			return;
		}

		if (cmd.hasOption(CLIOptionsStrings.moduleOptionShort)) {
			AtomicDecompositionGenerator.getInstance().getADOfModuleOf(ontology, axiom, outDirStr, outputFileNameStr,
					true);
			return;
		}

		if(cmd.hasOption(CLIOptionsStrings.concreteDomainNameOptionShort)){
			ConcreteDomainName cdn = ConcreteDomainName.getConcreteDomainName(concreteDomainNameStr);
			ReasonerName rn = ReasonerName.getReasonerName(reasonerNameStr);

			EvonneCDProofGenerator.getCDExplanation(ontologyPathStr, constraintsPathStr, rn, cdn, axiom, outDirStr,
					outputFileNameStr);

			return;
		}

		if (cmd.hasOption(CLIOptionsStrings.mDsOptionShort)) {
			String[] mdsArgs = cmd.getOptionValues(CLIOptionsStrings.mDsOptionLong);

			ReasonerName reasonerName = Helper.getReasonerName(mdsArgs);
			String mdsID = Helper.getMDsID(mdsArgs);

			ExitCode ecode = ExitCode.terminatedSuccessfully;
			try {

				ecode = ASPMinimalDiagnoses.getAllMinimalDiagnoses(axiom, ontology, mdsID, outDirStr, Sets.newHashSet(),
						reasonerName);
			
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			System.exit(ecode.getValue());
		}

		if (cmd.hasOption(CLIOptionsStrings.diagnosisOptionShort)){
			String[] diagnosisArgs = cmd.getOptionValues(CLIOptionsStrings.diagnosisOptionLong);
			ReasonerName reasonerName = Helper.getReasonerName(diagnosisArgs);
			String dID = Helper.getMDsID(diagnosisArgs);
			ExitCode ecode = ExitCode.terminatedSuccessfully;
			
			
			try {
				ecode = ASPMinimalDiagnoses.parseUserInteraction(axiom, ontology, dID, outDirStr, reasonerName, ontologyPathStr);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			System.exit(ecode.getValue());
		}

		
		if (cmd.hasOption(CLIOptionsStrings.repairOptionShort)){
			String[] repairArgs = cmd.getOptionValues(CLIOptionsStrings.repairOptionLong);
			ReasonerName reasonerName = Helper.getReasonerName(repairArgs);

			if (!HelperFunctions.reasonerAxiomTypeCheck(reasonerName, axiom)){
				System.err.println("The selected reasoner " + reasonerName + " does not support the given axiom type.");
				System.exit(ExitCode.NotSupportedAxiom.getValue());
			}

			String axiomsPath = cmd.getOptionValue(CLIOptionsStrings.interestingAxiomOptionLong);
			OWLOntology axiomsOntology = OWLManager.createOWLOntologyManager()
				.loadOntologyFromOntologyDocument(new File(axiomsPath));

			if(!HelperFunctions.reasonerOntologyAxiomTypeCheck(reasonerName, axiomsOntology)){
				System.err.println("The selected reasoner " + reasonerName + " does not support the axiom types of the given interesting axioms.");
				System.exit(ExitCode.NotSupportedAxiom.getValue());
			}
			SortMethod sortMethod = SortMethod.getSortMethod(sortMethodStr);
			Boolean liveSort = cmd.hasOption(CLIOptionsStrings.liveSortOptionShort);
			ExitCode ecode = ExitCode.terminatedSuccessfully;
			try{
				ecode = ComputeRepair.computeRepairOntology(axiom, ontology, axiomsOntology, reasonerName, outDirStr, ontologyPathStr, sortMethod, liveSort);	
			} catch (Exception e){
				e.printStackTrace();
			}		

			System.exit(ecode.getValue());	
		}
		
		Collection<OWLEntity> signature = null;
		if (cmd.hasOption(CLIOptionsStrings.signatureFilePathOptionShort)) {
			File sigFile = new File(cmd.getOptionValue(CLIOptionsStrings.signatureFilePathOptionLong));
			if (sigFile.exists())
				signature = ToOWLTools.getInstance().getOWLEntityFromFile(sigFile, ontology);
		}

		ExitCode eCode;

		MyELkProofManager elkProofManager = new MyELkProofManager(ontology);

		eCode = elkProofManager.getProofs(conclusionAxiomStr, outputFileNameStr, outDirStr,
				OutputType.getTypeValue(outTypeStr), ProofType.getTypeValue(proofTypeStr), signature, genPNG,
				translateAxioms);

		final IModelGenerator meTModelGenerator = MetTelModelGenerator.getInstance();
		final IProverGenerator metProverGenerator = MetTelProverGenerator.getInstance();

		if (eCode == ExitCode.NotEntailed) {
			try {

				if (cmd.hasOption(CLIOptionsStrings.exampleReasonerOptionShort)) {

					metProverGenerator.generateProver(ontology, axiom);
					meTModelGenerator.getCounterModel(ontology, axiom, outputFileNameStr);
				}

				else {
					MyELKModelManager elkModelManager = new MyELKModelManager(ontology);

					elkModelManager.getModel(axiom, outputFileNameStr, outDirStr, OutputType.getTypeValue(outTypeStr)
							, ModelType.getTypeValue(modelTypeStr), ModelFormat.getTypeValue(modelFormatStr), genPNG,
							exportMapper);
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (ModelGenerationException | EntityCheckerException e) {
				throw new RuntimeException(e);
			}
		} else if (eCode == ExitCode.NotSupportedAxiom)
			System.exit(ExitCode.NotSupportedAxiom.getValue());
	}

}
