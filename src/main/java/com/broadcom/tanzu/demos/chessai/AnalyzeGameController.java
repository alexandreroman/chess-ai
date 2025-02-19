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

import io.github.wolfraam.chessgame.pgn.PGNExporter;
import io.github.wolfraam.chessgame.pgn.PGNTag;
import org.apache.hc.core5.http.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;

@Controller
class AnalyzeGameController {
    private final Logger logger = LoggerFactory.getLogger(AnalyzeGameController.class);
    private final BoardRepository repo;

    AnalyzeGameController(BoardRepository repo) {
        this.repo = repo;
    }

    @GetMapping(value = "/chess/{boardId}/analyze")
    ResponseEntity<?> analyze(@PathVariable String boardId) {
        logger.atInfo().log("Redirecting to lichess.org to analyze board: {}", boardId);

        final var board = repo.load(boardId).orElseThrow();
        if (!board.game().getAvailablePGNTags().contains(PGNTag.RESULT)) {
            board.game().getPGNData().setPGNTag(PGNTag.RESULT, "*");
        }
        board.game().getPGNData().setPGNTag(PGNTag.WHITE, "Human");
        board.game().getPGNData().setPGNTag(PGNTag.BLACK, "AI");

        final var buf = new ByteArrayOutputStream(1024);
        new PGNExporter(buf).write(board.game());
        final var pgn = buf.toString(StandardCharsets.UTF_8);

        final var uri = UriComponentsBuilder
                .fromUriString("https://lichess.org/paste")
                .queryParam("pgn", pgn)
                .build();
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, uri.toUriString())
                .build();
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<?> handleNoSuchElementException(NoSuchElementException e) {
        return ResponseEntity.notFound().build();
    }
}
