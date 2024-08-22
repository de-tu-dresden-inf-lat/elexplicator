package de.tu_dresden.lat.metTelCounterModel.parsing.types;

import java.util.Set;
import java.util.stream.Collectors;

import de.tu_dresden.lat.metTelCounterModel.parsing.types.modelElement.ModelElement;

/**
 * @author Christian Alrabbaa
 *
 */
public class Model {

	private final Set<? extends ModelElement> elements;

	public Model(Set<? extends ModelElement> assertions) {
		this.elements = assertions;
	}

	public Set<? extends ModelElement> getElements() {
		return elements;
	}

	@Override
	public String toString() {
		return "Model: [ " + elements.stream().map(ModelElement::toString).collect(Collectors.joining("\n")) + " ]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((elements == null) ? 0 : elements.hashCode());
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
		Model other = (Model) obj;
		if (elements == null) {
			if (other.elements != null)
				return false;
		} else if (!elements.equals(other.elements))
			return false;
		return true;
	}

}
