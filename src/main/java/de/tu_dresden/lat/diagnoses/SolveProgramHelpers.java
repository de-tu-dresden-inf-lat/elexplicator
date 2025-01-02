package de.tu_dresden.lat.diagnoses;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

import org.semanticweb.owlapi.model.OWLAxiom;

public class SolveProgramHelpers {
    public static void createProgram(Set<Set<? extends OWLAxiom>> allJustifications, String outDirStr, Map<OWLAxiom, String> axioms2Identifiers, Map<String, OWLAxiom> identifiers2Axioms, String programFileName)
			throws IOException {
		StringJoiner program = new StringJoiner("\n");

		program.add("%All Justifications");
		allJustifications.forEach(justification -> {
			{
				program.add(getRule(justification, axioms2Identifiers));
			}			
		});

		program.add("%Choices");
		program.add(getChoices(identifiers2Axioms));

		File outDir = new File(outDirStr);
		if (!outDir.exists())
			throw new IOException("Directory does not exist -> " + outDirStr);

		HelperFunctions.saveText(program.toString(), outDirStr + File.separator + programFileName);
	}

    /**
	 * For a given justification {alpha1, alpha2,...} return a string representing
	 * an ASP rule of the form "statement :- alpha1, alpha2, ... ."
	 * 
	 * @param justification
	 * @return
	 */
	private static String getRule(Set<? extends OWLAxiom> justification, Map<OWLAxiom, String> axioms2Identifiers) {
		String ruleHead = "statement :- ";

		StringJoiner ruleBody = new StringJoiner(",");
		justification.forEach(axiom -> {			
				ruleBody.add(axioms2Identifiers.get(axiom) + "()");
		});

		return ruleHead + ruleBody + ".";
	}	

    /**
	 * Return a string representing the ASP choice rule of the form {alpha1; ... ;
	 * alpha_i} where each alpha_n is an axiom appearing in some justification
	 * 
	 * @return
	 */
	private static String getChoices(Map<String, OWLAxiom> identifiers2Axioms) {
		StringJoiner choiceRule = new StringJoiner(";");

		identifiers2Axioms.keySet().forEach(choiceRule::add);

		return "{" + choiceRule + "}.";
	}
}
