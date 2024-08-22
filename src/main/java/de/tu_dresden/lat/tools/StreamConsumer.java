package de.tu_dresden.lat.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.log4j.Logger;

/**
 * @author Christian Alrabbaa
 *
 */
public class StreamConsumer extends Thread {

	private static final Logger logger = Logger.getLogger(StreamConsumer.class);

	InputStream inputStream;
	String streamType;

	public StreamConsumer(InputStream inputStream, String streamType) {
		this.inputStream = inputStream;
		this.streamType = streamType;
	}

	public void run() {
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(this.inputStream));
			String line;

			while ((line = reader.readLine()) != null)
				logger.debug(this.streamType + " -> " + line);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
