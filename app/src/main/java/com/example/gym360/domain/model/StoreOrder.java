package com.example.gym360.domain.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class StoreOrder {
    public final String id, userUid, status, source, paymentMethod, deliveryNote;
    public final long totalCents, createdAtMillis, deliveredAtMillis;
    public final List<String> lines;
    public StoreOrder(DocumentSnapshot doc) {
        id = doc.getId();
        userUid = val(doc.getString("userUid"));
        status = val(doc.getString("status"));
        source = val(doc.getString("source"));
        paymentMethod = val(doc.getString("paymentMethod"));
        deliveryNote = val(doc.getString("deliveryNote"));
        Long amount = doc.getLong("totalCents");
        totalCents = amount == null ? 0 : amount;
        Timestamp created = doc.getTimestamp("createdAt"), delivered=doc.getTimestamp("deliveredAt");
        createdAtMillis = created==null?0:created.toDate().getTime();
        deliveredAtMillis = delivered == null ? 0 : delivered.toDate().getTime();
        lines = new ArrayList<>();
        Object list = doc.get("items");

        if(list instanceof List) for(Object raw:(List<?>)list) if(raw instanceof Map) {
            Map<?,?> item = (Map<?,?>) raw;
            lines.add(val(String.valueOf(item.get("name")))+" ×" + String.valueOf(item.get("quantity")));
        }
    }
    private static String val(String s){
        return s == null ? "" : s;
    }
}
