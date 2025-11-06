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
import com.wire.sdk.model.QualifiedId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
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


        createLoad(wireAppSdk); ////  TODO ::: TEMPORARY METHOD JUST FOR TESTING
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

    private void createLoad(WireAppSdk wireAppSdk) {
        final List<QualifiedId> users = new ArrayList<>();
        users.add(new QualifiedId(
                UUID.fromString("67781a55-0b11-46b1-90ec-b0c5a5695ca0"), //baris
                "staging.zinfra.io"));

        final String conversationNamePrefix = "LoadTestConv-"+UUID.randomUUID().toString().substring(0, 3)+"-";

        for (int i = 0; i < 100; i++) {
            String conversationName = conversationNamePrefix + i;
            System.out.println("- - - - - - - - - - - - - - - - - -");
            System.out.println("Creating conversation : " + conversationName);
            System.out.println("- - - - - - - - - - - - - - - - - -");
            wireAppSdk.getApplicationManager().createGroupConversation(conversationName, users);
            try {
                Thread.sleep(3);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
