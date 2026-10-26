package com.railreserve.booking;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class AdminAuditServiceTest {
    @Mock JdbcTemplate jdbc;
    @InjectMocks AdminAuditService service;

    @Test
    void trimsFiltersAndClampsLimit() {
        doReturn(List.of()).when(jdbc).query(anyString(), any(RowMapper.class),
            any(), any(), any(), any(), anyInt());

        service.list(" admin@example.test ", " PAYMENT_REFUND_RESULT ", 1000);

        verify(jdbc).query(anyString(), any(RowMapper.class),
            eq("admin@example.test"), eq("admin@example.test"),
            eq("PAYMENT_REFUND_RESULT"), eq("PAYMENT_REFUND_RESULT"), eq(100));
    }

    @Test
    void blankFiltersBecomeNullAndMinimumLimitIsOne() {
        doReturn(List.of()).when(jdbc).query(anyString(), any(RowMapper.class),
            any(), any(), any(), any(), anyInt());

        service.list("  ", null, 0);

        verify(jdbc).query(anyString(), any(RowMapper.class), eq(null), eq(null), eq(null), eq(null), eq(1));
    }

    @Test
    void rejectsOverlongFilterBeforeDatabaseAccess() {
        assertThrows(IllegalArgumentException.class, () -> service.list("a".repeat(129), null, 10));
        assertThrows(IllegalArgumentException.class, () -> service.list(null, "x".repeat(101), 10));
        verifyNoInteractions(jdbc);
    }
}
