package com.gemform.be.echoes.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gemform.be.echoes.entity.OkhttpChangelogEntry;
import com.gemform.be.echoes.rag.OkhttpChangelogQueryService;
import com.gemform.be.echoes.rag.OkhttpChangelogSyncService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private static final Set<String> KINDS = Set.of("NEW", "FIX", "BREAKING", "UPGRADE", "CHANGED");

    private final OkhttpChangelogQueryService queryService;
    private final OkhttpChangelogSyncService syncService;

    public RagController(OkhttpChangelogQueryService queryService, OkhttpChangelogSyncService syncService) {
        this.queryService = queryService;
        this.syncService = syncService;
    }

    public record ChangelogItem(String version, String releaseDate, String kind, String text, boolean translated) {
    }

    public record QueryResponse(int total, Map<String, Long> countsByKind, List<String> versions,
            List<ChangelogItem> items) {
    }

    @GetMapping("/okhttp5")
    public ResponseEntity<Object> okhttp5(@RequestParam(required = false) String version,
            @RequestParam(required = false) String kind, @RequestParam(required = false) String format) {
        String wantedKind = kind == null || kind.isBlank() ? null : kind.trim().toUpperCase();
        if (wantedKind != null && !KINDS.contains(wantedKind)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "kind must be one of " + KINDS + ", but was " + wantedKind);
        }
        List<OkhttpChangelogEntry> rows = queryService.query(version, wantedKind);

        if ("text".equalsIgnoreCase(format)) {
            String text = rows.isEmpty() ? "没有匹配的记录" : queryService.render(rows);
            return ResponseEntity.ok()
                    .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                    .body(text);
        }
        List<ChangelogItem> items = rows.stream().map(RagController::toItem).toList();
        return ResponseEntity.ok(new QueryResponse(rows.size(), queryService.countsByKind(rows),
                queryService.versions(), items));
    }

    @GetMapping("/okhttp5/versions")
    public List<String> versions() {
        return queryService.versions();
    }

    @PostMapping("/okhttp5/sync")
    public ResponseEntity<Object> sync() {
        try {
            return ResponseEntity.ok(syncService.sync());
        }
        catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
        }
    }

    private static ChangelogItem toItem(OkhttpChangelogEntry entry) {
        boolean translated = entry.getTextZh() != null;
        return new ChangelogItem(entry.getVersion(), entry.getReleaseDate(), entry.getKind(),
                translated ? entry.getTextZh() : entry.getTextEn(), translated);
    }
}
