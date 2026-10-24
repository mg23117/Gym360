package com.example.gym360.data.local.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import androidx.annotation.NonNull;

import java.util.List;

@Entity(tableName = "memberships")
public class MembershipEntity {
    public static final String SYNCED = "SYNCED";
    public static final String PENDING_UPSERT = "PENDING_UPSERT";
    public static final String PENDING_DELETE = "PENDING_DELETE";

    @PrimaryKey
    @NonNull
    private String id;

    @NonNull
    private String name;

    private int durationMonths;

    private double price;

    @NonNull
    private List<String> benefits;

    // "SYNCED", PENDING_UPSERT o PENDING_DELETE
    private String syncStatus;

    @ColumnInfo(defaultValue = "0")
    private long localRevision;

    public MembershipEntity(@NonNull String id, @NonNull String name, int durationMonths, double price, @NonNull List<String> benefits, String syncStatus, long localRevision) {
        this.id = id;
        this.name = name;
        this.durationMonths = durationMonths;
        this.price = price;
        this.benefits = benefits;
        this.syncStatus = syncStatus;
        this.localRevision = localRevision;
    }

    @NonNull public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    @NonNull public String getName() { return name; }

    @NonNull public void setName(String name) { this.name = name; }

    public int getDurationMonths() { return durationMonths; }

    public void setDurationMonths(int durationMonths) { this.durationMonths = durationMonths; }

    public double getPrice() { return price; }

    public void setPrice(double price) { this.price = price; }

    @NonNull public List<String> getBenefits() { return benefits; }

    public void setBenefits(List<String> benefits) { this.benefits = benefits; }

    public String getSyncStatus() { return syncStatus; }

    public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }

    public long getLocalRevision() { return localRevision; }
    public void setLocalRevision(long localRevision) { this.localRevision = localRevision; }
}
