package dev.appify.content;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.InputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MinioObjectStorage implements ObjectStorage {
  private final MinioClient client; private final String bucket;
  public MinioObjectStorage(@Value("${app.minio.endpoint}") String endpoint,@Value("${app.minio.user}") String user,@Value("${app.minio.password}") String password,@Value("${app.minio.bucket}") String bucket) {
    this.client=MinioClient.builder().endpoint(endpoint).credentials(user,password).build();this.bucket=bucket;
  }
  @Override public void put(String key,InputStream input,long size,String contentType) throws Exception {
    if(!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
    client.putObject(PutObjectArgs.builder().bucket(bucket).object(key).stream(input,size,-1).contentType(contentType).build());
  }
  @Override public byte[] get(String key) throws Exception {
    try(var stream=client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())) {return stream.readAllBytes();}
  }
}
