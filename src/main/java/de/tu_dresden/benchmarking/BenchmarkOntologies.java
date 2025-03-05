package de.tu_dresden.benchmarking;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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

import de.tu_dresden.inf.lat.model.tools.ToOWLTools;
import de.tu_dresden.lat.data.names.ReasonerName;
import de.tu_dresden.lat.diagnoses.HelperFunctions;
import de.tu_dresden.lat.ontologyGenerator.ELOntologyGenerator;
import de.tu_dresden.lat.ontologyGenerator.NameGenerator;

public class BenchmarkOntologies {

    private static Logger logger = Logger.getLogger(BenchmarkOntologies.class);
    public static List<Map<String, Object>> generateExampleInstances(String outDirString){
        int[] totalJustificationsList = {2, 5, 8};
        int[] justificationMaxSizeList = {5, 8, 12};
        int[] maxCommonAxiomsList = {0, 3, 8};
        // int[] totalJustificationsList = {8};
        // int[] justificationMaxSizeList = {5, 8};
        // int[] maxCommonAxiomsList = {0, 3};
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

    private static void writeToBenchFile(String filePath, Map<String, List<String>> benchMap) throws IOException{
        FileWriter writer = new FileWriter(filePath);
        List<String> headers = new ArrayList<>(benchMap.keySet());
        writer.append(String.join(",", headers)).append("\n");

        int rowCount = benchMap.values().iterator().next().size();

        for (int i = 0; i < rowCount; i++){
            List<String> row = new ArrayList<>();
            for (String header : headers){
                row.add(benchMap.get(header).get(i));
            }
            writer.append(String.join(",", row)).append("\n");
        }

        writer.close();
        logger.info("Benchmark results written to file: "+filePath);
    }

    public static void main(String[] args) {
        int iterations = 10;

        String outDirString = "Benchmark";
        if(!(new File(outDirString)).exists()){
            try {
                Files.createDirectory(Paths.get(outDirString));
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }                

        Map<String, List<String>> benchmarkMap = new LinkedHashMap<>();
        benchmarkMap.put("Example", new ArrayList<>());        
        benchmarkMap.put("Num of Justifications", new ArrayList<>());
        benchmarkMap.put("Max Justification Size", new ArrayList<>());
        benchmarkMap.put("Max Common Axioms", new ArrayList<>());
        benchmarkMap.put("Average Runtime", new ArrayList<>());
        benchmarkMap.put("Standard Deviation", new ArrayList<>());
        benchmarkMap.put("Std Dev %", new ArrayList<>());

        List<Map<String, Object>> exampleList = generateExampleInstances(outDirString);

        // List<Map<String, Object>> exampleList = loadExampleInstances("C:\\Users\\kansa\\Desktop\\BenchmarkOWLExamples");

        for (Map<String, Object> example : exampleList){
            // System.out.println("Defect: " + example.get("defectAxiom"));
            // System.out.println("InterestingAxiom: "+ ((OWLOntology) example.get("interestingAxiomOntology")).getAxioms());
            String exampleName = (String) example.get("example");
            OWLOntology ontology = (OWLOntology) example.get("ontology");
            OWLAxiom axiom = (OWLAxiom) example.get("defectAxiom");
            OWLOntology interestingAxiomOntology = (OWLOntology) example.get("interestingAxiomOntology");
            String ontologyPathStr = (String) example.get("ontologyPathStr");

            Map<String, Integer>justificationInfo = getJustificationInfo(ontology, axiom);
            benchmarkMap.get("Num of Justifications").add(String.valueOf(justificationInfo.get("totalJustifications")));
            benchmarkMap.get("Max Justification Size").add(String.valueOf(justificationInfo.get("maxJustificationSize")));
            benchmarkMap.get("Max Common Axioms").add(String.valueOf(justificationInfo.get("maxCommonAxioms")));

            BenchmarkImpactComputation bench = new BenchmarkImpactComputation(
                ontology, interestingAxiomOntology, axiom, ontologyPathStr, iterations
                );
            try {
                System.out.println("Benchmarking example: "+exampleName);
                List<Long> runtime = bench.run();
                benchmarkMap.get("Example").add(exampleName);
                for(int i=1; i <= iterations; i++){
                    
                    String key = "Iteration "+i;
                    String val = String.valueOf(runtime.get(i-1));
                    benchmarkMap.put(key, benchmarkMap.getOrDefault(key, new ArrayList<>()));
                    benchmarkMap.get(key).add(val);
                }
                
                Mean mean = new Mean();
                double avg = mean.evaluate(runtime.stream().mapToDouble(Long::doubleValue).toArray());
                benchmarkMap.get("Average Runtime").add(String.valueOf((long) avg));

                StandardDeviation sd = new StandardDeviation();
                double std_dev = sd.evaluate(runtime.stream().mapToDouble(Long::doubleValue).toArray());

                benchmarkMap.get("Standard Deviation").add(String.format("%.2f", std_dev));
                double std_dev_percent = (std_dev/avg)*100;
                benchmarkMap.get("Std Dev %").add(String.format("%.2f", std_dev_percent));
                System.out.println("Mean: "+avg);
                System.out.println("Std deviation: "+std_dev);
                System.out.println("Std deviation %: "+(std_dev/avg)*100);

            } catch (Exception e) {
                System.out.println("Error in benchmarkontologies main");
                e.printStackTrace();
                Thread.currentThread().interrupt();
                return;
            } 
        }

        

        try {
            String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date());
            String fileName = "benchResult_"+timestamp;
            writeToBenchFile(outDirString + File.separator + fileName +".csv", benchmarkMap);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
        
}
