package de.tu_dresden.lat.proofRewriting;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;

import de.tu_dresden.inf.lat.evee.proofs.data.Proof;
import de.tu_dresden.inf.lat.evee.proofs.interfaces.IInference;
import de.tu_dresden.inf.lat.evee.proofs.interfaces.IProof;

/**
 * @author Christian Alrabbaa
 *
 */
public class ProofSlicer<SENTENCE> {
	private static final Logger logger = Logger.getLogger(ProofSlicer.class);

	private final IProof<SENTENCE> proof;

	public ProofSlicer(IProof<SENTENCE> proof) {
		this.proof = proof;
	}

	public Set<IProof<SENTENCE>> slice(String ruleName, int depth) {
		Set<IProof<SENTENCE>> res = new HashSet<>();
		Set<IInference<SENTENCE>> startingInfs = getStartingInferences(ruleName);

		Set<IInference<SENTENCE>> infs;
		for (IInference<SENTENCE> startingInf : startingInfs) {
			infs = new HashSet<>();
			slice(infs, startingInf, depth);
			res.add(new Proof<>(startingInf.getConclusion(), infs));
		}

		logger.info("Total number of proof slices = " + res.size());

		return res;
	}

	private Set<IInference<SENTENCE>> getStartingInferences(String ruleName) {
		return this.proof.getInferences().stream().filter(x -> x.getRuleName().equals(ruleName))
				.collect(Collectors.toSet());
	}

	private void slice(Set<IInference<SENTENCE>> inferences, IInference<SENTENCE> startingInf, int depth) {
		inferences.add(startingInf);

		if (depth == 0)
			return;

		for (SENTENCE premise : startingInf.getPremises())
			for (IInference<SENTENCE> inf : this.proof.getInferences(premise)) {
				slice(inferences, inf, depth - 1);
			}
	}

}
