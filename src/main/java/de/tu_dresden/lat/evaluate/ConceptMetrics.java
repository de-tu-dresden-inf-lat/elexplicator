package de.tu_dresden.lat.evaluate;

 public class ConceptMetrics {
    int depth;
    int breadth;
    double cost;
    int individualCount;

    public ConceptMetrics(int depth, int breadth, double cost) {
        this.depth = depth;
        this.breadth = breadth;
        this.cost = cost;
    }

    public void setIndividualCount(int individualCount) {
        this.individualCount = individualCount;
    }

}