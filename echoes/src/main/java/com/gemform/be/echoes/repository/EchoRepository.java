package com.gemform.be.echoes.repository;

import com.gemform.be.echoes.entity.Echo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EchoRepository extends JpaRepository<Echo, Long> {
}
