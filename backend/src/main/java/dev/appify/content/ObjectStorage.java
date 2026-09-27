package dev.appify.content;

import java.io.InputStream;

public interface ObjectStorage {
  void put(String key,InputStream input,long size,String contentType) throws Exception;
  byte[] get(String key) throws Exception;
}
