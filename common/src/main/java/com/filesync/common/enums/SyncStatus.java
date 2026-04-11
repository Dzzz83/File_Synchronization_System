package com.filesync.common.enums;

public enum SyncStatus {
    SYNCED,      // local and remote are identical
    MODIFIED,    // local changed, remote unchanged
    REMOTE_NEW,  // remote has file, local doesn't
    CONFLICT     // both sides changed differently
}