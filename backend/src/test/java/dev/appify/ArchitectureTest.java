package dev.appify;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class ArchitectureTest {
  @Test void apiDoesNotUseFeaturesCoveredByTemporaryScanExceptions() {
    var classes=new ClassFileImporter().importPackages("dev.appify");
    noClasses().should().dependOnClassesThat().resideInAnyPackage(
      "org.springframework.web.servlet.view..").check(classes);
    for (String type : new String[]{
      "org.springframework.web.servlet.mvc.method.annotation.SseEmitter",
      "org.springframework.http.codec.ServerSentEvent"}) {
      noClasses().should().dependOnClassesThat().haveFullyQualifiedName(type).check(classes);
    }
  }
  @Test void attendanceRulesDoNotDependOnWebOrPersistence() {
    var classes=new ClassFileImporter().importPackages("dev.appify.attendance");
    noClasses().that().haveSimpleName("AttendanceRules").should().dependOnClassesThat().resideInAnyPackage("org.springframework..","jakarta.persistence..","dev.appify.admin..").check(classes);
  }
  @Test void noPaymentModule() {
    var classes=new ClassFileImporter().importPackages("dev.appify");
    noClasses().should().resideInAPackage("dev.appify.payment..").check(classes);
  }
  @Test void businessAccessAndCleanupDependOnProviderContract() {
    var classes=new ClassFileImporter().importPackages("dev.appify");
    noClasses().that().resideInAnyPackage("dev.appify.entitlement..","dev.appify.attendance..","dev.appify.scheduling..","dev.appify.media.AssetDeletionConsumer")
      .should().dependOnClassesThat().haveSimpleName("YouTubeVideoProvider").check(classes);
  }
}
