package com.gemform.be.echoes.rag;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.gemform.be.echoes.entity.OkhttpChangelogEntry;
import com.gemform.be.echoes.repository.OkhttpChangelogEntryRepository;
import com.gemform.be.echoes.rag.OkhttpChangelogParser.ParsedEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 抓取官方 changelog，与库内条目按 content_hash 比对，只回写新增的部分并翻成简体中文。
 */
@Component
public class OkhttpChangelogSyncService {

    public static final String SOURCE_URL = "https://lysine.dev/okhttp/changelogs/changelog/";

    private static final Logger log = LoggerFactory.getLogger(OkhttpChangelogSyncService.class);
    private static final Pattern NUMBERED_LINE = Pattern.compile("^\\s*(\\d+)[.)]\\s*(.+)$");
    private static final int TRANSLATION_BATCH_SIZE = 20;

    private final OkhttpChangelogParser parser;
    private final OkhttpChangelogEntryRepository repository;
    // 翻译不能挂记忆 advisor：否则这些系统提示会混进用户的会话记忆里
    private final ChatClient translationChatClient;
    private final HttpClient httpClient;
    private final AtomicBoolean syncing = new AtomicBoolean();

    public OkhttpChangelogSyncService(OkhttpChangelogParser parser, OkhttpChangelogEntryRepository repository,
            ChatModel chatModel) {
        this.parser = parser;
        this.repository = repository;
        this.translationChatClient = ChatClient.builder(chatModel).build();
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public record SyncReport(int parsed, int inserted, Map<String, Long> insertedByKind, int translated,
            long totalInDb, boolean changed) {
    }

    @Scheduled(initialDelayString = "30000", fixedDelayString = "${rag.okhttp.sync-interval-ms:21600000}")
    public void scheduledSync() {
        try {
            sync();
        }
        catch (Exception ex) {
            // 定时任务里抛出去只有一行堆栈，日志留一条可读的
            log.warn("okhttp changelog 定时同步失败: {}", ex.toString());
        }
    }

    public SyncReport sync() {
        if (!syncing.compareAndSet(false, true)) {
            throw new IllegalStateException("已有 okhttp changelog 同步在运行，请稍后再试");
        }
        try {
            return doSync();
        }
        finally {
            syncing.set(false);
        }
    }

    private SyncReport doSync() {
        List<ParsedEntry> parsed = parser.parse5x(fetch());

        Map<String, ParsedEntry> byHash = new LinkedHashMap<>();
        for (ParsedEntry entry : parsed) {
            if (!entry.textEn().isBlank()) {
                byHash.putIfAbsent(hash(entry), entry);
            }
        }
        Set<String> alreadyStored = repository.findByContentHashIn(byHash.keySet()).stream()
                .map(OkhttpChangelogEntry::getContentHash)
                .collect(Collectors.toSet());

        List<OkhttpChangelogEntry> fresh = byHash.entrySet().stream()
                .filter(entry -> !alreadyStored.contains(entry.getKey()))
                .map(entry -> toEntity(entry.getValue(), entry.getKey()))
                .toList();
        if (!fresh.isEmpty()) {
            repository.saveAll(fresh);
        }

        Map<String, Long> insertedByKind = fresh.stream()
                .collect(Collectors.groupingBy(OkhttpChangelogEntry::getKind, LinkedHashMap::new,
                        Collectors.counting()));

        int translated = translatePending();

        log.info("okhttp changelog 同步: 解析 {} 条 / 已存在 {} 条 / 本次新增 {} 条（新增功能 {} 条，修复问题 {} 条，破坏性变更 {} 条，依赖升级 {} 条）/ 本次翻译 {} 条 / 库内合计 {} 条",
                parsed.size(), byHash.size() - fresh.size(), fresh.size(),
                insertedByKind.getOrDefault("NEW", 0L), insertedByKind.getOrDefault("FIX", 0L),
                insertedByKind.getOrDefault("BREAKING", 0L), insertedByKind.getOrDefault("UPGRADE", 0L),
                translated, repository.count());

        return new SyncReport(parsed.size(), fresh.size(), insertedByKind, translated, repository.count(),
                !fresh.isEmpty());
    }

    private String fetch() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(SOURCE_URL))
                .header("User-Agent", "EchoesRag/1.0")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("changelog 页面返回 HTTP " + response.statusCode());
            }
            return response.body();
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("抓取 changelog 被中断", ex);
        }
        catch (java.io.IOException ex) {
            throw new IllegalStateException("抓取 changelog 失败: " + ex.getMessage(), ex);
        }
    }

    private String hash(ParsedEntry entry) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((entry.version() + "|" + entry.kind() + "|" + entry.textEn())
                    .getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        }
        catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    private OkhttpChangelogEntry toEntity(ParsedEntry entry, String contentHash) {
        OkhttpChangelogEntry entity = new OkhttpChangelogEntry();
        entity.setVersion(entry.version());
        entity.setReleaseDate(entry.releaseDate());
        entity.setKind(entry.kind());
        entity.setTextEn(entry.textEn());
        entity.setContentHash(contentHash);
        entity.setSourceUrl(SOURCE_URL);
        return entity;
    }

    /**
     * 逐条把英文原文译成中文；模型不按编号回吐时整批留空，下一次同步再补。
     */
    private int translatePending() {
        List<OkhttpChangelogEntry> pending = repository.findByTextZhIsNullOrderByIdAsc();
        if (pending.isEmpty()) {
            return 0;
        }
        int translated = 0;
        for (int from = 0; from < pending.size(); from += TRANSLATION_BATCH_SIZE) {
            List<OkhttpChangelogEntry> batch = pending.subList(from,
                    Math.min(from + TRANSLATION_BATCH_SIZE, pending.size()));
            List<String> lines = translate(batch.stream().map(OkhttpChangelogEntry::getTextEn).toList());
            if (lines.size() != batch.size()) {
                // 条数不匹配基本都是提供方限流，继续试只会把配额撞得更死；整批留到下次
                log.warn("翻译结果条数不匹配（期望 {}，实际 {}），剩余 {} 条留待下次重试", batch.size(), lines.size(),
                        pending.size() - from);
                break;
            }
            for (int i = 0; i < batch.size(); i++) {
                batch.get(i).setTextZh(lines.get(i));
            }
            repository.saveAll(batch);
            translated += batch.size();
        }
        return translated;
    }

    private List<String> translate(List<String> englishItems) {
        StringBuilder prompt = new StringBuilder(
                "把下面每条 okhttp 更新日志单独翻译成简体中文。要求：只输出 %d 行，每行以“序号. ”开头，顺序与输入一致，"
                        + "不要合并、不要解释；类名、方法名、Maven 坐标、配置项等英文标识符原样保留。\n\n"
                        .formatted(englishItems.size()));
        for (int i = 0; i < englishItems.size(); i++) {
            prompt.append(i + 1).append(". ").append(englishItems.get(i)).append('\n');
        }
        String reply;
        try {
            reply = translationChatClient.prompt().user(prompt.toString()).call().content();
        }
        catch (Exception ex) {
            log.warn("翻译调用失败，留待下次重试: {}", ex.toString());
            return List.of();
        }
        if (reply == null || reply.isBlank()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (String raw : reply.split("\\R")) {
            Matcher matcher = NUMBERED_LINE.matcher(raw);
            if (matcher.matches()) {
                lines.add(matcher.group(2).trim());
            }
        }
        return lines;
    }
}
