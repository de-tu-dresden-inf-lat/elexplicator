package de.tu_dresden.lat.metTelCounterModel.parsing.types.expresisons.concepts;

import de.tu_dresden.inf.lat.model.interfaces.IConcept;
import de.tu_dresden.inf.lat.model.interfaces.IInstance;

/**
 * @author Christian Alrabbaa
 *
 */
public class Nominal implements IConcept {

	private final IInstance value;

	public Nominal(IInstance val) {
		value = val;
	}

	@Override
	public String toString() {
		return "{ " + value + " }";
	}

	public IInstance getValue() {
		return value;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((value == null) ? 0 : value.hashCode());
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
		Nominal other = (Nominal) obj;
		if (value == null) {
			if (other.value != null)
				return false;
		} else if (!value.equals(other.value))
			return false;
		return true;
	}

}
