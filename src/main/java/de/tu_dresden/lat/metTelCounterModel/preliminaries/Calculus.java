package de.tu_dresden.lat.metTelCounterModel.preliminaries;

import java.io.FileOutputStream;
import java.util.LinkedList;
import java.util.List;

import de.tu_dresden.inf.lat.model.interfaces.IData;
import de.tu_dresden.inf.lat.model.tools.GeneralTools;

/**
 * @author Christian Alrabbaa
 *
 */
public class Calculus implements IData {

	private static final List<String> initRules = getinitRules();

	private Calculus() {

	}

	private static class LazyHolder {
		static Calculus instance = new Calculus();
	}

	public static Calculus getInstance() {
		return LazyHolder.instance;
	}

	/**
	 * @param outStream
	 */
	public void appendALCOInitCalculus(FileOutputStream outStream) {

		GeneralTools.writeCollectionTo(initRules, outStream);
	}

	/**
	 * @return
	 */
	private static final List<String> getinitRules() {
		List<String> res = new LinkedList<>();

		// res.add("tableau " + NAME + "{\n");
		res.add("// Default Rules \n\n");
		// Equality Rules

		res.add("@l {l2} / @l2 {l} priority 1$;\n");
		res.add("@l ~{l2} / @l2 {l2} priority 1$;\n");

		res.add("@l P / @l {l} priority 1$;\n");

		res.add("@l exists r.{l2} / @l2 {l2} priority 1$;\n");
		res.add("@l P  @l {l2} / @l2 P priority 2$;\n");
		res.add("@l exists r.{l2}  @l2 {l3} / @l exists r.{l3} priority 2$;\n");

		// Decomposition Rules

		res.add("@l ~(~P) / @l P priority 1$;\n");
		res.add("@l (P|Q) / @l P $| @l Q priority 3$;\n");
		res.add("@l ~(P|Q) / @l ~P @l ~Q priority 1$;\n");
		res.add("@l (P&Q) / @l P @l Q priority 1$;\n");
		res.add("@l ~(P&Q) / @l ~P $| @l ~Q priority 3$;\n");
		res.add("@l (P -> Q) / @l ~P $| @l Q priority 3$;\n");
		res.add("@l ~(P -> Q) / @l P  @l ~Q priority 1$;\n");
		res.add("@l (P <-> Q) / @l P @l Q $| @l ~P @l ~Q priority 4$;\n");
		res.add("@l ~(P <-> Q) / @l P  @l ~Q $| @l ~P @l Q priority 4$;\n");

		res.add("@l forall r.P / @l ~(exists r.~P) priority 1$;\n");
		res.add("@l ~(forall r.P) / @l exists r.~P priority 1$;\n");

		res.add("@l exists r.P / @l exists r.{f(l,r,P)} @f(l,r,P) P priority 7$;\n");
		res.add("@l ~(exists r.P) @l exists r.{l2} / @l2 ~P priority 2$;\n");

		// Roles
		res.add("@l exists r;s.{l2} / @l exists r.{g(l,l2,r,s)} @g(l,l2,r,s) {l2} priority 7$;\n");
		res.add("@l ~(exists r;s.P) / @l ~(exists r.(exists s.P)) priority 1$;\n");

		res.add("@l exists r|s.{l2} / @l exists r.{l2} $| @l exists s.{l2} priority 3$;\n");
		res.add("@l ~(exists r|s.P) / @l ~(exists r.P) @l ~(exists s.P) priority 1$;\n");

		res.add("@l exists r-.{l2} / @l2 exists r.{l} priority 1$;\n");
		res.add("@l ~(exists r-.P) @l2 exists r.{l} / @l2 ~P priority 2$;\n");
		res.add("@l ~(exists (r|s)-.P) / @l ~(exists (r-)|(s-).P) priority 1$;\n");
		res.add("@l ~(exists (r;s)-.P) / @l ~(exists (s-);(r-).P) priority 1$;\n");

		// Closure Rule
		res.add("@l P  @l ~P /  priority 0$;\n");

		// Blocking
		res.add("@l{l0} / [l=l0] priority 1$;\n");
		res.add("@l{l} @l2{l2} / [l = l2] $| ~([l = l2]) priority 6$;\n");

		// Truth
		res.add("@l P / @l true priority 5 $;\n");
		res.add("@l P / @l ~(false) priority 5 $;\n");// }\n");

		return res;
	}
}
