package com.example.gym360.domain.model;

import androidx.annotation.NonNull;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/* expiresOnExclusive es el primer día que la membresía no está vigente*/
public class MembershipTime {
    private static final TimeZone GYM_ZONE = TimeZone.getTimeZone("America/El_Salvador");
    private MembershipTime() {}

    public static int remainingDays(@NonNull String expiresOnExclusive) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setLenient(false);
        format.setTimeZone(GYM_ZONE);
        try {
            long expiresMillis = format.parse(expiresOnExclusive).getTime();
            Calendar today = Calendar.getInstance(GYM_ZONE);
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);
            return (int) Math.max(0L, (expiresMillis - today.getTimeInMillis()) / 86400000L);
        } catch (ParseException e) {
            throw new IllegalArgumentException("Fecha de vencimiento inválida", e);
        }
    }
}
