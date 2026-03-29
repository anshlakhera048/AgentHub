package com.agenthub.repository;

import com.agenthub.model.entity.SessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SessionRepository extends JpaRepository<SessionEntity, UUID> {

    List<SessionEntity> findByUserIdOrderByCreatedAtDesc(String userId);
}
