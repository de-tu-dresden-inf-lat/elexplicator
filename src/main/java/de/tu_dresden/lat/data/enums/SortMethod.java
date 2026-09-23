package de.tu_dresden.lat.data.enums;

public enum SortMethod {
    Frequency,
    Entropy;

    private SortMethod(){
    }

    public static SortMethod getSortMethod(String str){
        SortMethod result = parseArg(str);
        if (result != null){
            return result;
        } else {
            throw new IllegalArgumentException("Invalid sort method: " + str);
        }
    }

    public static boolean isSortMethod(String str){
        return parseArg(str) != null;
    }

    private static SortMethod parseArg(String str) {
        if (str.equalsIgnoreCase("frequency")){
            return Frequency;
        } else if (str.equalsIgnoreCase("entropy")){
            return Entropy;
        } else {
            return null;
        }
    }
}
