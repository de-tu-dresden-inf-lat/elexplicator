package de.tu_dresden.benchmarking;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.semanticweb.elk.owlapi.ElkReasoner;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;

import de.tu_dresden.inf.lat.model.tools.ToOWLTools;
import de.tu_dresden.lat.ontologyGenerator.ELOntologyGenerator;
import de.tu_dresden.lat.ontologyGenerator.NameGenerator;

public class BenchmarkOntologies {
    public static void main(String[] args) {
        int[] totalJustificationsList = {2, 5};
        int[] justificationMaxSizeList = {2, 5};
        int[] maxCommonAxiomsList = {0, 3};
        int iterations = 1;

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
        benchmarkMap.put("Total Justifications", new ArrayList<>());
        benchmarkMap.put("Max Justification Size", new ArrayList<>());
        benchmarkMap.put("Max Common Axioms", new ArrayList<>());
        benchmarkMap.put("Average Runtime", new ArrayList<>());

        String lhsStr= "A", rhsStr="C";

        OWLClass lhs = NameGenerator.getInstance().getAsNameGeneratorConceptName(lhsStr);
        OWLClass rhs = NameGenerator.getInstance().getAsNameGeneratorConceptName(rhsStr);

        OWLSubClassOfAxiom axiom = ToOWLTools.getInstance().getOWLSubClassOfAxiom(lhs,rhs);
        ELOntologyGenerator generator = new ELOntologyGenerator(axiom);

        OWLOntology ontology;
        OWLOntology interestingAxiomOntology;

        System.out.println("Running Benchmark for "+ iterations +" iterations");

        for (int totalJustification : totalJustificationsList){
            for (int justificationMaxSize : justificationMaxSizeList){
                for (int maxCommonAxioms : maxCommonAxiomsList){
                    String ontologyPathStr = outDirString+ File.separator +"BenchmarkOntology"+totalJustification+"-"+justificationMaxSize+"-"+maxCommonAxioms+".owl";
                    generator.generateOntology(totalJustification, justificationMaxSize,
                                               maxCommonAxioms, ontologyPathStr);
                    OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
                    try {
                        ontology = manager.loadOntologyFromOntologyDocument(new File(ontologyPathStr));
                    } catch (OWLOntologyCreationException e) {
                        e.printStackTrace();
                        return;
                    } 
                    try {
                        interestingAxiomOntology = generateInterestingAxiom(ontologyPathStr);
                    } catch (OWLOntologyCreationException e) {
                        e.printStackTrace();
                        return;
                    }

                    BenchmarkImpactComputation bench = new BenchmarkImpactComputation(ontology, interestingAxiomOntology, axiom, ontologyPathStr, 1);
                    try {
                        System.out.println("Benchmarking 1:\njustifications: "+totalJustification+" size: "+justificationMaxSize+" common axiom: "+maxCommonAxioms);
                        long runtime = bench.run();
                        System.out.println("Average Runtime: "+ runtime +"ms");
                        benchmarkMap.get("Total Justifications").add(String.valueOf(totalJustification));
                        benchmarkMap.get("Max Justification Size").add(String.valueOf(justificationMaxSize));
                        benchmarkMap.get("Max Common Axioms").add(String.valueOf(maxCommonAxioms));
                        benchmarkMap.get("Average Runtime").add(String.valueOf(runtime));

                    
                    } catch (Exception e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                        return;
                    } 
                }
            }
        }

        try {
            System.out.println(benchmarkMap);
            writeToBenchFile(outDirString + File.separator + "benchResult.csv", benchmarkMap);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
                //method to get the interesting axiom ontology
    }
        
    private static OWLOntology generateInterestingAxiom(String ontologyPath) throws OWLOntologyCreationException{
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
                    if (!ontology.containsAxiom(subclassAxiom) && !inferredSubClass.isBottomEntity()){                        
                        axiomSet1.add(subclassAxiom);
                    } else {
                        axiomSet2.add(subclassAxiom);
                    }
                }
            }
        }

        reasoner.dispose();
        OWLOntology interestingAxiomOntology = manager.createOntology();

        if (!axiomSet1.isEmpty()){
            manager.addAxiom(interestingAxiomOntology, axiomSet1.iterator().next()); 
        } else {
            manager.addAxiom(interestingAxiomOntology, axiomSet2.iterator().next());
        }

        return interestingAxiomOntology;
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
    }
}
