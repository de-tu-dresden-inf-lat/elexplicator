package de.tu_dresden.lat.diagnoses;
import java.util.Set;

import org.semanticweb.HermiT.ReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;

import uk.ac.manchester.cs.owlapi.modularity.ModuleType;
import uk.ac.manchester.cs.owlapi.modularity.SyntacticLocalityModuleExtractor;

public class SegmenterHermit{

    public static OWLOntology getStarModule(OWLOntology ontology, Set<OWLEntity> concept, IRI iri)
			throws OWLOntologyCreationException {

		OWLReasonerFactory factory = new ReasonerFactory();
		OWLReasoner reasoner = factory.createReasoner(ontology);
		SyntacticLocalityModuleExtractor extractor = new SyntacticLocalityModuleExtractor(
				OWLManager.createOWLOntologyManager(), ontology, ModuleType.STAR);
		return extractor.extractAsOntology(concept, iri, 0, 0, reasoner);
	}

}
