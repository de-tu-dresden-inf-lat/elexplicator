package de.tu_dresden.lat.api;
import java.util.List;
import java.util.Map;
import java.util.Set;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SaveResponse {
    private long nodeId;
    private RepairStatus repairStatus;
    // private List<Set<String>> possibleMaximalRepairs;
    private Map<Long, Set<String>> possibleMaximalRepairs; //nodeID : diagnosis related to maximal repair

    @JsonCreator 
    public SaveResponse(@JsonProperty("nodeId") long nodeId, 
                        @JsonProperty("repairStatus") RepairStatus repairStatus,
                        @JsonProperty("possibleMaximalRepairs") Map<Long, Set<String>> possibleMaximalRepairs) {
        this.nodeId = nodeId;
        this.repairStatus = repairStatus;
        this.possibleMaximalRepairs = possibleMaximalRepairs;
    }

    public SaveResponse(long nodeId, RepairStatus repairStatus) {
        this.nodeId = nodeId;
        this.repairStatus = repairStatus;
    }

    public long getNodeId(){
        return this.nodeId;
    }

    public RepairStatus getRepairStatus(){
        return this.repairStatus;
    }


    public void setPossibleMaximalRepairs(Map<Long, Set<String>> possibleMaximalRepairs){
        this.possibleMaximalRepairs = possibleMaximalRepairs;
    }

    public Map<Long, Set<String>> getPossibleMaximalRepairs(){
        return this.possibleMaximalRepairs;
    }

}

enum RepairStatus {
    MAXIMAL,
    NON_MAXIMAL,
    NOT_REPAIR
}
