package de.tu_dresden.lat.tools;

import java.util.Collection;

import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLEntity;

import de.tu_dresden.inf.lat.evee.data.ProofType;
import de.tu_dresden.inf.lat.evee.proofs.data.exceptions.ProofGenerationFailedException;
import de.tu_dresden.inf.lat.evee.proofs.interfaces.IProof;
import de.tu_dresden.inf.lat.evee.proofs.proofGenerators.TreeProofGenerator;

/**
 * @author Christian Alrabbaa
 *
 */
public class ProofRefiner {

	public static IProof<OWLAxiom> refineProof(IProof<OWLAxiom> inputProof, ProofType type,
			Collection<OWLEntity> signature) throws ProofGenerationFailedException {

		if (type == ProofType.TreeUnravellingOfMinimalSizeGraph)
			return TreeProofGenerator.getTreeUnravelOfMinHypProof(inputProof);

		//

		if (type == ProofType.MinimalTreeSize)
			return TreeProofGenerator.getMinimalTreeSizeProof(inputProof);

		if (type == ProofType.CondensedMinimalTreeSize) {
			if (signature == null)
				throw new NullPointerException("Signature is missing!");
			return TreeProofGenerator.getCondensedMinimalTreeSizeProof(inputProof, signature);
		}

		//

		if (type == ProofType.MinimalWeightedTreeSize)
			return TreeProofGenerator.getMinimalWeightedTreeSizeProof(inputProof);

		if (type == ProofType.CondensedMinimalWeightedTreeSize) {
			if (signature == null)
				throw new NullPointerException("Signature is missing!");
			return TreeProofGenerator.getCondensedMinimalWeightedTreeSizeProof(inputProof, signature);
		}

		//

		if (type == ProofType.MinimalDepth)
			return TreeProofGenerator.getMinimalDepthProof(inputProof);

		if (type == ProofType.CondensedMinimalDepth) {
			if (signature == null)
				throw new NullPointerException("Signature is missing!");
			return TreeProofGenerator.getCondensedMinimalDepthProof(inputProof, signature);
		}

		assert false : "Should not be here!";
		return null;
	}

}
