/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestDateHelper {

    void checkDuration(String target, String source) {
        assertEquals(target, DateHelper.parseDuration(source).toString());
    }

    @Test
    public void testDuration() {
        String duration = "1.5d";
        checkDuration("PT36H", "1.5d");
        checkDuration("PT30M", "0.5h");
        checkDuration("PT1M30S", "1.5m");
        checkDuration("PT1M40.5S", "100.5s");
        checkDuration("PT0.2S", "200ms");
    }

    @Test
    public void testStartOfMonth() {
        LocalDate now = LocalDate.now();
        LocalDate d = DateHelper.firstDayOfMonth(now);
        assertEquals(d.getMonth(), now.getMonth());
        assertEquals(d.getYear(), now.getYear());
        assertEquals(d.getDayOfMonth(), 1);

        d = DateHelper.lastDayOfMonth(now);
        assertEquals(d.getMonth(), now.getMonth());
        assertEquals(d.getYear(), now.getYear());
        assertEquals(d.plusDays(1).getDayOfMonth(), 1);
    }

    @Test
    public void testDayOfWeek() {
        LocalDate now = LocalDate.now();
        for (int i = 1; i <= 7; i++) {
            assertEquals(i, DateHelper.toDayOfWeek(now, i).getDayOfWeek().getValue());
        }
    }

    @Test
    public void testZone() {
        LocalDateTime dt = DateHelper.toZone(LocalDateTime.now(), ZoneOffset.UTC);
        System.out.println(dt);
    }
}