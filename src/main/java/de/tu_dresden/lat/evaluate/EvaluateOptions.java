package de.tu_dresden.lat.evaluate;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.Thread.State;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.semanticweb.owlapi.model.OWLOntology;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class EvaluateOptions {

    enum State {
                NORMAL,
                WAITING_FOR_OPTIONS,
                WAITING_FOR_RESULTS,
                WAITING_FOR_ANSWER
            }
    public static void main(String[] args) {
        String ontologyPathString = args[0];
        String defectAxiomString = args[1];
        String interestingAxiomString = args[2];
        String aboxOntologyString = args[3];
        String outputPathString = args[4];
        double yesProb = 0.75;

        String jar_path = "target/ELExplicator.jar";
        String[] commands = {"java", "-jar", jar_path, 
            "-a", defectAxiomString,
            "-o", ontologyPathString,
            "-r",  "Hermit",
            "-ia", interestingAxiomString,
            "-od", outputPathString
        };

        List<String> options = new ArrayList<>();
        options.add("option1");
        options.add("option2");
        options.add("option3");
        options.add("user");
        options.add("mix");

        for (String option : options){
            System.out.println("Running repair process for option: " + option);
            String result = "";
            if (option.equals("option1")){
                runRepairProcess(outputPathString, commands, "1", Optional.empty());
            } else if (option.equals("option2")){
                runRepairProcess(outputPathString, commands, "2", Optional.empty());
            } else if (option.equals("option3")){
                runRepairProcess(outputPathString, commands, "3", Optional.empty());
            } else if (option.equals("mix")){
                runRepairProcess(outputPathString, commands, "mix", Optional.empty());
            } else if (option.equals("user")){
                while (true){
                    result = runRepairProcess(outputPathString, commands, "user", Optional.of(yesProb));
                    if (result.equals("complete")){
                        break;
                    } else {
                        yesProb = Math.max(0.0, yesProb - 0.15);
                        if (yesProb <= 0.0){
                            System.out.println("No repair possible with user option.");
                            break;
                        }
                    }
                }
            }
            double cost = evaluateRepair(outputPathString, aboxOntologyString);
            System.out.println("Total repair cost for option " + option + ": " + cost);
            //to do: write to csv file.
        }

        
    }

    private static String runRepairProcess(String outputPathString, String[] commands, String option, Optional<Double> yesProbOpt) {
            
        try {
            List<String> answers = new ArrayList<>();
            ProcessBuilder pb = new ProcessBuilder(commands);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));

            StringBuilder recentOutput = new StringBuilder();
            String line;

            State currentState = State.NORMAL;
            while ((line = reader.readLine()) != null) {
                // System.out.println("JAR output: " + line);
                recentOutput.append(line).append("\n");
                if (recentOutput.toString().contains("\u0007")) {
                    String inputText = null;
                    String outputText = recentOutput.toString();
                    switch (currentState){
                        
                        case WAITING_FOR_OPTIONS:
                            if (option.equals("mix")){
                                inputText = "1,2,3"; //user option
                            } else if (option.equals("user")){
                                inputText = ""; //mix option
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
                                inputText = option1Decision(outputPathString); // call the function to read from the impact file and decide on the result.
                            } else if (option.equals("2")){
                                inputText = option2Decision(outputPathString); // call the function to read from the impact file and decide on the result.
                            } else if (option.equals("3")){
                                inputText = option3Decision(outputPathString); // call the function to read from the impact file and decide on the result.
                            } else if (option.equals("mix")){
                                inputText = optionMixDecision(outputPathString); // call the function to read from the impact file and decide on the result.
                            } else if (option.equals("user")){
                                double yesProb = Optional.of(yesProbOpt).get().get();
                                inputText = optionUserDecision(yesProb); // call the function to read from the impact file and decide on the result.
                            }
                            answers.add(inputText);
                            currentState = State.NORMAL;
                            break;
                        case NORMAL:
                        default:
                            if(outputText.contains("All justifications have been computed.")){
                                inputText = "save";
                            }

                            else if (outputText.contains("Enter \"save\" to save the repair or \"continue\" to continue answering the remaining justification axioms.")){
                                inputText = "save";
                            }

                            else if (outputText.contains("Enter the filename to save as:")){
                                inputText = "repairOntology";
                            }

                            else if (outputText.contains("The resulting ontology is not a repair")){
                                inputText = "Cancel\nExit";
                                return "no repair";
                            }
                            else{
                                // if option is not "not sure", currentState = Waiting for options:
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
            }  
            System.out.println("Answers given: " + answers.toString()); 
            return "complete";             
        } catch (Exception e) {
            e.printStackTrace();
            return "error";
        }
            
            
    }

    private static String option1Decision(String outputPathString) throws IOException {
        double sumYes = 0.0;
        double sumNo = 0.0;
        // Read from json file. Sum the probabilities for "yes" and "no". return the option with higher probability.
        String probabilitiesJson = outputPathString + File.separator + "probabilities.json";
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> probabilitiesMap = objectMapper.readValue(new File(probabilitiesJson), new TypeReference<Map<String, Object>>(){});
        if (probabilitiesMap.get("yes") instanceof String){
            sumYes = 0.0;
        } else {
            Map<String, Double> probabilitiesYes = (Map<String, Double>) probabilitiesMap.get("yes");
            sumYes = probabilitiesYes.values().stream().mapToDouble(Double::doubleValue).sum();
        }

        if (probabilitiesMap.get("no") instanceof String){
            sumNo = 0.0;
        } else {
            Map<String, Double> probabilitiesNo = (Map<String, Double>) probabilitiesMap.get("no");
            sumNo = probabilitiesNo.values().stream().mapToDouble(Double::doubleValue).sum();
        }
        if (sumYes >= sumNo)
            return "yes";
        else
            return "no";    
    }

    private static String option2Decision(String outputPathString) throws IOException {
        //Read from class hierarchy json file, the two owl class hierarchies and evaluate them with the computeCost function. Select the one with lowest and answer accordingly.
        double costYes = 0.0;
        double costNo = 0.0;
        String classHierarchyJson = outputPathString + File.separator + "classHierarchyDifference.json";
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode = objectMapper.readTree(new File(classHierarchyJson));
        
        return "no";
    }

    private static String option3Decision(String outputPathString) throws IOException {
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

    private static String optionUserDecision(double yesProb) {
        String decision = Math.random() < yesProb ? "yes" : "no";
        return decision;
        
    }
    private static String optionMixDecision(String outputPathString) throws IOException {
        List<String> answers = new ArrayList<>();
        answers.add(option1Decision(outputPathString));
        answers.add(option2Decision(outputPathString));
        answers.add(option3Decision(outputPathString));

        long countYes = answers.stream().filter(ans -> ans.equals("yes")).count();
        long countNo = answers.stream().filter(ans -> ans.equals("no")).count();

        if (countYes >= countNo){
            return "yes";
        } else {
            return "no";
        }
    }

    private static double evaluateRepair(String outputPathString, String aboxOntologyString) {
        // TO DO: for ontologies with name starting with RepairOntology and file type .owl computeCost and store the result in map.
        //return the sum of costs.
        //for file in outputPathString
        File outputDir = new File(outputPathString);
        double repairCost = 0.0;
        for (File outFile : outputDir.listFiles()) {
            if (outFile.isFile() && outFile.getName().startsWith("repairOntology") && outFile.getName().endsWith(".owl")) {
                String repairOntologyPath = outFile.getAbsolutePath();
                double cost = CostComputing.CostComputing(repairOntologyPath, aboxOntologyString);
                if (cost >= 0) {
                    System.out.println("Computed cost for " + outFile.getName() + ": " + cost);
                    repairCost += cost;
                } else {
                    System.out.println("Failed to compute cost for " + outFile.getName());
                }
            }
        }
        return repairCost;
    }
}
