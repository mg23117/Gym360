package com.example.gym360.domain.model;

import androidx.annotation.NonNull;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/** Para convertir expiresOnExclusive en una fecha amigable: el ÚLTIMO día vigente es el anterior. */
public final class MembershipDateLabels {
    private static final TimeZone ZONE = TimeZone.getTimeZone("America/El_Salvador");
    private static final Locale LOCALE = new Locale("es", "SV");
    private MembershipDateLabels() {}

    @NonNull public static String lastValidDay(@NonNull String expiresOnExclusive) {
        SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        input.setLenient(false);
        input.setTimeZone(ZONE);
        try {
            Calendar calendar = Calendar.getInstance(ZONE, LOCALE);
            calendar.setTime(input.parse(expiresOnExclusive));
            calendar.add(Calendar.DAY_OF_MONTH, -1);
            SimpleDateFormat output = new SimpleDateFormat("d 'de' MMMM 'de' yyyy", LOCALE);
            output.setTimeZone(ZONE);
            return output.format(calendar.getTime());
        } catch (ParseException exception) {
            return expiresOnExclusive;
        }
    }
}
