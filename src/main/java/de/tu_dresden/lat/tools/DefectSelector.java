package de.tu_dresden.lat.tools;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.io.File;

import org.apache.log4j.Logger;
import org.semanticweb.elk.owlapi.ElkReasoner;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
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

    /**
     * Given an ontology select n classes as defects
     * @param ontology
     * @param n
     * @return set of selected defect classes
     * @throws OWLOntologyCreationException 
     */
    public static Set<OWLAxiom> selectNDefects(OWLOntology ontology, int n) throws OWLOntologyCreationException{
        //TODO : get top n classes from classConnectivityMap
        //for each class select the most connected subclass if none, select the most connected superclass
        //collect the appropriate axiom in set if not already present. If present, try another sub/super class
        //return the set of axioms
        Set<OWLAxiom> selectedDefects = new HashSet<>();
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
        
        // System.out.print("Class Connectivity Map is of type " + classesInSubClassAxioms.getClass());
        int classIndex = 0;
        while (selectedDefects.size() < n){
            if (classIndex >= classConnectivityMap.size()){
                break;
            }
            OWLClass candidateClass = (OWLClass)classConnectivityMap.keySet().toArray()[classIndex];
            if (classesInSubClassAxioms.contains(candidateClass)){
                OWLAxiom defectAxiom = selectNewSubClassAxiom(ontology, candidateClass, selectedDefects);
                if (defectAxiom != null){
                    // System.out.println("Defect already exists? " + selectedDefects.contains(defectAxiom));
                    selectedDefects.add(defectAxiom);
                }
            }
            classIndex++;
        }
        // System.out.println("Selected Defects:");
        // for (OWLAxiom ax : selectedDefects){
        //     System.out.println(ax.toString());
        // }
        if (selectedDefects.size() < n){
            logger.warn("Only "+selectedDefects.size()+" defects could be selected.");
        }
        return selectedDefects;
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

    public static OWLAxiom selectNewSubClassAxiom(OWLOntology ontology, OWLClass cls, Set<OWLAxiom> existingDefects) throws OWLOntologyCreationException{
        System.out.println("Selecting new defect for class: " + cls.toString());
        Boolean foundNewDefect = false;
        OWLOntologyManager manager = ontology.getOWLOntologyManager();
        OWLDataFactory dataFactory = manager.getOWLDataFactory();

        ElkReasonerFactory reasonerFactory = new ElkReasonerFactory();
        ElkReasoner reasoner = reasonerFactory.createReasoner(ontology);
        
        Set<OWLClass> inferredSubClasses = reasoner.getSubClasses(cls, false).getFlattened();
        Set<OWLClass> inferredSuperClasses = reasoner.getSuperClasses(cls, false).getFlattened();

        inferredSubClasses.remove(dataFactory.getOWLNothing());
        inferredSuperClasses.remove(dataFactory.getOWLThing());

        List<OWLClass> sortedInferredSubClasses = inferredSubClasses.stream()
        .sorted(Collections.reverseOrder(Comparator.comparingInt(subCls -> classConnectivityMap.getOrDefault(subCls, 0))))
        .collect(Collectors.toList());
        


        List<OWLClass> sortedInferredSuperClasses = inferredSuperClasses.stream()
        .sorted(Collections.reverseOrder(Comparator.comparingInt(supCls -> classConnectivityMap.getOrDefault(supCls, 0))))
        .collect(Collectors.toList());

        OWLAxiom selectedAxiom = null;
        while (!sortedInferredSubClasses.isEmpty()){
            OWLClass subClass = sortedInferredSubClasses.remove(0);
            selectedAxiom = dataFactory.getOWLSubClassOfAxiom(subClass, cls);
            if (!existingDefects.contains(selectedAxiom)){
                foundNewDefect = true;
                break;
            }
        }
        if (!foundNewDefect){
            while (!sortedInferredSuperClasses.isEmpty()){
                OWLClass supClass = sortedInferredSuperClasses.remove(0);
                selectedAxiom = dataFactory.getOWLSubClassOfAxiom(cls, supClass);
                if (!existingDefects.contains(selectedAxiom)){
                    foundNewDefect = true;
                    break;
                }
            }
        }
        reasoner.dispose();
        if(foundNewDefect){
            logger.info("Defect selected: "+selectedAxiom.toString());
        } else {
            logger.info("No defect selected.");
        }        
        System.gc();
        return selectedAxiom;       
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
            OWLClass subClass = inferredSubClasses.stream().max(Comparator.comparingInt(subCls -> classConnectivityMap.get(subCls))).get();               
            selectedAxiom = dataFactory.getOWLSubClassOfAxiom(subClass, cls);
        } else {
            Set<OWLClass> inferredSupClasses = reasoner.getSuperClasses(cls, false).getFlattened();
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

    public static void main(String[] args) {
        // TODO Auto-generated method stub
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        try {
            OWLOntology ontology = manager.loadOntologyFromOntologyDocument(new File("src/test/TestOntology/HospitalManagement.owl"));
            Set<OWLAxiom> defectAxioms = selectNDefects(ontology, 10);
        } catch (OWLOntologyCreationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}
