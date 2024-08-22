package de.tu_dresden.lat.data;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.log4j.Logger;

import de.tu_dresden.inf.lat.evee.proofs.interfaces.IInference;
import de.tu_dresden.inf.lat.evee.proofs.interfaces.IProof;
import de.tu_dresden.lat.tools.MapTools;

/**
 * @author Christian Alrabbaa
 * @implNote A similar structure to {@link ProofPattern} where the inferences of
 *           the provided proof are stored in a structure guided by the
 *           structure of the provided pattern. </br>
 *           This class assumes that the root inference of the pattern and the
 *           proof have the same rule name</br>
 *           //TODO add an example of a proof and a small pattern that matches multiple times in the proof
 * @param <SENTENCE>
 */
public class ProofTemplate<SENTENCE> {
	private static final Logger logger = Logger.getLogger(ProofTemplate.class);

	private final IProof<SENTENCE> proof;
	private final ProofPattern<SENTENCE> pattern;

	private final Map<IInference<SENTENCE>, Set<IInference<SENTENCE>>> conclusionInference2PremiseInferences;
	private final Map<IInference<SENTENCE>, Set<IInference<SENTENCE>>> premiseInference2ConclusionInferences;

	private final Map<Integer, Map<Integer, Set<IInference<SENTENCE>>>> level2RuleIDsToInferences;

	public ProofTemplate(ProofPattern<SENTENCE> pattern, IProof<SENTENCE> proof) {
		this.proof = proof;
		this.pattern = pattern;

		this.conclusionInference2PremiseInferences = new HashMap<>();
		this.premiseInference2ConclusionInferences = new HashMap<>();

		this.level2RuleIDsToInferences = new HashMap<>();

		//TODO have a check and better with exception
		IInference<SENTENCE> currentInf = getCurrentInf(this.proof.getFinalConclusion()).get();
		fillTemplate(0, currentInf);

		logger.info("Template object generated!");
	}

	private boolean fillTemplate(Integer level, IInference<SENTENCE> currentInf) {
		Optional<IInference<SENTENCE>> premiseInfOpt;
		Map<Integer, Set<IInference<SENTENCE>>> ruleID2Inferences = new HashMap<>();

		if (level > this.pattern.getMaxLevel())
			return true;

		boolean isMatch = false;

		for (int ruleID : this.pattern.getLevel2RulesIDsInLevel().get(level)) {
			if (this.pattern.getId2RuleName().get(ruleID).equals(currentInf.getRuleName())) {
				MapTools.update(ruleID, currentInf, ruleID2Inferences);

				if (currentInf.getPremises().isEmpty() || level == this.pattern.getMaxLevel())
					isMatch = true;

				for (SENTENCE premise : currentInf.getPremises()) {
					premiseInfOpt = getCurrentInf(premise);

					if (premiseInfOpt.isPresent()) {

						if (fillTemplate(level + 1, premiseInfOpt.get())) {
							isMatch = true;

							MapTools.update(currentInf, premiseInfOpt.get(), this.conclusionInference2PremiseInferences);
							MapTools.update(premiseInfOpt.get(), currentInf,
									this.premiseInference2ConclusionInferences);
						}
					}
				}
			}
		}
		if (!isMatch)
			return false;

		if (this.level2RuleIDsToInferences.containsKey(level)) {
			ruleID2Inferences.keySet().forEach(key -> {
				ruleID2Inferences.get(key).forEach(val -> {
					MapTools.update(key, val, this.level2RuleIDsToInferences.get(level));
				});
			});
		} else
			this.level2RuleIDsToInferences.put(level, new HashMap<>(ruleID2Inferences));

		return isMatch;

	}

	private Optional<IInference<SENTENCE>> getCurrentInf(SENTENCE sentence) {
		assert this.proof.getInferences(sentence).size() <= 1 : "The input IProof is not a proof! " + sentence
				+ " can be proven in multiple ways\n" + this.proof.getInferences(sentence);

		if (this.proof.getInferences(sentence).size() == 0)
			return Optional.empty();

		return Optional.of(this.proof.getInferences(sentence).iterator().next());
	}

	public IProof<SENTENCE> getProof() {
		return proof;
	}

	public ProofPattern<SENTENCE> getPattern() {
		return pattern;
	}

	public Map<IInference<SENTENCE>, Set<IInference<SENTENCE>>> getConclusionInference2PremiseInferences() {
		return conclusionInference2PremiseInferences;
	}

	public Map<Integer, Map<Integer, Set<IInference<SENTENCE>>>> getLevel2RuleIDsToInferences() {
		return level2RuleIDsToInferences;
	}

	public Map<IInference<SENTENCE>, Set<IInference<SENTENCE>>> getPremiseInference2ConclusionInferences() {
		return premiseInference2ConclusionInferences;
	}

	public boolean eval(Set<Boolean> vals) {
		if (vals.size() > 1)
			return false;
		return vals.iterator().next();
	}
}
