package de.tu_dresden.lat.Tests;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLObjectAllValuesFrom;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;

import de.tu_dresden.lat.tools.AxiomChecker;

/**
 * @author Christian Alrabbaa
 *
 */

public class AxiomCheckerTest {

	@Test
	public void test1() {
		OWLDataFactory f = OWLManager.getOWLDataFactory();
		OWLClass a = f.getOWLClass(IRI.create("a"));
		OWLObjectAllValuesFrom all = f.getOWLObjectAllValuesFrom(f.getOWLObjectProperty(IRI.create("r")), a);

		OWLSubClassOfAxiom axiom1 = f.getOWLSubClassOfAxiom(f.getOWLNothing(), f.getOWLObjectIntersectionOf(a, all));
		OWLSubClassOfAxiom axiom2 = f.getOWLSubClassOfAxiom(f.getOWLNothing(), a);

		assertFalse(AxiomChecker.isInEL(axiom1));
		assertTrue(AxiomChecker.isInEL(axiom2));

		assertTrue(AxiomChecker.isInALC(axiom1));
		assertTrue(AxiomChecker.isInALC(axiom2));

	}

}
