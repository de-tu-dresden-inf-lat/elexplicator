package de.tu_dresden.lat.metTelCounterModel;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import de.tu_dresden.inf.lat.model.interfaces.IExpression;
import de.tu_dresden.inf.lat.model.interfaces.IInstance;
import de.tu_dresden.inf.lat.model.interfaces.IType;
import org.apache.log4j.Logger;

import de.tu_dresden.lat.metTelCounterModel.parsing.types.Model;
import de.tu_dresden.lat.metTelCounterModel.parsing.types.expresisons.concepts.ConceptName;
import de.tu_dresden.lat.metTelCounterModel.parsing.types.expresisons.concepts.ExistentialRestriction;
import de.tu_dresden.lat.metTelCounterModel.parsing.types.expresisons.concepts.Nominal;
import de.tu_dresden.lat.metTelCounterModel.parsing.types.expresisons.roles.RoleName;
import de.tu_dresden.lat.metTelCounterModel.parsing.types.modelElement.Assertion;
import de.tu_dresden.lat.metTelCounterModel.parsing.types.modelElement.ModelElement;
import de.tu_dresden.lat.metTelCounterModel.parsing.types.terms.BasicTerm;
import de.tu_dresden.lat.metTelCounterModel.parsing.types.terms.SkolemTermF;

/**
 * @author Christian Alrabbaa
 *
 */

public class ToJsonFormatter {

	private static final Logger logger = Logger.getLogger(ToJsonFormatter.class);

	private final Map<SkolemTermF, BasicTerm> skolemMap;
	private static final String aliasPrefix = "object_";
	private static int aliasSufix = 0;

	private ToJsonFormatter() {
		skolemMap = new HashMap<>();
	}

	private static class LazyHolder {
		static ToJsonFormatter instance = new ToJsonFormatter();
	}

	public static ToJsonFormatter getInstance() {
		return LazyHolder.instance;
	}

	/**
	 * Return a map that represent a model in Json format
	 * 
	 * @param model
	 * @return{@code Map<IType, List<IInstance>>}
	 */
	public Map<IType, List<Object>> toJson(Model model) {
		Map<IType, List<Object>> modelJson = new HashMap<>();
		model.getElements().forEach(modelElement -> {
			addToJsonModel(modelJson, modelElement);
		});
		logger.debug("Json Model: " + modelJson);
		return modelJson;
	}

	/**
	 * Add the map representation of every model element to the json model
	 * 
	 * @param modelJson
	 * @param modelElement
	 */
	private void addToJsonModel(Map<IType, List<Object>> modelJson, ModelElement modelElement) {
		Map<IType, List<Object>> elements = getElementMap(modelElement);

		elements.keySet().forEach(key -> {
			if (modelJson.containsKey(key)) {
				modelJson.get(key).addAll(elements.get(key));
			} else
				modelJson.put(key, elements.get(key));
		});

	}

	private Map<IType, List<Object>> getElementMap(ModelElement modelElement) {
		Map<IType, List<Object>> elementsMap = new HashMap<>();

		if (!(modelElement instanceof Assertion))
			return elementsMap;

		Assertion assertion = (Assertion) modelElement;

		fillElements(elementsMap, assertion);
		return elementsMap;
	}

	private void fillElements(Map<IType, List<Object>> elementsMap, Assertion assertion) {

		BasicTerm alias = getSkolemAlias(assertion.getIndividual());

		IExpression expression = assertion.getExpression();

		// A(a)
		if (expression instanceof ConceptName) {
			ConceptName conceptName = (ConceptName) expression;
			if (conceptName instanceof ConceptName) {
				if (elementsMap.containsKey(conceptName))
					assert false : "A key must not occure multiple times";
				else {
					List<Object> lst = new LinkedList<>();
					lst.add(alias);
					elementsMap.put(conceptName, lst);
				}
			}

		}

		// r(a,b)
		if (expression instanceof ExistentialRestriction) {
			ExistentialRestriction exRes = (ExistentialRestriction) expression;
			if (exRes.getConcept() instanceof Nominal && exRes.getRole() instanceof RoleName) {
				RoleName roleName = ((RoleName) exRes.getRole());
				IInstance successor = ((Nominal) exRes.getConcept()).getValue();
				BasicTerm succAlias = getSkolemAlias(successor);
				List<BasicTerm> pair = Arrays.asList(alias, succAlias);

				if (elementsMap.containsKey(roleName))
					assert false : "A key must not occure multiple times";
				else
					elementsMap.put(roleName, new LinkedList<>(Arrays.asList(pair)));
			}
		}
	}

	private BasicTerm getSkolemAlias(IInstance successor) {

		if (successor instanceof SkolemTermF) {
			SkolemTermF sTerm = (SkolemTermF) successor;
			if (skolemMap.containsKey(sTerm))
				return skolemMap.get(sTerm);
			else {
				BasicTerm alias = new BasicTerm(getNextAlias());
				skolemMap.put(sTerm, alias);
				return alias;
			}
		}
		if (successor instanceof BasicTerm)
			return (BasicTerm) successor;

		System.err.println(successor);
		assert false : "should not be reachable";
		return null;
	}

	private String getNextAlias() {
		return aliasPrefix + (aliasSufix++);
	}
}
