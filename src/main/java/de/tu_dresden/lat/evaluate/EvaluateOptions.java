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
import java.util.concurrent.atomic.AtomicReference;
import java.time.LocalDateTime;

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
    Integer timeoutSeconds = 3600;

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
        String[] commands = {"java", "-Xmx12g", "-Xms2g", "-jar", jar_path, 
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
            
            System.out.println(LocalDateTime.now() + " Running repair process for option: " + option);
            ExecutorService service = Executors.newSingleThreadExecutor();
            try{
                if (option.equals("option1")){
                    RunRepairProcess repairProcess = new RunRepairProcess(commands, "1", Optional.empty(), outputPathString, aboxOntologyString);
                    repEval = runRepairWithTimeout(repairProcess, repEval, timeoutSeconds);
                } else if (option.equals("option2")){
                    RunRepairProcess repairProcess = new RunRepairProcess(commands, "2", Optional.empty(), outputPathString, aboxOntologyString);
                    repEval = runRepairWithTimeout(repairProcess, repEval, timeoutSeconds);
                } else if (option.equals("option3")){
                    RunRepairProcess repairProcess = new RunRepairProcess(commands, "3", Optional.empty(), outputPathString, aboxOntologyString);
                    repEval = runRepairWithTimeout(repairProcess, repEval, timeoutSeconds);
                } else if (option.equals("mix")){
                    RunRepairProcess repairProcess = new RunRepairProcess(commands, "mix", Optional.empty(), outputPathString, aboxOntologyString);
                    repEval = runRepairWithTimeout(repairProcess, repEval, timeoutSeconds);
                } else if (option.equals("user")){
                    repEval = runUserOption(commands, repEval);
                }
            } catch (RuntimeException e){
                this.errorOccurred = true;
                throw new EvaluationException("Error during repair process execution for option: " + option, e, evalList);
            }

            //if not timeout and answersMap not null compute cost
            if(repEval.getAnswersMap().get("Status").equals("Repair reached!")){
                System.out.println(LocalDateTime.now() + " Computing cost!");
                
                ExecutorService executor = Executors.newSingleThreadExecutor();
                Future<Double> future = executor.submit(() -> evaluateRepair());

                try {
                    long startTime = System.currentTimeMillis();
                    cost = future.get(timeoutSeconds, TimeUnit.SECONDS);
                    long endTime = System.currentTimeMillis();
                    repEval.setEvaluationTime(endTime-startTime);
                } catch (TimeoutException e) {
                    future.cancel(true); 
                    repEval.setEvaluationTime(-1);
                    System.out.println("Timeout during cost evaluation.");
                } catch (Exception e) {
                    this.errorOccurred = true;
                    e.printStackTrace();
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
            System.out.println(LocalDateTime.now() + " Running repair process for option: " + option + ", Total repair cost: " + cost);
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
            System.out.println("Timeout on option: " + this.currentOption);
            repEval.setAnswersMap(Map.of("Status", "Timeout"));
            repEval.setRepairTime(-1);
            killProcessTree(process.getProcess());
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

    public static void killProcessTree(Process process){
        if(process == null){
            return;
        }
        ProcessHandle processHandle = process.toHandle();
        processHandle.descendants().forEach(child -> {
            try{
                child.destroy();
            } catch (Exception e){
                e.printStackTrace();
            }
        });
        process.destroy();

        try{
            if(process.waitFor(500, TimeUnit.MILLISECONDS)){
                return;
            } 
        }catch (InterruptedException e){
            e.printStackTrace();
        }

        processHandle.descendants().forEach(child -> child.destroyForcibly());
        process.destroyForcibly();
    }

    private RepairEvaluation runUserOption(String[] commands, RepairEvaluation repEval) throws RuntimeErrorException {
        double yesProb = 0.75;
        int attempts = 0;
        while (yesProb >= 0.0){
            RunRepairProcess repairProcess = new RunRepairProcess(commands, "user", Optional.of(yesProb), outputPathString, aboxOntologyString);
            
            RepairEvaluation repEvalResult = runRepairWithTimeout(repairProcess, repEval, timeoutSeconds);
            if (!repEvalResult.getAnswersMap().get("Status").equals("Repair not possible!")){
                if (!repEvalResult.getAnswersMap().get("Status").equals("No selection!")){
                    attempts++;
                    repEvalResult.setAttempts(attempts);
                }
                return repEvalResult;
            } 
            attempts++;
            yesProb = yesProb - 0.15;
            System.out.println(LocalDateTime.now() + " Couldn't reach a repair. Lowering probability for 'yes'");
        }
        repEval.setAnswersMap(Map.of("Status", "Repair not possible!"));
        repEval.setAttempts(attempts);
        return repEval;
    }

    private double evaluateRepair() {
        File outputDir = new File(outputPathString);
        AtomicReference<Double> repairCost = new AtomicReference<>(0.0);
        for (File outFile : outputDir.listFiles()) {
            if (outFile.isFile() && outFile.getName().startsWith("repairOntology") && outFile.getName().endsWith(".owl")) {
                String repairOntologyPath = outFile.getAbsolutePath();
                Thread t = new Thread(() -> repairCost.set(CostComputing.CostComputing(repairOntologyPath, aboxOntologyString)));
                t.start();
                try{
                    t.join();
                } catch (InterruptedException e){
                    //already handled by outer thread.
                }
                outFile.delete();
                break;
            }
        }
        return repairCost.get();
    }
}
