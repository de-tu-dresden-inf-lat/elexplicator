package de.tu_dresden.lat.Tests;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;

import org.junit.Before;
import org.junit.Test;
import org.semanticweb.HermiT.ReasonerFactory;
import org.semanticweb.elk.owlapi.ElkReasoner;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

import de.tu_dresden.inf.lat.prettyPrinting.formatting.SimpleOWLFormatter;

/**
 * @author Christian Alrabbaa
 *
 */
public class TestEntailmentsAndDiagnoses {
	private static final OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
	private static final OWLDataFactory factory = manager.getOWLDataFactory();

	private static OWLOntology ontology1;

	@Before
	public void init() throws OWLOntologyCreationException {

		File ontology1File = new File(
				Objects.requireNonNull(getClass().getResource("/ontologies/testEntailment.owl")).getFile());
		ontology1 = manager.loadOntologyFromOntologyDocument(ontology1File);
	}

	@Test
	public void test1() {
		ElkReasoner elk = (new ElkReasonerFactory()).createReasoner(ontology1);
		OWLReasoner hermit = (new ReasonerFactory()).createReasoner(ontology1);

		OWLClass a = factory.getOWLClass(IRI.create("http://testEntailment#A"));
		OWLClass b = factory.getOWLClass(IRI.create("http://testEntailment#B"));
		OWLClass c = factory.getOWLClass(IRI.create("http://testEntailment#C"));

		OWLObjectProperty r = factory.getOWLObjectProperty(IRI.create("http://testEntailment#r"));

		OWLAxiom axiom = factory.getOWLSubClassOfAxiom(a,
				factory.getOWLObjectSomeValuesFrom(r,
						factory.getOWLObjectIntersectionOf(new HashSet<>(Arrays.asList(b, c)))));

		System.out.println("Axiom -> " + SimpleOWLFormatter.format(axiom));

		System.out.println("ELK, is Entailed -> " + elk.isEntailed(axiom));
		System.out.println("Hermit, is Entailed -> " + hermit.isEntailed(axiom));

		assertFalse(elk.isEntailed(axiom));
		assertTrue(hermit.isEntailed(axiom));

	}

}
