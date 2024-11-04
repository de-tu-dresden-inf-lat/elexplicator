package de.tu_dresden.lat.diagnoses;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.StringJoiner;

import de.tu_dresden.inf.lat.model.tools.GeneralTools;
import de.tu_dresden.inf.lat.prettyPrinting.formatting.SimpleDLFormatter$;
import de.tu_dresden.inf.lat.prettyPrinting.formatting.SimpleOWLFormatterCl;
import de.tu_dresden.inf.lat.model.tools.ToOWLTools;
import de.tu_dresden.lat.data.names.ReasonerName;
import org.apache.log4j.Logger;
import org.omg.CORBA.SystemException;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLOntology;

import de.tu_dresden.inf.lat.prettyPrinting.formatting.SimpleOWLFormatter;
import de.tu_dresden.lat.data.enums.ExitCode;
import de.tu_dresden.lat.tools.AxiomChecker;
import scala.util.parsing.json.JSONObject;

/**
 * @author Christian Alrabbaa
 *
 */
public class ASPMinimalDiagnoses {

	private static final Logger logger = Logger.getLogger(ASPMinimalDiagnoses.class);

	private static Map<OWLAxiom, String> axioms2Identifiers;
	private static Map<String, OWLAxiom> identifiers2Axioms;
	private static final String axiomPrefix = "alpha";
	private static final String programFileName = "pi.txt";
	private static final String INCAPath = "externalTools" + File.separator + "ASP_Min" + File.separator + "inca"
			+ File.separator + "incaMDs.py";
	// private static final String DiagPath = "externalTools" + File.separator + "ASP_Min" + File.separator + "inca"
	// 		+ File.separator + "diag.py";
	private static final String NavPath = "externalTools" + File.separator + "ASP_Min" + File.separator + "inca"
			+ File.separator + "diagnosisNav.py";

	// Added this to have a SimpleOWLFormatterCL that can format using preferred labels.
	// Need to use setOntology first.
	private static SimpleOWLFormatterCl sOWLFormatter = new SimpleOWLFormatterCl(true, SimpleDLFormatter$.MODULE$,
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
		runProgram(mDsID, outDirStr, true, false, false, Optional.empty());
		allOptimalDiagnoses.addAll(returnResult(mDsID, outDirStr));

		logger.info("Generating output file");
		saveResult(allOptimalDiagnoses, mDsID, outDirStr);

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

	private static void saveResult(Set<Set<? extends OWLAxiom>> allOptimalDiagnoses, String mDsID,
			String outDirStr) throws IOException {
		StringJoiner oneDiagnosis, allDiagnoses = new StringJoiner("\n");

		String columnsNames = getColumnsNames(allOptimalDiagnoses);
		allDiagnoses.add(columnsNames);

		for (Set<? extends OWLAxiom> diagnosis : allOptimalDiagnoses) {
			oneDiagnosis = new StringJoiner("; ");
			for (OWLAxiom axiom : diagnosis)
				oneDiagnosis.add(sOWLFormatter.format(axiom).replaceAll("\"",""));

			allDiagnoses.add(oneDiagnosis.toString());
		}

		saveText(allDiagnoses.toString(), getMDSFilePathStr(outDirStr, mDsID));
	}

	private static String getColumnsNames(Set<Set<? extends OWLAxiom>> allOptimalDiagnoses) {
		int maxSize = 0;
		for (Set<? extends OWLAxiom> diagnosis : allOptimalDiagnoses) {
			if (diagnosis.size() > maxSize)
				maxSize = diagnosis.size();
		}

		StringJoiner columnsNames = new StringJoiner("; ");
		for (int i = 0; i < maxSize; i++)
			columnsNames.add("axiom" + i);

		return columnsNames.toString();
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

		saveText(program.toString(), outDirStr + File.separator + programFileName);
	}

	private static void saveText(String str, String filePath) throws IOException {
		
		File file = GeneralTools.createFile(filePath);

		FileOutputStream outStream = new FileOutputStream(file);

		OutputStreamWriter writer = new OutputStreamWriter(outStream, StandardCharsets.UTF_8);

		try {
			writer.write(str);
		} catch (Exception e) {
			logger.error("Failed to write to -> " + filePath);
		} finally {
			writer.close();
			logger.info("Done writing to -> " + filePath);
		}
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

	private static void runProgram(String mDsID, String outDirStr, Boolean minDiag, Boolean facetDiag, Boolean firstRun, Optional<String> facetIdentifier) throws IOException {
		String argsOpt = "";
		if (minDiag){
			argsOpt = argsOpt + " -md";
		}
		if (facetDiag){
			argsOpt = argsOpt + " -fd";
		}
		if (firstRun){
			argsOpt = argsOpt + " -fr";
		}
		if (facetIdentifier.isPresent()){
			argsOpt = argsOpt + " -facet \"" + facetIdentifier.get().toString() +"\"";
			System.out.println(facetIdentifier.get());
		}
		System.out.println(argsOpt);
		Process p;
		int tc = -1;
		
		try {
			if (System.getProperty("os.name").toLowerCase().contains("windows")) {
				p = Runtime.getRuntime()
						.exec("py " + INCAPath + " -f " + outDirStr + File.separator + programFileName + " -m "
								+ (identifiers2Axioms.keySet().size() - 1) + " -out "
								+ getMDSFilePathStr(outDirStr, mDsID) + argsOpt);
				BufferedReader ErrorReader = new BufferedReader(new InputStreamReader(p.getErrorStream()));
				while (ErrorReader.readLine() != null){
					System.out.println(ErrorReader.readLine());
				}
				BufferedReader Outputreader = new BufferedReader(new InputStreamReader(p.getInputStream()));
				BufferedReader erreader = new BufferedReader(new InputStreamReader(p.getErrorStream()));
				System.out.println(erreader.readLine());
				StringBuilder output = new StringBuilder();
				String line;
				while ((line = Outputreader.readLine()) != null) {
					output.append(line);
				}
				System.out.println(output);
				tc = p.waitFor();
			} else {
				p = Runtime.getRuntime()
						.exec("python3 " + INCAPath + " -f " + outDirStr + File.separator + programFileName + " -m "
								+ (identifiers2Axioms.keySet().size() - 1) + " -out "
								+ getMDSFilePathStr(outDirStr, mDsID) + argsOpt);
				tc = p.waitFor();
			}
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
			System.out.println("tc = " + tc);
		}
	}

	private static Set<Set<? extends OWLAxiom>> returnResult(String mDsID, String outDirStr) throws IOException {
		Set<Set<? extends OWLAxiom>> allDiagnoses = new HashSet<>();
		Set<OWLAxiom> diagnosis;

		Path path = Paths.get(getMDSFilePathStr(outDirStr, mDsID));
		Scanner scanner = new Scanner(path);
		while (scanner.hasNextLine()) {
			diagnosis = new HashSet<>();

			String line = scanner.nextLine();
			for (String id : line.split(","))
				
				diagnosis.add(identifiers2Axioms.get(axiomPrefix + id.trim()));
			allDiagnoses.add(diagnosis);

		}

		scanner.close();
		return allDiagnoses;
	}
	private static List<String> returnDeepInv(String outFile) throws IOException {
		List<String> facets = new ArrayList<>();

		Path path = Paths.get(outFile);
		Scanner scanner = new Scanner(path);
		while (scanner.hasNextLine()) {		

			String line = scanner.nextLine().trim();
			if(!line.isEmpty()){
				if (line.equals("Selection:")){
					facets.add("Selection:");
				}
				else if(line.equals("Dependency:")){
					facets.add("Dependency:");
				}
				else if(line.length() >= 4 && line.startsWith("not ")){
					String id = line.substring(4).trim();
					OWLAxiom axiom = identifiers2Axioms.get(id);
					String simplifiedAxiom = SimpleOWLFormatter.format(axiom);
					facets.add("not "+ id +  ": not "+simplifiedAxiom.toString());
				}else{
					String id = line.trim();
					OWLAxiom axiom = identifiers2Axioms.get(id);
					String simplifiedAxiom = SimpleOWLFormatter.format(axiom);
					facets.add(id + ": " + simplifiedAxiom.toString());
				}
			}		
		}
		scanner.close();
		return facets;
	}


	private static List<String> returnFacets(String outFile) throws IOException {
		List<String> facets = new ArrayList<>();

		Path path = Paths.get(outFile);
		Scanner scanner = new Scanner(path);
		while (scanner.hasNextLine()) {		

			String line = scanner.nextLine().trim();
			if(!line.isEmpty()){
				if (line.equals("Available facets")){
					facets.add("Available facets");
				}
				else if(line.equals("Unavailable facets")){
					facets.add("Unavailable facets");
				}
				else if(line.equals("Chosen facets")){
					facets.add("Chosen facets");
				}
				else if (line.length() >= 4 && line.startsWith("not ")){
					String id = line.substring(line.indexOf('(')+1, line.indexOf(')'));
					OWLAxiom axiom = identifiers2Axioms.get(axiomPrefix + id.trim());
					String simplifiedAxiom = SimpleOWLFormatter.format(axiom);
					facets.add("not "+ axiomPrefix+id.trim() +  ": not "+simplifiedAxiom.toString());
				} 
				else{
					String id = line.substring(line.indexOf('(')+1, line.indexOf(')'));
					OWLAxiom axiom = identifiers2Axioms.get(axiomPrefix + id.trim());
					String simplifiedAxiom = SimpleOWLFormatter.format(axiom);
					facets.add(axiomPrefix + id.trim() + ": " + simplifiedAxiom.toString());
				}
			}		
		}
		scanner.close();
		return facets;
	}

	private static void displayFacets(List<String> allFacets, String fileName) throws IOException {
		System.out.println("List of Available Facets:");
		
		StringJoiner facets= new StringJoiner("\n");

		for (String f : allFacets){
			facets.add(f);
		}

		File file = GeneralTools.createFile(fileName);

		FileOutputStream outStream = new FileOutputStream(file);

		OutputStreamWriter writer = new OutputStreamWriter(outStream, StandardCharsets.UTF_8);
		try {
			writer.write(facets.toString());
		} catch (Exception e) {
			logger.error("Failed to write");
		} finally {
			writer.close();
			logger.info("Done writing ");
		}
	}

	public static ExitCode reactivateFunction(String dID, String outDirStr, String facetIdentifiers) throws IOException, InterruptedException {
		// get the identifier of the facet, send to the minimaldiag py file via incamds.py, different function in minimaldiag will be invoked corresponding to reactivate function
		Process p;
		int tc = -1;
		try {
			if (System.getProperty("os.name").toLowerCase().contains("windows")) {
				p = Runtime.getRuntime()
						.exec("py " + NavPath + " -path " + outDirStr + File.separator + programFileName + " -reactivate \"" + facetIdentifiers + "\"");
				BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
				BufferedReader erreader = new BufferedReader(new InputStreamReader(p.getErrorStream()));
				StringBuilder output = new StringBuilder();
				String line;
				while ((line = reader.readLine()) != null) {
					output.append(line).append("\n");
				}
				System.out.println(output);
				tc = p.waitFor();
				if ((erreader.readLine())!= null){
					System.out.println(erreader.readLine());
				}
			} else {
				p = Runtime.getRuntime()
						.exec("python3 " + NavPath + " -path " + outDirStr + File.separator + programFileName + " -reactivate \"" + facetIdentifiers + "\"");
				tc = p.waitFor();
			}
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
			System.out.println("tc = " + tc);
		}

		return ExitCode.terminatedSuccessfully;
	}


	public static ExitCode getImpact(String dID, String outDirStr, String facetIdentifiers) throws IOException, InterruptedException {
		// get the identifier of the facet, send to the minimaldiag py file via incamds.py,  different function in minimaldiag will be invoked corresponding to impact function
		Process p;
		int tc = -1;
		try {
			if (System.getProperty("os.name").toLowerCase().contains("windows")) {
				p = Runtime.getRuntime()
						.exec("py " + NavPath + " -path " + outDirStr + File.separator + programFileName + " -impact \"" + facetIdentifiers + "\"");
				BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
				BufferedReader erreader = new BufferedReader(new InputStreamReader(p.getErrorStream()));
				StringBuilder output = new StringBuilder();
				String line;
				while ((line = reader.readLine()) != null) {
					output.append(line).append("\n");
				}
				System.out.println(output);
				tc = p.waitFor();
				if ((erreader.readLine())!= null){
					System.out.println(erreader.readLine());
				}
			} else {
				p = Runtime.getRuntime()
						.exec("python3 " + NavPath + " -path " + outDirStr + File.separator + programFileName + " -impact \"" + facetIdentifiers + "\"");
				tc = p.waitFor();
			}
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
			System.out.println("tc = " + tc);
		}
		return ExitCode.terminatedSuccessfully;
	}

	private static String getMDSFilePathStr(String outDir, String mDsID) {
		String fileName = mDsID.isEmpty() ? "mDs.txt" : "mDs_" + mDsID + ".txt";
		return outDir + File.separator + fileName;
	}

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
		runProgram(mDsID, outDirStr, false, true, firstRun, Optional.empty());
		allOptimalDiagnoses.addAll(returnResult(mDsID, outDirStr));
		displayFacets(returnFacets("facets_options.txt"), "facets_options.txt");

		logger.info("Generating output file");
		saveResult(allOptimalDiagnoses, mDsID, outDirStr);

		return ExitCode.terminatedSuccessfully;
	}

	public static ExitCode applyFacet(String dID, String outDirStr, String facetIdentifier) throws IOException, InterruptedException {
		
		Process p;
		int tc = -1;
		try {
			if (System.getProperty("os.name").toLowerCase().contains("windows")) {
				p = Runtime.getRuntime()
						.exec("py " + NavPath + " -path " + outDirStr + File.separator + programFileName + " -facet \"" + facetIdentifier + "\"");
				BufferedReader reader = new BufferedReader(new InputStreamReader(p.getErrorStream()));
				StringBuilder output = new StringBuilder();
				String line;
				while ((line = reader.readLine()) != null) {
					output.append(line).append("\n");
				}
				System.out.println(output);
				tc = p.waitFor();
			} else {
				p = Runtime.getRuntime()
						.exec("python3 " + NavPath + " -path " + outDirStr + File.separator + programFileName + " -facet \"" + facetIdentifier + "\"");
				tc = p.waitFor();
			}
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
			System.out.println("tc = " + tc);
		}
		logger.info("Extracting All Minimal Classical Diagnoses");
		runProgram(dID, outDirStr, false, true, false, Optional.of(facetIdentifier));
		Set allOptimalDiagnoses = new HashSet<>();
		allOptimalDiagnoses.addAll(returnResult(dID, outDirStr));
		displayFacets(returnFacets("facets_options.txt"), "facets_options.txt");
		if (Files.exists(Paths.get("deep_investigation.txt"))){
			displayFacets(returnDeepInv("deep_investigation.txt"), "deep_investigation_log.txt");
		}	

		logger.info("Generating output file");
		saveResult(allOptimalDiagnoses, dID, outDirStr);
		return ExitCode.terminatedSuccessfully;


	}

	

}

