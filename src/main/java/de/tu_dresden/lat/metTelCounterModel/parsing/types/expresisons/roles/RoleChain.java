package de.tu_dresden.lat.metTelCounterModel.parsing.types.expresisons.roles;

import de.tu_dresden.inf.lat.model.interfaces.IRole;

import java.util.LinkedList;

import java.util.List;
import java.util.stream.Collectors;



/**
 * @author Christian Alrabbaa
 *
 */
public class RoleChain implements IRole {

	private final List<? extends IRole> roles;

	/**
	 * @param roles List<IRole>
	 */
	public RoleChain(List<? extends IRole> roles) {
		this.roles = new LinkedList<>(roles);
	}

	public List<? extends IRole> getRoles() {
		return roles;
	}

	@Override
	public String toString() {

		return "( " + roles.stream().map(IRole::toString).collect(Collectors.joining(" ; ")) + " )";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((roles == null) ? 0 : roles.hashCode());
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
		RoleChain other = (RoleChain) obj;
		if (roles == null) {
			if (other.roles != null)
				return false;
		} else if (!roles.equals(other.roles))
			return false;
		return true;
	}

}
