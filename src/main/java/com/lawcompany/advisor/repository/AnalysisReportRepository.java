package com.lawcompany.advisor.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class AnalysisReportRepository {

    private static final String INSERT_SQL = """
            INSERT INTO analysis_report (report_id, session_id, metrics, tasks)
            VALUES (?, ?, CAST(? AS jsonb), CAST(? AS jsonb))
            """;

    private final JdbcTemplate jdbc;

    public AnalysisReportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID reportId, UUID sessionId, String metricsJson, String tasksJson) {
        jdbc.update(INSERT_SQL, ps -> {
            ps.setObject(1, reportId);
            ps.setObject(2, sessionId);
            ps.setString(3, metricsJson);
            ps.setString(4, tasksJson);   // null 가능
        });
    }
}
