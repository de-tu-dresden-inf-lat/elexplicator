package de.tu_dresden.lat.Tests;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Objects;

import de.tu_dresden.inf.lat.model.interfaces.IModelGenerator;
import de.tu_dresden.inf.lat.model.interfaces.IProverGenerator;
import de.tu_dresden.inf.lat.model.tools.ToMetTools;
import de.tu_dresden.inf.lat.model.tools.ToOWLTools;
import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import com.google.common.collect.Sets;

import de.tu_dresden.lat.metTelCounterModel.MetTelModelGenerator;
import de.tu_dresden.lat.metTelCounterModel.MetTelProverGenerator;

/**
 * @author Christian Alrabbaa
 *
 */
public class ToMetTelTest {

	private static final OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
	private static final OWLDataFactory df = OWLManager.getOWLDataFactory();
	private static final ToOWLTools oWLTools = ToOWLTools.getInstance();
	private static final IModelGenerator metModelGenerator = MetTelModelGenerator.getInstance();
	private static final IProverGenerator metProverGenerator = MetTelProverGenerator.getInstance();
	private static final String ontologiesPath = System.getProperty("user.dir").substring(0,
			System.getProperty("user.dir").lastIndexOf(File.separator)) + File.separator + "Ontologies"
			+ File.separator;

	@Test
	public void test1() {

		OWLSubClassOfAxiom axiom = df.getOWLSubClassOfAxiom(df.getOWLClass(IRI.create("http://test#A")),
				df.getOWLObjectComplementOf(df.getOWLClass(IRI.create("http://test#B"))));

		OWLClassExpression lhs = axiom.getSubClass();
		OWLClassExpression rhs = axiom.getSuperClass();

		System.out.println("Class Name");
		String res = ToMetTools.getInstance().getMetClassExpression(lhs);
		System.out.println(res);

		assertEquals("A", res);

		System.out.println("Negated Class Name");
		res = ToMetTools.getInstance().getMetClassExpression(rhs);
		System.out.println(res);

		assertEquals("( ~ B )", res);
	}

	@Test
	public void test2() {

		OWLSubClassOfAxiom axiom = df.getOWLSubClassOfAxiom(
				df.getOWLObjectSomeValuesFrom(df.getOWLObjectProperty(IRI.create("http://test#r")),
						df.getOWLClass(IRI.create("http://test#A"))),
				df.getOWLObjectSomeValuesFrom(df.getOWLObjectInverseOf(df.getOWLObjectProperty(IRI.create("http" +
								"://test#s"))),
						df.getOWLThing()));

		OWLClassExpression lhs = axiom.getSubClass();
		OWLClassExpression rhs = axiom.getSuperClass();

		System.out.println("Exists r.A");
		String res = ToMetTools.getInstance().getMetClassExpression(lhs);
		System.out.println(res);

		assertEquals("( exists r.A )", res);

		System.out.println("Exists inverse s.Top");
		res = ToMetTools.getInstance().getMetClassExpression(rhs);
		System.out.println(res);

		assertEquals("( exists s-.true )", res);

		System.out.println("Forall inverse s.Bot)");
		res = ToMetTools.getInstance().getMetClassExpression(rhs.getComplementNNF());
		System.out.println(res);

		assertEquals("( forall s-.false )", res);
	}

	@Test
	public void test3() {

		OWLSubClassOfAxiom axiom = df.getOWLSubClassOfAxiom(
				df.getOWLObjectComplementOf(df.getOWLObjectSomeValuesFrom(df.getOWLObjectProperty(IRI.create("http://test#r")),
						df.getOWLClass(IRI.create("http://test#A")))),
				df.getOWLObjectSomeValuesFrom(df.getOWLObjectInverseOf(df.getOWLObjectProperty(IRI.create("http" +
								"://test#s"))), df.getOWLThing()));

		OWLClassExpression lhs = axiom.getSubClass();

		System.out.println("Not Exists r.A");
		String res = ToMetTools.getInstance().getMetClassExpression(lhs);
		System.out.println(res);

		assertEquals("( ~ ( exists r.A ) )", res);

		System.out.println("Forall r.~A");
		res = ToMetTools.getInstance().getMetClassExpression(lhs.getNNF());
		System.out.println(res);

		assertEquals("( forall r.( ~ A ) )", res);

		System.out.println("not exists r. A implies exists s-.Top");
		res = ToMetTools.getInstance().getMetImplication(axiom);
		System.out.println(res);

		assertEquals("( ( ~ ( exists r.A ) ) -> ( exists s-.true ) )", res);

	}

	@Test
	public void test4() {

		OWLObjectPropertyDomainAxiom dAxiom = df.getOWLObjectPropertyDomainAxiom(
				df.getOWLObjectProperty(IRI.create("http://test#r")),
				df.getOWLObjectSomeValuesFrom(df.getOWLObjectInverseOf(df.getOWLObjectProperty(IRI.create("http" +
								"://test#s"))),
						df.getOWLClass(IRI.create("http://test#A"))),
				Sets.newHashSet());

		OWLObjectPropertyRangeAxiom rAxiom = df.getOWLObjectPropertyRangeAxiom(df.getOWLObjectProperty(IRI.create(
				"http://test#m")),
				df.getOWLObjectComplementOf(df.getOWLClass(IRI.create("http://test#A"))), new HashSet<>());

		System.out.println("domain of r is (exists s-.A)");
		OWLSubClassOfAxiom subAxiom = (OWLSubClassOfAxiom) oWLTools.getAsSubClassOf(dAxiom).toArray()[0];
		String res = ToMetTools.getInstance().getMetImplication(subAxiom);
		System.out.println(res);

		assertEquals("( ( exists r.true ) -> ( exists s-.A ) )", res);

		System.out.println("range of m is not A");
		subAxiom = (OWLSubClassOfAxiom) oWLTools.getAsSubClassOf(rAxiom).toArray()[0];
		res = ToMetTools.getInstance().getMetImplication(subAxiom);
		System.out.println(res);

		assertEquals("( true -> ( forall m.( ~ A ) ) )", res);
	}

	@Test
	public void getConclusionAssertionTest() {

		// A <= B
		OWLAxiom axiom = df.getOWLSubClassOfAxiom(df.getOWLClass(IRI.create("http://test#A")),
				df.getOWLObjectComplementOf(df.getOWLClass(IRI.create("http://test#B"))));
		String assertionStr = metModelGenerator.getConclusionAsAssertion(axiom);
		System.out.println(assertionStr);

		assertEquals("@e A\n@e B\n", assertionStr);

		// Er.A <= As-.Bot
		axiom = df.getOWLSubClassOfAxiom(
				df.getOWLObjectSomeValuesFrom(df.getOWLObjectProperty(IRI.create("http://test#r")),
						df.getOWLClass(IRI.create("http://test#A"))),
				df.getOWLObjectSomeValuesFrom(df.getOWLObjectInverseOf(df.getOWLObjectProperty(IRI.create("http" +
								"://test#s"))),
						df.getOWLThing()));
		assertionStr = metModelGenerator.getConclusionAsAssertion(axiom);
		System.out.println(assertionStr);

		assertEquals("@e ( exists r.A )\n@e ( forall s-.false )\n", assertionStr);

		// Er.Top <= Es-.A
		axiom = df.getOWLObjectPropertyDomainAxiom(df.getOWLObjectProperty(IRI.create("http://test#r")),
				df.getOWLObjectSomeValuesFrom(df.getOWLObjectInverseOf(df.getOWLObjectProperty(IRI.create("http" +
								"://test#s"))),
						df.getOWLClass(IRI.create("http://test#A"))),
				Sets.newHashSet());
		assertionStr = metModelGenerator.getConclusionAsAssertion(axiom);
		System.out.println(assertionStr);

		assertEquals("@e ( exists r.true )\n@e ( forall s-.( ~ A ) )\n", assertionStr);

		// top <= Am.~A
		axiom = df.getOWLObjectPropertyRangeAxiom(df.getOWLObjectProperty(IRI.create("http://test#m")),
				df.getOWLObjectComplementOf(df.getOWLClass(IRI.create("http://test#A"))), new HashSet<>());
		assertionStr = metModelGenerator.getConclusionAsAssertion(axiom);
		System.out.println(assertionStr);

		assertEquals("@e true\n@e ( exists m.A )\n", assertionStr);
	}

	@Test
	public void translationTest() throws OWLOntologyCreationException, IOException {
		File file = new File(Objects.requireNonNull(getClass().getResource("/ontologies/pizza.owl")).getFile());
		// String prefix = "http://www.co-ode.org/ontologies/pizza/pizza.owl#";

		OWLOntology pizzaOnt = manager.loadOntologyFromOntologyDocument(file);

		Files.createDirectories(Paths.get("generatedCounterModels"));

		metProverGenerator.OWL2MetRules(pizzaOnt, pizzaOnt.getClassesInSignature().iterator().next(),
				new FileOutputStream("generatedCounterModels/OWLPizza_2_MeTPizza_Test"));
	}
}
