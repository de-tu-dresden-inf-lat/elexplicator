package de.tu_dresden.lat.evaluate;

import java.util.List;

public class EvaluationException extends Exception {

    private final List<RepairEvaluation> evaluationResults;

    public EvaluationException(String message, Throwable cause, List<RepairEvaluation> evaluationResults) {
        super(message, cause);
        this.evaluationResults = evaluationResults;
    }

    public List<RepairEvaluation> getEvaluationResults() {
        return evaluationResults;
    }

}
