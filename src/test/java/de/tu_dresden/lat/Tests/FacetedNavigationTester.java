package de.tu_dresden.lat.Tests;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.After;
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
import de.tu_dresden.lat.diagnoses.FacetedNavigation;
import de.tu_dresden.lat.tools.Helper;

import static org.junit.Assert.assertEquals;
public class FacetedNavigationTester{

    String ontologyPathStr = "src\\test\\resources\\ontologies\\modifiedPizzaOntology.owl";
    String axiomStr = "<http://subPizza#SpicyIceCream> SubClassOf: owl:Nothing";
    String mDsID = "test";
    String expectedOutDir = "src\\test\\resources\\expected_outputs";
    ReasonerName reasonerName = Helper.getReasonerName(new String[] {"ELK"});

    Set<String> axiom1 = new HashSet<>(Arrays.asList("DisjointClasses(<http://subPizza#IceCream> <http://subPizza#Pizza> <http://subPizza#PizzaBase> <http://subPizza#PizzaTopping>)"));
    Set<String> axiom2 = new HashSet<>(Arrays.asList("SubClassOf(<http://subPizza#IceCream> ObjectSomeValuesFrom(<http://subPizza#hasTopping> <http://subPizza#FruitTopping>))"));
    Set<String> axiom3 = new HashSet<>(Arrays.asList("EquivalentClasses(<http://subPizza#SpicyIceCream> ObjectIntersectionOf(<http://subPizza#IceCream> ObjectSomeValuesFrom(<http://subPizza#hasSpiciness> <http://subPizza#Hot>)) )"));
    Set<String> axiom4 = new HashSet<>(Arrays.asList("ObjectPropertyDomain(<http://subPizza#hasTopping> <http://subPizza#Pizza>)"));

    private Path tempDir;
    private String outDirStr;
    private OWLOntology ontology;
    private OWLAxiom axiom;
    @Before
    public void setUp() throws IOException, OWLOntologyCreationException, EntityCheckerException{
        // Create a temporary directory for each test
        tempDir = Files.createTempDirectory("testDir");
        outDirStr = tempDir.toString();
        ontology = OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(new File(ontologyPathStr));

	    axiom = ToOWLTools.getInstance().getOWLAxiomFromStr(axiomStr, ontology);
    }
    
    @After
    public void cleanUp() throws IOException{
        deleteDirectory(tempDir.toFile());
    }

    private void deleteDirectory(File directory) {
        if (directory.isDirectory()) {
            for (File file : directory.listFiles()) {
                deleteDirectory(file);
            }
        }
        directory.delete();
    }

    @Test
    public void testFirstRun() throws IOException, InterruptedException{
        Set<Set<String>> diagnoses_expected = new HashSet<>();        
        diagnoses_expected.addAll(Arrays.asList(axiom1, axiom2, axiom3, axiom4));

        Set<Set<String>> returnElements = ASPMinimalDiagnoses.getAllDiagnoses(axiom, ontology, mDsID, outDirStr, Sets.newHashSet(),reasonerName, true, true);        

        assertEquals("The diagnosis set generated in first run is inaccurate!", diagnoses_expected, returnElements);

    }

    @Test
    public void testFacetApplication() throws IOException, InterruptedException{
        Set<Set<String>> diagnoses_expected = new HashSet<>();        
        diagnoses_expected.addAll(Arrays.asList(axiom2, axiom3, axiom4));

        String simulatedFacetInput = "alpha0";

        ASPMinimalDiagnoses.getAllDiagnoses(axiom, ontology, mDsID, outDirStr, Sets.newHashSet(),
						reasonerName, true);

        Set<Set<String>> returnElements =FacetedNavigation.applyFacet(mDsID, outDirStr, simulatedFacetInput, true);
        
        assertEquals("Facet application generated inaccurate diagnoses.", diagnoses_expected, returnElements);
    }

    @Test
    // make raw file and continue as testReactivate
    public void testDeepInvestigation() throws IOException, InterruptedException{
        Map<String, java.lang.Object> expected_output = new HashMap<>();
        Map<String, java.lang.Object> generated_output = new HashMap<>();

        Set<String> selected_facet = new HashSet<>(Arrays.asList("alpha0", "alpha1", "alpha2"));
        Set<String> expected_dependency = new HashSet<>(Arrays.asList("not alpha3"));
        
        expected_output.put("Selection:", selected_facet);
        expected_output.put("Dependency:", expected_dependency);

        generated_output.put("Selection:", new HashSet<>());
        generated_output.put("Dependency:", new HashSet<>());

        String simulatedFacetInput = "alpha0/alpha1/alpha2";

        ASPMinimalDiagnoses.getAllDiagnoses(axiom, ontology, mDsID, outDirStr, Sets.newHashSet(),
						reasonerName, true);

        FacetedNavigation.applyFacet(mDsID, outDirStr, simulatedFacetInput);

        File generatedDeepInvFile = new File(outDirStr+File.separator+"deep_investigation.txt");

        BufferedReader reader = new BufferedReader(new FileReader(generatedDeepInvFile));
        String line;
        String keyword = "";
        while ((line = reader.readLine()) != null) {
            switch(line){
                case "Selection:": case "Dependency:":
                    keyword = line;
                    Set<String> values = new HashSet<>();
                    generated_output.put(keyword, values);
                    break;
                default:
                    Set<String> sets = (Set<String>) generated_output.get(keyword);
                    sets.add(line);
                    
            }
        }
        reader.close();

        assertEquals("Inaccurate deep investigation result generated.", expected_output, generated_output);

    }

    @Test
    public void testImpacts() throws IOException, InterruptedException{
        Map<String, java.lang.Object> expected_output = new HashMap<>();
        Map<String, java.lang.Object> generated_output = new HashMap<>();

        Set<String> selected_facet = new HashSet<>(Arrays.asList("alpha1"));
        Set<String> expected_impact = new HashSet<>(Arrays.asList("not alpha3"));
        
        expected_output.put("Removing:", selected_facet);
        expected_output.put("Retracts the facets:", expected_impact);

        generated_output.put("Removing:", new HashSet<>());
        generated_output.put("Retracts the facets:", new HashSet<>());

        String simulatedFacetInput = "alpha0/alpha1/alpha2";
        String simulatedImpactInput = "alpha1";

        ASPMinimalDiagnoses.getAllDiagnoses(axiom, ontology, mDsID, outDirStr, Sets.newHashSet(),
						reasonerName, true);

        FacetedNavigation.applyFacet(mDsID, outDirStr, simulatedFacetInput);
        FacetedNavigation.getImpact(mDsID, outDirStr, simulatedImpactInput);

        File generatedImpactFile = new File(outDirStr+File.separator+"impacts_raw.txt");

        BufferedReader reader = new BufferedReader(new FileReader(generatedImpactFile));
        String line;
        String keyword = "";
        while ((line = reader.readLine()) != null) {
            switch(line){
                case "Removing:": case "Retracts the facets:":
                    keyword = line;
                    break;
                default:
                    if (!keyword.equals("")){                        
                        Set<String> sets = (Set<String>) generated_output.get(keyword);
                        sets.add(line);
                    }
                    
            }
        }
        reader.close();
        assertEquals("Inaccurate impacts generated.", expected_output, generated_output);
    }

    @Test
    public void testReactivate() throws IOException, InterruptedException{
        String simulatedFacetInput = "alpha0/alpha1/alpha2";
        String simulatedReactivateInput = "alpha3";

        Map<String, java.lang.Object> expected_output = new HashMap<>();
        Map<String, java.lang.Object> generated_output = new HashMap<>();

        Set<String> expected_intersection = new HashSet<>();
        Set<String> selected_facet = new HashSet<>(Arrays.asList("alpha3"));
        Set<Set<String>> expected_combination = new HashSet<>();
        Set<String> comb1 = new HashSet<>(Arrays.asList("alpha0"));
        Set<String> comb2 = new HashSet<>(Arrays.asList("alpha1"));
        Set<String> comb3 = new HashSet<>(Arrays.asList("alpha2"));
        expected_combination.addAll(Arrays.asList(comb1, comb2, comb3));
        expected_output.put("Facet to reactivate:", selected_facet);
        expected_output.put("Combination:", expected_combination);
        expected_output.put("Intersection:", expected_intersection);

        generated_output.put("Facet to reactivate:", new HashSet<>());
        generated_output.put("Combination:", new HashSet<>());
        generated_output.put("Intersection:", new HashSet<>());

        ASPMinimalDiagnoses.getAllDiagnoses(axiom, ontology, mDsID, outDirStr, Sets.newHashSet(),
						reasonerName, true);

        FacetedNavigation.applyFacet(mDsID, outDirStr, simulatedFacetInput);
        FacetedNavigation.reactivateFunction(mDsID, outDirStr, simulatedReactivateInput);

        File generatedCorrectionFile = new File(outDirStr+File.separator+"corrections_raw.txt");
        BufferedReader reader = new BufferedReader(new FileReader(generatedCorrectionFile));
        String line;
        String keyword = "";
        while ((line = reader.readLine()) != null) {
            switch(line){
                case "Facet to reactivate:": case "Intersection:": case "Combination:":
                    keyword = line;
                    break;
                default:
                    if (!keyword.equals("")){
                        if (keyword.equals("Combination:")){
                        String[] comb = line.split(";");
                        Set<String> combSet = new HashSet<>(Arrays.asList(comb));
                        Set<Set<String>> sets = (Set<Set<String>>) generated_output.get(keyword);
                        sets.add(combSet);
                        } else {
                            Set<String> sets = (Set<String>) generated_output.get(keyword);
                            sets.add(line);
                        }
                    }
                    
            }
        }
        reader.close();
        assertEquals("Inaccurate correction set generated.", expected_output, generated_output);
    }

    @Test
    public void testDelete() throws IOException, InterruptedException{
        Set<Set<String>> diagnoses_expected = new HashSet<>(Arrays.asList(axiom2, axiom4));        
        String simulatedFacetInput = "alpha0/alpha1/alpha2";
        String simulatedDelInput = "alpha1";

        ASPMinimalDiagnoses.getAllDiagnoses(axiom, ontology, mDsID, outDirStr, Sets.newHashSet(),
						reasonerName, true);

        FacetedNavigation.applyFacet(mDsID, outDirStr, simulatedFacetInput);
        Set<Set<String>> returnElements = FacetedNavigation.delete(mDsID, outDirStr, Optional.of(simulatedDelInput), true);

        assertEquals("The diagnosis set generated after facet retraction is inaccurate!", diagnoses_expected, returnElements);
    }

    @Test
    public void testDelAll() throws IOException, InterruptedException{
        Set<Set<String>> diagnoses_expected = new HashSet<>();        
        diagnoses_expected.addAll(Arrays.asList(axiom1, axiom2, axiom3, axiom4));
        String simulatedFacetInput = "alpha0/alpha1/alpha2";

        ASPMinimalDiagnoses.getAllDiagnoses(axiom, ontology, mDsID, outDirStr, Sets.newHashSet(),
						reasonerName, true);

        FacetedNavigation.applyFacet(mDsID, outDirStr, simulatedFacetInput);
        Set<Set<String>> returnElements = FacetedNavigation.delete(mDsID, outDirStr, Optional.empty(), true);

        assertEquals("The diagnosis set generated after retracting all facets is inaccurate!", diagnoses_expected, returnElements);
    }

    @Test
    public void testSave() throws IOException, InterruptedException, EntityCheckerException, OWLOntologyCreationException, OWLOntologyStorageException{
        String simulatedFacetInput = "alpha0/alpha1/alpha2";

        ASPMinimalDiagnoses.getAllDiagnoses(axiom, ontology, mDsID, outDirStr, Sets.newHashSet(),
						reasonerName, true);

        FacetedNavigation.applyFacet(mDsID, outDirStr, simulatedFacetInput);

        FacetedNavigation.saveRepair(outDirStr, mDsID, ontologyPathStr, axiom, reasonerName, "saved_ontology.owl");

        File generatedOWLFile = new File(outDirStr+File.separator+"saved_ontology.owl");
        OWLOntology generatedOntology = OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(generatedOWLFile);
        Set<OWLAxiom> generatedOntologyAxioms = generatedOntology.getAxioms();
        File expectedOWLFile = new File(expectedOutDir + File.separator + "save"  + File.separator+"ontology_expected.owl");
        OWLOntology expectedOntology = OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(expectedOWLFile);
        Set<OWLAxiom> expectedOntologyAxioms = expectedOntology.getAxioms();

        assertEquals("The saved ontology is inaccurate.", expectedOntologyAxioms, generatedOntologyAxioms);
    }


}
