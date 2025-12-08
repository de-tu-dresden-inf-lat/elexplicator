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

    public EvaluateOptions(String ontoPath, String defectStr, String intAxiomsStr, String aboxOnto, String outPath){
        this.ontologyPathString = ontoPath;
        this.defectAxiomString =defectStr;
        this.interestingAxiomString = intAxiomsStr;
        this.aboxOntologyString = aboxOnto;
        this.outputPathString = outPath;
    }

    enum State {
                NORMAL,
                WAITING_FOR_OPTIONS,
                WAITING_FOR_RESULTS,
                WAITING_FOR_ANSWER
            }
            
    public List<RepairEvaluation> evaluateOpt() {
        // String ontologyPathString = args[0];
        // String defectAxiomString = args[1];
        // String interestingAxiomString = args[2];
        // String aboxOntologyString = args[3];
        // String outputPathString = args[4];
        double yesProb = 0.75;

        List<RepairEvaluation> evalList = new ArrayList<>();

        String jar_path = "target/ELExplicator.jar";
        String[] commands = {"java", "-jar", jar_path, 
            "-a", defectAxiomString,
            "-o", ontologyPathString,
            "-r",  "ELK",
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
            double cost = -1;
            Boolean noRepair = false;
            RepairEvaluation repEval = new RepairEvaluation(option);
            System.out.println("Running repair process for option: " + option);
            if (option.equals("option1")){
                Map<String, String> answersMap = runRepairProcess(commands, "1", Optional.empty());
                repEval.setAnswersMap(answersMap);
            } else if (option.equals("option2")){
                Map<String, String> answersMap = runRepairProcess(commands, "2", Optional.empty());
                repEval.setAnswersMap(answersMap);
            } else if (option.equals("option3")){
                Map<String, String> answersMap = runRepairProcess(commands, "3", Optional.empty());
                repEval.setAnswersMap(answersMap);
            } else if (option.equals("mix")){
                Map<String, String> answersMap = runRepairProcess(commands, "mix", Optional.empty());
                repEval.setAnswersMap(answersMap);
            } else if (option.equals("user")){
                while (true){
                    Map<String, String> answersMap= runRepairProcess(commands, "user", Optional.of(yesProb));
                    if (answersMap != null){
                        repEval.setAnswersMap(answersMap);
                        break;
                    } else {
                        System.out.println("Couldn't reach a repair. Lowering probability for 'yes'");
                        yesProb = yesProb - 0.15;
                        System.out.println(yesProb);
                        if (yesProb < 0.0){
                            System.out.println("No repair possible with user option.");
                            noRepair = true;
                            break;
                        }
                    }
                }
            }
            if (!noRepair){
                if (!repEval.getAnswersMap().isEmpty()){
                    cost = evaluateRepair();
                } else {
                    cost = -1; 
                }               
            }              
            repEval.setCost(cost);
            System.out.println("Total repair cost for option " + option + ": " + cost);
            evalList.add(repEval);
        }
        
        return evalList;
        
    }

    private Map<String, String> runRepairProcess(String[] commands, String option, Optional<Double> yesProbOpt) {
        ProcessBuilder pb = new ProcessBuilder(commands);
        pb.redirectErrorStream(true);
        Process process = null;
        try {
            process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));

            StringBuilder recentOutput = new StringBuilder();
            String line;

            State currentState = State.NORMAL;
            Map<String, String> answersMap = new HashMap<>();
            String question = "";
            String prevLine = "";
            while ((line = reader.readLine()) != null) {
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
                                return null;
                            }
                            else{
                                //read the axiom in outputText
                                // if option is not "not sure", currentState = Waiting for options:
                                question = prevLine.trim();
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
            System.out.println("Answers given: " + answersMap); 
            return answersMap;             
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if (process!=null){
                process.destroy();
                try{
                    process.waitFor();
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }
        }
            
            
    }

    private String option1Decision() throws IOException {
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

    private String option2Decision() throws IOException {
        //Read from class hierarchy json file, the two owl class hierarchies and evaluate them with the computeCost function. Select the one with lowest and answer accordingly.
        double costYes = 0.0;
        double costNo = 0.0;
        String ontologyYes = outputPathString + File.separator + "ontoYes.owl";
        String ontologyNo = outputPathString + File.separator + "ontoNo.owl";
        costYes = CostComputing.CostComputing(ontologyYes, aboxOntologyString);
        costNo = CostComputing.CostComputing(ontologyNo, aboxOntologyString);

        if (costYes <= costNo){
            return "yes"; 
        } else {
            return "no";
        }        
    }

    private String option3Decision() throws IOException {
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

    private String optionUserDecision(double yesProb) {
        String decision = Math.random() < yesProb ? "yes" : "no";
        return decision;
        
    }
    private String optionMixDecision(String outputPathString) throws IOException {
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

    private double evaluateRepair() {
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
