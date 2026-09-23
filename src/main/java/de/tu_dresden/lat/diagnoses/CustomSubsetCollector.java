package de.tu_dresden.lat.diagnoses;

import java.util.Collection;
import java.util.Set;

import org.liveontologies.puli.pinpointing.MinimalSubsetCollector;
import org.semanticweb.owlapi.model.OWLAxiom;

public class CustomSubsetCollector<E> extends MinimalSubsetCollector<E> {


	public CustomSubsetCollector(Collection<Set<? extends E>> sets) {
		super(sets);
	}

	@Override
	public void newMinimalSubset(final Set<E> set) {
		// Add the set to the collector
		super.newMinimalSubset(set);

		// Add to the queue
		try {
			ComputeRepair.justificationQueue.put((Set<? extends OWLAxiom>) set);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
	}

}

