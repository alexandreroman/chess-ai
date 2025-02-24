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

package com.broadcom.tanzu.demos.chessai.impl.stockfishonline;

import com.broadcom.tanzu.demos.chessai.ChessEngine;
import io.github.wolfraam.chessgame.ChessGame;
import io.github.wolfraam.chessgame.move.Move;
import io.github.wolfraam.chessgame.notation.NotationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.regex.Pattern;

class StockfishOnlineEngine implements ChessEngine {
    private final Logger logger = LoggerFactory.getLogger(StockfishOnlineEngine.class);
    private final Pattern bestmovePattern = Pattern.compile("bestmove\\s(\\S+)");
    private final StockfishOnline api;

    StockfishOnlineEngine(StockfishOnline api) {
        this.api = api;
    }

    public Optional<Move> getNextMove(ChessGame game) {
        final var fen = game.getFen();
        logger.atDebug().log("Using Stockfish.online to guess next move using FEN: {}", fen);
        final var resp = api.getNextMove(fen, 3);
        if (resp == null || !resp.success()) {
            logger.atWarn().log("No next move found using Stockfish.online using FEN: {}", fen);
            return Optional.empty();
        }

        final var matcher = bestmovePattern.matcher(resp.bestmove());
        if (!matcher.find()) {
            logger.atWarn().log("Unable to read best move from Stockfish.online using FEN '{}': {}", fen, resp.bestmove());
            return Optional.empty();
        }

        final var bestMove = matcher.group(1);
        final var nextMove = game.getMove(NotationType.UCI, bestMove);
        logger.atInfo().log("Found next move with Stockfish.online using FEN '{}': {}", fen, nextMove);
        return Optional.of(nextMove);
    }

    @Override
    public String toString() {
        return "Stockfish.online";
    }
}
