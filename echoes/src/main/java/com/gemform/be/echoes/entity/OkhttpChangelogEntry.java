package com.gemform.be.echoes.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "okhttp_changelog_entry",
        uniqueConstraints = @UniqueConstraint(name = "uk_okhttp_changelog_entry_hash", columnNames = "content_hash"))
public class OkhttpChangelogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String version;

    // 官网各段日期写法不统一（2025-07-07 与 2026_10_22 都出现过），原样存文本
    @Column(name = "release_date", length = 32)
    private String releaseDate;

    @Column(nullable = false, length = 16)
    private String kind;

    @Column(name = "text_en", nullable = false, columnDefinition = "text")
    private String textEn;

    // 翻译失败时留空，下一次同步接着补
    @Column(name = "text_zh", columnDefinition = "text")
    private String textZh;

    // 官网条目没有稳定 id，用 sha256(version|kind|textEn) 做幂等回写
    @Column(name = "content_hash", nullable = false, length = 64, updatable = false)
    private String contentHash;

    @Column(name = "source_url", nullable = false, length = 512)
    private String sourceUrl;

    @Column(name = "fetched_at", nullable = false, updatable = false)
    private OffsetDateTime fetchedAt;

    @PrePersist
    void fillFetchedAt() {
        if (fetchedAt == null) {
            fetchedAt = OffsetDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(String releaseDate) {
        this.releaseDate = releaseDate;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getTextEn() {
        return textEn;
    }

    public void setTextEn(String textEn) {
        this.textEn = textEn;
    }

    public String getTextZh() {
        return textZh;
    }

    public void setTextZh(String textZh) {
        this.textZh = textZh;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public OffsetDateTime getFetchedAt() {
        return fetchedAt;
    }
}
