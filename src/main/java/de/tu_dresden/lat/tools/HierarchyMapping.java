package de.tu_dresden.lat.tools;

import org.semanticweb.owlapi.model.OWLClass;
import java.util.Map;
import java.util.Set;

public class HierarchyMapping {
    private Map<OWLClass, Set<OWLClass>> subClassMap;
    private Map<OWLClass, Set<OWLClass>> superClassMap;

    public HierarchyMapping(Map<OWLClass, Set<OWLClass>> subClassMap, Map<OWLClass, Set<OWLClass>> superClassMap) {
        this.subClassMap = subClassMap;
        this.superClassMap = superClassMap;
    }

    public Map<OWLClass, Set<OWLClass>> getSubClassMap() {
        if (subClassMap == null) {
            return new java.util.HashMap<>();
        }
        return subClassMap;
    }

    public Map<OWLClass, Set<OWLClass>> getSuperClassMap() {
        if (superClassMap == null) {
            return new java.util.HashMap<>();
        }
        return superClassMap;
    }
}