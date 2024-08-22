package de.tu_dresden.lat.data.enums;

/**
 * @author Christian Alrabbaa
 *
 */
public enum ExitCode {
	NotEntailed(-2), Entailed(-1), terminatedSuccessfully(0), NotSupportedAxiom(3),
	NoJustificationsComputed(4), TranslationToOWLXMLFailed(5);

	private final int value;

	ExitCode(int value) {
		this.value = value;
	}

	public int getValue() {
		return value;
	}
}
