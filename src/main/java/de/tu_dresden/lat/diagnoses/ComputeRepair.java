package de.tu_dresden.lat.diagnoses;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.LinkedBlockingQueue;

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
	public static Map<String, OWLAxiom> identifiers2Axioms;
    private static Set<OWLAxiom> keepAxioms;
    private static Set<OWLAxiom> removeAxioms;
	private static Set<Set<? extends OWLAxiom>> allOptimalDiagnoses;
	private static String ontologyPathStr;
	private static Set<? extends OWLAxiom> interestingAxiomsSet; 
	public static Set<Set<? extends OWLAxiom>> allJustifications;
	public static BlockingQueue<Set<? extends OWLAxiom>> justificationQueue;
	public static Map<OWLAxiom,Integer> axiomWeightMap;

	public static final String programFileName = "pi.txt";

    private static SimpleOWLFormatterCl sOWLFormatter = new SimpleOWLFormatterCl(true, SimpleDLFormatter$.MODULE$,
        true);

    public static void computeRepairOntology(OWLAxiom axiom, OWLOntology ontology, OWLOntology interestingAxiomOntology, ReasonerName reasonerName, String outDirStr, String ontologyPath) throws IOException, EntityCheckerException, OWLOntologyCreationException, OWLOntologyStorageException{
        ontologyPathStr = ontologyPath;

		if (outDirStr.isEmpty())
			outDirStr = "defaultRepairFolder";

		sOWLFormatter.setReferenceOntology(ontology);
		interestingAxiomsSet = interestingAxiomOntology.getAxioms();
		allJustifications = new CopyOnWriteArraySet<>();
		justificationQueue = new LinkedBlockingQueue<>();
		keepAxioms = new HashSet<>();
        removeAxioms = new HashSet<>();
		Boolean inputFlag = true;

		try{
			ComputeJustificationsThread runnable1 = new ComputeJustificationsThread(reasonerName, axiom, ontology);
			Thread justificationsThread = new Thread(runnable1); 
			justificationsThread.start();

			System.out.println("For the following axioms, choose if you want them in the repair (\"yes\"), not (\"no\") or check their effect (\"not sure\").");
			java.util.Scanner scanner = new java.util.Scanner(System.in);
			
			while(inputFlag){
				while (!justificationQueue.isEmpty() || justificationsThread.isAlive()){
					while(justificationQueue.isEmpty()){
						LoadingScreen.main(null);
					}
					Set<? extends OWLAxiom> justificationSet = justificationQueue.take();
					System.out.println("Justification Set");
					for (OWLAxiom justificationAxiom : justificationSet){
						if(keepAxioms.contains(justificationAxiom) | removeAxioms.contains(justificationAxiom)){
							System.out.println(sOWLFormatter.format(justificationAxiom).toString() + " -- selection already made for the axiom!");
							continue;
						}
						while (true){
							System.out.println(sOWLFormatter.format(justificationAxiom).toString());
							String user_in = scanner.nextLine();
							switch(user_in.toLowerCase()){
								case "yes":
									keepAxioms.add(justificationAxiom);
									break;
								case "no":
									removeAxioms.add(justificationAxiom);
									break;
								case "not sure":
									{
										justificationsThread.join();
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
									}
									continue;
								case "save":
									{
										justificationsThread.join();
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
											try{
												ComputeDiagnosesThread diagnosesRunnable = new ComputeDiagnosesThread(allJustifications, new HashSet<>(), save_filename, outDirStr);
												Thread diagnosesThread = new Thread(diagnosesRunnable);
												diagnosesThread.start();
												while (diagnosesThread.isAlive()){
													LoadingScreen.main(null);
												}
												diagnosesThread.join(0);
												System.out.println("Repaired ontologies saved!");
											} catch(Exception e) {
												e.printStackTrace();
											}
											
										}
									}
									continue;
								case "exit":
									System.out.println("Exiting repair mode!");
									inputFlag = false;
									justificationsThread.interrupt();
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
					Thread.sleep(1000);
				}
				if (!inputFlag){
					break;
				}
				System.out.println("All justifications have been computed.\nWould you like to exit or save the repair?");
				while(true){
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
								System.out.println("You have selected the following axioms to be in the repair which ll belong to the same justification set.");
								for (OWLAxiom selectedAxiom : selectedJustification){
									System.out.println(sOWLFormatter.format(selectedAxiom).toString());
								}
							} else {
								System.out.println("Enter the filename to save as: ");
								String save_filename = scanner.nextLine();
								try{
									ComputeDiagnosesThread diagnosesRunnable = new ComputeDiagnosesThread(allJustifications, new HashSet<>(), save_filename, outDirStr);
									Thread diagnosesThread = new Thread(diagnosesRunnable);
									diagnosesThread.start();
									while (diagnosesThread.isAlive()){
										LoadingScreen.main(null);
									}
									diagnosesThread.join(0);
									System.out.println("Repaired ontologies saved!");
								} catch(Exception e) {
									e.printStackTrace();
								}
								inputFlag = false;
							}
						}						
						break;
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
 * 
 */
	public static void computeDiagnoses(Set<Set<? extends OWLAxiom>> allJustifications, Set<Set<? extends OWLAxiom>> allOptDiagnoses, String outDirStr, String outFileName) throws IOException, OWLOntologyCreationException, OWLOntologyStorageException, EntityCheckerException{
		allOptimalDiagnoses = allOptDiagnoses;
        String mDsID = "repair";
        logger.info("Creating Program");
		SolveProgramHelpers.createProgram(allJustifications, outDirStr, axioms2Identifiers, identifiers2Axioms, programFileName);

		StringJoiner keepAxiomsProgram = new StringJoiner("\n");
		keepAxiomsProgram.add("");
		StringJoiner removeAxiomsProgram = new StringJoiner("\n");
		removeAxiomsProgram.add("");

		applyUserSelection(outDirStr);

		logger.info("Extracting All Minimal Classical Diagnoses");
		HelperFunctions.runProgram(mDsID, outDirStr, true, false, false, Optional.empty());
		allOptimalDiagnoses.addAll(HelperFunctions.returnResult(mDsID, outDirStr));

		logger.info("Generating output file");
		HelperFunctions.saveResult(allOptimalDiagnoses, mDsID, outDirStr);

		int counter = 1;
		String outFileNameStr;
		for (Set<? extends OWLAxiom> axiomSets : allOptimalDiagnoses){
			if (allOptDiagnoses.size() > 1){
				outFileNameStr = outFileName + "_" + Integer.toString(counter) + ".owl";
			} else{
				outFileNameStr = outFileName + ".owl";
			}
			OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
			OWLOntology ontology = manager.loadOntologyFromOntologyDocument(new File(ontologyPathStr));

			for (OWLAxiom axiom: axiomSets){
				manager.removeAxiom(ontology, axiom);
			}
			File outputFile = new File(HelperFunctions.getRepairFilePathStr(outDirStr, outFileNameStr));
			OWLDocumentFormat format = manager.getOntologyFormat(ontology);
			manager.saveOntology(ontology, format, new FileOutputStream(outputFile));
			counter += 1;
		}
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
	}

	private static void displayAxiomWeights(Map<OWLAxiom,Integer> axiomWeightMap){
		for (Map.Entry<OWLAxiom,Integer> axiomWeightEntry : axiomWeightMap.entrySet()) {
			System.out.println(sOWLFormatter.format(axiomWeightEntry.getKey()).toString() + " = " + axiomWeightEntry.getValue());
		}
	}

	public static Set<Set<? extends OWLAxiom>> getAllJustificationsAsync(ReasonerName reasonerName, OWLAxiom axiom, OWLOntology ontology, BlockingQueue<Set<? extends OWLAxiom>> queue) {
		if (reasonerName == ReasonerName.Elk)
			return JustificationsGenerator.getAllELKJustificationsAsync(axiom, ontology, queue);

		return JustificationsGenerator.getAllHermitJustificationsAsync(axiom, ontology);
	}

}
