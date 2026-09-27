package dev.appify.entitlement;

import org.springframework.http.HttpStatus;

public class SessionAccessDenied extends RuntimeException {
  private final SessionEntitlementService.Outcome outcome;
  public SessionAccessDenied(SessionEntitlementService.Outcome outcome) {super(outcome.name());this.outcome=outcome;}
  public SessionEntitlementService.Outcome outcome() {return outcome;}
  public HttpStatus status() {return switch(outcome) {
    case LOGIN_REQUIRED -> HttpStatus.UNAUTHORIZED;
    case SESSION_NOT_FOUND -> HttpStatus.NOT_FOUND;
    case SESSION_NOT_OPEN,SESSION_EXPIRED,ALREADY_ATTENDED_TODAY,ASSET_NOT_READY -> HttpStatus.CONFLICT;
    default -> HttpStatus.FORBIDDEN;
  };}
}
