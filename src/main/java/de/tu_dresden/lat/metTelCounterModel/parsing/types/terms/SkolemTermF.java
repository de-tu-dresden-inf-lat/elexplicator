package de.tu_dresden.lat.metTelCounterModel.parsing.types.terms;

import de.tu_dresden.inf.lat.model.interfaces.IConcept;
import de.tu_dresden.inf.lat.model.interfaces.IInstance;
import de.tu_dresden.inf.lat.model.interfaces.IRole;

/**
 * @author Christian Alrabbaa
 *
 */
public class SkolemTermF implements IInstance {

	private final IInstance ind;
	private final IRole role;
	private final IConcept clas;

	public SkolemTermF(IInstance i, IRole r, IConcept exp) {
		ind = i;
		role = r;
		clas = exp;
	}

	public IInstance getIndividual() {
		return ind;
	}

	public IRole getRole() {
		return role;
	}

	public IConcept getClassExpression() {
		return clas;
	}

	@Override
	public String toString() {
		return "f( " + ind + ", " + role + ", " + clas + " )";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((clas == null) ? 0 : clas.hashCode());
		result = prime * result + ((ind == null) ? 0 : ind.hashCode());
		result = prime * result + ((role == null) ? 0 : role.hashCode());
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
		SkolemTermF other = (SkolemTermF) obj;
		if (clas == null) {
			if (other.clas != null)
				return false;
		} else if (!clas.equals(other.clas))
			return false;
		if (ind == null) {
			if (other.ind != null)
				return false;
		} else if (!ind.equals(other.ind))
			return false;
		if (role == null) {
			if (other.role != null)
				return false;
		} else if (!role.equals(other.role))
			return false;
		return true;
	}

}
