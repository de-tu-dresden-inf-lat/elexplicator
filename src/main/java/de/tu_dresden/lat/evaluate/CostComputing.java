package de.tu_dresden.lat.evaluate;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.semanticweb.elk.owlapi.ElkReasoner;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLNamedIndividual;


public class CostComputing {
    	public static double CostComputing(String ontologyPathStr, String aboxPathStr) {
        // String ontologyPathStr = "src/test/resources/ontologies/SimpleTBox.owl";
        // String aboxPathStr = "src/test/resources/ontologies/SimpleABox.owl";
        OWLOntology aboxOntology = null;
        OWLOntology tboxOntology = null; 
        try {
            aboxOntology = OWLManager.createOWLOntologyManager()
                .loadOntologyFromOntologyDocument(new File(aboxPathStr));
            tboxOntology = OWLManager.createOWLOntologyManager()
                .loadOntologyFromOntologyDocument(new File(ontologyPathStr));
        } catch (OWLOntologyCreationException e) {
            e.printStackTrace();
            return -1;
        }
        ElkReasonerFactory reasonerFactory = new ElkReasonerFactory();
        ElkReasoner aboxReasoner = reasonerFactory.createReasoner(aboxOntology);
        aboxReasoner.precomputeInferences(InferenceType.CLASS_ASSERTIONS, InferenceType.CLASS_HIERARCHY);
        ElkReasoner tboxReasoner = reasonerFactory.createReasoner(tboxOntology);
        tboxReasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY, InferenceType.CLASS_ASSERTIONS);
        
        List<OWLClass>classNames = null;
        Map<OWLClass, ConceptMetrics> costMap = new HashMap<>();
        //read tbox onto
        //get all concepts 
        try {
            classNames = fetchTBox(ontologyPathStr);
            for (OWLClass className : classNames) {
                if (!costMap.containsKey(className)){
                    costMap = computeConceptCost(className, tboxReasoner, costMap);
                }
                    
                //get number of instances from ABox classified under concept
                int individualCount = getInstancesForConcept(className, aboxOntology, aboxReasoner);
                costMap.get(className).setIndividualCount(individualCount);
            }
            
        } catch (OWLOntologyCreationException e) {
            e.printStackTrace();
            return -1;
        } finally{
            aboxReasoner.dispose();
            tboxReasoner.dispose();
            System.gc();
        }
        double overallCost = overallTBoxCost(costMap);
        System.out.println("TBox Cost: " + overallCost);
        return overallCost;
    }

    private static Map<OWLClass, ConceptMetrics> computeConceptCost(OWLClass concept, ElkReasoner reasoner, Map<OWLClass, ConceptMetrics> metricsMap) {
        if (metricsMap.containsKey(concept)) {
            return metricsMap;
        }

        if(!reasoner.isSatisfiable(concept)) {
            // Unsatisfiable class, assign zero metrics
            metricsMap.put(concept, new ConceptMetrics(0, 0, 0.0));
            return metricsMap;
        }
        
        //Get all direct superclasses (parents) of the concept
        NodeSet<OWLClass> superClasses = reasoner.getSuperClasses(concept, true);
        List<OWLClass> directParents = new ArrayList<>(superClasses.getFlattened());
        directParents.sort(Comparator.comparing(OWLClass::toStringID));
        //Handle the case of owl:Thing, which is a superclass of all others
        directParents.remove(reasoner.getRootOntology().getOWLOntologyManager()
            .getOWLDataFactory().getOWLThing());
        if (directParents.isEmpty()) {
            //This is a root class
            OWLClass parentClass = reasoner.getRootOntology().getOWLOntologyManager()
                .getOWLDataFactory().getOWLThing();
            NodeSet<OWLClass> children = reasoner.getSubClasses(parentClass, true);
            int breadth = children.getFlattened().size();
            double cost = 0.5 * 1 + 0.3 * breadth; // depth is 1 for root and cost of parent is 0.
            metricsMap.put(concept, new ConceptMetrics(1, breadth,  cost));
            // System.out.println("Concept: " + concept + ", Cost: " + metricsMap.get(concept).cost );
            return metricsMap;
        }
        
        double maxParentCost = 0.0;
        int maxDepth = 0;
        int maxBreadth = 0;
        double conceptCost = 0.0;
        for (OWLClass parent : directParents) {
            metricsMap = computeConceptCost(parent, reasoner, metricsMap);
            ConceptMetrics parentMetrics = metricsMap.get(parent);
            // System.out.println("Parent Metrics of " + parent + ": Depth: " + parentMetrics.depth + ", Breadth: " + parentMetrics.breadth + ", Cost: " + parentMetrics.cost);
            if(parentMetrics.cost > maxParentCost){
                maxParentCost = parentMetrics.cost;
                maxDepth = parentMetrics.depth + 1;
                maxBreadth = reasoner.getSubClasses(parent, true).getFlattened().size();
            }  
        }
        conceptCost = maxParentCost + (0.5 * maxDepth) + (0.3 * maxBreadth);
        // System.out.println("Concept: " + concept + ", Depth: " + maxDepth + ", Breadth: " + maxBreadth + ", Cost: " + conceptCost);

        metricsMap.put(concept, new ConceptMetrics(maxDepth, maxBreadth, conceptCost));
        return metricsMap;
    }

    private static List<OWLClass> fetchTBox(String ontologyPathStr) throws OWLOntologyCreationException {
        //read tbox onto
        OWLOntology axiomsOntology = OWLManager.createOWLOntologyManager()
				.loadOntologyFromOntologyDocument(new File(ontologyPathStr));
        List<OWLClass> classNames = new ArrayList<>(axiomsOntology.getClassesInSignature());
        classNames.sort(Comparator.comparing(OWLClass::toStringID));
        return classNames; //return the concepts
    }

    private static int getInstancesForConcept(OWLClass concept, OWLOntology aboxOntology, ElkReasoner reasoner) {
        //read abox onto
        //get number of instances from ABox classified under concept
        int instanceCount = 0;
        // System.out.println("Getting instances for concept: " + concept);
        for (OWLNamedIndividual individual : aboxOntology.getIndividualsInSignature()){
            boolean isInstance = false;
            isInstance = reasoner.getTypes(individual, false).containsEntity(concept);
            if (isInstance) {
                // System.out.println("Individual " + individual);
                instanceCount++;
            }
        };
        return instanceCount;
    }

    private static double overallTBoxCost(Map<OWLClass,ConceptMetrics> conceptInfoMap) {
        double totalCost = 0.0;
        for (Map.Entry<OWLClass, ConceptMetrics> entry : conceptInfoMap.entrySet()) {
            double cost = entry.getValue().cost;
            int individualCount = entry.getValue().individualCount;
            totalCost += cost * individualCount;
        }
        return totalCost;
    }
    


}

