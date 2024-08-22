package de.tu_dresden.lat.metTelCounterModel.preliminaries;

import de.tu_dresden.inf.lat.model.interfaces.IData;
import de.tu_dresden.inf.lat.model.tools.GeneralTools;

import java.io.FileOutputStream;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Christian Alrabbaa
 *
 */
public class Syntax implements IData {

	private static final List<String> syntax = getSyntax();

	private Syntax() {

	}

	private static class LazyHolder {
		static Syntax instance = new Syntax();
	}

	public static Syntax getInstance() {
		return LazyHolder.instance;
	}

	/**
	 * @param outStream
	 */
	public void appendALCOsyntax(FileOutputStream outStream) {

		GeneralTools.writeCollectionTo(syntax, outStream);
	}

	/**
	 * @return
	 */
	private static List<String> getSyntax() {
		List<String> res = new LinkedList<>();

		res.add("syntax " + NAME + "{\n");
		res.add("sort concept;\nsort individual;\nsort role;\n");
		res.add("concept true = 'true';\nconcept false = 'false';\n");
		res.add("concept singleton = '{' individual '}';\n");
		res.add("concept negation = '~' concept;\n");
		res.add("concept existentialRestriction = 'exists' role '.' concept;\n");
		res.add("concept universalRestriction = 'forall' role '.' concept;\n");
		res.add("concept at = '@' individual concept;\n");
		res.add("concept conjunction = concept '&' concept;\n");
		res.add("concept disjunction = concept '|' concept;\n");
		res.add("concept implication = concept '->' concept;\n");
		res.add("concept equivalence = concept '<->' concept;\n");
		res.add("role composition = role ';' role;\n");
		res.add("role union = role '|' role;\n");
		res.add("role converse = role '-';\n");
		res.add("individual skolemF = 'f' '(' individual ',' role ',' concept ')';\n");
		res.add("individual skolemG = 'g' '(' individual ',' individual ',' role ',' role ')';\n");
		res.add("concept equality =  '[' individual '=' individual ']';\n}\n");
		// res.add("tableau " + NAME + "{}");
		return res;
	}
}
