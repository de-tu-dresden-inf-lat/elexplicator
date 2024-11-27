package de.tu_dresden.lat.diagnoses;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.StringJoiner;

import org.apache.log4j.Logger;
import org.semanticweb.HermiT.ReasonerFactory;
import org.semanticweb.elk.owlapi.ElkReasoner;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;

import de.tu_dresden.inf.lat.model.tools.GeneralTools;
import de.tu_dresden.inf.lat.prettyPrinting.formatting.SimpleOWLFormatter;
import de.tu_dresden.inf.lat.prettyPrinting.formatting.SimpleOWLFormatterCl;
import de.tu_dresden.lat.data.names.ReasonerName;

public class HelperFunctions {
    public static Set<Set<OWLAxiom>> currentDiagnoses = new HashSet<Set<OWLAxiom>>();

    private static Map<OWLAxiom, String> axioms2Identifiers = ASPMinimalDiagnoses.axioms2Identifiers;
	private static Map<String, OWLAxiom> identifiers2Axioms = ASPMinimalDiagnoses.identifiers2Axioms;
    private static final String axiomPrefix = ASPMinimalDiagnoses.axiomPrefix;

    private static final Logger logger = ASPMinimalDiagnoses.logger;

    private static final String INCAPath = "externalTools" + File.separator + "ASP_Min" + File.separator + "inca"
        + File.separator + "incaMDs.py";

    private static final String programFileName = ASPMinimalDiagnoses.programFileName;

	private static SimpleOWLFormatterCl sOWLFormatter = ASPMinimalDiagnoses.sOWLFormatter;

    public static String getValidFacets(String inputString){
		String[] inputStrings = inputString.split("/");
		StringBuilder facetsStr = new StringBuilder();
		for (String s : inputStrings){
			String id = s;
			if (s.contains("not")){
				int lastNotIndex = s.lastIndexOf("not ");
				id = s.substring(lastNotIndex + 4).trim();
			}
			try{
				identifiers2Axioms.get(id).toString();
				facetsStr.append(s+'/');
			} catch (NullPointerException e){
				System.out.printf("\033[1;31m%1s is an invalid option\n\033[0m", s);
			}
		} 
		return facetsStr.toString();
	}
	
	public static void displayWarning(String outFile) throws IOException {
				Path path = Paths.get(outFile);
				Scanner scanner = new Scanner(path);
				System.out.println("\033[1;36mDependency Information!\033[0m");
				while (scanner.hasNextLine()) {		
					String line = scanner.nextLine().trim();
					if (line.toString().equals("Selection:")){
						System.out.println("\033[1;32mThe selection:\033[0m");
					} else if (line.toString().equals("Dependency:")){
						System.out.println("\033[1;32mAlso applies:\033[0m");
					} else {
						System.out.println(line);
					}
					
				}
	}

    public static Set<Set<String>> createStringSet(Set<Set<? extends OWLAxiom>> axiomsSet) {
        Set<Set<String>> stringSet = new HashSet<>();

        for (Set<? extends OWLAxiom> innerSet : axiomsSet) {
            Set<String> stringInnerSet = new HashSet<>();
            
            for (OWLAxiom axiom : innerSet) {
                if (axiom != null) {
                    stringInnerSet.add(axiom.toString()); 
                }
            }
            
            stringSet.add(stringInnerSet);
        }

        return stringSet;
    }

    public static Boolean checkEntailment(OWLOntology ontology, OWLAxiom axiom, ReasonerName reasonerName){
		if (reasonerName == ReasonerName.Elk){
			ElkReasonerFactory reasonerFactory = new ElkReasonerFactory();
			ElkReasoner reasoner = reasonerFactory.createReasoner(ontology);
			return reasoner.isEntailed(axiom);
		} else {
			OWLReasonerFactory reasonerFactory = new ReasonerFactory();
			OWLReasoner reasoner = reasonerFactory.createReasoner(ontology);
			return reasoner.isEntailed(axiom);
		}		
	}

    public static String getMDSFilePathStr(String outDir, String mDsID) {
		String fileName = mDsID.isEmpty() ? "mDs.txt" : "mDs_" + mDsID + ".txt";
		return outDir + File.separator + fileName;
	}

	public static String getRepairFilePathStr(String outDirStr, String outputFileName){
		return outDirStr + File.separator + outputFileName;
	} 

    public static void storeFacets(List<String> allFacets, String fileName) throws IOException {
		
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

    public static List<String> returnFacets(String outFile) throws IOException {
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

    public static List<String> returnImpacts(String outFile) throws IOException {
		List<String> facets = new ArrayList<>();

		Path path = Paths.get(outFile);
		Scanner scanner = new Scanner(path);
		while (scanner.hasNextLine()) {		

			String line = scanner.nextLine().trim();
			if(!line.isEmpty()){
				switch(line){
					case "Selection:": case "Dependency:": case "Removing:": case "Retracts the facets:": case "To reactivate:": case "Remove:": case "Remove all:": case "Remove combination of:": case "OR":
						facets.add(line);
						break;
					default:
						if(line.length() >= 4 && line.startsWith("not ")){
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
						break;
				}
			}		
		}

		scanner.close();
		return facets;
	}

    public static Set<Set<? extends OWLAxiom>> returnResult(String mDsID, String outDirStr) throws IOException {
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

    public static void runProgram(String mDsID, String outDirStr, Boolean minDiag, Boolean facetDiag, Boolean firstRun, Optional<String> facetIdentifier) throws IOException {
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
		}
		Process p;
		int tc = -1;
		
		try {
			if (System.getProperty("os.name").toLowerCase().contains("windows")) {
				p = Runtime.getRuntime()
						.exec("py " + INCAPath + " -f " + outDirStr + File.separator + programFileName + " -m "
								+ (identifiers2Axioms.keySet().size() - 1) + " -out "
								+ getMDSFilePathStr(outDirStr, mDsID) + argsOpt);
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

    public static void saveText(String str, String filePath) throws IOException {
		
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

    public static void saveResult(Set<Set<? extends OWLAxiom>> allOptimalDiagnoses, String mDsID,
			String outDirStr) throws IOException {
		StringJoiner oneDiagnosis, allDiagnoses = new StringJoiner("\n");
		// StringJoiner oneDiagnosisOWL, allDiagnosesOWL = new StringJoiner("\n");
		currentDiagnoses = new HashSet<Set<OWLAxiom>>();

		String columnsNames = getColumnsNames(allOptimalDiagnoses);
		allDiagnoses.add(columnsNames);

		for (Set<? extends OWLAxiom> diagnosis : allOptimalDiagnoses) {
			oneDiagnosis = new StringJoiner("; ");
			Set<OWLAxiom> diagnosisSet = new HashSet<OWLAxiom>();
			for (OWLAxiom axiom : diagnosis){
				diagnosisSet.add(axiom);
				oneDiagnosis.add(sOWLFormatter.format(axiom).replaceAll("\"",""));
			}
			allDiagnoses.add(oneDiagnosis.toString());
			currentDiagnoses.add(diagnosisSet);
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

}
