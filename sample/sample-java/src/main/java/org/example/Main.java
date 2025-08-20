package org.example;

import com.wire.integrations.jvm.WireAppSdk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    final static UUID MY_APPLICATION_ID = UUID.randomUUID();
    final static String WIRE_API_TOKEN = "myApiToken";
    final static String WIRE_API_HOST = "https://staging-nginz-https.zinfra.io";
    final static String WIRE_CRYPTOGRAPHY_STORAGE_PASSWORD = "myDummyPasswordOfRandom32BytesCH";

    public static void main(String[] args) {
        new Main().initApp();
        logger.info("Application started.");
    }

    private void initApp() {
        final var wireAppSdk = initSdkInstance();
        wireAppSdk.startListening();
    }

    private WireAppSdk initSdkInstance() {
        return new WireAppSdk(
                MY_APPLICATION_ID,
                WIRE_API_TOKEN,
                WIRE_API_HOST,
                WIRE_CRYPTOGRAPHY_STORAGE_PASSWORD,
                new CustomWireEventsHandler()
        );
    }

}
