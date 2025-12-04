package de.tu_dresden.lat.tools;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;
import org.semanticweb.elk.owlapi.ElkReasoner;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLEquivalentClassesAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;


public class DefectSelector {
    
    private static final Logger logger = Logger.getLogger(DefectSelector.class); 
    private static Map<OWLClass, Integer> classConnectivityMap;
/**
 * Given an ontology select a subclass axiom such that having it as defect will result into possibly many minimal diagnoses
 * @param OWLOntology
 * @return selected defect axiom
 * @throws OWLOntologyCreationException 
 */
    public static OWLAxiom selectDefect(OWLOntology ontology) throws OWLOntologyCreationException{
        classConnectivityMap = new java.util.HashMap<>();
        Set<OWLClass> classesInSubClassAxioms = subClassAxiomClasses(ontology); //classes that are involved in subclassof axioms with only atomic concepts
        equivalentClassAxiomClasses(ontology);
        classConnectivityMap = classConnectivityMap.entrySet().stream()
            .filter(cls ->  
                !cls.getKey().isOWLThing() 
                && !cls.getKey().isOWLNothing())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        classConnectivityMap = classConnectivityMap.entrySet().stream()
            .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
            .collect(Collectors.toMap(
                Map.Entry::getKey, Map.Entry::getValue,
                (oldVal, newVal) -> oldVal, java.util.LinkedHashMap::new));
        
        OWLClass candidateClass = null;
        for (OWLClass cls : classConnectivityMap.keySet()){
            if (classesInSubClassAxioms.contains(cls)){
                candidateClass = cls;
                break;
            }
        }
        // System.out.println("Candidate class is OWL thing or Nothing? "+ (candidateClass.isOWLThing()|| candidateClass.isOWLNothing()));
        logger.info("Selected most connected class: "+candidateClass.toString());
        OWLAxiom defectAxiom = selectSubClassAxiom(ontology, candidateClass);
        return defectAxiom;
    } 

    private static Set<OWLClass> subClassAxiomClasses(OWLOntology ontology){
        Set<OWLSubClassOfAxiom> axioms = ontology.getAxioms(AxiomType.SUBCLASS_OF);
        Set<OWLClass> simpleSubClassClasses = new HashSet<>();
        for (OWLSubClassOfAxiom axiom : axioms){
            if (!axiom.getSubClass().isAnonymous() && !axiom.getSuperClass().isAnonymous()){
                simpleSubClassClasses.addAll(axiom.getClassesInSignature());
            }
            axiom.getClassesInSignature().forEach( cls -> {
                classConnectivityMap.putIfAbsent(cls, 0);
                classConnectivityMap.put(cls, classConnectivityMap.get(cls)+1);
            });
        }
        return simpleSubClassClasses;
    }

    private static Map<OWLClass, Integer> equivalentClassAxiomClasses(OWLOntology ontology){
        Set<OWLEquivalentClassesAxiom> axioms = ontology.getAxioms(AxiomType.EQUIVALENT_CLASSES);
        for (OWLEquivalentClassesAxiom axiom : axioms){
            axiom.getClassesInSignature().forEach( cls -> {
                classConnectivityMap.putIfAbsent(cls, 0);
                classConnectivityMap.put(cls, classConnectivityMap.get(cls)+1);
            });
        }
        return classConnectivityMap;
    }

    public static OWLAxiom selectSubClassAxiom(OWLOntology ontology, OWLClass cls) throws OWLOntologyCreationException{
        OWLOntologyManager manager = ontology.getOWLOntologyManager();
        OWLDataFactory dataFactory = manager.getOWLDataFactory();

        ElkReasonerFactory reasonerFactory = new ElkReasonerFactory();
        ElkReasoner reasoner = reasonerFactory.createReasoner(ontology);
        
        Set<OWLClass> inferredSubClasses = reasoner.getSubClasses(cls, false).getFlattened();

        inferredSubClasses.remove(dataFactory.getOWLNothing());

        OWLAxiom selectedAxiom = null;
        if (!inferredSubClasses.isEmpty()) { 
            //select the subClass with highest involvement score
            OWLClass subClass = inferredSubClasses.stream().max(Comparator.comparingInt(subCls -> classConnectivityMap.get(subCls))).get();               
            selectedAxiom = dataFactory.getOWLSubClassOfAxiom(subClass, cls);
        } else {
            Set<OWLClass> inferredSupClasses = reasoner.getSuperClasses(cls, false).getFlattened();
            // System.out.println("Inferred superclasses: " + inferredSupClasses);
            inferredSupClasses.remove(dataFactory.getOWLThing());
            logger.info("Inferred Superclasses size: "+inferredSubClasses.size());
            if (! inferredSupClasses.isEmpty()){
                OWLClass supClass = inferredSupClasses.stream().max(Comparator.comparingInt(supCls -> classConnectivityMap.get(supCls))).get();
                selectedAxiom = dataFactory.getOWLSubClassOfAxiom(cls, supClass);
            }
        }
        reasoner.dispose();
        if(selectedAxiom != null){
            logger.info("Defect selected: "+selectedAxiom.toString());
        } else {
            logger.info("No defect selected.");
        }
        
        System.gc();
        return selectedAxiom;
    }
}
