package de.tu_dresden.lat.diagnoses;

import java.util.Set;

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
			ComputeRepair.computeDiagnoses(allJustifications, allOptDiagnoses, outDirStr, outFileName);
		} catch (Exception e){
			Thread.currentThread().interrupt();
		}
	}
	
}
