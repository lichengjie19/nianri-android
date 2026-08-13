package com.nianri.app;

import java.time.LocalDate;

public final class Occurrence {
    public final LocalDate solarDate;
    public final long daysFromToday;
    public final boolean expired;
    public final String primaryDate;
    public final String secondaryDate;

    public Occurrence(
            LocalDate solarDate,
            long daysFromToday,
            boolean expired,
            String primaryDate,
            String secondaryDate
    ) {
        this.solarDate = solarDate;
        this.daysFromToday = daysFromToday;
        this.expired = expired;
        this.primaryDate = primaryDate;
        this.secondaryDate = secondaryDate;
    }
}
