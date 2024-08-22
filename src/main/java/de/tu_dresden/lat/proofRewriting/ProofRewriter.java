package de.tu_dresden.lat.proofRewriting;

import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;

import de.tu_dresden.inf.lat.evee.proofs.data.Inference;
import de.tu_dresden.inf.lat.evee.proofs.data.Proof;
import de.tu_dresden.inf.lat.evee.proofs.interfaces.IInference;
import de.tu_dresden.inf.lat.evee.proofs.interfaces.IProof;
import de.tu_dresden.inf.lat.evee.proofs.tools.ProofTools;
import de.tu_dresden.lat.data.ProofPattern;
import de.tu_dresden.lat.data.ProofTemplate;
import de.tu_dresden.lat.exceptions.ProofRwritingException;

/**
 * @author Christian Alrabbaa
 *
 */
public class ProofRewriter {
	private static final Logger logger = Logger.getLogger(ProofRewriter.class);

	private int id;

	private ProofRewriter() {
		id = 0;
	}

	private static class LazyHolder {
		static ProofRewriter instance = new ProofRewriter();
	}

	public static ProofRewriter getInstance() {
		return LazyHolder.instance;
	}

	public void resetID() {
		id = 0;
	}

	public <SENTENCE> IProof<SENTENCE> rewrite(IProof<SENTENCE> proof, IProof<SENTENCE> patternAsProof)
			throws ProofRwritingException {

		return rewrite(proof, new ProofPattern<SENTENCE>(patternAsProof), "");
	}

	public <SENTENCE> IProof<SENTENCE> rewrite(IProof<SENTENCE> proof, IProof<SENTENCE> patternAsProof,
			String lemmaTitle) throws ProofRwritingException {

		return rewrite(proof, new ProofPattern<SENTENCE>(patternAsProof), lemmaTitle);
	}

	public <SENTENCE> IProof<SENTENCE> rewrite(IProof<SENTENCE> proof, ProofPattern<SENTENCE> pattern,
			String LemmaTitle) throws ProofRwritingException {

		if (!pattern.getLevel2RulesIDsInLevel().containsKey(0))
			throw new ProofRwritingException("Pattern must contain an inference at level 0!");

		String[] title = { LemmaTitle };
		IProof<SENTENCE> rewritten = proof;
		Set<IInference<SENTENCE>> inferences;
		int iteration = 1;
		while (true) {

			logger.info("Rewriting proof iteration " + iteration++);
			Map<Integer, IInference<SENTENCE>> firstmatchMap = getFirst(proof, pattern, title);

			if (firstmatchMap.isEmpty())
				break;

			IInference<SENTENCE> lemma = getLemma(firstmatchMap, pattern, title[0]);

			Set<IInference<SENTENCE>> toRemove = getInferencesToRemove(new HashSet<>(firstmatchMap.values()), proof);

			rewritten = new Proof<>(proof.getFinalConclusion());

			inferences = new HashSet<>(Collections.singletonList(lemma));

			proof.getInferences().stream().filter(x -> !toRemove.contains(x)).forEach(inferences::add);

			rewritten.addInferences(getReachableInferences(inferences, proof.getFinalConclusion()));

			inferences = new HashSet<>();

			proof = rewritten;

		}
		return rewritten;
	}

	private <SENTENCE> Set<IInference<SENTENCE>> getReachableInferences(Collection<IInference<SENTENCE>> inferences,
			SENTENCE finalConclusion) throws ProofRwritingException {
		Optional<IInference<SENTENCE>> finalInfOpt =
				inferences.stream().filter(x -> x.getConclusion().equals(finalConclusion))
				.findFirst();

		if (!finalInfOpt.isPresent())
			throw new ProofRwritingException("Could not get the final inference of the proof");

		Set<IInference<SENTENCE>> reachable = new HashSet<>(Collections.singletonList(finalInfOpt.get())),
				tmp = new HashSet<>();

		while (true) {
			for (IInference<SENTENCE> i : reachable) {
				for (SENTENCE p : i.getPremises()) {
					tmp.addAll(inferences.stream().filter(x -> x.getConclusion().equals(p) && !reachable.contains(x))
							.collect(Collectors.toSet()));
				}
			}

			if (tmp.isEmpty())
				break;
			else {
				reachable.addAll(tmp);
				tmp = new HashSet<>();
			}
		}

		return reachable;
	}

	private <SENTENCE> Set<IInference<SENTENCE>> getInferencesToRemove(Set<IInference<SENTENCE>> matchInferences,
			IProof<SENTENCE> proof) {
		Set<IInference<SENTENCE>> toRemove = new HashSet<>();

		Set<IInference<SENTENCE>> supporters;
		for (IInference<SENTENCE> inf : matchInferences) {
			supporters = getSupporters(inf, proof);

			supporters.retainAll(matchInferences);

			if (supporters.isEmpty())
				toRemove.add(inf);
		}

		return toRemove;
	}

	private <SENTENCE> Set<IInference<SENTENCE>> getSupporters(IInference<SENTENCE> inf, IProof<SENTENCE> proof) {
		return proof.getInferences().stream().filter(x -> x.getPremises().contains(inf.getConclusion()))
				.collect(Collectors.toSet());
	}

	private <SENTENCE> IInference<SENTENCE> getLemma(Map<Integer, IInference<SENTENCE>> firstmatchMap,
			ProofPattern<SENTENCE> pattern, String lemmaTitle) throws ProofRwritingException {
		if (!firstmatchMap.containsKey(0))
			throw new ProofRwritingException(
					"Match map does not contain a sink inference! key 0 is missing -> " + firstmatchMap.keySet());

		SENTENCE conclusion = firstmatchMap.get(0).getConclusion();

		List<SENTENCE> premise = new LinkedList<>();

		if (firstmatchMap.size() == 1)
			premise.addAll(firstmatchMap.get(0).getPremises());

		else {
			for (Entry<Integer, IInference<SENTENCE>> e : firstmatchMap.entrySet())
				for (SENTENCE s : e.getValue().getPremises())
					if (isLeaf(s, e.getKey(), pattern, firstmatchMap))
						premise.add(s);
		}

		premise.sort(Comparator.comparing(Object::toString));
		return new Inference<>(conclusion, lemmaTitle, premise);
	}

	/**
	 * A leaf is either a premise that has no proof in the pattern or a premise that
	 * is a conclusion of an "assert" inference
	 * 
	 * @param <SENTENCE>
	 * @param sentence
	 * @param ruleID
	 * @param pattern
	 * @param firstmatchMap
	 * @return
	 */
	private <SENTENCE> boolean isLeaf(SENTENCE sentence, Integer ruleID, ProofPattern<SENTENCE> pattern,
			Map<Integer, IInference<SENTENCE>> firstmatchMap) {

		for (int id : pattern.getId2Premises().getOrDefault(ruleID, new HashSet<>())) {
			if (firstmatchMap.get(id).getConclusion().equals(sentence)) {
				if (ProofTools.isAsserted(firstmatchMap.get(id)))
					return true;
				return false;
			}
		}
		return true;
	}

	private <SENTENCE> Map<Integer, IInference<SENTENCE>> getFirst(IProof<SENTENCE> proof,
			ProofPattern<SENTENCE> pattern, String[] lemmaTitle) {
		ProofSlicer<SENTENCE> s = new ProofSlicer<>(proof);
		Set<IProof<SENTENCE>> proofSlices = s.slice(pattern.getId2RuleName().get(0), pattern.getMaxLevel());

		ProofTemplate<SENTENCE> t;
		PatternInstancesFinder<SENTENCE> f;
		Map<Integer, IInference<SENTENCE>> matchMap = new HashMap<>();

		for (IProof<SENTENCE> pS : proofSlices) {
			t = new ProofTemplate<>(pattern, pS);
			f = new PatternInstancesFinder<>(t);

			matchMap = f.firstAsMap();

			if (!matchMap.isEmpty()) {
				getLemmaTitle(lemmaTitle, pS);
				break;
			}
		}

		return matchMap;
	}

	private <SENTENCE> void getLemmaTitle(String[] lemmaTitle, IProof<SENTENCE> proofSlice) {

		if (!lemmaTitle[0].equals(""))
			return;

		Set<String> ruleNames = proofSlice.getInferences().stream().map(IInference::getRuleName)
				.collect(Collectors.toSet());

		if (ruleNames.size() == 1)
			lemmaTitle[0] = ruleNames.iterator().next();
		else {
			lemmaTitle[0] = "Lemma " + getInstance().id;
			getInstance().id++;
		}
	}
}
