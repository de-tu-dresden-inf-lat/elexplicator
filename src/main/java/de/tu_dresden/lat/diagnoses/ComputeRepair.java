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
import de.tu_dresden.lat.tools.AxiomChecker;
import de.tu_dresden.lat.tools.LoadingScreen;

class ComputeJustificationsThread implements Runnable{
	private ReasonerName reasonerName;
	private OWLAxiom axiom;
	private OWLOntology ontology;

	public ComputeJustificationsThread(ReasonerName reasonerName, OWLAxiom axiom, OWLOntology ontology){
		this.reasonerName = reasonerName;
		this.axiom = axiom;
		this.ontology = ontology;
	}

	@Override
	public void run(){	
		try{
			ComputeRepair.allJustifications = ComputeRepair.getAllJustificationsAsync(reasonerName, axiom, ontology, ComputeRepair.justificationQueue);        
			
			ComputeRepair.fillMap(ComputeRepair.allJustifications);
			HelperFunctions.identifiers2Axioms = ComputeRepair.identifiers2Axioms;
		} catch (Exception e) {
			Thread.currentThread().interrupt(); // Set the interrupt status again for any further handlers
			// e.printStackTrace();
		}
		
	}
}

class ComputeAxiomWeightThread implements Runnable{
	String outDirStr;
	String mDsID;
	String ontologyPath;
	String outputFileName;
	public ComputeAxiomWeightThread(String outDirStr, String mDsID, String ontologyPath, String outputFileName){
		this.outDirStr = outDirStr;
		this.mDsID = mDsID;
		this.ontologyPath = ontologyPath;
		this.outputFileName = outputFileName;
	}

	@Override
	public void run(){
		try{
			ComputeRepair.computeRepairs(outDirStr, mDsID, ontologyPath, outputFileName);
		} catch (Exception e){
			Thread.currentThread().interrupt();
			// e.printStackTrace();
		}
		
	}
}

class ComputeDiagnosesThread implements Runnable{
	Set<Set<? extends OWLAxiom>> allJustifications;
	Set<Set<? extends OWLAxiom>> allOptDiagnoses;
	String outFileName;

	public ComputeDiagnosesThread(Set<Set<? extends OWLAxiom>> allJustifications, Set<Set<? extends OWLAxiom>> allOptDiagnoses, String outFileName){
		this.allJustifications = allJustifications;
		this.allOptDiagnoses = allOptDiagnoses;
		this.outFileName = outFileName;
	}

	@Override
	public void run() {
		try{
			ComputeRepair.computeDiagnoses(allJustifications, allOptDiagnoses, outFileName);
		} catch (Exception e){
			Thread.currentThread().interrupt();
		}
	}
	
}

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

    private static SimpleOWLFormatterCl sOWLFormatter = new SimpleOWLFormatterCl(true, SimpleDLFormatter$.MODULE$,
        true);

    public static void computeRepairOntology(OWLAxiom axiom, OWLOntology ontology, OWLOntology interestingAxiomOntology, ReasonerName reasonerName, String outDirStr, String ontologyPath) throws IOException, EntityCheckerException, OWLOntologyCreationException, OWLOntologyStorageException{
        ontologyPathStr = ontologyPath;
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

			// Set<? extends OWLAxiom> justificationSet = justificationQueue.poll(1000, TimeUnit.MILLISECONDS);
			System.out.println("For the following axioms, choose if you want them in the repair (\"yes\"), not (\"no\") or check their effect (\"not sure\").");
			// Thread.sleep(1500);
			java.util.Scanner scanner = new java.util.Scanner(System.in);
			
			while(inputFlag){
				while (!justificationQueue.isEmpty() || justificationsThread.isAlive()){
					while(justificationQueue.isEmpty()){
						LoadingScreen.main(null);
					}
					Set<? extends OWLAxiom> justificationSet = justificationQueue.take();
					System.out.println("Justification Set");
						//if we're not allowing users to select "yes" for all justification axioms then for set<owl axiom> in allJustifications
						//if set not subset of removeAxioms+current axiom in iteration then ask for user input else skip to next justification set (break)
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
											getAxiomWeight(allJustifications, new HashSet<>());
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
												ComputeDiagnosesThread diagnosesRunnable = new ComputeDiagnosesThread(allJustifications, new HashSet<>(), save_filename);
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
									ComputeDiagnosesThread diagnosesRunnable = new ComputeDiagnosesThread(allJustifications, new HashSet<>(), save_filename);
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

	/*
 * Save the given string into the specified filePath
 */
    public static void appendTextToFile(String str, String filePath) throws IOException {
		
		// File file = GeneralTools.createFile(filePath);

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

	private static Set<? extends OWLAxiom> checkAxiomSelection(Set<Set<? extends OWLAxiom>> allJustifications){
		for (Set<? extends OWLAxiom> justificationSet : allJustifications){
			if (keepAxioms.containsAll(justificationSet)){
				return justificationSet;
			} 
		}
		return null;
	}

	public static void computeDiagnoses(Set<Set<? extends OWLAxiom>> allJustifications, Set<Set<? extends OWLAxiom>> allOptDiagnoses, String outFileName) throws IOException, OWLOntologyCreationException, OWLOntologyStorageException, EntityCheckerException{
		allOptimalDiagnoses = allOptDiagnoses;
		String outDirStr = "Repair";
        String mDsID = "repair";
        logger.info("Creating Program");
		createProgram(allJustifications, outDirStr);

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
		String programFileName = "pi.txt";
		appendTextToFile(keepAxiomsProgram.toString(), outDirStr + File.separator + programFileName);
		appendTextToFile(removeAxiomsProgram.toString(), outDirStr + File.separator + programFileName);



		logger.info("Extracting All Minimal Classical Diagnoses");
		HelperFunctions.runProgram(mDsID, outDirStr, true, false, false, Optional.empty());
		allOptimalDiagnoses.addAll(HelperFunctions.returnResult(mDsID, outDirStr));

		logger.info("Generating output file");
		HelperFunctions.saveResult(allOptimalDiagnoses, mDsID, outDirStr);

		int counter = 1;
		for (Set<? extends OWLAxiom> axiomSets : allOptimalDiagnoses){
			OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
			OWLOntology ontology = manager.loadOntologyFromOntologyDocument(new File(ontologyPathStr));

			for (OWLAxiom axiom: axiomSets){
				manager.removeAxiom(ontology, axiom);
			}
			File outputFile = new File(HelperFunctions.getRepairFilePathStr(outDirStr, outFileName+"_"+Integer.toString(counter)+".owl"));
			OWLDocumentFormat format = manager.getOntologyFormat(ontology);
			manager.saveOntology(ontology, format, new FileOutputStream(outputFile));
			counter += 1;
		}
	}

    private static void getAxiomWeight(Set<Set<? extends OWLAxiom>> allJustifications, Set<Set<? extends OWLAxiom>> allOptDiagnoses) throws IOException, EntityCheckerException, OWLOntologyCreationException, OWLOntologyStorageException{
		allOptimalDiagnoses = allOptDiagnoses;
		String outDirStr = "Repair";
        String mDsID = "repair";
        logger.info("Creating Program");
		createProgram(allJustifications, outDirStr);

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
		String programFileName = "pi.txt";
		appendTextToFile(keepAxiomsProgram.toString(), outDirStr + File.separator + programFileName);
		appendTextToFile(removeAxiomsProgram.toString(), outDirStr + File.separator + programFileName);



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

	private static void createProgram(Set<Set<? extends OWLAxiom>> allJustifications, String outDirStr)
			throws IOException {
        String programFileName = "pi.txt";
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

	public static void computeRepairs(String outDirStr, String mDsID, String ontologyPath, String outputFileName) throws IOException, EntityCheckerException, OWLOntologyCreationException, OWLOntologyStorageException{
		String tempfolderPath = "tempRepairsFolder"; // Path of the folder to create
		outDirStr = outDirStr + "/" + tempfolderPath;
		File repFolder = new File(outDirStr);
		repFolder.mkdir();
		int counter = 1;

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

		

	File[] allContents = repFolder.listFiles();
    if (allContents != null) {
        for (File file : allContents) {
            file.delete();
        }
    }
	repFolder.delete();
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
