package de.tu_dresden.lat.api;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

public class ImpactResponse {
    private long nodeId;
    private String nodeAxiom;
    private JsonNode impact;

    public ImpactResponse(long nodeId, String nodeAxiom, JsonNode impact){
        this.nodeId = nodeId;
        this.nodeAxiom = nodeAxiom;
        this.impact = impact;
    }

    public long getNodeId(){
        return this.nodeId;
    }

    public String getNodeAxiom(){
        return this.nodeAxiom;
    }

    public JsonNode getDecisionImpact(){
        return this.impact;
    }

    // @Override
    // public String toString(){
    //     return "Response{" + this.nodeId + "," + this.nodeAxiom + "," + this.impact + "}\n";
    // }
}
