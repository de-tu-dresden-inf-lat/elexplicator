package de.tu_dresden.lat.diagnoses;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDocumentFormat;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.model.parameters.Imports;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tu_dresden.inf.lat.prettyPrinting.formatting.SimpleOWLFormatterCl;
import de.tu_dresden.inf.lat.counterExample.tools.Segmenter;
import de.tu_dresden.inf.lat.exceptions.EntityCheckerException;

import de.tu_dresden.inf.lat.prettyPrinting.formatting.SimpleDLFormatter$;
import de.tu_dresden.lat.data.enums.ExitCode;
import de.tu_dresden.lat.data.names.ReasonerName;
import de.tu_dresden.lat.tools.LoadingScreen;


public class ComputeRepair {
	private static final Logger logger = Logger.getLogger(ComputeRepair.class);

	public static volatile Boolean isSnapshotActive;
	public static Map<OWLAxiom, String> axioms2Identifiers;	
	public static Map<String, OWLAxiom> identifiers2Axioms;
	public static Set<Set<? extends OWLAxiom>> allJustifications;
	public static BlockingQueue<Set<? extends OWLAxiom>> justificationQueue;
	public static BlockingQueue<String> tempFiles;
	public static ConcurrentHashMap<OWLAxiom, Integer> axiomMap = new ConcurrentHashMap<>();
	public static Map<OWLAxiom,Integer> axiomWeightMap;
	public static volatile Boolean justificationsCompleted;
	private static ByteArrayOutputStream axiomWeightOutputBuffer = new ByteArrayOutputStream();

	public static final String programFileName = "pi.txt";

	private static Thread axiomWeightThread = null;
	private static Thread computeDiagnosisThread = null;

	private static SimpleOWLFormatterCl sOWLFormatter = new SimpleOWLFormatterCl(true, SimpleDLFormatter$.MODULE$,
		true);

	private static Boolean signal = true;
	private static Boolean inputFlag = true;

	private static Boolean repairCheck = true;
	public static volatile Boolean diagnosisComputed = false;
	public static Set<Set <? extends OWLAxiom>> minimalDiagnoses = new HashSet<>();

/**
 * interactive method to compute the repair ontology based on user selection of justification axioms
 * @param axiom
 * @param ontology
 * @param interestingAxiomOntology
 * @param reasonerName
 * @param outDirStr
 * @param ontologyPath
 * @throws IOException
 * @throws EntityCheckerException
 * @throws OWLOntologyCreationException
 * @throws OWLOntologyStorageException
 */
	public static ExitCode computeRepairOntology(OWLAxiom axiom, OWLOntology ontology, OWLOntology interestingAxiomOntology, ReasonerName reasonerName, String outDirStr, String ontologyPath) throws IOException, EntityCheckerException, OWLOntologyCreationException, OWLOntologyStorageException{
		ExitCode ecode = ExitCode.terminatedSuccessfully;
		Runtime.getRuntime().addShutdownHook(new Thread(()->{
			System.out.println("Shutting down");
			signal = false;
			inputFlag = false;
			// cleanup();
		}));

		if (outDirStr.isEmpty())
			outDirStr = "defaultRepairFolder";

		sOWLFormatter.setReferenceOntology(ontology);
		Set<? extends OWLAxiom> interestingAxiomsSet = interestingAxiomOntology.getTBoxAxioms(Imports.EXCLUDED);
		allJustifications = new CopyOnWriteArraySet<>();
		justificationQueue = new LinkedBlockingQueue<>();
		axiomMap = new ConcurrentHashMap<>();
		Set<OWLAxiom> keepAxioms = new HashSet<>();
		Set<OWLAxiom> removeAxioms = new HashSet<>();
		
		justificationsCompleted = false;
		isSnapshotActive = true;

		Thread computeJustificationsThread = null;
		Thread sortJustificationsThread = null;

		OWLOntology defectModule =  Segmenter.getStarModule(ontology, axiom.getSignature(),
				ontology.getOntologyID().getOntologyIRI().isPresent() ? ontology.getOntologyID().getOntologyIRI().get()
						: IRI.create("http://example.org/temp-ontology"));
		try{
			ComputeJustificationsThread computeJustificationsRunnable = new ComputeJustificationsThread(reasonerName, axiom, defectModule);
			computeJustificationsThread = new Thread(computeJustificationsRunnable); 
			computeJustificationsThread.start();

			SortJustificationsThread sortJustificationsRunnable = new SortJustificationsThread();
			sortJustificationsThread = new Thread(sortJustificationsRunnable);
			sortJustificationsThread.start();

			ComputeDiagnosisThread computeDiagnosisRunnable = new ComputeDiagnosisThread(ontology, axiom, outDirStr, reasonerName);
			computeDiagnosisThread = new Thread(computeDiagnosisRunnable);
			computeDiagnosisThread.start();

			System.out.println("Entered repair mode for the defect axiom: " + sOWLFormatter.format(axiom).toString());
			System.out.println("For the following axioms, choose if you want them in the repair (\"yes\"), not (\"no\") or check their effect (\"not sure\").");
			Scanner scanner = new Scanner(System.in);
			
			while(inputFlag){
				Map<OWLAxiom, Integer> freqMap; 
				while (!axiomMap.isEmpty() || isSnapshotActive){
					
					while (axiomMap.isEmpty()){
						LoadingScreen.main(null);
						if (!isSnapshotActive){
							break;
						}
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
						if(keepAxioms.contains(justificationAxiom) | removeAxioms.contains(justificationAxiom) | justificationAxiom.equals(axiom)){
							continue;
						}
						while (signal){
							System.out.println(sOWLFormatter.format(justificationAxiom).toString());
							String user_in = scanner.nextLine();
							if (!user_in.isEmpty()){
								if (axiomWeightOutputBuffer.size() > 0){
									overwriteWithBlankLines(axiomWeightOutputBuffer.toString());
									axiomWeightOutputBuffer.reset();
								}
							}
							switch(user_in.toLowerCase()){
								case "yes":{							
									keepAxioms.add(justificationAxiom);
									break;
								}
								case "no":{
									removeAxioms.add(justificationAxiom);
									break;
								}
								case "not sure":{
									String bufferedString = "Select from the options: \n" + //
																"1. Compute the entailment probabilities of the interesting axioms\n" + //
																"2. Compute the class hierarchy difference\n" + //
																"3. Hamming distance of current ontology to preferred repair";
									System.out.println(bufferedString);
									String userSelections = scanner.nextLine();
									String[] selections = userSelections.split(",");
									for (String opt : selections){
										switch(opt.trim()){
											case "1":{
												computeJustificationsThread.join();
												bufferedString += computeProbabilities(justificationAxiom, keepAxioms, removeAxioms, outDirStr, ontologyPath, interestingAxiomsSet, reasonerName);
												break;
											}
											case "2":{
												bufferedString += computeHierarchyDiff(keepAxioms, removeAxioms, outDirStr, ontologyPath, justificationAxiom, reasonerName);
												break;
											}
											case "3":{
												computeJustificationsThread.join();
												bufferedString += hammingDistance(justificationAxiom, removeAxioms, keepAxioms, interestingAxiomsSet, ontologyPath, reasonerName, outDirStr);
												break;
											}
											default:{
												String retString = opt+ " is an invalid option!";
												System.out.println(retString);
												bufferedString += retString;
												break;
											}
											
										}
									}
									scanner.nextLine();

									if (bufferedString.length() > 0){
										overwriteWithBlankLines(bufferedString);
									}
									continue;
								}
								case "save":{
									computeJustificationsThread.join();
										
									System.out.println("Enter the filename to save as: ");
									String save_filename = scanner.nextLine();

									computeDiagnosisThread.join();
									Boolean savedFlag = saveProcess(ontology, axiom, removeAxioms, ontologyPath, outDirStr, save_filename, reasonerName, scanner);

									if(savedFlag){
										inputFlag = false;
										break;
									} else {
										continue;
									}
								}
								case "exit":{
									System.out.println("Exiting repair mode!");
									inputFlag = false;
									computeJustificationsThread.interrupt();
									sortJustificationsThread.interrupt();
									computeDiagnosisThread.interrupt();
									break;
								}
								default:{
									System.out.println("invalid option!");
									continue;
								}
							}
							break;
							
						}
						if(diagnosisComputed && repairCheck){
							computeDiagnosisThread.join();
							Boolean repairStatus = checkRepair(axiom, ontology, removeAxioms, outDirStr, reasonerName, ontologyPath, scanner);
							if (repairStatus){
								inputFlag = false;
								break;
							}
							// entropySorting(allJustifications, minimalDiagnoses);
						}

						if (!inputFlag){break;}
					}
					if (!inputFlag){break;}
				}
				if (!inputFlag){
					break;
				}
				
				while(signal){
					System.out.println("All justifications have been computed.\nWould you like to exit or save the repair?");
					String user_in = scanner.nextLine();
					switch(user_in.toLowerCase()){
						case "exit":{
							System.out.println("Exiting repair mode!");
							inputFlag = false;
							break;
						}
						case "save":{
							System.out.println("Enter the filename to save as: ");
							String save_filename = scanner.nextLine();

							computeJustificationsThread.join();
							Boolean savedFlag = saveProcess(ontology, axiom, removeAxioms, ontologyPath, outDirStr, save_filename, reasonerName, scanner);

							if(savedFlag){
								inputFlag = false;
								break;
							} else {
								continue;
							}							
						}	
						default:{	
							System.out.println("Invalid input!");
							continue;
						}
						
					}
					if (!inputFlag){
						break;
					}
				} 
			}
		} catch (Exception e){
			e.printStackTrace();
			computeJustificationsThread.interrupt();
			sortJustificationsThread.interrupt();
			if (axiomWeightThread != null){
				axiomWeightThread.interrupt();
			}
			Thread.currentThread().interrupt();
			ecode = ExitCode.executionInterrupted;
			System.out.println(e.getMessage());
			return ecode;
		} 
		// finally {
		// 	cleanup();
		// }

		return ecode;
		
	}

/**
 * handle option probabilities in not sure case
 * @param str
 * @param filePath
 * @throws IOException
 * @throws EntityCheckerException 
 * @throws OWLOntologyStorageException 
 * @throws OWLOntologyCreationException 
 */
	private static String computeProbabilities(OWLAxiom justificationAxiom, Set<OWLAxiom> keepAxioms, Set<OWLAxiom> removeAxioms, String outDirStr, String ontologyPath, Set<? extends OWLAxiom> interestingAxiomsSet, ReasonerName reasonerName) throws IOException, OWLOntologyCreationException, OWLOntologyStorageException, EntityCheckerException{
		//entailment probability when retaining the justification axiom
		String bufferedString = "";
		axiomWeightOutputBuffer.write("Entailment probability when retaining the justification axiom:\n".getBytes());
		Set<OWLAxiom> updatedKeepAxioms = new HashSet<>(keepAxioms);
		updatedKeepAxioms.add(justificationAxiom);
		getEntailmentProbability(allJustifications, updatedKeepAxioms, removeAxioms, outDirStr, ontologyPath, interestingAxiomsSet, reasonerName);
		bufferedString += axiomWeightOutputBuffer.toString();									
		axiomWeightOutputBuffer.reset();
		
		//entailment probability when removing the justification axiom
		axiomWeightOutputBuffer.write("Entailment probability when removing the justification axiom:\n".getBytes());
		Set<OWLAxiom> updatedRemoveAxioms = new HashSet<>(removeAxioms);
		updatedRemoveAxioms.add(justificationAxiom);
		getEntailmentProbability(allJustifications, keepAxioms, updatedRemoveAxioms, outDirStr, ontologyPath, interestingAxiomsSet, reasonerName);
		bufferedString += axiomWeightOutputBuffer.toString();									
		axiomWeightOutputBuffer.reset();

		return bufferedString;
	}


/**
 * compute the class hierarchy difference when retaining and removing the justification axiom
 * @param keepAxioms
 * @param removeAxioms
 * @param outDirStr
 * @param ontologyPath
 * @param justificationAxiom
 * @param reasonerName
 * @return String containing the class hierarchy difference
 * @throws IOException
 * @throws OWLOntologyCreationException
 * @throws OWLOntologyStorageException
 */
	private static String computeHierarchyDiff(Set<OWLAxiom> keepAxioms, Set<OWLAxiom> removeAxioms, String outDirStr, String ontologyPath, OWLAxiom justificationAxiom, ReasonerName reasonerName) throws IOException, OWLOntologyCreationException, OWLOntologyStorageException{
		String bufferedString = "";
		//class hierarchy difference when retaining and removing the justification axiom
		ClassHierarchyDifference classHierarchyDifference = new ClassHierarchyDifference(outDirStr, ontologyPath, keepAxioms, removeAxioms, justificationAxiom, reasonerName);
		classHierarchyDifference.getClassHierarchy(ontologyPath);
		writeClassHierarchyDifferenceToFile(classHierarchyDifference.hierarchyMap1, classHierarchyDifference.hierarchyMap2, classHierarchyDifference.hierarchyDifference, outDirStr);
		displayClassHierarchyDifference(classHierarchyDifference.hierarchyDifference);
		bufferedString += axiomWeightOutputBuffer.toString();									
		axiomWeightOutputBuffer.reset();

		return bufferedString;
	}
/**
 * append constraints in given string to the logic program file
 * @param str
 * @param filePath
 * @throws IOException
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
 * check for each justification set in allJustifications if all the axioms in it are selected to be in the repair
 * @param allJustifications
 * @return
 */
	public static Set<? extends OWLAxiom> checkAxiomSelection(Set<Set<? extends OWLAxiom>> allJustifications, Set<OWLAxiom> keepAxioms){
		for (Set<? extends OWLAxiom> justificationSet : allJustifications){
			if (keepAxioms.containsAll(justificationSet)){
				return justificationSet;
			} 
		}
		return null;
	}

/**
 * compute the diagnoses for the given justifications and save the repaired ontologies
 * @param allJustifications
 * @param allOptDiagnoses
 * @param outDirStr
 * @throws IOException
 */
	public static Set<Set<? extends OWLAxiom>> computeDiagnosis(Set<Set<? extends OWLAxiom>> allJustifications, Set<OWLAxiom> keepAxioms, Set<OWLAxiom> removeAxioms, String outDirStr) throws IOException{
		Set<Set<? extends OWLAxiom>> allOptimalDiagnoses = new HashSet<>();
		String mDsID = "repair";
		logger.info("Creating program");
		SolveProgramHelpers.createProgram(allJustifications, outDirStr, axioms2Identifiers, identifiers2Axioms, programFileName);

		applyUserSelection(keepAxioms, removeAxioms, outDirStr);

		logger.info("Extracting Diagnoses Sets");
		HelperFunctions.runProgram(mDsID, outDirStr, true, false, false, Optional.empty());
		
		allOptimalDiagnoses.addAll(HelperFunctions.returnResult(mDsID, outDirStr));

		logger.info("Generating the diagnoses output file");
		HelperFunctions.saveResult(allOptimalDiagnoses, mDsID, outDirStr);

		return allOptimalDiagnoses;
	}

/**
 * add constraints respective to keepAxioms and removeAxioms to the logic program file
 * @param outDirStr
 * @throws IOException
 */
	private static void applyUserSelection(Set<OWLAxiom> keepAxioms,Set<OWLAxiom> removeAxioms, String outDirStr) throws IOException{
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

	private static void getEntailmentProbability(Set<Set<? extends OWLAxiom>> allJustifications, Set<OWLAxiom> keepAxioms, Set<OWLAxiom> removeAxioms, String outDirStr, String ontologyPath, Set<? extends OWLAxiom> interestingAxiomsSet, ReasonerName reasonerName) throws IOException, OWLOntologyCreationException, OWLOntologyStorageException, EntityCheckerException{
		
		Set<? extends OWLAxiom> selectedJustification = checkAxiomSelection(allJustifications, keepAxioms);
		if (selectedJustification != null){
			displayNoRepair(selectedJustification);
			
		} else {
			//interesting axioms entailment percentage in repairs when retaining the justification axiom
			getAxiomWeight(allJustifications, outDirStr, ontologyPath, interestingAxiomsSet, keepAxioms, removeAxioms, reasonerName);
			displayAxiomWeights(axiomWeightMap);
		}
	}

/**
 * create and run logic program based on user selection of justification axioms, compute the diagnoses and invoke the async thread to compute axiom weight
 * @param allJustifications
 * @param allOptDiagnoses
 * @param outDirStr
 * @throws IOException
 * @throws EntityCheckerException
 * @throws OWLOntologyCreationException
 * @throws OWLOntologyStorageException
 */
	private static void getAxiomWeight(Set<Set<? extends OWLAxiom>> allJustifications, String outDirStr, String ontologyPath, Set<? extends OWLAxiom> interestingAxiomsSet, Set<OWLAxiom> keepAxioms, Set<OWLAxiom> removeAxioms, ReasonerName reasonerName) throws IOException, EntityCheckerException, OWLOntologyCreationException, OWLOntologyStorageException{
		Set<Set<? extends OWLAxiom>> allOptimalDiagnoses = computeDiagnosis(allJustifications, keepAxioms, removeAxioms, outDirStr);
		axiomWeightThread = null;
		try{
			ComputeAxiomWeightThread runnable2 = new ComputeAxiomWeightThread(outDirStr, "repair", ontologyPath, "repairOntology", allOptimalDiagnoses, interestingAxiomsSet, reasonerName);
			axiomWeightThread = new Thread(runnable2); 
			axiomWeightThread.start();
			
			while(axiomWeightThread.isAlive()){
				LoadingScreen.main(null);
			}
			axiomWeightThread.join();
		} catch (InterruptedException e){
			axiomWeightThread.interrupt();
			Thread.currentThread().interrupt();
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
 * compute the possible repairs, extract and save the modules of important axioms from the repaired ontologies in a map
 * replaces the computeRepairs method before the enatilment percentage computation used modules 
 * @return Map<OWLAxiom, Set<OWLOntology>>
 * @throws OWLOntologyCreationException 
 */
	public static Map<OWLAxiom, List<OWLOntology>> computeRepairsModules(String ontologyPath, Set<Set<? extends OWLAxiom>> allOptimalDiagnoses, Set<? extends OWLAxiom> interestingAxiomsSet) throws OWLOntologyCreationException{
		Map<OWLAxiom, List<OWLOntology>> repairModules = new HashMap<>();
		for (Set<? extends OWLAxiom> axiomSets : allOptimalDiagnoses){
			OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
			OWLOntology ontology = manager.loadOntologyFromOntologyDocument(new File(ontologyPath));

			for (OWLAxiom axiom: axiomSets){
				manager.removeAxiom(ontology, axiom);
				
			}
			for (OWLAxiom impAxiom : interestingAxiomsSet){
				OWLOntology impModule = Segmenter.getStarModule(ontology, impAxiom.getSignature(),
            		ontology.getOntologyID().getOntologyIRI().isPresent() ? ontology.getOntologyID().getOntologyIRI().get()
            				: IRI.create("http://example.org/temp-repair-ontology"));
				repairModules.putIfAbsent(impAxiom, new ArrayList<>());
				repairModules.get(impAxiom).add(impModule);
			}
		}
		return repairModules;
	}

/**
 * compute the repair ontology for the given diagnosis set	
 * @param selectedMinDiagnosis
 * @param ontologyPath
 * @return
 * @throws OWLOntologyCreationException
 */
	public static OWLOntology computeRepair(Set<? extends OWLAxiom> selectedMinDiagnosis, String ontologyPath) throws OWLOntologyCreationException{
			OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
			OWLOntology repairOntology = manager.loadOntologyFromOntologyDocument(new File(ontologyPath));
	
				for (OWLAxiom axiom: selectedMinDiagnosis){
				manager.removeAxiom(repairOntology, axiom);
			}
		
		return repairOntology;
	}

	private static Boolean checkRepair(OWLAxiom defectAxiom, OWLOntology defectOntology, Set<OWLAxiom> removeAxioms, String outDirStr, ReasonerName reasonerName, String ontologyPath, Scanner scanner) throws IOException, InterruptedException{
		
		Set<Set<? extends OWLAxiom>> satisfiedDiagnoses = new HashSet<>();

		if (minimalDiagnoses.size() > 0){
			for (Set<? extends OWLAxiom> diagnosisSet : minimalDiagnoses){
				if (removeAxioms.containsAll(diagnosisSet)){
					satisfiedDiagnoses.add(diagnosisSet);
				}
			}
		} 

		if (satisfiedDiagnoses.size() > 0){
			System.out.println("Repair already reached!");
			System.out.println("Enter \"save\" to save the repair or \"continue\" to continue answering the remaining justification axioms.");
			String user_in = scanner.nextLine();
			String fileName = "";
			if (user_in.toLowerCase().equals("save")){
				System.out.println("Enter the filename to save as: ");
				fileName = scanner.nextLine();
				return saveProcess(defectOntology, defectAxiom, removeAxioms, ontologyPath, outDirStr, fileName, reasonerName, scanner);
			} else if (user_in.toLowerCase().equals("continue")){
				repairCheck = false;
				return false;
			} 
		}	
		return false;	
	}

	public static Boolean saveProcess(OWLOntology ontology, OWLAxiom axiom, Set<OWLAxiom> removeAxioms, String ontologyPath, String outDirStr, String save_filename, ReasonerName reasonerName, Scanner scanner){
		OWLOntology repairOntology;
		try {
			repairOntology = computeRepair(removeAxioms, ontologyPath);
		} catch (OWLOntologyCreationException e) {
			e.printStackTrace();
			return false;
		}
		if (HelperFunctions.checkEntailment(repairOntology, axiom, reasonerName)){
			return noRepair(repairOntology, outDirStr, save_filename, scanner);											
		} else {
			try {
				return refineRepair(ontology, repairOntology, axiom, removeAxioms, ontologyPath, outDirStr, save_filename, reasonerName, scanner);
			} catch (OWLOntologyCreationException | OWLOntologyStorageException | IOException | EntityCheckerException
					| InterruptedException | ExecutionException e) {
				e.printStackTrace();
				return false;
			}
		}
	} 

/**
 * if the resulting ontology is not a repair, prompt the user to save or cancel
 * @param resultOntology
 * @param outDirStr
 * @param save_filename
 * @param scanner
 * @return true if the ontology is saved, false if not saved 
 */
	private static Boolean noRepair(OWLOntology resultOntology, String outDirStr, String save_filename, Scanner scanner){
		String user_in;
		System.out.println("The resulting ontology is not a repair.\nEnter \"continue\" to save the ontology or \"cancel\" to cancel.");
		user_in = scanner.nextLine();
		if (user_in.toLowerCase().equals("cancel")){
			System.out.println("Cancelling save!");
			return false;
		} else if (user_in.toLowerCase().equals("continue")){
			try{
				saveRepairOntology(resultOntology, outDirStr, save_filename);
				return true;
			} catch (Exception e){
				e.printStackTrace();
				return false;
			}															
		} else {
			System.out.println("Invalid input!");
			return false;
		}
	} 

/**
 * check if the diagnosis is minimal	
 * @param defectOntology
 * @param defectAxiom
 * @param outDirStr
 * @return
 * @throws IOException
 * @throws InterruptedException
 */
	public static Boolean checkDiagMinimality(OWLOntology defectOntology, OWLAxiom defectAxiom, Set<OWLAxiom> removeAxioms, String outDirStr, ReasonerName reasonerName) 
		throws IOException, InterruptedException{
		
		if (minimalDiagnoses.contains(removeAxioms)){
			return true; //i.e diagnosis is minimal
		} else {
			return false;
		}
	}

	private static Boolean refineRepair(OWLOntology defectOntology, OWLOntology repairOntology, OWLAxiom axiom, Set<OWLAxiom> removeAxioms, String ontologyPath, String outDirStr, String save_filename, ReasonerName reasonerName, Scanner scanner) 
		throws OWLOntologyCreationException, OWLOntologyStorageException, IOException, EntityCheckerException, InterruptedException, ExecutionException{
		String user_in;
		
		ExecutorService executor = Executors.newSingleThreadExecutor();
		CheckMinimalityThread minimalityThread = new CheckMinimalityThread(defectOntology, axiom, removeAxioms, outDirStr, reasonerName);

    	Future<Boolean> isMinimal = executor.submit(minimalityThread); 
		

		while (!isMinimal.isDone()){
			LoadingScreen.main(null);
		}
		//if diagnosis is already computed, use that as the minimalDiagnoses else do the computation
		if (isMinimal.get().booleanValue()){
			try{
				saveRepairOntology(repairOntology, outDirStr, save_filename);
			} catch (Exception e){
				e.printStackTrace();
				return false;
			}
			System.out.println("Repair Saved!");
			return true; 
		} else {
			System.out.println("The resulting ontology is not maximal repair.\nEnter \"max\" to compute maximal or \"continue\" to save.");
			user_in = scanner.nextLine();
			if (user_in.toLowerCase().equals("continue")){
				try{
					saveRepairOntology(repairOntology, outDirStr, save_filename);
				} catch (Exception e){
					e.printStackTrace();
					return false;
				}
				System.out.println("Repair Saved!");
				return true;
			} else if(user_in.toLowerCase().equals("max")){
				List<Set<? extends OWLAxiom>> recommendedDiagnoses = recommendDiagnosisSet(minimalDiagnoses, removeAxioms);
				if (recommendedDiagnoses.size() < 2){
					Set<? extends OWLAxiom> selectedMinDiagnosis = recommendedDiagnoses.get(0);
					OWLOntology repairOntologyMax = computeRepair(selectedMinDiagnosis, ontologyPath);
					try{
						saveRepairOntology(repairOntologyMax, outDirStr, save_filename);
					} catch (Exception e){
						e.printStackTrace();
						return false;
					}
					System.out.println("Repair Saved!");
					return true;
				}
				for (Set<? extends OWLAxiom> diagnosisSet : recommendedDiagnoses){
					System.out.println("Diagnosis set: " + (recommendedDiagnoses.indexOf(diagnosisSet)+1));
					for (OWLAxiom diagAxiom : diagnosisSet){
						System.out.println(sOWLFormatter.format(diagAxiom).toString());
					}
				}
				System.out.println("Select a diagnosis set number to compute maximal repair: ");
				
				Set<? extends OWLAxiom> selectedMinDiagnosis = new HashSet<>();
				while(true){
					user_in = scanner.nextLine();
					try{
						selectedMinDiagnosis = recommendedDiagnoses.get(Integer.parseInt(user_in)-1);
						break;
					}catch(Exception e){
						System.out.println("Invalid input! Please select a valid diagnosis set number.");
						continue;
					}
				}				
				
				OWLOntology repairOntologyMax = computeRepair(selectedMinDiagnosis, ontologyPath);
				try{
					saveRepairOntology(repairOntologyMax, outDirStr, save_filename);														
				} catch (Exception e){
					e.printStackTrace();
					return false;
				}
				System.out.println("Repair Saved!");
				return true;
				
			} else {
				System.out.println("Invalid input!");
				return false;
			}
		}	
									
	}

	private static void saveRepairOntology(OWLOntology saveOntology, String outDirStr, String outFileNameStr) throws OWLOntologyCreationException, OWLOntologyStorageException, FileNotFoundException{
		OWLOntologyManager manager = saveOntology.getOWLOntologyManager();
		File outputFile = new File(HelperFunctions.getRepairFilePathStr(outDirStr, outFileNameStr + ".owl"));
		OWLDocumentFormat format = manager.getOntologyFormat(saveOntology);
		manager.saveOntology(saveOntology, format, new FileOutputStream(outputFile));
	}


/**
 * compute axiom weight of interesting axioms based on entailement in the repaired ontologies and update the axiomWeightMap
 * @param counter
 * @param outDirStr
 * @throws OWLOntologyCreationException
 */
	public static Map<OWLAxiom, Integer> computeAxiomWeight(Map<OWLAxiom, List<OWLOntology>> impAxiomRepMod, ReasonerName reasonerName, int totalRepairs) throws OWLOntologyCreationException{

		axiomWeightMap = new HashMap<>();
		
		//get the axiom weight of the interesting axioms
		for (OWLAxiom impAxiom : impAxiomRepMod.keySet()){
			axiomWeightMap.putIfAbsent(impAxiom, 0);
			List<OWLOntology> impAxiomSet = impAxiomRepMod.get(impAxiom);
			for (OWLOntology repModule : impAxiomSet){
				if (HelperFunctions.checkEntailment(repModule, impAxiom, reasonerName)){
					axiomWeightMap.put(impAxiom, axiomWeightMap.get(impAxiom)+1);
				} 		
			}
		}

		//get the axiom weight in percentage
		for (OWLAxiom axiom : axiomWeightMap.keySet()){
			axiomWeightMap.put(axiom, (axiomWeightMap.get(axiom)*100)/totalRepairs);
		}
		return axiomWeightMap;
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

/**
 * display temporarily that no repair possible due selection of axioms
 * @param justification
 * @throws UnsupportedEncodingException 
 */
	private static void displayNoRepair(Set<? extends OWLAxiom> justification) throws UnsupportedEncodingException{
		// axiomWeightOutputBuffer = new ByteArrayOutputStream();
		PrintStream bufferStream = new PrintStream(axiomWeightOutputBuffer, true, StandardCharsets.UTF_8.name());
		PrintStream originalOut = System.out;

		System.setOut(bufferStream);
		System.out.println("Repair not possible!");
		System.out.println("You have selected the following axioms to be in the repair which all belong to the same justification set.");
		for (OWLAxiom axiom : justification){
			System.out.println(sOWLFormatter.format(axiom).toString());
		}
		System.out.flush();
		System.setOut(originalOut); // Restore original output

		// Print the buffered output to the actual console
		System.out.print(axiomWeightOutputBuffer.toString(StandardCharsets.UTF_8.name()));
	}

/**
 * display the axiom weights of the interesting axioms
 * @param axiomWeightMap
 * @throws UnsupportedEncodingException 
 */
	private static void displayAxiomWeights(Map<OWLAxiom,Integer> axiomWeightMap) throws UnsupportedEncodingException{
		PrintStream bufferStream = new PrintStream(axiomWeightOutputBuffer, true, StandardCharsets.UTF_8.name());
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
		System.out.print(axiomWeightOutputBuffer.toString(StandardCharsets.UTF_8.name()));

    }

/**
 * write the initial, modified class hierarchy and the difference to a json file
 * @param hierarchies
 * @param outDirStr
 */
	private static void writeClassHierarchyDifferenceToFile(Map<OWLClass, Object> hierarchy1, Map<OWLClass, Object> hierarchy2, Map<String, Object> hierarchyDiff, String outDirStr){
		ObjectMapper mapper = new ObjectMapper();
		Map<String, Object> hierarchies = new HashMap<>();
		hierarchies.put("initialHierarchy", hierarchy1);
		hierarchies.put("modifiedHierarchy", hierarchy2);
		hierarchies.put("hierarchyDifference", hierarchyDiff);
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(outDirStr + File.separator + "classHierarchyDifference.json"), hierarchies);
        } catch (IOException e) {
            e.printStackTrace();
        }
	}

/**
 * display the class hierarchy difference
 * @param hierarchyDifferenceMap
 * @throws UnsupportedEncodingException 
 */
	private static void displayClassHierarchyDifference(Map<String, Object> hierarchyDifferenceMap) throws UnsupportedEncodingException {
		PrintStream bufferStream = new PrintStream(axiomWeightOutputBuffer, true, StandardCharsets.UTF_8.name());
		PrintStream originalOut = System.out;

		// Simulate printing class hierearchy difference
		System.setOut(bufferStream); // Redirect output
		
		StringJoiner hierarchyDiff = new StringJoiner("\n");
		hierarchyDiff.add("Class Hierarchy Difference:");
		Object removedObjects = hierarchyDifferenceMap.get("removed:");
		if (removedObjects instanceof Iterable && !((List<Map<OWLClass, Object>>) removedObjects).isEmpty()) {
			hierarchyDiff.add("Following sub-structures would be removed:");
			hierarchyDiff.add("==========================");
			for (Map<OWLClass, Object> removedSubTree : (List<Map<OWLClass, Object>>) removedObjects) {
				hierarchyDiff.add(printHierarchy(removedSubTree, "", new StringJoiner("\n")).toString());
				hierarchyDiff.add("----------------------");
			}
			hierarchyDiff.add("==========================");
		} 
		hierarchyDiff.add("");
		Object addedObjects = hierarchyDifferenceMap.get("added:");
			
		if (addedObjects instanceof Iterable && !((List<Map<OWLClass, Object>>) addedObjects).isEmpty()) {
			hierarchyDiff.add("Following sub-structures would be added:");
			hierarchyDiff.add("==========================");	
			for (Map<OWLClass, Object> addedSubTree : (List<Map<OWLClass, Object>>) addedObjects) {
				hierarchyDiff.add(printHierarchy(addedSubTree, "", new StringJoiner("\n")).toString());
				hierarchyDiff.add("----------------------");
			}
			hierarchyDiff.add("==========================");
		} 
			
		
		System.out.println(hierarchyDiff.toString());	
		System.out.flush();
		System.setOut(originalOut); // Restore original output

		// Print the buffered output to the actual console
		System.out.print(axiomWeightOutputBuffer.toString(StandardCharsets.UTF_8.name()));
	}

	private static StringJoiner printHierarchy(Map<OWLClass, Object> hierarchy, String indent, StringJoiner hierarchyStr) {
		for (Map.Entry<OWLClass, Object> entry : hierarchy.entrySet()) {
            OWLClass clazz = entry.getKey();
            Object value = entry.getValue();
            
            // Print the current class with proper indentation
            hierarchyStr.add(indent + clazz.getIRI().getShortForm());
            
            // If the value is a List, recursively print each child
            if (value instanceof List) {
                List<Map<OWLClass, Object>> childList = (List<Map<OWLClass, Object>>) value;
                for (Map<OWLClass, Object> child : childList) {
                    printHierarchy(child, indent+ "\t", hierarchyStr);  // Increase indentation
                }
            } else {
                // If it's another map (e.g., a nested class), recurse on it
				if (value instanceof OWLClass){
					hierarchyStr.add(indent + "\t" + ((OWLClass) value).getIRI().getShortForm());
				} else if (value == null){ 
					hierarchyStr.add(indent + "\t" + "\u22A5");
				} else {
					hierarchyStr.add(indent + "\t" + value.toString());
				}
				
                
            }
        }
		return hierarchyStr;
	}

/**
 * overwrite the given output in console with blank lines 
 * @param output
  * @throws InterruptedException 
  */
	private static void overwriteWithBlankLines(String output) throws InterruptedException {
		int lineCount = output.split("\n").length;

        for (int i = 0; i < lineCount+3; i++) {
            System.out.print("\033[F");// Move cursor
            System.out.print("\033[2K"); // Clear line
        }
    }

/**
 * recommend the minimal diagnosis set that is a subset of the selected diagnosis set
 * @param allMinimalOptimalDiagnoses
 * @param repairDiagnosis
 * @return
 * @throws IOException
 * @throws EntityCheckerException
 * @throws OWLOntologyCreationException
 * @throws OWLOntologyStorageException
 * @throws InterruptedException
 */
	private static List<Set<? extends OWLAxiom>> recommendDiagnosisSet(Set<Set<? extends OWLAxiom>> allMinimalOptimalDiagnoses, Set<? extends OWLAxiom> repairDiagnosis) throws IOException, EntityCheckerException, OWLOntologyCreationException, OWLOntologyStorageException, InterruptedException{
		List<Set<? extends OWLAxiom>> recommendationList =  new ArrayList<>();
		for (Set<? extends OWLAxiom> diagSet : allMinimalOptimalDiagnoses) {
			if(repairDiagnosis.containsAll(diagSet)){
				recommendationList.add(diagSet);
			}
		}
        return recommendationList;
    
	}

	public static void entropySorting(Set<Set<? extends OWLAxiom>> allJustifications, Set<Set<? extends OWLAxiom>> minimalDiagnoses){
		//get entropy score for each axiom in the justification sets: 
		//entropyscore = p(Y) log2 p(Y) + p(N) log2 p(N) + 1
		Map<OWLAxiom, Double> entropyScoreMap = new HashMap<>();
		for (Set<? extends OWLAxiom> justificationSet : allJustifications){
			for (OWLAxiom justAxiom : justificationSet){
				if (entropyScoreMap.containsKey(justAxiom)){
					continue;
				}
				entropyScoreMap.putIfAbsent(justAxiom, 0.0);
				int Dp = 0;
				int Dn = 0;
				for (Set<? extends OWLAxiom> diagSet : minimalDiagnoses){
					if (diagSet.contains(justAxiom)){
						Dp++;
					} else if (!diagSet.contains(justAxiom)){
						Dn++;
					} 
				}
				double pYProb = (double) Dp / minimalDiagnoses.size();
				double pNProb = (double) Dn / minimalDiagnoses.size();
				double entropyScore = (pYProb * Math.log(pYProb) / Math.log(2)) + (pNProb * Math.log(pNProb) / Math.log(2)) + 1;
				entropyScoreMap.put(justAxiom, entropyScore);
			}
		}
		System.out.println("Entropy scores:" + entropyScoreMap);
	}

	public static Map<OWLOntology, Integer> getPreferredRepair(Set<OWLAxiom> keepAxioms, Set<OWLAxiom> removeAxioms, String outDirStr, String ontologyPath, Set<? extends OWLAxiom> interestingAxiomsSet, ReasonerName reasonerName) throws IOException, OWLOntologyCreationException, EntityCheckerException{
		Map<OWLOntology, Integer> preferredRepair = new HashMap<>();
		Set<Set<? extends OWLAxiom>> allOptimalDiagnoses = computeDiagnosis(allJustifications, keepAxioms, removeAxioms, outDirStr);
		int max_entailed = 0;
		OWLOntology preferredOntology = null;
		for (Set<? extends OWLAxiom> diagnosisSet : allOptimalDiagnoses){
			int ia_entailment_count = 0;
			OWLOntology repairOntology = computeRepair(diagnosisSet, ontologyPath);
			for (OWLAxiom ia : interestingAxiomsSet){
				if (HelperFunctions.checkEntailment(repairOntology, ia, reasonerName)){
					ia_entailment_count++;
				}
			}
			if (ia_entailment_count > max_entailed){
				max_entailed = ia_entailment_count;
				preferredOntology = repairOntology;
			}
		}
		preferredRepair.put(preferredOntology, max_entailed);
		return preferredRepair;
	}

	public static double computeHammingDistance(OWLOntology currentOntology, OWLOntology preferredRepair){
		Set<OWLAxiom> intersection = new HashSet<>(currentOntology.getAxioms());
		intersection.retainAll(preferredRepair.getAxioms());
		Set<OWLAxiom> union = new HashSet<>(currentOntology.getAxioms());
		union.addAll(preferredRepair.getAxioms());
		double hammingDistance = 1 - ((double) intersection.size() / (double) union.size());
		return hammingDistance;
	}

	public static String hammingDistance(OWLAxiom justAxiom, Set<OWLAxiom> removeAxioms, Set<OWLAxiom> keepAxioms, Set<? extends OWLAxiom> interestingAxiomsSet, String ontologyPath, ReasonerName reasonerName, String outDirStr) throws OWLOntologyCreationException, IOException, EntityCheckerException{
		OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
		OWLOntology ontology = manager.loadOntologyFromOntologyDocument(new File(ontologyPath));
		manager.removeAxioms(ontology, removeAxioms);

		Set<OWLAxiom> updKeepAxioms = new HashSet<>(keepAxioms);
		updKeepAxioms.add(justAxiom);
		Map<OWLOntology, Integer> preferredRepair_yes = getPreferredRepair(updKeepAxioms, removeAxioms, outDirStr, ontologyPath, interestingAxiomsSet, reasonerName);
		OWLOntology ontology_yes = preferredRepair_yes.keySet().iterator().next();
		int max_entailed_yes = preferredRepair_yes.get(ontology_yes);
		
		PrintStream bufferStream = new PrintStream(axiomWeightOutputBuffer, true, StandardCharsets.UTF_8.name());
		PrintStream originalOut = System.out;

		// Simulate printing class hierearchy difference
		System.setOut(bufferStream);

		//hamming distance: 1 - (size of intersection of axiom sets / size of union of axiom sets))
		
		System.out.println("Hamming distance when answer \"yes\" to the query: " + computeHammingDistance(ontology, ontology_yes));
		System.out.println("Maximum number of interesting axioms entailed by the repair when answer \"yes\": " + max_entailed_yes);

		Set<OWLAxiom> updRemoveAxioms = new HashSet<>(removeAxioms);
		updRemoveAxioms.add(justAxiom);
		Map<OWLOntology, Integer> preferredRepair_no = getPreferredRepair(keepAxioms, updRemoveAxioms, outDirStr, ontologyPath, interestingAxiomsSet, reasonerName);
		OWLOntology ontology_no = preferredRepair_no.keySet().iterator().next();
		int max_entailed_no = preferredRepair_no.get(ontology_no);
		

		//hamming distance: 1 - (size of intersection of axiom sets / size of union of axiom sets))
		
		System.out.println("Hamming distance when answer \"no\" to the query: " + computeHammingDistance(ontology, ontology_no));
		System.out.println("Maximum number of interesting axioms entailed by the repair when answer \"no\": " + max_entailed_no);

		System.out.flush();
		System.setOut(originalOut);

		System.out.print(axiomWeightOutputBuffer.toString(StandardCharsets.UTF_8.name()));
		String bufferString = axiomWeightOutputBuffer.toString();
		axiomWeightOutputBuffer.reset();
		return bufferString;
	}

/**
 * based on the reasoner selected, invoke the async method to compute justifications
 * @param reasonerName
 * @param axiom
 * @param ontology
 * @param queue
 * @return
 */
	public static Set<Set<? extends OWLAxiom>> getAllJustificationsAsync(ReasonerName reasonerName, OWLAxiom axiom, OWLOntology ontology, BlockingQueue<Set<? extends OWLAxiom>> queue) {
		if (reasonerName == ReasonerName.Elk){
		 	return JustificationsGenerator.getAllELKJustificationsAsync(axiom, ontology, queue);
		}

		return JustificationsGenerator.getAllHermitJustificationsAsync(axiom, ontology);
	}

	public static void main(Object[] args){
		OWLAxiom defect = (OWLAxiom) args[0];
		OWLOntology ontology = (OWLOntology) args[1];
		OWLOntology interestingAxiomOntology = (OWLOntology) args[2];
		ReasonerName reasonerName = (ReasonerName) args[3];
		String outDirStr = (String) args[4];
		String ontologyPath = (String) args[5];

		try{
			computeRepairOntology(defect, ontology, interestingAxiomOntology, reasonerName, outDirStr, ontologyPath);
		} catch(Exception e){
			e.printStackTrace();
		}
	}

}
