package de.tu_dresden.lat.ontologyGenerator;

import com.google.common.collect.Lists;
import de.tu_dresden.inf.lat.model.tools.ToOWLTools;
import de.tu_dresden.inf.lat.prettyPrinting.formatting.SimpleOWLFormatter;
import de.tu_dresden.lat.data.enums.ExitCode;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.OWLXMLDocumentFormat;
import org.semanticweb.owlapi.model.*;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A class to generate an ontology for the purpose of being repaired with respect to a given entailment.
 *
 * @author Christian Alrabbaa
 */

public class ELOntologyGenerator {
    private final OWLSubClassOfAxiom axiom;
    private final OWLObjectProperty roleName;
    private static final ToOWLTools tools = ToOWLTools.getInstance();
    private static final NameGenerator nameGenerator = NameGenerator.getInstance();
    public ELOntologyGenerator(OWLSubClassOfAxiom axiom) {
        this.axiom = axiom;

        nameGenerator.resetAllCounters();
        roleName = nameGenerator.getNextRoleName();
    }

    /**
     * Return an ontology that has exactly #totalJustification justifications for the axiom of this generator.\n
     * Each of these justifications has a size of at most #justificationMaxSize.\n
     * In addition, each of the justifications share at most #justificationMaxCommonAxioms axioms witch are from the
     * first #justificationMaxCommonAxioms axioms in the first justification.
     * @param totalJustification
     * @param justificationMaxSize
     * @param justificationMaxCommonAxioms
     * @return
     */
    public ExitCode generateOntology(int totalJustification, int justificationMaxSize,
                                     int justificationMaxCommonAxioms, String ontologyFileName){

        assert justificationMaxCommonAxioms < justificationMaxSize : "The max number of common axioms must be less " +
                "than the max size of the justification";

        Set<OWLAxiom> ontologyAxioms = new HashSet<>();
        GeneratorHelper helper = new GeneratorHelper();

        OWLClassExpression startLHS = tools.getLHS(this.axiom);
        OWLClassExpression endRHS = tools.getRHS(this.axiom);

        List<OWLAxiom> justification;
        Set<List<OWLAxiom>> justifications = new HashSet<>();

        System.out.println("Entailment = " + SimpleOWLFormatter.format(axiom) + ", total justifications = " + totalJustification);

        for(int i = 0; i < totalJustification; i++){
            justification = createJustification(startLHS,endRHS,justificationMaxSize,
                    justificationMaxCommonAxioms, helper, i==0);

            System.out.println(justification.stream().map(SimpleOWLFormatter::format).collect(Collectors.toList()));

            if(justifications.contains(justification))
                i--;
            justifications.add(justification);

            ontologyAxioms.addAll(justification);
        }

        try {
            createSaveOntology(ontologyAxioms, ontologyFileName, totalJustification, justificationMaxSize, justificationMaxCommonAxioms);
        } catch (IOException | OWLOntologyCreationException | OWLOntologyStorageException e) {
            throw new RuntimeException(e);
        }

        return ExitCode.terminatedSuccessfully;
    }

    private List<OWLAxiom> createJustification(OWLClassExpression startLHS, OWLClassExpression endRHS,
                                                               int justificationMaxSize, int justificationMaxCommonAxioms,
                                                               GeneratorHelper helper, boolean firstJustification) {

        int justificationSize = firstJustification? justificationMaxSize:Selector.getRandomInt(1, justificationMaxSize);

        //This work under the assumption that the smallest justification size is 1
        int justificationCommonAxiomsCount = justificationSize;
        while(justificationCommonAxiomsCount >= justificationSize)
            justificationCommonAxiomsCount = Selector.getRandomInt(0, justificationMaxCommonAxioms);

        System.out.println("Justification size = " + justificationSize + " common axioms count = " + justificationCommonAxiomsCount);

        List<OWLAxiom> currentCommonAxioms = helper.getCommonAxiomsList().size()>0 ?
                helper.getCommonAxiomsList().subList(0, justificationCommonAxiomsCount) : new LinkedList<>();

        List<OWLAxiom> justification = Lists.newLinkedList(currentCommonAxioms);

        OWLClassExpression currentLHS = justification.size() == 0?startLHS :
                tools.getRHS(currentCommonAxioms.get(currentCommonAxioms.size()-1));
        OWLClassExpression currentRHS;
        OWLAxiom newAxiom;

        int depth = 0;
        if(!firstJustification && !currentCommonAxioms.isEmpty())
            depth = helper.getDepthAt(currentCommonAxioms.get(currentCommonAxioms.size()-1));

        for(int i = justification.size(); i < justificationSize; i++){
            currentRHS = i == justificationSize-1? endRHS : getRandomConceptExpression();

            // To go back to depth 0 and have the appropriate LHS
            if(i == justificationSize - 1)
                currentLHS = getChainExistential(currentLHS, depth);

            //Add axiom
            newAxiom = tools.getOWLSubClassOfAxiom(currentLHS,currentRHS);
            justification.add(newAxiom);

            //Update depth
            depth = depth + getDepthDifference(currentLHS, currentRHS);

            //update currentLHS to the value of the next iteration
            Selection selection = Selector.selectRandomly();

            if (currentRHS.getClassExpressionType() == ClassExpressionType.OBJECT_SOME_VALUES_FROM &&
                    selection!=Selection.ExistentialRestriction) {

                currentLHS = ((OWLObjectSomeValuesFrom)currentRHS).getFiller();

            }else
                currentLHS = currentRHS;

            //update the helper
            if(firstJustification){
                helper.updateMap(newAxiom, depth);
                helper.addCommonAxiom(newAxiom);
            }
        }

        return justification;
    }

    private int getDepthDifference(OWLClassExpression currentLHS, OWLClassExpression currentRHS) {
        //We have only the following possibilities:
        //1- C <= C, and Er.C <= Er.c
        //2- C <= Er.C
        //3- Er.C <= C

        if(currentLHS instanceof OWLClass && currentRHS instanceof OWLObjectSomeValuesFrom)
            return 1;
        if(currentRHS instanceof OWLClass && currentLHS instanceof OWLObjectSomeValuesFrom)
            return -1;
        return 0;
    }

    private OWLClassExpression getChainExistential(OWLClassExpression concept, int depth) {
        //#concept can only be a concept name or an existential restriction of depth at most 1

        for(int i = concept instanceof OWLObjectSomeValuesFrom?1:0; i < depth; i++)
            concept = tools.getOWLExistentialRestriction(roleName,concept);
        return concept;
    }

    private OWLClassExpression getRandomConceptExpression() {
        Selection selection = Selector.selectRandomly();

        if(selection == Selection.ExistentialRestriction)
            return generateNextExistentialRestriction();
        return nameGenerator.getNextConceptName();
    }

    private OWLClassExpression generateNextExistentialRestriction() {
        return ToOWLTools.getInstance().getOWLExistentialRestriction(roleName, nameGenerator.getNextConceptName());
    }

    private void createSaveOntology(Set<OWLAxiom> axioms, String ontologyFilePath,
                                    int justificationsTotal, int justificationMaxSize, int justificationMaxCommonCount)
            throws IOException, OWLOntologyCreationException, OWLOntologyStorageException {

        OutputStream outputstream = Files.newOutputStream(new File(ontologyFilePath).toPath());

        OWLDocumentFormat ontologyFormat = new OWLXMLDocumentFormat();

        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();

        OWLOntology ontology = manager.createOntology();

        //Add all the axioms of the justifications
        manager.addAxioms(ontology,axioms);

        //Create a comment in the ontology with all the input data
        OWLAnnotationProperty comment =  manager.getOWLDataFactory().getOWLAnnotationProperty(IRI.create("rdfs" +
                ":comment"));
        OWLLiteral commentText =
                manager.getOWLDataFactory().getOWLLiteral(
                        "An ontology that entails" + SimpleOWLFormatter.format(axiom) +
                                ".\nJustifications total = " + justificationsTotal +
                                "; Max justification size = " + justificationMaxSize +
                                ", Max shared axioms = " + justificationMaxCommonCount);
        OWLAnnotation an = manager.getOWLDataFactory().getOWLAnnotation(comment, commentText);

        AnnotationChange c = new AddOntologyAnnotation(ontology, an);
        manager.applyChange(c);

        //Save the ontology
        manager.saveOntology(ontology, ontologyFormat, outputstream);
    }
}
