package de.tu_dresden.lat.proofRewriting;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.github.jsonldjava.shaded.com.google.common.collect.Sets;
import org.apache.log4j.Logger;

import de.tu_dresden.inf.lat.evee.proofs.interfaces.IInference;
import de.tu_dresden.lat.data.ProofTemplate;

/**
 * @author Christian Alrabbaa
 *
 * @param <SENTENCE>
 */
public class PatternInstancesFinder<SENTENCE> {
	private static final Logger logger = Logger.getLogger(PatternInstancesFinder.class);

	private final ProofTemplate<SENTENCE> template;

	public PatternInstancesFinder(ProofTemplate<SENTENCE> template) {
		this.template = template;
	}

	/**
	 * @return a set of inferences representing the first match of the pattern in
	 * 	 * the template
	 */
	public Set<IInference<SENTENCE>> first() {
		return findFirstMatch().entrySet().stream().map(Entry::getValue).collect(Collectors.toSet());
	}

	/**
	 * @return a map representing the assignment of each of the pattern rule ids to
	 * 	 * an inference from the proof that corresponds to a match of the pattern
	 */
	public Map<Integer, IInference<SENTENCE>> firstAsMap() {
		return findFirstMatch();
	}

	private Map<Integer, IInference<SENTENCE>> findFirstMatch() {
		if (this.template.getProof().getInferences().isEmpty())
			return failed();

		Map<Integer, IInference<SENTENCE>> mapping = new HashMap<>();
		boolean successful;

		if (this.template.getPattern().getMaxLevel() == 0) {
			if (this.template.getLevel2RuleIDsToInferences().getOrDefault(0, new HashMap<>()).containsKey(0)) {
				mapping.put(0, this.template.getLevel2RuleIDsToInferences().get(0).get(0).iterator().next());
				return mapping;
			} else
				return failed();
		}

		for (int level = this.template.getPattern().getMaxLevel(); level > 0; level--) {

			for (int ruleID : this.template.getPattern().getLevel2RulesIDsInLevel().get(level)) {
				Set<IInference<SENTENCE>> conclusionInferences;
				if (!mapping.containsKey(ruleID)) {
					Optional<IInference<SENTENCE>> lOpt;

					lOpt = selectInference(level, ruleID, mapping);

					if (!lOpt.isPresent())
						return failed();

					IInference<SENTENCE> l = lOpt.get();

					conclusionInferences = this.template.getPremiseInference2ConclusionInferences().getOrDefault(l,
							Collections.emptySet());
				} else
					conclusionInferences = this.template.getPremiseInference2ConclusionInferences().get(mapping.get(ruleID));

				if (conclusionInferences == null)
					continue;
				if (conclusionInferences.isEmpty())
					continue;

				successful = mapInfs(level - 1, conclusionInferences, mapping);

				if (!successful)
					return failed();

				for (IInference<SENTENCE> c : conclusionInferences) {
					successful = mapInfs(level, this.template.getConclusionInference2PremiseInferences().get(c),
							mapping);

					if (!successful)
						return failed();
				}

			}
		}

		logger.info("Done matching!");

		return mapping;
	}

	private Map<Integer, IInference<SENTENCE>> failed() {
		logger.info("No match found!");
		return new HashMap<>();
	}

	private boolean mapInfs(int maxLevel, Set<IInference<SENTENCE>> infs, Map<Integer, IInference<SENTENCE>> mapping) {

		Set<IInference<SENTENCE>> used = new HashSet<>(mapping.values());
		infs.removeAll(used);

		boolean successful;

		for (IInference<SENTENCE> inf : infs) {

			successful = false;
			for (int level = maxLevel; level >= 0; level--) {
				for (int ruleID : this.template.getPattern().getLevel2RulesIDsInLevel().get(level)) {
					if (this.template.getLevel2RuleIDsToInferences().get(level).get(ruleID).contains(inf)) {
						mapping.put(ruleID, inf);

						successful = true;
						break;
					}
				}
			}

			if (!successful)
				return false;
		}

		return true;
	}

	private Optional<IInference<SENTENCE>> selectInference(int level, int ruleID,
			Map<Integer, IInference<SENTENCE>> mapping) {
		Set<IInference<SENTENCE>> possibilities = this.template.getLevel2RuleIDsToInferences()
				.getOrDefault(level, new HashMap<>()).getOrDefault(ruleID, Collections.emptySet());

		possibilities.removeAll(new HashSet<>(mapping.values()));

		if (possibilities.isEmpty())
			return Optional.empty();

		IInference<SENTENCE> res = null;
		Set<Integer> conIDs = this.template.getPattern().getId2Conclusions().getOrDefault(ruleID, new HashSet<>());

		Set<IInference<SENTENCE>> conInfs, instanceConInfs;

		for (int conID : conIDs) {
			for (IInference<SENTENCE> p : possibilities) {
				conInfs =
						Sets.newHashSet(this.template.getLevel2RuleIDsToInferences().get(level - 1).getOrDefault(conID, new HashSet<>()));

				instanceConInfs = this.template.getPremiseInference2ConclusionInferences().getOrDefault(p,
						new HashSet<>());
				conInfs.retainAll(instanceConInfs);
				if (!conInfs.isEmpty()) {
					res = p;
					break;
				}
			}

		}

		if (res == null)
			return Optional.empty();

		assert !mapping.containsKey(ruleID) : "This should not happen!";
		mapping.put(ruleID, res);

		return Optional.of(res);

	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((template == null) ? 0 : template.hashCode());
		return result;
	}

	@SuppressWarnings("unchecked")
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		PatternInstancesFinder<SENTENCE> other = (PatternInstancesFinder<SENTENCE>) obj;
		if (template == null) {
			if (other.template != null)
				return false;
		} else if (!template.equals(other.template))
			return false;
		return true;
	}

}