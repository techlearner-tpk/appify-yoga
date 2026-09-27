package dev.appify.content;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentRepository extends JpaRepository<ContentItem,UUID> {
  List<ContentItem> findByPublishedTrueOrderByCreatedAtDesc();
}
