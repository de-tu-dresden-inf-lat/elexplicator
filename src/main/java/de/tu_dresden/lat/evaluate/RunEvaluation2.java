package de.tu_dresden.lat.evaluate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.semanticweb.elk.owlapi.ElkReasoner;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.OWLXMLDocumentFormat;
import org.semanticweb.owlapi.manchestersyntax.renderer.ManchesterOWLSyntaxOWLObjectRendererImpl;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDocumentFormat;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.model.parameters.OntologyCopy;
import org.semanticweb.owlapi.reasoner.InconsistentOntologyException;
import org.semanticweb.owlapi.util.ShortFormProvider;

import com.fasterxml.jackson.core.exc.StreamWriteException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.tu_dresden.inf.lat.exceptions.EntityCheckerException;
import de.tu_dresden.lat.tools.ABoxGenerator;
import de.tu_dresden.lat.tools.DefectSelector;
import de.tu_dresden.lat.tools.OntologyToDNF;

public class RunEvaluation2 {
    
    public static Map<String, Object> loadExampleInstances(File exampleFile, String outDirStr){

        OWLOntology ontology = null;
        OWLOntology normalized = null;
        String normalizedFilePath = exampleFile.getParent().toString() + File.separator + "normalized.owl";

        Map<String, Object> map = new HashMap<>();
        System.out.println("loading file: "+exampleFile.getName());
        try{
            OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
            ontology = manager.loadOntologyFromOntologyDocument(exampleFile);

            OWLOntologyManager manager2 = OWLManager.createOWLOntologyManager();
            normalized = manager2.copyOntology(ontology, OntologyCopy.DEEP);
        }
        catch(Exception e){
            e.printStackTrace();
            System.out.println("Error loading ontology "+exampleFile.getName());
            return null;
        }
        List<OWLAxiom> defectsSet = null;
        try{
            defectsSet = (List<OWLAxiom>) DefectSelector.selectNDefects(ontology, 10);
        } catch (Exception e){
            e.printStackTrace();
        }
        if (defectsSet.isEmpty()){
            System.out.println("Error selecting defects"+exampleFile.getName());
            return null;
        }
        try{
            OntologyToDNF ontToDNF = new OntologyToDNF(normalized);
            normalized = ontToDNF.normalizeOntology();
            OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
            OutputStream outputstream = Files.newOutputStream(new File(normalizedFilePath).toPath());
            OWLDocumentFormat ontologyFormat = new OWLXMLDocumentFormat();
            manager.saveOntology(normalized, ontologyFormat, outputstream);
        } catch (Exception e){
            System.out.println("Error normalizing ontology "+exampleFile.getName());
            return null;
        }

        map.put("example", exampleFile.getName());
        map.put("ontology", ontology);
        map.put("normalizedOntology", normalized);
        map.put("defectAxiomsSet", defectsSet);
        map.put("ontologyPathStr", exampleFile.getPath());  
        map.put("normalizedOntologyPathStr", normalizedFilePath);     
        
        return map;

    }

    
    private static OWLAxiom selectDefectAxiom(String ontologyPath) throws OWLOntologyCreationException{
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntology ontology = manager.loadOntologyFromOntologyDocument(new File(ontologyPath));
        ElkReasonerFactory reasonerFactory = new ElkReasonerFactory();
        ElkReasoner reasoner = reasonerFactory.createReasoner(ontology);
        List<OWLClass> signClasses = new ArrayList<>(ontology.getClassesInSignature());
        signClasses.removeAll(Collections.singleton(reasoner.getRootOntology().getOWLOntologyManager()
        .getOWLDataFactory().getOWLThing()));
    
        Collections.shuffle(signClasses);
        Set<OWLClass> classes = new HashSet<>(signClasses.subList(0, Math.min(20, signClasses.size())));
        OWLDataFactory factory = manager.getOWLDataFactory();

        List<OWLSubClassOfAxiom> axiomList = new ArrayList<>(); 
        for (OWLClass owlClass : classes){
            Set<OWLClass> inferredSubClasses = new HashSet<>();
            inferredSubClasses = reasoner.getSubClasses(owlClass, false).getFlattened();

            if(!inferredSubClasses.isEmpty()){
                for (OWLClass inferredSubClass : inferredSubClasses){
                    if(ontology.containsClassInSignature(inferredSubClass.getIRI())){
                        OWLSubClassOfAxiom axiom = factory.getOWLSubClassOfAxiom(inferredSubClass, owlClass);
                        if (!ontology.containsAxiom(axiom) && !inferredSubClass.isBottomEntity()){
                            axiomList.add(axiom);
                        }
                    }
                    
                }
            }
        }
        reasoner.dispose();
        reasoner = null;
        manager.removeOntology(ontology);
        System.gc();
        if(axiomList.isEmpty()){
            axiomList = new ArrayList<>(ontology.getAxioms(AxiomType.SUBCLASS_OF));
        }
        Random random = new Random();
        OWLAxiom selectedDefectAxiom = axiomList.get(random.nextInt(axiomList.size()));
        System.out.println("Defect axiom selected: "+selectedDefectAxiom.toString());
        return selectedDefectAxiom;
    }

    private static String generateInterestingAxiom(String ontologyPath, OWLAxiom defectAxiom, String outDirStr) throws OWLOntologyCreationException, IOException{
        System.out.println("Generating Interesting Axiom");
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntology ontology = manager.loadOntologyFromOntologyDocument(new File(ontologyPath));
        ElkReasonerFactory reasonerFactory = new ElkReasonerFactory();
        ElkReasoner reasoner = reasonerFactory.createReasoner(ontology);
        
        List<OWLClass> signClasses = new ArrayList<>(ontology.getClassesInSignature());
        Collections.shuffle(signClasses);
        Set<OWLClass> classes = new HashSet<>(signClasses.subList(0, Math.min(20, signClasses.size())));
        OWLDataFactory factory = manager.getOWLDataFactory();

        Set<OWLSubClassOfAxiom> axiomSet1 = new HashSet<>();
        Set<OWLSubClassOfAxiom> axiomSet2 = new HashSet<>();

        for (OWLClass owlClass : classes) {
            Set<OWLClass> inferredSubClasses = new HashSet<>();
            inferredSubClasses = reasoner.getSubClasses(owlClass, false).getFlattened();
            if (!inferredSubClasses.isEmpty()) {                
                for (OWLClass inferredSubClass: inferredSubClasses){
                    OWLSubClassOfAxiom subclassAxiom = factory.getOWLSubClassOfAxiom(inferredSubClass, owlClass);
                    if(!subclassAxiom.equals(defectAxiom)){
                        if (!ontology.containsAxiom(subclassAxiom) && !inferredSubClass.isBottomEntity()){                        
                            axiomSet1.add(subclassAxiom);
                        } else {
                            axiomSet2.add(subclassAxiom);
                        }
                    }
                    
                }
            }
        }

        reasoner.dispose();
        reasoner = null;
        manager.removeOntology(ontology);
        System.gc();

        String IApath = outDirStr+File.separator+"IA_ontology.owl";
        OutputStream outputstream = Files.newOutputStream(new File(IApath).toPath());

        OWLDocumentFormat ontologyFormat = new OWLXMLDocumentFormat();


        OWLOntology interestingAxiomOntology = manager.createOntology();

        List<OWLSubClassOfAxiom> axiomList;
        if (!axiomSet1.isEmpty()){
            axiomList = new ArrayList<>(axiomSet1);
        } else {
            axiomList = new ArrayList<>(axiomSet2);
        }
        if(axiomList.isEmpty()){
            axiomList = new ArrayList<>(ontology.getAxioms(AxiomType.SUBCLASS_OF));
        }
        Random random = new Random();
        
        try {
            manager.addAxiom(interestingAxiomOntology, axiomList.get(random.nextInt(axiomList.size())));
            manager.saveOntology(interestingAxiomOntology, ontologyFormat, outputstream);
        } catch (OWLOntologyStorageException e) {
            e.printStackTrace();
            return null;
        }
        manager.removeOntology(interestingAxiomOntology);
        System.gc();

        return IApath;
    }

    private static void writeToCSV(String filePath, List<RepairEvaluation> repairEval, String exampleName, OWLAxiom axiom) throws IOException{
        File resultfile = new File(filePath+File.separator+"result.csv");
        
        
        FileWriter writer = new FileWriter(filePath+File.separator+"result.csv", true);
        
        List<String> headers = new ArrayList<>();
        headers.add("Example");
        headers.add("Defect Axiom");
        for (RepairEvaluation e : repairEval){
            headers.add(e.optionName);
        }

        if (resultfile.exists() && resultfile.length() == 0){
            writer.append(String.join(",", headers)).append("\n");
        }
        headers.remove("Example");
        List<String> values = new ArrayList<>();
        values.add(exampleName);
        values.add(axiom.toString());
        for (int i=0; i<repairEval.size(); i++){
            values.add(String.valueOf(repairEval.get(i).cost));
        }
        writer.append(String.join(",", values));
        writer.append("\n");

        writer.flush();
        writer.close();
    }

    private static void logDecisions(String filePath, List<RepairEvaluation> repairEval, String exampleName, OWLAxiom axiom) throws StreamWriteException, DatabindException, IOException{
        ObjectMapper objectMapper = new ObjectMapper();
        File jsonFile = new File(filePath + File.separator + "decisons.json");

        ObjectNode rootNode;
        if (jsonFile.exists() && jsonFile.length() > 0){
            rootNode = (ObjectNode) objectMapper.readTree(jsonFile);
        } else {
            rootNode = objectMapper.createObjectNode();
        }

        JsonNode evalListNode = objectMapper.valueToTree(repairEval);

        ObjectNode exampleDetailsNode = objectMapper.createObjectNode();
        exampleDetailsNode.put("Defect", axiom.toString());
        exampleDetailsNode.put("Evaluation", evalListNode);

        if (rootNode.has(exampleName)){
            ArrayNode existingDefectEvals = (ArrayNode) rootNode.get(exampleName);
            existingDefectEvals.add(exampleDetailsNode);
            rootNode.set(exampleName, existingDefectEvals);
        } else {
            ArrayNode defectEvalsArray = objectMapper.createArrayNode();
            defectEvalsArray.add(exampleDetailsNode);
            rootNode.set(exampleName, defectEvalsArray);
        }
        
        
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(jsonFile, rootNode);
    }

    public static void main(String[] args) throws OWLOntologyCreationException, OWLOntologyStorageException, EntityCheckerException, IOException {
        String examplePath = args[0];
        String intermediateOutDir = args[1];
        Boolean resume = Boolean.parseBoolean(args[2]);  
        // String examplePath = "C:/Users/prati/Desktop/ELExplicator/elexplicator/Examples";
        //get an arg to specify if first run or rerun
        //if rerun, read program state from a file and resume
        // else start fresh

        //for the first run, create output dir, get list of example files and serialize
        // else read from serialized file
        String outDirString = "OptionsEval";

        if(!(new File(outDirString)).exists()){
            try {
                Files.createDirectory(Paths.get(outDirString));
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }

        File[] exampleFiles = null;
        Map<String, Object> example = new HashMap<>();
        Map<String, Object> programState = new HashMap<>();
        if (!resume){           
            
            File resultFile = new File(outDirString + File.separator + "result.csv");
            try {
                Files.deleteIfExists(resultFile.toPath());
            } catch (IOException e) {
                e.printStackTrace();
            }
            File exampleDir = new File(examplePath); 
            exampleFiles = exampleDir.listFiles();
            Arrays.sort(exampleFiles, Comparator.comparingLong(File::length));
        } else {
            //load from serialized file
            exampleFiles = getExampleFiles();
            programState = loadCheckpoint();
        }
        
        List<String> options = Arrays.asList("option1", "option2", "option3", "user", "mix");
        for (File exampleFile : exampleFiles){
            if (resume && exampleFile.getName().equals(((Map<String, Object>) programState.get("currentExample")).get("example").toString())){
                // resumeExample(send the deserialized program state) 
                resumeExample(exampleFile, intermediateOutDir, programState, options);
                resume = false;
                continue;
            } else if (resume){
                continue;
            } else {
                example = loadExampleInstances(exampleFile, outDirString);
                if (example == null){
                    return;
                }               
                
                runExampleRepairEvaluation(exampleFile, outDirString, intermediateOutDir, options, example);
            }
        }
        saveCheckpoint(Map.of("status", "Success"), outDirString);
            
    }

    private static File[] getExampleFiles(){
        File [] exampleFiles = null;
        try{
            FileInputStream exampleListIn = new FileInputStream("examplesList.ser");
            ObjectInputStream in = new ObjectInputStream(exampleListIn);
            exampleFiles = (File[]) in.readObject();
            in.close();
        } catch (Exception e) {
            e.printStackTrace();
            return exampleFiles;
        }
        return exampleFiles;
    }

    private static Map<String, Object> loadCheckpoint(){
        Map<String, Object> programState = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        File checkpointFile = new File("programState.json");
        if (checkpointFile.exists() && checkpointFile.length() > 0){
            try {
                programState = objectMapper.readValue(checkpointFile, 
                    new TypeReference<Map<String, Object>>(){}
                );
            } catch (IOException e) {
                e.printStackTrace();
                return programState;
            }
        }
        return programState;
    }

    private static void resumeExample(File exampleFile, String intermediateOutDir, Map<String, Object> programState, List<String> options) throws OWLOntologyCreationException, OWLOntologyStorageException, EntityCheckerException, IOException{
        // To-Do: implement resuming from serialized file
        // get current defect axiom and current evaluation option
        Map<String, Object> example = (Map<String, Object>) programState.get("currentExample");
        String exampleName = (String) example.get("example");
        String normOntologyPathStr = (String) example.get("normalizedOntologyPathStr");
        
        String defectAxiomStr = programState.get("defectStr").toString();
        String interestingAxiomOntology = programState.get("iaOnto").toString(); 
        String aboxPathStr = programState.get("abox").toString();
        String outDirString = programState.get("outDir").toString();
        OWLAxiom defectAxiom = (OWLAxiom) programState.get("defectAxiom");
        List<OWLAxiom> defectsList = (List<OWLAxiom>) example.get("defectAxiomsSet");

        String evaluationOption = programState.get("currentOption").toString();
        List<RepairEvaluation> repEvalList = new ArrayList<>();
        if (!evaluationOption.equals("option1")){
            repEvalList = (List<RepairEvaluation>) programState.get("evaluations");
        }
        List<String> remainingOpts = options.subList(options.indexOf(evaluationOption), options.size());
        EvaluateOptions evaluateOptions = new EvaluateOptions(normOntologyPathStr, defectAxiomStr, interestingAxiomOntology, aboxPathStr, outDirString, remainingOpts);
        try{
            List<RepairEvaluation> newEvalList = evaluateOptions.evaluateOpt();
            repEvalList.addAll(newEvalList);
        } catch (EvaluationException e){
            repEvalList.addAll(e.getEvaluationResults());
            //log current state and break
            String currentOption = evaluateOptions.currentOption;
            programState.clear();
            programState.put("status", "Failure");
            programState.put("currentExample", example);
            programState.put("defectStr", defectAxiomStr);
            programState.put("iaOnto", interestingAxiomOntology);
            programState.put("abox", aboxPathStr);
            programState.put("outDir", outDirString);
            programState.put("defectAxiom", defectAxiom);
            programState.put("currentOption", currentOption);
            programState.put("evaluations", repEvalList);
            saveCheckpoint(programState, outDirString);
            System.exit(1);
        }
        try {
            writeToCSV(outDirString, repEvalList, exampleName, defectAxiom);
            logDecisions(outDirString, repEvalList, exampleName, defectAxiom);
            new File(aboxPathStr).delete();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }       

        List<OWLAxiom> remainingDefects = defectsList.subList(defectsList.indexOf(defectAxiom)+1, defectsList.size());
        example.put("defectAxiomsSet", remainingDefects);
        if (!remainingDefects.isEmpty()){
            runExampleRepairEvaluation(exampleFile, outDirString, intermediateOutDir, options, example);
        }
    }

    private static void runExampleRepairEvaluation(File exampleFile, String outDirString, String intermediateOutDir, List<String> options, Map<String, Object> example) throws OWLOntologyCreationException, OWLOntologyStorageException, EntityCheckerException, IOException{
        String exampleName = (String) example.get("example");
        OWLOntology normalizedOntology = (OWLOntology) example.get("normalizedOntology");
        List<OWLAxiom> defectsSet = (List<OWLAxiom>) example.get("defectAxiomsSet");
        String normOntologyPathStr = (String) example.get("normalizedOntologyPathStr");
        long fileSize = exampleFile.length();

        
        // OWLAxiom axiom = (OWLAxiom) example.get("defectAxiom");
        for (OWLAxiom axiom : defectsSet){
            System.out.println("Processing defect: "+axiom.toString());
            String interestingAxiomOntology = null;
            try{
                interestingAxiomOntology = generateInterestingAxiom(exampleFile.getPath(), axiom, outDirString);
                System.out.print("Interesting axiom ontology generated!");
            }
            catch(Exception e){
                System.out.println("Error generating interesting axiom "+exampleFile.getName());
                continue;
            }             
        
            ManchesterOWLSyntaxOWLObjectRendererImpl renderer = new ManchesterOWLSyntaxOWLObjectRendererImpl();
            // renderer.setShortFormProvider(new SimpleShortFormProvider());;
            renderer.setShortFormProvider(new ShortFormProvider() {
                @Override
                public String getShortForm(OWLEntity entity){
                    return "<"+entity.getIRI().toString()+">";
                }

                @Override
                public void dispose(){}
            });
            String defectAxiomStr = renderer.render(axiom);
            Map<String, Object> exampleTimeTracker = new HashMap<>();
            try{
                ABoxGenerator aBoxGenerator = new ABoxGenerator(normalizedOntology, exampleFile.getName(), defectAxiomStr, intermediateOutDir);
                aBoxGenerator.generateABox();
                Map<String, java.lang.Object> aboxGenTimeMap = aBoxGenerator.getAboxGenTimeMap();
                exampleTimeTracker.put("Example", exampleName);
                exampleTimeTracker.put("Defect", defectAxiomStr);
                exampleTimeTracker.put("File Size (kB)", fileSize/1000);
                exampleTimeTracker.putAll(aboxGenTimeMap);               
                
            } catch (InconsistentOntologyException e){
                System.out.println("Inconsistent Onto Error");
                continue;
            } 
            try{
                logAboxGenerationTime(exampleTimeTracker);
            }catch (IOException e){
                e.printStackTrace();
            }
            String aboxPathStr = intermediateOutDir + File.separator + exampleName.split(".owl")[0] + "_ABox.owl";
            EvaluateOptions evaluateOptions = new EvaluateOptions(normOntologyPathStr, defectAxiomStr, interestingAxiomOntology, aboxPathStr, outDirString, options);
            List<RepairEvaluation> repEvalList = new ArrayList<>();
            try{
                repEvalList = evaluateOptions.evaluateOpt();
            } catch (EvaluationException e){
                String currentOption = evaluateOptions.currentOption;
                Map<String, Object> programState = new HashMap<>();
                programState.put("status", "Failure");
                programState.put("currentExample", example);
                programState.put("defectStr", defectAxiomStr);
                programState.put("iaOnto", interestingAxiomOntology);
                programState.put("abox", aboxPathStr);
                programState.put("outDir", outDirString);
                programState.put("defectAxiom", axiom);
                programState.put("currentOption", currentOption);
                programState.put("evaluations", repEvalList);
                saveCheckpoint(programState, outDirString);
                System.exit(1);
            }
            try{
                writeToCSV(outDirString, repEvalList, exampleName, axiom);
                logDecisions(outDirString, repEvalList, exampleName, axiom);
                new File(aboxPathStr).delete();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try{
            new File(normOntologyPathStr).delete();
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    private static void logAboxGenerationTime(Map<String, Object> aboxGenTimeMap) throws IOException{
        //if csv file already exists and not empty: append
        //else create the csv file, add headers and add the data from map
        File timeMapFile = new File("aboxGenerationTime.csv");
        FileWriter fw = new FileWriter(timeMapFile, true);
        List<String> headers = new ArrayList<>(aboxGenTimeMap.keySet());
        if (timeMapFile.length() == 0){
            fw.append(String.join(",", headers)).append("\n");
        }
        List<String> values = new ArrayList<>();
        for (String key : headers){
            values.add(aboxGenTimeMap.getOrDefault(key, "").toString());
        }
        fw.append(String.join(",", values));
        fw.append("\n");
        
        fw.flush();
        fw.close();
    }

    private static void saveCheckpoint(Map<String, Object> programState, String outDirString){
        ObjectMapper objectMapper = new ObjectMapper();
        File checkpointFile = new File(outDirString + File.separator + "programState.json");
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(checkpointFile, programState);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}


