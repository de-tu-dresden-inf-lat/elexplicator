package de.tu_dresden.lat.api;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import de.tu_dresden.inf.lat.prettyPrinting.formatting.SimpleOWLFormatterCl;
import de.tu_dresden.lat.data.names.ReasonerName;
import de.tu_dresden.lat.diagnoses.ComputeRepair;
import de.tu_dresden.inf.lat.exceptions.EntityCheckerException;
import de.tu_dresden.inf.lat.prettyPrinting.formatting.SimpleDLFormatter$;

public class RepairSession {
    private static SimpleOWLFormatterCl sOWLFormatter = new SimpleOWLFormatterCl(true, SimpleDLFormatter$.MODULE$,
		true);
    private List<AxiomNode> session;
    private List<OWLAxiom> axioms;
    public AxiomNode root;
    public String outDirStr; 
    public String ontologyPath; 
    public ReasonerName reasonerName;
    public OWLOntology ontology;
    public Set<? extends OWLAxiom> interestingAxiomsSet;
    public OWLAxiom defectAxiom;
    //reasonername

    public void startRepair(OWLAxiom defectAxiom, String outDir, String ontologyPath, ReasonerName reasonerName, OWLOntology ontology, Set<? extends OWLAxiom> interestingAxiomsSet){
        session = new LinkedList<>();
        this.outDirStr = outDir;
        this.ontologyPath = ontologyPath;
        this.reasonerName = reasonerName;
        this.ontology = ontology;
        this.interestingAxiomsSet = interestingAxiomsSet;
        this.defectAxiom = defectAxiom;
    }

    public AxiomNode buildTree(List<OWLAxiom> justificationAxioms){
        session = new LinkedList<>();
        AxiomNode.counter = 0;
        axioms = justificationAxioms;
        OWLAxiom rootAxiom = axioms.get(0);
        root = new AxiomNode(0, new LinkedList<>(), sOWLFormatter.format(rootAxiom), rootAxiom, null, null);
        session.add(root);

        Queue<AxiomNode> queue = new LinkedList<>();
        queue.add(root);

        while(!queue.isEmpty()) {
            AxiomNode currentNode = queue.poll();
            if (currentNode.depth == axioms.size()) {
                continue;
            }

            int nextDepth = currentNode.depth + 1;
            OWLAxiom nextAxiom = (nextDepth < axioms.size()) ? axioms.get(nextDepth) : null;
            String nextAxiomStr =  (nextAxiom != null) ? sOWLFormatter.format(nextAxiom).toString() : null;
            currentNode.nextAxiom = nextAxiom; // Store the next axiom in the current node
            OWLAxiom nextAxiomNext = (nextDepth+1 < axioms.size()) ? axioms.get(nextDepth+1) : null;
             // YES branch
            LinkedList<Map<String, Object>> yesPath = new LinkedList<Map<String, Object>>(currentNode.path); //if we are storing node IDs for path, yesPath.add(currentNode.nodeId)
            Map<String,Object> yes_pathInfo = new HashMap<>();
            yes_pathInfo.put("node", currentNode.nodeId);
            yes_pathInfo.put("axiom", currentNode.axiom);
            yes_pathInfo.put("axiomStr", currentNode.axiomStr);
            yes_pathInfo.put("answer", true);
            yesPath.add(yes_pathInfo);
            AxiomNode yesChild = new AxiomNode(nextDepth, yesPath, nextAxiomStr, nextAxiom, nextAxiomNext, null);
            currentNode.yeschild = yesChild;
            session.add(yesChild);
            queue.add(yesChild);

            // NO branch
            LinkedList<Map<String,Object>> noPath = new LinkedList<Map<String, Object>>(currentNode.path);
            Map<String,Object> no_pathInfo = new HashMap<>();
            no_pathInfo.put("node", currentNode.nodeId);
            no_pathInfo.put("axiom", currentNode.axiom);
            no_pathInfo.put("axiomStr", currentNode.axiomStr);
            no_pathInfo.put("answer", false);
            noPath.add(no_pathInfo);
            AxiomNode noChild = new AxiomNode(nextDepth, noPath, nextAxiomStr, nextAxiom, nextAxiomNext, null);
            currentNode.nochild = noChild;
            session.add(noChild);
            queue.add(noChild);

            Map<String, Object> yes_childInfo = new HashMap<>();
            yes_childInfo.put("node", yesChild.nodeId);
            yes_childInfo.put("axiom", yesChild.axiom);
            yes_childInfo.put("axiomStr", yesChild.axiomStr);
            yes_childInfo.put("answer", true);
            Map<String, Object> no_childInfo = new HashMap<>();
            no_childInfo.put("node", noChild.nodeId);
            no_childInfo.put("axiom", noChild.axiom);
            no_childInfo.put("axiomStr", noChild.axiomStr);
            no_childInfo.put("answer", false);
            Set<Map<String, Object>> childNodes = new HashSet<>(Arrays.asList(yes_childInfo, no_childInfo));

            currentNode.nextNodes = childNodes;
        }
        return root;
    }

    public List<Map<String, Object>> getDecisionTree() {
        List<Map<String, Object>> serializedNodes = new ArrayList<>();

        for (AxiomNode node : session) {
            Map<String, Object> serialized = new HashMap<>();
            serialized.put("nodeId", node.nodeId);
            serialized.put("axiomStr", node.axiomStr);
            serialized.put("yes", node.yeschild != null ? node.yeschild.nodeId : null);
            serialized.put("no", node.nochild != null ? node.nochild.nodeId : null);
            serializedNodes.add(serialized);
        }

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        try {
            mapper.writeValue(new File(outDirStr + File.separator + "decisionsTree.json"), serializedNodes);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return serializedNodes;
    }

    public AxiomNode getNodeById(long id) {
        for (AxiomNode node : session) {
            if (node.nodeId == id) {
                return node;
            }
        }
        return null; // Node not found
    }

    public AxiomNode getNodeByPath(Set<String> reqPath){
        for (AxiomNode node : session){
            // LinkedList<Map<String, Object>> path
            Set<String> removedInPath = node.path.stream()
                .filter(e -> ! (boolean) e.get("answer"))
                .map(e -> (String) e.get("axiomStr"))
                .collect(Collectors.toSet());
            if (removedInPath.equals(reqPath)){
                return node;
            }
        }
        return null;
    }

    public ImpactResponse getHierarchyImpact(long id) {
        JsonNode hierarchyDiffNode = null;
        File jsonFile = new File(outDirStr + File.separator + "classHierarchyDifference_" + id + ".json");
        AxiomNode node = getNodeById(id);
        if (node == null) {
            return null;
        }
        if (node.axiom == null) {
            return new ImpactResponse(id, "No selections possible. End of justification axioms.", null, null);
        }
        ObjectMapper objMapper = new ObjectMapper();
        if(jsonFile.exists()){
            try {
				// JsonNode jsonNode = objMapper.readTree(jsonFile);
                // hierarchyDiffNode = jsonNode.get("hierarchyDifference");
                hierarchyDiffNode = objMapper.readTree(jsonFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            List<OWLAxiom> keep_list = new LinkedList<>();
            List<OWLAxiom> remove_list = new LinkedList<>();
            for (int i = 0; i < node.path.size(); i++) {
                Map<String, Object> p = node.path.get(i);
                if((Boolean) p.get("answer")){
                    keep_list.add((OWLAxiom) p.get("axiom"));
                } else {
                    remove_list.add((OWLAxiom) p.get("axiom"));
                }
            }
            try {
                ComputeRepair.computeHierarchyDiff(defectAxiom, new HashSet<OWLAxiom>(keep_list), new HashSet<OWLAxiom>(remove_list), outDirStr, ontologyPath, node.axiom, reasonerName, Optional.of(Long.toString(id)));
                try {
                    hierarchyDiffNode = objMapper.readTree(jsonFile);
                    // hierarchyDiffNode = jsonNode.get("hierarchyDifference");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        ImpactResponse impactResponse = new ImpactResponse(id, node.axiom.toString(), node.axiomStr, hierarchyDiffNode);
        return impactResponse;

    }

    public ImpactResponse getProbabilityImpact(long id) {  
        JsonNode probabilityNode = null;
        File jsonFile = new File(outDirStr + File.separator + "probabilities_" + id + ".json");
        AxiomNode node = getNodeById(id);
        if (node == null) {
            return null;
        }
        if (node.axiom == null) {
            return new ImpactResponse(id, "No selections possible. End of justification axioms.", null, null);
        }

        ObjectMapper objMapper = new ObjectMapper();
        if (jsonFile.exists()){
            try {
                probabilityNode = objMapper.readTree(new File(outDirStr + File.separator + "probabilities_" + id + ".json"));
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            List<OWLAxiom> keep_list = new LinkedList<>();
            List<OWLAxiom> remove_list = new LinkedList<>();
            for (int i = 0; i < node.path.size(); i++) {
                Map<String, Object> p = node.path.get(i);
                if((Boolean) p.get("answer")){
                    keep_list.add((OWLAxiom) p.get("axiom"));
                } else {
                    remove_list.add((OWLAxiom) p.get("axiom"));
                }
            }
            try{
                ComputeRepair.computeProbabilities(node.axiom, new HashSet<OWLAxiom>(keep_list), new HashSet<OWLAxiom>(remove_list), outDirStr, ontologyPath, interestingAxiomsSet, reasonerName, Optional.of(Long.toString(id)));
                try {
                    probabilityNode = objMapper.readTree(new File(outDirStr + File.separator + "probabilities_" + id + ".json"));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                
            } catch (Exception e){
                e.printStackTrace();
            }
        }
        
        ImpactResponse impactResponse = new ImpactResponse(id, node.axiom.toString(), node.axiomStr, probabilityNode);
        return impactResponse;
        
    }

    public ImpactResponse getHammingImpact(long id) {  
        JsonNode hammingNode = null;
        File jsonFile = new File(outDirStr + File.separator + "hammingDistance_" + id + ".json");
        AxiomNode node = getNodeById(id);
        ObjectMapper objMapper = new ObjectMapper();
        if (node == null) {
            return null;
        }
        if (node.axiom == null) {
            return new ImpactResponse(id, "No selections possible. End of justification axioms.", null, null);
        }
        if (jsonFile.exists()){
            try {
			    hammingNode = objMapper.readTree(jsonFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else  {
            List<OWLAxiom> keep_list = new LinkedList<>();
            List<OWLAxiom> remove_list = new LinkedList<>();
            for (int i = 0; i < node.path.size(); i++) {
                Map<String, Object> p = node.path.get(i);
                if((Boolean) p.get("answer")){
                    keep_list.add((OWLAxiom) p.get("axiom"));
                } else {
                    remove_list.add((OWLAxiom) p.get("axiom"));
                }
            }
            try{
                ComputeRepair.hammingDistance(node.axiom, new HashSet<OWLAxiom>(remove_list), new HashSet<OWLAxiom>(keep_list), interestingAxiomsSet, ontologyPath, reasonerName, outDirStr, Optional.of(Long.toString(id)));
                
                try {
                    hammingNode = objMapper.readTree(jsonFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                
            } catch (Exception e){
                e.printStackTrace();
            }
        }
        ImpactResponse impactResponse = new ImpactResponse(id, node.axiom.toString(), node.axiomStr, hammingNode);
        return impactResponse;
    }

    public SaveResponse saveOntology(long id, String filename){
        System.out.println("Saving ontology for node ID: " + id + " with filename: " + filename);
        //if there already exists a json file with possible maximal repairs, then return that instead of computing again
        File jsonFile = new File(outDirStr + File.separator + "saveContext_" + id + ".json");
        System.out.println("Checking if save context exists: " + jsonFile.exists());
        ObjectMapper objMapper = new ObjectMapper();
        if (jsonFile.exists()){
            try {
                SaveResponse response = objMapper.readValue(jsonFile, SaveResponse.class);
                return response;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        AxiomNode node = getNodeById(id);
        if (node == null) {
            throw new NodeNotFoundException("Node not found!");
        }
        Set<OWLAxiom> remove_list = new HashSet<>();
        for (int i = 0; i < node.path.size(); i++) {
            Map<String, Object> p = node.path.get(i);
            if(!(Boolean) p.get("answer")){
                remove_list.add((OWLAxiom) p.get("axiom"));
            }
        }
        
        if (ComputeRepair.isRepair(remove_list)){
            if (ComputeRepair.checkMinimality(remove_list)){
                try {
                    OWLOntology modifiedOntology = ComputeRepair.computeRepair(remove_list, ontologyPath);
                    ComputeRepair.saveRepairOntology(modifiedOntology, outDirStr, filename);
                    SaveResponse response = new SaveResponse(id, RepairStatus.MAXIMAL);
                    objMapper.writerWithDefaultPrettyPrinter().writeValue(jsonFile, response);
                    return response;
                } catch (OWLOntologyCreationException | OWLOntologyStorageException | IOException e) {
                    throw new RuntimeException("Error saving ontology: " + e.getMessage(), e);
                }
            } else {
                SaveResponse response = new SaveResponse(id, RepairStatus.NON_MAXIMAL);
                try {
                    List<Set<? extends OWLAxiom>> recommendedDiagnoses = ComputeRepair.recommendDiagnosisSet(remove_list);
                    List<Set<String>> recommendedDiagnosisStrings = recommendedDiagnoses.stream()
                    .map(elem -> ComputeRepair.toStringSet(elem))
                    .collect(Collectors.toList());
                    Map<Long, Set<String>> recDiagMap = recommendedDiagnosisStrings.stream()
                        .collect(Collectors.toMap(
                            elem -> getNodeByPath(elem).nodeId,
                            elem -> elem));
                    //Map the nodeID of node having path corresponding to recommendedDiagnosisStrings
                    response.setPossibleMaximalRepairs(recDiagMap);
                    
                    objMapper.writerWithDefaultPrettyPrinter().writeValue(jsonFile, response);

                } catch (OWLOntologyCreationException | OWLOntologyStorageException | IOException
                        | EntityCheckerException | InterruptedException e) {
                    throw new RuntimeException("Error computing possible maximal repairs: " + e.getMessage(), e);
                }
                return response;
            }
        } else {
            SaveResponse response = new SaveResponse(id, RepairStatus.NOT_REPAIR);
            try {
                objMapper.writerWithDefaultPrettyPrinter().writeValue(jsonFile, response);
            } catch (IOException e) {
                throw new RuntimeException("Error saving save context: " + e.getMessage(), e);
            }
            return response;
        }

    }

    public void saveAnyway(long id, String filename){
        AxiomNode node = getNodeById(id);
        if (node == null) {
            throw new NodeNotFoundException("Node not found!");
        }
        Set<OWLAxiom> remove_list = new HashSet<>();
        for (int i = 0; i < node.path.size(); i++) {
            Map<String, Object> p = node.path.get(i);
            if(!(Boolean) p.get("answer")){
                remove_list.add((OWLAxiom) p.get("axiom"));
            }
        }
        try {
            OWLOntology modifiedOntology = ComputeRepair.computeRepair(remove_list, ontologyPath);
            ComputeRepair.saveRepairOntology(modifiedOntology, outDirStr, filename);
        } catch (OWLOntologyCreationException | OWLOntologyStorageException | IOException e) {
            throw new RuntimeException("Error saving ontology: " + e.getMessage(), e);
        }
    }
}
