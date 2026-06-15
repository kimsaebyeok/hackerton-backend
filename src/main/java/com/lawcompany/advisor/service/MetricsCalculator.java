package com.lawcompany.advisor.service;

import com.lawcompany.advisor.domain.EventRecord;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 간이 지표 계산(LLM 프롬프트 + analysis_report.metrics 용).
 * ※ 정식 6렌즈 지표/산식은 docs/ANALYSIS_RULES_PROPOSAL.md 검토 후 확장 예정.
 */
@Component
public class MetricsCalculator {

    public Map<String, Object> compute(List<EventRecord> events) {
        long totalActiveMs = 0;
        Map<String, Long> timeByDomain = new LinkedHashMap<>();
        Map<String, Integer> domainVisits = new LinkedHashMap<>();
        Map<String, int[]> clickCounts = new LinkedHashMap<>();   // key → count
        Map<String, String[]> clickMeta = new LinkedHashMap<>();  // key → [tag, role, label]
        List<String> domainSequence = new ArrayList<>();

        for (EventRecord e : events) {
            switch (e.type()) {
                case "dwell_time" -> {
                    if (e.durationMs() != null) {
                        totalActiveMs += e.durationMs();
                        timeByDomain.merge(e.domain(), e.durationMs(), Long::sum);
                    }
                }
                case "navigation_completed", "tab_activated" -> {
                    domainVisits.merge(e.domain(), 1, Integer::sum);
                    domainSequence.add(e.domain());
                }
                case "click" -> {
                    String key = e.tag() + "|" + e.role() + "|" + e.label();
                    clickCounts.computeIfAbsent(key, k -> new int[1])[0]++;
                    clickMeta.putIfAbsent(key, new String[]{e.tag(), e.role(), e.label()});
                }
                default -> { /* ignore */ }
            }
        }

        // 연속 도메인 전환 수
        int toolSwitchCount = 0;
        for (int i = 1; i < domainSequence.size(); i++) {
            if (!domainSequence.get(i).equals(domainSequence.get(i - 1))) toolSwitchCount++;
        }

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("eventCount", events.size());
        metrics.put("totalActiveMs", totalActiveMs);
        metrics.put("domainVisits", toList(domainVisits, (d, v) -> Map.of("domain", d, "visits", v)));
        metrics.put("timeByDomain", toList(timeByDomain, (d, v) -> Map.of("domain", d, "totalMs", v)));
        metrics.put("topClicks", topClicks(clickCounts, clickMeta));
        metrics.put("domainSequence", domainSequence);
        metrics.put("toolSwitchCount", toolSwitchCount);
        return metrics;
    }

    private <V> List<Map<String, Object>> toList(Map<String, V> map, java.util.function.BiFunction<String, V, Map<String, Object>> f) {
        List<Map<String, Object>> out = new ArrayList<>();
        map.forEach((k, v) -> out.add(f.apply(k, v)));
        return out;
    }

    private List<Map<String, Object>> topClicks(Map<String, int[]> counts, Map<String, String[]> meta) {
        return counts.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, int[]> e) -> e.getValue()[0]).reversed())
                .limit(5)
                .map(e -> {
                    String[] m = meta.get(e.getKey());
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("tag", m[0]);
                    row.put("role", m[1]);
                    row.put("label", m[2]);
                    row.put("count", e.getValue()[0]);
                    return row;
                })
                .toList();
    }
}
