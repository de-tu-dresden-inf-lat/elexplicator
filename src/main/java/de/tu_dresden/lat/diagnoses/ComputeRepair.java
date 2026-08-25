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
import java.util.Collections;
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
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.log4j.Logger;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
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
import de.tu_dresden.lat.api.ElExplicatorApplication;
import de.tu_dresden.lat.api.RepairSession;
import de.tu_dresden.lat.data.enums.ExitCode;
import de.tu_dresden.lat.data.enums.SortMethod;
import de.tu_dresden.lat.data.names.ReasonerName;
import de.tu_dresden.lat.tools.LoadingScreen;


public class ComputeRepair {
	private static final Logger logger = Logger.getLogger(ComputeRepair.class);

	public static Map<OWLAxiom, String> axioms2Identifiers;	
	public static Map<String, OWLAxiom> identifiers2Axioms;
	public static Set<Set<? extends OWLAxiom>> allJustifications;
	public static BlockingQueue<Set<? extends OWLAxiom>> justificationQueue;
	public static BlockingQueue<String> tempFiles;
	public static ConcurrentHashMap<OWLAxiom, Double> axiomMap = new ConcurrentHashMap<>();
	public static AtomicReference<ConcurrentLinkedQueue<OWLAxiom>> orderedAxiomsCMD = new AtomicReference<>(new ConcurrentLinkedQueue<>()); 
	public static AtomicReference<ConcurrentLinkedQueue<OWLAxiom>> orderedAxiomsAPI = new AtomicReference<>(new ConcurrentLinkedQueue<>());
	public static Map<OWLAxiom,Double> axiomWeightMap;
	public static volatile Boolean justificationsCompleted;
	public static volatile Boolean sortingCompleted;
	private static ByteArrayOutputStream axiomWeightOutputBuffer = new ByteArrayOutputStream();

	public static final String programFileName = "pi.txt";


	private static SimpleOWLFormatterCl sOWLFormatter = new SimpleOWLFormatterCl(true, SimpleDLFormatter$.MODULE$,
		true);

	private static Boolean signal = true;
	private static Boolean inputFlag = true;

	private static Boolean repairCheck = true;
	public static volatile Boolean diagnosisComputed;
	public static Set<Set <? extends OWLAxiom>> minimalDiagnoses = new HashSet<>();
	public static Set<Set <? extends OWLAxiom>> allDiagnoses = new HashSet<>();
	
	public static ReasonerName reasonerName;
	private static RepairSession session;

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
 * @throws InterruptedException 
 */
	public static ExitCode computeRepairOntology(OWLAxiom axiom, OWLOntology ontology, OWLOntology interestingAxiomOntology, ReasonerName rName, String outDirStr, String ontologyPath, SortMethod sortMethod, Boolean liveSort, Boolean visualize) throws IOException, EntityCheckerException, OWLOntologyCreationException, OWLOntologyStorageException, InterruptedException{
	
		ExitCode ecode = ExitCode.terminatedSuccessfully;
		Runtime.getRuntime().addShutdownHook(new Thread(()->{
			System.out.println("Shutting down");
			signal = false;
			inputFlag = false;
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

		if (visualize){
			session = new RepairSession();
			session.startRepair(axiom, outDirStr, ontologyPath, reasonerName, ontology, interestingAxiomsSet);
			ElExplicatorApplication.setRepairSession(session);
			try {
				ElExplicatorApplication.main(new String[] { "server", "config.yml" });
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		justificationsCompleted = false;
		diagnosisComputed = false;
		sortingCompleted = false;

		reasonerName = rName;
		OWLOntology defectModule = null;
		if (reasonerName==ReasonerName.Elk){
			defectModule =  Segmenter.getStarModule(ontology, axiom.getSignature(),
				ontology.getOntologyID().getOntologyIRI().isPresent() ? ontology.getOntologyID().getOntologyIRI().get()
						: IRI.create("http://example.org/temp-ontology"));
		} else {
			defectModule =  SegmenterHermit.getStarModule(ontology, axiom.getSignature(),
				ontology.getOntologyID().getOntologyIRI().isPresent() ? ontology.getOntologyID().getOntologyIRI().get()
						: IRI.create("http://example.org/temp-ontology"));
		}
		ExecutorService executor = Executors.newFixedThreadPool(4);

		Future<?> justificationFuture = executor.submit(new ComputeJustificationsThread(reasonerName, axiom, defectModule));

		Future<?> diagnosisFuture = executor.submit(new ComputeDiagnosisThread(ontology, axiom, outDirStr, reasonerName));
		Future<?> decisionTreeFuture = null;
		if (visualize){
			decisionTreeFuture = executor.submit(new BuildDecisionTreeThread(session));
		}
		

		if (!liveSort){
			//loading screen till the justifications are computed
			while (!justificationFuture.isDone() || !diagnosisFuture.isDone()){
				checkFuture(justificationFuture);
				checkFuture(diagnosisFuture);
				LoadingScreen.main(null);
			}
			waitForFuture(diagnosisFuture);
		}
		Future<?> sortingFuture;
		if (sortMethod == SortMethod.Frequency){
			sortingFuture = executor.submit(new FrequencySortingThread());
		} else {
			sortingFuture = executor.submit(new EntropySortingThread());
		}	
		if (!liveSort){
			waitForFuture(sortingFuture);
			if (visualize){
				waitForFuture(decisionTreeFuture);
			}
		}
		try{			
			Scanner scanner = new Scanner(System.in);
			List<OWLAxiom> orderAxiomsSSPrev = null;
			while(inputFlag){
				if (liveSort){
					checkFuture(justificationFuture);
					checkFuture(diagnosisFuture);
					checkFuture(sortingFuture);
					if (visualize){
						checkFuture(decisionTreeFuture);
					}
				}				
				while (!orderedAxiomsCMD.get().isEmpty() || !sortingFuture.isDone()){
					
					while (orderedAxiomsCMD.get().isEmpty()){
						LoadingScreen.main(null);
						if(sortingFuture.isDone()){
							break;
						}
					}
					ConcurrentLinkedQueue<OWLAxiom> snapshotRef = orderedAxiomsCMD.get();
					List<OWLAxiom> orderedAxiomSS = new ArrayList<>(snapshotRef);
					orderedAxiomsCMD.compareAndSet(snapshotRef, new ConcurrentLinkedQueue<>());
					if (orderAxiomsSSPrev != null){
						orderedAxiomSS.removeAll(orderAxiomsSSPrev);
					}
										
					for (OWLAxiom justificationAxiom : orderedAxiomSS){
						if(keepAxioms.contains(justificationAxiom) | removeAxioms.contains(justificationAxiom) | justificationAxiom.equals(axiom)){
							if (justificationAxiom.equals(axiom)){
								removeAxioms.add(justificationAxiom);
							}
							continue;
						}
						while (signal){
							System.out.println(sOWLFormatter.format(justificationAxiom).toString());
							System.out.println("\u0007");
							System.out.flush();
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
																"3. Hamming distance of current ontology to a candidate repair";
									System.out.println(bufferedString);
									System.out.println("\u0007");
									System.out.flush();
									String userSelections = scanner.nextLine();
									String[] selections = userSelections.split(",");
									for (String opt : selections){
										switch(opt.trim()){
											case "1":{
												// computeJustificationsThread.join();
												waitForFuture(justificationFuture);
												waitForFuture(diagnosisFuture);
												bufferedString += computeProbabilities(justificationAxiom, keepAxioms, removeAxioms, outDirStr, ontologyPath, interestingAxiomsSet, reasonerName, Optional.empty());
												break;
											}
											case "2":{
												bufferedString += computeHierarchyDiff(axiom, keepAxioms, removeAxioms, outDirStr, ontologyPath, justificationAxiom, reasonerName, Optional.empty());
												break;
											}
											case "3":{
												// computeJustificationsThread.join();
												waitForFuture(justificationFuture);
												waitForFuture(diagnosisFuture);
												bufferedString += hammingDistance(justificationAxiom, removeAxioms, keepAxioms, interestingAxiomsSet, ontologyPath, reasonerName, outDirStr, Optional.empty());
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
									System.out.println("\u0007");
									System.out.flush();
									scanner.nextLine();

									if (bufferedString.length() > 0){
										overwriteWithBlankLines(bufferedString);
									}
									continue;
								}
								case "save":{
									// computeJustificationsThread.join();
									waitForFuture(justificationFuture);
									
									System.out.println("Enter the filename to save as: ");
									System.out.println("\u0007");
									System.out.flush();
									String save_filename = scanner.nextLine();

									waitForFuture(diagnosisFuture);
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
									justificationFuture.cancel(true);
									diagnosisFuture.cancel(true);
									sortingFuture.cancel(true);
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
							// computeDiagnosisThread.join();
							waitForFuture(diagnosisFuture);
							Boolean repairStatus = checkRepair(axiom, ontology, removeAxioms, outDirStr, reasonerName, ontologyPath, scanner);
							if (repairStatus){
								inputFlag = false;
								break;
							}
							// entropySorting(allJustifications, minimalDiagnoses);
						}

						if (!inputFlag){break;}
					}
					orderAxiomsSSPrev = new ArrayList<>(orderedAxiomSS);
					if (!inputFlag){break;}
				}
				if (!inputFlag){
					break;
				}
				
				while(signal){
					System.out.println("All justifications have been computed.\nWould you like to exit or save the repair?");
					System.out.println("\u0007");
					System.out.flush();
					String user_in = scanner.nextLine();
					switch(user_in.toLowerCase()){
						case "exit":{
							System.out.println("Exiting repair mode!");
							inputFlag = false;
							break;
						}
						case "save":{
							System.out.println("Enter the filename to save as: ");
							System.out.println("\u0007");
							System.out.flush();
							String save_filename = scanner.nextLine();

							// computeJustificationsThread.join();
							waitForFuture(justificationFuture);
							waitForFuture(diagnosisFuture);
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
			justificationFuture.cancel(true);
			diagnosisFuture.cancel(true);
			sortingFuture.cancel(true);
			decisionTreeFuture.cancel(true);
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
	public static String computeProbabilities(OWLAxiom justificationAxiom, Set<OWLAxiom> keepAxioms, Set<OWLAxiom> removeAxioms, String outDirStr, String ontologyPath, Set<? extends OWLAxiom> interestingAxiomsSet, ReasonerName reasonerName, Optional<String> nodeId) throws IOException, OWLOntologyCreationException, OWLOntologyStorageException, EntityCheckerException{
		//entailment probability when retaining the justification axiom
		String bufferedString = "";
		axiomWeightOutputBuffer.write("\n\t\t1. Entailment probability\n".getBytes());
		axiomWeightOutputBuffer.write("==========================\n".getBytes());
		axiomWeightOutputBuffer.write("\tAnswer = yes:\n".getBytes());
		Set<OWLAxiom> updatedKeepAxioms = new HashSet<>(keepAxioms);
		updatedKeepAxioms.add(justificationAxiom);
		Boolean entailment_yes = getEntailmentProbability(allJustifications, updatedKeepAxioms, removeAxioms, outDirStr, ontologyPath, interestingAxiomsSet, reasonerName);
		Map<OWLAxiom, Double> axiomWeightYes = new HashMap<>(axiomWeightMap);
		bufferedString += axiomWeightOutputBuffer.toString();								
		axiomWeightOutputBuffer.reset();
		
		//entailment probability when removing the justification axiom
		axiomWeightOutputBuffer.write("\tAnswer = no:\n".getBytes());
		Set<OWLAxiom> updatedRemoveAxioms = new HashSet<>(removeAxioms);
		updatedRemoveAxioms.add(justificationAxiom);
		Boolean entailment_no = getEntailmentProbability(allJustifications, keepAxioms, updatedRemoveAxioms, outDirStr, ontologyPath, interestingAxiomsSet, reasonerName);
		
		Map<OWLAxiom, Double> axiomWeightNo = new HashMap<>(axiomWeightMap);
		//write the axiom weight map to the json file as value for key "yes"
		bufferedString += axiomWeightOutputBuffer.toString();									
		axiomWeightOutputBuffer.reset();

		if (nodeId.isPresent()) {
			writeProbabilitiesToFile(entailment_yes, entailment_no, axiomWeightYes, axiomWeightNo, outDirStr, Optional.of(nodeId.get()));
		} else {
			writeProbabilitiesToFile(entailment_yes, entailment_no, axiomWeightYes, axiomWeightNo, outDirStr, Optional.empty());
		}

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
	public static String computeHierarchyDiff(OWLAxiom defect, Set<OWLAxiom> keepAxioms, Set<OWLAxiom> removeAxioms, String outDirStr, String ontologyPath, OWLAxiom justificationAxiom, ReasonerName reasonerName, Optional<String> nodeId) throws IOException, OWLOntologyCreationException, OWLOntologyStorageException{
		String bufferedString = "";
		ClassHierarchyDifference classHierarchyDifference = new ClassHierarchyDifference(allJustifications, outDirStr, ontologyPath, keepAxioms, removeAxioms, justificationAxiom, reasonerName);
		classHierarchyDifference.getClassHierarchy(ontologyPath);
		if (nodeId.isPresent()){
			writeClassHierarchyDifferenceToFile(classHierarchyDifference.hierarchyMap1, classHierarchyDifference.hierarchyMap2, classHierarchyDifference.hierarchyDifference, classHierarchyDifference.repairYes, classHierarchyDifference.repairNo, outDirStr, Optional.of(nodeId.get()));
		} else {
			writeClassHierarchyDifferenceToFile(classHierarchyDifference.hierarchyMap1, classHierarchyDifference.hierarchyMap2, classHierarchyDifference.hierarchyDifference, classHierarchyDifference.repairYes, classHierarchyDifference.repairNo, outDirStr, Optional.empty());
		}
		
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
 * @return unsatisfiable justification set
 */
	public static Set<? extends OWLAxiom> checkAxiomSelection(Set<Set<? extends OWLAxiom>> allJustifications, Set<OWLAxiom> keepAxioms){
		for (Set<? extends OWLAxiom> justificationSet : allJustifications){
			if (keepAxioms.containsAll(justificationSet)){
				return justificationSet;
			} 
		}
		return null;
	}

	public static Set<Set<? extends OWLAxiom>> getAvailableDiagnoses(Set<OWLAxiom> keepAxioms, Set<OWLAxiom> removeAxioms){
		Set<Set <? extends OWLAxiom>> availableDiag = new HashSet<>();
		availableDiag = allDiagnoses.stream().filter(d -> d.containsAll(removeAxioms) & Collections.disjoint(d, keepAxioms)).collect(Collectors.toSet());
		availableDiag.addAll(minimalDiagnoses.stream().filter(d -> d.containsAll(removeAxioms) & Collections.disjoint(d, keepAxioms)).collect(Collectors.toSet()));
		return availableDiag;
	}

	public static Set<Set<? extends OWLAxiom>> getAvailableMinDiagnoses(Set<OWLAxiom> keepAxioms, Set<OWLAxiom> removeAxioms){
		Set<Set <? extends OWLAxiom>> availableDiag = new HashSet<>();
		availableDiag = minimalDiagnoses.stream().filter(d -> d.containsAll(removeAxioms) & Collections.disjoint(d, keepAxioms)).collect(Collectors.toSet());
		return availableDiag;
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
		HelperFunctions.runProgram(mDsID, outDirStr, true, false, false, false, Optional.empty());
		
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

	private static Boolean getEntailmentProbability(Set<Set<? extends OWLAxiom>> allJustifications, Set<OWLAxiom> keepAxioms, Set<OWLAxiom> removeAxioms, String outDirStr, String ontologyPath, Set<? extends OWLAxiom> interestingAxiomsSet, ReasonerName reasonerName) throws IOException, OWLOntologyCreationException, OWLOntologyStorageException, EntityCheckerException{
		axiomWeightMap = new HashMap<>();
		Set<? extends OWLAxiom> selectedJustification = checkAxiomSelection(allJustifications, keepAxioms);
		if (selectedJustification != null){
			displayNoRepair(selectedJustification);
			return false;
		} else {
			//interesting axioms entailment percentage in repairs when retaining the justification axiom
			getAxiomWeight(allJustifications, outDirStr, ontologyPath, interestingAxiomsSet, keepAxioms, removeAxioms, reasonerName);
			displayAxiomWeights(axiomWeightMap);
			return true;
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
		Set<Set<? extends OWLAxiom>> allReachableDiagnoses = getAvailableDiagnoses(keepAxioms, removeAxioms);
		ExecutorService executor = Executors.newSingleThreadExecutor();
		Future<?> axiomWeightFuture;

		axiomWeightFuture = executor.submit(new ComputeAxiomWeightThread(outDirStr, "repair", ontologyPath, "repairOntology", allReachableDiagnoses, interestingAxiomsSet, reasonerName));
		waitForFuture(axiomWeightFuture);
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
				OWLOntology impModule = null;
				if (reasonerName == ReasonerName.Elk){
					impModule = Segmenter.getStarModule(ontology, impAxiom.getSignature(),
            		ontology.getOntologyID().getOntologyIRI().isPresent() ? ontology.getOntologyID().getOntologyIRI().get()
            				: IRI.create("http://example.org/temp-repair-ontology"));
				} else {
					impModule = SegmenterHermit.getStarModule(ontology, impAxiom.getSignature(),
            		ontology.getOntologyID().getOntologyIRI().isPresent() ? ontology.getOntologyID().getOntologyIRI().get()
            				: IRI.create("http://example.org/temp-repair-ontology"));
				}
				
				repairModules.putIfAbsent(impAxiom, new ArrayList<>());
				repairModules.get(impAxiom).add(impModule);
			}
		}
		return repairModules;
	}

	public static Map<OWLAxiom, Set<Set<? extends OWLAxiom>>> getInterestingAxiomsEntailment(String ontologyPath, Set<Set<? extends OWLAxiom>> allOptimalDiagnoses, Set<? extends OWLAxiom> interestingAxiomsSet) throws OWLOntologyCreationException{
		Map<OWLAxiom, Set<Set<? extends OWLAxiom>>> axiomToRepairs = new HashMap<>();
		for (Set<? extends OWLAxiom> axiomSets : allOptimalDiagnoses){
			OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
			OWLOntology ontology = manager.loadOntologyFromOntologyDocument(new File(ontologyPath));

			for (OWLAxiom axiom: axiomSets){
				manager.removeAxiom(ontology, axiom);
			}

			for (OWLAxiom impAxiom : interestingAxiomsSet){
				if (HelperFunctions.checkEntailment(ontology, impAxiom, reasonerName)){
					axiomToRepairs.get(impAxiom).add(axiomSets);
				}			
				
			}
		}
		return axiomToRepairs;
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
			OWLOntology originalOntology = manager.loadOntologyFromOntologyDocument(new File(ontologyPath));
			
			OWLOntology repairOntology = manager.createOntology();
			//for axioms in the original ontology, add to repair ontology if not in diagnosis
			for (OWLAxiom axiom: originalOntology.getAxioms()){
				if (!selectedMinDiagnosis.contains(axiom)){
					manager.addAxiom(repairOntology, axiom);
				}
			}
		
		return repairOntology;
	}

	private static Boolean isRepair(Set<OWLAxiom> removeAxioms){
		Set<Set<? extends OWLAxiom>> satisfiedDiagnoses = new HashSet<>();

		if (minimalDiagnoses.size() > 0){
			for (Set<? extends OWLAxiom> diagnosisSet : minimalDiagnoses){
				if (removeAxioms.containsAll(diagnosisSet)){
					satisfiedDiagnoses.add(diagnosisSet);
				}
			}
		} 
		return satisfiedDiagnoses.size() > 0;
	}

	private static Boolean checkRepair(OWLAxiom defectAxiom, OWLOntology defectOntology, Set<OWLAxiom> removeAxioms, String outDirStr, ReasonerName reasonerName, String ontologyPath, Scanner scanner) throws IOException, InterruptedException{
		
		if (isRepair(removeAxioms)){
			System.out.println("Repair already reached!");
			System.out.println("Enter \"save\" to save the repair or \"continue\" to continue answering the remaining justification axioms.");
			while(true){
				System.out.println("\u0007");
				System.out.flush();
				String user_in = scanner.nextLine();
				String fileName = "";
				if (user_in.toLowerCase().equals("save")){
					System.out.println("Enter the filename to save as: ");
					System.out.println("\u0007");
					System.out.flush();
					fileName = scanner.nextLine();
					return saveProcess(defectOntology, defectAxiom, removeAxioms, ontologyPath, outDirStr, fileName, reasonerName, scanner);
				} else if (user_in.toLowerCase().equals("continue")){
					repairCheck = false;
					return false;
				} else{
					System.out.println("Invalid input!");
					continue;
				}
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

		if (!isRepair(removeAxioms)){
			return noRepair(repairOntology, outDirStr, save_filename, scanner);											
		} else {
			try {
				// return refineRepair2(ontology, repairOntology, axiom, removeAxioms, ontologyPath, outDirStr, save_filename, reasonerName);
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
		System.out.println("\u0007");
		System.out.flush();
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
			System.out.println("\u0007");
			System.out.flush();
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
					System.out.println("\u0007");
					System.out.flush();
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

	//for not sure option evaluation. Save all maximal repairs.
	private static Boolean refineRepair2(OWLOntology defectOntology, OWLOntology repairOntology, OWLAxiom axiom, Set<OWLAxiom> removeAxioms, String ontologyPath, String outDirStr, String save_filename, ReasonerName reasonerName) 
		throws OWLOntologyCreationException, OWLOntologyStorageException, IOException, EntityCheckerException, InterruptedException, ExecutionException{

		
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
			
			List<Set<? extends OWLAxiom>> recommendedDiagnoses = recommendDiagnosisSet(minimalDiagnoses, removeAxioms);
			for (Set<? extends OWLAxiom> diagnosisSet : recommendedDiagnoses){
				OWLOntology repairOntologyMax = computeRepair(diagnosisSet, ontologyPath);
				try{
					saveRepairOntology(repairOntologyMax, outDirStr, save_filename);														
				} catch (Exception e){
					e.printStackTrace();
					return false;
				}
				System.out.println("Repair Saved!");
				
			}
			return true;
		}	
									
	}

	public static void saveRepairOntology(OWLOntology saveOntology, String outDirStr, String outFileNameStr) throws OWLOntologyCreationException, OWLOntologyStorageException, FileNotFoundException{
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
	public static Map<OWLAxiom, Double> computeAxiomWeight(Map<OWLAxiom, Set<Set<? extends OWLAxiom>>> impAxiomRep, ReasonerName reasonerName, int totalRepairs) throws OWLOntologyCreationException{

		axiomWeightMap = new HashMap<>();

		for (OWLAxiom axiom : impAxiomRep.keySet()){
			axiomWeightMap.put(axiom, (double) impAxiomRep.get(axiom).size()*100/totalRepairs);
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
	private static void displayAxiomWeights(Map<OWLAxiom,Double> axiomWeightMap) throws UnsupportedEncodingException{
		PrintStream bufferStream = new PrintStream(axiomWeightOutputBuffer, true, StandardCharsets.UTF_8.name());
		PrintStream originalOut = System.out;

		// Simulate printing axiom weights
		System.setOut(bufferStream); // Redirect output
		for (Map.Entry<OWLAxiom,Double> axiomWeightEntry : axiomWeightMap.entrySet()) {
			String axiomWeightStr = sOWLFormatter.format(axiomWeightEntry.getKey()).toString() + " = " + axiomWeightEntry.getValue() + "%";
			System.out.println(axiomWeightStr);
		}
		System.out.println("----------------------");
		System.out.flush();
		System.setOut(originalOut); // Restore original output

		// Print the buffered output to the actual console
		System.out.print(axiomWeightOutputBuffer.toString(StandardCharsets.UTF_8.name()));

    }

/**
 * write the initial, modified class hierarchy and the difference to a json file
 * @param hierarchy1
 * @param hierarchy2
 * @param hierarchyDiff
 * @param repairYes
 * @param repairNo
 * @param outDirStr
 */

	private static void writeClassHierarchyDifferenceToFile(Set<List<String>> hierarchy1, Set<List<String>> hierarchy2, Map<String, Set<List<String>>> hierarchyDiff, Boolean repairYes,Boolean repairNo, String outDirStr, Optional<String> nodeId){
		ObjectMapper mapper = new ObjectMapper();
		String filename = "classHierarchyDifference.json";
		if (nodeId.isPresent()){
			filename="classHierarchyDifference_"+nodeId.get()+".json";
		}
		Map<String, Object> hierarchies = new HashMap<>();
		hierarchies.put("initialHierarchy", hierarchy1);
		hierarchies.put("repairYes", repairYes);
		hierarchies.put("modifiedHierarchy", hierarchy2);
		hierarchies.put("repairNo", repairNo);
		hierarchies.put("hierarchyDifference", hierarchyDiff);
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(outDirStr + File.separator + filename), hierarchies);
        } catch (IOException e) {
            e.printStackTrace();
        }
	}

/**
 * write the probabilities of the axioms to a json file
 * @param probabilities_yes
 * @param probabilities_no
 * @param outDirStr
 */
	private static void writeProbabilitiesToFile(Boolean entail_yes, Boolean entail_no, Map<OWLAxiom, Double> probabilities_yes, Map<OWLAxiom, Double> probabilities_no, String outDirStr, Optional<String> nodeId) {
		ObjectMapper mapper = new ObjectMapper();
		Map<String, Object> probabilitiesMap = new HashMap<>();
		if (entail_yes){
			Map<String, Double> formattedProbabilitiesYes = new HashMap<>();
			for (Map.Entry<OWLAxiom, Double> entry : probabilities_yes.entrySet()) {
				formattedProbabilitiesYes.put(sOWLFormatter.format(entry.getKey()), entry.getValue());
			}
			probabilitiesMap.put("yes", formattedProbabilitiesYes);
		} else {
			probabilitiesMap.put("yes", "No Repair!");
		}

		if (entail_no){
			Map<String, Double> formattedProbabilitiesNo = new HashMap<>();
			for (Map.Entry<OWLAxiom, Double> entry : probabilities_no.entrySet()) {
				formattedProbabilitiesNo.put(sOWLFormatter.format(entry.getKey()), entry.getValue());
			}
			probabilitiesMap.put("no", formattedProbabilitiesNo);
		} else{
			probabilitiesMap.put("no", "No Repair!");
		}
				
		String filename = "probabilities.json";
		if (nodeId.isPresent()){
			filename = "probabilities_"+ nodeId.get() + ".json";
		}
		try {
			mapper.writerWithDefaultPrettyPrinter().writeValue(new File(outDirStr + File.separator + filename), probabilitiesMap);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void writeHammingDistanceToFile(Boolean noRepairYes, Boolean noRepairNo, Map<Set<? extends OWLAxiom>, Double> hammingYes, Map<Set<? extends OWLAxiom>, Double> hammingNo, Set<OWLAxiom> entailedYes, Set<OWLAxiom> entailedNo, Set<OWLAxiom> entailedBoth, String outDirStr, Optional<String> nodeId) {
		ObjectMapper mapper = new ObjectMapper();
		String filename = "hammingDistance.json";
		if (nodeId.isPresent()) filename="hammingDistance_"+nodeId.get()+".json";
		Map<String, Object> hammingData = new HashMap<>();
		if (noRepairYes){
			hammingData.put("hamming_yes", "No Repair!");
		} else {
			Set<String> hamming_yes_repair = new HashSet<>();
			for (OWLAxiom repAx : hammingYes.keySet().iterator().next()){
				hamming_yes_repair.add(sOWLFormatter.format(repAx));
			}
			hammingData.put("hamming_yes_repair", hamming_yes_repair);
			hammingData.put("hamming_yes", hammingYes.values().iterator().next());
			hammingData.put("entailed_yes", toStringSet(entailedYes));
		}

		if (noRepairNo){
			hammingData.put("hamming_no", "No Repair!");
		} else {
			Set<String> hamming_no_repair = new HashSet<>();
			for (OWLAxiom repAx : hammingNo.keySet().iterator().next()){
				hamming_no_repair.add(sOWLFormatter.format(repAx));
			}
			hammingData.put("hamming_no_repair", hamming_no_repair);
			hammingData.put("hamming_no", hammingNo.values().iterator().next());
			hammingData.put("entailed_no", toStringSet(entailedNo));
		}
		
		if (!noRepairYes && !noRepairNo){
			hammingData.put("entailed_both", toStringSet(entailedBoth));
		}		
		
		try {
			mapper.writerWithDefaultPrettyPrinter().writeValue(new File(outDirStr + File.separator + filename), hammingData);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

/**
 * display the class hierarchy difference
 * @param hierarchyDifferenceMap
 * @throws UnsupportedEncodingException 
 */
	private static void displayClassHierarchyDifference(Map<String, Set<List<String>>> hierarchyDifferenceMap) throws UnsupportedEncodingException {
		PrintStream bufferStream = new PrintStream(axiomWeightOutputBuffer, true, StandardCharsets.UTF_8.name());
		PrintStream originalOut = System.out;

		// Simulate printing class hierearchy difference
		System.setOut(bufferStream); // Redirect output
		
		StringJoiner hierarchyDiff = new StringJoiner("\n");
		
		hierarchyDiff.add("\n\t\t2. Class Hierarchy Difference\n");
		Set<List<String>> removedObjects = hierarchyDifferenceMap.get("removedEdges");
		if (removedObjects instanceof Iterable && !((Set<List<String>>) removedObjects).isEmpty()) {
			hierarchyDiff.add("Following sub-structures would be removed:");
			hierarchyDiff.add("==========================");
			hierarchyDiff.add(HelperFunctions.getCHDifferenceTree(removedObjects).toString());
			hierarchyDiff.add("==========================");
		} 
		hierarchyDiff.add("");
		Set<List<String>> addedObjects = hierarchyDifferenceMap.get("addedEdges");
			
		if (addedObjects instanceof Iterable && !((Set<List<String>>) addedObjects).isEmpty()) {
			hierarchyDiff.add("Following sub-structures would be added:");
			hierarchyDiff.add("==========================");	
			hierarchyDiff.add(HelperFunctions.getCHDifferenceTree(addedObjects).toString());
			hierarchyDiff.add("==========================");
		} 

		if(((Set<List<String>>) addedObjects).isEmpty() && ((Set<List<String>>) removedObjects).isEmpty()){
			hierarchyDiff.add("===========================");
			hierarchyDiff.add("No changes in the class hierarchy.");
			hierarchyDiff.add("===========================");
		}
			
		
		System.out.println(hierarchyDiff.toString());	
		System.out.flush();
		System.setOut(originalOut); // Restore original output

		// Print the buffered output to the actual console
		System.out.print(axiomWeightOutputBuffer.toString(StandardCharsets.UTF_8.name()));
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

	/**
	 * get the repairs from smallest minimal diagnosis sets that entail the maximum interesting axioms as preferred repairs
	 * @param keepAxioms
	 * @param removeAxioms
	 * @param outDirStr
	 * @param ontologyPath
	 * @param interestingAxiomsSet
	 * @param reasonerName
	 * @return
	 * @throws IOException
	 * @throws OWLOntologyCreationException
	 * @throws EntityCheckerException
	 */

	public static Map<Set<? extends OWLAxiom>, Set<OWLAxiom>> getPreferredRepair(Set<OWLAxiom> keepAxioms, Set<OWLAxiom> removeAxioms, String outDirStr, String ontologyPath, Set<? extends OWLAxiom> interestingAxiomsSet, ReasonerName reasonerName) throws IOException, OWLOntologyCreationException, EntityCheckerException{
		Set<Set<? extends OWLAxiom>> allAvailableDiagnoses = getAvailableDiagnoses(keepAxioms, removeAxioms);
		int minMDSize = allAvailableDiagnoses.stream()
							.mapToInt(Set::size)
							.min()
							.orElse(0);
		Set<Set<? extends OWLAxiom>> smallestMDs = allAvailableDiagnoses.stream()
													.filter(s -> s.size() == minMDSize).collect(Collectors.toSet());

		int max_entailed = 0, ia_entailment_count = 0;
		Map<Set<? extends OWLAxiom>, Set<OWLAxiom>> repairEntailMap = new HashMap<>();
		Map<Set<? extends OWLAxiom>, Set<OWLAxiom>> preferredRepairs = new HashMap<>();
		Set<OWLAxiom> entailedIA;
		OWLOntology repairOntology;
		for (Set<? extends OWLAxiom> diagnosisSet : smallestMDs){
			ia_entailment_count = 0;	
			repairOntology = computeRepair(diagnosisSet, ontologyPath);
			entailedIA = new HashSet<>();
			for (OWLAxiom ia : interestingAxiomsSet){
				if (HelperFunctions.checkEntailment(repairOntology, ia, reasonerName)){
					entailedIA.add(ia);
					ia_entailment_count++;
				}
			}
			if (repairEntailMap.containsKey(diagnosisSet)){
				throw new IllegalStateException("Duplicate repair ontology detected!");
			}
			repairEntailMap.put(diagnosisSet, entailedIA);
			if (ia_entailment_count > max_entailed){
				max_entailed = ia_entailment_count;
			}
		}
		System.out.println("Max entailed interesting axioms count: " + max_entailed);
		for (Map.Entry<Set<? extends OWLAxiom>, Set<OWLAxiom>> entry : repairEntailMap.entrySet()) {
			if (entry.getValue().size() == max_entailed) {
				System.out.println("Also max entailed! Adding to preferred repairs.");
				preferredRepairs.put(entry.getKey(), entry.getValue());
			}
		}
		System.out.println("Preferred repairs found: " + preferredRepairs);
		return preferredRepairs;
	}


	private static double computeHammingDistance(OWLOntology currentOntology, OWLOntology preferredRepair){
		Set<OWLAxiom> intersection = new HashSet<>(currentOntology.getAxioms());
		intersection.retainAll(preferredRepair.getAxioms());
		Set<OWLAxiom> union = new HashSet<>(currentOntology.getAxioms());
		union.addAll(preferredRepair.getAxioms());
		double hammingDistance = 1 - ((double) intersection.size() / (double) union.size());
		return hammingDistance;
	}

	private static Map<Set<? extends OWLAxiom>, Double> getBestRepair(Map<Set<? extends OWLAxiom>, Set<OWLAxiom>> repairs, OWLOntology ontology, String ontologyPath) throws OWLOntologyCreationException {
		if (repairs == null || repairs.isEmpty()) return null;
		Map<Set<? extends OWLAxiom>, Double> repairHammingDist = new HashMap<>();
		Set<? extends OWLAxiom> bestRepair = repairs.keySet().iterator().next();

		OWLOntology repairOntology = null;
		repairOntology = computeRepair(bestRepair, ontologyPath);
		double distance = computeHammingDistance(ontology, repairOntology);
		repairHammingDist.put(bestRepair, distance);
		return repairHammingDist;
	}

	public static String hammingDistance(OWLAxiom justAxiom, Set<OWLAxiom> removeAxioms, Set<OWLAxiom> keepAxioms, Set<? extends OWLAxiom> interestingAxiomsSet, String ontologyPath, ReasonerName reasonerName, String outDirStr, Optional<String> nodeId) throws OWLOntologyCreationException, IOException, EntityCheckerException{
		OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
		OWLOntology ontology = manager.loadOntologyFromOntologyDocument(new File(ontologyPath));
		manager.removeAxioms(ontology, removeAxioms);

		String bufferString = "";

		Boolean noRepairYes = false;
		Boolean noRepairNo = false;
		Set<OWLAxiom> entailedIA_yes = new HashSet<>();
		Set<OWLAxiom> entailedIA_no = new HashSet<>();

		Set<OWLAxiom> updKeepAxioms = new HashSet<>(keepAxioms);
		Set<OWLAxiom> updRemoveAxioms = new HashSet<>(removeAxioms);

		Map<Set<? extends OWLAxiom>, Set<OWLAxiom>> preferredRepairs_yes = null;
		Map<Set<? extends OWLAxiom>, Double> preferredRepair_yes = new HashMap<>();
		Map<Set<? extends OWLAxiom>, Set<OWLAxiom>> preferredRepairs_no = null;
		Map<Set<? extends OWLAxiom>, Double> preferredRepair_no = new HashMap<>();
			
		updKeepAxioms.add(justAxiom);
		Set<? extends OWLAxiom>unsatJust_yes = checkAxiomSelection(allJustifications, updKeepAxioms);
		if (unsatJust_yes!=null){
			noRepairYes=true;
		} else {
			preferredRepairs_yes = getPreferredRepair(updKeepAxioms, removeAxioms, outDirStr, ontologyPath, interestingAxiomsSet, reasonerName);
			preferredRepair_yes = getBestRepair(preferredRepairs_yes, ontology, ontologyPath);
			entailedIA_yes = preferredRepairs_yes.get(preferredRepair_yes.keySet().iterator().next());
		}
		
		updRemoveAxioms.add(justAxiom);
		Set<? extends OWLAxiom>unsatJust_no = checkAxiomSelection(allJustifications, keepAxioms);
		if (unsatJust_no!=null){
			noRepairNo=true;
		} else {
			preferredRepairs_no = getPreferredRepair(keepAxioms, updRemoveAxioms, outDirStr, ontologyPath, interestingAxiomsSet, reasonerName);
			preferredRepair_no = getBestRepair(preferredRepairs_no, ontology, ontologyPath);
			entailedIA_no = preferredRepairs_no.get(preferredRepair_no.keySet().iterator().next());
		}

		Map<String, Set<OWLAxiom>> entailed_ia = getEntailedSets(entailedIA_yes, entailedIA_no);
		Set<OWLAxiom> entailed_yes = entailed_ia.get("entailed_yes");
		Set<OWLAxiom> entailed_no = entailed_ia.get("entailed_no");
		Set<OWLAxiom> entailed_both = entailed_ia.get("entailed_both");

 		PrintStream bufferStream = new PrintStream(axiomWeightOutputBuffer, true, StandardCharsets.UTF_8.name());
		PrintStream originalOut = System.out;

		// Simulate printing class hierearchy difference
		System.setOut(bufferStream);
		System.out.println("\n\t\t3. Hamming Distance");
		System.out.println("============================");
		System.out.println("\tAnswer = yes:");
		
		if(noRepairYes){		
			System.setOut(originalOut);	
			displayNoRepair(unsatJust_yes);
			bufferString += axiomWeightOutputBuffer.toString();
			axiomWeightOutputBuffer.reset();
			System.setOut(bufferStream);
		} else {
			System.out.println("Hamming distance to a candidate repair : " + String.format("%.2f", preferredRepair_yes.values().iterator().next()));
			System.out.println("Maximum number of interesting axioms entailed by the repair : " + (entailedIA_yes.size()));
			if(!entailed_yes.isEmpty()){
				System.out.println("Interesting axioms entailed by the repair : ");
				for (OWLAxiom ia : entailed_yes){
					System.out.println(sOWLFormatter.format(ia).toString());
				}
			}
		}
		
		System.out.println("-----------------------------");

		System.out.println("\tAnswer = no:");
		
		if(noRepairNo){
			System.setOut(originalOut);
			displayNoRepair(unsatJust_no);
			bufferString += axiomWeightOutputBuffer.toString();
			axiomWeightOutputBuffer.reset();
			System.setOut(bufferStream);
		} else {
			System.out.println("Hamming distance to a candidate repair : " + String.format("%.2f", preferredRepair_no.values().iterator().next()));
			System.out.println("Maximum number of interesting axioms entailed by the repair : " + entailedIA_no.size());
			if(!entailed_no.isEmpty()){
				System.out.println("Interesting axioms entailed by the repair : ");
				for (OWLAxiom ia : entailed_no){

					System.out.println(sOWLFormatter.format(ia).toString());
				}
			}
		}
		

		if(!entailed_both.isEmpty()){
			System.out.println("-----------------------------");
			System.out.println("Interesting axioms entailed by both the repairs : ");
			for (OWLAxiom ia : entailed_both){
				System.out.println(sOWLFormatter.format(ia).toString());
			}
		}
		System.out.println("============================");
		System.out.flush();
		System.setOut(originalOut);

		System.out.print(axiomWeightOutputBuffer.toString(StandardCharsets.UTF_8.name()));
		bufferString += axiomWeightOutputBuffer.toString();
		axiomWeightOutputBuffer.reset();

		if (nodeId.isPresent()){
			writeHammingDistanceToFile(noRepairYes, noRepairNo, preferredRepair_yes, preferredRepair_no, entailed_yes, entailed_no, entailed_both, outDirStr, Optional.of(nodeId.get()));
		} else {
			writeHammingDistanceToFile(noRepairYes, noRepairNo, preferredRepair_yes, preferredRepair_no, entailed_yes, entailed_no, entailed_both, outDirStr, Optional.empty());
		}
		
		
		return bufferString;
	}

	private static Set<String> toStringSet(Set<OWLAxiom> axioms) {
	Set<String> result = new HashSet<>();
	for (OWLAxiom axiom : axioms) {
		result.add(sOWLFormatter.format(axiom).toString()); // or use a renderer for nicer output
	}
	return result;
}


	private static Map<String, Set<OWLAxiom>> getEntailedSets(Set<OWLAxiom> entailed_yes, Set<OWLAxiom> entailed_no){
		Set<OWLAxiom> both = new HashSet<>(entailed_yes);
		both.retainAll(entailed_no);

		Set<OWLAxiom> only_yes = new HashSet<>(entailed_yes);
		only_yes.removeAll(both);

		Set<OWLAxiom> only_no = new HashSet<>(entailed_no);
		only_no.removeAll(both);

		Map<String, Set<OWLAxiom>> result = new HashMap<>();
		result.put("entailed_yes", only_yes);
		result.put("entailed_no", only_no);
		result.put("entailed_both", both);

		return result;
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
		SortMethod sortMethod = (SortMethod) args[6];
		Boolean liveSort = (Boolean) args[7];
		Boolean visualize = (Boolean) args[8];

		try{
			computeRepairOntology(defect, ontology, interestingAxiomOntology, reasonerName, outDirStr, ontologyPath, sortMethod, liveSort, visualize);
		} catch(Exception e){
			e.printStackTrace();
		}
	}

	private static void checkFuture(Future<?> future){
		if (!future.isDone()){
			return;
		}

		try {
			future.get();
		} catch (InterruptedException e){
			Thread.currentThread().interrupt();
			e.printStackTrace();
			System.exit(1);
			
		} catch (ExecutionException e){
			Throwable cause = e.getCause();

			cause.printStackTrace();
			Thread.currentThread().interrupt();
			System.exit(1);
		}
	}

	private static void waitForFuture(Future<?> future){
		try{
			future.get();
		} catch (InterruptedException e){
			Thread.currentThread().interrupt();
			e.printStackTrace();
			System.exit(1);
			
		} catch (ExecutionException e){
			Throwable cause = e.getCause();

			cause.printStackTrace();
			Thread.currentThread().interrupt();
			System.exit(1);
		}
	}

}
