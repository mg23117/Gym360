package com.example.gym360.domain.model;

import java.util.List;

public class Membership {
    private String id;
    private String name;
    private int durationMonths;
    private int durationDays;
    private double price;
    private List<String> benefits;

    public Membership() {}

    // Para no romper los formularios anteriores de meses.
    public Membership(String id, String name, int durationMonths,
                      double price, List<String> benefits) {
        this(id, name, durationMonths, 0, price, benefits);
    }

    public Membership(String id, String name, int durationMonths, int durationDays, double price, List<String> benefits) {
        this.id = id;
        this.name = name;
        this.durationMonths = durationMonths;
        this.durationDays = durationDays;
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
    public int getDurationDays() { return durationDays; }
    public void setDurationDays(int durationDays) { this.durationDays = durationDays; }

    // 1 mes será igual a 30 dias.
    public int getWeightDays() {
        return durationDays > 0 ? durationDays : Math.multiplyExact(durationMonths, 30);
    }

    public String getDurationLabel() {
        if (durationDays > 0) return durationDays == 1 ? "1 día" : durationDays + " días";
        return durationMonths == 1 ? "1 mes" : durationMonths + " meses";
    }
}