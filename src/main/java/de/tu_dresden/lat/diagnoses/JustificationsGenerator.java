package de.tu_dresden.lat.diagnoses;

import java.util.HashSet;
import java.util.Set;

import org.apache.log4j.Logger;
import org.liveontologies.puli.DynamicProof;
import org.liveontologies.puli.Inference;
import org.liveontologies.puli.InferenceJustifier;
import org.liveontologies.puli.InferenceJustifiers;
import org.liveontologies.puli.pinpointing.InterruptMonitor;
import org.liveontologies.puli.pinpointing.MinimalSubsetCollector;
import org.liveontologies.puli.pinpointing.MinimalSubsetEnumerators;
import org.semanticweb.HermiT.ReasonerFactory;
import org.semanticweb.elk.owlapi.ElkReasoner;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.elk.owlapi.proofs.ElkOwlInference;
import org.semanticweb.elk.owlapi.proofs.ElkOwlProof;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;

import com.clarkparsia.owlapi.explanation.DefaultExplanationGenerator;
import com.clarkparsia.owlapi.explanation.util.SilentExplanationProgressMonitor;

/**
 * @author Christian Alrabbaa
 *
 */
public class JustificationsGenerator {

	private static final Logger logger = Logger.getLogger(JustificationsGenerator.class);

	/**
	 * Return a set of all justifications using ELK
	 * 
	 * @param axiom
	 * @param ontology
	 * @return
	 */
	public static Set<Set<? extends OWLAxiom>> getAllELKJustifications(OWLAxiom axiom, OWLOntology ontology) {
		ElkReasonerFactory reasonerFactory = new ElkReasonerFactory();
		ElkReasoner reasoner = reasonerFactory.createReasoner(ontology);

		DynamicProof<ElkOwlInference> proof = ElkOwlProof.create(reasoner, axiom);

		InferenceJustifier<Inference<OWLAxiom>, ? extends Set<? extends OWLAxiom>> justifier = InferenceJustifiers
				.justifyAssertedInferences();

		Set<Set<? extends OWLAxiom>> allJustifications = new HashSet<>();

		MinimalSubsetEnumerators.enumerateJustifications(axiom, proof, justifier, InterruptMonitor.DUMMY,
				new MinimalSubsetCollector<>(allJustifications));

		if (logger.isDebugEnabled())
			allJustifications.forEach(logger::debug);

		return allJustifications;
	}

	/**
	 * Return a set of all justifications using Hermit
	 * 
	 * 
	 * @param axiom
	 * @param ontology
	 * @return
	 */
	public static Set<Set<? extends OWLAxiom>> getAllHermitJustifications(OWLAxiom axiom, OWLOntology ontology) {
		OWLReasonerFactory factory = new ReasonerFactory();

		OWLReasoner reasoner = factory.createReasoner(ontology);

		DefaultExplanationGenerator explainer = new DefaultExplanationGenerator(OWLManager.createOWLOntologyManager(),
				factory, ontology, reasoner, new SilentExplanationProgressMonitor());

		Set<Set<? extends OWLAxiom>> allJustifications = new HashSet<>(explainer.getExplanations(axiom));

		if (logger.isDebugEnabled())
			allJustifications.forEach(logger::debug);

		return allJustifications;

	}
}
