package de.tu_dresden.lat.data.cli;

import de.tu_dresden.inf.lat.counterExample.data.ModelFormat;
import de.tu_dresden.inf.lat.counterExample.data.ModelType;
import de.tu_dresden.inf.lat.evee.data.ProofType;
import de.tu_dresden.lat.data.enums.OutputType;
import de.tu_dresden.lat.data.names.ReasonerName;

/**
 * @author Christian Alrabbaa
 *
 */

public class CLIOptionsDefaultValues {
    public final static String
            defaultModuleFileNameStr = "",
            defaultADFileNameStr = "",
            defaultConstraintsFilePathStr = "",
            defaultLemmaTitle = "",
            defaultOutputDirectoryStr = "defaultOutputFolder",
            defaultOutputFileNameStr = "result",
            defaultReasonerStr = ReasonerName.Elk.name(),
            defaultModelFormatStr = ModelFormat.Individuals.name(),
            defaultModelTypeStr = ModelType.FullCanonical.name(),
            defaultProofTypeStr = ProofType.MinimalTreeSize.name(),
            defaultOutputTypeStr = OutputType.NonRecursiveJSON.name();
}
