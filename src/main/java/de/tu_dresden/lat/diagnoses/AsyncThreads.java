package de.tu_dresden.lat.diagnoses;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.apache.log4j.Logger;

import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLOntology;

import de.tu_dresden.lat.data.names.ReasonerName;

//Thread to compute justifications to an ontology for a given axiom and update id map
class ComputeJustificationsThread implements Runnable{
	private ReasonerName reasonerName;
	private OWLAxiom axiom;
	private OWLOntology ontology;

	Logger logger = Logger.getLogger(ComputeJustificationsThread.class);

	public ComputeJustificationsThread(ReasonerName reasonerName, OWLAxiom axiom, OWLOntology ontology){
		this.reasonerName = reasonerName;
		this.axiom = axiom;
		this.ontology = ontology;
	}

	@Override
	public void run(){	
		try{
			ComputeRepair.allJustifications = ComputeRepair.getAllJustificationsAsync(reasonerName, axiom, ontology, ComputeRepair.justificationQueue);        
			if (ComputeRepair.allJustifications != null){
				ComputeRepair.fillMap(ComputeRepair.allJustifications);
				HelperFunctions.identifiers2Axioms = ComputeRepair.identifiers2Axioms;
			}			
			ComputeRepair.justificationsCompleted = true;
		} catch (Exception e) {
			logger.warn("Thread exception: " + e.getMessage());
			Thread.currentThread().interrupt(); 
		} finally {
			ComputeRepair.justificationsCompleted = true;
		}
		
	}
}

//another thread to compute and set the diagnosis and status 
class ComputeDiagnosisThread implements Runnable{
	private OWLOntology ontology;
	private OWLAxiom axiom;
	private String outDirStr;
	private ReasonerName reasonerName;

	// private static final long CHECK_INTERVAL = 1000; 

	Logger logger = Logger.getLogger(ComputeDiagnosisThread.class);

	public ComputeDiagnosisThread(OWLOntology ontology, OWLAxiom axiom, String outDirStr, ReasonerName reasonerName){
		this.ontology = ontology;
		this.axiom = axiom;
		this.outDirStr = outDirStr;
		this.reasonerName = reasonerName;
	}

	@Override
	public void run(){
		try{
			
			while(true){
				if (ComputeRepair.justificationsCompleted){
					ASPMinimalDiagnoses.getAllClassicalRepairs(axiom, ontology, "minimal", outDirStr, new HashSet<>(), new HashSet<>(), reasonerName);
					ComputeRepair.minimalDiagnoses = new HashSet<>(ASPMinimalDiagnoses.allOptimalDiagnosesMin);
					ComputeRepair.allDiagnoses = new HashSet<>(ASPMinimalDiagnoses.allDiagnoses);
					ComputeRepair.diagnosisComputed = true;
					break;
				}
			}
		} catch (Exception e){
			logger.warn("Thread exception: " + e.getMessage());
			Thread.currentThread().interrupt();
		}
		
	}
}

//Thread to get the percentage of entailments of interesting axioms in the repaired ontologies obtained from current state
class ComputeAxiomWeightThread implements Runnable{
	String outDirStr;
	String mDsID;
	String ontologyPath;
	String outputFileName;
	Set<Set<? extends OWLAxiom>> allOptimalDiagnoses;
	Set<? extends OWLAxiom> interestingAxioms;
	ReasonerName reasonerName;

	Logger logger = Logger.getLogger(ComputeAxiomWeightThread.class);

	public ComputeAxiomWeightThread(String outDirStr, String mDsID, String ontologyPath, String outputFileName, Set<Set<? extends OWLAxiom>> allOptimalDiagnoses, Set<? extends OWLAxiom> interestingAxioms, ReasonerName reasonerName){
		this.outDirStr = outDirStr;
		this.mDsID = mDsID;
		this.ontologyPath = ontologyPath;
		this.outputFileName = outputFileName;
		this.allOptimalDiagnoses = allOptimalDiagnoses;
		this.interestingAxioms = interestingAxioms;
		this.reasonerName = reasonerName;
	}

	@Override
	public void run(){
		String tempfolderPath = "tempRepairsFolder"; // Path of the folder to create
		String tempOutDirStr = outDirStr + "/" + tempfolderPath;
		try{			
			// int counter = ComputeRepair.computeRepairs(tempOutDirStr, mDsID, ontologyPath, outputFileName, allOptimalDiagnoses);
			Map<OWLAxiom, List<OWLOntology>> modulesMap = ComputeRepair.computeRepairsModules(ontologyPath, allOptimalDiagnoses, interestingAxioms);
			int totalRepairs = allOptimalDiagnoses.size();
			// ComputeRepair.computeAxiomWeight(counter, tempOutDirStr, interestingAxioms, reasonerName);
			ComputeRepair.computeAxiomWeight(modulesMap, reasonerName, totalRepairs);
		} catch (Exception e){
			logger.warn("Thread exception: " + e.getMessage());
			Thread.currentThread().interrupt();
		} 
		// finally {
		// 	ComputeRepair.cleanup();
		// }
		
	}
}

//Thread where a snapshot of justifications is taken every 5 seconds and the frequency of each axiom is updated in the map
class FrequencySortingThread_interval implements Runnable{
	private static final long SNAPSHOT_INTERVAL = 5000; 
	private static final Logger logger = Logger.getLogger(FrequencySortingThread.class);
	@Override
	public void run(){
		try{
			while (!ComputeRepair.justificationQueue.isEmpty() || !ComputeRepair.justificationsCompleted){
				Set<Set<? extends OWLAxiom>> justificationsSnapshot = new HashSet<>();
				long startTime = System.currentTimeMillis();
				while (System.currentTimeMillis() - startTime < SNAPSHOT_INTERVAL) {
					try{
						Set<? extends OWLAxiom> queueElement = ComputeRepair.justificationQueue.poll(SNAPSHOT_INTERVAL, TimeUnit.MILLISECONDS);
						if (queueElement != null){
							justificationsSnapshot.add(queueElement);
						}
					} catch (InterruptedException e){
						logger.warn("Thread interrupted");
						Thread.currentThread().interrupt();
					}
				}
				if (!justificationsSnapshot.isEmpty()){
					for (Set<? extends OWLAxiom> justificationSet : justificationsSnapshot){			
						for (OWLAxiom justificationAxiom : justificationSet){
							//if the axiom is already in the map, increment the frequency else add it with frequency 1	
							ComputeRepair.axiomMap.put(justificationAxiom, ComputeRepair.axiomMap.getOrDefault(justificationAxiom, 0.0) + 1);
						}
					};
					ComputeRepair.isSnapshotActive = true;
				} 
			}
			ComputeRepair.isSnapshotActive = false;
		} catch (Exception e) {
			logger.warn("Thread exception: " + e.getMessage());
            // Thread.currentThread().interrupt();
		}
	}
}

class FrequencySortingThread implements Runnable{
	private static final Logger logger = Logger.getLogger(FrequencySortingThread.class);
	@Override
	public void run(){
		while(!ComputeRepair.justificationQueue.isEmpty() || !ComputeRepair.justificationsCompleted){
			ComputeRepair.isSnapshotActive = true;	
			Set<? extends OWLAxiom> queueElement = null;				
			try {
				queueElement = ComputeRepair.justificationQueue.poll(1, TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				logger.error(e);
			}
			if (queueElement != null){		
				for (OWLAxiom justificationAxiom : queueElement){
					// if the axiom is already in the map, increment the frequency else add it with frequency 1	
					ComputeRepair.axiomMap.put(justificationAxiom, ComputeRepair.axiomMap.getOrDefault(justificationAxiom, 0.0) + 1);
				}
			} 
		} 
		ComputeRepair.isSnapshotActive = false;		
	}
		
}

class EntropySortingThread implements Runnable{

	@Override
	public void run() {
		while (true) {
			ComputeRepair.isSnapshotActive = true;
			if (!ComputeRepair.diagnosisComputed && !ComputeRepair.justificationQueue.isEmpty()) {
				try {
					for (OWLAxiom axiom : ComputeRepair.justificationQueue.poll(1, TimeUnit.MILLISECONDS)) {
						ComputeRepair.axiomMap.putIfAbsent(axiom, 0.0);
					}
					ComputeRepair.isSnapshotActive = false;
					continue;
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else if (ComputeRepair.diagnosisComputed){
				Set<Set<? extends OWLAxiom>> allJustifications = ComputeRepair.allJustifications;
				Set<Set<? extends OWLAxiom>> minimalDiagnoses = ComputeRepair.minimalDiagnoses;
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
				ComputeRepair.axiomMap = new ConcurrentHashMap<>(entropyScoreMap);
				ComputeRepair.isSnapshotActive = false;
				break;
			}
		}
		
	}
}

class CheckMinimalityThread implements Callable<Boolean>{
	private OWLOntology ontology;
	private OWLAxiom axiom;
	private Set<OWLAxiom> removeAxioms;
	private String outDirStr;
	private ReasonerName reasonerName;
	
	public CheckMinimalityThread(OWLOntology ontology, OWLAxiom axiom, Set<OWLAxiom> removeAxioms, String outDirStr, ReasonerName reasonerName){
		this.ontology = ontology;
		this.axiom = axiom;
		this.removeAxioms = removeAxioms;
		this.outDirStr = outDirStr;
		this.reasonerName = reasonerName;
	}
	
	@Override
	public Boolean call() throws Exception {
		return ComputeRepair.checkDiagMinimality(ontology, axiom, removeAxioms, outDirStr, reasonerName);
	}
	
}