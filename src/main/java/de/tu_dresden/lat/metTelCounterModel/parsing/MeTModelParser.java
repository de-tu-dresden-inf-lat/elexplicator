package de.tu_dresden.lat.metTelCounterModel.parsing;

import java.util.HashSet;
import java.util.List;

import de.tu_dresden.inf.lat.model.interfaces.*;
import org.codehaus.jparsec.Parser;
import org.codehaus.jparsec.Parsers;
import org.codehaus.jparsec.Scanners;
import org.codehaus.jparsec.functors.Map;

import de.tu_dresden.lat.metTelCounterModel.parsing.types.Model;
import de.tu_dresden.lat.metTelCounterModel.parsing.types.expresisons.Implication;
import de.tu_dresden.lat.metTelCounterModel.parsing.types.expresisons.concepts.ConceptName;
import de.tu_dresden.lat.metTelCounterModel.parsing.types.expresisons.concepts.ConceptsConjunction;
import de.tu_dresden.lat.metTelCounterModel.parsing.types.expresisons.concepts.ExistentialRestriction;
import de.tu_dresden.lat.metTelCounterModel.parsing.types.expresisons.concepts.NegatedConcept;
import de.tu_dresden.lat.metTelCounterModel.parsing.types.expresisons.concepts.Nominal;
import de.tu_dresden.lat.metTelCounterModel.parsing.types.expresisons.concepts.UniversalRestriction;
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
public class MeTModelParser {

	// White Spaces

	/**
	 * Takes care of white spaces around the operator
	 * 
	 * @param p {@code Parser<Void>}
	 * @return
	 */
	private static Parser<Void> spaces(Parser<Void> p) {

		return p.between(Scanners.WHITESPACES.many(), Scanners.WHITESPACES.many());
	}

	// Operators

	/**
	 * A scanner for "->"
	 * 
	 * @return {@code Parser<Void>}
	 */
	private static Parser<Void> imply() {
		return spaces(Scanners.string(Operators.RIGHTARROW, Operators.RIGHTARROW));
	}

	/**
	 * A scanner for "exists"
	 * 
	 * @return {@code Parser<Void>}
	 */
	private static Parser<Void> exists() {
		return spaces(Scanners.string(Operators.EXISTS, Operators.EXISTS));
	}

	/**
	 * A scanner for "forall"
	 * 
	 * @return {@code Parser<Void>}
	 */
	private static Parser<Void> forall() {
		return spaces(Scanners.string(Operators.FORALL, Operators.FORALL));
	}

	/**
	 * A scanner for "~"
	 * 
	 * @return {@code Parser<Void>}
	 */
	private static Parser<Void> not() {
		return spaces(Scanners.isChar(Operators.NOT, "~"));
	}

	/**
	 * A scanner for "f"
	 * 
	 * @return {@code Parser<Void>}
	 */
	private static Parser<Void> skolemF() {
		return Scanners.isChar(Operators.SKOLEMF, "f").between(Scanners.WHITESPACES.many(),
				Scanners.WHITESPACES.many());
	}

	/**
	 * A scanner for ";"
	 * 
	 * @return {@code Parser<Void>}
	 */
	private static Parser<Void> semicolon() {
		return Scanners.isChar(Operators.SEMICOLON, ";").between(Scanners.WHITESPACES.many(),
				Scanners.WHITESPACES.many());
	}

	/**
	 * A scanner for ","
	 * 
	 * @return {@code Parser<Void>}
	 */
	private static Parser<Void> comma() {
		return Scanners.isChar(Operators.COMMA, ",").between(Scanners.WHITESPACES.many(), Scanners.WHITESPACES.many());
	}

	/**
	 * A scanner for "."
	 * 
	 * @return {@code Parser<Void>}
	 */
	private static Parser<Void> dot() {

		return Scanners.isChar(Operators.DOT, ".").between(Scanners.WHITESPACES.many(), Scanners.WHITESPACES.many());
	}

	/**
	 * A scanner for "="
	 * 
	 * @return {@code Parser<Void>}
	 */
	private static Parser<Void> equals() {
		return Scanners.isChar(Operators.EQUALS, "=").between(Scanners.WHITESPACES.many(), Scanners.WHITESPACES.many());
	}

	/**
	 * A scanner for "&"
	 * 
	 * @return {@code Parser<Void>}
	 */
	private static Parser<Void> and() {
		return Scanners.isChar(Operators.AND, "&").between(Scanners.WHITESPACES.many(), Scanners.WHITESPACES.many());
	}

	/**
	 * A scanner for "@"
	 * 
	 * @return {@code Parser<Void>}
	 */
	protected static Parser<Void> at() {
		return Scanners.isChar(Operators.AT, "@").between(Scanners.WHITESPACES.many(), Scanners.WHITESPACES.many());
	}

	// Brackets

	/**
	 * A scanner for 0 or many "("
	 * 
	 * @return {@code Parser<Void>}
	 */
	private static Parser<Void> openMany() {
		return Scanners.isChar(Operators.OPEN, "(").skipMany()
				.between(Scanners.WHITESPACES.many(), Scanners.WHITESPACES.many()).skipMany();
	}

	/**
	 * A scanner for 0 or many ")"
	 * 
	 * @return {@code Parser<Void>}
	 */
	private static Parser<Void> closeMany() {
		return Scanners.isChar(Operators.CLOSE, ")").skipMany()
				.between(Scanners.WHITESPACES.many(), Scanners.WHITESPACES.many()).skipMany();
	}

	/**
	 * A scanner for 1 or many "("
	 * 
	 * @return {@code Parser<Void>}
	 */
	private static Parser<Void> openMany1() {
		return Scanners.isChar(Operators.OPEN, "(").skipMany1()
				.between(Scanners.WHITESPACES.many(), Scanners.WHITESPACES.many()).skipMany();
	}

	/**
	 * A scanner for 1 or many ")"
	 * 
	 * @return {@code Parser<Void>}
	 */
	private static Parser<Void> closeMany1() {
		return Scanners.isChar(Operators.CLOSE, ")").skipMany1()
				.between(Scanners.WHITESPACES.many(), Scanners.WHITESPACES.many()).skipMany();
	}

	/**
	 * A scanner for 1 or many "{"
	 * 
	 * @return {@code Parser<Void>}
	 */
	protected static Parser<Void> openCMany1() {
		return Scanners.isChar(Operators.OPENC, "{").skipMany1()
				.between(Scanners.WHITESPACES.many(), Scanners.WHITESPACES.many()).skipMany();
	}

	/**
	 * A scanner for 1 or many "}"
	 * 
	 * @return {@code Parser<Void>}
	 */
	private static Parser<Void> closeCMany1() {
		return Scanners.isChar(Operators.CLOSEC, "}").skipMany1()
				.between(Scanners.WHITESPACES.many(), Scanners.WHITESPACES.many()).skipMany();
	}

	/**
	 * A scanner for 1 or many "["
	 * 
	 * @return {@code Parser<Void>}
	 */
	private static Parser<Void> openSMany1() {
		return Scanners.isChar(Operators.OPENS, "[").skipMany1()
				.between(Scanners.WHITESPACES.many(), Scanners.WHITESPACES.many()).skipMany();
	}

	/**
	 * A scanner for 1 or many "]"
	 * 
	 * @return {@code Parser<Void>}
	 */
	private static Parser<Void> closeSMany1() {
		return Scanners.isChar(Operators.CLOSES, "]").skipMany1()
				.between(Scanners.WHITESPACES.many(), Scanners.WHITESPACES.many()).skipMany();
	}

	// Models

	/**
	 * Model Parser, the model is of the form "Model: '['m, ...']'" where 'Model: '
	 * is optional and 'm' is a model element
	 * 
	 * @return {@code Parser<Model>}
	 */
	protected static Parser<Model> model() {
		return Scanners.string("Model: ").optional().next(
				modelElement().sepBy(comma()).map(HashSet::new).map(Model::new).between(openSMany1(), closeSMany1()));
	}

	// Assertions

	/**
	 * Model element parser, an element is of the form "'@' i expression" where 'i'
	 * is an individual (basic or skolem)
	 * 
	 * @return {@code Parser<ModelElement>}
	 */
	protected static Parser<ModelElement> modelElement() {
		return equality().or(Parsers.sequence(instance(), expression(), (a, b) -> new Assertion(a, b)).cast()).cast();
	}

	// Expressions

	/**
	 * Expression parser, an expression is either an implication or a concepts
	 * conjunction
	 * 
	 * @return {@code Parser<IExpression>}
	 */
	protected static Parser<IExpression> expression() {
		return Parsers.or(implication(), conceptsConjunction()).between(openMany(), closeMany());
	}

	// Equality Assertions

	/**
	 * Equality and inequality assertion parser
	 * 
	 * @return {@code Parser<IEqual>}
	 */
	private static Parser<IEqual> equality() {
		return Parsers.or(individualsInequality(), individualsEquality());
	}

	/**
	 * Inequality assertion parser, the assertion is of the form "'~' '[' i = j']'"
	 * where 'i' and 'j' are individuals
	 * 
	 * @return{@code Parser<IndividualsInequality>}
	 */
	private static Parser<IndividualsInequality> individualsInequality() {
		return Parsers.sequence(not(), individualsEquality().between(openMany(), closeMany()))
				.map(new Map<IndividualsEquality, IndividualsInequality>() {
					public IndividualsInequality map(IndividualsEquality arg0) {
						return new IndividualsInequality(arg0.getLeftHandSide(), arg0.getRightHandSide());
					}
				}).between(openMany(), closeMany());
	}

	/**
	 * Equality assertion parser, the assertion is of the form "'[' i = j']'" where
	 * 'i' and 'j' are individuals
	 * 
	 * @return{@code Parser<IndividualsEquality>}
	 */
	private static Parser<IndividualsEquality> individualsEquality() {
		return (Parsers.sequence(individual(), equals(), individual(), (a, b, c) -> new IndividualsEquality(a, c))
				.between(openSMany1(), closeSMany1())).between(openMany(), closeMany());
	}

	// Implication

	/**
	 * Implication parser
	 * <p>
	 * Note: Role hierarchy is not supported
	 * </p>
	 * 
	 * @return {@code Parser<Implication>}
	 * 
	 */
	protected static Parser<Implication> implication() {
		return Parsers
				.sequence(conceptsConjunction(), imply(), conceptsConjunction(), (a, b, c) -> new Implication(a, c))
				.between(openMany(), closeMany());
	}

	// Identifiers

	/**
	 * Alphanumeric parser
	 * 
	 * @return {@code Parser<String>}
	 */
	protected static Parser<String> identifier() {
		return Scanners.IDENTIFIER.map(String::new).between(Scanners.WHITESPACES.many(), Scanners.WHITESPACES.many());
	}

	/**
	 * Simple Alphanumeric string parser
	 * 
	 * @return {@code Parser<BasicTerm>}
	 */
	private static Parser<BasicTerm> instanceName() {
		return identifier().map(BasicTerm::new).between(openMany(), closeMany());
	}

	// Instances

	/**
	 * Instance parser
	 * 
	 * @return {@code Parser<IInstance>}
	 */
	protected static Parser<IInstance> instance() {
		return Parsers.sequence(at(), individual()).between(openMany(), closeMany()).cast();
	}

	// Individuals

	/**
	 * individual parser (Skolem or simple term)
	 * 
	 * @return {@code Parser<IInstance>}
	 */
	private static Parser<IInstance> individual() {
		return skolemTerm().or(instanceName().cast()).between(openMany(), closeMany()).cast();
	}

	// Skolem Term

	/**
	 * @return {@code Parser<Void>}
	 */
	private static Parser<Void> skolemFStart() {
		return Parsers.sequence(skolemF(), openMany1());
	}

	/**
	 * @return {@code Parser<Void>}
	 */
	private static Parser<Void> skolemEnd() {
		return closeMany1();
	}

	/**
	 * Skolem Term parser
	 * 
	 * @return {@code Parser<SkolemtermF>}
	 */
	protected static Parser<SkolemTermF> skolemTerm() {
		return skolemTermBody(conceptsConjunction());
	}

	/**
	 * Skolem Term parser
	 * 
	 * @param concept {@code Parser<IConcept>}
	 * @return {@code Parser<SkolemTermF>}
	 */
	private static Parser<SkolemTermF> skolemTerm(Parser<IConcept> concept) {
		return skolemTermBody(conceptsConjunction(concept));
	}

	/**
	 * Skolem Term parser, the term is of the form 'f'(a,b,c) where a is an
	 * individual or a skolem term, b is a role and c is a concept
	 * 
	 * @param concept
	 * @return
	 */
	private static Parser<SkolemTermF> skolemTermBody(Parser<IConcept> concept) {
		Parser.Reference<SkolemTermF> ref = Parser.newReference();

		Parser<SkolemTermF> skolemTerm = Parsers
				.sequence(instanceName(), comma(), role(), comma(), concept,
						(a, b, c, d, e) -> new SkolemTermF(a, c, e))
				.or(Parsers.sequence(ref.lazy(), comma(), role(), comma(), concept,
						(a, b, c, d, e) -> new SkolemTermF(a, c, e)))
				.between(skolemFStart(), skolemEnd()).between(openMany(), closeMany());

		ref.set(skolemTerm);

		return skolemTerm;
	}

	// Concepts

	/**
	 * Concept name parser
	 * 
	 * @return {@code Parser<ConceptName>}
	 */
	private static Parser<IExpression> conceptName() {

		return identifier().map(ConceptName::new).between(openMany(), closeMany()).cast();
	}

	/**
	 * Concept name, existential restriction, universal restriction and negated
	 * concept parser
	 * 
	 * @return {@code Parser<IConcept>}
	 */
	private static Parser<IConcept> concept() {
		Parser.Reference<IConcept> ref = Parser.newReference();

		Parser<IConcept> concept = existentialRestriction(ref.lazy()).cast().or(universalRestriction(ref.lazy()).cast())
				.or(negatedConcept(ref.lazy()).cast()).or(nominalSkolem(ref.lazy()).cast()).or(conceptName().cast())
				.or(nominalBasic().cast()).between(openMany(), closeMany()).cast();

		ref.set(concept);

		return concept;
	}

	/**
	 * Concepts conjunction parser
	 * 
	 * @return {@code Parser<IConcept>}
	 */
	protected static Parser<IConcept> conceptsConjunction() {
		return conceptsConjunction(concept());
	}

	/**
	 * Concepts conjunction parser
	 * 
	 * @return {@code Parser<IConcept>}
	 */
	private static Parser<IConcept> conceptsConjunction(Parser<IConcept> lazy) {
		return lazy.sepBy(and()).map(new Map<List<IConcept>, IConcept>() {
			public IConcept map(List<IConcept> arg0) {
				if (arg0.size() == 1)
					return arg0.get(0);
				else
					return new ConceptsConjunction(arg0);
			}
		}).between(openMany(), closeMany());
	}

	// Existential Restriction

	/**
	 * Existential restriction parser, the concept is of the form "'exists' r . A"
	 * where r is a role and A is a concept
	 * 
	 * @param concept {@code Parser<IConcept>}
	 * @return {@code Parser<ExistentialRestriction>}
	 */
	private static Parser<ExistentialRestriction> existentialRestriction(Parser<IConcept> concept) {
		return Parsers.sequence(exists(), role(), dot(), conceptsConjunction(concept),
				(a, b, c, d) -> new ExistentialRestriction(b, d)).between(openMany(), closeMany());
	}

	// Universal Restriction

	/**
	 * Universal restriction parser, the concept is of the form "'forall' r . A"
	 * where r is a role and A is a concept
	 * 
	 * @param concept {@code Parser<IConcept>}
	 * @return {@code Parser<ExistentialRestriction>}
	 */
	private static Parser<UniversalRestriction> universalRestriction(Parser<IConcept> concept) {
		return Parsers.sequence(forall(), role(), dot(), conceptsConjunction(concept),
				(a, b, c, d) -> new UniversalRestriction(b, d)).between(openMany(), closeMany());
	}

	// Negated Concept

	/**
	 * Negated Concept parser, the concept is of the form "'~' concept"
	 * 
	 * @param concept {@code Parser<IConcept>}
	 * @return {@code Parser<NegatedConcept>}
	 */
	private static Parser<NegatedConcept> negatedConcept(Parser<IConcept> concept) {
		return not().next(conceptsConjunction(concept)).map(NegatedConcept::new).between(openMany(), closeMany());
	}
	// Nominals

	/**
	 * Nominal parser, the nominal is of the form "'{'skolem term'}'"
	 * 
	 * @param concept {@code Parser<IConcept>}
	 * @return {@code Parser<Nominal>}
	 */
	private static Parser<Nominal> nominalSkolem(Parser<IConcept> concept) {
		return skolemTerm(concept).map(Nominal::new).between(openCMany1(), closeCMany1()).between(openMany(),
				closeMany());
	}

	/**
	 * Nominal parser, the nominal is of the form "'{'instance name'}'"
	 * 
	 * @return{@code Parser<Nominal>}
	 */
	private static Parser<Nominal> nominalBasic() {
		return instanceName().map(Nominal::new).between(openCMany1(), closeCMany1()).between(openMany(), closeMany());
	}

	// Roles

	/**
	 * Role Chain and role name parser
	 * 
	 * @return {@code Parser<IRole>}
	 */
	protected static Parser<IRole> role() {
		return roleName().sepBy(semicolon()).map(new Map<List<RoleName>, IRole>() {
			public IRole map(List<RoleName> arg0) {
				if (arg0.size() == 1)
					return arg0.get(0);
				else
					return new RoleChain(arg0);
			}
		}).between(openMany(), closeMany());
	}

	/**
	 * Role name parser
	 * 
	 * @return {@code Parser<RoleName>}
	 */
	private static Parser<RoleName> roleName() {
		return identifier().map(RoleName::new).between(openMany(), closeMany());
	}
}
