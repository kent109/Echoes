package com.gemform.be.echoes.repository;

import java.util.List;
import java.util.Set;

import com.gemform.be.echoes.entity.OkhttpChangelogEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OkhttpChangelogEntryRepository extends JpaRepository<OkhttpChangelogEntry, Long> {

    List<OkhttpChangelogEntry> findByContentHashIn(Set<String> contentHashes);

    List<OkhttpChangelogEntry> findByTextZhIsNullOrderByIdAsc();

    List<OkhttpChangelogEntry> findByVersionOrderByIdAsc(String version);

    List<OkhttpChangelogEntry> findAllByOrderByIdAsc();

    @Query("select distinct e.version from OkhttpChangelogEntry e")
    List<String> findDistinctVersions();
}
