package de.tu_dresden.lat.Tests;

import java.io.File;
import java.util.Objects;

import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;

/**
 * @author Christian Alrabbaa
 *
 */
public class TestAnnotations {

	@Test
	public void test() throws OWLOntologyCreationException {
		String path = Objects.requireNonNull(getClass().getResource("/ontologies/cellOntology.owl")).getFile();
		System.out.println(path);
		OWLOntology ontology = OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(new File(path));
		ontology.getAxioms().forEach(axiom -> {
			axiom.getAnnotations().forEach(a -> {
				System.out.println(a.getClass().getName());
				System.out.println(a.getValue().toString().substring(1, a.getValue().toString().lastIndexOf("\"")));
			});
		});
	}

}
