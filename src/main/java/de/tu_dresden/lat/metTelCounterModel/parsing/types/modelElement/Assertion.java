package de.tu_dresden.lat.metTelCounterModel.parsing.types.modelElement;

import de.tu_dresden.inf.lat.model.interfaces.IExpression;
import de.tu_dresden.inf.lat.model.interfaces.IInstance;

/**
 * @author Christian Alrabbaa
 *
 */
public class Assertion extends ModelElement {

	private final IExpression exp;
	private final IInstance objct;

	public Assertion(IInstance ind, IExpression exp) {
		this.exp = exp;
		objct = ind;
	}

	public IExpression getExpression() {
		return exp;
	}

	public IInstance getIndividual() {
		return objct;
	}

	@Override
	public String toString() {
		return "( ( " + exp + " ) ( " + objct + " ) )";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((exp == null) ? 0 : exp.hashCode());
		result = prime * result + ((objct == null) ? 0 : objct.hashCode());
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
		Assertion other = (Assertion) obj;
		if (exp == null) {
			if (other.exp != null)
				return false;
		} else if (!exp.equals(other.exp))
			return false;
		if (objct == null) {
			if (other.objct != null)
				return false;
		} else if (!objct.equals(other.objct))
			return false;
		return true;
	}

}
