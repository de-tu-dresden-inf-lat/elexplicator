package de.tu_dresden.lat.Tests;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;

import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import de.tu_dresden.inf.lat.evee.data.RecursiveInference;
import de.tu_dresden.inf.lat.evee.data.RecursiveProof;
import de.tu_dresden.inf.lat.evee.proofs.json.JsonProofWriter;

/**
 * @author Christian Alrabbaa
 *
 */
public class ProofWriterTest {

	private static final OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
	private static final OWLDataFactory factory = manager.getOWLDataFactory();

	@Test
	public void JSonProofWriterTest1() throws IOException {

		OWLClass a = factory.getOWLClass(IRI.create("A"));
		OWLClass b = factory.getOWLClass(IRI.create("B"));
		OWLClass c = factory.getOWLClass(IRI.create("C"));
		OWLClass d = factory.getOWLClass(IRI.create("D"));

		OWLObjectProperty r = factory.getOWLObjectProperty(IRI.create("r"));

		OWLObjectSomeValuesFrom existsRB = factory.getOWLObjectSomeValuesFrom(r, b);
		OWLObjectSomeValuesFrom existsRC = factory.getOWLObjectSomeValuesFrom(r, c);

		RecursiveInference<OWLAxiom> prem1 = new RecursiveInference<>(
				factory.getOWLSubClassOfAxiom(a, existsRB), "Asserted", new LinkedList<>());
		RecursiveInference<OWLAxiom> prem2 = new RecursiveInference<>(factory.getOWLSubClassOfAxiom(b, c),
				"Asserted", new LinkedList<>());
		RecursiveInference<OWLAxiom> prem3 = new RecursiveInference<>(factory.getOWLSubClassOfAxiom(d, a),
				"Asserted", new LinkedList<>());

		OWLAxiom con1 = factory.getOWLSubClassOfAxiom(a, existsRC);
		OWLAxiom con2 = factory.getOWLSubClassOfAxiom(d, existsRC);

		RecursiveInference<OWLAxiom> inner = new RecursiveInference<>(con1, "rule 1",
				Arrays.asList(prem1, prem2));
		RecursiveInference<OWLAxiom> outer = new RecursiveInference<>(con2, "rule 2",
				Arrays.asList(prem3, inner));

		RecursiveProof<OWLAxiom> p = new RecursiveProof<>(con2);
		p.addInference(outer);

		JsonProofWriter<OWLAxiom> writer = new JsonProofWriter<>();

		String res = "testProofJson";
		File file = new File(res + ".json");
		writer.writeToFile(Collections.singletonList(p), res);

		assertEquals(575, (file).length());

	}
}
