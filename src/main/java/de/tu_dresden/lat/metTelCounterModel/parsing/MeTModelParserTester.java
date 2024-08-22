package de.tu_dresden.lat.metTelCounterModel.parsing;

import de.tu_dresden.lat.metTelCounterModel.parsing.types.Model;
import de.tu_dresden.lat.metTelCounterModel.parsing.types.modelElement.ModelElement;
import de.tu_dresden.lat.metTelCounterModel.parsing.types.terms.SkolemTermF;
import de.tu_dresden.inf.lat.model.interfaces.IConcept;
import de.tu_dresden.inf.lat.model.interfaces.IExpression;
import de.tu_dresden.inf.lat.model.interfaces.IInstance;
import de.tu_dresden.inf.lat.model.interfaces.IRole;

/**
 * 
 * @author Christian Alrabbaa
 */

/**
 * <p>
 * Note: Only used for testing
 * </p>
 */
public class MeTModelParserTester extends MeTModelParser {

	public static IInstance parseBasicIndividual(String toParse) {
		return instance().parse(toParse);
	}

	public static void parseAt(String toParse) {
		at().parse(toParse);
	}

	public static String parseIdentifier(String toParse) {
		return identifier().parse(toParse);

	}

	public static SkolemTermF parseSkolemTerm(String toParse) {
		return skolemTerm().parse(toParse);
	}

	public static IRole parseRole(String toParse) {
		return role().parse(toParse);
	}

	public static IConcept parseConcept(String toParse) {
		return conceptsConjunction().parse(toParse);
	}

	public static IExpression parseExpression(String toParse) {
		return expression().parse(toParse);
	}

	public static ModelElement parseAssertion(String toParse) {
		return modelElement().parse(toParse);
	}

	public static Model parseModel(String toParse) {
		return model().parse(toParse);
	}

	public static void parserOpenC1(String toParse) {
		openCMany1().parse(toParse);
	}
}
