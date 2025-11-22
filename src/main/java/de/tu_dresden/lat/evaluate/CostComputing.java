package de.tu_dresden.lat.evaluate;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Arrays;

import org.checkerframework.checker.units.qual.g;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLNamedIndividual;


public class CostComputing {
    	public static double CostComputing(String ontologyPathStr, String aboxPathStr) {
        // String ontologyPathStr = "src/test/resources/ontologies/SimpleTBox.owl";
        // String aboxPathStr = "src/test/resources/ontologies/SimpleABox.owl";
        OWLOntology aboxOntology = null;
        OWLOntology tboxOntology = null;
        Map<OWLClass, ArrayList<Object>> conceptInfo = new HashMap<>();    
        try {
            aboxOntology = OWLManager.createOWLOntologyManager()
                .loadOntologyFromOntologyDocument(new File(aboxPathStr));
            tboxOntology = OWLManager.createOWLOntologyManager()
                .loadOntologyFromOntologyDocument(new File(ontologyPathStr));
        } catch (OWLOntologyCreationException e) {
            e.printStackTrace();
            return -1;
        }
        OWLReasonerFactory reasonerFactory = new StructuralReasonerFactory();
        OWLReasoner aboxReasoner = reasonerFactory.createReasoner(aboxOntology);
        aboxReasoner.precomputeInferences(InferenceType.CLASS_ASSERTIONS, InferenceType.CLASS_HIERARCHY);
        OWLReasoner tboxReasoner = reasonerFactory.createReasoner(tboxOntology);
        tboxReasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY, InferenceType.CLASS_ASSERTIONS);
        
        Set<OWLClass>classNames = null;
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
        } 
        // System.out.println("Concept\tDepth\tBreadth\tInstances\tCost");
        // for (Map.Entry<OWLClass, ConceptMetrics> entry : costMap.entrySet())
        // {
        //     System.out.println(entry.getKey() + "\t" + entry.getValue().depth + "\t" + entry.getValue().breadth + "\t" + entry.getValue().individualCount + "\t" + entry.getValue().cost);
        // }

       //calculate cost for each concept
        // conceptInfo = computeConceptCost(conceptInfo, tboxReasoner);
        // System.out.println("Concept\tDepth\tBreadth\tInstances\tCost");
        // for (Map.Entry<OWLClass, ArrayList<Object>> entry : conceptInfo.entrySet()) {
        //     System.out.println(entry.getKey() + "\t" + entry.getValue().get(0) + "\t" + entry.getValue().get(1) + "\t" + entry.getValue().get(2) + "\t" + entry.getValue().get(3));
        // } 
       
       //calculate overall cost for tbox
        double overallCost = overallTBoxCost(costMap);
        System.out.println("TBox Cost: " + overallCost);
        return overallCost;
    }

    private static Map<OWLClass, ConceptMetrics> computeConceptCost(OWLClass concept, OWLReasoner reasoner, Map<OWLClass, ConceptMetrics> metricsMap) {
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
        Set<OWLClass> directParents = superClasses.getFlattened();
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

    private static Set<OWLClass> fetchTBox(String ontologyPathStr) throws OWLOntologyCreationException {
        //read tbox onto
        OWLOntology axiomsOntology = OWLManager.createOWLOntologyManager()
				.loadOntologyFromOntologyDocument(new File(ontologyPathStr));
        Set<OWLClass> classNames = axiomsOntology.getClassesInSignature();
        return classNames; //return the concepts
    }

    private static int getInstancesForConcept(OWLClass concept, OWLOntology aboxOntology, OWLReasoner reasoner) {
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

    private static Map<OWLClass, Integer> getConceptDepth(OWLReasoner reasoner, OWLClass concept) {
        Map<OWLClass, Integer> depthMap = new HashMap<>();
        OWLClass parentClass = null;
        if (reasoner.isSatisfiable(concept)) {
            // Get all direct superclasses (parents) of the concept
            NodeSet<OWLClass> superClasses = reasoner.getSuperClasses(concept, true);

            // Handle the case of owl:Thing, which is a superclass of all others
            Set<OWLClass> directParents = superClasses.getFlattened();
            directParents.remove(reasoner.getRootOntology().getOWLOntologyManager()
                .getOWLDataFactory().getOWLThing());

            if (directParents.isEmpty()) {
                // This is a root class
                parentClass = reasoner.getRootOntology().getOWLOntologyManager()
                    .getOWLDataFactory().getOWLThing();
                depthMap.put(parentClass, 1);
                return depthMap;
            }

            int maxDepth = 1;
            // Recursively find the depth of all direct parents
            for (OWLClass parent : directParents) {
                // System.out.println("Concept: " + concept);
                // System.out.println("Parent: " + parent);
                int depth = getConceptDepth(reasoner, parent).entrySet().iterator().next().getValue();
                maxDepth = Math.max(maxDepth, depth);
                if (maxDepth == depth) {
                    parentClass = parent;
                }
                
            }
            depthMap.put(parentClass, maxDepth + 1);
            // System.out.println("Concept: " + concept + ", Parent: " + parentClass + ", Depth: " + (maxDepth + 1));
            return depthMap;
        } else {
            // Unsatifiable class
            return depthMap;
        }
    };

    private static int getConceptBreadth(OWLReasoner reasoner, OWLClass concept) {
        //get the direct superclasses of the concept
        NodeSet<OWLClass> superClasses = reasoner.getSuperClasses(concept, true);
        Set<OWLClass> directParents = superClasses.getFlattened();
        //for each direct superclass, get its number of direct subclasses. The maximum of these is the breadth
        int maxBreadth = 0;
        for (OWLClass parent : directParents) {
            NodeSet<OWLClass> siblingClasses = reasoner.getSubClasses(parent, true);
            maxBreadth = Math.max(maxBreadth, siblingClasses.getFlattened().size());
        }
        return maxBreadth;
    }

    /*private static Map<OWLClass, ArrayList<Object>> computeConceptCost(Map<OWLClass, ArrayList<Object>> conceptInfo, OWLReasoner reasoner) {
        double totalCost = 0.0;
        for (Map.Entry<OWLClass, ArrayList<Object>> entry : conceptInfo.entrySet()) {
            totalCost = conceptCost(conceptInfo, entry.getKey(), reasoner);
            conceptInfo.get(entry.getKey()).add(totalCost);
        }
        return conceptInfo;
    }*/

    /*private static double conceptCost(Map<OWLClass, ArrayList<Object>> conceptInfo, OWLClass concept, OWLReasoner reasoner) {
        double k = 0.5; // weight for depth
        double m = 0.3; // weight for breadth
        
        double parentCost = 0;
        ArrayList<?> metrics = conceptInfo.get(concept);
        int depth = (int) metrics.get(0);
        int breadth = (int) metrics.get(1);
        OWLClass parentClass = null;
        Set<OWLClass> parentClasses = reasoner.getSuperClasses(concept, true).getFlattened();
        parentClasses.remove(reasoner.getRootOntology().getOWLOntologyManager()
            .getOWLDataFactory().getOWLThing());
        if (parentClasses.isEmpty()){
            parentCost = 0;
        } else {
            for (OWLClass parent : parentClasses) {
                parentClass = parent;
                parentCost = Math.max(parentCost, conceptCost(conceptInfo, parentClass, reasoner));
            }
        }
        // Example cost function: weighted sum of depth, breadth, and instances
        double cost = parentCost + (k * depth) + (m * breadth);
        return cost;
    }*/

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

