package de.tu_dresden.lat.metTelCounterModel.parsing.types.expresisons.concepts;

import de.tu_dresden.inf.lat.model.interfaces.IConcept;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Christian Alrabbaa
 *
 */
public class ConceptsConjunction implements IConcept {

	private final Set<IConcept> concepts;

	public ConceptsConjunction(Collection<? extends IConcept> concepts) {
		this.concepts = new HashSet<>(concepts);
	}

	@Override
	public String toString() {
		return "( " + concepts.stream().map(IConcept::toString).collect(Collectors.joining(" & ")) + " )";
	}

	public Set<IConcept> getConcepts() {
		return concepts;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((concepts == null) ? 0 : concepts.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ConceptsConjunction other = (ConceptsConjunction) obj;
		if (concepts == null) {
			if (other.concepts != null)
				return false;
		} else if (!concepts.equals(other.concepts))
			return false;
		return true;
	}

}
