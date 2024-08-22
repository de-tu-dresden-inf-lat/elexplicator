package de.tu_dresden.lat.data.enums;

/**
 * @author Christian Alrabbaa
 *
 */
public enum OutputType {

	RecursiveJSON, NonRecursiveJSON, Graph, Text;

	public static boolean isRecursive(OutputType m) {
		return m == OutputType.RecursiveJSON;
	}

	public static boolean isNonRecursive(OutputType m) {
		return !isRecursive(m);
	}

	public static OutputType getTypeValue(String str) {
		if (str.equalsIgnoreCase("graph"))
			return OutputType.Graph;

		if (str.equalsIgnoreCase("text"))
			return OutputType.Text;

		if (str.equalsIgnoreCase("nonrecursivejson"))
			return OutputType.NonRecursiveJSON;

		if (str.equalsIgnoreCase("recursivejson"))
			return OutputType.RecursiveJSON;

		throw new IllegalArgumentException("No Mode value for \"" + str + "\"");
	}
}
