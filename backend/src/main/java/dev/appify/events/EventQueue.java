package dev.appify.events;

import java.util.UUID;

public interface EventQueue { void publish(UUID eventId,String eventType,String payload); }
