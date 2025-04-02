package de.tu_dresden.benchmarking;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
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
    List<Long> runtime;

    String outdir = "Benchmark";

    public BenchmarkImpactComputation(OWLOntology ontology, String interestingAxiomOntology, String defect, String ontologyPath, int iterations, Long timeout, int curr_instance, int curr_iteration, List<Long> runtime) {
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

    public List<Long> run() throws OWLOntologyCreationException, OWLOntologyStorageException, IOException, EntityCheckerException, TimeoutException, InterruptedException{
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
        Boolean complete = false;
        ProcessBuilder pb = new ProcessBuilder(commands);
        pb.redirectInput(new File("src/test/resources/benchmarking/input.txt"));
        pb.redirectOutput(new File(outdir+File.separator+"output_log.txt"));
        pb.redirectError(new File(outdir+File.separator+"error_log.txt"));
            
        for (int i=iteration_index; i<iterations; i++){  
            try{
                Long itr_start = System.nanoTime();
                Process process = pb.start();
                
                complete = process.waitFor(timeout, TimeUnit.SECONDS);
                Long itr_end = System.nanoTime();
                if (complete){
                    runtime.add((itr_end-itr_start)/1000000);
                } else {
                    process.destroy();
                    if(!process.waitFor(10, TimeUnit.SECONDS)){
                        process.destroyForcibly();
                    }
                    runtime.add(timeout * 1000 + 1);
                } 
            }catch(OutOfMemoryError e){
                writeLog(instance_index, i, runtime);
                throw e;
            }finally{
                System.gc();
                Thread.sleep(5000);
            }
        }
        // System.setOut(originalOut);
        return runtime;
    }

    public static void writeLog(int instance, int iteration, List<Long> runtimes) throws JsonGenerationException, JsonMappingException, IOException{
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode jsonNode = objectMapper.createObjectNode();
        jsonNode.put("Status", "Failure");
        jsonNode.put("Instance", instance);
        jsonNode.put("Iteration", iteration);
        ArrayNode arraynode = jsonNode.putArray("Runtimes");
        for (Long runtime:runtimes){
            arraynode.add(runtime);
        }
        objectMapper.writeValue(new File("Benchmark/benchmark_log.json"), jsonNode);
    }
}
