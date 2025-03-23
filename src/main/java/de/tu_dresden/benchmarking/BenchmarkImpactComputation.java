package de.tu_dresden.benchmarking;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.MemoryHandler;

import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.tu_dresden.inf.lat.exceptions.EntityCheckerException;
import de.tu_dresden.lat.data.names.ReasonerName;
import de.tu_dresden.lat.diagnoses.ComputeRepair;

public class BenchmarkImpactComputation {
    OWLOntology ontology;
    OWLOntology interestingAxiomOntology;
    OWLAxiom defect;
    String ontologyPath;
    int iterations;
    int iteration_index;
    int instance_index;
    List<Long> runtime;

    String outdir = "Benchmark";

    public BenchmarkImpactComputation(OWLOntology ontology, OWLOntology interestingAxiomOntology, OWLAxiom defect, String ontologyPath, int iterations, int curr_instance, int curr_iteration, List<Long> runtime) {
        this.ontology = ontology;
        this.interestingAxiomOntology = interestingAxiomOntology;
        this.defect = defect;
        this.ontologyPath = ontologyPath;
        this.iterations = iterations;
        this.iteration_index = curr_iteration;
        this.instance_index = curr_instance;
        this.runtime = runtime;
    }

    public List<Long> run() throws OWLOntologyCreationException, OWLOntologyStorageException, IOException, EntityCheckerException, TimeoutException{
        PrintStream originalOut = System.out;
        PrintStream devNull = new PrintStream(new OutputStream() {
            @Override
            public void write(int b) { }
        });
        System.setOut(devNull);      

        long timeout = 120; //2min timeout

        for (int i=iteration_index; i<iterations; i++){
            ExecutorService executor = Executors.newSingleThreadExecutor();
            System.gc();
            Callable<Long> impactTask = () -> {
                long itr_start = System.nanoTime();
                simulateUserInput("not sure\n\nexit");
                ComputeRepair.computeRepairOntology(defect, ontology, interestingAxiomOntology, ReasonerName.getReasonerName("Elk"), outdir, ontologyPath);
                long itr_end = System.nanoTime();
                return(itr_end - itr_start);
            };

            Future<Long> future = executor.submit(impactTask);
            try{
                runtime.add(future.get(timeout, TimeUnit.SECONDS)/1000000);                
            } catch(TimeoutException e){
                runtime.add(timeout*60000+1);
                future.cancel(true);   
                // writeLog(instance_index, i, runtime);  
                // throw e;        
            }catch(OutOfMemoryError e){
                writeLog(instance_index, i, runtime);
                throw e;
            }
            catch(Exception e){
                e.printStackTrace();
            } finally {
                executor.shutdownNow();
                try {
                    executor.awaitTermination(2, TimeUnit.MINUTES);
                } catch (InterruptedException e) {
                    System.out.println("Executor shutdown interrupted");
                }
            }
            
        }
        System.setOut(originalOut);

        return runtime;
    }

    public static void simulateUserInput(String simulatedInput){
        InputStream originalSystemIn = System.in;
        ByteArrayInputStream inputStream = new ByteArrayInputStream(simulatedInput.getBytes());
        System.setIn(inputStream);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> System.setIn(originalSystemIn)));
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
