/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.sdk.sample;

import com.wire.sdk.WireAppSdk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    final static UUID MY_APPLICATION_ID = UUID.fromString(System.getenv("WIRE_SDK_APPLICATION_ID"));
    final static String MY_APPLICATION_DOMAIN = System.getenv("WIRE_SDK_APPLICATION_DOMAIN");
    final static String WIRE_API_TOKEN = System.getenv("WIRE_SDK_API_TOKEN");
    final static String WIRE_API_HOST = System.getenv("WIRE_SDK_API_HOST");
    final static String WIRE_CRYPTOGRAPHY_STORAGE_PASSWORD = System.getenv("WIRE_SDK_CRYPTO_STORAGE_PASSWORD");

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
                MY_APPLICATION_DOMAIN,
                WIRE_API_TOKEN,
                WIRE_API_HOST,
                WIRE_CRYPTOGRAPHY_STORAGE_PASSWORD,
                new CustomWireEventsHandler()
        );
    }

}
