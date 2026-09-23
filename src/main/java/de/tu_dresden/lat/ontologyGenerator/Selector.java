package de.tu_dresden.lat.ontologyGenerator;

import java.util.Random;

/**
 * A helper class for selecting randomly a type of class expression
 *
 * @author Christian Alrabbaa
 *
 */
public class Selector {
    private static final Selection[] expressionType = {Selection.ConceptName, Selection.ExistentialRestriction};
    public static Selection selectRandomly(){
        return expressionType[getRandomInt(0, expressionType.length-1)];
    }

    public static int getRandomInt(int minimum, int maximum) {
        Random random = new Random();
        return random.nextInt(maximum - minimum + 1) + minimum;
    }
}
