/*
 * Copyright (c) 2025 Broadcom, Inc. or its affiliates
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.broadcom.tanzu.demos.chessai;

import io.github.wolfraam.chessgame.ChessGame;
import io.github.wolfraam.chessgame.pgn.PGNExporter;
import io.github.wolfraam.chessgame.pgn.PGNTag;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

class ChessGameUtils {
    private ChessGameUtils() {
    }

    public static String getPGNData(ChessGame game) {
        if (!game.getAvailablePGNTags().contains(PGNTag.RESULT)) {
            game.getPGNData().setPGNTag(PGNTag.RESULT, "*");
        }
        game.getPGNData().setPGNTag(PGNTag.WHITE, "Human");
        game.getPGNData().setPGNTag(PGNTag.BLACK, "AI");

        final var buf = new ByteArrayOutputStream(1024);
        new PGNExporter(buf).write(game);
        return buf.toString(StandardCharsets.UTF_8);
    }
}
