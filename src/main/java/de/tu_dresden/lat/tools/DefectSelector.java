package de.tu_dresden.lat.tools;

import java.util.Comparator;
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
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;


public class DefectSelector {
    
    private static final Logger logger = Logger.getLogger(DefectSelector.class); 

/**
 * Given an ontology select a subclass axiom such that having it as defect will result into possibly many minimal diagnoses
 * @param OWLOntology
 * @return selected defect axiom
 * @throws OWLOntologyCreationException 
 */
    public static OWLAxiom selectDefect(OWLOntology ontology) throws OWLOntologyCreationException{
        Set<OWLClass> candidateClasses = getClassesInSubclassRelations(ontology);
        logger.info("Candidate classes size: "+candidateClasses.size());
        OWLClass connClass = candidateClasses.stream()
            .max(Comparator.comparingInt(cls -> computeScore(cls, ontology))).get();
        logger.info("Selected most connected class: "+connClass.toString());
        OWLAxiom defectAxiom = selectSubClassAxiom(ontology, connClass);
        return defectAxiom;
    } 

    public static Set<OWLClass> getClassesInSubclassRelations(OWLOntology ontology){
        Set<OWLAxiom> axioms = ontology.getAxioms(AxiomType.SUBCLASS_OF).stream().filter(
            axiom -> {
                OWLClassExpression sub = axiom.getSubClass();
                OWLClassExpression sup = axiom.getSuperClass();
                return !sub.isAnonymous() && !sup.isAnonymous() && !sub.isBottomEntity() && !sup.isTopEntity();
            }).collect(Collectors.toSet());
        
            Set<OWLClass> classes = axioms.stream()
                .flatMap(ax -> Stream.of(((OWLSubClassOfAxiom) ax).getSubClass(), ((OWLSubClassOfAxiom) ax).getSuperClass()))
                .filter(c -> !c.isAnonymous())
                .map(c -> c.asOWLClass())
                .filter(c -> !c.isOWLThing() && !c.isOWLThing())
                .collect(Collectors.toSet());
            
        return classes;
    }

    public static Integer computeScore(OWLClass cls, OWLOntology ontology){
        int score = ontology.getSubClassAxiomsForSubClass(cls).size() +
                ontology.getSubClassAxiomsForSuperClass(cls).size() + 
                ontology.getEquivalentClassesAxioms(cls).size() +
                ontology.getDisjointClassesAxioms(cls).size();
        
        return score;
    }

    public static OWLAxiom selectSubClassAxiom(OWLOntology ontology, OWLClass cls) throws OWLOntologyCreationException{
        OWLOntologyManager manager = ontology.getOWLOntologyManager();
        OWLDataFactory dataFactory = manager.getOWLDataFactory();

        // OWLOntology clsModule = Segmenter.getStarModule(ontology, cls.getSignature(), ontology.getOntologyID().getOntologyIRI().get());
        
        ElkReasonerFactory reasonerFactory = new ElkReasonerFactory();
        ElkReasoner reasoner = reasonerFactory.createReasoner(ontology);
        
        Set<OWLClass> inferredSubClasses = reasoner.getSubClasses(cls, false).getFlattened();

        // System.out.println("Inferred subclasses: " + inferredSubClasses);
        inferredSubClasses.remove(dataFactory.getOWLNothing());

        OWLAxiom selectedAxiom = null;
        logger.info("Inferred Subclasses Size: "+inferredSubClasses.size());
        if (!inferredSubClasses.isEmpty()) { 
            //select the subClass with highest involvement score
            OWLClass subClass = inferredSubClasses.stream().max(Comparator.comparingInt(subCls -> computeScore(subCls, ontology))).get();               
            selectedAxiom = dataFactory.getOWLSubClassOfAxiom(subClass, cls);
        } else {
            Set<OWLClass> inferredSupClasses = reasoner.getSuperClasses(cls, false).getFlattened();
            // System.out.println("Inferred superclasses: " + inferredSupClasses);
            inferredSupClasses.remove(dataFactory.getOWLThing());
            logger.info("Inferred Superclasses size: "+inferredSubClasses.size());
            if (! inferredSupClasses.isEmpty()){
                OWLClass supClass = inferredSupClasses.stream().max(
                    Comparator.comparingInt(supCls -> computeScore(supCls, ontology))).get();
                selectedAxiom = dataFactory.getOWLSubClassOfAxiom(cls, supClass);
            }
        }
        logger.info("Defect selected: "+selectedAxiom.toString());
        return selectedAxiom;
    }
}
