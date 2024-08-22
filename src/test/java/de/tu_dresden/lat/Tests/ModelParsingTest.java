package de.tu_dresden.lat.Tests;

import static org.junit.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

import de.tu_dresden.inf.lat.model.interfaces.IConcept;
import de.tu_dresden.inf.lat.model.interfaces.IInstance;
import de.tu_dresden.inf.lat.model.interfaces.IRole;
import org.apache.log4j.Logger;
import org.junit.Test;

import com.google.common.collect.Sets;

import de.tu_dresden.lat.metTelCounterModel.ToJsonFormatter;
import de.tu_dresden.lat.metTelCounterModel.parsing.MeTModelParserTester;
import de.tu_dresden.lat.metTelCounterModel.parsing.types.Model;
import de.tu_dresden.lat.metTelCounterModel.parsing.types.expresisons.Implication;
import de.tu_dresden.lat.metTelCounterModel.parsing.types.expresisons.concepts.ConceptName;
import de.tu_dresden.lat.metTelCounterModel.parsing.types.expresisons.concepts.ConceptsConjunction;
import de.tu_dresden.lat.metTelCounterModel.parsing.types.expresisons.concepts.ExistentialRestriction;
import de.tu_dresden.lat.metTelCounterModel.parsing.types.expresisons.concepts.NegatedConcept;
import de.tu_dresden.lat.metTelCounterModel.parsing.types.expresisons.concepts.Nominal;
import de.tu_dresden.lat.metTelCounterModel.parsing.types.expresisons.roles.RoleChain;
import de.tu_dresden.lat.metTelCounterModel.parsing.types.expresisons.roles.RoleName;
import de.tu_dresden.lat.metTelCounterModel.parsing.types.modelElement.Assertion;
import de.tu_dresden.lat.metTelCounterModel.parsing.types.modelElement.IndividualsEquality;
import de.tu_dresden.lat.metTelCounterModel.parsing.types.modelElement.IndividualsInequality;
import de.tu_dresden.lat.metTelCounterModel.parsing.types.modelElement.ModelElement;
import de.tu_dresden.lat.metTelCounterModel.parsing.types.terms.BasicTerm;
import de.tu_dresden.lat.metTelCounterModel.parsing.types.terms.SkolemTermF;

/**
 * @author Christian Alrabbaa
 *
 */
public class ModelParsingTest {

	private static final Logger logger = Logger.getLogger(ModelParsingTest.class);
	private static final ToJsonFormatter json = ToJsonFormatter.getInstance();
	private String toParse;
	private static int number = 0;

	private void testMessage(String message) {
		logger.debug("Test " + number + ": -> " + message);
	}

	@Test
	public void TestModelFile() throws IOException {
		number++;

		File file = new File(Objects.requireNonNull(getClass().getResource("/TestModel.model")).getFile());

		BufferedReader br = new BufferedReader(new FileReader(file));

		StringBuilder model = new StringBuilder();
		String line;
		while ((line = br.readLine()) != null) {
			if (line.equalsIgnoreCase("satisfiable"))
				continue;
			model.append(line).append("\n");
		}

		br.close();

		Model m = MeTModelParserTester.parseModel(model.toString());

		testMessage("Parsing a model from a file");
		testMessage(m.toString());
		assertEquals(17, m.getElements().size());

		Map<?, ?> M = json.toJson(m);
		assertEquals("{A=[i], B=[object_0], r=[[object_0, object_0], [i, object_0]]}", M.toString());
	}

	@Test
	public void TestModelString() {
		number++;

		String modelStr = "[( @ ( f ( i , r , B ) ) ( exists r . ( { ( f ( i , r , B ) ) } ) ) ), \n"
				+ "( ~ ( [ i = ( f ( i , r , B ) ) ] ) ), \n" + "( @ i A ), \n" + "( @ i ( { i } ) ), \n"
				+ "( @ i ( ~ B ) ), \n" + "( @ i ( A -> ( exists r . B ) ) ), \n"
				+ "( @ i ( B -> ( exists r . B ) ) ), \n" + "( @ i ( ( M & MM ) -> N ) ), \n" + "( [ i = i ] ), \n"
				+ "( @ i ( exists r . B ) ), \n" + "(@ i ( exists r . ( { ( f ( i , r , B ) ) } ) ) ), \n"
				+ "( @ ( f ( i , r , B ) ) B ), \n" + "( @ ( f ( i , r , B ) ) ( { ( f ( i , r , B ) ) } ) ), \n"
				+ "( @ ( f ( i , r , B ) ) ( A -> ( exists r . B ) ) ), \n"
				+ "( @ ( f ( i , r , B ) ) ( B -> ( exists r . B ) ) ), \n"
				+ "( @ ( f ( i , r , B ) ) ( ( M & MM ) -> N ) ), \n"
				+ "( [ ( f ( i , r , B ) ) = ( f ( i , r , B ) ) ] ), \n" + "( @ i ( ~ ( M & MM ) ) ), \n"
				+ "( @ i ( ~ M ) ), \n" + "( @ ( f ( i , r , B ) ) ( ~ A ) ), \n"
				+ "( @ ( f ( i , r , B ) ) ( exists r . B ) ), \n" + "( @ ( f ( i , r , B ) ) ( ~ ( M & MM ) ) ), \n"
				+ "( @ ( f ( i , r , B ) ) ( ~ M ) ), \n" + "( ~ ( [ ( f ( i , r , B ) ) = i ] ) )]";
		Model m = MeTModelParserTester.parseModel(modelStr);

		testMessage("Parsing a model from a string");
		testMessage(m.toString());
		assertEquals(24, m.getElements().size());

		Map<?, ?> M = json.toJson(m);
		assertEquals("{A=[i], B=[object_0], r=[[i, object_0], [object_0, object_0]]}", M.toString());
	}

	@Test
	public void TestAssertion() {
		number++;

		ConceptName c = new ConceptName("A");

		toParse = "@e A";
		testMessage("Parsing " + "\"@e A\"");
		Assertion m = (Assertion) MeTModelParserTester.parseAssertion(toParse);
		testMessage(m.toString());
		assertEquals(m.getExpression(), c);

		toParse = "(@e (A))";
		testMessage("Parsing " + "\"(@e (A))\"");
		m = (Assertion) MeTModelParserTester.parseAssertion(toParse);
		testMessage(m.toString());
		assertEquals(m.getExpression(), c);

		RoleName s = new RoleName("s");
		RoleChain chain = new RoleChain(Arrays.asList(s, new RoleName("r")));
		Nominal a = new Nominal(new BasicTerm("a"));
		ExistentialRestriction restriction = new ExistentialRestriction(s, a);
		BasicTerm e = new BasicTerm("e");

		toParse = "(@e (exists (s;r) . exists s.{a}))";
		testMessage("Parsing " + "\"(@e (exists (s;r) . exists s.{a}))\"");
		m = (Assertion) MeTModelParserTester.parseAssertion(toParse);
		testMessage(m.toString());
		assertEquals(new Assertion(e, new ExistentialRestriction(chain, restriction)), m);

		toParse = "(@e ({a}))";
		testMessage("Parsing " + "\"(@e ({a}))\"");
		m = (Assertion) MeTModelParserTester.parseAssertion(toParse);
		testMessage(m.toString());
		assertEquals(m.getExpression(), a);

		SkolemTermF sk = new SkolemTermF(new BasicTerm("a"), new RoleName("b"), new Nominal(new BasicTerm("c")));
		toParse = "@(( f(a,b,{c}) ({d})))";
		testMessage("Parsing " + "\"@(( f(a,b,{c}) ({d})))\"");
		m = (Assertion) MeTModelParserTester.parseAssertion(toParse);
		testMessage(m.toString());
		assertEquals(new Assertion(sk, new Nominal(new BasicTerm("d"))), m);

		toParse = "(@e (A -> exists s.A))";
		testMessage("Parsing " + "\"(@e (A -> exists s.A))\"");
		m = (Assertion) MeTModelParserTester.parseAssertion(toParse);
		testMessage(m.toString());
		assertEquals(new Assertion(e, new Implication(c, new ExistentialRestriction(s, c))), m);

		BasicTerm i = new BasicTerm("i");
		SkolemTermF f = new SkolemTermF(i, s, new NegatedConcept(new ConceptName("z")));

		toParse = "[i = f(i,s,~z)]";
		testMessage("Parsing " + "\"[i = f(i,s,~z)]\"");
		ModelElement m2 = MeTModelParserTester.parseAssertion(toParse);
		testMessage(m2.toString());
		assertEquals(m2, new IndividualsEquality(i, f));

		toParse = "(~([i = f(i,s,~z)]))";
		testMessage("Parsing " + "\"(~([i = f(i,s,~z)]))\"");
		m2 = MeTModelParserTester.parseAssertion(toParse);
		testMessage(m2.toString());
		assertEquals(m2, new IndividualsInequality(i, f));
		assertEquals(((IndividualsInequality) m2).getRightHandSide(), f);
		assertEquals(((IndividualsInequality) m2).getLeftHandSide(), i);

		RoleName r = new RoleName("r");
		sk = new SkolemTermF(new BasicTerm("i"), r, new ConceptName("B"));
		ExistentialRestriction ex = new ExistentialRestriction(r, new Nominal(sk));
		toParse = "( @ ( f ( i , r , B ) ) ( exists r . ( { ( f ( i , r , B ) ) } ) ) )";
		testMessage("Parsing " + "\"( @ ( f ( i , r , B ) ) ( exists r . ( { ( f ( i , r , B ) ) } ) ) )\"");
		m2 = MeTModelParserTester.parseAssertion(toParse);
		testMessage(m2.toString());
		assertEquals(new Assertion(sk, ex), m2);

	}

	@Test
	public void TestAt() {
		number++;

		toParse = "@";
		testMessage("Parsing \"@\" ");
		MeTModelParserTester.parseAt(toParse);

		toParse = "@ ";
		testMessage("Parsing \"@ \" ");
		MeTModelParserTester.parseAt(toParse);

		toParse = " @";
		testMessage("Parsing \" @\" ");
		MeTModelParserTester.parseAt(toParse);

		toParse = " @ ";
		testMessage("Parsing \" @ \" ");
		MeTModelParserTester.parseAt(toParse);
	}

	@Test
	public void TestOpenCMany1() {
		number++;

		toParse = "";
		testMessage("Parsing \"\" ");
		MeTModelParserTester.parserOpenC1(toParse);

		toParse = "{ ";
		testMessage("Parsing \"{ \" ");
		MeTModelParserTester.parserOpenC1(toParse);
	}

	@Test
	public void TestIdentifier() {
		number++;

		toParse = "_Alphanumber23 ";
		testMessage("Parsing \"_Alphanumber23 \" ");
		String res = MeTModelParserTester.parseIdentifier(toParse);
		assertEquals("_Alphanumber23", res);

		toParse = "_Alphanumber23 ";
		testMessage("Parsing \"_Alphanumber23 \" ");
		res = MeTModelParserTester.parseIdentifier(toParse);
		assertEquals("_Alphanumber23", res);

		toParse = " _Alphanumber23 ";
		testMessage("Parsing \" _Alphanumber23 \" ");
		res = MeTModelParserTester.parseIdentifier(toParse);
		assertEquals("_Alphanumber23", res);

		toParse = " _Alphanumber23 ";
		testMessage("Parsing \" _Alphanumber23 \" ");
		res = MeTModelParserTester.parseIdentifier(toParse);
		assertEquals("_Alphanumber23", res);
	}

	@Test
	public void TestBasicIndividual() {
		number++;

		toParse = "@e ";
		testMessage("Parsing \"@e \"");
		BasicTerm m = (BasicTerm) MeTModelParserTester.parseBasicIndividual(toParse);
		assertEquals(m.getValue(), "e");
		logger.debug("Basic Term = " + m);

		toParse = " @e ";
		testMessage("Parsing \" @e \"");
		m = (BasicTerm) MeTModelParserTester.parseBasicIndividual(toParse);
		assertEquals(m.getValue(), "e");

		toParse = "@e ";
		testMessage("Parsing \"@e \"");
		m = (BasicTerm) MeTModelParserTester.parseBasicIndividual(toParse);
		assertEquals(m.getValue(), "e");

		toParse = " @e ";
		testMessage("Parsing \" @e \"");
		m = (BasicTerm) MeTModelParserTester.parseBasicIndividual(toParse);
		assertEquals(m.getValue(), "e");

		toParse = "( @e )";
		testMessage("Parsing \"( @e )\"");
		m = (BasicTerm) MeTModelParserTester.parseBasicIndividual(toParse);
		assertEquals(m.getValue(), "e");

		toParse = " (@e )";
		testMessage("Parsing \" (@e )\"");
		m = (BasicTerm) MeTModelParserTester.parseBasicIndividual(toParse);
		assertEquals(m.getValue(), "e");

		toParse = " ((@		e ) )";
		testMessage("Parsing \" ((@		e ) )\"");
		m = (BasicTerm) MeTModelParserTester.parseBasicIndividual(toParse);
		assertEquals(m.getValue(), "e");
	}

	@Test
	public void TestNominal() {
		number++;

		BasicTerm term = new BasicTerm("a");
		IConcept a = new Nominal(term);

		toParse = " { a} ";
		testMessage("Parsing \" { a} \"");
		Nominal m = (Nominal) MeTModelParserTester.parseConcept(toParse);
		assertEquals(m, a);

		logger.debug("Nominal = " + m);

		toParse = " { f(x,y,z)} ";
		testMessage("Parsing \" { f(x,y,z)} \"");
		m = (Nominal) MeTModelParserTester.parseConcept(toParse);
		assertEquals(new Nominal(new SkolemTermF(new BasicTerm("x"), new RoleName("y"), new ConceptName("z"))), m);

		logger.debug("Nominal = " + m);
	}

	@Test
	public void TestConceptName() {
		number++;

		IConcept a = new ConceptName("A");

		toParse = "A ";
		testMessage("Parsing \"A \"");
		ConceptName m = (ConceptName) MeTModelParserTester.parseConcept(toParse);
		assertEquals(m, a);

		logger.debug("Concept name = " + m);
	}

	@Test
	public void TestExistentialRestriction() {
		number++;

		IRole r = new RoleName("r");
		IConcept a = new ConceptName("A");

		toParse = " exists r . A ";
		testMessage("Parsing \" exists r . A \"");
		ExistentialRestriction m = (ExistentialRestriction) MeTModelParserTester.parseConcept(toParse);
		testMessage(m.toString());
		assertEquals(m.getConcept(), a);
		assertEquals(m.getRole(), r);
		logger.debug("Simple Existential restriction = " + m);

		toParse = " exists r . {i} ";
		testMessage("Parsing \" exists r . {i} \"");
		m = (ExistentialRestriction) MeTModelParserTester.parseConcept(toParse);
		testMessage(m.toString());
		assertEquals(m.getConcept(), new Nominal(new BasicTerm("i")));
		assertEquals(m.getRole(), r);
		logger.debug("Existential restriction with nominal = " + m);
	}

	@Test
	public void TestNegatedConcepts() {
		number++;

		IConcept a = new ConceptName("A");

		toParse = "~A";
		testMessage("Parsing \"~A\"");
		NegatedConcept m = (NegatedConcept) MeTModelParserTester.parseConcept(toParse);
		assertEquals(m.getValue(), a);
		logger.debug("Negated Concept = " + m);

		toParse = " ~	A ";
		testMessage("Parsing \" ~	A \"");
		m = (NegatedConcept) MeTModelParserTester.parseConcept(toParse);
		assertEquals(m.getValue(), a);

		IRole r = new RoleName("r");
		a = new ExistentialRestriction(r, a);
		toParse = " (~	(exists(r).A))";
		testMessage("Parsing \" (~	(exists(r).A))\"");
		m = (NegatedConcept) MeTModelParserTester.parseConcept(toParse);
		assertEquals(m.getValue(), a);

		IRole s = new RoleName("s");
		a = new ExistentialRestriction(new RoleChain(Arrays.asList(r, s)), a);
		toParse = " (~	(exists r ; s . exists r.A))";
		testMessage("Parsing \" (~	(exists r ; s . exists r.A))\"");
		m = (NegatedConcept) MeTModelParserTester.parseConcept(toParse);
		assertEquals(m.getValue(), a);
		assertEquals(new NegatedConcept(a), m);
	}

	@Test
	public void TestRoles() {
		number++;

		IRole r = new RoleName("r");
		IRole s = new RoleName("s");
		IRole n = new RoleName("n");

		toParse = "r";
		testMessage("Parsing \"r\"");
		RoleName rolename = (RoleName) MeTModelParserTester.parseRole(toParse);
		assertEquals(rolename, r);
		logger.debug("Role name = " + rolename);

		toParse = "r;s";
		testMessage("Parsing \"r;s\"");
		RoleChain m = (RoleChain) MeTModelParserTester.parseRole(toParse);
		assertEquals(m.getRoles(), Arrays.asList(r, s));
		logger.debug("Role Chain = " + m);

		toParse = " (r ; s;( n);r)";
		testMessage("Parsing \" (r ; s;( n);r)\"");
		m = (RoleChain) MeTModelParserTester.parseRole(toParse);
		assertEquals(m.getRoles(), Arrays.asList(r, s, n, r));
	}

	@Test
	public void TestSimpleSkolemTerm() {
		number++;

		IInstance i = new BasicTerm("e");
		IRole r = new RoleName("r");
		IConcept a = new ConceptName("A");

		toParse = "f(e,r,A)";
		testMessage("Parsing \"f(e,r,A)\"");
		SkolemTermF m = MeTModelParserTester.parseSkolemTerm(toParse);
		assertEquals(m.getIndividual(), i);
		assertEquals(m.getRole(), r);
		assertEquals(m.getClassExpression(), a);
		logger.debug("Simple Skolem Term = " + m);

		toParse = " f ( e , r , A ) ";
		testMessage("Parsing \" f ( e , r , A ) \"");
		m = MeTModelParserTester.parseSkolemTerm(toParse);
		assertEquals(m.getIndividual(), i);
		assertEquals(m.getRole(), r);
		assertEquals(m.getClassExpression(), a);

		toParse = " f ( (e) , r ,( A ) )";
		testMessage("Parsing \" f ( (e) , r ,( A ) )\"");
		m = MeTModelParserTester.parseSkolemTerm(toParse);
		assertEquals(m.getIndividual(), i);
		assertEquals(m.getRole(), r);
		assertEquals(m.getClassExpression(), a);

		toParse = " f ( (e) , (  r) ,( A ) )";
		testMessage("Parsing \" f ( (e) , (  r) ,( A ) )\"");
		m = MeTModelParserTester.parseSkolemTerm(toParse);
		assertEquals(m.getIndividual(), i);
		assertEquals(m.getRole(), r);
		assertEquals(m.getClassExpression(), a);

		a = new ExistentialRestriction(r, a);
		toParse = "f(e,r,exists r.A)";
		testMessage("Parsing \"f(e,r,exists r.A)\"");
		m = MeTModelParserTester.parseSkolemTerm(toParse);
		assertEquals(m.getIndividual(), i);
		assertEquals(m.getRole(), r);
		assertEquals(m.getClassExpression(), a);

		toParse = "f(e,r,existsr.A)";
		testMessage("Parsing \"f(e,r,existsr.A)\"");
		m = MeTModelParserTester.parseSkolemTerm(toParse);
		assertEquals(m.getIndividual(), i);
		assertEquals(m.getRole(), r);
		assertEquals(m.getClassExpression(), a);

		a = new ExistentialRestriction(r, a);
		toParse = "f(e,r,existsr.existsr.A)";
		testMessage("Parsing \"f(e,r,existsr.A)\"");
		m = MeTModelParserTester.parseSkolemTerm(toParse);
		assertEquals(m.getIndividual(), i);
		assertEquals(m.getRole(), r);
		assertEquals(m.getClassExpression(), a);

		toParse = "(f(e,r,existsr.(existsr.A)))";
		testMessage("Parsing \"f(e,r,existsr.(existsr.A))\"");
		m = MeTModelParserTester.parseSkolemTerm(toParse);
		assertEquals(m.getIndividual(), i);
		assertEquals(m.getRole(), r);
		assertEquals(m.getClassExpression(), a);
	}

	@Test
	public void TestExpression() {
		number++;

		RoleName r = new RoleName("r");
		ConceptName a = new ConceptName("A");
		ConceptName b = new ConceptName("B");
		ExistentialRestriction n = new ExistentialRestriction(r, a);

		toParse = "B -> exists r. A";
		testMessage("Parsing \"B -> exists r. A\"");
		Implication imp = (Implication) MeTModelParserTester.parseExpression(toParse);
		assertEquals(imp, new Implication(b, n));
		logger.debug("Implication  = " + imp);

		toParse = "((exists((r)).(A)) -> (B))";
		testMessage("Parsing \"((exists((r)).(A)) -> (B))\"");
		imp = (Implication) MeTModelParserTester.parseExpression(toParse);
		assertEquals(imp, new Implication(n, b));

	}

	@Test
	public void TestConceptsConcjunction() {
		number++;

		RoleName r = new RoleName("r");
		ConceptName a = new ConceptName("A");
		ConceptName b = new ConceptName("B");
		ExistentialRestriction n = new ExistentialRestriction(r, a);

		toParse = "B & exists r. A";
		testMessage("Parsing \"B & exists r. A\"");
		ConceptsConjunction con = (ConceptsConjunction) MeTModelParserTester.parseConcept(toParse);
		assertEquals(new ConceptsConjunction(Arrays.asList(n, b)), con);
		assertEquals(new ConceptsConjunction(Arrays.asList(b, n)), con);
		logger.debug("Concepts Conjunction  = " + con);

		toParse = "B & A & {a}";
		testMessage("Parsing \"B & C & {a}\"");
		con = (ConceptsConjunction) MeTModelParserTester.parseConcept(toParse);
		assertEquals(new ConceptsConjunction(Arrays.asList(a, b, new Nominal(new BasicTerm("a")))), con);
		logger.debug("Concepts Conjunction  = " + con);

		ExistentialRestriction restriction = new ExistentialRestriction(r,
				new ConceptsConjunction(Arrays.asList(a, b)));
		toParse = "A & B & exists r.(A & B ))";
		testMessage("Parsing \"A & B & exists r.(A & B )\"");
		con = (ConceptsConjunction) MeTModelParserTester.parseConcept(toParse);
		assertEquals(new ConceptsConjunction(Arrays.asList(a, b, restriction)), con);
		assertEquals(Sets.newHashSet(a, b, restriction), con.getConcepts());
		logger.debug("Concepts Conjunction  = " + con);
	}

}
