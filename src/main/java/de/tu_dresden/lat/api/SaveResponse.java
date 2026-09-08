package de.tu_dresden.lat.api;
import java.util.List;
import java.util.Set;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SaveResponse {
    private long nodeId;
    private RepairStatus repairStatus;
    private List<Set<String>> possibleMaximalRepairs;

    @JsonCreator 
    public SaveResponse(@JsonProperty("nodeId") long nodeId, 
                        @JsonProperty("repairStatus") RepairStatus repairStatus,
                        @JsonProperty("possibleMaximalRepairs") List<Set<String>> possibleMaximalRepairs) {
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


    public void setPossibleMaximalRepairs(List<Set<String>> possibleMaximalRepairs){
        this.possibleMaximalRepairs = possibleMaximalRepairs;
    }

    public List<Set<String>> getPossibleMaximalRepairs(){
        return this.possibleMaximalRepairs;
    }

}

enum RepairStatus {
    MAXIMAL,
    NON_MAXIMAL,
    NOT_REPAIR
}
