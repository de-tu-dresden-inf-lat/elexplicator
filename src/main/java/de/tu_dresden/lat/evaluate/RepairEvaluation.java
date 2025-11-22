package de.tu_dresden.lat.evaluate;

import java.util.Map;

public class RepairEvaluation {
    String optionName;
    Map<String, String> answersMap;
    double cost;

    public RepairEvaluation(String optionName){
        this.optionName = optionName;
    }

    public void setAnswersMap(Map<String, String> answersMap){
        this.answersMap = answersMap;
    }

    public void setCost(Double cost){
        this.cost = cost;
    }
}

