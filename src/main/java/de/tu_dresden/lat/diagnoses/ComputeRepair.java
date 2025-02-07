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
import java.nio.file.Files;
import java.security.cert.CertPathValidatorException.Reason;
import java.util.ArrayList;
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
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLDocumentFormat;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;


import de.tu_dresden.inf.lat.prettyPrinting.formatting.SimpleOWLFormatterCl;
import de.tu_dresden.inf.lat.exceptions.EntityCheckerException;

import de.tu_dresden.inf.lat.prettyPrinting.formatting.SimpleDLFormatter$;
import de.tu_dresden.lat.data.enums.ExitCode;
import de.tu_dresden.lat.data.names.ReasonerName;
import de.tu_dresden.lat.tools.LoadingScreen;


public class ComputeRepair {
	private static final Logger logger = Logger.getLogger(ComputeRepair.class);

	public static Boolean isSnapshotActive;
	public static Map<OWLAxiom, String> axioms2Identifiers;	
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

		if (outDirStr.isEmpty())
			outDirStr = "defaultRepairFolder";

		sOWLFormatter.setReferenceOntology(ontology);
		Set<OWLAxiom> interestingAxiomsSet = interestingAxiomOntology.getAxioms();
		allJustifications = new CopyOnWriteArraySet<>();
		justificationQueue = new LinkedBlockingQueue<>();
		axiomMap = new ConcurrentHashMap<>();
		Set<OWLAxiom> keepAxioms = new HashSet<>();
		Set<OWLAxiom> removeAxioms = new HashSet<>();
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
			Scanner scanner = new Scanner(System.in);
			
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
							// System.out.println(sOWLFormatter.format(justificationAxiom).toString() + " -- selection already made for the axiom!");
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
										Set<? extends OWLAxiom> selectedJustification = checkAxiomSelection(allJustifications, keepAxioms);
										if (selectedJustification != null){
											System.out.println("Repair not possible!");
											System.out.println("You have selected the following axioms to be in the repair which all belong to the same justification set.");
											for (OWLAxiom selectedAxiom : selectedJustification){
												System.out.println(sOWLFormatter.format(selectedAxiom).toString());
											}
										} else {
											getAxiomWeight(justificationAxiom, allJustifications, outDirStr, ontologyPath, interestingAxiomsSet, keepAxioms, removeAxioms, reasonerName);
											displayAxiomWeights(axiomWeightMap);
											cleanup(outDirStr+"/tempRepairsFolder");
										}

										scanner.nextLine();

										if (axiomWeightOutputBuffer.size() > 0){
											overwriteWithBlankLines(axiomWeightOutputBuffer.toString());
											axiomWeightOutputBuffer.reset();
											continue;
										}
									}
								case "save":
									{
										computeJustificationsThread.join();
											
										System.out.println("Enter the filename to save as: ");
										String save_filename = scanner.nextLine();

										OWLOntology repairOntology = computeRepair(removeAxioms, ontologyPath);
										if (HelperFunctions.checkEntailment(repairOntology, axiom, reasonerName)){
											inputFlag = noRepair(repairOntology, outDirStr, save_filename, scanner);											
										} else {
											inputFlag = refineRepair(ontology, repairOntology, axiom, removeAxioms, ontologyPath, outDirStr, save_filename, reasonerName, scanner);
										}
										if (!inputFlag){
											break;
										} else {
											continue;
										}
										
									}
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
							System.out.println("Enter the filename to save as: ");
							String save_filename = scanner.nextLine();

							OWLOntology repairOntology = computeRepair(removeAxioms, ontologyPath);
							if (HelperFunctions.checkEntailment(repairOntology, axiom, reasonerName)){
								inputFlag = noRepair(repairOntology, outDirStr, save_filename, scanner);											
							} else {
								inputFlag = refineRepair(ontology, repairOntology, axiom, removeAxioms, ontologyPath, outDirStr, save_filename, reasonerName, scanner);
							}
							if (!inputFlag){
								break;
							} else {
								continue;
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

		return ecode;
		
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
	public static Set<Set<? extends OWLAxiom>> computeDiagnosis(Optional<OWLAxiom> selectedAxiom, Set<Set<? extends OWLAxiom>> allJustifications, Set<OWLAxiom> keepAxioms, Set<OWLAxiom> removeAxioms, String outDirStr) throws IOException{
		Set<Set<? extends OWLAxiom>> allOptimalDiagnoses = new HashSet<>();
		String mDsID = "repair";
		logger.info("Creating program");
		SolveProgramHelpers.createProgram(allJustifications, outDirStr, axioms2Identifiers, identifiers2Axioms, programFileName);

		if (selectedAxiom.isPresent()){
			keepAxioms.add(selectedAxiom.get());
		}
		applyUserSelection(keepAxioms, removeAxioms, outDirStr);

		if (selectedAxiom.isPresent()){
			keepAxioms.remove(selectedAxiom.get());
		}

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
	private static void getAxiomWeight(OWLAxiom selectedAxiom, Set<Set<? extends OWLAxiom>> allJustifications, String outDirStr, String ontologyPath, Set<OWLAxiom> interestingAxiomsSet, Set<OWLAxiom> keepAxioms, Set<OWLAxiom> removeAxioms, ReasonerName reasonerName) throws IOException, EntityCheckerException, OWLOntologyCreationException, OWLOntologyStorageException{
		Set<Set<? extends OWLAxiom>> allOptimalDiagnoses = computeDiagnosis(Optional.of(selectedAxiom), allJustifications, keepAxioms, removeAxioms, outDirStr);

		try{
			ComputeAxiomWeightThread runnable2 = new ComputeAxiomWeightThread(outDirStr, "repair", ontologyPath, "repairOntology", allOptimalDiagnoses, interestingAxiomsSet, reasonerName);
			Thread axiomWeightThread = new Thread(runnable2); 
			axiomWeightThread.start();
			
			while(axiomWeightThread.isAlive()){
				LoadingScreen.main(null);
			}
			axiomWeightThread.join();
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
 * computes the repair ontologies for each diagnosis set and invokes the method to compute axiom weight
 * @param outDirStr
 * @param mDsID
 * @param ontologyPath
 * @param outputFileName
 * @throws IOException
 * @throws EntityCheckerException
 * @throws OWLOntologyCreationException
 * @throws OWLOntologyStorageException
 */
	public static int computeRepairs(String outDirStr, String mDsID, String ontologyPath, String outputFileName, Set<Set<? extends OWLAxiom>> allOptimalDiagnoses) throws IOException, EntityCheckerException, OWLOntologyCreationException, OWLOntologyStorageException{
		
		File repFolder = new File(outDirStr);
		repFolder.mkdir();
		int counter = 0;

		//from the produced diagnoses set, compute repair ontology for each diagnosis set and save as ontology
		for (Set<? extends OWLAxiom> axiomSets : allOptimalDiagnoses){
			OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
			OWLOntology ontology = manager.loadOntologyFromOntologyDocument(new File(ontologyPath));

			for (OWLAxiom axiom: axiomSets){
				manager.removeAxiom(ontology, axiom);
			}
			File outputFile = new File(HelperFunctions.getRepairFilePathStr(outDirStr, outputFileName+"_"+Integer.toString(counter)));
			OWLDocumentFormat format = manager.getOntologyFormat(ontology);
			manager.saveOntology(ontology, format, new FileOutputStream(outputFile));
			counter += 1;
		}
		return counter;
	}

/**
 * compute the repair ontology for the given diagnosis set	
 * @param selectedMinDiagnosis
 * @param ontologyPath
 * @return
 * @throws OWLOntologyCreationException
 */
	private static OWLOntology computeRepair(Set<? extends OWLAxiom> selectedMinDiagnosis, String ontologyPath) throws OWLOntologyCreationException{
			OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
			OWLOntology repairOntology = manager.loadOntologyFromOntologyDocument(new File(ontologyPath));
	
				for (OWLAxiom axiom: selectedMinDiagnosis){
				manager.removeAxiom(repairOntology, axiom);
			}
		
		return repairOntology;
	}

/**
 * if the resulting ontology is not a repair, prompt the user to save or cancel
 * @param resultOntology
 * @param outDirStr
 * @param save_filename
 * @param scanner
 * @return true if the ontology not saved, false if the user saves the ontology (indicating the inputFlag)
 */
	private static Boolean noRepair(OWLOntology resultOntology, String outDirStr, String save_filename, Scanner scanner){
		String user_in;
		System.out.println("The resulting ontology is not a repair.\nEnter \"continue\" to save the ontology or \"cancel\" to cancel.");
		user_in = scanner.nextLine();
		if (user_in.toLowerCase().equals("cancel")){
			System.out.println("Cancelling save!");
			return true;
		} else if (user_in.toLowerCase().equals("continue")){
			try{
				saveRepairOntology(resultOntology, outDirStr, save_filename);
				return false;
			} catch (Exception e){
				e.printStackTrace();
				return true;
			}															
		} else {
			System.out.println("Invalid input!");
			return true;
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
		ASPMinimalDiagnoses.getAllMinimalDiagnoses(defectAxiom, defectOntology, "minimal", outDirStr, new HashSet<>(), reasonerName);
		if (ASPMinimalDiagnoses.allOptimalDiagnosesMin.contains(removeAxioms)){
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
		Set<Set<? extends OWLAxiom>> minimalDiagnoses = new HashSet<>(ASPMinimalDiagnoses.allOptimalDiagnosesMin);
		if (isMinimal.get().booleanValue()){
			try{
				saveRepairOntology(repairOntology, outDirStr, save_filename);
			} catch (Exception e){
				e.printStackTrace();
				return true;
			}
			System.out.println("Repair Saved!");
			return false; //i.e inputFlag = false
		} else {
			System.out.println("The resulting ontology is not maximal repair.\nEnter \"max\" to compute maximal or \"continue\" to save.");
			user_in = scanner.nextLine();
			if (user_in.toLowerCase().equals("continue")){
				try{
					saveRepairOntology(repairOntology, outDirStr, save_filename);
				} catch (Exception e){
					e.printStackTrace();
					return true;
				}
				System.out.println("Repair Saved!");
				return false;
			} else if(user_in.toLowerCase().equals("max")){
				List<Set<? extends OWLAxiom>> recommendedDiagnoses = recommendDiagnosisSet(minimalDiagnoses, removeAxioms);
				if (recommendedDiagnoses.size() < 2){
					Set<? extends OWLAxiom> selectedMinDiagnosis = recommendedDiagnoses.get(0);
					OWLOntology repairOntologyMax = computeRepair(selectedMinDiagnosis, ontologyPath);
					try{
						saveRepairOntology(repairOntologyMax, outDirStr, save_filename);
					} catch (Exception e){
						e.printStackTrace();
						return true;
					}
					System.out.println("Repair Saved!");
					return false;
				}
				for (Set<? extends OWLAxiom> diagnosisSet : recommendedDiagnoses){
					System.out.println("Diagnosis set: " + (recommendedDiagnoses.indexOf(diagnosisSet)+1));
					for (OWLAxiom diagAxiom : diagnosisSet){
						System.out.println(sOWLFormatter.format(diagAxiom).toString());
					}
				}
				System.out.println("Select a diagnosis set number to compute maximal repair: ");
				
				Set<? extends OWLAxiom> selectedMinDiagnosis;
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
					return true;
				}
				System.out.println("Repair Saved!");
				return false;
				
			} else {
				System.out.println("Invalid input!");
				return true;
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
 * cleanup the temporary repair ontologies folder
 * @param outDirStr
 */
	private static void cleanup(String outDirStr){
		System.gc();  // Force JVM to release file locks
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		File repFolder = new File(outDirStr);
		File[] allContents = repFolder.listFiles();
			if (allContents != null) {
				for (File delfile : allContents) {
					
					try {
						Files.deleteIfExists(delfile.toPath());
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
			repFolder.delete();
	}

/**
 * compute axiom weight of interesting axioms based on entailement in the repaired ontologies and update the axiomWeightMap
 * @param counter
 * @param outDirStr
 * @throws OWLOntologyCreationException
 */
	public static Map<OWLAxiom, Integer> computeAxiomWeight(int counter, String outDirStr, Set<OWLAxiom> interestingAxiomsSet, ReasonerName reasonerName) throws OWLOntologyCreationException{

		axiomWeightMap = new HashMap<>();
		
		//get the axiom weight of the interesting axioms
		for (OWLAxiom interestingAxiom : interestingAxiomsSet){
			axiomWeightMap.putIfAbsent(interestingAxiom, 0);
			for (int i=1; i<counter; i++){
				String ontologyFileName = "repairOntology_"+Integer.toString(i);
				String outputFile = HelperFunctions.getRepairFilePathStr(outDirStr, ontologyFileName);
				OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
				OWLOntology ontology = manager.loadOntologyFromOntologyDocument(new File(outputFile));
				if (HelperFunctions.checkEntailment(ontology, interestingAxiom, reasonerName)){
					axiomWeightMap.put(interestingAxiom, axiomWeightMap.get(interestingAxiom)+1);
				}
				manager.removeOntology(ontology);
			}
		}

		//get the axiom weight in percentage
		for (OWLAxiom axiom : axiomWeightMap.keySet()){
			axiomWeightMap.put(axiom, (axiomWeightMap.get(axiom)*100)/counter);
		}
		System.out.println("Axiom weight" + axiomWeightMap);
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
 * display the axiom weights of the interesting axioms
 * @param axiomWeightMap
 * @throws InterruptedException
 * @throws UnsupportedEncodingException 
 */
	private static void displayAxiomWeights(Map<OWLAxiom,Integer> axiomWeightMap) throws InterruptedException, UnsupportedEncodingException{
		axiomWeightOutputBuffer = new ByteArrayOutputStream();
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
 * overwrite the given output in console with blank lines 
 * @param output
 */
    private static void overwriteWithBlankLines(String output) {
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

}
