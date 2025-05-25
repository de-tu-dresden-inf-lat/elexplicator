package de.tu_dresden.lat.diagnoses;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.semanticweb.HermiT.ReasonerFactory;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;

import de.tu_dresden.lat.data.names.ReasonerName;

public class ClassHierarchyDifference {
    // Compute the class hierarchy difference between two ontologies and output the result to json file.
    String outputDirStr;
    String ontologyPathStr;
    Set<OWLAxiom> keepAxioms;
    Set<OWLAxiom> removeAxioms;
    OWLAxiom selectedAxiom;
    ReasonerName reasonerName;

    Map<OWLClass, Object> hierarchyMap1; // hierarchy map for the first ontology without removing the selected axiom
    Map<OWLClass, Object> hierarchyMap2;   // hierarchy map for the second ontology with removing the selected axiom
    Map<String, Object> hierarchyDifference;

    public ClassHierarchyDifference(String outputDirStr, String ontologyPathStr, Set<OWLAxiom> keepAxioms,
            Set<OWLAxiom> removeAxioms, OWLAxiom selectedAxiom, ReasonerName reasonerName) {
        this.outputDirStr = outputDirStr;
        this.ontologyPathStr = ontologyPathStr;
        this.keepAxioms = keepAxioms;
        this.removeAxioms = removeAxioms;
        this.selectedAxiom = selectedAxiom;
        this.reasonerName = reasonerName;

        this.hierarchyMap1 = new HashMap<>();
        this.hierarchyMap2 = new HashMap<>();
        this.hierarchyDifference = new HashMap<>();
    }

    public void getClassHierarchy(String ontologyPath) throws OWLOntologyCreationException {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
		OWLOntology ontology = manager.loadOntologyFromOntologyDocument(new File(ontologyPath));
        // Map<String, Object> hierarchies = new HashMap<>();
        
        OWLClass clazz = manager.getOWLDataFactory().getOWLThing();     
        manager.removeAxioms(ontology, removeAxioms);  
        this.hierarchyMap1 = printClassHierarchy(clazz, ontology);

         
        manager.removeAxioms(ontology, Collections.singleton(selectedAxiom));
        this.hierarchyMap2 = printClassHierarchy(clazz, ontology);
        
        getHierarchyDifference(hierarchyMap1, hierarchyMap2, new ArrayList<>(), new ArrayList<>(), null);

        // System.out.println("Removed:");
        // System.out.println(hierarchyDifference.get("removed:"));
        // System.out.println("Added:");
        // System.out.println(hierarchyDifference.get("added:"));

        // hierarchies.put("initial:", hierarchyMap1);	
        // hierarchies.put("modified:", hierarchyMap2);
        // hierarchies.put("difference:", hierarchyDifference);

        // return hierarchies;
    }

    private void getHierarchyDifference(Map<OWLClass, Object> ch1, Map<OWLClass, Object> ch2, List<Map<OWLClass, Object>> removed, List<Map<OWLClass, Object>> added, OWLClass parentClass) {

        Set<OWLClass> classes1 = ch1.keySet();
        Set<OWLClass> classes2 = ch2.keySet();

        for (OWLClass clazz: classes2){
            if (!classes1.contains(clazz)){
                if (parentClass != null){
                    List<Map<OWLClass, Object>> addedChild = Collections.singletonList(Collections.singletonMap(clazz, ch2.get(clazz)));
                    added.add(Collections.singletonMap(parentClass, addedChild));
                } else {
                    added.add(Collections.singletonMap(clazz, ch2.get(clazz)));
                }
            }
            
        }

        for (OWLClass clazz: classes1){
            Object children1Obj = ch1.get(clazz);
            if (!ch2.containsKey(clazz)) {
                // Entire subtree removed
                removed.add(Collections.singletonMap(clazz, children1Obj));
            } else {
                Object children2Obj = ch2.get(clazz);      
                if (children1Obj == null && children2Obj == null) { //both null
                    continue;
                } else if (children1Obj == null && children2Obj instanceof List) { //only 2 not null
                    added.add(Collections.singletonMap(clazz, children2Obj));
                } else if (children1Obj instanceof List && children2Obj == null) { //only 1 not null
                    removed.add(Collections.singletonMap(clazz, children1Obj));
                } else if (children1Obj instanceof List && children2Obj instanceof List) { //both lists but not equal
                    List<Map<OWLClass, Object>> children1 = (List<Map<OWLClass, Object>>) children1Obj;
                    List<Map<OWLClass, Object>> children2 = (List<Map<OWLClass, Object>>) children2Obj;

                    Map<OWLClass, Object> map1 = tranformToMap(children1);
                    Map<OWLClass, Object> map2 = tranformToMap(children2);

                    getHierarchyDifference(map1, map2, removed, added, clazz);
                } 
                // else {
                //     // both non-null but not equal i.e one is a list or null and other is a reference 
                //     if (!children1Obj.equals(children2Obj)) {
                //         removed.add(Collections.singletonMap(clazz, children1Obj));
                //         added.add(Collections.singletonMap(clazz, children2Obj));
                //     }
                // }
            }

        }
        

        this.hierarchyDifference.put("removed:", removed);
        this.hierarchyDifference.put("added:", added);

    }

    private Map<OWLClass, Object> tranformToMap(List<Map<OWLClass, Object>> childList) {
        Map<OWLClass, Object> childrenMap = new HashMap<>();
        if (childList == null) {
            return Collections.emptyMap();
        }
        for (Map<OWLClass, Object> child : childList) {
            for (Map.Entry<OWLClass, Object> entry : child.entrySet()) {
                childrenMap.put(entry.getKey(), entry.getValue());
            }
        }
        return childrenMap;
    }

    private Map<OWLClass, Object> printClassHierarchy(OWLClass clazz, OWLOntology ontology) {
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
        Set<OWLClass> visited = new HashSet<>();
        Map<OWLClass, Object> hierarchyMap = printClassHierarchy(clazz, reasoner, visited, new HashSet<>());
        return hierarchyMap;
    }

    public Map<OWLClass, Object> printClassHierarchy(OWLClass clazz, OWLReasoner reasoner, Set<OWLClass> visited, Set<OWLClass> visitedLeaf) {
 
        if (visited.contains(clazz)){
            if (!visitedLeaf.contains(clazz)){ // already visited non-leaf nodes 
                return Collections.singletonMap(clazz, "ref");
            } else {
                return Collections.singletonMap(clazz, null);
            }
        }

        visited.add(clazz);
        reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY);
        Set<OWLClass> children = reasoner.getSubClasses(clazz, true).getFlattened();
        children.addAll(reasoner.getEquivalentClasses(clazz).getEntities());
        List<Map<OWLClass, Object>> childNodes = new ArrayList<>();        
        for (OWLClass child : children) {            
            if (!child.equals(clazz) && !child.isOWLNothing()) {
                childNodes.add(printClassHierarchy(child, reasoner, visited, visitedLeaf));
            }
        }
        if (childNodes.isEmpty()){ // if the class is not a leaf node then add track it as visited
            visitedLeaf.add(clazz);
        }
        
        
        return Collections.singletonMap(clazz,
                childNodes.isEmpty() ? null : childNodes);
    }

    // private Map<OWLClass, Set<Object>> printClassHierarchy(OWLReasoner reasoner, OWLClass clazz, int level, Map<OWLClass, Set<Object>> hierarchyMap) {
    //     if (reasoner.isSatisfiable(clazz)) {
    //         for (int i = 0; i < level * 4; i++) {
    //             System.out.print(" ");
    //         }
    //         System.out.println(clazz.getIRI());
    //         List<OWLClass> children = new ArrayList<>(reasoner.getSubClasses(clazz, true).getFlattened());
    //         for (OWLClass child : children){
    //             if (!child.equals(clazz)){
    //                 hierarchyMap.putIfAbsent(clazz, new HashSet<>());
    //                 hierarchyMap.get(clazz).add(printClassHierarchy(reasoner, child, level+1, hierarchyMap));
    //             }
    //         }
    //         return hierarchyMap;
    //     }
    //     else{
    //         return hierarchyMap;
    //     }
    // }
}
