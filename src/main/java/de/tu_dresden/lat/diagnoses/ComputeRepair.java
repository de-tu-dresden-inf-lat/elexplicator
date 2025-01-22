package de.tu_dresden.lat.diagnoses;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLDocumentFormat;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;


import de.tu_dresden.inf.lat.prettyPrinting.formatting.SimpleOWLFormatterCl;
import de.tu_dresden.inf.lat.exceptions.EntityCheckerException;

import de.tu_dresden.inf.lat.prettyPrinting.formatting.SimpleDLFormatter$;

import de.tu_dresden.lat.data.names.ReasonerName;
import de.tu_dresden.lat.tools.LoadingScreen;


public class ComputeRepair {
	private static final Logger logger = Logger.getLogger(ComputeRepair.class);
	private static Map<OWLAxiom, String> axioms2Identifiers;	
	private static Set<OWLAxiom> keepAxioms;
	private static Set<OWLAxiom> removeAxioms;
	private static Set<Set<? extends OWLAxiom>> allOptimalDiagnoses;
	private static String ontologyPathStr;
	private static Set<? extends OWLAxiom> interestingAxiomsSet; 	
	private static ReasonerName selectedReasonerName;
	private static OWLAxiom defectAxiom;
	private static OWLOntology defectOntology;

	public static Boolean isSnapshotActive;
	public static Map<String, OWLAxiom> identifiers2Axioms;
	public static Set<Set<? extends OWLAxiom>> allJustifications;
	public static BlockingQueue<Set<? extends OWLAxiom>> justificationQueue;
	public static ConcurrentHashMap<OWLAxiom, Integer> axiomMap = new ConcurrentHashMap<>();
	public static Map<OWLAxiom,Integer> axiomWeightMap;
	public static Boolean justificationsCompleted;
	private static ByteArrayOutputStream axiomWeightOutputBuffer = new ByteArrayOutputStream();

	public static final String programFileName = "pi.txt";

	private static SimpleOWLFormatterCl sOWLFormatter = new SimpleOWLFormatterCl(true, SimpleDLFormatter$.MODULE$,
		true);

	public static void computeRepairOntology(OWLAxiom axiom, OWLOntology ontology, OWLOntology interestingAxiomOntology, ReasonerName reasonerName, String outDirStr, String ontologyPath) throws IOException, EntityCheckerException, OWLOntologyCreationException, OWLOntologyStorageException{
		defectAxiom = axiom;
		defectOntology = ontology;
		selectedReasonerName = reasonerName;
		ontologyPathStr = ontologyPath;

		if (outDirStr.isEmpty())
			outDirStr = "defaultRepairFolder";

		sOWLFormatter.setReferenceOntology(ontology);
		interestingAxiomsSet = interestingAxiomOntology.getAxioms();
		allJustifications = new CopyOnWriteArraySet<>();
		justificationQueue = new LinkedBlockingQueue<>();
		axiomMap = new ConcurrentHashMap<>();
		keepAxioms = new HashSet<>();
		removeAxioms = new HashSet<>();
		Boolean inputFlag = true;
		justificationsCompleted = false;
		isSnapshotActive = true;

		try{
			ComputeJustificationsThread computeJustificationsRunnable = new ComputeJustificationsThread(reasonerName, axiom, ontology);
			Thread computeJustificationsThread = new Thread(computeJustificationsRunnable); 
			computeJustificationsThread.start();

			SortJustificationsThread sortJustificationsRunnable = new SortJustificationsThread();
			Thread sortJustificationsThread = new Thread(sortJustificationsRunnable);
			sortJustificationsThread.start();

			System.out.println("For the following axioms, choose if you want them in the repair (\"yes\"), not (\"no\") or check their effect (\"not sure\").");
			java.util.Scanner scanner = new java.util.Scanner(System.in);
			
			while(inputFlag){
				Map<OWLAxiom, Integer> freqMap; 
				while (!axiomMap.isEmpty() || isSnapshotActive){
					
					while (axiomMap.isEmpty()){
						LoadingScreen.main(null);
					}

					Map<OWLAxiom, Integer> freqMapUnsorted = new HashMap<>(axiomMap);
					axiomMap.clear();
					isSnapshotActive = false;

					//sort the map freqMap by frequency value descending
					freqMap = freqMapUnsorted.entrySet()
					.stream()
					.sorted(Map.Entry.<OWLAxiom, Integer>comparingByValue().reversed())  
					.collect(Collectors.toMap(
						Map.Entry::getKey,
						Map.Entry::getValue,
						(e1, e2) -> e1, 
						LinkedHashMap::new 
					));;
					for (OWLAxiom justificationAxiom : freqMap.keySet()){
						if(keepAxioms.contains(justificationAxiom) | removeAxioms.contains(justificationAxiom)){
							System.out.println(sOWLFormatter.format(justificationAxiom).toString() + " -- selection already made for the axiom!");
							continue;
						}
						while (true){
							System.out.println(sOWLFormatter.format(justificationAxiom).toString());
							String user_in = scanner.nextLine();
							if (!user_in.isEmpty()){
								if (axiomWeightOutputBuffer.size() > 0){
									overwriteWithBlankLines(axiomWeightOutputBuffer.toString());
									axiomWeightOutputBuffer.reset();
								}
							}
							switch(user_in.toLowerCase()){
								case "yes":									
									keepAxioms.add(justificationAxiom);
									break;
								case "no":
									removeAxioms.add(justificationAxiom);
									break;
								case "not sure":
									{
										computeJustificationsThread.join();
										Set<? extends OWLAxiom> selectedJustification = checkAxiomSelection(allJustifications);
										if (selectedJustification != null){
											System.out.println("Repair not possible!");
											System.out.println("You have selected the following axioms to be in the repair which all belong to the same justification set.");
											for (OWLAxiom selectedAxiom : selectedJustification){
												System.out.println(sOWLFormatter.format(selectedAxiom).toString());
											}
										} else {
											getAxiomWeight(allJustifications, new HashSet<>(), outDirStr);
											displayAxiomWeights(axiomWeightMap);
										}

										// Read the user input (whether it's empty or not)
										String userInput = scanner.nextLine();

										if (axiomWeightOutputBuffer.size() > 0){
											overwriteWithBlankLines(axiomWeightOutputBuffer.toString());
											axiomWeightOutputBuffer.reset();
											continue;
										}
									}
								case "save":
									{
										computeJustificationsThread.join();
										Set<? extends OWLAxiom> selectedJustification = checkAxiomSelection(allJustifications);
										if (selectedJustification != null){
											System.out.println("Repair not possible!");
											System.out.println("You have selected the following axioms to be in the repair which ll belong to the same justification set.");
											for (OWLAxiom selectedAxiom : selectedJustification){
												System.out.println(sOWLFormatter.format(selectedAxiom).toString());
											}
										} else {
											
											System.out.println("Enter the filename to save as: ");
											String save_filename = scanner.nextLine();
											Boolean saveFunctionRet = saveFunction(allJustifications, new HashSet<>(), outDirStr, save_filename);
											if (saveFunctionRet){
												System.out.println("Repaired ontology saved!");
												inputFlag = false;
												break;				
											}						
										}
									}
									continue;
								case "exit":
									System.out.println("Exiting repair mode!");
									inputFlag = false;
									computeJustificationsThread.interrupt();
									sortJustificationsThread.interrupt();
									break;
								default:
									System.out.println("invalid option!");
									continue;
							}

							break;
						}

						if (!inputFlag){break;}
					}
					if (!inputFlag){break;}
				}
				if (!inputFlag){
					break;
				}
				
				while(true){
					System.out.println("All justifications have been computed.\nWould you like to exit or save the repair?");
					String user_in = scanner.nextLine();
					switch(user_in.toLowerCase()){
						case "exit":
							System.out.println("Exiting repair mode!");
							inputFlag = false;
							break;
						case "save":
						{
							Set<? extends OWLAxiom> selectedJustification = checkAxiomSelection(allJustifications);
							if (selectedJustification != null){
								System.out.println("Repair not possible!");
								System.out.println("You have selected the following axioms to be in the repair which all belong to the same justification set.");
								for (OWLAxiom selectedAxiom : selectedJustification){
									System.out.println(sOWLFormatter.format(selectedAxiom).toString());
								}
							} else {
								System.out.println("Enter the filename to save as: ");
								String save_filename = scanner.nextLine();
								Boolean saveFunctionRet = saveFunction(allJustifications, new HashSet<>(), outDirStr, save_filename);
								if (saveFunctionRet){
									System.out.println("Repaired ontologiy saved!");
									inputFlag = false;
									break;
								} else {
									continue;
								}	
							}
						}	
						default:
							System.out.println("Invalid input!");
							continue;
						
					}
					if (!inputFlag){
						break;
					}
				} 
			}
		} catch (Exception e){
			e.printStackTrace();
		}
		
	}

/**
 * append constraints the logic program file
 */
	public static void appendTextToFile(String str, String filePath) throws IOException {

		FileOutputStream outStream = new FileOutputStream(new File(filePath), true);

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
 * check for each justification set if all the axioms in it are selected to be in the repair
 */
	private static Set<? extends OWLAxiom> checkAxiomSelection(Set<Set<? extends OWLAxiom>> allJustifications){
		for (Set<? extends OWLAxiom> justificationSet : allJustifications){
			if (keepAxioms.containsAll(justificationSet)){
				return justificationSet;
			} 
		}
		return null;
	}

/**
 * compute the diagnoses for the current justification sets and save the repaired ontologies
  * @throws IOException 
  * 
  */
 public static void computeDiagnosis(Set<Set<? extends OWLAxiom>> allJustifications, Set<Set<? extends OWLAxiom>> allOptDiagnoses,  String outDirStr) throws IOException{
	String mDsID = "repair";
	allOptimalDiagnoses = allOptDiagnoses;
	logger.info("Creating program");
	SolveProgramHelpers.createProgram(allJustifications, outDirStr, axioms2Identifiers, identifiers2Axioms, programFileName);

	StringJoiner keepAxiomsProgram = new StringJoiner("\n");
	keepAxiomsProgram.add("");
	StringJoiner removeAxiomsProgram = new StringJoiner("\n");
	removeAxiomsProgram.add("");

	applyUserSelection(outDirStr);

	logger.info("Extracting Diagnoses Sets");
	HelperFunctions.runProgram(mDsID, outDirStr, true, false, false, Optional.empty());
	allOptimalDiagnoses.addAll(HelperFunctions.returnResult(mDsID, outDirStr));

	logger.info("Generating the diagnoses output file");
	HelperFunctions.saveResult(allOptimalDiagnoses, mDsID, outDirStr);
}

public static Boolean saveFunction(Set<Set<? extends OWLAxiom>> allJustifications, Set<Set<? extends OWLAxiom>> allOptDiagnoses, String outDirStr, String outFileName) throws IOException, InterruptedException, OWLOntologyCreationException, OWLOntologyStorageException, EntityCheckerException{
	computeDiagnosis(allJustifications, allOptDiagnoses, outDirStr);
	Set<? extends OWLAxiom> selectedDiagnosisSet;
	ASPMinimalDiagnoses.getAllMinimalDiagnoses(defectAxiom, defectOntology, "minimal", outDirStr, new HashSet<>(), selectedReasonerName);

	if(allOptimalDiagnoses.size() > 1){
		Boolean minDiagExists = false;
		Set<Set<? extends OWLAxiom>> minDiagSets = new HashSet<>();
		//check if any of the diagnoses sets are in the ASPMinimalDiagnoses.allOptimalDiagnosesMin, and if exists, prompt user to select one of them else just display all diagnoses sets are ask user to select one
		for(Set<? extends OWLAxiom> diagnosisSet : allOptimalDiagnoses){
			if (ASPMinimalDiagnoses.allOptimalDiagnosesMin.contains(diagnosisSet)){
				System.out.println(diagnosisSet);
				minDiagSets.add(diagnosisSet);
				minDiagExists = true;
			}
		}
		System.out.println("minDiagSet size" + minDiagSets.size());
		if (minDiagExists){
			System.out.println("The following diagnoses sets are minimal. Select one to compute maximal repair:");
			int counter = 1;
			for (Set<? extends OWLAxiom> minDiagSet : minDiagSets){
				System.out.println("Diagnosis set " + counter);
				for (OWLAxiom axiom : minDiagSet){
					System.out.println(sOWLFormatter.format(axiom).toString());
				}
				counter += 1;
			}
		} else {
			System.out.println("Select one of the following diagnoses sets to compute maximal repair:");
			int counter = 1;
			for (Set<? extends OWLAxiom> diagnosisSet : allOptimalDiagnoses){
				System.out.println("Diagnosis set " + counter);
				for (OWLAxiom axiom : diagnosisSet){
					System.out.println(sOWLFormatter.format(axiom).toString());
				}
				counter += 1;
			}
		}
		java.util.Scanner scanner = new java.util.Scanner(System.in);
		while(true){
			String user_in = scanner.nextLine();			
			try{
				int userSelectedDiagnosisSet = Integer.parseInt(user_in);
				selectedDiagnosisSet = allOptimalDiagnoses.toArray(new Set[0])[userSelectedDiagnosisSet-1];
				break;
			} catch (Exception e){
				System.out.println("Invalid input! Please select a valid diagnosis set.");
			}
		}

	} else {
		selectedDiagnosisSet = allOptimalDiagnoses.iterator().next();
	}

	if(!ASPMinimalDiagnoses.allOptimalDiagnosesMin.contains(selectedDiagnosisSet)){
		Set<Set<? extends OWLAxiom>> recommendedDiagnosesSet = recommendDiagnosisSet(ASPMinimalDiagnoses.allOptimalDiagnosesMin, selectedDiagnosisSet);
		if (recommendedDiagnosesSet.size() < 2){
			System.out.println("Enter \"save\" to proceed with selected diagnosis, \"min\" to proceed with minimal diagnosis or \"cancel\" to cancel.");
			java.util.Scanner scanner = new java.util.Scanner(System.in);
			String user_in = scanner.nextLine();
			if (user_in.equals("save")){
				// Do nothing
			} else if (user_in.equals("min")){
				selectedDiagnosisSet = recommendedDiagnosesSet.iterator().next();
			} else if (user_in.equals("cancel")){
				System.out.println("Cancelling save!");
				return false;
			} 
			else {
				System.out.println("Invalid input!");
				return false;
			}
		} else {
			System.out.println("Enter \"save\" to proceed with selected diagnosis, \"min\" to make it minimal or \"cancel\" to cancel.");
			java.util.Scanner scanner = new java.util.Scanner(System.in);
			String user_in = scanner.nextLine();
			if (user_in.equals("save")){
				// Do nothing
			} else if (user_in.equals("min")){
				System.out.println("For the following axioms, enter \"yes\" to keep in maximal repair, \"no\" to remove from maximal repair.");
				while (recommendedDiagnosesSet.size()>1){
					//get the most common axiom in the set of sets
					OWLAxiom diagAxiom = mostFrequentAxiom(recommendedDiagnosesSet);	
						while (true){
						System.out.println(sOWLFormatter.format(diagAxiom).toString());
						String user_in2 = scanner.nextLine();
						if (user_in2.equals("yes")){
							recommendedDiagnosesSet = recommendedDiagnosesSet.stream().filter(diagnosisSet -> !diagnosisSet.contains(diagAxiom)).collect(Collectors.toSet());
							break;
						} else if (user_in2.equals("no")){
							recommendedDiagnosesSet = recommendedDiagnosesSet.stream().filter(diagnosisSet -> diagnosisSet.contains(diagAxiom)).collect(Collectors.toSet());
							break;
						} else {
							System.out.println("Invalid input!");
						}
					}
					

				}
				selectedDiagnosisSet = recommendedDiagnosesSet.iterator().next();
			} else if (user_in.equals("cancel")){
				System.out.println("Cancelling save!");
				return false;
			}
			else{
				System.out.println("Invalid input!");
				return false;
			}
		}		
	}	
	try{
		saveRepairOntology(selectedDiagnosisSet, outDirStr, outFileName);
		return true;
	} catch (Exception e){
		e.printStackTrace();
		return false;
	}
	
}

public static void saveRepairOntology(Set<? extends OWLAxiom> diagnosisSet, String outDirStr, String outFileNameStr) throws OWLOntologyCreationException, OWLOntologyStorageException, FileNotFoundException{
	OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
	OWLOntology ontology = manager.loadOntologyFromOntologyDocument(new File(ontologyPathStr));

	for (OWLAxiom axiom: diagnosisSet){
		manager.removeAxiom(ontology, axiom);
	}
	File outputFile = new File(HelperFunctions.getRepairFilePathStr(outDirStr, outFileNameStr + ".owl"));
	OWLDocumentFormat format = manager.getOntologyFormat(ontology);
	manager.saveOntology(ontology, format, new FileOutputStream(outputFile));
}

/**
 * add constraints respective to keepAxioms and removeAxioms to the logic program file
 */
	private static void applyUserSelection(String outDirStr) throws IOException{
		StringJoiner keepAxiomsProgram = new StringJoiner("\n");
		keepAxiomsProgram.add("");
		StringJoiner removeAxiomsProgram = new StringJoiner("\n");
		removeAxiomsProgram.add("");

		for (OWLAxiom axiom : keepAxioms){
			String axiomID = axioms2Identifiers.get(axiom);
			keepAxiomsProgram.add(":- not "+axiomID+"().");
		}

		for (OWLAxiom axiom : removeAxioms){
			String axiomID = axioms2Identifiers.get(axiom);
			removeAxiomsProgram.add(":- "+axiomID+"().");
		}

		File outDir = new File(outDirStr);
		if (!outDir.exists())
			throw new IOException("Directory does not exist -> " + outDirStr);
		appendTextToFile(keepAxiomsProgram.toString(), outDirStr + File.separator + programFileName);
		appendTextToFile(removeAxiomsProgram.toString(), outDirStr + File.separator + programFileName);

	}

/*
 * create and run logic program based on user selections
 * computes all the possible repaired ontologies
 * get the axiom weight of the interesting axioms with respect to the obtained repaired ontologies
 */
    private static void getAxiomWeight(Set<Set<? extends OWLAxiom>> allJustifications, Set<Set<? extends OWLAxiom>> allOptDiagnoses, String outDirStr) throws IOException, EntityCheckerException, OWLOntologyCreationException, OWLOntologyStorageException{
		allOptimalDiagnoses = allOptDiagnoses;
        String mDsID = "repair";
        logger.info("Creating Program");
		SolveProgramHelpers.createProgram(allJustifications, outDirStr, axioms2Identifiers, identifiers2Axioms, programFileName);

		applyUserSelection(outDirStr);
		
		logger.info("Extracting All Minimal Classical Diagnoses");
		HelperFunctions.runProgram(mDsID, outDirStr, true, false, false, Optional.empty());
		allOptimalDiagnoses.addAll(HelperFunctions.returnResult(mDsID, outDirStr));

		logger.info("Generating output file");
		HelperFunctions.saveResult(allOptimalDiagnoses, mDsID, outDirStr);

		try{
			ComputeAxiomWeightThread runnable2 = new ComputeAxiomWeightThread(outDirStr, mDsID, ontologyPathStr, "repairOntology");
			Thread axiomWeightThread = new Thread(runnable2); 
			axiomWeightThread.start();
			
			while(axiomWeightThread.isAlive()){
				LoadingScreen.main(null);
			}
		} catch (InterruptedException e){
			e.printStackTrace();
		}
    }

	/**
	 * Map every axiom to an identifier of the form "alpha" + integer
	 * 
	 * @param allJustifications
	 */
	public static void fillMap(Set<Set<? extends OWLAxiom>> allJustifications) {

		axioms2Identifiers = new HashMap<>();
		identifiers2Axioms = new HashMap<>();
        String axiomPrefix = "alpha";

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

/**
 * computes the repair ontologies for each diagnosis set and compute axiom weight of interesting axioms
 */
	public static void computeRepairs(String outDirStr, String mDsID, String ontologyPath, String outputFileName) throws IOException, EntityCheckerException, OWLOntologyCreationException, OWLOntologyStorageException{
		String tempfolderPath = "tempRepairsFolder"; // Path of the folder to create
		String tempOutDirStr = outDirStr + "/" + tempfolderPath;
		File repFolder = new File(tempOutDirStr);
		repFolder.mkdir();
		int counter = 1;

		//from the produced diagnoses set, compute repair ontology for each diagnosis set and save as ontology
		for (Set<? extends OWLAxiom> axiomSets : allOptimalDiagnoses){
			OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
			OWLOntology ontology = manager.loadOntologyFromOntologyDocument(new File(ontologyPath));

			for (OWLAxiom axiom: axiomSets){
				manager.removeAxiom(ontology, axiom);
			}
			File outputFile = new File(HelperFunctions.getRepairFilePathStr(tempOutDirStr, outputFileName+"_"+Integer.toString(counter)));
			OWLDocumentFormat format = manager.getOntologyFormat(ontology);
			manager.saveOntology(ontology, format, new FileOutputStream(outputFile));
			counter += 1;
		}

		computeAxiomWeight(counter, tempOutDirStr);

		File[] allContents = repFolder.listFiles();
		if (allContents != null) {
			for (File file : allContents) {
				file.delete();
			}
		}
		repFolder.delete();
	}

/**
 * compute axiom weight of interesting axioms based on entailement in the repaired ontologies
 */
	public static void computeAxiomWeight(int counter, String outDirStr) throws OWLOntologyCreationException{

		axiomWeightMap = new HashMap<>();
		
		//get the axiom weight of the interesting axioms
		for (OWLAxiom interestingAxiom : interestingAxiomsSet){
			axiomWeightMap.putIfAbsent(interestingAxiom, 0);
			for (int i=1; i<counter; i++){
				String ontologyFileName = "repairOntology_"+Integer.toString(i);
				String outputFile = HelperFunctions.getRepairFilePathStr(outDirStr, ontologyFileName);
				OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
				OWLOntology ontology = manager.loadOntologyFromOntologyDocument(new File(outputFile));
				if (HelperFunctions.checkEntailment(ontology, interestingAxiom, ReasonerName.Hermit)){
					axiomWeightMap.put(interestingAxiom, axiomWeightMap.get(interestingAxiom)+1);
				}
			}
		}

		//get the axiom weight in percentage
		for (OWLAxiom axiom : axiomWeightMap.keySet()){
			axiomWeightMap.put(axiom, (axiomWeightMap.get(axiom)*100)/counter);
		}
	}

/**
 * get the most frequent axiom the set of sets of axioms provided
 * @param axioms2DSet
 * @return
 */
	public static OWLAxiom mostFrequentAxiom(Set<Set<? extends OWLAxiom>> axioms2DSet){
		Map<OWLAxiom, Integer> frequencyMap = new HashMap<>();
		for (Set<? extends OWLAxiom> axiomsSet : axioms2DSet){
			for (OWLAxiom axiom : axiomsSet){
				frequencyMap.putIfAbsent(axiom, 0);
				frequencyMap.put(axiom, frequencyMap.get(axiom)+1);
				if(frequencyMap.get(axiom) == axioms2DSet.size()){
					frequencyMap.remove(axiom);
				}
			}
		}
		OWLAxiom frequentAxiom = frequencyMap.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey();
		return frequentAxiom;
	}

	// Method to display the axiom weights

	private static void displayAxiomWeights(Map<OWLAxiom,Integer> axiomWeightMap) throws InterruptedException{
		axiomWeightOutputBuffer = new ByteArrayOutputStream();
        PrintStream bufferStream = new PrintStream(axiomWeightOutputBuffer);
        PrintStream originalOut = System.out;

        // Simulate printing axiom weights
        System.setOut(bufferStream); // Redirect output
        for (Map.Entry<OWLAxiom,Integer> axiomWeightEntry : axiomWeightMap.entrySet()) {
			String axiomWeightStr = sOWLFormatter.format(axiomWeightEntry.getKey()).toString() + " = " + axiomWeightEntry.getValue() + "%";
			System.out.println(axiomWeightStr);
		}
        System.out.flush();
        System.setOut(originalOut); // Restore original output

        // Print the buffered output to the actual console
        System.out.print(axiomWeightOutputBuffer.toString());

    }

    // Method to overwrite printed output with blank lines
    private static void overwriteWithBlankLines(String output) {
        int lineCount = output.split("\n").length;

        for (int i = 0; i < lineCount+3; i++) {
            System.out.print("\033[F");// Move cursor
            System.out.print("\033[2K"); // Clear line
        }
    }

	private static Set<Set<? extends OWLAxiom>> recommendDiagnosisSet(Set<Set<? extends OWLAxiom>> allMinimalOptimalDiagnoses, Set<? extends OWLAxiom> repairDiagnosis) throws IOException, EntityCheckerException, OWLOntologyCreationException, OWLOntologyStorageException, InterruptedException{
		Set<Set<? extends OWLAxiom>> recommendationSet = new HashSet<>();
		Boolean isMaxRepair = false;
		if (allMinimalOptimalDiagnoses.contains(repairDiagnosis)){
			isMaxRepair = true;
			recommendationSet.add(repairDiagnosis);
		}

		if (!isMaxRepair){
			for (Set<? extends OWLAxiom> diagSet : allMinimalOptimalDiagnoses) {
				if(repairDiagnosis.containsAll(diagSet)){
					recommendationSet.add(diagSet);
				}
        	}
		}
        return recommendationSet;
    
	}

	public static Set<Set<? extends OWLAxiom>> getAllJustificationsAsync(ReasonerName reasonerName, OWLAxiom axiom, OWLOntology ontology, BlockingQueue<Set<? extends OWLAxiom>> queue) {
		if (reasonerName == ReasonerName.Elk)
			return JustificationsGenerator.getAllELKJustificationsAsync(axiom, ontology, queue);

		return JustificationsGenerator.getAllHermitJustificationsAsync(axiom, ontology);
	}

}
