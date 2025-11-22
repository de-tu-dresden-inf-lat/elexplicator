package de.tu_dresden.lat.evaluate;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
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
import org.semanticweb.owlapi.reasoner.InconsistentOntologyException;
import org.semanticweb.owlapi.util.ShortFormProvider;

import com.fasterxml.jackson.core.exc.StreamWriteException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.tu_dresden.inf.lat.exceptions.EntityCheckerException;
import de.tu_dresden.lat.tools.ABoxGenerator;

public class RunEvaluation {
    //for example ontologies in Examples dir:
    //Select defect axiom
    //Create IAAxioms ontology

    //Create Abox
    //Run evaluate
    //Save to CSV file

    public static Map<String, Object> loadGenExamples(File exampleFile, String outDirStr, OWLSubClassOfAxiom axiom){
        OWLOntology ontology = null;
        String interestingAxiomOntology = null;

        Map<String, Object> map = new HashMap<>();
        
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        try {
            ontology = manager.loadOntologyFromOntologyDocument(exampleFile);
        } catch (OWLOntologyCreationException e) {
            e.printStackTrace();
            return null;
        } 
        try {
            interestingAxiomOntology = generateInterestingAxiom(exampleFile.getPath(), axiom, outDirStr);
        } catch (OWLOntologyCreationException | IOException e) {
            e.printStackTrace();
            return null;
        }
        
        map.put("example", exampleFile.getName());
        map.put("ontology", ontology);
        map.put("defectAxiom", axiom);
        map.put("interestingAxiomOntology", interestingAxiomOntology);
        map.put("ontologyPathStr", exampleFile.getPath());       
        
        return map;
    }
    
    public static Map<String, Object> loadExampleInstances(File exampleFile, String outDirStr){

        OWLOntology ontology = null;
        String interestingAxiomOntology = null;

        Map<String, Object> map = new HashMap<>();
        System.out.println("loading file: "+exampleFile.getName());
        try{
            OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
            ontology = manager.loadOntologyFromOntologyDocument(exampleFile);
        }
        catch(Exception e){
            System.out.println("Error loading ontology "+exampleFile.getName());
            return null;
        }
        OWLAxiom axiom = null;
        try{
            axiom = selectDefectAxiom(exampleFile.getPath());
        } catch (Exception e){
            e.printStackTrace();
        }
        if (axiom == null){
            System.out.println("Error selecting defect "+exampleFile.getName());
            return null;
        }
        try{
            interestingAxiomOntology = generateInterestingAxiom(exampleFile.getPath(), axiom, outDirStr);
        }
        catch(Exception e){
            System.out.println("Error generating interesting axiom "+exampleFile.getName());
            return null;
        }
        map.put("example", exampleFile.getName());
        map.put("ontology", ontology);
        map.put("defectAxiom", axiom);
        map.put("interestingAxiomOntology", interestingAxiomOntology);
        map.put("ontologyPathStr", exampleFile.getPath());       
        
        return map;

    }

    
    private static OWLAxiom selectDefectAxiom(String ontologyPath) throws OWLOntologyCreationException{
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntology ontology = manager.loadOntologyFromOntologyDocument(new File(ontologyPath));
        ElkReasonerFactory reasonerFactory = new ElkReasonerFactory();
        ElkReasoner reasoner = reasonerFactory.createReasoner(ontology);
        List<OWLClass> signClasses = new ArrayList<>(ontology.getClassesInSignature());
        Collections.shuffle(signClasses);
        Set<OWLClass> classes = new HashSet<>(signClasses.subList(0, Math.min(20, signClasses.size())));
        OWLDataFactory factory = manager.getOWLDataFactory();

        List<OWLSubClassOfAxiom> axiomList = new ArrayList<>(); 
        for (OWLClass owlClass : classes){
            Set<OWLClass> inferredSubClasses = new HashSet<>();
            inferredSubClasses = reasoner.getSubClasses(owlClass, false).getFlattened();
            if(!inferredSubClasses.isEmpty()){
                for (OWLClass inferredSubClass : inferredSubClasses){
                    OWLSubClassOfAxiom axiom = factory.getOWLSubClassOfAxiom(inferredSubClass, owlClass);
                    if (!ontology.containsAxiom(axiom) && !inferredSubClass.isBottomEntity()){
                        axiomList.add(axiom);
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
        return axiomList.get(random.nextInt(axiomList.size()));
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

    private static void writeToCSV(String filePath, List<RepairEvaluation> repairEval, String exampleName) throws IOException{
        File resultfile = new File(filePath+File.separator+"result.csv");
        
        
        FileWriter writer = new FileWriter(filePath+File.separator+"result.csv", true);
        
        List<String> headers = new ArrayList<>();
        headers.add("Example");
        for (RepairEvaluation e : repairEval){
            headers.add(e.optionName);
        }

        if (resultfile.exists() && resultfile.length() == 0){
            writer.append(String.join(",", headers)).append("\n");
        }
        headers.remove("Example");
        List<String> values = new ArrayList<>();
        values.add(exampleName);
        for (int i=0; i<repairEval.size(); i++){
            values.add(String.valueOf(repairEval.get(i).cost));
        }
        writer.append(String.join(",", values));
        writer.append("\n");

        writer.flush();
        writer.close();
    }

    private static void logDecisions(String filePath, List<RepairEvaluation> repairEval, String exampleName) throws StreamWriteException, DatabindException, IOException{
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode listNode = objectMapper.valueToTree(repairEval);
        ObjectNode rootNode = objectMapper.createObjectNode();
        rootNode.set(exampleName, listNode);

        File jsonFile = new File(filePath + File.separator + "decisons.json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(jsonFile, rootNode);
    }


    public static void main(String[] args) throws OWLOntologyCreationException, OWLOntologyStorageException, EntityCheckerException, IOException {
        String examplePath = args[0];
        String intermediateOutDir = args[1];
        String outDirString = "OptionsEval";

        if(!(new File(outDirString)).exists()){
            try {
                Files.createDirectory(Paths.get(outDirString));
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }
        Map<String, Object> example = new HashMap<>();
        File[] exampleFiles = null;
        File resultFile = new File(outDirString + File.separator + "result.csv");
        try {
            Files.deleteIfExists(resultFile.toPath());
        } catch (IOException e) {
            e.printStackTrace();
        }
        File exampleDir = new File(examplePath); 
        exampleFiles = exampleDir.listFiles();

        for (File exampleFile : exampleFiles){
            example = loadExampleInstances(exampleFile, outDirString);
            if (example == null){
                continue;
            }
            
            String exampleName = (String) example.get("example");
            OWLOntology ontology = (OWLOntology) example.get("ontology");
            OWLAxiom axiom = (OWLAxiom) example.get("defectAxiom");
            String interestingAxiomOntology = (String) example.get("interestingAxiomOntology");
            String ontologyPathStr = (String) example.get("ontologyPathStr");
            
            

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
            try{
                ABoxGenerator aBoxGenerator = new ABoxGenerator(exampleFile.getAbsolutePath(), defectAxiomStr, intermediateOutDir);
                aBoxGenerator.generateABox();
                
            } catch (InconsistentOntologyException e){
                System.out.println("Inconsistent Onto Error");
                continue;
            } 
            String aboxPathStr = intermediateOutDir + File.separator + exampleName.split(".owl")[0] + "_ABox.owl";
            try{
                EvaluateOptions evaluateOptions = new EvaluateOptions(ontologyPathStr, defectAxiomStr, interestingAxiomOntology, aboxPathStr, outDirString);
                List<RepairEvaluation> repEvalList = evaluateOptions.evaluateOpt();
                writeToCSV(outDirString, repEvalList, exampleName);
                logDecisions(outDirString, repEvalList, exampleName);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        }
            
    }
}

