package de.tu_dresden.lat.ontologyGenerator;

import com.google.common.collect.Lists;
import org.semanticweb.owlapi.model.OWLAxiom;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A helper class for the ontology generator to store common data for all justifications
 *
 * @author Christian Alrabbaa
 */
public class GeneratorHelper {
    private final List<OWLAxiom> commonAxioms;
    private final Map<OWLAxiom, Integer> axiom2CurrentChainDepth;

    public GeneratorHelper() {
        this.commonAxioms = Lists.newLinkedList();
        this.axiom2CurrentChainDepth = new HashMap<>();
    }

    public void addCommonAxiom(OWLAxiom axiom){
        this.commonAxioms.add(axiom);
    }

    public List<OWLAxiom> getCommonAxiomsList() {
        return commonAxioms;
    }

    public Map<OWLAxiom, Integer> getAxiom2CurrentChainDepthMap() {
        return axiom2CurrentChainDepth;
    }

    public void updateMap(OWLAxiom axiom, int depth){
        if(this.axiom2CurrentChainDepth.containsKey(axiom))
            throw new RuntimeException("Axiom already mapped!");

        this.axiom2CurrentChainDepth.put(axiom,depth);
    }

    public int getDepthAt(OWLAxiom axiom){
        return this.axiom2CurrentChainDepth.get(axiom);
    }

}
