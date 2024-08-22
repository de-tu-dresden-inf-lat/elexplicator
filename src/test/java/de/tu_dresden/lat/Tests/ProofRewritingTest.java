package de.tu_dresden.lat.Tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import de.tu_dresden.inf.lat.evee.data.ProofType;
import de.tu_dresden.inf.lat.evee.proofGenerators.ELKProofGenerator;
import de.tu_dresden.inf.lat.evee.proofs.data.exceptions.ProofGenerationFailedException;
import de.tu_dresden.inf.lat.generators.DotGraphGenerator;
import de.tu_dresden.inf.lat.generators.GraphMLGenerator;
import de.tu_dresden.inf.lat.model.tools.ToOWLTools;
import de.tu_dresden.inf.lat.prettyPrinting.formatting.SimpleOWLFormatter;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import de.tu_dresden.inf.lat.evee.proofs.data.Inference;
import de.tu_dresden.inf.lat.evee.proofs.data.Proof;
import de.tu_dresden.inf.lat.evee.proofs.interfaces.IInference;
import de.tu_dresden.inf.lat.evee.proofs.interfaces.IProof;
import de.tu_dresden.inf.lat.evee.proofs.json.JsonProofWriter;
import de.tu_dresden.lat.data.ProofPattern;
import de.tu_dresden.lat.data.ProofTemplate;
import de.tu_dresden.lat.exceptions.ProofRwritingException;
import de.tu_dresden.lat.proofRewriting.PatternInstancesFinder;
import de.tu_dresden.lat.proofRewriting.ProofRewriter;
import de.tu_dresden.lat.proofRewriting.ProofSlicer;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

/**
 * @author Christian Alrabbaa
 *
 */

public class ProofRewritingTest {
	private static final Logger logger = Logger.getLogger(ProofRewritingTest.class);
	private static final ToOWLTools tools = ToOWLTools.getInstance();

	// To use in the proofs
	private static final OWLSubClassOfAxiom a1 = tools.getOWLSubClassOfAxiom(tools.getOWLConceptName("C1"),
			tools.getOWLConceptName("D1"));
	private static final OWLSubClassOfAxiom a2 = tools.getOWLSubClassOfAxiom(tools.getOWLConceptName("C2"),
			tools.getOWLConceptName("D2"));
	private static final OWLSubClassOfAxiom a3 = tools.getOWLSubClassOfAxiom(tools.getOWLConceptName("C3"),
			tools.getOWLConceptName("D3"));
	private static final OWLSubClassOfAxiom a4 = tools.getOWLSubClassOfAxiom(tools.getOWLConceptName("C4"),
			tools.getOWLConceptName("D4"));
	private static final OWLSubClassOfAxiom a5 = tools.getOWLSubClassOfAxiom(tools.getOWLConceptName("C5"),
			tools.getOWLConceptName("D5"));
	private static final OWLSubClassOfAxiom a6 = tools.getOWLSubClassOfAxiom(tools.getOWLConceptName("C6"),
			tools.getOWLConceptName("D6"));
	private static final OWLSubClassOfAxiom a7 = tools.getOWLSubClassOfAxiom(tools.getOWLConceptName("C7"),
			tools.getOWLConceptName("D7"));
	private static final OWLSubClassOfAxiom a8 = tools.getOWLSubClassOfAxiom(tools.getOWLConceptName("C8"),
			tools.getOWLConceptName("D8"));
	private static final OWLSubClassOfAxiom a9 = tools.getOWLSubClassOfAxiom(tools.getOWLConceptName("C9"),
			tools.getOWLConceptName("D9"));

	// To use in describing the pattern as a proof
	private static final OWLSubClassOfAxiom b1 = tools.getOWLSubClassOfAxiom(tools.getOWLConceptName("LHS1"),
			tools.getOWLConceptName("RHS1"));
	private static final OWLSubClassOfAxiom b2 = tools.getOWLSubClassOfAxiom(tools.getOWLConceptName("LHS2"),
			tools.getOWLConceptName("RHS2"));
	private static final OWLSubClassOfAxiom b3 = tools.getOWLSubClassOfAxiom(tools.getOWLConceptName("LHS3"),
			tools.getOWLConceptName("RHS3"));
	private static final OWLSubClassOfAxiom b4 = tools.getOWLSubClassOfAxiom(tools.getOWLConceptName("LHS4"),
			tools.getOWLConceptName("RHS4"));
	private static final OWLSubClassOfAxiom b5 = tools.getOWLSubClassOfAxiom(tools.getOWLConceptName("LHS5"),
			tools.getOWLConceptName("RHS5"));
	private static final OWLSubClassOfAxiom b6 = tools.getOWLSubClassOfAxiom(tools.getOWLConceptName("LHS6"),
			tools.getOWLConceptName("RHS6"));
	private static final OWLSubClassOfAxiom b7 = tools.getOWLSubClassOfAxiom(tools.getOWLConceptName("LHS7"),
			tools.getOWLConceptName("RHS7"));

	//for DotGraphGenerator
	Map<OWLAxiom, String> axiomMAPStr = new HashMap<>();

	@Before
	public void drawRewritingResult() {
		logger.setLevel(Level.INFO);
		// Uncomment to generate PNGs of the rewritings
		logger.setLevel(Level.DEBUG);

		axiomMAPStr.put(a1, SimpleOWLFormatter.format(a1));
		axiomMAPStr.put(a2, SimpleOWLFormatter.format(a2));
		axiomMAPStr.put(a3, SimpleOWLFormatter.format(a3));
		axiomMAPStr.put(a4, SimpleOWLFormatter.format(a4));
		axiomMAPStr.put(a5, SimpleOWLFormatter.format(a5));
		axiomMAPStr.put(a6, SimpleOWLFormatter.format(a6));
		axiomMAPStr.put(a7, SimpleOWLFormatter.format(a7));
		axiomMAPStr.put(a8, SimpleOWLFormatter.format(a8));
		axiomMAPStr.put(a9, SimpleOWLFormatter.format(a9));

		axiomMAPStr.put(b1, SimpleOWLFormatter.format(b1));
		axiomMAPStr.put(b2, SimpleOWLFormatter.format(b2));
		axiomMAPStr.put(b3, SimpleOWLFormatter.format(b3));
		axiomMAPStr.put(b4, SimpleOWLFormatter.format(b4));
		axiomMAPStr.put(b5, SimpleOWLFormatter.format(b5));
		axiomMAPStr.put(b6, SimpleOWLFormatter.format(b6));
		axiomMAPStr.put(b7, SimpleOWLFormatter.format(b7));
	}

	@Test
	public void test0() {
		logger.debug("Test 0");

		// Example 0
		IInference<OWLAxiom> inf1 = new Inference<>(a1, "R1", Arrays.asList(a2, a4));
		IInference<OWLAxiom> inf2 = new Inference<>(a2, "R2", Arrays.asList(a3));
		IInference<OWLAxiom> inf3 = new Inference<>(a3, "R3", Collections.emptyList());

		IProof<OWLAxiom> proof0 = new Proof<>(a1);
		proof0.addInferences(Arrays.asList(inf1, inf2, inf3));

		// Pattern0
		IInference<OWLAxiom> patternInf1 = new Inference<>(b1, "R1", Arrays.asList(b2, b3));
		IInference<OWLAxiom> patternInf2 = new Inference<>(b2, "R2", Arrays.asList(b4));
		IInference<OWLAxiom> patternInf3 = new Inference<>(b3, "R2", Arrays.asList(b5));
		IInference<OWLAxiom> patternInf4 = new Inference<>(b4, "R3", Collections.emptyList());
		IInference<OWLAxiom> patternInf5 = new Inference<>(b5, "R3", Collections.emptyList());
		IProof<OWLAxiom> p0 = new Proof<>(b1);
		p0.addInferences(Arrays.asList(patternInf1, patternInf2, patternInf3, patternInf4, patternInf5));

		// Matching test
		ProofPattern<OWLAxiom> pattern0 = new ProofPattern<>(p0);
		String rootRuleName = pattern0.getId2RuleName()
				.get(pattern0.getLevel2RulesIDsInLevel().get(0).iterator().next());

		assertEquals(5, pattern0.getRulesIDsInAllLevel().size());
		assertEquals(2, pattern0.getLevel2RulesIDsInLevel().get(1).size());

		ProofTemplate<OWLAxiom> template;

		for (IProof<OWLAxiom> p : (new ProofSlicer<>(proof0)).slice(rootRuleName, pattern0.getMaxLevel())) {
			template = new ProofTemplate<>(pattern0, p);

			PatternInstancesFinder<OWLAxiom> e = new PatternInstancesFinder<>(template);
			Set<IInference<OWLAxiom>> firstMatch = e.first();

			assertTrue(firstMatch.isEmpty());
		}

		logger.debug("-----");
	}

	@Test
	public void test0Prime() {
		logger.debug("Test 0'");

		// Example 0'
		IInference<OWLAxiom> inf1 = new Inference<>(a1, "R1", Arrays.asList(a2, a3, a4));
		IInference<OWLAxiom> inf2 = new Inference<>(a2, "R2", Arrays.asList(a4));
		IInference<OWLAxiom> inf3 = new Inference<>(a3, "R2", Arrays.asList(a4));
		IInference<OWLAxiom> inf4 = new Inference<>(a4, "R3", Collections.emptyList());

		IProof<OWLAxiom> proof0Prime = new Proof<>(a1);
		proof0Prime.addInferences(Arrays.asList(inf1, inf2, inf3, inf4));

		// Pattern1
		IInference<OWLAxiom> patternInf1 = new Inference<>(b1, "R1", Arrays.asList(b2, b3, b4));
		IInference<OWLAxiom> patternInf2 = new Inference<>(b2, "R2", Arrays.asList(b4));
		IInference<OWLAxiom> patternInf3 = new Inference<>(b3, "R2", Arrays.asList(b4));
		IInference<OWLAxiom> patternInf4 = new Inference<>(b4, "R3", Collections.emptyList());
		IProof<OWLAxiom> p0Prime = new Proof<>(b1);
		p0Prime.addInferences(Arrays.asList(patternInf1, patternInf2, patternInf3, patternInf4));

		// Matching test
		ProofPattern<OWLAxiom> pattern0Prime = new ProofPattern<>(p0Prime);
		String rootRuleName = pattern0Prime.getId2RuleName()
				.get(pattern0Prime.getLevel2RulesIDsInLevel().get(0).iterator().next());

		assertEquals(4, pattern0Prime.getRulesIDsInAllLevel().size());
		assertEquals(3, pattern0Prime.getRulesIDsInAllLevel().stream().map(pattern0Prime.getId2RuleName()::get)
				.collect(Collectors.toSet()).size());
		assertEquals(3, pattern0Prime.getLevel2RulesIDsInLevel().get(1).size());

		ProofTemplate<OWLAxiom> template;

		for (IProof<OWLAxiom> p : (new ProofSlicer<>(proof0Prime)).slice(rootRuleName,
				pattern0Prime.getMaxLevel())) {
			template = new ProofTemplate<>(pattern0Prime, p);

			PatternInstancesFinder<OWLAxiom> e = new PatternInstancesFinder<>(template);
			Set<IInference<OWLAxiom>> firstMatch = e.first();

			assertEquals(4, firstMatch.size());
		}

		logger.debug("-----");
	}

	@Test
	public void test1() {
		logger.debug("Test 1");

		// Example 1
		IInference<OWLAxiom> inf1 = new Inference<>(a5, "R1", Arrays.asList(a4, a3));
		IInference<OWLAxiom> inf2 = new Inference<>(a4, "R1", Arrays.asList(a1, a7));
		IInference<OWLAxiom> inf3 = new Inference<>(a3, "R1", Arrays.asList(a2, a6));
		IInference<OWLAxiom> inf4 = new Inference<>(a2, "R2", Collections.emptyList());

		IProof<OWLAxiom> proof1 = new Proof<>(a5);
		proof1.addInferences(Arrays.asList(inf1, inf2, inf3, inf4));

		// Pattern1
		IProof<OWLAxiom> p1 = new Proof<>(b1);
		p1.addInference(new Inference<>(b1, "R1", Arrays.asList(b2, b3)));

		// Matching test
		ProofPattern<OWLAxiom> pattern1 = new ProofPattern<>(p1);
		String rootRuleName = pattern1.getId2RuleName()
				.get(pattern1.getLevel2RulesIDsInLevel().get(0).iterator().next());

		assertEquals(1, pattern1.getRulesIDsInAllLevel().size());
		assertEquals(1, pattern1.getLevel2RulesIDsInLevel().get(0).size());

		ProofTemplate<OWLAxiom> template;

		for (IProof<OWLAxiom> p : (new ProofSlicer<>(proof1)).slice(rootRuleName, pattern1.getMaxLevel())) {
			template = new ProofTemplate<>(pattern1, p);

			PatternInstancesFinder<OWLAxiom> e = new PatternInstancesFinder<>(template);
			Set<IInference<OWLAxiom>> firstMatch = e.first();

			assertEquals(1, firstMatch.size());
		}

		// Should not be matched
		IProof<OWLAxiom> p2 = new Proof<>(b1);
		p2.addInference(new Inference<>(b1, "R2", Arrays.asList(b2, b3)));

		ProofPattern<OWLAxiom> pattern2 = new ProofPattern<>(p2);
		rootRuleName = pattern1.getId2RuleName().get(pattern1.getLevel2RulesIDsInLevel().get(0).iterator().next());

		for (IProof<OWLAxiom> p : (new ProofSlicer<>(proof1)).slice(rootRuleName, pattern2.getMaxLevel())) {
			template = new ProofTemplate<>(pattern2, p);

			assertTrue((new PatternInstancesFinder<>(template)).first().isEmpty());
		}

		logger.debug("-----");
	}

	@Test
	public void test2() {
		logger.debug("Test 2");

		// Example 2
		IInference<OWLAxiom> inf1 = new Inference<>(a1, "R1", Arrays.asList(a2, a3, a4));
		IInference<OWLAxiom> inf2 = new Inference<>(a2, "R2", Arrays.asList(a8));
		IInference<OWLAxiom> inf3 = new Inference<>(a8, "R5", Collections.emptyList());

		IInference<OWLAxiom> inf4 = new Inference<>(a3, "R3", Arrays.asList(a5, a6));
		IInference<OWLAxiom> inf5 = new Inference<>(a5, "R6", Collections.emptyList());
		IInference<OWLAxiom> inf6 = new Inference<>(a6, "R4", Arrays.asList(a7));

		IInference<OWLAxiom> inf7 = new Inference<>(a4, "R2", Arrays.asList(a9));
		IInference<OWLAxiom> inf8 = new Inference<>(a9, "R6", Collections.emptyList());

		IProof<OWLAxiom> proof2 = new Proof<>(a1);
		proof2.addInferences(Arrays.asList(inf1, inf2, inf3, inf4, inf5, inf6, inf7, inf8));

		// Pattern2
		IInference<OWLAxiom> patternInf1 = new Inference<>(b1, "R1", Arrays.asList(b2, b3, b4));
		IInference<OWLAxiom> patternInf2 = new Inference<>(b2, "R2", Arrays.asList(b5));
		IInference<OWLAxiom> patternInf3 = new Inference<>(b5, "R6", Collections.emptyList());
		IInference<OWLAxiom> patternInf4 = new Inference<>(b3, "R3", Arrays.asList(b6, b7));
		IInference<OWLAxiom> patternInf5 = new Inference<>(b6, "R6", Collections.emptyList());

		IProof<OWLAxiom> p2 = new Proof<OWLAxiom>(b1);
		p2.addInferences(Arrays.asList(patternInf1, patternInf2, patternInf3, patternInf4, patternInf5));

		// Matching test
		ProofPattern<OWLAxiom> pattern2 = new ProofPattern<>(p2);

		String rootRuleName = pattern2.getId2RuleName()
				.get(pattern2.getLevel2RulesIDsInLevel().get(0).iterator().next());

		assertEquals(5, pattern2.getRulesIDsInAllLevel().size());
		assertEquals(1, pattern2.getLevel2RulesIDsInLevel().get(0).size());
		assertEquals(2, pattern2.getLevel2RulesIDsInLevel().get(1).size());
		assertEquals(2, pattern2.getLevel2RulesIDsInLevel().get(2).size());

		ProofTemplate<OWLAxiom> template;

		for (IProof<OWLAxiom> p : (new ProofSlicer<>(proof2)).slice(rootRuleName, pattern2.getMaxLevel())) {

			template = new ProofTemplate<>(pattern2, p);

			PatternInstancesFinder<OWLAxiom> e = new PatternInstancesFinder<>(template);
			Set<IInference<OWLAxiom>> firstMatch = e.first();

			assertEquals(5, firstMatch.size());
		}

		logger.debug("-----");
	}

	@Test
	public void test2Prime() {
		logger.debug("Test 2'");

		// Example 2
		IInference<OWLAxiom> inf1 = new Inference<>(a1, "R1", Arrays.asList(a2, a3, a4));
		IInference<OWLAxiom> inf2 = new Inference<>(a2, "R2", Arrays.asList(a8));
		IInference<OWLAxiom> inf3 = new Inference<>(a8, "R5", Collections.emptyList());

		IInference<OWLAxiom> inf4 = new Inference<>(a3, "R3", Arrays.asList(a5, a6));
		IInference<OWLAxiom> inf5 = new Inference<>(a5, "R6", Collections.emptyList());
		IInference<OWLAxiom> inf6 = new Inference<>(a6, "R4", Arrays.asList(a7));

		IInference<OWLAxiom> inf7 = new Inference<>(a4, "R2", Arrays.asList(a9));
		IInference<OWLAxiom> inf8 = new Inference<>(a9, "R6", Collections.emptyList());

		IProof<OWLAxiom> proof2 = new Proof<>(a1);
		proof2.addInferences(Arrays.asList(inf1, inf2, inf3, inf4, inf5, inf6, inf7, inf8));

		// Pattern2
		IInference<OWLAxiom> patternInf1 = new Inference<>(b1, "R1", Arrays.asList(b2, b3, b4));
		IInference<OWLAxiom> patternInf2 = new Inference<>(b2, "R2", Arrays.asList(b5));

		IProof<OWLAxiom> p2 = new Proof<>(b1);
		p2.addInferences(Arrays.asList(patternInf1, patternInf2));

		// Matching test
		ProofPattern<OWLAxiom> pattern2 = new ProofPattern<>(p2);

		String rootRuleName = pattern2.getId2RuleName()
				.get(pattern2.getLevel2RulesIDsInLevel().get(0).iterator().next());

		assertEquals(2, pattern2.getRulesIDsInAllLevel().size());
		assertEquals(1, pattern2.getLevel2RulesIDsInLevel().get(0).size());
		assertEquals(1, pattern2.getLevel2RulesIDsInLevel().get(1).size());

		ProofTemplate<OWLAxiom> template;

		for (IProof<OWLAxiom> p : (new ProofSlicer<>(proof2)).slice(rootRuleName, pattern2.getMaxLevel())) {
			template = new ProofTemplate<>(pattern2, p);

			PatternInstancesFinder<OWLAxiom> finder = new PatternInstancesFinder<>(template);

			assertEquals(2, finder.first().size());
		}

		logger.debug("-----");
	}

	@Test
	public void test3() {
		logger.debug("Test 3");

		// Example 3
		IInference<OWLAxiom> inf1 = new Inference<>(a7, "R1", Arrays.asList(a5, a6));
		IInference<OWLAxiom> inf2 = new Inference<>(a6, "R1", Arrays.asList(a8, a9));
		IInference<OWLAxiom> inf3 = new Inference<>(a9, "R2", Collections.emptyList());
		IInference<OWLAxiom> inf4 = new Inference<>(a8, "Asserted", Collections.emptyList());

		IInference<OWLAxiom> inf5 = new Inference<>(a5, "R1", Arrays.asList(a3, a4));
		IInference<OWLAxiom> inf6 = new Inference<>(a4, "R2", Collections.emptyList());
		IInference<OWLAxiom> inf7 = new Inference<>(a3, "R1", Arrays.asList(a1, a2));
		IInference<OWLAxiom> inf8 = new Inference<>(a2, "R2", Collections.emptyList());
		IInference<OWLAxiom> inf9 = new Inference<>(a1, "Asserted", Collections.emptyList());

		IProof<OWLAxiom> proof3 = new Proof<>(a7);
		proof3.addInferences(Arrays.asList(inf1, inf2, inf3, inf4, inf5, inf6, inf7, inf8, inf9));

		// Pattern3
		IInference<OWLAxiom> patternInf1 = new Inference<>(b1, "R1", Arrays.asList(b2, b3));
		IInference<OWLAxiom> patternInf2 = new Inference<>(b2, "Asserted", Collections.emptyList());
		IInference<OWLAxiom> patternInf3 = new Inference<>(b3, "R2", Collections.emptyList());

		IProof<OWLAxiom> p3 = new Proof<>(b1);
		p3.addInferences(Arrays.asList(patternInf1, patternInf2, patternInf3));

		// Matching test
		ProofPattern<OWLAxiom> pattern3 = new ProofPattern<>(p3);

		assertEquals(3, pattern3.getRulesIDsInAllLevel().size());
		assertEquals(1, pattern3.getLevel2RulesIDsInLevel().get(0).size());
		assertEquals(2, pattern3.getLevel2RulesIDsInLevel().get(1).size());

		String rootRuleName = pattern3.getId2RuleName()
				.get(pattern3.getLevel2RulesIDsInLevel().get(0).iterator().next());
		ProofTemplate<OWLAxiom> template;

		int possibleMatches = 0, actualMatches = 0;
		for (IProof<OWLAxiom> p : (new ProofSlicer<>(proof3)).slice(rootRuleName, pattern3.getMaxLevel())) {
			possibleMatches++;

			template = new ProofTemplate<>(pattern3, p);

			PatternInstancesFinder<OWLAxiom> e = new PatternInstancesFinder<>(template);
			Set<IInference<OWLAxiom>> firstMatch = e.first();

			if (!firstMatch.isEmpty()) {
				actualMatches++;
				assertEquals(3, firstMatch.size());
			}
		}

		assertEquals(4, possibleMatches);
		assertEquals(2, actualMatches);

		logger.debug("-----");
	}

	@Test
	public void testRewriting1() throws ProofRwritingException, IOException {
		logger.debug("Test Rewriting 1");

		// Example 1
		IInference<OWLAxiom> inf1 = new Inference<>(a5, "R1", Arrays.asList(a4, a3));
		IInference<OWLAxiom> inf2 = new Inference<>(a4, "R1", Arrays.asList(a1, a7));
		IInference<OWLAxiom> inf3 = new Inference<>(a3, "R1", Arrays.asList(a2, a6));
		IInference<OWLAxiom> inf4 = new Inference<>(a2, "R2", Collections.emptyList());

		IProof<OWLAxiom> proof1 = new Proof<>(a5);
		proof1.addInferences(Arrays.asList(inf1, inf2, inf3, inf4));

		// Pattern1
		IProof<OWLAxiom> pattern1 = new Proof<>(b1);
		pattern1.addInference(new Inference<>(b1, "R1", Arrays.asList(b2, b3)));

		// Rewriting test
		ProofRewriter pR = ProofRewriter.getInstance();
		IProof<OWLAxiom> result = pR.rewrite(proof1, pattern1, "Another Rule");

		assertEquals(proof1.getInferences().size(), result.getInferences().size());

		if (logger.isDebugEnabled()) {
			DotGraphGenerator.drawProofTree(proof1, axiomMAPStr, new HashMap<>(),"proof1");
			DotGraphGenerator.drawProofTree(result, axiomMAPStr, new HashMap<>(), "proof1Rewritten");
			DotGraphGenerator.drawProofTree(pattern1, axiomMAPStr, new HashMap<>(),"pattern1");
		}

		logger.debug("-----");
	}

	@Test
	public void testRewriting1Prime() throws ProofRwritingException, IOException {
		logger.debug("Test Rewriting 1'");

		// Example 1
		IInference<OWLAxiom> inf1 = new Inference<>(a5, "R1", Arrays.asList(a4, a3));
		IInference<OWLAxiom> inf2 = new Inference<>(a4, "R1", Arrays.asList(a1, a7));
		IInference<OWLAxiom> inf3 = new Inference<>(a3, "R1", Arrays.asList(a2, a6));
		IInference<OWLAxiom> inf4 = new Inference<>(a2, "R2", Collections.emptyList());

		IProof<OWLAxiom> proof1 = new Proof<>(a5);
		proof1.addInferences(Arrays.asList(inf1, inf2, inf3, inf4));

		// Pattern1
		IProof<OWLAxiom> pattern1 = new Proof<>(b1);
		pattern1.addInference(new Inference<>(b1, "R1", Arrays.asList(b2, b3)));
		pattern1.addInference(new Inference<>(b2, "R1", Arrays.asList(b4, b5)));

		// Rewriting test
		ProofRewriter pR = ProofRewriter.getInstance();
		IProof<OWLAxiom> result = pR.rewrite(proof1, pattern1);

		assertEquals(2, result.getInferences().size());
		assertTrue(result.getInferences().stream().anyMatch(x -> x.getRuleName().equals("R1")));
		assertEquals(4, result.getInferences().stream().filter(x -> x.getRuleName().equals("R1")).findAny().get()
				.getPremises().size());

		if (logger.isDebugEnabled()) {
			DotGraphGenerator.drawProofTree(proof1, axiomMAPStr, new HashMap<>(), "proof1Prime");
			DotGraphGenerator.drawProofTree(result, axiomMAPStr, new HashMap<>(), "proof1PrimeRewritten");
			DotGraphGenerator.drawProofTree(pattern1, axiomMAPStr, new HashMap<>(), "pattern1Prime");
		}

		logger.debug("-----");
	}

	@Test
	public void testRewriting2() throws ProofRwritingException, IOException {
		logger.debug("Test Rewriting 2");

		// Example 2
		IInference<OWLAxiom> inf1 = new Inference<>(a1, "R1", Arrays.asList(a2, a3, a4));
		IInference<OWLAxiom> inf2 = new Inference<>(a2, "R2", Arrays.asList(a8));
		IInference<OWLAxiom> inf3 = new Inference<>(a8, "R5", Collections.emptyList());

		IInference<OWLAxiom> inf4 = new Inference<>(a3, "R3", Arrays.asList(a5, a6));
		IInference<OWLAxiom> inf5 = new Inference<>(a5, "R6", Collections.emptyList());
		IInference<OWLAxiom> inf6 = new Inference<>(a6, "R4", Arrays.asList(a7));

		IInference<OWLAxiom> inf7 = new Inference<>(a4, "R2", Arrays.asList(a9));
		IInference<OWLAxiom> inf8 = new Inference<>(a9, "R6", Collections.emptyList());

		IProof<OWLAxiom> proof2 = new Proof<>(a1);
		proof2.addInferences(Arrays.asList(inf1, inf2, inf3, inf4, inf5, inf6, inf7, inf8));

		// Pattern2
		IInference<OWLAxiom> patternInf1 = new Inference<>(b1, "R1", Arrays.asList(b2, b3, b4));
		IInference<OWLAxiom> patternInf2 = new Inference<>(b2, "R2", Arrays.asList(b5));
		IInference<OWLAxiom> patternInf3 = new Inference<>(b5, "R6", Collections.emptyList());
		IInference<OWLAxiom> patternInf4 = new Inference<>(b3, "R3", Arrays.asList(b6, b7));
		IInference<OWLAxiom> patternInf5 = new Inference<>(b6, "R6", Collections.emptyList());

		IProof<OWLAxiom> pattern2 = new Proof<>(b1);
		pattern2.addInferences(Arrays.asList(patternInf1, patternInf2, patternInf3, patternInf4, patternInf5));

		// Rewriting test
		ProofRewriter pR = ProofRewriter.getInstance();
		IProof<OWLAxiom> result = pR.rewrite(proof2, pattern2);

		assertEquals(4, result.getInferences().stream().map(IInference::getRuleName).count());

		if (logger.isDebugEnabled()) {
			DotGraphGenerator.drawProofTree(proof2, axiomMAPStr, new HashMap<>(), "proof2");
			DotGraphGenerator.drawProofTree(result, axiomMAPStr, new HashMap<>(), "proof2Rewritten");
			DotGraphGenerator.drawProofTree(pattern2, axiomMAPStr, new HashMap<>(), "pattern2");
		}

		logger.debug("-----");

		JsonProofWriter<OWLAxiom> w = new JsonProofWriter<>();
		w.writeToFile(proof2, "proof2");
		w.writeToFile(pattern2, "pattern2");
	}

	@Test
	public void testRewriting2Prime() throws ProofRwritingException, IOException {
		logger.debug("Test Rewriting 2'");

		// Example 2
		IInference<OWLAxiom> inf1 = new Inference<>(a1, "R1", Arrays.asList(a2, a3, a4));
		IInference<OWLAxiom> inf2 = new Inference<>(a2, "R2", Arrays.asList(a8));
		IInference<OWLAxiom> inf3 = new Inference<>(a8, "R5", Collections.emptyList());

		IInference<OWLAxiom> inf4 = new Inference<>(a3, "R3", Arrays.asList(a5, a6));
		IInference<OWLAxiom> inf5 = new Inference<>(a5, "R6", Collections.emptyList());
		IInference<OWLAxiom> inf6 = new Inference<>(a6, "R4", Arrays.asList(a7));

		IInference<OWLAxiom> inf7 = new Inference<>(a4, "R2", Arrays.asList(a9));
		IInference<OWLAxiom> inf8 = new Inference<>(a9, "R6", Collections.emptyList());

		IProof<OWLAxiom> proof2 = new Proof<>(a1);
		proof2.addInferences(Arrays.asList(inf1, inf2, inf3, inf4, inf5, inf6, inf7, inf8));

		// Pattern2
		IInference<OWLAxiom> patternInf1 = new Inference<>(b1, "R1", Arrays.asList(b2, b3, b4));
		IInference<OWLAxiom> patternInf2 = new Inference<>(b2, "R2", Arrays.asList(b5));

		IProof<OWLAxiom> pattern2 = new Proof<>(b1);
		pattern2.addInferences(Arrays.asList(patternInf1, patternInf2));

		// Rewriting test
		ProofRewriter pR = ProofRewriter.getInstance();
		IProof<OWLAxiom> result = pR.rewrite(proof2, pattern2);

		assertEquals(proof2.getInferences().stream().map(IInference::getRuleName).distinct().count(),
				result.getInferences().stream().map(IInference::getRuleName).distinct().count());
		assertEquals(proof2.getInferences().size() - 1, result.getInferences().size());

		if (logger.isDebugEnabled()) {
			DotGraphGenerator.drawProofTree(proof2, axiomMAPStr, new HashMap<>(), "proof2Prime");
			DotGraphGenerator.drawProofTree(result, axiomMAPStr, new HashMap<>(), "proof2PrimeRewritten");
			DotGraphGenerator.drawProofTree(pattern2, axiomMAPStr, new HashMap<>(), "pattern2Prime");
		}

		logger.debug("-----");
	}

	@Test
	public void testRewriting3() throws ProofRwritingException, IOException {
		logger.debug("Test Rewriting 3");

		// Example 3
		IInference<OWLAxiom> inf1 = new Inference<>(a7, "R1", Arrays.asList(a5, a6));
		IInference<OWLAxiom> inf2 = new Inference<>(a6, "R1", Arrays.asList(a8, a9));
		IInference<OWLAxiom> inf3 = new Inference<>(a9, "R2", Collections.emptyList());
		IInference<OWLAxiom> inf4 = new Inference<>(a8, "Asserted Conclusion", Collections.emptyList());

		IInference<OWLAxiom> inf5 = new Inference<>(a5, "R1", Arrays.asList(a3, a4));
		IInference<OWLAxiom> inf6 = new Inference<>(a4, "R2", Collections.emptyList());
		IInference<OWLAxiom> inf7 = new Inference<>(a3, "R1", Arrays.asList(a1, a2));
		IInference<OWLAxiom> inf8 = new Inference<>(a2, "R2", Collections.emptyList());
		IInference<OWLAxiom> inf9 = new Inference<>(a1, "Asserted Conclusion", Collections.emptyList());

		IProof<OWLAxiom> proof3 = new Proof<>(a7);
		proof3.addInferences(Arrays.asList(inf1, inf2, inf3, inf4, inf5, inf6, inf7, inf8, inf9));

		// Pattern3
		IInference<OWLAxiom> patternInf1 = new Inference<>(b1, "R1", Arrays.asList(b2, b3));
		IInference<OWLAxiom> patternInf2 = new Inference<>(b2, "Asserted Conclusion", Collections.emptyList());
		IInference<OWLAxiom> patternInf3 = new Inference<>(b3, "R2", Collections.emptyList());

		IProof<OWLAxiom> pattern3 = new Proof<>(b1);
		pattern3.addInferences(Arrays.asList(patternInf1, patternInf2, patternInf3));

		// Rewriting test
		ProofRewriter pR = ProofRewriter.getInstance();
		IProof<OWLAxiom> result = pR.rewrite(proof3, pattern3);

		assertEquals(proof3.getInferences().size() - 2, result.getInferences().size());

		if (logger.isDebugEnabled()) {
			DotGraphGenerator.drawProofTree(proof3, axiomMAPStr, new HashMap<>(), "proof3");
			DotGraphGenerator.drawProofTree(result, axiomMAPStr, new HashMap<>(), "proof3Rewritten");
			DotGraphGenerator.drawProofTree(pattern3, axiomMAPStr, new HashMap<>(), "pattern3");
		}

		logger.debug("-----");

	}

	@Test
	public void pizzaExample() throws ProofRwritingException, IOException, OWLOntologyCreationException, ProofGenerationFailedException, ParserConfigurationException, TransformerException {
		logger.debug("Pizza Example for replacing one R2 and two R1 with one R3");

		OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
		OWLDataFactory factory = manager.getOWLDataFactory();

		OWLOntology ontology = manager.loadOntologyFromOntologyDocument(
						new File(Objects.requireNonNull(getClass().getResource("/ontologies/pizza-SpicyFood.owl")).getFile()));

		ELKProofGenerator g = new ELKProofGenerator(ontology);

		OWLClass a = factory.getOWLClass(IRI.create("http://www.co-ode.org/ontologies/pizza/pizza.owl#AmericanHot"));
		OWLClass d = factory.getOWLClass(IRI.create("http://www.co-ode.org/ontologies/pizza#SpicyFood"));

		IProof<OWLAxiom> p = g.getTreeProof(factory.getOWLSubClassOfAxiom(a, d), ProofType.MinimalTreeSize, null);

		// The Patterns
		IInference<OWLAxiom> patternInf1 = new Inference<>(b6, "Class Hierarchy", Arrays.asList(b1, b2));
		IInference<OWLAxiom> patternInf2 = new Inference<>(b1, "Class Hierarchy", Arrays.asList(b3, b4));
		IInference<OWLAxiom> patternInf3 = new Inference<>(b4, "Existential Filler Expansion",
				Collections.singletonList(b5));

		IProof<OWLAxiom> pattern = new Proof<>(b6);
		pattern.addInferences(Arrays.asList(patternInf1, patternInf2, patternInf3));

		// Rewriting test
		ProofRewriter pR = ProofRewriter.getInstance();
		IProof<OWLAxiom> result = pR.rewrite(p, pattern, "R3");

		assertEquals(p.getInferences().size() - 2, result.getInferences().size());

		//Bonus Pattern and rewriting
		IProof<OWLAxiom> pattern2 = new Proof<>(b6);
		pattern2.addInferences(Arrays.asList(patternInf1, patternInf2));

		pR = ProofRewriter.getInstance();
		IProof<OWLAxiom> result2 = pR.rewrite(result, pattern2, "Class Hierarchy");

		Map<OWLAxiom,String> axiomsMap = new HashMap<>();

		p.getInferences().forEach(x ->
				axiomsMap.put(x.getConclusion(),SimpleOWLFormatter.format(x.getConclusion()))
		);


		if (logger.isDebugEnabled()) {
			DotGraphGenerator.drawProofTree(p, axiomsMap, new HashMap<>(), "AmericanHotSpicyFood");
			DotGraphGenerator.drawProofTree(result, axiomsMap, new HashMap<>(), "AmericanHotSpicyFoodRewritten");
			DotGraphGenerator.drawProofTree(result2, axiomsMap, new HashMap<>(), "AmericanHotSpicyFoodRewritten2");
			DotGraphGenerator.drawProofTree(pattern, axiomMAPStr, new HashMap<>(), "AmericanHotSpicyFoodPattern");

			GraphMLGenerator.setOntology(ontology);
			GraphMLGenerator.drawProofs(Collections.singleton(p),"AmericanHotSpicyFood_Evonne",false);
			GraphMLGenerator.drawProofs(Collections.singleton(result),"AmericanHotSpicyFoodRewritten_Evonne",false);
			GraphMLGenerator.drawProofs(Collections.singleton(result2),"AmericanHotSpicyFoodRewritten2_Evonne",false);
		}

		logger.debug("-----");

	}
}
