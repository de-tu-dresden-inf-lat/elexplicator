package de.tu_dresden.lat.metTelCounterModel.parsing.types.modelElement;

import de.tu_dresden.inf.lat.model.interfaces.IEqual;
import de.tu_dresden.inf.lat.model.interfaces.IInstance;

/**
 * @author Christian Alrabbaa
 *
 */
public class IndividualsEquality extends ModelElement implements IEqual {

	private final IInstance lhs;
	private final IInstance rhs;

	public IndividualsEquality(IInstance left, IInstance right) {
		lhs = left;
		rhs = right;
	}

	@Override
	public String toString() {
		return "[ " + lhs + " = " + rhs + " ]";
	}

	public IInstance getLeftHandSide() {
		return lhs;
	}

	public IInstance getRightHandSide() {
		return rhs;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((lhs == null) ? 0 : lhs.hashCode());
		result = prime * result + ((rhs == null) ? 0 : rhs.hashCode());
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
		IndividualsEquality other = (IndividualsEquality) obj;
		if (lhs == null) {
			if (other.lhs != null)
				return false;
		} else if (!lhs.equals(other.lhs))
			return false;
		if (rhs == null) {
			if (other.rhs != null)
				return false;
		} else if (!rhs.equals(other.rhs))
			return false;
		return true;
	}
}
