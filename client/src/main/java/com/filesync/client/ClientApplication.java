package com.filesync.client;

import com.filesync.client.sync.SyncEngine;

public class ClientApplication {
    public static void main(String[] args) throws Exception
    {
        SyncEngine engine = new SyncEngine("user123", "./sync_folder", "http://localhost:8080");
        while (true)
        {
            engine.sync();
            Thread.sleep(5000);
        }

    }
}
