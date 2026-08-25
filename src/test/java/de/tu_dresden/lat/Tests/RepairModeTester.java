package de.tu_dresden.lat.Tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;

import com.google.common.collect.Sets;

import de.tu_dresden.inf.lat.exceptions.EntityCheckerException;
import de.tu_dresden.inf.lat.model.tools.ToOWLTools;
import de.tu_dresden.lat.data.names.ReasonerName;
import de.tu_dresden.lat.diagnoses.ASPMinimalDiagnoses;
import de.tu_dresden.lat.diagnoses.ComputeRepair;
import de.tu_dresden.lat.diagnoses.HelperFunctions;
import de.tu_dresden.lat.diagnoses.JustificationsGenerator;

public class RepairModeTester {
    Set<OWLAxiom> keepAxioms = new HashSet<>();
    Set<OWLAxiom> removeAxioms;
    Set<Set<? extends OWLAxiom>> allJustifications;
    OWLAxiom axiom;
    OWLOntology ontology;
    OWLOntology interestingAxiomsOntology;
    
    private Path tempDir;
    private String outDirStr;
    private static Set<OWLAxiom> interestingAxiomsSet; 	

    Map<OWLAxiom, String> axioms2Identifiers;	
    Map<String, OWLAxiom> identifiers2Axioms;	
    
    String expectedOutDir = "src\\test\\resources\\expected_outputs\\save";
    String ontologyPathString = "src/test/TestOntology/RepairTestOntology.owl";
    String interestingAxiomsOntologyPath = "src/test/TestOntology/interestingAxiomTest.owl";
    String programFileName = "pi.txt";
    String mDsID = "repair";
    ReasonerName reasonerName = ReasonerName.Elk;

    List<OWLAxiom> justificationAxioms;

    

    @Before
    public void setUp() throws OWLOntologyCreationException, EntityCheckerException, IOException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException{
        tempDir = Files.createTempDirectory("testDir");
        outDirStr = tempDir.toString();
        ontology = OWLManager.createOWLOntologyManager()
                .loadOntologyFromOntologyDocument(new File(ontologyPathString));
        axiom = ToOWLTools.getInstance().getOWLAxiomFromStr("<https://nameGenerator#A> SubClassOf <https://nameGenerator#C>", ontology);
        allJustifications = JustificationsGenerator.getAllELKJustifications(axiom, ontology);
        ComputeRepair.fillMap(allJustifications);
        interestingAxiomsOntology = OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(new File(interestingAxiomsOntologyPath));
        interestingAxiomsSet = interestingAxiomsOntology.getAxioms();

        justificationAxioms = Arrays.asList(
            ToOWLTools.getInstance().getOWLAxiomFromStr("<https://nameGenerator#B2> SubClassOf <https://nameGenerator#B9>", ontology), //0
            ToOWLTools.getInstance().getOWLAxiomFromStr("<https://nameGenerator#B5> SubClassOf <https://nameGenerator#r> some <https://nameGenerator#B6>", ontology),  //1
            ToOWLTools.getInstance().getOWLAxiomFromStr("<https://nameGenerator#B7> SubClassOf <https://nameGenerator#r> some <https://nameGenerator#B8>", ontology),  //2
            ToOWLTools.getInstance().getOWLAxiomFromStr("<https://nameGenerator#B2> SubClassOf <https://nameGenerator#r> some <https://nameGenerator#B3>", ontology),  //3
            ToOWLTools.getInstance().getOWLAxiomFromStr("<https://nameGenerator#B3> SubClassOf <https://nameGenerator#r> some <https://nameGenerator#B4>", ontology),  //4
            ToOWLTools.getInstance().getOWLAxiomFromStr("<https://nameGenerator#B> SubClassOf <https://nameGenerator#B1>", ontology),  //5
            ToOWLTools.getInstance().getOWLAxiomFromStr("<https://nameGenerator#r> some <https://nameGenerator#B13> SubClassOf <https://nameGenerator#C>", ontology), //6  
            ToOWLTools.getInstance().getOWLAxiomFromStr("<https://nameGenerator#B10> SubClassOf <https://nameGenerator#C>", ontology),  //7
            ToOWLTools.getInstance().getOWLAxiomFromStr("<https://nameGenerator#B9> SubClassOf <https://nameGenerator#B10>", ontology),  //8
            ToOWLTools.getInstance().getOWLAxiomFromStr("<https://nameGenerator#A> SubClassOf <https://nameGenerator#B5>", ontology),  //9
            ToOWLTools.getInstance().getOWLAxiomFromStr("<https://nameGenerator#B11> SubClassOf <https://nameGenerator#C>", ontology),  //10
            ToOWLTools.getInstance().getOWLAxiomFromStr("<https://nameGenerator#r> some <https://nameGenerator#B8> SubClassOf <https://nameGenerator#C>", ontology), //11  
            ToOWLTools.getInstance().getOWLAxiomFromStr("<https://nameGenerator#r> some <https://nameGenerator#r> some <https://nameGenerator#B4> SubClassOf <https://nameGenerator#C>", ontology),  //12
            ToOWLTools.getInstance().getOWLAxiomFromStr("<https://nameGenerator#B12> SubClassOf <https://nameGenerator#r> some <https://nameGenerator#B13>", ontology),  //13
            ToOWLTools.getInstance().getOWLAxiomFromStr("<https://nameGenerator#A> SubClassOf <https://nameGenerator#B>", ontology),   //14
            ToOWLTools.getInstance().getOWLAxiomFromStr("<https://nameGenerator#r> some <https://nameGenerator#B6> SubClassOf <https://nameGenerator#B7>", ontology), //15
            ToOWLTools.getInstance().getOWLAxiomFromStr("<https://nameGenerator#B1> SubClassOf <https://nameGenerator#B2>", ontology), //16
            ToOWLTools.getInstance().getOWLAxiomFromStr("<https://nameGenerator#B> SubClassOf <https://nameGenerator#B12>", ontology), //17
            ToOWLTools.getInstance().getOWLAxiomFromStr("<https://nameGenerator#A> SubClassOf <https://nameGenerator#B11>", ontology) //18
        );

        Field fieldHFid2axioms = HelperFunctions.class.getDeclaredField("identifiers2Axioms");
        fieldHFid2axioms.setAccessible(true);       
        Field fieldCRid2axioms = ComputeRepair.class.getDeclaredField("identifiers2Axioms");
        fieldCRid2axioms.setAccessible(true);
        fieldHFid2axioms.set(null, fieldCRid2axioms.get(null));

        Field minimalDiagnoses = ComputeRepair.class.getDeclaredField("minimalDiagnoses");
        minimalDiagnoses.setAccessible(true);
        Field allDiagnoses = ComputeRepair.class.getDeclaredField("allDiagnoses");
        allDiagnoses.setAccessible(true);
        try {
            ASPMinimalDiagnoses.getAllClassicalRepairs(axiom, ontology, "minimal", outDirStr, new HashSet<>(), new HashSet<>(), reasonerName);
            minimalDiagnoses.set(null, ASPMinimalDiagnoses.allOptimalDiagnosesMin);
            allDiagnoses.set(null, ASPMinimalDiagnoses.allDiagnoses);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } 

    }

    @Test
    public void testCheckAxiomSelection(){
        keepAxioms = new HashSet <>(Arrays.asList(justificationAxioms.get(10), justificationAxioms.get(18), justificationAxioms.get(12)));

        Set<? extends OWLAxiom> sameJustification =  new HashSet<>(Arrays.asList(justificationAxioms.get(10), justificationAxioms.get(18)));

        assertEquals("Justification axioms selection checking invalid!", sameJustification, ComputeRepair.checkAxiomSelection(allJustifications, keepAxioms));
    }

    @Test
    public void testGetAxiomWeight() throws IOException, OWLOntologyCreationException, OWLOntologyStorageException, EntityCheckerException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException{
        Map<OWLAxiom, Double> expectedAxiomWeight = new HashMap<>();
        expectedAxiomWeight.put(justificationAxioms.get(0), 100.0);
        expectedAxiomWeight.put(justificationAxioms.get(2), 0.0);
        expectedAxiomWeight.put(justificationAxioms.get(9), 50.0);
        expectedAxiomWeight.put(justificationAxioms.get(13), 42.857142857142854);
        expectedAxiomWeight.put(justificationAxioms.get(7), 50.0);

        Path tempOutDir = Files.createTempDirectory(tempDir,"tempOutDirStr");

        keepAxioms = new HashSet <>(Arrays.asList(
            justificationAxioms.get(0),	 
            justificationAxioms.get(4),
            justificationAxioms.get(16),
            justificationAxioms.get(14)
            ));
        removeAxioms = new HashSet<>(Arrays.asList(
            justificationAxioms.get(1),
            justificationAxioms.get(2),
            justificationAxioms.get(3),
            justificationAxioms.get(5)
            ));

        // get all diagnoses, filter out the available ones, then do the repair modules and axiom weight
        Set<Set<? extends OWLAxiom>> diagnoses = ComputeRepair.getAvailableDiagnoses(keepAxioms, removeAxioms);
        assertNotNull("Diagnosis not available!", diagnoses);

        // Map<OWLAxiom, List<OWLOntology>> modulesMap = ComputeRepair.computeRepairsModules(ontologyPathString, diagnoses, interestingAxiomsSet);
		// int totalRepairs = diagnoses.size();
		// Map<OWLAxiom, Double> actualAxiomWeight = ComputeRepair.computeAxiomWeight(modulesMap, reasonerName, totalRepairs);
        Map<OWLAxiom, Set<Set<? extends OWLAxiom>>> modulesMap = ComputeRepair.getInterestingAxiomsEntailment(ontologyPathString, diagnoses, interestingAxiomsSet);
		int totalRepairs = diagnoses.size();
		Map<OWLAxiom, Double> actualAxiomWeight = ComputeRepair.computeAxiomWeight(modulesMap, reasonerName, totalRepairs);
        // assertEquals("The axiom weight calculation is inaccurate!", expectedAxiomWeight, actualAxiomWeight);
        
    }

    @Test
    public void testNoRepair() throws OWLOntologyCreationException{
        keepAxioms = new HashSet<>(Arrays.asList(justificationAxioms.get(10), justificationAxioms.get(18)));
        Set<OWLAxiom> removeAxioms = new HashSet<>(justificationAxioms);
        removeAxioms.removeAll(keepAxioms); 

        String save_filename = "resultOntology";

        String simulatedInput = "continue\n";
        Scanner scanner = new Scanner(new ByteArrayInputStream(simulatedInput.getBytes()));

        Boolean savedFlag = ComputeRepair.saveProcess(ontology, axiom, removeAxioms, ontologyPathString, outDirStr, save_filename, reasonerName, scanner);
        assertTrue("The resulting ontology should have been saved!", savedFlag);

        File generatedOntologyFile = new File(outDirStr+File.separator+"resultOntology.owl");
        OWLOntology generatedOntology = OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(generatedOntologyFile);
        Set<OWLAxiom> actualResultOntoAxioms = generatedOntology.getAxioms();
        File expectedResultFile = new File(expectedOutDir +File.separator+"ontoNoRepair.owl");
        OWLOntology expectedOntology = OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(expectedResultFile);
        Set<OWLAxiom> expectedResultOntoAxioms = expectedOntology.getAxioms();
        assertEquals("The generated result ontology is inaccurate!", expectedResultOntoAxioms, actualResultOntoAxioms);


    }

    @Test
    public void testMaxRepair() throws OWLOntologyCreationException{
        removeAxioms = new HashSet<>(Arrays.asList(
            justificationAxioms.get(11), 
            justificationAxioms.get(14), 
            justificationAxioms.get(18)));

        String save_filename = "resultOntology";

        String simulatedInput = "";
        Scanner scanner = new Scanner(new ByteArrayInputStream(simulatedInput.getBytes()));

        Boolean savedFlag = ComputeRepair.saveProcess(ontology, axiom, removeAxioms, ontologyPathString, outDirStr, save_filename, reasonerName, scanner);
        assertTrue("The resulting ontology should have been saved!", savedFlag);

        File generatedOntologyFile = new File(outDirStr+File.separator+"resultOntology.owl");
        OWLOntology generatedOntology = OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(generatedOntologyFile);
        Set<OWLAxiom> actualResultOntoAxioms = generatedOntology.getAxioms();
        File expectedResultFile = new File(expectedOutDir +File.separator+"ontoMaxRepair.owl");
        OWLOntology expectedOntology = OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(expectedResultFile);
        Set<OWLAxiom> expectedResultOntoAxioms = expectedOntology.getAxioms();
        assertEquals("The generated result ontology is inaccurate!", expectedResultOntoAxioms, actualResultOntoAxioms);
    }

    @Test 
    public void testNonMaxRepair() throws OWLOntologyCreationException{
        removeAxioms = new HashSet<>(Arrays.asList(
            justificationAxioms.get(10), 
            justificationAxioms.get(11), 
            justificationAxioms.get(14), 
            justificationAxioms.get(18)));

        String save_filename = "resultOntology";

        String simulatedInput = "max\n2\n";
        Scanner scanner = new Scanner(new ByteArrayInputStream(simulatedInput.getBytes()));

        Boolean savedFlag = ComputeRepair.saveProcess(ontology, axiom, removeAxioms, ontologyPathString, outDirStr, save_filename, reasonerName, scanner);
        assertTrue("The resulting ontology should have been saved!", savedFlag);

        File generatedOntologyFile = new File(outDirStr+File.separator+"resultOntology.owl");
        OWLOntology generatedOntology = OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(generatedOntologyFile);
        Set<OWLAxiom> actualResultOntoAxioms = generatedOntology.getAxioms();
        File expectedResultFile = new File(expectedOutDir +File.separator+"ontoMaxRepair.owl");
        OWLOntology expectedOntology = OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(expectedResultFile);
        Set<OWLAxiom> expectedResultOntoAxioms = expectedOntology.getAxioms();
        assertEquals("The generated result ontology is inaccurate!", expectedResultOntoAxioms, actualResultOntoAxioms);
    }

    @Test
    public void testRepairUniqueness() throws OWLOntologyCreationException{
        OWLOntology repair1, repair2;
        Set<OWLAxiom> diagSet1, diagSet2; 
        diagSet1 = Sets.newHashSet();
        diagSet1.add(justificationAxioms.get(1));
        diagSet2 = Sets.newHashSet();   
        diagSet2.add(justificationAxioms.get(1));

        repair1 = ComputeRepair.computeRepair(diagSet1, ontologyPathString);
        repair2 = ComputeRepair.computeRepair(diagSet2, ontologyPathString);

        assertFalse("Repair 1 and 2 are equal", repair1.equals(repair2));
    }

}
