package de.tu_dresden.lat.evaluate;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.Thread.State;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.management.RuntimeErrorException;

import org.semanticweb.owlapi.model.OWLOntology;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class EvaluateOptions {

    String ontologyPathString;
    String defectAxiomString;
    String interestingAxiomString;
    String aboxOntologyString;
    String outputPathString;
    List<String> options;
    Boolean errorOccurred = false;
    String currentOption = "";

    public EvaluateOptions(String ontoPath, String defectStr, String intAxiomsStr, String aboxOnto, String outPath, List<String> options){
        this.ontologyPathString = ontoPath;
        this.defectAxiomString =defectStr;
        this.interestingAxiomString = intAxiomsStr;
        this.aboxOntologyString = aboxOnto;
        this.outputPathString = outPath;
        this.options = options;
    }

    
            
    public List<RepairEvaluation> evaluateOpt() throws EvaluationException {
        // String ontologyPathString = args[0];
        // String defectAxiomString = args[1];
        // String interestingAxiomString = args[2];
        // String aboxOntologyString = args[3];
        // String outputPathString = args[4];

        List<RepairEvaluation> evalList = new ArrayList<>();

        String jar_path = "target/ELExplicator.jar";
        String[] commands = {"java", "-jar", jar_path, 
            "-a", defectAxiomString,
            "-o", ontologyPathString,
            "-r",  "ELK",
            "-ia", interestingAxiomString,
            "-od", outputPathString
        };       
        for (String option : options){
            this.currentOption = option;
            Map<String, String> answersMap = null;
            double cost = -1;
            RepairEvaluation repEval = new RepairEvaluation(option);
            System.out.println("Running repair process for option: " + option);
            ExecutorService service = Executors.newSingleThreadExecutor();
            try{
                if (option.equals("option1")){
                    RunRepairProcess repairProcess = new RunRepairProcess(commands, "1", Optional.empty(), outputPathString, aboxOntologyString);
                    repEval = runRepairWithTimeout(repairProcess, repEval, 15);
                } else if (option.equals("option2")){
                    RunRepairProcess repairProcess = new RunRepairProcess(commands, "2", Optional.empty(), outputPathString, aboxOntologyString);
                    repEval = runRepairWithTimeout(repairProcess, repEval, 15);
                } else if (option.equals("option3")){
                    RunRepairProcess repairProcess = new RunRepairProcess(commands, "3", Optional.empty(), outputPathString, aboxOntologyString);
                    repEval = runRepairWithTimeout(repairProcess, repEval, 15);
                } else if (option.equals("mix")){
                    RunRepairProcess repairProcess = new RunRepairProcess(commands, "mix", Optional.empty(), outputPathString, aboxOntologyString);
                    repEval = runRepairWithTimeout(repairProcess, repEval, 15);
                } else if (option.equals("user")){
                    repEval = runUserOption(commands, repEval);
                }
            } catch (RuntimeErrorException e){
                this.errorOccurred = true;
                throw new EvaluationException("Error during repair process execution for option: " + option, e, evalList);
            }

            //if not timeout and answersMap not null compute cost
            if(repEval.getAnswersMap().get("Status").equals("Repair reached!")){
                System.out.println("Computing cost!");
                
                ExecutorService executor = Executors.newSingleThreadExecutor();
                Future<Double> future = executor.submit(() -> evaluateRepair());

                try {
                    long startTime = System.currentTimeMillis();
                    cost = future.get(15, TimeUnit.SECONDS);
                    long endTime = System.currentTimeMillis();
                    repEval.setEvaluationTime(endTime-startTime);
                } catch (TimeoutException e) {
                    future.cancel(true); // interrupts the thread
                    repEval.setEvaluationTime(-1);
                    System.out.println("Timeout during cost evaluation.");
                } catch (Exception e) {
                    this.errorOccurred = true;
                    throw new EvaluationException("Error during repair evaluation for option: " + option, e, evalList);
                } finally {
                    executor.shutdown();
                    try{
                        executor.awaitTermination(30, TimeUnit.SECONDS);
                    }catch (InterruptedException e){
                        executor.shutdownNow();
                    }
                }
                
            } else {
                cost = -1;
            }  
            repEval.setCost(cost);
            System.out.println("Total repair cost for option " + option + ": " + cost);
            System.out.println(repEval.getRepairTime());
            evalList.add(repEval);
        }
        
        return evalList;
        
    }

    private RepairEvaluation runRepairWithTimeout(RunRepairProcess process, RepairEvaluation repEval, long timeout) throws RuntimeErrorException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Map<String, String>> future = executor.submit(process);
        try {
            long startTime = System.currentTimeMillis();
            Map<String, String> answersMap = future.get(timeout, TimeUnit.SECONDS);
            long endTime = System.currentTimeMillis();
            repEval.setRepairTime(endTime-startTime);
            repEval.setAnswersMap(answersMap);
            return repEval;
        } catch (TimeoutException e) {
            future.cancel(true); // interrupts the thread
            System.out.println("Timeout");
            repEval.setAnswersMap(Map.of("Status", "Timeout"));
            repEval.setRepairTime(-1);
            return repEval;
        } catch (Exception e) {
            this.errorOccurred = true;
            throw new RuntimeErrorException(new Error("Repair process execution failed."), e.getMessage());
        } finally {
            executor.shutdown();
            try{
                executor.awaitTermination(30, TimeUnit.SECONDS);
            }catch (InterruptedException e){
                executor.shutdownNow();
            }
        }
    }

    private RepairEvaluation runUserOption(String[] commands, RepairEvaluation repEval) throws RuntimeErrorException {
        double yesProb = 0.75;
        while (yesProb >= 0.0){
            RunRepairProcess repairProcess = new RunRepairProcess(commands, "user", Optional.of(yesProb), outputPathString, aboxOntologyString);
            
            RepairEvaluation repEvalResult = runRepairWithTimeout(repairProcess, repEval, 15);
            if (!repEvalResult.getAnswersMap().get("Status").equals("Repair not possible!")){
                return repEvalResult;
            } 

            yesProb = yesProb - 0.15;
            System.out.println("Couldn't reach a repair. Lowering probability for 'yes'");
        }
        repEval.setAnswersMap(Map.of("Status", "Repair not possible!"));
        return repEval;
    }

    private double evaluateRepair() {
        File outputDir = new File(outputPathString);
        double repairCost = 0.0;
        for (File outFile : outputDir.listFiles()) {
            if (outFile.isFile() && outFile.getName().startsWith("repairOntology") && outFile.getName().endsWith(".owl")) {
                String repairOntologyPath = outFile.getAbsolutePath();
                repairCost = CostComputing.CostComputing(repairOntologyPath, aboxOntologyString);
                outFile.delete();
            }
        }
        return repairCost;
    }
}
