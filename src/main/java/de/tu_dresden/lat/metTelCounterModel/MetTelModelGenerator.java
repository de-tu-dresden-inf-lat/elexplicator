package de.tu_dresden.lat.metTelCounterModel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;


import de.tu_dresden.inf.lat.exceptions.EntityCheckerException;
import de.tu_dresden.inf.lat.model.interfaces.IModelGenerator;
import de.tu_dresden.inf.lat.model.interfaces.IType;
import de.tu_dresden.inf.lat.model.tools.GeneralTools;
import de.tu_dresden.inf.lat.model.tools.ToMetTools;
import de.tu_dresden.inf.lat.model.tools.ToOWLTools;
import org.apache.log4j.Logger;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import de.tu_dresden.lat.metTelCounterModel.parsing.ModelParser;
import de.tu_dresden.lat.metTelCounterModel.parsing.types.Model;
import de.tu_dresden.lat.metTelCounterModel.parsing.types.expresisons.concepts.ConceptName;

import de.tu_dresden.lat.tools.Cleaner;

/**
 * @author Christian Alrabbaa
 *
 */
public class MetTelModelGenerator implements IModelGenerator {
	private static final ToOWLTools oWLTools = ToOWLTools.getInstance();
	private static final ToMetTools metTools = ToMetTools.getInstance();

//TODO Remove implications with artificial concept names
	private static final Logger logger = Logger.getLogger(MetTelModelGenerator.class);

	private MetTelModelGenerator() {
	}

	private static class LazyHolder {
		static MetTelModelGenerator instance = new MetTelModelGenerator();
	}

	public static MetTelModelGenerator getInstance() {
		return LazyHolder.instance;
	}

	public void getCounterModel(OWLOntology ontology, OWLAxiom axiom, String outputFileName)
			throws IOException, InterruptedException, OWLOntologyCreationException, EntityCheckerException {

//		OWLAxiom axiom = oWLTools.getOWLAxiomFromStr(axiom, ontology);

		getMetInputFile(axiom, outputFileName);
		String outputPath = modelDirectory + File.separator + outputFileName + modelFileExtension;

		logger.info("Creating Counter Model");
		Instant start = Instant.now();

		Process p = Runtime.getRuntime().exec("java -jar " + proverJarFile + " -i " + inputFile + " -o " + outputPath
				+ " -t " + calculusExtensionFile);
		p.waitFor();

		Instant finish = Instant.now();
		logger.info(GeneralTools.getDuration(start, finish));

		GeneralTools.printCommandOutput(p);

		Cleaner.clean(calculusExtensionFile);
		Cleaner.clean(specificationsFile);
		Cleaner.clean(inputFile);

		if ((new File(outputPath)).exists())
			logger.info(
					"Counter Model file -> \"" + System.getProperty("user.dir") + File.separator + outputPath + "\"");
		else
			logger.fatal("The counter model was not created!");

		logger.info("Creating Model in Json Format");
		start = Instant.now();

		toJsonModel(outputFileName);

		finish = Instant.now();
		logger.info(GeneralTools.getDuration(start, finish));
	}

	/**
	 * Create the input file needed by the reasoner to generate the model
	 * 
	 * @param conclusion
	 * @param outputName
	 * @throws IOException
	 */
	public void getMetInputFile(OWLAxiom conclusion, String outputName) throws IOException {

		File meTInputFile = new File(inputFile);
		meTInputFile.createNewFile();

		FileOutputStream inputOutStream = new FileOutputStream(meTInputFile);

		GeneralTools.writeTo(getConclusionAsAssertion(conclusion), inputOutStream);

		inputOutStream.close();

	}

	public String getConclusionAsAssertion(OWLAxiom generalAxiom) {

		Set<OWLSubClassOfAxiom> axioms = oWLTools.getAsSubClassOf(generalAxiom);
		if (axioms.isEmpty())
			return "";

		StringBuffer res = new StringBuffer("");
		OWLClassExpression lhs, rhs;
		String lhsStr, rhsStr;
		for (OWLSubClassOfAxiom axiom : axioms) {

			lhs = axiom.getSubClass();
			rhs = axiom.getSuperClass();

			StringBuffer lhsBuffer = new StringBuffer();
			lhs.asConjunctSet().forEach(conj -> {
				lhsBuffer.append(metTools.getMetInstance(conj));
			});
			lhsStr = lhsBuffer.toString();// metTools.getMetInstance(lhs);

			StringBuffer rhsBuffer = new StringBuffer();
			rhs.asConjunctSet().forEach(conj -> {
				rhsBuffer.append(metTools.getMetInstance(conj.getComplementNNF()));
			});
			rhsStr = rhsBuffer.toString();// metTools.getMetInstance(rhs.getComplementNNF());

			if (!lhsStr.isEmpty() && !rhsStr.isEmpty())
				res.append(lhsStr + rhsStr);
		}

		return res.toString();
	}

	private void toJsonModel(String modelFileName) throws JsonGenerationException, JsonMappingException, IOException {
		ObjectMapper mapper = new ObjectMapper();
		ObjectWriter writer = mapper.writer(new DefaultPrettyPrinter());

		File modelFile = new File(modelDirectory + File.separator + modelFileName + modelFileExtension);

		Model model = ModelParser.parse(modelFile);

		ToJsonFormatter formatter = ToJsonFormatter.getInstance();
		Map<IType, List<Object>> modelJson = formatter.toJson(model);

		// filter out the alias from the final output
		modelJson.remove(new ConceptName(ClassAlias.getClassAlias().getIRI().getShortForm()));

		writer.writeValue(new File(modelDirectory + File.separator + modelFileName + ".json"), modelJson);

		Cleaner.clean(modelDirectory + File.separator + modelFileName + modelFileExtension);
	}
}
