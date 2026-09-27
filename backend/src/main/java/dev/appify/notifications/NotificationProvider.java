package dev.appify.notifications;

import java.util.UUID;

public interface NotificationProvider { void send(UUID notificationId,String destination,String body); }
