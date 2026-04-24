package de.tu_dresden.lat.diagnoses;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

import org.semanticweb.HermiT.ReasonerFactory;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.reasoner.Node;

import de.tu_dresden.inf.lat.prettyPrinting.formatting.SimpleOWLFormatterCl;
import de.tu_dresden.inf.lat.prettyPrinting.formatting.SimpleDLFormatter$;
import de.tu_dresden.lat.data.names.ReasonerName;


public class ClassHierarchyDifference {
    private static SimpleOWLFormatterCl sOWLFormatter = new SimpleOWLFormatterCl(true, SimpleDLFormatter$.MODULE$,
		true);
    // Compute the class hierarchy difference between two ontologies and output the result to json file.
    String outputDirStr;
    String ontologyPathStr;
    Set<OWLAxiom> keepAxioms;
    Set<OWLAxiom> removeAxioms;
    OWLAxiom selectedAxiom;
    ReasonerName reasonerName;
    Set<Set <? extends OWLAxiom>> allJustifications;

    // Map<OWLClass, Object> hierarchyMap1; // hierarchy map for the first ontology without removing the selected axiom
    // Map<OWLClass, Object> hierarchyMap2;   // hierarchy map for the second ontology with removing the selected axiom
    Set<List<String>> hierarchyMap1; 
    Set<List<String>> hierarchyMap2;
    Map<String, Set<List<String>>> hierarchyDifference;
    Boolean repairYes;
    Boolean repairNo;

    public ClassHierarchyDifference(Set<Set<? extends OWLAxiom>> allJustifications, String outputDirStr, String ontologyPathStr, Set<OWLAxiom> keepAxioms,
            Set<OWLAxiom> removeAxioms, OWLAxiom selectedAxiom, ReasonerName reasonerName) {
        this.allJustifications = allJustifications;
        this.outputDirStr = outputDirStr;
        this.ontologyPathStr = ontologyPathStr;
        this.keepAxioms = keepAxioms;
        this.removeAxioms = removeAxioms;
        this.selectedAxiom = selectedAxiom;
        this.reasonerName = reasonerName;

        this.hierarchyMap1 = new HashSet<>();
        this.hierarchyMap2 = new HashSet<>();
        this.hierarchyDifference = new HashMap<>();
    }

    public void getClassHierarchy(String ontologyPath) throws OWLOntologyCreationException {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
		OWLOntology ontology = manager.loadOntologyFromOntologyDocument(new File(ontologyPath));
  
        manager.removeAxioms(ontology, removeAxioms);  
        try {
            ComputeRepair.saveRepairOntology(ontology, outputDirStr, "ontoYes");
        } catch (OWLOntologyCreationException | OWLOntologyStorageException | FileNotFoundException e) {
            e.printStackTrace();
        }

        keepAxioms.add(selectedAxiom);
        Set<? extends OWLAxiom> unsatJust_yes  = ComputeRepair.checkAxiomSelection(allJustifications, keepAxioms);
        this.hierarchyMap1 = getInferredHierarchy(ontology);

        if (unsatJust_yes == null || unsatJust_yes.isEmpty()){
            this.repairYes = true;
        } else {
            this.repairYes = false;
        }
         
        manager.removeAxiom(ontology, selectedAxiom);
        try {
            ComputeRepair.saveRepairOntology(ontology, outputDirStr, "ontoNo");
        } catch (OWLOntologyCreationException | OWLOntologyStorageException | FileNotFoundException e) {
            e.printStackTrace();
        }        
        keepAxioms.remove(selectedAxiom);
        Set<? extends OWLAxiom> unsatJust_no  = ComputeRepair.checkAxiomSelection(allJustifications, keepAxioms);
        if (unsatJust_no == null || unsatJust_no.isEmpty()){
            this.repairNo = true;
        } else {
            this.repairNo = false;
        }
        this.hierarchyMap2 = getInferredHierarchy(ontology);
        
        getHierarchyDifference(hierarchyMap1, hierarchyMap2);

    }

    private void getHierarchyDifference(Set<List<String>> initialHierarchy, Set<List<String>> resultHierarchy){
		Set<List<String>> addedEdges = new HashSet<>();
		Set<List<String>> removedEdges = new HashSet<>();
		Map<String, Set<List<String>>> differencesMap = new HashMap<>();
		for (List<String> edge : resultHierarchy){
			if (!initialHierarchy.contains(edge)){
				addedEdges.add(edge);
			}
		}
		differencesMap.put("addedEdges", addedEdges);

		for (List<String> edge : initialHierarchy){
			if (!resultHierarchy.contains(edge)){
				removedEdges.add(edge);
			}
		}
		differencesMap.put("removedEdges", removedEdges);
		
		this.hierarchyDifference = differencesMap;
	}

    private Set<List<String>> getInferredHierarchy(OWLOntology ontology) {

		OWLReasoner reasoner = null;
        if (this.reasonerName == ReasonerName.Elk) {
            // Use ELK reasoner to compute class hierarchy
            OWLReasonerFactory reasonerFactory = new ElkReasonerFactory();
            reasoner = reasonerFactory.createNonBufferingReasoner(ontology);
        } else {
            // Use HermiT reasoner to compute class hierarchy
            OWLReasonerFactory reasonerFactory = new ReasonerFactory();
            reasoner = reasonerFactory.createNonBufferingReasoner(ontology);
        }

		reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY);

		Set<List<String>> edges = new HashSet<>();

		OWLDataFactory df =
				ontology.getOWLOntologyManager().getOWLDataFactory();

		OWLClass top = df.getOWLThing();
		OWLClass bottom = df.getOWLNothing();

		Set<OWLClass> unsatisfiableClasses = reasoner.getUnsatisfiableClasses().getEntitiesMinusBottom();

		traverse(reasoner, top, edges, unsatisfiableClasses);

		if (!unsatisfiableClasses.isEmpty()) {
			edges.add(List.of(sOWLFormatter.format(top), sOWLFormatter.format(bottom)));
		}

		for (OWLClass unsat : unsatisfiableClasses) {
			edges.add(List.of(sOWLFormatter.format(bottom), sOWLFormatter.format(unsat)));
		}

		return edges;
	}

	private static void traverse(OWLReasoner reasoner, OWLClass parent, Set<List<String>> edges, Set<OWLClass> unsatisfiableClasses) {

		for (Node<OWLClass> node : reasoner.getSubClasses(parent, true)) {

			for (OWLClass child : node) {

				if (child.isAnonymous()) continue;
				if (child.isOWLThing()) continue;
				if (child.isOWLNothing()) continue;
				if (unsatisfiableClasses.contains(child)) continue;
				if (!child.isOWLClass()) continue;

				edges.add(List.of(
						sOWLFormatter.format(parent),
						sOWLFormatter.format(child)
				));

				traverse(reasoner, child, edges, unsatisfiableClasses);
			}
		}
	}
    
}
