package dev.appify.content;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ContentController {
  private final JdbcTemplate db; private final ObjectStorage storage; private final ContentRepository contents;
  public ContentController(JdbcTemplate db,ObjectStorage storage,ContentRepository contents) {this.db=db;this.storage=storage;this.contents=contents;}
  @GetMapping("/api/content") public List<Map<String,Object>> list() {return contents.findByPublishedTrueOrderByCreatedAtDesc().stream().limit(100).map(c->Map.<String,Object>of("id",c.id,"title",c.title,"body",c.body,"has_image",c.objectKey!=null,"created_at",c.createdAt.toString())).toList();}
  @GetMapping("/api/content/{id}/image") public ResponseEntity<byte[]> image(@PathVariable UUID id) throws Exception {
    ContentItem item=contents.findById(id).filter(c->c.published && c.objectKey!=null).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Content image not found"));
    return ResponseEntity.ok().contentType(MediaType.parseMediaType(item.contentType)).body(storage.get(item.objectKey));
  }
  @PostMapping(value="/api/admin/content",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public Map<String,Object> create(@RequestParam String title,@RequestParam String body,@RequestParam(required=false) MultipartFile image,Authentication auth) throws Exception {
    if(title.isBlank() || body.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Title and body required");
    UUID id=UUID.randomUUID();String key=null;
    if(image!=null && !image.isEmpty()) {
      if(image.getSize()>5_000_000 || !List.of("image/jpeg","image/png","image/webp").contains(image.getContentType())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Image must be JPEG, PNG, or WebP under 5 MB");
      key=id+"/image";storage.put(key,image.getInputStream(),image.getSize(),image.getContentType());
    }
    contents.save(new ContentItem(id,title,body,key,image==null?null:image.getContentType()));
    db.update("insert into audit_log(id,actor_id,action,entity_type,entity_id) values(?,?,'CONTENT_CREATED','content',?)",UUID.randomUUID(),auth.getPrincipal(),id);
    return Map.of("id",id);
  }
}
