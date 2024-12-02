package de.tu_dresden.lat.diagnoses;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;

import de.tu_dresden.inf.lat.exceptions.EntityCheckerException;
import de.tu_dresden.inf.lat.prettyPrinting.formatting.SimpleDLFormatter$;
import de.tu_dresden.inf.lat.prettyPrinting.formatting.SimpleOWLFormatterCl;
import de.tu_dresden.lat.data.names.ReasonerName;
import org.apache.log4j.Logger;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;

import com.google.common.collect.Sets;

import de.tu_dresden.lat.data.enums.ExitCode;
import de.tu_dresden.lat.tools.AxiomChecker;

/**
 * @author Christian Alrabbaa
 *
 */
public class ASPMinimalDiagnoses {

	private static final Logger logger = Logger.getLogger(ASPMinimalDiagnoses.class);

	public static Map<OWLAxiom, String> axioms2Identifiers;
	public static Map<String, OWLAxiom> identifiers2Axioms;
	public static final String axiomPrefix = "alpha";
	public static final String programFileName = "pi.txt";


	// Added this to have a SimpleOWLFormatterCL that can format using preferred labels.
	// Need to use setOntology first.
	public static SimpleOWLFormatterCl sOWLFormatter = new SimpleOWLFormatterCl(true, SimpleDLFormatter$.MODULE$,
			true);

	public static ExitCode getAllMinimalDiagnoses(OWLAxiom axiom, OWLOntology ontology, String mDsID, String outDirStr,
			Set<Set<? extends OWLAxiom>> allOptimalDiagnoses, ReasonerName reasonerName)
			throws IOException, InterruptedException {

		sOWLFormatter.setReferenceOntology(ontology);

		if (!isAxiomSupported(reasonerName, axiom)) {
			logger.info("Axiom is not supported!");
			return ExitCode.NotSupportedAxiom;
		}

		Set<Set<? extends OWLAxiom>> allJustifications = getAllJustifications(reasonerName, axiom, ontology);

		if (!isJustified(allJustifications)) {
			logger.info("No justifications available for the provided statement");
			return ExitCode.NoJustificationsComputed;
		}

		if (outDirStr.isEmpty())
			outDirStr = "defaultMDsFolder";

		fillMap(allJustifications);

		logger.info("Creating Program");
		createProgram(allJustifications, outDirStr);

		logger.info("Extracting All Minimal Classical Diagnoses");
		HelperFunctions.runProgram(mDsID, outDirStr, true, false, false, Optional.empty());
		allOptimalDiagnoses.addAll(HelperFunctions.returnResult(mDsID, outDirStr));

		logger.info("Generating output file");
		HelperFunctions.saveResult(allOptimalDiagnoses, mDsID, outDirStr);

		return ExitCode.terminatedSuccessfully;
	}

	private static Set<Set<? extends OWLAxiom>> getAllJustifications(ReasonerName reasonerName, OWLAxiom axiom,
																	 OWLOntology ontology) {
		if (reasonerName == ReasonerName.Elk)
			return JustificationsGenerator.getAllELKJustifications(axiom, ontology);

		return JustificationsGenerator.getAllHermitJustifications(axiom, ontology);
	}

	private static boolean isAxiomSupported(ReasonerName reasonerName, OWLAxiom axiom) {
		if (reasonerName == ReasonerName.Elk)
			return AxiomChecker.isInEL(axiom);

		return AxiomChecker.isInALC(axiom);
	}

	private static boolean isJustified(Set<Set<? extends OWLAxiom>> allJustifications) {
		if (allJustifications.size() == 1)
			if (allJustifications.iterator().next().isEmpty())
				return false;

		return !allJustifications.isEmpty();
	}

	/**
	 * Map every axiom to an identifier of the form "alpha" + integer
	 * 
	 * @param allJustifications
	 */
	private static void fillMap(Set<Set<? extends OWLAxiom>> allJustifications) {
		axioms2Identifiers = new HashMap<>();
		identifiers2Axioms = new HashMap<>();

		String id;
		int axiomSuffix = 0;

		for (Set<? extends OWLAxiom> justification : allJustifications) {
			for (OWLAxiom axiom : justification) {
				if (!axioms2Identifiers.containsKey(axiom)) {
					id = axiomPrefix + axiomSuffix++;
					axioms2Identifiers.put(axiom, id);
					identifiers2Axioms.put(id, axiom);
				}
			}
		}
	}

	private static void createProgram(Set<Set<? extends OWLAxiom>> allJustifications, String outDirStr)
			throws IOException {
		StringJoiner program = new StringJoiner("\n");

		program.add("%All Justifications");
		allJustifications.forEach(justification -> {
			{
				program.add(getRule(justification));
			}			
		});

		program.add("%Choices");
		program.add(getChoices());

		File outDir = new File(outDirStr);
		if (!outDir.exists())
			throw new IOException("Directory does not exist -> " + outDirStr);

		HelperFunctions.saveText(program.toString(), outDirStr + File.separator + programFileName);
	}

	
	/**
	 * Return a string representing the ASP choice rule of the form {alpha1; ... ;
	 * alpha_i} where each alpha_n is an axiom appearing in some justification
	 * 
	 * @return
	 */
	private static String getChoices() {
		StringJoiner choiceRule = new StringJoiner(";");

		identifiers2Axioms.keySet().forEach(choiceRule::add);

		return "{" + choiceRule + "}.";
	}

	/**
	 * For a given justification {alpha1, alpha2,...} return a string representing
	 * an ASP rule of the form "statement :- alpha1, alpha2, ... ."
	 * 
	 * @param justification
	 * @return
	 */
	private static String getRule(Set<? extends OWLAxiom> justification) {
		String ruleHead = "statement :- ";

		StringJoiner ruleBody = new StringJoiner(",");
		justification.forEach(axiom -> {			
				ruleBody.add(axioms2Identifiers.get(axiom) + "()");
		});

		return ruleHead + ruleBody + ".";
	}	
	
/* 
	* compute and save all the minimal diagnoses (in the first run with no facets applied yet), 
	* store the list of available facets in a text file 
*/
	public static ExitCode getAllDiagnoses(OWLAxiom axiom, OWLOntology ontology, String mDsID, String outDirStr,
			Set<Set<? extends OWLAxiom>> allOptimalDiagnoses, ReasonerName reasonerName, Boolean firstRun)
			throws IOException, InterruptedException {
		if (!isAxiomSupported(reasonerName, axiom)) {
			logger.info("Axiom is not supported!");
			return ExitCode.NotSupportedAxiom;
		}

		Set<Set<? extends OWLAxiom>> allJustifications = getAllJustifications(reasonerName, axiom, ontology);

		if (!isJustified(allJustifications)) {
			logger.info("No justifications available for the provided statement");
			return ExitCode.NoJustificationsComputed;
		}

		if (outDirStr.isEmpty())
			outDirStr = "defaultMDsFolder";

		fillMap(allJustifications);

		logger.info("Creating Program");
		createProgram(allJustifications, outDirStr);

		logger.info("Extracting All Minimal Classical Diagnoses");
		HelperFunctions.runProgram(mDsID, outDirStr, false, true, firstRun, Optional.empty());
		allOptimalDiagnoses.addAll(HelperFunctions.returnResult(mDsID, outDirStr));
		
		HelperFunctions.storeFacets(HelperFunctions.returnFacets(outDirStr + File.separator +"facets_options.txt"), outDirStr + File.separator +"facets_options.txt");

		logger.info("Generating output file");
		HelperFunctions.saveResult(allOptimalDiagnoses, mDsID, outDirStr);
		return ExitCode.terminatedSuccessfully;
	}

	public static Set<Set<String>> getAllDiagnoses(OWLAxiom axiom, OWLOntology ontology, String mDsID, String outDirStr, Set<Set<? extends OWLAxiom>> allOptimalDiagnoses, ReasonerName reasonerName, Boolean firstRun, Boolean unitTest)
			throws IOException, InterruptedException{
		Set<Set<String>> diagnosesSet = new HashSet<>();
		if (!isAxiomSupported(reasonerName, axiom)) {
			logger.info("Axiom is not supported!");
			return diagnosesSet;
		}

		Set<Set<? extends OWLAxiom>> allJustifications = getAllJustifications(reasonerName, axiom, ontology);

		if (!isJustified(allJustifications)) {
			logger.info("No justifications available for the provided statement");
			return diagnosesSet;
		}

		if (outDirStr.isEmpty())
			outDirStr = "defaultMDsFolder";

		fillMap(allJustifications);

		logger.info("Creating Program");
		createProgram(allJustifications, outDirStr);

		logger.info("Extracting All Minimal Classical Diagnoses");
		HelperFunctions.runProgram(mDsID, outDirStr, false, true, firstRun, Optional.empty());
		allOptimalDiagnoses.addAll(HelperFunctions.returnResult(mDsID, outDirStr));
		
		HelperFunctions.storeFacets(HelperFunctions.returnFacets(outDirStr + File.separator +"facets_options.txt"), outDirStr + File.separator +"facets_options.txt");

		logger.info("Generating output file");
		HelperFunctions.saveResult(allOptimalDiagnoses, mDsID, outDirStr);

		diagnosesSet = HelperFunctions.createStringSet(allOptimalDiagnoses);

		return diagnosesSet;
	}

	public static ExitCode parseUserInteraction(OWLAxiom axiom, OWLOntology ontology, String dID, String outDirStr, ReasonerName reasonerName, String ontologyPathStr) throws IOException, InterruptedException, EntityCheckerException, OWLOntologyCreationException, OWLOntologyStorageException{
		ExitCode ecode = ExitCode.terminatedSuccessfully;

		boolean flag = true;
		ecode = ASPMinimalDiagnoses.getAllDiagnoses(axiom, ontology, dID, outDirStr, Sets.newHashSet(),
				reasonerName, true);

		Files.deleteIfExists(Paths.get(outDirStr + File.separator + "added_knowledge.txt"));
		Files.deleteIfExists(Paths.get(outDirStr + File.separator + "deep_investigation.txt"));

		while (flag == true){
			java.util.Scanner scanner = new java.util.Scanner(System.in);
			System.out.println("\033[1;36mType help to list commands:\033[0m");
			String user_in = scanner.nextLine();
			
			if (user_in.equals("exit")){
				flag = false;
				scanner.close();
			}
			else{
				if (!user_in.contains("#impact") && !user_in.contains("#reactivate") && !user_in.contains("#del") && !user_in.contains("delall") && !user_in.contains("save") && !user_in.contains("help")){	
					FacetedNavigation.applyFacet(dID, outDirStr, user_in);		
				}	
				if (user_in.contains("#impact")){
					FacetedNavigation.getImpact(dID, outDirStr, user_in.substring(8));
				}	
				if (user_in.contains("#reactivate")){
					FacetedNavigation.reactivateFunction(dID, outDirStr, user_in.substring(12));
				}
				if (user_in.contains("#del")){
					String del_axiom = user_in.substring(5);
					FacetedNavigation.delete(dID, outDirStr, Optional.of(del_axiom));
				}
				if (user_in.contains("delall")){
					FacetedNavigation.delete(dID, outDirStr, Optional.empty());
				}
				if (user_in.contains("save")){
					String outputFileStr = user_in.substring(5);
					FacetedNavigation.saveRepair(outDirStr, dID, ontologyPathStr, axiom, reasonerName, outputFileStr);
				}

				if (user_in.contains("help")){
					List<List<String>> helpText = new ArrayList<>(
						Arrays.asList(
							new ArrayList<>(Arrays.asList("Apply a nav. step using the identifier of a facet", "ex: alpha0\n")),
							new ArrayList<>(Arrays.asList("Retract a specific facet", "ex: alpha0\n" )),
							new ArrayList<>(Arrays.asList("Show the impact of removing certain facets", "ex: #impact alpha0\n")),
							new ArrayList<>(Arrays.asList("Find all min. correction sets to w.r.t a facet", "ex: #reactivate alpha0\n")),		
							new ArrayList<>(Arrays.asList("Retract all facets", "delall\n")),
							new ArrayList<>(Arrays.asList("Saves the repair w.r.t to the current diagnoses", "save\n")),
							new ArrayList<>(Arrays.asList("Terminate the program", "exit\n\n")),
							new ArrayList<>(Arrays.asList("\033[1;32m*Note* Multiple entries and deletions must be separated by \"/\"\033[0m", "\n\n"))
						)
					);			
					for (List<String> i : helpText){
						System.out.printf("%1$-50s %2$s", i.get(0), i.get(1));
					}
				}								
			}	
		}
		return ecode;
	}
}

