package de.tu_dresden.lat.diagnoses;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLOntology;

import de.tu_dresden.lat.data.names.ReasonerName;

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
		ComputeRepair.justificationsCompleted = true;
		
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
    String outDirStr;

	public ComputeDiagnosesThread(Set<Set<? extends OWLAxiom>> allJustifications, Set<Set<? extends OWLAxiom>> allOptDiagnoses, String outFileName, String outDirStr){
		this.allJustifications = allJustifications;
		this.allOptDiagnoses = allOptDiagnoses;
		this.outFileName = outFileName;
        this.outDirStr = outDirStr;
	}

	@Override
	public void run() {
		try{
			ComputeRepair.saveFunction(allJustifications, allOptDiagnoses, outDirStr, outFileName);
		} catch (Exception e){
			Thread.currentThread().interrupt();
		}
	}
	
}

//thread where the justification axioms are sorted on the basis of their frequency
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
			// Thread.sleep(5000);
		} catch (InterruptedException e) {
            Thread.currentThread().interrupt();
		}
	}
}