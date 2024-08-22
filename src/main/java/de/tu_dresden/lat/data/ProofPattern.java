package de.tu_dresden.lat.data;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import de.tu_dresden.inf.lat.evee.proofs.interfaces.IInference;
import de.tu_dresden.inf.lat.evee.proofs.interfaces.IProof;
import de.tu_dresden.lat.tools.MapTools;

/**
 * @author Christian Alrabbaa
 * @implNote A pattern is a sequence of rule applications in a proof.</br>
 *           <i>Note this does not take the axioms patterns into
 *           consideration</i>.</br>
 *           </br>
 * 
 *           Example: consider the following {@link IProof} with "Axiom5" as its
 *           final conclusion</br>
 *           </br>
 * 
 *           =Asserted=> Axiom1</br>
 *           =Known=> Axiom2</br>
 *           </br>
 * 
 *           Axiom1 =Normalise=> Axiom3</br>
 *           =Tautology=> Axiom4</br>
 *           Axiom2 =SomeRule=> Axiom5</br>
 *           </br>
 * 
 *           Axiom3, Axiom4, Axiom5 =AnotherRule=> Axiom6</br>
 *           </br>
 * 
 *           The extracted pattern is then the following linked map:</br>
 * 
 *           0 -> {0:AnotherRule -> {1, 2, 3}}</br>
 *           1 -> {1:Tautology -> {}, 2:Normalise -> {4}, 3:SomeRule ->
 *           {5}}</br>
 *           2 -> {4:Asserted -> {}, 5:Known -> {}}</br>
 * @param <SENTENCE>
 */
public class ProofPattern<SENTENCE> {
	private static final Logger logger = Logger.getLogger(ProofPattern.class);

	private final HashMap<Integer, String> id2RuleName;
	private final HashMap<IInference<SENTENCE>, Integer> inference2ID;
	private final HashMap<Integer, Set<Integer>> level2RulesIDsInLevel;

	private final HashMap<Integer, Set<Integer>> id2Premises;
	private final HashMap<Integer, Set<Integer>> id2Conclusions;

	private int id;

	public ProofPattern(IProof<SENTENCE> proof) {
		this.id2RuleName = new HashMap<>();
		this.inference2ID = new HashMap<>();
		this.level2RulesIDsInLevel = new HashMap<>();

		this.id2Premises = new HashMap<>();
		this.id2Conclusions = new HashMap<>();

		this.id = 0;

		proof.getInferences().forEach(inf -> {
			this.id2RuleName.put(id, inf.getRuleName());
			this.inference2ID.put(inf, id++);
		});

		Map<Integer, Set<IInference<SENTENCE>>> processed = new HashMap<>();
		extractPattern(proof, Arrays.asList(proof.getFinalConclusion()), processed, 0);

		logger.info("Pattern object generated!");

//		System.out.println("provided proof -> ");
//		proof.getInferences().forEach(System.out::println);
//
//		System.out.println("id2RuleName");
//		id2RuleName.entrySet().forEach(System.out::println);
//
//		System.out.println("id2Premises");
//		id2Premises.entrySet().forEach(System.out::println);
//
//		System.out.println("id2Conclusions");
//		id2Conclusions.entrySet().forEach(System.out::println);
//
//		System.out.println("RulesInEachLevel");
//		level2RulesIDsInLevel.entrySet().forEach(System.out::println);
	}

	private void extractPattern(IProof<SENTENCE> proof, List<? extends SENTENCE> conclusionsAtLevel,
			Map<Integer, Set<IInference<SENTENCE>>> processedPerLevel, int level) {

		for (SENTENCE conclusion : conclusionsAtLevel) {
			proof.getInferences(conclusion).forEach(inf -> {

				if (processedPerLevel.containsKey(level)) {
					if (processedPerLevel.get(level).contains(inf))
						return;
					processedPerLevel.get(level).add(inf);
				} else
					processedPerLevel.put(level, new HashSet<>(Arrays.asList(inf)));

				if (!this.level2RulesIDsInLevel.containsKey(level))
					this.level2RulesIDsInLevel.put(level, new HashSet<>(Arrays.asList(this.inference2ID.get(inf))));
				else
					this.level2RulesIDsInLevel.get(level).add(this.inference2ID.get(inf));

				IInference<SENTENCE> currentPremiseInf;
				for (int i = 0; i < inf.getPremises().size(); i++) {
					if (proof.getInferences(inf.getPremises().get(i)).size() == 1) {
						currentPremiseInf = proof.getInferences(inf.getPremises().get(i)).iterator().next();

						MapTools.update(this.inference2ID.get(currentPremiseInf), this.inference2ID.get(inf),
								this.id2Conclusions);
						MapTools.update(this.inference2ID.get(inf), this.inference2ID.get(currentPremiseInf),
								this.id2Premises);
					}

					extractPattern(proof, inf.getPremises(), processedPerLevel, level + 1);
				}

			});
		}

	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id2Conclusions == null) ? 0 : id2Conclusions.hashCode());
		result = prime * result + ((id2Premises == null) ? 0 : id2Premises.hashCode());
		result = prime * result + ((id2RuleName == null) ? 0 : id2RuleName.hashCode());
		result = prime * result + ((inference2ID == null) ? 0 : inference2ID.hashCode());
		result = prime * result + ((level2RulesIDsInLevel == null) ? 0 : level2RulesIDsInLevel.hashCode());
		return result;
	}

	@SuppressWarnings("rawtypes")
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ProofPattern other = (ProofPattern) obj;
		if (id2Conclusions == null) {
			if (other.id2Conclusions != null)
				return false;
		} else if (!id2Conclusions.equals(other.id2Conclusions))
			return false;
		if (id2Premises == null) {
			if (other.id2Premises != null)
				return false;
		} else if (!id2Premises.equals(other.id2Premises))
			return false;
		if (id2RuleName == null) {
			if (other.id2RuleName != null)
				return false;
		} else if (!id2RuleName.equals(other.id2RuleName))
			return false;
		if (inference2ID == null) {
			if (other.inference2ID != null)
				return false;
		} else if (!inference2ID.equals(other.inference2ID))
			return false;
		if (level2RulesIDsInLevel == null) {
			if (other.level2RulesIDsInLevel != null)
				return false;
		} else if (!level2RulesIDsInLevel.equals(other.level2RulesIDsInLevel))
			return false;
		return true;
	}

	public HashMap<Integer, String> getId2RuleName() {
		return id2RuleName;
	}

	public HashMap<IInference<SENTENCE>, Integer> getInference2ID() {
		return inference2ID;
	}

	public HashMap<Integer, Set<Integer>> getId2Premises() {
		return id2Premises;
	}

	public Set<Integer> getRulesIDsInAllLevel() {
		Set<Integer> res = new HashSet<>();
		this.level2RulesIDsInLevel.entrySet().forEach(x -> res.addAll(x.getValue()));
		return res;
	}

	public HashMap<Integer, Set<Integer>> getLevel2RulesIDsInLevel() {
		return level2RulesIDsInLevel;
	}

	public int getMaxLevel() {
		return this.level2RulesIDsInLevel.keySet().size() - 1;
	}

	public HashMap<Integer, Set<Integer>> getId2Conclusions() {
		return id2Conclusions;
	}
}
