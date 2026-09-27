package dev.appify.common;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import dev.appify.entitlement.SessionAccessDenied;

@RestControllerAdvice
public class ApiErrors {
  @ExceptionHandler(SessionAccessDenied.class)
  ResponseEntity<Map<String,String>> entitlement(SessionAccessDenied ex) {return ResponseEntity.status(ex.status()).body(Map.of("outcome",ex.outcome().name(),"error",ex.outcome().name()));}
  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<Map<String,String>> response(ResponseStatusException ex) { return ResponseEntity.status(ex.getStatusCode()).body(Map.of("error",ex.getReason()==null?"Request failed":ex.getReason())); }
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<Map<String,String>> validation(MethodArgumentNotValidException ex) { return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error",ex.getBindingResult().getFieldErrors().stream().findFirst().map(e -> e.getField()+": "+e.getDefaultMessage()).orElse("Invalid request"))); }
}
