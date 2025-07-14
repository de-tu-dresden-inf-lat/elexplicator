package de.tu_dresden.lat.api;

import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import org.semanticweb.owlapi.model.OWLAxiom;

import de.tu_dresden.inf.lat.prettyPrinting.formatting.SimpleOWLFormatterCl;
import de.tu_dresden.inf.lat.prettyPrinting.formatting.SimpleDLFormatter$;

public class AxiomNode {
    private static SimpleOWLFormatterCl sOWLFormatter = new SimpleOWLFormatterCl(true, SimpleDLFormatter$.MODULE$,
		true);
    static long counter = 0;
    long nodeId;
    int depth;
    LinkedList<Map<String, Object>> path;
    String repairInfo;
    AxiomNode yeschild;
    AxiomNode nochild;
    String axiomStr;
    OWLAxiom axiom;
    OWLAxiom nextAxiom;
    Set<Map<String, Object>> nextNodes;
    
    public AxiomNode(int depth, LinkedList<Map<String, Object>> path, String repairInfo, String axiomStr, OWLAxiom axiom, OWLAxiom nextAxiom, Set<Map<String, Object>> nextNodes) {
        this.nodeId = counter++; 
        this.depth = depth;
        this.path = new LinkedList<>(path);
        this.repairInfo = repairInfo;
        this.axiomStr = axiomStr;
        this.axiom = axiom;
        this.nextAxiom = nextAxiom;
        this.nextNodes = nextNodes;
    }

    public boolean isLeaf(int totalAxioms){
        return depth == totalAxioms;
    }

    @Override
    public String toString(){
        // StringBuilder sb = new StringBuilder("Node ID: " + nodeId + " | Axiom: " + axiomStr + " | Path: ");
        // for (int i=0; i < path.size(); i++) {
        //     sb.append("A").append(i+1).append("=").append(path.get(i) ? "Y" : "N").append(", ");
        // }
        // sb.append("Next: ").append(sOWLFormatter.format(nextAxiom).toString());

        String sb = "Node ID: " + nodeId + " | Axiom: " + axiomStr + " | Path: " + path.toString() + " | Next: " + nextAxiom;
        return sb.toString();
    }
}
