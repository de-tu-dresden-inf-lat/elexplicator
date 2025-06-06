package de.tu_dresden.benchmarking;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.tu_dresden.inf.lat.exceptions.EntityCheckerException;

public class BenchmarkImpactComputation {
    OWLOntology ontology;
    String interestingAxiomOntology;
    String defect;
    String ontologyPath;
    int iterations;
    Long timeout;
    int iteration_index;
    int instance_index;
    Map<String, List<Long>> runtime;

    String outdir = "Benchmark";
    List<String> options = Arrays.asList("opt1", "opt2", "opt3");

    public BenchmarkImpactComputation(OWLOntology ontology, String interestingAxiomOntology, String defect, String ontologyPath, int iterations, Long timeout, int curr_instance, int curr_iteration, Map<String, List<Long>> runtime) {
        this.ontology = ontology;
        this.interestingAxiomOntology = interestingAxiomOntology;
        this.defect = defect;
        this.ontologyPath = ontologyPath;
        this.iterations = iterations;
        this.timeout = timeout;
        this.iteration_index = curr_iteration;
        this.instance_index = curr_instance;
        this.runtime = runtime;
    }

    public Map<String, List<Long>> run() throws OWLOntologyCreationException, OWLOntologyStorageException, IOException, EntityCheckerException, TimeoutException, InterruptedException{
        // PrintStream originalOut = System.out;
        // PrintStream devNull = new PrintStream(new OutputStream() {
        //     @Override
        //     public void write(int b) { }
        // });
        // System.setOut(devNull);      
        String jar_path = "target/ELExplicator.jar";
        String[] commands = {"java", "-jar", jar_path, 
            "-a", defect,
            "-o", ontologyPath,
            "-r",  "ELK",
            "-ia", interestingAxiomOntology,
            "-od", outdir};
            
        for (int i=iteration_index; i<iterations; i++){  
            for (String option : options){
                Boolean complete = false;
                ProcessBuilder pb = new ProcessBuilder(commands);
                pb.redirectInput(new File(String.format("src/test/resources/benchmarking/input_%s.txt", option)));
                pb.redirectOutput(new File(outdir+File.separator+"output_log.txt"));
                pb.redirectError(new File(outdir+File.separator+"error_log.txt"));
                try{
                    Long itr_start = System.nanoTime();
                    Process process = pb.start();
                    
                    complete = process.waitFor(timeout, TimeUnit.SECONDS);
                    Long itr_end = System.nanoTime();
                    if (complete){
                        runtime.get(option).add((itr_end-itr_start)/1000000);
                    } else {
                        process.destroy();
                        if(!process.waitFor(10, TimeUnit.SECONDS)){
                            process.destroyForcibly();
                        }
                        runtime.get(option).add(timeout * 1000 + 1);
                    } 
                }catch(OutOfMemoryError e){
                    for (String rollbackOption : options){
                        if (runtime.get(rollbackOption).size() > i){
                            runtime.get(rollbackOption).remove(i);
                        }
                    }
                    writeLog(instance_index, i, runtime);
                    throw e;
                }finally{
                    System.gc();
                    Thread.sleep(5000);
                }
    
            }

        }
        // System.setOut(originalOut);
        return runtime;
    }

    public static void writeLog(int instance, int iteration, Map<String, List<Long>> runtimes) throws JsonGenerationException, JsonMappingException, IOException{
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode jsonNode = objectMapper.createObjectNode();
        jsonNode.put("Status", "Failure");
        jsonNode.put("Instance", instance);
        jsonNode.put("Iteration", iteration);
        JsonNode runtimeNode = objectMapper.valueToTree(runtimes);
        ObjectNode rootNode = objectMapper.createObjectNode();
        rootNode.set("Runtimes", runtimeNode);
        objectMapper.writeValue(new File("Benchmark/benchmark_log.json"), jsonNode);
    }
}
