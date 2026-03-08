package de.tu_dresden.lat.evaluate;

import java.util.HashMap;
import java.util.Map;
import java.io.Serializable;

public class RepairEvaluation implements Serializable{
    String optionName;
    Map<String, String> answersMap;
    double cost;
    long repairTime;
    long evaluationTime;

    public RepairEvaluation(String optionName){
        this.optionName = optionName;
        this.answersMap = new HashMap<>();
    }

    public void setOptionName(String optionName){
        this.optionName = optionName;
    }

    public String getOptionName(){
        return this.optionName;
    }

    public void setAnswersMap(Map<String, String> answersMap){
        if (answersMap != null){
            this.answersMap = answersMap;
        }
        
    }

    public Map<String, String> getAnswersMap(){
        return this.answersMap;
    }

    public void setCost(Double cost){
        this.cost = cost;
    }

    public Double getCost(){
        return this.cost;
    }

    public void setRepairTime(long repairTime){
        this.repairTime = repairTime;
    
    }

    public long getRepairTime(){
        return this.repairTime;
    }

    public void setEvaluationTime(long evaluationTime){
        this.evaluationTime = evaluationTime;
    }

    public long getEvaluationTime(){
        return this.evaluationTime;
    }
}

