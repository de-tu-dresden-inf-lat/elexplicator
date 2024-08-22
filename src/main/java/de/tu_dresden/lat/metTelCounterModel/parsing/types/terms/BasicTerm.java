package de.tu_dresden.lat.metTelCounterModel.parsing.types.terms;


import de.tu_dresden.inf.lat.model.interfaces.IInstance;

/**
 * @author Christian Alrabbaa
 *
 */
public class BasicTerm implements IInstance {

	private final String value;

	public BasicTerm(String val) {
		value = val;
	}

	public String getValue() {
		return value;
	}

	@Override
	public String toString() {
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
		BasicTerm other = (BasicTerm) obj;
		if (value == null) {
			if (other.value != null)
				return false;
		} else if (!value.equals(other.value))
			return false;
		return true;
	}
}
