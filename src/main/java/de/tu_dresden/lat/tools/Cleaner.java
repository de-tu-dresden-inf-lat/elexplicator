package de.tu_dresden.lat.tools;

import java.io.File;

import org.apache.commons.io.FileUtils;

import de.tu_dresden.lat.ELExplicator;

/**
 * @author Christian Alrabbaa
 *
 */
public class Cleaner {

	/**
	 * @param filePath
	 */
	public static void clean(String filePath) {
		clean(new File(filePath));
	}

	/**
	 * @param file
	 */
	public static void clean(File file) {
		if (!ELExplicator.keep)
			FileUtils.deleteQuietly(file);
	}

}
