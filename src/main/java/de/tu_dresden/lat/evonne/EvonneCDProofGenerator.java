package de.tu_dresden.lat.evonne;

import de.tu_dresden.inf.lat.cd.CompositeProof;
import de.tu_dresden.inf.lat.cd.CompositeProofExtractor;
import de.tu_dresden.inf.lat.evee.concreteDomains.CD2Predicate;
import de.tu_dresden.inf.lat.evee.concreteDomains.CDConstraint;
import de.tu_dresden.inf.lat.evee.concreteDomains.LinearConstraint;
import de.tu_dresden.inf.lat.evee.proofs.data.exceptions.ProofGenerationException;
import de.tu_dresden.inf.lat.generators.CDGraphMLGenerator;
import de.tu_dresden.inf.lat.model.tools.ToOWLTools;
import de.tu_dresden.lat.Manager;
import de.tu_dresden.lat.data.CDProof;
import de.tu_dresden.lat.data.names.ConcreteDomainName;
import de.tu_dresden.lat.data.names.ReasonerName;
import org.apache.commons.math3.fraction.BigFraction;
import org.apache.log4j.Logger;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.IOException;
import java.util.Set;

/**
 * @author Christian Alrabbaa
 *
 */
public class EvonneCDProofGenerator {
    private static final Logger logger = Logger.getLogger(EvonneCDProofGenerator.class);
    private static final Manager manager = Manager.getInstance();

    private static final int proveTimeOut = 1;//minutes
    private static final int refuteTimeOut = 1;
    private static final int classifyTimeOut = 1;

    public static void getCDExplanation(String ontologyPathStr, String constraintsPathStr,
                                        ReasonerName reasonerName, ConcreteDomainName concreteDomainName,
                                        OWLAxiom axiom, String outDirStr, String outputFileNameStr)
            throws ProofGenerationException, IOException, ParserConfigurationException, TransformerException {
        logger.info("Initialising CD Manager with default values");
        // turn off verification and stats services
        manager.setGeneratePNG(true);
        manager.setEnableExplanationEvaluation(true);
        manager.setEnableCDReasoningStats(true);
        manager.setClassifySaturatedOntologyWithELK(true);

        // set timeouts
        manager.setProveTimeOut(proveTimeOut);
        manager.setRefuteTimeOut(refuteTimeOut);
        manager.setClassifyTimeOut(classifyTimeOut);
        manager.setForceShutdownAfterTimeout(true);

        logger.info("Setting CD Manager Input");
        // set output info
        manager.setOutputDirectoryName(outDirStr);
        manager.setOutputFileName(outputFileNameStr);

        // set input info
        File ontologyFile = new File(ontologyPathStr);
        manager.setOntologyFile(ontologyFile);
        manager.setConstraintsFile(new File(constraintsPathStr));
        manager.setReasonerName(reasonerName);
        manager.setConcreteDomainName(concreteDomainName);

        logger.info("CD explanation...");
        // generate Proof
        Set<OWLSubClassOfAxiom> input = ToOWLTools.getInstance().getAsSubClassOf(axiom);
        if(input.size()!=1)
            throw new ProofGenerationException("Could not parse the input axiom as an OWLSubClassOf axiom");

        OWLSubClassOfAxiom axiomSubClsOf = input.iterator().next();

        OWLClassExpression lhs = ToOWLTools.getInstance().getLHS(axiomSubClsOf);
        OWLClassExpression rhs = ToOWLTools.getInstance().getRHS(axiomSubClsOf);

        if(!(lhs instanceof OWLClass) || !(rhs instanceof OWLClass))
            throw new ProofGenerationException("Only concept names are allowed in an axiom");

        manager.explain(lhs.asOWLClass(), rhs.asOWLClass());

        CDProof cdp = manager.getLastComputedProof();

        OWLOntology ontology;
        try {
            ontology = OWLManager
                    .createOWLOntologyManager().loadOntologyFromOntologyDocument(ontologyFile);
        } catch (OWLOntologyCreationException e) {
            throw new RuntimeException(e);
        }

        createGML(cdp, concreteDomainName, ontology, outDirStr + File.separator + outputFileNameStr);
    }

    private static void createGML(CDProof cdp, ConcreteDomainName concreteDomainName, OWLOntology ontology,
                                  String filePath) throws ParserConfigurationException, TransformerException {
        CompositeProof<? extends CDConstraint> cp;

        if(concreteDomainName == ConcreteDomainName.QGreater){
            CompositeProofExtractor<CD2Predicate> cpe = new CompositeProofExtractor<>();
            cp = cpe.getCP(cdp.getQGProof(), ConcreteDomainName.QGreater.toString());
        }else{
            CompositeProofExtractor<LinearConstraint<BigFraction>> cpe = new CompositeProofExtractor<>();
            cp = cpe.getCP(cdp.getLCProof(), ConcreteDomainName.LinearConstraints.toString());
        }

        CDGraphMLGenerator.setOntology(ontology);
        CDGraphMLGenerator.drawProof(cp, filePath, true);
    }

}
