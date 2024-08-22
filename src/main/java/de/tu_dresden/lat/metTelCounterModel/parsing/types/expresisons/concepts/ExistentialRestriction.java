package de.tu_dresden.lat.metTelCounterModel.parsing.types.expresisons.concepts;


import de.tu_dresden.inf.lat.model.interfaces.IConcept;
import de.tu_dresden.inf.lat.model.interfaces.IRole;

/**
 * @author Christian Alrabbaa
 *
 */
public class ExistentialRestriction implements IConcept {

	private final IRole role;
	private final IConcept clas;

	public ExistentialRestriction(IRole r, IConcept exp) {
		role = r;
		clas = exp;
	}

	public IRole getRole() {
		return role;
	}

	public IConcept getConcept() {
		return clas;
	}

	@Override
	public String toString() {
		return "exists " + role + " . ( " + clas + " )";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((clas == null) ? 0 : clas.hashCode());
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
		ExistentialRestriction other = (ExistentialRestriction) obj;
		if (clas == null) {
			if (other.clas != null)
				return false;
		} else if (!clas.equals(other.clas))
			return false;
		if (role == null) {
			if (other.role != null)
				return false;
		} else if (!role.equals(other.role))
			return false;
		return true;
	}

}
