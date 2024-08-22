package de.tu_dresden.lat.managers;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import de.tu_dresden.inf.lat.counterExample.ELKModelGenerator;
import de.tu_dresden.inf.lat.counterExample.ModelAsSetsFormatter;
import de.tu_dresden.inf.lat.counterExample.RedundancyRefiner;
import de.tu_dresden.inf.lat.counterExample.data.ModelFormat;
import de.tu_dresden.inf.lat.counterExample.data.ModelType;
import de.tu_dresden.inf.lat.counterExample.relevantExamplesGenerators.*;
import de.tu_dresden.inf.lat.evee.general.data.exceptions.ModelGenerationException;
import de.tu_dresden.inf.lat.generators.DotGraphGenerator;
import de.tu_dresden.inf.lat.generators.GraphMLGenerator;
import de.tu_dresden.inf.lat.model.data.Element;
import de.tu_dresden.inf.lat.model.data.ElkModel;
import de.tu_dresden.inf.lat.model.data.Mapper;
import de.tu_dresden.inf.lat.model.interfaces.IGenerator;
import de.tu_dresden.inf.lat.model.interfaces.IModelComponent;
import de.tu_dresden.inf.lat.model.json.JsonModelWriter;
import de.tu_dresden.inf.lat.model.tools.GeneralTools;
import de.tu_dresden.inf.lat.model.json.JsonMapperWriter;
import org.apache.log4j.Logger;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;

import de.tu_dresden.lat.data.enums.ExitCode;
import de.tu_dresden.lat.data.enums.OutputType;

/**
 * @author Christian Alrabbaa
 *
 */

public class MyELKModelManager implements IGenerator {

	private static final Logger logger = Logger.getLogger(MyELKModelManager.class);

	private final OWLOntology ontology;

	/**
	 * Constructor to initialize MyELKModelManager with a given ontology.
	 *
	 * @param ontology The OWLOntology to be used.
	 */
	public MyELKModelManager(OWLOntology ontology) {
		this.ontology = ontology;
	}

	/**
	 * Generates a model based on the given axiom and specified parameters.
	 *
	 * @param axiom The OWLAxiom for which the model is generated.
	 * @param fileNameStr The base name of the output file.
	 * @param outDirStr The directory where output files will be saved.
	 * @param outputType The type of output (e.g., Text, Graph, JSON).
	 * @param modelType The type of model to generate (e.g., FullCanonical, Alpha).
	 * @param modelFormat The format of the model (e.g., Sets, Individuals).
	 * @param generatePNG Flag to determine if PNG images should be generated.
	 * @param exportMapper Flag to determine if the mapper should be exported.
	 * @return ExitCode indicating the success or failure of the operation.
	 * @throws OWLOntologyCreationException If the ontology creation fails.
	 * @throws IOException If an I/O error occurs.
	 * @throws ModelGenerationException If model generation fails.
	 */
	public ExitCode getModel(OWLAxiom axiom, String fileNameStr, String outDirStr, OutputType outputType,
							 ModelType modelType, ModelFormat modelFormat, boolean generatePNG, boolean exportMapper)
			throws OWLOntologyCreationException, IOException, ModelGenerationException {

		Set<IModelComponent> componentsToHighlight = new HashSet<>();
		Map<Element, OWLClassExpression> labelsTohighlight = new HashMap<>();
		Set<Element> modelElements = getModelElements(axiom, modelType, componentsToHighlight, labelsTohighlight,
				exportMapper, outDirStr);

		if (outputType == OutputType.Text) {
			File textOutFile = new File(
					outDirStr + File.separator + fileNameStr + modelFileExtension + textFileExtension);

			if (modelFormat == ModelFormat.Sets) {
				ModelAsSetsFormatter.writeAsSets(modelElements, textOutFile);

			} else if (modelFormat == ModelFormat.Individuals) {
				GeneralTools.writeCollectionTo(
						modelElements.stream().map(Element::toString).map(x -> x + "\n").collect(Collectors.toSet()),
						new FileOutputStream(textOutFile));
			}

		} else if (outputType == OutputType.Graph) {
			generateGraph(modelElements, componentsToHighlight, labelsTohighlight, outDirStr, fileNameStr, generatePNG);

		} else if (outputType == OutputType.RecursiveJSON || outputType == OutputType.NonRecursiveJSON) {
			new JsonModelWriter<Element>().writeToFile(modelElements, outDirStr + File.separator + fileNameStr);
		}

		return ExitCode.terminatedSuccessfully;
	}

	/**
	 * Retrieves the elements of the model based on the given axiom and model type.
	 *
	 * @param axiom The OWLAxiom for which the model elements are retrieved.
	 * @param modelType The type of model to generate.
	 * @param componentsToHighlight The set of components to highlight in the model.
	 * @param lablesToHighlight The map of elements to OWL class expressions to highlight.
	 * @param exportMapper Flag to determine if the mapper should be exported.
	 * @param outDirStr The directory where output files will be saved.
	 * @return A set of model elements.
	 * @throws OWLOntologyCreationException If the ontology creation fails.
	 * @throws IOException If an I/O error occurs.
	 * @throws ModelGenerationException If model generation fails.
	 */
	private Set<Element> getModelElements(OWLAxiom axiom, ModelType modelType,
										  Set<IModelComponent> componentsToHighlight, Map<Element, OWLClassExpression> lablesToHighlight,
										  boolean exportMapper, String outDirStr) throws OWLOntologyCreationException, IOException, ModelGenerationException {
		ELKModelGenerator elkModelGenerator = new ELKModelGenerator(ontology, axiom);

		if (modelType == ModelType.FullCanonical) {
			ElkModel model = new ElkModel(elkModelGenerator.generateFullRawCanonicalModelElements(),
					elkModelGenerator.getMapper());

			componentsToHighlight.addAll(model.getConceptModel(model.getMapper().getOriginalLHS()));
			componentsToHighlight.addAll(model.getConceptModel(model.getMapper().getOriginalRHS()));

			exportMapper(outDirStr, exportMapper, elkModelGenerator.getMapper());

			return model.getFinalizedModelElements();
		} else {



			RelevantCounterExampleGenerator rCEGebnerator = null;
			RedundancyRefiner refiner;
			Set<Element> result;

			if (modelType == ModelType.Alpha) {
				rCEGebnerator = new AlphaRelevantGenerator(elkModelGenerator);
			}

			else if (modelType == ModelType.Beta)
				rCEGebnerator = new BetaRelevantGenerator(elkModelGenerator);

			else if (modelType == ModelType.Diff)
				rCEGebnerator = new DiffRelevantGenerator(elkModelGenerator);

			else if (modelType == ModelType.FlatDiff)
				rCEGebnerator = new FlatDiffRelevantGenerator(elkModelGenerator);

			assert rCEGebnerator != null;
			result = rCEGebnerator.generate();

			refiner = new RedundancyRefiner(result, rCEGebnerator);

			componentsToHighlight.addAll(rCEGebnerator.getElkModel()
					.getConceptModel(rCEGebnerator.getElkModel().getMapper().getOriginalLHS()));
			componentsToHighlight.addAll(rCEGebnerator.getElkModel()
					.getConceptModel(rCEGebnerator.getElkModel().getMapper().getOriginalRHS()));

			// Add elements to labels map
			for (Entry<OWLClass, Element> entry : rCEGebnerator.getElkModel().getMapper().getClassRepresentatives()
					.entrySet()) {
				lablesToHighlight.put(entry.getValue(), rCEGebnerator.getConceptFromAlias(entry.getKey()));
			}

			refiner.refine();

			exportMapper(outDirStr, exportMapper, rCEGebnerator.getElkModel().getMapper());

			return result;
		}
	}

	/**
	 * Exports the mapper to a JSON file.
	 *
	 * @param outDirStr The directory where the mapper file will be saved.
	 * @param exportMapper Flag to determine if the mapper should be exported.
	 * @param mapper The mapper object to be exported.
	 * @throws IOException If an I/O error occurs during the export.
	 */
	private void exportMapper(String outDirStr, boolean exportMapper, Mapper mapper) throws IOException {
		if (!exportMapper)
			return;

		File mapperFile = GeneralTools.createFile(outDirStr + File.separator + "mapper.json");

		logger.info("Exporting Mapper object to " + mapperFile.getAbsolutePath());

		new JsonMapperWriter().writeToFile(mapper, outDirStr + File.separator + "mapper");

	}

	/**
	 * Generates a graphical representation of the model elements.
	 *
	 * @param modelElements The set of model elements to be included in the graph.
	 * @param componentsToHighlight The set of components to highlight in the graph.
	 * @param labelsToHighlight The map of elements to OWL class expressions to highlight.
	 * @param outDirStr The directory where the graph files will be saved.
	 * @param fileNameStr The base name of the output file.
	 * @param generatePNG Flag to determine if a PNG image of the graph should be generated.
	 * @throws IOException If an I/O error occurs during graph generation.
	 */
	private void generateGraph(Set<Element> modelElements, Set<IModelComponent> componentsToHighlight,
							   Map<Element, OWLClassExpression> labelsToHighlight, String outDirStr, String fileNameStr,
							   boolean generatePNG) throws IOException {

		logger.info("Generating counter model graph");
		Instant start = Instant.now();

		if (generatePNG) {
			File outputFile = new File(
					outDirStr + File.separator + fileNameStr + modelFileExtension + graphFileExtension);
			DotGraphGenerator.drawCounterModel(modelElements, componentsToHighlight, labelsToHighlight,
					outputFile);
			logger.info("Graph PNG file -> \"" + outputFile.getAbsolutePath());
		}

		try {
			File outputFile = new File(outDirStr + File.separator + fileNameStr + IGenerator.modelFileExtension
					+ IGenerator.graphMLFileExtension);

			GraphMLGenerator.drawCounterModel(modelElements, outputFile);
			Instant finish = Instant.now();
			logger.info(GeneralTools.getDuration(start, finish));
			logger.info("Graph file -> \"" + outputFile.getAbsolutePath());
		} catch (ParserConfigurationException | TransformerException e) {
			e.printStackTrace();
		}
	}
}

