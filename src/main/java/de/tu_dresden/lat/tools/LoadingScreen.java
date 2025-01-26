package de.tu_dresden.lat.tools;

public class LoadingScreen {
    public static void main(String[] args) throws InterruptedException {
        String[] loadingStates = {"Loading", "Loading.", "Loading..", "Loading..."};
            for (String state : loadingStates) {
                System.out.print("\r" + "          "+"\r"+state+"\r");
                Thread.sleep(500); 

            }
            System.out.print("\r" + "          "+"\r");
    }
}


