package com.reForm.backend.ai.domain;

/**
 * SESSION PHASE ENUM
 * 
 * Defines the lifecycle state machine for real-time voice and conversational AI sessions:
 * - INIT: Handshake authenticated, persona loading, before first turn
 * - ACTIVE: Live conversation in progress, capturing periodic turn snapshots
 * - PAUSED: Socket disconnected unexpectedly (WiFi/4G drop), holding 5-minute reconnect grace window
 * - RECONNECTING: Reconnection in flight, acquiring distributed lock to prevent multi-tab split-brain
 * - TERMINATED: Session cleanly ended by user/AI (endSession), triggering final cleanup and metric export
 */
public enum SessionPhase {
    INIT,
    ACTIVE,
    PAUSED,
    RECONNECTING,
    TERMINATED
}
