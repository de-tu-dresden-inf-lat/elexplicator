package de.tu_dresden.lat.diagnoses;

import static org.semanticweb.owlapi.util.OWLAPIPreconditions.checkNotNull;

import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;

import com.clarkparsia.owlapi.explanation.BlackBoxExplanation;
import com.clarkparsia.owlapi.explanation.DefaultExplanationGenerator;
import com.clarkparsia.owlapi.explanation.MultipleExplanationGenerator;
import com.clarkparsia.owlapi.explanation.util.ExplanationProgressMonitor;

public class CustomDefaultExplanationGenerator extends DefaultExplanationGenerator {

    @Nonnull
    private final OWLDataFactory dataFactory;
    @Nonnull
    private final MultipleExplanationGenerator gen;


    /**
     * Instantiates a new customized default explanation generator that uses CustomHSTExplanationGenerator.
     * 
     * @param man
     *        manager
     * @param reasonerFactory
     *        reasoner factory
     * @param ontology
     *        ontology to reason on
     * @param reasoner
     *        the reasoner to use
     * @param progressMonitor
     *        progress monitor
     */
    public CustomDefaultExplanationGenerator(@Nonnull OWLOntologyManager man,
            @Nonnull OWLReasonerFactory reasonerFactory,
            @Nonnull OWLOntology ontology, @Nonnull OWLReasoner reasoner,
            @Nullable ExplanationProgressMonitor progressMonitor) {
        super(man, reasonerFactory, ontology, reasoner, progressMonitor);
        dataFactory = checkNotNull(man, "man cannot be null")
                .getOWLDataFactory();
        BlackBoxExplanation singleGen = new BlackBoxExplanation(checkNotNull(
                ontology, "ontology cannot be null"), checkNotNull(
                reasonerFactory, "reasonerFactory cannot be null"),
                checkNotNull(reasoner, "reasoner cannot be null"));
        gen = new CustomHSTExplanationGenerator(singleGen);
        if (progressMonitor != null) {
            gen.setProgressMonitor(progressMonitor);
        }
    }

    @Override
    public Set<Set<OWLAxiom>> getExplanations(OWLClassExpression unsatClass) {
        return gen.getExplanations(unsatClass);
    }

    @Override
    public Set<Set<OWLAxiom>> getExplanations(OWLClassExpression unsatClass,
            int maxExplanations) {
        return gen.getExplanations(unsatClass, maxExplanations);
    }
    
}
