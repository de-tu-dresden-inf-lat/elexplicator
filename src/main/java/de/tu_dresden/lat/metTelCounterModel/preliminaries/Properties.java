package de.tu_dresden.lat.metTelCounterModel.preliminaries;

import java.io.FileOutputStream;
import java.util.LinkedList;
import java.util.List;

import de.tu_dresden.inf.lat.model.interfaces.IData;
import de.tu_dresden.inf.lat.model.tools.GeneralTools;

public class Properties implements IData {

	public Properties() {
	}

	private static final List<String> properties = getProperties();

	private static class LazyHolder {
		static Properties instance = new Properties();
	}

	public static Properties getInstance() {
		return LazyHolder.instance;
	}

	/**
	 * @param outStream
	 */
	public void appendALCOProperties(FileOutputStream outStream) {

		GeneralTools.writeCollectionTo(properties, outStream);
	}

	/**
	 * @return
	 */
	private static List<String> getProperties() {
		List<String> res = new LinkedList<>();
		res.add("specification " + NAME + ";\n");
		res.add("options{\n");
		res.add("antlr.k = 1\n");
		res.add("antlr.superClass=mettel.core.language.MettelAbstractLogicParser\n");
		res.add("antlr.backtrack=false\n");
		res.add("antlr.memoize=false\n");
		res.add("#branch.bound=\n");
		res.add("equality.keywords={equality}\n");
		res.add("expression.left.delimiter=(\n");
		res.add("expression.right.delimiter=)\n");
		res.add("list.left.delimiter=\n");
		res.add("list.right.delimiter=\n");
		res.add("tableau.rule.delimiter=$;\n");
		res.add("tableau.rule.branch.delimiter=$|\n");
		res.add("tableau.rule.premise.delimiter=/\n}\n");

		return res;
	}
}
