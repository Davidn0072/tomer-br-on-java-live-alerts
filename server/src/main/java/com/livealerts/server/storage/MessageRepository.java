package com.livealerts.server.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<StoredMessage, Long> {

    List<StoredMessage> findAllByOrderByReceivedAtDesc();
}
