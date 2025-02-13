package de.tu_dresden.benchmarking;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;

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

    public long run() throws OWLOntologyCreationException, OWLOntologyStorageException, IOException, EntityCheckerException{
        
        PrintStream originalOut = System.out;
        PrintStream devNull = new PrintStream(new OutputStream() {
            @Override
            public void write(int b) { }
        });
        System.setOut(devNull);

        simulateUserInput("not sure\n\nexit");

        long startTime = System.currentTimeMillis();

        int i = 1;
        while (i<=iterations){
            ComputeRepair.computeRepairOntology(defect, ontology, interestingAxiomOntology, ReasonerName.getReasonerName("Elk"), outdir, ontologyPath);
            
            i += 1;
        }
        
        long endTime = System.currentTimeMillis();
        long totalRunTime = endTime - startTime;
        long avgRunTime = totalRunTime/iterations;

        System.setOut(originalOut);

        return avgRunTime;
    }

    public static void simulateUserInput(String simulatedInput){
        InputStream originalSystemIn = System.in;
        ByteArrayInputStream inputStream = new ByteArrayInputStream(simulatedInput.getBytes());
        System.setIn(inputStream);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> System.setIn(originalSystemIn)));
    }
}
