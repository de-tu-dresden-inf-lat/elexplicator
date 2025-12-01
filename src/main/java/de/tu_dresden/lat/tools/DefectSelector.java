package de.tu_dresden.lat.tools;

import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.log4j.Logger;
import org.semanticweb.elk.owlapi.ElkReasoner;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
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
        subClassAxiomClasses(ontology);
        Set<OWLClass> classesInSubClassAxioms = classConnectivityMap.keySet();
        equivalentClassAxiomClasses(ontology);
        classConnectivityMap.entrySet().stream().filter(cls -> classesInSubClassAxioms.contains(cls.getKey()) && !cls.getKey().isOWLThing() && !cls.getKey().isOWLNothing());

        classConnectivityMap = classConnectivityMap.entrySet().stream()
            .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
            .collect(Collectors.toMap(
                Map.Entry::getKey, Map.Entry::getValue,
                (oldVal, newVal) -> oldVal, java.util.LinkedHashMap::new));
        
        OWLClass candidateClass = classConnectivityMap.entrySet().iterator().next().getKey();
        logger.info("Selected most connected class: "+candidateClass.toString());
        OWLAxiom defectAxiom = selectSubClassAxiom(ontology, candidateClass);
        return defectAxiom;
    } 

    private static Map<OWLClass, Integer> subClassAxiomClasses(OWLOntology ontology){
        Set<OWLSubClassOfAxiom> axioms = ontology.getAxioms(AxiomType.SUBCLASS_OF);
        for (OWLSubClassOfAxiom axiom : axioms){
            axiom.getClassesInSignature().forEach( cls -> {
                classConnectivityMap.putIfAbsent(cls, 0);
                classConnectivityMap.put(cls, classConnectivityMap.get(cls)+1);
            });
        }
        return classConnectivityMap;
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

    // public static Set<OWLClass> getClassesInSubclassRelations(OWLOntology ontology){
    //     Set<OWLAxiom> axioms = ontology.getAxioms(AxiomType.SUBCLASS_OF).stream().filter(
    //         axiom -> {
    //             OWLClassExpression sub = axiom.getSubClass();
    //             OWLClassExpression sup = axiom.getSuperClass();
    //             return !sub.isAnonymous() && !sup.isAnonymous() && !sub.isBottomEntity() && !sup.isTopEntity();
    //         }).collect(Collectors.toSet());
        
    //         Set<OWLClass> classes = axioms.stream()
    //             .flatMap(ax -> Stream.of(((OWLSubClassOfAxiom) ax).getSubClass(), ((OWLSubClassOfAxiom) ax).getSuperClass()))
    //             .filter(c -> !c.isAnonymous())
    //             .map(c -> c.asOWLClass())
    //             .filter(c -> !c.isOWLThing() && !c.isOWLThing())
    //             .collect(Collectors.toSet());
            
    //     return classes;
    // }

    // public static Integer computeScore(OWLClass cls, OWLOntology ontology){
    //     int score = ontology.getSubClassAxiomsForSubClass(cls).size() +
    //             ontology.getSubClassAxiomsForSuperClass(cls).size() + 
    //             ontology.getEquivalentClassesAxioms(cls).size() +
    //             ontology.getDisjointClassesAxioms(cls).size();
        
    //     return score;
    // }

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
        logger.info("Defect selected: "+selectedAxiom.toString());
        return selectedAxiom;
    }
}
