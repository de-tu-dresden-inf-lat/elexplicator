package de.tu_dresden.lat.diagnoses;

import java.security.cert.CertPathValidatorException.Reason;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLOntology;

import de.tu_dresden.lat.data.names.ReasonerName;

//Thread to compute justifications to an ontology for a given axiom and update id map
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
			Thread.currentThread().interrupt(); 
			e.printStackTrace();
		}
		ComputeRepair.justificationsCompleted = true;
		
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
		try{
			String tempfolderPath = "tempRepairsFolder"; // Path of the folder to create
			String tempOutDirStr = outDirStr + "/" + tempfolderPath;
			int counter = ComputeRepair.computeRepairs(tempOutDirStr, mDsID, ontologyPath, outputFileName, allOptimalDiagnoses);
			ComputeRepair.computeAxiomWeight(counter, tempOutDirStr, interestingAxioms, reasonerName);
		} catch (Exception e){
			Thread.currentThread().interrupt();
			e.printStackTrace();
		}
		
	}
}

//Thread where a snapshot of justifications is taken every 5 seconds and the frequency of each axiom is updated in the map
class SortJustificationsThread implements Runnable{
	private static final long SNAPSHOT_INTERVAL = 5000; 
	
	@Override
	public void run(){
		try{
			while (!ComputeRepair.justificationQueue.isEmpty() || !ComputeRepair.justificationsCompleted){
				Set<Set<? extends OWLAxiom>> justificationsSnapshot = new HashSet<>();
				long startTime = System.currentTimeMillis();
				while (System.currentTimeMillis() - startTime < SNAPSHOT_INTERVAL) {
					Set<? extends OWLAxiom> queueElement = ComputeRepair.justificationQueue.poll(SNAPSHOT_INTERVAL, TimeUnit.MILLISECONDS);
					if (queueElement != null){
						justificationsSnapshot.add(queueElement);
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
		} catch (InterruptedException e) {
            Thread.currentThread().interrupt();
			e.printStackTrace();
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