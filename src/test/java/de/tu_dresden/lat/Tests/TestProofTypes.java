package de.tu_dresden.lat.Tests;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.Objects;

import com.google.common.collect.Sets;
import de.tu_dresden.inf.lat.model.tools.ToOWLTools;
import org.junit.Test;
import org.liveontologies.puli.DynamicProof;
import org.semanticweb.elk.owlapi.proofs.ElkOwlInference;
import org.semanticweb.elk.owlapi.proofs.ElkOwlProof;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import de.tu_dresden.inf.lat.evee.data.ProofType;
import de.tu_dresden.inf.lat.evee.proofGenerators.ELKProofGenerator;
import de.tu_dresden.inf.lat.evee.proofs.data.exceptions.ProofGenerationFailedException;
import de.tu_dresden.inf.lat.evee.proofs.interfaces.IProof;

/**
 * @author Christian Alrabbaa
 *
 */

public class TestProofTypes {

	private static final OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
	private static final OWLDataFactory factory = manager.getOWLDataFactory();
	private static OWLOntology ontology;

	@Test
	public void test1() throws OWLOntologyCreationException, ProofGenerationFailedException {
		ontology = manager.loadOntologyFromOntologyDocument(new File(Objects.requireNonNull(getClass().getResource(
				"/ontologies/simpleTest.owl")).getFile()));

		ELKProofGenerator g = new ELKProofGenerator(ontology);

		OWLClass a = factory.getOWLClass(IRI.create("http://simpleTest#A"));
		OWLClass d = factory.getOWLClass(IRI.create("http://simpleTest#D"));

		IProof<OWLAxiom> p = g.getTreeProof(factory.getOWLSubClassOfAxiom(a, d), ProofType.MinimalTreeSize, null);
		assertEquals(5, p.getInferences().size());

		p = g.getTreeProof(factory.getOWLSubClassOfAxiom(a, d), ProofType.CondensedMinimalTreeSize,
				ToOWLTools.getInstance().getOWLEntityFromFile(
						new File(Objects.requireNonNull(getClass().getResource("/sig.txt")).getFile()), ontology));
		assertEquals(3, p.getInferences().size());
	}

	@Test
	public void test2() throws ProofGenerationFailedException, OWLOntologyCreationException {
		ontology = manager.loadOntologyFromOntologyDocument(new File(Objects.requireNonNull(getClass().getResource(
				"/ontologies/equivalence.owl")).getFile()));

		ELKProofGenerator g = new ELKProofGenerator(ontology);

		OWLClass a = factory.getOWLClass(IRI.create("http://equivalence#A"));
		OWLClass b = factory.getOWLClass(IRI.create("http://equivalence#B"));
		OWLClass c = factory.getOWLClass(IRI.create("http://equivalence#C"));
		OWLClass d = factory.getOWLClass(IRI.create("http://equivalence#D"));

		OWLObjectSomeValuesFrom lhs = factory.getOWLObjectSomeValuesFrom(
				factory.getOWLObjectProperty(IRI.create("http://equivalence#r")),
				factory.getOWLObjectIntersectionOf(Sets.newHashSet(a, b)));

		OWLObjectIntersectionOf rhs = factory.getOWLObjectIntersectionOf(Sets.newHashSet(
				factory.getOWLObjectSomeValuesFrom(factory.getOWLObjectProperty(IRI.create("http://equivalence#r")), c),
				factory.getOWLObjectSomeValuesFrom(factory.getOWLObjectProperty(IRI.create("http://equivalence#r")),
						d)));

		OWLAxiom axiom = factory.getOWLSubClassOfAxiom(lhs, rhs);

		DynamicProof<ElkOwlInference> DerivationStructure = ElkOwlProof.create(g.getReasoner(), axiom);

		IProof<OWLAxiom> p = g.getTreeProof(axiom, ProofType.MinimalTreeSize, null);
		assertEquals(11, p.getInferences().size());

		p = g.getTreeProof(axiom, ProofType.TreeUnravellingOfMinimalSizeGraph, null);
		assertEquals(11, p.getInferences().size());

		g.resetExploredAxiomsSet();
		p = g.getMinimalHyperProof(axiom, DerivationStructure);
		assertEquals(9, p.getInferences().size());
	}
}
