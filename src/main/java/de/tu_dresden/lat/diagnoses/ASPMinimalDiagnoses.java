package de.tu_dresden.lat.diagnoses;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.StringJoiner;

import de.tu_dresden.inf.lat.model.tools.GeneralTools;
import de.tu_dresden.inf.lat.prettyPrinting.formatting.SimpleDLFormatter$;
import de.tu_dresden.inf.lat.prettyPrinting.formatting.SimpleOWLFormatterCl;
import de.tu_dresden.lat.data.names.ReasonerName;
import org.apache.log4j.Logger;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLOntology;

import de.tu_dresden.inf.lat.prettyPrinting.formatting.SimpleOWLFormatter;
import de.tu_dresden.lat.data.enums.ExitCode;
import de.tu_dresden.lat.tools.AxiomChecker;

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
		runProgram(mDsID, outDirStr);
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
			program.add(getRule(justification));
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

	private static void runProgram(String mDsID, String outDirStr) {
		Process p;
		int tc = -1;
		try {
			if (System.getProperty("os.name").toLowerCase().contains("windows")) {
				p = Runtime.getRuntime()
						.exec("py " + INCAPath + " -f " + outDirStr + File.separator + programFileName + " -m "
								+ (identifiers2Axioms.keySet().size() - 1) + " -out "
								+ getMDSFilePathStr(outDirStr, mDsID));
				tc = p.waitFor();
			} else {
				p = Runtime.getRuntime()
						.exec("python3 " + INCAPath + " -f " + outDirStr + File.separator + programFileName + " -m "
								+ (identifiers2Axioms.keySet().size() - 1) + " -out "
								+ getMDSFilePathStr(outDirStr, mDsID));
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

	private static String getMDSFilePathStr(String outDir, String mDsID) {
		String fileName = mDsID.isEmpty() ? "mDs.txt" : "mDs_" + mDsID + ".txt";
		return outDir + File.separator + fileName;
	}
}
