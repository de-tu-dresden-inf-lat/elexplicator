package de.tu_dresden.lat.diagnoses;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
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
		} catch (Exception e) {
			logger.warn("Thread exception: " + e.getMessage());
			Thread.currentThread().interrupt(); 
		} finally {
			ComputeRepair.justificationsCompleted = true;
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
	Set<OWLAxiom> interestingAxioms;
	ReasonerName reasonerName;
	public ComputeAxiomWeightThread(String outDirStr, String mDsID, String ontologyPath, String outputFileName, Set<Set<? extends OWLAxiom>> allOptimalDiagnoses, Set<OWLAxiom> interestingAxioms, ReasonerName reasonerName){
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
			int counter = ComputeRepair.computeRepairs(tempOutDirStr, mDsID, ontologyPath, outputFileName, allOptimalDiagnoses);
			ComputeRepair.computeAxiomWeight(counter, tempOutDirStr, interestingAxioms, reasonerName);
		} catch (Exception e){
			e.printStackTrace();
			Thread.currentThread().interrupt();
		} finally {
			// ComputeRepair.cleanup(tempOutDirStr);
			ComputeRepair.cleanup();
		}
		
	}
}

//Thread where a snapshot of justifications is taken every 5 seconds and the frequency of each axiom is updated in the map
class SortJustificationsThread implements Runnable{
	private static final long SNAPSHOT_INTERVAL = 5000; 
	private static final Logger logger = Logger.getLogger(SortJustificationsThread.class);
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
							ComputeRepair.axiomMap.put(justificationAxiom, ComputeRepair.axiomMap.getOrDefault(justificationAxiom, 0) + 1);
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