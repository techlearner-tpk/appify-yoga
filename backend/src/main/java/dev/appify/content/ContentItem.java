package dev.appify.content;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity @Table(name="content")
public class ContentItem {
  @Id public UUID id;
  @Column(nullable=false) public String title;
  @Column(nullable=false) public String body;
  @Column(name="object_key") public String objectKey;
  @Column(name="content_type") public String contentType;
  @Column(nullable=false) public boolean published;
  @CreationTimestamp @Column(name="created_at",nullable=false,updatable=false) public OffsetDateTime createdAt;
  @UpdateTimestamp @Column(name="updated_at",nullable=false) public OffsetDateTime updatedAt;
  @Version public long version;
  protected ContentItem() {}
  public ContentItem(UUID id,String title,String body,String objectKey,String contentType) {this.id=id;this.title=title;this.body=body;this.objectKey=objectKey;this.contentType=contentType;this.published=true;}
}
