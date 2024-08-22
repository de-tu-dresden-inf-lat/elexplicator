package de.tu_dresden.lat.Tests;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import de.tu_dresden.inf.lat.counterExample.ELKModelGenerator;
import de.tu_dresden.inf.lat.exceptions.EntityCheckerException;
import de.tu_dresden.inf.lat.generators.DotGraphGenerator;
import de.tu_dresden.inf.lat.model.data.Element;
import de.tu_dresden.inf.lat.model.tools.ToOWLTools;
import org.junit.Before;
import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import de.tu_dresden.inf.lat.evee.data.ProofType;
import de.tu_dresden.inf.lat.evee.proofs.data.exceptions.ProofGenerationFailedException;
import de.tu_dresden.lat.data.enums.OutputType;
import de.tu_dresden.lat.managers.MyELkProofManager;


/**
 * @author Christian Alrabbaa
 *
 */
public class SomeTests {

	private static OWLOntologyManager manager;
	private static OWLDataFactory factory;
	private static MyELkProofManager elkPM;

	@Before
	public void init() {
		manager = OWLManager.createOWLOntologyManager();
		factory = manager.getOWLDataFactory();
	}

	@Test
	public void noIceCream() throws OWLOntologyCreationException, IOException, ProofGenerationFailedException {
		File file = new File(Objects.requireNonNull(getClass().getResource("/ontologies/pizza.owl")).getFile());
		String prefix = "http://www.co-ode.org/ontologies/pizza/pizza.owl#";

		OWLOntology pizzaOnt = manager.loadOntologyFromOntologyDocument(file);

		OWLClass iceCream = factory.getOWLClass(IRI.create(prefix + "IceCream"));

		OWLAxiom alpha = factory.getOWLSubClassOfAxiom(iceCream, factory.getOWLNothing());

		elkPM = new MyELkProofManager(pizzaOnt);

		elkPM.getProofs(alpha, "proofIceCream", "", OutputType.RecursiveJSON,
				ProofType.TreeUnravellingOFMinimalSizeGraph, null, true, true);
	}

	@Test
	public void fruitIsVegetarian() throws OWLOntologyCreationException, IOException, ProofGenerationFailedException {
		File file = new File(Objects.requireNonNull(getClass().getResource("/ontologies/pizza.owl")).getFile());
		String prefix = "http://www.co-ode.org/ontologies/pizza/pizza.owl#";

		OWLOntology pizzaOnt = manager.loadOntologyFromOntologyDocument(file);

		OWLClass fruitTopping = factory.getOWLClass(IRI.create(prefix + "FruitTopping"));
		OWLClass vegetarianTopping = factory.getOWLClass(IRI.create(prefix + "VegetarianTopping"));

		OWLAxiom alpha = factory.getOWLSubClassOfAxiom(fruitTopping, vegetarianTopping);

		elkPM = new MyELkProofManager(pizzaOnt);

		elkPM.getProofs(alpha, "proofFruitVegetarian", "", OutputType.RecursiveJSON,
				ProofType.TreeUnravellingOFMinimalSizeGraph, null, true, true);
	}

	@Test
	public void subAC() throws OWLOntologyCreationException, IOException, ProofGenerationFailedException {

		File file = new File(Objects.requireNonNull(getClass().getResource("/ontologies/test.owl")).getPath());
		String prefix = "http://test#";

		OWLOntology ont = manager.loadOntologyFromOntologyDocument(file);

		OWLClass a = factory.getOWLClass(IRI.create(prefix + "A"));
		OWLClass c = factory.getOWLClass(IRI.create(prefix + "C"));

		OWLAxiom alpha = factory.getOWLSubClassOfAxiom(a, c);

		elkPM = new MyELkProofManager(ont);

		elkPM.getProofs(alpha, "proofAC", "", OutputType.RecursiveJSON, ProofType.TreeUnravellingOFMinimalSizeGraph,
				null, true, true);
	}

	@Test
	public void subisFunc() throws OWLOntologyCreationException, IOException, ProofGenerationFailedException {

		File file = new File(Objects.requireNonNull(getClass().getResource("/ontologies/test.owl")).getFile());
		String prefix = "http://www.co-ode.org/ontologies/galen#";

		OWLOntology ont = manager.loadOntologyFromOntologyDocument(file);

		OWLClass a = factory.getOWLClass(IRI.create(prefix + "AntiDiuresis"));
		OWLClass c = factory.getOWLClass(IRI.create(prefix + "C"));

		OWLAxiom alpha = factory.getOWLSubClassOfAxiom(a, c);

		elkPM = new MyELkProofManager(ont);

		elkPM.getProofs(alpha, "proofAC", "", OutputType.RecursiveJSON, ProofType.TreeUnravellingOFMinimalSizeGraph,
				null, true, true);
	}

	@Test
	public void testAxiomParser() throws OWLOntologyCreationException, EntityCheckerException {

		File file = new File(Objects.requireNonNull(getClass().getResource("/ontologies/test.owl")).getFile());
		OWLOntology ont = manager.loadOntologyFromOntologyDocument(file);
		ToOWLTools owlTools = ToOWLTools.getInstance();

		OWLAxiom a1 = owlTools.getOWLAxiomFromStr("<http://test#A> SubClassOf: <http://test#C>", ont);
		assertEquals("SubClassOf(<http://test#A> <http://test#C>)", a1.toString());

		file = new File(Objects.requireNonNull(getClass().getResource("/ontologies/pizza.owl")).getFile());
		ont = manager.loadOntologyFromOntologyDocument(file);

		OWLAxiom a2 = owlTools.getOWLAxiomFromStr("<http://www.co-ode.org/ontologies/pizza/pizza.owl#FruitTopping> " +
						"SubClassOf: <http://www.co-ode.org/ontologies/pizza/pizza.owl#VegetarianTopping>",
				ont);
		assertEquals( "SubClassOf(<http://www.co-ode.org/ontologies/pizza/pizza.owl#FruitTopping> <http://www.co-ode" +
						".org/ontologies/pizza/pizza.owl#VegetarianTopping>)",
				a2.toString());

		OWLAxiom a3 = owlTools.getOWLAxiomFromStr( "<http://www.co-ode.org/ontologies/pizza/pizza" +
						".owl#FourCheesesTopping> SubClassOf: <http://www.co-ode.org/ontologies/pizza/pizza.owl#hasSpiciness> some <http://www.co-ode.org/ontologies/pizza/pizza.owl#Mild>",
				ont);
		assertEquals( "SubClassOf(<http://www.co-ode.org/ontologies/pizza/pizza.owl#FourCheesesTopping> " +
						"ObjectSomeValuesFrom(<http://www.co-ode.org/ontologies/pizza/pizza.owl#hasSpiciness> <http://www.co-ode.org/ontologies/pizza/pizza.owl#Mild>))",
				a3.toString());

		OWLAxiom a4 = owlTools.getOWLAxiomFromStr( "<http://www.co-ode.org/ontologies/pizza/pizza" +
						".owl#FourCheesesTopping> SubClassOf: <http://www.co-ode.org/ontologies/pizza/pizza.owl#hasSpiciness> some owl:Thing",
				ont);
		assertEquals( "SubClassOf(<http://www.co-ode.org/ontologies/pizza/pizza.owl#FourCheesesTopping> " +
						"ObjectSomeValuesFrom(<http://www.co-ode.org/ontologies/pizza/pizza.owl#hasSpiciness> owl:Thing))",
				a4.toString());

		OWLAxiom a5 = owlTools.getOWLAxiomFromStr( "<http://www.co-ode.org/ontologies/pizza/pizza" +
						".owl#FourCheesesTopping> SubClassOf: inverse <http://www.co-ode.org/ontologies/pizza/pizza.owl#hasSpiciness> some owl:Thing",
				ont);
		assertEquals( "SubClassOf(<http://www.co-ode.org/ontologies/pizza/pizza.owl#FourCheesesTopping> " +
						"ObjectSomeValuesFrom(ObjectInverseOf(<http://www.co-ode.org/ontologies/pizza/pizza.owl#hasSpiciness>) owl:Thing))",
				a5.toString());
	}

	@Test
	public void example1() throws OWLOntologyCreationException, IOException {
		manager = OWLManager.createOWLOntologyManager();
		factory = manager.getOWLDataFactory();

		ELKModelGenerator model;
		OWLOntology ontology;

		File file =
				new File(Objects.requireNonNull(getClass().getResource("/ontologies/exampleOntology.owl")).getFile());

		ontology = manager.loadOntologyFromOntologyDocument(file);

		model = new ELKModelGenerator(ontology);

		Set<Element> fullCanonModel = model.generateFullRelevantCanonicalModel().getFinalizedModelElements();

		DotGraphGenerator.drawCounterModel(fullCanonModel, new HashSet<>(), new HashMap<>(), new File("out"));

		System.out.println("_-_-_-_-_-_-_-_-_-_");
	}

	@Test
	public void example2() throws OWLOntologyCreationException, IOException {
		manager = OWLManager.createOWLOntologyManager();
		factory = manager.getOWLDataFactory();

		ELKModelGenerator model;
		OWLOntology ontology;

		File file = new File(Objects.requireNonNull(getClass().getResource("/ontologies/exampleE1.owl")).getFile());

		ontology = manager.loadOntologyFromOntologyDocument(file);

		model = new ELKModelGenerator(ontology);

		Set<Element> fullCanonModel = model.generateFullRelevantCanonicalModel().getFinalizedModelElements();

		fullCanonModel.forEach(System.out::println);

		DotGraphGenerator.drawCounterModel(fullCanonModel, new HashSet<>(), new HashMap<>(), new File("out"));

		System.out.println("_-_-_-_-_-_-_-_-_-_");
	}

}
