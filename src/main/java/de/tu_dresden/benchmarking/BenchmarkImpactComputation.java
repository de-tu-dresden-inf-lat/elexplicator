package de.tu_dresden.benchmarking;

import java.io.ByteArrayInputStream;
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

import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;

import de.tu_dresden.inf.lat.exceptions.EntityCheckerException;
import de.tu_dresden.lat.data.names.ReasonerName;
import de.tu_dresden.lat.diagnoses.ComputeRepair;

public class BenchmarkImpactComputation {
    OWLOntology ontology;
    OWLOntology interestingAxiomOntology;
    OWLAxiom defect;
    String ontologyPath;
    int iterations;

    String outdir = "Benchmark";

    public BenchmarkImpactComputation(OWLOntology ontology, OWLOntology interestingAxiomOntology, OWLAxiom defect, String ontologyPath, int iterations) {
        this.ontology = ontology;
        this.interestingAxiomOntology = interestingAxiomOntology;
        this.defect = defect;
        this.ontologyPath = ontologyPath;
        this.iterations = iterations;
    }

    public List<Long> run() throws OWLOntologyCreationException, OWLOntologyStorageException, IOException, EntityCheckerException{
        List<Long> runtime = new ArrayList<>();

        PrintStream originalOut = System.out;
        PrintStream devNull = new PrintStream(new OutputStream() {
            @Override
            public void write(int b) { }
        });
        // System.setOut(devNull);      

        long timeout = 2; //2min timeout

        for (int i=1; i<=iterations; i++){
            ExecutorService executor = Executors.newSingleThreadExecutor();
            System.gc();
            Callable<Long> impactTask = () -> {
                long itr_start = System.nanoTime();
                System.out.println("Start time"+System.currentTimeMillis());
                simulateUserInput("not sure\n\nexit");
                ComputeRepair.computeRepairOntology(defect, ontology, interestingAxiomOntology, ReasonerName.getReasonerName("Elk"), outdir, ontologyPath);
                long itr_end = System.nanoTime();
                return(itr_end - itr_start);
            };

            Future<Long> future = executor.submit(impactTask);
            try{
                runtime.add(future.get(timeout, TimeUnit.MINUTES)/1000000);                
            } catch(TimeoutException e){
                runtime.add(timeout*60000+1);
                future.cancel(true);             
            } catch(Exception e){
                e.printStackTrace();
            } finally {
                System.out.println("End time"+System.currentTimeMillis());
                executor.shutdownNow();
                try {
                    executor.awaitTermination(timeout, TimeUnit.MINUTES);
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
}
