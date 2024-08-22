package de.tu_dresden.lat.evonne;

import de.tu_dresden.lat.data.cli.CLIOptionsStrings;
import de.tu_dresden.lat.data.cli.CLIOptions;
import org.apache.commons.cli.*;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.OWLXMLDocumentFormat;
import org.semanticweb.owlapi.model.*;

import java.io.File;

/**
 * @author Christian Alrabbaa
 *
 */

public class EvonneOntologyConverter {
    private static final CLIOptions myOpts = CLIOptions.getInstance();

    public static void main(String[] args){
        Options options = new Options();

        options.addOption(myOpts.ontologyPathOptionREQUIRED);

        options.addOption(myOpts.outDirOptionREQUIRED);

        options.addOption(myOpts.outFileLabelOptionREQUIRED);

        CommandLine cmd = null;

        try {
            cmd = myOpts.parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            myOpts.formatter.printHelp("utility-name", options);

            System.exit(1);
        }

        String ontologyPathStr = cmd.getOptionValue(CLIOptionsStrings.ontologyPathOptionLong);
        String outputDirectoryPathStr = cmd.getOptionValue(CLIOptionsStrings.outDirOptionLong);
        String outputFilesNameStr = cmd.getOptionValue(CLIOptionsStrings.outFileLabelOptionLong);

        OWLOntology ontology;
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        try {
            ontology = manager.loadOntologyFromOntologyDocument(new File(ontologyPathStr));
            manager.saveOntology(ontology, new OWLXMLDocumentFormat(), IRI.create( new File(System.getProperty("user" +
                    ".dir") + File.separator + outputDirectoryPathStr + File.separator + outputFilesNameStr + ".owl").toURI()));
        } catch (OWLOntologyCreationException | OWLOntologyStorageException e) {
            throw new RuntimeException(e);
        }
    }
}
