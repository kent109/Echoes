package com.gemform.be.echoes.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * 解析官方 changelog 页（MkDocs 结构）里 5.x 各版本的条目。
 * 页面按 h2 分段，段内是 ul/li，每条以 New:/Fix:/Breaking:/Upgrade:/Changed: 开头。
 */
@Component
public class OkhttpChangelogParser {

    private static final Pattern VERSION_5X = Pattern.compile("Version\\s+(5\\.\\d+\\.\\d+(?:-[A-Za-z]+\\.\\d+)?)");
    private static final Pattern KIND_PREFIX =
            Pattern.compile("^(New|Fix|Breaking|Upgrade|Changed)\\s*:\\s*(.+)$", Pattern.DOTALL);
    private static final Pattern RELEASE_DATE = Pattern.compile("(\\d{4})[-_](\\d{2})[-_](\\d{2})");

    public record ParsedEntry(String version, String releaseDate, String kind, String textEn) {
    }

    public List<ParsedEntry> parse5x(String html) {
        Document document = Jsoup.parse(html);
        Element article = document.selectFirst("article.md-content__inner");
        if (article == null) {
            article = document.body();
        }
        article.select("a.headerlink").remove();

        List<ParsedEntry> entries = new ArrayList<>();
        String version = null;
        String releaseDate = null;
        for (Element block : article.children()) {
            String tag = block.tagName();
            if (tag.equals("h1") || tag.equals("h2") || tag.equals("h3")) {
                String heading = block.text().trim();
                if (heading.contains("Version 4")) {
                    // 4.x 只留一个跳转标题，正文在另一页
                    break;
                }
                Matcher matcher = VERSION_5X.matcher(heading);
                version = matcher.find() ? matcher.group(1) : null;
                releaseDate = null;
            }
            else if (tag.equals("p") && version != null && releaseDate == null) {
                Matcher matcher = RELEASE_DATE.matcher(block.text());
                if (matcher.find()) {
                    releaseDate = matcher.group(1) + "-" + matcher.group(2) + "-" + matcher.group(3);
                }
            }
            else if (tag.equals("ul") && version != null) {
                for (Element item : block.select("> li")) {
                    Matcher matcher = KIND_PREFIX.matcher(item.text().trim());
                    if (matcher.matches() && !matcher.group(2).isBlank()) {
                        entries.add(new ParsedEntry(version, releaseDate, matcher.group(1).toUpperCase(),
                                matcher.group(2).trim()));
                    }
                }
            }
        }
        return List.copyOf(entries);
    }
}
