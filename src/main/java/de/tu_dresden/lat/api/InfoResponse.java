package de.tu_dresden.lat.api;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import org.semanticweb.owlapi.model.OWLAxiom;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

public class InfoResponse {
    //private String nodeAxiom; //change this to JSONNode
    private Long nodeId;
    private OWLAxiom axiom;
    private String axiomString;
    private LinkedList<Map<String, Object>> path;
    private Set<Map<String, Object>> nextNodes;

    public InfoResponse(AxiomNode node){
        this.nodeId = node.nodeId;
        this.axiom = node.axiom;
        this.axiomString = node.axiomStr;
        this.path = node.path;
        this.nextNodes = node.nextNodes;
    }

    public long getNodeId(){
        return this.nodeId;
    }

    public String getAxiom(){
        if (this.axiom != null){
            return this.axiom.toString();
        } else {
            return "No axiom";
        }
        
    }

    public String getAxiomString(){
        return this.axiomString;
    }

    public String getPath(){
        return this.path.toString();
    }

    public String getNextNodes(){
        if (this.nextNodes != null){
            return this.nextNodes.toString();
        } else {
            return "Empty";
        }
        
    }

    // @Override
    // public String toString(){
    //     return "Response{" + this.nodeId + "," + this.axiom + "," + this.axiomString + "," + this.path + "," + this.nextNodes + "}\n";  
    // }
}
