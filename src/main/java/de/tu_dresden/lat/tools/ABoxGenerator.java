package de.tu_dresden.lat.tools;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;
import org.semanticweb.HermiT.ReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.OWLXMLDocumentFormat;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassAssertionAxiom;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDeclarationAxiom;
import org.semanticweb.owlapi.model.OWLDisjointClassesAxiom;
import org.semanticweb.owlapi.model.OWLDocumentFormat;
import org.semanticweb.owlapi.model.OWLEquivalentClassesAxiom;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;

import de.tu_dresden.inf.lat.exceptions.EntityCheckerException;
import de.tu_dresden.inf.lat.model.tools.ToOWLTools;
import de.tu_dresden.lat.data.enums.ExitCode;

public class ABoxGenerator {
    private static final Logger logger = Logger.getLogger(ABoxGenerator.class);  

    private String tboxOntologyPath;
    private String defectAxiom;
    private String outputDir;

    public ABoxGenerator(String tboxOntologyPath, String defectAxiom, String outputDir) {
        this.tboxOntologyPath = tboxOntologyPath;
        this.defectAxiom = defectAxiom;
        this.outputDir = outputDir;
    }

    public ExitCode generateABox() throws OWLOntologyCreationException, EntityCheckerException, OWLOntologyStorageException, IOException {
        //read the TBox ontology from the given path
        File tboxOntoFile = new File(tboxOntologyPath);
        logger.info("Abox generating for "+ tboxOntoFile.getName());
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
		OWLOntology tbox = manager.loadOntologyFromOntologyDocument(tboxOntoFile);
        OWLDataFactory dataFactory = manager.getOWLDataFactory();

        IRI aboxIRI = IRI.create(tbox.getOntologyID().getOntologyIRI().get() + "_ABox");
        OWLOntology abox = manager.createOntology(aboxIRI);

        OWLReasonerFactory reasonerFactory = new ReasonerFactory();
        OWLReasoner reasoner = reasonerFactory.createReasoner(tbox);
        reasoner.precomputeInferences();
        OWLReasoner aboxReasoner = reasonerFactory.createReasoner(abox);
        Map<OWLClass, Set<OWLClass>> classHierarchy = new HashMap<>();
        Map<OWLClass, Set<OWLClass>> supClassHierarchy = new HashMap<>();
        long startTime = System.nanoTime();
        for (OWLClass cls : tbox.getClassesInSignature()) {
            Set<OWLClass> sup = reasoner.getSuperClasses(cls, false).getFlattened();
            Set<OWLClass> sub = reasoner.getSubClasses(cls, false).getFlattened();
            classHierarchy.put(cls, sub);
            classHierarchy.get(cls).remove(dataFactory.getOWLNothing());
            supClassHierarchy.put(cls, sup);
        }
        long endTime = System.nanoTime();
        logger.info("Class hierarchy creation time (s): "+ (endTime-startTime)/1000000000);

        //select 80% from the class hierarchy:
        startTime = System.nanoTime();
        List<OWLClass> leafNodes = classHierarchy.keySet().stream()
                .filter(c -> classHierarchy.get(c).isEmpty())
                .collect(Collectors.toList());
        Collections.shuffle(leafNodes);
        int selectionSize = (int) (leafNodes.size() * 0.8);
        List<OWLClass> selectedClasses = leafNodes.subList(0, selectionSize);
        endTime = System.nanoTime();
        logger.info("Leaf classes selection time (s): "+ (endTime-startTime)/1000000000);
        System.out.println("Selected 80% of leaf classes for ABox generation.");

        //add individuals for the selected classes
        startTime = System.nanoTime();
        for (OWLClass cls : selectedClasses) {
            OWLDeclarationAxiom declAxiom = dataFactory.getOWLDeclarationAxiom(cls);
            manager.addAxiom(abox, declAxiom);
            //add random number of individuals per class
            int individualCount = 1 + (int)(Math.random() * 5); //between 1 and 5 individuals
            for (int i = 0; i < individualCount; i++) {
                String individualIRI = cls.getIRI().toString() + "_indv_" + i;
                OWLNamedIndividual individual = dataFactory.getOWLNamedIndividual(IRI.create(individualIRI));
                OWLClassAssertionAxiom classAssertion = dataFactory.getOWLClassAssertionAxiom(cls, individual);
                manager.addAxiom(abox, classAssertion);
            }
        }
        endTime = System.nanoTime();
        logger.info("Instance assertion time (s): "+(endTime-startTime)/1000000000);

        aboxReasoner.flush();
        //propagate the class assertions up the hierarchy
        startTime = System.nanoTime();
        for (OWLNamedIndividual indv : abox.getIndividualsInSignature()) {
            NodeSet<OWLClass> types = aboxReasoner.getTypes(indv, false);
            for(OWLClass cls : types.getFlattened()) {
                if (supClassHierarchy.get(cls) == null) {
                    continue;
                }
                for (OWLClass sup : supClassHierarchy.get(cls)){
                    OWLClassAssertionAxiom classAssertion = dataFactory.getOWLClassAssertionAxiom(sup, indv);
                    if (!abox.containsAxiom(classAssertion)) {
                        manager.addAxiom(abox, classAssertion);
                    }
                }
            }
        }
        endTime = System.nanoTime();
        logger.info("Instance propagation time (s): "+ (endTime - startTime)/1000000000);

        //Add counterexample to the defect axiom
        OWLAxiom defect = ToOWLTools.getInstance().getOWLAxiomFromStr(defectAxiom, tbox);
        
        startTime = System.nanoTime();
        Map<String, Set<OWLClassExpression>> classExpressionsMap = mapAxiomToClassExpression(defect, dataFactory);
        OWLNamedIndividual defectIndv = dataFactory.getOWLNamedIndividual(IRI.create(aboxIRI + "counterexample_indv"));
        //for add classes in map add assertions and avoid propagating to classes in avoid set
        for (OWLClassExpression ce : classExpressionsMap.get("add")){
            OWLClassAssertionAxiom classAssertion = dataFactory.getOWLClassAssertionAxiom(ce, defectIndv);
            manager.addAxiom(abox, classAssertion);
            Set<OWLClass> superClasses = supClassHierarchy.get(ce.asOWLClass());
            for (OWLClassExpression avoidCE : classExpressionsMap.get("avoid")){
                superClasses.remove(avoidCE.asOWLClass());
            }
            for (OWLClass sc : superClasses) {
                OWLClassAssertionAxiom ax = dataFactory.getOWLClassAssertionAxiom(sc, defectIndv);
                manager.addAxiom(abox, ax);
            }
        } 
        endTime = System.nanoTime();
        logger.info("Defect entailment breaking time (s): "+(endTime - startTime)/1000000000);
        
        //get tbox file name from tboxOntologyPath
        String tboxFileName = new File(this.tboxOntologyPath).getName();
        String aboxPath = outputDir + File.separator + tboxFileName.split(".owl")[0] + "_ABox.owl";
        OutputStream outputstream = Files.newOutputStream(new File(aboxPath).toPath());
        OWLDocumentFormat ontologyFormat = new OWLXMLDocumentFormat();
        manager.saveOntology(abox, ontologyFormat, outputstream);
        

        return ExitCode.terminatedSuccessfully;
    }

    public static Map<String, Set<OWLClassExpression>> mapAxiomToClassExpression(OWLAxiom axiom, OWLDataFactory dataFactory) {
        Map<String, Set<OWLClassExpression>> axiomClassExprMap = new HashMap<>();
        Set<OWLClassExpression> classExpressions = new HashSet<>();
        Set<OWLClassExpression> avoidClassExpressions = new HashSet<>();
        if (axiom instanceof OWLSubClassOfAxiom) {
            OWLSubClassOfAxiom subAxiom = (OWLSubClassOfAxiom) axiom;
            classExpressions.add(subAxiom.getSubClass());
            avoidClassExpressions.add(subAxiom.getSuperClass());
            axiomClassExprMap.put("add", classExpressions);
            axiomClassExprMap.put("avoid", avoidClassExpressions);
            return axiomClassExprMap;
        } else if (axiom instanceof OWLEquivalentClassesAxiom){
            OWLEquivalentClassesAxiom eqAxiom = (OWLEquivalentClassesAxiom) axiom;
            classExpressions.add(eqAxiom.getClassExpressions().iterator().next());
            //add everything except the first one to avoid set
            for (OWLClassExpression ce : eqAxiom.getClassExpressions()){
                if (!ce.equals(classExpressions.iterator().next())){
                    avoidClassExpressions.add(ce);
                }
            }
            axiomClassExprMap.put("add", classExpressions);
            axiomClassExprMap.put("avoid", avoidClassExpressions);
            return axiomClassExprMap;
        } else if (axiom instanceof OWLDisjointClassesAxiom){
            OWLDisjointClassesAxiom disjAxiom = (OWLDisjointClassesAxiom) axiom;
            axiomClassExprMap.put("add", disjAxiom.getClassExpressions());
            axiomClassExprMap.put("avoid", avoidClassExpressions);
            return axiomClassExprMap;
        } 
        else {
            return axiomClassExprMap;
        }        
    }
    public static void main(String[] args) {
        
        String tboxOntologyPath = args[0];
        String defectAxiom = args[1];
        String outputDir = args[2];
        
        ABoxGenerator generator = new ABoxGenerator(tboxOntologyPath, defectAxiom, outputDir);
        try {
            ExitCode result = generator.generateABox();
            System.out.println("ABox generation completed with exit code: " + result);
        } catch (OWLOntologyCreationException e) {
            System.err.println("Error creating ontology: " + e.getMessage());
        } catch (EntityCheckerException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (OWLOntologyStorageException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}