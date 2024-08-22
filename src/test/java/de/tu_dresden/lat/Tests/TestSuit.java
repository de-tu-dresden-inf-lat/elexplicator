package de.tu_dresden.lat.Tests;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * @author Christian Alrabbaa
 *
 */

@RunWith(Suite.class)

@Suite.SuiteClasses({AxiomCheckerTest.class, DerivationStructureTest.class, ModelParsingTest.class,
        ProofRewritingTest.class, ProofWriterTest.class, SomeTests.class,
        TestEntailmentsAndDiagnoses.class, TestProofTypes.class, ToMetTelTest.class

})
    public class TestSuit {
}
