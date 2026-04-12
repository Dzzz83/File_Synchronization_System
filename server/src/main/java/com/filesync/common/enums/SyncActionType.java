package com.filesync.common.enums;

public enum SyncActionType {
    UPLOAD,      // client should upload this file to server
    DOWNLOAD,    // client should download this file from server
    CONFLICT,    // both sides changed; needs manual merge
    NO_ACTION    // files are identical; nothing to do
}
