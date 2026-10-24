package com.example.gym360.domain.model;

import java.util.List;

public class Membership {
    private String id;
    private String name;
    private int durationMonths;
    private double price;
    private List<String> benefits;

    public Membership() {}

    public Membership(String id, String name, int durationMonths, double price, List<String> benefits) {
        this.id = id;
        this.name = name;
        this.durationMonths = durationMonths;
        this.price = price;
        this.benefits = benefits;
    }

    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getDurationMonths() { return durationMonths; }
    public void setDurationMonths(int durationMonths) { this.durationMonths = durationMonths; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public List<String> getBenefits() { return benefits; }
    public void setBenefits(List<String> benefits) { this.benefits = benefits; }
}