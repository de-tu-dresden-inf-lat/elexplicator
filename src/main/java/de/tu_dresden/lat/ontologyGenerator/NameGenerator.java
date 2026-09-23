package de.tu_dresden.lat.ontologyGenerator;

import de.tu_dresden.inf.lat.model.tools.ToOWLTools;
import org.semanticweb.owlapi.model.*;

/**
 * A helper class to generate concept and role names
 *
 * @author Christian Alrabbaa
 */
public class NameGenerator {

    private static final String iriPrefix = "https://nameGenerator#",
            prefixConceptName = iriPrefix + "B",
            prefixRoleName = iriPrefix + "r";
    private int conceptCounter = 0, roleCounter = 0;
    private NameGenerator(){}

    private static class LazyHolder {
        static NameGenerator instance = new NameGenerator();
    }

    public static NameGenerator getInstance() {
        return NameGenerator.LazyHolder.instance;
    }

    public void resetAllCounters() {
        this.resetConceptNameCounter();
        this.resetRoleNameCounter();
    }

    public void resetRoleNameCounter() {
        this.roleCounter = 0;
    }

    public void resetConceptNameCounter() {
        this.conceptCounter = 0;
    }

    public OWLClass getNextConceptName(){
        String name = conceptCounter>0 ? prefixConceptName + conceptCounter: prefixConceptName;
        conceptCounter++;
        return ToOWLTools.getInstance().getOWLConceptName(name);
    }

    public OWLClass getAsNameGeneratorConceptName(String conceptNameStr){
        return ToOWLTools.getInstance().getOWLConceptName(iriPrefix + conceptNameStr);
    }

    public OWLObjectProperty getNextRoleName(){
        String name = roleCounter>0 ? prefixRoleName + roleCounter: prefixRoleName;
        roleCounter++;
        return ToOWLTools.getInstance().getPropertyName(name);
    }

    public OWLObjectProperty getAsNameGeneratorRoleName(String roleNameStr){
        return ToOWLTools.getInstance().getPropertyName(iriPrefix + roleNameStr);
    }
}
