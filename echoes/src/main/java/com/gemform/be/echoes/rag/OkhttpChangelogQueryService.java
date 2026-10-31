package com.gemform.be.echoes.rag;

import com.gemform.be.echoes.entity.OkhttpChangelogEntry;
import com.gemform.be.echoes.repository.OkhttpChangelogEntryRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 只读库，不访问网络：网络抓取全部走 OkhttpChangelogSyncService。
 */
@Component
public class OkhttpChangelogQueryService {

    private static final Pattern VERSION = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)(?:-([A-Za-z]+)\\.(\\d+))?");
    private static final Map<String, String> KIND_LABELS =
            Map.of("NEW", "新增功能", "FIX", "修复问题", "BREAKING", "破坏性变更", "UPGRADE", "依赖升级", "CHANGED", "变更");

    private final OkhttpChangelogEntryRepository repository;

    public OkhttpChangelogQueryService(OkhttpChangelogEntryRepository repository) {
        this.repository = repository;
    }

    @Tool(name = "query_okhttp5_info", description = """
            查询 okhttp 5.x（含 alpha 预发布版本）某个版本新增了哪些功能、修复了哪些问题，返回简体中文条目。
            两个参数都可为空；全部为空会返回 29 个版本共 180 条，内容很长，只在用户要完整历史时才这样查。
            数据来自本地库，无需联网。""")
    public String queryOkhttp5Info(
            @ToolParam(required = false, description = "版本号，例如 5.5.1 或 5.0.0-alpha.13；留空表示全部版本") String version,
            @ToolParam(required = false, description = "条目类型 NEW|FIX|BREAKING|UPGRADE|CHANGED；留空表示全部类型") String kind) {
        List<OkhttpChangelogEntry> rows = query(version, kind);
        if (rows.isEmpty()) {
            return "本地库里没有匹配 " + (version == null ? "" : version) + (kind == null ? "" : " / " + kind)
                    + " 的记录。现有版本：" + String.join(", ", versions());
        }
        return render(rows);
    }

    public List<String> versions() {
        List<String> versions = new ArrayList<>(repository.findDistinctVersions());
        versions.sort(Comparator.comparing(this::versionKey).reversed());
        return versions;
    }

    /**
     * version 与 kind 都可为空，空表示不过滤；kind 不区分大小写，接受 new/fix/breaking/upgrade/changed。
     */
    public List<OkhttpChangelogEntry> query(String version, String kind) {
        String wantedVersion = version == null || version.isBlank() ? null : version.trim().replaceFirst("^[vV]", "");
        String wantedKind = kind == null || kind.isBlank() ? null : kind.trim().toUpperCase();
        List<OkhttpChangelogEntry> rows = wantedVersion == null
                ? repository.findAllByOrderByIdAsc()
                : repository.findByVersionOrderByIdAsc(wantedVersion);
        List<OkhttpChangelogEntry> filtered = rows.stream()
                .filter(entry -> wantedKind == null || wantedKind.equals(entry.getKind()))
                .sorted(Comparator.comparing((OkhttpChangelogEntry entry) -> versionKey(entry.getVersion())).reversed()
                        .thenComparing(OkhttpChangelogEntry::getId))
                .toList();
        return filtered;
    }

    public Map<String, Long> countsByKind(List<OkhttpChangelogEntry> rows) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (OkhttpChangelogEntry row : rows) {
            counts.merge(row.getKind(), 1L, Long::sum);
        }
        return counts;
    }

    /**
     * 按版本分组渲染成中文文本，供工具与文本响应复用。
     */
    public String render(List<OkhttpChangelogEntry> rows) {
        Map<String, List<OkhttpChangelogEntry>> grouped = new LinkedHashMap<>();
        for (OkhttpChangelogEntry row : rows) {
            grouped.computeIfAbsent(row.getVersion(), key -> new ArrayList<>()).add(row);
        }
        StringBuilder text = new StringBuilder("okhttp 5.x 更新日志，共 ").append(grouped.size())
                .append(" 个版本 / ").append(rows.size()).append(" 条：\n");
        for (Map.Entry<String, List<OkhttpChangelogEntry>> group : grouped.entrySet()) {
            String releaseDate = group.getValue().stream()
                    .map(OkhttpChangelogEntry::getReleaseDate)
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse("");
            text.append("\n【").append(group.getKey());
            if (!releaseDate.isBlank()) {
                text.append(" · ").append(releaseDate);
            }
            text.append("】\n");
            for (OkhttpChangelogEntry entry : group.getValue()) {
                text.append("- ").append(KIND_LABELS.getOrDefault(entry.getKind(), entry.getKind())).append("：")
                        .append(entry.getTextZh() != null ? entry.getTextZh() : entry.getTextEn()).append('\n');
            }
        }
        return text.toString();
    }

    private VersionKey versionKey(String version) {
        Matcher matcher = VERSION.matcher(version == null ? "" : version);
        if (!matcher.matches()) {
            return new VersionKey(0, 0, 0, "", 0);
        }
        return new VersionKey(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3)), matcher.group(4) == null ? null : matcher.group(4),
                matcher.group(5) == null ? 0 : Integer.parseInt(matcher.group(5)));
    }

    /**
     * 预发布版排在同版本正式版之前，所以 compareTo 里 pre 非空要更小。
     */
    private record VersionKey(int major, int minor, int patch, String preRelease, int preNumber)
            implements Comparable<VersionKey> {

        @Override
        public int compareTo(VersionKey other) {
            int result = Integer.compare(major, other.major);
            if (result != 0) {
                return result;
            }
            result = Integer.compare(minor, other.minor);
            if (result != 0) {
                return result;
            }
            result = Integer.compare(patch, other.patch);
            if (result != 0) {
                return result;
            }
            if (preRelease == null && other.preRelease == null) {
                return 0;
            }
            if (preRelease == null) {
                return 1;
            }
            if (other.preRelease == null) {
                return -1;
            }
            result = preRelease.compareTo(other.preRelease);
            return result != 0 ? result : Integer.compare(preNumber, other.preNumber);
        }
    }
}
