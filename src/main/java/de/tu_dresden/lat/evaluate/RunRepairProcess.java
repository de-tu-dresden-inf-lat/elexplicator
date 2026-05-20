package de.tu_dresden.lat.evaluate;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.Thread.State;
import java.nio.channels.InterruptedByTimeoutException;
import java.util.concurrent.TimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class RunRepairProcess implements Callable<Map<String, String>> {
    
    private String[] commands;
    private String option;
    private Optional<Double> yesProbOpt;
    private String outputPathString;
    private String aboxOntologyString;
    private final AtomicReference<Process> process;
    public RunRepairProcess(String[] commands, String option, Optional<Double> yesProbOpt, String outputPathString, String aboxOntologyString){
        this.commands = commands;
        this.option = option;
        this.yesProbOpt = yesProbOpt;
        this.outputPathString = outputPathString;
        this.aboxOntologyString = aboxOntologyString;
        this.process = new AtomicReference<>();
    }

    enum State {
                NORMAL,
                WAITING_FOR_OPTIONS,
                WAITING_FOR_RESULTS,
                WAITING_FOR_ANSWER
            }
    
    public Process getProcess(){
        return this.process.get();
    }

    @Override
    public Map<String, String> call() throws RuntimeException {
        ProcessBuilder pb = new ProcessBuilder(commands);
        pb.redirectErrorStream(false);
        Map<String, String> answersMap = new HashMap<>();
        try {
            process.set(pb.start());
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.get().getInputStream()));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.get().getOutputStream()));
            BufferedReader err = new BufferedReader(new InputStreamReader(process.get().getErrorStream()));
            StringBuilder recentOutput = new StringBuilder();
            String line;

            State currentState = State.NORMAL;
            
            String question = "";
            String prevLine = "";
            while ((line = reader.readLine()) != null) {
                if(Thread.currentThread().isInterrupted()){
                    System.out.println("Repair process interrupted. Killing process tree.");
                    Thread.currentThread().interrupt();
                    EvaluateOptions.killProcessTree(process.get());
                    return null;
                }
                // System.out.println("JAR output: " + line);
                recentOutput.append(line).append("\n");
                if (recentOutput.toString().contains("\u0007")) {
                    String inputText = null;
                    String outputText = recentOutput.toString();
                    switch (currentState){
                        
                        case WAITING_FOR_OPTIONS:
                            if (option.equals("mix")){
                                inputText = "1,2,3"; //mix option
                            } else if (option.equals("user")){
                                inputText = ""; //user option
                            } else {
                                inputText = option; //option 1,2,3
                            }
                            currentState = State.WAITING_FOR_RESULTS;
                            break;
                        case WAITING_FOR_RESULTS:
                            inputText = "";
                            currentState = State.WAITING_FOR_ANSWER;
                            break;
                        case WAITING_FOR_ANSWER:
                            if (option.equals("1")){
                                inputText = option1Decision(); // call the function to read from the impact file and decide on the result.
                            } else if (option.equals("2")){
                                inputText = option2Decision(); // call the function to read from the impact file and decide on the result.
                            } else if (option.equals("3")){
                                inputText = option3Decision(); // call the function to read from the impact file and decide on the result.
                            } else if (option.equals("mix")){
                                inputText = optionMixDecision(outputPathString); // call the function to read from the impact file and decide on the result.
                            } else if (option.equals("user")){
                                double yesProb = Optional.of(yesProbOpt).get().get();
                                inputText = optionUserDecision(yesProb); // call the function to read from the impact file and decide on the result.
                            }
                            answersMap.put(question, inputText);
                            System.out.println("Question: "+question + "\nAnswer: " + inputText);
                            currentState = State.NORMAL;
                            break;
                        case NORMAL:
                        default:
                            if(outputText.contains("All justifications have been computed.")){
                                if (answersMap.size() >= 1){
                                    inputText = "save";
                                } else {
                                    inputText = "exit";
                                    answersMap.put("Status", "No selection!");
                                }
                            }

                            else if (outputText.contains("Enter \"save\" to save the repair or \"continue\" to continue answering the remaining justification axioms.")){
                                inputText = "save";                                
                            }

                            else if (outputText.contains("Enter the filename to save as:")){
                                inputText = "repairOntology";
                                answersMap.put("Status", "Repair reached!");
                            }
                            
                            else if (outputText.contains("Enter \"max\" to compute maximal or \"continue\" to save.")){
                                inputText = "continue";                         
                            }

                            else if (outputText.contains("The resulting ontology is not a repair")){
                                inputText = "Cancel\nExit";
                                if (answersMap.size() >= 1){
                                    answersMap.put("Status", "Repair not possible!");
                                } else {
                                    answersMap.put("Status", "No selection!");
                                }                                
                            }
                            
                            else if (outputText.contains("The resulting ontology is not a repair")){
                                inputText = "Cancel\nExit";
                                if (answersMap.size() >= 1){
                                    answersMap.put("Status", "Repair not possible!");
                                } else {
                                    answersMap.put("Status", "No selection!");
                                }                                
                            }
                            else{
                                //read the axiom in outputText
                                // if option is not "not sure", currentState = Waiting for options:
                                question = prevLine.trim();
                                System.out.println("Q: " + question);
                                if (!option.equals("user")){
                                    currentState = State.WAITING_FOR_OPTIONS;
                                    inputText = "not sure";
                                } else {
                                    inputText = "";
                                    currentState = State.WAITING_FOR_ANSWER;
                                }
                            }
                            break;
                        
                    }
                    writer.write(inputText + "\n");
                    writer.flush();
                    recentOutput.setLength(0);
                }
                prevLine = line;
            }

            if (process.get().waitFor() != 0) {
                StringBuilder errorOutput = new StringBuilder();
                String errLine;
                while ((errLine = err.readLine()) != null) {
                    errorOutput.append(errLine).append("\n");
                }
                throw new RuntimeException("Repair process exited with non-zero code. Error output: " + errorOutput.toString());
            }

            System.out.println("Complete Answers given: " + answersMap); 
            return answersMap;             
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error during repair process execution.");
        }      
            
    }

    public String option1Decision() throws IOException {
        double sumYes = 0.0;
        double sumNo = 0.0;
        // Read from json file. Sum the probabilities for "yes" and "no". return the option with higher probability.
        String probabilitiesJson = outputPathString + File.separator + "probabilities.json";
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> probabilitiesMap = objectMapper.readValue(new File(probabilitiesJson), new TypeReference<Map<String, Object>>(){});
        if (probabilitiesMap.get("yes") instanceof String){
            return "no";
        } else {
            Map<String, Double> probabilitiesYes = (Map<String, Double>) probabilitiesMap.get("yes");
            sumYes = probabilitiesYes.values().stream().mapToDouble(Double::doubleValue).sum();
        }

        if (probabilitiesMap.get("no") instanceof String){
            return "yes";
        } else {
            Map<String, Double> probabilitiesNo = (Map<String, Double>) probabilitiesMap.get("no");
            sumNo = probabilitiesNo.values().stream().mapToDouble(Double::doubleValue).sum();
        }
        if (sumYes >= sumNo)
            return "yes";
        else
            return "no";    
    }

    public String option2Decision() throws IOException {
        //Read from class hierarchy json file, the two owl class hierarchies and evaluate them with the computeCost function. Select the one with lowest and answer accordingly.
        // double costYes = 0.0;
        // double costNo = 0.0;
        String ontologyYes = outputPathString + File.separator + "ontoYes.owl";
        String ontologyNo = outputPathString + File.separator + "ontoNo.owl";
        String jsonFile = outputPathString + File.separator + "classHierarchyDifference.json";
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> classHierarchyMap = objectMapper.readValue(new File(jsonFile), new TypeReference<Map<String, Object>>(){});
        
        Boolean repairYes = (Boolean) classHierarchyMap.get("repairYes");
        Boolean repairNo = (Boolean) classHierarchyMap.get("repairNo");
        
        if (repairYes && repairNo){
            System.out.println("Repair possible from both");
            AtomicReference<Double> costYes = new AtomicReference<>(0.0);
            Thread tYes = new Thread(()-> costYes.set(CostComputing.CostComputing(ontologyYes, aboxOntologyString)));
            tYes.start();
            try{
                tYes.join();
            } catch(InterruptedException e){
                //swallow the exception
            }
            AtomicReference<Double> costNo = new AtomicReference<>(0.0);
            Thread tNo = new Thread(()-> costNo.set(CostComputing.CostComputing(ontologyNo, aboxOntologyString)));
            tNo.start();
            try{
                tNo.join();
            } catch(InterruptedException e){
                //swallow the exception
            }
            if (costYes.get() <= costNo.get()){
                return "yes"; 
            } else {
                return "no";
            } 
        } else if (repairYes && !repairNo){
            System.out.println("Repair possible from yes");
            return "yes";
        } else if (!repairYes && repairNo){
            System.out.println("Repair possible from no");
            return "no";
        } else{
            System.out.println("Repair possible from none");
            return "yes";
        }     
    }

    public String option3Decision() throws IOException {
        String answer = "yes";
        Double hammingYes = 0.0;
        Double hammingNo = 0.0;
        String probabilitiesJson = outputPathString + File.separator + "hammingDistance.json";
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode = objectMapper.readTree(new File(probabilitiesJson));
        if (rootNode.get("hamming_yes").isTextual()){ //i.e. no repair when option "yes" is chosen
            answer = "no";
            return answer;
        } else {
            hammingYes = rootNode.get("hamming_yes").asDouble();
        }

        if (rootNode.get("hamming_no").isTextual()){ //i.e. no repair when option "no" is chosen
            answer = "yes";
            return answer;
        } else {
            hammingNo = rootNode.get("hamming_no").asDouble();
        }

        if (hammingYes <= hammingNo){
            answer = "yes";
        } else {
            answer = "no";
        }
        
        return answer;
    }

    public String optionUserDecision(double yesProb) {
        String decision = Math.random() < yesProb ? "yes" : "no";
        return decision;
        
    }

    public String optionMixDecision(String outputPathString) throws IOException {
        List<String> answers = new ArrayList<>();
        answers.add(option1Decision());
        answers.add(option2Decision());
        answers.add(option3Decision());

        long countYes = answers.stream().filter(ans -> ans.equals("yes")).count();
        long countNo = answers.stream().filter(ans -> ans.equals("no")).count();

        if (countYes >= countNo){
            return "yes";
        } else {
            return "no";
        }
    }

    
}
