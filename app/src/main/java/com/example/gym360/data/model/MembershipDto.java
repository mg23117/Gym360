package com.example.gym360.data.model;

import java.util.List;

// POJO exclusivo para Firestore
public class MembershipDto {
    private String id;
    private String name;
    private int durationMonths;
    private int durationDays;
    private double price;
    private List<String> benefits;

    // Constructor vacío requerido por Firestore
    public MembershipDto() {}

    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getDurationMonths() { return durationMonths; }
    public void setDurationMonths(int durationMonths) { this.durationMonths = durationMonths; }
    public int getDurationDays() { return durationDays; }
    public void setDurationDays(int durationDays) { this.durationDays = durationDays; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public List<String> getBenefits() { return benefits; }
    public void setBenefits(List<String> benefits) { this.benefits = benefits; }
}