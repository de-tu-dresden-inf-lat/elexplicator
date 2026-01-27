package de.tu_dresden.lat.tools;

import java.util.Set;

import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLOntology;

public class OWLOntologyContentKey {
    private final Set<OWLAxiom> axioms;
    private final int hash;
    private final OWLOntology ontology;

    public OWLOntologyContentKey(OWLOntology ontology) {
        this.axioms = ontology.getAxioms();
        this.hash = axioms.hashCode();
        this.ontology = ontology;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof OWLOntologyContentKey))
            return false;
        OWLOntologyContentKey other = (OWLOntologyContentKey) obj;
        return this.axioms.equals(other.axioms);
    }

    public OWLOntology getOntology() {
        return this.ontology;
    }
}
