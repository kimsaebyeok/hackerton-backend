package com.lawcompany.advisor.repository;

import com.lawcompany.advisor.domain.EventRecord;
import com.lawcompany.advisor.domain.WorkLogEventRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * work_log_event 단건 적재. event_id 는 FE 생성이므로 충돌 시 무시(중복방지).
 */
@Repository
public class WorkLogEventRepository {

    private static final String INSERT_SQL = """
            INSERT INTO work_log_event
                (event_id, session_id, type, event_time, tab_id, domain,
                 safe_url, title, duration_ms, tag, role, label)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (event_id) DO NOTHING
            """;

    private final JdbcTemplate jdbc;

    public WorkLogEventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String SELECT_BY_SESSION_SQL = """
            SELECT type, event_time, tab_id, domain, safe_url, title, duration_ms, tag, role, label
            FROM work_log_event
            WHERE session_id = ?
            ORDER BY event_time
            """;

    private static final RowMapper<EventRecord> EVENT_MAPPER = (rs, i) -> new EventRecord(
            rs.getString("type"),
            toInstant(rs.getTimestamp("event_time")),
            (Integer) rs.getObject("tab_id"),
            rs.getString("domain"),
            rs.getString("safe_url"),
            rs.getString("title"),
            (Long) rs.getObject("duration_ms"),
            rs.getString("tag"),
            rs.getString("role"),
            rs.getString("label")
    );

    public void insert(WorkLogEventRow r) {
        jdbc.update(INSERT_SQL, (PreparedStatement ps) -> bind(ps, r));
    }

    public List<EventRecord> findBySession(UUID sessionId) {
        return jdbc.query(SELECT_BY_SESSION_SQL, EVENT_MAPPER, sessionId);
    }

    private static Instant toInstant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    private static void bind(PreparedStatement ps, WorkLogEventRow r) throws SQLException {
        ps.setObject(1, r.eventId());
        ps.setObject(2, r.sessionId());
        ps.setString(3, r.type());
        ps.setTimestamp(4, Timestamp.from(r.eventTime()));
        ps.setInt(5, r.tabId());
        ps.setString(6, r.domain());
        ps.setString(7, r.safeUrl());
        ps.setString(8, r.title());
        if (r.durationMs() == null) ps.setNull(9, Types.BIGINT);
        else ps.setLong(9, r.durationMs());
        ps.setString(10, r.tag());
        ps.setString(11, r.role());
        ps.setString(12, r.label());
    }
}
