package com.lawcompany.advisor.repository;

import com.lawcompany.advisor.dto.SessionSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class WorkSessionRepository {

    private static final String UPSERT_SQL = """
            INSERT INTO work_session (session_id, user_id, started_at, status)
            VALUES (?, ?, ?, 'collecting')
            ON CONFLICT (session_id) DO NOTHING
            """;

    /** 종료: 이벤트로부터 집계 확정 + status=closed. RETURNING 으로 결과 반환. */
    private static final String CLOSE_SQL = """
            UPDATE work_session s SET
                started_at = COALESCE((SELECT min(event_time) FROM work_log_event e WHERE e.session_id = s.session_id), s.started_at),
                ended_at   = (SELECT max(event_time) FROM work_log_event e WHERE e.session_id = s.session_id),
                active_duration_ms = COALESCE((SELECT sum(duration_ms) FROM work_log_event e WHERE e.session_id = s.session_id AND e.type = 'dwell_time'), 0),
                event_count = (SELECT count(*) FROM work_log_event e WHERE e.session_id = s.session_id),
                status = 'closed'
            WHERE s.session_id = ?
            RETURNING session_id, user_id, status, event_count, active_duration_ms, started_at, ended_at
            """;

    /** 현황(실시간 집계). 수집 중에도 누적 상태를 확인. */
    private static final String SUMMARY_SQL = """
            SELECT s.session_id, s.user_id, s.status,
                (SELECT count(*) FROM work_log_event e WHERE e.session_id = s.session_id) AS event_count,
                COALESCE((SELECT sum(duration_ms) FROM work_log_event e WHERE e.session_id = s.session_id AND e.type = 'dwell_time'), 0) AS active_duration_ms,
                (SELECT min(event_time) FROM work_log_event e WHERE e.session_id = s.session_id) AS started_at,
                (SELECT max(event_time) FROM work_log_event e WHERE e.session_id = s.session_id) AS ended_at
            FROM work_session s
            WHERE s.session_id = ?
            """;

    private static final RowMapper<SessionSummary> MAPPER = (rs, i) -> new SessionSummary(
            rs.getObject("session_id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getString("status"),
            rs.getLong("event_count"),
            rs.getLong("active_duration_ms"),
            toInstant(rs, "started_at"),
            toInstant(rs, "ended_at")
    );

    private final JdbcTemplate jdbc;

    public WorkSessionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void upsertIfAbsent(UUID sessionId, UUID userId, Instant startedAt) {
        jdbc.update(UPSERT_SQL, ps -> {
            ps.setObject(1, sessionId);
            if (userId == null) ps.setNull(2, Types.OTHER);
            else ps.setObject(2, userId);
            ps.setTimestamp(3, Timestamp.from(startedAt));
        });
    }

    public Optional<SessionSummary> close(UUID sessionId) {
        return jdbc.query(CLOSE_SQL, MAPPER, sessionId).stream().findFirst();
    }

    public Optional<SessionSummary> findSummary(UUID sessionId) {
        return jdbc.query(SUMMARY_SQL, MAPPER, sessionId).stream().findFirst();
    }

    private static Instant toInstant(ResultSet rs, String col) throws SQLException {
        Timestamp t = rs.getTimestamp(col);
        return t == null ? null : t.toInstant();
    }
}
