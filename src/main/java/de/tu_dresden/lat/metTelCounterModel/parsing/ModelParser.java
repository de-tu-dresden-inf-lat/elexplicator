package de.tu_dresden.lat.metTelCounterModel.parsing;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.apache.log4j.Logger;

import de.tu_dresden.lat.metTelCounterModel.parsing.types.Model;

/**
 * @author Christian Alrabbaa
 *
 */
public class ModelParser extends MeTModelParser {

	private static final Logger logger = Logger.getLogger(ModelParser.class);

	private ModelParser() {
	}

	/**
	 * Parse a model of the form '[' model element, ... ']' from a string
	 * 
	 * @param modelStr
	 * @return Model
	 */
	public static Model parse(String modelStr) {
		Model result = model().parse(modelStr);
		logger.debug(result);
		return result;
	}

	/**
	 * Parse a model of the form '[' model element, ... ']' from a file
	 * 
	 * <p>
	 * Note: The MetTel model file format is the following: (Un)Satisfiable\n
	 * Model:'[' model element, ...']'
	 * </p>
	 * 
	 * @param modelFile
	 * @return Model
	 * @throws IOException
	 */
	public static Model parse(File modelFile) throws IOException {

		BufferedReader br = new BufferedReader(new FileReader(modelFile));

		StringBuffer modelStr = new StringBuffer();
		String line;
		while ((line = br.readLine()) != null) {
			if (line.toLowerCase().equals("satisfiable."))
				continue;
			modelStr.append(line + "\n");
		}

		br.close();

		Model model = parse(modelStr.toString());

		return model;
	}
}
