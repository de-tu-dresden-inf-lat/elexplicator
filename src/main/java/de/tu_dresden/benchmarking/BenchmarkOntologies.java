package de.tu_dresden.benchmarking;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.log4j.Logger;
import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.semanticweb.elk.owlapi.ElkReasoner;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.tu_dresden.inf.lat.model.tools.ToOWLTools;
import de.tu_dresden.lat.data.names.ReasonerName;
import de.tu_dresden.lat.diagnoses.HelperFunctions;
import de.tu_dresden.lat.ontologyGenerator.ELOntologyGenerator;
import de.tu_dresden.lat.ontologyGenerator.NameGenerator;

public class BenchmarkOntologies {

    private static Logger logger = Logger.getLogger(BenchmarkOntologies.class);
    public static List<Map<String, Object>> generateExampleInstances(String outDirString){
        int[] totalJustificationsList = {2, 5, 8, 12};
        int[] justificationMaxSizeList = {3, 5, 8};
        int[] maxCommonAxiomsList = {0, 3, 5};
        String ontologyPathStr = null;

        String lhsStr= "A", rhsStr="C";

        OWLClass lhs = NameGenerator.getInstance().getAsNameGeneratorConceptName(lhsStr);
        OWLClass rhs = NameGenerator.getInstance().getAsNameGeneratorConceptName(rhsStr);

        OWLSubClassOfAxiom axiom = ToOWLTools.getInstance().getOWLSubClassOfAxiom(lhs,rhs);
        ELOntologyGenerator generator = new ELOntologyGenerator(axiom);

        OWLOntology ontology = null;
        OWLOntology interestingAxiomOntology = null;

        List<Map<String, Object>> returnList = new ArrayList<>();

        for (int totalJustification : totalJustificationsList){
            for (int justificationMaxSize : justificationMaxSizeList){
                for (int maxCommonAxioms : maxCommonAxiomsList){
                    if (maxCommonAxioms >= totalJustification){
                        continue;
                    }
                    Map<String, Object> map = new HashMap<>();
                    String exampleName = "BenchmarkOntology"+totalJustification+"-"+justificationMaxSize+"-"+maxCommonAxioms;
                    ontologyPathStr = outDirString+ File.separator +exampleName+".owl";
                    generator.generateOntology(totalJustification, justificationMaxSize,
                                               maxCommonAxioms, ontologyPathStr);
                    OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
                    try {
                        ontology = manager.loadOntologyFromOntologyDocument(new File(ontologyPathStr));
                    } catch (OWLOntologyCreationException e) {
                        e.printStackTrace();
                        return null;
                    } 
                    try {
                        interestingAxiomOntology = generateInterestingAxiom(ontologyPathStr, axiom);
                    } catch (OWLOntologyCreationException e) {
                        e.printStackTrace();
                        return null;
                    }
                    map.put("example", exampleName);
                    map.put("ontology", ontology);
                    map.put("defectAxiom", axiom);
                    map.put("interestingAxiomOntology", interestingAxiomOntology);
                    map.put("ontologyPathStr", ontologyPathStr);

                    returnList.add(map);
                }
            }
        }     
        

        return returnList;
    }

    public static List<Map<String, Object>> loadExampleInstances(String exampleDirStr){
        List<Map<String, Object>> returnList = new ArrayList<>();

        File exampleDir = new File(exampleDirStr);
        File[] dirFiles = exampleDir.listFiles();

        OWLOntology ontology = null;
        OWLOntology interestingAxiomOntology = null;

        for (File exampleFile : dirFiles){
            Map<String, Object> map = new HashMap<>();
            System.out.println("loading file: "+exampleFile.getName());
            try{
                OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
                ontology = manager.loadOntologyFromOntologyDocument(exampleFile);
            }
            catch(Exception e){
                System.out.println("Error loading ontology "+exampleFile.getName());
                continue;
            }
            OWLAxiom axiom = null;
            try{
                axiom = selectDefectAxiom(exampleFile.getPath());
            } catch (Exception e){
                e.printStackTrace();
            }
            if (axiom == null){
                System.out.println("Error selecting defect "+exampleFile.getName());
                continue;
            }
            try{
               interestingAxiomOntology = generateInterestingAxiom(exampleFile.getPath(), axiom);
            }
            catch(Exception e){
                System.out.println("Error generating interesting axiom "+exampleFile.getName());
                continue;
            }
            map.put("example", exampleFile.getName());
            map.put("ontology", ontology);
            map.put("defectAxiom", axiom);
            map.put("interestingAxiomOntology", interestingAxiomOntology);
            map.put("ontologyPathStr", exampleFile.getPath());
            returnList.add(map);
        }
        
        return returnList;

    }

    
    private static OWLAxiom selectDefectAxiom(String ontologyPath) throws OWLOntologyCreationException{
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntology ontology = manager.loadOntologyFromOntologyDocument(new File(ontologyPath));
        ElkReasonerFactory reasonerFactory = new ElkReasonerFactory();
        ElkReasoner reasoner = reasonerFactory.createReasoner(ontology);

        Set<OWLClass> classes = ontology.getClassesInSignature();
        OWLDataFactory factory = manager.getOWLDataFactory();

        List<OWLSubClassOfAxiom> axiomList = new ArrayList<>(); 
        for (OWLClass owlClass : classes){
            Set<OWLClass> inferredSubclasses = reasoner.getSubClasses(owlClass, false).getFlattened();
            if(!inferredSubclasses.isEmpty()){
                for (OWLClass inferredSubClass : inferredSubclasses){
                    OWLSubClassOfAxiom axiom = factory.getOWLSubClassOfAxiom(inferredSubClass, owlClass);
                    if (!ontology.containsAxiom(axiom) && !inferredSubClass.isBottomEntity()){
                        axiomList.add(axiom);
                    }
                }
            }
        }
        reasoner.dispose();
        if(axiomList.isEmpty()){
            axiomList = new ArrayList<>(ontology.getAxioms(AxiomType.SUBCLASS_OF));
        }
        Collections.shuffle(axiomList);
        return axiomList.get(0);
    }

    private static OWLOntology generateInterestingAxiom(String ontologyPath, OWLAxiom defectAxiom) throws OWLOntologyCreationException{
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntology ontology = manager.loadOntologyFromOntologyDocument(new File(ontologyPath));
        ElkReasonerFactory reasonerFactory = new ElkReasonerFactory();
        ElkReasoner reasoner = reasonerFactory.createReasoner(ontology);
        
        Set<OWLClass> classes = ontology.getClassesInSignature();
        OWLDataFactory factory = manager.getOWLDataFactory();

        Set<OWLSubClassOfAxiom> axiomSet1 = new HashSet<>();
        Set<OWLSubClassOfAxiom> axiomSet2 = new HashSet<>();

        for (OWLClass owlClass : classes) {
            Set<OWLClass> inferredSubclasses = reasoner.getSubClasses(owlClass, false).getFlattened();            
            if (!inferredSubclasses.isEmpty()) {                
                for (OWLClass inferredSubClass: inferredSubclasses){
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
        OWLOntology interestingAxiomOntology = manager.createOntology();

        List<OWLSubClassOfAxiom> axiomList;
        if (!axiomSet1.isEmpty()){
            axiomList = new ArrayList<>(axiomSet1);
        } else {
            axiomList = new ArrayList<>(axiomSet2);
        }
        Collections.shuffle(axiomList);
        manager.addAxiom(interestingAxiomOntology, axiomList.get(0));

        return interestingAxiomOntology;
    }

    private static Map<String, Integer> getJustificationInfo(OWLOntology ontology, OWLAxiom axiom){
        // given an OWL ontolgy and defect, get the number of justifications, max justification size and max number of common axioms
        Set<Set<? extends OWLAxiom>> justifications = new HashSet<>();
        try{
            justifications = HelperFunctions.getAllJustifications(ReasonerName.Elk , axiom, ontology);
        } catch (Exception e){
            e.printStackTrace();
            Thread.currentThread().interrupt();
        }
        
        Map<String, Integer> infoMap = new HashMap<>();

        int totalJustifications = justifications.size();
        int maxJustificationSize = 0;
        int maxCommonAxioms = 0;

        for (Set<? extends OWLAxiom> justification : justifications){
            maxJustificationSize = Math.max(maxJustificationSize, justification.size());
        }

        List<Set<? extends OWLAxiom>> setList = justifications.stream().collect(Collectors.toList());

        maxCommonAxioms = setList.stream()
            .flatMap(outerSet -> setList.stream()
                .filter(innerSet -> !outerSet.equals(innerSet))  
                .map(innerSet -> {
                    Set<? extends OWLAxiom> intersection = new HashSet<>(outerSet);
                    intersection.retainAll(innerSet);
                    return intersection.size();
                })
            )
            .max(Integer::compare)
            .orElse(0);  

        infoMap.put("totalJustifications", totalJustifications);
        infoMap.put("maxJustificationSize", maxJustificationSize);
        infoMap.put("maxCommonAxioms", maxCommonAxioms);

        return infoMap;
    }

    private static void writeToBenchFile(String filePath, List<Map<String, Object>> benchMap) throws IOException{
        FileWriter writer = new FileWriter(filePath+File.separator+"result.csv");
        List<String> headers = new ArrayList<>(benchMap.get(0).keySet());
        headers.removeAll(Arrays.asList("ontology", "interestingAxiomOntology", "ontologyPathStr"));
        writer.append(String.join(",", headers)).append("\n");
        for (Map<String, Object> row : benchMap){
            List<String> values = new ArrayList<>();
            for (String header : headers){
                values.add(row.get(header).toString());
            }
            writer.append(String.join(",", values));
            writer.append("\n");
        }
        writer.flush();
        writer.close();
        logger.info("Benchmark results written to file: "+filePath);
    }

    public static void writeBenchLog() throws IOException{
        ObjectMapper jsonObjectMapper = new ObjectMapper();
        ObjectNode jsonNode = jsonObjectMapper.createObjectNode();
        jsonNode.put("Status", "Success");
        jsonObjectMapper.writeValue(new File("Benchmark/benchmark_log.json"), jsonNode);
    }

    public static void main(String[] args) {
        Boolean firstRun = Boolean.parseBoolean(args[0]);

        int iterations = 3;

        String outDirString = "Benchmark";
        if(!(new File(outDirString)).exists()){
            try {
                Files.createDirectory(Paths.get(outDirString));
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }

        List<Map<String, Object>> exampleList = new ArrayList<>();
        int iteration_index = 0;
        int instance_index = 0;
        List<Long> runtime = new ArrayList<>();
        if (firstRun){
            exampleList = generateExampleInstances(outDirString);
            for (Map<String, Object> example : exampleList){
                OWLOntology ontology = (OWLOntology) example.get("ontology");
                OWLAxiom axiom = (OWLAxiom) example.get("defectAxiom");
    
                Map<String, Integer>justificationInfo = getJustificationInfo(ontology, axiom);
                example.put("Num of Justifications", String.valueOf(justificationInfo.get("totalJustifications")));
                example.put("Max Justification Size", String.valueOf(justificationInfo.get("maxJustificationSize")));
                example.put("Max Common Axioms", String.valueOf(justificationInfo.get("maxCommonAxioms")));
            }
            try {
                FileOutputStream exampleOut = new FileOutputStream("examples.ser");
                ObjectOutputStream out = new ObjectOutputStream(exampleOut);
                out.writeObject(exampleList);
                out.close();
                exampleOut.close();
                System.out.println("Serialization done!");
            } catch (Exception e) {
                e.printStackTrace();
            }
            
        } else {
            ObjectMapper objMapper = new ObjectMapper();
            try {
				JsonNode jsonNode = objMapper.readTree(new File("Benchmark/benchmark_log.json"));
                instance_index = jsonNode.get("Instance").asInt();
                iteration_index = jsonNode.get("Iteration").asInt();
                runtime = objMapper.convertValue(jsonNode.get("Runtimes"), List.class);
			} catch (Exception e) {
				e.printStackTrace();
			} 
            try{
                FileInputStream exampleIn = new FileInputStream("examples.ser");
                ObjectInputStream in = new ObjectInputStream(exampleIn);
                exampleList = (List<Map<String, Object>>) in.readObject();

            } catch (Exception e){
                e.printStackTrace();
            }
            
        }

        
        while (instance_index < exampleList.size()){
            Map<String, Object> example = exampleList.get(instance_index);
            String exampleName = (String) example.get("example");
            OWLOntology ontology = (OWLOntology) example.get("ontology");
            OWLAxiom axiom = (OWLAxiom) example.get("defectAxiom");
            OWLOntology interestingAxiomOntology = (OWLOntology) example.get("interestingAxiomOntology");
            String ontologyPathStr = (String) example.get("ontologyPathStr");

            BenchmarkImpactComputation bench = new BenchmarkImpactComputation(
                ontology, interestingAxiomOntology, axiom, ontologyPathStr, iterations,
                instance_index, iteration_index, runtime
                );
            try {
                System.out.println("Benchmarking example: "+exampleName);
                runtime = bench.run();
                for(int i=1; i <= iterations; i++){
                    
                    String key = "Iteration "+i;
                    Long iter_runtime = runtime.get(i-1);
                    if (iter_runtime > 120000){
                        example.put(key, "Timeout");
                        continue;
                    }
                    String val = String.valueOf(runtime.get(i-1));                    
                    example.put(key, val);
                }

                //Calc mean and std dev
                Mean mean = new Mean();
                double avg = mean.evaluate(runtime.stream().mapToDouble(Long::doubleValue).toArray());
                example.put("Average Runtime", String.valueOf((long) avg));

                StandardDeviation sd = new StandardDeviation();
                double std_dev = sd.evaluate(runtime.stream().mapToDouble(Long::doubleValue).toArray());

                example.put("Standard Deviation", String.format("%.2f", std_dev));
                double std_dev_percent = (std_dev/avg)*100;
                example.put("Std Dev %", String.format("%.2f", std_dev_percent));
                
                iteration_index = 0;
                runtime = new ArrayList<Long>();
                instance_index++;
            } catch (Exception e){
                System.out.println("Error in benchmarkontologies main");
                e.printStackTrace();
                Thread.currentThread().interrupt();
                return;
            }
        }

        try {
            writeToBenchFile(outDirString, exampleList);
            writeBenchLog();
        } catch (IOException e) {
            e.printStackTrace();
        }
            
    }
        
}
