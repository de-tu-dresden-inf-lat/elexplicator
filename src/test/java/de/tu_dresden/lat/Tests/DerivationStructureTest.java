package de.tu_dresden.lat.Tests;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import com.google.common.collect.Sets;
import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import de.tu_dresden.inf.lat.evee.proofGenerators.ELKProofGenerator;
import de.tu_dresden.inf.lat.evee.proofs.data.exceptions.ProofGenerationFailedException;
import de.tu_dresden.inf.lat.evee.proofs.interfaces.IProof;
import de.tu_dresden.inf.lat.evee.proofs.json.JsonProofWriter;
import de.tu_dresden.inf.lat.evee.proofs.proofGenerators.TreeProofGenerator;

/**
 * @author Christian Alrabbaa
 *
 */

public class DerivationStructureTest {

	private static final OWLDataFactory factory = OWLManager.createOWLOntologyManager().getOWLDataFactory();

	@Test
	public void test() throws OWLOntologyCreationException, IOException, ProofGenerationFailedException {
		File ontologyFile =
				new File(Objects.requireNonNull(getClass().getResource("/ontologies/test.owl")).getFile());

		OWLOntology ontology = OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(ontologyFile);

		ELKProofGenerator g = new ELKProofGenerator(ontology);

		OWLClass a = factory.getOWLClass(IRI.create("http://test#A"));
		OWLClass c = factory.getOWLClass(IRI.create("http://test#C"));

		IProof<OWLAxiom> p = g.getProof(factory.getOWLSubClassOfAxiom(a, c));

		new JsonProofWriter<OWLAxiom>().writeToFile(p, "derivationStructureTest.json");
	}

	@Test
	public void test2() throws OWLOntologyCreationException, IOException, ProofGenerationFailedException {
		File ontologyFile =
				new File(Objects.requireNonNull(getClass().getResource("/ontologies/equivalence.owl")).getFile());

		OWLOntology ontology = OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(ontologyFile);

		ELKProofGenerator g = new ELKProofGenerator(ontology);

		OWLClass a = factory.getOWLClass(IRI.create("http://equivalence#A"));
		OWLClass b = factory.getOWLClass(IRI.create("http://equivalence#B"));
		OWLClass c = factory.getOWLClass(IRI.create("http://equivalence#C"));
		OWLClass d = factory.getOWLClass(IRI.create("http://equivalence#D"));

		OWLObjectProperty r = factory.getOWLObjectProperty(IRI.create("http://equivalence#r"));

		OWLClassExpression aAndB = factory.getOWLObjectIntersectionOf(Sets.newHashSet(a, b));
		OWLClassExpression erAAndB = factory.getOWLObjectSomeValuesFrom(r, aAndB);

		OWLClassExpression erC = factory.getOWLObjectSomeValuesFrom(r, c);
		OWLClassExpression erD = factory.getOWLObjectSomeValuesFrom(r, d);
		OWLClassExpression erCAnderD = factory.getOWLObjectIntersectionOf(Sets.newHashSet(erC, erD));

		IProof<OWLAxiom> p = g.getProof(factory.getOWLSubClassOfAxiom(erAAndB, erCAnderD));

		// Test the tree generation functionality

		new JsonProofWriter<OWLAxiom>().writeToFile(p, "ds.json");
		new JsonProofWriter<OWLAxiom>().writeToFile( TreeProofGenerator.getTreeUnravelOfMinHypProof(p),
				"treeOfMinimal.json");
	}

}
