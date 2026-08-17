package de.tu_dresden.lat.diagnoses;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.apache.log4j.Logger;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLDocumentFormat;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;

import de.tu_dresden.inf.lat.exceptions.EntityCheckerException;
import de.tu_dresden.lat.data.enums.ExitCode;
import de.tu_dresden.lat.data.names.ReasonerName;

public class FacetedNavigation {
    private static final String NavPath = "externalTools" + File.separator + "ASP_Min" + File.separator + "inca"
			+ File.separator + "diagnosisNav.py";
    private static final String programFileName = ASPMinimalDiagnoses.programFileName;
    private static final Logger logger = Logger.getLogger(FacetedNavigation.class);

/*
	* handle facet application, computes the new minimal diagnoses and list of facet options.
	* If any dependency found while facet application, notify the user about it (deep_investigation) 
*/
    public static ExitCode applyFacet(String dID, String outDirStr, String facetIdentifier) throws IOException, InterruptedException {

		String facetsStr = HelperFunctions.getValidFacets(facetIdentifier);
		if (facetsStr.length() != 0){
			facetIdentifier = facetsStr.substring(0, facetsStr.length()-1);			
		    Files.deleteIfExists(Paths.get(outDirStr + File.separator + "deep_investigation.txt"));    
        }else{
			return ExitCode.InvalidOption;
		}
		Process p;
		int tc = -1;
		try {
			if (System.getProperty("os.name").toLowerCase().contains("windows")) {
				p = Runtime.getRuntime()
						.exec("py " + NavPath + " -path " + outDirStr + File.separator + programFileName + " -facet \"" + facetIdentifier + "\"");
				tc = p.waitFor();
			} else {
				p = Runtime.getRuntime()
						.exec("python3 " + NavPath + " -path " + outDirStr + File.separator + programFileName + " -facet \"" + facetIdentifier + "\"");
				tc = p.waitFor();
			}			
			
			if(tc != 0){
				BufferedReader erreader = new BufferedReader(new InputStreamReader(p.getErrorStream()));
				StringBuilder errOutput = new StringBuilder();
				String errLine;
				while ((errLine = erreader.readLine())!= null){
					errOutput.append(errLine).append("\n");
				}
			System.out.println(errOutput);
			}
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
			System.out.println("tc = " + tc);
		}
		logger.info("Extracting All Minimal Classical Diagnoses");
		HelperFunctions.runProgram(dID, outDirStr, false, true, false, false, Optional.of(facetIdentifier));
		Set<Set<? extends OWLAxiom>> allOptimalDiagnoses = new HashSet<>();
		allOptimalDiagnoses.addAll(HelperFunctions.returnResult(dID, outDirStr));
		HelperFunctions.storeFacets(HelperFunctions.returnFacets(outDirStr + File.separator +"facets_options.txt"), outDirStr + File.separator + "facets_options.txt");
		if (Files.exists(Paths.get(outDirStr + File.separator + "deep_investigation.txt"))){
			HelperFunctions.storeFacets(HelperFunctions.returnImpacts(outDirStr + File.separator +"deep_investigation.txt"), outDirStr + File.separator +"deep_investigation_log.txt");
			HelperFunctions.displayWarning(outDirStr + File.separator +"deep_investigation_log.txt");
		}	

		
		logger.info("Generating output file");
		HelperFunctions.saveResult(allOptimalDiagnoses, dID, outDirStr);

		return ExitCode.terminatedSuccessfully;
	}

	public static Set<Set<String>> applyFacet(String dID, String outDirStr, String facetIdentifier, Boolean unitTest) throws IOException, InterruptedException {
		Set<Set<String>> diagnosesSet = new HashSet<>();

		String facetsStr = HelperFunctions.getValidFacets(facetIdentifier);
		if (facetsStr.length() != 0){
			facetIdentifier = facetsStr.substring(0, facetsStr.length()-1);			
		    Files.deleteIfExists(Paths.get(outDirStr + File.separator + "deep_investigation.txt"));    
        }else{
			return diagnosesSet;
		}
		Process p;
		int tc = -1;
		try {
			if (System.getProperty("os.name").toLowerCase().contains("windows")) {
				p = Runtime.getRuntime()
						.exec("py " + NavPath + " -path " + outDirStr + File.separator + programFileName + " -facet \"" + facetIdentifier + "\"");
				tc = p.waitFor();
			} else {
				p = Runtime.getRuntime()
						.exec("python3 " + NavPath + " -path " + outDirStr + File.separator + programFileName + " -facet \"" + facetIdentifier + "\"");
				tc = p.waitFor();
			}			
			
			if(tc != 0){
				BufferedReader erreader = new BufferedReader(new InputStreamReader(p.getErrorStream()));
				StringBuilder errOutput = new StringBuilder();
				String errLine;
				while ((errLine = erreader.readLine())!= null){
					errOutput.append(errLine).append("\n");
				}
			System.out.println(errOutput);
			}
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
			System.out.println("tc = " + tc);
		}
		logger.info("Extracting All Minimal Classical Diagnoses");
		HelperFunctions.runProgram(dID, outDirStr, false, true, false, false, Optional.of(facetIdentifier));
		Set<Set<? extends OWLAxiom>> allOptimalDiagnoses = new HashSet<>();
		allOptimalDiagnoses.addAll(HelperFunctions.returnResult(dID, outDirStr));
		HelperFunctions.storeFacets(HelperFunctions.returnFacets(outDirStr + File.separator +"facets_options.txt"), outDirStr + File.separator + "facets_options.txt");
		if (Files.exists(Paths.get(outDirStr + File.separator + "deep_investigation.txt"))){
			HelperFunctions.storeFacets(HelperFunctions.returnImpacts(outDirStr + File.separator +"deep_investigation.txt"), outDirStr + File.separator +"deep_investigation_log.txt");
			HelperFunctions.displayWarning(outDirStr + File.separator +"deep_investigation_log.txt");
		}	

		
		logger.info("Generating output file");
		HelperFunctions.saveResult(allOptimalDiagnoses, dID, outDirStr);

		// parse the axioms in the current sets of diagnoses to string and return as a 2D set. 
		diagnosesSet = HelperFunctions.createStringSet(allOptimalDiagnoses);
		return diagnosesSet;
	}


	// compute the impact of retracting certain facets and write to a text file
    public static ExitCode getImpact(String dID, String outDirStr, String facetIdentifiers) throws IOException, InterruptedException {
		String facetsStr = HelperFunctions.getValidFacets(facetIdentifiers);
		if (facetsStr.length() != 0){
			facetIdentifiers = facetsStr.substring(0, facetsStr.length()-1);			
		}else{
			return ExitCode.InvalidOption;
		}

		Process p;
		int tc = -1;
		try {
			if (System.getProperty("os.name").toLowerCase().contains("windows")) {
				p = Runtime.getRuntime()
						.exec("py " + NavPath + " -path " + outDirStr + File.separator + programFileName + " -impact \"" + facetIdentifiers + "\"");
				tc = p.waitFor();
			} else {
				p = Runtime.getRuntime()
						.exec("python3 " + NavPath + " -path " + outDirStr + File.separator + programFileName + " -impact \"" + facetIdentifiers + "\"");
				tc = p.waitFor();
			}					
			if (tc != 0){
				switch(tc){
					case 1:
						System.out.println("\033[1;33mNo facets have been applied yet!\033[0m");
						break;
				}
				return ExitCode.InvalidOption;
			}
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
			System.out.println("tc = " + tc);
		}
		HelperFunctions.storeFacets(HelperFunctions.returnImpacts(outDirStr + File.separator + "impacts_raw.txt"), outDirStr + File.separator + "impacts.txt");

		return ExitCode.terminatedSuccessfully;
	}

	//compute the correction set for making a facet applicable and write to a text file
    public static ExitCode reactivateFunction(String dID, String outDirStr, String facetIdentifiers) throws IOException, InterruptedException {
		String facetsStr = HelperFunctions.getValidFacets(facetIdentifiers);
		if (facetsStr.length() != 0){
			facetIdentifiers = facetsStr.substring(0, facetsStr.length()-1);			
		}else{
			return ExitCode.InvalidOption;
		}
		Process p;
		int tc = -1;
		try {
			if (System.getProperty("os.name").toLowerCase().contains("windows")) {
				p = Runtime.getRuntime()
						.exec("py " + NavPath + " -path " + outDirStr + File.separator + programFileName + " -reactivate \"" + facetIdentifiers + "\"");
				tc = p.waitFor();
				
			} else {
				p = Runtime.getRuntime()
						.exec("python3 " + NavPath + " -path " + outDirStr + File.separator + programFileName + " -reactivate \"" + facetIdentifiers + "\"");
				tc = p.waitFor();
			}
			if (tc != 0){
				switch(tc){
					case 1:
						System.out.println("\033[1;33mNo facets have been applied yet!\033[0m");
						break;
					case 2:
						System.out.println("\033[1;33mThe question must be about an element of the unavailable options!\033[0m");
						break;
				}
				return ExitCode.InvalidOption;
			}
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
			System.out.println("tc = " + tc);
		}	
		HelperFunctions.storeFacets(HelperFunctions.returnImpacts(outDirStr + File.separator + "corrections.txt"), outDirStr + File.separator + "corrections.txt");	
		return ExitCode.terminatedSuccessfully;
	}

	// retract a (set of) applied facets, recompute the new minimal diagnoses sets and facet options.
    public static ExitCode delete(String dID, String outDirStr, Optional<String> facetIdentifiers) throws IOException, InterruptedIOException{
		String argsOpt = "";
		if (facetIdentifiers.isPresent()){
			String facetsStr = HelperFunctions.getValidFacets(facetIdentifiers.get().toString());
			if (facetsStr.length() != 0){
				String facetIdentifiersStr = facetsStr.substring(0, facetsStr.length()-1);	
				argsOpt = argsOpt + " -del \"" + facetIdentifiersStr + "\"";		
			}else{
				return ExitCode.InvalidOption;
			}
			
		} else{
			argsOpt = argsOpt + " -delall";
		}
		Process p;
		int tc = -1;
		try {
			if (System.getProperty("os.name").toLowerCase().contains("windows")) {
				p = Runtime.getRuntime()
						.exec("py " + NavPath + " -path " + outDirStr + File.separator + programFileName + argsOpt);
				tc = p.waitFor();
				
			} else {
				p = Runtime.getRuntime()
						.exec("python3 " + NavPath + " -path " + outDirStr + File.separator + programFileName + argsOpt);
				tc = p.waitFor();
			}
			if (tc != 0){
				switch (tc) {
					case 1:
						System.out.println("\033[1;33mNo facet has been applied yet!\033[0m");
						break;
				}
				return ExitCode.InvalidOption;
			}
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
			System.out.println("tc = " + tc);
		}		
		logger.info("Extracting All Minimal Classical Diagnoses");
		HelperFunctions.runProgram(dID, outDirStr, false, true, false, false, Optional.empty());
		Set<Set<? extends OWLAxiom>> allOptimalDiagnoses = new HashSet<>();
		allOptimalDiagnoses.addAll(HelperFunctions.returnResult(dID, outDirStr));
		HelperFunctions.storeFacets(HelperFunctions.returnFacets(outDirStr + File.separator + "facets_options.txt"), outDirStr + File.separator + "facets_options.txt");
		logger.info("Generating output file");
		HelperFunctions.saveResult(allOptimalDiagnoses, dID, outDirStr);

		return ExitCode.terminatedSuccessfully;
	}

	public static Set<Set<String>> delete(String dID, String outDirStr, Optional<String> facetIdentifiers, Boolean unitTest) throws IOException, InterruptedIOException{
		Set<Set<String>> diagnosesSet = new HashSet<>();
		String argsOpt = "";
		if (facetIdentifiers.isPresent()){
			String facetsStr = HelperFunctions.getValidFacets(facetIdentifiers.get().toString());
			if (facetsStr.length() != 0){
				String facetIdentifiersStr = facetsStr.substring(0, facetsStr.length()-1);	
				argsOpt = argsOpt + " -del \"" + facetIdentifiersStr + "\"";		
			}else{
				return diagnosesSet;
			}
			
		} else{
			argsOpt = argsOpt + " -delall";
		}
		Process p;
		int tc = -1;
		try {
			if (System.getProperty("os.name").toLowerCase().contains("windows")) {
				p = Runtime.getRuntime()
						.exec("py " + NavPath + " -path " + outDirStr + File.separator + programFileName + argsOpt);
				tc = p.waitFor();
				
			} else {
				p = Runtime.getRuntime()
						.exec("python3 " + NavPath + " -path " + outDirStr + File.separator + programFileName + argsOpt);
				tc = p.waitFor();
			}
			if (tc != 0){
				switch (tc) {
					case 1:
						System.out.println("\033[1;33mNo facet has been applied yet!\033[0m");
						break;
				}
				return diagnosesSet;
			}
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
			System.out.println("tc = " + tc);
		}		
		logger.info("Extracting All Minimal Classical Diagnoses");
		HelperFunctions.runProgram(dID, outDirStr, false, true, false, false, Optional.empty());
		Set<Set<? extends OWLAxiom>> allOptimalDiagnoses = new HashSet<>();
		allOptimalDiagnoses.addAll(HelperFunctions.returnResult(dID, outDirStr));
		HelperFunctions.storeFacets(HelperFunctions.returnFacets(outDirStr + File.separator + "facets_options.txt"), outDirStr + File.separator + "facets_options.txt");
		logger.info("Generating output file");
		HelperFunctions.saveResult(allOptimalDiagnoses, dID, outDirStr);

		diagnosesSet = HelperFunctions.createStringSet(allOptimalDiagnoses);

		return diagnosesSet;
	}

/*
	* remove the axioms present in the current minimal diagnoses sets from the ontology and save as owl file.
	* Also check for the entailment of the defect in the saved ontology
*/
    public static void saveRepair(String outDirStr, String mDsID, String ontologyPath, OWLAxiom defect, ReasonerName reasonerName, String outputFileName) throws IOException, EntityCheckerException, OWLOntologyCreationException, OWLOntologyStorageException{
		OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
		OWLOntology ontology = manager.loadOntologyFromOntologyDocument(new File(ontologyPath));

		for (Set<OWLAxiom> axiomSets : HelperFunctions.currentDiagnoses){
			for (OWLAxiom axiom: axiomSets){
				manager.removeAxiom(ontology, axiom);
			}
		}
		
		if (HelperFunctions.checkEntailment(ontology, defect, reasonerName)){
			System.out.println("\033[1;31mThe repaired ontology still entails the defect.\n\033[0m");
		} else {
			System.out.println("\033[1;32mThe repaired ontology does not entail the defect.\n\033[0m");
		}
		
		File outputFile = new File(HelperFunctions.getRepairFilePathStr(outDirStr, outputFileName));
		OWLDocumentFormat format = manager.getOntologyFormat(ontology);
		manager.saveOntology(ontology, format, new FileOutputStream(outputFile));
	}
}
