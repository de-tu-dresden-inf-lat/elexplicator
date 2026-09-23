package de.tu_dresden.lat.tools;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLEquivalentClassesAxiom;
import org.semanticweb.owlapi.model.OWLObjectIntersectionOf;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;

public class OntologyToDNF {
    //Takes an ontology and converts it to Definition Normal Form (i.e. only atomic concepts in axioms and complex concepts are defined via equivalence axioms)
    private static OWLOntology ontology;
    private static Map<OWLClassExpression, OWLClass> complexToAtomicMap;
    private static OWLDataFactory dataFactory;
    
    public OntologyToDNF(OWLOntology ontology) {
        this.ontology = ontology;
        this.complexToAtomicMap = new HashMap<>();
        this.dataFactory = ontology.getOWLOntologyManager().getOWLDataFactory();
    }

    public OWLOntology normalizeOntology(){
        Set<OWLSubClassOfAxiom> axioms = ontology.getAxioms(AxiomType.SUBCLASS_OF);
        for (OWLSubClassOfAxiom axiom : axioms){
            OWLSubClassOfAxiom normalizedAxiom = normalizeSubClassOfAxiom(axiom);
            ontology.getOWLOntologyManager().removeAxiom(ontology, axiom);
            ontology.getOWLOntologyManager().addAxiom(ontology, normalizedAxiom);
        }
        return ontology;
        
    }

    private static OWLSubClassOfAxiom normalizeSubClassOfAxiom(OWLSubClassOfAxiom axiom){
        OWLClassExpression subclass = axiom.getSubClass();
        OWLClassExpression superclass = axiom.getSuperClass();

        OWLClass atomicSup = getOrCreateAtomicConcept(superclass);
        OWLSubClassOfAxiom normalizedAxiom = dataFactory.getOWLSubClassOfAxiom(subclass, atomicSup);
        return normalizedAxiom;
    }

    private static OWLClass getOrCreateAtomicConcept(OWLClassExpression expr){
        if (!expr.isAnonymous()){
            //expr is atomic concept, return as it
            return expr.asOWLClass();
        }

        if (complexToAtomicMap.containsKey(expr)){
            return complexToAtomicMap.get(expr);
        }
        
        //Introduce new aux atomic concept for complex expression
        String iriHint = getExpressionShort(expr);
        String atomicIRI = ontology.getOntologyID().getOntologyIRI().get().toString() + iriHint;
        OWLClass atomicConcept = dataFactory.getOWLClass(IRI.create(atomicIRI));
        complexToAtomicMap.put(expr, atomicConcept);

        OWLClassExpression normalizedCE = normalizeComplexExpression(expr);
        OWLEquivalentClassesAxiom eqvAxiom = dataFactory.getOWLEquivalentClassesAxiom(atomicConcept, normalizedCE);
        ontology.getOWLOntologyManager().addAxiom(ontology, eqvAxiom);

        return atomicConcept;
    }

    private static String getExpressionShort(OWLClassExpression expr){
        if (!expr.isAnonymous()) {
            return expr.asOWLClass().getIRI().getShortForm();
        }

        if (expr instanceof OWLObjectSomeValuesFrom) {
            OWLObjectSomeValuesFrom some = (OWLObjectSomeValuesFrom) expr;
            return "Exists" + some.getProperty().asOWLObjectProperty().getIRI().getShortForm() + "_" + getExpressionShort(some.getFiller());
        }

        if (expr instanceof OWLObjectIntersectionOf) {
            OWLObjectIntersectionOf intersection = (OWLObjectIntersectionOf) expr;
            Set<OWLClassExpression> operands = intersection.getOperands();
            StringBuilder sb = new StringBuilder();
            for (OWLClassExpression op : operands) {
                sb.append(getExpressionShort(op)).append("_&_");
            }

            return sb.substring(0, sb.length() - 5); 
        }

        // fallback
        return "Complex";
    }
    private static OWLClassExpression normalizeComplexExpression(OWLClassExpression expr){
        if (expr instanceof OWLObjectSomeValuesFrom){
            OWLObjectSomeValuesFrom some = (OWLObjectSomeValuesFrom) expr;
            OWLClassExpression filler = some.getFiller();
            OWLClass atomicFiller = getOrCreateAtomicConcept(filler);
            OWLObjectSomeValuesFrom normalizedSome = dataFactory.getOWLObjectSomeValuesFrom(some.getProperty(), atomicFiller);
            return normalizedSome;
        }

        if (expr instanceof OWLObjectIntersectionOf){
            OWLObjectIntersectionOf intersection = (OWLObjectIntersectionOf) expr;
            Set<OWLClassExpression> operands = intersection.getOperands();
            Set<OWLClassExpression> normalizedOperands = operands.stream()
                .map(op -> getOrCreateAtomicConcept(op))
                .collect(java.util.stream.Collectors.toSet());
            OWLObjectIntersectionOf normalizedIntersection = dataFactory.getOWLObjectIntersectionOf(normalizedOperands);
            return normalizedIntersection;
        }

        return expr;
    }


}
