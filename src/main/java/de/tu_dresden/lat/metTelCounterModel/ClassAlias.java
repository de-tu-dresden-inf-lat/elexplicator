package de.tu_dresden.lat.metTelCounterModel;

import de.tu_dresden.inf.lat.model.tools.ObjectGenerator;
import org.semanticweb.owlapi.model.OWLClass;

public final class ClassAlias {
	private static final OWLClass classAlias = ObjectGenerator.getInstance().getNextConceptName();

	public static OWLClass getClassAlias() {
		return classAlias;
	}
}
