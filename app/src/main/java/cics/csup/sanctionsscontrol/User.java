package cics.csup.sanctionsscontrol;

public class User {
    private String name;
    private String section;
    private long totalSanctions;
    private long passedBottles;

    // An empty constructor is required for Firestore
    public User() {
    }

    public User(String name, String section, long totalSanctions, long passedBottles) {
        this.name = name;
        this.section = section;
        this.totalSanctions = totalSanctions;
        this.passedBottles = passedBottles;
    }

    // --- Getters and Setters ---
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public long getTotalSanctions() {
        return totalSanctions;
    }

    public void setTotalSanctions(long totalSanctions) {
        this.totalSanctions = totalSanctions;
    }

    public long getPassedBottles() {
        return passedBottles;
    }

    public void setPassedBottles(long passedBottles) {
        this.passedBottles = passedBottles;
    }

    // Helper method to calculate remaining bottles
    public long getToBePass() {
        return totalSanctions - passedBottles;
    }
}