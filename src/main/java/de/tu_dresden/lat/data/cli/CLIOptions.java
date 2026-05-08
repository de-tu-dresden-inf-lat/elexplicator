package de.tu_dresden.lat.data.cli;

import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;


/**
 * @author Christian Alrabbaa
 *
 */

public class CLIOptions {

    public final CommandLineParser parser = new DefaultParser();
    public final HelpFormatter formatter = new HelpFormatter();

    //

    public final Option constraintsPathOption = new Option(CLIOptionsStrings.constraintsPathOptionShort,
            CLIOptionsStrings.constraintsPathOptionLong , true,
            CLIOptionsDescriptions.constraintsPathOptionDescription);

    public final Option keepGeneratedStuffOption = new Option(CLIOptionsStrings.keepGeneratedStuffOptionShort,
            CLIOptionsStrings.keepGeneratedStuffOptionLong, false,
            CLIOptionsDescriptions.keepGeneratedStuffOptionDescription);

    public final Option proofOutTypeOption = new Option(CLIOptionsStrings.proofOutTypeOptionShort,
            CLIOptionsStrings.proofOutTypeOptionLong, true, CLIOptionsDescriptions.proofOutTypeOptionDescription);

    public final Option modelTypeOption = new Option( CLIOptionsStrings.modelTypeOptionShort,
            CLIOptionsStrings.modelTypeOptionLong,true,CLIOptionsDescriptions.modelTypeOptionDescription);

    public final Option modelFormatOption = new Option(CLIOptionsStrings.modelFormatOptionShort,
            CLIOptionsStrings.modelFormatOptionLong, true,CLIOptionsDescriptions.modelFormatOptionDescription);

    public final Option concreteDomainNameOption = new Option(CLIOptionsStrings.concreteDomainNameOptionShort,
            CLIOptionsStrings.concreteDomainNameOptionLong, true,
            CLIOptionsDescriptions.concreteDomainNameOptionDescription);

    public final Option reasonerNameOption =
            new Option(CLIOptionsStrings.reasonerNameOptionShort, CLIOptionsStrings.reasonerNameOptionLong, true,
            CLIOptionsDescriptions.reasonerNameOptionDescription);
    public final Option exampleReasonerOption = new Option(CLIOptionsStrings.exampleReasonerOptionShort,
            CLIOptionsStrings.exampleReasonerOptionLong, false, CLIOptionsDescriptions.exampleReasonerOptionDescription);

    public final Option mDsOption = new Option(CLIOptionsStrings.mDsOptionShort, CLIOptionsStrings.mDsOptionLong, true,
            CLIOptionsDescriptions.mDsOptionDescription);

    public final Option adOption = new Option(CLIOptionsStrings.adOptionShort, CLIOptionsStrings.adOptionLong, false,
            CLIOptionsDescriptions.adOptionDescription);

    public final Option moduleOption = new Option(CLIOptionsStrings.moduleOptionShort,
            CLIOptionsStrings.moduleOptionLong, false, CLIOptionsDescriptions.moduleOptionDescription);

    public final Option signatureFilePathOption = new Option(CLIOptionsStrings.signatureFilePathOptionShort,
            CLIOptionsStrings.signatureFilePathOptionLong, true,
            CLIOptionsDescriptions.signatureFilePathOptionDescription);

    public final Option generatePNGOption =
            new Option(CLIOptionsStrings.generatePNGOptionShort, CLIOptionsStrings.generatePNGOptionLong, false,
                    CLIOptionsDescriptions.generatePNGOptionDescription);

    public final Option translateAxiom2NLOption = new Option(CLIOptionsStrings.translateAxiom2NLOptionShort,
            CLIOptionsStrings.translateAxiom2NLOptionLong, false,
            CLIOptionsDescriptions.translateAxiom2NLOptionDescription);

    public final Option exportMapperOption = new Option(CLIOptionsStrings.exportMapperOptionShort,
            CLIOptionsStrings.exportMapperOptionLong, false, CLIOptionsDescriptions.exportMapperOptionDescription);

    public final Option inputJsonProofPathOption = new Option(CLIOptionsStrings.inputJsonProofPathOptionShort,
            CLIOptionsStrings.inputJsonProofPathOptionLong, true, CLIOptionsDescriptions.inputJsonProofPathOptionDescription);
    public final Option inputJsonProofPathOptionREQUIRED = new Option(CLIOptionsStrings.inputJsonProofPathOptionShort,
            CLIOptionsStrings.inputJsonProofPathOptionLong, true, CLIOptionsDescriptions.inputJsonProofPathOptionDescription);

    public final Option outDirOption = new Option(CLIOptionsStrings.outDirOptionShort,
            CLIOptionsStrings.outDirOptionLong, true, CLIOptionsDescriptions.outDirOptionDescription);
    public final Option outDirOptionREQUIRED = new Option(CLIOptionsStrings.outDirOptionShort,
            CLIOptionsStrings.outDirOptionLong, true, CLIOptionsDescriptions.outDirOptionDescription);

    public final Option proofTypeOption = new Option(CLIOptionsStrings.proofTypeOptionShort,
            CLIOptionsStrings.proofTypeOptionLong, true, CLIOptionsDescriptions.proofTypeOptionDescription);

    public final Option proofTypeOptionREQUIRED = new Option(CLIOptionsStrings.proofTypeOptionShort,
            CLIOptionsStrings.proofTypeOptionLong, true, CLIOptionsDescriptions.proofTypeOptionDescription);

    public final Option outFileLabelOption = new Option(CLIOptionsStrings.outFileLabelOptionShort,
            CLIOptionsStrings.outFileLabelOptionLong, true, CLIOptionsDescriptions.outFileLabelOptionDescription);
    public final Option outFileLabelOptionREQUIRED = new Option(CLIOptionsStrings.outFileLabelOptionShort,
            CLIOptionsStrings.outFileLabelOptionLong, true, CLIOptionsDescriptions.outFileLabelOptionDescription);

    public final Option ontologyPathOption =
            new Option(CLIOptionsStrings.ontologyPathOptionShort, CLIOptionsStrings.ontologyPathOptionLong, true,
            CLIOptionsDescriptions.ontologyPathOptionDescription);
    public final Option ontologyPathOptionREQUIRED =
            new Option(CLIOptionsStrings.ontologyPathOptionShort, CLIOptionsStrings.ontologyPathOptionLong, true,
            CLIOptionsDescriptions.ontologyPathOptionDescription);

    public final Option conclusionAxiomOption = new Option(CLIOptionsStrings.conclusionAxiomOptionShort,
            CLIOptionsStrings.conclusionAxiomOptionLong, true, CLIOptionsDescriptions.conclusionAxiomOptionDescription);
    public final Option conclusionAxiomOptionREQUIRED = new Option(CLIOptionsStrings.conclusionAxiomOptionShort,
            CLIOptionsStrings.conclusionAxiomOptionLong, true, CLIOptionsDescriptions.conclusionAxiomOptionDescription);

    public final Option patternDirOption = new Option(CLIOptionsStrings.patternDirOptionShort,
            CLIOptionsStrings.patternDirOptionLong, true,CLIOptionsDescriptions.patternDirOptionDescription);
    public final Option patternDirOptionREQUIRED = new Option(CLIOptionsStrings.patternDirOptionShort,
            CLIOptionsStrings.patternDirOptionLong, true,CLIOptionsDescriptions.patternDirOptionDescription);

    public final Option lemmaTitleOption = new Option(CLIOptionsStrings.lemmaTitleOptionShort,
            CLIOptionsStrings.lemmaTitleOptionLong, true, CLIOptionsDescriptions.lemmaTitleOptionDescription);
    public final Option lemmaTitleOptionREQUIRED = new Option(CLIOptionsStrings.lemmaTitleOptionShort,
            CLIOptionsStrings.lemmaTitleOptionLong, true, CLIOptionsDescriptions.lemmaTitleOptionDescription);

    public final Option diagnosisOption = new Option(CLIOptionsStrings.diagnosisOptionShort, CLIOptionsStrings.diagnosisOptionLong, 
            true, CLIOptionsDescriptions.diagnosisOptionDescription);

    public final Option repairOption = new Option(CLIOptionsStrings.repairOptionShort, CLIOptionsStrings.repairOptionLong, 
            true, CLIOptionsDescriptions.repairOptionDescription);

    public final Option interestingAxiomOption = new Option(CLIOptionsStrings.interestingAxiomOptionShort, CLIOptionsStrings.interestingAxiomOptionLong, 
            true, CLIOptionsDescriptions.interestingAxiomDescription);
    public final Option interestingAxiomOptionREQUIRED = new Option(CLIOptionsStrings.interestingAxiomOptionShort, CLIOptionsStrings.interestingAxiomOptionLong, 
            true, CLIOptionsDescriptions.interestingAxiomDescription);
    
    public final Option liveSortOption = new Option(CLIOptionsStrings.liveSortOptionShort, CLIOptionsStrings.liveSortOptionLong, 
            false, CLIOptionsDescriptions.liveSortOptionDescription);

    public final Option sortMethodOption = new Option(CLIOptionsStrings.sortMethodOptionShort, CLIOptionsStrings.sortMethodOptionLong,
            true, CLIOptionsDescriptions.sortMethodOptionDescription);
    public final Option sortMethodOptionREQUIRED = new Option(CLIOptionsStrings.sortMethodOptionShort, CLIOptionsStrings.sortMethodOptionLong,
            true, CLIOptionsDescriptions.sortMethodOptionDescription);

    public final Option visualizeOption = new Option(CLIOptionsStrings.visualizeOptionShort, CLIOptionsStrings.visualizeOptionLong, false,
            CLIOptionsDescriptions.visualizeOptionDescription);
    private CLIOptions() {
        formatter.setWidth(200);

        //

        ontologyPathOption.setRequired(false);
        ontologyPathOption.setArgName(CLIOptionsArgs.pathArg);
        ontologyPathOptionREQUIRED.setRequired(true);
        ontologyPathOptionREQUIRED.setArgName(CLIOptionsArgs.pathArg);

        conclusionAxiomOption.setRequired(false);
        conclusionAxiomOption.setArgName(CLIOptionsArgs.axiomArg);
        conclusionAxiomOptionREQUIRED.setRequired(true);
        conclusionAxiomOptionREQUIRED.setArgName(CLIOptionsArgs.axiomArg);

        constraintsPathOption.setRequired(false);
        constraintsPathOption.setArgName(CLIOptionsArgs.pathArg);

        outFileLabelOption.setRequired(false);
        outFileLabelOption.setArgName(CLIOptionsArgs.labelArg);
        outFileLabelOptionREQUIRED.setRequired(true);
        outFileLabelOptionREQUIRED.setArgName(CLIOptionsArgs.labelArg);

        outDirOption.setRequired(false);
        outDirOptionREQUIRED.setRequired(true);

        keepGeneratedStuffOption.setRequired(false);

        proofOutTypeOption.setRequired(false);
        proofOutTypeOption.setArgs(1);
        proofOutTypeOption.setArgName(CLIOptionsArgs.typeArg);

        proofTypeOption.setRequired(false);
        proofTypeOption.setArgs(1);
        proofTypeOption.setArgName(CLIOptionsArgs.typeArg);
        proofTypeOptionREQUIRED.setRequired(true);
        proofTypeOptionREQUIRED.setArgs(1);
        proofTypeOptionREQUIRED.setArgName(CLIOptionsArgs.typeArg);

        modelTypeOption.setRequired(false);
        modelTypeOption.setArgs(1);
        modelTypeOption.setArgName(CLIOptionsArgs.typeArg);

        modelFormatOption.setRequired(false);
        modelFormatOption.setArgs(1);
        modelFormatOption.setArgName(CLIOptionsArgs.formatArg);

        concreteDomainNameOption.setRequired(false);
        concreteDomainNameOption.setArgs(1);
        concreteDomainNameOption.setArgName(CLIOptionsArgs.nameArg);

        reasonerNameOption.setRequired(false);
        reasonerNameOption.setArgs(1);
        reasonerNameOption.setArgName(CLIOptionsArgs.nameArg);

        mDsOption.setRequired(false);
        mDsOption.setArgs(2);
        mDsOption.setOptionalArg(true);
        mDsOption.setValueSeparator(',');
        mDsOption.setArgName(CLIOptionsArgs.mDsOptionArgs);

        adOption.setRequired(false);

        exampleReasonerOption.setRequired(false);

        moduleOption.setRequired(false);
        moduleOption.setArgName(CLIOptionsArgs.labelArg);

        signatureFilePathOption.setRequired(false);

        generatePNGOption.setRequired(false);

        translateAxiom2NLOption.setRequired(false);

        exportMapperOption.setRequired(false);

        inputJsonProofPathOption.setRequired(false);
        inputJsonProofPathOptionREQUIRED.setRequired(true);

        patternDirOption.setRequired(false);
        patternDirOptionREQUIRED.setRequired(true);

        lemmaTitleOption.setRequired(false);
        lemmaTitleOptionREQUIRED.setRequired(true);

        diagnosisOption.setRequired(false);
        diagnosisOption.setArgs(2);
        diagnosisOption.setOptionalArg(true);
        diagnosisOption.setValueSeparator(',');
        diagnosisOption.setArgName(CLIOptionsArgs.diagnosisOptionArgs);

        repairOption.setRequired(false);
        repairOption.setArgs(1);
        repairOption.setOptionalArg(true);
        repairOption.setArgName(CLIOptionsArgs.repairOptionArgs);

        interestingAxiomOption.setRequired(false);
        interestingAxiomOption.setArgName(CLIOptionsArgs.pathArg);
        interestingAxiomOptionREQUIRED.setRequired(true);
        interestingAxiomOptionREQUIRED.setArgName(CLIOptionsArgs.pathArg);

        liveSortOption.setRequired(false);

        sortMethodOption.setRequired(false);
        sortMethodOption.setArgName(CLIOptionsArgs.nameArg);
        sortMethodOptionREQUIRED.setRequired(true);
        sortMethodOptionREQUIRED.setArgName(CLIOptionsArgs.nameArg);

        visualizeOption.setRequired(false);
    }

    private static class LazyHolder {
        static CLIOptions instance = new CLIOptions();
    }

    public static CLIOptions getInstance() {
        return CLIOptions.LazyHolder.instance;
    }



}

